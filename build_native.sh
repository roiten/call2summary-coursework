#!/usr/bin/env bash
# Builds gigastt (UniFFI) for Android arm64 and generates Kotlin bindings.
# Mirrors gigastt's own CI recipe (.github/workflows/android-aar.yml), arm64 only.
# Needs: git, curl, unzip, python3 (>=3.11), rustup+cargo (stable, >=1.94), protoc,
#        cargo-ndk, and ANDROID_NDK_HOME pointing to an Android NDK (r26+).
set -euo pipefail

GIGASTT_COMMIT=47f5cf55e926a397d328978aec3441d955ab9483   # the revision this project was written against
ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="${GIGASTT_SRC:-$ROOT/gigastt-src}"
WORK="$ROOT/ort-work"
JNI="$ROOT/app/src/main/jniLibs"
KT="$ROOT/app/src/main/java"

for t in git curl unzip python3 cargo rustup protoc cargo-ndk; do
  command -v "$t" >/dev/null || { echo "MISSING TOOL: $t"; exit 1; }
done
: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to your Android NDK directory}"

rustup target add aarch64-linux-android

if [ ! -d "$SRC/.git" ]; then git clone https://github.com/ekhodzitsky/gigastt.git "$SRC"; fi
git -C "$SRC" fetch --quiet origin "$GIGASTT_COMMIT" 2>/dev/null || true
git -C "$SRC" checkout --quiet "$GIGASTT_COMMIT"

# 1) Official Microsoft onnxruntime-android AAR, version + SHA-256 pinned by gigastt.
ORT_VER=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["version"])' "$SRC/packaging/android/onnxruntime.json")
mkdir -p "$WORK/arm64-v8a"
curl -fsSL -o "$WORK/ort-android.aar" \
  "https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/${ORT_VER}/onnxruntime-android-${ORT_VER}.aar"
( cd "$SRC" && cargo metadata --locked --format-version 1 > "$WORK/cargo-metadata.json" )
python3 "$SRC/scripts/check-android-runtime.py" \
  --metadata "$WORK/cargo-metadata.json" --pin "$SRC/packaging/android/onnxruntime.json" --aar "$WORK/ort-android.aar"
unzip -p "$WORK/ort-android.aar" jni/arm64-v8a/libonnxruntime.so > "$WORK/arm64-v8a/libonnxruntime.so"
test -s "$WORK/arm64-v8a/libonnxruntime.so"


# 2) Native lib for arm64 (передаем фичу на уровне воркспейса или через --features)
mkdir -p "$JNI"
( cd "$SRC" && ORT_PREFER_DYNAMIC_LINK=1 ORT_LIB_LOCATION="$WORK/arm64-v8a" \
    cargo ndk -t arm64-v8a -o "$JNI" build --release -p gigastt-uniffi --features gigastt-core/diarization )
cp "$WORK/arm64-v8a/libonnxruntime.so" "$JNI/arm64-v8a/"

# 3) Kotlin bindings из хост-сборки
( cd "$SRC" && CARGO_PROFILE_RELEASE_STRIP=false cargo build --release -p gigastt-uniffi --features gigastt-core/diarization )
HOST_LIB=$(ls "$SRC"/target/release/libgigastt_uniffi.so "$SRC"/target/release/libgigastt_uniffi.dylib 2>/dev/null | head -1 || true)
( cd "$SRC" && CARGO_PROFILE_RELEASE_STRIP=false cargo run --release -p gigastt-uniffi --bin uniffi-bindgen -- \
    generate --library "$HOST_LIB" --language kotlin --out-dir "$KT" )


# # 2) Native lib for arm64 (dynamic link to the onnxruntime above).
# mkdir -p "$JNI"
# ( cd "$SRC" && ORT_PREFER_DYNAMIC_LINK=1 ORT_LIB_LOCATION="$WORK/arm64-v8a" \
#     cargo ndk -t arm64-v8a -o "$JNI" build --release -p gigastt-uniffi )
# cp "$WORK/arm64-v8a/libonnxruntime.so" "$JNI/arm64-v8a/"

# # 3) Kotlin bindings from a HOST build (strip must be off or bindgen finds no metadata).
# ( cd "$SRC" && CARGO_PROFILE_RELEASE_STRIP=false cargo build --release -p gigastt-uniffi )
# HOST_LIB=$(ls "$SRC"/target/release/libgigastt_uniffi.so "$SRC"/target/release/libgigastt_uniffi.dylib 2>/dev/null | head -1 || true)
# ( cd "$SRC" && CARGO_PROFILE_RELEASE_STRIP=false cargo run --release -p gigastt-uniffi --bin uniffi-bindgen -- \
#     generate --library "$HOST_LIB" --language kotlin --out-dir "$KT" )

# 4) Sanity checks.
ls -l "$JNI/arm64-v8a/"
GEN=$(find "$KT/uniffi" -name '*.kt' | head -1)
echo "Generated: $GEN"
grep -m1 '^package ' "$GEN"
grep -q '^package uniffi.gigastt_uniffi' "$GEN" || {
  echo "!! Generated package differs from 'uniffi.gigastt_uniffi'."
  echo "!! Fix the 'import uniffi.gigastt_uniffi.Engine' line in MainActivity.kt to match the package above."
  exit 1; }
echo "Native build OK. Now: gradle :app:assembleDebug"
