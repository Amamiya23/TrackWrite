# Implementation Plan

## Ordered Checklist

1. Read the applicable frontend/backend quality and theme specs through `trellis-before-dev` before editing.
2. In `MainActivity.writeDefault()`, gate the copy branch with the existing `requestMediaLocationPermissionThen` helper, then continue into `writeCopiesUsingDefaultFolder()` only after permission is granted.
3. Confirm the export-folder launcher still calls the internal write function only after the permission-gated flow opened it, and that denial leaves `bulkOperation`, `writeProgress`, and `pendingExportMode` idle.
4. In `WriteResultSheet`, replace the full-width `PrimaryActionButton` close action with a trailing-aligned Material `TextButton` that reuses `R.string.close` and keeps the standard accessible touch target.
5. Review the diff for accidental changes to `PhotoGeotagging`, original-write confirmation/permission behavior, result statistics, or localized resources.

## Validation

Run:

```bash
./gradlew testDebugUnitTest
./gradlew :app:compileDebugKotlin
git diff --check
```

If an Android device is connected, install the debug build and verify:

1. Copy mode, permission not granted: system media-location permission appears before folder selection/write.
2. Deny: no copy is created, no write progress starts, and the existing localized permission message appears.
3. Grant: export continues and a written JPEG with GPS reports success after final URI validation.
4. Permission already granted: later exports do not prompt again.
5. Result Sheet: close action is compact and trailing-aligned in both success and failure result states.
6. Original mode: confirmation and media-location permission behavior remain unchanged.

## Risk and Review Gates

- Primary implementation file: `app/src/main/java/com/trackwrite/app/MainActivity.kt`.
- Do not edit `PhotoGeotagging.kt` unless implementation evidence disproves the permission root cause; return to planning first if that happens.
- Do not weaken GPS verification or treat a null GPS readback as success.
- Do not add a second permission launcher or duplicate permission-state logic.
- Do not replace the compact close action with a custom button; use Material `TextButton` and existing theme tokens.

## Rollback Point

Before verification, the entire implementation should be representable as two localized hunks in `MainActivity.kt`. If compile or device behavior shows the permission gate does not provide unredacted output URI reads, revert those hunks and return to Phase 1 to design a provider-aware verification strategy.
