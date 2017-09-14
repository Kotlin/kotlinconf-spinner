#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

cinterop -def microhttpd.def -o microhttpd.klib -library ../../common/common.klib
