package com.offlinesupply.server.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stored lowercase in the users.role CHECK column; serialized lowercase in API payloads. */
public enum Role {
    STOREKEEPER("storekeeper"),
    SUPERVISOR("supervisor");

    private final String dbValue;

    Role(String dbValue) {
        this.dbValue = dbValue;
    }

    @JsonValue
    public String dbValue() {
        return dbValue;
    }

    public static Role fromDb(String value) {
        for (Role role : values()) {
            if (role.dbValue.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }

    @Converter(autoApply = true)
    public static class RoleConverter implements AttributeConverter<Role, String> {
        @Override
        public String convertToDatabaseColumn(Role attribute) {
            return attribute == null ? null : attribute.dbValue();
        }

        @Override
        public Role convertToEntityAttribute(String dbData) {
            return dbData == null ? null : Role.fromDb(dbData);
        }
    }
}
