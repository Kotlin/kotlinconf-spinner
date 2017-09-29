cmake_minimum_required(VERSION 3.8)

set(CMAKE_MODULE_PATH ${CMAKE_CURRENT_LIST_DIR}/KotlinCMakeModule)

project(kotlin-native-demo Kotlin)

cinterop(
        NAME common
        DEF_FILE common/src/common.def
)

cinterop(
        NAME jansson
        DEF_FILE json/src/jansson.def
        LIBRARIES common
)

cinterop(
        NAME libcurl
        DEF_FILE kurl/src/libcurl.def
        LIBRARIES common
)

cinterop(
        NAME sqlite3
        DEF_FILE sql/src/sqlite3.def
        LIBRARIES common
)

cinterop(
        NAME microhttpd
        DEF_FILE httpserver/microhttpd/microhttpd.def
        LIBRARIES common; jansson; sqlite3
)

set(KOMMON_SRC "common/src/Kommon.kt")
if (APPLE)
    set(KOMMON_SRC ${KOMMON_SRC} common/src/KommonOsx.kt)
elseif (LINUX)
    set(KOMMON_SRC ${KOMMON_SRC} common/src/KommonLinux.kt)
else ()
    message(FATAL_ERROR "Unsupported host")
endif ()

kotlinc_library(
        NAME kommon
        SOURCES ${KOMMON_SRC}
        LIBRARIES common
)

kotlinc_library(
        NAME kliopt
        SOURCES getopt/src
)

kotlinc_library(
        NAME kjson
        SOURCES json/src
        LIBRARIES jansson; kommon
)

kotlinc_library(
        NAME kurl
        SOURCES kurl/src
        LIBRARIES libcurl; kommon
)

kotlinc_library(
        NAME sql
        SOURCES sql/src
        LIBRARIES sqlite3; kommon
)

kotlinc(
        NAME HttpServer
        SOURCES httpserver/src
        LIBRARIES microhttpd; kjson; sql; kommon; kliopt
)

kotlinc(
        NAME CliClient
        SOURCES clients/cli/src
        LIBRARIES kommon; kliopt; kurl; kjson
)