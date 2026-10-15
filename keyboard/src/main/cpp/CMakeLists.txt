# Dictée hors ligne de Fern Clavier : whisper.cpp (licence MIT) compilé pour le téléphone.
# Les sources de whisper.cpp sont téléchargées par la CI au moment de compiler (pas dans le dépôt).
cmake_minimum_required(VERSION 3.18)
project(fernwhisper CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)
FetchContent_Declare(
    whispercpp
    URL https://github.com/ggml-org/whisper.cpp/archive/refs/tags/v1.7.6.tar.gz
    DOWNLOAD_EXTRACT_TIMESTAMP TRUE
)

# Seulement la bibliothèque (pas d'exemples, de tests ni de serveur), en statique.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
# Pas d'OpenMP (ggml a sa propre réserve de fils), pas d'optimisation « pour la machine de la CI ».
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
# Fairphone 6 (Snapdragon 7s Gen 3) : instructions vectorielles fp16 et produit scalaire.
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)

FetchContent_MakeAvailable(whispercpp)

add_library(fernwhisper SHARED whisper_jni.cpp)
target_link_libraries(fernwhisper PRIVATE whisper log android)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(fernwhisper PRIVATE -O3 -fvisibility=hidden -ffunction-sections -fdata-sections)
    target_link_options(fernwhisper PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
endif ()
