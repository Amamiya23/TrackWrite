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
- **[To be resolved during implementation]**: Used exclusively for active recording states, primary actions, and selected map markers.

### Neutral
- **[To be resolved during implementation]**: Backgrounds, surfaces, text, and subtle borders. 

## 3. Typography

**Display Font:** [font pairing to be chosen at implementation]
**Body Font:** [font pairing to be chosen at implementation]

**Character:** A single, highly legible sans-serif family. It should feel technical enough to handle dense lists of coordinates and timestamps, but warm enough to feel like a modern Android app rather than a terminal.

### Hierarchy
- **Display** ([weight], [size/clamp], [line-height]): [To be resolved during implementation]
- **Headline** ([weight], [size], [line-height]): [To be resolved during implementation]
- **Title** ([weight], [size], [line-height]): [To be resolved during implementation]
- **Body** ([weight], [size], [line-height]): [To be resolved during implementation]
- **Label** ([weight], [size], [letter-spacing], [case if uppercase]): [To be resolved during implementation]

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
