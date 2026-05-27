# UI refinement

## Goal

Redesign TrackWrite from a functional Android MVP/debug shell into a product-grade mobile workflow for photographers who record GPS tracks, match photos, manually correct locations, and safely write EXIF metadata. The task should improve information architecture, visual hierarchy, interaction clarity, and trust signals without changing the proven GPS/geotagging/map behavior from the MVP.

## What I already know

* The previous MVP task is archived under `.trellis/tasks/archive/2026-05/05-26-trackwrite-android-mvp`.
* The user confirmed the AMap WebView manual location picker now works on device.
* A checkpoint commit exists for the manual picker: `b23dd06 Add AMap manual location picker`.
* A later UI pass exists: `c08aba7 Redesign app UI with a polished, card-based Quiet Instrument layout`.
* That UI pass improved spacing, cards, buttons, and tags, but it is still mostly visual packaging around the old debug-style workflow.
* Current tracked worktree is clean.
* `MainActivity` is still a single programmatic View screen built with `LinearLayout`, `ScrollView`, and `TextView` controls.
* `ManualLocationActivity` is also programmatic View UI around the WebView picker, AMap search/map selection, and confirm/cancel actions.
* The app currently has no Compose setup and no Material component dependency. Existing dependencies include AndroidX Activity/Core, DocumentFile, ExifInterface, Room, and coroutines.
* Current UI resources are simple XML drawables for primary/secondary/danger buttons, cards, selected cards, and recording/match status tags.
* The user chose Jetpack Compose for the product-grade UI migration.
* The user chose to migrate both `MainActivity` and `ManualLocationActivity` to Compose in this task, accepting the larger scope and WebView interop risk for stronger UI consistency.
* The user initially chose a three-tab bottom navigation structure, then revised the information architecture to two bottom navigation destinations: Record and Match. The unified top-right settings entry remains required.
* The UI must support Chinese.
* The user wants this main session to perform the full development rather than handing UI work to another agent.
* The settings area should be a complete, real settings surface covering appearance, track recording frequency, photo matching time difference, export, and related workflow defaults.
* Current project versions: AGP `8.7.3`, Kotlin `2.1.0`, Gradle `8.12`.
* Recording interval/distance are currently hard-coded in `TrackingService` as `BALANCED_INTERVAL_MS = 5_000L` and `BALANCED_DISTANCE_METERS = 8f`.
* Photo match defaults are currently in `MatchOptions`, including `maxTimeDifference = Duration.ofMinutes(5)`.
* Export-copy and write-original behaviors are currently in `PhotoGeotagging`; GPX share/export support is in `GpxFileActions`.

## Product and design context

* Product register: `product`.
* Target users are photography users recording GPS tracks while shooting, then selecting photos later for batch matching and EXIF writes.
* North star: "The Quiet Instrument" - reliable, light, quiet.
* The interface should foreground photos, timestamps, tracks, map points, match status, and write safety.
* Avoid cyberpunk dashboards, fitness/social app aesthetics, decorative gradients, glassmorphism, over-animated onboarding, and endless identical card grids.
* Destructive original-file writes must be visually and structurally distinct from exporting safe copies.
* Status must not rely on color alone; pair color with label/icon/structure.
* UI needs to remain legible for interrupted field use and outdoor conditions.

## Current UI assessment

The current UI is better than the initial debug shell but still not product-grade:

* It is one long vertical page with sections in cards, so hierarchy is flat and scanning cost is high.
* The home screen does not yet communicate the end-to-end workflow clearly: record/import track -> select photos -> review matches -> correct locations -> export/write.
* Photos are not visually dominant; selected photos are text rows without thumbnails.
* Track rows expose raw IDs/timestamps more than user-relevant summary and confidence.
* Match rules are edited through a comma-separated text dialog, which still feels like an internal tool.
* Manual map selection exists per photo but is not yet clearly presented as a normal correction action.
* `TextView` is used as custom buttons, so accessibility and interaction states are weaker than native buttons or a real component system.
* Visual tokens are not centralized beyond a small `colors.xml`; many colors and dimensions are hard-coded in Kotlin.
* The UI commit has a trailing whitespace warning in `MainActivity.kt`, indicating polish/check was incomplete.

## Requirements (evolving)

* Rework the main experience around the photographer workflow, not around implementation modules.
* Use two bottom navigation destinations: Record and Match.
* Provide one consistent top app bar with a right-aligned Settings entry.
* Implement a real Settings screen/flow from the top-right entry.
* Settings must include appearance preferences.
* Settings must include track recording frequency controls that affect `TrackingService` location update requests.
* Settings must include photo matching time-difference controls that affect the matching workflow.
* Settings must include export/write defaults that affect available behavior where supported by the current app, including default write mode and default export-copy folder handling.
* Settings must include a default write mode control with two modes: write exported copies and write originals.
* New users should default to writing exported copies.
* Writing originals must still require confirmation every time, even if original writing is the default write mode.
* Settings must include a default export-copy directory control that stores a persisted SAF tree URI, shows configured/unconfigured status, and allows choosing or clearing the directory.
* If the default export-copy directory is missing or its authorization is invalid, clicking the write-copies action should open the directory picker, save the new directory, and continue the write operation.
* Make recording truth immediately visible: state, active track, point count, duration, pause/resume/stop affordances.
* The app should use edge-to-edge status bar handling across the main UI and manual map picker. Main app content should remain protected by the top app bar/insets rather than being visually crowded under the status bar.
* The Record page should focus on GPS recording and track history, not track source selection for matching.
* Redesign the recording panel around state and duration first, then point count, distance, and recording-session context.
* Recording controls should be state-driven: stopped shows start, recording shows pause/stop, paused shows resume/stop.
* Starting a recording should keep the existing track-name confirmation dialog.
* Track history should live on the Record page, default collapsed when tracks exist, and show a simple count summary such as Track history (N).
* Track history entries should support selection for local viewing/management, rename, GPX export, and delete. Remove share from the primary track history actions.
* Track history selection and Match-page current track selection should be separate concepts. The Record page does not set a track for matching directly.
* Track source selection for matching should live at the top of the Match page as a collapsible section.
* The Match-page track source section should show the current matching track name plus point count, duration, and distance when collapsed. With no current matching track, it should expand and guide the user to import GPX or choose an existing track.
* Match-page track source actions should be limited to choosing a source: import GPX or choose from existing tracks. GPX export, rename, and delete belong to Record-page track history.
* Importing a GPX from the Match page should automatically select the imported track as the current matching track and collapse the track source section.
* After recording stops, the new track should become the current matching track only if the Match page does not already have a current matching track.
* The current matching track is session-only and does not need to persist across app restarts.
* Make photo matching review the primary workspace after photos are selected.
* After batch photo or folder selection, photos should default to a collapsed batch summary rather than a long list.
* The collapsed photo batch summary should show selected count plus matched, unmatched, and manual-position counts. It should also show a clear warning when there are photos that need attention.
* The photo batch should remain collapsed after selection, including when there are unmatched photos. Users can expand it to inspect or correct individual photos.
* Expanding the photo batch should show a compact row for each photo: thumbnail, file name, match status, and direct manual-location action. Detailed timestamps/coordinates/reasons should remain secondary to preserve batch density.
* Manual location should be available for every photo, not only unmatched photos. If a manual location exists, it takes precedence over automatic matching until cleared.
* Matching-setting changes should immediately recompute the current batch when a batch and matching track exist. Existing manual locations must not be overwritten, and the current folded/unfolded state should be preserved.
* When returning from manual location selection, the photo batch should expand, scroll to the edited photo, and temporarily highlight it without changing list order.
* The selected photo batch should not persist across app restarts.
* Make manual location correction easy to discover from each photo while preserving the batch-writing workflow as the primary path.
* Preserve the current AMap WebView manual picker behavior, including Web key/security config and GCJ-02 to WGS84 conversion boundary.
* Rebuild `ManualLocationActivity` with Compose while preserving the existing WebView, JavaScript bridge, AMap search/select behavior, missing-key unavailable state, and Activity result extras.
* Replace the two visible Match-page write actions with one write button whose label follows the default write mode: Write copies or Write originals.
* The Match page should show write readiness near the button, such as "will write X, skip Y". The button may remain visible and clickable in empty/incomplete states; missing prerequisites should produce a short prompt.
* Write operations should automatically skip unmatched photos, process matched photos and manual-location photos, and report written/skipped/failed counts.
* If there is no current matching track but some photos have manual locations, writing should still be allowed for those manual-location photos.
* Write/export completion should show a bottom-sheet result panel with written, skipped, and failed counts. Failed items should list file name and reason; skipped unmatched photos can remain summarized. Closing the result panel clears it.
* Make export copies and write originals clearly different through settings, button label, and confirmation behavior. Original writes remain dangerous and must not lose confirmation.
* Replace comma-separated match rule editing with a clearer UI pattern.
* Move reusable visual constants/components out of ad hoc duplicated Kotlin where practical.
* Use Jetpack Compose and Material 3 as the new UI foundation.
* Add a Compose theme/component layer for TrackWrite instead of scattering visual constants through Activity code.
* Move user-visible UI strings into Android string resources and use `stringResource` from Compose.
* Add Simplified Chinese resources for the redesigned UI.
* Keep the design calm and utilitarian, with strong typography, spacing, and state communication rather than decorative effects.
* Do not hardcode AMap keys or secrets.

## Acceptance Criteria (evolving)

* [ ] Main UI uses edge-to-edge status bar handling; main content remains protected by top app bar/insets.
* [ ] Main UI has bottom navigation with only Record and Match tabs.
* [ ] Main UI has a unified top-right settings entry on both tabs.
* [ ] TopAppBar title shows Record / Match depending on active tab.
* [ ] The Record page focuses on recording: redesigned recording panel with state+duration first, state-driven controls, and existing start-name dialog.
* [ ] The Record page contains collapsible track history with count summary (N). Track history supports selection, rename, export GPX, and delete; share removed.
* [ ] Track history and Match-page current track selection are separate concepts.
* [ ] The Match page top has a collapsible track source section: collapsed shows current matching track name + stats; expanded shows import GPX and choose from existing tracks.
* [ ] Importing GPX from Match page auto-selects it as current matching track.
* [ ] Recording stop auto-sets new track as current matching track only when Match page has none.
* [ ] Current matching track is session-only.
* [ ] After batch photo/folder selection, photos default to collapsed batch summary showing count + matched/unmatched/manual stats + warning for attention-needed photos.
* [ ] Photo batch remains collapsed by default; expanding shows compact rows: thumbnail, file name, match status, and direct manual-location action.
* [ ] Manual location is available for every photo; manual location takes precedence over automatic matching.
* [ ] Matching-setting changes immediately recompute the current batch; existing manual locations preserved; folded/unfolded state preserved.
* [ ] Returning from manual location expands photo batch, scrolls to edited photo, and temporarily highlights it.
* [ ] Selected photo batch does not persist across app restarts.
* [ ] `ManualLocationActivity` is Compose-based around the AMap WebView and still returns WGS84 coordinates through the existing result contract.
* [ ] Match page has one write button whose label follows settings: Write copies / Write originals.
* [ ] The write button area shows "will write X, skip Y" summary. The button remains clickable in empty/incomplete states; missing prerequisites produce a short prompt.
* [ ] Write operations automatically skip unmatched photos, process matched + manual-location photos, and report written/skipped/failed counts.
* [ ] Writing is allowed with no current matching track when some photos have manual locations.
* [ ] Write/export completion shows a bottom-sheet result panel with written/skipped/failed counts; failed items list file name and reason; closing clears the panel.
* [ ] Original writes always require confirmation, even when original writing is the default write mode.
* [ ] Settings include default write mode (write copies / write originals) and default export-copy directory (persisted SAF tree URI, status display, choose/clear). Missing/invalid directory triggers directory picker on write and continues.
* [ ] UI states cover empty, selected, matched, unmatched, manual, recording, paused, stopped, error/short feedback.
* [ ] Redesigned Compose UI supports Chinese through Android string resources.
* [ ] Touch targets and text contrast are suitable for Android mobile use.
* [ ] Existing geotagging, recording, GPX, Room, and AMap behavior is preserved.
* [ ] `./gradlew :app:compileDebugKotlin`, `./gradlew testDebugUnitTest`, and `./gradlew :app:lintDebug` pass.

## Out of Scope (explicit)

* Replacing the geotagging/domain algorithms.
* Changing the AMap API key model or coordinate conversion contract.
* Adding cloud sync, account systems, sharing communities, route analytics, or fitness-style features.
* Building a marketing/landing page.
* Decorative animation or visual effects unrelated to state feedback.

## Open Questions

* None. Initial appearance scope is system/light/dark theme mode only; density/accent customization is out of scope until there is a stronger product reason.

## Technical Notes

* Current main UI file: `app/src/main/java/com/trackwrite/app/MainActivity.kt`.
* Current manual picker UI file: `app/src/main/java/com/trackwrite/app/map/ManualLocationActivity.kt`.
* Current design context: `PRODUCT.md`, `DESIGN.md`.
* Current frontend spec index is still generic and should be improved after the UI direction is chosen.
* Existing Gradle setup does not include Compose or Material Components; adding either changes project setup and needs deliberate scope.
* Compose migration research: `research/compose-migration.md`.
* Because the project uses Kotlin `2.1.0`, Compose compiler setup should use the Kotlin Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) rather than relying only on old `composeOptions.kotlinCompilerExtensionVersion` patterns.
* Use Compose BOM plus Material 3 dependencies for aligned Compose library versions.
* Migrating the map picker requires Compose/View interop for `WebView`, and must not alter the AMap JS bridge or GCJ-02 to WGS84 conversion boundary.
* Compose UI should use `stringResource` for localized strings. Add `values-zh-rCN` resources for Simplified Chinese copy.
* Settings need a persisted app preferences store. SharedPreferences is enough for this app's current scope and matches the existing `RecordingStateStore` style.
* Appearance settings should start with three persisted options: system, light, dark.
* Recording frequency should use understandable presets that map to interval/distance pairs rather than raw provider arguments.
* Export settings should avoid fake controls. Include defaults and safety preferences only where the current export/write workflow can actually honor them.

## Definition of Done

* Requirements agreed and PRD updated.
* UI implementation completed according to the chosen approach.
* Tests/lint/type-check pass.
* Any new UI conventions worth preserving are documented under `.trellis/spec/`.
* Changes committed as a coherent UI refinement checkpoint.
