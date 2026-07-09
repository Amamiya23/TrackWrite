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
| primary | `0xFF326AA8` | `0xFF8AB4F0` (brand blue, lightened) |
| primaryContainer | `0xFFEAF2FB` | `0xFF1B3A5F` |
| onSurface | `0xFF0F172A` (slate-900) | `0xFFE2E8F0` (slate-200) |

See `TrackWriteTheme.kt` for the full token mapping.

Light and dark share the same brand blue hue; only lightness shifts so switching
modes feels like a lighting change, not a brand change. Dark `primary` is
`#8AB4F0` (brand blue lightened to AA), **not** the old neutral `#94A3B8` —
the prior neutral-slate dark primary broke brand cohesion.

### System Mode: Symmetric Manual Scheme

All three `AppearanceMode` values (System / Light / Dark) resolve to the manual
`LightColors` / `DarkColors` defined in `TrackWriteTheme.kt`. Dynamic color
(`dynamicLightColorScheme` / `dynamicDarkColorScheme`) is intentionally **not**
used. Brand consistency wins over device-wallpaper personalization; the
restraint is backed by DESIGN.md's "single purposeful accent" rule.

## Token System

Design scales live in `TrackWriteTheme.kt` as three top-level objects. Prefer
these over raw `dp` / `alpha` / `RoundedCornerShape` literals:

```kotlin
TrackShape.pill    // 50% — chips, status pills
TrackShape.control // 12dp — buttons, inputs, small controls
TrackShape.card    // 16dp — SurfaceCard, panels, sheet-internal cards

TrackSpacing.x1..x7  // 4 / 8 / 12 / 16 / 20 / 24 / 28 dp (multiples of 4)

TrackAlpha.disabled // 0.38 — unavailable controls / icons
TrackAlpha.faint    // 0.60 — muted icons, dividers, borders
TrackAlpha.subtle   // 0.80 — container overlays
```

Forbidden: scattering `RoundedCornerShape(6/8/10/12/14/16.dp)` or
`.copy(alpha = 0.28/0.45/0.55/0.65f)` literals across composables. Use a token;
if a value is genuinely new and contextual, name it in `TrackAlpha` rather than
inlining.

## Typography

A custom `Typography` (`AppTypography` in `TrackWriteTheme.kt`) overrides the
Material 3 default. No custom font family is introduced — system Roboto stays.
Weights are restricted to four tiers: Normal 400, Medium 500, SemiBold 600, Bold
700. **`FontWeight.ExtraBold` / `Black` are forbidden** in composables; they
break "The Quiet Instrument" restraint. Titles default to SemiBold, body to
Normal, labels to Medium.

## Icon System

All icons resolve through `Icons.Rounded.*` (Material Symbols Rounded). The
`TrackWriteIcon` enum in `MainActivity.kt` maps each name to an `ImageVector`
and `TrackWriteLineIcon` delegates to the standard Material `Icon()`. Do not
introduce filled (`Icons.Default` / `Icons.Filled`) icons — they clash visually
with the rounded line set. Do not re-add hand-drawn Canvas icon drawing.

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
