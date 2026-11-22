#!/bin/sh
# One non-debuggable Android 4.0.4+ APK, signed with an existing key.
set -eu
root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
export PATH="$JAVA_HOME/bin:$PATH"
keystore_path=${SIGNING_KEYSTORE:-"$HOME/.android/debug.keystore"}
[ -f "$keystore_path" ] || { echo 'Existing signing key missing; refusing to generate another identity.' >&2; exit 1; }
: "${SIGNING_STORE_PASS:?Set SIGNING_STORE_PASS}"
: "${SIGNING_KEY_PASS:?Set SIGNING_KEY_PASS}"
export SIGNING_STORE_PASS SIGNING_KEY_PASS
alias_name=${SIGNING_ALIAS:-androiddebugkey}
"$root_dir/gradlew" -p "$root_dir" :app:assembleRelease "$@"
version=$(sed -n 's/.*versionName "\([^" ]*\)".*/\1/p' "$root_dir/app/build.gradle")
[ -n "$version" ] || { echo 'Missing versionName.' >&2; exit 1; }
output_dir="$root_dir/dist"
mkdir -p "$output_dir"
build_tools="$ANDROID_HOME/build-tools/35.0.0"
unsigned="$root_dir/app/build/outputs/apk/release/app-release-unsigned.apk"
aligned="$root_dir/app/build/outputs/apk/release/delivery-aligned.apk"
output="$output_dir/tv-study-guard-v$version.apk"
"$build_tools/zipalign" -f -p 4 "$unsigned" "$aligned"
"$build_tools/apksigner" sign --ks "$keystore_path" --ks-key-alias "$alias_name" --ks-pass env:SIGNING_STORE_PASS --key-pass env:SIGNING_KEY_PASS --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out "$output" "$aligned"
"$build_tools/apksigner" verify --print-certs "$output"
"$build_tools/zipalign" -c -p 4 "$output"
(cd "$output_dir" && shasum -a 256 "tv-study-guard-v$version.apk" > SHA256SUMS.txt)
cat "$output_dir/SHA256SUMS.txt"
