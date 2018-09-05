#!/usr/bin/env bash

DIR=$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd )
PATH=$KONAN_HOME/bin:$PATH

konanc -g src/main/kotlin \
                     -r $DIR/../common \
                     -r $DIR/../sql \
                     -r $DIR/../json \
                     -r $DIR/../getopt \
                     -l microhttpd \
                     -l kjson \
                     -l ksqlite \
                     -l kommon \
                     -l kliopt \
                     -o HttpServer
