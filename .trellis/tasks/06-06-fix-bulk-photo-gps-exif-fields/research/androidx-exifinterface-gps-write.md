# AndroidX ExifInterface GPS Write Research

## Question

How should TrackWrite write valid GPS EXIF fields for JPG/JPEG files using `androidx.exifinterface:exifinterface:1.4.1`?

## Sources

* Context7 lookup for `/androidx/androidx`: `androidx.exifinterface.media.ExifInterface setLatLong setAltitude saveAttributes GPSLatitudeRef GPSVersionID`.
* Project dependency: `app/build.gradle.kts` uses `androidx.exifinterface:exifinterface:1.4.1`.
* Project write path: `app/src/main/java/com/trackwrite/app/media/PhotoGeotagging.kt`.

## Findings

* AndroidX ExifInterface exposes GPS-specific APIs:
  * `setLatLong(double, double)`
  * `setAltitude(double)`
  * `setGpsInfo(android.location.Location?)`
  * `saveAttributes()`
  * `getLatLong()` and `getAltitude(double)` for read-back checks
* AndroidX exposes GPS tag constants including:
  * `TAG_GPS_LATITUDE`
  * `TAG_GPS_LATITUDE_REF`
  * `TAG_GPS_LONGITUDE`
  * `TAG_GPS_LONGITUDE_REF`
  * `TAG_GPS_ALTITUDE`
  * `TAG_GPS_ALTITUDE_REF`
  * `TAG_GPS_VERSION_ID`
* Current code already calls `setLatLong(...)`, but manually writes altitude as a rational string and ref. The user reports that a third-party EXIF viewer shows invalid/blank GPS tags for JPG/JPEG output.
* AndroidX 1.4.x includes `setAltitude(double)`, so altitude should be written through the API rather than handwritten tag strings.
* The fix should verify saved metadata by reopening the written file with `ExifInterface`, because external-tool-visible failures happen after `saveAttributes()` and file rewrite.

## Repo Constraints

* GPS/photo matching and EXIF write coordinates must stay WGS84.
* `ExifInterface.saveAttributes()` may only be called for JPEG, PNG, and WebP; RAW/NEF must continue to fail early.
* Bulk photo import and write I/O must remain off the main thread.
* Per-photo write outcomes must be preserved.

## Recommended Implementation Direction

* Keep the current temp-file write structure unless it proves to corrupt saved metadata.
* Use AndroidX GPS APIs for coordinate and altitude writes.
* Avoid writing altitude tags at all when `GeoPoint.altitudeMeters` is absent.
* Add a small verification helper/test that writes GPS to a local JPEG temp file, saves, reopens, and checks:
  * `getLatLong()` returns the expected coordinate.
  * `TAG_GPS_LATITUDE_REF` and `TAG_GPS_LONGITUDE_REF` match coordinate signs.
  * `TAG_GPS_VERSION_ID` is not blank or `0.0.0.0` for newly written GPS data.
  * altitude read-back works only when altitude was supplied.
