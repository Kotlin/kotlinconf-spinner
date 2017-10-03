#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

cinterop -def jansson.def -o jansson.klib -library ../common/common.klib
konanc -p library KJson.kt -o kjson.klib \
     -r ../common \
     -l jansson \
     -l kommon
