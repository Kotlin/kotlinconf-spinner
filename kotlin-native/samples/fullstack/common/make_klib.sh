#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

case "$OSTYPE" in
  darwin*)  TARGET=Osx;;
  linux*)   TARGET=Linux ;;
  *)        echo "unknown: $OSTYPE" && exit 1;;
esac

cinterop -def src/common.def -o common.klib
konanc -p library src/Kommon.kt src/Kommon$TARGET.kt -o kommon.klib \
     -library common.klib

