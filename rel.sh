#!/usr/bin/env bash

set -euo pipefail

TOKEN="${CODEBERG_TOKEN:?Set CODEBERG_TOKEN}"
USER="dryerlint"
REPO="dejpeg"
API="https://codeberg.org/api/v1"
GRADLE="app/build.gradle.kts"
VERSION=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$GRADLE")
VERSIONCODE=$(grep -oP 'versionCode\s*=\s*\K\d+' "$GRADLE")
FASTLANE_FILE="fastlane/metadata/android/en-US/changelogs/$VERSIONCODE.txt"
TAG="release-$VERSION"

argparse() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --pre)
        TAG="prerelease-$VERSION"
        ;;
      *)
        echo "Unknown argument: $1"
        exit 1
        ;;
    esac
    shift
  done
}

argparse "$@"

if [[ -f "$FASTLANE_FILE" ]]; then
  FASTLANE=$(<"$FASTLANE_FILE")
else
  if [[ "$TAG" == prerelease* ]]; then
    echo "psst, hey, fastlane not found for $VERSIONCODE, make one dummy"
    echo "since it's a prerelease you survive for now"
  else
    echo "psst, hey, fastlane not found for $VERSIONCODE, make one dummy"
    touch "$FASTLANE_FILE"
    echo "im an idiot who forgot to make a fastlane changelog file" > "$FASTLANE_FILE"
    echo "forget it, i made one for you, maybe fill it in"
    exit 1
  fi
fi

if [[ "$TAG" == prerelease* ]]; then
  FASTLANE=$'Prerelease changelog:\n'"$FASTLANE"
  prerelease=true
else
  FASTLANE=$'Release changelog:\n'"$FASTLANE"
  prerelease=false
fi

echo "version: $VERSION"
echo "version code: $VERSIONCODE"
echo -e "body: \n$FASTLANE"
echo "tag: $TAG"
echo "prerelease: $prerelease"

RELEASE_DIR="app/build/outputs/apk/release"
OIDN_DIR="app/build/outputs/apk/oidnRelease"
OIDN_ZIP="$OIDN_DIR/dejpeg-arm64-v8a-oidn.zip"

shopt -s nullglob
release_files=("$RELEASE_DIR"/*.apk)
oidn_files=("$OIDN_DIR"/*.apk)

if (( ${#release_files[@]} == 0 || ${#oidn_files[@]} == 0 )); then
  echo "expected apks in both $RELEASE_DIR and $OIDN_DIR; aborting"
  exit 1
fi

command -v zip >/dev/null 2>&1 || { echo "zip exec not found" >&2; exit 1; }
rm -f "$OIDN_ZIP"
zip -j "$OIDN_ZIP" "${oidn_files[@]}" >/dev/null || { echo "failed to create $OIDN_ZIP" >&2; exit 1; }

ATTACHMENTS=("${release_files[@]}" "$OIDN_ZIP")

echo "creating release ($TAG)"

RELEASE_JSON=$(curl -sf \
  -X POST "$API/repos/$USER/$REPO/releases" \
  -H "Authorization: token $TOKEN" \
  -H "Content-Type: application/json" \
  -d "$(jq -n \
    --arg tag "$TAG" \
    --arg body "$FASTLANE" \
    --argjson pre "$prerelease" \
    '{tag_name: $tag, name: $tag, body: $body, draft: false, prerelease: $pre}')")

RELEASE_ID=$(echo "$RELEASE_JSON" | jq -r '.id')

for ATTACHMENT in "${ATTACHMENTS[@]}"; do
  NAME=$(basename "$ATTACHMENT")
  echo "uploading $NAME ..."
  curl -sf \
    -X POST "$API/repos/$USER/$REPO/releases/$RELEASE_ID/assets?name=$NAME" \
    -H "Authorization: token $TOKEN" \
    -H "Content-Type: application/octet-stream" \
    --data-binary "@$ATTACHMENT" \
    | jq -r '.browser_download_url'
done

echo "done: https://codeberg.org/$USER/$REPO/releases/tag/$TAG"
