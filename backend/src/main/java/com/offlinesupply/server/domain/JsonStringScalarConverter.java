package com.offlinesupply.server.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * For jsonb columns that hold a human-readable STRING scalar (discrepancies.details):
 * Hibernate treats String + @JdbcTypeCode(JSON) as already-JSON text, so the prose must
 * be written as a JSON string literal and unwrapped on read.
 */
@Converter
public class JsonStringScalarConverter implements AttributeConverter<String, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("cannot serialize jsonb string scalar", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, String.class);
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse jsonb string scalar", e);
        }
    }
}
