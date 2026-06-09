# Theme & Color Scheme Guidelines

---

## Color Palette: Slate With Quiet Blue Accent

This project uses a slate/blue-neutral color system derived from the native
TrackWrite UI and the HTML redesign mockups. Neutrals carry most structure;
the quiet blue accent is reserved for primary actions, selected navigation, and
positive/active recording states.

**Do NOT** introduce unrelated hue families (for example teal, neon green, or
purple) for app chrome. Light and dark palettes must stay visually cohesive so
switching modes feels like a lighting change, not a brand change.

| Token | Light | Dark |
|-------|---------------|---------------|
| background | `0xFFF7F7F7` | `0xFF0F172A` (slate-900) |
| surface | `0xFFFFFFFF` (white) | `0xFF1E293B` (slate-800) |
| surfaceContainerLow | `0xFFFFFFFF` (white) | `0xFF1E293B` (slate-800) |
| surfaceVariant | `0xFFF4F5F7` | `0xFF334155` (slate-700) |
| surfaceContainer | `0xFFECEFF3` | `0xFF273549` |
| primary | `0xFF326AA8` | `0xFF94A3B8` (slate-400) |
| primaryContainer | `0xFFEAF2FB` | `0xFF334155` |
| onSurface | `0xFF0F172A` (slate-900) | `0xFFE2E8F0` (slate-200) |

See `TrackWriteTheme.kt` for the full token mapping.

When using Material You dynamic light color, copy these structural tokens over
the dynamic scheme. The app must keep the mockup-aligned background, white
panels, quiet blue primary, and soft selected-state container instead of letting
device wallpaper colors recolor the core workflow.

---

## Forbidden Pattern: Conditional Dark-Mode Overrides

### Don't

```kotlin
val darkTheme = isSystemInDarkTheme()
val cardColor = if (darkTheme) {
    MaterialTheme.colorScheme.surfaceContainerLow
} else {
    Color.White
}
```

**Why it's bad**: It bypasses the theme system and hardcodes assumptions about what colors should be in each mode. When the theme palette is correct, these conditionals are redundant and create maintenance burden — every new color override must be updated separately for dark mode.

### Instead

```kotlin
val cardColor = MaterialTheme.colorScheme.surfaceContainerLow
```

**Why**: The theme's color tokens already handle both modes. If the card color looks wrong in dark mode, fix the **theme palette token** (`surfaceContainerLow` in `TrackWriteTheme.kt`), not the component code.

---

## Convention: Navigation and Back Actions

Record, Match, and Settings are peer destinations in the redesigned app shell.
They share the custom three-item bottom bar, with the selected item rendered as
a rounded soft-primary pill. Settings should not show a top-left back arrow in
this shell; system Back may still close Settings via `BackHandler`.

When a future screen truly replaces the bottom navigation, the back/close action
uses the navigation area on the left side, not the right-side action area.

**Pattern**:
```kotlin
TrackWriteBottomBar(
    selectedTab = state.selectedTab,
    settingsSelected = state.showSettings,
    onRecord = { onTabSelected(MainTab.Record) },
    onMatch = { onTabSelected(MainTab.Match) },
    onSettings = onSettings,
)
```

This keeps the app shell aligned with the HTML mockups while preserving native
Compose navigation behavior.
