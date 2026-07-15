# STATUS — walk-simulator (pikmin2)

Tier: **big**  ·  Updated: 2026-07-01  ·  Owner: orchestrator

## Gates
| Gate | What | State |
|---|---|---|
| Gate 1 | Acceptance criteria approved (`requirements.md` v1) | ✅ **approved 2026-06-30** |
| Gate 2 | Final architecture approved (`design.md` v2 + cross-critique) | ✅ **approved 2026-06-30** |
| Gate 3 | Final acceptance / merge | ✅ **merged to `main` 2026-06-30** (fast-forward; feature branch retired) |

## Stage ledger
| Stage | State | Proof / notes |
|---|---|---|
| Enablement | ✅ | Device rooted (Magisk 30.7 + Zygisk + LSPosed 1.9.2); Pikmin Bloom v147 reinstalled (throwaway acct). |
| Spike S1a (sensor-injection via shell) | ❌ superseded | `proofs/walk-sim_spike_function_fail_s1a-*` — legacy HAL has no injection path. |
| Spike S1b (LSPosed step injection) | ✅ **PROVEN** | `proofs/walk-sim_spike_function_pass_s1b-*` — hook on Pikmin's `FitnessManager` STEP_DETECTOR; steps credited, phone still. Module: `spike-step-hook/`. |
| Research / requirements | ✅ | `requirements.md` v1 — 23 ACs approved; input = osmdroid pin, default Shibuya, Tokyo. |
| Design | ✅ | `design.md` **v2**; **4-reviewer shift-left cross-critique applied** (accept/reject log). **Gate 2 ✅ approved.** Caught pre-code: interface/compile mismatches, a live thread-leak into Pikmin, tautological-test traps, AC-23 mechanism gap — all folded. |
| Implement — S2 (`core-sim` port) | ✅ | 25/25 green + surgical (`WalkPlayer` facade + **geometric AC-3 bearing filter**). **qa-principle PASS**, **qa-function PASS** (after defect #1). Independently re-verified. Proof: `proofs/walk-sim_unit_function_pass_s2-core.xml`. |
| Implement — S3 (`core-osm`) | ✅ | 30 green (25 `core-sim` + 5 `core-osm`), surgical (only `settings`/`libs` touched). Non-tautological WALKABLE filter test + real-Shibuya integration (1 connected component, exact-length route, **forced-only** AC-3 bearing check). Gated by orchestrator independent audit. Proof: `proofs/walk-sim_unit_function_pass_s3-core-osm.xml`. |
| Implement — S4 (`app` location+FGS+UI) | ✅ | build green + 12 unit tests; **ON-DEVICE PASS** — Pikmin credited a Shibuya walk (avatar advanced + big flowers bloomed), gps/network/fused agree+mock, 346 fixes over a backgrounded FGS, restore-on-stop clean. Proofs: `walk-sim_acceptance_function_pass_s4-*`. |
| Implement — S5 (`stephook` steps) | ✅ | productionized step module + `PaceProvider` channel; build + PaceScheduler tests green. **ON-DEVICE PASS** — Pikmin step count **1→53→470→760** at the injected ~110/min pace (Pikmin now credits **both distance AND steps**). Regression (`PaceClient` `Long.MIN_VALUE` overflow → never queried the provider) found + fixed **on-device** (defect #2). Proofs: `…s5-*`. |
| Refinements — on-road + coverage (user) | ✅ | GPS-noise σ **4.0→2.5 m** (avatar hugs road; AC-10 stays real: stddev 2.465, autocorr 0.979) + **max unique-street coverage** route (`generateCoverage`, prefers unvisited edges) now the `:app` default (**+56 edges: 209 vs 153** on Shibuya). 43 tests green. **ON-DEVICE PASS** — footprint trail follows the street grid; Pikmin/flowers spread broadly across Shibuya; steps 760→**3269** (no crediting regression). Proofs: `…s6-*`. |
| Verify / Finish | ✅ | full JVM suite (43) + `:app` + `:stephook` builds green; `walk-simulator` fast-forward-merged → `main` (12607ef); feature branch deleted; tree clean. **Project complete: Pikmin Bloom credits both location and steps from a simulated Shibuya walk, proven on-device.** |
| Post-merge — location picker (user) | ✅ | Spinner picker (**Shibuya / Shinjuku / Xinyi·Taipei**); selecting one loads its area (map+pin) and runs the max-coverage default route (Shibuya = baked, others = live Overpass). Start-snap 50→200 m for city-centre pins. **ON-DEVICE PASS** — all 3 inject the correct city (Shibuya 35.659 / Shinjuku **35.689** / Xinyi **25.033**, all surfaces mock; maps match). Proofs: `…s7-*`. |
| Post-merge — spread-coverage loop + sequential mode (user) | ✅ | `spreadLoop` Eulerian street coverage + <40 m spur trim (repeat 29.3%, kept-coverage 99.9%); "All areas (sequential)" spinner default; Shibuya baked→live fetch; AC-10 v1.3. Checkpoint commit `0c3f856`. Superseded by v1.4 below. |
| **v1.4 — harvest sweep (user re-baseline, 2026-07-01)** | ✅ | Big flowers harvest within **~350 m** → route objective flipped from street coverage to **NEW-swept-area**: `sweepRoute` outward spiral of Dijkstra shortest paths (550 m lanes, first ring 300 m, duration-sized; fetch radius derived, cap 2000 m; `spacing_s` adb-tunable). `spreadLoop` + tests deleted. AC-2 revised, **AC-24** added, AC-3 scoped (requirements v1.4); design Overpass budget re-baselined ≈4 MB @ cap. Tests: grid revisit 0.000 / harvestCov 1.000; baked Shibuya len 5684 m, cov 1.000, revisit 0.175, gen 4 ms; suite 75/75. QA: principle **PASS**, function **PASS**, quality **FAIL→resolved** (stale budget line + `spacing_s` clamp). **ON-DEVICE PASS** — 1 h Shibuya spiral: live 1336 m fetch (no fallback), ring radius 355→624 m, steps 122–180/min the whole hour, Pikmin foregrounded, big-flower bloom (139 nectar) at run end. Proofs: `…sweep1h-shibuya-*` + `walk-sim_unit_function_pass_sweep-v14-0c3f856wt.xml`. |

| **v1.5 — reach 500 m + closed loop + leak fix + deadhead cut (user, 2026-07-01)** | ✅ | Three route improvements + a reach re-baseline. (1) **Closed run** (`close_s`, AC-24e): shortest path home + engine plays the full loop; on-device returns to start. (2) **Real-GPS-leak fix** (AC-12): holding fix at the pin before the fetch — no more jump to the user's real location on live-fetched cities. (3) **Deadhead cut** (AC-24b): connector Dijkstra penalizes walked edges (×3). (4) **Reach 350→500 m** → default spacing **550→850 m**: Shibuya revisit 0.078→**0.004**, coverage held at 500 m, swept radius 734→**852 m**. Presets: Okubo moved to dense centre **35.6975,139.7005**; Xinyi kept at 101 core. Suite **77/77**. QA: function+principle **PASS** (v1.5 function FAIL was a doc-only contradiction, fixed). **ON-DEVICE PASS** — Okubo/Shinjuku 20 km closed spiral (no leak, returns home, dense trail) + Xinyi 850-spacing spiral (no leak, Taipei fetch, ring 131→429 m, dense trail). Proofs: `…v15-*`, `…closedrun-smoke-*`, `…connector-penalty.xml`. |

## Open items
- **AC-24 spacing fine-tune:** default is now **850 m** (2×500 reach − 150 margin). If a sparse/irregular map shows un-harvested flowers *between* rings, tighten via `spacing_s` (no rebuild) and lower `DEFAULT_LANE_SPACING_M`. Grid cities (Tokyo/Taipei) verified gap-free on-device.
- **Closed presets (user, 2026-07-01):** the UI picker now runs a **CLOSED** loop by default for single-preset / custom-pin walks (MainActivity sends `close_s=1`); "All areas" stays an open tour (hands off city→city). **ON-DEVICE PASS** — a UI Shibuya pick (dur 3 min) played 20 min (closed extends past duration via `runUntilPathEnd`; open would stop at 3) and returned home (peak ~400 m → trending to start). Note: closed overshoot is large for *tiny* durations (3-min → ~1.4 km, min spiral leg ≫ budget); negligible at normal 30–60 min. Proof: `…ui-closed-default-shibuya-returnhome.log`.
- **AC-3 clarification — ✅ RESOLVED (human-confirmed 2026-06-30):** AC-3 exemption broadened to *functional* dead-ends (forbid *avoidable* >150° U-turns; forced ones exempt). `requirements.md` v1.2 updated; the S3 walker + test already comply.
- **Minor (non-blocking):** `Geo.haversineMeters` is duplicated in `:core-osm` (to keep it off `:core-sim`); consider hoisting `Geo` into `:core-model` in a later cleanup. `ShibuyaFixtureBaker` kept env-gated (reproducible re-bake + only live-HTTP exercise).

## Open defects
| # | Found by | Defect | Routed to | Retry | Status |
|---|---|---|---|---|---|
| 1 | qa-function (S2 gate) | **AC-3 geometric bearing bound unimplemented** — ported `GraphRandomWalker` topological-only; in-radius >150° hairpin allowed. **RESOLVED (retry 1):** `turnDegrees` filter added to `walk()` (>150° rejected at degree>1; dead-end/boundary exempt); test rebuilt with in-radius trap (RED→GREEN); AC-15 + main-source KDoc remapped. Independently re-verified 25/25 + real filter. | engineer | 1/3 | ✅ **resolved** |
| 2 | on-device (S5) | **Steps not credited** — `PaceClient` never queried the provider: `lastPollMs = Long.MIN_VALUE` → `now - lastPollMs` overflows negative → always `< 500 ms` poll guard → cached `NOT_PLAYING` forever. In original S5 code; unit tests missed it (they hit `PaceScheduler` directly). **RESOLVED:** `lastPollMs = 0L` + direct driver-thread query. Verified on-device (step count 1→470→760). | orchestrator | 1/3 | ✅ **resolved** |

## Decisions baked into requirements v1
- **Sequencing:** spike-first (de-risk step injection) — done.
- **Location:** standard mock (`isMock()==true`) for v1; root un-flagging = planned follow-up.
- **Scope:** streamlined personal tool — dropped offline area manager, polished map UI, broad API matrix.
- **Test account:** throwaway Google account.

## Outcome metrics (honest signals — never LOC)
| Metric | Value |
|---|---|
| Highest-risk assumption (step injection credited) | ✅ retired by Spike S1b |
| Time to first working result | S1b: ~same-day (root → LSPosed hook → credited) |
