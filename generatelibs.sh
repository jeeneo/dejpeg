#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

###### configurable build params ######
# abis: arm64-v8a, armeabi-v7a, x86_64, x86, or all
TARGET_ABI="arm64-v8a"
# build_type: debug, release
BUILD_TYPE="release"
# upx: true/false (will attempt to download if not found in PATH)
USE_UPX=true
# build_variant: full (opencv + ONNX), lite (ONNX only)
BUILD_VARIANT="lite"
# no_clean: skip cleanup and reuse existing jniLibs
NO_CLEAN=false

###### constants - do not edit ######
ALL_ABIS=(arm64-v8a armeabi-v7a x86_64 x86)
UPX_BIN=""
BUILDTEMP="./buildtemp"
JNILIBS_ONNX="app/src/main/jniLibs"
JNILIBS_BRISQUE="app/src/full/jniLibs"
declare -A ABI_ARCH=([arm64-v8a]=aarch64-linux-android [armeabi-v7a]=arm-linux-androideabi [x86_64]=x86_64-linux-android [x86]=i686-linux-android)
declare -A CPU_BASE=([arm64-v8a]="" [armeabi-v7a]=NEON [x86_64]=SSE3 [x86]=SSE2)
declare -A CPU_DISP=([arm64-v8a]="" [armeabi-v7a]="" [x86_64]="SSE4_2,AVX,AVX2" [x86]="SSE4_2,AVX")

###### helpers ######
log() { echo -e "\033[0;34m[INFO]\033[0m $1"; }
err() { echo -e "\033[0;31m[ERROR]\033[0m $1" >&2; exit 1; }
require() {
    case "$1" in
        f) [[ -f "$2" ]] || err "$3";;
        d) [[ -d "$2" ]] || err "$3";;
        *) err "Invalid require flag: $1";;
    esac
}

get_prop() { grep -oP "$1=\\K.*" local.properties 2>/dev/null || true; }

cleanup() {
    if [[ "$NO_CLEAN" == "true" ]]; then
        return
    fi
    log "cleaning build artifacts"
    rm -rf "$JNILIBS_ONNX" "$JNILIBS_BRISQUE" opencv/build_android_* 2>/dev/null || true
}

compress_lib() {
    local f=$1
    [[ "$BUILD_TYPE" != "release" || ! -f "$f" ]] && return 0
    [[ "$(basename "$f")" == "libc++_shared.so" ]] && return 0
    [[ -n "${STRIP:-}" ]] && "$STRIP" "$f" 2>/dev/null || true
    $USE_UPX && [[ -n "$UPX_BIN" ]] && { chmod +x "$f"; "$UPX_BIN" --best --lzma --android-shlib "$f" 2>/dev/null || true; }
}

process_libs() {
    local search_paths=("$JNILIBS_ONNX")
    [[ "$BUILD_VARIANT" == "full" ]] && search_paths+=("$JNILIBS_BRISQUE")
    for search_path in "${search_paths[@]}"; do
        [[ ! -d "$search_path" ]] && { log "skipping lib processing: $search_path not found"; continue; }
        log "processing libs in $search_path"
        find "$search_path" -name "*.so" 2>/dev/null | while read -r lib; do compress_lib "$lib"; done || true
    done
}

###### argument parsing ######
while [[ $# -gt 0 ]]; do
    case $1 in
        --abi) TARGET_ABI="$2"; shift 2;;
        --debug) BUILD_TYPE="debug"; shift;;
        --no-upx) USE_UPX=false; shift;;
        --full) BUILD_VARIANT="full"; shift;;
        --no-cleanup) NO_CLEAN=true; shift;;
        --help) echo "Usage: $0 [--abi <abi|all>] [--debug] [--no-upx] [--full] [--no-cleanup]"; exit 0;;
        *) err "Unknown option: $1";;
    esac
done

[[ "$TARGET_ABI" != "all" && ! " ${ALL_ABIS[*]} " =~ " $TARGET_ABI " ]] && err "invalid ABI: $TARGET_ABI"

###### environment setup ######
mkdir -p "$BUILDTEMP"
[[ -z "${ANDROID_SDK_ROOT:-}" ]] && ANDROID_SDK_ROOT=$(get_prop "sdk.dir")
require d "${ANDROID_SDK_ROOT:-}" "Set ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT ANDROID_HOME="$ANDROID_SDK_ROOT"

if [[ "$BUILD_VARIANT" == "full" || "$BUILD_TYPE" == "release" ]]; then
    ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$(find "$ANDROID_SDK_ROOT/ndk" -maxdepth 1 -name "27.3.*" 2>/dev/null | head -1)}"
    require d "${ANDROID_NDK_HOME:-}" "NDK 27.3.x not found"
    export ANDROID_NDK_HOME
    STRIP="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
fi

ONNX_VER=$(grep -oP 'onnxruntimeAndroid\s*=\s*"\K[^"]+' gradle/libs.versions.toml 2>/dev/null || true)
[[ -z "$ONNX_VER" ]] && { read -rp "ONNX version: " ONNX_VER; [[ -z "$ONNX_VER" ]] && err "Version required"; }

# UPX setup
if $USE_UPX; then
    if command -v upx &>/dev/null; then
        UPX_BIN="upx"
    else
        log "UPX not found in PATH. Please install UPX using your system's package manager or download it manually from https://upx.github.io/ and add to PATH."
        exit 1
    fi
fi

###### cache invalidation ######
SKIP_LIB_BUILD=false
BUILD_SIG="$TARGET_ABI|$BUILD_VARIANT|$ONNX_VER|$USE_UPX"

if [[ -f "$BUILDTEMP/.build_sig" ]] && [[ "$(cat "$BUILDTEMP/.build_sig")" != "$BUILD_SIG" ]]; then
    log "build config changed, cleaning"; cleanup
fi
echo "$BUILD_SIG" > "$BUILDTEMP/.build_sig"s

# check existing libs
if [[ "$NO_CLEAN" == "true" ]]; then
    libs_exist=true
    if ! ls "$JNILIBS_ONNX"/*/libonnxruntime.so &>/dev/null 2>&1; then
        libs_exist=false
    fi
    if [[ "$BUILD_VARIANT" == "full" ]] && ! ls "$JNILIBS_BRISQUE"/*/libopencv_core.so &>/dev/null 2>&1; then
        libs_exist=false
    fi
    if [[ "$libs_exist" == "true" ]]; then
        log "reusing existing $BUILD_VARIANT libs"; SKIP_LIB_BUILD=true
    else
        log "incomplete libs found, rebuilding"
    fi
fi

###### build functions ######
build_opencv() {
    local abi=$1 build_dir="opencv/build_android_$abi"
    [[ -d "$build_dir" && "$NO_CLEAN" != "true" ]] && rm -rf "$build_dir"
    if [[ ! -d opencv/opencv ]]; then
        git clone https://github.com/opencv/opencv.git opencv/opencv || err "opencv clone failed"
    fi
    if [[ ! -d opencv/opencv_contrib ]]; then
        git clone https://github.com/opencv/opencv_contrib.git opencv/opencv_contrib || err "opencv-contrib clone failed"
    fi

    mkdir -p "$build_dir" && cd "$build_dir"
    local dispatch_arg=""; [[ -n "${CPU_DISP[$abi]}" ]] && dispatch_arg="-DCPU_DISPATCH=${CPU_DISP[$abi]}"
    cmake -Wno-deprecated -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_USE_LEGACY_TOOLCHAIN_FILE=OFF -DANDROID_ABI="$abi" -DANDROID_NATIVE_API_LEVEL=21 -DANDROID_STL=c++_shared \
        -DCMAKE_BUILD_TYPE=MinSizeRel \
        -DCMAKE_CXX_FLAGS_MINSIZEREL="-Os -DNDEBUG -fvisibility=hidden -ffunction-sections -fdata-sections" \
        -DCMAKE_C_FLAGS_MINSIZEREL="-Os -DNDEBUG -fvisibility=hidden -ffunction-sections -fdata-sections" \
        -DCMAKE_SHARED_LINKER_FLAGS="-Wl,--gc-sections -Wl,-z,max-page-size=16384" \
        -DOPENCV_EXTRA_MODULES_PATH=../opencv_contrib/modules -DBUILD_SHARED_LIBS=ON \
        -DBUILD_LIST=core,imgproc,imgcodecs,ml,quality -DCPU_BASELINE="${CPU_BASE[$abi]}" $dispatch_arg \
        -DBUILD_{TESTS,PERF_TESTS,ANDROID_EXAMPLES,DOCS,opencv_java,opencv_python2,opencv_python3,opencv_apps,EXAMPLES,PACKAGE,FAT_JAVA_LIB,JASPER,OPENEXR,PROTOBUF,ITT,JAVA,OBJC}=OFF \
        -DBUILD_ZLIB=ON -DBUILD_PNG=ON \
        -DWITH_{JPEG,PNG}=ON -DWITH_{TIFF,WEBP,OPENEXR,JASPER,OPENJPEG,IMGCODEC_HDR,IMGCODEC_SUNRASTER,IMGCODEC_PXM,IMGCODEC_PFM,IPP,EIGEN,TBB,OPENCL,CUDA,OPENGL,VTK,GTK,QT,GSTREAMER,FFMPEG,V4L,1394,ADE,PROTOBUF,QUIRC,LAPACK,OBSENSOR,ANDROID_MEDIANDK}=OFF \
        -DENABLE_LTO=ON -DENABLE_THIN_LTO=ON -DOPENCV_ENABLE_NONFREE=OFF -DOPENCV_GENERATE_PKGCONFIG=OFF \
        -DCV_DISABLE_OPTIMIZATION=OFF -DCV_ENABLE_INTRINSICS=ON ../opencv && { make -j"$(nproc)" || make -j1; }
    cd ../..
}

setup_onnx_runtime() {
    local abi=$1 dst="$JNILIBS_ONNX/$abi" tmp="$BUILDTEMP/onnx_$abi"
    local lib="$tmp/jni/$abi/libonnxruntime.so"
    mkdir -p "$dst"
    if [[ ! -f "$lib" ]]; then
        mkdir -p "$tmp"; log "Downloading ONNX Runtime $ONNX_VER for $abi"
        curl -sL "https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/$ONNX_VER/onnxruntime-android-$ONNX_VER.aar" -o "$tmp/onnx.aar" \
            || err "ONNX download failed"
        unzip -q "$tmp/onnx.aar" -d "$tmp" || err "ONNX extraction failed"
    fi
    require f "$lib" "ONNX library missing for $abi"
    cp "$lib" "$dst/" && log "ONNX $ONNX_VER ready for $abi"
}

setup_opencv_libs() {
    local abi=$1 build_dir="opencv/build_android_$abi" dst="$JNILIBS_BRISQUE/$abi"
    build_opencv "$abi"
    mkdir -p "$dst"
    for lib in core imgproc ml imgcodecs quality; do
        local src="$build_dir/lib/$abi/libopencv_$lib.so"
        require f "$src" "OpenCV build failed: libopencv_$lib.so missing"
        cp "$src" "$dst/"
    done
    cp "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/${ABI_ARCH[$abi]}/libc++_shared.so" "$dst/" 2>/dev/null || true
    log "building brisque_jni for $abi"
    BUILD_BRISQUE_JNI=ON ./gradlew clean -PtargetAbi="$abi" :app:externalNativeBuildFullDebug || err "BRISQUE JNI build failed"
    find app/build -path "*/$abi/*" -name "libbrisque_jni.so" -exec cp {} "$dst/" \;
    require f "$dst/libbrisque_jni.so" "BRISQUE JNI not found for $abi"
}

###### main build ######
log "arch: $TARGET_ABI | upx: $USE_UPX | variant: $BUILD_VARIANT | build: $BUILD_TYPE"
if [[ "$SKIP_LIB_BUILD" != "true" ]]; then
    abis=("${ALL_ABIS[@]}"); [[ "$TARGET_ABI" != "all" ]] && abis=("$TARGET_ABI")
    for abi in "${abis[@]}"; do
        log "building $abi..."; setup_onnx_runtime "$abi"
        [[ "$BUILD_VARIANT" == "full" ]] && setup_opencv_libs "$abi"
    done
    [[ "$BUILD_TYPE" == "release" ]] && process_libs
fi

log "lib generation complete"
