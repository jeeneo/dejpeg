set -euo pipefail

NDK_ROOT="$(realpath "${ANDROID_NDK_ROOT:-${ANDROID_HOME}/ndk/29.0.14206865}")"
ISPC="$(realpath "${ISPC_EXECUTABLE:-./odinroot/ispc/ispc-v1.30.0-linux/bin/ispc}")"
TBB_SRC="$(realpath "${TBB_SOURCE_DIR:-./odinroot/oneTBB}")"
OIDN_SRC="$(realpath "${OIDN_SOURCE_DIR:-./odinroot/odin}")"
BUILD="$(mkdir -p "${1:-./odinroot/build/build-android-static}" && realpath "${1:-./odinroot/build/build-android-static}")"
INSTALL="$(mkdir -p "${2:-./odinroot/build/oidn-android-arm64-static}" && realpath "${2:-./odinroot/build/oidn-android-arm64-static}")"
TOOLCHAIN="$NDK_ROOT/build/cmake/android.toolchain.cmake"

for path in "$NDK_ROOT" "$ISPC" "$TBB_SRC" "$OIDN_SRC" "$TOOLCHAIN"; do
  if [ ! -e "$path" ]; then
    echo "ERROR: Path does not exist: $path"
    exit 1
  fi
done

rm -rf "$BUILD/tbb-static-build" "$BUILD/tbb-static-install"
mkdir -p "$BUILD/tbb-static-build"
cmake -S "$TBB_SRC" -B "$BUILD/tbb-static-build" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_ROOT/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="arm64-v8a" \
    -DANDROID_PLATFORM="android-21" \
    -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=OFF \
    -DTBB_TEST=OFF \
    -DTBB_STRICT=OFF \
    -DCMAKE_INSTALL_PREFIX="$BUILD/tbb-static-install"

cmake --build "$BUILD/tbb-static-build" --parallel "$(nproc)"
cmake --install "$BUILD/tbb-static-build"
ls -lh "$BUILD/tbb-static-install/lib/"*.a
rm -rf "$BUILD/oidn-build"
mkdir -p "$BUILD/oidn-build"
cmake -S "$OIDN_SRC" -B "$BUILD/oidn-build" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_ROOT/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="arm64-v8a" \
    -DANDROID_PLATFORM="android-21" \
    -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DOIDN_DEVICE_CPU=ON \
    -DOIDN_DEVICE_SYCL=OFF \
    -DOIDN_DEVICE_CUDA=OFF \
    -DOIDN_DEVICE_HIP=OFF \
    -DOIDN_FILTER_RT=ON \
    -DOIDN_FILTER_RTLIGHTMAP=OFF \
    -DOIDN_STATIC_LIB=ON \
    -DOIDN_LIBRARY_VERSIONED=OFF \
    -DOIDN_APPS=OFF \
    -DISPC_EXECUTABLE="$ISPC" \
    -DTBB_DIR="$BUILD/tbb-static-install/lib/cmake/TBB" \
    -DCMAKE_INSTALL_PREFIX="$INSTALL"

cmake --build "$BUILD/oidn-build" --parallel "$(nproc)"
cmake --install "$BUILD/oidn-build"
cp -v "$BUILD/tbb-static-install/lib/"*.a "$INSTALL/lib/"

echo ""
echo ""
echo "done, run gradlew"
