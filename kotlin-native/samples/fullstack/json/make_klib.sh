#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

cinterop -def jansson.def -o jansson.klib -library ../common/common.klib
konanc -p library KJson.kt -o kjson.klib \
     -library jansson.klib \
     -library ../common/common.klib \
     -library ../common/kommon.klib
