# Requirements — portrait-only UI + flower-tour presets (R1–R4)

Date: 2026-08-11 · Device of record: Pixel 7 Pro (cheetah), Pikmin Bloom **v150.0**, account *wasai* (Lv 59).
Tier: **Big** (UI surface + new route subsystem + mandatory on-device acceptance).

## R1 — Portrait only

**R1.1** WHEN the device is rotated to landscape WHILE WalkSim is foreground, the app SHALL remain in portrait.

*Rationale (measured, not assumed):* WalkSim is one tall `Column` with no landscape variant. Rotated, the
weighted map slot collapses to a letterbox strip, the `duration (min)` / `pace (m/s)` labels disappear and the
HUD card falls off-screen — see `proofs/portrait_acceptance_function_before_landscape.png`.

## R2 — Haneda Airport preset

**R2.1** The picker SHALL offer a `Haneda Airport, Tokyo, Japan` preset.
**R2.2** Its route SHALL pass every big-flower site censused in the airport vicinity, by a **near-shortest**
road walk. "Near-shortest" is nearest-neighbour + 2-opt, measured within **3.4%** of the brute-force optimum
over 25 seeded random surveys and gated at 5% (`FlowerRouteTest.toursAreOptimalAcrossRandomSurveys`). Exact TSP
is not attempted; 3% of a 17 km tour is ~9 minutes.
**R2.3** "Passes" SHALL mean the route comes within the **250 m** harvest reach of the site (project v1.9 reach).
**R2.4** IF no censused site is reachable (offline-fallback graph, or a failed fetch) the preset SHALL fall back
to the standard 500 m harvest sweep rather than stalling the session.

## R3 — Beach preset

**R3.1** The picker SHALL offer a beach-area preset chosen for big-flower density.
**R3.2–R3.4** As R2.2–R2.4 for that area.

## R4 — Pace fidelity

**R4.1** WHEN the user sets pace P ∈ {1.3, 5, 7, 10, 20} m/s, the walk SHALL be played at ≈P, not a clamped value.
**R4.2** Pikmin Bloom SHALL credit steps at a rate that scales with P (verified in-game, not only app-side).
**R4.3** The pace channel (`content://com.pikmin.walksim.pace/current`) SHALL remain reachable from Pikmin's
process — the historical "pace not picked up" defect.

## R5 — No regression (global constraint)

**R5.1** The ten existing harvest-sweep presets SHALL be behaviourally unchanged.
**R5.2** The JVM suite SHALL stay green against the pre-change baseline of **164 tests / 0 failures**.

## Scope note (2026-08-11, code review)

Haneda census points #12 (35.5430,139.7620) and #16 (35.5440,139.7440) were sampled while the avatar stood on
water, with the counted flowers on the far bank. They were initially EXCLUDED from the preset, which silently
narrowed R2.2 — the requirement says *every* censused site. They are now included; each snaps to the nearest
walkable bank and both are gated by `TourPresetReachTest`.

## Decisions taken without the user (assumptions, flagged)

1. **Beach area = Enoshima / Katase-Kaigan.** Japan is the dominant Pikmin Bloom market, and big flowers only
   bloom where ~300 flowers accumulate within 40 m, so foot traffic decides. Enoshima is compact, wayspot-dense
   and heavily walked. *Measured after the fact: 1.78 flowers/frame on land vs Haneda's 1.25 — the denser of the two.*
2. **"All big flowers" is resolved at census-sample granularity, not per individual flower.** Pikmin exposes no
   flower coordinates; the tilted 3-D map cannot be read to a position. The established project method (v1.9 Ueno
   census) *counts* flowers per teleport sample and never locates them. So a site = a sampled point that was
   observed to bear big flowers, and **what R2.2/R3.2 actually guarantee is "the route passes within 250 m of
   every sampled point that bore flowers"** — not "within 250 m of every individual big flower".
   **Correction (2026-08-11, code review):** an earlier draft claimed sample spacing was "~400–700 m, matched to
   the 250 m reach". Measured nearest-neighbour spacing of the census actually run is Haneda min 315 / mean 565 /
   **max 1001 m**, Enoshima max 726 m. At a 250 m reach, tiling needs ≤500 m, so the census leaves unsampled
   corridors and flowers can exist between sampled points. Closing that would need a re-census at ≤500 m
   spacing (~20 min device time); it has NOT been done. The guarantee above is the honest one.
3. **Route model = tour, not sweep.** A spiral sweep is right for a uniformly dense city core; Haneda and
   Enoshima have flowers in a few clusters separated by barren apron/sea, so the shortest tour over the surveyed
   sites is the correct shape for "pass them all by the shortest path".
