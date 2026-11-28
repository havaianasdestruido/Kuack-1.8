# my personal goal with this fork

everything that is **bold** is a prority.


- [x] **add [SodiumMC](https://github.com/CaffeineMC/sodium) baked-in with the game**
- [ ] **add [Distant Horizons](https://gitlab.com/distant-horizons-team/distant-horizons) baked-in with the game**
- [ ] compile to WASM instead of JS, since it's way faster and TeaVM supports it natively.
- [ ] **add [in-game music](https://bafybeiayojww5jfyzvlmtuk7l5ufkt7nlfto7mhwmzf2vs4bvsjd5ouiuq.ipfs.nftstorage.link/?filename=Music_For_Eaglercraft.zip)**
- [ ] **add new config "performance presets" where the user can change between presets made for ultra low-end devices up to high-end devices**
- - [ ] Toaster preset mode
  - [ ] Arduino Uno R3 preset mode
  - [ ] Abacus preset mode
  - [ ] Bacteria preset mode 
- [ ] (better) mobile support
- - [x] Joysticks, better fullscreen support, etc.
    - [x] full gamepad support with Legacy Console / Xbox / PlayStation / Nintendo Switch presets and look/deadzone toggles (browser runtime; see README "Gamepad Support")
    - [ ] better fullscreen support
- [x] performance optimizations
  - [x] fixing some hotloops and removed barely used code
    - [x] math/LUT sweep: `Matrix4f.rotate` now uses the engine's sine table (2.42x per rotation) and the mipmap gamma filter uses a 256-entry decode table (4.6x) — see `perfbench/FINDINGS.md` #10/#11
    - [x] built-in PBR shader hot loops: `pow(x,5)` removed from every Fresnel term, point-light and per-light-loop culls moved before `normalize()`/the falloff divide, dead work dropped — pow/SFU pairs per invocation 3.41 → 0 in the forward light loop, 0 of 14.66 M point-lit fragments change an 8-bit output — see `perfbench/FINDINGS.md` #14 and `perfbench/shader/`
  - [ ] particle physics: `EntityFX` runs the full entity collision path (`World.getCollidingBoundingBoxes`, 79-95 ns/call, ~0.76 ms/tick at 4,000 particles) — needs allocation reuse in collision code, tracked at the top of `perfbench/HOTSPOTS.md`
  - [ ] `BlockModelRenderer` per-block allocations and the AO property lookups (see `perfbench/HOTSPOTS.md`)
- [ ] toggle between reporting or not that the client is cracked (for some servers that rejects cracked users, use at your own risk, dont abuse this or you may get banned from some servers, dont be an asshole)
- - [ ] _oficial client spoofing_

## stuff i may add later

- [ ] include the full game source code + assets + whatever needed to make a build of it and apply our patches on it, so the repo is not just a bunch of "patch" gibberish and actually turns into real patched Java code
- [ ] rewrite some stuff into Kotlin (only important logic or stuff that needs some kind of revamp)
