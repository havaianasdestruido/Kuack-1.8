#!/usr/bin/env bash
# Rebuild sources/minecraft (patched Java, assets and lang files) from the
# decompiled 1.8.8 sources, using only the build inputs vendored in this repo.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="${TMPDIR:-/tmp}/eaglercraft-vendored-regen"
PATHARG="${1:-$TMP}"

if ! command -v java >/dev/null 2>&1; then
  echo "Java 11 or newer is required" >&2
  exit 1
fi

exec java ${EAGLERCRAFT_JAVA_OPTS:-} \
  -cp "$REPO/buildtools/BuildTools.jar:$REPO/buildtools/deps/*" \
  net.lax1dude.eaglercraft.v1_8.buildtools.task.init.RegenerateVendored \
  "$REPO" "$PATHARG"
