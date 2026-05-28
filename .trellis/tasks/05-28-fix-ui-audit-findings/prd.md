# Fix UI audit findings

## Goal

Address the technical UI issues found by the Impeccable audit in priority order: harden accessibility and semantics, adapt cramped controls for small screens and large text, optimize photo thumbnail decoding, move remaining user-visible copy into Android resources, then do a final polish pass.

## What I already know

- TrackWrite is a Jetpack Compose Android product UI for photographers recording GPS tracks and geotagging photos.
- The UI should remain quiet, reliable, Material 3 based, and workflow-first.
- The audit found no P0 blockers and no major AI-style visual anti-patterns.
- Required project checks are `./gradlew :app:compileDebugKotlin`, `./gradlew testDebugUnitTest`, and `./gradlew :app:lintDebug`.

## Requirements

- Harden accessibility:
  - Stepper increment/decrement controls must have useful semantic labels.
  - Icon-only track actions must meet recommended touch target sizing.
  - Bottom navigation icons should not duplicate visible labels for accessibility services.
- Adapt responsive layout:
  - Horizontal action rows must avoid clipping on narrow screens, large font settings, and Chinese strings.
  - Export mode controls must wrap or stack instead of forcing a cramped single row.
  - Photo-row manual-location controls must remain usable with longer localized labels.
- Optimize thumbnails:
  - Photo thumbnails should avoid decoding full-resolution images for a 64dp display.
  - The implementation should keep memory use bounded and avoid unnecessary repeated decode work.
- Clarify/i18n:
  - Remaining user-visible log, error, and map strings must be moved into `strings.xml`.
  - Simplified Chinese resources must stay in sync.
- Polish:
  - Preserve the current restrained product visual direction.
  - Keep Material 3 components under `TrackWriteTheme`.
  - Avoid decorative gradients, glassmorphism, nested cards, or modal overuse.

## Acceptance Criteria

- [x] Stepper buttons expose localized accessibility descriptions and use at least 48dp tap targets.
- [x] Track history icon buttons use at least 48dp tap targets.
- [x] Bottom navigation avoids duplicate icon descriptions where labels already exist.
- [x] Main action rows and settings export controls gracefully stack/wrap in tight widths.
- [x] Thumbnail decoding is sampled/bounded for the 64dp thumbnail surface.
- [x] User-visible English strings added by the implementation have Simplified Chinese translations.
- [x] `./gradlew :app:compileDebugKotlin` passes.
- [x] `./gradlew testDebugUnitTest` passes.
- [x] `./gradlew :app:lintDebug` passes.

## Completion Notes

- Replaced cramped button rows with wrapping `FlowRow` where actions can overflow.
- Increased compact icon targets to 48dp and added localized action descriptions.
- Sampled photo thumbnail decoding before rendering the 64dp preview.
- Moved manual-location, GPX, and AMap user-visible messages into default and Simplified Chinese resources.
- No spec update required: the work followed existing frontend quality guidelines and did not introduce a new reusable project convention or cross-layer contract.

## Out of Scope

- No redesign of the primary Record/Match workflow.
- No new image-loading dependency unless the existing platform APIs are insufficient.
- No changes to matching, EXIF write behavior, GPX import/export behavior, or AMap coordinate conversion contracts.

## Technical Notes

- Primary files expected to change:
  - `app/src/main/java/com/trackwrite/app/MainActivity.kt`
  - `app/src/main/java/com/trackwrite/app/map/ManualLocationHtml.kt`
  - `app/src/main/java/com/trackwrite/app/map/ManualLocationActivity.kt`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-zh-rCN/strings.xml`
- Relevant project spec:
  - `.trellis/spec/frontend/quality-guidelines.md`
  - `.trellis/spec/guides/code-reuse-thinking-guide.md`
  - `.trellis/spec/guides/cross-layer-thinking-guide.md`
