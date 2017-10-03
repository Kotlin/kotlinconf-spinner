#!/usr/bin/env bash

DIR=$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd )
PATH=$KONAN_HOME/bin:$PATH
STATIC_DIR=$DIR/../../static/
mkdir -p $STATIC_DIR

konanc StatView.kt \
    -r $DIR/../../../../external/klib \
    -l canvas -l jsinterop \
    -target wasm32 -o $DIR/../../static/view

cp ./index.html $STATIC_DIR
