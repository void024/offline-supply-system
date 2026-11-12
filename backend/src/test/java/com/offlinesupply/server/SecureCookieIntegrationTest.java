package com.offlinesupply.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract requires HttpOnly; Secure; SameSite=Strict. The default (non-dev)
 * configuration keeps Secure ON; this class proves the flag lands on the wire with the
 * production setting, while the dev/test profiles relax it only for http://localhost.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.session.secure-cookie=true")
@ActiveProfiles("test")
class SecureCookieIntegrationTest {

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from sessions");
        jdbc.update("delete from devices");
    }

    @Test
    void productionCookieIsSecureHttpOnlySameSiteStrict() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Device-Id", UUID.randomUUID().toString());
        ObjectNode body = mapper.createObjectNode().put("username", "keeper1").put("password", "demo");
        ResponseEntity<JsonNode> response = http.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body.toString(), headers), JsonNode.class);
        String setCookie = response.getHeaders().get(HttpHeaders.SET_COOKIE).get(0);
        assertTrue(setCookie.contains("Secure"), "production cookie must carry Secure: " + setCookie);
        assertTrue(setCookie.contains("HttpOnly"), "cookie must be HttpOnly: " + setCookie);
        assertTrue(setCookie.contains("SameSite=Strict"), "cookie must be SameSite=Strict: " + setCookie);
    }
}
