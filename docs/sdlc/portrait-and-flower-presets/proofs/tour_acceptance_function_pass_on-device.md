# Proof — both flower tours walked on the Pixel 7 Pro, every surveyed site passed (R2/R3)

**Date:** 2026-08-11 · **Device:** Pixel 7 Pro (cheetah), WalkSim installed `--force-queryable`.
**Method:** select the preset in the app's own picker (the real production path — NOT an `adb` intent), set pace
20 m/s so a multi-km tour completes in minutes, START, then poll the INJECTED fix
(`dumpsys location` → fused) every 5–6 s and measure each surveyed site's closest approach to that track.
Raw tracks: `haneda_acceptance_function_pass_device-track.txt`, `enoshima_acceptance_function_pass_device-track.txt`.

Sampling is coarse by construction — at 20 m/s a 6 s poll leaves ~120 m between samples — so these figures are an
UPPER bound on the true closest approach. The route geometry itself was measured separately against the live
Overpass graph (`flower-route_integration_function_pass_live-overpass.txt`: Haneda worst 53 m, Enoshima 223 m).

## Haneda Airport — 10/10 sites passed, worst 246 m (at 16.69 km of the 17.13 km tour)

| site | closest approach |
|---|---|
| Anamori-inari | 23 m |
| Haneda Innovation City | 19 m |
| Tenkubashi | 53 m |
| Otorii | 25 m |
| Anamori south | 49 m |
| Haneda 4-chome | 29 m |
| Asahi-cho | 36 m |
| Terminal 3 | 246 m |
| Terminal 1 | 14 m |
| Terminal 2 | 57 m |

Walk envelope `lat[35.5378..35.5572] lng[139.7406..139.7903]` fully contains the survey envelope
`lat[35.5449..35.5570] lng[139.7405..139.7876]` — i.e. the avatar really did cross the whole 4 km airport,
landside cluster AND both domestic terminals.

## Enoshima / Katase-Kaigan — 8/8 sites passed, worst 238 m (6.73 km tour, ran to completion)

| site | closest approach |
|---|---|
| Enoshima island shrine | 14 m |
| Iwaya (island south) | 238 m |
| Benten bridge | 11 m |
| Katase-Kaigan beach east | 32 m |
| Katase-Enoshima station | 21 m |
| Katase | 43 m |
| Katase inland | 49 m |
| Koshigoe | 150 m |

The tour played to its end and the mock was released back to real GPS afterwards, which also exercises the
`runUntilPathEnd` completion path.

## Incident during verification (kept for honesty)

The first attempt walked **Shibuya**, not Haneda: `overpass-api.de` returned `IOException: Overpass HTTP 504`
for several minutes (reproduced independently with `curl`, and on the kumi mirror), so the app took its
documented offline-Shibuya fallback. Once Overpass recovered (HTTP 200, 4.8 MB for the same bbox) the tour ran
correctly. Check the "Map fetch failed" banner before trusting a tour run.
