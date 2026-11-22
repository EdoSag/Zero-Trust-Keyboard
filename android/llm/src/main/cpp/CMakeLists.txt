# SPDX-License-Identifier: AGPL-3.0-only
# Builds libzt_llm.so: llama.cpp and ggml (both MIT) linked statically behind a small JNI
# layer. CPU only, no network code. The model is a file the user downloaded.
cmake_minimum_required(VERSION 3.22.1)
# Always optimised, also in debug builds of the app.
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
project(zt_llm C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# llama.cpp release, fetched at build time. To take a newer release change the tag and the
# hash together (sha256 of the tag's source archive). A build stops when the archive differs.
set(ZT_LLAMA_TAG "b12024")
set(ZT_LLAMA_SHA256 "39f4f84d61ed0b2e65146ed04d56fc108c59c39c6328be0c878ad706d091ad23")

include(FetchContent)
FetchContent_Declare(llama
    URL "https://github.com/ggml-org/llama.cpp/archive/refs/tags/${ZT_LLAMA_TAG}.tar.gz"
    URL_HASH "SHA256=${ZT_LLAMA_SHA256}")

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
# No downloads from inside the library: the app fetches the model itself.
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
# ggml's own thread pool; OpenMP would need libomp.so in the APK.
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
# One build must run on every phone of the ABI, so no -march=native.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(llama)

add_library(zt_llm SHARED zt_llm_jni.cpp)
target_link_libraries(zt_llm PRIVATE llama log)
target_compile_options(zt_llm PRIVATE -fvisibility=hidden -ffunction-sections -fdata-sections)
target_link_options(zt_llm PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
