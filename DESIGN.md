---
name: TrackWrite
description: Reliable GPS tracking and photo geotagging for photographers.
---

<!-- SEED: re-run /impeccable document once there's code to capture the actual tokens and components. -->

# Design System: TrackWrite

## 1. Overview

**Creative North Star: "The Quiet Instrument"**

TrackWrite is a calm, dependable tool that borrows the typographic restraint of modern productivity software (like Linear and Notion) while respecting the native affordances of Android's Material You. It is an instrument designed to do a technical job—recording GPS tracks and matching EXIF data—without feeling heavy or overly technical. 

The aesthetic philosophy centers on giving primacy to the user's data (photos, timestamps, maps) by keeping the UI chrome minimal. The interface relies on crisp typography, generous whitespace, and subtle state feedback rather than heavy containers or decorative color. It explicitly rejects the aesthetic of a "cyberpunk dashboard" or an aggressive fitness tracker; there are no neon accents, deep dark-mode glow effects, or complex telemetry walls here.

**Key Characteristics:**
- Content-forward, putting maps and photo thumbnails first.
- Typographic hierarchy over structural dividers.
- High-contrast states to ensure legibility outdoors.
- Predictable Android native patterns.

## 2. Colors

The palette is highly restrained, leaning on tinted neutral grays/slates to build structure, while reserving color exclusively for critical state communication (e.g., active GPS recording, destructive write warnings).

**The Restrained Rule.** Tinted neutrals handle the structure. A single, purposeful accent color carries ≤10% of any given surface. 

### Primary
- **Quiet Blue**: `#326AA8` (light) / `#8AB4F0` (dark). Used exclusively for active recording states, primary actions, and selected map markers. Light and dark share the same blue hue; only lightness shifts so switching modes feels like a lighting change, not a brand change.

### Neutral
- **Cold-tinted Slate**: Light `#F7F7F7` background, `#FFFFFF` surfaces, `#F4F5F7` panels; Dark `#0F172A` background, `#1E293B` surfaces, `#334155` panels. Tinted toward blue to stay cohesive with the primary accent. Backgrounds, surfaces, text, and subtle borders.

## 3. Typography

**Display Font:** System Roboto (no custom font family)
**Body Font:** System Roboto (no custom font family)

**Character:** A single, highly legible sans-serif family. It should feel technical enough to handle dense lists of coordinates and timestamps, but warm enough to feel like a modern Android app rather than a terminal.

### Hierarchy
- **Headline** (SemiBold 600, 28sp/24sp, 36sp/32sp line-height): `headlineMedium` / `headlineSmall`
- **Title** (Medium 500, 22sp/16sp/14sp, 28sp/24sp/20sp line-height): `titleLarge` / `titleMedium` / `titleSmall`
- **Body** (Normal 400, 16sp/14sp/12sp, 24sp/20sp/16sp line-height): `bodyLarge` / `bodyMedium` / `bodySmall`
- **Label** (Medium 500, 14sp/12sp/11sp, 20sp/16sp/16sp line-height, 0.1-0.5sp letter-spacing): `labelLarge` / `labelMedium` / `labelSmall`

## 4. Elevation

The application is flat by default. Because motion energy is restrained strictly to state changes (hover, press, feedback), depth is primarily conveyed through subtle tonal shifts rather than cast shadows. Shadows, if used at all, are reserved only to lift transient elements (like a photo picker modal or a map overlay) off the base canvas.

## 6. Do's and Don'ts

### Do:
- **Do** use typographic weight and size contrast to establish hierarchy rather than drawing boxes around everything.
- **Do** ensure all text and interactive elements meet WCAG AA contrast standards, particularly for outdoor daylight use.
- **Do** use color solely to communicate state (e.g., active recording, error, success) rather than for decoration.
- **Do** reserve motion for immediate, functional feedback—no choreographed entry sequences.

### Don't:
- **Don't** make the interface look like a "cyberpunk dashboard" or telemetry wall with dark neon, glowing borders, or heavy technical chrome.
- **Don't** use decorative gradients, glassmorphism, or purely aesthetic blur effects.
- **Don't** rely on color alone to communicate status; always pair it with an icon, label, or structural indicator.
- **Don't** use identical card grids endlessly; vary the layout to fit the specific data (e.g., track list vs. photo picker).
