package com.smartmove.config;

/**
 * Validator for domain objects.
 * Defensive programming tactic.
 */
public class DomainValidator {
    
    public static void validateVehicleId(String vehicleId) {
        if (vehicleId == null || vehicleId.isBlank()) {
            throw new IllegalArgumentException("Vehicle ID cannot be null or blank");
        }
        if (vehicleId.length() > 50) {
            throw new IllegalArgumentException("Vehicle ID too long: " + vehicleId);
        }
    }
    
    public static void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be null or blank");
        }
    }
    
    public static void validateBatteryPercent(int battery) {
        if (battery < 0 || battery > 100) {
            throw new IllegalArgumentException("Invalid battery percent: " + battery);
        }
    }
    
    public static void validateTemperature(double temp) {
        if (temp < -50 || temp > 200) {
            throw new IllegalArgumentException("Temperature out of valid range: " + temp);
        }
    }
    
    public static void validateCoordinate(double lat, double lon) {
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Invalid latitude: " + lat);
        }
        if (lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Invalid longitude: " + lon);
        }
    }
    
    public static void validateAmount(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative: " + amount);
        }
    }
    
    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }
}
