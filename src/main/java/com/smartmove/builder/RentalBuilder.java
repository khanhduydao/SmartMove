package com.smartmove.builder;

import com.smartmove.domain.*;
import com.smartmove.domain.vehicle.*;

/**
 * Builder for Rental.
 */
public class RentalBuilder {
    private String id = "R" + System.currentTimeMillis();
    private String userId;
    private String vehicleId;
    private String startTime = java.time.Instant.now().toString();
    
    public static RentalBuilder aRental() {
        return new RentalBuilder();
    }
    
    public RentalBuilder withId(String id) {
        this.id = id;
        return this;
    }
    
    public RentalBuilder forUser(String userId) {
        this.userId = userId;
        return this;
    }
    
    public RentalBuilder forVehicle(String vehicleId) {
        this.vehicleId = vehicleId;
        return this;
    }
    
    public Rental build() {
        if (userId == null) throw new IllegalStateException("User ID required");
        if (vehicleId == null) throw new IllegalStateException("Vehicle ID required");
        return new Rental(id, userId, vehicleId, startTime);
    }
}
