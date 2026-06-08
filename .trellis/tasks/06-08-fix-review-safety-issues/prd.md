# Fix Code Review Safety Issues

## Goal

Fix the user-selected code review findings from the prior audit so recording, map selection, imports, service performance, and thumbnail loading behave safely under realistic failure and lifecycle conditions.

## What I Already Know

* The user asked to fix findings 1, 2, 4, 5, 6, and 8 from the review.
* Finding 1: deleting an active recording track leaves `RecordingStateStore.trackId` pointing at a deleted row, so the next service insert can violate the Room foreign key.
* Finding 2: `TrackingService` returns `START_REDELIVER_INTENT`, and a redelivered `ACTION_START` can create a new track instead of recovering the active recording.
* Finding 4: `ManualLocationActivity` exposes `TrackWrite` JavaScript bridge while using a permissive default `WebViewClient`.
* Finding 5: GPX import reads and DOM-parses the full file on the main thread with no size or point-count limit.
* Finding 6: `TrackingService.onLocationChanged` performs blocking repository work and reloads the whole track to count points.
* Finding 8: thumbnail decode failures are not caught and can crash the match UI.

## Assumptions

* The fix should be conservative and local to the existing Android/Kotlin architecture.
* No new third-party dependencies are required.
* Finding 3 from the review, original-photo rollback/backup verification, is intentionally out of scope because the user did not include it.

## Requirements

* Prevent deletion of the currently recording track from leaving the recording service in a broken state.
* Make service start/recovery idempotent so redelivered START commands do not create duplicate active tracks.
* Restrict the manual-location WebView to the expected AMap origin and remove the bridge before any disallowed navigation.
* Move GPX import off the main thread and reject oversized GPX content or excessive track-point counts before saving.
* Avoid whole-track reloads in the location callback when updating the foreground notification point count.
* Treat thumbnail decode failures as non-fatal and show the existing placeholder.

## Acceptance Criteria

* [x] Deleting the active recording track is blocked or safely stops/clears recording before deletion.
* [x] Redelivered `ACTION_START` while a recording is already active does not create a second track.
* [x] WebView navigation outside the expected AMap origin is blocked and cannot use `TrackWrite` bridge.
* [x] GPX import runs through an IO coroutine and enforces size and point-count limits.
* [x] Location callback notification updates do not query and materialize the full track.
* [x] Thumbnail decode exceptions do not escape the Compose coroutine.
* [x] Unit tests cover the new pure business limits/helpers where practical.
* [x] `./gradlew test` passes.

## Definition of Done

* Tests added or updated for changed behavior where practical.
* Project unit tests pass.
* Changes remain scoped to the requested findings.

## Out of Scope

* Original photo rollback and backup-byte verification.
* Android backup/data extraction policy.
* Permission continuation UX for first-time recording permission grant.

## Technical Notes

* Likely files: `TrackingService.kt`, `TrackRepository.kt`, `TrackDao.kt`, `MainActivity.kt`, `ManualLocationActivity.kt`, `GpxCodec.kt`, and targeted tests.
* The project is a single Android app module using Room, Compose, AndroidX ExifInterface, and JUnit tests.
