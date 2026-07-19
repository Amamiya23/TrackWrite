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
- GPS EXIF writes use AndroidX `ExifInterface.setLatLong(...)` and
  `setAltitude(...)` rather than hand-written latitude/altitude rationals. When
  GPS data is written, set `TAG_GPS_VERSION_ID` to EXIF GPS version `2.3.0.0`;
  when altitude is absent, remove both altitude tags instead of writing an
  undefined altitude/ref pair. Post-write verification should block on
  coordinate, coordinate-ref, and altitude correctness; `TAG_GPS_VERSION_ID`
  read-back differences are best-effort and must not fail an otherwise valid
  photo write.
- AndroidX `ExifInterface.saveAttributes()` must only be used for formats it can
  write: JPEG, PNG, and WebP. Treat JPEG MIME aliases such as `image/pjpeg`,
  `image/x-jpeg`, and `image/x-jpg` as canonical JPEG, and treat uppercase and
  lowercase JPEG extensions equivalently. RAW extensions such as NEF/DNG must
  still fail early even when a provider reports a misleading JPEG MIME type.
- Original photo mutation modifies the source `Uri` directly and does not create
  app-managed backups. Users who want separate files should use exported copies.
  Build and verify an edited temp file first, replace the source through an
  explicit truncating file-descriptor path such as
  `ContentResolver.openFileDescriptor(uri, "rwt")`, then reopen and validate the
  original `Uri` before reporting success. Do not use ambiguous
  `openOutputStream(uri, "w")` for original replacement. Keep the explicit `rwt`
  constraint scoped to destructive original replacement: newly created export
  files may use `openOutputStream(uri, "w")` because some providers support
  output streams for new files but not direct `rwt` descriptors. Any strict GPS
  read-back from MediaStore-backed photos, including newly created export copies,
  must request `ACCESS_MEDIA_LOCATION` before writing and prefer
  `MediaStore.setRequireOriginal(uri)` so Android's location privacy redaction
  does not make a successful write look like missing EXIF coordinates.
- Bulk photo import, EXIF reads, and EXIF writes must run off the Android main
  thread. Keep UI state changes on the main thread, but run `ContentResolver`,
  `DocumentFile`, full-photo copy, and `ExifInterface` work through
  `Dispatchers.IO` or an equivalent background worker.
- Manual photo fallback opens a dedicated location picker. It uses
  `TRACKWRITE_AMAP_WEB_KEY` plus `TRACKWRITE_AMAP_SECURITY_JS_CODE` for AMap JS
  search/map-tap binding. Do not hardcode Android SDK keys, Web JS keys, or Web
  JS security codes in source.

## Scenario: GitHub Release Authentication and Target Boundary

### 1. Scope / Trigger
- Trigger: an agent or developer builds a versioned APK and publishes it with
  `scripts/release-apk.sh` through GitHub CLI.

### 2. Signatures
- Build command: `scripts/build-apk.sh <version-name> --code <version-code>`.
- Release command:
  `scripts/release-apk.sh <version-name> --code <version-code> --target <ref>`.
- Authentication probe: `gh auth status -h github.com`.
- Release assets: `TrackWrite-<version-name>.apk` and
  `trackwrite-update.json`.

### 3. Contracts
- In Codex, run `gh auth status` and the release script outside the sandbox.
  Host GitHub credentials live under `~/.config/gh`; sandbox credential or
  network isolation can produce a false authentication failure.
- A sandboxed failure must be retried once outside the sandbox. Start
  `gh auth login` only when the host-level check also fails.
- Do not print or retrieve the raw token. Use `gh auth status` for validation.
- The release tag must point at source that is already available on GitHub.
  Push the relevant branch first, with explicit user authorization for shared
  branches such as `origin/main`.
- `--target` accepts a branch name or commit. Abbreviated local commit hashes
  are expanded to a full 40-character SHA before calling GitHub; a commit SHA
  must already exist on the remote.
- `trackwrite-update.json.apkAssetName` and `.sha256` must match the uploaded
  versioned APK exactly.

### 4. Validation & Error Matrix
- Sandboxed `gh auth status` fails -> retry outside sandbox; do not log in yet.
- Host-level `gh auth status` fails -> stop and authenticate with
  `gh auth login`.
- `Release.target_commitish is invalid` -> confirm the commit was pushed and
  retry with a branch or full 40-character SHA.
- Release asset already exists -> use `--clobber` only when replacement is the
  intended user action.
- Tracked files are dirty -> commit them, or use `--skip-dirty-check` only when
  the user explicitly accepts a release from uncommitted sources.
- Metadata hash differs from local APK -> treat the release as invalid and
  replace the mismatched assets before announcing success.

### 5. Good/Base/Bad Cases
- Good: host auth succeeds, `main` is pushed, the script targets the full HEAD
  SHA, and remote metadata matches the local APK hash.
- Base: a short local SHA is supplied; the script expands it to the full SHA
  and GitHub accepts the already-pushed commit.
- Bad: a sandbox auth failure triggers an unnecessary login flow and overwrites
  a working host credential.
- Bad: the APK is built from local commits but the Release tag targets an older
  remote default branch.

### 6. Tests Required
- Run `bash -n scripts/apk-common.sh scripts/build-apk.sh scripts/release-apk.sh`.
- Build a release APK and confirm the reported versioned path exists.
- Run host-level `gh auth status -h github.com` before publishing.
- After publishing, use `gh release view <version> --json assets,targetCommitish`
  and read back `trackwrite-update.json`.
- Compare the remote metadata SHA-256 with `sha256sum` of the local APK.

### 7. Wrong vs Correct

#### Wrong
```bash
# A sandbox failure is treated as authoritative and starts an unnecessary login.
gh auth status || gh auth login

# GitHub may reject the abbreviated SHA, or the commit may not exist remotely.
scripts/release-apk.sh v2.10 --code 210 --target b5d9cd2
```

#### Correct
```bash
# Run both commands outside the sandbox; only log in if the host check fails.
gh auth status -h github.com

git push origin main  # only after explicit user authorization
scripts/release-apk.sh v2.10 --code 210 \
  --target b5d9cd253611cd0ac5aaec54ca04bede983cb438
```

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
  AMap search/map UI and reports that map search is unavailable.
- A configured `TRACKWRITE_AMAP_WEB_KEY` should be paired with
  `TRACKWRITE_AMAP_SECURITY_JS_CODE` for JSAPI 2.0 initialization. If AMap
  rejects the key/security pair, the picker must surface the JSAPI/search error
  instead of collapsing every failure to "No results."
- WebView AMap search/map-tap binding uses only `TRACKWRITE_AMAP_WEB_KEY`.
  It must not fall back to `TRACKWRITE_AMAP_API_KEY`.
- `ManualLocationActivity` converts AMap/GCJ-02 coordinates to WGS84 before it
  returns activity-result extras to `MainActivity`.
- Manual location binding and clearing operate on one selected photo index at a
  time; batch clearing is a separate explicit behavior if added later.

### 4. Validation & Error Matrix
- Missing web key -> show unavailable state; search/map WebView is not exposed
  as an available action.
- Missing/invalid JS security code with a Web key -> show the AMap JS/search
  error in the picker so the configuration can be fixed.
- No selected map/search location -> picker displays a validation message and
  does not return a result.
- Returned latitude/longitude is NaN -> caller logs invalid manual result and
  leaves photos unchanged.
- Returned coordinate is outside `GeoPoint` bounds -> caller logs the
  validation message and leaves photos unchanged.
- Photo index is missing or out of range -> caller logs an index error and does
  not open/bind/clear a manual location.

### 5. Good/Base/Bad Cases
- Good: Web key configured, user picks an AMap search result, result is
  converted to WGS84, and only the target photo gets a manual location.
- Base: Web key configured, user taps the map directly, result is converted to
  WGS84 and returned through the same activity result payload.
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

## Scenario: Bulk Photo Storage Work

### 1. Scope / Trigger
- Trigger: loading or writing multiple photo `Uri`s, especially batches large
  enough to read or rewrite many EXIF payloads.
- Trigger: reporting a GPS write as successful only after reading the final
  shared-media `Uri` back through Android's privacy boundary.

### 2. Signatures
- UI entry: `MainActivity` receives `List<Uri>` from
  `ActivityResultContracts.OpenMultipleDocuments` or a folder `Uri` from
  `ActivityResultContracts.OpenDocumentTree`.
- Storage operations: `PhotoGeotagging.loadPhotos(...)`,
  `PhotoGeotagging.loadPhotosFromFolder(...)`,
  `PhotoGeotagging.exportCopies(...)`, and
  `PhotoGeotagging.writeInPlace(results, onProgress)`.
- Permission gate: `MainActivity.requestMediaLocationPermissionThen { ... }`
  must wrap both copy and original write entry points before storage work starts.

### 3. Contracts
- The Activity may update Compose state before and after the operation on the
  main thread.
- `ContentResolver` streams, `DocumentFile.listFiles`, full-file copy, and
  `ExifInterface` reads/writes must execute on `Dispatchers.IO` or an equivalent
  background worker.
- Before creating exported copies or mutating originals, storage code must check
  whether the source format is writable by `ExifInterface.saveAttributes()`.
  Treat only JPEG, PNG, and WebP as writable; do not send RAW formats such as
  NEF/DNG into `saveAttributes()`.
- Original mutation writes the selected source `Uri` directly. It must not
  create app-managed backup files; users who want separate files should use
  exported copies.
- Original replacement must use an explicit read/write/truncate descriptor path
  and then validate the source `Uri` again. A successful byte copy alone is not
  enough to report `Written`.
- The explicit `rwt` replacement contract applies only to destructive original
  replacement. Exported-copy files are newly created documents; write them
  through the provider's normal output stream, then validate the resulting
  document bytes.
- Post-write GPS validation on MediaStore-backed photo `Uri`s requires
  `ACCESS_MEDIA_LOCATION` and an unredacted stream from
  `MediaStore.setRequireOriginal(uri)`; otherwise Android may hide location EXIF
  and make a successful write look like missing GPS metadata.
- Request that permission before either exported-copy or original-photo writes.
  The storage layer keeps strict final-`Uri` validation; the Activity owns the
  runtime permission request and must not open a folder picker or start a bulk
  write until permission is granted.
- A running bulk operation must block overlapping loads/writes or disable the
  entry buttons until it completes.

### 4. Validation & Error Matrix
- Photo has no match/manual location -> return a per-photo skipped outcome.
- Photo MIME/extension is not writable by AndroidX `ExifInterface` -> return a
  per-photo failed outcome with an unsupported-format reason before copying or
  mutating the file.
- Edited temp file cannot be decoded, has a mismatched file signature, or GPS
  cannot be read back -> return a per-photo failed outcome before replacing the
  original.
- Source replacement cannot obtain a direct truncate/write descriptor -> return
  a per-photo failed outcome after edited-temp validation and before reporting
  success.
- Export-copy output stream cannot be opened -> return a failed outcome for that
  created document path; do not treat it as proof that original replacement
  would be unsafe.
- Post-write source validation fails -> return a per-photo failed outcome.
- User denies `ACCESS_MEDIA_LOCATION` before copy or original writes -> do not
  start the write flow or create an output document; explain that the app needs
  photo-location access to verify GPS metadata after writing.
- Photo write fails -> return a per-photo failed outcome and continue the batch.
- Top-level load/write failure -> surface an app error message and clear the
  busy state.
- Activity is destroyed -> lifecycle cancellation should not be swallowed as a
  successful result.

### 5. Good/Base/Bad Cases
- Good: Selecting 80 photos shows a loading state while EXIF reads run on
  `Dispatchers.IO`; the UI remains responsive.
- Base: Writing 80 matched photos snapshots the result list, disables duplicate
  write entry points, runs file copy/EXIF writes on `Dispatchers.IO`, then shows
  the usual result sheet.
- Base: A matched `DSC_1446.NEF` is reported as unsupported before an exported
  copy is created or an original mutation is attempted.
- Base: Writing originals validates the edited temp file, replaces the source
  via `rwt`, and validates the source `Uri` before counting the photo as
  written.
- Base: The first exported-copy write requests `ACCESS_MEDIA_LOCATION`, then
  writes and strictly validates the final SAF/MediaStore-backed output `Uri`.
- Bad: Calling `loadPhotos(...)`, `exportCopies(...)`, or `writeInPlace(...)`
  directly from an Activity result callback or button handler on the main
  thread.
- Bad: Copying a RAW file into the export folder, then calling
  `ExifInterface.saveAttributes()` and leaving behind an ungeotagged failed
  copy.
- Bad: Replacing an original photo through `openOutputStream(uri, "w")` and
  assuming a completed stream means the storage provider produced a valid image.
- Bad: Requiring `openFileDescriptor(uri, "rwt")` for newly created export-copy
  documents and rejecting providers that could have written those
  non-destructive outputs through an output stream.
- Bad: Starting an exported-copy write without media-location permission, then
  treating a privacy-redacted `getLatLong() == null` result as a storage failure
  even though the output file contains GPS.

### 6. Tests Required
- Run `./gradlew :app:compileDebugKotlin` for Activity/Compose/coroutine wiring.
- Run `./gradlew testDebugUnitTest` to ensure domain matching behavior is
  unchanged.
- Unit-test the writable format helper: JPEG/PNG/WebP accepted; NEF/DNG rejected.
- Unit-test pure helpers for image signature checks when original-write
  validation behavior changes.
- Manually test original writes on real SAF/MediaStore providers for JPEG, PNG,
  and WebP because JVM tests cannot model provider-specific descriptor behavior.
- Manually test copy export with media-location permission denied, newly granted,
  and already granted. Denial must create no output; granted cases must pass
  final-`Uri` GPS validation on a real provider.
- Run `./gradlew :app:lintDebug` for Android main-thread and resource checks.

### 7. Wrong vs Correct

#### Wrong
```kotlin
selectedPhotos = geotagging.loadPhotos(uris)
val outcomes = geotagging.writeInPlace(matchResults, onProgress)
```

```kotlin
// RAW files can be readable but not writable through AndroidX ExifInterface.
writeGps(rawUri, position)
```

```kotlin
// Provider behavior for "w" can be ambiguous for original replacement.
resolver.openOutputStream(originalUri, "w").use { output ->
    editedTemp.inputStream().use { it.copyTo(output) }
}
```

```kotlin
// A copy write reaches strict shared-media GPS read-back without permission.
writeCopiesUsingDefaultFolder()
```

#### Correct
```kotlin
lifecycleScope.launch {
    val photos = withContext(Dispatchers.IO) {
        geotagging.loadPhotos(uris)
    }
    selectedPhotos = photos
}
```

```kotlin
val writableMimeType = gpsWritableMimeType(resolver.getType(uri), displayName)
if (writableMimeType == null) {
    return PhotoWriteOutcome(displayName, Failed, unsupportedFormatReason)
}
writeGps(uri, position, writableMimeType)
```

```kotlin
val outcomes = geotagging.writeInPlace(matchResults, onProgress)
```

```kotlin
requestMediaLocationPermissionThen {
    writeCopiesUsingDefaultFolder()
}
```

```kotlin
resolver.openFileDescriptor(originalUri, "rwt").use { descriptor ->
    FileOutputStream(requireNotNull(descriptor).fileDescriptor).use { output ->
        editedTemp.inputStream().use { it.copyTo(output) }
        output.fd.sync()
    }
}
validateImageUri(originalUri, writableMimeType, position)
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
