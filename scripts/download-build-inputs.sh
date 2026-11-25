#!/usr/bin/env bash
# Download build inputs without storing third-party binaries in the repository.
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 OUTPUT_DIRECTORY" >&2
  exit 2
fi
inputs=$1
mkdir -p "$inputs"

default_mcp='https://raw.githubusercontent.com/Spacehost-me/MCP/057b8f287af3090e0dacd4d29616050f34efd7ae/mcp918.zip'
default_jar='https://launcher.mojang.com/v1/objects/0983f08be6a4e624f5d85689d1aca869ed99c738/client.jar'
default_index='https://launchermeta.mojang.com/v1/packages/f6ad102bcaa53b1a58358f16e376d548d44933ec/1.8.json'

fetch_input() {
  local label=$1 url=$2 default_url=$3 destination=$4 expected_hash=$5 hash_command=$6
  local -a auth_args=()
  local tmp
  tmp=$(mktemp "$inputs/.download.XXXXXXXX")
  # A private token is only needed for explicitly configured URLs; never send it
  # to a public default, and curl does not forward it to a redirected host.
  if [ -n "$url" ] && [ -n "${BUILD_INPUTS_TOKEN:-}" ]; then
    auth_args=(-H "Authorization: Bearer ${BUILD_INPUTS_TOKEN}")
  fi
  local is_default=0
  if [ -z "$url" ]; then
    url=$default_url
    is_default=1
  fi
  echo "Downloading $label..."
  if ! curl --fail --silent --show-error --location \
    --retry 3 --retry-delay 2 --connect-timeout 20 --max-time 1800 \
    --proto '=https' --proto-redir '=https' \
    "${auth_args[@]}" --output "$tmp" "$url"; then
    rm -f "$tmp"
    echo "Failed to download $label" >&2
    return 1
  fi
  if [ ! -s "$tmp" ]; then
    rm -f "$tmp"
    echo "Empty download: $label" >&2
    return 1
  fi
  if [ "$is_default" = 1 ] && ! echo "$expected_hash  $tmp" | "$hash_command" --check --status; then
    rm -f "$tmp"
    echo "Checksum mismatch for $label" >&2
    return 1
  fi
  mv -f "$tmp" "$destination"
}

fetch_input mcp918.zip "${MCP918_URL:-}" "$default_mcp" "$inputs/mcp918.zip" c936dffb3007110b24538da5f334c28ec83c6787a56cc4c63fd840cdff306eb0 sha256sum
# Mojang publishes SHA-1 hashes in the version manifest; check those separately.
# Use SHA-256 for the pinned MCP archive, and SHA-1 for the official Minecraft files.
fetch_input 1.8.8.jar "${MINECRAFT_JAR_URL:-}" "$default_jar" "$inputs/1.8.8.jar" 0983f08be6a4e624f5d85689d1aca869ed99c738 sha1sum
fetch_input 1.8.json "${ASSETS_INDEX_URL:-}" "$default_index" "$inputs/1.8.json" f6ad102bcaa53b1a58358f16e376d548d44933ec sha1sum

python3 - "$inputs/mcp918.zip" "$inputs/1.8.8.jar" "$inputs/1.8.json" <<'PYCODE'
import json
import sys
import zipfile

for path in sys.argv[1:3]:
    with zipfile.ZipFile(path) as archive:
        bad = archive.testzip()
        if bad:
            raise SystemExit(f"Corrupt archive {path}: {bad}")
with open(sys.argv[3], encoding="utf-8") as index:
    data = json.load(index)
    if not isinstance(data.get("objects"), dict) or not data["objects"]:
        raise SystemExit("Asset index must contain a non-empty objects map")
PYCODE
