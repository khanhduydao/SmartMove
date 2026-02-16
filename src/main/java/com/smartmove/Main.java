package com.smartmove;

import com.smartmove.controller.SmartMoveException;
import com.smartmove.controller.SmartMoveCentralController;
import com.smartmove.domain.*;
import com.smartmove.domain.vehicle.*;
import com.smartmove.util.DataSeeder;

import java.time.Instant;

/**
 * SmartMove Core Engine — Main Demonstration
 *
 * Demonstrates:
 *   1. Multi-city vehicle reservation & rental (London, Milan, Rome)
 *   2. City-specific policy enforcement (congestion charge, helmet check, zone restrictions)
 *   3. State machine transitions with validation
 *   4. Real-time telemetry processing (critical temp, low battery, theft alarm)
 *   5. Concurrent rental safety with primitive locking
 *   6. Audit trail with checksum chain verification
 *   7. Rollback on failure
 *   8. File-based persistence (CSV)
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        printBanner();

        // ─── 1. Seed initial data ──────────────────────────────────────────
        SmartMoveCentralController controller = new SmartMoveCentralController();
        DataSeeder.seed(controller.getVehicleRepo(), controller.getUserRepo());

        pause(300);

        // ─── 2. London Scenario ────────────────────────────────────────────
        System.out.println("\n" + section("LONDON SCENARIO — Congestion Charge"));
        londonScenario(controller);

        pause(500);

        // ─── 3. Milan Scenario ─────────────────────────────────────────────
        System.out.println("\n" + section("MILAN SCENARIO — Helmet Check for Moped"));
        milanScenario(controller);

        pause(500);

        // ─── 4. Rome Scenario ──────────────────────────────────────────────
        System.out.println("\n" + section("ROME SCENARIO — Zone Restrictions for Scooter"));
        romeScenario(controller);

        pause(500);

        // ─── 5. Telemetry Scenarios ────────────────────────────────────────
        System.out.println("\n" + section("TELEMETRY — Critical Temperature & Theft Alarm"));
        telemetryScenario(controller);

        pause(1500); // Give background telemetry thread time to process

        // ─── 6. Concurrency Test ───────────────────────────────────────────
        System.out.println("\n" + section("CONCURRENCY — Simultaneous Reservation Attempts"));
        concurrencyScenario(controller);

        pause(500);

        // ─── 7. Audit Verification ─────────────────────────────────────────
        System.out.println("\n" + section("AUDIT TRAIL — Chain Integrity Verification"));
        controller.printAuditLog();
        boolean valid = controller.verifyAuditChain();
        System.out.println("Audit chain valid: " + valid);

        // ─── 8. State Summary ──────────────────────────────────────────────
        System.out.println("\n" + section("FLEET STATUS SUMMARY"));
        printFleetSummary(controller);

        // ─── Shutdown ──────────────────────────────────────────────────────
        controller.stopTelemetryMonitor();
        System.out.println("\n[Main] SmartMove engine shutdown complete.");
    }

    // ─── LONDON: Reserve scooter → start → end (congestion charge applied) ───

    private static void londonScenario(SmartMoveCentralController c) {
        try {
            System.out.println("User U001 (Alice) reserves electric scooter LON-ES001 in London");
            Rental rental = c.reserveVehicle("U001", "LON-ES001");
            System.out.println("  → " + rental);

            System.out.println("Alice starts her rental...");
            c.startRental(rental.getId(), "LON-ES001");

            System.out.println("Alice ends her trip (congestion charge will be added)...");
            Payment payment = c.endRental(rental.getId(), "LON-ES001");
            System.out.println("  → " + payment);

        } catch (SmartMoveException e) {
            System.err.println("London scenario error: " + e.getMessage());
        }
    }

    // ─── MILAN: Moped unlock without helmet (should fail), then with helmet ───

    private static void milanScenario(SmartMoveCentralController c) {
        try {
            System.out.println("User U003 (Carlos) reserves Moped MIL-M001 in Milan");
            Rental rental = c.reserveVehicle("U003", "MIL-M001");
            System.out.println("  → " + rental);

            System.out.println("Carlos tries to start rental WITHOUT helmet...");
            try {
                c.startRental(rental.getId(), "MIL-M001");
                System.out.println("  → ERROR: Should have been rejected!");
            } catch (SmartMoveException e) {
                System.out.println("  → CORRECTLY REJECTED: " + e.getMessage());
            }

            System.out.println("Carlos puts on helmet (sensor confirms)...");
            Vehicle moped = c.getVehicleRepo().findById("MIL-M001").orElseThrow();
            if (moped instanceof Moped) {
                ((Moped) moped).setHelmetDetected(true);
            }

            System.out.println("Carlos tries again WITH helmet...");
            c.startRental(rental.getId(), "MIL-M001");
            System.out.println("  → Rental started successfully!");

            Payment payment = c.endRental(rental.getId(), "MIL-M001");
            System.out.println("  → " + payment);

        } catch (SmartMoveException e) {
            System.err.println("Milan scenario error: " + e.getMessage());
        }
    }

    // ─── ROME: Scooter tries to enter archaeological zone (should trigger lock) ─

    private static void romeScenario(SmartMoveCentralController c) {
        try {
            System.out.println("User U002 (Bob) reserves Scooter ROM-ES002 in Rome");
            Rental rental = c.reserveVehicle("U002", "ROM-ES002");
            c.startRental(rental.getId(), "ROM-ES002");
            System.out.println("  → Bob is riding scooter ROM-ES002 in Rome...");

            System.out.println("Checking: scooter heading toward Colosseum area (restricted zone)...");
            GeoCoordinate colosseumArea = new GeoCoordinate(41.8902, 12.4922);
            boolean allowed = c.checkGpsAllowed("ROM-ES002", colosseumArea);
            System.out.println("  → GPS check result: " + (allowed ? "ALLOWED" : "BLOCKED — emergency lock applied"));

            // Show the vehicle was locked
            Vehicle scooter = c.getVehicleRepo().findById("ROM-ES002").orElseThrow();
            System.out.println("  → Scooter state after GPS check: " + scooter.getState());

        } catch (SmartMoveException e) {
            System.err.println("Rome scenario error: " + e.getMessage());
        }
    }

    // ─── TELEMETRY: Critical temperature & theft alarm ────────────────────────

    private static void telemetryScenario(SmartMoveCentralController c) {
        // Critical temperature during active rental
        System.out.println("Simulating critical overheating on LON-ES002...");
        TelemetryData criticalTemp = new TelemetryData(
                Instant.now().toString(),
                new GeoCoordinate(51.5100, -0.1200),
                65, 75.0, false  // 75°C — WAY over the 60°C threshold
        );
        c.processTelemetry("LON-ES002", Instant.now().toString(), criticalTemp);

        // Low battery warning
        System.out.println("Simulating critically low battery on ROM-B001...");
        TelemetryData lowBattery = new TelemetryData(
                Instant.now().toString(),
                new GeoCoordinate(41.9028, 12.4964),
                3, 25.0, false  // 3% battery — below critical 5% threshold
        );
        c.processTelemetry("ROM-B001", Instant.now().toString(), lowBattery);

        // Theft alarm: vehicle moves without rental
        System.out.println("Simulating theft alarm on MIL-B001 (moved 50m without rental)...");
        // First update: set current location
        TelemetryData telemetry1 = new TelemetryData(
                Instant.now().toString(),
                new GeoCoordinate(45.4700, 9.1950),  // moved significantly
                90, 22.0, false
        );
        c.processTelemetry("MIL-B001", Instant.now().toString(), telemetry1);
    }

    // ─── CONCURRENCY: Two users try to reserve the same vehicle at once ───────

    private static void concurrencyScenario(SmartMoveCentralController c) {
        String vehicleId = "LON-B002";

        // Make sure vehicle is available
        c.getVehicleRepo().findById(vehicleId).ifPresent(v -> {
            if (v.getState() != VehicleState.AVAILABLE) {
                System.out.println("  Note: " + vehicleId + " is " + v.getState() + ", resetting for test");
                v.transitionTo(VehicleState.AVAILABLE);
            }
        });

        System.out.println("Two threads simultaneously trying to reserve " + vehicleId + "...");

        Thread t1 = new Thread(() -> {
            try {
                Rental r = c.reserveVehicle("U004", vehicleId);
                System.out.println("  [Thread-1] SUCCESS: Elena reserved " + vehicleId + " → " + r.getId());
            } catch (SmartMoveException e) {
                System.out.println("  [Thread-1] REJECTED: " + e.getMessage());
            }
        }, "Thread-1");

        Thread t2 = new Thread(() -> {
            try {
                Rental r = c.reserveVehicle("U005", vehicleId);
                System.out.println("  [Thread-2] SUCCESS: James reserved " + vehicleId + " → " + r.getId());
            } catch (SmartMoveException e) {
                System.out.println("  [Thread-2] REJECTED: " + e.getMessage());
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
                System.out.println("  Final state of " + vehicleId + ": " + v.getState()
                        + " (only one reservation should have succeeded)"));
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private static void printFleetSummary(SmartMoveCentralController c) {
        System.out.println("Vehicle ID       | Type             | City   | State          | Bat% | Temp°C");
        System.out.println("-----------------|------------------|--------|----------------|------|-------");
        c.getVehicleRepo().getAll().values().stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .forEach(v -> System.out.printf("%-17s| %-17s| %-7s| %-15s| %4d | %.1f%n",
                        v.getId(), v.getType(), v.getCity().getName(),
                        v.getState(), v.getBatteryPercent(), v.getTemperatureC()));
    }

    private static void printBanner() {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║         SmartMove Core Engine — v1.0                     ║");
        System.out.println("║  Urban Mobility Platform  |  London · Milan · Rome        ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
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
