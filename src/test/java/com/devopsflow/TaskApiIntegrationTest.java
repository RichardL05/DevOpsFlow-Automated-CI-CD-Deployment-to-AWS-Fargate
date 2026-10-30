package com.devopsflow;

import static org.junit.jupiter.api.Assertions.*;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class TaskApiIntegrationTest {
    private static ConfigurableApplicationContext context;
    private static URI baseUri;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @BeforeAll
    static void startServer() {
        // A random port avoids the user's already running EC2 application.
        context = SpringApplication.run(DevopsflowApplication.class,
                "--server.port=0", "--server.address=127.0.0.1");
        String port = context.getEnvironment().getRequiredProperty("local.server.port");
        baseUri = URI.create("http://127.0.0.1:" + port);
    }

    @AfterAll
    static void stopServer() {
        if (context != null) {
            context.close();
        }
    }

    private static HttpResponse<String> request(String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(baseUri.resolve(path)).timeout(Duration.ofSeconds(30));
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return CLIENT.send(builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static long idOf(HttpResponse<String> response) {
        return ((Number) JsonPath.read(response.body(), "$.id")).longValue();
    }

    @Test
    void completeTaskLifecycleWorksOverHttp() throws Exception {
        var created = request("POST", "/api/tasks", """
                {"title":"  Learn Docker  ","description":"Build an image"}
                """);
        assertEquals(201, created.statusCode(), created.body());
        long id = idOf(created);
        assertEquals("/api/tasks/" + id, created.headers().firstValue("Location").orElseThrow());
        assertEquals("Learn Docker", JsonPath.read(created.body(), "$.title"));
        assertEquals("TODO", JsonPath.read(created.body(), "$.status"));

        var retrieved = request("GET", "/api/tasks/" + id, null);
        assertEquals(200, retrieved.statusCode(), retrieved.body());
        assertEquals(id, idOf(retrieved));
        assertEquals("Build an image", JsonPath.read(retrieved.body(), "$.description"));

        var updated = request("PUT", "/api/tasks/" + id, """
                {"title":"Learn Docker","description":"Image built","status":"DONE"}
                """);
        assertEquals(200, updated.statusCode(), updated.body());
        assertEquals(id, idOf(updated));
        assertEquals("DONE", JsonPath.read(updated.body(), "$.status"));
        assertEquals("Image built", JsonPath.read(updated.body(), "$.description"));

        var list = request("GET", "/api/tasks", null);
        assertEquals(200, list.statusCode(), list.body());
        List<Number> ids = JsonPath.read(list.body(), "$[*].id");
        List<Long> actual = ids.stream().map(Number::longValue).toList();
        assertTrue(actual.contains(id));
        assertEquals(actual.stream().sorted().toList(), actual);

        var deleted = request("DELETE", "/api/tasks/" + id, null);
        assertEquals(204, deleted.statusCode(), deleted.body());
        assertTrue(deleted.body().isEmpty());
        var missing = request("GET", "/api/tasks/" + id, null);
        assertEquals(404, missing.statusCode(), missing.body());
        assertEquals("Task not found", JsonPath.read(missing.body(), "$.title"));
        assertEquals("Task not found: " + id, JsonPath.read(missing.body(), "$.detail"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TODO", "IN_PROGRESS", "DONE"})
    void acceptsEachAllowedStatus(String status) throws Exception {
        var response = request("POST", "/api/tasks", "{\"title\":\"Status test\",\"status\":\"" + status + "\"}");
        assertEquals(201, response.statusCode(), response.body());
        assertEquals(status, JsonPath.read(response.body(), "$.status"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"title\":\"\"}", "{\"title\":\"   \"}", "{\"title\":null}", "{}"})
    void rejectsMissingOrBlankTitle(String body) throws Exception {
        var response = request("POST", "/api/tasks", body);
        assertEquals(400, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        assertEquals("Title must not be blank", JsonPath.read(response.body(), "$.errors.title"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"title\":\"Test\",\"status\":\"INVALID\"}",
            "{\"title\":\"Test\",\"status\":1}",
            "{\"title\":"})
    void rejectsInvalidStatusOrMalformedJson(String body) throws Exception {
        var response = request("POST", "/api/tasks", body);
        assertEquals(400, response.statusCode(), response.body());
        assertEquals("Invalid request body", JsonPath.read(response.body(), "$.title"));
    }

    @Test
    void missingTasksCannotBeUpdatedOrDeleted() throws Exception {
        var update = request("PUT", "/api/tasks/9223372036854775807",
                "{\"title\":\"Missing\",\"status\":\"DONE\"}");
        assertEquals(404, update.statusCode(), update.body());
        var delete = request("DELETE", "/api/tasks/9223372036854775807", null);
        assertEquals(404, delete.statusCode(), delete.body());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"title\":\"Valid\"}",
            "{\"title\":\"Valid\",\"status\":null}",
            "{\"title\":\" \",\"status\":\"DONE\"}"})
    void invalidUpdateDoesNotChangeTheStoredTask(String body) throws Exception {
        var created = request("POST", "/api/tasks", "{\"title\":\"Unchanged\"}");
        assertEquals(201, created.statusCode(), created.body());
        long id = idOf(created);
        var update = request("PUT", "/api/tasks/" + id, body);
        assertEquals(400, update.statusCode(), update.body());
        var stored = request("GET", "/api/tasks/" + id, null);
        assertEquals(200, stored.statusCode(), stored.body());
        assertEquals("Unchanged", JsonPath.read(stored.body(), "$.title"));
        assertEquals("TODO", JsonPath.read(stored.body(), "$.status"));
    }

    @Test
    void optionalDescriptionCanBeOmitted() throws Exception {
        var created = request("POST", "/api/tasks", "{\"title\":\"No description\"}");
        assertEquals(201, created.statusCode(), created.body());
        assertNull(JsonPath.read(created.body(), "$.description"));
        long id = idOf(created);
        var updated = request("PUT", "/api/tasks/" + id, "{\"title\":\"No description\",\"status\":\"IN_PROGRESS\"}");
        assertEquals(200, updated.statusCode(), updated.body());
        assertNull(JsonPath.read(updated.body(), "$.description"));
    }

    @Test
    void healthIsAvailableWithoutManagementDetails() throws Exception {
        var response = request("GET", "/actuator/health", null);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("UP", JsonPath.read(response.body(), "$.status"));
        assertFalse(response.body().contains("components"));
        assertEquals(404, request("GET", "/actuator/env", null).statusCode());
    }

    @Test
    void prometheusExportsJvmAndHttpMetrics() throws Exception {
        assertEquals(200, request("GET", "/api/tasks", null).statusCode());
        var metrics = request("GET", "/actuator/prometheus", null);
        assertEquals(200, metrics.statusCode(), metrics.body());
        assertTrue(metrics.body().contains("jvm_memory_used_bytes"));
        assertTrue(metrics.body().contains("http_server_requests_seconds_count"));
        assertEquals(404, request("GET", "/actuator/env", null).statusCode());
    }

    @Test
    void nonNumericIdReturnsBadRequest() throws Exception {
        assertEquals(400, request("GET", "/api/tasks/not-a-number", null).statusCode());
    }
}
