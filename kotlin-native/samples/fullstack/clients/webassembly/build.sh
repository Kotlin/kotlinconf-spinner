#!/usr/bin/env bash

DIR=$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd )
PATH=$KONAN_HOME/bin:$PATH

konanc StatView.kt \
    -r $DIR/../../../../external \
    -l canvas -l jsinterop \
    -target wasm32 -o $DIR/../../static/view

