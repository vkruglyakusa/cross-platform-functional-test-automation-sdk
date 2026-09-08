---
applyTo: "**"
---

# Skill: Mobile Locator Strategy & Uniqueness Rules

## Purpose
This skill defines the mandatory locator selection policy for all mobile Page
Objects in this project (Android/iOS, native + hybrid, Appium-based). It is the
mobile analogue of `functional-test-automation-sdk`'s
`locator-strategy.instructions.md`, adapted for Appium's element model and the
remote-call-economics constraint described in
`docs/proposals/mobile-automation-strategy.md` section 8a. It is enforced by
`MobileElementCrawler` (uniqueness validation) and reviewed by the developer before
finalizing any `@AndroidFindBy`/`@iOSXCUITFindBy` annotation.

---

## Uniqueness Is Mandatory
A locator is only acceptable if it resolves to exactly one element on the current
screen. `MobileElementCrawler` validates this **without live per-candidate remote
calls** — it fetches `getPageSource()` once per screen and counts attribute-value
frequency in memory (see "Remote-call economics" below), marking each candidate:

| Marker | Meaning | Action |
|---|---|---|
| `UNIQUE` | Exactly 1 match in the current screen's page source | ✅ Safe to use |
| `NOT_UNIQUE` | Multiple elements share this attribute value | ❌ Do not use |
| `DYNAMIC` | Matches a known auto-generated/recycled-id pattern | ❌ Do not use, even if unique in this snapshot |
| `STRUCTURAL` | Tag-only fallback, no identity attributes at all | ❌ Never use unless absolutely last resort, and never in a committed page object |

---

## Locator Priority Ladder

### Android
Always use the **first applicable unique strategy**:

```
1.  @resource-id                 -- most stable; reject if it matches a recycled-list pattern (see below)
2.  @content-desc                -- accessibility label; usually stable, developer-controlled
3.  @text (exact)                -- fine for static labels/buttons; breaks on localization or dynamic content
4.  -android uiautomator          -- UiSelector expressions (e.g. resourceIdMatches, className+instance) -- use only when 1-3 fail
5.  xpath (compound predicate)    -- combine resource-id + content-desc + text into one predicate when no single attribute is unique alone
-----------------------------------------------------
❌ xpath by index/position        -- NEVER (`(//android.widget.Button)[2]`)
❌ className alone                -- NEVER as sole locator (almost never unique)
❌ recycled list-item ids         -- NEVER (see dynamic pattern table)
```

### iOS
Always use the **first applicable unique strategy**:

```
1.  accessibility id (-ios predicate `name ==`)   -- maps to @name; most stable, developer-controlled
2.  -ios predicate string (`label ==`, `value ==`) -- for elements with a stable label/value but no accessibility id
3.  -ios class chain                              -- for structural disambiguation within a known-stable hierarchy
4.  xpath (compound predicate)                    -- combine name + label + type into one predicate when no single attribute is unique alone
-----------------------------------------------------
❌ xpath by index/position                        -- NEVER
❌ deeply-nested structural xpath                 -- NEVER as primary (e.g. chains of `XCUIElementTypeOther` -- see React Native note below)
```

---

## Dynamic ID Patterns — Always Reject
`MobileElementCrawler`'s `DYNAMIC_ID_PATTERNS` rejects any resource-id/content-desc
matching these, regardless of the current match count (untrustworthy across app
builds/runs even when unique in one snapshot):

| Pattern | Example | Reason |
|---|---|---|
| Recycled list-item ids | `.../list_item_3`, `.../row_7`, `.../cell_12` | RecyclerView/UITableView cell reuse — index is not stable across scroll/data changes |
| Purely-numeric suffix | `.../42` | No semantic meaning, likely a runtime-generated index |
| Hash/UUID-like suffix | `.../a3f2c1d9` | Random per session/build |

---

## Compound Locators — When No Single Attribute Is Unique
Mirrors the desktop SDK's "combine attributes for specificity" rule and the
compound-locator pattern used by tools like Appium MCP's `generate-all-locators`.
`MobileElementCrawler#buildCompoundCandidate` builds this automatically during a
crawl: when resource-id, content-desc, and text are each ambiguous alone, it
combines them into one XPath predicate and re-checks uniqueness against a
precomputed compound-frequency map (still zero extra remote calls).

```xpath
✅  //android.widget.TextView[@resource-id='com.app:id/cell' and @text='Row 1']
✅  //XCUIElementTypeCell[@name='cell' and @label='Row 1']
```

Only accept a compound locator if the crawler (or manual verification) confirms it
resolves to exactly one element. Never hand-write a compound locator without
verifying uniqueness first — see "Remote-call economics" for how to verify without
burning excessive remote calls.

---

## React Native / Hybrid-Specific Notes

Several pilot apps (e.g. `311_Mobile_Automation`) are built with **React Native**,
identifiable by:
- Generic `android.widget.TextView`/`android.view.View` (Android) or long chains of
  `XCUIElementTypeOther` (iOS) with **no `resource-id`/`name`** — RN does not assign
  native ids unless the developer sets `testID`.
- Auto-generated accessibility labels following the pattern
  `"<label> tab. <index> of <count>. Double tap to activate."` — this exact phrasing
  is RN's default accessibility hint format for tab/switch-like components.
- Occasional natively-implemented screens embedded within the RN app (e.g. a native
  map/search screen) that DO have real `resource-id`s with the app's package name
  (e.g. `gov.nyc.doitt.ThreeOneOne:id/search_input`) — these are safe to locate the
  normal native way; only the RN-rendered screens need the fallback rules below.

**Fallback rules for RN screens with no resource-id/content-desc:**
1. Prefer `@text` (exact) when the text is static and not user-generated/localized.
2. If `@text` is ambiguous, use a compound predicate combining `@text` with the
   nearest stable ancestor/sibling structure (e.g.
   `//android.widget.TextView[@text='Recurring problem']/following-sibling::android.view.View`)
   — acceptable because it anchors on stable text, not raw position.
3. Ask the app team to add a `testID` (Android → `resource-id`, iOS → accessibility
   id) to ambiguous interactive elements — this is the only way to get a truly
   robust locator on a React Native screen with duplicate/dynamic text.
4. Never fall back to pure structural/index-based xpath as a permanent locator —
   treat it as a temporary placeholder pending a `testID` addition, and flag it in
   the generated page object comments (see `MobilePageObjectGenerator`'s STRUCTURAL
   handling).

WebView/hybrid content within any app (RN or native) is delegated to the desktop
SDK's `ElementCrawler` and follows the **desktop** `locator-strategy.instructions.md`
rules instead (DOM attributes, not native accessibility attributes).

---

## Remote-Call Economics — Why This Matters More on Mobile

Unlike a local Selenium `WebDriver`, an Appium session (especially on a cloud grid
like BrowserStack) bills and is latency-bound **per remote call**. Verifying N
locator candidates with N separate `driver.findElements()` calls could mean
120–160+ remote round trips per screen (30–90+ seconds, real cost). Always prefer:

1. One `getPageSource()` call per screen, parsed entirely in memory
   (`MobileElementCrawler.analyzePageSource`).
2. Local frequency-counting for uniqueness (O(n) single pass, not O(n²)).
3. Reserve `verifyCandidateRemotely()` for the rare case a locator cannot be
   statically resolved (e.g. a compound `-android uiautomator` expression relying on
   runtime-only predicates) — this is the only place a "live" verification call is
   acceptable, and it should be used sparingly.

Never write a hand-authored locator strategy (in a page object or a prompt/skill)
that implies verifying each candidate with its own live `findElement` call — that
directly contradicts this SDK's core mobile crawler design constraint.

---

## Quick Reference Card

```java
// ✅ Correct -- stable, unique, meaningful
@AndroidFindBy(xpath = "//android.widget.EditText[@resource-id='gov.nyc.doitt.ThreeOneOne:id/search_input']")
@iOSXCUITFindBy(xpath = "//XCUIElementTypeButton[@name='loginButton']")
@AndroidFindBy(xpath = "//android.widget.Button[@content-desc='Create Service Request']")
@iOSXCUITFindBy(xpath = "//XCUIElementTypeStaticText[@name='Location type']")

// ✅ Correct -- compound fallback, confirmed UNIQUE by the crawler
@AndroidFindBy(xpath = "//android.widget.TextView[@resource-id='com.app:id/cell' and @text='Row 1']")

// ❌ Wrong -- dynamic, fragile, or non-unique
@AndroidFindBy(xpath = "//android.widget.TextView[@resource-id='com.app:id/list_item_3']")  // recycled list id
@AndroidFindBy(xpath = "(//android.widget.Button)[2]")                                      // positional
@iOSXCUITFindBy(xpath = "//XCUIElementTypeOther[3]/XCUIElementTypeOther[1]/XCUIElementTypeButton") // deep structural chain
```
