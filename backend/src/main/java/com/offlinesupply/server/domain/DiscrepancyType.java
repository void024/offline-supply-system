package com.offlinesupply.server.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum DiscrepancyType {
    NEGATIVE_BALANCE,
    UNPLANNED_HANDOVER,
    PLAN_EXCEEDED,
    UNRESOLVED_REFERENCE;

    @Converter(autoApply = true)
    public static class DiscrepancyTypeConverter implements AttributeConverter<DiscrepancyType, String> {
        @Override
        public String convertToDatabaseColumn(DiscrepancyType attribute) {
            return attribute == null ? null : attribute.name();
        }

        @Override
        public DiscrepancyType convertToEntityAttribute(String dbData) {
            return dbData == null ? null : DiscrepancyType.valueOf(dbData);
        }
    }
}
