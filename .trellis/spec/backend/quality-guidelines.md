# Quality Guidelines

> Code quality standards for backend development.

---

## Overview

TrackWrite starts from a Kotlin Android app with a pure JVM-testable domain
layer. Preserve that separation: domain behavior should be deterministic,
framework-free, and covered by local unit tests before it is wired into Android
UI, services, storage, MediaStore, EXIF, or AMap.

---

## Forbidden Patterns

- Do not put Android framework types (`Context`, `Uri`, `Activity`, `Service`,
  SDK map/location classes) in `com.trackwrite.app.domain`.
- Do not hardcode AMap keys in Kotlin, XML, or checked-in Gradle files. Use the
  `TRACKWRITE_AMAP_API_KEY`, `TRACKWRITE_AMAP_WEB_KEY`, and
  `TRACKWRITE_AMAP_SECURITY_JS_CODE` Gradle/local properties through manifest
  placeholders.
- Do not parse user-supplied GPX XML with external entity or external DTD loading
  enabled.
- Do not use an AMap Android SDK key for Web JS search/map-tap binding, and do
  not use a Web JS key for Android SDK integration. Keep key types separate.
- Do not pass AMap/GCJ-02 coordinates into domain, GPX, matching, or EXIF write
  paths. Convert provider coordinates to WGS84 at the map integration boundary.

---

## Required Patterns

- Gradle uses Android application id `com.trackwrite.app`, `minSdk = 31`, and
  `compileSdk = 34` until deliberately changed.
- Local SDK paths belong in `local.properties`, which must remain ignored.
- Use Java/Kotlin target 17 for Android builds.
- Photo matching defaults must stay aligned with the PRD: camera offset `0`,
  max time difference `5 minutes`, and start/end endpoint fallback enabled.
- GPX import/export and domain matching must operate on WGS84 coordinates.
- Active recording uses a foreground service with `foregroundServiceType="location"`
  and an ongoing notification. Recovery scope is app/process recovery only;
  reboot and user force-stop auto-resume are out of scope for the MVP baseline.
- Track history persists through Room. If legacy `tracks.json` exists, migrate it
  once into `trackwrite.db` and keep the old file as `tracks.migrated.json`.
- Photo EXIF writes must be reported per photo. Exported copies are the safer
  default; in-place original mutation is a separate confirmed action and may fail
  if Android write grants are unavailable.
- Manual photo fallback opens a dedicated location picker. It uses
  `TRACKWRITE_AMAP_WEB_KEY` plus `TRACKWRITE_AMAP_SECURITY_JS_CODE` for AMap JS
  search/map-tap binding when configured, and still supports direct WGS84
  coordinate entry when no web key is available. Do not hardcode Android SDK
  keys, Web JS keys, or Web JS security codes in source.

## Scenario: Manual Location Picker AMap Boundary

### 1. Scope / Trigger
- Trigger: Manual photo fallback crosses Android UI, manifest placeholders,
  WebView/AMap JS integration, coordinate conversion, and EXIF write inputs.

### 2. Signatures
- Manifest placeholder: `amapWebKey` is populated from Gradle property
  `TRACKWRITE_AMAP_WEB_KEY`.
- Manifest placeholder: `amapSecurityJsCode` is populated from Gradle property
  `TRACKWRITE_AMAP_SECURITY_JS_CODE`.
- Manifest metadata key: `com.trackwrite.amap.web_key`.
- Manifest metadata key: `com.trackwrite.amap.security_js_code`.
- Activity result fields from `ManualLocationActivity`:
  - `EXTRA_LATITUDE`: `Double`, WGS84 latitude in `-90.0..90.0`.
  - `EXTRA_LONGITUDE`: `Double`, WGS84 longitude in `-180.0..180.0`.
  - `EXTRA_LABEL`: optional display label for logs/review.
- AMap WebView bridge input: `TrackWrite.select(latitude, longitude, label)`
  receives AMap/GCJ-02 coordinates from map taps or place-search results.

### 3. Contracts
- `TRACKWRITE_AMAP_WEB_KEY` is optional. When blank, the picker hides/disables
  AMap search/map UI and keeps direct WGS84 coordinate entry available.
- A configured `TRACKWRITE_AMAP_WEB_KEY` should be paired with
  `TRACKWRITE_AMAP_SECURITY_JS_CODE` for JSAPI 2.0 initialization. If AMap
  rejects the key/security pair, the picker must surface the JSAPI/search error
  instead of collapsing every failure to "No results."
- WebView AMap search/map-tap binding uses only `TRACKWRITE_AMAP_WEB_KEY`.
  It must not fall back to `TRACKWRITE_AMAP_API_KEY`.
- `ManualLocationActivity` converts AMap/GCJ-02 coordinates to WGS84 before it
  returns activity-result extras to `MainActivity`.
- Direct typed coordinates are treated as WGS84 and validated through
  `GeoPoint` before being attached to a `PhotoCandidate`.
- Manual location binding and clearing operate on one selected photo index at a
  time; batch clearing is a separate explicit behavior if added later.

### 4. Validation & Error Matrix
- Missing web key -> show coordinate-entry fallback; search/map WebView is not
  exposed as an available action.
- Missing/invalid JS security code with a Web key -> show the AMap JS/search
  error in the picker so the configuration can be fixed.
- Non-numeric typed latitude/longitude -> picker displays a validation message
  and does not return a result.
- Returned latitude/longitude is NaN -> caller logs invalid manual result and
  leaves photos unchanged.
- Returned coordinate is outside `GeoPoint` bounds -> caller logs the
  validation message and leaves photos unchanged.
- Photo index is missing or out of range -> caller logs an index error and does
  not open/bind/clear a manual location.

### 5. Good/Base/Bad Cases
- Good: Web key configured, user picks an AMap search result, result is
  converted to WGS84, and only the target photo gets a manual location.
- Base: Web key blank, user types WGS84 coordinates, and the same result payload
  path attaches the manual location.
- Bad: Web key blank but search appears enabled, or GCJ-02 coordinates are
  written directly to EXIF as if they were WGS84.

### 6. Tests Required
- JVM tests for `AmapCoordinateConverter.gcj02ToWgs84` covering a China
  coordinate conversion and an outside-China no-op.
- Compile/lint checks for manifest placeholders, activity registration, and
  WebView picker code.
- If binding/clearing logic moves out of `MainActivity`, add unit tests for
  single-photo target updates and out-of-range index handling.

### 7. Wrong vs Correct

#### Wrong
```kotlin
// AMap JS result is GCJ-02; writing it directly drifts EXIF coordinates.
photo.copy(manualLocation = GeoPoint(amapLatitude, amapLongitude))
```

#### Correct
```kotlin
val wgs84 = AmapCoordinateConverter.gcj02ToWgs84(amapLatitude, amapLongitude)
photo.copy(manualLocation = GeoPoint(wgs84.latitude, wgs84.longitude))
```

---

## Testing Requirements

- Add JVM unit tests for new or changed domain algorithms.
- Photo matching tests must cover exact point hits, interpolation, camera offset,
  endpoint fallback, max-time-difference rejection, and empty/out-of-range cases
  when those behaviors change.
- GPX tests must cover at least round-trip point coordinates, altitude, time, and
  track name behavior when codec behavior changes.
- Run these checks before reporting implementation complete:

```bash
./gradlew testDebugUnitTest
./gradlew :app:compileDebugKotlin
./gradlew :app:lintDebug
```

If the Gradle wrapper is not yet downloaded in the local sandbox, the same tasks
may be run with an already-installed Gradle 8.12 binary.

---

## Code Review Checklist

- Domain package has no Android framework or provider SDK dependency.
- GPS/photo matching defaults match the PRD.
- User file parsing is defensive and does not enable XML external entities.
- AMap key handling uses placeholders/local properties, not source-controlled
  secrets.
- AMap Web picker output is WGS84 before it reaches `PhotoCandidate`,
  `PhotoMatchResult`, or EXIF writing.
- Unit tests cover every new domain behavior.
