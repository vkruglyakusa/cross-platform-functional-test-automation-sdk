package com.test.automation.sdk.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ApiTestBase")
class ApiTestBaseTest {

    private HttpServer server;
    private ApiTestBase api;

    @TempDir
    File tempDir;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/users/2", exchange -> respondJson(exchange, 200,
                "{\"data\":{\"id\":2,\"name\":\"Alice\"}}"));
        server.createContext("/echo", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            respondJson(exchange, 201, new String(body, StandardCharsets.UTF_8).isEmpty()
                    ? "{}" : new String(body, StandardCharsets.UTF_8));
        });
        server.createContext("/not-found", exchange -> respondJson(exchange, 404, "{\"error\":\"not found\"}"));
        server.start();

        System.setProperty("api.baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        System.setProperty("api.outputDirectory", tempDir.getAbsolutePath());
        System.setProperty("api.logRequestsAndResponses", "true");

        api = new ApiTestBase();
        api.environment = "test";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        System.clearProperty("api.baseUrl");
        System.clearProperty("api.baseUrl.test");
        System.clearProperty("api.outputDirectory");
        System.clearProperty("api.logRequestsAndResponses");
        System.clearProperty("api.authHeaderName");
        System.clearProperty("api.authTokenEnvVar");
    }

    private static void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    @DisplayName("get() reaches the configured base URL and returns the response body")
    void getReturnsResponseBody() {
        Response response = api.get("Get user", "/users/2");

        assertEquals(200, response.getStatusCode());
        assertEquals(Integer.valueOf(2), response.jsonPath().get("data.id"));
    }

    @Test
    @DisplayName("post() sends the request body and returns the echoed response")
    void postEchoesBody() {
        Response response = api.post("Echo", "/echo", "{\"hello\":\"world\"}");

        assertEquals(201, response.getStatusCode());
        assertEquals("world", response.jsonPath().getString("hello"));
    }

    @Test
    @DisplayName("assertStatusCode passes on a matching status code and fails on a mismatch")
    void assertStatusCodeBehavior() {
        Response ok = api.get("Get user", "/users/2");
        assertDoesNotThrow(() -> api.assertStatusCode(ok, 200));

        Response notFound = api.get("Missing", "/not-found");
        assertThrows(AssertionError.class, () -> api.assertStatusCode(notFound, 200));
    }

    @Test
    @DisplayName("assertJsonPath passes on a matching value and fails on a mismatch")
    void assertJsonPathBehavior() {
        Response response = api.get("Get user", "/users/2");
        assertDoesNotThrow(() -> api.assertJsonPath(response, "data.name", "Alice"));
        assertThrows(AssertionError.class, () -> api.assertJsonPath(response, "data.name", "Bob"));
    }

    @Test
    @DisplayName("assertResponseTimeUnder passes for a generous threshold and fails for an impossible one")
    void assertResponseTimeUnderBehavior() {
        Response response = api.get("Get user", "/users/2");
        assertDoesNotThrow(() -> api.assertResponseTimeUnder(response, 30_000));
        assertThrows(AssertionError.class, () -> api.assertResponseTimeUnder(response, -1));
    }

    @Test
    @DisplayName("per-environment api.baseUrl.<env> takes precedence over the environment-neutral api.baseUrl")
    void perEnvironmentBaseUrlWins() {
        System.setProperty("api.baseUrl.test", "http://127.0.0.1:" + server.getAddress().getPort());
        System.setProperty("api.baseUrl", "http://127.0.0.1:1"); // deliberately unreachable

        Response response = api.get("Get user", "/users/2");

        assertEquals(200, response.getStatusCode());
    }

    @Test
    @DisplayName("execute() writes an API payload evidence file to the configured output directory")
    void capturesPayloadEvidence() {
        api.get("Get user", "/users/2");

        File[] files = tempDir.listFiles();
        assertTrue(files != null && files.length > 0, "Expected at least one captured API payload file");
    }

    @Test
    @DisplayName("given() throws a clear error when no base URL is configured for the environment")
    void missingBaseUrlThrows() {
        System.clearProperty("api.baseUrl");
        ApiTestBase noBaseUrl = new ApiTestBase();
        noBaseUrl.environment = "unconfigured";

        assertThrows(IllegalStateException.class, noBaseUrl::given);
    }
}
