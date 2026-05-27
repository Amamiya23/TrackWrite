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
* The user chose a three-tab bottom navigation structure and requested a unified top-right settings entry.
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
* Use three bottom navigation destinations: Record, Match, and Library.
* Provide one consistent top app bar with a right-aligned Settings entry.
* Implement a real Settings screen/flow from the top-right entry.
* Settings must include appearance preferences.
* Settings must include track recording frequency controls that affect `TrackingService` location update requests.
* Settings must include photo matching time-difference controls that affect the matching workflow.
* Settings must include export-related controls or defaults that affect available export/write behavior where supported by the current app.
* Make recording truth immediately visible: state, active track, point count, duration, pause/resume/stop affordances.
* Make track selection/import/export manageable without letting GPX operations dominate the photo workflow.
* Make photo matching review the primary workspace after photos are selected.
* Make manual location correction easy to discover from each unmatched/incorrect photo.
* Preserve the current AMap WebView manual picker behavior, including Web key/security config and GCJ-02 to WGS84 conversion boundary.
* Rebuild `ManualLocationActivity` with Compose while preserving the existing WebView, JavaScript bridge, AMap search/select behavior, missing-key unavailable state, and Activity result extras.
* Make export copies and write originals clearly different in hierarchy, tone, and confirmation.
* Replace comma-separated match rule editing with a clearer UI pattern.
* Move reusable visual constants/components out of ad hoc duplicated Kotlin where practical.
* Use Jetpack Compose and Material 3 as the new UI foundation.
* Add a Compose theme/component layer for TrackWrite instead of scattering visual constants through Activity code.
* Move user-visible UI strings into Android string resources and use `stringResource` from Compose.
* Add Simplified Chinese resources for the redesigned UI.
* Keep the design calm and utilitarian, with strong typography, spacing, and state communication rather than decorative effects.
* Do not hardcode AMap keys or secrets.

## Acceptance Criteria (evolving)

* [ ] The first screen clearly shows recording status and next best workflow action.
* [ ] Main UI has bottom navigation with Record, Match, and Library tabs.
* [ ] Main UI has a unified top-right settings entry.
* [ ] Settings is a complete Compose surface covering appearance, recording frequency, photo matching time difference, and export/write behavior supported by the app.
* [ ] Recording frequency settings are persisted and used by `TrackingService`.
* [ ] Photo matching time-difference settings are persisted and used by the match workflow.
* [ ] Track, photo, match, manual correction, and write/export steps are visually distinct and easy to scan.
* [ ] Manual AMap location selection remains functional and is easier to discover from photo review.
* [ ] `ManualLocationActivity` is Compose-based around the AMap WebView and still returns WGS84 coordinates through the existing result contract.
* [ ] Safe export is visually primary over direct original writes unless the user intentionally chooses the dangerous path.
* [ ] Match rule editing no longer requires a comma-separated string.
* [ ] UI states cover empty, selected, matched, unmatched, manual, recording, paused, stopped, error/log feedback.
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
