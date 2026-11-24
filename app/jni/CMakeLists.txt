# Gold Box Game Engine for Android: libSDL2.so (SDL, as SDL builds itself)
# and libmain.so - the CYD engine and the PortMaster port's SDL2 front end,
# the same sources the port's own build uses (pm/CMakeLists.txt).
cmake_minimum_required(VERSION 3.16)
project(goldbox_android C CXX)

get_filename_component(APP_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../.." ABSOLUTE)
set(PM_ROOT "${APP_ROOT}/pm")
set(SDL_DIR "${APP_ROOT}/SDL")

if(NOT EXISTS "${PM_ROOT}/cyd/src/game/play.cpp")
    message(FATAL_ERROR "The front end / engine aren't here: run  git submodule update --init --recursive")
endif()
if(NOT EXISTS "${SDL_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "SDL isn't here: run  git submodule update --init")
endif()

# (the version the game list shows: the app's)
file(STRINGS "${APP_ROOT}/version.properties" PM_VERSION REGEX "^versionName=")
string(REPLACE "versionName=" "" PM_VERSION "${PM_VERSION}")
file(STRINGS "${PM_ROOT}/cyd/VERSION" CYD_VERSION LIMIT_COUNT 1)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS ON)
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# SDL2 (shared: SDLActivity loads libSDL2.so, then libmain.so)
set(SDL_SHARED ON CACHE BOOL "" FORCE)
set(SDL_STATIC OFF CACHE BOOL "" FORCE)
set(SDL_TEST OFF CACHE BOOL "" FORCE)
add_subdirectory("${SDL_DIR}" SDL)

# The engine, built unchanged
file(GLOB ENGINE_SOURCES CONFIGURE_DEPENDS "${PM_ROOT}/cyd/src/engine/*.cpp")
add_library(gbengine STATIC ${ENGINE_SOURCES}
    "${PM_ROOT}/cyd/src/game/play.cpp" "${PM_ROOT}/cyd/src/game/companion.cpp"
    "${PM_ROOT}/cyd/src/game/music_make.cpp" "${PM_ROOT}/cyd/src/game/tidy.cpp" "${PM_ROOT}/src/compat.cpp")
target_include_directories(gbengine PUBLIC "${PM_ROOT}/cyd/src")
target_compile_options(gbengine PRIVATE -O2 -Wno-unknown-warning-option
    -include "${PM_ROOT}/src/compat.h")

# The front end: the PortMaster port's, with its Android parts (__ANDROID__,
# touchpad.cpp)
set(FRONT "${PM_ROOT}/src")
add_library(main SHARED
    ${FRONT}/main.cpp ${FRONT}/video.cpp ${FRONT}/library.cpp ${FRONT}/settings.cpp
    ${FRONT}/platform_sdl.cpp ${FRONT}/audio_sdl.cpp ${FRONT}/task.cpp ${FRONT}/art.cpp
    ${FRONT}/panel.cpp ${FRONT}/jour.cpp ${FRONT}/touchpad.cpp ${FRONT}/shell.cpp)
target_compile_definitions(main PRIVATE PM_VERSION="${PM_VERSION}" CYD_VERSION="${CYD_VERSION}")
target_compile_options(main PRIVATE -O2 -Wall)
target_link_libraries(main PRIVATE gbengine SDL2::SDL2 log android)
