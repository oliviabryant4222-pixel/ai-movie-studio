cmake_minimum_required(VERSION 3.22.1)
project(realframe_sd_native LANGUAGES C CXX)

# Copyright (c) leejet, stable-diffusion.cpp (MIT). Native sources are fetched
# at a pinned commit by tools/fetch_native_engine.sh before building the APK.
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(SD_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SD_WEBP OFF CACHE BOOL "" FORCE)
set(SD_WEBM OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_GGML_LIB OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

set(SDCPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/vendor/stable-diffusion.cpp")
if(NOT EXISTS "${SDCPP_DIR}/CMakeLists.txt" OR NOT EXISTS "${SDCPP_DIR}/ggml/CMakeLists.txt")
    message(FATAL_ERROR
        "stable-diffusion.cpp missing. Run tools/fetch_native_engine.sh "
        "from the realframe project root before compiling Android APK.")
endif()

add_subdirectory("${SDCPP_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/sdcpp" EXCLUDE_FROM_ALL)

add_library(realframe_sd SHARED realframe_sd_jni.cpp)
target_include_directories(realframe_sd PRIVATE "${SDCPP_DIR}/include")
target_compile_options(realframe_sd PRIVATE -fexceptions -frtti)
find_library(android_log log)
target_link_libraries(realframe_sd PRIVATE stable-diffusion ${android_log} android)
