#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

#cinterop -def microhttpd.def -o microhttpd.klib -compilerOpts "-I $DIR/include"
cinterop -def microhttpd.def -o microhttpd.klib
