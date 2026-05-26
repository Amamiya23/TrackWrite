# Directory Structure

> How backend code is organized in this project.

---

## Overview

TrackWrite is a single Android application module. Pure business logic that does
not need Android framework APIs lives under `app/src/main/java/com/trackwrite/app/domain`.
Keep this package free of Activity, Service, Context, storage, map SDK, and EXIF
implementation details so matching, GPX, and statistics behavior can be tested
with fast JVM unit tests.

---

## Directory Layout

```
app/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── java/com/trackwrite/app/
    │   │   ├── MainActivity.kt
    │   │   └── domain/
    │   └── res/
    └── test/java/com/trackwrite/app/domain/
```

---

## Module Organization

- `domain/`: platform-independent models and algorithms (`Track`, `TrackPoint`,
  `GeoPoint`, photo-to-track matching, GPX import/export, track statistics).
- Android integration code should be added outside `domain/` in focused packages
  such as recording, storage, media, map, or UI.
- Keep provider-specific coordinate conversions at integration boundaries. Domain
  coordinates are WGS84 because GPX, matching, and EXIF writes use WGS84.

---

## Naming Conventions

- Domain data models use nouns: `Track`, `TrackPoint`, `GeoPoint`.
- Stateless algorithms use explicit service names: `PhotoTrackMatcher`,
  `GpxCodec`.
- Unit tests mirror the production class or behavior they verify:
  `PhotoTrackMatcherTest`, `GpxCodecTest`, `TrackStatsTest`.

---

## Examples

- `app/src/main/java/com/trackwrite/app/domain/PhotoMatch.kt`
- `app/src/main/java/com/trackwrite/app/domain/GpxCodec.kt`
