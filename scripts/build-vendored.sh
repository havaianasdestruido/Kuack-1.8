#!/usr/bin/env bash
# Compile the client entirely from the patched Java sources and assets checked
# into this repository. The only thing fetched from the network are the
# open-source TeaVM compiler JARs (from Maven Central by default).
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${1:-$REPO/dist}"
MAVEN_LOCAL="${MAVEN_LOCAL:-$REPO/buildtools/maven}"
MAVEN_URL="${TEAVM_MAVEN_URL:-}"
TEMP_DIR="${BUILD_TEMP_DIR:-${TMPDIR:-/tmp}/eaglercraft-vendored-build}"
CONFIG="$(mktemp)"

mkdir -p "$OUT" "$MAVEN_LOCAL" "$TEMP_DIR"
trap 'rm -f "$CONFIG"' EXIT

python3 - "$REPO" "$OUT" "$MAVEN_LOCAL" "$MAVEN_URL" "$TEMP_DIR" > "$CONFIG" <<'PY'
import json
import sys

repo, out, maven, url, tmp = sys.argv[1:6]
config = {
    "repositoryFolder": repo,
    "minecraftSourceDirectory": repo + "/sources/minecraft/java",
    "minecraftAssetsDirectory": repo + "/sources/minecraft/assets",
    "minecraftLanguagesDirectory": repo + "/sources/minecraft/lang",
    "outputDirectory": out,
    "temporaryDirectory": tmp,
    "mavenLocal": maven,
    "productionIndex": repo + "/buildtools/production-index.html",
    "productionFavicon": repo + "/buildtools/production-favicon.png",
    "generateOfflineDownload": True,
    "offlineDownloadTemplate": repo + "/sources/setup/workspace_template/javascript/OfflineDownloadTemplate.txt",
    "minifying": True,
    "writeSourceMap": False,
    "keepTemporaryFiles": False
}
if url:
    config["mavenURL"] = url
print(json.dumps(config))
PY

JAVA_BIN="${JAVA:-java}"
"$JAVA_BIN" -Deaglercraft.TeaVMBridge="$REPO/buildtools/TeaVMBridge.jar" \
  -cp "$REPO/buildtools/BuildTools.jar:$REPO/buildtools/deps/*" \
  net.lax1dude.eaglercraft.v1_8.buildtools.gui.headless.CompileLatestClientHeadless \
  -y "$CONFIG"
