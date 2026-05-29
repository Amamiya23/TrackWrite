# Settings Back Button Position and Dark Mode Style Fixes

## Goal

Fix two UI inconsistencies: (1) move the settings back button from top-right to top-left as an icon, and (2) make the dark mode color scheme correspond to the light mode slate palette instead of the current unrelated greenish-teal palette.

## Requirements

1. **Back button position change**: Remove the `TextButton("Back")` from `TopAppBar.actions` (top-right) and add an `IconButton` with `ArrowBack` icon in `TopAppBar.navigationIcon` (top-left) when `state.showSettings` is true.
2. **Dark mode color scheme**: Replace `DarkColors` in `TrackWriteTheme.kt` with a dark variant of the light slate palette (same hue family — slate/gray — adjusted for dark backgrounds per MD3 guidelines).
3. **Remove conditional dark-mode override in SettingsScreen**: The `settingsGroupColor` conditional (`if (darkTheme) surfaceContainerLow else Color.White`) should be replaced with `MaterialTheme.colorScheme.surfaceContainerLow` directly, since the theme's `surfaceContainerLow` token will now correctly handle both modes.

## Acceptance Criteria

- [ ] Settings page shows a left-aligned back arrow icon instead of a right-aligned "Back" text button
- [ ] Dark mode color scheme uses slate-gray tones consistent with the light mode palette
- [ ] Settings group cards render correctly in both light and dark modes without conditional color overrides
- [ ] Light mode appearance is unchanged

## Definition of Done

- Lint and type-check pass
- Visual verification: light and dark mode both look consistent
- No hardcoded dark-theme conditionals remain in SettingsScreen

## Technical Approach

**Back button**: In `MainActivity.kt` line 640-649, change `TopAppBar`:
- Add `navigationIcon = { if (state.showSettings) IconButton(onClick = onCloseSettings) { Icon(Icons.AutoMirrored.Filled.ArrowBack, ...) } }`
- Remove the `TextButton` from `actions` when `state.showSettings`
- Add import for `Icons.AutoMirrored.Filled.ArrowBack`

**Dark mode palette**: In `TrackWriteTheme.kt`, replace `DarkColors` with a slate-gray dark palette derived from the light slate colors:
- Dark background: ~0xFF0F172A (slate-900)
- Dark surface: ~0xFF1E293B (slate-800)
- Dark primary: ~0xFF94A3B8 (slate-400) 
- All other tokens mapped per MD3 dark scheme conventions

**Settings conditional removal**: In `MainActivity.kt` line 1663-1667, replace:
```kotlin
val settingsGroupColor = if (darkTheme) {
    MaterialTheme.colorScheme.surfaceContainerLow
} else {
    Color.White
}
```
with simply `MaterialTheme.colorScheme.surfaceContainerLow`, and remove the `darkTheme` variable if no longer needed elsewhere.

## Out of Scope

- System (dynamic) theme behavior — leave as-is
- Other screens' dark mode adjustments beyond what the theme palette change covers
- Adding new theme mode options

## Technical Notes

- Files: `MainActivity.kt` (TopAppBar at line 622-654, SettingsScreen at line 1650-1672), `TrackWriteTheme.kt` (DarkColors at line 48-76)
- Light palette uses Slate colors (Tailwind slate scale)
- Current dark palette is unrelated greenish-teal — must be replaced entirely