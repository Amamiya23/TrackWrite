# Apply Custom Light Theme Color Scheme

## Goal

Apply a custom slate/charcoal color scheme for the light theme in TrackWrite, replacing the default Material 3 colors.

## Requirements

Apply the following color mapping to the light theme in `TrackWriteTheme.kt`:

| Material Role | Hex |
|---|---|
| primary | #1E293B |
| onPrimary | #FFFFFF |
| primaryContainer | #E2E8F0 |
| onPrimaryContainer | #0F172A |
| secondary | #475569 |
| onSecondary | #FFFFFF |
| secondaryContainer | #F1F5F9 |
| onSecondaryContainer | #1E293B |
| background | #F8FAFC |
| onBackground | #0F172A |
| surface | #FFFFFF |
| onSurface | #0F172A |
| surfaceVariant | #F1F5F9 |
| onSurfaceVariant | #475569 |
| outline | #CBD5E1 |
| error | #DC2626 |
| onError | #FFFFFF |
| errorContainer | #FEE2E2 |
| onErrorContainer | #991B1B |

## Acceptance Criteria

- [ ] Light theme uses the specified color values
- [ ] Dark theme remains unchanged (or is also adjusted for consistency)
- [ ] Compile and lint checks pass

## Technical Notes

- Theme file: `app/src/main/java/com/trackwrite/app/ui/TrackWriteTheme.kt`
- The app already has Dynamic Color support for Android 12+
- This only affects the fallback light theme when Dynamic Color is not available
