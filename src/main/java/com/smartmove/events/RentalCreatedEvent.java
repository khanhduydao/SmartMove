package com.smartmove.events;

import java.time.Instant; /**
 * Fired when rental is created.
 */
public class RentalCreatedEvent implements Event {
    private final String eventId;
    private final Instant timestamp;
    private final String rentalId;
    private final String userId;
    private final String vehicleId;
    
    public RentalCreatedEvent(String rentalId, String userId, String vehicleId) {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.rentalId = rentalId;
        this.userId = userId;
        this.vehicleId = vehicleId;
    }
    
    public String getEventId() { return eventId; }
    public Instant getTimestamp() { return timestamp; }
    public String getRentalId() { return rentalId; }
    public String getUserId() { return userId; }
    public String getVehicleId() { return vehicleId; }
}
