#!/usr/bin/env bash

readonly APK_APP_NAME="TrackWrite"

apk_asset_filename() {
    local version_name="$1"
    local safe_version
    safe_version="$(printf '%s' "$version_name" | LC_ALL=C tr -c 'A-Za-z0-9._-' '-')"
    printf '%s-%s.apk' "$APK_APP_NAME" "$safe_version"
}

apk_variant_for_gradle_task() {
    case "${1##*:}" in
        assembleRelease)
            printf 'release'
            ;;
        assembleDebug)
            printf 'debug'
            ;;
        *)
            return 1
            ;;
    esac
}

apk_gradle_output_path() {
    local variant="$1"
    printf 'app/build/outputs/apk/%s/app-%s.apk' "$variant" "$variant"
}

apk_distribution_path() {
    local version_name="$1"
    local variant="$2"
    printf 'app/build/outputs/apk/%s/%s' "$variant" "$(apk_asset_filename "$version_name")"
}
