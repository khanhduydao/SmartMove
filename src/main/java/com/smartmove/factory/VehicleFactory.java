package com.smartmove.factory;

import com.smartmove.domain.*;
import com.smartmove.domain.vehicle.*;
import com.smartmove.config.DomainValidator;

/**
 * Factory for creating vehicles with validation.
 * Centralizes object creation logic and validation.
 */
public class VehicleFactory {
    
    /**
     * Create vehicle from type string and parameters.
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static Vehicle createVehicle(String type, String id, City city, 
                                         GeoCoordinate location, int batteryPercent) {
        // Validation
        DomainValidator.validateVehicleId(id);
        DomainValidator.requireNonNull(city, "City cannot be null");
        DomainValidator.requireNonNull(location, "Location cannot be null");
        DomainValidator.validateBatteryPercent(batteryPercent);
        
        return switch (type.toLowerCase()) {
            case "bicycle" -> new Bicycle(id, city, location, batteryPercent);
            case "electricscooter", "scooter" -> new ElectricScooter(id, city, location, batteryPercent);
            case "moped" -> new Moped(id, city, location, batteryPercent);
            default -> throw new IllegalArgumentException("Unknown vehicle type: " + type);
        };
    }
    
    /**
     * Create a vehicle with default battery level (80%).
     */
    public static Vehicle createVehicle(String type, String id, City city, GeoCoordinate location) {
        return createVehicle(type, id, city, location, 80);
    }
}

