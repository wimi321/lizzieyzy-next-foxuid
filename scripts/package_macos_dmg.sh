#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

DATE_TAG="${1:-$(date +%F)}"
APP_VERSION="${2:-2.5.3}"
JAR_PATH="${3:-target/lizzie-yzy2.5.3-shaded.jar}"

JAVA_HOME_DEFAULT="$ROOT_DIR/.tools/jdk-21/jdk-21.0.10.jdk/Contents/Home"
if [[ -d "$JAVA_HOME_DEFAULT" ]]; then
  export JAVA_HOME="$JAVA_HOME_DEFAULT"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage not found. Please use JDK 14+ with jpackage."
  exit 1
fi

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Jar not found: $JAR_PATH"
  echo "Build first: mvn -DskipTests package"
  exit 1
fi

ARCH="$(uname -m)"
if [[ "$ARCH" == "arm64" ]]; then
  ARCH_TAG="mac-arm64"
  ENGINE_PLATFORM_DIR="macos-arm64"
else
  ARCH_TAG="mac-amd64"
  ENGINE_PLATFORM_DIR="macos-amd64"
fi

has_bundled_katago() {
  [[ -f "$ROOT_DIR/weights/default.bin.gz" ]] \
    && [[ -d "$ROOT_DIR/engines/katago/$ENGINE_PLATFORM_DIR" ]] \
    && find "$ROOT_DIR/engines/katago/$ENGINE_PLATFORM_DIR" -maxdepth 1 -type f | grep -q .
}

if has_bundled_katago; then
  PACKAGE_FLAVOR="with-katago"
  PACKAGE_NOTE="Bundled KataGo included for this macOS package."
else
  PACKAGE_FLAVOR="without.engine"
  PACKAGE_NOTE="No bundled KataGo in this macOS package."
fi

copy_bundle_engine_assets() {
  if ! has_bundled_katago; then
    return 0
  fi

  mkdir -p "$INPUT_DIR/engines/katago" "$INPUT_DIR/weights"
  cp -R "$ROOT_DIR/engines/katago/$ENGINE_PLATFORM_DIR" "$INPUT_DIR/engines/katago/"
  if [[ -d "$ROOT_DIR/engines/katago/configs" ]]; then
    cp -R "$ROOT_DIR/engines/katago/configs" "$INPUT_DIR/engines/katago/"
  fi
  if [[ -f "$ROOT_DIR/engines/katago/VERSION.txt" ]]; then
    cp "$ROOT_DIR/engines/katago/VERSION.txt" "$INPUT_DIR/engines/katago/"
  fi
  cp "$ROOT_DIR/weights/default.bin.gz" "$INPUT_DIR/weights/default.bin.gz"
}

DIST_DIR="$ROOT_DIR/dist/macos"
INPUT_DIR="$DIST_DIR/input"
APP_IMAGE_DIR="$DIST_DIR/app-image"
DMG_DIR="$DIST_DIR/dmg"
rm -rf "$INPUT_DIR" "$APP_IMAGE_DIR" "$DMG_DIR"
mkdir -p "$INPUT_DIR" "$APP_IMAGE_DIR" "$DMG_DIR"

cp "$JAR_PATH" "$INPUT_DIR/"
cp README.md README_EN.md README_KO.md LICENSE.txt "$INPUT_DIR/"
cp readme_cn.pdf readme_en.pdf "$INPUT_DIR/"
copy_bundle_engine_assets

APP_NAME="LizzieYzy Next-FoxUID"
MAIN_JAR="$(basename "$JAR_PATH")"
IDENTIFIER="com.wimi321.lizzieyzy.nextfoxuid"

jpackage \
  --type app-image \
  --name "$APP_NAME" \
  --input "$INPUT_DIR" \
  --main-jar "$MAIN_JAR" \
  --main-class featurecat.lizzie.Lizzie \
  --dest "$APP_IMAGE_DIR" \
  --app-version "$APP_VERSION" \
  --vendor "wimi321" \
  --description "LizzieYzy maintained fork with Fox UID sync fix" \
  --java-options "-Xmx4096m"

jpackage \
  --type dmg \
  --name "$APP_NAME" \
  --input "$INPUT_DIR" \
  --main-jar "$MAIN_JAR" \
  --main-class featurecat.lizzie.Lizzie \
  --dest "$DMG_DIR" \
  --app-version "$APP_VERSION" \
  --vendor "wimi321" \
  --description "LizzieYzy maintained fork with Fox UID sync fix" \
  --mac-package-identifier "$IDENTIFIER" \
  --java-options "-Xmx4096m"

APP_BUNDLE="$APP_IMAGE_DIR/$APP_NAME.app"
APP_ZIP="$ROOT_DIR/dist/release/${DATE_TAG}-${ARCH_TAG}.${PACKAGE_FLAVOR}.app.zip"
DMG_FILE="$(ls "$DMG_DIR"/*.dmg | head -n 1)"
FINAL_DMG="$ROOT_DIR/dist/release/${DATE_TAG}-${ARCH_TAG}.${PACKAGE_FLAVOR}.dmg"

mkdir -p "$ROOT_DIR/dist/release"
cp "$DMG_FILE" "$FINAL_DMG"
(
  cd "$APP_IMAGE_DIR"
  ditto -c -k --sequesterRsrc --keepParent "$APP_NAME.app" "$APP_ZIP"
)

cat >"$ROOT_DIR/dist/release/${DATE_TAG}-${ARCH_TAG}.${PACKAGE_FLAVOR}-install.txt" <<EOF
Package type: unsigned macOS app + dmg
Build architecture: $ARCH
Generated on: $DATE_TAG
Engine: $PACKAGE_NOTE

Install:
1. Open the dmg and drag app to Applications.
2. First run may be blocked by Gatekeeper, use:
   System Settings -> Privacy & Security -> Open Anyway

Bundled KataGo paths inside the app bundle:
- Engine: LizzieYzy Next-FoxUID.app/Contents/app/engines/katago/$ENGINE_PLATFORM_DIR/katago
- Weight: LizzieYzy Next-FoxUID.app/Contents/app/weights/default.bin.gz
- Configs: LizzieYzy Next-FoxUID.app/Contents/app/engines/katago/configs/

Notes:
- This package is unsigned/not notarized.
- For Intel/Apple Silicon dual-native support, build once on each architecture.
EOF

echo "Artifacts:"
ls -lh "$ROOT_DIR/dist/release/${DATE_TAG}-${ARCH_TAG}.${PACKAGE_FLAVOR}"*
