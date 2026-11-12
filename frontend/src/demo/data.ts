/**
 * demo/data.ts
 * Responsibility: the demo dataset. Loaded ONLY when VITE_DEMO_MODE=true.
 * This is demonstration data for layout and flow review - it is not user data, not production
 * data, and never leaves the browser. The real API path (src/api/client.ts) is untouched.
 */

import type { AuthUser, Discrepancy, Item, Role, SupplyEvent } from '../types'

export interface DemoUser extends AuthUser {
  password: string
}

export const DEMO_USERS: DemoUser[] = [
  {
    user_id: '11111111-1111-4111-8111-111111111111',
    username: 'keeper1',
    password: 'demo',
    role: 'storekeeper' satisfies Role,
  },
  {
    user_id: '22222222-2222-4222-8222-222222222222',
    username: 'boss1',
    password: 'demo',
    role: 'supervisor' satisfies Role,
  },
]

export const SEED_ITEMS: Item[] = [
  { id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa01', name: 'Rice', unit: '25 kg sack', balance: 0, planned_outstanding: 0 },
  { id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa02', name: 'Drinking water', unit: '1 litre bottle', balance: 0, planned_outstanding: 0 },
  { id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa03', name: 'Bandage rolls', unit: 'roll', balance: 0, planned_outstanding: 0 },
]

const at = (minutesAgo: number): string => new Date(Date.now() - minutesAgo * 60_000).toISOString()

export const SEED_EVENTS: SupplyEvent[] = [
  {
    event_id: 'e0000000-0000-4000-8000-000000000001',
    device_seq: 1,
    recorded_by_user_id: '11111111-1111-4111-8111-111111111111',
    type: 'RECEIPT',
    item_id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa01',
    quantity: 100,
    planned_issue_id: null,
    reason: null,
    occurred_at: at(24 * 60),
    server_seq: 1,
    origin: 'DEVICE',
    received_at: at(24 * 60),
  },
  {
    event_id: 'e0000000-0000-4000-8000-000000000002',
    device_seq: 2,
    recorded_by_user_id: '11111111-1111-4111-8111-111111111111',
    type: 'RECEIPT',
    item_id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa02',
    quantity: 200,
    planned_issue_id: null,
    reason: null,
    occurred_at: at(23 * 60),
    server_seq: 2,
    origin: 'DEVICE',
    received_at: at(23 * 60),
  },
  {
    event_id: 'e0000000-0000-4000-8000-000000000003',
    device_seq: 3,
    recorded_by_user_id: '11111111-1111-4111-8111-111111111111',
    type: 'RECEIPT',
    item_id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa03',
    quantity: 50,
    planned_issue_id: null,
    reason: null,
    occurred_at: at(23 * 60),
    server_seq: 3,
    origin: 'DEVICE',
    received_at: at(23 * 60),
  },
  {
    event_id: 'e0000000-0000-4000-8000-000000000004',
    device_seq: 4,
    recorded_by_user_id: '11111111-1111-4111-8111-111111111111',
    type: 'HANDOVER',
    item_id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa01',
    quantity: 30,
    planned_issue_id: null,
    reason: null,
    occurred_at: at(5 * 60),
    server_seq: 4,
    origin: 'DEVICE',
    received_at: at(5 * 60),
  },
  {
    event_id: 'e0000000-0000-4000-8000-000000000005',
    device_seq: 5,
    recorded_by_user_id: '11111111-1111-4111-8111-111111111111',
    type: 'HANDOVER',
    item_id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa02',
    quantity: 250,
    planned_issue_id: null,
    reason: null,
    occurred_at: at(4 * 60),
    server_seq: 5,
    origin: 'DEVICE',
    received_at: at(4 * 60),
  },
  {
    event_id: 'e0000000-0000-4000-8000-000000000006',
    device_seq: 6,
    recorded_by_user_id: '11111111-1111-4111-8111-111111111111',
    type: 'PLANNED_ISSUE',
    item_id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa02',
    quantity: 40,
    planned_issue_id: null,
    reason: null,
    occurred_at: at(3 * 60),
    server_seq: 6,
    origin: 'DEVICE',
    received_at: at(3 * 60),
  },
]

export const SEED_DISCREPANCIES: Discrepancy[] = [
  {
    id: 'd0000000-0000-4000-8000-000000000001',
    item_id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa02',
    type: 'NEGATIVE_BALANCE',
    details: 'Accounting balance is -50 for Drinking water. A delayed receipt or a factual over-handover can explain this; the facts are retained.',
    status: 'OPEN',
    detected_at: at(4 * 60),
    resolution: null,
  },
]
