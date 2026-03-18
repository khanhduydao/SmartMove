package com.smartmove.factory;

import com.smartmove.config.DomainValidator;
import com.smartmove.domain.*; /**
 * Factory for creating domain entities with proper validation.
 */
public class DomainEntityFactory {
    
    public static User createUser(String id, String name) {
        DomainValidator.validateUserId(id);
        DomainValidator.requireNonNull(name, "User name cannot be null");
        
        if (name.isBlank()) {
            throw new IllegalArgumentException("User name cannot be blank");
        }
        
        return new User(id, name);
    }
    
    public static Rental createRental(String id, String userId, String vehicleId, String startTime) {
        DomainValidator.requireNonNull(id, "Rental ID cannot be null");
        DomainValidator.validateUserId(userId);
        DomainValidator.validateVehicleId(vehicleId);
        DomainValidator.requireNonNull(startTime, "Start time cannot be null");
        
        return new Rental(id, userId, vehicleId, startTime);
    }
    
    public static Payment createPayment(String id, String rentalId, 
                                         double baseAmount, double surcharges, String description) {
        DomainValidator.requireNonNull(id, "Payment ID cannot be null");
        DomainValidator.requireNonNull(rentalId, "Rental ID cannot be null");
        DomainValidator.validateAmount(baseAmount);
        DomainValidator.validateAmount(surcharges);
        DomainValidator.requireNonNull(description, "Description cannot be null");
        
        return new Payment(id, rentalId, baseAmount, surcharges, description);
    }
    
    public static City createCity(String name) {
        DomainValidator.requireNonNull(name, "City name cannot be null");
        
        if (name.isBlank()) {
            throw new IllegalArgumentException("City name cannot be blank");
        }
        
        return new City(name);
    }
    
    public static Zone createZone(String zoneId, GeoCoordinate center, 
                                   double radiusMeters, boolean restricted) {
        DomainValidator.requireNonNull(zoneId, "Zone ID cannot be null");
        DomainValidator.requireNonNull(center, "Zone center cannot be null");
        
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("Zone radius must be positive: " + radiusMeters);
        }
        
        return new Zone(zoneId, center, radiusMeters, restricted);
    }
}
