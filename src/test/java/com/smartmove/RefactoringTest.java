package com.smartmove;

import com.smartmove.persistence.*;
import com.smartmove.builder.*;
import com.smartmove.config.*;
import com.smartmove.domain.*;
import com.smartmove.domain.vehicle.*;
import com.smartmove.events.*;
import com.smartmove.factory.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.smartmove.constants.SmartMoveConstants.*;
import static com.smartmove.constants.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for refactored components (constants, events, builders, factories).
 * This test suite increases code coverage for newly added classes.
 */
class RefactoringTest {

    @BeforeEach
    void setUp() {
        EventBus.getInstance().clear();
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testSmartMoveConstants_AllValuesPresent() {
        assertEquals(60.0, CRITICAL_TEMPERATURE_C);

        assertEquals(5, CRITICAL_BATTERY_PERCENT);

        assertNotNull(DATA_DIR);
        assertEquals("data", DATA_DIR);

        assertEquals(6.00, BASE_RENTAL_AMOUNT);
    }

    @Test
    void testTestConstants_VehicleIds() {
        assertNotNull(LON_SCOOTER_001);
        assertNotNull(MIL_MOPED_001);
        assertNotNull(ROM_SCOOTER_002);

        assertTrue(LON_SCOOTER_001.startsWith("LON-"));
        assertTrue(MIL_MOPED_001.startsWith("MIL-"));
        assertTrue(ROM_SCOOTER_002.startsWith("ROM-"));
    }

    @Test
    void testTestConstants_UserIds() {
        assertNotNull(USER_001);
        assertNotNull(USER_005);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DOMAIN VALIDATOR TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testDomainValidator_VehicleId() {
        assertDoesNotThrow(() -> DomainValidator.validateVehicleId("V123"));

        assertThrows(IllegalArgumentException.class,
                () -> DomainValidator.validateVehicleId(null));
        assertThrows(IllegalArgumentException.class,
                () -> DomainValidator.validateVehicleId(""));
        assertThrows(IllegalArgumentException.class,
                () -> DomainValidator.validateVehicleId("A".repeat(51)));
    }

    @Test
    void testDomainValidator_Battery() {
        assertDoesNotThrow(() -> DomainValidator.validateBatteryPercent(50));
        assertDoesNotThrow(() -> DomainValidator.validateBatteryPercent(0));
        assertDoesNotThrow(() -> DomainValidator.validateBatteryPercent(100));

        assertThrows(IllegalArgumentException.class,
                () -> DomainValidator.validateBatteryPercent(-1));
        assertThrows(IllegalArgumentException.class,
                () -> DomainValidator.validateBatteryPercent(101));
    }

    @Test
    void testDomainValidator_Temperature() {
        assertDoesNotThrow(() -> DomainValidator.validateTemperature(25.0));
        assertDoesNotThrow(() -> DomainValidator.validateTemperature(-49.0));
        assertDoesNotThrow(() -> DomainValidator.validateTemperature(199.0));

        assertThrows(IllegalArgumentException.class,
                () -> DomainValidator.validateTemperature(-51.0));
        assertThrows(IllegalArgumentException.class,
                () -> DomainValidator.validateTemperature(201.0));
    }

    @Test
    void testDomainValidator_Coordinate() {
        assertDoesNotThrow(() -> DomainValidator.validateCoordinate(51.5, -0.1));
        assertDoesNotThrow(() -> DomainValidator.validateCoordinate(-90, -180));
        assertDoesNotThrow(() -> DomainValidator.validateCoordinate(90, 180));

        assertThrows(IllegalArgumentException.class,
                () -> DomainValidator.validateCoordinate(91, 0));
        assertThrows(IllegalArgumentException.class,
                () -> DomainValidator.validateCoordinate(0, 181));
    }

    @Test
    void testDomainValidator_Amount() {
        assertDoesNotThrow(() -> DomainValidator.validateAmount(10.50));
        assertDoesNotThrow(() -> DomainValidator.validateAmount(0));

        assertThrows(IllegalArgumentException.class,
                () -> DomainValidator.validateAmount(-1));
    }

    @Test
    void testDomainValidator_RequireNonNull() {
        String value = DomainValidator.requireNonNull("test", "error");
        assertEquals("test", value);

        assertThrows(IllegalArgumentException.class,
                () -> DomainValidator.requireNonNull(null, "Value is null"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // SYSTEM CONFIGURATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testSystemConfiguration_Singleton() {
        SystemConfiguration config1 = SystemConfiguration.getInstance();
        SystemConfiguration config2 = SystemConfiguration.getInstance();
        assertSame(config1, config2);
    }

    @Test
    void testSystemConfiguration_DefaultValues() {
        SystemConfiguration config = SystemConfiguration.getInstance();
        assertEquals(CRITICAL_TEMPERATURE_C, config.getCriticalTemperatureC());
        assertEquals(CRITICAL_BATTERY_PERCENT, config.getCriticalBatteryPercent());
    }

    @Test
    void testSystemConfiguration_Validation() {
        SystemConfiguration config = SystemConfiguration.getInstance();
        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    void testSystemConfiguration_SettersWithValidation() {
        SystemConfiguration config = SystemConfiguration.getInstance();

        assertDoesNotThrow(() -> config.setCriticalTemperatureC(70.0));
        assertEquals(70.0, config.getCriticalTemperatureC());

        assertThrows(IllegalArgumentException.class,
                () -> config.setCriticalTemperatureC(-10));
        assertThrows(IllegalArgumentException.class,
                () -> config.setCriticalTemperatureC(300));
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT BUS TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testEventBus_Singleton() {
        EventBus bus1 = EventBus.getInstance();
        EventBus bus2 = EventBus.getInstance();
        assertSame(bus1, bus2);
    }

    @Test
    void testEventBus_SubscribeAndPublish() {
        AtomicBoolean received = new AtomicBoolean(false);

        EventBus.getInstance().subscribe(CriticalTemperatureEvent.class, event -> {
            received.set(true);
        });

        Vehicle v = VehicleBuilder.aVehicle()
                .withId("TEST")
                .inCity("London")
                .at(51.5, -0.1)
                .asScooter()
                .build();

        EventBus.getInstance().publish(new CriticalTemperatureEvent(v, 75.0));

        assertTrue(received.get());
    }

    @Test
    void testEventBus_MultipleSubscribers() {
        AtomicInteger count = new AtomicInteger(0);

        EventBus.getInstance().subscribe(CriticalBatteryEvent.class, e -> count.incrementAndGet());
        EventBus.getInstance().subscribe(CriticalBatteryEvent.class, e -> count.incrementAndGet());
        EventBus.getInstance().subscribe(CriticalBatteryEvent.class, e -> count.incrementAndGet());

        Vehicle v = VehicleBuilder.aVehicle()
                .withId("TEST")
                .inCity("Rome")
                .at(41.9, 12.5)
                .asBicycle()
                .build();

        EventBus.getInstance().publish(new CriticalBatteryEvent(v, 3));

        assertEquals(3, count.get());
    }

    @Test
    void testEventBus_Unsubscribe() {
        AtomicInteger count = new AtomicInteger(0);

        java.util.function.Consumer<TheftAlarmEvent> handler = e -> count.incrementAndGet();

        EventBus.getInstance().subscribe(TheftAlarmEvent.class, handler);

        Vehicle v = VehicleBuilder.aVehicle()
                .withId("TEST")
                .inCity("London")
                .at(51.5, -0.1)
                .asMoped()
                .build();

        EventBus.getInstance().publish(new TheftAlarmEvent(v, 15.0));
        assertEquals(1, count.get());

        EventBus.getInstance().unsubscribe(TheftAlarmEvent.class, handler);
        EventBus.getInstance().publish(new TheftAlarmEvent(v, 20.0));
        assertEquals(1, count.get()); // Still 1, not incremented
    }

    @Test
    void testEventBus_GetSubscriberCount() {
        EventBus bus = EventBus.getInstance();
        bus.clear();

        assertEquals(0, bus.getSubscriberCount(CriticalTemperatureEvent.class));

        bus.subscribe(CriticalTemperatureEvent.class, e -> {});
        assertEquals(1, bus.getSubscriberCount(CriticalTemperatureEvent.class));

        bus.subscribe(CriticalTemperatureEvent.class, e -> {});
        assertEquals(2, bus.getSubscriberCount(CriticalTemperatureEvent.class));
    }

    // ═══════════════════════════════════════════════════════════════════
    // FACTORY TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testVehicleFactory_CreateBicycle() {
        Vehicle v = VehicleFactory.createVehicle("bicycle", "B1",
                new City("London"), new GeoCoordinate(51.5, -0.1), 85);

        assertNotNull(v);
        assertTrue(v instanceof Bicycle);
        assertEquals("B1", v.getId());
    }

    @Test
    void testVehicleFactory_CreateScooter() {
        Vehicle v = VehicleFactory.createVehicle("scooter", "S1",
                new City("Milan"), new GeoCoordinate(45.4, 9.1), 90);

        assertTrue(v instanceof ElectricScooter);
    }

    @Test
    void testVehicleFactory_CreateMoped() {
        Vehicle v = VehicleFactory.createVehicle("moped", "M1",
                new City("Rome"), new GeoCoordinate(41.9, 12.5), 75);

        assertTrue(v instanceof Moped);
    }

    @Test
    void testVehicleFactory_InvalidType() {
        assertThrows(IllegalArgumentException.class, () -> {
            VehicleFactory.createVehicle("helicopter", "H1",
                    new City("London"), new GeoCoordinate(51.5, -0.1), 100);
        });
    }

    @Test
    void testVehicleFactory_ValidationErrors() {
        assertThrows(IllegalArgumentException.class, () -> {
            VehicleFactory.createVehicle("bicycle", null,
                    new City("London"), new GeoCoordinate(51.5, -0.1), 80);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            VehicleFactory.createVehicle("bicycle", "B1",
                    null, new GeoCoordinate(51.5, -0.1), 80);
        });
    }

    @Test
    void testTelemetryDataFactory_Create() {
        TelemetryData t = TelemetryDataFactory.create(
                "2025-01-01T12:00:00Z", 51.5, -0.1, 80, 25.0, false);

        assertNotNull(t);
        assertEquals(80, t.getBatteryPercent());
        assertEquals(25.0, t.getTemperatureC());
    }

    @Test
    void testTelemetryDataFactory_CreateNow() {
        TelemetryData t = TelemetryDataFactory.createNow(51.5, -0.1, 75, 22.0);

        assertNotNull(t);
        assertEquals(75, t.getBatteryPercent());
    }

    @Test
    void testDomainEntityFactory_CreateUser() {
        User u = DomainEntityFactory.createUser("U123", "Alice");
        assertNotNull(u);
        assertEquals("U123", u.getId());
        assertEquals("Alice", u.getName());
    }

    @Test
    void testDomainEntityFactory_CreateCity() {
        City c = DomainEntityFactory.createCity("Paris");
        assertNotNull(c);
        assertEquals("Paris", c.getName());
    }

    @Test
    void testDomainEntityFactory_CreateZone() {
        Zone z = DomainEntityFactory.createZone("Z1",
                new GeoCoordinate(51.5, -0.1), 1000, true);

        assertNotNull(z);
        assertEquals("Z1", z.getZoneId());
        assertEquals(1000, z.getRadiusMeters());
        assertTrue(z.isRestricted());
    }

    // ═══════════════════════════════════════════════════════════════════
    // OPERATION RESULT TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testOperationResult_Success() {
        OperationResult<String> result = OperationResult.success("data");

        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
        assertEquals("data", result.getValue());
    }

    @Test
    void testOperationResult_Failure() {
        OperationResult<String> result = OperationResult.failure("error occurred");

        assertFalse(result.isSuccess());
        assertTrue(result.isFailure());
        assertEquals("error occurred", result.getErrorMessage());
    }

    @Test
    void testOperationResult_GetValueFromFailure() {
        OperationResult<String> result = OperationResult.failure("error");

        assertThrows(IllegalStateException.class, () -> result.getValue());
    }

    @Test
    void testOperationResult_GetValueOrDefault() {
        OperationResult<String> success = OperationResult.success("value");
        assertEquals("value", success.getValueOrDefault("default"));

        OperationResult<String> failure = OperationResult.failure("error");
        assertEquals("default", failure.getValueOrDefault("default"));
    }

    @Test
    void testOperationResult_Map() {
        OperationResult<Integer> result = OperationResult.success(5);
        OperationResult<String> mapped = result.map(i -> "Number: " + i);

        assertTrue(mapped.isSuccess());
        assertEquals("Number: 5", mapped.getValue());
    }

    // ═══════════════════════════════════════════════════════════════════
    // GEOCOORDINATE TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testGeoCoordinate_Parse() {
        GeoCoordinate coord = GeoCoordinate.parse("51.5074,-0.1278");
        assertEquals(51.5074, coord.getLatitude(), 0.0001);
        assertEquals(-0.1278, coord.getLongitude(), 0.0001);
    }

    @Test
    void testGeoCoordinate_ParseInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> GeoCoordinate.parse("invalid"));
        assertThrows(IllegalArgumentException.class,
                () -> GeoCoordinate.parse("51.5"));
    }

    @Test
    void testGeoCoordinate_DistanceTo() {
        GeoCoordinate london = new GeoCoordinate(51.5074, -0.1278);
        GeoCoordinate paris = new GeoCoordinate(48.8566, 2.3522);

        double distance = london.distanceTo(paris);
        assertTrue(distance > 300_000); // ~340 km
        assertTrue(distance < 400_000);
    }

    @Test
    void testGeoCoordinate_EqualsAndHashCode() {
        GeoCoordinate c1 = new GeoCoordinate(51.5, -0.1);
        GeoCoordinate c2 = new GeoCoordinate(51.5, -0.1);
        GeoCoordinate c3 = new GeoCoordinate(51.6, -0.1);

        assertEquals(c1, c2);
        assertNotEquals(c1, c3);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    // ═══════════════════════════════════════════════════════════════════
    // TELEMETRY DATA TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testTelemetryData_IsCritical() {
        TelemetryData critical = new TelemetryData(
                "2025-01-01T12:00:00Z",
                new GeoCoordinate(51.5, -0.1),
                3,
                75.0,
                false
        );

        assertTrue(critical.isCritical());
    }

    @Test
    void testTelemetryData_IsWarning() {
        TelemetryData warning = new TelemetryData(
                "2025-01-01T12:00:00Z",
                new GeoCoordinate(51.5, -0.1),
                12,
                55.0,
                false
        );

        assertTrue(warning.isWarning());
        assertFalse(warning.isCritical());
    }

    // ═══════════════════════════════════════════════════════════════════
// PERSISTENCE TESTS
// ═══════════════════════════════════════════════════════════════════

    @Test
    void testVehicleRepository_PutAndFind() {
        VehicleRepository repo = new VehicleRepository();

        Vehicle vehicle = VehicleBuilder.aVehicle()
                .withId("REPO-TEST-1")
                .inCity("London")
                .at(51.5, -0.1)
                .asBicycle()
                .build();

        repo.put(vehicle);

        var found = repo.findById("REPO-TEST-1");
        assertTrue(found.isPresent());
        assertEquals("REPO-TEST-1", found.get().getId());
    }

    @Test
    void testVehicleRepository_FindByCity() {
        VehicleRepository repo = new VehicleRepository();

        Vehicle v1 = VehicleBuilder.aVehicle()
                .withId("LON-1")
                .inCity("London")
                .at(51.5, -0.1)
                .asBicycle()
                .build();

        Vehicle v2 = VehicleBuilder.aVehicle()
                .withId("LON-2")
                .inCity("London")
                .at(51.51, -0.12)
                .asScooter()
                .build();

        repo.put(v1);
        repo.put(v2);

        var londonVehicles = repo.findByCity("London");
        assertTrue(londonVehicles.size() >= 2);
    }

    @Test
    void testUserRepository_PutAndFind() {
        UserRepository repo = new UserRepository();

        User user = new User("TEST-USER", "Test User");
        repo.save(user);

        var found = repo.findById("TEST-USER");
        assertTrue(found.isPresent());
        assertEquals("Test User", found.get().getName());
    }

    @Test
    void testRentalRepository_FindActive() {
        RentalRepository repo = new RentalRepository();

        Rental rental = RentalBuilder.aRental()
                .withId("R-TEST")
                .forUser("U001")
                .forVehicle("V001")
                .build();

        repo.save(rental);

        var found = repo.findActiveByVehicleId("V001");
        assertTrue(found.isPresent());
        assertEquals("R-TEST", found.get().getId());
    }
}
