# Proof — Ueno "best route" recriteria, decided by real in-Pikmin big-flower census (v1.9)

**Date:** 2026-07-08 · **Device:** Pixel 7 Pro (cheetah, Android 17), Pikmin Bloom v148, account *traso tw*.
**Decision rule (user):** the route that passes **more distinct big flowers** becomes the Ueno default — judged on
**real Pikmin observation, not the graph proxy**.

## New criteria (v1.9)
1. A big flower is harvestable within **250 m** of the avatar (stricter than v1.5's 500 m; Ueno-scoped).
2. Objective: maximize **distinct big flowers passed within a 10 km budget**.
3. Best start: surrounded by the most big flowers within a **500 m** radius.
4. Equivalent: pass the most distinct flowers on **both sides** of the route.
5. Efficiency: **minimal path overlap** (no back-and-forth).

## Candidates (both 10 km, same Ameyoko start 35.7089,139.7745 — isolates the spacing change)
- **OLD** = current tuning, lane spacing **850 m**.
- **NEW** = both-sides-tiling spacing **500 m** (= 2×250 m reach), chosen from a live-graph sweep of 400/450/500/550.

## Live-graph design metrics (proof: `ueno-route-diagnostic_v1.9_250m-reach.txt`, 18 357-node Overpass graph)
| route | spacing | length | maxR | cov@250 | revisit |
|---|---|---|---|---|---|
| OLD | 850 | 10 191 m | 1473 m | **0.692** | 0.002 |
| NEW | 500 | 10 040 m | 1271 m | **0.954** | 0.062 |

cov@250 (fraction of inner-disc nodes within 250 m of the route) strongly favors NEW. BUT the honest read is
subtler: OLD's wider spiral reaches a **larger disc** (maxR 1473 vs 1271), so its *covered area*
(≈ cov × π·innerR²) is comparable to NEW's (OLD ≈ 2.98 M m² vs NEW ≈ 2.81 M m²). So the proxy does **not**
predict a blowout — which is exactly why the user required a real in-game count.

## Census method (real Pikmin, "sample each route's path")
Tooling added: walk-sim `hold_s=1` static-hold action (live-re-pointable → fast teleport with no STOP→real-GPS
detour) + `core-osm/UenoRouteDiagnostic` (dumps each route's road-snapped waypoints). Protocol: teleport the
avatar to each waypoint, **dwell 18 s** so big flowers fully load (verified necessary — an 8 s dwell renders
half-loaded/barren), screenshot, count **blooming big flowers** per frame (large flowers on stalks; excluding
the ground carpet, mushroom battle-spots, and PokéStop discs). Passenger-mode dismisses the speed lock once.
Frames + montages retained in the proof bundle. OLD captured first, then NEW; the shared start frame counted
**8 for both** (OLD-01 = NEW-01 = 8), confirming negligible harvest-depletion between passes.

## Results — 11-point pass (~1000 m spacing)
| frame | 01 | 02 | 03 | 04 | 05 | 06 | 07 | 08 | 09 | 10 | 11 | **total** |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| OLD | 8 | 3 | 7 | 7 | 8 | 6 | 4 | 4 | 5 | 4 | 6 | **62** |
| NEW | 8 | 4 | 5 | 6 | 8 | 7 | 5 | 5 | 6 | 6 | 5 | **65** |

NEW ahead 65:62 — but within counting noise, and 1000 m spacing leaves ~400 m gaps between frames (undersamples
exactly where NEW's advantage — gap-filling — lives). Hence a full 21-point (500 m) census was run to decide.

## Results — full 21-point census (500 m spacing, identical protocol)
Per-frame blooming-big-flower counts:
- **OLD** 11-pt: `8 3 7 7 8 6 4 4 5 4 6` = 62 · infill: `7 5 6 8 6 8 3 7 7 5` = 62 → **124**
- **NEW** 11-pt: `8 4 5 6 8 7 5 5 6 6 5` = 65 · infill: `6 7 6 5 7 5 7 5 7 5` = 60 → **125**

| route | spacing | 21-pt big-flower count | revisit (overlap) | cov@250 |
|---|---|---|---|---|
| OLD | 850 m | **124** | **0.002** | 0.692 |
| NEW | 500 m | **125** | 0.062 | 0.954 |

## Conclusion — TIE on the decisive metric; NEW not adopted
124 vs 125 is a **0.8% gap — inside counting noise** (per-frame error ±1–2 over 42 frames). By the real
in-Pikmin count (the user's decisive test), **spacing 500 does NOT pass more big flowers than spacing 850** at
a 10 km budget from Ameyoko. Why the graph proxy misled: OLD's wider 850 m spiral reaches a **larger disc**
(maxR 1473 vs 1271 m), and its extra area cancels its 31% intra-disc gaps — in uniformly flower-dense Ueno,
distinct-flowers ≈ covered-area, and the two cover ~equal area (OLD 2.98 M vs NEW 2.81 M m²).

On the remaining criteria the tie breaks toward OLD on **overlap** (criterion 5: revisit 0.002 vs 0.062) but
toward NEW on **gap-freeness** (criterion 4). Coverage does not saturate with length, so the real lever for
*more* flowers is route length, not spacing.

## Tiebreak — walk time to the same coverage (user 2026-07-08: "prefer the shorter time span")
Distinct harvestable locations (nodes within 250 m of route ∝ big flowers) vs length/time (proof:
`ueno-route-diagnostic_v1.9_250m-reach.txt`, grep `SWEEP`):

| coverage | 500 m route | 850 m route |
|---|---|---|
| ~1700 locs | ~6 km → **82 min** | ~8 km → ~106 min |
| full tie (~124 flowers) | 10.0 km → **129 min** | 10.2 km → 131 min |

500 m reaches the **same** coverage in **less walk time** — marginally at full coverage (129 vs 131 min),
clearly at partial coverage (850 m's gaps only fill once its bigger disc is walked). **FINAL DECISION: Ueno
default → 500 m / 10 km** (`Locations.kt`, `LocationsSpacingTest`), chosen on shortest-time-to-coverage. On-device
verified: selecting the Ueno preset sets duration to 10 km/1.3 m/s ≈ 128 min and runs spacing 500.

**Honest caveats:** tilted ~300 m view (far/occluded flowers undercounted); flower load needs ~18 s dwell/point
(verified); OLD captured before NEW, but the shared start frame counted 8 for both → negligible depletion. All
42 frames (as 2-up montages) are in `proofs/ueno-census-frames/` (`fold_*`/`fnew_*` = 11-pt pass, `goldf_*`/`gnewf_*` = 500 m infill).
