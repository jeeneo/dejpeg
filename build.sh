#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

###### defaults ######
TARGET_ABI="arm64-v8a"
BUILD_TYPE="release"
SIGN_APK=false
CLEAN=true

###### argument parsing ######
while [[ $# -gt 0 ]]; do
    case $1 in
        --abi) TARGET_ABI="$2"; shift 2;;
        --debug) BUILD_TYPE="debug"; shift;;
        --sign) SIGN_APK=true; BUILD_TYPE="release"; shift;;
        --no-clean) CLEAN=false; shift;;
        --help) 
            echo "Usage: $0 [--abi <abi|all>] [--debug] [--sign] [--no-clean]"
            echo "  --abi <abi>  Target ABI: arm64-v8a, armeabi-v7a, x86_64, x86, or all"
            echo "  --debug      Build debug variant"
            echo "  --sign       Sign release APK (requires keystore config)"
            echo "  --no-clean   Skip cleaning of build artifacts (default: clean everything)"
            exit 0;;
        *) echo "unknown option: $1"; exit 1;;
    esac
done

###### validate ######
ALL_ABIS=(arm64-v8a armeabi-v7a x86_64 x86)
if [[ "$TARGET_ABI" != "all" ]] && [[ ! " ${ALL_ABIS[*]} " =~ " $TARGET_ABI " ]]; then
    echo "Invalid ABI: $TARGET_ABI"
    exit 1
fi

###### clean if requested ######
if [[ "$CLEAN" == "true" ]]; then
    echo "cleaning"
    rm -rf opencv/build_android_* 2>/dev/null || true
    rm -rf app/.cxx 2>/dev/null || true
    rm -rf app/src/main/jniLibs 2>/dev/null || true
    rm -rf buildtemp 2>/dev/null || true
    rm -rf app/build 2>/dev/null || true
    rm -rf build 2>/dev/null || true
    rm -rf apks 2>/dev/null || true
fi

###### build opencv first if needed ######
if [[ "$TARGET_ABI" == "all" ]]; then
    OPENCV_TASK="buildOpencv"
else
    OPENCV_TASK="buildOpencv_$TARGET_ABI"
fi

echo "running: ./gradlew $OPENCV_TASK"
./gradlew "$OPENCV_TASK"

###### build gradle args ######
GRADLE_ARGS=()

if [[ "$CLEAN" == "true" ]]; then
    GRADLE_ARGS+=("clean")
fi

VARIANT="${BUILD_TYPE^}"
GRADLE_ARGS+=("assemble${VARIANT}")

if [[ "$TARGET_ABI" != "all" ]]; then
    GRADLE_ARGS+=("-PtargetAbi=$TARGET_ABI")
fi

if [[ "$SIGN_APK" == "true" ]]; then
    GRADLE_ARGS+=("-PsignApk=true")
fi

if [[ "$BUILD_TYPE" == "release" ]]; then
    GRADLE_ARGS+=(--no-daemon)
fi

###### execute ######
echo "running: ./gradlew ${GRADLE_ARGS[*]}"
./gradlew "${GRADLE_ARGS[@]}"

###### report ######
if [[ "$SIGN_APK" == "true" ]]; then
    APK_DIR="apks"
else
    APK_DIR="app/build/outputs/apk"
fi

if [[ -d "$APK_DIR" ]]; then
    APK_COUNT=$(find "$APK_DIR" -name "*.apk" 2>/dev/null | wc -l)
    if [[ $APK_COUNT -gt 0 ]]; then
        if [[ $APK_COUNT -eq 1 ]]; then
            echo "built $APK_COUNT apk in $APK_DIR"
        else
            echo "built $APK_COUNT apks in $APK_DIR"
        fi
        find "$APK_DIR" -name "*.apk" -exec ls -lh {} \;
    fi
fi
