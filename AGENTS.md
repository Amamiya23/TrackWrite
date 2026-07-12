<!-- TRELLIS:START -->
# Trellis Instructions

These instructions are for AI assistants working in this project.

This project is managed by Trellis. The working knowledge you need lives under `.trellis/`:

- `.trellis/workflow.md` — development phases, when to create tasks, skill routing
- `.trellis/spec/` — package- and layer-scoped coding guidelines (read before writing code in a given layer)
- `.trellis/workspace/` — per-developer journals and session traces
- `.trellis/tasks/` — active and archived tasks (PRDs, research, jsonl context)

If a Trellis command is available on your platform (e.g. `/trellis:finish-work`, `/trellis:continue`), prefer it over manual steps. Not every platform exposes every command.

If you're using Codex or another agent-capable tool, additional project-scoped helpers may live in:
- `.agents/skills/` — reusable Trellis skills
- `.codex/agents/` — optional custom subagents

Managed by Trellis. Edits outside this block are preserved; edits inside may be overwritten by a future `trellis update`.

<!-- TRELLIS:END -->

## GitHub Release Operations

- Run `gh auth status` and `scripts/release-apk.sh` outside the Codex sandbox.
  The sandbox may be unable to use the host GitHub credential or network and
  can report an invalid token even when the host credential is valid.
- A sandboxed `gh auth status` failure is not proof that the token expired.
  Retry the same check once outside the sandbox before suggesting or starting
  `gh auth login`.
- Never print, copy, or inspect the raw token. `gh auth status` is sufficient.
- Before creating a Release, commit and push the source used for the APK. A
  push to `origin/main` requires explicit user authorization.
- Pass a branch name or a full 40-character commit SHA to the release script's
  `--target`. GitHub may reject abbreviated commit SHAs as
  `Release.target_commitish is invalid`.
- After upload, verify the Release asset names and read back
  `trackwrite-update.json`; its `apkAssetName` and `sha256` must match the local
  versioned APK.
