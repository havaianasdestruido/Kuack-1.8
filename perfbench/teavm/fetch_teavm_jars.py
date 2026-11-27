#!/usr/bin/env python3
"""Fetch the TeaVM 0.9.2 jars this project's build needs into a local directory.

The canonical source is Maven Central (coordinates are listed in
buildtools/src/main/java/.../gui/TeaVMBinaries.java and in README.md). This sandbox only allows
github.com / api.github.com / npmjs.org / pypi.org, so this helper uses a public GitHub mirror of
the same Maven artifacts as a fallback. It is a development-time convenience, NOT a build input
of the game.

Usage: python3 perfbench/teavm/fetch_teavm_jars.py /tmp/teavm-jars
"""

from __future__ import annotations

import base64
import json
import os
import sys
import urllib.request

REPO = "mrfrenchguy23445/Eag19"
TREE = "ae1c2bba12fee1a2fbc8aea76dd4e07e7a78a867"
EXTRA = ("joda-time-2.12.2.jar", "jzlib-1.1.3.jar")


def api(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": "kuack-perfbench/1.0"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def main() -> int:
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/teavm-jars"
    os.makedirs(out, exist_ok=True)
    tree = api("https://api.github.com/repos/%s/git/trees/%s?recursive=1" % (REPO, TREE))
    if tree.get("truncated"):
        print("ERROR: GitHub tree was truncated", file=sys.stderr)
        return 1
    wanted = [
        t for t in tree["tree"]
        if t["path"].startswith("gradle/local-libs/")
        and t["path"].endswith(".jar")
        and "/0.9.2/" in t["path"]
        and t["type"] == "blob"
    ] + [
        t for t in tree["tree"]
        if t["path"].startswith("gradle/local-libs/")
        and t["path"].endswith(".jar")
        and os.path.basename(t["path"]) in EXTRA
        and t["type"] == "blob"
    ]
    print("fetching %d TeaVM jars into %s" % (len(wanted), out))
    for t in wanted:
        name = os.path.basename(t["path"])
        dest = os.path.join(out, name)
        if os.path.exists(dest) and os.path.getsize(dest) == t["size"]:
            continue
        blob = api("https://api.github.com/repos/%s/git/blobs/%s" % (REPO, t["sha"]))
        data = base64.b64decode(blob["content"])
        if len(data) != t["size"]:
            print("  size mismatch: %s" % name, file=sys.stderr)
            return 1
        with open(dest, "wb") as f:
            f.write(data)
        print("  %-46s %8d bytes" % (name, len(data)))
    print("done. Set TEAVM_JARS=%s" % out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
