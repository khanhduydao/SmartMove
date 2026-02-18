package com.smartmove.util;

import com.smartmove.domain.*;
import com.smartmove.domain.vehicle.*;
import com.smartmove.persistence.*;

import java.util.Arrays;
import java.util.List;

/**
 * Seeds the system with sample vehicles, users, and cities for demo/testing.
 */
public class DataSeeder {

    public static void seed(VehicleRepository vehicleRepo, UserRepository userRepo) {
        System.out.println("[DataSeeder] Seeding initial data...");

        // ─── Users ────────────────────────────────────────────────────────
        List<User> users = Arrays.asList(
                new User("U001", "Alice Johnson"),
                new User("U002", "Bob Smith"),
                new User("U003", "Carlos Rossi"),
                new User("U004", "Elena Bianchi"),
                new User("U005", "James Davies")
        );
        users.forEach(userRepo::save);

        // ─── Cities ───────────────────────────────────────────────────────
        City london = new City("London");
        City milan  = new City("Milan");
        City rome   = new City("Rome");

        // ─── London fleet ─────────────────────────────────────────────────
        vehicleRepo.put(new Bicycle("LON-B001", london,
                new GeoCoordinate(51.5074, -0.1278), 85));
        vehicleRepo.put(new Bicycle("LON-B002", london,
                new GeoCoordinate(51.5200, -0.0850), 72));
        vehicleRepo.put(new ElectricScooter("LON-ES001", london,
                new GeoCoordinate(51.5155, -0.1168), 90));
        vehicleRepo.put(new ElectricScooter("LON-ES002", london,
                new GeoCoordinate(51.5010, -0.1247), 45));
        vehicleRepo.put(new Moped("LON-M001", london,
                new GeoCoordinate(51.5000, -0.1250), 78));

        // ─── Milan fleet ──────────────────────────────────────────────────
        vehicleRepo.put(new Bicycle("MIL-B001", milan,
                new GeoCoordinate(45.4642, 9.1900), 95));
        vehicleRepo.put(new ElectricScooter("MIL-ES001", milan,
                new GeoCoordinate(45.4654, 9.1866), 60));
        vehicleRepo.put(new Moped("MIL-M001", milan,
                new GeoCoordinate(45.4730, 9.1920), 88));
        vehicleRepo.put(new Moped("MIL-M002", milan,
                new GeoCoordinate(45.4600, 9.1800), 30));

        // ─── Rome fleet ───────────────────────────────────────────────────
        vehicleRepo.put(new Bicycle("ROM-B001", rome,
                new GeoCoordinate(41.9300, 12.5200), 92));
        vehicleRepo.put(new ElectricScooter("ROM-ES001", rome,
                new GeoCoordinate(41.9350, 12.5150), 55));
        vehicleRepo.put(new ElectricScooter("ROM-ES002", rome,
                new GeoCoordinate(41.9400, 12.5100), 70));
        vehicleRepo.put(new Moped("ROM-M001", rome,
                new GeoCoordinate(41.9450, 12.5050), 80));

        // Save all vehicles
        vehicleRepo.saveAll();

        System.out.println("[DataSeeder] Seeded "
                + vehicleRepo.getAll().size() + " vehicles and "
                + users.size() + " users.");
    }
}
