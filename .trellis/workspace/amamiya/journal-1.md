# Journal - amamiya (Part 1)

> AI development session journal
> Started: 2026-05-26

---



## Session 1: Redesign TrackWrite UI with custom card-based layouts

**Date**: 2026-05-27
**Task**: Redesign TrackWrite UI with custom card-based layouts
**Branch**: `main`

### Summary

Upgraded the UI of MainActivity and ManualLocationActivity using rounded custom cards and a forest-green brand palette. Relocated the manual selection controls directly onto photo items.

### Main Changes

- Set light and system-light `colorScheme.background` to Slate 50 (`#F8FAFC`).
- Set light card surface tokens to `Color.White`, including dynamic system-light overrides.
- Updated Settings groups to use white cards in light mode while preserving dark mode surfaces.
- Documented the light background and white card convention in frontend quality guidelines.

### Git Commits

| Hash | Message |
|------|---------|
| `c08aba7` | (see git log) |

### Testing

- [OK] `./gradlew :app:compileDebugKotlin`
- [OK] `./gradlew testDebugUnitTest :app:lintDebug`

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 2: Settings UX critique fixes

**Date**: 2026-05-28
**Task**: Settings UX critique fixes
**Branch**: `main`

### Summary

Fixed 6 UX critique issues on settings page: merged General card, replaced Refresh icon with Speed, removed duplicate Export label, changed BottomSheet to inline expand, added stepper units, added description subtitles. Updated frontend spec with settings UI patterns.

### Main Changes

- Relaxed JPEG GPSVersionID read-back verification so it no longer blocks otherwise valid GPS writes.
- Kept strict post-write checks for latitude/longitude, coordinate refs, and altitude metadata.
- Added a regression test for the non-blocking GPSVersionID read-back policy.
- Updated backend quality guidance to record the EXIF verification contract.

### Git Commits

| Hash | Message |
|------|---------|
| `bdcc400` | (see git log) |
| `de10717` | (see git log) |

### Testing

- [OK] `testDebugUnitTest`
- [OK] `:app:compileDebugKotlin`
- [OK] `:app:lintDebug`

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 3: Settings MD3 dialogs, theme optimization, version bump

**Date**: 2026-05-29
**Task**: Settings MD3 dialogs, theme optimization, version bump
**Branch**: `main`

### Summary

浅色模式卡片对比度优化，设置页外观和记录频率改为 MD3 AlertDialog，弹窗改为点击即确认，深色主题对比度增强，版本号更新至 v1.1.0，移除设置标签中的（分钟）后缀

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `1026dd4` | (see git log) |
| `e305430` | (see git log) |
| `a6b2437` | (see git log) |
| `cbf55f6` | (see git log) |
| `1a1200c` | (see git log) |
| `d7e9ef3` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 4: Light mode background and white settings cards

**Date**: 2026-05-29
**Task**: Light mode background and white settings cards
**Branch**: `main`

### Summary

Pinned light/system-light background to Slate 50, forced light settings cards to white, and documented the color convention.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `f267e1c` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 5: Settings back button + dark mode fixes

**Date**: 2026-05-29
**Task**: Settings back button + dark mode fixes
**Branch**: `main`

### Summary

Moved settings back button from top-right TextButton to top-left ArrowBack icon in TopAppBar.navigationIcon. Replaced greenish-teal dark color scheme with slate-gray palette matching the light theme. Removed conditional dark-mode override for settingsGroupColor, using theme token directly.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `1df4ce2` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 6: Fix bulk photo GPS EXIF fields

**Date**: 2026-06-06
**Task**: Fix bulk photo GPS EXIF fields
**Branch**: `main`

### Summary

Fixed JPG/JPEG GPS EXIF writing so batch photo geotagging writes valid coordinates, refs, GPS version, and altitude handling; added JPEG MIME alias and uppercase extension coverage while preserving RAW rejection.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `f9174c3` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 7: Fix photo GPS version verification

**Date**: 2026-06-06
**Task**: Fix photo GPS version verification
**Branch**: `main`

### Summary

Relaxed JPEG GPSVersionID read-back verification so valid GPS coordinate writes are not reported as failed; kept coordinate/ref/altitude verification strict and documented the EXIF verification contract.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `4c01f9b` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete
