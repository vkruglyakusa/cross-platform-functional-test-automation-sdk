---
name: analyze-locator-candidates
version: 1.0
capability: element-discovery
inputSchema: discovery-result-v1
outputSchema: locator-recommendation-v1
requiredTools:
  - element-discovery
---

# Analyze Locator Candidates

You are assisting a test automation engineer who is building a Page Object.
You will be given:

1. A **target description** -- a plain-language description of one UI element
   the engineer wants to interact with (e.g. "the Submit button on the login
   form", "the Borough dropdown").
2. A **`DiscoveryResult`** JSON document (see `discovery-result-v1.schema.json`)
   -- the normalized output of a single crawl pass over the current page/screen,
   produced by `com.test.automation.sdk.discovery.ElementDiscoveryService`
   (via `WebElementDiscoveryAdapter` for web or `MobileElementDiscoveryAdapter`
   for mobile). Each element in `elements[]` carries a `platform`, `tagOrType`,
   `text`, and a `candidates[]` list of locator strategies already tried and
   scored against the live page/screen.

## Your task

1. Find the element in `elements[]` that best matches the target description,
   using `tagOrType` and `text` as your primary signals.
2. From that element's `candidates[]`, select the best candidate using this
   priority order:
   - Prefer a candidate with `marker == "UNIQUE"`.
   - If more than one candidate is `UNIQUE`, prefer (in order): an
     accessibility/semantic strategy (`ACCESSIBILITY_ID`, `BY FORMCONTROLNAME`,
     `BY ID`, `RESOURCE_ID`) over a text-based strategy (`TEXT`,
     `BY ARIA-LABEL`), and prefer either over a positional/`STRUCTURAL`
     strategy.
   - **Never** recommend a candidate marked `DYNAMIC` or `STRUCTURAL` unless
     no `UNIQUE` candidate exists at all -- in that case, recommend `null` and
     explain why in `warnings` instead of picking an unsafe fallback.
3. Do not invent a locator that isn't present in `candidates[]`. If the
   correct element isn't in `elements[]` at all, or none of its candidates are
   safe, return `recommendedCandidate: null` with an explanatory warning
   rather than guessing.

## Output

Respond with a single JSON object matching `locator-recommendation-v1.schema.json`
-- no prose outside the JSON object.

## Example

Input target description: `"the Submit button"`

Input `DiscoveryResult` element (abridged):

```json
{
  "platform": "web",
  "tagOrType": "button",
  "text": "Submit",
  "resolved": true,
  "candidates": [
    { "strategyLabel": "BY ID", "value": "//button[@id='submit-btn']", "marker": "UNIQUE", "matchCount": 1 },
    { "strategyLabel": "STRUCTURAL", "value": "//div[3]/button[1]", "marker": "STRUCTURAL", "matchCount": -1 }
  ]
}
```

Expected output:

```json
{
  "targetDescription": "the Submit button",
  "recommendedCandidate": { "strategyLabel": "BY ID", "value": "//button[@id='submit-btn']", "marker": "UNIQUE", "matchCount": 1 },
  "confidence": 0.95,
  "warnings": []
}
```
