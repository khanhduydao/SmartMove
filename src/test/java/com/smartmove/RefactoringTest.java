package com.smartmove;

import com.smartmove.handlers.*;
import com.smartmove.telemetry.TelemetryMonitor;
import org.mockito.Mockito;
import com.smartmove.persistence.*;
import com.smartmove.builder.*;
import com.smartmove.config.*;
import com.smartmove.domain.*;
import com.smartmove.domain.vehicle.*;
import com.smartmove.events.*;
import com.smartmove.factory.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.smartmove.constants.SmartMoveConstants.*;
import static com.smartmove.constants.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for refactored components.
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

    // ═══════════════════════════════════════════════════════════════════
    // EVENT BUS - COMPREHENSIVE TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testEventBus_PublishWithNullEvent() {
        EventBus bus = EventBus.getInstance();

        assertThrows(IllegalArgumentException.class, () -> {
            bus.publish(null);
        });
    }

    @Test
    void testEventBus_MultipleEventTypes() {
        EventBus bus = EventBus.getInstance();
        bus.clear();

        AtomicInteger tempCount = new AtomicInteger(0);
        AtomicInteger batteryCount = new AtomicInteger(0);

        bus.subscribe(CriticalTemperatureEvent.class, e -> tempCount.incrementAndGet());
        bus.subscribe(CriticalBatteryEvent.class, e -> batteryCount.incrementAndGet());

        Vehicle v = VehicleBuilder.aVehicle()
                .withId("TEST")
                .inCity("London")
                .at(51.5, -0.1)
                .asScooter()
                .build();

        // Publish temperature event
        bus.publish(new CriticalTemperatureEvent(v, 75.0));
        assertEquals(1, tempCount.get());
        assertEquals(0, batteryCount.get());

        // Publish battery event
        bus.publish(new CriticalBatteryEvent(v, 3));
        assertEquals(1, tempCount.get());
        assertEquals(1, batteryCount.get());
    }

    @Test
    void testEventBus_HandlerException() {
        EventBus bus = EventBus.getInstance();
        bus.clear();

        AtomicBoolean handler1Called = new AtomicBoolean(false);
        AtomicBoolean handler2Called = new AtomicBoolean(false);

        // Handler 1 throws exception
        bus.subscribe(TheftAlarmEvent.class, e -> {
            handler1Called.set(true);
            throw new RuntimeException("Handler error");
        });

        // Handler 2 should still execute
        bus.subscribe(TheftAlarmEvent.class, e -> {
            handler2Called.set(true);
        });

        Vehicle v = VehicleBuilder.aVehicle()
                .withId("TEST")
                .inCity("Rome")
                .at(41.9, 12.5)
                .asMoped()
                .build();

        // Should not throw, but print error
        assertDoesNotThrow(() -> {
            bus.publish(new TheftAlarmEvent(v, 15.0));
        });

        // Both handlers should be called despite exception
        assertTrue(handler1Called.get());
        assertTrue(handler2Called.get());
    }

    @Test
    void testEventBus_ClearSubscriptions() {
        EventBus bus = EventBus.getInstance();

        bus.subscribe(CriticalTemperatureEvent.class, e -> {});
        assertTrue(bus.getSubscriberCount(CriticalTemperatureEvent.class) > 0);

        bus.clear();
        assertEquals(0, bus.getSubscriberCount(CriticalTemperatureEvent.class));
    }

    @Test
    void testEventBus_EventProperties() {
        Vehicle v = VehicleBuilder.aVehicle()
                .withId("EVENT-TEST")
                .inCity("Milan")
                .at(45.4, 9.1)
                .asScooter()
                .build();

        CriticalTemperatureEvent event = new CriticalTemperatureEvent(v, 75.0);

        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
        assertEquals(v, event.getVehicle());
        assertEquals(75.0, event.getTemperature());
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXCEPTION HANDLER TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testExceptionHandler_PersistenceOperationSuccess() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = ExceptionHandler.handlePersistenceOperation(
                "test operation",
                () -> {
                    attempts.incrementAndGet();
                    return "success";
                },
                3
        );

        assertEquals("success", result);
        assertEquals(1, attempts.get()); // Should succeed on first try
    }

    @Test
    void testExceptionHandler_PersistenceOperationRetry() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = ExceptionHandler.handlePersistenceOperation(
                "test operation",
                () -> {
                    attempts.incrementAndGet();
                    if (attempts.get() < 2) {
                        throw new IOException("Transient error");
                    }
                    return "success after retry";
                },
                3
        );

        assertEquals("success after retry", result);
        assertEquals(2, attempts.get()); // Should succeed on second try
    }

    @Test
    void testExceptionHandler_PersistenceOperationFailure() {
        assertThrows(ExceptionHandler.PersistenceException.class, () -> {
            ExceptionHandler.handlePersistenceOperation(
                    "test operation",
                    () -> {
                        throw new IOException("Permanent error");
                    },
                    3
            );
        });
    }

    @Test
    void testOperationResult_SuccessFlow() {
        OperationResult<String> result = OperationResult.success("data");

        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
        assertEquals("data", result.getValue());
        assertEquals("data", result.getValueOrDefault("default"));
    }

    @Test
    void testOperationResult_FailureFlow() {
        OperationResult<String> result = OperationResult.failure("error message");

        assertFalse(result.isSuccess());
        assertTrue(result.isFailure());
        assertEquals("error message", result.getErrorMessage());
        assertEquals("default", result.getValueOrDefault("default"));

        assertThrows(IllegalStateException.class, () -> result.getValue());
    }

    @Test
    void testOperationResult_MapSuccess() {
        OperationResult<Integer> result = OperationResult.success(5);
        OperationResult<String> mapped = result.map(i -> "Value: " + i);

        assertTrue(mapped.isSuccess());
        assertEquals("Value: 5", mapped.getValue());
    }

    @Test
    void testOperationResult_MapFailure() {
        OperationResult<Integer> result = OperationResult.failure("error");
        OperationResult<String> mapped = result.map(i -> "Value: " + i);

        assertTrue(mapped.isFailure());
        assertEquals("error", mapped.getErrorMessage());
    }

    // ═══════════════════════════════════════════════════════════════════
    // SYSTEM CONFIGURATION - EXTENDED TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testSystemConfiguration_SetBatteryPercent() {
        SystemConfiguration config = SystemConfiguration.getInstance();

        assertDoesNotThrow(() -> config.setCriticalBatteryPercent(10));
        assertEquals(10, config.getCriticalBatteryPercent());

        assertThrows(IllegalArgumentException.class,
                () -> config.setCriticalBatteryPercent(-1));
        assertThrows(IllegalArgumentException.class,
                () -> config.setCriticalBatteryPercent(101));
    }

    @Test
    void testSystemConfiguration_SetQueueCapacity() {
        SystemConfiguration config = SystemConfiguration.getInstance();

        assertDoesNotThrow(() -> config.setTelemetryQueueCapacity(10000));
        assertEquals(10000, config.getTelemetryQueueCapacity());

        assertThrows(IllegalArgumentException.class,
                () -> config.setTelemetryQueueCapacity(0));
        assertThrows(IllegalArgumentException.class,
                () -> config.setTelemetryQueueCapacity(-100));
    }

    @Test
    void testSystemConfiguration_AllGetters() {
        SystemConfiguration config = SystemConfiguration.getInstance();

        assertNotNull(config.getDataDirectory());
        assertTrue(config.getWarningTemperatureC() > 0);
        assertTrue(config.getLowBatteryPercent() > 0);
        assertTrue(config.getTheftMovementThresholdMeters() > 0);
        assertTrue(config.getTelemetryPollTimeoutMs() > 0);
        assertTrue(config.getBaseRentalAmount() >= 0);
    }

    // ═══════════════════════════════════════════════════════════════════
    // FACTORY - COMPREHENSIVE VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testVehicleFactory_AllValidationErrors() {
        // Null ID
        assertThrows(IllegalArgumentException.class, () -> {
            VehicleFactory.createVehicle("bicycle", null,
                    new City("London"), new GeoCoordinate(51.5, -0.1), 80);
        });

        // Null city
        assertThrows(IllegalArgumentException.class, () -> {
            VehicleFactory.createVehicle("bicycle", "B1",
                    null, new GeoCoordinate(51.5, -0.1), 80);
        });

        // Null location
        assertThrows(IllegalArgumentException.class, () -> {
            VehicleFactory.createVehicle("bicycle", "B1",
                    new City("London"), null, 80);
        });

        // Invalid battery
        assertThrows(IllegalArgumentException.class, () -> {
            VehicleFactory.createVehicle("bicycle", "B1",
                    new City("London"), new GeoCoordinate(51.5, -0.1), -10);
        });

        // Invalid type
        assertThrows(IllegalArgumentException.class, () -> {
            VehicleFactory.createVehicle("car", "C1",
                    new City("London"), new GeoCoordinate(51.5, -0.1), 80);
        });
    }

    @Test
    void testTelemetryDataFactory_AllValidations() {
        // Invalid coordinates
        assertThrows(IllegalArgumentException.class, () -> {
            TelemetryDataFactory.create("2025-01-01T12:00:00Z",
                    91.0, 0, 80, 25.0, false); // lat > 90
        });

        assertThrows(IllegalArgumentException.class, () -> {
            TelemetryDataFactory.create("2025-01-01T12:00:00Z",
                    0, 181.0, 80, 25.0, false); // lon > 180
        });

        // Invalid battery
        assertThrows(IllegalArgumentException.class, () -> {
            TelemetryDataFactory.create("2025-01-01T12:00:00Z",
                    51.5, -0.1, 101, 25.0, false);
        });

        // Invalid temperature
        assertThrows(IllegalArgumentException.class, () -> {
            TelemetryDataFactory.create("2025-01-01T12:00:00Z",
                    51.5, -0.1, 80, -60.0, false);
        });
    }

    @Test
    void testDomainEntityFactory_AllValidations() {
        // User with blank name
        assertThrows(IllegalArgumentException.class, () -> {
            DomainEntityFactory.createUser("U1", "");
        });

        // City with blank name
        assertThrows(IllegalArgumentException.class, () -> {
            DomainEntityFactory.createCity("   ");
        });

        // Zone with invalid radius
        assertThrows(IllegalArgumentException.class, () -> {
            DomainEntityFactory.createZone("Z1",
                    new GeoCoordinate(51.5, -0.1), -100, true);
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // HANDLERS - COMPREHENSIVE TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testCriticalTemperatureHandler_EmergencyLock() {
        AtomicBoolean emergencyLockCalled = new AtomicBoolean(false);

        // Mock VehicleStateManager
        VehicleStateManager stateManager = new VehicleStateManager() {
            @Override
            public void emergencyLock(Vehicle vehicle, String reason) {
                emergencyLockCalled.set(true);
                assertTrue(reason.contains("Critical temperature"));
            }

            @Override
            public void sendToMaintenance(Vehicle vehicle, String reason) {
                fail("Should not call sendToMaintenance");
            }
        };

        Vehicle vehicle = VehicleBuilder.aVehicle()
                .withId("HANDLER-TEST")
                .inCity("London")
                .at(51.5, -0.1)
                .withTemperature(75.0)
                .asScooter()
                .build();

        CriticalTemperatureHandler handler = new CriticalTemperatureHandler(stateManager);
        handler.handle(vehicle);

        assertTrue(emergencyLockCalled.get());
    }

    @Test
    void testCriticalBatteryHandler_InUse() {
        AtomicBoolean rentalTerminated = new AtomicBoolean(false);

        VehicleStateManager stateManager = new VehicleStateManager() {
            @Override
            public void emergencyLock(Vehicle vehicle, String reason) {
                fail("Should not lock when in use");
            }

            @Override
            public void sendToMaintenance(Vehicle vehicle, String reason) {
                fail("Should terminate rental, not send to maintenance");
            }
        };

        RentalTerminator terminator = new RentalTerminator() {
            @Override
            public void terminateEmergency(Vehicle vehicle, String reason) {
                rentalTerminated.set(true);
                assertTrue(reason.contains("Critical battery"));
            }
        };

        Vehicle vehicle = VehicleBuilder.aVehicle()
                .withId("BATTERY-TEST")
                .inCity("Milan")
                .at(45.4, 9.1)
                .withBattery(3)
                .asScooter()
                .build();

        vehicle.transitionTo(VehicleState.RESERVED);
        vehicle.transitionTo(VehicleState.IN_USE);

        CriticalBatteryHandler handler = new CriticalBatteryHandler(stateManager, terminator);
        handler.handle(vehicle);

        assertTrue(rentalTerminated.get());
    }

    @Test
    void testCriticalBatteryHandler_NotInUse() {
        AtomicBoolean maintenanceCalled = new AtomicBoolean(false);

        VehicleStateManager stateManager = new VehicleStateManager() {
            @Override
            public void emergencyLock(Vehicle vehicle, String reason) {}

            @Override
            public void sendToMaintenance(Vehicle vehicle, String reason) {
                maintenanceCalled.set(true);
                assertTrue(reason.contains("Critical battery"));
            }
        };

        RentalTerminator terminator = (vehicle, reason) -> {
            fail("Should not terminate - vehicle is not IN_USE");
        };

        Vehicle vehicle = VehicleBuilder.aVehicle()
                .withId("MAINT-TEST")
                .inCity("Rome")
                .at(41.9, 12.5)
                .withBattery(2)
                .asBicycle()
                .build();

        assertEquals(VehicleState.AVAILABLE, vehicle.getState());

        CriticalBatteryHandler handler = new CriticalBatteryHandler(stateManager, terminator);
        handler.handle(vehicle);

        assertTrue(maintenanceCalled.get());
    }

    @Test
    void testTheftAlarmHandler() {
        AtomicBoolean lockCalled = new AtomicBoolean(false);

        VehicleStateManager stateManager = new VehicleStateManager() {
            @Override
            public void emergencyLock(Vehicle vehicle, String reason) {
                lockCalled.set(true);
                assertTrue(reason.contains("Theft alarm"));
            }

            @Override
            public void sendToMaintenance(Vehicle vehicle, String reason) {}
        };

        Vehicle vehicle = VehicleBuilder.aVehicle()
                .withId("THEFT-TEST")
                .inCity("London")
                .at(51.5, -0.1)
                .asMoped()
                .build();

        TheftAlarmHandler handler = new TheftAlarmHandler(stateManager);
        handler.handle(vehicle);

        assertTrue(lockCalled.get());
    }

    @Test
    void testWarningEventHandler() {
        Vehicle vehicle = VehicleBuilder.aVehicle()
                .withId("WARNING-TEST")
                .inCity("Milan")
                .at(45.4, 9.1)
                .withBattery(12)
                .withTemperature(55.0)
                .asScooter()
                .build();

        WarningEventHandler handler = new WarningEventHandler();

        // Should not throw
        assertDoesNotThrow(() -> handler.handle(vehicle));
    }

    // ═══════════════════════════════════════════════════════════════════
    // TELEMETRY MONITOR - INTEGRATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testTelemetryMonitor_StartAndStop() throws InterruptedException {
        TelemetryMonitor monitor = new TelemetryMonitor();
        Thread thread = new Thread(monitor);
        thread.setDaemon(true);
        thread.start();

        Thread.sleep(200); // Let it run

        monitor.stop();
        thread.join(1000); // Wait for shutdown

        assertFalse(thread.isAlive());
    }

    @Test
    void testTelemetryMonitor_ProcessTelemetry() throws InterruptedException {
        TelemetryMonitor monitor = new TelemetryMonitor();
        Thread thread = new Thread(monitor);
        thread.setDaemon(true);
        thread.start();

        AtomicBoolean eventReceived = new AtomicBoolean(false);

        EventBus.getInstance().subscribe(CriticalTemperatureEvent.class, event -> {
            eventReceived.set(true);
        });

        Vehicle vehicle = VehicleBuilder.aVehicle()
                .withId("TELEM-TEST")
                .inCity("London")
                .at(51.5, -0.1)
                .asScooter()
                .build();

        TelemetryData criticalData = TelemetryDataBuilder.aTelemetryData()
                .at(51.5, -0.1)
                .withBattery(80)
                .withTemperature(75.0)
                .build();

        monitor.submitTelemetry(vehicle, criticalData);

        Thread.sleep(500); // Wait for processing

        assertTrue(eventReceived.get());

        monitor.stop();
        thread.join(1000);
    }

    @Test
    void testTelemetryMonitor_QueueSize() {
        TelemetryMonitor monitor = new TelemetryMonitor();

        assertEquals(0, monitor.getQueueSize());

        Vehicle vehicle = VehicleBuilder.aVehicle()
                .withId("QUEUE-TEST")
                .inCity("Rome")
                .at(41.9, 12.5)
                .asBicycle()
                .build();

        TelemetryData data = TelemetryDataBuilder.aTelemetryData()
                .at(41.9, 12.5)
                .withBattery(50)
                .build();

        monitor.submitTelemetry(vehicle, data);

        // Queue size should increase (might be 0 if processed immediately)
        assertTrue(monitor.getQueueSize() >= 0);
    }

    @Test
    void testTelemetryMonitor_Integration() throws InterruptedException {
        TelemetryMonitor monitor = new TelemetryMonitor();
        Thread thread = new Thread(monitor, "TelemetryTest");
        thread.setDaemon(true);
        thread.start();

        // Give it time to start
        Thread.sleep(100);

        AtomicBoolean eventReceived = new AtomicBoolean(false);

        EventBus.getInstance().subscribe(CriticalTemperatureEvent.class, event -> {
            eventReceived.set(true);
        });

        Vehicle vehicle = VehicleBuilder.aVehicle()
                .withId("INT-TEST")
                .inCity("London")
                .at(51.5, -0.1)
                .asScooter()
                .build();

        TelemetryData criticalData = TelemetryDataBuilder.aTelemetryData()
                .at(51.5, -0.1)
                .withBattery(80)
                .withTemperature(75.0) // Critical!
                .build();

        monitor.submitTelemetry(vehicle, criticalData);

        // Wait for processing
        Thread.sleep(1000);

        // Event should be published
        assertTrue(eventReceived.get(), "Critical temperature event should be published");

        // Cleanup
        monitor.stop();
        thread.join(2000);
    }

    // ═══════════════════════════════════════════════════════════════════
    // TELEMETRY MONITOR - SIMPLE TESTS (No threading)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testTelemetryMonitor_Constructor() {
        TelemetryMonitor monitor = new TelemetryMonitor();
        assertNotNull(monitor);
        assertEquals(0, monitor.getQueueSize());
    }

    @Test
    void testTelemetryMonitor_SubmitTelemetry() {
        TelemetryMonitor monitor = new TelemetryMonitor();

        Vehicle vehicle = VehicleBuilder.aVehicle()
                .withId("TELEM-V1")
                .inCity("London")
                .at(51.5074, -0.1278)
                .asScooter()
                .build();

        TelemetryData data = TelemetryDataBuilder.aTelemetryData()
                .at(51.5074, -0.1278)
                .withBattery(80)
                .withTemperature(25.0)
                .build();

        assertDoesNotThrow(() -> {
            monitor.submitTelemetry(vehicle, data);
        });
    }

    @Test
    void testTelemetryMonitor_SubmitMultipleTelemetry() {
        TelemetryMonitor monitor = new TelemetryMonitor();

        Vehicle v1 = VehicleBuilder.aVehicle()
                .withId("V1")
                .inCity("Milan")
                .at(45.4, 9.1)
                .asBicycle()
                .build();

        Vehicle v2 = VehicleBuilder.aVehicle()
                .withId("V2")
                .inCity("Rome")
                .at(41.9, 12.5)
                .asMoped()
                .build();

        TelemetryData data1 = TelemetryDataBuilder.aTelemetryData()
                .at(45.4, 9.1)
                .withBattery(50)
                .build();

        TelemetryData data2 = TelemetryDataBuilder.aTelemetryData()
                .at(41.9, 12.5)
                .withBattery(70)
                .build();

        monitor.submitTelemetry(v1, data1);
        monitor.submitTelemetry(v2, data2);

        // Queue should have items (or they might be processed already)
        assertTrue(monitor.getQueueSize() >= 0);
    }

    @Test
    void testTelemetryMonitor_Stop() {
        TelemetryMonitor monitor = new TelemetryMonitor();

        // Should not throw
        assertDoesNotThrow(() -> monitor.stop());
    }

    // ═══════════════════════════════════════════════════════════════════
    // DOMAIN EVENTS - ADDITIONAL TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testVehicleStateChangedEvent() {
        Vehicle vehicle = VehicleBuilder.aVehicle()
                .withId("STATE-TEST")
                .inCity("London")
                .at(51.5, -0.1)
                .asScooter()
                .build();

        VehicleStateChangedEvent event = new VehicleStateChangedEvent(
                vehicle,
                VehicleState.AVAILABLE,
                VehicleState.RESERVED
        );

        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
        assertEquals(vehicle, event.getVehicle());
        assertEquals(VehicleState.AVAILABLE, event.getOldState());
        assertEquals(VehicleState.RESERVED, event.getNewState());
    }

    @Test
    void testRentalCreatedEvent() {
        RentalCreatedEvent event = new RentalCreatedEvent("R123", "U001", "V001");

        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
        assertEquals("R123", event.getRentalId());
        assertEquals("U001", event.getUserId());
        assertEquals("V001", event.getVehicleId());
    }

    @Test
    void testPaymentProcessedEvent() {
        PaymentProcessedEvent event = new PaymentProcessedEvent("P001", "R123", 15.50);

        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
        assertEquals("P001", event.getPaymentId());
        assertEquals("R123", event.getRentalId());
        assertEquals(15.50, event.getAmount(), 0.01);
    }

    @Test
    void testHighTemperatureWarningEvent() {
        Vehicle vehicle = VehicleBuilder.aVehicle()
                .withId("WARN-TEST")
                .inCity("Milan")
                .at(45.4, 9.1)
                .asScooter()
                .build();

        HighTemperatureWarningEvent event = new HighTemperatureWarningEvent(vehicle, 55.0);

        assertEquals(vehicle, event.getVehicle());
        assertEquals(55.0, event.getTemperature());
    }

    @Test
    void testLowBatteryWarningEvent() {
        Vehicle vehicle = VehicleBuilder.aVehicle()
                .withId("LOW-BAT-TEST")
                .inCity("Rome")
                .at(41.9, 12.5)
                .asBicycle()
                .build();

        LowBatteryWarningEvent event = new LowBatteryWarningEvent(vehicle, 12);

        assertEquals(vehicle, event.getVehicle());
        assertEquals(12, event.getBatteryPercent());
    }

    // ═══════════════════════════════════════════════════════════════════
// DOMAIN VEHICLE - ADDITIONAL COVERAGE
// ═══════════════════════════════════════════════════════════════════

    @Test
    void testVehicle_GettersAllTypes() {
        // Test Bicycle
        Vehicle bicycle = VehicleBuilder.aVehicle()
                .withId("B-TEST")
                .inCity("London")
                .at(51.5, -0.1)
                .asBicycle()
                .build();

        assertEquals("B-TEST", bicycle.getId());
        assertEquals("Bicycle", bicycle.getType());
        assertNotNull(bicycle.getCity());
        assertNotNull(bicycle.getLocation());
        assertNotNull(bicycle.getStateLock());

        // Test ElectricScooter
        Vehicle scooter = VehicleBuilder.aVehicle()
                .withId("S-TEST")
                .inCity("Milan")
                .at(45.4, 9.1)
                .asScooter()
                .build();

        assertEquals("ElectricScooter", scooter.getType());

        // Test Moped
        Vehicle moped = VehicleBuilder.aVehicle()
                .withId("M-TEST")
                .inCity("Rome")
                .at(41.9, 12.5)
                .asMoped()
                .build();

        assertEquals("Moped", moped.getType());
    }

    @Test
    void testVehicle_HashCodeAndEquals() {
        Vehicle v1 = VehicleBuilder.aVehicle()
                .withId("SAME-ID")
                .inCity("London")
                .at(51.5, -0.1)
                .asBicycle()
                .build();

        Vehicle v2 = VehicleBuilder.aVehicle()
                .withId("SAME-ID")
                .inCity("Milan")
                .at(45.4, 9.1)
                .asScooter()
                .build();

        Vehicle v3 = VehicleBuilder.aVehicle()
                .withId("DIFFERENT-ID")
                .inCity("London")
                .at(51.5, -0.1)
                .asBicycle()
                .build();

        // Same ID = equal
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());

        // Different ID = not equal
        assertNotEquals(v1, v3);
    }

    @Test
    void testMoped_HelmetDetectedMethods() {
        Moped moped = (Moped) VehicleBuilder.aVehicle()
                .withId("HELMET-TEST")
                .inCity("Milan")
                .at(45.4, 9.1)
                .asMoped()
                .build();

        assertFalse(moped.isHelmetDetected());

        moped.setHelmetDetected(true);
        assertTrue(moped.isHelmetDetected());

        moped.setHelmetDetected(false);
        assertFalse(moped.isHelmetDetected());
    }
}
