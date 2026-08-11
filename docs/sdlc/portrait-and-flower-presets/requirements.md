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
**R2.2** Its route SHALL pass every big-flower site censused in the airport vicinity, by the shortest road walk.
**R2.3** "Passes" SHALL mean the route comes within the **250 m** harvest reach of the site (project v1.9 reach).

## R3 — Beach preset

**R3.1** The picker SHALL offer a beach-area preset chosen for big-flower density.
**R3.2–R3.3** As R2.2–R2.3 for that area.

## R4 — Pace fidelity

**R4.1** WHEN the user sets pace P ∈ {1.3, 5, 7, 10, 20} m/s, the walk SHALL be played at ≈P, not a clamped value.
**R4.2** Pikmin Bloom SHALL credit steps at a rate that scales with P (verified in-game, not only app-side).
**R4.3** The pace channel (`content://com.pikmin.walksim.pace/current`) SHALL remain reachable from Pikmin's
process — the historical "pace not picked up" defect.

## R5 — No regression (global constraint)

**R5.1** The ten existing harvest-sweep presets SHALL be behaviourally unchanged.
**R5.2** The JVM suite SHALL stay green against the pre-change baseline of **164 tests / 0 failures**.

## Decisions taken without the user (assumptions, flagged)

1. **Beach area = Enoshima / Katase-Kaigan.** Japan is the dominant Pikmin Bloom market, and big flowers only
   bloom where ~300 flowers accumulate within 40 m, so foot traffic decides. Enoshima is compact, wayspot-dense
   and heavily walked. *Measured after the fact: 1.78 flowers/frame on land vs Haneda's 1.25 — the denser of the two.*
2. **"All big flowers" is resolved at census-sample granularity, not per individual flower.** Pikmin exposes no
   flower coordinates; the tilted 3-D map cannot be read to a position. The established project method (v1.9 Ueno
   census) *counts* flowers per teleport sample and never locates them. So a site = a sampled point that was
   observed to bear big flowers. Sample spacing (~400–700 m) is matched to the 250 m harvest reach.
3. **Route model = tour, not sweep.** A spiral sweep is right for a uniformly dense city core; Haneda and
   Enoshima have flowers in a few clusters separated by barren apron/sea, so the shortest tour over the surveyed
   sites is the correct shape for "pass them all by the shortest path".
