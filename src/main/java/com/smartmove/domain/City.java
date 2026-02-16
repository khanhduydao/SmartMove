package com.smartmove.domain;

public class City {
    private final String name;

    public City(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    @Override
    public String toString() { return name; }
}
