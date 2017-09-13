#!/usr/bin/env bash

DIR=$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd )
PATH=$KONAN_HOME/bin:$PATH

konanc -opt HttpServer.kt -library $DIR/microhttpd/microhttpd.klib \
                     -library $DIR/../json/kjson.klib \
                     -library $DIR/../json/jansson.klib \
                     -library $DIR/../sql/sqlite3.klib \
                     -library $DIR/../sql/ksqlite.klib \
                     -library $DIR/../common/common.klib \
                     -o HttpServer
