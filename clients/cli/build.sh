#!/usr/bin/env bash

DIR=$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd )
PATH=$KONAN_HOME/bin:$PATH

konanc -opt src/hostMain/kotlin/Client.kt \
                     -r $DIR/../../json \
                     -r $DIR/../../kurl \
                     -r $DIR/../../common \
                     -r $DIR/../../getopt \
                     -l kjson \
                     -l kurl \
                     -l kliopt \
                     -l kommon \
                     -o CliClient
