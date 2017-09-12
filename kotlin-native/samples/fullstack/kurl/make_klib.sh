#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

cinterop -def libcurl.def -o libcurl.klib
konanc -p library KUrl.kt -library libcurl.klib -o kurl.klib
