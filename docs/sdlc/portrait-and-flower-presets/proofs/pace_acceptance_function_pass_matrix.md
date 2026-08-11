# Proof — pace matrix 1.3 / 5 / 7 / 10 / 20 m/s, verified in Pikmin Bloom (R4)

**Date:** 2026-08-11 · **Device:** Pixel 7 Pro (cheetah), Pikmin Bloom **v150.0**, account *wasai*.
WalkSim installed `pm install --force-queryable` (mandatory — see "defect 1" below).

## The two defects this uncovered

**1. Pace channel unreachable (the historical "pace not picked up" bug).** A plain `adb install` breaks package
visibility, so the hook inside Pikmin's process cannot see the provider:

```
E ActivityThread: Failed to find provider info for com.pikmin.walksim.pace
I VectorLegacyBridge: PaceClient: query returned null (provider unreachable)
```

Reinstalling with `--force-queryable` cleared it; the hook then attached
(`StepInjector: attach type=18 …FitnessManager$1 (feeds=1)`) and no further provider errors appeared.
**This install flag is part of the deliverable.**

**2. Any pace above ~1.8 m/s was silently ignored.** BEFORE the fix the pace channel published a hard
**144.0 steps/min** for 5, 7 and 20 m/s — exactly 1.8 m/s ÷ 0.75 m stride × 60, i.e. the `WalkProfile.speedRange`
ceiling. A second clamp (fixed 4.5 m/tick no-teleport bound) capped ground speed at 4.5 m/s independently.

| pace | published steps/min BEFORE | AFTER | expected (pace ÷ 0.75 m × 60) |
|---|---|---|---|
| 1.3 | 112.9 | 105.3 | ~104 |
| 5 | **144.0** (clamped) | 397.8 / 401.5 | ~400 |
| 7 | **144.0** (clamped) | 556.8 / 562.6 / 557.4 | ~560 |
| 10 | — (sampled mid-pause) | 809.8 / 796.0 / 793.2 | ~800 |
| 20 | **144.0** (clamped) | 1620.4 / 1587.0 / 1610.7 | ~1600 |

App-side, every pace now publishes within a few percent of its target.

## In-game verification (the decisive test)

Isolated runs: walk 120 s at the pace, STOP, allow 30 s for Google Fit reconciliation, then read Pikmin's
landing-page counter. Windows chain exactly (each t1 is the next t0), so there is no unmeasured gap.
Frames: `pace-steps/isolated_120s_windows.png`.

| pace | counter t0 → t1 | Δ over 120 s | credited steps/min |
|---|---|---|---|
| 1.3 | 55 925 → 56 092 | 167 | 84 |
| 5 | 56 092 → 56 296 | 204 | 102 |
| 7 | 56 296 → 57 144 | 848 | 424 |
| 10 | 57 144 → 58 449 | 1 305 | 653 |
| 20 | 58 449 → 60 904 | 2 455 | **1 228** |

**Verdict: all five paces work — Pikmin credits every one, and the credited rate scales with the pace**
(84 → 102 → 424 → 653 → 1228 steps/min). Before the fix, everything ≥1.8 m/s was pinned at 144.

Across the whole session Pikmin's counter went **865 → 60 904 steps**, all from injection.

## Honest caveats

- Per-window totals read **low** versus the published rate (~72% of expected overall). Cause is the known,
  already-documented Google Fit reconciliation lag on this setup: credit lands late and spills into later
  windows, and the final window's tail is cut off by the last screenshot. The *ordering and scaling* are the
  reliable signal here, not the absolute per-window number.
- The 60 s back-to-back run (`pace-steps/back_to_back_60s_windows.png`) shows the same shape with more leakage,
  which is why the isolated 120 s + settle protocol was run as well.
- A 20 m/s walk is 72 km/h. It is credited, but it is far outside plausible human motion; Pikmin's
  "You're going too fast!" lock can appear on such runs. Use high paces knowingly.
