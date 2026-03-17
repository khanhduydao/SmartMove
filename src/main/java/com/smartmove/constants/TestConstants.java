package com.smartmove.constants;

/**
 * Test data identifiers used across test suite.
 * Eliminates duplicate string literals in tests.
 */
public final class TestConstants {

    private TestConstants() {
        throw new AssertionError("Cannot instantiate test constants class");
    }

    // ═══════════════════════════════════════════════════════════════════
    // VEHICLE IDS
    // ═══════════════════════════════════════════════════════════════════
    
    // London
    public static final String LON_BICYCLE_001 = "LON-B001";
    public static final String LON_BICYCLE_002 = "LON-B002";
    public static final String LON_SCOOTER_001 = "LON-ES001";
    public static final String LON_SCOOTER_002 = "LON-ES002";
    public static final String LON_MOPED_001 = "LON-M001";
    
    // Milan
    public static final String MIL_BICYCLE_001 = "MIL-B001";
    public static final String MIL_SCOOTER_001 = "MIL-ES001";
    public static final String MIL_MOPED_001 = "MIL-M001";
    public static final String MIL_MOPED_002 = "MIL-M002";
    
    // Rome
    public static final String ROM_BICYCLE_001 = "ROM-B001";
    public static final String ROM_SCOOTER_001 = "ROM-ES001";
    public static final String ROM_SCOOTER_002 = "ROM-ES002";
    public static final String ROM_MOPED_001 = "ROM-M001";

    // ═══════════════════════════════════════════════════════════════════
    // USER IDS
    // ═══════════════════════════════════════════════════════════════════
    
    public static final String USER_001 = "U001";
    public static final String USER_002 = "U002";
    public static final String USER_003 = "U003";
    public static final String USER_004 = "U004";
    public static final String USER_005 = "U005";

    // ═══════════════════════════════════════════════════════════════════
    // CITY NAMES
    // ═══════════════════════════════════════════════════════════════════
    
    public static final String CITY_LONDON = "London";
    public static final String CITY_MILAN = "Milan";
    public static final String CITY_ROME = "Rome";
}
