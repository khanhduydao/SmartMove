package com.smartmove.policy;

import java.util.HashMap;
import java.util.Map;

public class PolicyFactory {
    private static final Map<String, CityPolicy> POLICIES = new HashMap<>();

    static {
        POLICIES.put("London", new LondonPolicy());
        POLICIES.put("Milan",  new MilanPolicy());
        POLICIES.put("Rome",   new RomePolicy());
    }

    public static CityPolicy getPolicy(String cityName) {
        CityPolicy policy = POLICIES.get(cityName);
        if (policy == null) {
            // Default no-op policy for unrecognized cities
            return new DefaultPolicy();
        }
        return policy;
    }

    // No-op policy for cities without specific rules
    private static class DefaultPolicy implements CityPolicy {
        @Override
        public void beforeUnlock(com.smartmove.domain.vehicle.Vehicle v,
                                  com.smartmove.domain.TelemetryData t,
                                  com.smartmove.domain.Rental r) {}
        @Override
        public double afterTrip(com.smartmove.domain.Rental r, double b) { return 0.0; }
        @Override
        public boolean validateTransition(com.smartmove.domain.vehicle.Vehicle v,
                                          com.smartmove.domain.vehicle.VehicleState to) { return true; }
        @Override
        public boolean isAllowed(com.smartmove.domain.vehicle.Vehicle v,
                                  com.smartmove.domain.GeoCoordinate gps) { return true; }
    }
}
