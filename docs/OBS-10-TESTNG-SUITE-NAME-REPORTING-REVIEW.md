# OBS-10 — Correct TestNG Suite Name Across SDK Reporting

## Consumer Request
Report suite name should come from TestNG/testng.xml rather than Maven/Surefire internal naming.

## Root Cause
Current displayed value:
Reporter behavior was inconsistent: the unified SDK event model had no suite metadata, so logs and Extent could not display the real TestNG suite at all, and reporter-specific/default hierarchy could fall back to synthetic names such as `Surefire suite` outside the SDK's control.

Source of current value:
`ExecutionReporting` only resolved `testName` (`Class.method`) and `testCaseName`. It did not resolve or publish `ISuite.getName()` / `ITestContext.getName()` through OBS-8 unified events.

Why incorrect value appeared:
The SDK had no single authoritative suite-identity source in its reporting layer, so reporters either guessed, inherited upstream defaults, or showed no suite identity. The fix therefore belonged at the shared metadata boundary, not inside Allure alone.

## Authoritative Metadata
TestNG suite:
`ISuite.getName()` from the live TestNG runtime. This is the business-facing suite name and corresponds to `<suite name="...">`.

TestNG test:
`ITestContext.getName()` from the live TestNG runtime. This remains distinct from the suite and corresponds to `<test name="...">`.

Class:
`ITestResult.getTestClass().getRealClass().getSimpleName()`

Method:
`ITestResult.getMethod().getMethodName()`

## Implementation
Files changed:
- `src/main/java/com/test/automation/sdk/reporting/ExecutionEvent.java`
- `src/main/java/com/test/automation/sdk/reporting/ExecutionEventType.java`
- `src/main/java/com/test/automation/sdk/reporting/ExecutionReporting.java`
- `src/main/java/com/test/automation/sdk/reporting/ExecutionLogReporter.java`
- `src/main/java/com/test/automation/sdk/reporting/AllureExecutionReporter.java`
- `src/main/java/com/test/automation/sdk/reporting/ExtentExecutionReporter.java`
- `src/main/java/com/test/automation/sdk/listener/Listener.java`
- `src/test/java/com/test/automation/sdk/reporting/ExecutionReportingUnitTest.java`
- `src/test/java/com/test/automation/sdk/reporting/ExecutionReportingArtifactCompatibilityTest.java`
- `src/test/java/com/test/automation/sdk/reporting/TestNgSuiteMetadataIntegrationTest.java`
- `README.md`
- `src/main/resources/README.md`
- `SDK-USER-GUIDE.md`
- `src/main/resources/SDK-USER-GUIDE.md`
- `CHANGELOG.md`

Classes changed:
- `ExecutionEvent`
- `ExecutionReporting`
- `ExecutionLogReporter`
- `AllureExecutionReporter`
- `ExtentExecutionReporter`
- `Listener`
- reporting regression tests listed above

Reporting metadata changes:
- Added unified metadata fields: `suiteName`, `testNgTestName`, `className`, `methodName`
- Added explicit fallback policy for non-meaningful suite names: `ISuite.getName()` -> meaningful `ITestContext.getName()` -> test class simple name -> `Unknown Suite`
- Added suite lifecycle event types: `SUITE_STARTED`, `SUITE_COMPLETED`

Lifecycle changes:
- `Listener.onStart(ISuite)` now emits unified suite-start metadata
- `Listener.onFinish(ISuite)` now emits unified suite-complete metadata before OBS-9 report generation
- `ExecutionReporting.onTestStarted/onTestPassed/onTestFailed/onTestSkipped` now refresh authoritative TestNG suite/test/class/method metadata once and publish it on every event

## Reporter Validation
### Log
Expected:
Human-readable logs identify the real TestNG suite and keep suite/test/class/method distinct.

Actual:
`target/test-work/reporting-pass/logs/sdk-reporting.log` contains `Suite started: Example Suite Name`, `suite=Example Suite Name`, and `testngTest=Example Test Group`. Failure-path logs carry the same metadata.

### Allure
Expected:
User-facing suite identity reflects the TestNG suite name; TestNG test and class remain distinct.

Actual:
Real generated Allure result JSON uses `parentSuite=Example XML Suite`, `suite=Example XML Test Group`, `subSuite=ExampleXmlSuiteTest`. Unified manual-probe Allure JSON now also carries `parentSuite=Example Suite Name`, `suite=Example Test Group`, `subSuite=StepEnabledProbeTestBase`.

Evidence:
- `TestNgSuiteMetadataIntegrationTest.xmlSuiteNameComesFromSuiteElement`
- `ExecutionReportingArtifactCompatibilityTest.passingStoryCreatesAlignedArtifacts`
- `ExecutionReportingArtifactCompatibilityTest.failingStoryCreatesAlignedFailureArtifacts`

### ExtentReports
Expected:
Any suite metadata shown by Extent comes from the same authoritative TestNG suite identity.

Actual:
`target/test-work/reporting-pass/extent/Test-Automaton-Report.html` and the failure-path HTML both contain `Example Suite Name` and `Example Test Group` emitted from the unified event stream.

Evidence:
- `ExecutionReportingArtifactCompatibilityTest.passingStoryCreatesAlignedArtifacts`
- `ExecutionReportingArtifactCompatibilityTest.failingStoryCreatesAlignedFailureArtifacts`

## Multiple Suite Validation
Result:
Passed. `TestNgSuiteMetadataIntegrationTest.multipleSuitesRetainIndependentSuiteNames` runs two synthetic suites in one TestNG invocation and verifies separate Allure results/containers for `Example Multi Suite A` and `Example Multi Suite B`.

## Parallel Safety
Result:
Passed. `ExecutionReportingUnitTest.parallelIsolationIsMaintained` verifies thread-local reporting state keeps `Suite-TC-A` and `Suite-TC-B` isolated across parallel execution.

## Backward Compatibility
Result:
Passed. OBS-8 unified step/evidence flow is unchanged apart from added metadata, OBS-9 automatic Allure HTML generation remains unchanged, and `mvn clean test` / `mvn clean package` both succeeded. The 1 Azure Test Case ID = 1 `@Test` method contract was not changed.

## Automated Tests
Previous:
579

Added:
5

Current:
584

Failures:
0

Errors:
0

Skipped:
0

## Package Validation
Result:
`mvn clean package` succeeded. JAR, sources JAR, and javadoc JAR were produced. Existing javadoc warnings remain warnings only; no new packaging failure was introduced.

## Remaining Risks
- Downstream reports that bypass the SDK listener/unified reporting layer and rely only on third-party defaults can still show whatever those third parties choose; OBS-10 fixes the SDK-owned reporting path.
- Existing Surefire `LATEST` warning for BrowserStack remains pre-existing and unchanged.
- Existing javadoc warnings remain pre-existing and unchanged.

## Final Decision
OBS-10 READY FOR REVIEW
