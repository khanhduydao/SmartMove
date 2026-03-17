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

public class SmartMoveCentralController {
    private static final Logger logger = LoggerFactory.getLogger(SmartMoveCentralController.class);

    private final VehicleRepository vehicleRepo;
    private final UserRepository userRepo;
    private final RentalRepository rentalRepo;
    private final PaymentRepository paymentRepo;

    private final AuditLog auditLog;

    private final TelemetryMonitor telemetryMonitor;
    private final Thread telemetryThread;

    private final ConcurrentHashMap<String, Object> vehicleLocks = new ConcurrentHashMap<>();

    private final AtomicLong rentalIdSeq  = new AtomicLong(RENTAL_ID_SEED);
    private final AtomicLong paymentIdSeq = new AtomicLong(PAYMENT_ID_SEED);

    private final ConcurrentHashMap<String, VehicleState> stateSnapshots = new ConcurrentHashMap<>();

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

    public Rental reserveVehicle(String userId, String vehicleId) throws SmartMoveException {
        Vehicle v = getVehicleOrThrow(vehicleId);
        User user = getUserOrThrow(userId);

        Object lock = getVehicleLock(vehicleId);
        synchronized (lock) {
            if (v.getState() != VehicleState.AVAILABLE) {
                throw new SmartMoveException("Vehicle " + vehicleId + " is not available (state: " + v.getState() + ")");
            }

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
                rollback(vehicleId, VehicleState.AVAILABLE, "reserve failed: " + e.getMessage());
                throw new SmartMoveException("Reserve transaction failed and was rolled back: " + e.getMessage());
            }

            logger.info(() -> "[Controller] Vehicle " + vehicleId + " RESERVED by user "
                    + user.getName() + " (rental " + rentalId + ")");
            return rental;
        }
    }

    public void startRental(String rentalId, String vehicleId) throws SmartMoveException {
        Vehicle v = getVehicleOrThrow(vehicleId);
        Rental rental = getRentalOrThrow(rentalId);

        Object lock = getVehicleLock(vehicleId);
        synchronized (lock) {
            if (v.getState() != VehicleState.RESERVED) {
                throw new SmartMoveException("Cannot start rental: vehicle is " + v.getState());
            }

            CityPolicy policy = PolicyFactory.getPolicy(v.getCity().getName());
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

            logger.info(() -> "[Controller] Rental " + rentalId + " STARTED for vehicle "
                    + vehicleId + " in " + v.getCity().getName());
        }
    }

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

            double baseAmount = BASE_RENTAL_AMOUNT;
            double surcharge = 0.0;
            String surchargeDesc = "";

            try {
                surcharge = policy.afterTrip(rental, baseAmount);
                if (surcharge > 0) {
                    surchargeDesc = v.getCity().getName() + " surcharge";
                }
            } catch (PolicyViolationException e) {
                logger.severe(() -> "[Controller] Warning: afterTrip policy error: " + e.getMessage());
            }

            String paymentId = "P" + paymentIdSeq.incrementAndGet();
            String desc = "Rental " + rentalId + " in " + v.getCity().getName()
                    + (surchargeDesc.isEmpty() ? "" : " + " + surchargeDesc);
            Payment payment = new Payment(paymentId, rentalId, baseAmount, surcharge, desc);

            v.transitionTo(VehicleState.AVAILABLE);

            final double finalSurcharge = surcharge;
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
                                + " surcharge=" + String.format("%.2f", finalSurcharge)
                                + " total=" + String.format("%.2f", payment.getTotal()));
                lastStableSnapshotId = auditLog.getLastStableSnapshotId();
            } catch (Exception e) {
                rollback(vehicleId, VehicleState.IN_USE, "endRental failed: " + e.getMessage());
                throw new SmartMoveException("End rental rolled back: " + e.getMessage());
            }

            logger.info(() -> String.format("[Controller] Rental %s ENDED. Total: €%.2f (base=%.2f + surcharge=%.2f)",
                    rentalId, payment.getTotal(), baseAmount, finalSurcharge));
            return payment;
        }
    }

    public void processTelemetry(String vehicleId, TelemetryData t) {
        vehicleRepo.findById(vehicleId).ifPresent(v -> {
            telemetryMonitor.submitTelemetry(v, t);
        });
    }

    public boolean validateTransition(Vehicle v, VehicleState to) {
        try {
            CityPolicy policy = PolicyFactory.getPolicy(v.getCity().getName());
            policy.validateTransition(v, to);
            return v.isValidTransition(v.getState(), to);
        } catch (PolicyViolationException e) {
            logger.severe(() -> "[Controller] Transition validation failed: " + e.getMessage());
            return false;
        }
    }

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

    public void rollback(String lastStableSnapshotId) {
        logger.info(() -> "[Controller] ROLLBACK requested to snapshot: " + lastStableSnapshotId);
        stateSnapshots.forEach((vehicleId, savedState) -> {
            vehicleRepo.findById(vehicleId).ifPresent(v -> {
                synchronized (getVehicleLock(vehicleId)) {
                    VehicleState current = v.getState();
                    if (current != savedState) {
                        forceVehicleState(v, savedState);
                        logger.info(() -> "[Controller] Rolled back vehicle " + vehicleId
                                + ": " + current + " → " + savedState);
                    }
                }
            });
        });
        stateSnapshots.clear();
        logger.info("[Controller] Rollback complete.");
    }

    private void rollback(String vehicleId, VehicleState targetState, String reason) {
        logger.severe(() -> "[Controller] ROLLBACK: vehicle=" + vehicleId
                + " → " + targetState + " reason: " + reason);
        vehicleRepo.findById(vehicleId).ifPresent(v -> forceVehicleState(v, targetState));
    }

    private void forceVehicleState(Vehicle v, VehicleState state) {
        synchronized (v.getStateLock()) {
            if (!v.transitionTo(state)) {
                v.transitionTo(VehicleState.AVAILABLE);
                v.transitionTo(state);
            }
        }
    }

    private void registerEventHandlers(VehicleStateManager stateManager,
                                       RentalTerminator rentalTerminator) {
        EventBus bus = EventBus.getInstance();

        bus.subscribe(CriticalTemperatureEvent.class, event -> {
            new CriticalTemperatureHandler(stateManager).handle(event.getVehicle());
        });

        bus.subscribe(CriticalBatteryEvent.class, event -> {
            new CriticalBatteryHandler(stateManager, rentalTerminator).handle(event.getVehicle());
        });

        bus.subscribe(TheftAlarmEvent.class, event -> {
            new TheftAlarmHandler(stateManager).handle(event.getVehicle());
        });

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
            logger.severe(() -> "[Controller] EMERGENCY LOCK: vehicle=" + v.getId() + " reason=" + reason);
        }
    }

    private void sendToMaintenance(Vehicle v, String reason) {
        boolean transitioned = v.transitionTo(VehicleState.MAINTENANCE);
        if (transitioned) {
            vehicleRepo.save(v);
            writeAudit(AuditEventType.VEHICLE_MAINTENANCE, "vehicle=" + v.getId() + " reason=" + reason);
            logger.info(() -> "[Controller] Vehicle " + v.getId() + " sent to MAINTENANCE: " + reason);
        }
    }

    private void handleCriticalAlert(Vehicle v, String eventType, String message) {
        logger.severe(() -> "[Controller] ALERT [" + eventType + "]: vehicle=" + v.getId() + " — " + message);
    }

    public boolean checkGpsAllowed(String vehicleId, GeoCoordinate gps) {
        return vehicleRepo.findById(vehicleId).map(v -> {
            try {
                CityPolicy policy = PolicyFactory.getPolicy(v.getCity().getName());
                return policy.isAllowed(v, gps);
            } catch (PolicyViolationException e) {
                logger.severe(() -> "[Controller] GPS violation for " + vehicleId + ": " + e.getMessage());
                triggerEmergencyLock(v, "GPS restriction violation: " + e.getMessage());
                return false;
            }
        }).orElse(false);
    }

    private void writeAudit(AuditEventType eventType, String payload) {
        try {
            AuditEntry entry = auditLog.createEntry(eventType, payload);
            ExceptionHandler.handlePersistenceOperation(
                    "audit write " + eventType,
                    () -> {
                        auditLog.append(entry);
                        return null;
                    },
                    3
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

    public VehicleRepository getVehicleRepo() { return vehicleRepo; }
    public UserRepository getUserRepo()       { return userRepo; }
    public RentalRepository getRentalRepo()   { return rentalRepo; }
    public PaymentRepository getPaymentRepo() { return paymentRepo; }
    public AuditLog getAuditLog()             { return auditLog; }
}
