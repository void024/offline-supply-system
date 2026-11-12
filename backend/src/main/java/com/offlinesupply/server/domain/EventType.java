package com.offlinesupply.server.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Ledger event types; identical spelling in the database CHECK and the API. */
public enum EventType {
    RECEIPT,
    PLANNED_ISSUE,
    HANDOVER,
    ADJUSTMENT;

    @Converter(autoApply = true)
    public static class EventTypeConverter implements AttributeConverter<EventType, String> {
        @Override
        public String convertToDatabaseColumn(EventType attribute) {
            return attribute == null ? null : attribute.name();
        }

        @Override
        public EventType convertToEntityAttribute(String dbData) {
            return dbData == null ? null : EventType.valueOf(dbData);
        }
    }
}
