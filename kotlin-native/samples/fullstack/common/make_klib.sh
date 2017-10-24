#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

case "$OSTYPE" in
  darwin*)  TARGET=osx;;
  linux*)   TARGET=linux ;;
  *)        echo "unknown: $OSTYPE" && exit 1;;
esac

konanc -p library src/main/kotlin src/$TARGET/kotlin  -o kommon.klib

