# AI Assets

Versioned, vendor-neutral assets consumed by AI agents/LLM tooling that operate
on this SDK -- see `docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md`
sections 26-28 ("AI Agent Enablement") and Structure Cleanup Phase 10.

**This is not an AI agent runtime.** No Java-side `AgentToolRegistry`,
`AgentContextBuilder`, or orchestration code lives here or is implied by these
files -- per the roadmap's Priority & Sequencing Adjustments item 6, that
infrastructure remains conditional until a concrete agent consumer needs it.
What *is* required now is that prompts and skills exist as reviewable,
versioned files instead of being hardcoded inside Java classes or invented ad
hoc by whichever agent happens to be calling the SDK.

Do not confuse this directory with `src/main/resources/sdk-prompts/` --
that is a separate, already-shipping feature (human-facing GitHub Copilot CLI
`#slash-command` prompt templates, extracted into consumer repos by
`utility.InstructionExtractor`). The two serve different audiences and must
not be merged: `sdk-prompts/` is for a human developer using Copilot Chat;
`ai/` is for an LLM agent driving the SDK's tool contracts (e.g.
`discovery.ElementDiscoveryService`) programmatically.

## Layout

```text
ai/
|-- prompts/
|   `-- <capability>/<prompt-name>.md      (one prompt per file, YAML front matter)
|-- skills/
|   `-- <capability>/<skill-name>.skill.yaml
`-- schemas/
    `-- <schema-name>-v<N>.schema.json     (JSON Schema, draft-07)
```

## Prompt files (`prompts/<capability>/*.md`)

Each prompt is a single Markdown file with YAML front matter metadata,
followed by the prompt body:

```yaml
---
name: analyze-locator-candidates
version: 1.0
capability: element-discovery
inputSchema: discovery-result-v1
outputSchema: locator-recommendation-v1
requiredTools:
  - element-discovery
---
```

- `name` / `version` -- identity for the prompt; bump `version` on any change
  to its inputs, outputs, or expected behavior (do not silently edit a
  released prompt in place).
- `capability` -- the SDK capability area this prompt supports (matches its
  parent directory and the associated skill's `name`).
- `inputSchema` / `outputSchema` -- schema file names (without extension)
  under `ai/schemas/` that define the exact shape the agent must send/expect.
- `requiredTools` -- SDK tool contracts (see `ai/skills/`) the agent must have
  available before invoking this prompt.

## Skill descriptors (`skills/<capability>/*.skill.yaml`)

A skill is broader than a prompt: it describes the end-to-end workflow (which
SDK tool(s) to call, which prompt(s) to invoke, and how to validate the
result) for one capability. See `element-discovery/element-discovery.skill.yaml`
for a worked example tied to `discovery.ElementDiscoveryService`
(Structure Cleanup Phase 6).

## Schemas (`schemas/*.schema.json`)

Plain JSON Schema (draft-07) files. Name format is `<name>-v<N>.schema.json`;
introduce a new version file (`-v2`, `-v3`, ...) rather than editing a
released schema in place, so prompts/skills that pin an older `inputSchema`/
`outputSchema` version keep working.

## Adding a new capability

1. Add `prompts/<capability>/<prompt-name>.md` with front matter as above.
2. Add `skills/<capability>/<capability>.skill.yaml` describing the workflow.
3. Add any new `schemas/*.schema.json` the prompt/skill references.
4. Do not add Java orchestration code (tool registry, context builder, output
   validator) for it unless a concrete agent consumer requires it -- keep
   this directory limited to the versioned asset files themselves.
