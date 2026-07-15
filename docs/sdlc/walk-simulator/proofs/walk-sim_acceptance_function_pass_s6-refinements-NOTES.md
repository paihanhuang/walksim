# Refinements — reduce GPS noise (on-road) + max-coverage default route (user requests)

- **Date:** 2026-06-30 · **Device:** Pixel XL rooted · **Consumer:** Pikmin Bloom v147

## 1. GPS noise reduced → avatar hugs the road
- `WalkingMotionEngine.NOISE_STAT_STDDEV` **4.0 → 2.5 m** (steady-state per-axis σ; `NOISE_PHI=0.98` unchanged so autocorrelation stays high). One surgical constant.
- AC-10 test still real (not weakened): measured **stddev = 2.465 m** (∈[2,15]), **lag-1 autocorr = 0.979** (>0.5), **accuracy ∈ [5.09, 18.5] m** (⊂[5,50]).
- **On-device:** the walked footprint trail follows the Shibuya street grid. Proof: `…s6-onroad-footprints-on-streets.png`.

## 2. Max unique-street-coverage route, now the `:app` default
- `GraphRandomWalker.generateCoverage(...)` prefers **unvisited edges** (tracks visited undirected edge keys) while keeping AC-2 (radius), AC-3 (≤150° turn), AC-4 (road-snap), AC-5 (determinism). Random walker untouched.
- `WalkPlayerConfig.coverage = true` (default) → `WalkPlayer.play` branches to coverage; `:app` Start uses it with **zero `MainActivity`/`WalkService` changes**.
- Test (baked Shibuya, center pin, target 5000 m, radius 800 m, seed 42): **coverage 209 unique edges vs random 153 (+56)**, all in-radius, deterministic. `core-osm/…/ShibuyaCoverageTest.kt`.
- **On-device:** Pikmin/flowers spread broadly across the Shibuya street grid. Proof: `…s6-coverage-shibuya-spread.png`.

## No regression (both credits still work on the new build)
- **Steps** kept crediting: count 760 → **3269** rising, hit a "12000 steps" achievement. Proof: `…s6-steps-3269-noregression.png`.
- **Distance** still credits (big flowers keep blooming).
- Build: 43 JVM tests green; `:app:assembleDebug` green (clean re-run verified).

*(Proofs 2 & 3 are captured through Pikmin's lifelog/achievement popups — the popups themselves are extra evidence of heavy step/distance crediting.)*
