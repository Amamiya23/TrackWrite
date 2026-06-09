#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'USAGE'
Usage:
  scripts/build-apk.sh <version-name> [--code <version-code>] [--task <gradle-task>]

Examples:
  scripts/build-apk.sh v2.1
  scripts/build-apk.sh v2.1.3 --code 213
  scripts/build-apk.sh v2.1 --task :app:assembleDebug
USAGE
}

if [[ $# -lt 1 ]]; then
    usage
    exit 1
fi

version_name="$1"
version_code=""
gradle_task=":app:assembleRelease"
shift

while [[ $# -gt 0 ]]; do
    case "$1" in
        --code)
            if [[ $# -lt 2 ]]; then
                echo "Missing value for --code" >&2
                exit 1
            fi
            version_code="$2"
            shift 2
            ;;
        --task)
            if [[ $# -lt 2 ]]; then
                echo "Missing value for --task" >&2
                exit 1
            fi
            gradle_task="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage
            exit 1
            ;;
    esac
done

if [[ -z "$version_name" ]]; then
    echo "Version name cannot be empty" >&2
    exit 1
fi

if [[ -z "$version_code" ]]; then
    version_code="$(printf '%s' "$version_name" | tr -cd '0-9')"
fi

if [[ ! "$version_code" =~ ^[0-9]+$ ]] || (( 10#$version_code < 1 )); then
    echo "Version code must be a positive integer. Pass it with --code." >&2
    exit 1
fi

echo "Building $gradle_task with versionName=$version_name versionCode=$version_code"
./gradlew "$gradle_task" \
    -Ptrackwrite.versionName="$version_name" \
    -Ptrackwrite.versionCode="$version_code"
