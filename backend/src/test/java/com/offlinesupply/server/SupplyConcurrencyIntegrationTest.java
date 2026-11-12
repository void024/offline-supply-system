package com.offlinesupply.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency and failure-semantics verification against REAL PostgreSQL - core
 * correctness, not load benchmarking: serialized ordering under simultaneous pushes,
 * rollback/contiguity, response-loss retry, paginated pull coverage, resolve
 * idempotency under a race, the evidence reopen gate, and session/device binding.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SupplyConcurrencyIntegrationTest {

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

    private record Client(String cookie, UUID deviceId, String deviceKey) {
    }

    private Client login(String username, UUID deviceId, String deviceKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Device-Id", deviceId.toString());
        if (deviceKey != null) {
            headers.set("X-Device-Key", deviceKey);
        }
        ObjectNode body = mapper.createObjectNode().put("username", username).put("password", "demo");
        ResponseEntity<JsonNode> response = http.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body.toString(), headers), JsonNode.class);
        assertEquals(200, response.getStatusCode().value(), "login failed: " + response.getBody());
        String cookie = response.getHeaders().get(HttpHeaders.SET_COOKIE).get(0).split(";")[0];
        String issued = response.getBody().hasNonNull("device_key")
                ? response.getBody().get("device_key").asText() : deviceKey;
        return new Client(cookie, deviceId, issued);
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
        if (plannedIssueId == null) {
            e.putNull("planned_issue_id");
        } else {
            e.put("planned_issue_id", plannedIssueId);
        }
        if (reason == null) {
            e.putNull("reason");
        } else {
            e.put("reason", reason);
        }
        e.put("occurred_at", OffsetDateTime.now().toString());
        return e;
    }

    private ResponseEntity<JsonNode> pushRaw(Client client, ObjectNode... events) {
        ArrayNode array = mapper.createArrayNode();
        for (ObjectNode e : events) {
            array.add(e);
        }
        ObjectNode body = mapper.createObjectNode().set("events", array);
        return http.exchange("/api/v1/events", HttpMethod.POST,
                new HttpEntity<>(body.toString(), auth(client)), JsonNode.class);
    }

    private JsonNode push(Client client, ObjectNode... events) {
        ResponseEntity<JsonNode> response = pushRaw(client, events);
        assertEquals(200, response.getStatusCode().value(), "push failed: " + response.getBody());
        return response.getBody();
    }

    private JsonNode get(Client client, String path) {
        ResponseEntity<JsonNode> response = http.exchange(path, HttpMethod.GET,
                new HttpEntity<>(auth(client)), JsonNode.class);
        assertEquals(200, response.getStatusCode().value(), "GET " + path + " failed: " + response.getBody());
        return response.getBody();
    }

    private void designate(Client boss) {
        ObjectNode body = mapper.createObjectNode().put("device_id", boss.deviceId().toString());
        http.exchange("/api/v1/issuer/designate", HttpMethod.POST,
                new HttpEntity<>(body.toString(), auth(boss)), JsonNode.class);
    }

    // ---------- ordering under concurrency ----------

    @Test
    void simultaneousPushesAreSerializedWithContiguousSeqs() throws Exception {
        Client keeperA = login("keeper1", UUID.randomUUID(), null);
        Client bossB = login("boss1", UUID.randomUUID(), null);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch barrier = new CountDownLatch(1);
        List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
        for (int side = 0; side < 2; side++) {
            Client client = side == 0 ? keeperA : bossB;
            futures.add(pool.submit(() -> {
                ObjectNode[] batch = new ObjectNode[5];
                for (int i = 0; i < 5; i++) {
                    batch[i] = event(UUID.randomUUID(), i + 1, "RECEIPT", RICE, 10, null, null);
                }
                assertTrue(barrier.await(5, java.util.concurrent.TimeUnit.SECONDS));
                return pushRaw(client, batch);
            }));
        }
        barrier.countDown();
        Set<String> statuses = new HashSet<>();
        for (Future<ResponseEntity<JsonNode>> f : futures) {
            ResponseEntity<JsonNode> response = f.get();
            assertEquals(200, response.getStatusCode().value());
            for (JsonNode r : response.getBody().get("results")) {
                statuses.add(r.get("status").asText());
            }
        }
        pool.shutdown();
        assertEquals(Set.of("ACCEPTED"), statuses);

        List<Long> seqs = jdbc.queryForList("select server_seq from events order by server_seq", Long.class);
        assertEquals(10, seqs.size());
        for (int i = 0; i < 10; i++) {
            assertEquals(i + 1L, seqs.get(i), "server_seq stream must be contiguous - no gaps, no duplicates");
        }
    }

    @Test
    void sameDeviceSeqRaceDecidesExactlyOneWinner() throws Exception {
        Client keeper = login("keeper1", UUID.randomUUID(), null);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch barrier = new CountDownLatch(1);
        List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                assertTrue(barrier.await(5, java.util.concurrent.TimeUnit.SECONDS));
                return pushRaw(keeper, event(UUID.randomUUID(), 1, "RECEIPT", RICE, 10, null, null));
            }));
        }
        barrier.countDown();
        List<String> verdicts = new ArrayList<>();
        for (Future<ResponseEntity<JsonNode>> f : futures) {
            assertEquals(200, f.get().getStatusCode().value());
            verdicts.add(f.get().getBody().get("results").get(0).get("status").asText());
        }
        pool.shutdown();
        assertEquals(2, verdicts.size());
        assertTrue(verdicts.contains("ACCEPTED"), "exactly one push must win: " + verdicts);
        assertTrue(verdicts.contains("CONFLICT"), "the loser must be reported CONFLICT: " + verdicts);

        Long count = jdbc.queryForObject("select count(*) from events", Long.class);
        assertEquals(1, count, "the losing batch must be fully rolled back");

        // And the counter was not burned: the next accepted event continues at 2.
        JsonNode next = push(keeper, event(UUID.randomUUID(), 2, "RECEIPT", RICE, 5, null, null));
        assertEquals("ACCEPTED", next.get("results").get(0).get("status").asText());
        List<Long> seqs = jdbc.queryForList("select server_seq from events order by server_seq", Long.class);
        assertEquals(List.of(1L, 2L), seqs, "a rolled-back batch must leave no sequence gap");
    }

    @Test
    void simultaneousDesignatesHaveExactlyOneWinner() throws Exception {
        Client bossA = login("boss1", UUID.randomUUID(), null);
        Client bossB = login("boss1", UUID.randomUUID(), null);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch barrier = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (Client boss : List.of(bossA, bossB)) {
            futures.add(pool.submit(() -> {
                ObjectNode body = mapper.createObjectNode().put("device_id", boss.deviceId().toString());
                assertTrue(barrier.await(5, java.util.concurrent.TimeUnit.SECONDS));
                return http.exchange("/api/v1/issuer/designate", HttpMethod.POST,
                        new HttpEntity<>(body.toString(), auth(boss)), JsonNode.class)
                        .getStatusCode().value();
            }));
        }
        barrier.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get());
        }
        pool.shutdown();
        assertTrue(statuses.contains(200) && statuses.contains(409),
                "exactly one designate must win the singleton: " + statuses);
        assertEquals(1, jdbc.queryForObject("select count(*) from issuer_designations", Long.class));
    }

    // ---------- failure semantics ----------

    @Test
    void responseLossRetryAppliesNothingTwice() {
        Client keeper = login("keeper1", UUID.randomUUID(), null);
        ObjectNode a = event(UUID.randomUUID(), 1, "RECEIPT", RICE, 10, null, null);
        ObjectNode b = event(UUID.randomUUID(), 2, "RECEIPT", WATER, 4, null, null);

        push(keeper, a, b);
        // Simulate a lost 200: the client retries the exact same batch.
        JsonNode retry = push(keeper, a, b).get("results");
        assertEquals("DUPLICATE", retry.get(0).get("status").asText());
        assertEquals("DUPLICATE", retry.get(1).get("status").asText());

        assertEquals(2, jdbc.queryForObject("select count(*) from events", Long.class));
        assertEquals(10, jdbc.queryForObject(
                "select coalesce(sum(quantity),0) from events where item_id = '" + RICE + "'", Long.class).longValue());
    }

    @Test
    void paginatedPullsCoverEveryEventExactlyOnce() {
        Client keeper = login("keeper1", UUID.randomUUID(), null);
        Set<String> pushed = new HashSet<>();
        for (int i = 1; i <= 5; i++) {
            UUID id = UUID.randomUUID();
            pushed.add(id.toString());
            push(keeper, event(id, i, "RECEIPT", RICE, 1, null, null));
        }

        Set<String> seen = new HashSet<>();
        long cursor = 0;
        int pages = 0;
        while (true) {
            JsonNode page = get(keeper, "/api/v1/sync/pull?after=" + cursor + "&limit=2");
            for (JsonNode e : page.get("events")) {
                assertTrue(seen.add(e.get("event_id").asText()), "event delivered twice across pages");
            }
            cursor = page.get("next_cursor").asLong();
            pages++;
            if (!page.get("has_more").asBoolean()) {
                assertTrue(page.get("reference_as_of").asLong() >= 5);
                break;
            }
            assertTrue(pages < 10, "pagination must terminate");
        }
        assertEquals(3, pages);
        assertEquals(pushed, seen, "pages together must deliver every event exactly once");
    }

    // ---------- resolve idempotency under a race ----------

    @Test
    void simultaneousResolveWithSameIdCreatesExactlyOneAdjustment() throws Exception {
        Client boss = login("boss1", UUID.randomUUID(), null);
        designate(boss);
        push(boss, event(UUID.randomUUID(), 1, "RECEIPT", WATER, 5, null, null));
        push(boss, event(UUID.randomUUID(), 2, "HANDOVER", WATER, 9, null, null));

        JsonNode discrepancy = null;
        for (JsonNode d : get(boss, "/api/v1/discrepancies?status=OPEN").get("discrepancies")) {
            if (d.get("type").asText().equals("NEGATIVE_BALANCE")) {
                discrepancy = d;
            }
        }
        assertNotNull(discrepancy);
        String path = "/api/v1/discrepancies/" + discrepancy.get("id").asText() + "/resolve";

        UUID resolutionId = UUID.randomUUID();
        ObjectNode body = mapper.createObjectNode()
                .put("resolution_id", resolutionId.toString())
                .put("outcome", "ADJUSTMENT")
                .put("reason", "stock found")
                .put("quantity_delta", 4);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch barrier = new CountDownLatch(1);
        List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String p = path;
            futures.add(pool.submit(() -> {
                assertTrue(barrier.await(5, java.util.concurrent.TimeUnit.SECONDS));
                return http.exchange(p, HttpMethod.POST, new HttpEntity<>(body.toString(), auth(boss)),
                        JsonNode.class);
            }));
        }
        barrier.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<ResponseEntity<JsonNode>> f : futures) {
            statuses.add(f.get().getStatusCode().value());
        }
        pool.shutdown();

        assertTrue(statuses.get(0) == 200 || statuses.get(1) == 200, "at least one resolve succeeds: " + statuses);
        // The racy loser either replays the idempotent outcome (200) or conflicts (409) - never a second write.
        assertTrue(statuses.stream().allMatch(s -> s == 200 || s == 409), "unexpected statuses: " + statuses);
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from events where event_id = '" + resolutionId + "'", Long.class),
                "exactly one ADJUSTMENT may exist for the resolution id");
        assertEquals(1, jdbc.queryForObject("select count(*) from resolutions", Long.class));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from discrepancies where status = 'OPEN' and type = 'NEGATIVE_BALANCE'",
                Long.class).longValue());
    }

    @Test
    void reviewedFactsNeverReflagButNewFactsOpenANewRow() {
        Client boss = login("boss1", UUID.randomUUID(), null);
        designate(boss);
        push(boss, event(UUID.randomUUID(), 1, "HANDOVER", WATER, 9, null, null)); // balance -9

        String discrepancyId = null;
        for (JsonNode d : get(boss, "/api/v1/discrepancies?status=OPEN").get("discrepancies")) {
            if (d.get("type").asText().equals("NEGATIVE_BALANCE")) {
                discrepancyId = d.get("id").asText();
            }
        }
        assertNotNull(discrepancyId);
        ObjectNode resolve = mapper.createObjectNode()
                .put("resolution_id", UUID.randomUUID().toString())
                .put("outcome", "NO_CHANGE")
                .put("reason", "delayed receipt confirmed with supplier");
        ResponseEntity<JsonNode> resolved = http.exchange(
                "/api/v1/discrepancies/" + discrepancyId + "/resolve", HttpMethod.POST,
                new HttpEntity<>(resolve.toString(), auth(boss)), JsonNode.class);
        assertEquals(200, resolved.getStatusCode().value());

        // New activity on an UNRELATED item must not reflag the reviewed facts.
        push(boss, event(UUID.randomUUID(), 2, "RECEIPT", RICE, 3, null, null));
        Long openForWater = jdbc.queryForObject(
                "select count(*) from discrepancies where status = 'OPEN' and type = 'NEGATIVE_BALANCE'",
                Long.class);
        assertEquals(0, openForWater, "reviewed facts must never reflag unchanged");

        // A genuinely new offending fact must open a NEW row (the old one stays RESOLVED).
        push(boss, event(UUID.randomUUID(), 3, "HANDOVER", WATER, 2, null, null)); // balance -11
        Long openAfter = jdbc.queryForObject(
                "select count(*) from discrepancies where status = 'OPEN' and type = 'NEGATIVE_BALANCE'",
                Long.class);
        Long totalRows = jdbc.queryForObject(
                "select count(*) from discrepancies where type = 'NEGATIVE_BALANCE'", Long.class);
        assertEquals(1, openAfter, "newer offending facts must produce a new visible row");
        assertEquals(2, totalRows, "the reviewed row stays as history");
    }

    // ---------- session + device binding ----------

    @Test
    void sessionIsBoundToDeviceAndLogoutInvalidates() {
        Client keeper = login("keeper1", UUID.randomUUID(), null);

        // Cookie + correct device credentials -> 200.
        assertEquals(200, http.exchange("/api/v1/items", HttpMethod.GET,
                new HttpEntity<>(auth(keeper)), JsonNode.class).getStatusCode().value());

        // Same cookie, another device's id -> 403.
        HttpHeaders otherDevice = auth(keeper);
        otherDevice.set("X-Device-Id", UUID.randomUUID().toString());
        assertEquals(403, http.exchange("/api/v1/items", HttpMethod.GET,
                new HttpEntity<>(otherDevice), JsonNode.class).getStatusCode().value());

        // Same cookie, right device id, wrong key -> 403.
        HttpHeaders wrongKey = auth(keeper);
        wrongKey.set("X-Device-Key", "forged");
        assertEquals(403, http.exchange("/api/v1/items", HttpMethod.GET,
                new HttpEntity<>(wrongKey), JsonNode.class).getStatusCode().value());

        // Logout -> the same cookie is dead everywhere.
        assertEquals(200, http.exchange("/api/v1/auth/logout", HttpMethod.POST,
                new HttpEntity<>(auth(keeper)), JsonNode.class).getStatusCode().value());
        assertEquals(401, http.exchange("/api/v1/items", HttpMethod.GET,
                new HttpEntity<>(auth(keeper)), JsonNode.class).getStatusCode().value());
    }

    @Test
    void sessionCookieCarriesTheContractFlags() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Device-Id", UUID.randomUUID().toString());
        ObjectNode body = mapper.createObjectNode().put("username", "keeper1").put("password", "demo");
        ResponseEntity<JsonNode> response = http.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body.toString(), headers), JsonNode.class);
        String setCookie = response.getHeaders().get(HttpHeaders.SET_COOKIE).get(0);
        assertTrue(setCookie.contains("HttpOnly"), "cookie must be HttpOnly: " + setCookie);
        assertTrue(setCookie.contains("SameSite=Strict"), "cookie must be SameSite=Strict: " + setCookie);
        assertFalse(setCookie.contains("Secure"),
                "the TEST profile relaxes Secure for http://localhost; default config keeps it on");
    }
}
