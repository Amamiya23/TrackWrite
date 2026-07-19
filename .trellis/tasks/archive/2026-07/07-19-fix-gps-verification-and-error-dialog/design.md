# Technical Design

## Scope and Boundaries

This is one integrated bug fix with two small changes in the existing Android app:

1. `MainActivity` must gate copy export on the same runtime media-location permission already used by original-photo writes.
2. `WriteResultSheet` must render its close action as a compact, trailing Material action instead of a full-width primary button.

The storage implementation in `PhotoGeotagging` remains unchanged. Its preflight and final-URI validation are already the safety mechanism that detected the privacy-redacted readback.

## Root Cause

`PhotoGeotagging.exportCopies(...)` builds a geotagged temp image, writes it to the selected SAF output document, then calls `validateImageUri(...)`. GPS verification uses an input stream opened through `MediaStore.setRequireOriginal(uri)` when possible and otherwise falls back to the normal URI stream.

Android 10+ redacts sensitive EXIF location from shared-media reads unless the caller has `ACCESS_MEDIA_LOCATION`. The original-write flow requests this permission before entering the storage layer. The copy-export flow enters the same validation without requesting it. The output bytes can therefore contain GPS while `ExifInterface.getLatLong()` sees a redacted stream and returns null.

## Permission Flow

```text
User taps Write
  -> copy mode?
     -> requestMediaLocationPermissionThen
        -> granted/already granted: resolve or choose export folder
           -> writeCopies
              -> exportCopies
                 -> write output
                 -> strict final URI readback validation
        -> denied: existing localized permission message, no write starts
  -> original mode?
     -> existing confirmation
        -> existing requestMediaLocationPermissionThen
           -> existing writeOriginals flow
```

The permission gate belongs in `MainActivity`, not `PhotoGeotagging`, because runtime permission requests require an Activity/UI contract. Place the gate before directory selection so a rejected request does not open an unnecessary folder picker or create output state.

## Validation Contract

- Do not change `GPS_COORDINATE_VERIFY_TOLERANCE` or altitude tolerance.
- Do not catch and reinterpret `Could not verify written GPS coordinates.` as success.
- Do not replace final-URI verification with temp-file-only verification.
- Permission denial prevents the operation from starting and uses the existing `media_location_permission_required` feedback.
- Granted permission lets `openUnredactedInputStream(...)` retrieve the original bytes where the provider supports Android's MediaStore contract; provider/read/write failures remain per-photo failures.

## Result Sheet UI

Replace the full-width `PrimaryActionButton` in `WriteResultSheet` with a standard Material `TextButton` inside a full-width trailing-aligned row. The row owns alignment; the button sizes to its label and standard content padding. Compose Material's minimum interactive component sizing preserves an accessible touch target while the visible control no longer dominates the result summary.

No new color, shape, spacing, or string token is needed. This follows the existing AlertDialog action vocabulary and TrackWrite's quiet, content-forward product register.

## Compatibility and Trade-offs

- The first copy export after install or permission revocation adds a system permission prompt. The user explicitly approved this trade-off.
- If the user denies permission, copy export is blocked rather than weakening verification. This matches the existing original-write safety policy.
- The change is compatible with API 31+ (the project minimum) and reuses the already declared manifest permission and existing Activity Result launcher.
- SAF providers can still reject access or fail strict readback; those are real failures and remain visible.

## Rollback

Both changes are isolated to `MainActivity.kt`. Rollback is a small revert of the copy-mode permission wrapper and result-sheet action. No persisted state, schema, file format, or migration is introduced.
