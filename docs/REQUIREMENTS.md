# Requirements — Mobile Functional Test Automation SDK

> Living document. Fields marked **TBD** need your input before Phase 1 implementation
> starts. See `docs/proposals/mobile-automation-strategy.md` for the research and
> architecture rationale behind these requirements.

## 1. Business goal

Enable automated functional testing of native/hybrid mobile apps (Android and/or iOS),
using the same team conventions (TestNG, Excel-driven data, Allure reporting, POM-style
page objects) already established in `functional-test-automation-sdk`, without
destabilizing that production SDK.

## 2. In-scope platforms (Phase 1)

- [x] Android — confirmed in scope (matches `311_Mobile_Automation` prior art)
- [x] iOS — confirmed in scope (matches `311_Mobile_Automation` prior art)
- Both, in parallel — **tentatively yes**, pending final sign-off (§9 of strategy doc)

## 3. App types

- [x] Native app — confirmed (matches prior art)
- [ ] Hybrid (WebView-based) app — **TBD**, not yet confirmed as in-scope

## 4. Device access strategy

- **Primary (Phase 1):** BrowserStack App Automate — cloud real-device grid.
  Already subscribed. ✅ decided.
- **Secondary (Phase 2):** Local Appium (emulator/simulator/USB-attached device) for
  engineers who want zero cloud cost / offline development.
- **Not in scope unless requirements change:** AWS Device Farm, Firebase Test Lab
  (see strategy doc §4 for why).

## 5. First consumer / pilot project — prior art identified

**`311_Mobile_Automation`** (Azure DevOps, `OTI QA Automation` project, `trunk`
branch) is an existing, working mobile automation project for the NYC 311 app
(Android + iOS, native, BrowserStack + local Appium, official `io.appium:java-client`
9.4.0). Proposed as the Phase 1 pilot: **TBD sign-off** — confirm whether Phase 1 is a
refactor/generalization of this existing project (recommended, see strategy doc §6a)
or validation against a different/new app.

Still need to confirm:
- Who owns test data / app builds (APK/IPA) for upload to BrowserStack for 311 going forward?
- Whether 311's existing BrowserStack subscription/project is the one this SDK validates against, or a separate one.

## 6. Functional requirements (draft — refine as decided)

| # | Requirement | Priority | Status |
|---|---|---|---|
| FR-1 | Ability to start a session against BrowserStack App Automate given an app-under-test (APK/IPA) and device/OS capabilities | Must | Planned |
| FR-2 | Upload app binary to BrowserStack via REST API and reuse the returned app-id in session capabilities | Must | Planned |
| FR-3 | Common mobile gesture helpers: tap, swipe, scroll, long-press, hide keyboard | Must | Planned |
| FR-4 | App lifecycle helpers: install, launch, terminate, reset app state between tests | Must | Planned |
| FR-5 | TestNG lifecycle integration (`MobileTestBase`) mirroring desktop `TestBase` conventions (config loading, Allure reporting, logging) | Must | Planned |
| FR-6 | Excel-driven `@DataProvider` support, reusing/adapting the desktop SDK's Excel reader utility | Should | Planned |
| FR-7 | Local Appium provider as an alternate `MobileProvider` (Phase 2) | Should | Deferred to Phase 2 |
| FR-8 | Additional cloud providers (Sauce Labs, LambdaTest, Perfecto) as pluggable `MobileProvider` implementations | Could | Deferred to Phase 3 |
| FR-9 | Mobile-equivalent of the web `ElementCrawler`/`PageObjectGenerator` for scaffolding mobile page objects, using one-shot `getPageSource()` capture + local in-memory uniqueness computation (not per-candidate remote `findElements` calls) — see strategy doc §8a | Must | **Elevated from Could/TBD** — confirmed crucial (user feedback); design drafted in strategy doc §8a, pending sign-off (§9 item 6) on timing (Phase 1 vs. Phase 1.5) |

## 7. Non-functional requirements

| # | Requirement | Notes |
|---|---|---|
| NFR-1 | Java 20 (final, both SDKs) | **Decided 2026-09-02** — official `io.appium:java-client` requires Java 11+; `311_Mobile_Automation` prior art already runs on JDK 20 in CI. Desktop `functional-test-automation-sdk` has also been bumped from Java 8 to Java 20 so both SDKs share one target version across the ecosystem (see its CHANGELOG). |
| NFR-2 | Use official `io.appium:java-client`, not a hand-rolled `executeScript`-only client | **Revised** — matches proven, working prior art (`311_Mobile_Automation`); `executeScript("mobile: ...")` kept only as a fallback for gestures without a typed API |
| NFR-3 | No shared release train with `functional-test-automation-sdk` | Separate repo, separate versioning/CHANGELOG. (Java version is now shared/aligned at 20, but release cadence/versioning remains independent.) |
| NFR-4 | Secrets (BrowserStack credentials) never committed to git | Mirror `sdk-config.yaml` gitignore pattern from desktop SDK — **note:** `311_Mobile_Automation`'s current `browserstack.yml` has credentials committed in plaintext; do not repeat this in the new SDK |
| NFR-5 | Reuse shared, Java-8-safe utilities from the desktop SDK via a dependency (config/reporting/Excel), not by copy-paste | Avoids duplicate maintenance |

## 8. Constraints carried over from the desktop SDK (framework/tooling conventions only — NOT the Java 8 pin, see NFR-1)

- Java 20 source/target compatibility (aligned with the desktop SDK — see NFR-1).
- TestNG as the test runner.
- Allure for step reporting (`step("...")`) — reuse the same reporting conventions if
  practical.
- `runMode` skip-logic convention for data-driven tests, if Excel-driven data providers
  are adopted here too.

## 9. Open questions (duplicated from strategy doc §9 for visibility)

1. Confirm final repo name: `mobile-functional-test-automation-sdk` (as created).
2. Confirm platform scope for Phase 1 (Android / iOS / both).
3. Identify pilot/consumer app for Phase 1 validation.
4. Confirm whether a mobile locator-strategy instructions doc is in scope (in
   addition to the FR-9 crawler, now elevated to Must — see strategy doc §8a), and
   for which phase.
5. Confirm remote git hosting for this repo (same Azure DevOps project as the other
   SDKs, or elsewhere?).
6. Confirm release/publishing process expectations (own Azure Artifacts feed? shared
   `maven-repository`? own `release.ps1`?).

## 10. Non-goals (explicit, to prevent scope creep)

- AWS Device Farm and Firebase Test Lab support (excluded per strategy doc §4).
- Reusing desktop `WebDriverFactory`/`TestBase` directly (architecturally different —
  new mobile-specific equivalents will be built instead).
- Multi-provider abstraction complexity before there's a concrete need for a second
  provider beyond BrowserStack.
