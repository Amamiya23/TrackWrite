# Product

## Register

product

## Users

TrackWrite is for photography users who record a GPS track while shooting, then return to batch-match photos against the track and write reliable location metadata. The primary scenario is an outdoor/photo-session workflow: start or resume recording before and during shooting, inspect that recording is actually working, then later select photos and geotag them from the recorded or imported track.

The product should also respect adjacent long-duration use cases, including travel-day recording, battery-conscious tracking, and browsing historical tracks after a shoot.

## Product Purpose

TrackWrite exists to make photo geotagging dependable without turning it into a technical GIS task. Success means users can trust three things: the current track is being recorded, the matching rules are understandable, and photo metadata writes are safe and reversible enough for real photo libraries.

## Brand Personality

Reliable, light, quiet.

The interface should feel calm and capable rather than heavy or performative. It should make complex operations, like foreground GPS tracking, GPX import/export, time-offset matching, and EXIF writes, feel understandable without hiding important state.

## Anti-references

Do not make TrackWrite look like a cyberpunk dashboard, command-center telemetry wall, or dark neon developer tool. Avoid dense technical chrome that makes a photography workflow feel like infrastructure monitoring.

Do not make it feel like a running, fitness, or social competition app. Tracks are supporting evidence for photos, not public performance records.

Avoid generic identical card grids, decorative gradients, glassmorphism, and over-animated onboarding. The product should feel purpose-built for photographers managing real files.

## Design Principles

1. Show recording truth. GPS recording must never feel like a black box; state, pause/resume, point count, duration, and recovery status should be visible.
2. Make destructive work explicit. Original photo mutation must be clearly different from exporting safe copies, with confirmation and per-photo results.
3. Keep photos and tracks in the foreground. UI decoration should recede so timestamps, thumbnails, map points, and match status carry the experience.
4. Prefer calm confidence over drama. Use restrained hierarchy, clear copy, and predictable controls so users feel in control during batch work.
5. Design for interrupted field use. The app should remain legible when a user is outside, moving, low on battery, or returning after process recovery.

## Accessibility & Inclusion

Target WCAG AA contrast for text and interactive states. Do not communicate match status, warnings, or write results by color alone; pair color with labels, icons, or structural indicators. Ensure focus states, touch targets, and permission/error flows are clear. Motion should be purposeful and brief, with reduced-motion support for non-essential transitions.
