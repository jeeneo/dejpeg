cmake_minimum_required(VERSION 3.22.1)
project("oidn_jni")

set(PROJECT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../..")
set(OIDN_INSTALL_DIR "${PROJECT_ROOT}/odinroot/build/oidn-android-arm64-static")
set(OIDN_STATIC_LIBS_DIR "${OIDN_INSTALL_DIR}/lib")
set(OIDN_INCLUDE_DIR     "${OIDN_INSTALL_DIR}/include")

add_library(oidn_main       STATIC IMPORTED)
add_library(oidn_core       STATIC IMPORTED)
add_library(oidn_device_cpu STATIC IMPORTED)
add_library(tbb             STATIC IMPORTED)
add_library(tbbmalloc       STATIC IMPORTED)

set_target_properties(oidn_main PROPERTIES 
    IMPORTED_LOCATION "${OIDN_STATIC_LIBS_DIR}/libOpenImageDenoise.a")
set_target_properties(oidn_core PROPERTIES 
    IMPORTED_LOCATION "${OIDN_STATIC_LIBS_DIR}/libOpenImageDenoise_core.a")
set_target_properties(oidn_device_cpu PROPERTIES 
    IMPORTED_LOCATION "${OIDN_STATIC_LIBS_DIR}/libOpenImageDenoise_device_cpu.a")
set_target_properties(tbb PROPERTIES 
    IMPORTED_LOCATION "${OIDN_STATIC_LIBS_DIR}/libtbb.a")
set_target_properties(tbbmalloc PROPERTIES 
    IMPORTED_LOCATION "${OIDN_STATIC_LIBS_DIR}/libtbbmalloc.a")

add_library(oidn_jni SHARED oidn_jni.cpp)

target_include_directories(oidn_jni PRIVATE ${OIDN_INCLUDE_DIR})

target_link_libraries(oidn_jni
    -Wl,--whole-archive
    oidn_main
    oidn_core
    oidn_device_cpu
    -Wl,--no-whole-archive
    tbb
    tbbmalloc
    log
    android
    dl
    m
    z)
