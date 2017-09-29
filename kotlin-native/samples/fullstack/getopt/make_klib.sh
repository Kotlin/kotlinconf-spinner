#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

konanc -produce library src/KliOpt.kt -o kliopt.klib
