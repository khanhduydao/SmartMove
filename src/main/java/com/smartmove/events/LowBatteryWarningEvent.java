package com.smartmove.events;

import com.smartmove.domain.vehicle.Vehicle; /**
 * Fired when vehicle battery is low but not critical.
 */
public class LowBatteryWarningEvent extends VehicleEvent {
    private final int batteryPercent;
    
    public LowBatteryWarningEvent(Vehicle vehicle, int batteryPercent) {
        super(vehicle);
        this.batteryPercent = batteryPercent;
    }
    
    public int getBatteryPercent() { return batteryPercent; }
}
