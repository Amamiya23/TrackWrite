# Batch Location Write Progress Dialog

## Goal

When users batch-write photo locations, the UI should show clear progress feedback immediately after they confirm the write. This keeps destructive or long-running EXIF/location writes from feeling stalled.

## What I Already Know

- The user specifically asked for a popup progress animation after confirming batch location writes.
- `MainActivity.writeDefault()` opens the original-photo confirmation dialog when the default mode is writing originals.
- `writeOriginals()` and `writeCopies()` both route through `writePhotos(...)`.
- `writePhotos(...)` already sets `uiState.bulkOperation` to `WritingOriginals` or `WritingCopies` while work runs on `Dispatchers.IO`.
- The Match screen currently shows inline bulk operation text, but no modal progress feedback after the confirmation dialog closes.
- Existing strings already distinguish writing copies and writing originals in English and Simplified Chinese.

## Requirements

- After confirming original-photo writes, close the confirmation dialog and immediately show a visible progress popup while writing is running.
- The same progress popup should also appear for copy writes, because both are batch location write operations.
- The popup should use the existing `BulkOperation` state so it cannot drift from the actual operation lifecycle.
- The popup should show real per-photo progress, not only an indeterminate spinner.
- The popup should show processed count and percentage so the write does not feel like a black box.
- The popup should be passive progress feedback, not a second confirmation step.
- When writing completes or fails, the progress popup should disappear because `bulkOperation` returns to null.
- Existing write result and error behavior should remain unchanged.

## Acceptance Criteria

- [x] `BulkOperation.WritingOriginals` renders a Material progress dialog with real per-photo progress and the existing original-write progress text.
- [x] `BulkOperation.WritingCopies` renders the same style of progress dialog with real per-photo progress and the existing copy-write progress text.
- [x] The write progress dialog shows processed count and percentage.
- [x] Non-writing bulk operations, such as loading photos, do not show this write progress dialog.
- [x] Existing inline bulk operation feedback remains intact.
- [x] User-visible copy is stored in default and Simplified Chinese string resources when new text is needed.
- [x] `./gradlew :app:compileDebugKotlin` passes.

## Out of Scope

- Do not change EXIF write behavior.
- Do not redesign the write result sheet or matching workflow.

## Technical Notes

- Main file: `app/src/main/java/com/trackwrite/app/MainActivity.kt`.
- Write progress source: `PhotoGeotagging.exportCopies(...)` and `PhotoGeotagging.writeInPlace(...)` accept optional per-photo progress callbacks.
- String resources:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-zh-rCN/strings.xml`
- UI should stay calm and product-focused, matching TrackWrite's quiet/reliable design direction.
- Spec update judgment: no `.trellis/spec` change needed. This task reused existing Compose, string-resource, and `BulkOperation` conventions; the new progress callback is an internal optional parameter for this write flow, not a persisted API, database, or settings contract.
