#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

cinterop -def src/main/c_interop/sqlite3.def -o sqlite3.klib
konanc -p library src/main/kotlin -l sqlite3 -o ksqlite
