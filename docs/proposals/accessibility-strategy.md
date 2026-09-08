# Accessibility Testing Strategy — Vendor Research & Architecture Proposal

**Status:** DRAFT — for review. Research-backed, not yet implemented.
**Author context:** produced during joint research session with @vkruglyak_NYC.
**Scope:** compare our SDK's existing accessibility implementation against leading
commercial/OSS accessibility platforms, and propose architecture improvements
**without** replacing our SDK with a vendor product.

**Sourcing legend:** 🟢 PRIMARY (vendor docs/GitHub/site) · 🔵 SECONDARY (review
site/archive) · ⚠️ GAP (unverifiable in research pass, flagged not asserted) ·
🧭 OPINION (architectural recommendation, not a verified fact).

Research performed by two parallel research agents (web-fetch of vendor sites,
GitHub repos, npm/Maven registries, Wayback Machine where live pages were
JS-rendered/paywalled) plus direct inspection of our own SDK source.

---

## 0. Executive Summary

Our SDK's accessibility engine is **architecturally sound and already follows the
"vendor engine + own everything else" pattern** the user asked us to validate:
Layer 1 (static WCAG scanning) wraps the free/open-source **axe-core** engine
(`com.deque.html.axe-core:selenium:4.10.1`, MPL-2.0) directly via Maven — the same
engine that underlies Microsoft Accessibility Insights, and one of two engines
Pa11y supports. Four additional layers (Interaction, WCAG 2.2, Structural, Motion)
plus session management (dedup, SPA auto-scan, noise thresholds, allowlisting) are
100% custom-written. Mobile-native scanning (`NativeAccessibilityChecker`) has no
vendor dependency at all (axe-core requires a DOM, native mobile has none).

**Nothing here should be replaced.** The gaps we found are not "wrong engine
choice" — axe-core is what most competitors use too — they are in three areas
none of the researched vendors solve for free either: (1) reporting output is
**string-based, not structured** (no normalized finding object); (2) there is
**no historical/trend dashboard or baseline/regression comparison**; (3) **no VPAT/
compliance-document generation**. These are exactly the areas enterprise vendors
(Level Access AMP, Siteimprove, Deque Bundle) charge quote-based enterprise
pricing for — and even they largely deliver dashboards/VPATs as much through
**paid human audit services** as through pure automation.

**Recommendation in one sentence:** keep axe-core as the pluggable Layer-1 engine,
introduce a normalized `AccessibilityFinding` model as the contract between engine
and reporting (this unlocks everything else cheaply), and build a lightweight
self-hosted baseline/history store — modeled on IBM Equal Access's file-based
baseline approach and Pa11y Dashboard's self-hosted model, not a SaaS rebuild of
Siteimprove.

---

## 1. What Our SDK Currently Supports (verified by source inspection)

Source files inspected: `AccessibilityChecker.java` (~2200 lines),
`A11ySessionManager.java` (~48KB), `A11yConfig.java`, `A11yTestNGListener.java`,
`AccessibilityViolationException.java`, `report/A11yReporter.java` +
`Slf4jReporter`/`NoOpReporter`/`AllureA11yReporter`/`CompositeReporter`,
`AccessibilityExcelReporter.java`, `mobile/accessibility/NativeAccessibilityChecker.java`
+ `NativeAccessibilityIssue.java`, `SDK-USER-GUIDE.md` §14.

### 1.1 Engine (web/hybrid)

- **Layer 1 — axe-core (vendor):** `com.deque.html.axe-core:selenium:4.10.1`
  (MPL-2.0), wraps `AxeBuilder`. Inherits axe-core's WCAG 2.0/2.1/2.2 A/AA/AAA
  rule coverage, ARIA/color/forms/keyboard-markup categories, `impact` severity
  scale (`minor|moderate|serious|critical`), and axe's "returns zero false
  positives" design philosophy (a vendor claim, not independently audited — see
  §2.1).
- **Layer 2 — Interaction (custom):** engine-tagged `"Interaction:<checkId>"` in
  reports — live, non-static checks axe-core's static DOM pass cannot do.
- **Layer 3 — WCAG 2.2 (custom):** supplements axe-core's own 2.2 coverage.
- **Layer 4 — Structural (custom).**
- **Layer 5 — Motion (custom).**
- **Mobile-native — `NativeAccessibilityChecker` (100% custom, no vendor dep):**
  4 rules today — missing-accessible-name, unlabeled-editable-field,
  duplicate-accessible-name, touch-target-too-small. Necessary because axe-core
  requires a DOM; native mobile has none (same limitation researched vendors hit —
  Microsoft's Android tool paired axe with Google's ATFA instead of axe alone).

### 1.2 Session management (`A11ySessionManager`) — more sophisticated than assumed

- Global enable/disable kill switch (`accessibility.checking.enabled`).
- **Scan deduplication**: URL + DOM-fingerprint based, configurable cooldown
  (`accessibility.session.dedup.cooldown.ms`) and per-URL scan cap
  (`accessibility.session.max.scans.per.url`).
- **SPA support**: `wrapWithAutoScan()` returns a `WebDriver` decorated via
  Selenium's `EventFiringDecorator`, plus a URL poller (`startUrlPoller`) for
  hash-routing SPAs that don't fire normal navigation events.
- **Dialog/modal watcher** (`startWatcher`, `computeDialogSignature`) — detects
  and re-scans dynamically-appearing dialogs, a capability most competitor docs
  only mention in passing ("dynamic content support") without our level of detail.
- **Noise reduction**: `ImpactThreshold` enum (`CRITICAL/SERIOUS/MODERATE/MINOR`,
  same 4-tier scale as axe-core's own `impact` field) plus rule allowlisting
  (`allowRule`) and URL-fragment allowlisting (`allowUrl`) — functionally
  equivalent to Level Access's "mark as false positive to suppress in future
  scans," just config-file-driven instead of UI-driven.
- **Mobile-native context guard** (`isMobileNativeContext`) — automatically skips
  DOM-based scanning when the driver is in a native mobile context, deferring to
  `NativeAccessibilityChecker` instead.

### 1.3 Reporting (`A11yReporter` + implementations)

- Reporter is a **3-method string interface** (`info/warn/fail`) — deliberately
  minimal so any host project can bridge to its own reporting tool in ~3 lines.
  Implementations shipped: `Slf4jReporter`, `NoOpReporter`, `AllureA11yReporter`,
  `CompositeReporter` (fan-out to multiple reporters).
- `AccessibilityExcelReporter` writes a structured `.xlsx` (summary sheet +
  per-violation detail sheet) with an in-memory `ScanRecord`/`ViolationRecord`
  pair. `ViolationRecord` fields today: `ruleId`, `impact`, `description`, `help`,
  `helpUrl`, `affectedElementCount`, `firstAffectedElement` (single CSS-selector
  string). **No WCAG success-criterion field, no DOM/HTML snippet, no page URL on
  the record itself, no timestamp per violation, no screenshot reference, no
  engine version, no test name.**
- `AccessibilityViolationException` carries only a formatted message string — no
  structured payload for a caller to inspect programmatically.
- **No dashboard, no trend/history store, no baseline/regression comparison, no
  VPAT/compliance-document generation** anywhere in the codebase today.

### 1.4 Automation integration

- TestNG auto-registration via `META-INF/services` (`A11yTestNGListener`) — zero
  config to turn on suite-wide scanning, matching (and arguably exceeding, for
  TestNG users) the "drop-in" ergonomics vendors like axe DevTools' browser
  extension offer for manual use.
- Config resolution order: JVM system property → env var → working-dir
  `accessibility.properties` → classpath `accessibility.properties`
  (`A11yConfig`) — framework-agnostic by design, no host config-system coupling.
- Java-native throughout (fits our Selenium/Appium/TestNG stack directly); no
  REST API layer, no CI-specific plugin (Jenkins/GitHub Actions/Azure DevOps
  extension) beyond "it's a JUnit-style exception, any CI that runs `mvn test`
  sees the failure."

---

## 2. Vendor Research Findings

*(Condensed from two full research passes; every claim below is footnoted 🟢/🔵/⚠️
in the underlying agent transcripts. Only load-bearing facts for the
recommendation are reproduced here.)*

### 2.1 Deque Systems (axe-core / axe DevTools / Linter / Auditor)

- axe-core: MPL-2.0, OSS, **the same engine our SDK already uses**. Deque's own
  current stated figure: axe-core "can find on average 57% of WCAG issues
  automatically" 🟢 (axe-core README/npm). Design principle: "returns zero false
  positives (bugs notwithstanding)" 🟢 — a vendor claim, not an independent audit.
- Paid tiers (Pro/Bundle) add: Intelligent Guided Tests (semi-automated keyboard/
  focus checks), Jira export, shareable screenshot permalinks, **automated issue
  deduplication**, "CI/CD support" (unnamed specifics). Enterprise
  dashboard/trend/baseline features exist per marketing copy but were **not
  independently verifiable** from public pages (JS-rendered, 404s on deeper URLs) ⚠️.
- Java integration: `com.deque.html.axe-core` Maven group (what we already use).
  No confirmed native Appium/mobile axe product ⚠️.
- **Takeaway: we already use their free engine; their paid tiers mostly sell
  dashboard/dedup/IGT/Jira convenience, not a materially different scanning
  engine.**

### 2.2 Level Access (AMP)

- Proprietary engine (legacy "InFocus", Java-based) + ML/NLP extensions (e.g.
  alt-text quality) — **no evidence of axe-core underneath** 🟢/⚠️(absence-of-
  evidence).
- **"Auto-Match Findings"**: dedups recurring findings across scans, auto-closes
  resolved ones, supports marking false positives — a **real regression/history
  mechanism**, more developed than anything in our SDK today 🟢.
- Severity: Critical/High/Low + up to 2 custom severities, remappable to a
  customer's own bug-tracker scale 🟢 — more flexible than our fixed 4-tier scale.
- **"AMP Compliance Score"** explicitly documented as combining automated scans +
  **paid manual audits (1-2x/yr) + user testing with people with disabilities** —
  i.e., even Level Access does not claim a pure-automation score is sufficient 🟢.
- Appium+Maven-based **"Access Continuum"** mobile toolkit (JSON output with
  native-element attributes) — closest researched analog to our
  `NativeAccessibilityChecker`, but proprietary/paid 🟢.
- Org→Workspace→Portfolio→Asset governance hierarchy, FedRAMP-authorized (2021) 🟢
  — enterprise governance features well beyond our scope/need as an internal SDK.
- Quote-based pricing only; no self-service tier found ⚠️.

### 2.3 Siteimprove

- WCAG 2.1/2.2 A/AA/AAA; single 0–100 **site-wide accessibility score, tracked
  over time**, resolved-issues list, goal-setting 🟢 — the clearest "historical
  trend dashboard" example found in this research, but **scoring formula
  unpublished** ⚠️, and **no Selenium/Playwright/Appium/Java SDK exists at all** —
  integration is REST-API-only (well-documented, rate-limited, HAL/OpenAPI) 🟢.
  Automatically scans PDFs for tags/reading-order/alt-text — a capability we do
  not have and most others don't mention either 🟢.
- **Takeaway: best trend-dashboard UX researched, but zero test-framework
  integration — would require us to build our own Selenium/Java wrapper around
  their REST API from scratch, which is significant net-new work for a
  proprietary, paid, closed engine.**

### 2.4 Microsoft Accessibility Insights (Web + Android)

- **Confirmed via `package.json`**: built directly on axe-core 4.11.3, MIT
  licensed, free 🟢 — validates that "axe-core underneath, custom everything
  else" (our own architecture) is an industry-standard pattern, not a
  compromise.
- Distributed as a browser extension (FastPass ~50 automated checks + guided
  manual "Assessment" for the rest, explicitly WCAG 2.1 AA scoped) — **not a
  CI/headless tool**; not directly reusable by us beyond confirming axe-core's
  credibility 🟢.
- Android variant **officially discontinued June 2023**, source kept MIT — paired
  axe ("Raw Axe... Results") with Google's ATFA for native-Android checks, a
  precedent for combining a DOM engine with a native-platform engine, same shape
  as our axe-core (web) + `NativeAccessibilityChecker` (native) split 🟢.

### 2.5 WebAIM WAVE

- Proprietary engine, explicitly **not** axe-core 🟢. WAVE explicitly frames
  itself as a **human-assisted evaluation aid**, not a pass/fail automated
  verdict: "WAVE cannot tell you if your web content is accessible... only a
  human can" 🟢 — validates our own design choice not to treat "0 violations" as
  a compliance guarantee.
- **AIM report**: automated score + human review of 4 sampled pages against 10
  criteria — another "automation alone is insufficient" data point from a
  well-respected vendor 🟢. Stand-alone API is CI-capable but no dedicated
  Selenium/Java SDK; defers enterprise dashboarding to a partner product (Pope
  Tech) rather than building its own 🟢.

### 2.6 IBM Equal Access Toolkit — most directly relevant OSS prior art

- Apache-2.0, **own independent rule engine** (not axe-core), rule sets
  `IBM_Accessibility` / `WCAG_2_1`, selectable via config 🟢.
- **Result taxonomy**: `violation | potentialviolation | recommendation |
  potentialrecommendation | manual | pass` — a materially richer confidence
  gradient than our current binary violation/no-violation-with-impact-string
  model, and a clean way to represent axe-core's own `"incomplete"` bucket
  without discarding it 🟢.
- **Baseline-comparison via `aChecker.assertCompliance(report)`** — compares a
  scan against a **checked-into-source-control baseline file** to fail/pass and
  filter known/expected issues. This is a **file-based, source-control-native
  regression mechanism** — no SaaS backend required, directly portable to our
  Java SDK and Maven/Git workflow 🟢. **This is the single most directly
  reusable idea found in the entire research pass.**
- Native JSON/CSV/**XLSX**/HTML output (XLSX "no additional installation
  required") — we already do this via `AccessibilityExcelReporter`, confirming
  our reporting format choice is aligned with credible OSS prior art 🟢.
- Integrates with Selenium/Puppeteer/Playwright test frameworks 🟢, though no
  native Java package — still Node-based under the hood.

### 2.7 Pa11y (+ Pa11y CI, Pa11y Dashboard)

- LGPL-3.0, OSS. **Dual-runner**: HTML_CodeSniffer (independent, WCAG2A/AA/AAA)
  *or* axe-core, selectable/combinable per run — the only tool researched that
  natively runs two independent engines side by side, directly addressing single-
  engine false-negative risk 🟢.
- Result objects carry a WCAG-technique-coded `code`
  (e.g. `WCAG2AA.Principle1.Guideline1_1.1_1_1.H30.2`), `message`, `context`
  (HTML snippet), CSS `selector`, `type` — **this shape is close to the
  normalized finding model we should adopt** (§5) 🟢.
- `--screen-capture <path>` — built-in per-scan screenshot, something our
  Excel/session reporting does not currently attach automatically 🟢.
- **Pa11y Dashboard**: self-hosted, MongoDB-backed, GPL-3.0 — a real "we built
  our own lightweight dashboard instead of paying for Siteimprove" precedent,
  validating that a small self-hosted history store (not a SaaS purchase) is a
  proven, low-cost path 🟢.

### 2.8 TPGi ARC Toolkit / ARC Platform

- Proprietary "ARC rule set" (own engine, not axe-core, not IBM's) 🟢. Covers
  WCAG 2.0/2.1/2.2, EN 301 549, Section 508. Enterprise ARC Platform markets
  "regression detection" and "executive-ready reporting" 🟢, but most platform-
  tier detail was inaccessible to research tooling (JS-heavy site) ⚠️. No OSS
  engine, no confirmed test-framework SDK — least reusable of the seven tools
  researched for our purposes.

### 2.9 AudioEye — low-confidence, flagged explicitly

- Site blocked automated research tooling (Vercel bot-protection, 429 on every
  attempt across direct fetch, proxy, and multiple search engines) 🔵. Historical
  archived content positions AudioEye as a **hybrid automated + paid human-expert
  remediation + legal/certification service**, not a pure automated-testing
  product, with no evidence of a public SDK/CLI. **Overall confidence: LOW** —
  treat directionally only, not as a verified comparison point.

---

## 3. Capability Matrix

`✅` = strong/native · `➖` = partial/manual-assisted · `❌` = absent · `⚠️` = unverified for that vendor

| Capability | **Our SDK** | axe-core/Deque | Level Access AMP | Siteimprove | IBM Equal Access | Pa11y |
|---|---|---|---|---|---|---|
| WCAG 2.2 A/AA coverage | ✅ (axe-core + custom Layer 3) | ✅ | ✅ | ✅ | ✅ (WCAG_2_1 policy; 2.2 ⚠️) | ➖ (htmlcs=2.0 tiers; axe runner=2.2) |
| Open-source engine | ✅ (axe-core, MPL-2.0) | ✅ | ❌ proprietary | ❌ proprietary | ✅ (Apache-2.0) | ✅ (LGPL-3.0) |
| Severity/impact scale | ✅ 4-tier (matches axe) | ✅ 4-tier | ✅ 3-tier + custom | ➖ "criticality" (unpublished) | ✅ 6-state taxonomy | ✅ error/warning/notice |
| Confidence gradient (violation vs. "needs review") | ➖ (axe `incomplete` surfaced but not modeled distinctly) | ✅ `incomplete` bucket | ➖ | ⚠️ | ✅ explicit `potentialviolation`/`manual` | ➖ |
| Live interaction/keyboard checks beyond static DOM | ✅ custom Layer 2 | ➖ (paid IGTs, semi-auto) | ➖ manual-only per own docs | ➖ manual-only per own docs | ➖ | ➖ |
| SPA / dynamic DOM auto-rescan | ✅ (EventFiringDecorator + URL poller + dialog watcher) | ➖ (must be re-invoked manually) | ➖ (extension explicitly **doesn't** support SPA) | ⚠️ | ➖ | ➖ (`--wait` flag only) |
| Scan dedup / noise reduction | ✅ (URL+DOM fingerprint, cooldown, allowlists) | ✅ (paid tier) | ✅ (Auto-Match Findings) | ⚠️ | ➖ (baseline file, not live dedup) | ❌ |
| Mobile-native (non-DOM) support | ✅ `NativeAccessibilityChecker` (OSS, custom) | ⚠️ unverified product | ✅ Access Continuum (proprietary, paid) | ❌ | ❌ | ❌ |
| **Normalized structured finding model** | ❌ (string-based reporter + ad hoc Excel record) | ✅ (`target`/`html`/`tags`/`impact` JSON) | ✅ (structured report + JSON for mobile) | ⚠️ | ✅ | ✅ (`code`/`context`/`selector`/`type`) |
| Screenshot/evidence auto-attach | ❌ | ✅ (extension permalinks) | ✅ (Thumbnail column) | ⚠️ | ➖ | ✅ (`--screen-capture`) |
| **Baseline / regression comparison** | ❌ | ⚠️ (marketing only) | ✅ (Auto-Match) | ⚠️ | ✅ (`assertCompliance`, file-based) | ➖ (via Dashboard history only) |
| **Historical trend dashboard** | ❌ | ⚠️ (marketing only) | ✅ (portfolio dashboard) | ✅ (0-100 score, strongest researched) | ❌ (labeled files only) | ✅ (Pa11y Dashboard, self-hosted) |
| VPAT/compliance-doc generation | ❌ | ➖ (services offering, not confirmed product feature) | ➖ (audit service, not self-service) | ➖ (Statement Generator ≠ VPAT) | ➖ | ❌ |
| Selenium/Java-native integration | ✅ (native, this SDK) | ✅ (Maven artifact) | ✅ (legacy Selenium API) | ❌ (REST API only) | ✅ (via Node checker) | ❌ (own Puppeteer only) |
| Zero-config CI auto-scan (TestNG) | ✅ (`META-INF/services`) | ❌ (manual `AxeBuilder` call) | ❌ | ❌ | ❌ | ➖ (Pa11y CI = separate crawler run) |
| Cost | Free (internal SDK) | Free (core) / $ paid tiers | $$$ quote-only | $$$ quote-only | Free | Free |

**Reading the matrix:** our SDK is at or above parity with every free/OSS
competitor on live-interaction testing, SPA handling, and mobile-native support —
areas that took real engineering effort and are not simply "install axe-core."
The only column where we are behind **every** other option is **structured
findings / evidence / history / baseline**, which is a reporting-layer gap, not
an engine gap.

---

## 4. Gap Analysis

1. **No normalized finding model (highest-impact gap).** `A11yReporter` is a
   3-method string interface; `AccessibilityExcelReporter`'s `ViolationRecord` is
   an internal, Excel-writer-only class not exposed to callers. Every other
   researched tool (including all 4 OSS ones) exposes a structured
   rule/impact/selector/HTML/WCAG-code object. This blocks everything below it.
2. **No baseline/regression comparison.** IBM's `assertCompliance(report)` proves
   this doesn't require a SaaS backend — a checked-in JSON baseline file per
   page/test is suf ficient and fits our existing file-based Excel/JSON output
   model.
3. **No historical trend/dashboard.** Every commercial vendor's flagship
   enterprise feature. Pa11y Dashboard proves a *lightweight self-hosted* version
   is realistic; Siteimprove proves the *value* of a single trending score even
   without deep dashboarding.
4. **No screenshot/evidence auto-capture tied to a specific finding.** Our SDK
   captures screenshots elsewhere (`TestBase.getScreenShot`) but does not
   currently correlate one to a specific accessibility violation the way Pa11y's
   `--screen-capture` or Level Access's "Thumbnail" column do.
5. **No confidence gradient beyond axe's raw `impact`.** IBM's 6-state taxonomy
   (`violation/potentialviolation/recommendation/potentialrecommendation/manual/
   pass`) is a better model than ours for surfacing axe-core's own `"incomplete"`
   results distinctly instead of dropping/merging them.
6. **No VPAT/compliance-document generation.** Lowest priority — even the
   best-funded vendors (Deque, Level Access) deliver this mostly as a **paid
   human-audit service**, not a pure automation feature, so there is no
   free/OSS pattern to copy, and it is out of scope for an internal QA SDK.
7. **Accuracy/false-positive limitation is shared, not SDK-specific.** axe-core's
   "57% average automatic detection" and "zero false positives (bugs
   notwithstanding)" are vendor claims we inherit as-is; no researched
   alternative engine has an independently audited accuracy advantage over
   axe-core. This is not something to "fix" by switching engines.

---

## 5. Proposed Architecture 🧭

```
Automation Test
      ↓
AccessibilityService              (new: thin facade over session mgmt + engines)
      ↓
AccessibilityEngine (interface)    ← pluggable: AxeCoreEngine (default, wraps
      ↓                              existing AccessibilityChecker), NativeMobileEngine
                                     (wraps existing NativeAccessibilityChecker),
                                     future: SecondEngineAdapter (e.g. HTML_CodeSniffer,
                                     Pa11y-style dual-runner) — no public API change
                                     needed to add one.
Normalized AccessibilityFinding
      ↓
Evidence Collector                (new: correlates finding → screenshot via
      ↓                              existing TestBase.getScreenShot, HTML/DOM snippet
                                     already available from axe-core's `html` field)
Report Generator                  (existing A11yReporter/Excel/Allure reporters,
      ↓                              now fed from the normalized model instead of
                                     ad hoc strings — mostly additive, not a rewrite)
Historical Results / Dashboard    (new: file-based baseline compare, modeled on
                                     IBM's assertCompliance; optional lightweight
                                     self-hosted trend store modeled on Pa11y
                                     Dashboard — NOT a SaaS/Siteimprove rebuild)
```

Key design constraint honored: **`AccessibilityEngine` is an interface**, so the
public SDK API is never coupled to axe-core specifically — exactly as requested.
`AccessibilityChecker` and `NativeAccessibilityChecker` become the first two
concrete implementations; nothing about their internals needs to change to fit
behind the interface, since both already return a discoverable violation list.

**Backward compatibility:** `A11yReporter`, `A11yTestNGListener`,
`A11ySessionManager`'s public static API, and all existing config keys are
**unchanged**. The normalized model is introduced as a new, additive data type
that existing reporters can ignore; `AccessibilityExcelReporter` and
`AllureA11yReporter` are updated internally to consume it instead of building
their own ad hoc records, with identical output format.

---

## 6. Proposed `AccessibilityFinding` Model 🧭

```java
public final class AccessibilityFinding {
    String ruleId;              // e.g. axe-core's rule id ("color-contrast")
    String wcagCriterion;       // e.g. "1.4.3" — derived from axe-core `tags`
    String wcagLevel;           // "A" | "AA" | "AAA"
    String severity;            // "minor"|"moderate"|"serious"|"critical" (axe impact scale, kept for compatibility)
    String confidence;          // NEW: "violation"|"potential"|"manual_review" — IBM-style gradient over axe's raw impact/incomplete
    String description;
    String affectedElementSelector; // axe-core `target` (CSS selector), or native-mobile element id
    String affectedElementHtml;     // axe-core `html` snippet (already available today, not yet surfaced)
    String remediationGuidance;      // axe-core `helpUrl` / custom Layer help text
    String pageUrl;
    String testName;                 // from TestNG ITestResult / TestBase.currentTestCaseName
    Instant timestamp;
    String screenshotPath;           // NEW: correlated via Evidence Collector
    String engine;                   // "axe-core" | "Interaction" | "WCAG2.2" | "Structural" | "Motion" | "NativeMobile"
    String engineVersion;            // e.g. "4.10.1"
}
```

This is a superset of what axe-core, IBM's checker, and Pa11y each already expose
individually — no single vendor's shape had to be invented from scratch, it is a
merge of fields already proven useful across three OSS/free tools.

---

## 7. Final Recommendation

### A. Vendor comparison
See §2 and §3. axe-core (already our engine) is credible, free, and used by
Microsoft's own tool. No researched alternative engine has a proven accuracy
advantage justifying a switch.

### B. Accuracy/reliability comparison
No independently audited accuracy comparison exists across any of the 7 vendors
researched — every accuracy claim (axe's 57%/zero-false-positives, Level Access's
ML claims, Siteimprove's scoring) is vendor-self-reported. This is an
industry-wide gap, not specific to us.

### C. Comparison with our existing implementation
See §3 capability matrix. We match or exceed free/OSS competitors on
engine sophistication (live interaction checks, SPA handling, mobile-native);
we lag on structured output, history, and baselines.

### D. Gap analysis
See §4.

### E. Recommended improvements
See §5–§6.

### F. Proposed architecture
See §5.

### G. Reporting model
See §6.

### H. Prioritized roadmap

**REQUIRED**
- Introduce `AccessibilityFinding` normalized model (§6); have
  `AccessibilityChecker`/`NativeAccessibilityChecker` populate it internally
  alongside their existing violation lists (purely additive).
- Update `AccessibilityExcelReporter` and `AllureA11yReporter` to source their
  existing output from the normalized model instead of duplicating field
  extraction — no visible output change, removes duplicated logic.
- Extract an `AccessibilityEngine` interface behind `AccessibilityChecker` /
  `NativeAccessibilityChecker` so the engine is swappable without touching
  `A11ySessionManager`/`A11yTestNGListener` call sites.

**RECOMMENDED**
- File-based baseline/regression comparison modeled directly on IBM Equal
  Access's `assertCompliance(report)` — a JSON baseline checked into the
  consumer's repo per page/test, diffed on each run, config-toggle to fail on
  new violations only (not all known ones). No new infrastructure required.
- Evidence Collector: correlate each `AccessibilityFinding` to a screenshot via
  the existing `TestBase.getScreenShot` mechanism, store the path on the finding.
- Add an IBM-style confidence gradient (`violation`/`potential`/`manual_review`)
  so axe-core's `incomplete` results are surfaced distinctly instead of being
  merged into pass/fail.

**FUTURE**
- Lightweight self-hosted trend store/dashboard, modeled on Pa11y Dashboard
  (self-hosted, not SaaS) — only if/when multiple projects consume this SDK and
  cross-project trend visibility becomes a real ask, not before.
- Optional second engine adapter (e.g. HTML_CodeSniffer) behind the new
  `AccessibilityEngine` interface for dual-engine cross-validation, Pa11y-style
  — speculative, only pursue if false-negative rate from axe-core alone becomes
  an observed problem in practice.
- VPAT/compliance-document generation — explicitly deprioritized; even
  best-funded vendors deliver this as a paid human-audit service, not a pure
  automation feature, so there's no free technical pattern to build toward, and
  it is arguably out of scope for a test-automation SDK vs. a compliance tool.

---

## 8. Explicit Non-Recommendation

Per the constraint given for this research: **we are not recommending replacing
our SDK's accessibility implementation with any commercial vendor.** All
researched commercial platforms (Level Access, Siteimprove, TPGi ARC, AudioEye)
either use closed/proprietary engines with no independently verified accuracy
advantage over axe-core, or price at enterprise quote-only tiers with no
transparent cost, or (Siteimprove) have zero Selenium/Java test-framework
integration at all. The technically strongest ideas found — IBM's baseline-file
regression model and Pa11y's dual-runner/self-hosted-dashboard pattern — are
themselves open-source and directly portable into our own SDK without adopting
any vendor dependency beyond axe-core, which we already use.
