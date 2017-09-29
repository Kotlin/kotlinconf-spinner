#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

if [ x$TARGET == x ]; then
case "$OSTYPE" in
  darwin*)  TARGET=osx ;;
  linux*)   TARGET=linux ;;
  *)        echo "unknown: $OSTYPE" && exit 1;;
esac
fi

COMPILER_OPTS_linux="-I/usr/include -I/usr/include/x86_64-linux-gnu"
COMPILER_OPTS_osx="-I/opt/local/include"

var=COMPILER_OPTS_${TARGET}
COMPILER_OPTS="${!var}"

cinterop -def src/libcurl.def -o libcurl.klib -library ../common/common.klib -compilerOpts "$COMPILER_OPTS"
konanc -p library src/KUrl.kt -r ../common -l libcurl -o kurl
