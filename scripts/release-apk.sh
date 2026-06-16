#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'USAGE'
Usage:
  scripts/release-apk.sh <version-name> [options]

Uploads a compiled APK to a GitHub Release named by <version-name>. If the
release/tag does not exist, gh creates it and points the tag at the target ref.
For an existing release, --title, --notes, --notes-file, --draft,
--prerelease, and --target update the release before assets are uploaded.

Options:
  --apk <path>          APK to upload (default: app/build/outputs/apk/release/app-release.apk)
  --code <version-code> Required Android versionCode for trackwrite-update.json
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
  scripts/release-apk.sh v2.3 --code 23
  scripts/release-apk.sh v2.3 --code 23 --apk app/build/outputs/apk/debug/app-debug.apk --prerelease
USAGE
}

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
cd "$repo_root"

sha256_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        echo "Required command not found: sha256sum or shasum" >&2
        exit 1
    fi
}

json_escape() {
    local value="$1"
    value="${value//\\/\\\\}"
    value="${value//\"/\\\"}"
    value="${value//$'\n'/\\n}"
    value="${value//$'\r'/\\r}"
    value="${value//$'\t'/\\t}"
    printf '%s' "$value"
}

version_name=""
version_code=""
apk_path="app/build/outputs/apk/release/app-release.apk"
repo=""
target=""
title=""
title_provided=false
notes=""
notes_provided=false
notes_file=""
notes_file_provided=false
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
            version_code="$((10#$2))"
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
            title_provided=true
            shift 2
            ;;
        --notes)
            if [[ $# -lt 2 ]]; then
                echo "Missing value for --notes" >&2
                exit 1
            fi
            notes="$2"
            notes_provided=true
            shift 2
            ;;
        --notes-file)
            if [[ $# -lt 2 ]]; then
                echo "Missing value for --notes-file" >&2
                exit 1
            fi
            notes_file="$2"
            notes_file_provided=true
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

if [[ -z "$version_code" ]]; then
    echo "Version code is required. Pass it with --code." >&2
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

if [[ "$notes_file_provided" == true && "$notes_file" == "-" ]]; then
    :
elif [[ "$notes_file_provided" == true && ! -f "$notes_file" ]]; then
    echo "Release notes file not found: $notes_file" >&2
    exit 1
fi

if [[ "$notes_provided" == true && "$notes_file_provided" == true ]]; then
    echo "Use either --notes or --notes-file, not both" >&2
    exit 1
fi

if [[ "$generate_notes" == true && "$notes_file_provided" == true ]]; then
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
    if [[ "$notes_provided" == true ]]; then
        create_flags+=(--notes "$notes")
    fi
elif [[ "$notes_file_provided" == true ]]; then
    create_flags+=(--notes-file "$notes_file")
elif [[ "$notes_provided" == true ]]; then
    create_flags+=(--notes "$notes")
else
    create_flags+=(--notes "")
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

edit_flags=()
if [[ "$title_provided" == true ]]; then
    edit_flags+=(--title "$title")
fi
if [[ -n "$target" ]]; then
    edit_flags+=(--target "$target")
fi
if [[ "$notes_file_provided" == true ]]; then
    edit_flags+=(--notes-file "$notes_file")
elif [[ "$notes_provided" == true ]]; then
    edit_flags+=(--notes "$notes")
fi
if [[ "$draft" == true ]]; then
    edit_flags+=(--draft)
fi
if [[ "$prerelease" == true ]]; then
    edit_flags+=(--prerelease)
fi

apk_basename="$(basename "$apk_path")"
apk_sha256="$(sha256_file "$apk_path")"
metadata_dir="$(mktemp -d "${TMPDIR:-/tmp}/trackwrite-release.XXXXXX")"
metadata_path="$metadata_dir/trackwrite-update.json"
trap 'rm -rf "$metadata_dir"' EXIT

{
    printf '{\n'
    printf '  "versionName": "%s",\n' "$(json_escape "$version_name")"
    printf '  "versionCode": %s,\n' "$version_code"
    printf '  "apkAssetName": "%s",\n' "$(json_escape "$apk_basename")"
    printf '  "sha256": "%s"\n' "$apk_sha256"
    printf '}\n'
} > "$metadata_path"

echo "Preparing GitHub release $version_name"
echo "APK: $apk_path"
echo "Update metadata: $metadata_path"
if [[ -n "$target" ]]; then
    echo "Target for new tag: $target"
else
    echo "Target for new tag: GitHub default branch"
fi

release_view_error=""
if release_view_error="$(gh release view "$version_name" "${gh_args[@]}" 2>&1 >/dev/null)"; then
    if [[ "$generate_notes" == true ]]; then
        echo "Release exists. --generate-notes only applies when creating a release; applying explicit edit flags only." >&2
    fi
    if [[ ${#edit_flags[@]} -gt 0 ]]; then
        echo "Release exists. Updating release details..."
        gh release edit "$version_name" "${edit_flags[@]}" "${gh_args[@]}"
    fi
    echo "Release exists. Uploading APK and update metadata assets..."
    upload_error=""
    if ! upload_error="$(gh release upload "$version_name" "$apk_path" "$metadata_path" "${upload_flags[@]}" "${gh_args[@]}" 2>&1)"; then
        echo "$upload_error" >&2
        upload_error_lower="$(printf '%s' "$upload_error" | tr '[:upper:]' '[:lower:]')"
        if [[ "$upload_error_lower" == *"same name"* || "$upload_error_lower" == *"already exists"* ]]; then
            echo "Release assets with these names already exist. Re-run with --clobber to replace them." >&2
        fi
        exit 1
    fi
else
    release_view_error_lower="$(printf '%s' "$release_view_error" | tr '[:upper:]' '[:lower:]')"
    if [[ "$release_view_error_lower" != *"not found"* && "$release_view_error_lower" != *"no release found"* ]]; then
        echo "Failed to check release $version_name:" >&2
        echo "$release_view_error" >&2
        exit 1
    fi

    echo "Release does not exist. Creating release and tag..."
    gh release create "$version_name" "$apk_path" "$metadata_path" "${create_flags[@]}" "${gh_args[@]}"
fi

echo "Published APK and trackwrite-update.json to release $version_name"
