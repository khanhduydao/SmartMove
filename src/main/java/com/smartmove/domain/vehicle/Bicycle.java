package com.smartmove.domain.vehicle;

import com.smartmove.domain.City;
import com.smartmove.domain.GeoCoordinate;

public class Bicycle extends Vehicle {
    public Bicycle(String id, City city, GeoCoordinate location, int batteryPercent) {
        super(id, city, location, batteryPercent);
    }

    @Override
    public String getType() { return "Bicycle"; }
}
