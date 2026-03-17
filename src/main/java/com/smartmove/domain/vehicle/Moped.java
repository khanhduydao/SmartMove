package com.smartmove.domain.vehicle;

import com.smartmove.domain.City;
import com.smartmove.domain.GeoCoordinate; /**
 * Moped vehicle type with helmet detection capability.
 */
public final class Moped extends Vehicle {
    private volatile boolean helmetDetected = false;

    public Moped(String id, City city, GeoCoordinate location, int batteryPercent) {
        super(id, city, location, batteryPercent);
    }

    public boolean isHelmetDetected() { return helmetDetected; }
    
    public synchronized void setHelmetDetected(boolean helmetDetected) {
        this.helmetDetected = helmetDetected;
    }

    @Override
    public String getType() { return "Moped"; }
}
