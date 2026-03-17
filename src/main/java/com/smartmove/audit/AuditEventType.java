package com.smartmove.audit;

/**
 * Enum for audit event types.
 * Eliminates magic strings in audit log entries.
 */
public enum AuditEventType {
    // Vehicle events
    VEHICLE_RESERVED("VEHICLE_RESERVED"),
    VEHICLE_STATE_CHANGED("VEHICLE_STATE_CHANGED"),
    VEHICLE_MAINTENANCE("VEHICLE_MAINTENANCE"),
    VEHICLE_THROTTLED("VEHICLE_THROTTLED"),
    
    // Rental events
    RENTAL_STARTED("RENTAL_STARTED"),
    RENTAL_ENDED("RENTAL_ENDED"),
    EMERGENCY_RENTAL_END("EMERGENCY_RENTAL_END"),
    
    // Payment events
    PAYMENT_PROCESSED("PAYMENT_PROCESSED"),
    
    // Security events
    EMERGENCY_LOCK("EMERGENCY_LOCK"),
    THEFT_ALARM("THEFT_ALARM"),
    
    // System events
    LOW_BATTERY_WARNING("LOW_BATTERY_WARNING"),
    CRITICAL_TEMP_ALERT("CRITICAL_TEMP"),
    POLICY_VIOLATION("POLICY_VIOLATION");
    
    private final String eventName;
    
    AuditEventType(String eventName) {
        this.eventName = eventName;
    }
    
    public String getEventName() {
        return eventName;
    }
    
    @Override
    public String toString() {
        return eventName;
    }
    
    public static AuditEventType fromString(String text) {
        for (AuditEventType type : AuditEventType.values()) {
            if (type.eventName.equalsIgnoreCase(text)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No audit event type with name: " + text);
    }
}
