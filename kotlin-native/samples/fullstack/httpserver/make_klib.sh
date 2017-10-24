#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

cinterop -def src/main/c_interop/microhttpd.def -o microhttpd.klib
