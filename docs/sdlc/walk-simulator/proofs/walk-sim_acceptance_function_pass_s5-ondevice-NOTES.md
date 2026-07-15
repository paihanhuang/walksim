# S5 step-injection on-device — regression FIXED + VERIFIED: Pikmin credits injected steps

- **Date:** 2026-06-30 · **Device:** Pixel XL rooted · **Consumer:** Pikmin Bloom v147 (throwaway) · **Module:** `com.pikmin.stephook` (LSPosed, scoped to Pikmin)

## Regression (found by on-device verification, not unit tests)
S5's `PaceClient` **never queried** `:app`'s `PaceProvider`: `lastPollMs` initialized to `Long.MIN_VALUE`, so `now - lastPollMs` **overflowed to a huge negative**, which is always `< POLL_INTERVAL_MS (500)` → `poll()` returned cached `NOT_PLAYING` forever → `PaceScheduler` got `playing=false` every tick → 0 steps. Present in the original S5 code; unit tests passed because they exercised `PaceScheduler` directly, never the overflowed poll guard.

## Fix
- `PaceClient.lastPollMs = 0L` (not `Long.MIN_VALUE`).
- Simplified `PaceClient` to a **direct driver-thread query** (dropped the single-worker + `future.get(100ms)` + `cancel(true)` design, which could also hang permanently on a cold binder query).

## PROOF (real, on-device — per the "verify on device, no claim without proof" directive)
Module trace after the fix: `PaceClient: read playing=true spm=110` → `SD tick: playing=true pulses=1 feeds=1` → `SD deliver: 1`.

Pikmin's home-screen **step count rose 1 → 53 → 470** as the injected walk played (phone still):

| time | steps |
|---|---|
| 09:19 | 1 (pre-fix) |
| 09:36 | 53 (t0, ~27 s after fix) |
| 09:40 | 470 (t1) |

Δ = **+417 steps in ~4 min ≈ 104 steps/min** — matches the injected pace (105–142/min, = speed ÷ 0.75 m stride × 60). Map behind the counter shows 渋谷 (Shibuya). Proofs: `walk-sim_acceptance_function_pass_s5-steps-t0.png` (53), `…-t1.png` (470).

## Verdict
**S5 ✅** — with S4's distance crediting, Pikmin now credits **both distance and steps** from the fully-simulated Shibuya walk. The complete goal (steps + location picked up by Pikmin Bloom) is met on-device.

*(Follow-up before commit: strip the temporary `SD tick`/`SD deliver`/`PaceClient: read` debug logs.)*
