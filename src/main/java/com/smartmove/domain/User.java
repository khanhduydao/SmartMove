package com.smartmove.domain;

import com.smartmove.config.DomainValidator;

/**
 * User entity.
 */
public final class User {
    private final String id;
    private final String name;

    public User(String id, String name) {
        DomainValidator.validateUserId(id);
        DomainValidator.requireNonNull(name, "User name cannot be null");
        
        if (name.isBlank()) {
            throw new IllegalArgumentException("User name cannot be blank");
        }
        
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }

    @Override
    public String toString() { return String.format("User[%s, %s]", id, name); }
}
