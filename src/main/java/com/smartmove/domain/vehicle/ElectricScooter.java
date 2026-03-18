package com.smartmove.domain.vehicle;

import com.smartmove.domain.City;
import com.smartmove.domain.GeoCoordinate; /**
 * Electric Scooter vehicle type.
 */
public final class ElectricScooter extends Vehicle {
    public ElectricScooter(String id, City city, GeoCoordinate location, int batteryPercent) {
        super(id, city, location, batteryPercent);
    }

    @Override
    public String getType() { return "ElectricScooter"; }
}
