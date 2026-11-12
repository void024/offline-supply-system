package com.offlinesupply.server.domain;

/** Per-event outcome of POST /api/v1/events. API-level only; never persisted. */
public enum PushStatus {
    ACCEPTED,
    DUPLICATE,
    CONFLICT,
    QUARANTINED
}
