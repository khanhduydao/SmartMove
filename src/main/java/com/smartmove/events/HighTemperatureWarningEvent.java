package com.smartmove.events;

import com.smartmove.domain.vehicle.Vehicle; /**
 * Fired when vehicle temperature is high but not critical.
 */
public class HighTemperatureWarningEvent extends VehicleEvent {
    private final double temperature;
    
    public HighTemperatureWarningEvent(Vehicle vehicle, double temperature) {
        super(vehicle);
        this.temperature = temperature;
    }
    
    public double getTemperature() { return temperature; }
}
