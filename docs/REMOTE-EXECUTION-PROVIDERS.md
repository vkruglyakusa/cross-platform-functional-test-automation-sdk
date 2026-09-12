# Remote Execution Provider Architecture

## Status

This document describes the provider-neutral execution contract introduced in SDK 1.2.x. It is the reference for SDK, consumer-template, and Automation Studio changes.

The architectural rule is:

> A provider is an execution destination, not a run mode.

## Canonical execution key

Every driver session is selected using four independent dimensions:

```text
Platform + AutomationTechnology + RunMode + ProviderId
```

Examples:

```text
WEB    + SELENIUM + LOCAL  + none
WEB    + SELENIUM + REMOTE + browserstack
ANDROID+ APPIUM   + REMOTE + browserstack
ANDROID+ APPIUM   + REMOTE + custom
IOS    + APPIUM   + REMOTE + custom
```

`SessionFactoryRegistry` is the only authoritative session resolver. Adding a provider must not introduce another resolver or provider-specific branching in test classes, page objects, crawlers, or reporting.

## Core types

- `Platform`: `WEB`, `ANDROID`, or `IOS`.
- `AutomationTechnology`: currently `SELENIUM` or `APPIUM`.
- `RunMode`: canonical values are `LOCAL` and `REMOTE`.
- `ProviderId`: normalized, case-insensitive provider identifier containing alphanumeric characters and hyphens.
- `ExecutionContext`: immutable session request containing all resolution dimensions plus browser/device information.
- `SessionFactory`: provider adapter contract that creates a Selenium/Appium `WebDriver`.
- `SessionFactoryRegistry`: thread-safe registry keyed by all four dimensions.

Provider IDs are required for `REMOTE` and prohibited for `LOCAL`. Duplicate registration fails during bootstrap. Deliberate test/runtime replacement must use the explicit `registerOrReplace` operation.

## Current provider support

| Destination | Web Selenium | Android Appium | iOS Appium | Status |
|---|---:|---:|---:|---|
| Local | Yes | Yes | Yes | Existing |
| BrowserStack | Yes | Yes | Yes | Migrated to provider-aware registry |
| Custom remote Appium | N/A | Yes | Yes | Provider ID `custom` |
| Custom Selenium Grid | Yes | N/A | N/A | Provider ID `custom`; Chrome, Firefox, Edge |
| Sauce Labs | Planned | Planned | Planned | Add after contract/configuration validation |
| TestMu | Planned | Planned | Planned | Add after contract/configuration validation |

“Planned” means the architecture can accept the adapter; it does not imply a tested provider integration.

## Compatibility mapping

Existing consumer projects remain supported during the 1.x migration window:

```text
BROWSERSTACK  -> REMOTE + providerId=browserstack
REMOTE_APPIUM -> REMOTE + providerId=custom
```

The deprecated enum values remain readable by old Maven profiles and system properties. `SessionFactoryRegistry` canonicalizes those values before lookup, so legacy and new callers use the same registered provider factories rather than two execution paths.

New code should construct canonical contexts:

```java
ExecutionContext browserStack = ExecutionContext.forWebWithProvider(
        "chrome", RunMode.REMOTE, new ProviderId("browserstack"));

ExecutionContext remoteAndroid = ExecutionContext.forMobileWithProvider(
        Platform.ANDROID,
        "Pixel_8",
        RunMode.REMOTE,
        new ProviderId("custom"));

ExecutionContext seleniumGrid = ExecutionContext.forWebWithProvider(
        "firefox", RunMode.REMOTE, new ProviderId("custom"));
```

Custom Selenium Grid configuration:

```yaml
execution:
  runMode: remote
  provider: custom

providers:
  custom:
    hubUrl: "https://grid.company.test/wd/hub"
    allowInsecureHttp: false
```

For a trusted local/private Grid that exposes only HTTP, set
`allowInsecureHttp: true` explicitly. URLs containing credentials, query
parameters, fragments, unsupported schemes, or no host are rejected before a
session is opened. The endpoint can be overridden using
`-Dproviders.custom.hubUrl=...` or `PROVIDERS_CUSTOM_HUBURL`.

User-managed remote Appium uses the same fail-fast endpoint policy:

```yaml
mobile:
  appium:
    url: "https://mac-runner.company.test:4723/wd/hub"
    allowInsecureHttp: false
```

`mobile.appium.url` is required for remote Appium and never falls back to the
local Appium endpoint. Credentials, query parameters, and fragments are
rejected. Plain HTTP requires the explicit `mobile.appium.allowInsecureHttp`
opt-in and should be limited to a trusted private runner.

Legacy configuration removal is a breaking change and must not occur before a documented major-version migration.

## Adding a provider

A provider implementation must:

1. Use `RunMode.REMOTE`.
2. Return its canonical `ProviderId` from `SessionFactory.getProviderId()`.
3. Supply one factory for every supported platform/technology combination.
4. Register through `SessionFactoryRegistry`.
5. Validate required endpoint, credentials, capabilities, and platform inputs before opening a session.
6. Keep credentials out of source control, exceptions, logs, reports, and serialized execution metadata.
7. Add unit tests for metadata, resolution, missing configuration, and duplicate registration.
8. Add credential-backed integration tests only when the provider environment is available.

Provider-specific capability building belongs inside the provider adapter. Tests and reusable SDK components must not contain `if (provider == ...)` branches.

## Configuration and security requirements

Provider configuration must flow through `ConfigurationManager`, whose precedence is:

```text
JVM/system property > environment variable > sdk-config.yaml > SDK default
```

`RunMode.resolve()` preserves legacy system-property precedence, then reads
`execution.runMode` through `ConfigurationManager`. Canonical projects therefore
use `execution.runMode` and `execution.provider` in YAML, while command-line runs
can continue to use `-Drun.mode=REMOTE` as the highest-precedence override.

Before Automation Studio exposes arbitrary remote endpoints, the SDK/Studio integration must add:

- HTTPS and endpoint syntax validation;
- an administrator-controlled host/network policy to prevent SSRF;
- secret redaction in logs, errors, events, and ED-RCA artifacts;
- typed or allowlisted capabilities, with provider-namespaced extensions;
- connection and session-creation timeouts;
- provider-specific preflight diagnostics.

## Reporting contract

Execution events sent to Studio or ED-RCA should eventually carry these non-secret fields:

```text
platform
automationTechnology
runMode
providerId
browserName or deviceName
providerSessionId
providerJobUrl (when safe and available)
```

Provider metadata is diagnostic context. Studio should not duplicate screenshots, DOM, stack traces, logs, or build artifacts already owned by ED-RCA.

## Delivery sequence

1. Provider-neutral SDK core and compatibility mapping.
2. BrowserStack and custom Appium migration.
3. Custom Selenium Grid adapter and configuration model. **Complete.**
4. Web and mobile consumer-template updates.
5. SDK and template documentation plus regression validation.
6. Automation Studio provider selection, artifact upload, and preflight UI.
7. Sauce Labs and TestMu adapters after concrete accounts and capability contracts are available.

Automation Studio must consume the tested SDK contract; it must not define a competing provider model.
