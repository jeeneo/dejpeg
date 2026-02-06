#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE="dejpeg-builder"
REPO="/build/repo"
TARGET="${1:-arm64-v8a}"

rm -rf "${SCRIPT_DIR}/build"
rm -rf "${SCRIPT_DIR}/app/build"
rm -rf "${SCRIPT_DIR}/.cxx"
rm -rf ${SCRIPT_DIR}/opencv/build_android_*
rm -rf ${SCRIPT_DIR}/app/src/main/jniLibs/

docker build -t "$IMAGE" -f "${SCRIPT_DIR}/DOCKERFILE" "${SCRIPT_DIR}"

docker run --rm \
    -u "$(id -u):$(id -g)" \
    -e HOME=/tmp -e SOURCE_DATE_EPOCH=0 -e TZ=UTC -e LANG=C.UTF-8 \
    -e BUILD_JNI=1 \
    -v "${SCRIPT_DIR}:${REPO}" \
    "$IMAGE" bash -c "git config --global --add safe.directory '*' && ${REPO}/build.sh ${TARGET}"
