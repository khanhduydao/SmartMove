package com.smartmove;

import com.smartmove.config.LoggerFactory;
import java.util.logging.Logger;
import com.smartmove.controller.SmartMoveException;
import com.smartmove.controller.SmartMoveCentralController;
import com.smartmove.domain.*;
import com.smartmove.domain.vehicle.*;
import com.smartmove.util.DataSeeder;

import java.time.Instant;

/**
 * SmartMove Core Engine — Main Demonstration
 * Demonstrates:
 *   1. Multi-city vehicle reservation and rental (London, Milan, Rome)
 *   2. City-specific policy enforcement (congestion charge, helmet check, zone restrictions)
 *   3. State machine transitions with validation
 *   4. Real-time telemetry processing (critical temp, low battery, theft alarm)
 *   5. Concurrent rental safety with primitive locking
 *   6. Audit trail with checksum chain verification
 *   7. Rollback on failure
 *   8. File-based persistence (CSV)
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        printBanner();

        // ─── 1. Seed initial data ──────────────────────────────────────────
        SmartMoveCentralController controller = new SmartMoveCentralController();
        DataSeeder.seed(controller.getVehicleRepo(), controller.getUserRepo());

        pause(300);

        // ─── 2. London Scenario ────────────────────────────────────────────
        logger.info("\n%s".formatted(section("LONDON SCENARIO — Congestion Charge")));
        londonScenario(controller);

        pause(500);

        // ─── 3. Milan Scenario ─────────────────────────────────────────────
        logger.info("\n%s".formatted(section("MILAN SCENARIO — Helmet Check for Moped")));
        milanScenario(controller);

        pause(500);

        // ─── 4. Rome Scenario ──────────────────────────────────────────────
        logger.info("\n%s".formatted(section("ROME SCENARIO — Zone Restrictions for Scooter")));
        romeScenario(controller);

        pause(500);

        // ─── 5. Telemetry Scenarios ────────────────────────────────────────
        logger.info("\n%s".formatted(section("TELEMETRY — Critical Temperature & Theft Alarm")));
        telemetryScenario(controller);

        pause(1500); // Give background telemetry thread time to process

        // ─── 6. Concurrency Test ───────────────────────────────────────────
        logger.info("\n%s".formatted(section("CONCURRENCY — Simultaneous Reservation Attempts")));
        concurrencyScenario(controller);

        pause(500);

        // ─── 7. Audit Verification ─────────────────────────────────────────
        logger.info("\n%s".formatted(section("AUDIT TRAIL — Chain Integrity Verification")));
        controller.printAuditLog();
        boolean valid = controller.verifyAuditChain();
        logger.info("Audit chain valid: %s".formatted(valid));

        // ─── 8. State Summary ──────────────────────────────────────────────
        logger.info("\n%s".formatted(section("FLEET STATUS SUMMARY")));
        printFleetSummary(controller);

        // ─── Shutdown ──────────────────────────────────────────────────────
        controller.stopTelemetryMonitor();
        logger.info("\n[Main] SmartMove engine shutdown complete.");
    }

    // ─── LONDON: Reserve scooter → start → end (congestion charge applied) ───

    private static void londonScenario(SmartMoveCentralController c) {
        try {
            logger.info("User U001 (Alice) reserves electric scooter LON-ES001 in London");
            Rental rental = c.reserveVehicle("U001", "LON-ES001");
            logger.info("  → %s".formatted(rental));

            logger.info("Alice starts her rental...");
            c.startRental(rental.getId(), "LON-ES001");

            logger.info("Alice ends her trip (congestion charge will be added)...");
            Payment payment = c.endRental(rental.getId(), "LON-ES001");
            logger.info("  → %s".formatted(payment));

        } catch (SmartMoveException e) {
            logger.severe("London scenario error: %s".formatted(e.getMessage()));
        }
    }

    // ─── MILAN: Moped unlock without a helmet (should fail), then with helmet ───

    private static void milanScenario(SmartMoveCentralController c) {
        try {
            logger.info("User U003 (Carlos) reserves Moped MIL-M001 in Milan");
            Rental rental = c.reserveVehicle("U003", "MIL-M001");
            logger.info("  → %s".formatted(rental));

            logger.info("Carlos tries to start rental WITHOUT helmet...");
            try {
                c.startRental(rental.getId(), "MIL-M001");
                logger.info("  → ERROR: Should have been rejected!");
            } catch (SmartMoveException e) {
                logger.info("  → CORRECTLY REJECTED: %s".formatted(e.getMessage()));
            }

            logger.info("Carlos puts on helmet (sensor confirms)...");
            Vehicle moped = c.getVehicleRepo().findById("MIL-M001").orElseThrow();
            if (moped instanceof Moped) {
                ((Moped) moped).setHelmetDetected(true);
            }

            logger.info("Carlos tries again WITH helmet...");
            c.startRental(rental.getId(), "MIL-M001");
            logger.info("  → Rental started successfully!");

            Payment payment = c.endRental(rental.getId(), "MIL-M001");
            logger.info("  → %s".formatted(payment));

        } catch (SmartMoveException e) {
            logger.severe("Milan scenario error: %s".formatted(e.getMessage()));
        }
    }

    // ─── ROME: Scooter tries to enter archaeological zone (should trigger lock) ─

    private static void romeScenario(SmartMoveCentralController c) {
        try {
            logger.info("User U002 (Bob) reserves Scooter ROM-ES002 in Rome");
            Rental rental = c.reserveVehicle("U002", "ROM-ES002");
            c.startRental(rental.getId(), "ROM-ES002");
            logger.info("  → Bob is riding scooter ROM-ES002 in Rome...");

            logger.info("Checking: scooter heading toward Colosseum area (restricted zone)...");
            GeoCoordinate colosseumArea = new GeoCoordinate(41.8902, 12.4922);
            boolean allowed = c.checkGpsAllowed("ROM-ES002", colosseumArea);
            logger.info("  → GPS check result: %s".formatted((allowed ? "ALLOWED" : "BLOCKED — emergency lock applied")));

            // Show the vehicle was locked
            Vehicle scooter = c.getVehicleRepo().findById("ROM-ES002").orElseThrow();
            logger.info("  → Scooter state after GPS check: %s".formatted(scooter.getState()));

        } catch (SmartMoveException e) {
            logger.severe("Rome scenario error: %s".formatted(e.getMessage()));
        }
    }

    // ─── TELEMETRY: Critical temperature & theft alarm ────────────────────────

    private static void telemetryScenario(SmartMoveCentralController c) {
        // Critical temperature during active rental
        logger.info("Simulating critical overheating on LON-ES002...");
        TelemetryData criticalTemp = new TelemetryData(
                Instant.now().toString(),
                new GeoCoordinate(51.5100, -0.1200),
                65, 75.0, false  // 75°C — WAY over the 60°C threshold
        );
        c.processTelemetry("LON-ES002", criticalTemp);

        // Low battery warning
        logger.info("Simulating critically low battery on ROM-B001...");
        TelemetryData lowBattery = new TelemetryData(
                Instant.now().toString(),
                new GeoCoordinate(41.9028, 12.4964),
                3, 25.0, false  // 3% battery — below the critical 5% threshold
        );
        c.processTelemetry("ROM-B001", lowBattery);

        // Theft alarm: vehicle moves without rental
        logger.info("Simulating theft alarm on MIL-B001 (moved 50m without rental)...");
        // First update: set current location
        TelemetryData telemetry1 = new TelemetryData(
                Instant.now().toString(),
                new GeoCoordinate(45.4700, 9.1950),  // moved significantly
                90, 22.0, false
        );
        c.processTelemetry("MIL-B001", telemetry1);
    }

    // ─── CONCURRENCY: Two users try to reserve the same vehicle at once ───────

    private static void concurrencyScenario(SmartMoveCentralController c) {
        String vehicleId = "LON-B002";

        // Make sure vehicle is available
        c.getVehicleRepo().findById(vehicleId).ifPresent(v -> {
            if (v.getState() != VehicleState.AVAILABLE) {
                logger.info("  Note: %s".formatted(vehicleId + " is " + v.getState()) + ", resetting for test");
                v.transitionTo(VehicleState.AVAILABLE);
            }
        });

        logger.info("Two threads simultaneously trying to reserve %s".formatted(vehicleId + "..."));

        Thread t1 = new Thread(() -> {
            try {
                Rental r = c.reserveVehicle("U004", vehicleId);
                logger.info("  [Thread-1] SUCCESS: Elena reserved %s".formatted(vehicleId + " → " + r.getId()));
            } catch (SmartMoveException e) {
                logger.info("  [Thread-1] REJECTED: %s".formatted(e.getMessage()));
            }
        }, "Thread-1");

        Thread t2 = new Thread(() -> {
            try {
                Rental r = c.reserveVehicle("U005", vehicleId);
                logger.info("  [Thread-2] SUCCESS: James reserved %s".formatted(vehicleId + " → " + r.getId()));
            } catch (SmartMoveException e) {
                logger.info("  [Thread-2] REJECTED: %s".formatted(e.getMessage()));
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
                logger.info("%s (only one reservation should have succeeded)".formatted("  Final state of %s".formatted("%s: %s".formatted(vehicleId, v.getState())))));
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private static void printFleetSummary(SmartMoveCentralController c) {
        logger.info("Vehicle ID       | Type             | City   | State          | Bat% | Temp°C");
        logger.info("-----------------|------------------|--------|----------------|------|-------");
        c.getVehicleRepo().getAll().values().stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .forEach(v -> logger.info(String.format("%-17s| %-17s| %-7s| %-15s| %4d | %.1f%n",
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
