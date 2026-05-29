# Light Mode Background and Card Colors

## Goal

Align the app's light appearance with the requested Slate 50 page background and white card treatment, with special attention to Settings.

## Requirements

- In light appearance, `MaterialTheme.colorScheme.background` must be `#F8FAFC`.
- In system appearance while the device is in light mode, `MaterialTheme.colorScheme.background` must also be `#F8FAFC`.
- Settings page group cards must use a hard-coded white container color (`Color.White`, `#FFFFFF`) instead of deriving from `surfaceContainerLow`.
- Keep dark appearance behavior unchanged.
- Do not change layout, copy, navigation, settings behavior, or business logic.

## Acceptance Criteria

- [ ] Light appearance page backgrounds render from `colorScheme.background = #F8FAFC`.
- [ ] System appearance with a light system theme uses `colorScheme.background = #F8FAFC`.
- [ ] Settings page card groups render with `Color.White`.
- [ ] Dark appearance colors are not changed.
- [ ] `./gradlew :app:compileDebugKotlin` passes.

## Definition of Done

- Code follows existing Compose and `TrackWriteTheme` patterns.
- Scope stays limited to color values and card container color.
- Relevant compile/type check passes.

## Technical Approach

- Update the custom light color scheme in `TrackWriteTheme.kt`.
- For system-light mode, preserve dynamic color where possible but override `background` to the requested Slate 50 value.
- Update `SettingsGroup` in `MainActivity.kt` to use `Color.White`.

## Decision (ADR-lite)

**Context**: Material You dynamic light colors can make system-light backgrounds vary by device, and Settings currently uses `surfaceContainerLow`, which can visually merge into the page background.

**Decision**: Pin the light background token to `#F8FAFC` for both explicit light and system-light theme paths, and hard-code Settings group surfaces to `Color.White`.

**Consequences**: Page/card contrast is consistent in light mode. Dynamic color remains available for other system-light tokens, while dark mode remains unchanged.

## Out of Scope

- Changing dark mode colors.
- Redesigning Settings layout, spacing, typography, or interactions.
- Changing non-settings card styles unless needed to satisfy the background token requirement.

## Technical Notes

- Theme: `app/src/main/java/com/trackwrite/app/ui/TrackWriteTheme.kt`
- Settings groups: `app/src/main/java/com/trackwrite/app/MainActivity.kt`
- Relevant frontend spec: `.trellis/spec/frontend/quality-guidelines.md`
