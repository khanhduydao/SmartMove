package com.smartmove;

import com.smartmove.config.LoggerFactory;
import java.util.logging.Logger;
import com.smartmove.controller.SmartMoveException;
import com.smartmove.controller.SmartMoveCentralController;
import com.smartmove.domain.*;
import com.smartmove.domain.vehicle.*;
import com.smartmove.util.DataSeeder;

import java.time.Instant;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final String ARROW = "  → ";

    public static void main(String[] args) {
        printBanner();

        SmartMoveCentralController controller = new SmartMoveCentralController();
        DataSeeder.seed(controller.getVehicleRepo(), controller.getUserRepo());

        pause(300);

        logger.info(() -> "\n" + section("LONDON SCENARIO — Congestion Charge"));
        londonScenario(controller);

        pause(500);

        logger.info(() -> "\n" + section("MILAN SCENARIO — Helmet Check for Moped"));
        milanScenario(controller);

        pause(500);

        logger.info(() -> "\n" + section("ROME SCENARIO – Zone Restrictions for Scooter"));
        romeScenario(controller);

        pause(500);

        logger.info(() -> "\n" + section("TELEMETRY — Critical Temperature & Theft Alarm"));
        telemetryScenario(controller);

        pause(1500);

        logger.info(() -> "\n" + section("CONCURRENCY — Simultaneous Reservation Attempts"));
        concurrencyScenario(controller);

        pause(500);

        logger.info(() -> "\n" + section("AUDIT TRAIL — Chain Integrity Verification"));
        controller.printAuditLog();
        boolean valid = controller.verifyAuditChain();
        logger.info(() -> "Audit chain valid: " + valid);

        logger.info(() -> "\n" + section("FLEET STATUS SUMMARY"));
        printFleetSummary(controller);

        controller.stopTelemetryMonitor();
        logger.info("\n[Main] SmartMove engine shutdown complete.");
    }

    private static void londonScenario(SmartMoveCentralController c) {
        try {
            logger.info("User U001 (Alice) reserves electric scooter LON-ES001 in London");
            Rental rental = c.reserveVehicle("U001", "LON-ES001");
            logger.info(() -> ARROW + rental);

            logger.info("Alice starts her rental...");
            c.startRental(rental.getId(), "LON-ES001");

            logger.info("Alice ends her trip (congestion charge will be added)...");
            Payment payment = c.endRental(rental.getId(), "LON-ES001");
            logger.info(() -> ARROW + payment);

        } catch (SmartMoveException e) {
            logger.severe(() -> "London scenario error: " + e.getMessage());
        }
    }

    private static void milanScenario(SmartMoveCentralController c) {
        try {
            logger.info("User U003 (Carlos) reserves Moped MIL-M001 in Milan");
            Rental rental = c.reserveVehicle("U003", "MIL-M001");
            logger.info(() -> ARROW + rental);

            logger.info("Carlos tries to start rental WITHOUT helmet...");
            try {
                c.startRental(rental.getId(), "MIL-M001");
                logger.info(ARROW + "ERROR: Should have been rejected!");
            } catch (SmartMoveException e) {
                logger.info(() -> ARROW + "CORRECTLY REJECTED: " + e.getMessage());
            }

            logger.info("Carlos puts on helmet (sensor confirms)...");
            Vehicle moped = c.getVehicleRepo().findById("MIL-M001").orElseThrow();
            if (moped instanceof Moped) {
                ((Moped) moped).setHelmetDetected(true);
            }

            logger.info("Carlos tries again WITH helmet...");
            c.startRental(rental.getId(), "MIL-M001");
            logger.info(ARROW + "Rental started successfully!");

            Payment payment = c.endRental(rental.getId(), "MIL-M001");
            logger.info(() -> ARROW + payment);

        } catch (SmartMoveException e) {
            logger.severe(() -> "Milan scenario error: " + e.getMessage());
        }
    }

    private static void romeScenario(SmartMoveCentralController c) {
        try {
            logger.info("User U002 (Bob) reserves Scooter ROM-ES002 in Rome");
            Rental rental = c.reserveVehicle("U002", "ROM-ES002");
            c.startRental(rental.getId(), "ROM-ES002");
            logger.info(ARROW + "Bob is riding scooter ROM-ES002 in Rome...");

            logger.info("Checking: scooter heading toward Colosseum area (restricted zone)...");
            GeoCoordinate colosseumArea = new GeoCoordinate(41.8902, 12.4922);
            boolean allowed = c.checkGpsAllowed("ROM-ES002", colosseumArea);
            logger.info(() -> ARROW + "GPS check result: " + (allowed ? "ALLOWED" : "BLOCKED — emergency lock applied"));

            Vehicle scooter = c.getVehicleRepo().findById("ROM-ES002").orElseThrow();
            logger.info(() -> ARROW + "Scooter state after GPS check: " + scooter.getState());

        } catch (SmartMoveException e) {
            logger.severe(() -> "Rome scenario error: " + e.getMessage());
        }
    }

    private static void telemetryScenario(SmartMoveCentralController c) {
        logger.info("Simulating critical overheating on LON-ES002...");
        TelemetryData criticalTemp = new TelemetryData(
                Instant.now().toString(),
                new GeoCoordinate(51.5100, -0.1200),
                65, 75.0, false
        );
        c.processTelemetry("LON-ES002", criticalTemp);

        logger.info("Simulating critically low battery on ROM-B001...");
        TelemetryData lowBattery = new TelemetryData(
                Instant.now().toString(),
                new GeoCoordinate(41.9028, 12.4964),
                3, 25.0, false
        );
        c.processTelemetry("ROM-B001", lowBattery);

        logger.info("Simulating theft alarm on MIL-B001 (moved 50m without rental)...");
        TelemetryData telemetry1 = new TelemetryData(
                Instant.now().toString(),
                new GeoCoordinate(45.4700, 9.1950),
                90, 22.0, false
        );
        c.processTelemetry("MIL-B001", telemetry1);
    }

    private static void concurrencyScenario(SmartMoveCentralController c) {
        String vehicleId = "LON-B002";

        c.getVehicleRepo().findById(vehicleId).ifPresent(v -> {
            if (v.getState() != VehicleState.AVAILABLE) {
                logger.info(() -> "  Note: " + vehicleId + " is " + v.getState() + ", resetting for test");
                v.transitionTo(VehicleState.AVAILABLE);
            }
        });

        logger.info(() -> "Two threads simultaneously trying to reserve " + vehicleId + "...");

        Thread t1 = new Thread(() -> {
            try {
                Rental r = c.reserveVehicle("U004", vehicleId);
                logger.info(() -> "  [Thread-1] SUCCESS: Elena reserved " + vehicleId + " → " + r.getId());
            } catch (SmartMoveException e) {
                logger.info(() -> "  [Thread-1] REJECTED: " + e.getMessage());
            }
        }, "Thread-1");

        Thread t2 = new Thread(() -> {
            try {
                Rental r = c.reserveVehicle("U005", vehicleId);
                logger.info(() -> "  [Thread-2] SUCCESS: James reserved " + vehicleId + " → " + r.getId());
            } catch (SmartMoveException e) {
                logger.info(() -> "  [Thread-2] REJECTED: " + e.getMessage());
            }
        }, "Thread-2");

        t1.start();
        t2.start();

        try {
            t1.join(2000);
            t2.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        c.getVehicleRepo().findById(vehicleId).ifPresent(v ->
                logger.info(() -> "  Final state of " + vehicleId + ": " + v.getState() + " (only one reservation should have succeeded)"));
    }

    private static void printFleetSummary(SmartMoveCentralController c) {
        logger.info("Vehicle ID       | Type             | City   | State          | Bat% | Temp°C");
        logger.info("-----------------|------------------|--------|----------------|------|-------");
        c.getVehicleRepo().getAll().values().stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .forEach(v -> logger.info(() -> String.format("%-17s| %-17s| %-7s| %-15s| %4d | %.1f%n",
                        v.getId(), v.getType(), v.getCity().getName(),
                        v.getState(), v.getBatteryPercent(), v.getTemperatureC())));
    }

    private static void printBanner() {
        logger.info("╔══════════════════════════════════════════════════════════╗");
        logger.info("║         SmartMove Core Engine — v1.0                     ║");
        logger.info("║  Urban Mobility Platform  |  London · Milan · Rome       ║");
        logger.info("╚══════════════════════════════════════════════════════════╝");
    }

    private static String section(String title) {
        return "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                + "  " + title + "\n"
                + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
    }

    private static void pause(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
