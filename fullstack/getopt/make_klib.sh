#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

konanc -produce library src/main/kotlin -o kliopt.klib
