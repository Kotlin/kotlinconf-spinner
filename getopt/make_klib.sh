#!/usr/bin/env bash

PATH=$KONAN_HOME/bin:$PATH

konanc -produce library src/hostMain/kotlin -o kliopt.klib
