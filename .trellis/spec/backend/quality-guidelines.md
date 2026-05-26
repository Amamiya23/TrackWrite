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
  `TRACKWRITE_AMAP_API_KEY` Gradle property through manifest placeholders.
- Do not parse user-supplied GPX XML with external entity or external DTD loading
  enabled.

---

## Required Patterns

- Gradle uses Android application id `com.trackwrite.app`, `minSdk = 31`, and
  `compileSdk = 34` until deliberately changed.
- Local SDK paths belong in `local.properties`, which must remain ignored.
- Use Java/Kotlin target 17 for Android builds.
- Photo matching defaults must stay aligned with the PRD: camera offset `0`,
  max time difference `5 minutes`, and start/end endpoint fallback enabled.
- GPX import/export and domain matching must operate on WGS84 coordinates.

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
- Unit tests cover every new domain behavior.
