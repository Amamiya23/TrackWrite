# Fix settings backup folder persistence and live duration refresh

## Goal

Fix two visible product bugs: the default export-copy folder chosen from Settings must survive app refresh/restart and show the selected folder in the Settings row, and the active recording duration must keep advancing while recording even when no new location point arrives.

## What I already know

- `AppSettingsStore` persists settings in SharedPreferences named `app_settings`.
- Default export-copy writes read `AppSettings.defaultExportFolderUri` before prompting for a folder.
- Settings UI currently exposes the default export folder through `ExportFolderRow`.
- Recording UI renders the duration in `RecordingPanel` from the selected active track.
- `TrackingService` appends points when Android delivers location updates; those updates can be sparse because recording frequency uses both time and distance thresholds.

## Requirements

- Persist the folder URI selected through the default export-copy folder picker.
- Take persistable SAF permissions for the selected export folder when possible.
- Display a readable selected folder label/path in the Settings export folder row after selection and after reloading persisted settings.
- Keep the selected folder URI as the behavior source of truth for write-copy export.
- While recording, recompute the visible duration on a clock tick independent of new GPS points.
- Preserve stopped/paused track duration behavior from persisted track points.

## Acceptance Criteria

- [ ] Choosing a default export folder updates `AppSettingsStore.defaultExportFolderUri`.
- [ ] Re-entering Settings shows the selected folder instead of only the unconfigured copy.
- [ ] Write-copy export uses the persisted default folder when it is still writable.
- [ ] The Record tab duration increments during active recording even if point count is unchanged.
- [ ] Kotlin compilation passes.

## Definition of Done

- Run `./gradlew :app:compileDebugKotlin`.
- Run relevant unit tests when feasible.
- Preserve existing Compose and settings-store conventions.
- Do not include unrelated dirty files in any commit plan.

## Out of Scope

- Adding a folder clear/reset affordance.
- Changing GPX duration semantics for stopped tracks.
- Adding database schema fields for track created time.

## Technical Notes

- Relevant UI and activity wiring: `app/src/main/java/com/trackwrite/app/MainActivity.kt`.
- Relevant settings persistence: `app/src/main/java/com/trackwrite/app/settings/AppSettingsStore.kt`.
- Relevant recording service state: `app/src/main/java/com/trackwrite/app/recording/RecordingStateStore.kt` and `TrackingService.kt`.
- Relevant spec: `.trellis/spec/frontend/quality-guidelines.md`.
