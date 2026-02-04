#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

export PATH="$PWD/cmake-local/bin:$PATH"
echo "cmake path: $(which cmake)"
echo "cmake version: $(cmake --version | head -n1)"

# defaults
ABI="arm64-v8a"
DEBUG=false
SIGN=false
CLEAN=true

# parse arguments
for arg in "$@"; do
    case $arg in
        --abi=*) ABI="${arg#*=}";;
        --debug) DEBUG=true;;
        --sign) SIGN=true;;
        --no-clean) CLEAN=false;;
        --help)
            echo "usage: $0 [--abi=<abi|all>] [--debug] [--sign] [--no-clean]"
            echo "  --abi=<abi>  arm64-v8a, armeabi-v7a, x86_64, x86, or all (default: arm64-v8a)"
            echo "  --debug      build debug variant (default: release)"
            echo "  --sign       sign release APK"
            echo "  --no-clean   skip cleaning build artifacts"
            exit 0;;
        *) echo "unknown option: $arg (try --help)"; exit 1;;
    esac
done

# validate abi
[[ "$ABI" =~ ^(arm64-v8a|armeabi-v7a|x86_64|x86|all)$ ]] || { echo "Invalid ABI: $ABI"; exit 1; }

# clean
if $CLEAN; then
    echo "cleaning"
    rm -rf opencv/build_android_* app/{.cxx,build,src/main/jniLibs} {build,buildtemp,apks} 2>/dev/null || true
fi

# build opencv
OPENCV_TASK="buildOpencv${ABI:+_$ABI}"
[[ "$ABI" == "all" ]] && OPENCV_TASK="buildOpencv"
echo "running: ./gradlew $OPENCV_TASK --no-daemon"
./gradlew "$OPENCV_TASK" --no-daemon

# build apk
VARIANT=$($DEBUG && echo "Debug" || echo "Release")
ARGS=("assemble$VARIANT")
$CLEAN && ARGS=("clean" "${ARGS[@]}")
[[ "$ABI" != "all" ]] && ARGS+=("-PtargetAbi=$ABI")
$SIGN && ARGS+=("-PsignApk=true")
! $DEBUG && ARGS+=("--no-daemon")

echo "running: ./gradlew ${ARGS[*]}"
./gradlew "${ARGS[@]}"

# report results
APK_DIR=$($SIGN && echo "apks" || echo "app/build/outputs/apk")
if [[ -d "$APK_DIR" ]]; then
    APKS=($(find "$APK_DIR" -name "*.apk" 2>/dev/null))
    [[ ${#APKS[@]} -gt 0 ]] && {
        count=${#APKS[@]}
        plural=$([[ $count -eq 1 ]] && echo "apk" || echo "apks")
        echo "built $count $plural"
        ls -lh "${APKS[@]}"
    }
fi
