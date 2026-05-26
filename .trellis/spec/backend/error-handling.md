# Error Handling

> How errors are handled in this project.

---

## Overview

Domain operations should make expected user-data outcomes explicit in return
types and reserve exceptions for invalid programmer input or malformed external
data that the caller must handle at an integration boundary.

---

## Error Types

- Photo matching uses `PhotoMatch.Unmatched` with `UnmatchedReason` for expected
  outcomes such as empty track, before/after track bounds, and exceeding the
  configured maximum time difference.
- `PhotoMatch.Matched` carries the matched `GeoPoint`, `MatchSource`, adjusted
  photo time, and time difference.

Do not encode normal unmatched photos as thrown exceptions; batch geotagging must
be able to show per-photo unmatched state and continue processing.

---

## Error Handling Patterns

- Use `require` for invalid domain construction inputs, such as invalid latitude,
  longitude, or unsorted track points.
- GPX decoding may throw for malformed XML, invalid numeric fields, or missing
  point times. Android storage/import callers should catch those failures and
  report a per-file import error instead of crashing the app.
- XML parsers used for user-supplied GPX must disable DOCTYPE, external general
  entities, external parameter entities, and external DTD loading.

---

## API Error Responses

No network API exists in the MVP baseline. For Android UI flows, convert domain
results and import/write failures into per-track or per-photo status rows.

---

## Common Mistakes

- Treating an unmatched photo as an exception. It is a normal review state.
- Allowing GPX XML parsing defaults. Default JVM XML parser behavior can allow
  external entity or DTD processing unless disabled explicitly.
