# Fix Bulk Photo GPS EXIF Fields

## Goal

Ensure photo batch geotagging writes valid, standard GPS EXIF metadata that can be read by external EXIF inspection tools, including latitude, longitude, coordinate refs, GPS version, and altitude when available.

## What I Already Know

* User reports that after writing track locations to photos in bulk, an EXIF viewer shows empty or invalid GPS fields:
  * `GPSLatitude` / `GPSLongitude` appear blank.
  * `GPSLatitudeRef` / `GPSLongitudeRef` show `Unknown ()`.
  * `GPSAltitude` shows `undef`.
  * `GPSAltitudeRef` shows `Above Sea Level`.
  * `GPSVersionID` shows `0.0.0.0`.
* The current GPS write path is `PhotoGeotagging.writeGps(...)` in `app/src/main/java/com/trackwrite/app/media/PhotoGeotagging.kt`.
* Current code copies the source `Uri` to a temp file, opens `ExifInterface(temp)`, calls `setLatLong(latitude, longitude)`, manually sets `TAG_GPS_ALTITUDE` and `TAG_GPS_ALTITUDE_REF` when altitude exists, saves, then copies the temp file back.
* The project uses `androidx.exifinterface:exifinterface:1.4.1`.
* AndroidX ExifInterface 1.4.x provides `setLatLong(double, double)`, `setAltitude(double)`, `setGpsInfo(Location?)`, and `saveAttributes()`.
* Existing backend spec requires GPS/photo data to remain WGS84 and EXIF writes to run off the main thread.

## Assumptions

* Confirmed by user: the inspected files are JPG/JPEG, not RAW/NEF files rejected by the prior unsupported-format fix.
* Follow-up test result: lowercase `.jpg` files show written location correctly, but uppercase `.JPG` files still do not show location in the EXIF viewer.
* The broken fields are caused by incomplete or non-standard GPS tag writing, not by the matching algorithm selecting the wrong coordinate.
* Fixing the EXIF write helper and adding verification coverage is sufficient; no UI redesign is needed.

## Requirements

* Write GPS latitude and longitude in a way external EXIF tools recognize as valid numeric coordinates.
* Fix the issue for JPG/JPEG files specifically, while preserving existing PNG/WebP support behavior.
* Treat uppercase and lowercase JPEG extensions equivalently; `.JPG` / `.JPEG` must write and verify the same as `.jpg` / `.jpeg`.
* Ensure latitude and longitude reference tags are valid (`N`/`S` and `E`/`W`) after save.
* Ensure GPS version metadata is valid instead of `0.0.0.0` when location data is written.
* Use AndroidX ExifInterface GPS APIs where possible instead of handcrafting fragile GPS tag values.
* Write altitude through the AndroidX altitude API when altitude is present, preserving above/below sea level semantics.
* Do not write a misleading `GPSAltitudeRef` or undefined altitude value when the matched/manual location has no altitude.
* Preserve existing per-photo outcomes and unsupported format handling.
* Keep EXIF file work on the existing background/bulk write path.

## Acceptance Criteria

* [ ] A geotagged JPEG can be read back with AndroidX `ExifInterface.getLatLong()` and returns the written coordinate.
* [ ] Uppercase `.JPG` / `.JPEG` files have the same valid GPS EXIF output as lowercase `.jpg` / `.jpeg` files.
* [ ] Saved EXIF contains valid `GPSLatitudeRef` and `GPSLongitudeRef` values matching coordinate signs.
* [ ] Saved EXIF does not leave `GPSVersionID` as `0.0.0.0` for newly written GPS data.
* [ ] When altitude is present, saved EXIF contains a readable altitude and correct altitude ref.
* [ ] When altitude is absent, saved EXIF does not create an undefined/misleading altitude field.
* [ ] Existing JPEG/PNG/WebP writable-format behavior and RAW rejection behavior remain intact.
* [ ] `./gradlew testDebugUnitTest` and `./gradlew :app:compileDebugKotlin` pass; run lint if touched code warrants it.

## Definition Of Done

* Code change is minimal and focused on EXIF GPS write correctness.
* Tests or validation cover read-back of written GPS fields where practical.
* Any new EXIF write convention learned from the fix is reflected in `.trellis/spec/backend/quality-guidelines.md` if reusable.
* Verification commands and any limitations are recorded before completion.

## Out Of Scope

* Changing photo-track matching rules.
* Changing AMap/manual-location coordinate conversion.
* Adding per-photo progress UI.
* Supporting EXIF writes for RAW formats unsupported by AndroidX `ExifInterface.saveAttributes()`.
* Reworking the export/in-place write UX.

## Technical Approach

* Inspect `PhotoGeotagging.writeGps(...)` and replace fragile GPS tag handling with AndroidX GPS APIs where appropriate.
* Add a narrow testable helper if needed so JVM tests can validate GPS tag decisions without Android storage dependencies.
* Prefer read-back verification through `ExifInterface` for a temporary JPEG test asset if feasible in local unit tests.
* Preserve current copy-to-temp and copy-back structure unless investigation proves it causes the invalid tags.

## Research References

* [`research/androidx-exifinterface-gps-write.md`](research/androidx-exifinterface-gps-write.md) — AndroidX 1.4.x GPS write APIs and TrackWrite-specific constraints for valid JPG/JPEG GPS EXIF output.

## Decision (ADR-lite)

**Context**: The app currently calls `setLatLong` but manually writes altitude tags and appears to produce GPS EXIF values that third-party tools treat as blank or unknown.

**Decision**: Treat EXIF GPS writing as an API-level operation: use AndroidX `setLatLong(...)` and `setAltitude(...)` where possible, and only set explicit GPS tags if required to produce standards-compliant metadata.

**Consequences**: This keeps the fix small and aligned with AndroidX behavior, but implementation must verify the actual saved file because some EXIF tool display issues only appear after `saveAttributes()` and file rewrite.

## Technical Notes

* Relevant files inspected:
  * `app/src/main/java/com/trackwrite/app/media/PhotoGeotagging.kt`
  * `app/src/test/java/com/trackwrite/app/media/PhotoGeotaggingTest.kt`
  * `.trellis/spec/backend/quality-guidelines.md`
  * `.trellis/tasks/06-06-fix-bulk-photo-location-crash/prd.md`
  * `app/build.gradle.kts`
* Documentation checked with Context7:
  * `/androidx/androidx` docs for `ExifInterface` GPS methods: `setLatLong`, `setAltitude`, `setGpsInfo`, `saveAttributes`, and GPS tag constants.
