#!/bin/bash

# 1. Set environment variables
export JAVA_HOME=~/jdk17
export ANDROID_HOME=~/android-sdk
export DISPLAY=:0
SERIAL="${1:-${ADB_SERIAL:-}}"
# 3. Build the APK
./gradlew assembleDebug

# 4. Start emulator if not already running
if ! ~/android-sdk/platform-tools/adb devices | grep -q "emulator"; then
    echo "Starting emulator..."
    ~/android-sdk/emulator/emulator -avd SleepLock -no-snapshot -no-boot-anim -gpu swiftshader_indirect & # start emulator (skip if already running)
    
    # 5. Wait for the emulator to fully boot up
    echo "Waiting for emulator to boot..."
    ~/android-sdk/platform-tools/adb wait-for-device
    while [ "$(~/android-sdk/platform-tools/adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do
        sleep 2
    done
    echo "Emulator is ready!"
else
    echo "Emulator is already running."
    # pgrep -f qemu-system-x86_64 || echo QEMU_DEAD
    # ~/android-sdk/platform-tools/adb emu kill
fi

# 6. Install and launch the app
 
~/android-sdk/platform-tools/adb devices
~/android-sdk/platform-tools/adb -s "$SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
~/android-sdk/platform-tools/adb -s "$SERIAL" shell am start -n dev.routinebuilder/.MainActivity
