# Android UI Redesign From HTML Mockups

## Goal

Rebuild TrackWrite's native Android UI for the Record, Match, and Settings surfaces using the supplied HTML mockups as visual references only. The app must remain a Kotlin + Jetpack Compose + Material3 implementation and preserve all existing recording, permission, file selection, matching, manual location, export, and write behavior.

## Confirmed Facts

- The mockups live under `docs/mockups/` and cover the Record, Match, and Settings pages.
- The current Android UI is implemented in `app/src/main/java/com/trackwrite/app/MainActivity.kt` with Compose functions for Record, Match, Settings, sheets, dialogs, and shared UI primitives.
- `MainActivity` owns ActivityResult launchers and business callbacks for location permissions, GPX import/export, photo/file selection, manual map selection, and write confirmation.
- Settings are persisted through `AppSettingsStore`; recording frequency is read by `TrackingService`, and matching settings map to `MatchOptions`.
- Product direction from `PRODUCT.md` / `DESIGN.md`: reliable, light, quiet, content-forward, restrained color, no decorative gradients, no neon, no repeated generic card grids.
- User-visible strings must remain Android resources in default and Simplified Chinese resource files.

## Visual Decisions To Extract

### Record

- Use a single high-priority recording proof panel at the top: status pill, short sync/confidence text, strong state title, evidence row, and primary actions.
- Map recording states to real `RecordingSnapshot` and track state:
  - stopped -> before-shooting prompt
  - recording + waiting for first fix -> pending tone
  - recording + recent point -> success tone
  - recording + stale point / permission / location disabled -> warning or error tone
  - paused -> pause tone
- Keep track metrics close to recording state: track name, usability mark, point count, duration, and distance.
- Keep history management in a bottom sheet with compact track rows and action buttons.
- Do not add new GPS diagnostics beyond existing provider, issue, last point, points, duration, and distance.

### Match

- Structure the page as a task flow: track source, photo input or batch overview, then write readiness/action.
- Track source should show an empty state when no track is selected, and a compact metric summary when selected.
- Photo input should keep the existing individual photo picker and folder picker actions.
- Batch overview should summarize matched, unmatched, manual, and skipped/writeable counts from real `PhotoMatchResult`.
- Write area should distinguish safe copies from original writes with clear visual hierarchy and warning tone for originals.
- Bottom sheets should expose track selection/import and photo details/manual location actions without adding new business features.

### Settings

- Keep settings as grouped native rows, using sections for General, Photo matching, and Export.
- Appearance and recording frequency should use lightweight native option selection instead of alert dialogs. To stay close to the HTML drawer interaction, implement them as native Material bottom sheets with compact selected rows.
- Numeric settings should remain stepper controls with visible units.
- Switches and segmented export mode should remain backed by persisted settings.
- Export folder row must show the saved readable SAF tree id when configured.

## Requirements

- Use native Compose + Material3. Do not use WebView for these pages and do not add a web runtime.
- Keep the behavior layer intact. UI code may call existing callbacks but must not rewrite permission handling, file selection, photo loading, matching, manual map selection, or EXIF write flows.
- Map static mockup values to existing real state. Do not invent new persistent settings or new matching/write features.
- Keep changes focused on target page UI and shared primitives needed by those pages.
- Preserve accessibility basics: labels, icons paired with text for semantic states, large enough touch targets, and color not used as the only status signal.
- Keep theme usage token-based. Avoid new page-level light/dark branching when `TrackWriteTheme` already owns color tokens.
- Treat the HTML bottom navigation as the source of truth for this redesign: Record, Match, and Settings should share one native three-item bottom bar with matching icon intent, spacing, selected-state pill, and restrained slate color treatment.

## Acceptance Criteria

- [x] Record page visually follows the mockup's proof-panel + metrics + history row layout while showing real recording status, confidence, active/selected track stats, and existing controls.
- [x] Match page visually follows the mockup's track source + photo selection/batch + write area flow while preserving existing picker, matching, manual location, and write callbacks.
- [x] Settings page visually follows the mockup's sectioned form layout while preserving persisted settings behavior and existing folder chooser.
- [x] Record, Match, and Settings share a bottom navigation bar visually aligned with the HTML mockups.
- [x] Existing sheets/dialogs for history, track source, photo batch, write progress, write results, start/rename/delete/write confirmation still work from the same state and callbacks.
- [x] No user-visible copy is hard-coded in Composables unless it already existed as a non-resource symbol; new copy is added to default and Simplified Chinese resources.
- [x] `./gradlew :app:compileDebugKotlin` succeeds.

## Out Of Scope

- New business capabilities not present in the app, including new GPS diagnostics, map preview on the main pages, new batch filters, new write modes, or extra settings.
- Embedding or rendering the supplied HTML inside the app.
- Large architecture extraction outside the target UI surface.
- Reworking the manual AMap picker.

## Open Questions

None blocking. The user requested direct implementation after reading the mockups and current UI. Any visual choice not explicitly defined by the mockups should follow the current TrackWrite product and theme guidelines.
