#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

cinterop -def src/nativeInterop/cinterop/sqlite3.def -o sqlite3.klib
konanc -p library src/hostMain/kotlin -l sqlite3 -o ksqlite
