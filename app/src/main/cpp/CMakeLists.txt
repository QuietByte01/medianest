cmake_minimum_required(VERSION 3.22.1)
project("medianest_ffmpeg")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# FFmpeg libraries provided by ffmpeg-kit or bundled
set(FFMPEG_LIBS avformat avcodec avutil swresample swscale)

# Path to the merged native libraries from ffmpeg-kit
set(FFMPEG_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib/${ANDROID_ABI})

# Handle the _neon suffix for armeabi-v7a if present
set(LIB_SUFFIX "")
if(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(LIB_SUFFIX "_neon")
endif()

foreach(LIB ${FFMPEG_LIBS})
    add_library(${LIB} SHARED IMPORTED)
    set_target_properties(${LIB} PROPERTIES
        IMPORTED_LOCATION ${FFMPEG_DIR}/lib${LIB}${LIB_SUFFIX}.so
    )
endforeach()

add_library(medianest_ffmpeg SHARED
    ffmpeg_jni.cpp
)

target_include_directories(medianest_ffmpeg PRIVATE include)

# Ensure 16KB page size alignment for Android 15+ compatibility
target_link_options(medianest_ffmpeg PRIVATE "-Wl,-z,max-page-size=16384")

find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(medianest_ffmpeg
    ${FFMPEG_LIBS}
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
)
