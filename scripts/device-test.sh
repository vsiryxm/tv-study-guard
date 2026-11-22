#!/bin/sh
set -eu
# Run only on a dedicated test emulator: instrumentation writes test rules/PIN/records.
if [ "$#" -ne 1 ]; then
    echo 'Usage: ./scripts/device-test.sh emulator-5554 (dedicated emulator only)' >&2
    exit 2
fi
test_device_serial=$1
case "$test_device_serial" in
    emulator-*) ;;
    *) echo 'Refusing instrumentation on a physical/family TV. Use a dedicated emulator.' >&2; exit 2 ;;
esac
./gradlew --offline :app:assembleDebug :app:assembleDebugAndroidTest
adb -s "$test_device_serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$test_device_serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
test_result_file=$(mktemp)
trap 'rm -f "$test_result_file"' EXIT HUP INT TERM
adb -s "$test_device_serial" shell am instrument -w cn.studyguard.tv.test/android.test.InstrumentationTestRunner > "$test_result_file"
cat "$test_result_file"
if ! grep -Eq '^OK \([0-9]+ tests?\)' "$test_result_file"; then
    echo 'Android runner did not report success.' >&2
    exit 1
fi
