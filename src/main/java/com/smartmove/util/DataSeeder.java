package com.smartmove.util;

import com.smartmove.config.LoggerFactory;
import java.util.logging.Logger;

import com.smartmove.domain.*;
import com.smartmove.domain.vehicle.*;
import com.smartmove.persistence.*;
import static com.smartmove.constants.TestConstants.*;

import java.util.Arrays;
import java.util.List;

/**
 * Seeds the system with sample vehicles, users, and cities for demo/testing.
 */
public class DataSeeder {
    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    public static void seed(VehicleRepository vehicleRepo, UserRepository userRepo) {
        logger.info("[DataSeeder] Seeding initial data...");

        // ─── Users ────────────────────────────────────────────────────────
        List<User> users = Arrays.asList(
                new User(USER_001, "Alice Johnson"),
                new User(USER_002, "Bob Smith"),
                new User(USER_003, "Carlos Rossi"),
                new User(USER_004, "Elena Bianchi"),
                new User(USER_005, "James Davies")
        );
        users.forEach(userRepo::save);

        // ─── Cities ───────────────────────────────────────────────────────
        City london = new City(CITY_LONDON);
        City milan  = new City(CITY_MILAN);
        City rome   = new City(CITY_ROME);

        // ─── London fleet ─────────────────────────────────────────────────
        vehicleRepo.put(new Bicycle(LON_BICYCLE_001, london,
                new GeoCoordinate(51.5074, -0.1278), 85));
        vehicleRepo.put(new Bicycle(LON_BICYCLE_002, london,
                new GeoCoordinate(51.5200, -0.0850), 72));
        vehicleRepo.put(new ElectricScooter(LON_SCOOTER_001, london,
                new GeoCoordinate(51.5155, -0.1168), 90));
        vehicleRepo.put(new ElectricScooter(LON_SCOOTER_002, london,
                new GeoCoordinate(51.5010, -0.1247), 45));
        vehicleRepo.put(new Moped(LON_MOPED_001, london,
                new GeoCoordinate(51.5000, -0.1250), 78));

        // ─── Milan fleet ──────────────────────────────────────────────────
        vehicleRepo.put(new Bicycle(MIL_BICYCLE_001, milan,
                new GeoCoordinate(45.4642, 9.1900), 95));
        vehicleRepo.put(new ElectricScooter(MIL_SCOOTER_001, milan,
                new GeoCoordinate(45.4654, 9.1866), 60));
        vehicleRepo.put(new Moped(MIL_MOPED_001, milan,
                new GeoCoordinate(45.4730, 9.1920), 88));
        vehicleRepo.put(new Moped(MIL_MOPED_002, milan,
                new GeoCoordinate(45.4600, 9.1800), 30));

        // ─── Rome fleet ───────────────────────────────────────────────────
        vehicleRepo.put(new Bicycle(ROM_BICYCLE_001, rome,
                new GeoCoordinate(41.9300, 12.5200), 92));
        vehicleRepo.put(new ElectricScooter(ROM_SCOOTER_001, rome,
                new GeoCoordinate(41.9350, 12.5150), 55));
        vehicleRepo.put(new ElectricScooter(ROM_SCOOTER_002, rome,
                new GeoCoordinate(41.9400, 12.5100), 70));
        vehicleRepo.put(new Moped(ROM_MOPED_001, rome,
                new GeoCoordinate(41.9450, 12.5050), 80));

        // Save all vehicles
        vehicleRepo.saveAll();

      
logger.info(() -> "[DataSeeder] Seeded "
        + vehicleRepo.getAll().size() + " vehicles and "
        + users.size() + " users.");

    }
}
