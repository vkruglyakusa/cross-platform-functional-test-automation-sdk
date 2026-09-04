---
applyTo: "**"
---

# Skill: Locator Strategy & Uniqueness Rules

## Purpose
This skill defines the mandatory locator selection policy for all Page Objects
in this project. It is enforced by `ElementCrawler` (uniqueness validation) and
reviewed by the developer before finalizing any `@FindBy` annotation.

---

## Uniqueness is Mandatory
A locator is only acceptable if `driver.findElements(By.xpath("...")).size() == 1`.  
The crawler validates this automatically and marks each strategy:

| Marker | Meaning | Action |
|---|---|---|
| `UNIQUE [x]` | Exactly 1 element found | ? Safe to use |
| `NOT UNIQUE (N found)` | Multiple elements match | ? Do not use |
| `DYNAMIC` | Pattern is auto-generated | ? Do not use |
| `STALE` | 0 elements found | ? Element may be conditional |
| `STRUCTURAL` | Position-based fallback | ? Do not use unless absolutely last resort |

---

## Locator Priority Ladder
Always use the **first applicable unique strategy**:

```
1.  @id                   -- most stable; use only if not auto-generated
2.  @data-testid          -- second best; intentional test attribute
3.  @formcontrolname      -- Angular reactive forms; stable
4.  @name + @type         -- reliable for standard HTML forms
5.  @name                 -- reliable when type is not needed
6.  @aria-label (exact)   -- accessibility attribute; usually stable
7.  @placeholder (exact)  -- good for inputs; use exact match
8.  normalize-space(.)    -- for buttons/links with unique visible text
9.  @routerlink           -- Angular navigation; stable
10. @href (contains)      -- for static links; use last path segment
-----------------------------------------------------
?  @class                -- NEVER as primary; use only with contains() + another attribute
?  position [N]          -- NEVER; breaks on any UI change
?  auto-generated IDs    -- NEVER (see dynamic ID patterns below)
```

---

## Dynamic ID Patterns -- Always Reject
Any locator matching these patterns must be rejected:

| Pattern | Example | Reason |
|---|---|---|
| `mat-input-\d+` | `//input[@id='mat-input-3']` | Angular Material auto-increment |
| `mat-select-\d+` | `//mat-select[@id='mat-select-0']` | Angular Material auto-increment |
| `cdk-\w+-\d+` | `//*[@id='cdk-overlay-2']` | Angular CDK runtime ID |
| `ngb-\w+-\d+` | `//*[@id='ngb-nav-0']` | ng-bootstrap auto-increment |
| Pure numeric | `//input[@id='12345']` | No semantic meaning |
| UUID/hash | `//div[@id='a3f2c1d9']` | Random per session |
| `_nghost-*` | `//*[@_nghost-abc-c0]` | Angular view encapsulation attr |
| `ng-reflect-*` | `//*[@ng-reflect-model]` | Angular debug attribute |

---

## Writing XPath -- Best Practices

### Prefer exact match over contains()
```xpath
?  //input[@placeholder='Enter Pole ID']
[!]?  //input[contains(@placeholder,'Pole')]   -- only if exact is not unique
```

### Use normalize-space() for text with whitespace
```xpath
?  //button[normalize-space(.)='Save Changes']
?  //button[text()='Save Changes']           -- fails if there is whitespace
```

### Combine attributes for specificity
```xpath
?  //input[@name='password' and @type='password']
?  //button[@type='submit' and normalize-space(.)='Login']
```

### Parent context when needed
```xpath
?  //form[@id='loginForm']//input[@name='email']
?  //section[@class='enrollment-panel']//button[normalize-space(.)='Submit']
```

### Cross-layout fallback with union (`|`)
Use `|` only when the same business element is rendered by different layouts and each branch is stable.

```xpath
?  (
      //table[contains(@class,'product-grid-view')]//tr[contains(@class,'product-row') and .//td[normalize-space(.)='{{PRODUCT_NAME}}']]
      |
      //section[contains(@class,'clearance-layout')]//div[contains(@class,'product-row') and .//*[normalize-space(.)='{{PRODUCT_NAME}}']]
      |
      //ul[contains(@class,'legacy-product-list')]//li[contains(@class,'product-row') and .//*[normalize-space(.)='{{PRODUCT_NAME}}']]
    )
```

Rules:
- Keep each branch independently meaningful (no positional `[N]`).
- Prefer exact business anchors (name/sku/label text) inside each branch.
- Accept only if the full union locator is `UNIQUE [x]` in crawler output.

### Angular Material dropdowns
```xpath
# The mat-select trigger:
?  //mat-select[@formcontrolname='borough']

# The option panel (used in action methods, not @FindBy):
?  //mat-option[normalize-space(.)='Manhattan']
```

---

## Crawler Uniqueness Validation
`ElementCrawler.buildAllXPaths()` tests every generated XPath against the live page:

```
For each strategy XPath:
  count = driver.findElements(By.xpath(xp)).size()
  if  count == 1  -> label as  UNIQUE [x]
  if  count >  1  -> label as  NOT UNIQUE (N found)  -> excluded from @FindBy block
  if  count == 0  -> label as  STALE
  if  matches dynamic pattern -> label as  DYNAMIC  -> always excluded
```

Only `UNIQUE [x]` locators appear in the active `@FindBy` annotation.  
All others are still shown in comments for reference but must not be activated.

---

## Quick Reference Card

```java
// ? Correct -- stable, unique, meaningful
@FindBy(xpath = "//input[@id='email']")
@FindBy(xpath = "//input[@formcontrolname='password']")
@FindBy(xpath = "//button[normalize-space(.)='Log In']")
@FindBy(xpath = "//input[@name='poleId' and @type='text']")
@FindBy(xpath = "//mat-select[@formcontrolname='borough']")
@FindBy(xpath = "//a[@routerlink='/dashboard']")

// ? Wrong -- dynamic, fragile, or non-unique
@FindBy(xpath = "//input[@id='mat-input-0']")        // dynamic
@FindBy(xpath = "(//input)[2]")                       // positional
@FindBy(xpath = "//div[3]/form/input[1]")             // structural
@FindBy(css  = "input.form-control")                  // CSS not allowed
@FindBy(xpath = "//*[contains(@class,'ng-valid')]")   // Angular lifecycle class
```

---

## Automating Map Widgets (Google Maps, Leaflet, Mapbox GL, OpenLayers, Bing Maps)

Map widgets are a deliberate **exception** to the uniqueness ladder above -- their
tiles and many markers are painted on a `<canvas>`/WebGL surface with **zero DOM
representation**, so no XPath can ever match them, and any marker DOM that does
exist is frequently regenerated with unstable, internal, auto-generated CSS class
names that must never be used as a locator (same rule as Angular Material/CDK IDs).

`ElementCrawler` detects known map containers by CSS-class fingerprint and tags
them `isMapWidget=true` / `mapProvider=<name>` on `ElementInfo` instead of trying
to generate per-tile/per-pixel `@FindBy` locators for their internal content:

| Provider | Container class fingerprint |
|---|---|
| Google Maps | `.gm-style` |
| Leaflet | `.leaflet-container` |
| Mapbox GL JS | `.mapboxgl-map` |
| MapLibre GL JS | `.maplibregl-map` |
| OpenLayers | `.ol-viewport` |
| Bing Maps | `.MicrosoftMap` |
| Unrecognized | any `<canvas>` not inside a known container above |

### Layered locator strategy for map content

1. **DOM-based locators FIRST** -- `@aria-label`, `@alt`, `@title` on any real DOM
   node inside the map (search boxes, accessible markers, zoom controls). These
   survive library upgrades far better than internal CSS classes.
   - Google Maps tip: setting a `google.maps.Marker`'s `title` property causes a
     real `aria-label` to render in the DOM -- the most reliable hook for legacy
     markers. `AdvancedMarkerElement` allows fully custom HTML/`aria-label`.
2. **Pixel/JS fallback SECOND** -- only when a marker has zero DOM presence at
   all. Use `MapWidgetHelper.clickAtPixelOffset(driver, container, x, y)` and
   assert on the resulting side effect (info window, URL change), never on the
   marker itself.

### Explicit limitations (not solvable in general)

- Canvas/WebGL-drawn markers with no DOM representation cannot satisfy the
  uniqueness rule -- there is nothing for `driver.findElements()` to match.
- There is no universal "map fully loaded" DOM event across libraries;
  `MapWidgetHelper.waitForMapReady()` uses heuristic signals (sized canvas / tile
  images + a short settle buffer), not a hard guarantee.
- `clickAtPixelOffset()` cannot verify what it clicked -- pair it with an
  assertion on the expected side effect, not on the pixel target.

### Quick Reference

```java
// [MAP WIDGET] Detected provider: Google Maps
// Do NOT attempt per-tile/per-marker locators here -- use MapWidgetHelper.
@FindBy(xpath = "//div[contains(@class,'gm-style')]")
public WebElement mapWidget;
```

```java
waitForMapReady(mapWidget);
searchMapAddress(mapWidget, "350 5th Ave, New York, NY");
WebElement pin = findMapMarkerByLabel(mapWidget, "My Store");
if (pin != null) {
    pin.click();
} else {
    com.test.automation.sdk.utility.MapWidgetHelper.clickAtPixelOffset(driver, mapWidget, 0, 0);
}
```

---

## Automating Shadow DOM Elements (Web Components, Lit/Stencil, Salesforce Lightning-LWC)

XPath **cannot cross a shadow boundary** -- this is a W3C DOM spec limitation, not
a tooling gap. Any framework that uses real DOM encapsulation (Web Components,
Lit, Stencil, Salesforce Lightning/LWC) renders content that plain
`driver.findElement(By.xpath(...))` simply cannot see. (Angular's default/emulated
view encapsulation does **not** use real shadow roots and needs no special
handling -- this only applies to actual `element.shadowRoot`.)

`ElementCrawler` discovers elements inside **open** shadow roots (recursing into
nested shadow roots too) and tags them on `ElementInfo`:

| Field | Meaning |
|---|---|
| `inShadowDom` | `true` when this element lives inside an open shadow root |
| `shadowHostXpath` | Light-DOM XPath to the shadow-root HOST element |
| `shadowRelativeCss` | CSS selector for the element, resolved via `host.getShadowRoot()` (shadow roots only support CSS, never XPath) |

`PageObjectGenerator` emits a **method**, not a `@FindBy` field, for these
elements (an annotation can't express a two-step lookup):

```java
public WebElement submitButton() {
    WebElement host = driver.findElement(By.xpath("//my-form-component"));
    return host.getShadowRoot().findElement(By.cssSelector("button#submit"));
}
```

Use it directly, or via the `TestBase` wrapper:

```java
WebElement submit = findInShadowDom(By.xpath("//my-form-component"), "button#submit");
submit.click();
```

**Limitation (not solvable):** *closed* shadow roots (`element.shadowRoot ===
null` from outside) cannot be discovered or traversed by any script or WebDriver
command -- this is an intentional browser security boundary. If a component uses
`attachShadow({mode: 'closed'})`, automation cannot reach inside it at all; flag
this to the development team rather than attempting a workaround.

See `SDK-USER-GUIDE.md` section 7.2 and `TESTBASE-API.md` section 32 for full details.

---

