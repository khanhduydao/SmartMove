package com.smartmove.builder;

import com.smartmove.domain.City;
import com.smartmove.domain.GeoCoordinate;
import com.smartmove.domain.TelemetryData;
import com.smartmove.domain.vehicle.Bicycle;
import com.smartmove.domain.vehicle.ElectricScooter;
import com.smartmove.domain.vehicle.Moped;
import com.smartmove.domain.vehicle.Vehicle; /**
 * Builder pattern for creating test vehicles.
 * Improves testability by providing fluent API for object creation.
 */
public class VehicleBuilder {
    private String id;
    private City city;
    private GeoCoordinate location;
    private int batteryPercent = 80;
    private double temperatureC = 20.0;
    private VehicleType type = VehicleType.BICYCLE;
    
    public enum VehicleType {
        BICYCLE, ELECTRIC_SCOOTER, MOPED
    }
    
    public static VehicleBuilder aVehicle() {
        return new VehicleBuilder();
    }
    
    public VehicleBuilder withId(String id) {
        this.id = id;
        return this;
    }
    
    public VehicleBuilder inCity(String cityName) {
        this.city = new City(cityName);
        return this;
    }
    
    public VehicleBuilder inCity(City city) {
        this.city = city;
        return this;
    }
    
    public VehicleBuilder at(double lat, double lon) {
        this.location = new GeoCoordinate(lat, lon);
        return this;
    }
    
    public VehicleBuilder at(GeoCoordinate location) {
        this.location = location;
        return this;
    }
    
    public VehicleBuilder withBattery(int percent) {
        this.batteryPercent = percent;
        return this;
    }
    
    public VehicleBuilder withTemperature(double celsius) {
        this.temperatureC = celsius;
        return this;
    }
    
    public VehicleBuilder ofType(VehicleType type) {
        this.type = type;
        return this;
    }
    
    public VehicleBuilder asBicycle() {
        this.type = VehicleType.BICYCLE;
        return this;
    }
    
    public VehicleBuilder asScooter() {
        this.type = VehicleType.ELECTRIC_SCOOTER;
        return this;
    }
    
    public VehicleBuilder asMoped() {
        this.type = VehicleType.MOPED;
        return this;
    }
    
    public Vehicle build() {
        validateRequiredFields();
        
        Vehicle vehicle = switch (type) {
            case BICYCLE -> new Bicycle(id, city, location, batteryPercent);
            case ELECTRIC_SCOOTER -> new ElectricScooter(id, city, location, batteryPercent);
            case MOPED -> new Moped(id, city, location, batteryPercent);
        };
        
        // Apply optional temperature if different from default
        if (temperatureC != 20.0) {
            TelemetryData telemetry = new TelemetryData(
                    java.time.Instant.now().toString(),
                    location,
                    batteryPercent,
                    temperatureC,
                    false
            );
            vehicle.applyTelemetry(telemetry);
        }
        
        return vehicle;
    }
    
    private void validateRequiredFields() {
        if (id == null) throw new IllegalStateException("Vehicle ID is required");
        if (city == null) throw new IllegalStateException("City is required");
        if (location == null) throw new IllegalStateException("Location is required");
    }
}
