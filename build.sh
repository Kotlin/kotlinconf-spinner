#!/usr/bin/env bash

if [ ! -d $KONAN_HOME ]; then
  echo "Set KONAN_HOME to Kotlin/Native redist"
  exit 1
fi

for l in common getopt json sql kurl httpserver ; do
  echo "Building $l..."
  (cd $l && ./make_klib.sh)
done

for e in httpserver clients/cli clients/webassembly ; do
  echo "Building $e..."
  (cd $e && ./build.sh)
done 
