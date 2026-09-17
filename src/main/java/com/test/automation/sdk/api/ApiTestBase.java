package com.test.automation.sdk.api;

import com.test.automation.sdk.config.ConfigurationManager;
import com.test.automation.sdk.reporting.ExecutionReporting;
import com.test.automation.sdk.testbase.TestBase;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.test.automation.sdk.utility.Excel_Reader;

/**
 * Standalone base class for pure API test classes -- deliberately does
 * <b>not</b> extend {@link TestBase}/require a {@code WebDriver}, mirroring
 * how {@code MobileTestBase} is a peer of {@code TestBase} rather than a
 * subclass, but going one step further here since API tests need no browser
 * or device session at all.
 *
 * <p>Built on RestAssured -- the library this org's existing legacy API
 * projects (e.g. {@code DOITTQA_311_API}) already use -- rather than
 * introducing a new HTTP client choice. Per-environment base URLs, an
 * optional single auth header, timeouts, and evidence capture are all
 * resolved through {@link ConfigurationManager.ApiConfig}, following the
 * same system property &gt; environment variable &gt; project YAML &gt;
 * default precedence used everywhere else in the SDK.</p>
 *
 * <p>Integrates with the SDK's existing cross-cutting features by delegating
 * to {@link TestBase}'s static, driver-free {@code currentTestCaseName}
 * ThreadLocal (see {@link #setCurrentTestCaseName(String)}) and to the
 * owner-less {@link ExecutionReporting} overloads -- so API test runs show up
 * in Allure/Extent reports, the cross-run analytics store, and the flaky-test
 * quarantine exactly like Web/Mobile runs do, with no extra wiring required.</p>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * public class Test_GetUser extends ApiTestBase {
 *     @Test
 *     public void testGetUser(String testCaseName, ..., String runMode) {
 *         if ("N".equalsIgnoreCase(runMode)) throw new SkipException("Skipping: " + testCaseName);
 *         setCurrentTestCaseName(testCaseName);
 *         Response response = get("Get user by id", "/api/users/2");
 *         assertStatusCode(response, 200);
 *         assertJsonPath(response, "data.id", 2);
 *     }
 * }
 * }</pre>
 */
public class ApiTestBase {

    public static final Logger log = LogManager.getLogger(ApiTestBase.class.getName());

    private static final AtomicLong SEQUENCE = new AtomicLong();

    /** TestNG {@code environment} parameter, resolved once per test class -- same convention as {@code TestBase}. */
    protected String environment;

    /**
     * Resolves the {@code environment} TestNG parameter, matching the
     * {@code TestBase.setUp(@Optional String environment)} convention used
     * by Web/Mobile test classes so the same {@code -Denvironment=stg}
     * invocation works for API test classes too.
     */
    @BeforeClass
    @Parameters("environment")
    public void setUp(@Optional("stg") String environment) {
        this.environment = environment;
    }

    /**
     * Delegates to {@link TestBase#setCurrentTestCaseName(String)} -- the
     * same static, driver-free ThreadLocal used by Web/Mobile tests -- so
     * API test runs are attributed correctly in reports, analytics, and the
     * flaky-test quarantine without a separate, disconnected tracking
     * mechanism.
     */
    public static void setCurrentTestCaseName(String testCaseName) {
        TestBase.setCurrentTestCaseName(testCaseName);
    }

    public static String getCurrentTestCaseName() {
        return TestBase.getCurrentTestCaseName();
    }

    /**
     * Throws {@link SkipException} when {@code runMode} is {@code "N"} --
     * convenience wrapper around the skip check every generated test method
     * performs inline; safe to call instead of duplicating the
     * {@code if ("N".equalsIgnoreCase(runMode))} check in every test.
     */
    protected void checkRunMode(String testCaseName, String runMode) {
        if ("N".equalsIgnoreCase(runMode)) {
            throw new SkipException("Skipping: " + testCaseName);
        }
    }

    /**
     * Builds a fresh, pre-configured {@link RequestSpecification}: base URI
     * resolved for the current {@link #environment}, configured timeouts,
     * default JSON content type, and the single configured auth header (if
     * both {@code api.authHeaderName} and {@code api.authTokenEnvVar} are
     * set). Callers can further customize the returned spec (headers, query
     * params, body) before passing it to {@link #execute}.
     */
    protected RequestSpecification given() {
        ConfigurationManager.ApiConfig config = ConfigurationManager.getApiConfig();
        String baseUrl = config.baseUrl(environment);
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalStateException("No API base URL configured -- set api.baseUrl.<environment> or "
                    + "api.baseUrl in sdk-config.yaml (environment='" + environment + "').");
        }

        RestAssuredConfig restAssuredConfig = RestAssured.config()
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", config.connectionTimeoutMillis())
                        .setParam("http.socket.timeout", config.readTimeoutMillis()));
        if (config.relaxedHttpsValidation()) {
            restAssuredConfig = restAssuredConfig.sslConfig(SSLConfig.sslConfig().relaxedHTTPSValidation());
        }

        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(baseUrl)
                .setConfig(restAssuredConfig)
                .setContentType(ContentType.JSON);

        String authHeaderName = config.authHeaderName();
        String authTokenEnvVar = config.authTokenEnvVar();
        if (!authHeaderName.isEmpty() && !authTokenEnvVar.isEmpty()) {
            String tokenValue = System.getenv(authTokenEnvVar);
            if (tokenValue != null && !tokenValue.isEmpty()) {
                builder.addHeader(authHeaderName, tokenValue);
            } else {
                log.warn("[ApiTestBase] api.authHeaderName='{}' configured but environment variable '{}' is unset -- "
                        + "request will be sent without the auth header.", authHeaderName, authTokenEnvVar);
            }
        }

        return builder.build();
    }

    /** {@code GET} convenience wrapper around {@link #execute}. */
    protected Response get(String action, String path) {
        return execute(action, Method.GET, path, given());
    }

    /** {@code GET} convenience wrapper accepting a caller-customized request spec. */
    protected Response get(String action, String path, RequestSpecification spec) {
        return execute(action, Method.GET, path, spec);
    }

    /** {@code POST} convenience wrapper around {@link #execute}. */
    protected Response post(String action, String path, Object body) {
        return execute(action, Method.POST, path, given().body(body));
    }

    /** {@code PUT} convenience wrapper around {@link #execute}. */
    protected Response put(String action, String path, Object body) {
        return execute(action, Method.PUT, path, given().body(body));
    }

    /** {@code PATCH} convenience wrapper around {@link #execute}. */
    protected Response patch(String action, String path, Object body) {
        return execute(action, Method.PATCH, path, given().body(body));
    }

    /** {@code DELETE} convenience wrapper around {@link #execute}. */
    protected Response delete(String action, String path) {
        return execute(action, Method.DELETE, path, given());
    }

    /**
     * Executes {@code spec} against {@code path} using {@code method},
     * reporting the call through {@link ExecutionReporting} (start/complete/
     * failed, timed) and -- when {@code api.logRequestsAndResponses} is
     * enabled -- capturing the request/response payload to disk and
     * publishing it as evidence, exactly like the "capture once, publish
     * many" pattern used for Web/Mobile screenshots.
     */
    protected Response execute(String action, Method method, String path, RequestSpecification spec) {
        ConfigurationManager.ApiConfig config = ConfigurationManager.getApiConfig();
        long startedAt = System.currentTimeMillis();
        ExecutionReporting.actionStarted(action, path, method + " " + path);
        try {
            // NOTE: intentionally routed through RestAssured.given().spec(spec) rather than
            // calling spec.request(...) directly -- a RequestSpecification built via
            // RequestSpecBuilder().build() has its internal responseSpecification wiring set
            // up lazily by given(), and invoking .request()/.get() directly on it throws
            // "NullPointerException: Cannot get property 'assertionClosure' on null object"
            // (see https://github.com/rest-assured/rest-assured/issues/938).
            Response response = RestAssured.given().spec(spec).request(method, path);
            long durationMillis = System.currentTimeMillis() - startedAt;
            ExecutionReporting.actionCompleted(action, path,
                    method + " " + path + " -> " + response.getStatusCode(), durationMillis);
            if (config.logRequestsAndResponses()) {
                capturePayloadEvidence(config, action, method, path, response, null);
            }
            return response;
        } catch (RuntimeException e) {
            long durationMillis = System.currentTimeMillis() - startedAt;
            ExecutionReporting.actionFailed(action, path, method + " " + path + " failed", e, durationMillis);
            if (config.logRequestsAndResponses()) {
                capturePayloadEvidence(config, action, method, path, null, e);
            }
            throw e;
        }
    }

    private void capturePayloadEvidence(ConfigurationManager.ApiConfig config, String action, Method method,
                                         String path, Response response, Exception error) {
        try {
            Path dir = Paths.get(config.outputDirectory());
            Files.createDirectories(dir);
            String fileName = System.currentTimeMillis() + "_" + SEQUENCE.incrementAndGet() + "_"
                    + sanitize(action) + ".json";
            Path file = dir.resolve(fileName);

            StringBuilder dump = new StringBuilder();
            dump.append("{\n  \"action\": \"").append(escape(action)).append("\",\n")
                    .append("  \"method\": \"").append(method).append("\",\n")
                    .append("  \"path\": \"").append(escape(path)).append("\",\n");
            if (response != null) {
                dump.append("  \"statusCode\": ").append(response.getStatusCode()).append(",\n")
                        .append("  \"responseTimeMillis\": ").append(response.getTime()).append(",\n")
                        .append("  \"responseBody\": ").append(jsonStringOrRaw(response.getBody().asString())).append("\n");
            } else if (error != null) {
                dump.append("  \"error\": \"").append(escape(String.valueOf(error.getMessage()))).append("\"\n");
            }
            dump.append("}\n");

            FileUtils.writeStringToFile(file.toFile(), dump.toString(), StandardCharsets.UTF_8);

            String evidenceName = "API " + method + " " + path
                    + (response != null ? " [" + response.getStatusCode() + "]" : " [error]");
            ExecutionReporting.publishEvidence(file, evidenceName, "apiPayload");
        } catch (IOException e) {
            log.warn("[ApiTestBase] Failed to capture API payload evidence for action '{}'", action, e);
        }
    }

    private static String jsonStringOrRaw(String body) {
        if (body == null) {
            return "null";
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        return "\"" + escape(trimmed) + "\"";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    private static String sanitize(String value) {
        return value == null ? "action" : value.replaceAll("[^a-zA-Z0-9_-]+", "_");
    }

    /** Asserts the response's HTTP status code, reporting the check through {@link ExecutionReporting#validation}. */
    protected void assertStatusCode(Response response, int expectedStatusCode) {
        ExecutionReporting.validation("Status code", String.valueOf(expectedStatusCode),
                String.valueOf(response.getStatusCode()));
        Assert.assertEquals(response.getStatusCode(), expectedStatusCode,
                "Unexpected status code. Response body: " + response.getBody().asString());
    }

    /**
     * Asserts the value at {@code jsonPath} in the response body equals
     * {@code expectedValue} (RestAssured's {@code JsonPath} expression
     * syntax, e.g. {@code "data.id"} or {@code "items[0].name"}).
     */
    protected void assertJsonPath(Response response, String jsonPath, Object expectedValue) {
        Object actualValue = response.jsonPath().get(jsonPath);
        ExecutionReporting.validation("JSON path '" + jsonPath + "'",
                String.valueOf(expectedValue), String.valueOf(actualValue));
        Assert.assertEquals(actualValue, expectedValue, "Unexpected value at JSON path '" + jsonPath + "'");
    }

    /** Asserts the response was received within {@code maxMillis}. */
    protected void assertResponseTimeUnder(Response response, long maxMillis) {
        long actual = response.getTime();
        ExecutionReporting.validation("Response time under " + maxMillis + "ms",
                "<= " + maxMillis + "ms", actual + "ms");
        Assert.assertTrue(actual <= maxMillis,
                "Response time " + actual + "ms exceeded the maximum of " + maxMillis + "ms");
    }

    /**
     * Asserts the response body matches the given JSON schema (a classpath
     * resource path, e.g. {@code "schemas/user-response.json"} under
     * {@code src/test/resources}).
     */
    protected void assertMatchesJsonSchema(Response response, String schemaClasspathResource) {
        ExecutionReporting.validation("Matches JSON schema", schemaClasspathResource, "(see response body)");
        response.then().assertThat().body(JsonSchemaValidator.matchesJsonSchemaInClasspath(schemaClasspathResource));
    }

    /**
     * Reads Excel test data, mirroring {@code TestBase.getData(String, String)}
     * so API test classes use the same {@code @DataProvider} pattern as
     * Web/Mobile ones (first parameter always {@code testCaseName}, resolved
     * relative to {@code System.getProperty("user.dir")}).
     *
     * @param excelFilePath path (relative to the working directory) to the .xlsx file
     * @param sheetName sheet name to read
     */
    protected Object[][] getData(String excelFilePath, String sheetName) throws IOException {
        String path = System.getProperty("user.dir") + File.separator + excelFilePath;
        log.info("[ApiTestBase] Reading Excel file '{}', sheet '{}'", path, sheetName);
        return Excel_Reader.getDataFromSheet(path, sheetName);
    }
}
