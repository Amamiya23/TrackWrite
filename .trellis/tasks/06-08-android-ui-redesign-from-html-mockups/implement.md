# Implementation Plan

## Checklist

1. [x] Read task artifacts and frontend/theme specs.
2. [x] Inspect mockup structure and current `MainActivity.kt` UI functions.
3. [x] Add any missing string resources in both default and Simplified Chinese files.
4. [x] Update shared UI primitives only where needed by the target pages.
4.1. [x] Replace the default Material bottom navigation with a mockup-aligned three-item native Compose bar for Record, Match, and Settings.
5. [x] Redesign Record:
   - proof panel
   - current track metrics
   - history row and sheet item styling
6. [x] Redesign Match:
   - track source panel
   - photo input / batch overview
   - write readiness/action panel
   - track and photo bottom sheets
7. [x] Redesign Settings:
   - section spacing and row styling
   - native bottom-sheet option pickers for appearance and frequency
   - preserve steppers, switches, segmented export, folder row
8. [x] Run `./gradlew :app:compileDebugKotlin`.
9. [x] If time and environment permit, run `./gradlew testDebugUnitTest`.
10. [x] Run `./gradlew :app:lintDebug`.
11. [x] Fix any compile/test issues and summarize touched files.

## Validation Commands

```bash
./gradlew :app:compileDebugKotlin
./gradlew testDebugUnitTest
./gradlew :app:lintDebug
```

## Validation Results

- `./gradlew :app:compileDebugKotlin` passed.
- `./gradlew testDebugUnitTest` passed after running outside the default sandbox because Gradle needed to write the wrapper lock under `~/.gradle`.
- `./gradlew :app:lintDebug` passed after running outside the default sandbox for the same wrapper-lock reason.

## Risk Points

- `MainActivity.kt` is a large file; keep edits focused and avoid moving behavior into new files unless necessary.
- Import churn can break Compose compilation. Verify with `compileDebugKotlin`.
- User's Trellis update has many dirty files. Do not stage, revert, or alter those files during UI work.
- Settings rows must remain backed by `AppSettingsStore`; avoid adding inert controls.
- Write originals flow must still pass through the existing confirmation dialog.

## Review Gate

The user explicitly requested implementation after the Trellis update. Treat that as approval to start once these planning artifacts exist.
