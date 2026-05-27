# Research: Android GPS track recording

- **Query**: Research Android GPS track recording for a photography geotagging MVP. Focus on foreground services, background location permission constraints, recovering active recordings after process death/reboot if applicable, location update power tradeoffs, and user-visible recording state.
- **Scope**: mixed
- **Date**: 2026-05-26

## Findings

### Files Found

| File Path | Description |
|---|---|
| _Not found_ | No Android Kotlin, Java, Android XML, or Gradle files were found in the repository with `**/*.{kt,java,xml,gradle,gradle.kts}`. |
| `.trellis/spec/frontend/*.md` | Existing frontend Trellis specs; no Android-specific implementation guidance found. |
| `.trellis/spec/backend/*.md` | Existing backend Trellis specs; no Android-specific implementation guidance found. |
| `.trellis/spec/guides/*.md` | Cross-layer/code-reuse Trellis guides; no Android-specific implementation guidance found. |

### Code Patterns

No in-repository Android location recording code was found. A repository grep for `ForegroundService`, `foregroundServiceType`, `ACCESS_BACKGROUND_LOCATION`, `FusedLocationProvider`, `LocationRequest`, `BOOT_COMPLETED`, `WorkManager`, `notification`, and `location` returned only unrelated dependency content under `.opencode/node_modules`, not app code.

### External References

- [Android `Service.startForeground(int, Notification)` reference](https://developer.android.com/reference/android/app/Service) — a started service can be promoted to a foreground service by supplying an ongoing notification. Apps targeting Android P/API 28+ must request `android.permission.FOREGROUND_SERVICE`. The notification id must not be `0`. On Android S/API 31+, `ForegroundServiceStartNotAllowedException` can occur when a target-S+ app is restricted from becoming a foreground service due to background-start limits. On Android U/API 34+, missing or invalid foreground service type declarations can throw `MissingForegroundServiceTypeException`, `InvalidForegroundServiceTypeException`, or `SecurityException`.
- [Android `Service.startForeground(int, Notification, int)` reference](https://developer.android.com/reference/android/app/Service) — for SDK Q/API 29+ the service can specify foreground service types, including `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION`; the runtime type must be a subset of `android:foregroundServiceType` declared in the manifest. Beginning with Android U/API 34, apps targeting API 34+ are not allowed to start foreground services without a valid foreground service type.
- [Android foreground service location manifest example](https://developer.android.com/health-and-fitness/health-services/active-data) — official example declares `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />` and a service with `android:foregroundServiceType="health|location"`; for location-only recording the relevant foreground service type is `location`.
- [Android `Manifest.permission.FOREGROUND_SERVICE_LOCATION` reference](https://developer.android.com/reference/android/Manifest.permission) — allows a regular app to use `Service.startForeground` with the `location` foreground service type.
- [Android 10 privacy changes: background location](https://developer.android.com/about/versions/10/privacy/changes) — accessing device location in the background on Android 10/API 29+ requires `ACCESS_BACKGROUND_LOCATION`; this specifically governs location access when an app is not visible and not running a foreground service with a location type. The same source notes geofence monitoring targeting Android 10+ must declare this permission.
- [Android `Context.startForegroundService(Intent)` reference](https://developer.android.com/reference/android/content/Context) — after `startForegroundService`, the service must call `startForeground(int, Notification)` shortly after starting or the process may be crashed. The reference notes the method can be called regardless of foreground state, but apps targeting Android S/API 31+ are not allowed to start foreground services from the background.
- [Android `Service.START_REDELIVER_INTENT` reference](https://developer.android.com/reference/android/app/Service) — if returned from `onStartCommand`, and the service process is killed after `onStartCommand` returns, the system schedules service restart and redelivers the last delivered intent until `stopSelf(startId)` is called. This is relevant to recovering an active recording after process death while the service is still considered started.
- [Android `IntentService.setIntentRedelivery(boolean)` reference](https://developer.android.com/reference/android/app/IntentService) — deprecated in API 30, but documents the same redelivery semantics: only the most recent intent is guaranteed to be redelivered if multiple intents were sent.
- [Android `Manifest.permission.ACCESS_BACKGROUND_LOCATION` reference](https://developer.android.com/reference/android/Manifest.permission) — allows an app to access location in the background.

### Foreground services and user-visible recording state

Android foreground services require an ongoing notification supplied to `startForeground`. The service notification is the user-visible indicator while recording is active. For a GPS recording MVP, the foreground service type associated with location must be declared in the manifest (`android:foregroundServiceType="location"`) and used consistently at runtime when targeting modern Android SDKs.

### Background location permission constraints

Android 10+ separates background location access through `ACCESS_BACKGROUND_LOCATION`. The Android 10 privacy-change documentation distinguishes location access while an app is not visible and not running a foreground service with a location type from foreground-service location access. A foreground service with `location` type is therefore a key documented state for ongoing visible recording; background location permission is separately required for background access outside that visible foreground-service case, such as geofencing.

### Process death and reboot recovery

`START_REDELIVER_INTENT` provides documented process-death recovery semantics for a started service: after the process is killed, Android schedules a restart and redelivers the last intent. This covers process death while the service remains in Android's started-service lifecycle, not necessarily user-force-stop or all reboot cases. No official reboot-specific Android documentation was fetched in this run; no in-repository Android code declares `BOOT_COMPLETED` or a receiver because no Android project files were found.

### Location update power tradeoffs

The fetched official docs in this run did not include detailed Fused Location Provider interval/priority battery guidance. Android's foreground-service model itself implies an ongoing, user-visible high-importance operation via notification. Power-tuning details for GPS track recording remain an external-doc gap in this research file.

### Related Specs

- `.trellis/spec/frontend/index.md` — frontend spec index exists, but no Android-specific recording guidance found.
- `.trellis/spec/backend/index.md` — backend spec index exists, but no Android-specific recording guidance found.
- `.trellis/spec/guides/index.md` — guide index exists, but no Android-specific recording guidance found.

## Caveats / Not Found

- No Android app module or Android source files were found in the repository.
- `webfetch` attempts for Android foreground service, background location, and battery pages failed with transport errors, so the persisted external references are based on Context7-fetched official Android documentation excerpts.
- Detailed Fused Location Provider power tradeoff guidance and reboot receiver behavior were not successfully fetched in this run.
