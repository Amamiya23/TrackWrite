# Fix bulk photo location crash

## Goal

Prevent the app from freezing or crashing when a user selects and writes location metadata for a large batch of photos, specifically around 80 photos selected at once.

## What I already know

* User reported: selecting 80 photos at once and writing location information causes the app to crash.
* The app is an Android/Kotlin/Compose project focused on GPS tracks and photo geotagging.
* Photo selection uses `ActivityResultContracts.OpenMultipleDocuments()` and immediately loads EXIF metadata in `MainActivity`.
* Folder photo import also loads all candidate photos synchronously.
* Writing copies and writing originals call `PhotoGeotagging.exportCopies()` / `writeInPlace()` synchronously from `MainActivity`.
* `PhotoGeotagging.writeGps()` copies the full source photo to a cache file, saves EXIF, then copies the full file back to the target URI, which is expensive for large batches.
* Follow-up report: JPG files write successfully, but NEF RAW files fail with `ExifInterface only supports saving attributes for JPEG, PNG, and WebP formats`.

## Assumptions

* The reported crash is likely caused by heavy photo/EXIF I/O blocking the main thread long enough to trigger ANR or process death, rather than a single malformed photo.
* The fix should keep current write behavior and result reporting, not redesign the export workflow.

## Requirements

* Move bulk photo loading and location writing off the main thread.
* Prevent users from launching overlapping photo loads or writes while one is already running.
* Keep existing write result behavior: written/skipped/failed counts should still be shown.
* Preserve current support for writing copies and originals.
* Detect formats unsupported by AndroidX `ExifInterface.saveAttributes()` before copying exported files or mutating originals.
* Report RAW/NEF files with a clear unsupported-format result instead of surfacing the low-level `ExifInterface` exception.
* Keep changes narrow to the photo batch crash path.

## Acceptance Criteria

* [x] Selecting a large batch such as 80 photos does not perform EXIF loading on the main thread.
* [x] Writing locations for a large batch such as 80 photos does not perform full-photo copy/write work on the main thread.
* [x] Repeated taps during an active bulk operation are ignored or disabled.
* [x] Existing photo match and write result UI continues to compile.
* [x] Project Kotlin compilation passes.
* [x] NEF/RAW files are rejected before EXIF save/copy mutation, while JPEG/PNG/WebP remain writable.

## Definition of Done

* Tests or compile checks run where practical.
* Lint/typecheck/compile status reported.
* Specs reviewed and updated only if this introduces a reusable project convention.

## Out of Scope

* Per-photo progress bars.
* A full work manager/background notification redesign.
* Changing EXIF matching logic or coordinate conversion.
* Rewriting image thumbnail loading.

## Technical Notes

* Relevant files inspected:
  * `app/src/main/java/com/trackwrite/app/MainActivity.kt`
  * `app/src/main/java/com/trackwrite/app/media/PhotoGeotagging.kt`
  * `app/build.gradle.kts`
* The project already depends on `kotlinx-coroutines-android` and imports `Dispatchers` / `withContext` in `MainActivity.kt`.

## Verification

* `./gradlew :app:compileDebugKotlin` passed.
* `./gradlew testDebugUnitTest` passed.
* `./gradlew :app:lintDebug` passed.
* Follow-up RAW/NEF fix: `./gradlew :app:compileDebugKotlin`, `./gradlew testDebugUnitTest`, and `./gradlew :app:lintDebug` passed.
