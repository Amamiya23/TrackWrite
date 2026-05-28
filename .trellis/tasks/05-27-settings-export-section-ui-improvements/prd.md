# Settings Export Section UI Improvements

## Goal

Improve the export settings section in the settings page:
1. Change write copies/originals to segmented buttons
2. Remove subtitle from default export folder
3. Add folder icon on right side of export folder row
4. Click row to trigger folder selection

## Requirements

### 1. Segmented buttons for export mode
- Replace the two SettingChoiceRow (radio buttons) with a segmented button control
- Two options: "Write copies" and "Write originals"
- Show selected state visually

### 2. Export folder row redesign
- Remove subtitle text ("No folder configured..." / "Folder configured.")
- Show folder icon on the right side
- Clicking the entire row triggers folder selection

## Acceptance Criteria

- [ ] Export mode uses segmented buttons instead of radio buttons
- [ ] Export folder row has no subtitle
- [ ] Export folder row has folder icon on right
- [ ] Clicking export folder row triggers folder picker
- [ ] Compile and lint checks pass

## Technical Notes

- Settings file: `app/src/main/java/com/trackwrite/app/MainActivity.kt`
- Current implementation: SettingsScreen, ExportFolderSetting
