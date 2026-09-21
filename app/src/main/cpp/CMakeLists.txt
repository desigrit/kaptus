cmake_minimum_required(VERSION 3.22.1)
project(kaptus_whisper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)

# Android Studio installs the Debug variant during normal device testing. Keep native speech
# inference optimized in that variant as well, while retaining debug symbols and Kotlin/JNI
# debuggability. This option is inherited by whisper.cpp and GGML below.
if(ANDROID)
    add_compile_options(-O3)
endif()

include(FetchContent)
FetchContent_Declare(
    whisper_cpp
    URL https://github.com/ggml-org/whisper.cpp/archive/927cfce34f31707e17f2bff35c349632fb9e2c3a.tar.gz
    URL_HASH SHA256=41b664fee09e79176ac277b5237debec34f8d74af3c7d71f333f1ec67989ecde
)
FetchContent_MakeAvailable(whisper_cpp)

add_library(kaptus_whisper SHARED kaptus_whisper.cpp)
target_include_directories(kaptus_whisper PRIVATE ${whisper_cpp_SOURCE_DIR}/include)
target_link_libraries(kaptus_whisper PRIVATE whisper android log)

if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(kaptus_whisper PRIVATE -O3 -fvisibility=hidden -ffunction-sections -fdata-sections)
    target_link_options(kaptus_whisper PRIVATE -Wl,--gc-sections)
endif()
