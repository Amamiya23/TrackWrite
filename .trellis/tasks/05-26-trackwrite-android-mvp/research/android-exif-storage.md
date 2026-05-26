# Research: Android photo EXIF GPS writing and storage constraints

- **Query**: Research Android photo EXIF GPS writing for a photography geotagging MVP. Focus on MediaStore/SAF constraints on Android 10+, in-place writes vs copy/export flow, ExifInterface capabilities, permissions, preserving originals, and batch write safety.
- **Scope**: mixed
- **Date**: 2026-05-26

## Findings

### Files Found

| File Path | Description |
|---|---|
| `.trellis/tasks/05-26-trackwrite-android-mvp/prd.md` | Active PRD requiring batch photo selection/folder selection and EXIF GPS writes. |

No Android application source tree is present yet. Project specs currently have no Android/Exif/MediaStore-specific guidance.

### Code Patterns

- `.trellis/tasks/05-26-trackwrite-android-mvp/prd.md:14` requires batch photo selection from Android media or folder selection, then writing location into photos by matching EXIF capture time against track point times.
- `.trellis/tasks/05-26-trackwrite-android-mvp/prd.md:23` states photo metadata writing must preserve original image content and avoid destructive changes unless Android storage constraints force a copy/write-back flow.
- `.trellis/tasks/05-26-trackwrite-android-mvp/prd.md:32` keeps the product choice open: modify originals in place, create edited copies, or offer both.
- `.trellis/tasks/05-26-trackwrite-android-mvp/prd.md:56-66` defines photo geotagging: select photos/folders, read EXIF capture time, match against tracks, and write matched GPS information into EXIF metadata.
- `.trellis/tasks/05-26-trackwrite-android-mvp/prd.md:95-97` requires explicit permission/storage handling and rollback/data-safety consideration for EXIF writes.

### Android 10+ MediaStore constraints

- Android 10/API 29 scoped storage gives apps direct access to app-specific files and media files they created, but not unrestricted filesystem mutation of other apps' media. External docs fetched via Context7 cite Android 10 privacy changes: apps targeting Android 10+ get scoped access to external storage by default and can access app-specific directories plus media files they created.
- For adding/exporting new images to shared storage on Android 10+, no broad storage write permission is needed; docs note apps that only add files to shared storage can stop requesting storage permissions on Android 10+ and use `READ_EXTERNAL_STORAGE` only with `maxSdkVersion="28"` for older devices.
- Android 10 introduced `MediaStore.MediaColumns.IS_PENDING` so an app can create a new media item, write it while hidden from other apps, then publish it by clearing pending. This is relevant to a copy/export flow because it gives exclusive write access while creating the output item.
- For media not created by the app, write/delete/favorite/trash-style operations require user-mediated access. Android 10 supports per-item user consent through `RecoverableSecurityException` after a write/update attempt. Android 11/API 30+ adds batch-oriented request APIs such as `MediaStore.createWriteRequest(...)` for a collection of `Uri`s.
- Read permissions vary by API level: Android 12L/API 32 and lower use `READ_EXTERNAL_STORAGE`; Android 13/API 33+ uses `READ_MEDIA_IMAGES`; Android 14/API 34+ can grant partial visual access through `READ_MEDIA_VISUAL_USER_SELECTED`. Context7 docs for Android 14 partial access include the manifest/runtime permission split.

### SAF constraints

- The Storage Access Framework (SAF) is the documented route for user-selected documents/files created by other apps. Android docs fetched via Context7 state that for media/documents created in other apps, use SAF when direct app access is not available.
- SAF access is URI-based, not path-based. Persistent access requires `takePersistableUriPermission(...)` after an `ACTION_OPEN_DOCUMENT`/tree result, and write access depends on receiving/requesting `FLAG_GRANT_WRITE_URI_PERMISSION` and the provider allowing writes.
- EXIF writing through SAF should be modeled as `ContentResolver.openFileDescriptor(uri, "rw")` or equivalent if the provider supports writable, seekable descriptors. Providers may be cloud-backed or non-seekable; not every SAF URI is safe for in-place metadata mutation.

### ExifInterface capabilities and limitations

- AndroidX `ExifInterface` supports reading and setting GPS coordinates via `getLatLong()`, `setLatLong(double, double)`, generic `getAttribute(...)`, `setAttribute(...)`, and persistence through `saveAttributes()`.
- Context7 AndroidX docs list constructors for `File`, `String path`, `FileDescriptor`, and `InputStream`. The docs show GPS write flow: create `ExifInterface`, call `setLatLong(...)`, then `saveAttributes()`.
- Practical storage implication: `InputStream` sources are suitable for reading EXIF but not a reliable write target. For in-place writes, use a writable file/path/file descriptor backed by MediaStore or SAF. `saveAttributes()` throws `IOException`; callers must treat each file write as fallible.
- MIME support should be checked before offering writes. Context7 docs expose `ExifInterface.isSupportedMimeType(String)` and `saveAttributes() throws IOException`. For a photography MVP centered on preserving image content, JPEG is the lowest-risk target; support for other formats should be gated by ExifInterface support and tested per format/provider.
- GPS metadata writing can use `setLatLong(latitude, longitude)` rather than manually formatting `TAG_GPS_LATITUDE`, `TAG_GPS_LATITUDE_REF`, `TAG_GPS_LONGITUDE`, and `TAG_GPS_LONGITUDE_REF`.

### In-place writes vs copy/export flow

| Flow | Storage access shape | Original preservation | Batch implications |
|---|---|---|---|
| In-place MediaStore write | Requires ownership or user write grant. Android 10 per-item `RecoverableSecurityException`; Android 11+ batch `createWriteRequest`. | Mutates original metadata; image pixels should remain unchanged but original file bytes/metadata are still modified. | Must handle per-URI denial/failure; Android 10 consent may be item-by-item. |
| In-place SAF write | Requires SAF URI with write permission and provider support for writable/seekable descriptor. | Mutates the selected document. Provider behavior varies. | Tree/document grants can cover multiple files, but each provider/file can still fail. |
| Copy/export | App creates new media items with `IS_PENDING` or app-specific/export destination, copies image bytes, writes EXIF to the copy, then publishes/shares. | Preserves originals by default. | Safer for rollback; requires storage space and clear duplicate/export semantics. |

### Permissions summary

- Reading media library images: `READ_EXTERNAL_STORAGE` up to API 32, `READ_MEDIA_IMAGES` on API 33+, and possible `READ_MEDIA_VISUAL_USER_SELECTED` partial access on API 34+.
- Photo Picker can avoid broad read permissions but grants temporary read access; it is not a general write grant for modifying originals.
- Writing new exported images on Android 10+ generally does not require broad storage write permission when using MediaStore insert/app-owned output.
- Writing existing images created by other apps requires user-granted write access through MediaStore consent flows or SAF writable URI grants.
- `WRITE_EXTERNAL_STORAGE` is not a modern Android 10+ solution for arbitrary photo modification.

### Preserving originals and batch write safety

- The PRD explicitly requires original-preservation awareness (`prd.md:23`) and rollback/data-safety consideration (`prd.md:97`). Android storage constraints mean the safest persistence model is to treat every EXIF write as a transaction-like per-photo operation with preflight checks and independent success/failure reporting.
- For in-place writes, batch safety requires: validate MIME/support first, obtain required user write grants before mutation where possible, write one URI at a time, verify resulting EXIF GPS after `saveAttributes()`, and record per-item outcome. A crash or provider failure can leave an item partially processed unless the app uses copies/backups.
- For copy/export, use `IS_PENDING=1` while copying/writing and only publish (`IS_PENDING=0`) after EXIF verification. If a write fails, delete the pending item; originals remain untouched.
- Because MediaStore/SAF providers can reject or fail individual writes, batch UX/data model should not assume all-or-nothing success across a set of photos.

### External References

- Android Developers via Context7 `/websites/developer_android`, Android 10 privacy/storage changes — scoped storage default for Android 10+; app access is scoped to app files and media the app created.
- Android Developers via Context7 `/websites/developer_android`, Android 10 features — `IS_PENDING` for exclusive access while writing new media files.
- Android Developers via Context7 `/websites/developer_android`, Android 14 partial photo/video access — `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, and `READ_MEDIA_VISUAL_USER_SELECTED` permission model.
- Android Developers via Context7 `/websites/developer_android`, minimize permission requests / SAF — use Photo Picker for temporary selected-media read access; use SAF for files/media created by other apps when needed.
- AndroidX via Context7 `/androidx/androidx`, `androidx.exifinterface.media.ExifInterface` — constructors, `getLatLong()`, `setLatLong(...)`, `setAttribute(...)`, `saveAttributes()`, and `isSupportedMimeType(...)`.

### Related Specs

- No `.trellis/spec/**/*.md` files matching Android, Exif, MediaStore, SAF, photo, GPS, or geotag were found.

## Caveats / Not Found

- Direct `webfetch` requests to `developer.android.com` returned transport errors in this environment; Android documentation findings above are from Context7-fetched Android/AndroidX docs.
- No app source code exists yet, so there are no project implementation patterns for storage permissions, MediaStore writes, SAF, or ExifInterface usage to inspect.
- Exact ExifInterface format write behavior should be confirmed against the library version selected for the Android project and with real device/provider tests, especially for non-JPEG images and SAF/cloud providers.
