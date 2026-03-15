#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE_DIR="${CACHE_DIR:-$ROOT_DIR/.cache/katago}"
KATAGO_TAG="${KATAGO_TAG:-v1.16.4}"
KATAGO_RELEASE_BASE="https://github.com/lightvector/KataGo/releases/download/${KATAGO_TAG}"
WINDOWS_ASSET="${WINDOWS_ASSET:-katago-${KATAGO_TAG}-eigen-windows-x64.zip}"
LINUX_ASSET="${LINUX_ASSET:-katago-${KATAGO_TAG}-eigen-linux-x64.zip}"
PREFERRED_MODEL_NAME="${PREFERRED_MODEL_NAME:-g170e-b20c256x2-s5303129600-d1228401921.bin.gz}"
MODEL_SOURCE="${MODEL_SOURCE:-}"

ENGINES_ROOT="$ROOT_DIR/engines/katago"
WEIGHTS_ROOT="$ROOT_DIR/weights"
CONFIG_ROOT="$ENGINES_ROOT/configs"
WINDOWS_ROOT="$ENGINES_ROOT/windows-x64"
LINUX_ROOT="$ENGINES_ROOT/linux-x64"
MACOS_ARM64_ROOT="$ENGINES_ROOT/macos-arm64"

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd"
    exit 1
  fi
}

download_asset() {
  local asset_name="$1"
  local dest="$CACHE_DIR/$asset_name"
  local url="$KATAGO_RELEASE_BASE/$asset_name"

  mkdir -p "$CACHE_DIR"
  if [[ -f "$dest" ]] && unzip -tqq "$dest" >/dev/null 2>&1; then
    echo "Using cached asset: $asset_name"
    return 0
  fi

  rm -f "$dest" "$dest.part"
  echo "Downloading $asset_name"
  curl -fL --retry 5 --retry-all-errors -o "$dest.part" "$url"
  mv "$dest.part" "$dest"
  unzip -tqq "$dest" >/dev/null
}

extract_asset() {
  local asset_name="$1"
  local out_dir="$CACHE_DIR/${asset_name%.zip}"
  rm -rf "$out_dir"
  mkdir -p "$out_dir"
  unzip -qo "$CACHE_DIR/$asset_name" -d "$out_dir"
  echo "$out_dir"
}

copy_matching_files() {
  local src_dir="$1"
  local dest_dir="$2"
  shift 2
  mkdir -p "$dest_dir"
  for pattern in "$@"; do
    find "$src_dir" -maxdepth 1 -type f -name "$pattern" -exec cp -f {} "$dest_dir/" \;
  done
}

find_model_source() {
  if [[ -n "$MODEL_SOURCE" ]]; then
    if [[ -f "$MODEL_SOURCE" ]]; then
      printf '%s\n' "$MODEL_SOURCE"
      return 0
    fi
    echo "MODEL_SOURCE does not exist: $MODEL_SOURCE"
    exit 1
  fi

  local candidates=(
    "/opt/homebrew/opt/katago/share/katago/$PREFERRED_MODEL_NAME"
    "/opt/homebrew/opt/katago/share/katago/g170-b40c256x2-s5095420928-d1229425124.bin.gz"
    "/opt/homebrew/opt/katago/share/katago/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz"
  )
  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -f "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  if [[ -d /opt/homebrew/opt/katago/share/katago ]]; then
    candidate="$(find /opt/homebrew/opt/katago/share/katago -maxdepth 1 -type f -name '*.bin.gz' | head -n 1)"
    if [[ -n "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  fi

  echo "No KataGo model found. Set MODEL_SOURCE=/path/to/model.bin.gz and rerun."
  exit 1
}

prepare_configs() {
  local source_dir="$1"
  rm -rf "$CONFIG_ROOT"
  mkdir -p "$CONFIG_ROOT"
  cp -f "$source_dir/default_gtp.cfg" "$CONFIG_ROOT/gtp.cfg"
  cp -f "$source_dir/default_gtp.cfg" "$CONFIG_ROOT/estimate.cfg"
  cp -f "$source_dir/analysis_example.cfg" "$CONFIG_ROOT/analysis.cfg"
}

prepare_windows_bundle() {
  local source_dir="$1"
  rm -rf "$WINDOWS_ROOT"
  mkdir -p "$WINDOWS_ROOT"
  copy_matching_files "$source_dir" "$WINDOWS_ROOT" "katago.exe" "*.dll" "cacert.pem"
}

prepare_linux_bundle() {
  local source_dir="$1"
  rm -rf "$LINUX_ROOT"
  mkdir -p "$LINUX_ROOT"
  copy_matching_files "$source_dir" "$LINUX_ROOT" "katago" "*.so" "*.so.*" "cacert.pem"
  chmod +x "$LINUX_ROOT/katago"
}

prepare_macos_arm64_bundle() {
  local katago_bin="${MACOS_ARM64_KATAGO_BIN:-/opt/homebrew/bin/katago}"
  local libzip_bin="${MACOS_ARM64_LIBZIP:-/opt/homebrew/opt/libzip/lib/libzip.5.dylib}"
  local liblzma_bin="${MACOS_ARM64_LIBLZMA:-/opt/homebrew/opt/xz/lib/liblzma.5.dylib}"
  local libzstd_bin="${MACOS_ARM64_LIBZSTD:-/opt/homebrew/opt/zstd/lib/libzstd.1.dylib}"

  if [[ ! -x "$katago_bin" ]]; then
    echo "Skipping macos-arm64 bundle: $katago_bin not found"
    return 0
  fi

  require_cmd install_name_tool

  rm -rf "$MACOS_ARM64_ROOT"
  mkdir -p "$MACOS_ARM64_ROOT/lib"

  cp -Lf "$katago_bin" "$MACOS_ARM64_ROOT/katago"
  cp -Lf "$libzip_bin" "$MACOS_ARM64_ROOT/lib/libzip.5.dylib"
  cp -Lf "$liblzma_bin" "$MACOS_ARM64_ROOT/lib/liblzma.5.dylib"
  cp -Lf "$libzstd_bin" "$MACOS_ARM64_ROOT/lib/libzstd.1.dylib"
  chmod +x "$MACOS_ARM64_ROOT/katago"

  install_name_tool \
    -change /opt/homebrew/opt/libzip/lib/libzip.5.dylib \
    @executable_path/lib/libzip.5.dylib \
    "$MACOS_ARM64_ROOT/katago"
  install_name_tool -id @loader_path/libzip.5.dylib "$MACOS_ARM64_ROOT/lib/libzip.5.dylib"
  install_name_tool -id @loader_path/liblzma.5.dylib "$MACOS_ARM64_ROOT/lib/liblzma.5.dylib"
  install_name_tool -id @loader_path/libzstd.1.dylib "$MACOS_ARM64_ROOT/lib/libzstd.1.dylib"
  install_name_tool \
    -change /opt/homebrew/opt/xz/lib/liblzma.5.dylib \
    @loader_path/liblzma.5.dylib \
    "$MACOS_ARM64_ROOT/lib/libzip.5.dylib"
  install_name_tool \
    -change /opt/homebrew/opt/zstd/lib/libzstd.1.dylib \
    @loader_path/libzstd.1.dylib \
    "$MACOS_ARM64_ROOT/lib/libzip.5.dylib"

  if command -v codesign >/dev/null 2>&1; then
    codesign --force --sign - "$MACOS_ARM64_ROOT/lib/"*.dylib "$MACOS_ARM64_ROOT/katago" >/dev/null 2>&1 || true
  fi

  "$MACOS_ARM64_ROOT/katago" version >/dev/null
}

write_manifest() {
  local model_path="$1"
  mkdir -p "$ENGINES_ROOT"
  cat >"$ENGINES_ROOT/VERSION.txt" <<EOF
KataGo release: $KATAGO_TAG
Windows bundle: $WINDOWS_ASSET
Linux bundle: $LINUX_ASSET
Model source: $(basename "$model_path")
Prepared at: $(date '+%F %T %z')
EOF
}

main() {
  require_cmd curl
  require_cmd unzip

  download_asset "$WINDOWS_ASSET"
  download_asset "$LINUX_ASSET"

  local windows_src
  local linux_src
  local model_path
  windows_src="$(extract_asset "$WINDOWS_ASSET")"
  linux_src="$(extract_asset "$LINUX_ASSET")"
  model_path="$(find_model_source)"

  mkdir -p "$WEIGHTS_ROOT"
  cp -f "$model_path" "$WEIGHTS_ROOT/default.bin.gz"

  prepare_configs "$windows_src"
  prepare_windows_bundle "$windows_src"
  prepare_linux_bundle "$linux_src"
  prepare_macos_arm64_bundle
  write_manifest "$model_path"

  echo
  echo "Prepared bundled KataGo assets:"
  find "$ENGINES_ROOT" -maxdepth 2 -type f | sort
  echo "$WEIGHTS_ROOT/default.bin.gz"
}

main "$@"
