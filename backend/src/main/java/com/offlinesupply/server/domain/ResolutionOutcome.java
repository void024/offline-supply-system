package com.offlinesupply.server.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public enum ResolutionOutcome {
    NO_CHANGE,
    ADJUSTMENT;

    @Converter(autoApply = true)
    public static class ResolutionOutcomeConverter implements AttributeConverter<ResolutionOutcome, String> {
        @Override
        public String convertToDatabaseColumn(ResolutionOutcome attribute) {
            return attribute == null ? null : attribute.name();
        }

        @Override
        public ResolutionOutcome convertToEntityAttribute(String dbData) {
            return dbData == null ? null : ResolutionOutcome.valueOf(dbData);
        }
    }
}
