# Design

## Boundaries

The implementation target is native Compose UI under the existing `MainActivity.kt` surface. The Activity remains the owner of:

- permission and ActivityResult launchers
- recording service commands
- GPX import/export
- photo and folder selection
- manual location activity results
- photo matching and write execution

The redesign changes layout, hierarchy, component composition, and state presentation for Record, Match, Settings, and the directly related bottom sheets. It does not change domain models, repositories, storage, matching algorithms, or write safety logic.

## Visual System

- Page background: use `MaterialTheme.colorScheme.background`, aligned to the mockup's light `#f9fafc`.
- Main panels: use `surfaceContainerLow` / `surface` with 10-14 dp rounded corners and subtle outline when needed.
- State tones: use existing Material tokens, mapping success to primary/primaryContainer, warning to tertiary/tertiaryContainer, error to error/errorContainer, neutral to surfaceVariant/onSurfaceVariant.
- Top navigation: custom native top bar with app label, 22 sp-ish strong page title, and a 42 dp settings icon button on Record/Match only.
- Bottom navigation: custom Compose surface instead of default `NavigationBar`, with three equal items, compact line icons, 62 dp height, subtle top border, and selected item rendered as a rounded soft-primary pill.
- Layout rhythm: 15 dp page padding, 10 dp vertical gaps, compact 6-10 dp internal metric gaps.
- Typography: use Material typography with stronger weights for state titles and row labels; do not add custom fonts.
- Motion: use existing Material sheet/dialog behavior only. No decorative page-load animation.

## Record Data Flow

`RecordScreen` computes:

- `activeTrack` from `recording.trackId`
- `selectedTrack` from `recordTrackId`
- displayed track for metrics from active track first, then selected track
- duration from `activeRecordingDuration` when recording, otherwise `TrackStats.duration`
- tone and title from `RecordingSnapshot.status`, `RecordingIssue`, and last point age

The proof panel renders status, confidence/evidence, and existing actions:

- stopped -> start
- recording -> pause and stop
- paused -> resume and stop

The history row opens the existing history sheet. Track row actions keep their current callback behavior by selecting the relevant track before export/rename/delete.

## Match Data Flow

`MatchScreen` computes:

- selected track from `matchTrackId`
- batch counts from `matches`
- write readiness from existing `writeReadiness(matches)`
- write mode from `settings.preferExportCopies`
- loading state from `bulkOperation`

The page is ordered as:

1. Track source panel with import/select affordance.
2. Photo input panel if no photos are loaded, or batch overview when matches exist.
3. Write panel when matches exist, including writeable/skipped counts and the single existing default write action.
4. Bulk operation panel when a write/load operation is active.

No new write behavior is added. The write button still calls `onWriteDefault`, which routes through existing confirmation and result logic.

## Settings Data Flow

`SettingsScreen` still receives `AppSettings` and emits `onSettingsChanged(settings.copy(...))`.

Appearance and recording frequency are represented by native Material bottom sheets with compact selectable rows. This follows the HTML drawer interaction while keeping the implementation native. Numeric steppers, switches, segmented export choice, and folder selection keep the current persistence contracts.

Settings remains backed by `state.showSettings`; the shared bottom navigation toggles that flag for the Settings item and clears it when Record or Match are selected. This preserves the existing state model while matching the mockup's three-page navigation.

## Compatibility

- Existing string resources remain the source of user-facing copy.
- Existing helper functions such as `formatDuration`, `formatDistance`, `statusLabel`, `recordingConfidenceText`, `displayTreeUri`, and write readiness helpers should be reused.
- Existing tests are mostly domain-level; the required implementation validation is Compose/Kotlin compilation.

## Rollback

Because this is scoped to UI composition, rollback is file-level:

- revert UI changes in `MainActivity.kt` and string resources if compilation or behavior review fails
- leave unrelated Trellis update files untouched
- do not revert `docs/mockups/`, because they are user-provided inputs
