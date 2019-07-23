#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

if [ x$TARGET == x ]; then
case "$OSTYPE" in
  darwin*)  TARGET=osx ;;
  linux*)   TARGET=linux ;;
  *)        echo "unknown: $OSTYPE" && exit 1;;
esac
fi

COMPILER_OPTS_linux="-I/usr/include"
COMPILER_OPTS_osx="-I/opt/local/include"

var=COMPILER_OPTS_${TARGET}
COMPILER_OPTS="${!var}"

cinterop -def src/nativeInterop/cinterop/jansson.def -o jansson.klib -compilerOpts "$COMPILER_OPTS"
konanc -p library src/nativeMain/kotlin -o kjson.klib \
     -r ../common \
     -l jansson \
     -l kommon
