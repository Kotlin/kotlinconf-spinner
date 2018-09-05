#!/usr/bin/env bash

alphabet="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
androidFont="$ANDROID_HOME/platforms/android-25/data/fonts/Roboto-Black.ttf"
iosFont="/System/Library/Fonts/Menlo.ttc"
fontSize=100

./FontGenerator.kexe -f $androidFont -s $fontSize -d ./android -c $alphabet
./FontGenerator.kexe -f $iosFont -s $fontSize -d ./ios -c $alphabet
