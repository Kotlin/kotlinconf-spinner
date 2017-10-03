#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

cinterop -def libcurl.def -o libcurl.klib -library ../common/common.klib
konanc -p library KUrl.kt -r ../common -l libcurl -o kurl
