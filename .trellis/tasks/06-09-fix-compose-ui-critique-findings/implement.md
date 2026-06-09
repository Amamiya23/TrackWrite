# Implementation Plan

1. Add snackbar feedback plumbing
   - Add `onLogMessageConsumed` callback.
   - Render `SnackbarHost` in `Scaffold`.
   - Consume `state.logMessage` after snackbar display.

2. Fix Match screen track empty copy
   - Add or reuse a string for "choose track".
   - Keep existing track source sheet interaction.

3. Fix touch targets
   - Ensure drawer close, stepper buttons, and photo inline actions are at least 44dp.
   - Keep visual styling restrained.

4. Remove progress duplication
   - Keep `WriteProgressDialog`.
   - Show inline loading only for photo/folder loading where no dialog exists.

5. Improve per-photo status and filters
   - Change unmatched `MatchPill` tone to warning.
   - Add local `PhotoBatchFilter` and filter chips/buttons in `PhotoBatchSheet`.
   - Keep manual location callbacks using original match indices.

6. Validate
   - Run `./gradlew :app:compileDebugKotlin`.
   - If time allows, run focused unit tests only if UI changes unexpectedly touch domain code.

7. Theme token adjustment
   - Set the light/system-light app background token to `#F7F7F7`.
   - Keep card surfaces on `surface` / `surfaceContainerLow` as `#FFFFFF`.
   - Sync theme specs with the token change.

8. Settings layout and typography pass
   - Increase settings section and row typography for better readability.
   - Keep section headers bold and individual setting labels regular.
   - Use the existing white-card settings grouping and 44dp controls.

## Risk / Rollback Points

- Snackbar consumption touches app-level state. If behavior regresses, revert only the snackbar plumbing and callback.
- Photo filtering must preserve original indices for manual location actions.
