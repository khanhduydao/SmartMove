package com.smartmove.domain.vehicle;

import com.smartmove.domain.City;
import com.smartmove.domain.GeoCoordinate;
import com.smartmove.domain.TelemetryData;
import com.smartmove.config.DomainValidator;
import com.smartmove.events.EventBus;
import com.smartmove.events.VehicleStateChangedEvent;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Base class for all vehicle types.
 * Refactored to publish events on state changes (Event-Driven Architecture).
 */
public abstract class Vehicle {
    protected final String id;
    protected final AtomicReference<VehicleState> state;
    protected final AtomicInteger batteryPercent;
    protected final AtomicReference<Double> temperatureC;
    protected final AtomicReference<GeoCoordinate> location;
    protected final City city;

    // Lock object for thread-safe state transitions
    private final Object stateLock = new Object();

    public Vehicle(String id, City city, GeoCoordinate location, int batteryPercent) {
        DomainValidator.validateVehicleId(id);
        DomainValidator.requireNonNull(city, "City cannot be null");
        DomainValidator.requireNonNull(location, "Location cannot be null");
        DomainValidator.validateBatteryPercent(batteryPercent);
        
        this.id = id;
        this.state = new AtomicReference<>(VehicleState.AVAILABLE);
        this.city = city;
        this.location = new AtomicReference<>(location);
        this.batteryPercent = new AtomicInteger(batteryPercent);
        this.temperatureC = new AtomicReference<>(20.0);
    }

    public abstract String getType();

    /**
     * Thread-safe state transition with event publishing.
     * Returns true if transition was successful.
     */
    public boolean transitionTo(VehicleState newState) {
        synchronized (stateLock) {
            VehicleState currentState = this.state.get();
            if (isValidTransition(currentState, newState)) {
                VehicleState oldState = this.state.getAndSet(newState);

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

        };
    }

    /**
     * Apply telemetry data to update vehicle state.
     * Thread-safe operation.
     */
    public void applyTelemetry(TelemetryData t) {
        DomainValidator.requireNonNull(t, "Telemetry data cannot be null");
        
        synchronized (stateLock) {
            this.location.set(t.getGps());
            this.batteryPercent.set(t.getBatteryPercent());
            this.temperatureC.set(t.getTemperatureC());
        }
    }

    // Getters
    public String getId() { return id; }
    public VehicleState getState() { return state.get(); }
    public int getBatteryPercent() { return batteryPercent.get(); }
    public double getTemperatureC() { return temperatureC.get(); }
    public GeoCoordinate getLocation() { return location.get(); }
    public City getCity() { return city; }
    public Object getStateLock() { return stateLock; }

    @Override
    public String toString() {
        return String.format("%s[id=%s, state=%s, bat=%d%%, temp=%.1f°C, city=%s]",
                getType(), id, state.get(), batteryPercent.get(), temperatureC.get(), city.getName());
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
