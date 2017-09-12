#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

cinterop -def sqlite3.def -o sqlite3.klib
konanc -p library KSqlite.kt -library sqlite3.klib -o ksqlite.klib
