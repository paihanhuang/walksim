# Location picker (Shibuya / Shinjuku / Xinyi) + per-location coverage route (user request)

- **Date:** 2026-06-30 · **Device:** Pixel XL rooted · **Consumer:** Pikmin Bloom (mock location)

## What was added
- **`Locations.kt`** — `PRESET_LOCATIONS`: Shibuya (35.6595,139.7006), Shinjuku (35.6896,139.7005), Xinyi/Taipei (25.0339,121.5645).
- **`MainActivity`** — a `Spinner` picker at the top; selecting a preset recentres the osmdroid map and moves the start pin (`wireLocationSpinner`).
- **Per-location default route = max unique-street coverage** (`GraphRandomWalker.generateCoverage`, already the `:app` default). Graph per area: Shibuya = baked `shibuya.json`; Shinjuku/Xinyi = **live Overpass fetch** at start (`WalkService.resolveGraph`).
- **`GraphRandomWalker`** — start-snap tolerance **50 m → 200 m** so landmark/city-centre preset pins land on the nearest mapped road (the route is still fully on-road from the snapped node; AC-4 holds). Fixed Xinyi (Taipei 101 pin was >50 m from a road node). `WalkService` user message de-hardcoded off "50 m".

## ON-DEVICE PROOF — all 3 presets load + inject the correct city
Selecting each preset moved the pin/map, and START injected **mock coords matching that city** (all surfaces gps+network+fused+passive, all `mock`):

| Preset | Pin set | Injected GPS (dumpsys) | Map landmarks | Proof |
|---|---|---|---|---|
| Shibuya | 35.6595,139.7006 | 35.659… (baked graph) | Shibuya Crossing | (S4 proofs) |
| Shinjuku | 35.6896,139.7005 | **35.689311,139.700066** (moving N) | 新宿駅 / 歌舞伎町 / 新宿御苑 | `…s7-shinjuku-injected.png` |
| Xinyi, Taipei | 25.0339,121.5645 | **25.033243,121.564085** (moving S) | 台北101 / 市政府 / 信義商圈 | `…s7-xinyi-taipei-injected.png` |

Live Overpass fetch confirmed working on-device for both non-baked cities (no "falling back to Shibuya"; injected coords are the selected city, ~2500 km from the Shibuya fallback for Taipei). Picker UI: `…s7-picker-3-locations.png`.

## Build
43 JVM tests green (snap-tolerance change broke nothing); `:app:assembleDebug` green.
