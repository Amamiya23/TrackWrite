# Safe Original Photo GPS Writes

## Goal

Make original-photo GPS writes reliable enough for real photo libraries by automatically saving backups in the app-managed backup folder, building and validating edited image bytes before replacing originals, replacing originals through an explicit truncating file descriptor path, and validating the original URI again after write-back. Keep original writes available for JPEG, PNG, and WebP.

## What I Already Know

* Current original writes copy the source URI into a temp file, call `ExifInterface.saveAttributes()`, then overwrite the source URI through `ContentResolver.openOutputStream(uri, "w")`.
* Corrupted images that render as colorful noise are consistent with damaged image bytes after overwrite, not bad GPS coordinate values.
* The user wants to keep PNG and WebP original writes rather than restricting originals to JPEG.
* Existing copy export writes into a SAF tree URI and already has an export folder launcher.
* Newly created SAF backup/export documents should not be forced through the original-replacement `rwt` path; use normal output streams for those non-destructive writes, then validate the created document.
* User feedback after the first implementation: backup location should default to an app-provided path so original writes do not ask for a folder every time.

## Requirements

* Original writes must use the app-managed default backup path before any source file is modified; users should not have to choose a backup folder for every write.
* Each original file must be copied to `Pictures/TrackWrite Backups` before its source URI is overwritten.
* GPS writes must first build an edited temp file and validate that temp file before replacing the source.
* Source replacement must avoid ambiguous `openOutputStream(uri, "w")`; use an explicit read/write/truncate descriptor path instead.
* After replacement, the app must reopen and validate the original URI before reporting success.
* JPEG, PNG, and WebP original writes must remain supported when AndroidX ExifInterface and the content provider allow them.
* UI copy must communicate that original writes modify source files, create app-managed backups, and may skip unsupported/provider-blocked files.
* Post-write GPS validation must handle Android photo-location metadata redaction by requesting media-location permission and reading unredacted MediaStore streams when available.

## Acceptance Criteria

* [x] Choosing "write originals" uses the app-managed default backup path and does not prompt for a backup folder each time.
* [x] For every writable original, the app creates a backup copy in `Pictures/TrackWrite Backups` before replacing the original.
* [x] Edited temp files are validated before write-back.
* [x] Original URIs are validated after write-back.
* [x] Write-back no longer uses `openOutputStream(uri, "w")` for original replacement.
* [x] JPEG, PNG, and WebP remain eligible for original writes.
* [x] Result reasons explain backup, validation, unsupported format, and provider-write failures.
* [x] MediaStore-backed original GPS validation uses media-location permission plus unredacted readback to avoid false "GPS missing" failures.

## Definition of Done

* Tests added/updated where feasible for pure helper behavior.
* Android compile/test command run or failure documented.
* UI strings updated in default and zh-rCN resources.
* Existing unrelated dirty work is not reverted.

## Out of Scope

* Adding a user-configurable backup folder setting.
* Full rollback from backup if post-write validation fails.
* Supporting RAW/HEIC original EXIF writes.

## Technical Notes

* Main files expected to change:
  * `app/src/main/java/com/trackwrite/app/media/PhotoGeotagging.kt`
  * `app/src/main/java/com/trackwrite/app/MainActivity.kt`
  * `app/src/main/res/values/strings.xml`
  * `app/src/main/res/values-zh-rCN/strings.xml`
* Prefer scoped changes. Do not refactor unrelated UI or settings code.
* Follow-up bug: after the first safe-write implementation, a real batch write reported `写入后原图未通过校验：Could not verify written GPS coordinates.` The likely root cause is Android media-location redaction hiding GPS EXIF from normal reads after write-back. Fix: request `ACCESS_MEDIA_LOCATION` before original writes and use `MediaStore.setRequireOriginal(uri)` for post-write GPS validation.
* Follow-up UX change: original backups now use the app-managed MediaStore path `Pictures/TrackWrite Backups` instead of asking the user to choose a SAF folder for every original-write batch.

## Verification

* `./gradlew :app:compileDebugKotlin` passed after the media-location readback fix.
* `git diff --check` passed.
* After final review fixes, `git diff --check` and `./gradlew :app:compileDebugKotlin` passed again.
* After switching original backups to the app-managed MediaStore path, `git diff --check` and `./gradlew :app:compileDebugKotlin` passed again.
* `./gradlew testDebugUnitTest` and `./gradlew :app:lintDebug` could not run inside the sandbox because Gradle wrapper access to the user-level `.gradle` lock file hit a read-only filesystem. Escalated retries were blocked by the approval service returning 503, so these checks remain pending.
