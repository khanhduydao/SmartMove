package com.smartmove.events;

import com.smartmove.domain.vehicle.Vehicle; /**
 * Fired when vehicle temperature exceeds critical threshold.
 */
public class CriticalTemperatureEvent extends VehicleEvent {
    private final double temperature;
    
    public CriticalTemperatureEvent(Vehicle vehicle, double temperature) {
        super(vehicle);
        this.temperature = temperature;
    }
    
    public double getTemperature() { return temperature; }
}
