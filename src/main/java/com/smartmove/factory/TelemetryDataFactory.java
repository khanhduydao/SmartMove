package com.smartmove.factory;

import com.smartmove.config.DomainValidator;
import com.smartmove.domain.GeoCoordinate;
import com.smartmove.domain.TelemetryData; /**
 * Factory for creating telemetry data with validation.
 */
public class TelemetryDataFactory {
    
    public static TelemetryData create(String timestamp, double lat, double lon,
                                        int battery, double temp, boolean helmet) {
        DomainValidator.validateCoordinate(lat, lon);
        DomainValidator.validateBatteryPercent(battery);
        DomainValidator.validateTemperature(temp);
        
        GeoCoordinate gps = new GeoCoordinate(lat, lon);
        return new TelemetryData(timestamp, gps, battery, temp, helmet);
    }
    
    public static TelemetryData createNow(double lat, double lon, int battery, double temp) {
        return create(java.time.Instant.now().toString(), lat, lon, battery, temp, false);
    }
}
