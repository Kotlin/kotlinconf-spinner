#!/usr/bin/env bash

#TODO: Update shell-scripts too.

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

cinterop -def src/main/c_interop/freetype.def -o freetype.klib -compilerOpts "$COMPILER_OPTS"
konanc src/FontGenerator/kotlin -o FontGenerator -r ../common -l freetype -l kommon -r ../getopt -l kliopt
konanc src/BmpConvertor/kotlin -o BmpConvertor -r ../common -l kommon
