#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

cinterop -def src/sqlite3.def -o sqlite3.klib -library ../common/common.klib
konanc -p library src/KSqlite.kt -r ../common -l sqlite3 -o ksqlite
