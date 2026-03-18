package com.smartmove.constants;

/**
 * Centralized constants to eliminate magic strings and numbers.
 * Improves maintainability by providing a single source of truth.
 */
public final class SmartMoveConstants {

    private SmartMoveConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    // ═══════════════════════════════════════════════════════════════════
    // TELEMETRY THRESHOLDS
    // ═══════════════════════════════════════════════════════════════════
    
    public static final double CRITICAL_TEMPERATURE_C = 60.0;
    public static final double WARNING_TEMPERATURE_C = 50.0;
    public static final int CRITICAL_BATTERY_PERCENT = 5;
    public static final int LOW_BATTERY_PERCENT = 15;
    public static final double THEFT_MOVEMENT_THRESHOLD_METERS = 10.0;

    // ═══════════════════════════════════════════════════════════════════
    // CITY POLICY SETTINGS
    // ═══════════════════════════════════════════════════════════════════
    
    // London
    public static final double LONDON_CONGESTION_CHARGE = 3.50;
    public static final int LONDON_MIN_BATTERY_PERCENT = 15;
    
    // Milan
    public static final double MILAN_CITY_CENTER_SURCHARGE = 1.50;
    public static final int MILAN_MIN_BATTERY_PERCENT = 15;
    
    // Rome
    public static final int ROME_MIN_BATTERY_PERCENT = 15;

    // ═══════════════════════════════════════════════════════════════════
    // FILE PATHS
    // ═══════════════════════════════════════════════════════════════════
    
    public static final String DATA_DIR = "data";
    public static final String VEHICLES_CSV = DATA_DIR + "/vehicles.csv";
    public static final String USERS_CSV = DATA_DIR + "/users.csv";
    public static final String RENTALS_CSV = DATA_DIR + "/rentals.csv";
    public static final String PAYMENTS_CSV = DATA_DIR + "/payments.csv";
    public static final String AUDIT_LOG_CSV = DATA_DIR + "/audit_log.csv";

    // ═══════════════════════════════════════════════════════════════════
    // AUDIT SETTINGS
    // ═══════════════════════════════════════════════════════════════════
    
    public static final String GENESIS_CHECKSUM = "0000000000000000";
    public static final long CHECKSUM_HASH_SEED = 5381L;

    // ═══════════════════════════════════════════════════════════════════
    // QUEUE SETTINGS
    // ═══════════════════════════════════════════════════════════════════
    
    public static final int TELEMETRY_QUEUE_CAPACITY = 50_000;
    public static final long TELEMETRY_POLL_TIMEOUT_MS = 100L;

    // ═══════════════════════════════════════════════════════════════════
    // PRICING
    // ═══════════════════════════════════════════════════════════════════
    
    public static final double BASE_RENTAL_AMOUNT = 6.00;

    // ═══════════════════════════════════════════════════════════════════
    // SEQUENCE ID SEEDS
    // ═══════════════════════════════════════════════════════════════════
    
    public static final long RENTAL_ID_SEED = 1000L;
    public static final long PAYMENT_ID_SEED = 1000L;
    public static final long AUDIT_SEQ_SEED = 0L;
}
