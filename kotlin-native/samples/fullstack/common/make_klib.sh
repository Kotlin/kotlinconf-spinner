#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

case "$OSTYPE" in
  darwin*)  TARGET=Osx;;
  linux*)   TARGET=Linux ;;
  *)        echo "unknown: $OSTYPE" && exit 1;;
esac

cinterop -def common.def -o common.klib
konanc -p library Kommon.kt Kommon$TARGET.kt -o kommon.klib \
     -library common.klib

