# Theme & Color Scheme Guidelines

---

## Color Palette: Slate Family

This project uses the **Tailwind Slate** color scale as the basis for both light and dark themes. Dark and light modes share the **same hue family** (slate/gray) — only lightness and saturation are adjusted per MD3 dark scheme conventions.

**Do NOT** introduce a different hue family (e.g., teal, green) for dark mode. The two palettes must be visually cohesive so that switching between modes feels like a lighting change, not a brand change.

| Token | Light (Slate) | Dark (Slate) |
|-------|---------------|---------------|
| background | `0xFFF8FAFC` (slate-50) | `0xFF0F172A` (slate-900) |
| surface | `0xFFFFFFFF` (white) | `0xFF1E293B` (slate-800) |
| surfaceContainerLow | `0xFFFFFFFF` (white) | `0xFF1E293B` (slate-800) |
| surfaceVariant | `0xFFF1F5F9` (slate-100) | `0xFF334155` (slate-700) |
| primary | `0xFF1E293B` (slate-800) | `0xFF94A3B8` (slate-400) |
| onSurface | `0xFF0F172A` (slate-900) | `0xFFE2E8F0` (slate-200) |

See `TrackWriteTheme.kt` for the full token mapping.

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

## Convention: Navigation Icon for Back Actions

When a screen replaces the default navigation (e.g., Settings replacing the main tabs), the back/close action uses the `navigationIcon` slot of `TopAppBar` (left side), not the `actions` slot (right side).

**Pattern**:
```kotlin
TopAppBar(
    navigationIcon = {
        if (showSubScreen) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    },
    actions = {
        if (!showSubScreen) {
            IconButton(onClick = onSettings) { ... }
        }
    },
)
```

This follows the Material Design convention: navigation/back goes left, secondary actions go right.