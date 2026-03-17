package com.smartmove.domain.vehicle;

import com.smartmove.domain.City;
import com.smartmove.domain.GeoCoordinate;
import com.smartmove.domain.TelemetryData;
import com.smartmove.config.DomainValidator;
import com.smartmove.events.EventBus;
import com.smartmove.events.VehicleStateChangedEvent;

/**
 * Base class for all vehicle types.
 * Refactored to publish events on state changes (Event-Driven Architecture).
 */
public abstract class Vehicle {
    protected final String id;
    protected volatile VehicleState state;
    protected volatile int batteryPercent;
    protected volatile double temperatureC;
    protected volatile GeoCoordinate location;
    protected final City city;

    // Lock object for thread-safe state transitions
    private final Object stateLock = new Object();

    public Vehicle(String id, City city, GeoCoordinate location, int batteryPercent) {
        DomainValidator.validateVehicleId(id);
        DomainValidator.requireNonNull(city, "City cannot be null");
        DomainValidator.requireNonNull(location, "Location cannot be null");
        DomainValidator.validateBatteryPercent(batteryPercent);
        
        this.id = id;
        this.state = VehicleState.AVAILABLE;
        this.city = city;
        this.location = location;
        this.batteryPercent = batteryPercent;
        this.temperatureC = 20.0;
    }

    public abstract String getType();

    /**
     * Thread-safe state transition with event publishing.
     * Returns true if transition was successful.
     */
    public boolean transitionTo(VehicleState newState) {
        synchronized (stateLock) {
            if (isValidTransition(this.state, newState)) {
                VehicleState oldState = this.state;
                this.state = newState;
                
                // Publish state change event (Event-Driven Architecture)
                EventBus.getInstance().publish(
                    new VehicleStateChangedEvent(this, oldState, newState)
                );
                
                return true;
            }
            return false;
        }
    }

    /**
     * Validate if transition from one state to another is allowed.
     * State machine validation logic.
     */
    public boolean isValidTransition(VehicleState from, VehicleState to) {
        return switch (from) {
            case AVAILABLE -> to == VehicleState.RESERVED 
                           || to == VehicleState.MAINTENANCE
                           || to == VehicleState.EMERGENCY_LOCK 
                           || to == VehicleState.RELOCATING;
            
            case RESERVED -> to == VehicleState.IN_USE 
                          || to == VehicleState.AVAILABLE
                          || to == VehicleState.EMERGENCY_LOCK;
            
            case IN_USE -> to == VehicleState.AVAILABLE 
                        || to == VehicleState.MAINTENANCE
                        || to == VehicleState.EMERGENCY_LOCK;
            
            case MAINTENANCE -> to == VehicleState.AVAILABLE 
                             || to == VehicleState.EMERGENCY_LOCK;
            
            case EMERGENCY_LOCK -> to == VehicleState.MAINTENANCE 
                                || to == VehicleState.AVAILABLE;
            
            case RELOCATING -> to == VehicleState.AVAILABLE 
                            || to == VehicleState.MAINTENANCE;
            
            default -> false;
        };
    }

    /**
     * Apply telemetry data to update vehicle state.
     * Thread-safe operation.
     */
    public void applyTelemetry(TelemetryData t) {
        DomainValidator.requireNonNull(t, "Telemetry data cannot be null");
        
        synchronized (stateLock) {
            this.location = t.getGps();
            this.batteryPercent = t.getBatteryPercent();
            this.temperatureC = t.getTemperatureC();
        }
    }

    // Getters
    public String getId() { return id; }
    public VehicleState getState() { return state; }
    public int getBatteryPercent() { return batteryPercent; }
    public double getTemperatureC() { return temperatureC; }
    public GeoCoordinate getLocation() { return location; }
    public City getCity() { return city; }
    public Object getStateLock() { return stateLock; }

    @Override
    public String toString() {
        return String.format("%s[id=%s, state=%s, bat=%d%%, temp=%.1f°C, city=%s]",
                getType(), id, state, batteryPercent, temperatureC, city.getName());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Vehicle)) return false;
        return id.equals(((Vehicle) obj).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
