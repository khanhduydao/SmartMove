package com.smartmove;

import com.smartmove.controller.SmartMoveCentralController;
import com.smartmove.domain.Payment;
import com.smartmove.domain.Rental;
import com.smartmove.domain.vehicle.Vehicle;
import com.smartmove.domain.vehicle.VehicleState;
import com.smartmove.util.DataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationTest {

    private SmartMoveCentralController controller;

    @BeforeEach
    void setUp() {
        controller = new SmartMoveCentralController();
        DataSeeder.seed(controller.getVehicleRepo(), controller.getUserRepo());
    }

    @Test
    void runMainDemo() throws Exception {
        // Runs all scenarios in Main.java
        Main.main(new String[]{});

        assertTrue(controller.verifyAuditChain(),
                "Audit chain should be valid after all operations");

        assertFalse(controller.getAuditLog().getEntries().isEmpty(),
                "Audit log should contain entries after running scenarios");
    }

    @Test
    void testLondonReservationWithCongestionCharge() throws Exception {
        // Reserve vehicle
        Rental rental = controller.reserveVehicle("U001", "LON-ES001");
        assertNotNull(rental, "Rental should be created");
        assertEquals("U001", rental.getUserId());
        assertEquals("LON-ES001", rental.getVehicleId());
        assertTrue(rental.isActive(), "Rental should be active");

        // Verify vehicle state changed to RESERVED
        Optional<Vehicle> vehicle = controller.getVehicleRepo().findById("LON-ES001");
        assertTrue(vehicle.isPresent());
        assertEquals(VehicleState.RESERVED, vehicle.get().getState(),
                "Vehicle should be in RESERVED state");

        // Start rental
        controller.startRental(rental.getId(), "LON-ES001");
        assertEquals(VehicleState.IN_USE, vehicle.get().getState(),
                "Vehicle should be in IN_USE state after starting rental");

        // End rental
        Payment payment = controller.endRental(rental.getId(), "LON-ES001");
        assertNotNull(payment, "Payment should be created");
        assertTrue(payment.getTotal() > payment.getBaseAmount(),
                "London should apply congestion charge (total > base)");
        assertEquals(3.50, payment.getSurcharges(), 0.01,
                "London congestion charge should be £3.50");
        assertFalse(rental.isActive(), "Rental should be inactive after ending");
        assertEquals(VehicleState.AVAILABLE, vehicle.get().getState(),
                "Vehicle should be AVAILABLE after rental ends");
    }

    @Test
    void testMilanHelmetCheckForMoped() throws Exception {
        // Reserve moped
        Rental rental = controller.reserveVehicle("U003", "MIL-M001");
        assertNotNull(rental);

        // Try to start without helmet - should fail
        Exception exception = assertThrows(Exception.class, () -> {
            controller.startRental(rental.getId(), "MIL-M001");
        }, "Starting moped rental without helmet should throw exception");

        assertTrue(exception.getMessage().contains("Helmet") ||
                        exception.getMessage().contains("helmet"),
                "Exception should mention helmet requirement");
    }

    @Test
    void testRomeArchaeologicalZoneRestriction() throws Exception {
        // Reserve and start scooter rental
        Rental rental = controller.reserveVehicle("U002", "ROM-ES001");
        controller.startRental(rental.getId(), "ROM-ES001");

        // Try to enter Colosseum area (restricted for scooters)
        com.smartmove.domain.GeoCoordinate colosseum =
                new com.smartmove.domain.GeoCoordinate(41.8902, 12.4922);

        boolean allowed = controller.checkGpsAllowed("ROM-ES001", colosseum);
        assertFalse(allowed);
        // Verify vehicle was emergency locked
        Optional<Vehicle> vehicle = controller.getVehicleRepo().findById("ROM-ES001");
        assertTrue(vehicle.isPresent());
        assertEquals(VehicleState.EMERGENCY_LOCK, vehicle.get().getState());
    }

    @Test
    void testAuditChainIntegrity() {
        // Perform some operations
        assertDoesNotThrow(() -> {
            controller.reserveVehicle("U004", "LON-B002");
        });

        // Verify audit chain
        assertTrue(controller.verifyAuditChain(),
                "Audit chain checksums should be valid");

        // Verify entries exist
        int entryCount = controller.getAuditLog().getEntries().size();
        assertTrue(entryCount > 0,
                "Audit log should contain at least one entry");
    }

    @Test
    void testConcurrentReservationPrevention() throws Exception {
        String vehicleId = "MIL-B001";

        // Ensure vehicle is available
        Optional<Vehicle> vehicle = controller.getVehicleRepo().findById(vehicleId);
        assertTrue(vehicle.isPresent());
        if (vehicle.get().getState() != VehicleState.AVAILABLE) {
            vehicle.get().transitionTo(VehicleState.AVAILABLE);
        }

        // First reservation should succeed
        Rental rental1 = controller.reserveVehicle("U004", vehicleId);
        assertNotNull(rental1);
        assertEquals(VehicleState.RESERVED, vehicle.get().getState());

        // Second reservation should fail (vehicle already reserved)
        Exception exception = assertThrows(Exception.class, () -> {
            controller.reserveVehicle("U005", vehicleId);
        }, "Second reservation of same vehicle should fail");

        assertTrue(exception.getMessage().contains("not available") ||
                        exception.getMessage().contains("RESERVED"),
                "Exception should indicate vehicle is not available");
    }
}