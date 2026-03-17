package com.smartmove.controller;

import com.smartmove.config.LoggerFactory;
import java.util.logging.Logger;
import com.smartmove.audit.AuditEntry;
import com.smartmove.audit.AuditLog;
import com.smartmove.domain.*;
import com.smartmove.domain.vehicle.*;
import com.smartmove.persistence.*;
import com.smartmove.policy.*;
import com.smartmove.telemetry.TelemetryMonitor;
import static com.smartmove.constants.SmartMoveConstants.*;
import com.smartmove.events.*;
import com.smartmove.handlers.*;
import com.smartmove.config.*;
import com.smartmove.audit.AuditEventType;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SmartMoveCentralController
 * <p>
 * The single entry point for all business logic in the SmartMove system.
 * Responsibilities:
 *   - Vehicle reservation, rental start/end with multi-city policy enforcement
 *   - State machine management with primitive locking (no frameworks)
 *   - Background telemetry monitoring with concurrent safety
 *   - High-integrity audit trail with checksum chaining and rollback support
 *   - File-based persistence (CSV) for all domain entities
 */
public class SmartMoveCentralController {
    private static final Logger logger = LoggerFactory.getLogger(SmartMoveCentralController.class);

    // ─── Repositories ─────────────────────────────────────────────────────
    private final VehicleRepository vehicleRepo;
    private final UserRepository userRepo;
    private final RentalRepository rentalRepo;
    private final PaymentRepository paymentRepo;

    // ─── Audit ────────────────────────────────────────────────────────────
    private final AuditLog auditLog;

    // ─── Telemetry ────────────────────────────────────────────────────────
    private final TelemetryMonitor telemetryMonitor;
    private final Thread telemetryThread;

    // ─── Concurrency: per-vehicle locks (primitive synchronization) ───────
    // ConcurrentHashMap guarantees thread-safe put/get; each value is a
    // dedicated Object used as a monitor via synchronized().
    private final ConcurrentHashMap<String, Object> vehicleLocks = new ConcurrentHashMap<>();

    // ─── ID generation ────────────────────────────────────────────────────
    private final AtomicLong rentalIdSeq  = new AtomicLong(RENTAL_ID_SEED);
    private final AtomicLong paymentIdSeq = new AtomicLong(PAYMENT_ID_SEED);

    // ─── Rollback snapshot: vehicleId → last known stable state ──────────
    private final ConcurrentHashMap<String, VehicleState> stateSnapshots = new ConcurrentHashMap<>();

    // Reference to last stable audit snapshot ID (used for rollback description)
    private volatile long lastStableSnapshotId = AUDIT_SEQ_SEED;

    public SmartMoveCentralController() {
        this.vehicleRepo  = new VehicleRepository();
        this.userRepo     = new UserRepository();
        this.rentalRepo   = new RentalRepository();
        this.paymentRepo  = new PaymentRepository();
        this.auditLog     = new AuditLog();

        VehicleStateManager stateManager = new VehicleStateManagerImpl();
        RentalTerminator rentalTerminator = new RentalTerminatorImpl(stateManager);

        registerEventHandlers(stateManager, rentalTerminator);

        this.telemetryMonitor = new TelemetryMonitor();
        this.telemetryThread  = new Thread(telemetryMonitor, "TelemetryMonitor");
        this.telemetryThread.setDaemon(true);
        this.telemetryThread.start();

        logger.info("[Controller] SmartMoveCentralController initialized.");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 1.  RESERVE VEHICLE
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Reserves a vehicle for a user. Validates user exists and the vehicle is available.
     */
    public Rental reserveVehicle(String userId, String vehicleId) throws SmartMoveException {
        Vehicle v = getVehicleOrThrow(vehicleId);
        User user = getUserOrThrow(userId);

        Object lock = getVehicleLock(vehicleId);
        synchronized (lock) {
            if (v.getState() != VehicleState.AVAILABLE) {
                throw new SmartMoveException("Vehicle " + vehicleId + " is not available (state: " + v.getState() + ")");
            }

            // Take a snapshot before state change
            stateSnapshots.put(vehicleId, v.getState());

            v.transitionTo(VehicleState.RESERVED);

            String rentalId = "R" + rentalIdSeq.incrementAndGet();
            Rental rental = new Rental(rentalId, userId, vehicleId, Instant.now().toString());

            try {
                rentalRepo.save(rental);
                vehicleRepo.save(v);
                writeAudit(AuditEventType.VEHICLE_RESERVED,
                        "vehicle=" + vehicleId + " user=" + userId + " rental=" + rentalId);
                lastStableSnapshotId = auditLog.getLastStableSnapshotId();
            } catch (Exception e) {
                // Rollback
                rollback(vehicleId, VehicleState.AVAILABLE, "reserve failed: " + e.getMessage());
                throw new SmartMoveException("Reserve transaction failed and was rolled back: " + e.getMessage());
            }

            logger.info(String.format("[Controller] Vehicle %s RESERVED by user %s (rental %s)%n",
                    vehicleId, user.getName(), rentalId));
            return rental;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2.  START RENTAL
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Starts an active rental. Applies city-specific pre-unlock checks
     * (e.g. Milan helmet check for mopeds).
     */
    public void startRental(String rentalId, String vehicleId) throws SmartMoveException {
        Vehicle v = getVehicleOrThrow(vehicleId);
        Rental rental = getRentalOrThrow(rentalId);

        Object lock = getVehicleLock(vehicleId);
        synchronized (lock) {
            if (v.getState() != VehicleState.RESERVED) {
                throw new SmartMoveException("Cannot start rental: vehicle is " + v.getState());
            }

            CityPolicy policy = PolicyFactory.getPolicy(v.getCity().getName());

            // Fetch latest telemetry for pre-unlock checks
            TelemetryData latestTelemetry = buildCurrentTelemetry(v);

            try {
                policy.beforeUnlock(v, latestTelemetry, rental);
                policy.validateTransition(v, VehicleState.IN_USE);
            } catch (PolicyViolationException e) {
                throw new SmartMoveException("Pre-unlock policy check failed: " + e.getMessage());
            }

            stateSnapshots.put(vehicleId, v.getState());
            v.transitionTo(VehicleState.IN_USE);

            try {
                vehicleRepo.save(v);
                writeAudit(AuditEventType.RENTAL_STARTED,
                        "vehicle=" + vehicleId + " rental=" + rentalId
                                + " city=" + v.getCity().getName());
                lastStableSnapshotId = auditLog.getLastStableSnapshotId();
            } catch (Exception e) {
                rollback(vehicleId, VehicleState.RESERVED, "startRental failed: " + e.getMessage());
                throw new SmartMoveException("Start rental rolled back: " + e.getMessage());
            }

            logger.info(String.format("[Controller] Rental %s STARTED for vehicle %s in %s%n",
                    rentalId, vehicleId, v.getCity().getName()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3.  END RENTAL
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Ends an active rental. Calculates the payment including city-specific
     * surcharges (e.g. London congestion charge).
     */
    public Payment endRental(String rentalId, String vehicleId) throws SmartMoveException {
        Vehicle v = getVehicleOrThrow(vehicleId);
        Rental rental = getRentalOrThrow(rentalId);

        if (!rental.isActive()) {
            throw new SmartMoveException("Rental " + rentalId + " is already ended.");
        }

        Object lock = getVehicleLock(vehicleId);
        synchronized (lock) {
            if (v.getState() != VehicleState.IN_USE) {
                throw new SmartMoveException("Vehicle is not IN_USE (state: " + v.getState() + ")");
            }

            stateSnapshots.put(vehicleId, v.getState());
            rental.end(Instant.now().toString());

            CityPolicy policy = PolicyFactory.getPolicy(v.getCity().getName());

            // Calculate base fare (simplified: €0.30/min, assume 20-minute trip)
            double baseAmount = BASE_RENTAL_AMOUNT;
            double surcharge = 0.0;
            String surchargeDesc = "";

            try {
                surcharge = policy.afterTrip(rental, baseAmount);
                if (surcharge > 0) {
                    surchargeDesc = v.getCity().getName() + " surcharge";
                }
            } catch (PolicyViolationException e) {
                logger.severe("[Controller] Warning: afterTrip policy error: %s".formatted(e.getMessage()));
            }

            String paymentId = "P" + paymentIdSeq.incrementAndGet();
            String desc = "Rental " + rentalId + " in " + v.getCity().getName()
                    + (surchargeDesc.isEmpty() ? "" : " + " + surchargeDesc);
            Payment payment = new Payment(paymentId, rentalId, baseAmount, surcharge, desc);

            v.transitionTo(VehicleState.AVAILABLE);

            try {
                rentalRepo.save(rental);
                paymentRepo.save(payment);
                vehicleRepo.save(v);
                writeAudit(AuditEventType.RENTAL_ENDED,
                        "vehicle=" + vehicleId + " rental=" + rentalId
                                + " total=" + String.format("%.2f", payment.getTotal()));
                writeAudit(AuditEventType.PAYMENT_PROCESSED,
                        "payment=" + paymentId + " rental=" + rentalId
                                + " base=" + String.format("%.2f", baseAmount)
                                + " surcharge=" + String.format("%.2f", surcharge)
                                + " total=" + String.format("%.2f", payment.getTotal()));
                lastStableSnapshotId = auditLog.getLastStableSnapshotId();
            } catch (Exception e) {
                rollback(vehicleId, VehicleState.IN_USE, "endRental failed: " + e.getMessage());
                throw new SmartMoveException("End rental rolled back: " + e.getMessage());
            }

            logger.info(String.format("[Controller] Rental %s ENDED. Total: €%.2f (base=%.2f + surcharge=%.2f)%n",
                    rentalId, payment.getTotal(), baseAmount, surcharge));
            return payment;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 4.  PROCESS TELEMETRY (external push)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Accepts a new telemetry data point for a vehicle and submits it
     * to the background monitor queue.
     */
    public void processTelemetry(String vehicleId, TelemetryData t) {
        vehicleRepo.findById(vehicleId).ifPresent(v -> {
            telemetryMonitor.submitTelemetry(v, t);
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // 5.  VALIDATE TRANSITION (public API)
    // ─────────────────────────────────────────────────────────────────────

    public boolean validateTransition(Vehicle v, VehicleState to) {
        try {
            CityPolicy policy = PolicyFactory.getPolicy(v.getCity().getName());
            policy.validateTransition(v, to);
            return v.isValidTransition(v.getState(), to);
        } catch (PolicyViolationException e) {
            logger.severe("[Controller] Transition validation failed: %s".formatted(e.getMessage()));
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 6.  MONITOR TELEMETRY STREAM (start/stop)
    // ─────────────────────────────────────────────────────────────────────

    public void monitorTelemetryStream() {
        if (!telemetryMonitor.isRunning()) {
            logger.info("[Controller] TelemetryMonitor already stopped or not started.");
        } else {
            logger.info("[Controller] TelemetryMonitor is running.");
        }
    }

    public void stopTelemetryMonitor() {
        telemetryMonitor.stop();
        logger.info("[Controller] TelemetryMonitor stop signal sent.");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 7.  ROLLBACK
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Manually rolls back the in-memory vehicle state to its last stable snapshot.
     * Called when persistence write fails, to keep in-memory state
     * consistent with the persisted audit log.
     */
    public void rollback(String lastStableSnapshotId) {
        logger.info("[Controller] ROLLBACK requested to snapshot: %s".formatted(lastStableSnapshotId));
        // Restore all vehicles to their last snapshotted state
        stateSnapshots.forEach((vehicleId, savedState) -> {
            vehicleRepo.findById(vehicleId).ifPresent(v -> {
                synchronized (getVehicleLock(vehicleId)) {
                    VehicleState current = v.getState();
                    if (current != savedState) {
                        // Force the state back (bypass normal transition validation for rollback)
                        forceVehicleState(v, savedState);
                        logger.info(String.format("[Controller] Rolled back vehicle %s: %s → %s%n",
                                vehicleId, current, savedState));
                    }
                }
            });
        });
        stateSnapshots.clear();
        logger.info("[Controller] Rollback complete.");
    }

    private void rollback(String vehicleId, VehicleState targetState, String reason) {
        logger.severe("[Controller] ROLLBACK: vehicle=%s".formatted(vehicleId
                + " → " + targetState + " reason: " + reason));
        vehicleRepo.findById(vehicleId).ifPresent(v -> forceVehicleState(v, targetState));
    }

    /**
     * Forces a vehicle state, bypassing transition validation.
     * ONLY to be used for rollback operations.
     */
    private void forceVehicleState(Vehicle v, VehicleState state) {
        // Use the vehicle's own lock for thread safety
        synchronized (v.getStateLock()) {
            // Direct field manipulation through subclass would break encapsulation;
            // instead, try all valid transitions to reach the target.
            // In a rollback, we attempt all possible paths.
            if (!v.transitionTo(state)) {
                // If direct transition fails, go through AVAILABLE as intermediary
                v.transitionTo(VehicleState.AVAILABLE);
                v.transitionTo(state);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 8.  EVENT HANDLERS
    // ─────────────────────────────────────────────────────────────────────

    private void registerEventHandlers(VehicleStateManager stateManager,
                                       RentalTerminator rentalTerminator) {
        EventBus bus = EventBus.getInstance();

        // Critical temperature handler
        bus.subscribe(CriticalTemperatureEvent.class, event -> {
            new CriticalTemperatureHandler(stateManager).handle(event.getVehicle());
        });

        // Critical battery handler
        bus.subscribe(CriticalBatteryEvent.class, event -> {
            new CriticalBatteryHandler(stateManager, rentalTerminator).handle(event.getVehicle());
        });

        // Theft alarm handler
        bus.subscribe(TheftAlarmEvent.class, event -> {
            new TheftAlarmHandler(stateManager).handle(event.getVehicle());
        });

        // Warning handlers
        bus.subscribe(HighTemperatureWarningEvent.class, event -> {
            new WarningEventHandler().handle(event.getVehicle());
        });

        bus.subscribe(LowBatteryWarningEvent.class, event -> {
            new WarningEventHandler().handle(event.getVehicle());
        });
    }


    private void triggerEmergencyLock(Vehicle v, String reason) {
        boolean transitioned = v.transitionTo(VehicleState.EMERGENCY_LOCK);
        if (transitioned) {
            vehicleRepo.save(v);
            writeAudit(AuditEventType.EMERGENCY_LOCK, "vehicle=" + v.getId() + " reason=" + reason);
            logger.severe(String.format("[Controller] EMERGENCY LOCK: vehicle=%s reason=%s%n", v.getId(), reason));
        }
    }

    private void sendToMaintenance(Vehicle v, String reason) {
        boolean transitioned = v.transitionTo(VehicleState.MAINTENANCE);
        if (transitioned) {
            vehicleRepo.save(v);
            writeAudit(AuditEventType.VEHICLE_MAINTENANCE, "vehicle=" + v.getId() + " reason=" + reason);
            logger.info(String.format("[Controller] Vehicle %s sent to MAINTENANCE: %s%n", v.getId(), reason));
        }
    }

    private void handleCriticalAlert(Vehicle v, String eventType, String message) {
        logger.severe(String.format("[Controller] ALERT [%s]: vehicle=%s — %s%n", eventType, v.getId(), message));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 9.  ZONE / GPS VALIDATION
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Validates whether a vehicle is allowed at the given GPS position
     * according to the city's policy.
     */
    public boolean checkGpsAllowed(String vehicleId, GeoCoordinate gps) {
        return vehicleRepo.findById(vehicleId).map(v -> {
            try {
                CityPolicy policy = PolicyFactory.getPolicy(v.getCity().getName());
                return policy.isAllowed(v, gps);
            } catch (PolicyViolationException e) {
                logger.severe("[Controller] GPS violation for %s".formatted(vehicleId + ": " + e.getMessage()));
                triggerEmergencyLock(v, "GPS restriction violation: " + e.getMessage());
                return false;
            }
        }).orElse(false);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 10. AUDIT HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private void writeAudit(AuditEventType eventType, String payload) {
        try {
            AuditEntry entry = auditLog.createEntry(eventType, payload);

            ExceptionHandler.handlePersistenceOperation(
                    "audit write " + eventType,
                    () -> {
                        auditLog.append(entry);
                        return null;
                    },
                    3  // max retries
            );
        } catch (Exception e) {
            rollback(String.valueOf(lastStableSnapshotId));
        }
    }

    public boolean verifyAuditChain() {
        return auditLog.verifyChain();
    }

    public void printAuditLog() {
        auditLog.printLog();
    }

    // ─────────────────────────────────────────────────────────────────────
    // 11. UTILITY
    // ─────────────────────────────────────────────────────────────────────

    private Vehicle getVehicleOrThrow(String vehicleId) throws SmartMoveException {
        return vehicleRepo.findById(vehicleId)
                .orElseThrow(() -> new SmartMoveException("Vehicle not found: " + vehicleId));
    }

    private User getUserOrThrow(String userId) throws SmartMoveException {
        return userRepo.findById(userId)
                .orElseThrow(() -> new SmartMoveException("User not found: " + userId));
    }

    private Rental getRentalOrThrow(String rentalId) throws SmartMoveException {
        return rentalRepo.findById(rentalId)
                .orElseThrow(() -> new SmartMoveException("Rental not found: " + rentalId));
    }

    private Object getVehicleLock(String vehicleId) {
        return vehicleLocks.computeIfAbsent(vehicleId, k -> new Object());
    }

    /**
     * Builds a TelemetryData snapshot from the vehicle's current state.
     * Used when we need to pass telemetry to policy checks.
     */
    private TelemetryData buildCurrentTelemetry(Vehicle v) {
        return new TelemetryData(
                Instant.now().toString(),
                v.getLocation(),
                v.getBatteryPercent(),
                v.getTemperatureC(),
                v instanceof Moped && ((Moped) v).isHelmetDetected()
        );
    }

    private class VehicleStateManagerImpl implements VehicleStateManager {
        @Override
        public void emergencyLock(Vehicle vehicle, String reason) {
            triggerEmergencyLock(vehicle, reason);
        }

        @Override
        public void sendToMaintenance(Vehicle vehicle, String reason) {
            SmartMoveCentralController.this.sendToMaintenance(vehicle, reason);
        }
    }

    private class RentalTerminatorImpl implements RentalTerminator {
        private final VehicleStateManager stateManager;

        public RentalTerminatorImpl(VehicleStateManager stateManager) {
            this.stateManager = stateManager;
        }

        @Override
        public void terminateEmergency(Vehicle vehicle, String reason) {
            Optional<Rental> activeRental = rentalRepo.findActiveByVehicleId(vehicle.getId());
            activeRental.ifPresent(r -> {
                try {
                    endRental(r.getId(), vehicle.getId());
                    writeAudit(AuditEventType.EMERGENCY_RENTAL_END,
                            "vehicle=" + vehicle.getId() + " reason=" + reason);
                } catch (SmartMoveException e) {
                    stateManager.emergencyLock(vehicle, "Emergency end failed: " + e.getMessage());
                }
            });
        }
    }

    // ─── Getters for testing / dashboard ─────────────────────────────────
    public VehicleRepository getVehicleRepo() { return vehicleRepo; }
    public UserRepository getUserRepo()       { return userRepo; }
    public RentalRepository getRentalRepo()   { return rentalRepo; }
    public PaymentRepository getPaymentRepo() { return paymentRepo; }
    public AuditLog getAuditLog()             { return auditLog; }
}
