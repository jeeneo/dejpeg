#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

# defaults
ABI="arm64-v8a"
DEBUG=false
SIGN=false
CLEAN=false

# parse arguments
for arg in "$@"; do
    case $arg in
        --abi=*) ABI="${arg#*=}";;
        --debug) DEBUG=true;;
        --sign) SIGN=true;;
        --clean) CLEAN=true;;
        --help)
            echo "usage: $0 [--abi=<abi>] [--debug] [--sign] [--no-clean]"
            echo "  --abi=<abi>  arm64-v8a, armeabi-v7a, x86_64, x86 (default: arm64-v8a)"
            echo "  --debug      build debug variant (default: release)"
            echo "  --sign       sign release APK"
            echo "  --no-clean   skip cleaning build artifacts"
            exit 0;;
        *) echo "unknown option: $arg (try --help)"; exit 1;;
    esac
done

# validate abi
[[ "$ABI" =~ ^(arm64-v8a|armeabi-v7a|x86_64|x86)$ ]] || { echo "Invalid ABI: $ABI"; exit 1; }

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

if $CLEAN; then
    echo -e "${YELLOW}cleaning up...${NC}"
    docker-compose down --remove-orphans -v 2>/dev/null || true
    rm -rf opencv/build_android_* app/{.cxx,build,src/main/jniLibs} {build,buildtemp,apks} 2>/dev/null || true
fi

VARIANT=$($DEBUG && echo "Debug" || echo "Release")
HOST_UID=$(id -u)
HOST_GID=$(id -g)

JNI_DIR="app/src/main/jniLibs/$ABI"
if [[ -f "$JNI_DIR/libbrisque_jni.so" ]]; then
    echo -e "${GREEN}skipping docker build${NC}"
else
    echo -e "${YELLOW}building brisque in docker...${NC}"
    docker-compose run --rm dejpeg bash -c '
        set -e
        ./gradlew externalNativeBuild'"$VARIANT"' -PtargetAbi='"$ABI"' --no-daemon
        chown -R '"$HOST_UID:$HOST_GID"' app/build app/.cxx opencv/build_android_* 2>/dev/null || true
    '
    SO_FILE=$(find app/build -name "libbrisque_jni.so" -path "*/$ABI/*" 2>/dev/null | head -1)
    if [[ -n "$SO_FILE" ]]; then
        mkdir -p "$JNI_DIR"
        cp "$SO_FILE" "$JNI_DIR/"
        echo -e "${GREEN}native library copied to $JNI_DIR${NC}"
    else
        echo "error: libbrisque_jni.so not found"
        exit 1
    fi
fi

echo -e "${YELLOW}building apk${NC}"
ARGS=("assemble$VARIANT" "-PtargetAbi=$ABI")
$SIGN && ARGS+=("-PsignApk=true")
./gradlew "${ARGS[@]}" --no-daemon

APK_DIR=$($SIGN && echo "apks" || echo "app/build/outputs/apk")
echo -e "${GREEN}build complete!${NC}"
find "$APK_DIR" -name "*.apk" -exec ls -lh {} \; 2>/dev/null || echo "No APKs found"
