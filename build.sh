#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

###### user-configurable build params ######

# below are the default build params (and other values)
# abis: arm64-v8a (default), armeabi-v7a, x86_64, x86, or all
TARGET_ABI="arm64-v8a"

# build_type: debug, release (forced true for signing, default is release)
BUILD_TYPE="release"

# upx: true/false (true by default, will attempt to download if not found in PATH)
USE_UPX=true

# build_variant: full, lite (lite by default)
# full = opencv (brisque) + ONNX, lite = ONNX only
BUILD_VARIANT="lite"

# sign_apk: true/false (internal, for release builds only, false by default)
SIGN_APK=false

# no_clean: true/false (internal, will skip cleanup and reuse existing jniLibs if true, false by default)
# user is expected to delete jniLibs manually
NO_CLEAN=false

###### do not edit below this line ######
ALL_ABIS=(arm64-v8a armeabi-v7a x86_64 x86)
UPX_URL="https://github.com/upx/upx/releases/download/v5.1.0/upx-5.1.0-amd64_linux.tar.xz"
ONNX_MAVEN="https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android"
BUILDTEMP="./buildtemp"
UPX_BIN=""

cleanup() {
    if [[ -d app/src/main/jniLibs || -d app/src/full/jniLibs || -d app/src/lite/jniLibs ]]; then
        log "deleting native libraries from source directory..."
        rm -rf app/src/main/jniLibs app/src/full/jniLibs app/src/lite/jniLibs
    fi
    if [[ -d "opencv/build_android" ]]; then
        log "deleting OpenCV"
        rm -rf opencv/build_android
    fi
}

log() { echo -e "\033[0;34m[INFO]\033[0m $1"; }
err() { echo -e "\033[0;31m[ERROR]\033[0m $1" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
    case $1 in
        --abi) TARGET_ABI="$2"; shift 2;;
        --debug) BUILD_TYPE="debug"; shift;;
        --no-upx) USE_UPX=false; shift;;
        --sign) SIGN_APK=true; BUILD_TYPE="release"; shift;;
        --full) BUILD_VARIANT="full"; shift;;
        --lite) BUILD_VARIANT="lite"; shift;;
        --no-cleanup) NO_CLEAN=true; shift;;
        --help) echo "Usage: $0 [--abi <abi|all>] [--debug] [--no-upx] [--sign] [--full] [--lite] [--no-cleanup]"; exit 0;;
        *) err "Unknown option: $1";;
    esac
done

# skip cleanup
[[ "$NO_CLEAN" != "true" ]] && trap cleanup EXIT

# check if jniLibs already exist when in no-cleanup mode
if [[ "$NO_CLEAN" == "true" && ( -d "app/src/main/jniLibs" || -d "app/src/full/jniLibs" || -d "app/src/lite/jniLibs" ) ]]; then
    log "libraries already exist"
    exit 0
fi

# force release if signing
[[ "$SIGN_APK" == "true" && "$BUILD_TYPE" == "debug" ]] && { log "Ignoring --debug, signing requires a release"; BUILD_TYPE="release"; }

# keystore validation
if [[ "$SIGN_APK" == "true" ]]; then
    KEYSTORE_PATH="${KEYSTORE_PATH:-$(grep -oP 'keystore\.path=\K.*' local.properties 2>/dev/null || true)}"
    KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-$(grep -oP 'keystore\.password=\K.*' local.properties 2>/dev/null || true)}"
    KEYSTORE_ALIAS="${KEYSTORE_ALIAS:-$(grep -oP 'keystore\.alias=\K.*' local.properties 2>/dev/null || true)}"
    KEY_PASSWORD="${KEY_PASSWORD:-$(grep -oP 'keystore\.keyPassword=\K.*' local.properties 2>/dev/null || true)}"
    
    [[ -z "$KEYSTORE_PATH" ]] && err "KEYSTORE_PATH not set. Set via environment variable or keystore.path in local.properties"
    [[ ! -f "$KEYSTORE_PATH" ]] && err "Keystore file not found: $KEYSTORE_PATH"
    [[ -z "$KEYSTORE_PASSWORD" ]] && err "KEYSTORE_PASSWORD not set. Set via environment variable or keystore.password in local.properties"
    [[ -z "$KEYSTORE_ALIAS" ]] && err "KEYSTORE_ALIAS not set. Set via environment variable or keystore.alias in local.properties"
    [[ -z "$KEY_PASSWORD" ]] && err "KEY_PASSWORD not set. Set via environment variable or keystore.keyPassword in local.properties"

    export KEYSTORE_PATH KEYSTORE_PASSWORD KEYSTORE_ALIAS KEY_PASSWORD
fi

# grab NDK from local.properties or environment, prefer environment if both are set
[[ -z "${ANDROID_SDK_ROOT:-}" ]] && ANDROID_SDK_ROOT=$(grep "sdk.dir" local.properties 2>/dev/null | cut -d= -f2 || true)
[[ -z "$ANDROID_SDK_ROOT" || ! -d "$ANDROID_SDK_ROOT" ]] && err "Set ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT ANDROID_HOME="$ANDROID_SDK_ROOT"

if [[ "$BUILD_VARIANT" == "full" ]]; then
    ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$(find "$ANDROID_SDK_ROOT/ndk" -maxdepth 1 -name "27.3.*" 2>/dev/null | head -1)}"
    [[ -z "$ANDROID_NDK_HOME" || ! -d "$ANDROID_NDK_HOME" ]] && err "NDK 27.3.x not found"
    export ANDROID_NDK_HOME
    STRIP="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
fi

# check and grab onnx aar
ONNX_VER=$(grep -oP 'onnxruntimeAndroid\s*=\s*"\K[^"]+' gradle/libs.versions.toml 2>/dev/null || true)
[[ -z "$ONNX_VER" ]] && { read -rp "ONNX Runtime version: " ONNX_VER; [[ -z "$ONNX_VER" ]] && err "Version required"; }

# check if ONNX version changed and clean buildtemp if needed
if [[ -f "$BUILDTEMP/.onnx_version" ]]; then
    STORED_VER=$(cat "$BUILDTEMP/.onnx_version")
    if [[ "$STORED_VER" != "$ONNX_VER" ]]; then
        log "ONNX version changed ($STORED_VER -> $ONNX_VER), cleaning buildtemp"
        rm -rf "$BUILDTEMP"
    fi
fi

mkdir -p "$BUILDTEMP"
echo "$ONNX_VER" > "$BUILDTEMP/.onnx_version"

# check/download upx if needed
if $USE_UPX; then
    if command -v upx &>/dev/null; then UPX_BIN="upx"
    else
        read -rp "UPX not found. Download? [Y/n]: " r
        if [[ "${r:-Y}" =~ ^[Yy]$ ]]; then
            curl -L "$UPX_URL" | tar -xJ -C "$BUILDTEMP" && UPX_BIN="$BUILDTEMP/upx-5.1.0-amd64_linux/upx"
        else USE_UPX=false; fi
    fi
fi

# ABI helpers
declare -A ABI_ARCH=([arm64-v8a]=aarch64-linux-android [armeabi-v7a]=arm-linux-androideabi [x86_64]=x86_64-linux-android [x86]=i686-linux-android)

build_opencv() {
    if [[ -d "opencv/build_android" ]]; then
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
            cpu_baseline="NEON"
            cpu_dispatch="NEON_FP16"
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
    mkdir -p "$dst"
    
    build_opencv "$abi"
    cp opencv/build_android/lib/"$abi"/libopencv_{core,imgproc,ml,imgcodecs,quality}.so "$dst/"
    cp "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/${ABI_ARCH[$abi]}/libc++_shared.so" "$dst/" 2>/dev/null || true
    [[ "$NO_CLEAN" != "true" ]] && "$STRIP" "$dst"/libopencv_*.so
    
    # build BRISQUE JNI
    rm -f "$dst/libbrisque_jni.so"
    BUILD_BRISQUE_JNI=ON ./gradlew -PtargetAbi="$abi" :app:externalNativeBuildFullDebug
    find app/build -path "*/$abi/*" -name "libbrisque_jni.so" -exec cp {} "$dst/" \;
    
    # UPX compress OpenCV and BRISQUE JNI
    if $USE_UPX && [[ -n "$UPX_BIN" ]]; then
        for lib in "$dst"/libopencv_*.so "$dst/libbrisque_jni.so"; do
            [[ -f "$lib" ]] && chmod +x "$lib" && "$UPX_BIN" --best --lzma --android-shlib "$lib" 2>/dev/null || true
        done
    fi
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
    
    # UPX compress ONNX
    if $USE_UPX && [[ -n "$UPX_BIN" ]]; then
        [[ -f "$dst/libonnxruntime.so" ]] && chmod +x "$dst/libonnxruntime.so" && "$UPX_BIN" --best --lzma --android-shlib "$dst/libonnxruntime.so" 2>/dev/null || true
    fi

    log "ONNX $ONNX_VER set up for $abi under $dst"
}

setup_libs() {
    local abi=$1
    setup_onnx_runtime "$abi"
    if [[ "$BUILD_VARIANT" == "full" ]]; then
        setup_opencv_libs "$abi"
    fi
}

log "arch: $TARGET_ABI | compress: $USE_UPX | variant: $BUILD_VARIANT | build: $BUILD_TYPE | sign: $SIGN_APK"
[[ -n "${ANDROID_NDK_HOME:-}" ]] && log "NDK: $ANDROID_NDK_HOME"

abis=("${ALL_ABIS[@]}"); [[ "$TARGET_ABI" != "all" ]] && abis=("$TARGET_ABI")
for abi in "${abis[@]}"; do log "processing $abi..."; setup_libs "$abi"; done

# exit in no-cleanup mode
if [[ "$NO_CLEAN" == "true" ]]; then
    log "libraries built"
    exit 0
fi

gradle_args=(clean)
variant_cap="${BUILD_VARIANT^}"
if [[ "$BUILD_TYPE" == "release" ]]; then
    gradle_args+=("assemble${variant_cap}Release")
    [[ "$SIGN_APK" == "true" ]] && gradle_args+=(-PsignApk=true)
else
    gradle_args+=("assemble${variant_cap}Debug")
fi
[[ "$TARGET_ABI" != "all" ]] && gradle_args+=(-PtargetAbi="$TARGET_ABI")
./gradlew "${gradle_args[@]}"

if [[ "$SIGN_APK" == "true" ]]; then
    unset KEYSTORE_PATH KEYSTORE_PASSWORD KEYSTORE_ALIAS KEY_PASSWORD
fi

if [[ "$SIGN_APK" == "true" ]]; then
    log "signed APK(s) built at apks/"
else
    log "unsigned APK(s) built at app/build/outputs/apk/"
fi
