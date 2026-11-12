package com.offlinesupply.server.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum DiscrepancyStatus {
    OPEN,
    RESOLVED;

    @Converter(autoApply = true)
    public static class DiscrepancyStatusConverter implements AttributeConverter<DiscrepancyStatus, String> {
        @Override
        public String convertToDatabaseColumn(DiscrepancyStatus attribute) {
            return attribute == null ? null : attribute.name();
        }

        @Override
        public DiscrepancyStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : DiscrepancyStatus.valueOf(dbData);
        }
    }
}
