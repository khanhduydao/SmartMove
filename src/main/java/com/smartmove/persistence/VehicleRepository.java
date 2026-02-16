package com.smartmove.persistence;

import com.smartmove.domain.City;
import com.smartmove.domain.GeoCoordinate;
import com.smartmove.domain.vehicle.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VehicleRepository {

    private final Map<String, Vehicle> vehicleMap = new ConcurrentHashMap<>();
    private final VehicleCsvStorage storage = new VehicleCsvStorage();

    public VehicleRepository() {
        loadAll().forEach(v -> vehicleMap.put(v.getId(), v));
    }

    public List<Vehicle> loadAll() {
        return storage.loadAll();
    }

    public void saveAll() {
        storage.saveAll(new ArrayList<>(vehicleMap.values()));
    }

    public void save(Vehicle vehicle) {
        vehicleMap.put(vehicle.getId(), vehicle);
        saveAll();
    }

    public Optional<Vehicle> findById(String id) {
        return Optional.ofNullable(vehicleMap.get(id));
    }

    public List<Vehicle> findByCity(String cityName) {
        List<Vehicle> result = new ArrayList<>();
        for (Vehicle v : vehicleMap.values()) {
            if (v.getCity().getName().equalsIgnoreCase(cityName)) result.add(v);
        }
        return result;
    }

    public List<Vehicle> findByState(VehicleState state) {
        List<Vehicle> result = new ArrayList<>();
        for (Vehicle v : vehicleMap.values()) {
            if (v.getState() == state) result.add(v);
        }
        return result;
    }

    public Map<String, Vehicle> getAll() { return Collections.unmodifiableMap(vehicleMap); }

    public void put(Vehicle v) { vehicleMap.put(v.getId(), v); }

    // Inner CSV storage class
    private static class VehicleCsvStorage extends CsvFileStorage<Vehicle> {
        VehicleCsvStorage() {
            super("data/vehicles.csv",
                    "id,type,state,batteryPercent,temperatureC,lat,lon,city");
        }

        @Override
        protected Vehicle fromCsv(String line) {
            String[] p = splitCsv(line);
            if (p.length < 8) return null;
            String id = p[0].trim();
            String type = p[1].trim();
            VehicleState state = VehicleState.valueOf(p[2].trim());
            int battery = Integer.parseInt(p[3].trim());
            double temp = Double.parseDouble(p[4].trim());
            double lat = Double.parseDouble(p[5].trim());
            double lon = Double.parseDouble(p[6].trim());
            City city = new City(p[7].trim());
            GeoCoordinate loc = new GeoCoordinate(lat, lon);

            Vehicle v;
            switch (type) {
                case "Bicycle":       v = new Bicycle(id, city, loc, battery); break;
                case "ElectricScooter": v = new ElectricScooter(id, city, loc, battery); break;
                case "Moped":         v = new Moped(id, city, loc, battery); break;
                default: return null;
            }
            // Force-set fields that can't be set through constructor
            // (state may have changed from AVAILABLE default)
            if (state != VehicleState.AVAILABLE) {
                v.transitionTo(state);
            }
            return v;
        }

        @Override
        protected String toCsv(Vehicle v) {
            return String.join(",",
                    escapeCsv(v.getId()),
                    escapeCsv(v.getType()),
                    v.getState().name(),
                    String.valueOf(v.getBatteryPercent()),
                    String.format("%.1f", v.getTemperatureC()),
                    String.valueOf(v.getLocation().getLatitude()),
                    String.valueOf(v.getLocation().getLongitude()),
                    escapeCsv(v.getCity().getName())
            );
        }
    }
}
