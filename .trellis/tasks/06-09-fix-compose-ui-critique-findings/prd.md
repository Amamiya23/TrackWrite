# Fix Compose UI critique findings

## Goal

Address P1/P2 UI critique findings in TrackWrite native Compose screens: visible feedback, match empty wording, touch targets, progress duplication, unmatched tone, and photo list filtering.

## Requirements

- Add a visible, low-noise feedback channel for existing `MainUiState.logMessage` events such as settings saved, folder saved, validation failures, import/write errors, and blocked actions.
- Clarify the Match screen empty/unselected track state so it asks the user to choose or import a track, without incorrectly implying that no tracks exist.
- Ensure interactive controls introduced or already flagged by the critique meet a 44dp minimum touch target, especially drawer close controls, steppers, and photo row inline actions.
- Remove duplicate write/loading progress feedback on the Match screen. The user should see one clear progress surface for a given operation.
- Make single-photo unmatched status visually consistent with batch-level warning status while retaining existing match behavior.
- Add lightweight filtering inside the photo batch bottom sheet so users can narrow the list by all, unmatched, manual, and writeable photos without changing matching or write behavior.
- Set the light/system-light app background to `#F7F7F7` while keeping card and panel surfaces that use `surface` / `surfaceContainerLow` pure white (`#FFFFFF`).
- Improve Settings page layout and readability: increase small typography, make only setting group titles bold, and keep individual setting labels at regular weight.
- Preserve existing recording, permission, GPX import/export, photo selection, manual location, matching, settings persistence, and write confirmation flows.
- Keep the UI aligned with TrackWrite principles: reliable, light, quiet, native Compose/Material3, no WebView, no decorative gradients, no new fake features.

## Acceptance Criteria

- [x] Existing `logMessage` updates are surfaced visibly and consumed without requiring a full page refresh.
- [x] Match screen with no selected track displays a clear "choose track" style prompt and still opens the existing track source sheet.
- [x] Flagged controls have at least 44dp hit targets.
- [x] Loading photos and writing photos do not produce redundant visible progress blocks on the Match screen.
- [x] Unmatched photo rows use warning tone in the per-photo status pill.
- [x] Photo batch sheet includes functional all/unmatched/manual/writeable filters backed by current `matches` state.
- [x] Light and system-light backgrounds render as `#F7F7F7`; card surfaces remain `#FFFFFF`.
- [x] Settings groups have bold readable section titles, while concrete setting rows use larger regular-weight text.
- [x] No business logic or persistence contracts are changed outside these UI fixes.
- [x] `./gradlew :app:compileDebugKotlin` passes.

## Notes

- Source critique target: `app/src/main/java/com/trackwrite/app/MainActivity.kt`.
