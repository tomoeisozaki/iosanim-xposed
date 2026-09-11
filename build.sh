#!/bin/bash
# Rebuild modul Xposed iOS-anim (Vector). Edit control point kurva di
# src/com/tomoe/iosanim/Hook.java -> ios(), lalu: ./build.sh
# Hasil: ios-anim.apk. Install: adb install -r ios-anim.apk, lalu di device
#   vector-cli modules enable com.tomoe.iosanim
#   vector-cli scope set com.tomoe.iosanim android/0 com.android.systemui/0
# (enable/scope harus dijalankan user -- classifier Claude memblokirnya), lalu REBOOT.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
BT=/Users/tomoeisozaki/Library/Android/sdk/build-tools/37.0.0
AJ=/Users/tomoeisozaki/Library/Android/sdk/platforms/android-33/android.jar
KS=/Users/tomoeisozaki/Documents/Projects/poco_f1/gamespace-app/keystore/ngk-production.keystore
cd "$HERE"
rm -rf build && mkdir -p build/classes
javac -source 8 -target 8 -bootclasspath "$AJ" -cp "$AJ:api-82.jar" -d build/classes src/com/tomoe/iosanim/Hook.java
"$BT/d8" --min-api 27 --lib "$AJ" --lib api-82.jar --output build/ build/classes/com/tomoe/iosanim/*.class
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$AJ" --manifest AndroidManifest.xml -A assets \
  --min-sdk-version 27 --target-sdk-version 33 build/res.zip
cp build/classes.dex classes.dex && zip -q build/base.apk classes.dex && rm classes.dex
"$BT/zipalign" -f -p 4 build/base.apk build/aligned.apk
"$BT/apksigner" sign --ks "$KS" --ks-key-alias ngk --ks-pass pass:android --key-pass pass:android \
  --out ios-anim.apk build/aligned.apk
"$BT/apksigner" verify ios-anim.apk && echo "OK -> $HERE/ios-anim.apk"
