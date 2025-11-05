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
    set(OPENCV_BUILD_DIR "/home/linux/source/openvc/build_android/install/sdk/native")
    include_directories(${OPENCV_BUILD_DIR}/jni/include)
    set(OPENCV_LIB_DIR "${OPENCV_BUILD_DIR}/libs/${ANDROID_ABI}")
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
        LINK_FLAGS "-Wl,-rpath,/system/lib64:/data/data/com.je.dejpeg.debug/lib"
    )
else()
    message(STATUS "not building brisque_jni")
endif()