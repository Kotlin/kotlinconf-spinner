#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

cinterop -def jansson.def -o jansson.klib
konanc -p library KJson.kt -library jansson.klib -o kjson.klib
