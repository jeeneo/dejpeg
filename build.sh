#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

###### configurable build params ######
# abis: arm64-v8a (default), armeabi-v7a, x86_64, x86, or all
TARGET_ABI="arm64-v8a"
# build_type: debug, release (forced to release for signing)
BUILD_TYPE="release"
# upx: true/false (will attempt to download if not found in PATH)
USE_UPX=true
# build_variant: full (opencv + ONNX), lite (ONNX only)
BUILD_VARIANT="lite"
# sign_apk: true/false (release builds only)
SIGN_APK=false
# no_clean: skip cleanup and reuse existing jniLibs
NO_CLEAN=false
# skip_gradle: skip gradlew build, only build native libraries
SKIP_GRADLE=false

###### constants - do not modify ######
ALL_ABIS=(arm64-v8a armeabi-v7a x86_64 x86)
UPX_URL="https://github.com/upx/upx/releases/download/v5.1.0/upx-5.1.0-amd64_linux.tar.xz"
ONNX_MAVEN="https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android"
BUILDTEMP="./buildtemp"
UPX_BIN=""
declare -A ABI_ARCH=([arm64-v8a]=aarch64-linux-android [armeabi-v7a]=arm-linux-androideabi [x86_64]=x86_64-linux-android [x86]=i686-linux-android)

###### helper functions ######
log() { echo -e "\033[0;34m[INFO]\033[0m $1"; }
err() { echo -e "\033[0;31m[ERROR]\033[0m $1" >&2; exit 1; }

cleanup() {
    if [[ -d app/src/main/jniLibs || -d app/src/full/jniLibs || -d app/src/lite/jniLibs ]]; then
        log "deleting native libraries"
        rm -rf app/src/main/jniLibs app/src/full/jniLibs app/src/lite/jniLibs
    fi
    if [[ -d "opencv/build_android" ]]; then
        log "deleting OpenCV build"
        rm -rf opencv/build_android
    fi
}

compress_with_upx() {
    local file=$1
    if $USE_UPX && [[ -n "$UPX_BIN" ]] && [[ -f "$file" ]] && [[ "$BUILD_TYPE" == "release" ]]; then
        chmod +x "$file"
        "$UPX_BIN" --best --lzma --android-shlib "$file" 2>/dev/null || true
    fi
}

process_existing_libs() {
    log "processing existing libs for release (strip + compress)..."
    for dir in app/src/main/jniLibs app/src/full/jniLibs app/src/lite/jniLibs; do
        [[ ! -d "$dir" ]] && continue
        find "$dir" -name "*.so" | while read -r lib; do
            [[ -n "${STRIP:-}" ]] && "$STRIP" "$lib" 2>/dev/null || true
            compress_with_upx "$lib"
        done
    done
}

validate_keystore() {
    KEYSTORE_PATH="${KEYSTORE_PATH:-$(grep -oP 'keystore\.path=\K.*' local.properties 2>/dev/null || true)}"
    KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-$(grep -oP 'keystore\.password=\K.*' local.properties 2>/dev/null || true)}"
    KEYSTORE_ALIAS="${KEYSTORE_ALIAS:-$(grep -oP 'keystore\.alias=\K.*' local.properties 2>/dev/null || true)}"
    KEY_PASSWORD="${KEY_PASSWORD:-$(grep -oP 'keystore\.keyPassword=\K.*' local.properties 2>/dev/null || true)}"
    
    local required_vars=("KEYSTORE_PATH" "KEYSTORE_PASSWORD" "KEYSTORE_ALIAS" "KEY_PASSWORD")
    for var in "${required_vars[@]}"; do
        if [[ -z "${!var}" ]]; then
            err "${var#KEYSTORE_} not set. Set via environment variable or keystore.${var@L} in local.properties"
        fi
    done
    [[ ! -f "$KEYSTORE_PATH" ]] && err "Keystore file not found: $KEYSTORE_PATH"
    
    export KEYSTORE_PATH KEYSTORE_PASSWORD KEYSTORE_ALIAS KEY_PASSWORD
}

###### argument parsing ######
while [[ $# -gt 0 ]]; do
    case $1 in
        --abi) TARGET_ABI="$2"; shift 2;;
        --debug) BUILD_TYPE="debug"; shift;;
        --no-upx) USE_UPX=false; shift;;
        --sign) SIGN_APK=true; BUILD_TYPE="release"; shift;;
        --full) BUILD_VARIANT="full"; shift;;
        --no-cleanup) NO_CLEAN=true; shift;;
        --skip-gradle) SKIP_GRADLE=true; shift;;
        --help) echo "Usage: $0 [--abi <abi|all>] [--debug] [--no-upx] [--sign] [--full] [--no-cleanup] [--skip-gradle]"; exit 0;;
        *) err "Unknown option: $1";;
    esac
done

# validate arguments
[[ "$TARGET_ABI" != "all" && ! " ${ALL_ABIS[*]} " =~ " $TARGET_ABI " ]] && err "Invalid ABI: $TARGET_ABI. Valid: ${ALL_ABIS[*]} or 'all'"
[[ "$BUILD_VARIANT" != "full" && "$BUILD_VARIANT" != "lite" ]] && err "Invalid variant: $BUILD_VARIANT. Valid: full, lite"
[[ "$SIGN_APK" == "true" && "$BUILD_TYPE" == "debug" ]] && { log "Ignoring --debug, signing requires release"; BUILD_TYPE="release"; }

###### environment setup ######
mkdir -p "$BUILDTEMP"

# SDK/NDK setup
[[ -z "${ANDROID_SDK_ROOT:-}" ]] && ANDROID_SDK_ROOT=$(grep "sdk.dir" local.properties 2>/dev/null | cut -d= -f2 || true)
[[ -z "$ANDROID_SDK_ROOT" || ! -d "$ANDROID_SDK_ROOT" ]] && err "Set ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT ANDROID_HOME="$ANDROID_SDK_ROOT"

if [[ "$BUILD_VARIANT" == "full" || "$BUILD_TYPE" == "release" ]]; then
    ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$(find "$ANDROID_SDK_ROOT/ndk" -maxdepth 1 -name "27.3.*" 2>/dev/null | head -1)}"
    [[ -z "$ANDROID_NDK_HOME" || ! -d "$ANDROID_NDK_HOME" ]] && err "NDK 27.3.x not found"
    export ANDROID_NDK_HOME
    STRIP="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
fi

# keystore validation
[[ "$SIGN_APK" == "true" ]] && validate_keystore

# ONNX version check
ONNX_VER=$(grep -oP 'onnxruntimeAndroid\s*=\s*"\K[^"]+' gradle/libs.versions.toml 2>/dev/null || true)
[[ -z "$ONNX_VER" ]] && { read -rp "ONNX Runtime version: " ONNX_VER; [[ -z "$ONNX_VER" ]] && err "Version required"; }

# UPX setup (early, needed for process_existing_libs)
if $USE_UPX; then
    if command -v upx &>/dev/null; then
        UPX_BIN="upx"
    elif [[ -x "$BUILDTEMP/upx-5.1.0-amd64_linux/upx" ]]; then
        UPX_BIN="$BUILDTEMP/upx-5.1.0-amd64_linux/upx"
    else
        read -rp "UPX not found. Download? [Y/n]: " r
        if [[ "${r:-Y}" =~ ^[Yy]$ ]]; then
            curl -L "$UPX_URL" | tar -xJ -C "$BUILDTEMP" && UPX_BIN="$BUILDTEMP/upx-5.1.0-amd64_linux/upx"
        else
            USE_UPX=false
        fi
    fi
fi

###### cache invalidation ######
SKIP_LIB_BUILD=false
BUILD_SIG="$TARGET_ABI|$BUILD_VARIANT|$ONNX_VER|$USE_UPX"

if [[ -f "$BUILDTEMP/.build_sig" ]] && [[ "$(cat "$BUILDTEMP/.build_sig")" != "$BUILD_SIG" ]]; then
    log "build config changed, cleaning"
    rm -rf app/src/main/jniLibs app/src/full/jniLibs app/src/lite/jniLibs
    rm -rf opencv/build_android
    rm -f "$BUILDTEMP/.build_sig"
fi
echo "$BUILD_SIG" > "$BUILDTEMP/.build_sig"

# handle cleanup mode
if [[ "$NO_CLEAN" == "true" ]]; then
    # check if libraries already exist
    if [[ -d "app/src/main/jniLibs" || -d "app/src/full/jniLibs" || -d "app/src/lite/jniLibs" ]]; then
        log "libraries already exist, skipping library build"
        SKIP_LIB_BUILD=true
    fi
else
    trap cleanup EXIT
    cleanup
fi

# always process existing libs for release builds to ensure stripping + compression
if [[ "$BUILD_TYPE" == "release" && "$SKIP_LIB_BUILD" == "true" ]]; then
    process_existing_libs
fi

###### build functions ######
build_opencv() {
    if [[ -d "opencv/build_android" && "$NO_CLEAN" != "true" ]]; then
        log "cleaning build_android"
        rm -rf opencv/build_android
    fi
    local abi=$1
    [[ ! -d opencv/opencv ]] && { mkdir -p opencv && git -C opencv clone https://github.com/opencv/opencv.git; }
    [[ ! -d opencv/opencv_contrib ]] && git -C opencv clone https://github.com/opencv/opencv_contrib.git
    
    # CPU optimization
    local cpu_baseline="" cpu_dispatch=""
    case "$abi" in
        arm64-v8a)
            cpu_baseline=""
            cpu_dispatch=""
            ;;
        armeabi-v7a)
            cpu_baseline="NEON"
            cpu_dispatch=""
            ;;
        x86_64)
            cpu_baseline="SSE3"
            cpu_dispatch="SSE4_2,AVX,AVX2"
            ;;
        x86)
            cpu_baseline="SSE2"
            cpu_dispatch="SSE4_2,AVX"
            ;;
    esac

    mkdir -p opencv/build_android && cd opencv/build_android
    rm -rf CMakeCache.txt CMakeFiles/
    cmake -Wno-deprecated -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_USE_LEGACY_TOOLCHAIN_FILE=OFF \
        -DANDROID_ABI="$abi" -DANDROID_NATIVE_API_LEVEL=21 -DANDROID_STL=c++_shared \
        -DCMAKE_BUILD_TYPE=MinSizeRel \
        -DCMAKE_CXX_FLAGS_MINSIZEREL="-Os -DNDEBUG -fvisibility=hidden -ffunction-sections -fdata-sections" \
        -DCMAKE_C_FLAGS_MINSIZEREL="-Os -DNDEBUG -fvisibility=hidden -ffunction-sections -fdata-sections" \
        -DCMAKE_SHARED_LINKER_FLAGS="-Wl,--gc-sections -Wl,-z,max-page-size=16384" \
        -DOPENCV_EXTRA_MODULES_PATH=../opencv_contrib/modules -DBUILD_SHARED_LIBS=ON \
        -DBUILD_LIST=core,imgproc,imgcodecs,ml,quality \
        -DCPU_BASELINE="$cpu_baseline" \
        ${cpu_dispatch:+-DCPU_DISPATCH="$cpu_dispatch"} \
        -DBUILD_TESTS=OFF -DBUILD_PERF_TESTS=OFF -DBUILD_ANDROID_EXAMPLES=OFF -DBUILD_DOCS=OFF -DBUILD_opencv_java=OFF \
        -DBUILD_opencv_python2=OFF -DBUILD_opencv_python3=OFF -DBUILD_opencv_apps=OFF \
        -DBUILD_EXAMPLES=OFF -DBUILD_PACKAGE=OFF -DBUILD_FAT_JAVA_LIB=OFF \
        -DBUILD_ZLIB=ON -DBUILD_PNG=ON -DBUILD_JASPER=OFF -DBUILD_OPENEXR=OFF \
        -DWITH_JPEG=ON -DWITH_PNG=ON -DWITH_TIFF=OFF -DWITH_WEBP=OFF -DWITH_OPENEXR=OFF \
        -DWITH_JASPER=OFF -DWITH_OPENJPEG=OFF -DWITH_IMGCODEC_HDR=OFF -DWITH_IMGCODEC_SUNRASTER=OFF \
        -DWITH_IMGCODEC_PXM=OFF -DWITH_IMGCODEC_PFM=OFF \
        -DWITH_IPP=OFF -DWITH_EIGEN=OFF -DWITH_TBB=OFF -DWITH_OPENCL=OFF -DWITH_CUDA=OFF \
        -DWITH_OPENGL=OFF -DWITH_VTK=OFF -DWITH_GTK=OFF -DWITH_QT=OFF -DWITH_GSTREAMER=OFF \
        -DWITH_FFMPEG=OFF -DWITH_V4L=OFF -DWITH_1394=OFF -DWITH_ADE=OFF -DWITH_PROTOBUF=OFF \
        -DWITH_QUIRC=OFF -DWITH_LAPACK=OFF -DWITH_OBSENSOR=OFF -DWITH_ANDROID_MEDIANDK=OFF \
        -DBUILD_PROTOBUF=OFF -DBUILD_ITT=OFF -DBUILD_JAVA=OFF -DBUILD_OBJC=OFF \
        -DENABLE_LTO=ON -DENABLE_THIN_LTO=ON \
        -DOPENCV_ENABLE_NONFREE=OFF -DOPENCV_GENERATE_PKGCONFIG=OFF \
        -DCV_DISABLE_OPTIMIZATION=OFF -DCV_ENABLE_INTRINSICS=ON \
        ../opencv && make -j"$(nproc)"
    cd ../..
}

setup_opencv_libs() {
    local abi=$1
    local dst="app/src/full/jniLibs/$abi"
    build_opencv "$abi"
    mkdir -p "$dst"
    cp opencv/build_android/lib/"$abi"/libopencv_{core,imgproc,ml,imgcodecs,quality}.so "$dst/"
    cp "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/${ABI_ARCH[$abi]}/libc++_shared.so" "$dst/" 2>/dev/null || true
    [[ "$NO_CLEAN" != "true" ]] && "$STRIP" "$dst"/libopencv_*.so
    
    # build BRISQUE JNI
    # rm -f "$dst/libbrisque_jni.so"
    rm -rf "app/.cxx"

    BUILD_BRISQUE_JNI=ON ./gradlew clean -PtargetAbi="$abi" :app:externalNativeBuildFullDebug
    find app/build -path "*/$abi/*" -name "libbrisque_jni.so" -exec cp {} "$dst/" \;

    # strip and compress BRISQUE JNI library
    [[ "$BUILD_TYPE" == "release" && -n "${STRIP:-}" ]] && "$STRIP" "$dst/libbrisque_jni.so" 2>/dev/null || true
    compress_with_upx "$dst/libbrisque_jni.so"
    
    # compress OpenCV libraries
    for lib in "$dst"/libopencv_*.so; do
        compress_with_upx "$lib"
    done
}

setup_onnx_runtime() {
    local abi=$1
    local dst="app/src/main/jniLibs/$abi"
    mkdir -p "$dst"
    
    local tmp="$BUILDTEMP/onnx_$abi"
    if [[ ! -f "$tmp/jni/$abi/libonnxruntime.so" ]]; then
        mkdir -p "$tmp"
        curl -sL "$ONNX_MAVEN/$ONNX_VER/onnxruntime-android-$ONNX_VER.aar" -o "$tmp/onnx.aar"
        unzip -q "$tmp/onnx.aar" -d "$tmp"
    fi
    cp "$tmp/jni/$abi/libonnxruntime.so" "$dst/"
    
    # compress ONNX library
    compress_with_upx "$dst/libonnxruntime.so"

    log "ONNX $ONNX_VER set up for $abi under $dst"
}

setup_libs() {
    local abi=$1
    setup_onnx_runtime "$abi"
    [[ "$BUILD_VARIANT" == "full" ]] && setup_opencv_libs "$abi"
}

###### main build process ######
log "arch: $TARGET_ABI | compress: $USE_UPX | variant: $BUILD_VARIANT | build: $BUILD_TYPE | sign: $SIGN_APK"
[[ -n "${ANDROID_NDK_HOME:-}" ]] && log "NDK: $ANDROID_NDK_HOME"

# build native libraries
if [[ "$SKIP_LIB_BUILD" != "true" ]]; then
    abis=("${ALL_ABIS[@]}")
    [[ "$TARGET_ABI" != "all" ]] && abis=("$TARGET_ABI")
    for abi in "${abis[@]}"; do
        log "processing $abi..."
        setup_libs "$abi"
    done
fi

# exit if skipping gradle build
if [[ "$SKIP_GRADLE" == "true" ]]; then
    log "libraries built"
    exit 0
fi

# build APK
gradle_args=(clean -PskipBuildLibs=true)
variant_cap="${BUILD_VARIANT^}"
if [[ "$BUILD_TYPE" == "release" ]]; then
    gradle_args+=("assemble${variant_cap}Release")
    [[ "$SIGN_APK" == "true" ]] && gradle_args+=(-PsignApk=true)
else
    gradle_args+=("assemble${variant_cap}Debug")
fi
[[ "$TARGET_ABI" != "all" ]] && gradle_args+=(-PtargetAbi="$TARGET_ABI")
./gradlew "${gradle_args[@]}"

# cleanup sensitive env vars
[[ "$SIGN_APK" == "true" ]] && unset KEYSTORE_PATH KEYSTORE_PASSWORD KEYSTORE_ALIAS KEY_PASSWORD

# output location
if [[ "$SIGN_APK" == "true" ]]; then
    log "signed APK(s) built at apks/"
else
    log "APK(s) built at app/build/outputs/apk/"
fi
