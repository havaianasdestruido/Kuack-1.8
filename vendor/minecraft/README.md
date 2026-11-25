# Vendored Minecraft 1.8 build inputs

These files are the exact inputs used to produce the checked in patched source in
`sources/minecraft/`. They let the repository regenerate that source and apply
`patches/minecraft` / `patches/resources` without downloading anything from
Mojang or a third-party mirror.

| File | Source | Pinned hash |
| --- | --- | --- |
| `mcp918.zip` | [Spacehost-me/MCP](https://github.com/Spacehost-me/MCP), commit `057b8f287af3090e0dacd4d29616050f34efd7ae` | SHA-256 `c936dffb3007110b24538da5f334c28ec83c6787a56cc4c63fd840cdff306eb0` |
| `1.8.8.jar` | Mojang Minecraft 1.8.8 client JAR | SHA-1 `0983f08be6a4e624f5d85689d1aca869ed99c738` |
| `1.8.json` | Mojang 1.8 asset index | SHA-1 `f6ad102bcaa53b1a58358f16e376d548d44933ec` |
| `assets/<first two hex chars>/<sha1>` | Mojang resource objects listed in `1.8.json` | SHA-1 is the file name |

Only the objects actually used by `mcp918/assetsIndexTransformer.json` are
included (sounds, icons, `sounds.json` and the language files). `SHA256SUMS`
covers every file in this directory and is verified by CI and by
`tests/test_vendored_sources.py`.

Run `./scripts/regenerate-vendored-sources.sh` from the repository root to
rebuild `sources/minecraft` from these inputs. Redistribution of Mojang's
assets and decompiled code may be restricted; see `sources/minecraft/LICENSE`.
