package com.offlinesupply.server.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Quarantine/report kinds. The Prompt 2 schema CHECK lists the first four;
 * VALIDATION_QUARANTINE is an explicit Prompt 4 correction: contract endpoint 3
 * requires per-event validation failures (unknown item, bad quantity, missing
 * reason) to be QUARANTINED with a submission_issues row, but supplied no kind
 * for them. Adding the kind is the smallest honest fix; it is documented in the
 * companion document.
 */
public enum IssueKind {
    PAYLOAD_CONFLICT,
    SEQ_CONFLICT,
    ROLE_QUARANTINE,
    ISSUER_QUARANTINE,
    VALIDATION_QUARANTINE;

    @Converter(autoApply = true)
    public static class IssueKindConverter implements AttributeConverter<IssueKind, String> {
        @Override
        public String convertToDatabaseColumn(IssueKind attribute) {
            return attribute == null ? null : attribute.name();
        }

        @Override
        public IssueKind convertToEntityAttribute(String dbData) {
            return dbData == null ? null : IssueKind.valueOf(dbData);
        }
    }
}
