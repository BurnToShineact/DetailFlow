#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$PROJECT_DIR/version.properties"
ANDROID_SDK="${ANDROID_SDK_ROOT:-/home/vadim/OrbisCalculator/android-sdk}"
BUILD_TOOLS="$ANDROID_SDK/build-tools/36.0.0"
ANDROID_JAR="$ANDROID_SDK/platforms/android-36/android.jar"
if [[ -n "${DETAILFLOW_JDK:-}" ]]; then
  JDK_DIR="$DETAILFLOW_JDK"
elif command -v javac >/dev/null 2>&1; then
  JDK_DIR="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
else
  JDK_DIR="/home/vadim/OrbisCalculator/.tooling/jdk17"
fi
JAVAC="$JDK_DIR/bin/javac"
KEYTOOL="$JDK_DIR/bin/keytool"
OUT="$PROJECT_DIR/build"

if [[ ! -f "$ANDROID_JAR" || ! -x "$BUILD_TOOLS/aapt2" ]]; then
  echo "Android SDK 36 не найден: $ANDROID_SDK" >&2
  exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

"$BUILD_TOOLS/aapt2" compile --dir "$PROJECT_DIR/res" -o "$OUT/resources.zip"
"$BUILD_TOOLS/aapt2" link \
  -o "$OUT/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$PROJECT_DIR/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version 26 \
  --target-sdk-version 36 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  "$OUT/resources.zip"

mapfile -t JAVA_FILES < <(find "$PROJECT_DIR/src" "$OUT/gen" -name '*.java' -type f | sort)
"$JAVAC" -encoding UTF-8 -source 17 -target 17 -classpath "$ANDROID_JAR" -d "$OUT/classes" "${JAVA_FILES[@]}"

mapfile -t CLASS_FILES < <(find "$OUT/classes" -name '*.class' -type f | sort)
"$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 26 --output "$OUT/dex" "${CLASS_FILES[@]}"

cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KEYSTORE="${DETAILFLOW_KEYSTORE:-$PROJECT_DIR/detailflow-debug.keystore}"
KEY_ALIAS="detailflow"
KEYSTORE_PASSWORD="android"
KEY_PASSWORD="android"
if [[ ! -f "$KEYSTORE" ]]; then
  if [[ -n "${DETAILFLOW_KEYSTORE:-}" ]]; then
    echo "Указанный keystore не найден: $KEYSTORE" >&2
    exit 1
  fi
  "$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias detailflow -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=DetailFlow Debug,O=Local Development,C=RU" >/dev/null 2>&1
fi

"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEYSTORE_PASSWORD" \
  --key-pass "pass:$KEY_PASSWORD" \
  --out "$PROJECT_DIR/DetailFlow-debug.apk" \
  "$OUT/aligned.apk"

"$BUILD_TOOLS/apksigner" verify --verbose "$PROJECT_DIR/DetailFlow-debug.apk"
echo "APK: $PROJECT_DIR/DetailFlow-debug.apk"
