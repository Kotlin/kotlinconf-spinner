cmake_minimum_required(VERSION 3.8)

set(CMAKE_MODULE_PATH ${CMAKE_CURRENT_LIST_DIR}/KotlinCMakeModule)

project(kotlin-native-demo Kotlin)

konanc_library_search(jansson jansson.h)
cinterop(
        NAME jansson
        DEF_FILE json/src/main/c_interop/jansson.def
        COMPILER_OPTS "-I ${JANSSON_INCLUDE_DIR}"
)

konanc_library_search(curl curl/curl.h)
cinterop(
        NAME libcurl
        DEF_FILE kurl/src/main/c_interop/libcurl.def
        COMPILER_OPTS "-I ${CURL_INCLUDE_DIR}"
)

konanc_library_search(sqlite3 sqlite3.h)
cinterop(
        NAME sqlite3
        DEF_FILE sql/src/main/c_interop/sqlite3.def
        COMPILER_OPTS "-I ${SQLITE3_INCLUDE_DIR}"
)

konanc_library_search(microhttpd microhttpd.h)
cinterop(
        NAME microhttpd
        DEF_FILE httpserver/src/main/c_interop/microhttpd.def
        COMPILER_OPTS "-I ${MICROHTTPD_INCLUDE_DIR}"
)

konanc_library_search(freetype freetype.h)
cinterop(
        NAME freetype
        DEF_FILE utils/src/main/c_interop/freetype.def
        COMPILER_OPTS "-I ${FREETYPE_INCLUDE_DIR} -I ${FREETYPE_INCLUDE_DIR}/.."
)

set(COMMON_SRC "common/src/main/kotlin")
if (APPLE)
    set(COMMON_SRC ${COMMON_SRC} common/src/osx/kotlin)
elseif (${CMAKE_SYSTEM_NAME} MATCHES "Linux")
    set(COMMON_SRC ${COMMON_SRC} common/src/linux/kotlin)
else ()
    message(FATAL_ERROR "Unsupported host")
endif ()

konanc_library(
        NAME common
        SOURCES ${COMMON_SRC}
)

konanc_library(
        NAME kliopt
        SOURCES getopt/src/main/kotlin
)

konanc_library(
        NAME kjson
        SOURCES json/src/main/kotlin
        LIBRARIES jansson; common
)

konanc_library(
        NAME kurl
        SOURCES kurl/src/main/kotlin
        LIBRARIES libcurl; common
)

konanc_library(
        NAME sql
        SOURCES sql/src/main/kotlin
        LIBRARIES sqlite3; common
)

konanc_executable(
        NAME HttpServer
        SOURCES httpserver/src/main/kotlin
        TEST_SOURCES httpserver/src/test/kotlin
        LIBRARIES microhttpd; kjson; sql; common; kliopt
        LINKER_OPTS "-L${SQLITE3_LIBRARY_DIR} -lsqlite3 -L${MICROHTTPD_LIBRARY_DIR} -lmicrohttpd -L${JANSSON_LIBRARY_DIR} -ljansson"
)

konanc_executable(
        NAME CliClient
        SOURCES clients/cli/src/main/kotlin
        LIBRARIES common; kliopt; kurl; kjson
        LINKER_OPTS "-L${SQLITE3_LIBRARY_DIR} -lsqlite3 -L${MICROHTTPD_LIBRARY_DIR} -lmicrohttpd -L${JANSSON_LIBRARY_DIR} -ljansson -L${CURL_LIBRARY_DIR} -lcurl"
)

konanc_executable(
        NAME FontGenerator
        SOURCES utils/src/FontGenerator/kotlin
        LIBRARIES freetype; common; kliopt
        LINKER_OPTS "-L${FREETYPE_LIBRARY_DIR} -lfreetype"
)

konanc_executable(
        NAME BmpConvertor
        SOURCES utils/src/BmpConvertor/kotlin
        LIBRARIES common
)