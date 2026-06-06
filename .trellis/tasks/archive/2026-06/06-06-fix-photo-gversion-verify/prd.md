# Fix Photo GPS Version Verification Failure

## Goal

When writing GPS metadata to JPEG photos, TrackWrite should not fail the photo write solely because AndroidX `ExifInterface` does not read back `TAG_GPS_VERSION_ID` in the exact byte form expected by our verifier.

## What I Already Know

* User reported that writing a photo shows `DSC_1417.JPG: Written Gversion did not verify.`
* The current failure originates from `PhotoGeotagging.verifyWrittenGps()` requiring `TAG_GPS_VERSION_ID` bytes to equal `2,3,0,0`.
* The write path still calls `writeGpsAttributes(...)`, which sets `TAG_GPS_VERSION_ID` to EXIF GPS version `2.3.0.0`.
* AndroidX `ExifInterface.setLatLong(...)`, `setAltitude(...)`, and `saveAttributes()` are the supported write path used by the project.

## Requirements

* Keep writing `TAG_GPS_VERSION_ID` with GPS EXIF version `2.3.0.0`.
* Keep strict verification for user-visible GPS data:
  * latitude/longitude can be read back and match the requested position within tolerance,
  * latitude/longitude refs match coordinate signs,
  * altitude and altitude ref match when altitude exists,
  * altitude tags are absent when altitude is absent.
* Do not fail an otherwise successful JPEG write only because `TAG_GPS_VERSION_ID` is missing or returned in a different byte representation.
* Preserve per-photo failure reporting for real write failures.

## Acceptance Criteria

* [x] The reported `Written GPS version did not verify.` failure no longer blocks writing `DSC_1417.JPG`-style JPEG photos when coordinates verify.
* [x] GPS coordinates and refs are still verified after JPEG writes.
* [x] Existing MIME/RAW format handling remains unchanged.
* [x] Unit tests cover the GPS version verification policy.
* [x] `./gradlew testDebugUnitTest` passes.
* [x] `./gradlew :app:compileDebugKotlin` passes.

## Definition of Done

* Tests added or updated for changed behavior.
* Relevant Gradle checks pass, or any inability to run them is reported.
* Specs are reviewed for whether the GPS verification contract needs a small update.

## Technical Approach

Relax only the `TAG_GPS_VERSION_ID` read-back assertion. Keep writing the tag, and keep strict coordinate/ref/altitude verification because those fields determine whether the resulting photo is actually geotagged correctly.

## Decision (ADR-lite)

**Context**: AndroidX `ExifInterface` supports generic EXIF attribute writes and exposes `getAttributeBytes(...)`, but byte-form read-back for GPS version is not the user-facing success criterion. The current verifier turns a non-critical compatibility detail into a failed photo write.

**Decision**: Treat GPS version read-back as best-effort. Continue writing it, but make post-write verification block only on coordinates, refs, and altitude correctness.

**Consequences**: TrackWrite avoids false negative write failures while preserving the meaningful safety checks for location metadata. External tools may still show GPS version according to their own parser behavior, but coordinate correctness remains enforced.

## Out of Scope

* Changing the AndroidX ExifInterface dependency version.
* Adding a custom EXIF/TIFF writer.
* Broad UI copy changes for write result messages.

## Technical Notes

* Main file: `app/src/main/java/com/trackwrite/app/media/PhotoGeotagging.kt`.
* Existing tests: `app/src/test/java/com/trackwrite/app/media/PhotoGeotaggingTest.kt`.
* Related archived research: `.trellis/tasks/archive/2026-06/06-06-fix-bulk-photo-gps-exif-fields/research/androidx-exifinterface-gps-write.md`.

## Research References

* [`research/androidx-exifinterface-gps-version.md`](research/androidx-exifinterface-gps-version.md) - AndroidX docs and local implementation notes for GPS version verification.
