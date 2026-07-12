# Quality Guidelines

> Code quality standards for frontend development.

---

## Overview

TrackWrite's Android UI is a Jetpack Compose product interface, not a debug
shell around backend workflows. Keep the UI workflow-first: recording truth,
track source, photo matching, manual correction, export, and destructive write
safety should be visible and easy to scan.

---

## Forbidden Patterns

- Do not build new app UI with ad hoc `LinearLayout` / `TextView` button shells
  when Compose is available for the surface.
- Do not hard-code user-visible UI copy in Composables or Activities. Use
  Android string resources and `stringResource(...)` so Chinese resources stay
  in sync.
- Do not add settings controls that are not backed by persisted app behavior.
  A visible settings item must either update a real app preference or be left
  out of scope.
- Do not pass AMap/GCJ-02 coordinates into UI state that feeds EXIF or matching.
  The map boundary must return WGS84.

---

## Required Patterns

- Compose-only screens use `ComponentActivity.setContent { TrackWriteTheme(...) { ... } }`.
- Manual AMap picker screens may use a native `FrameLayout` root with a full-size
  native `WebView`, a top search `ComposeView`, and a bottom selection
  `ComposeView`. Keep the controls inside `TrackWriteTheme`; do not embed the
  interactive AMap WebView inside a scrollable Compose card via `AndroidView`,
  because that can leave the map blank or incorrectly measured.
- Compose Material components must be rendered inside `TrackWriteTheme`, which
  wraps Material 3 `MaterialTheme`.
- Use Android string resources for default and Simplified Chinese UI copy:
  - default: `app/src/main/res/values/strings.xml`
  - Simplified Chinese: `app/src/main/res/values-zh-rCN/strings.xml`
- If `MainUiState.logMessage` or equivalent UI event state is updated, the
  active Compose shell must render and consume it through a visible feedback
  surface such as a Material3 `SnackbarHost`. Do not leave user-facing success,
  validation, or error messages as state-only updates.
- Persist product settings through `AppSettingsStore`, then read that store from
  the behavior layer that needs the value. For example, recording frequency is
  read by `TrackingService`, not only displayed in Settings.
- Keep provider-specific map objects at the UI boundary. Manual AMap selections
  must be converted from GCJ-02 to WGS84 before returning Activity result extras.

---

## Scenario: Compose UI Settings Boundary

### 1. Scope / Trigger
- Trigger: A setting is exposed in Compose UI and changes recording, matching,
  export, theme, or map behavior.

### 2. Signatures
- Store class: `com.trackwrite.app.settings.AppSettingsStore`.
- Snapshot model: `AppSettings`.
- Theme enum: `AppearanceMode` with storage values `system`, `light`, `dark`.
- Recording enum: `RecordingFrequency` with interval/distance fields.
- Main UI entry: `MainActivity.setContent { TrackWriteTheme(settings.appearance) { ... } }`.
- Manual picker entry: `ManualLocationActivity` renders AMap in a native
  `WebView` and renders controls in `ComposeView { TrackWriteTheme(...) { ... } }`.

### 3. Contracts
- Settings are stored in SharedPreferences named `app_settings`.
- Recording frequency maps to concrete `LocationManager.requestLocationUpdates`
  arguments:
  - `efficient`: `15_000 ms`, `25 m`
  - `balanced`: `5_000 ms`, `8 m`
  - `precise`: `2_000 ms`, `3 m`
- Photo matching settings map to `MatchOptions`:
  - camera offset minutes
  - max photo time difference minutes
  - start fallback enabled
  - end fallback enabled
- Export safety settings must affect the write/export UI hierarchy or
  confirmation behavior; they must not be inert switches.

### 4. Validation & Error Matrix
- Unknown stored enum value -> fall back to the default enum value.
- Camera offset outside allowed range -> clamp to the supported range before
  persisting.
- Max photo time difference outside allowed range -> clamp before persisting.
- Missing AMap Web key -> hide interactive map/search and show that map search
  is unavailable.
- No selected map/search location -> show validation message and do not return a
  location result.

### 5. Good/Base/Bad Cases
- Good: User selects "Precise" recording; `TrackingService` reads
  `RecordingFrequency.Precise` and requests `2_000 ms`, `3 m` updates.
- Base: User changes max time difference to 10 minutes; `MainActivity` rebuilds
  `MatchOptions(maxTimeDifference = Duration.ofMinutes(10))`.
- Bad: Settings screen shows a recording-frequency control but
  `TrackingService` still uses hard-coded balanced constants.

### 6. Tests Required
- Run `./gradlew :app:compileDebugKotlin` for Compose type safety.
- Run `./gradlew testDebugUnitTest` for domain and map conversion coverage.
- Run `./gradlew :app:lintDebug` for Android/Compose lint.
- Add or update JVM tests when settings change domain behavior rather than only
  wiring an existing option into UI.

### 7. Wrong vs Correct

#### Wrong
```kotlin
// UI-only switch; service behavior never changes.
Switch(checked = precise, onCheckedChange = { precise = it })
```

#### Correct
```kotlin
settingsStore.setRecordingFrequency(RecordingFrequency.Precise)
val frequency = settingsStore.current().recordingFrequency
locationManager.requestLocationUpdates(provider, frequency.intervalMs, frequency.distanceMeters, listener)
```

---

## Testing Requirements

- Run these checks before reporting UI implementation complete:

```bash
./gradlew :app:compileDebugKotlin
./gradlew testDebugUnitTest
./gradlew :app:lintDebug
```

## Record Screen Context Boundaries

### Convention: Separate Active Recording, Latest History, and Management Selection

**What**: Treat the record screen's three track contexts as separate values:

- `recording.trackId` identifies the active or paused recording and is the only
  source for live point, duration, and distance metrics.
- `historicalTracksForRecording(tracks, recording)` is the history collection.
  It excludes the active or paused `recording.trackId`, and includes that track
  again only after recording reaches `Stopped`.
- `recordTrackId` is a transient management target for export, rename, and
  delete actions. It must not drive the live recording panel.

**Why**: Reusing the management selection for live metrics produces
contradictory UI, such as a stopped recording saying there are no current
points while an old selected track shows a non-zero point count directly below.

**Required behavior**:

- Stopped state does not render live track metrics.
- Recording and paused states render metrics only for `recording.trackId`.
- History counts, summaries, and the history sheet all use the filtered history
  collection, independent of the management target.
- Starting or pausing a recording must not add the active track to history or
  change the history entry's position.
- File-picker callbacks freeze the target track ID before launch; do not read a
  mutable global selection when the picker returns.

```kotlin
// Correct: each context has one purpose.
val activeTrack = state.recording.trackId?.let { id -> state.tracks.firstOrNull { it.id == id } }
val historicalTracks = historicalTracksForRecording(state.tracks, state.recording)
val latestTrack = historicalTracks.firstOrNull()

RecordingControlDock(activeTrack = activeTrack)
TrackHistoryButton(trackCount = historicalTracks.size, latestTrack = latestTrack)
TrackHistorySheet(tracks = historicalTracks)
```

```kotlin
// Wrong: a historical management selection leaks into current recording truth.
val displayTrack = activeTrack ?: state.tracks.firstOrNull { it.id == state.recordTrackId }
TrackMetricsPanel(state, displayTrack)
```

**Tests and checks**:

- Verify stopped, recording, and paused states bind the intended track.
- Verify recording and paused states exclude `recording.trackId` from history,
  while stopped state includes it.
- Verify changing a history management target does not change current metrics.
- Verify asynchronous export writes the track selected when the picker opened.

### Convention: Keep Recording State Changes Size-Stable

**What**: Render the stopped, recording, and paused controls in one fixed-height
recording dock. Replace content inside that dock; do not insert a recording
proof card or live metrics card into the scrollable page when recording starts.

**Why**: Starting a recording can simultaneously change GPS evidence, controls,
metrics, and repository contents. If each change inserts its own page block,
the history content jumps even though the user's task has not changed.

```kotlin
Box(Modifier.fillMaxSize()) {
    Column(Modifier.padding(bottom = RecordingDockReservedSpace)) {
        TrackHistoryButton(...)
        TrackRouteViewport(
            track = recordViewportTrack(historicalTracks, activeTrack, recording),
            status = recording.status,
        )
    }
    RecordingControlDock(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .height(RecordingDockHeight),
    )
}
```

The stopped state shows only the start action. Active and paused states show
the essential status and controls in the same bounds; secondary metrics belong
in a progressively disclosed details sheet.

The middle route viewport is also part of this size-stable contract. Its outer
slot always exists: stopped state shows the latest historical track, while
recording and paused states show only `activeTrack`. Replace the viewport's
internal state with a short crossfade; never conditionally insert or remove the
viewport when recording starts.

Route projection stays Compose-independent and bounded:

```kotlin
projectRouteToViewport(
    points = track.points.map { it.position },
    viewportWidth = width,
    viewportHeight = height,
    maxDrawPoints = 600,
)
```

The projection must preserve first/last points and four-direction extrema,
center single or zero-span axes, keep output within the viewport, and unwrap
longitude across the antimeridian. Unit tests cover these cases. The Canvas is
a visualization boundary only; it must not change WGS84 storage or recording
sampling behavior.

### Convention: Keep Expandable Bottom Sheets Anchor-Stable

**What**: A `ModalBottomSheet` whose content can grow or shrink in place must
use `rememberModalBottomSheetState(skipPartiallyExpanded = true)`. Give lazy
list items stable keys, and animate the local reveal instead of letting a
content remeasure move the whole sheet between expanded and partial anchors.

**Why**: Material 3 recalculates sheet anchors when inline actions change the
measured content height. If a partial anchor is available, tapping an item can
make the entire sheet jump to half height even though the user did not drag it.

```kotlin
val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

ModalBottomSheet(
    sheetState = sheetState,
    onDismissRequest = onDismiss,
) {
    LazyColumn {
        items(items = tracks, key = { it.id }) { track ->
            TrackManagementRow(track)
        }
    }
}
```

---

## Match Screen Photo Batch State

### Convention: Reset Photo Batch Sources Together

**What**: The Activity that owns a selected photo batch must clear the source
photos and every derived UI representation in one operation. A batch-level
clear action resets `selectedPhotos`, `matchResults`, pending/highlighted photo
indices, `MainUiState.photos`, and `MainUiState.matches`, then closes the photo
sheet. It preserves the selected match track and never deletes or modifies the
source photo files.

**Why**: Match screen counts, filters, manual corrections, and write readiness
are derived from both the source photo list and its match results. Clearing only
the visible `MainUiState` or only `selectedPhotos` leaves stale rows or write
counts that reappear on the next refresh.

Filtered photo rows must retain their original batch indices when invoking
manual-location callbacks. User-visible match failures must use exhaustive
localized mappings rather than rendering domain enum names.

```kotlin
// Correct: reset the owner state and all derived UI state together.
private fun clearPhotoBatch() {
    if (isBulkOperationRunning) return
    selectedPhotos = emptyList()
    matchResults = emptyList()
    pendingManualPhotoIndex = null
    uiState = uiState.copy(
        photos = emptyList(),
        matches = emptyList(),
        showPhotoBatchSheet = false,
        highlightedPhotoIndex = null,
    )
}

// Wrong: this hides the current rows but leaves the source batch alive.
uiState = uiState.copy(matches = emptyList())
```

**Required checks**:

- Clearing one photo, a folder batch, or a batch with manual locations returns
  Match to the photo-input state and leaves the selected track unchanged.
- Dismissing the sheet does not clear the batch.
- Filtered manual-location actions still target the original photo index.
- Default and Simplified Chinese resources cover every `UnmatchedReason`.

---

## Scenario: Browser-Based App Updates

### 1. Scope / Trigger
- Trigger: Settings > About exposes app update checks and crosses GitHub release
  metadata, Activity intent launching, Compose state, and localized UI copy.

### 2. Signatures
- UI callback: `onOpenReleasePage: (String) -> Unit` receives the GitHub Release
  `html_url` from `UpdateCandidate.releasePageUrl`.
- Activity entry: `openReleasePage(url: String)` launches
  `Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)`.
- Update state: `AppUpdateUiState` supports `Idle`, `Checking`, `UpToDate`,
  `Available`, and `Error` only.

### 3. Contracts
- Version detection still fetches GitHub Releases metadata and compares
  `UpdateMetadata.versionCode` with `InstalledAppVersion.versionCode`.
- Available updates open the release page in a browser; the app must not download
  APK bytes, verify APK checksums, request install permissions, or launch the
  Android package installer.
- Keep `FileProvider` manifest wiring if another feature still uses it, such as
  GPX sharing; do not remove shared provider wiring only because update install
  support was removed.

### 4. Validation & Error Matrix
- `releasePageUrl == null` -> hide the browser action button and keep the
  available-update status visible.
- No browser or blocked browser intent -> set `AppUpdateUiState.Error` with
  `update_error_browser_unavailable` so the SnackbarHost surfaces it.
- Network, missing release, or malformed metadata -> keep using existing update
  error states; do not introduce download/install errors.

### 5. Good/Base/Bad Cases
- Good: A newer release is detected, the About card shows "Go to download", and
  tapping it opens the GitHub Release page.
- Base: The latest release is not newer, so Settings shows the up-to-date state
  and no release-page action.
- Bad: The app requests `REQUEST_INSTALL_PACKAGES`, downloads an APK into cache,
  or launches `Intent.ACTION_INSTALL_PACKAGE`.

### 6. Tests Required
- Run `./gradlew :app:compileDebugKotlin` for Compose and state exhaustiveness.
- Run `./gradlew testDebugUnitTest` so removed download classes do not leave
  stale unit tests or unresolved references.
- Run `./gradlew :app:lintDebug` for manifest, resource, and intent checks.
- Search app sources for old installer strings/classes before reporting done.

### 7. Wrong vs Correct

#### Wrong
```kotlin
uiState = uiState.copy(updateState = AppUpdateUiState.Downloading(candidate))
updateDownloader.download(candidate)
apkInstallerLauncher.launchInstaller(apk, candidate.apkAsset.downloadUrl)
```

#### Correct
```kotlin
val releasePageUrl = (updateState as? AppUpdateUiState.Available)?.candidate?.releasePageUrl
PrimaryActionButton(
    text = stringResource(R.string.open_release_page),
    onClick = { onOpenReleasePage(releasePageUrl) },
)
```

---

## Settings UI Patterns

### Convention: Symmetric Manual ColorScheme
**What**: All three `AppearanceMode` values (System / Light / Dark) resolve to
the manual `LightColors` / `DarkColors` defined in `TrackWriteTheme.kt`. Dynamic
color is intentionally not used; brand consistency wins over device-wallpaper
personalization.

**Why**: The prior asymmetric approach (light mode pinned structural tokens over
`dynamicLightColorScheme`, dark mode fell through to raw `dynamicDarkColorScheme`)
caused the dark `primary` to lose the brand blue and become a neutral slate.
Removing dynamic color entirely makes System mode symmetric and fixes the brand
break, while the manual scheme already encodes the `#F7F7F7` background /
white-card structure the old override protected.

**Example**:
```kotlin
val colorScheme = when (appearance) {
    AppearanceMode.System -> if (darkTheme) DarkColors else LightColors
    AppearanceMode.Light -> LightColors
    AppearanceMode.Dark -> DarkColors
}
```

### Convention: Settings Group Structure
**What**: Group related settings into a single `SettingsGroup` card with `HorizontalDivider` between items. Use `SettingsSectionHeader` above the card for group titles.

**Why**: Prevents visual fragmentation from lonely single-item cards. Maintains consistent rhythm across the settings page.

**Example**:
```kotlin
SettingsGroup {
    SettingNavigationRow(title = "Appearance", ...)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, ...)
    SettingNavigationRow(title = "Recording frequency", ...)
}
```

### Convention: Lightweight Option Selection
**What**: When a setting has ≤4 options, prefer inline expansion within the card
unless the active visual spec calls for a drawer/bottom-sheet interaction. The
HTML redesign for Settings uses drawer-style selection, so Appearance and
Recording frequency use native Material bottom sheets with compact option rows.

**Why**: Inline expansion reduces interaction steps for ordinary settings, but
matching an approved visual and interaction model is more important for a
page-level redesign. Do not use `AlertDialog` for these small option sets.

### Convention: Stepper Unit Display
**What**: Always display the unit next to numeric values in `SettingStepper`. Pass `unit` parameter and render as `"$value $unit"`.

**Why**: Prevents user confusion about what the number represents (minutes vs seconds vs meters).

### Convention: Persisted Folder Settings Show the Saved Value
**What**: When a Settings row stores a SAF folder URI or another path-like value,
persist the selected value immediately and render a readable saved value in the
row subtitle. For SAF tree URIs, display the decoded tree id (for example,
`Download/TrackWrite`) rather than a generic "configured" label.

**Why**: Folder settings are hard to verify from memory. Showing the persisted
value confirms that the selection survived and helps users spot the wrong
destination before writing files.

### Don't: Redundant Labels
**Problem**:
```kotlin
SettingsSectionHeader("Export")
SettingsGroup {
    Column {
        Text("Export")  // Redundant with section header
        SegmentedButton(...)
    }
}
```
**Instead**: Let the section header provide context; inner components show only their controls.

### Don't: Semantic Icon Mismatch
**Problem**: Using `Icons.Default.Refresh` for recording frequency (refresh ≠ frequency).
**Instead**: Use `Icons.Default.Speed` for frequency/speed, or omit the icon if no good match exists.

---

## Code Review Checklist

- Compose UI is under `TrackWriteTheme`.
- Bottom navigation has exactly the Record, Match, and Settings destinations.
  Track history management belongs on Record; matching track source selection
  belongs on Match; Settings remains backed by persisted `AppSettings`.
- Settings controls are backed by real persisted behavior.
- User-visible text is in string resources with Chinese equivalents.
- Recording status must expose field confidence, not just the coarse
  Recording/Paused/Stopped state. Show recent point age and provider when
  available, and explicit permission/location/waiting states when capture is
  blocked or still waiting for a fix.
- Manual AMap WebView is hosted as a native `WebView` root surface, with Compose
  controls overlaid rather than wrapping the map in a Compose scroll/card layout.
- AMap WebView interop still returns WGS84 through the existing Activity result
  extras.
