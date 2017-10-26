cmake_minimum_required(VERSION 3.8)

set(CMAKE_MODULE_PATH ${CMAKE_CURRENT_LIST_DIR}/KotlinCMakeModule)

project(kotlin-native-demo Kotlin)


cinterop(
        NAME jansson
        DEF_FILE json/src/main/c_interop/jansson.def
)

cinterop(
        NAME libcurl
        DEF_FILE kurl/src/main/c_interop/libcurl.def
)

cinterop(
        NAME sqlite3
        DEF_FILE sql/src/main/c_interop/sqlite3.def
)

cinterop(
        NAME microhttpd
        DEF_FILE httpserver/src/main/c_interop/microhttpd.def
)

cinterop(
        NAME freetype
        DEF_FILE utils/src/main/c_interop/freetype.def
)

set(KOMMON_SRC "common/src/main/kotlin")
if (APPLE)
    set(KOMMON_SRC ${KOMMON_SRC} common/src/osx/kotlin)
elseif (LINUX)
    set(KOMMON_SRC ${KOMMON_SRC} common/src/linux/kotlin)
else ()
    message(FATAL_ERROR "Unsupported host")
endif ()

konanc_library(
        NAME kommon
        SOURCES ${KOMMON_SRC}
)

konanc_library(
        NAME kliopt
        SOURCES getopt/src/main/kotlin
)

konanc_library(
        NAME kjson
        SOURCES json/src/main/kotlin
        LIBRARIES jansson; kommon
)

konanc_library(
        NAME kurl
        SOURCES kurl/src/main/kotlin
        LIBRARIES libcurl; kommon
)

konanc_library(
        NAME sql
        SOURCES sql/src/main/kotlin
        LIBRARIES sqlite3; kommon
)

konanc_executable(
        NAME HttpServer
        SOURCES httpserver/src/main/kotlin
        TEST_SOURCES httpserver/src/test/kotlin
        LIBRARIES microhttpd; kjson; sql; kommon; kliopt
)

konanc_executable(
        NAME CliClient
        SOURCES clients/cli/src/main/kotlin
        LIBRARIES kommon; kliopt; kurl; kjson
)

konanc_executable(
        NAME FontGenerator
        SOURCES utils/src/FontGenerator/kotlin
        LIBRARIES freetype; kommon; kliopt
)

konanc_executable(
        NAME BmpConvertor
        SOURCES utils/src/BmpConvertor/kotlin
        LIBRARIES kommon
)