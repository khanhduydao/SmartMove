package com.smartmove.events;

import com.smartmove.domain.vehicle.Vehicle;

import java.time.Instant;

/**
 * Base interface for all domain events.
 */
public interface Event {
    String getEventId();
    Instant getTimestamp();
}

/**
 * Base class for vehicle-related events.
 */
abstract class VehicleEvent implements Event {
    private final String eventId;
    private final Instant timestamp;
    private final Vehicle vehicle;
    
    protected VehicleEvent(Vehicle vehicle) {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.vehicle = vehicle;
    }
    
    public String getEventId() { return eventId; }
    public Instant getTimestamp() { return timestamp; }
    public Vehicle getVehicle() { return vehicle; }
}