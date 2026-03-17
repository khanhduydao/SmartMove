package com.smartmove.events;

import com.smartmove.domain.vehicle.Vehicle; /**
 * Fired when vehicle battery is critically low.
 */
public class CriticalBatteryEvent extends VehicleEvent {
    private final int batteryPercent;
    
    public CriticalBatteryEvent(Vehicle vehicle, int batteryPercent) {
        super(vehicle);
        this.batteryPercent = batteryPercent;
    }
    
    public int getBatteryPercent() { return batteryPercent; }
}
