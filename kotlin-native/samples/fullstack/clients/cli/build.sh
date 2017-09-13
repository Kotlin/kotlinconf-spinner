#!/usr/bin/env bash

DIR=$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd )
PATH=$KONAN_HOME/bin:$PATH

# libcurl dep is temporary, until transitive deps are here.
konanc Client.kt \
                     -library $DIR/../../json/kjson.klib \
                     -library $DIR/../../json/jansson.klib \
                     -library $DIR/../../kurl/kurl.klib \
                     -library $DIR/../../kurl/libcurl.klib \
                     -library $DIR/../../getopt/kliopt.klib \
                     -library $DIR/../../common/common.klib \
                     -library $DIR/../../common/kommon.klib \
                     -o CliClient
