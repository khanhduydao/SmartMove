package com.smartmove.persistence;

import com.smartmove.domain.Rental;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RentalRepository {
    private final Map<String, Rental> rentalMap = new ConcurrentHashMap<>();
    private final RentalCsvStorage storage = new RentalCsvStorage();

    public RentalRepository() {
        storage.loadAll().forEach(r -> rentalMap.put(r.getId(), r));
    }

    public void save(Rental rental) {
        rentalMap.put(rental.getId(), rental);
        storage.saveAll(new ArrayList<>(rentalMap.values()));
    }

    public Optional<Rental> findById(String id) {
        return Optional.ofNullable(rentalMap.get(id));
    }

    public Optional<Rental> findActiveByVehicleId(String vehicleId) {
        return rentalMap.values().stream()
                .filter(r -> r.isActive() && r.getVehicleId().equals(vehicleId))
                .findFirst();
    }

    public Map<String, Rental> getAll() { return Collections.unmodifiableMap(rentalMap); }
}
