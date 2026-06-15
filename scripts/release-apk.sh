#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'USAGE'
Usage:
  scripts/release-apk.sh <version-name> [options]

Uploads a compiled APK to a GitHub Release named by <version-name>. If the
release/tag does not exist, gh creates it and points the tag at the target ref.

Options:
  --apk <path>          APK to upload (default: app/build/outputs/apk/release/app-release.apk)
  --code <version-code> Accepted for parity with build-apk.sh; ignored when uploading
  --repo <owner/repo>   GitHub repository for gh, if not the current git remote
  --target <ref>        Branch or full commit SHA for a newly-created tag (default: GitHub default branch)
  --title <title>       Release title (default: <version-name>)
  --notes <text>        Release notes (default: empty notes)
  --notes-file <path>   Read release notes from a file
  --generate-notes      Let GitHub generate release notes
  --draft               Create the release as a draft
  --prerelease          Mark the release as a prerelease
  --clobber             Replace an existing release asset with the same name
  --skip-dirty-check    Allow releasing when tracked files have uncommitted changes
  -h, --help            Show this help

Examples:
  scripts/build-apk.sh v2.3 --code 23
  scripts/release-apk.sh v2.3
  scripts/release-apk.sh v2.3 --apk app/build/outputs/apk/debug/app-debug.apk --prerelease
USAGE
}

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
cd "$repo_root"

version_name=""
apk_path="app/build/outputs/apk/release/app-release.apk"
repo=""
target=""
title=""
notes=""
notes_file=""
generate_notes=false
draft=false
prerelease=false
clobber=false
skip_dirty_check=false

if [[ $# -lt 1 ]]; then
    usage
    exit 1
fi

while [[ $# -gt 0 ]]; do
    case "$1" in
        --apk)
            if [[ $# -lt 2 ]]; then
                echo "Missing value for --apk" >&2
                exit 1
            fi
            apk_path="$2"
            shift 2
            ;;
        --code)
            if [[ $# -lt 2 ]]; then
                echo "Missing value for --code" >&2
                exit 1
            fi
            if [[ ! "$2" =~ ^[0-9]+$ ]] || (( 10#$2 < 1 )); then
                echo "Version code must be a positive integer" >&2
                exit 1
            fi
            shift 2
            ;;
        --repo)
            if [[ $# -lt 2 ]]; then
                echo "Missing value for --repo" >&2
                exit 1
            fi
            repo="$2"
            shift 2
            ;;
        --target)
            if [[ $# -lt 2 ]]; then
                echo "Missing value for --target" >&2
                exit 1
            fi
            target="$2"
            shift 2
            ;;
        --title)
            if [[ $# -lt 2 ]]; then
                echo "Missing value for --title" >&2
                exit 1
            fi
            title="$2"
            shift 2
            ;;
        --notes)
            if [[ $# -lt 2 ]]; then
                echo "Missing value for --notes" >&2
                exit 1
            fi
            notes="$2"
            shift 2
            ;;
        --notes-file)
            if [[ $# -lt 2 ]]; then
                echo "Missing value for --notes-file" >&2
                exit 1
            fi
            notes_file="$2"
            shift 2
            ;;
        --generate-notes)
            generate_notes=true
            shift
            ;;
        --draft)
            draft=true
            shift
            ;;
        --prerelease)
            prerelease=true
            shift
            ;;
        --clobber)
            clobber=true
            shift
            ;;
        --skip-dirty-check)
            skip_dirty_check=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        --*)
            echo "Unknown argument: $1" >&2
            usage
            exit 1
            ;;
        *)
            if [[ -n "$version_name" ]]; then
                echo "Unexpected positional argument: $1" >&2
                usage
                exit 1
            fi
            version_name="$1"
            shift
            ;;
    esac
done

if [[ -z "$version_name" ]]; then
    echo "Version name is required" >&2
    usage
    exit 1
fi

if [[ "$version_name" =~ [[:space:]] ]]; then
    echo "Version name cannot contain whitespace because it is used as a git tag" >&2
    exit 1
fi

if [[ ! -f "$apk_path" ]]; then
    echo "APK not found: $apk_path" >&2
    echo "Build it first, for example: scripts/build-apk.sh $version_name" >&2
    exit 1
fi

for command_name in git gh; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "Required command not found: $command_name" >&2
        exit 1
    fi
done

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "This script must be run inside a git work tree" >&2
    exit 1
fi

if [[ -z "$title" ]]; then
    title="$version_name"
fi

if [[ "$notes_file" == "-" ]]; then
    :
elif [[ -n "$notes_file" && ! -f "$notes_file" ]]; then
    echo "Release notes file not found: $notes_file" >&2
    exit 1
fi

if [[ -n "$notes" && -n "$notes_file" ]]; then
    echo "Use either --notes or --notes-file, not both" >&2
    exit 1
fi

if [[ "$generate_notes" == true && -n "$notes_file" ]]; then
    echo "Use either --generate-notes or --notes-file, not both" >&2
    exit 1
fi

if [[ "$skip_dirty_check" == false ]] && { ! git diff --quiet || ! git diff --cached --quiet; }; then
    echo "Tracked files have uncommitted changes. Commit them or pass --skip-dirty-check." >&2
    exit 1
fi

gh_args=()
if [[ -n "$repo" ]]; then
    gh_args+=(--repo "$repo")
fi

if ! gh auth status >/dev/null; then
    echo "gh is not authenticated. Run: gh auth login" >&2
    exit 1
fi

create_flags=(--title "$title")
if [[ -n "$target" ]]; then
    create_flags+=(--target "$target")
fi
if [[ "$generate_notes" == true ]]; then
    create_flags+=(--generate-notes)
    if [[ -n "$notes" ]]; then
        create_flags+=(--notes "$notes")
    fi
elif [[ -n "$notes_file" ]]; then
    create_flags+=(--notes-file "$notes_file")
else
    create_flags+=(--notes "$notes")
fi

if [[ "$draft" == true ]]; then
    create_flags+=(--draft)
fi

if [[ "$prerelease" == true ]]; then
    create_flags+=(--prerelease)
fi

upload_flags=()
if [[ "$clobber" == true ]]; then
    upload_flags+=(--clobber)
fi

echo "Preparing GitHub release $version_name"
echo "APK: $apk_path"
if [[ -n "$target" ]]; then
    echo "Target for new tag: $target"
else
    echo "Target for new tag: GitHub default branch"
fi

release_view_error=""
if release_view_error="$(gh release view "$version_name" "${gh_args[@]}" 2>&1 >/dev/null)"; then
    echo "Release exists. Uploading APK asset..."
    gh release upload "$version_name" "$apk_path" "${upload_flags[@]}" "${gh_args[@]}"
else
    release_view_error_lower="$(printf '%s' "$release_view_error" | tr '[:upper:]' '[:lower:]')"
    if [[ "$release_view_error_lower" != *"not found"* && "$release_view_error_lower" != *"no release found"* ]]; then
        echo "Failed to check release $version_name:" >&2
        echo "$release_view_error" >&2
        exit 1
    fi

    echo "Release does not exist. Creating release and tag..."
    gh release create "$version_name" "$apk_path" "${create_flags[@]}" "${gh_args[@]}"
fi

echo "Published APK to release $version_name"
