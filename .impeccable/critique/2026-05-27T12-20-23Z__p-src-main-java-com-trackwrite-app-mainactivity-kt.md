---
target: app/src/main/java/com/trackwrite/app/MainActivity.kt
total_score: 27
p0_count: 0
p1_count: 3
timestamp: 2026-05-27T12-20-23Z
slug: p-src-main-java-com-trackwrite-app-mainactivity-kt
---
# Impeccable Critique: TrackWrite Main Product UI

## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Recording state and write results are visible, but long-running import/export/write operations have no loading or in-progress state. |
| 2 | Match System / Real World | 3 | Record and Match match the photographer workflow, but terms like camera offset, fallback, and matching track still need clearer user framing. |
| 3 | User Control and Freedom | 3 | Cancel paths exist for dialogs and sheets, but there is no obvious clear selected matching track or undo for manual location changes. |
| 4 | Consistency and Standards | 3 | Material 3 patterns are mostly consistent, but cards and action rows are used as a repeated default across nearly every section. |
| 5 | Error Prevention | 3 | Original writes are confirmed and copy export defaults are safer, but write/import/export actions do not show preflight detail before starting. |
| 6 | Recognition Rather Than Recall | 3 | Main actions are labeled and visible, but settings values are summarized tersely and some advanced match behavior is hard to understand in context. |
| 7 | Flexibility and Efficiency of Use | 2 | Batch writing exists, but there are few accelerators for reviewing many photos, jumping between unmatched items, or changing defaults quickly. |
| 8 | Aesthetic and Minimalist Design | 3 | Quiet, restrained, and non-decorative, but the interface leans heavily on similar cards and equal spacing, flattening hierarchy. |
| 9 | Error Recovery | 2 | Result sheets report failures, but picker cancellation, invalid export folder recovery, and import/write failures mostly collapse into short log text. |
| 10 | Help and Documentation | 2 | Empty states and labels exist, but there is little contextual help for matching rules, export folder behavior, or original-write consequences. |
| **Total** | | **27/40** | **Acceptable, close to Good. The structure is much better than the old debug shell, but trust and hierarchy still need work.** |

## Anti-Patterns Verdict

**LLM assessment**: This does not immediately read as generic AI output in tone or palette. It follows the product direction: restrained neutrals, standard Android affordances, two clear workflow destinations, and explicit destructive-write handling. The biggest AI-slop tell is structural sameness: nearly every meaningful area is a `SurfaceCard`, many sections use the same 16dp card padding and 18dp list spacing, and the design leans on repeated icon + title + action-row patterns. It is calm, but it risks becoming a stack of polite boxes rather than a purpose-built field instrument.

**Deterministic scan**: The detector was attempted with `node /home/cat/.agents/skills/impeccable/scripts/detect.mjs --json app/src/main/java/com/trackwrite/app/MainActivity.kt`. It exited with code 1 and returned `Error: bundled detector not found.` No rule counts or file locations are available. The detector entrypoint exists, but its bundled rule payload is missing in this environment.

**Visual overlays**: No reliable user-visible overlay is available. Browser visualization was skipped because this target is Android Compose/Kotlin source, not a local web page, and no browser automation surface for an Android runtime is available in this session.

## Overall Impression

TrackWrite now has the right product skeleton. Record and Match map to the photographer workflow, the write path is safer, and the UI avoids the wrong genre cues. The single biggest opportunity is to turn the current "well-organized card stack" into a stronger operational surface: recording truth, photo attention, and write safety should visually dominate more clearly than section chrome.

## What's Working

1. **The information architecture is now pointed in the right direction.** Two destinations, Record and Match, are easier to understand than the previous Record/Match/Library split. Track history and matching track source are separated, which reduces the old mental model conflict.

2. **The write safety model is materially better.** A single default write button, persisted copy-folder setting, and mandatory original-write confirmation align with the product promise: safe metadata writes for real photo libraries.

3. **The visual tone matches the brand.** The palette is quiet, not cyberpunk or fitness-like. Material 3 controls, labeled buttons, and restrained semantic colors support a native, trustworthy product feel.

## Priority Issues

### [P1] Recording state is informative, but not yet field-confident

**Why it matters**: The product promise starts with recording trust. The panel shows state, active track, points, duration, and distance, but it does not communicate recency, GPS freshness, provider status, permission/location disabled states, or whether the current session has actually received a recent point. A photographer outdoors needs to know "is this still working right now?"

**Fix**: Add a last-point freshness line and state-specific feedback in the recording panel: "Last point 12s ago", "Waiting for GPS", "Location disabled", "Permission needed", "Paused". Promote this above secondary metrics when recording is active. Keep points/duration/distance, but make live reliability the strongest signal.

**Suggested command**: `$impeccable harden`

### [P1] Match page still has too many equal-weight decisions at once

**Why it matters**: The Match screen can show track source, photo selection, match settings summary, batch summary, photo rows, and write controls. Several are collapsed, but when expanded the user sees many peer actions: import GPX, choose track, select photos, select folder, set location, clear, write. That raises cognitive load during the highest-stakes batch workflow.

**Fix**: Make Match behave more like a stepwise workspace. Show the current step as primary: choose track if none, select photos if none, review attention if unmatched exists, write when ready. Keep secondary actions available, but visually subordinate them. Add an "attention only" path for unmatched/manual-review photos.

**Suggested command**: `$impeccable distill`

### [P1] Error and recovery feedback is too small for write/import operations

**Why it matters**: Current failures can appear as short log messages or summarized result rows. For geotagging real photo libraries, users need precise recovery: which files failed, why, and what to do next. "Export folder required" or an import failure in a log line is easy to miss.

**Fix**: Standardize a feedback surface for import, folder permission, write cancellation, invalid folder, and write failures. Use the bottom-sheet result pattern for all batch operations, not only completed writes. Include next actions: choose folder again, inspect failed files, retry writeable files.

**Suggested command**: `$impeccable harden`

### [P2] The design overuses cards as the default composition unit

**Why it matters**: Cards help grouping, but using `SurfaceCard` for almost every section makes the interface flatter, not clearer. It also conflicts with the design principle of typographic hierarchy over structural dividers.

**Fix**: Keep cards for bounded objects: track rows, photo rows, result sheets. Let page-level sections become unframed bands or typographic groups. Make the recording panel a distinct instrument-like surface, and make the write panel visually distinct from ordinary settings/history blocks.

**Suggested command**: `$impeccable layout`

### [P2] Settings exposes powerful matching terms without enough context

**Why it matters**: "Camera offset minutes", "max time difference", "start fallback", and "end fallback" are valid domain concepts, but first-time users may not know how these affect matching. Misconfiguration can silently change results.

**Fix**: Add concise helper copy and examples inside settings: "Use camera offset when your camera clock is ahead/behind your phone", "Fallback uses the first/last track point when a photo is near the start/end." Prefer presets or compact explanatory subtitles over raw labels alone.

**Suggested command**: `$impeccable clarify`

## Persona Red Flags

**Mika, photographer in the field**: Mika starts recording before a shoot and glances at the phone outdoors. The app shows recording status and counters, but not whether the last GPS point is fresh. If she starts recording in poor GPS conditions, the panel can still look structurally calm while the actual tracking confidence is unknown. The primary risk is false confidence.

**Jordan, first-time batch geotagger**: Jordan understands Record and Match, but may stall on "matching track", "camera offset", and "fallback". On Match, the path can feel like multiple setup decisions rather than a guided flow. Jordan needs stronger "do this next" sequencing and plainer explanations around match settings.

**Riley, cautious archivist**: Riley cares about not damaging originals. Mandatory original-write confirmation is good, but folder permission errors, skipped photos, and failed writes need more recovery detail. Riley will want to know exactly which files were changed, which were skipped, and whether it is safe to retry.

**Alex, power user**: Alex can batch select and write, but lacks efficient review tools: filter unmatched, jump next attention item, bulk clear manual locations, or quickly change the matching time window from the review context. Alex will tolerate the safety confirmations, but not repeated scanning through long photo rows.

## Minor Observations

- The top subtitle repeats globally even when Record or Match already establishes context. It may be better as first-run or empty-state copy.
- `Share` icon is reused for GPX import/export and write copies. It works as a placeholder, but import/export/write deserve clearer icon distinction.
- The manual-location highlight uses `tertiaryContainer`; it is functional but could read as a persistent warning rather than temporary focus.
- Empty states are present, but "No photos selected" and "No tracks yet" could teach the next action more directly.
- The system log is no longer a primary UI element, which is good, but some transient failures still rely on log-style messages.

## Questions to Consider

- What would make a user trust recording in three seconds outdoors: counters, freshness, map trace, or an explicit GPS health line?
- Should Match be a workspace with a visible current step rather than a list of independent panels?
- Is every card earning its border, or are some sections only boxed because the component exists?
- What would a cautious photographer need to see before believing "write originals" is safe enough to confirm?
