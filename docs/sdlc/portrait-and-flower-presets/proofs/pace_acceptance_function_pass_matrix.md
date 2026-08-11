# Proof — pace matrix 1.3 / 5 / 7 / 10 / 20 m/s, verified in Pikmin Bloom (R4)

**Date:** 2026-08-11 · **Device:** Pixel 7 Pro (cheetah), Pikmin Bloom **v150.0**, account *wasai*.
WalkSim installed `pm install --force-queryable` (mandatory — see defect 1).

> **Revision (post code-review).** An earlier version of this file reported per-pace credit from windows run
> back-to-back and explained the shortfalls as "Google Fit lag spilling forward". A reviewer showed that claim
> does not survive its own data, so it is **withdrawn**. Every number below comes from ISOLATED windows: walk
> 120 s, STOP, allow 45 s for Fit to reconcile, then read the counter. The windows chain exactly (each t1 is the
> next t0), so no credit is unmeasured.

## The two defects this uncovered

**1. Pace channel unreachable (the historical "pace not picked up" bug).** A plain `adb install` breaks package
visibility, so the hook inside Pikmin's process cannot see the provider:
`Failed to find provider info for com.pikmin.walksim.pace` → `PaceClient: query returned null`. Reinstalling
with `--force-queryable` cleared it and the hook attached (`StepInjector: attach type=18 …FitnessManager$1`).
**This install flag is part of the deliverable.**

**2. Any pace above ~1.8 m/s was silently ignored.** BEFORE the fix the channel published a hard **144.0
steps/min** for 5, 7 and 20 m/s — exactly 1.8 m/s ÷ 0.75 m stride × 60, the `WalkProfile.speedRange` ceiling.
A second clamp (fixed 4.5 m/tick no-teleport bound) capped ground speed at 4.5 m/s independently. Both now
scale with the requested pace and evaluate to the old literals at the 1.3 default.

## App-side: the channel publishes what was asked for

| pace | published steps/min (BEFORE → AFTER) | expected (pace ÷ 0.75 m × 60) |
|---|---|---|
| 1.3 | 112.9 → 70 / 129 / 114 | ~104 |
| 5 | **144.0 clamped** → 389 / 373 / 392 / 371 / 370 | ~400 |
| 7 | **144.0 clamped** → 555 / 537 | ~560 |
| 10 | — → 809 / 784 | ~800 |
| 20 | **144.0 clamped** → 1615 / 1597 | ~1600 |

## In-game: what Pikmin actually credits (isolated 120 s windows)

| pace | counter t0 → t1 | Δ | credited steps/min | published | credited ÷ published |
|---|---|---|---|---|---|
| 1.3 | 69 696 → 69 912 | 216 | 108 | ~104 | **104%** |
| 5 (run A) | 69 912 → 70 171 | 259 | 130 | ~385 | **34%** |
| 5 (run B) | 70 171 → 70 417 | 246 | 123 | ~370 | **33%** |
| 7 | 70 417 → 71 257 | 840 | 420 | ~546 | **77%** |
| 10 | 71 257 → 72 375 | 1 118 | 559 | ~797 | **70%** |
| 20 | 72 375 → 74 845 | 2 470 | **1 235** | ~1 606 | **77%** |

**Verdict: all five paces are credited by Pikmin and the credited rate rises with pace** — 108 → ~125 → 420 →
559 → 1 235 steps/min. Before the fix everything ≥1.8 m/s was pinned at 144. Over the whole matrix the counter
went 69 696 → 74 845 (5 149 steps in ~16.5 min).

## Honest open item: the 5 m/s outlier

1.3 credits ~100% of what is published and 7/10/20 credit a consistent 70–77%, but **5 m/s credits only ~33%,
reproducibly** (three separate runs: 103, 130, 123 steps/min). I do not have an explanation.

Two hypotheses were tested and **both rejected**:
- *"Fit lag spills into the next window"* — rejected: the windows chain with no gap, and the following 7 m/s
  window shows no compensating surplus (it is itself at 77%).
- *"Bursty delivery is filtered — pulses inside a tick are 1 ms apart, so only ~1 per 500 ms tick survives,
  capping credit near 120/min"* — rejected: that ceiling would cap every fast pace, yet 20 m/s credits 1 235/min.

So the 5 m/s deficit is **unexplained, not explained away**. It does not block R4.2 (the pace is credited and
scales), but anyone relying on exact step accounting at 5 m/s should know the shortfall is real.

## Further caveat

A 20 m/s walk is 72 km/h. It is credited, but it is far outside plausible human motion and Pikmin's
"You're going too fast!" lock can appear. Use high paces knowingly.
