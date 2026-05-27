# TrackWrite Android MVP

## Goal

Build TrackWrite, an Android app for photographers to record or import GPS tracks, manage historical tracks, and write matched GPS location metadata into photos based on EXIF capture time. The MVP should make track recording reliable and transparent, support GPX export/sharing, support photo-to-track geotagging with configurable matching rules, and allow manual location fallback for photos that cannot be matched automatically.

## What I Already Know

* The product is an Android app named TrackWrite.
* Target users are photographers who need to geotag photos using GPS tracks.
* The repository currently has Trellis configuration/specs but no Android application source tree yet.
* The app must support in-phone GPS track recording with pause, resume, stop, crash/process-restart recovery, visible recording state, and reasonable power usage.
* The app must support historical track management, including list view, per-track stats, GPX export, system share, rename, and delete.
* The app must support batch photo selection from Android media, or selecting a folder, then write location into photos by matching EXIF capture time against track point times.
* Matching must support camera time offset, maximum allowed time difference, endpoint fallback before/after track bounds, exact point matches, linear interpolation between points, and unmatched marking when outside the allowed time difference.
* Manual location fallback must support per-photo location setting from a preview list, map selection page, place search, result selection, direct map tap, confirm binding, and clearing manual location.
* The MVP technical direction is Native Kotlin Android with Jetpack Compose and AMap (高德地图) for map/search/location provider integration.
* AMap integration docs are available at `docs/maps.md` and should be consulted during implementation.
* Android `applicationId` should be `com.trackwrite.app`.
* Default recording mode should be balanced: roughly every 5 seconds or after meaningful movement.
* Default photo matching should use camera time offset `0`, maximum allowed time difference `5 minutes`, and endpoint fallback enabled before/after the track bounds.

## Assumptions (Temporary)

* MVP targets Android 12+.
* Track storage can be local-only for the MVP; no account or cloud sync is assumed.
* GPX import is included in the MVP so users can geotag photos from externally recorded tracks.
* Photo metadata writing must support both original-preserving copy/export and explicit in-place original modification.
* In-progress recording recovery targets app close/process death recovery; device reboot/user force-stop automatic continuation is out of scope for the MVP.
* AMap key should be provided through local build configuration or manifest placeholders, not hardcoded into source-controlled app code.
* Internal track/photo geotag coordinates should be stored and written as WGS84; AMap display/search/selection coordinates should be converted at provider boundaries when needed.

## Open Questions

* Final MVP scope confirmation before implementation.

## Requirements (Evolving)

### Track Recording

* Record GPS track points on device.
* Support pause, resume, and stop.
* Recover an in-progress recording after normal app close, process death, or abnormal interruption.
* Do not require automatic continuation after device reboot or user force-stop in the MVP.
* Show current recording state clearly, including whether recording is active, paused, or stopped.
* Use a power-conscious location strategy suitable for long-running recording.
* Default to a balanced recording preset: record a point roughly every 5 seconds or after meaningful movement.

### Track Management

* Show historical track list.
* Show each track's start time, duration, point count, distance, and average speed.
* Import GPX files as historical tracks.
* Export a historical track as GPX.
* Share exported GPX through Android system sharing.
* Rename a track.
* Delete a track.

### Photo Geotagging

* Batch select photos from Android media.
* Select a folder as a photo source.
* Use each photo's EXIF capture time as the matching baseline.
* Support camera time calibration offset.
* Support configurable maximum allowed time difference.
* Default camera time offset to `0`.
* Default maximum allowed time difference to `5 minutes`.
* Enable track-start and track-end fallback by default.
* If photo time is before track start, optionally use the track start point.
* If photo time is after track end, optionally use the track end point.
* If photo time exactly matches a track point timestamp, use that point directly.
* If photo time falls between two track points, linearly interpolate location.
* If the nearest usable point/interpolation exceeds the maximum allowed time difference, mark the photo as unmatched.
* Write matched GPS information into the photo EXIF metadata.
* Support exporting geotagged copies so originals are preserved.
* Support explicit in-place original modification after user confirmation/write permission.

### Manual Location Fallback

* In the photo preview list, allow setting location for an individual photo.
* Open a map point-selection page.
* Search for places using AMap search capabilities.
* Select a location from search results.
* Select a location by tapping directly on the map.
* Confirm the selected location and bind it to the target photo.
* Clear a manually assigned location.

## Acceptance Criteria (Evolving)

* [ ] A user can start, pause, resume, stop, and inspect the state of a GPS recording.
* [ ] An active recording can continue or be recovered after the app is closed/reopened or interrupted.
* [ ] MVP recovery behavior is documented as process/app recovery only, excluding reboot and user force-stop auto-resume.
* [ ] Historical tracks show start time, duration, point count, distance, and average speed.
* [ ] A user can import a GPX file and use it as a historical track for photo matching.
* [ ] A historical track can be exported and shared as GPX.
* [ ] Tracks can be renamed and deleted.
* [ ] Photos can be selected in batch from media and from a folder source.
* [ ] Photo EXIF capture time plus camera offset is used for track matching.
* [ ] Matching handles endpoint fallback, exact point hit, interpolation, and unmatched states according to the configured maximum time difference.
* [ ] Matching defaults are offset `0`, max difference `5 minutes`, and endpoint fallback enabled.
* [ ] Matched or manually assigned GPS locations can be written to photo EXIF either as exported copies or by explicit original in-place modification.
* [ ] Manual location assignment supports map search, search result selection, map tap selection, confirmation, and clearing.

## Definition of Done

* Tests added/updated where practical for matching, GPX import/export, statistics, and persistence behavior.
* Lint/typecheck/build pass for the Android project.
* Permission, storage, and background location behavior are handled explicitly.
* Docs/notes updated if architecture or behavior decisions are made.
* Rollback/data-safety considered for photo EXIF writes.

## Out of Scope (Explicit, Draft)

* Cloud sync, user accounts, and multi-device history sync.
* Social sharing beyond Android's standard share sheet.
* Advanced route editing.
* Non-Android platforms.
* RAW sidecar support unless explicitly added later.
* Automatic recording continuation after device reboot or user force-stop.

## Technical Notes

* Task directory: `.trellis/tasks/05-26-trackwrite-android-mvp/`.
* No Android source files are present yet; this task likely includes bootstrapping the app project.
* Relevant future research topics: Android location/background recording, Android storage/MediaStore EXIF write constraints, GPX parsing/export, photo folder selection UX.
* AMap docs: `docs/maps.md`; current local docs describe the Android lightweight map SDK, search SDK, location SDK, manifest key metadata, permissions, WebView-backed map setup, location blue dot, overlays, and ProGuard rules.
* Product design context is captured in `PRODUCT.md`.

## Design Context

### Register

* Product UI. TrackWrite is a task-focused Android tool; design serves recording, matching, file safety, and review workflows.

### Users and Primary Scenario

* Photography users record GPS tracks before/during shooting, then later batch-match photos and write location metadata.
* The design should also account for long-duration travel recording, battery-conscious use, and historical track browsing.

### Brand Personality

* Reliable, light, quiet.
* The interface should feel calm and capable, not heavy, competitive, or overly technical.

### Design Principles

* Show recording truth: active/paused/recovered state, duration, point count, and recording confidence must be visible.
* Make destructive work explicit: exported copies and original in-place writes must be clearly differentiated.
* Keep photos and tracks in the foreground: timestamps, thumbnails, map points, and match status should carry the experience.
* Prefer calm confidence over drama: restrained hierarchy, clear copy, predictable controls.
* Design for interrupted field use: outdoor legibility, low-battery awareness, and process recovery states matter.

### Anti-references and Accessibility

* Avoid cyberpunk dashboards, dark neon telemetry walls, fitness/social tracking aesthetics, generic card grids, decorative gradients, and glassmorphism.
* Target WCAG AA contrast. Do not rely on color alone for status. Keep focus states, touch targets, permission flows, and reduced-motion behavior clear.

## Research References

* [`research/android-location-recording.md`](research/android-location-recording.md) — Android GPS recording should use a user-visible foreground service with `location` type; process death can be partially recovered via started-service redelivery semantics, but force-stop/reboot need explicit scope decisions.
* [`research/android-exif-storage.md`](research/android-exif-storage.md) — Android 10+ original EXIF mutation requires user-mediated MediaStore/SAF write grants; a copy/export flow using `IS_PENDING` is safer for original-preserving batch writes.
* [`research/map-search-providers.md`](research/map-search-providers.md) — external provider comparison; user chose AMap for the MVP because China-oriented map/search support is first-class for this project.

## Research Notes

### Chosen MVP Technical Direction

* Build a native Kotlin Android app with Jetpack Compose.
* Use `com.trackwrite.app` as the Android `applicationId`.
* Set the minimum supported Android version to Android 12+.
* Use Room/local storage for tracks and recording state.
* Use a foreground service for active GPS recording.
* Use AndroidX ExifInterface plus MediaStore/SAF for reading and writing photo metadata.
* Use AMap (高德地图) for manual map selection, place search, selected-point display, and location SDK integration where appropriate.
* Configure AMap through manifest placeholders/local properties and keep test/production keys out of source-controlled Kotlin/XML where practical.
* Keep a clear coordinate boundary: WGS84 for persisted tracks, GPX, matching, and EXIF writes; AMap/provider coordinates only inside map/search/location integration adapters.

### Storage/EXIF Safety Baseline

* Treat each photo write as fallible and report per-photo success/failure.
* Support both original-preserving copy/export and explicit in-place original mutation.
* Make copy/export the safer default path in UX; require clear confirmation and Android write grants before in-place original mutation.
* Verify EXIF after saving for both write paths.

### Location Recording Baseline

* Use a foreground service with an ongoing notification for active recording.
* Persist in-progress recording state and points incrementally so app/process restart can resume or recover visible state.
* Scope recovery to app close/process death; reboot/user force-stop auto-continuation is out of scope for the MVP.
* Tune location update intervals/priority for a photography track use case rather than maximum-frequency navigation.

## Decision (ADR-lite)

**Context**: TrackWrite is Android-first and depends heavily on Android-specific platform capabilities: foreground location recording, scoped storage/MediaStore, SAF, EXIF metadata, and embedded map/search UX.

**Decision**: Use Native Kotlin Android with Jetpack Compose and AMap (高德地图). Implement AMap according to `docs/maps.md`; provide the AMap API key through local build configuration/manifest placeholders rather than hardcoding secrets in source.

**Consequences**: This optimizes for Android platform correctness and China-oriented map/search availability. It keeps the MVP focused on Android and does not optimize for future iOS reuse. AMap SDK integration, privacy/compliance switches, key/package/SHA1 binding, and provider-specific coordinate conversion must be handled explicitly during implementation.
