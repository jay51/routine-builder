#!/usr/bin/env bash
set -euo pipefail

# Usage: scripts/run-on-device.sh [adb-serial]
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/home/jay/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-/home/jay/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

PACKAGE_NAME="dev.routinebuilder"
ACTIVITY_NAME="$PACKAGE_NAME/.MainActivity"
APK_PATH="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"

if [[ $# -gt 1 ]]; then
    echo "Usage: $0 [adb-serial]" >&2
    exit 2
fi
ADB_SERIAL="${1:-${ADB_SERIAL:-}}"

cd "$PROJECT_ROOT"
./gradlew assembleDebug

ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"

connected_devices() {
    "$ADB" devices | awk '$2 == "device" { print $1 }'
}
find_emulator() {
    "$ADB" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }'
}

if [[ -n "$ADB_SERIAL" ]]; then
    if ! connected_devices | rg -Fxq "$ADB_SERIAL"; then
        echo "ADB target '$ADB_SERIAL' is not connected and authorized." >&2
        exit 1
    fi
    SERIAL="$ADB_SERIAL"
else
    mapfile -t DEVICES < <(connected_devices)
    if [[ ${#DEVICES[@]} -eq 1 ]]; then
        SERIAL="${DEVICES[0]}"
    elif [[ ${#DEVICES[@]} -gt 1 ]]; then
        echo "Several Android devices are connected. Choose one with:" >&2
        printf '  %s <adb-serial>\n' "$0" >&2
        printf '  %s\n' "${DEVICES[@]}" >&2
        exit 1
    else
        AVD_NAME="${AVD_NAME:-}"
        if [[ -z "$AVD_NAME" ]]; then
            mapfile -t AVAILABLE_AVDS < <("$EMULATOR" -list-avds)
            if [[ ${#AVAILABLE_AVDS[@]} -eq 1 ]]; then
                AVD_NAME="${AVAILABLE_AVDS[0]}"
            elif [[ ${#AVAILABLE_AVDS[@]} -eq 0 ]]; then
                echo "No Android device is connected and no AVD is configured." >&2
                exit 1
            else
                echo "Several AVDs are configured. Set AVD_NAME to choose one:" >&2
                printf '  %s\n' "${AVAILABLE_AVDS[@]}" >&2
                exit 1
            fi
        fi

        nohup "$EMULATOR" -avd "$AVD_NAME" -no-snapshot-save >/tmp/routine-builder-emulator.log 2>&1 </dev/null &
        for _ in $(seq 1 120); do
            SERIAL="$(find_emulator)"
            [[ -n "$SERIAL" ]] && break
            sleep 2
        done
        if [[ -z "$SERIAL" ]]; then
            echo "Emulator did not appear in ADB. See /tmp/routine-builder-emulator.log" >&2
            tail -40 /tmp/routine-builder-emulator.log >&2 || true
            exit 1
        fi
    fi
fi

timeout 120s "$ADB" -s "$SERIAL" wait-for-device
for _ in $(seq 1 120); do
    if [[ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
        break
    fi
    sleep 2
done
if [[ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]]; then
    echo "Android device did not finish booting." >&2
    exit 1
fi

"$ADB" -s "$SERIAL" install -r "$APK_PATH"
"$ADB" -s "$SERIAL" shell am start -n "$ACTIVITY_NAME"
echo "Routine Builder is running on $SERIAL"
