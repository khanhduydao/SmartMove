package com.smartmove.controller;

import com.smartmove.audit.AuditEntry;
import com.smartmove.audit.AuditLog;
import com.smartmove.audit.AuditWriteException;
import com.smartmove.domain.*;
import com.smartmove.domain.vehicle.*;
import com.smartmove.persistence.*;
import com.smartmove.policy.*;
import com.smartmove.telemetry.TelemetryMonitor;
import com.smartmove.telemetry.TelemetryMonitor.TelemetryEvent;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SmartMoveCentralController
 *
 * The single entry point for all business logic in the SmartMove system.
 * Responsibilities:
 *   - Vehicle reservation, rental start/end with multi-city policy enforcement
 *   - State machine management with primitive locking (no frameworks)
 *   - Background telemetry monitoring with concurrent safety
 *   - High-integrity audit trail with checksum chaining and rollback support
 *   - File-based persistence (CSV) for all domain entities
 */
public class SmartMoveCentralController {

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
    private final AtomicLong rentalIdSeq  = new AtomicLong(1000);
    private final AtomicLong paymentIdSeq = new AtomicLong(1000);

    // ─── Rollback snapshot: vehicleId → last known stable state ──────────
    private final ConcurrentHashMap<String, VehicleState> stateSnapshots = new ConcurrentHashMap<>();

    // Reference to last stable audit snapshot ID (used for rollback description)
    private volatile long lastStableSnapshotId = 0L;

    public SmartMoveCentralController() {
        this.vehicleRepo  = new VehicleRepository();
        this.userRepo     = new UserRepository();
        this.rentalRepo   = new RentalRepository();
        this.paymentRepo  = new PaymentRepository();
        this.auditLog     = new AuditLog();

        // Initialize per-vehicle locks for all loaded vehicles
        vehicleRepo.getAll().keySet().forEach(id -> vehicleLocks.put(id, new Object()));

        // Set up telemetry monitor with callback to this controller
        this.telemetryMonitor = new TelemetryMonitor(this::handleTelemetryEvent);
        this.telemetryThread  = new Thread(telemetryMonitor, "TelemetryMonitor");
        this.telemetryThread.setDaemon(true);
        this.telemetryThread.start();

        System.out.println("[Controller] SmartMoveCentralController initialized.");
        System.out.println("[Controller] Loaded " + vehicleRepo.getAll().size() + " vehicles.");
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

            // Take snapshot before state change
            stateSnapshots.put(vehicleId, v.getState());

            v.transitionTo(VehicleState.RESERVED);

            String rentalId = "R" + rentalIdSeq.incrementAndGet();
            Rental rental = new Rental(rentalId, userId, vehicleId, Instant.now().toString());

            try {
                rentalRepo.save(rental);
                vehicleRepo.save(v);
                writeAudit("VEHICLE_RESERVED",
                        "vehicle=" + vehicleId + " user=" + userId + " rental=" + rentalId);
                lastStableSnapshotId = auditLog.getLastStableSnapshotId();
            } catch (Exception e) {
                // Rollback
                rollback(vehicleId, VehicleState.AVAILABLE, "reserve failed: " + e.getMessage());
                throw new SmartMoveException("Reserve transaction failed and was rolled back: " + e.getMessage());
            }

            System.out.printf("[Controller] Vehicle %s RESERVED by user %s (rental %s)%n",
                    vehicleId, user.getName(), rentalId);
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
                writeAudit("RENTAL_STARTED",
                        "vehicle=" + vehicleId + " rental=" + rentalId
                                + " city=" + v.getCity().getName());
                lastStableSnapshotId = auditLog.getLastStableSnapshotId();
            } catch (Exception e) {
                rollback(vehicleId, VehicleState.RESERVED, "startRental failed: " + e.getMessage());
                throw new SmartMoveException("Start rental rolled back: " + e.getMessage());
            }

            System.out.printf("[Controller] Rental %s STARTED for vehicle %s in %s%n",
                    rentalId, vehicleId, v.getCity().getName());
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
            double baseAmount = 6.00;
            double surcharge = 0.0;
            String surchargeDesc = "";

            try {
                surcharge = policy.afterTrip(rental, baseAmount);
                if (surcharge > 0) {
                    surchargeDesc = v.getCity().getName() + " surcharge";
                }
            } catch (PolicyViolationException e) {
                System.err.println("[Controller] Warning: afterTrip policy error: " + e.getMessage());
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
                writeAudit("RENTAL_ENDED",
                        "vehicle=" + vehicleId + " rental=" + rentalId
                                + " total=" + String.format("%.2f", payment.getTotal()));
                writeAudit("PAYMENT_PROCESSED",
                        "payment=" + paymentId + " rental=" + rentalId
                                + " base=" + String.format("%.2f", baseAmount)
                                + " surcharge=" + String.format("%.2f", surcharge)
                                + " total=" + String.format("%.2f", payment.getTotal()));
                lastStableSnapshotId = auditLog.getLastStableSnapshotId();
            } catch (Exception e) {
                rollback(vehicleId, VehicleState.IN_USE, "endRental failed: " + e.getMessage());
                throw new SmartMoveException("End rental rolled back: " + e.getMessage());
            }

            System.out.printf("[Controller] Rental %s ENDED. Total: €%.2f (base=%.2f + surcharge=%.2f)%n",
                    rentalId, payment.getTotal(), baseAmount, surcharge);
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
    public void processTelemetry(String vehicleId, String timestamp, TelemetryData t) {
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
            System.err.println("[Controller] Transition validation failed: " + e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 6.  MONITOR TELEMETRY STREAM (start/stop)
    // ─────────────────────────────────────────────────────────────────────

    public void monitorTelemetryStream() {
        if (!telemetryMonitor.isRunning()) {
            System.out.println("[Controller] TelemetryMonitor already stopped or not started.");
        } else {
            System.out.println("[Controller] TelemetryMonitor is running.");
        }
    }

    public void stopTelemetryMonitor() {
        telemetryMonitor.stop();
        System.out.println("[Controller] TelemetryMonitor stop signal sent.");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 7.  ROLLBACK
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Manually rolls back the in-memory vehicle state to its last stable snapshot.
     * Called when a persistence write fails, to keep in-memory state
     * consistent with the persisted audit log.
     */
    public void rollback(String lastStableSnapshotId) {
        System.out.println("[Controller] ROLLBACK requested to snapshot: " + lastStableSnapshotId);
        // Restore all vehicles to their last snapshotted state
        stateSnapshots.forEach((vehicleId, savedState) -> {
            vehicleRepo.findById(vehicleId).ifPresent(v -> {
                synchronized (getVehicleLock(vehicleId)) {
                    VehicleState current = v.getState();
                    if (current != savedState) {
                        // Force the state back (bypass normal transition validation for rollback)
                        forceVehicleState(v, savedState);
                        System.out.printf("[Controller] Rolled back vehicle %s: %s → %s%n",
                                vehicleId, current, savedState);
                    }
                }
            });
        });
        stateSnapshots.clear();
        System.out.println("[Controller] Rollback complete.");
    }

    private void rollback(String vehicleId, VehicleState targetState, String reason) {
        System.err.println("[Controller] ROLLBACK: vehicle=" + vehicleId
                + " → " + targetState + " reason: " + reason);
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
    // 8.  TELEMETRY EVENT HANDLER (callback from TelemetryMonitor)
    // ─────────────────────────────────────────────────────────────────────

    private void handleTelemetryEvent(Vehicle v, TelemetryEvent event) {
        String vehicleId = v.getId();
        Object lock = getVehicleLock(vehicleId);

        synchronized (lock) {
            switch (event) {
                case CRITICAL_TEMPERATURE:
                    handleCriticalAlert(v, "CRITICAL_TEMP",
                            "Temperature exceeded 60°C — emergency lock triggered");
                    triggerEmergencyLock(v, "Critical temperature: " + v.getTemperatureC() + "°C");
                    break;

                case HIGH_TEMPERATURE_WARNING:
                    System.out.printf("[Controller] High temp warning for %s (%.1f°C) — throttling speed%n",
                            vehicleId, v.getTemperatureC());
                    writeAudit("VEHICLE_THROTTLED", "vehicle=" + vehicleId
                            + " temp=" + v.getTemperatureC() + "C");
                    break;

                case CRITICAL_BATTERY:
                    if (v.getState() == VehicleState.IN_USE) {
                        System.out.println("[Controller] Critical battery on " + vehicleId
                                + " — initiating emergency rental termination");
                        // Find and auto-terminate the active rental
                        Optional<Rental> activeRental = rentalRepo.findActiveByVehicleId(vehicleId);
                        activeRental.ifPresent(r -> {
                            try {
                                endRental(r.getId(), vehicleId);
                                writeAudit("EMERGENCY_RENTAL_END",
                                        "vehicle=" + vehicleId + " reason=critical_battery");
                            } catch (SmartMoveException e) {
                                System.err.println("[Controller] Emergency end failed: " + e.getMessage());
                                triggerEmergencyLock(v, "Critical battery, emergency end failed");
                            }
                        });
                    } else {
                        sendToMaintenance(v, "Critical battery: " + v.getBatteryPercent() + "%");
                    }
                    break;

                case LOW_BATTERY_WARNING:
                    writeAudit("LOW_BATTERY_WARNING", "vehicle=" + vehicleId
                            + " battery=" + v.getBatteryPercent() + "%");
                    break;

                case THEFT_ALARM:
                    handleCriticalAlert(v, "THEFT_ALARM",
                            "Vehicle moved without active rental — emergency lock triggered");
                    triggerEmergencyLock(v, "Theft alarm: movement without rental");
                    break;
            }
        }
    }

    private void triggerEmergencyLock(Vehicle v, String reason) {
        boolean transitioned = v.transitionTo(VehicleState.EMERGENCY_LOCK);
        if (transitioned) {
            vehicleRepo.save(v);
            writeAudit("EMERGENCY_LOCK", "vehicle=" + v.getId() + " reason=" + reason);
            System.err.printf("[Controller] EMERGENCY LOCK: vehicle=%s reason=%s%n", v.getId(), reason);
        }
    }

    private void sendToMaintenance(Vehicle v, String reason) {
        boolean transitioned = v.transitionTo(VehicleState.MAINTENANCE);
        if (transitioned) {
            vehicleRepo.save(v);
            writeAudit("VEHICLE_MAINTENANCE", "vehicle=" + v.getId() + " reason=" + reason);
            System.out.printf("[Controller] Vehicle %s sent to MAINTENANCE: %s%n", v.getId(), reason);
        }
    }

    private void handleCriticalAlert(Vehicle v, String eventType, String message) {
        System.err.printf("[Controller] ALERT [%s]: vehicle=%s — %s%n", eventType, v.getId(), message);
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
                System.err.println("[Controller] GPS violation for " + vehicleId + ": " + e.getMessage());
                triggerEmergencyLock(v, "GPS restriction violation: " + e.getMessage());
                return false;
            }
        }).orElse(false);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 10. AUDIT HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private void writeAudit(String eventType, String payload) {
        try {
            AuditEntry entry = auditLog.createEntry(eventType, payload);
            auditLog.append(entry);
        } catch (AuditWriteException e) {
            System.err.println("[Controller] AUDIT WRITE FAILED: " + e.getMessage());
            // Critical: audit write failure triggers rollback
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

    // ─── Getters for testing / dashboard ─────────────────────────────────
    public VehicleRepository getVehicleRepo() { return vehicleRepo; }
    public UserRepository getUserRepo()       { return userRepo; }
    public RentalRepository getRentalRepo()   { return rentalRepo; }
    public PaymentRepository getPaymentRepo() { return paymentRepo; }
    public AuditLog getAuditLog()             { return auditLog; }
}
