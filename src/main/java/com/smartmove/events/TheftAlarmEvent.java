package com.smartmove.events;

import com.smartmove.domain.vehicle.Vehicle; /**
 * Fired when vehicle moves without active rental (theft alarm).
 */
public class TheftAlarmEvent extends VehicleEvent {
    private final double distanceMoved;
    
    public TheftAlarmEvent(Vehicle vehicle, double distanceMoved) {
        super(vehicle);
        this.distanceMoved = distanceMoved;
    }
    
    public double getDistanceMoved() { return distanceMoved; }
}
