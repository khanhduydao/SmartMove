package com.smartmove.domain;

import com.smartmove.config.DomainValidator;

/**
 * Represents a city where SmartMove operates.
 * Immutable value object.
 */
public final class City {
    private final String name;

    public City(String name) {
        DomainValidator.requireNonNull(name, "City name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("City name cannot be blank");
        }
        this.name = name;
    }

    public String getName() { return name; }

    @Override
    public String toString() { return name; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof City)) return false;
        return name.equals(((City) obj).name);
    }

    @Override
    public int hashCode() { return name.hashCode(); }
}
