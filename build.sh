#!/bin/bash

# ################################# #
#  DO NOT RUN THIS SCRIPT DIRECTLY  #
# unless you know what you're doing #
# ################################# #

NDK_VERSION="29.0.14206865"
CMAKE_VERSION="3.22.1"
OPENCV_COMMIT="52633170a7c3c427dbddd7836b13d46db1915e9e"
CONTRIB_COMMIT="abaddbcddf27554137d2fc4f0f70df013cf31a65"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OPENCV_DIR="${SCRIPT_DIR}/opencv"
ABIS=("arm64-v8a" "armeabi-v7a" "x86_64" "x86")

declare -A CPU_BASELINE=(["arm64-v8a"]="" ["armeabi-v7a"]="NEON" ["x86_64"]="SSE3" ["x86"]="SSE2")
declare -A CPU_DISPATCH=(["arm64-v8a"]="" ["armeabi-v7a"]="" ["x86_64"]="SSE4_2,AVX,AVX2" ["x86"]="SSE4_2,AVX")

export SOURCE_DATE_EPOCH=0
export TZ=UTC
export LANG=C.UTF-8

BUILD_JNI="${BUILD_JNI:-0}"

get_sdk_dir() {
    local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT}}"
    [ -z "$sdk" ] && { echo "SDK not found" >&2; exit 1; }
    echo "$sdk"
}

get_ndk_dir() {
    local ndk="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT}}"
    if [ -z "$ndk" ]; then
        ndk="$(get_sdk_dir)/ndk/${NDK_VERSION}"
    fi
    [ ! -d "$ndk" ] && { echo "NDK not found at $ndk" >&2; exit 1; }
    echo "$ndk"
}

get_cmake() {
    echo "$(get_sdk_dir)/cmake/${CMAKE_VERSION}/bin/cmake"
}

get_sdkmanager() {
    local sdkmanager="$(get_sdk_dir)/cmdline-tools/latest/bin/sdkmanager"
    [ ! -f "$sdkmanager" ] && sdkmanager="$(get_sdk_dir)/tools/bin/sdkmanager"
    [ ! -f "$sdkmanager" ] && { echo "sdkmanager not found" >&2; exit 1; }
    echo "$sdkmanager"
}

check_sdk() {
    local sdkmanager
    sdkmanager="$(get_sdkmanager)"
    yes | "$sdkmanager" --licenses
    "$sdkmanager" "platform-tools" "platforms;android-36" "build-tools;35.0.0" "ndk;${NDK_VERSION}" "cmake;${CMAKE_VERSION}" || {
        echo "failed to install SDK components" >&2
        exit 1
    }
}

check_cmake() {
    local cmake_path="$(get_cmake)"
    if [ ! -f "$cmake_path" ]; then
        echo "adding cmake ${CMAKE_VERSION}..."
        local sdkmanager
        sdkmanager="$(get_sdkmanager)"
        "$sdkmanager" "cmake;${CMAKE_VERSION}" || { echo "failed to install CMake" >&2; exit 1; }
    fi
}

# clone repos and checkout
check_opencv() {
    mkdir -p "$OPENCV_DIR"
    cd "$OPENCV_DIR"
    for repo in "opencv" "opencv_contrib"; do
        local commit="${OPENCV_COMMIT}"
        [ "$repo" = "opencv_contrib" ] && commit="${CONTRIB_COMMIT}"
        
        if [ ! -d "${repo}/.git" ]; then
            echo "cloning ${repo}..."
            rm -rf "$repo"
            git clone --single-branch -b 4.x "https://github.com/opencv/${repo}.git" # both to same branch
        else
            echo "fetching ${repo}..."
            cd "$repo"
            git fetch origin "$commit"
            cd ..
        fi
        echo "checking out ${repo} at ${commit}..."
        cd "$repo"
        git checkout "$commit"
        cd ..
    done
}

build_opencv_abi() {
    local abi="$1"
    local build_dir="${OPENCV_DIR}/build_android_${abi}"
    local install_dir="${build_dir}/install"
    mkdir -p "$build_dir"
    cd "$build_dir"
    local ndk="$(get_ndk_dir)"
    local cmake="$(get_cmake)"
    local flags="-ffunction-sections -fdata-sections -fvisibility=hidden -Os"
    local baseline="${CPU_BASELINE[$abi]}"
    local dispatch="${CPU_DISPATCH[$abi]}"

    local cmake_args=(
        "-Wno-deprecated"
        "-DCMAKE_TOOLCHAIN_FILE=${ndk}/build/cmake/android.toolchain.cmake"
        "-DANDROID_USE_LEGACY_TOOLCHAIN_FILE=OFF"
        "-DANDROID_ABI=${abi}"
        "-DANDROID_PLATFORM=android-24"
        "-DANDROID_STL=c++_static"
        "-DCMAKE_BUILD_TYPE=MinSizeRel"
        "-DCMAKE_INSTALL_PREFIX=${install_dir}"
        "-DBUILD_SHARED_LIBS=OFF"
        "-DOPENCV_EXTRA_MODULES_PATH=${OPENCV_DIR}/opencv_contrib/modules"
        "-DBUILD_LIST=core,imgproc,imgcodecs,ml,quality"
        "-DCPU_BASELINE=${baseline}"
        "-DBUILD_ZLIB=ON" "-DBUILD_PNG=ON" "-DBUILD_JPEG=ON"
        "-DWITH_JPEG=ON" "-DWITH_PNG=ON"
        "-DOPENCV_ENABLE_NONFREE=OFF" "-DOPENCV_GENERATE_PKGCONFIG=OFF"
        "-DBUILD_INFO_SKIP_SYSTEM_VERSION=ON" "-DBUILD_INFO_SKIP_TIMESTAMP=ON" # for reproducible builds
        "-DENABLE_LTO=ON"
        "-DCMAKE_CXX_FLAGS=${flags}" "-DCMAKE_C_FLAGS=${flags}"
    )

    [ -n "$dispatch" ] && cmake_args+=("-DCPU_DISPATCH=${dispatch}")
    for opt in TESTS PERF_TESTS ANDROID_EXAMPLES DOCS opencv_java opencv_python2 opencv_python3 opencv_apps EXAMPLES PACKAGE FAT_JAVA_LIB JASPER OPENEXR PROTOBUF JAVA OBJC ANDROID_PROJECTS; do
        cmake_args+=("-DBUILD_${opt}=OFF")
    done
    for opt in TIFF WEBP OPENEXR JASPER OPENJPEG IMGCODEC_HDR IMGCODEC_SUNRASTER IMGCODEC_PXM IMGCODEC_PFM IPP EIGEN TBB OPENCL CUDA OPENGL VTK GTK QT GSTREAMER FFMPEG V4L 1394 ADE PROTOBUF QUIRC LAPACK OBSENSOR ANDROID_MEDIANDK ITT; do
        cmake_args+=("-DWITH_${opt}=OFF")
    done
    cmake_args+=("${OPENCV_DIR}/opencv")
    "$cmake" "${cmake_args[@]}"
    make -j"$(nproc)" || make -j1
    make install
}

build_jni_abi() {
    local abi="$1"
    local jni_dir="${SCRIPT_DIR}/app/src/main/jniLibs/${abi}"
    local output="${jni_dir}/libbrisque_jni.so"
    [ -f "$output" ] && { echo "lib exists for ${abi}, exiting now"; return 0; }

    local build_dir="${SCRIPT_DIR}/build_native_${abi}"
    local ndk="$(get_ndk_dir)"
    local cmake="$(get_cmake)"

    echo "building JNI lib for ${abi}..."
    rm -rf "$build_dir"
    mkdir -p "$build_dir"
    cd "$build_dir"

    "$cmake" \
        -DCMAKE_TOOLCHAIN_FILE="${ndk}/build/cmake/android.toolchain.cmake" \
        -DANDROID_USE_LEGACY_TOOLCHAIN_FILE=OFF \
        -DANDROID_ABI="${abi}" \
        -DANDROID_PLATFORM=android-24 \
        -DANDROID_STL=c++_static \
        "${SCRIPT_DIR}/app/src/main/cpp"

    make -j"$(nproc)" || make -j1
    "${ndk}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded libbrisque_jni.so
    mkdir -p "$jni_dir"
    cp libbrisque_jni.so "$output"

    cd "$SCRIPT_DIR"
    rm -rf "$build_dir"
}

TARGET_ABI="${1:-arm64-v8a}"
[ "$TARGET_ABI" = "all" ] && TARGET_ABI="${ABIS[*]}"

check_sdk
check_cmake
check_opencv

for abi in $TARGET_ABI; do
    build_opencv_abi "$abi"
    if [ "$BUILD_JNI" = "1" ]; then
        build_jni_abi "$abi"
    fi
done
