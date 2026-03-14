#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if ! command -v zip >/dev/null 2>&1; then
  echo "zip command not found"
  exit 1
fi

DATE_TAG="${1:-$(date +%F)}"
JAR_PATH="${2:-target/lizzie-yzy2.5.3-shaded.jar}"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Jar not found: $JAR_PATH"
  echo "Build first: mvn -DskipTests package"
  exit 1
fi

STAGE_DIR="dist/stage"
OUT_DIR="dist/release"
rm -rf "$STAGE_DIR" "$OUT_DIR"
mkdir -p "$STAGE_DIR" "$OUT_DIR"

make_bundle() {
  local bundle_name="$1"
  local start_file="$2"
  local start_content="$3"
  local root="$STAGE_DIR/$bundle_name"
  local app="$root/Lizzieyzy"

  mkdir -p "$app"
  cp "$JAR_PATH" "$app/"
  cp LICENSE.txt README.md README_EN.md README_KO.md readme_cn.pdf readme_en.pdf "$app/"

  cat >"$root/$start_file" <<EOF
$start_content
EOF
  if [[ "$start_file" == *.sh ]]; then
    chmod +x "$root/$start_file"
  fi

  cat >"$root/Required java version.txt" <<'EOF'
Java 11+ is required.
EOF

  cat >"$root/Update.txt" <<EOF
Project: LizzieYzy Next-FoxUID
Date: $DATE_TAG
Changes:
- Fix Fox kifu sync by UID using Fox H5 API.
- UID-only query mode (username lookup removed).
EOF
}

make_bundle \
  "$DATE_TAG-windows64.without.engine" \
  "start-windows64.bat" \
  "@echo off
setlocal
cd /d %~dp0
java -jar \"Lizzieyzy\\lizzie-yzy2.5.3-shaded.jar\""

make_bundle \
  "$DATE_TAG-windows32.without.engine" \
  "start-windows32.bat" \
  "@echo off
setlocal
cd /d %~dp0
java -jar \"Lizzieyzy\\lizzie-yzy2.5.3-shaded.jar\""

make_bundle \
  "$DATE_TAG-Macosx.amd64.Linux.amd64.without.engine" \
  "start-macos-linux.sh" \
  "#!/usr/bin/env bash
set -e
cd \"\$(dirname \"\$0\")\"
if [[ \"\$(uname -s)\" == \"Darwin\" ]]; then
  ARCH=\"\$(uname -m)\"
  if [[ \"\$ARCH\" == \"arm64\" ]]; then
    sh ./start-mac-arm64.sh
    exit 0
  fi
  sh ./start-mac-amd64.sh
  exit 0
fi
java -jar \"Lizzieyzy/lizzie-yzy2.5.3-shaded.jar\""

cat >"$STAGE_DIR/$DATE_TAG-Macosx.amd64.Linux.amd64.without.engine/start-mac-arm64.sh" <<'EOF'
#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
java -jar "Lizzieyzy/lizzie-yzy2.5.3-shaded.jar"
EOF
chmod +x "$STAGE_DIR/$DATE_TAG-Macosx.amd64.Linux.amd64.without.engine/start-mac-arm64.sh"

cat >"$STAGE_DIR/$DATE_TAG-Macosx.amd64.Linux.amd64.without.engine/start-mac-amd64.sh" <<'EOF'
#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
java -jar "Lizzieyzy/lizzie-yzy2.5.3-shaded.jar"
EOF
chmod +x "$STAGE_DIR/$DATE_TAG-Macosx.amd64.Linux.amd64.without.engine/start-mac-amd64.sh"

cat >"$STAGE_DIR/$DATE_TAG-Macosx.amd64.Linux.amd64.without.engine/Mac-CPU-Notes.txt" <<'EOF'
Mac chip support:
- Apple Silicon (arm64): use start-mac-arm64.sh
- Intel (x86_64): use start-mac-amd64.sh
- Or use start-macos-linux.sh for auto-detection.

If old native dependencies are incompatible in your environment, install a matching JDK and try Rosetta for x86_64 mode.
EOF

make_bundle \
  "$DATE_TAG-other-systems.without.engine" \
  "start.sh" \
  "#!/usr/bin/env bash
set -e
cd \"\$(dirname \"\$0\")\"
java -jar \"Lizzieyzy/lizzie-yzy2.5.3-shaded.jar\""

for d in "$STAGE_DIR"/*; do
  (
    cd "$STAGE_DIR"
    zip -rq "$ROOT_DIR/$OUT_DIR/$(basename "$d").zip" "$(basename "$d")"
  )
done

echo "Built release zips:"
ls -lh "$OUT_DIR"
