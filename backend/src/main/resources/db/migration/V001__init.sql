-- V001: Prompt 2 contract schema (9 tables) + two explicitly documented engineering additions.
--
-- ADDITION 1 (sessions): the contract fixes cookie-based sessions but leaves session
-- storage to the implementation; the sessions table below is that detail.
--
-- CORRECTION 2 (VALIDATION_QUARANTINE): contract endpoint 3 requires events that fail
-- per-event validation to be QUARANTINED with a submission_issues row, but the schema's
-- kind CHECK lists no kind for validation failures. 'VALIDATION_QUARANTINE' is added as
-- the smallest honest fix (flagged in the Prompt 4 companion document).
--
-- Conventions (contract): UUID primary keys; created_at/updated_at on every table;
-- nothing in v1 deletes, so no deleted_at. No constraint forbids negative computed stock.

create table users (
    id            uuid primary key,
    username      text not null unique,
    password_hash text not null,                -- sensitive: never logged or returned
    role          text not null check (role in ('storekeeper', 'supervisor')),
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

create table devices (
    id                 uuid primary key,        -- client-generated device_id
    label              text,
    device_key_hash    text not null,           -- sensitive: SHA-256 of the one-time key
    enrolled_by_user_id uuid not null references users (id),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now()
);

create table items (
    id          uuid primary key,
    name        text not null,
    unit        text not null,
    opening_qty integer not null default 0,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create table events (
    -- The append-only ledger. No UPDATE, no DELETE, ever.
    event_id            uuid primary key,        -- idempotency key
    origin              text not null check (origin in ('DEVICE', 'SERVER')),
    server_seq          bigint not null unique,  -- assigned only via sync_counters
    device_id           uuid references devices (id),
    device_seq          integer,
    recorded_by_user_id uuid references users (id),
    pushed_by_user_id   uuid not null references users (id),
    type                text not null check (type in ('RECEIPT', 'PLANNED_ISSUE', 'HANDOVER', 'ADJUSTMENT')),
    item_id             uuid not null references items (id),
    quantity            integer not null,
    planned_issue_id    uuid,                    -- soft link, deliberately NOT a foreign key (R4)
    reason              text,
    occurred_at         timestamptz not null,    -- device clock for DEVICE: advisory
    received_at         timestamptz not null default now(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint events_quantity_check check (
        (type in ('RECEIPT', 'PLANNED_ISSUE', 'HANDOVER') and quantity > 0)
        or (type = 'ADJUSTMENT' and quantity <> 0)),
    constraint events_reason_check check (type <> 'ADJUSTMENT' or reason is not null),
    constraint events_provenance_check check (
        (origin = 'DEVICE' and device_id is not null and device_seq is not null and recorded_by_user_id is not null)
        or (origin = 'SERVER' and device_id is null and device_seq is null))
);
create unique index events_device_seq_uniq on events (device_id, device_seq);  -- NULLs never collide
create index events_item_seq_idx on events (item_id, server_seq);
create index events_server_seq_idx on events (server_seq);

create table resolutions (
    -- The audited review record (one per resolved discrepancy).
    resolution_id       uuid primary key,        -- client-generated idempotency key
    discrepancy_id      uuid not null,           -- FK added after discrepancies exists (circular pair)
    outcome             text not null check (outcome in ('NO_CHANGE', 'ADJUSTMENT')),
    reason              text not null,
    quantity_delta      integer,
    created_by_user_id  uuid not null references users (id),
    device_id           uuid not null references devices (id),
    adjustment_event_id uuid references events (event_id),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint resolutions_delta_check check (
        (outcome = 'ADJUSTMENT' and quantity_delta is not null and quantity_delta <> 0)
        or (outcome = 'NO_CHANGE' and quantity_delta is null))
);

create table discrepancies (
    id                        uuid primary key,
    item_id                   uuid not null references items (id),
    type                      text not null check (type in ('NEGATIVE_BALANCE', 'UNPLANNED_HANDOVER', 'PLAN_EXCEEDED', 'UNRESOLVED_REFERENCE')),
    evidence_key              text not null,     -- stable identity of the specific facts
    evidence_seq              bigint not null,   -- highest server_seq of covered facts (no-reopen rule)
    details                   jsonb not null,    -- human-readable explanation (jsonb string scalar)
    status                    text not null check (status in ('OPEN', 'RESOLVED')),
    resolved_by_resolution_id uuid references resolutions (resolution_id),
    detected_at               timestamptz not null,
    created_at                timestamptz not null default now(),
    updated_at                timestamptz not null default now()
);
alter table resolutions
    add constraint resolutions_discrepancy_fk foreign key (discrepancy_id) references discrepancies (id);
-- One OPEN row per (type, evidence_key); RESOLVED history never blocks a genuinely new row.
create unique index discrepancies_open_uniq on discrepancies (type, evidence_key) where status = 'OPEN';

create table issuer_designations (
    -- Singleton table: one row, ever; a concurrent second INSERT fails -> 409.
    singleton_key         smallint primary key check (singleton_key = 1),
    device_id             uuid not null references devices (id),
    designated_by_user_id uuid not null references users (id),
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now()
);

create table sync_counters (
    -- Singleton ordering row; locked FOR UPDATE by every ledger-writing transaction.
    singleton_key smallint primary key check (singleton_key = 1),
    next_value    bigint not null,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);
insert into sync_counters (singleton_key, next_value) values (1, 1);

create table submission_issues (
    -- The quarantine/report table: nothing is silently dropped, retries never multiply rows.
    id               uuid primary key,
    device_id        uuid not null references devices (id),
    claimed_event_id uuid,
    kind             text not null check (kind in ('PAYLOAD_CONFLICT', 'SEQ_CONFLICT', 'ROLE_QUARANTINE', 'ISSUER_QUARANTINE', 'VALIDATION_QUARANTINE')),
    payload          jsonb not null,             -- the verbatim rejected submission
    detail           text,
    detected_at      timestamptz not null,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint submission_issues_dedupe_uniq unique (claimed_event_id, kind)
);

create table sessions (
    -- ADDITION (implementation detail, see header): server-side state behind the
    -- HttpOnly cookie. Only the SHA-256 hash of the bearer token is stored.
    id         uuid primary key,
    token_hash text not null unique,
    user_id    uuid not null references users (id),
    device_id  uuid not null references devices (id),
    expires_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Seed data (contract: "Seeded with the six demo items via migration").
insert into items (id, name, unit, opening_qty) values
    ('11111111-0000-4000-8000-000000000001', 'Rice', '25 kg sack', 0),
    ('11111111-0000-4000-8000-000000000002', 'Drinking water', '1 litre bottle', 0),
    ('11111111-0000-4000-8000-000000000003', 'Bandage rolls', 'roll', 0),
    ('11111111-0000-4000-8000-000000000004', 'Blankets', 'piece', 0),
    ('11111111-0000-4000-8000-000000000005', 'ORS sachets', 'sachet', 0),
    ('11111111-0000-4000-8000-000000000006', 'Cooking oil', '5 litre can', 0);
