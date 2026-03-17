package com.smartmove.events;

import com.smartmove.domain.vehicle.Vehicle; /**
 * Fired when vehicle state changes.
 */
public class VehicleStateChangedEvent extends VehicleEvent {
    private final com.smartmove.domain.vehicle.VehicleState oldState;
    private final com.smartmove.domain.vehicle.VehicleState newState;
    
    public VehicleStateChangedEvent(Vehicle vehicle,
                                    com.smartmove.domain.vehicle.VehicleState oldState,
                                    com.smartmove.domain.vehicle.VehicleState newState) {
        super(vehicle);
        this.oldState = oldState;
        this.newState = newState;
    }
    
    public com.smartmove.domain.vehicle.VehicleState getOldState() { return oldState; }
    public com.smartmove.domain.vehicle.VehicleState getNewState() { return newState; }
}
