# AndroidX ExifInterface GPS Version Verification

## Question

How should TrackWrite handle `TAG_GPS_VERSION_ID` verification when JPEG GPS writes otherwise verify correctly?

## Sources

* Context7 library lookup for `AndroidX ExifInterface`: selected `/androidx/androidx` as the best match because it is the high-reputation AndroidX source package.
* Context7 docs for `/androidx/androidx`: confirmed `ExifInterface` exposes `saveAttributes()` for persisting modified EXIF attributes and utility APIs around supported MIME types.
* Context7 docs for `/websites/developer_android_reference_kotlin_androidx`: did not return a useful `ExifInterface` GPSVersionID-specific reference.
* Local dependency: `app/build.gradle.kts` uses `androidx.exifinterface:exifinterface:1.4.1`.
* Local class inspection: `javap` confirmed `TAG_GPS_VERSION_ID`, `getAttributeBytes(String)`, `setLatLong(double, double)`, `setAltitude(double)`, and `saveAttributes()` exist in the bundled `exifinterface-1.4.1` classes.

## Findings

* Project code already writes the GPS version tag through `setAttribute(TAG_GPS_VERSION_ID, "2,3,0,0")`.
* AndroidX `setLatLong(...)` writes the coordinate rationals plus latitude/longitude refs.
* AndroidX `setAltitude(...)` writes altitude plus altitude ref using the supported API.
* `getAttributeBytes(...)` returns raw stored bytes for a tag if that tag is present, but the docs found through Context7 do not state that GPS version byte read-back must be stable across all JPEG inputs.
* The current verifier fails the whole photo write when the version byte assertion fails, even when the location fields that users rely on are correct.

## Recommended Direction

Keep setting `TAG_GPS_VERSION_ID` to `2.3.0.0`, but do not make the post-write verifier fail on GPS version read-back. Treat coordinate/ref/altitude verification as the blocking safety check.
