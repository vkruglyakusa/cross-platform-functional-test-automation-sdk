# API TestBase Reference

Every method on `com.test.automation.sdk.api.ApiTestBase`, with usage
guidance. This is the API-testing analogue of
[`TESTBASE-API.md`](TESTBASE-API.md) (web) and
[`MOBILE-TESTBASE-API.md`](MOBILE-TESTBASE-API.md) (mobile) -- `ApiTestBase`
is a standalone base class that does **not** extend `TestBase` and requires
no `WebDriver`/device session at all, built on
[RestAssured](https://rest-assured.io/).

For setup and configuration, see [`SDK-USER-GUIDE.md`](SDK-USER-GUIDE.md)
section 18, "API Testing (`ApiTestBase`)".

---

## Table of Contents

1. [Lifecycle & Setup](#1-lifecycle--setup)
2. [Building Requests](#2-building-requests)
3. [Executing Requests](#3-executing-requests)
4. [Assertions](#4-assertions)
5. [Test Data](#5-test-data)

---

## 1. Lifecycle & Setup

### `setUp(String environment)`
`@BeforeClass` hook. Reads the TestNG `environment` parameter (defaults to
`"stg"`, same convention as `TestBase.setUp(@Optional String environment)`)
and stores it on the `environment` field. Called automatically -- you do not
call this yourself.

### `environment`
Protected `String` field holding the resolved environment name, used by
`given()` to look up `api.baseUrl.<environment>`.

### `setCurrentTestCaseName(String)` / `getCurrentTestCaseName()`
Static methods that delegate directly to `TestBase.setCurrentTestCaseName(...)`
/ `TestBase.getCurrentTestCaseName()` -- the same driver-free ThreadLocal used
by Web/Mobile tests, so API test runs are attributed correctly in reports,
analytics, and the flaky-test quarantine without a separate tracking
mechanism. Call `setCurrentTestCaseName(testCaseName)` at the start of every
`@Test` method, exactly like Web/Mobile test classes do.

### `checkRunMode(String testCaseName, String runMode)`
Throws `SkipException("Skipping: " + testCaseName)` when `runMode` is `"N"`
(case-insensitive) -- convenience wrapper around the skip check every
generated test method performs; equivalent to writing the check inline.

---

## 2. Building Requests

### `given()`
Returns a fresh `RequestSpecification`: base URI resolved via
`ConfigurationManager.getApiConfig().baseUrl(environment)`, connection/read
timeouts from `api.connectionTimeoutMillis`/`api.readTimeoutMillis`, default
`ContentType.JSON`, and -- when both `api.authHeaderName` and
`api.authTokenEnvVar` are configured -- a single auth header whose value is
read from the named environment variable. Throws `IllegalStateException` with
a clear message if no base URL is configured for the current environment.
Customize the returned spec (extra headers, query params, body) before
passing it to `execute`/the `get(action, path, spec)` overload for anything
`given()` doesn't cover out of the box.

---

## 3. Executing Requests

### `get(String action, String path)` / `get(String action, String path, RequestSpecification spec)`
`post(String action, String path, Object body)` / `put(...)` / `patch(...)` / `delete(String action, String path)`
Convenience wrappers around `execute`, building the request via `given()`
(or an explicitly supplied spec) and serializing `body` as JSON for
POST/PUT/PATCH.

### `execute(String action, Method method, String path, RequestSpecification spec)`
The core call path every convenience wrapper routes through. Times the
call and reports it through `ExecutionReporting.actionStarted`/
`actionCompleted`/`actionFailed`; when `api.logRequestsAndResponses` is
enabled (default), captures the request/response payload to
`api.outputDirectory` and publishes it as evidence (`ExecutionEventType
.API_PAYLOAD_CAPTURED`), so it appears as an attachment in Allure/Extent
reports the same way a screenshot does for Web/Mobile tests. Re-throws any
`RuntimeException` from the underlying HTTP call after reporting it as
failed.

---

## 4. Assertions

### `assertStatusCode(Response response, int expectedStatusCode)`
Asserts the response's HTTP status code, reporting the check through
`ExecutionReporting.validation(...)`. Failure message includes the response
body for fast diagnosis.

### `assertJsonPath(Response response, String jsonPath, Object expectedValue)`
Asserts the value at `jsonPath` (RestAssured `JsonPath` expression syntax,
e.g. `"data.id"` or `"items[0].name"`) equals `expectedValue`.

### `assertResponseTimeUnder(Response response, long maxMillis)`
Asserts the response was received within `maxMillis`.

### `assertMatchesJsonSchema(Response response, String schemaClasspathResource)`
Asserts the response body matches the given JSON schema, a classpath
resource path (e.g. `"schemas/user-response.json"` under
`src/test/resources`), via RestAssured's `json-schema-validator` module.

---

## 5. Test Data

### `getData(String excelFilePath, String sheetName)`
Reads Excel test data via `Excel_Reader.getDataFromSheet(...)` -- the same
utility `TestBase.getData(...)` uses -- so API test classes follow the exact
same `@DataProvider` pattern as Web/Mobile ones (first parameter always
`testCaseName`, path resolved relative to `System.getProperty("user.dir")`).
