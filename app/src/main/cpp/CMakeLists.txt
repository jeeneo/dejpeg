cmake_minimum_required(VERSION 3.10)
project(brisque_jni)

get_filename_component(PROJECT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
set(OPENCV_BUILD "${PROJECT_ROOT}/opencv/build_android_${ANDROID_ABI}")
set(STATIC_DIR "${OPENCV_BUILD}/install/sdk/native")
set(THIRDPARTY_DIR "${OPENCV_BUILD}/3rdparty/lib/${ANDROID_ABI}")

include_directories("${STATIC_DIR}/jni/include")
add_library(brisque_jni SHARED brisque_assessor.cpp)
target_link_libraries(brisque_jni
    "${STATIC_DIR}/staticlibs/${ANDROID_ABI}/libopencv_quality.a"
    "${STATIC_DIR}/staticlibs/${ANDROID_ABI}/libopencv_ml.a"
    "${STATIC_DIR}/staticlibs/${ANDROID_ABI}/libopencv_imgcodecs.a"
    "${STATIC_DIR}/staticlibs/${ANDROID_ABI}/libopencv_imgproc.a"
    "${STATIC_DIR}/staticlibs/${ANDROID_ABI}/libopencv_core.a"
)

target_link_libraries(brisque_jni
    "${THIRDPARTY_DIR}/liblibpng.a"
    "${THIRDPARTY_DIR}/liblibjpeg-turbo.a"
    "${THIRDPARTY_DIR}/libzlib.a"
    "${THIRDPARTY_DIR}/libcpufeatures.a"
    "${THIRDPARTY_DIR}/libkleidicv.a"
    "${THIRDPARTY_DIR}/libkleidicv_thread.a"
    "${THIRDPARTY_DIR}/libkleidicv_hal.a"
)

target_link_libraries(brisque_jni log z dl m atomic)

target_compile_options(brisque_jni PRIVATE 
    -Os 
    -ffunction-sections 
    -fdata-sections 
    -fvisibility=hidden
    -frandom-seed=brisque_jni
    -fdebug-prefix-map=${CMAKE_SOURCE_DIR}=/workspace/app/src/main/cpp
    -fdebug-prefix-map=${PROJECT_ROOT}=/workspace
)

target_link_options(brisque_jni PRIVATE 
    -Wl,--gc-sections 
    -Wl,--exclude-libs,ALL 
    -Wl,-z,max-page-size=16384
    -Wl,--build-id=none
    -Wl,--hash-style=gnu
)

set_target_properties(brisque_jni PROPERTIES 
    CXX_STANDARD 17 
    CXX_STANDARD_REQUIRED ON
)
