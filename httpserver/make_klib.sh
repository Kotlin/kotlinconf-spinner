#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

cinterop -def src/nativeInterop/cinterop/microhttpd.def -o microhttpd.klib
