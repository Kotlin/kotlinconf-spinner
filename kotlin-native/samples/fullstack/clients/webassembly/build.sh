#!/usr/bin/env bash

DIR=$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd )
PATH=$KONAN_HOME/bin:$PATH

if [ x$KONAN_SOURCE == x ]; then
    echo "Set KONAN_SOURCE to point to kotlin native source tree."
    echo "Build the html5Canvas demo before that."
fi

konanc StatView.kt \
    -r $KONAN_SOURCE/samples/html5Canvas/build \
    -l canvas -l jsinterop \
    -target wasm32 -o $DIR/../../static/view

