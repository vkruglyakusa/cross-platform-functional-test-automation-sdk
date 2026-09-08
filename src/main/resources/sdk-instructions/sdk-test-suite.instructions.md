---
applyTo: "src/test/java/**/*.java"
---

# Skill: SDK Test Suite Rules

## Trigger
Apply this skill when adding or modifying tests in test-automation-sdk/src/test/java/.

## Purpose
SDK tests validate framework logic ONLY - no browser, no application, no end-to-end flows.
These are fast unit/integration tests that run in CI without any external dependencies.

---

## Test Framework
- JUnit 5 (jupiter) - NOT TestNG
- Surefire configured with surefire-junit-platform provider
- All 39 tests must pass on every commit before running mvn install

---

## Test Class Rules

```java
package com.test.automation.sdk.<subpackage>;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class MyComponentTest {

    @Test
    void testSomeBehavior() {
        // Arrange
        // Act
        // Assert
        assertEquals(expected, actual, "Descriptive failure message");
    }
}
```

### Rules:
- Use JUnit 5 Assertions (assertThrows, assertEquals, assertTrue, assertFalse, assertNotNull)
- No Selenium WebDriver instantiation in SDK unit tests
- No browser, no ChromeDriver, no network calls
- No TestNG annotations (@Test from TestNG is wrong - use org.junit.jupiter.api.Test)
- Test method names in camelCase, descriptive of what they verify

---

## Test Coverage Targets

| Component | Test class | Min tests |
|---|---|---|
| SdkConfig | SdkConfigTest | 9 |
| TestBase (logic) | TestBaseUnitTest | 6 |
| PageContext | PageContextTest | 6 |
| Retry / RetryListener | RetryTest | 6 |
| Excel_Reader | Excel_ReaderTest | 4 |
| Class loading | SdkSmokeTest | 8 |

Total: 39 tests minimum.

---

## Running SDK Tests
```bash
cd C:\Users\vkruglyak\IdeaProjects\test-automation-sdk
mvn clean test
```
Expected output: Tests run: 39, Failures: 0, Errors: 0, Skipped: 0

---

## Adding New Tests
When adding a new SDK component:
1. Create corresponding test class in matching sub-package under src/test/java
2. Cover: normal case, edge case, error/exception case
3. Verify total count still matches or exceeds 39
4. Run mvn clean test before committing

---

## Known Constraints
- poi-ooxml must NOT be declared at test scope in pom.xml - it must be compile scope only
- junit-platform-launcher must be in test scope for surefire-junit-platform to work
- When both TestNG and JUnit5 are on classpath, surefire-junit-platform provider must be explicitly configured as a surefire plugin dependency