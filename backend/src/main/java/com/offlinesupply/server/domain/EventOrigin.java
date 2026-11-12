package com.offlinesupply.server.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** DEVICE = pushed by an enrolled device; SERVER = written by a resolution. */
public enum EventOrigin {
    DEVICE,
    SERVER;

    @Converter(autoApply = true)
    public static class EventOriginConverter implements AttributeConverter<EventOrigin, String> {
        @Override
        public String convertToDatabaseColumn(EventOrigin attribute) {
            return attribute == null ? null : attribute.name();
        }

        @Override
        public EventOrigin convertToEntityAttribute(String dbData) {
            return dbData == null ? null : EventOrigin.valueOf(dbData);
        }
    }
}
