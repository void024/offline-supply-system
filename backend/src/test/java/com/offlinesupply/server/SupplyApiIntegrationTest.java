package com.offlinesupply.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full-stack integration tests against a REAL PostgreSQL database (TEST_DB_URL) with real
 * HTTP through the embedded server - no mocks anywhere in this class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SupplyApiIntegrationTest {

    private static final String RICE = "11111111-0000-4000-8000-000000000001";
    private static final String WATER = "11111111-0000-4000-8000-000000000002";
    private static final String KEEPER = "11111111-1111-4111-8111-111111111111";

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        // Wipe all mutable state; keep the seeded users and items.
        // resolutions <-> discrepancies reference each other; null one side first.
        jdbc.update("update discrepancies set resolved_by_resolution_id = null");
        jdbc.update("delete from resolutions");
        jdbc.update("delete from discrepancies");
        jdbc.update("delete from submission_issues");
        jdbc.update("delete from events");
        jdbc.update("delete from issuer_designations");
        jdbc.update("delete from sessions");
        jdbc.update("delete from devices");
        jdbc.update("update sync_counters set next_value = 1");
    }

    // ---------- helpers ----------

    private record Client(String cookie, UUID deviceId, String deviceKey, UUID userId) {
    }

    private Client login(String username, String password, UUID deviceId, String deviceKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Device-Id", deviceId.toString());
        if (deviceKey != null) {
            headers.set("X-Device-Key", deviceKey);
        }
        ObjectNode body = mapper.createObjectNode().put("username", username).put("password", password);
        ResponseEntity<JsonNode> response = http.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body.toString(), headers), JsonNode.class);
        assertEquals(200, response.getStatusCode().value(), "login failed: " + response.getBody());
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies);
        String cookie = cookies.get(0).split(";")[0];
        String issuedKey = response.getBody().hasNonNull("device_key")
                ? response.getBody().get("device_key").asText() : null;
        return new Client(cookie, deviceId, issuedKey != null ? issuedKey : deviceKey,
                UUID.fromString(response.getBody().get("user_id").asText()));
    }

    private HttpHeaders auth(Client client) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, client.cookie());
        headers.set("X-Device-Id", client.deviceId().toString());
        headers.set("X-Device-Key", client.deviceKey());
        return headers;
    }

    private ObjectNode event(UUID eventId, int deviceSeq, String type, String itemId, int quantity,
                             String plannedIssueId, String reason) {
        ObjectNode e = mapper.createObjectNode();
        e.put("event_id", eventId.toString());
        e.put("device_seq", deviceSeq);
        e.put("recorded_by_user_id", KEEPER);
        e.put("type", type);
        e.put("item_id", itemId);
        e.put("quantity", quantity);
        if (plannedIssueId != null) {
            e.put("planned_issue_id", plannedIssueId);
        } else {
            e.putNull("planned_issue_id");
        }
        if (reason != null) {
            e.put("reason", reason);
        } else {
            e.putNull("reason");
        }
        e.put("occurred_at", OffsetDateTime.now().toString());
        return e;
    }

    private JsonNode push(Client client, ObjectNode... events) {
        ArrayNode array = mapper.createArrayNode();
        for (ObjectNode e : events) {
            array.add(e);
        }
        ObjectNode body = mapper.createObjectNode().set("events", array);
        ResponseEntity<JsonNode> response = http.exchange("/api/v1/events", HttpMethod.POST,
                new HttpEntity<>(body.toString(), auth(client)), JsonNode.class);
        assertEquals(200, response.getStatusCode().value(), "push failed: " + response.getBody());
        return response.getBody();
    }

    private JsonNode get(Client client, String path) {
        ResponseEntity<JsonNode> response = http.exchange(path, HttpMethod.GET,
                new HttpEntity<>(auth(client)), JsonNode.class);
        assertEquals(200, response.getStatusCode().value(), "GET " + path + " failed: " + response.getBody());
        return response.getBody();
    }

    private long itemBalance(Client client, String itemId) {
        for (JsonNode item : get(client, "/api/v1/items").get("items")) {
            if (item.get("id").asText().equals(itemId)) {
                return item.get("balance").asLong();
            }
        }
        throw new AssertionError("item not found: " + itemId);
    }

    private JsonNode findDiscrepancy(Client client, String type) {
        for (JsonNode d : get(client, "/api/v1/discrepancies").get("discrepancies")) {
            if (d.get("type").asText().equals(type)) {
                return d;
            }
        }
        return null;
    }

    // ---------- tests ----------

    @Test
    @Order(1)
    void unauthenticatedAndMismatchedDeviceRequestsRejected() {
        ResponseEntity<JsonNode> noCookie = http.getForEntity("/api/v1/items", JsonNode.class);
        assertEquals(401, noCookie.getStatusCode().value());

        Client keeper = login("keeper1", "demo", UUID.randomUUID(), null);
        HttpHeaders wrongKey = auth(keeper);
        wrongKey.set("X-Device-Key", "wrong-key");
        ResponseEntity<JsonNode> badKey = http.exchange("/api/v1/items", HttpMethod.GET,
                new HttpEntity<>(wrongKey), JsonNode.class);
        assertEquals(403, badKey.getStatusCode().value());
    }

    @Test
    @Order(2)
    void loginEnrollsDeviceOnceAndRejectsBadKey() {
        UUID device = UUID.randomUUID();
        Client first = login("keeper1", "demo", device, null);
        assertNotNull(first.deviceKey(), "first login must enroll and return device_key");

        // Second login with the key: the body must carry device_key: null (key is returned once).
        HttpHeaders againHeaders = new HttpHeaders();
        againHeaders.setContentType(MediaType.APPLICATION_JSON);
        againHeaders.set("X-Device-Id", device.toString());
        againHeaders.set("X-Device-Key", first.deviceKey());
        ObjectNode againBody = mapper.createObjectNode().put("username", "keeper1").put("password", "demo");
        ResponseEntity<JsonNode> again = http.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(againBody.toString(), againHeaders), JsonNode.class);
        assertEquals(200, again.getStatusCode().value());
        assertTrue(again.getBody().has("device_key") && again.getBody().get("device_key").isNull(),
                "enrolled device must not receive the key again");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Device-Id", device.toString());
        headers.set("X-Device-Key", "wrong-key");
        ObjectNode body = mapper.createObjectNode().put("username", "keeper1").put("password", "demo");
        ResponseEntity<JsonNode> response = http.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body.toString(), headers), JsonNode.class);
        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    @Order(3)
    void badCredentialsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Device-Id", UUID.randomUUID().toString());
        ObjectNode body = mapper.createObjectNode().put("username", "keeper1").put("password", "nope");
        ResponseEntity<JsonNode> response = http.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body.toString(), headers), JsonNode.class);
        assertEquals(401, response.getStatusCode().value());

        // A successful login clears the failure window so the shared-IP guard test stays honest.
        login("keeper1", "demo", UUID.randomUUID(), null);
    }

    @Test
    @Order(4)
    void receiptAcceptedAndBalanceComputedFromLog() {
        Client keeper = login("keeper1", "demo", UUID.randomUUID(), null);
        JsonNode results = push(keeper, event(UUID.randomUUID(), 1, "RECEIPT", RICE, 100, null, null)).get("results");
        assertEquals("ACCEPTED", results.get(0).get("status").asText());
        assertEquals(100, itemBalance(keeper, RICE));
    }

    @Test
    @Order(5)
    void identicalReplayIsDuplicateAndChangesNothing() {
        Client keeper = login("keeper1", "demo", UUID.randomUUID(), null);
        ObjectNode receipt = event(UUID.randomUUID(), 1, "RECEIPT", RICE, 100, null, null);
        push(keeper, receipt);
        JsonNode replay = push(keeper, receipt).get("results");
        assertEquals("DUPLICATE", replay.get(0).get("status").asText());
        assertEquals(100, itemBalance(keeper, RICE), "replay must not double-apply");
        assertEquals(1, get(keeper, "/api/v1/events?item_id=" + RICE).get("events").size());
    }

    @Test
    @Order(6)
    void conflictingPayloadIsQuarantinedAndDeduplicated() {
        Client keeper = login("keeper1", "demo", UUID.randomUUID(), null);
        UUID eventId = UUID.randomUUID();
        push(keeper, event(eventId, 1, "RECEIPT", RICE, 100, null, null));

        ObjectNode changed = event(eventId, 1, "RECEIPT", RICE, 999, null, null);
        JsonNode conflict = push(keeper, changed).get("results");
        assertEquals("CONFLICT", conflict.get(0).get("status").asText());
        assertEquals(100, itemBalance(keeper, RICE), "conflicting payload must not be applied");

        JsonNode issues = get(keeper, "/api/v1/sync/pull?after=0").get("reference").get("submission_issues");
        assertEquals(1, issues.size());
        assertEquals("PAYLOAD_CONFLICT", issues.get(0).get("kind").asText());

        // A retry of the same rejected event returns the same status without a new row.
        push(keeper, changed);
        JsonNode issuesAfter = get(keeper, "/api/v1/sync/pull?after=0").get("reference").get("submission_issues");
        assertEquals(1, issuesAfter.size(), "retry must not multiply quarantine rows");
    }

    @Test
    @Order(7)
    void designationIsSingletonAndIssuesOnlyFromDesignatedDevice() {
        Client boss = login("boss1", "demo", UUID.randomUUID(), null);
        Client keeper = login("keeper1", "demo", UUID.randomUUID(), null);

        ObjectNode designate = mapper.createObjectNode().put("device_id", boss.deviceId().toString());
        ResponseEntity<JsonNode> first = http.exchange("/api/v1/issuer/designate", HttpMethod.POST,
                new HttpEntity<>(designate.toString(), auth(boss)), JsonNode.class);
        assertEquals(200, first.getStatusCode().value());

        ResponseEntity<JsonNode> sameAgain = http.exchange("/api/v1/issuer/designate", HttpMethod.POST,
                new HttpEntity<>(designate.toString(), auth(boss)), JsonNode.class);
        assertEquals(200, sameAgain.getStatusCode().value(), "same device re-designation must be idempotent");

        ObjectNode other = mapper.createObjectNode().put("device_id", keeper.deviceId().toString());
        ResponseEntity<JsonNode> conflict = http.exchange("/api/v1/issuer/designate", HttpMethod.POST,
                new HttpEntity<>(other.toString(), auth(boss)), JsonNode.class);
        assertEquals(409, conflict.getStatusCode().value());

        JsonNode keeperPlan = push(keeper, event(UUID.randomUUID(), 1, "PLANNED_ISSUE", RICE, 10, null, null))
                .get("results");
        assertEquals("QUARANTINED", keeperPlan.get(0).get("status").asText());

        JsonNode bossPlan = push(boss, event(UUID.randomUUID(), 1, "PLANNED_ISSUE", RICE, 10, null, null))
                .get("results");
        assertEquals("ACCEPTED", bossPlan.get(0).get("status").asText());
    }

    @Test
    @Order(8)
    void adjustmentRequiresSupervisorSessionAtPushTime() {
        Client keeper = login("keeper1", "demo", UUID.randomUUID(), null);
        Client boss = login("boss1", "demo", UUID.randomUUID(), null);

        JsonNode denied = push(keeper, event(UUID.randomUUID(), 1, "ADJUSTMENT", RICE, -5, null, "damage"))
                .get("results");
        assertEquals("QUARANTINED", denied.get(0).get("status").asText());

        push(boss, event(UUID.randomUUID(), 1, "RECEIPT", RICE, 50, null, null));
        JsonNode allowed = push(boss, event(UUID.randomUUID(), 2, "ADJUSTMENT", RICE, -5, null, "damage"))
                .get("results");
        assertEquals("ACCEPTED", allowed.get(0).get("status").asText());
        assertEquals(45, itemBalance(boss, RICE));
    }

    @Test
    @Order(9)
    void planExceededAndUnplannedHandoverAreSurfaced() {
        Client boss = login("boss1", "demo", UUID.randomUUID(), null);
        http.exchange("/api/v1/issuer/designate", HttpMethod.POST, new HttpEntity<>(
                mapper.createObjectNode().put("device_id", boss.deviceId().toString()).toString(),
                auth(boss)), JsonNode.class);

        push(boss, event(UUID.randomUUID(), 1, "RECEIPT", RICE, 100, null, null));
        UUID plan = UUID.randomUUID();
        push(boss, event(plan, 2, "PLANNED_ISSUE", RICE, 10, null, null));
        push(boss, event(UUID.randomUUID(), 3, "HANDOVER", RICE, 15, plan.toString(), null));

        JsonNode exceeded = findDiscrepancy(boss, "PLAN_EXCEEDED");
        assertNotNull(exceeded, "over-delivery must surface PLAN_EXCEEDED");
        assertEquals("OPEN", exceeded.get("status").asText());
        assertTrue(exceeded.get("details").asText().contains("excess 5"));

        for (JsonNode item : get(boss, "/api/v1/items").get("items")) {
            if (item.get("id").asText().equals(RICE)) {
                assertEquals(85, item.get("balance").asLong());
                assertEquals(0, item.get("planned_outstanding").asLong(), "outstanding clamps at zero");
            }
        }

        push(boss, event(UUID.randomUUID(), 4, "HANDOVER", WATER, 3, null, null));
        JsonNode unplanned = findDiscrepancy(boss, "UNPLANNED_HANDOVER");
        assertNotNull(unplanned, "handover without a plan must surface UNPLANNED_HANDOVER");
    }

    @Test
    @Order(10)
    void unresolvedReferenceAutoClosesOnlyForMatchingPlan() {
        Client boss = login("boss1", "demo", UUID.randomUUID(), null);
        http.exchange("/api/v1/issuer/designate", HttpMethod.POST, new HttpEntity<>(
                mapper.createObjectNode().put("device_id", boss.deviceId().toString()).toString(),
                auth(boss)), JsonNode.class);

        push(boss, event(UUID.randomUUID(), 1, "RECEIPT", RICE, 100, null, null));
        UUID missingPlan = UUID.randomUUID();
        UUID handoverId = UUID.randomUUID();
        push(boss, event(handoverId, 2, "HANDOVER", RICE, 10, missingPlan.toString(), null));

        JsonNode flag = findDiscrepancy(boss, "UNRESOLVED_REFERENCE");
        assertNotNull(flag);
        assertEquals("OPEN", flag.get("status").asText());

        // Wrong type arrives under the referenced id: must NOT auto-close.
        // (A RECEIPT under that id is accepted from any device.)
        push(boss, event(missingPlan, 3, "RECEIPT", RICE, 1, null, null));
        JsonNode stillOpen = findDiscrepancy(boss, "UNRESOLVED_REFERENCE");
        assertEquals("OPEN", stillOpen.get("status").asText(), "wrong type must never auto-close");

        // The real plan arrives with a fresh id - so flag stays; now push the matching plan
        // under a new missing reference to check the positive path.
        UUID missingPlan2 = UUID.randomUUID();
        UUID handover2 = UUID.randomUUID();
        push(boss, event(handover2, 4, "HANDOVER", RICE, 10, missingPlan2.toString(), null));
        push(boss, event(missingPlan2, 5, "PLANNED_ISSUE", RICE, 10, null, null));
        JsonNode closed = get(boss, "/api/v1/discrepancies").get("discrepancies");
        boolean foundClosed = false;
        for (JsonNode d : closed) {
            if (d.get("type").asText().equals("UNRESOLVED_REFERENCE")
                    && d.get("status").asText().equals("RESOLVED")) {
                foundClosed = true;
            }
        }
        assertTrue(foundClosed, "matching PLANNED_ISSUE for the same item must auto-close the flag");
    }

    @Test
    @Order(11)
    void negativeBalanceFlagsAndResolvingWithAdjustmentIsIdempotent() {
        Client boss = login("boss1", "demo", UUID.randomUUID(), null);
        http.exchange("/api/v1/issuer/designate", HttpMethod.POST, new HttpEntity<>(
                mapper.createObjectNode().put("device_id", boss.deviceId().toString()).toString(),
                auth(boss)), JsonNode.class);

        push(boss, event(UUID.randomUUID(), 1, "RECEIPT", WATER, 10, null, null));
        push(boss, event(UUID.randomUUID(), 2, "HANDOVER", WATER, 15, null, null));
        assertEquals(-5, itemBalance(boss, WATER), "negative balances are surfaced, never clamped");

        JsonNode negative = findDiscrepancy(boss, "NEGATIVE_BALANCE");
        assertNotNull(negative);
        assertTrue(negative.get("details").asText().contains("-5"));

        UUID resolutionId = UUID.randomUUID();
        ObjectNode resolveBody = mapper.createObjectNode()
                .put("resolution_id", resolutionId.toString())
                .put("outcome", "ADJUSTMENT")
                .put("reason", "found 5 uncounted bottles")
                .put("quantity_delta", 5);
        String path = "/api/v1/discrepancies/" + negative.get("id").asText() + "/resolve";
        ResponseEntity<JsonNode> resolved = http.exchange(path, HttpMethod.POST,
                new HttpEntity<>(resolveBody.toString(), auth(boss)), JsonNode.class);
        assertEquals(200, resolved.getStatusCode().value());
        assertEquals(resolutionId.toString(), resolved.getBody().get("adjustment_event_id").asText());

        // The ledger gained a SERVER-origin adjustment whose event_id is the resolution_id.
        JsonNode history = get(boss, "/api/v1/events?item_id=" + WATER + "&type=ADJUSTMENT").get("events");
        assertEquals(1, history.size());
        assertEquals("SERVER", history.get(0).get("origin").asText());
        assertTrue(history.get(0).get("device_seq").isNull(), "SERVER events carry NULL device_seq");
        assertEquals(0, itemBalance(boss, WATER));

        // Retry with the identical payload returns the original outcome.
        ResponseEntity<JsonNode> retry = http.exchange(path, HttpMethod.POST,
                new HttpEntity<>(resolveBody.toString(), auth(boss)), JsonNode.class);
        assertEquals(200, retry.getStatusCode().value());
        assertEquals(resolutionId.toString(), retry.getBody().get("adjustment_event_id").asText());
        assertEquals(1, get(boss, "/api/v1/events?item_id=" + WATER + "&type=ADJUSTMENT").get("events").size(),
                "retry must not create a second adjustment");

        // Same resolution id, different payload -> 409.
        ObjectNode different = resolveBody.deepCopy().put("reason", "changed story");
        ResponseEntity<JsonNode> conflict = http.exchange(path, HttpMethod.POST,
                new HttpEntity<>(different.toString(), auth(boss)), JsonNode.class);
        assertEquals(409, conflict.getStatusCode().value());

        // A fresh resolution id against an already-RESOLVED row -> 409.
        ObjectNode fresh = resolveBody.deepCopy().put("resolution_id", UUID.randomUUID().toString());
        ResponseEntity<JsonNode> alreadyResolved = http.exchange(path, HttpMethod.POST,
                new HttpEntity<>(fresh.toString(), auth(boss)), JsonNode.class);
        assertEquals(409, alreadyResolved.getStatusCode().value());

        // Resolved discrepancy now carries the embedded review record.
        JsonNode after = findDiscrepancy(boss, "NEGATIVE_BALANCE");
        assertEquals("RESOLVED", after.get("status").asText());
        assertEquals("found 5 uncounted bottles", after.get("resolution").get("reason").asText());
    }

    @Test
    @Order(12)
    void pullCursorSemanticsAndReferenceSnapshot() {
        Client keeper = login("keeper1", "demo", UUID.randomUUID(), null);
        push(keeper,
                event(UUID.randomUUID(), 1, "RECEIPT", RICE, 10, null, null),
                event(UUID.randomUUID(), 2, "RECEIPT", RICE, 10, null, null),
                event(UUID.randomUUID(), 3, "RECEIPT", RICE, 10, null, null));

        JsonNode page1 = get(keeper, "/api/v1/sync/pull?after=0&limit=2");
        assertEquals(2, page1.get("events").size());
        assertTrue(page1.get("has_more").asBoolean());
        long cursor = page1.get("next_cursor").asLong();
        assertEquals(page1.get("events").get(1).get("server_seq").asLong(), cursor,
                "next_cursor must be the last event's server_seq, never the table max");

        JsonNode page2 = get(keeper, "/api/v1/sync/pull?after=" + cursor);
        assertEquals(1, page2.get("events").size());
        assertFalse(page2.get("has_more").asBoolean());

        JsonNode empty = get(keeper, "/api/v1/sync/pull?after=9999");
        assertEquals(0, empty.get("events").size());
        assertEquals(9999, empty.get("next_cursor").asLong(), "empty page keeps the client's cursor");
        assertTrue(empty.get("reference_as_of").asLong() >= 3);
        assertEquals(6, empty.get("reference").get("items").size(), "six seeded items");
        assertNotNull(empty.get("reference").get("issuer"));
    }

    @Test
    @Order(13)
    void storekeeperCannotUseSupervisorEndpoints() {
        Client keeper = login("keeper1", "demo", UUID.randomUUID(), null);
        ObjectNode designate = mapper.createObjectNode().put("device_id", keeper.deviceId().toString());
        ResponseEntity<JsonNode> forbidden = http.exchange("/api/v1/issuer/designate", HttpMethod.POST,
                new HttpEntity<>(designate.toString(), auth(keeper)), JsonNode.class);
        assertEquals(403, forbidden.getStatusCode().value());

        ObjectNode resolve = mapper.createObjectNode()
                .put("resolution_id", UUID.randomUUID().toString())
                .put("outcome", "NO_CHANGE")
                .put("reason", "attempted");
        ResponseEntity<JsonNode> forbiddenResolve = http.exchange(
                "/api/v1/discrepancies/" + UUID.randomUUID() + "/resolve", HttpMethod.POST,
                new HttpEntity<>(resolve.toString(), auth(keeper)), JsonNode.class);
        assertEquals(403, forbiddenResolve.getStatusCode().value());
    }

    // Runs last: the login guard counts failures per client IP, which all tests share.
    @Test
    @Order(99)
    void loginGuardBlocksAfterFiveFailures() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Device-Id", UUID.randomUUID().toString());
        ObjectNode body = mapper.createObjectNode().put("username", "keeper1").put("password", "wrong");
        for (int i = 0; i < 5; i++) {
            ResponseEntity<JsonNode> r = http.exchange("/api/v1/auth/login", HttpMethod.POST,
                    new HttpEntity<>(body.toString(), headers), JsonNode.class);
            assertEquals(401, r.getStatusCode().value());
        }
        ResponseEntity<JsonNode> blocked = http.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body.toString(), headers), JsonNode.class);
        assertEquals(429, blocked.getStatusCode().value());
        assertNotNull(blocked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }
}
