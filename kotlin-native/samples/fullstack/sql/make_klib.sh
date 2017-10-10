#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

cinterop -def src/sqlite3.def -o sqlite3.klib
konanc -p library src/KSqlite.kt -l sqlite3 -o ksqlite
