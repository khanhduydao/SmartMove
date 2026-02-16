package com.smartmove.domain.vehicle;

import com.smartmove.domain.City;
import com.smartmove.domain.GeoCoordinate;

public class ElectricScooter extends Vehicle {
    public ElectricScooter(String id, City city, GeoCoordinate location, int batteryPercent) {
        super(id, city, location, batteryPercent);
    }

    @Override
    public String getType() { return "ElectricScooter"; }
}
