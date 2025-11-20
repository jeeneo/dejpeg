cmake_minimum_required(VERSION 3.10)
project(brisque_jni)
# Default is OFF (use pre-built binaries from jniLibs/)
# Set BUILD_BRISQUE_JNI=ON to compile
if(DEFINED ENV{BUILD_BRISQUE_JNI})
    set(CMAKE_BUILD_BRISQUE $ENV{BUILD_BRISQUE_JNI})
else()
    set(CMAKE_BUILD_BRISQUE OFF)
endif()
if(CMAKE_BUILD_BRISQUE)
    get_filename_component(PROJECT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
    set(OPENCV_SRC_DIR "${PROJECT_ROOT}/opencv/opencv")
    set(OPENCV_CONTRIB_DIR "${PROJECT_ROOT}/opencv/opencv_contrib")
    set(OPENCV_BUILD_DIR "${PROJECT_ROOT}/opencv/build_android")
    include_directories(
        ${OPENCV_SRC_DIR}/modules/core/include
        ${OPENCV_SRC_DIR}/modules/imgproc/include
        ${OPENCV_SRC_DIR}/modules/imgcodecs/include
        ${OPENCV_SRC_DIR}/modules/ml/include
        ${OPENCV_CONTRIB_DIR}/modules/quality/include
        ${OPENCV_BUILD_DIR}
        ${OPENCV_BUILD_DIR}/opencv2
    )
    set(OPENCV_LIB_DIR "${OPENCV_BUILD_DIR}/lib/${ANDROID_ABI}")
    add_library(brisque_jni SHARED brisque_assessor.cpp)
    target_link_directories(brisque_jni PRIVATE ${OPENCV_LIB_DIR})
    target_link_libraries(brisque_jni 
        opencv_core
        opencv_imgproc
        opencv_imgcodecs
        opencv_quality
        log
    )
    set_target_properties(brisque_jni PROPERTIES
        CXX_STANDARD 17
        CXX_STANDARD_REQUIRED ON
        LINK_FLAGS "-Wl,-rpath,/system/lib64:/data/data/com.je.dejpeg.debug/lib -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=4096"
    )
else()
    message(STATUS "not building brisque_jni")
endif()
