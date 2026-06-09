# Design

## Boundaries

All implementation is scoped to native Compose UI in `MainActivity.kt` and string resources if new user-visible copy is needed. Existing service, repository, GPX, geotagging, matching, settings persistence, permission, and file-picker behavior remain unchanged.

## UI Approach

- Surface `MainUiState.logMessage` through a Material3 `SnackbarHost` in the existing `Scaffold`.
- Clear consumed log messages through a new callback from `TrackWriteApp` to `MainActivity`.
- Update Match empty track copy only at the UI/string layer.
- Raise touch targets by adjusting existing component dimensions and hit areas, not by changing callbacks.
- Keep `WriteProgressDialog` as the authoritative write-progress surface; suppress the duplicate inline bulk panel for writing operations.
- Use warning tone for `PhotoMatch.Unmatched`.
- Add local sheet state for photo filters inside `PhotoBatchSheet`; filters derive from `matches` and do not mutate match results.
- Update the light theme token source so `background = #F7F7F7` and white card surfaces continue to come from `surface` / `surfaceContainerLow`.
- Rebalance Settings page hierarchy with a simple product-layout rhythm: slightly larger section headers, stronger section/header separation, regular-weight row labels, and body-sized secondary text for readability.

## Compatibility

Settings, track selection, photo selection, manual location binding, and writes continue to use existing callbacks and state models. New filtering is visual-only and reset when the sheet leaves composition.

## Tradeoffs

Snackbar feedback is intentionally transient and low-noise. It improves visibility without adding persistent status panels to the quiet product UI. Photo filtering is limited to four useful categories to avoid a broad search/sort feature.

## Rollback

All changes can be reverted from `MainActivity.kt`, `TrackWriteTheme.kt`, spec docs, and added string resources without migration.
