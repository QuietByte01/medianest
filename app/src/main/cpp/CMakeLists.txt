cmake_minimum_required(VERSION 3.22.1)
project("medianest_lib")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# FFmpeg libraries provided by extracted native libraries
set(FFMPEG_LIBS avformat avcodec avutil swresample swscale avfilter)

# Path to the extracted native libraries
set(FFMPEG_DIR ${FFMPEG_EXTRACTION_DIR}/${ANDROID_ABI})

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

find_package(oboe REQUIRED CONFIG)

# Decoder-only native player C++ shared library for main app
add_library(medianest_lib SHARED
    ffmpeg_jni.cpp
    video_colorizer.cpp
)

# Alias target for backwards compatibility with existing JNI loaders
add_library(medianest_ffmpeg ALIAS medianest_lib)

target_include_directories(medianest_lib PRIVATE include)

# Ensure 16KB page size alignment for Android 15+ compatibility
target_link_options(medianest_lib PRIVATE "-Wl,-z,max-page-size=16384")

find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(medianest_lib
    ${FFMPEG_LIBS}
    oboe::oboe
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
)
