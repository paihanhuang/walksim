# Architecture Optimization Plan — walk-sim (Plan A)

**Tier:** Big · **Date:** 2026-07-14 · **Owner:** orchestrator (architect path)
**Goals:** versatility · maintainability · scalability
**Constraint:** walk-sim is Gate-3 complete and proven on-device — surgical evolution of a finished tool, **not** a rewrite. Preserve every existing AC and on-device proof.

> **Two-plan decomposition.** The UI reskin the user requested is a separate initiative tracked as **Plan B — Pikmin-style GUI redesign** (its own spec). Plan B executes **after** Plan A's stages have all cleared their 3-QA gates. This document is **Plan A only**; it makes **no GUI changes**.

---

## 1. Scope decisions (confirmed with the user, 2026-07-14)

| Question | Decision |
|---|---|
| What is driving the optimization? | **Harden the finished tool** — no new features; improve maintainability / robustness / testability with surgical changes. Matches the prior three-QA reconciled minimal path. |
| How should the two Gradle roots relate? | **Keep dual-root; harden the contract** — separate builds, with a single source-of-truth + schema version + a contract test both roots run for the Pace IPC. No monorepo absorb. |
| How far on the `WalkService` god-orchestrator? | **Extract its pure logic + test with fakes** — done last, behind the golden baseline, as a proven-equivalent refactor. |

Reference: a prior, more ambitious 9-stage plan and its three-QA critique live at `~/Projects/pikmin-grok/docs/sdlc/walk-simulator/arch-optimization-plan.md`; this plan adopts its reconciled minimal path plus a characterization-gated orchestrator extraction.

---

## 2. Current architecture (as-built)

Two Gradle roots, ~2.4k LOC production, connected by exactly one seam.

```
walk-sim/ (root #1)
  :core-model   value types: LatLng, Edge, WalkGraph, Route, DensePath, WalkProfile, SimSample
  :core-sim     Geo, GraphRandomWalker, SweepRoute (harvest spiral + Dijkstra), PathEngine,
                WalkingMotionEngine (OU motion → Flow<SimSample>), WalkPlayer (facade)
  :core-osm     RoadSource (iface), OverpassRoadSource (in-proc cache, injectable fetch),
                OverpassClient (HTTP), OverpassGraph (parse + WALKABLE + largest-component)
  :app          WalkService (FGS orchestrator, ~304 LOC), MainActivity (osmdroid UI),
                LocationInjector (mock gps/network/fused), Locations (presets + sequencePlan),
                WalkBus (in-proc state), PaceProvider (exported IPC), PaceDerivation,
                WalkStateMachine, LocationMapping

spike-step-hook/ (root #2)
  StepHook (Xposed) → StepInjector (per-process step driver) → PaceClient + PaceScheduler
  polls content://com.pikmin.walksim.pace/current   ← the ONLY cross-root seam
```

### Ranked smells (what this plan targets)

| # | Smell | Impact |
|---|---|---|
| S1 | `WalkService` (~304 LOC) is a **god-orchestrator** (graph resolve, sequential multi-city, hold mode, player config, injection, FGS lifecycle, notification, fallback) whose sequencing is **tested only on-device**. | Biggest maintainability liability; refactors can't be verified off-device. |
| S2 | **IPC contract is unversioned + string-duplicated** — `PaceProvider` and `PaceClient` independently hardcode the URI + `playing`/`stepsPerMin` columns; two separate `Pace` classes. | A rename on either side silently kills step crediting (defect history confirms this seam). |
| S3 | `Geo.haversineMeters` **duplicated** — a private copy in `OverpassGraph` keeps it off `:core-sim`. | Hygiene; blocks `:core-osm` using `Geo` cleanly. |
| S4 | `:app` **hardcodes `OverpassRoadSource()`** although `RoadSource` is already an interface. | No injection seam for offline / fixtures / service-level test doubles. |

Not targeted (measure-gated): eager frame/point materialization in the motion + densify path (no evidence of a real ceiling yet).

---

## 3. Governing principles

1. **Characterization-first** — no production line moves until a golden/characterization test pins current behavior. Refactors are proven *equivalent*, never "looks right."
2. **One change per stage, smallest that ships** — each stage is independently shippable and revertible.
3. **Surgical + minimal (CLAUDE.md)** — diff confined to the stage's scope; no drive-by cleanup, no abstraction without a present consumer, no speculative/dead code.
4. **Behavior frozen unless a stage explicitly changes it** — and then only with a human note and new goldens.

---

## 4. The 3-QA gate (applied to every stage)

**Hard rule (user constraint):** no stage N+1 work begins until stage N is approved by **all three** QA roles. A single FAIL blocks the stage; the reason is routed to the engineer (via `systematic-debugging` if it is a defect), fixed, re-verified, and the three QAs re-run.

| Role | Realized by | Checks each stage |
|---|---|---|
| **qa-function** | agent + `test-driven-development` | Every existing AC still holds; new tests are non-tautological (RED→GREEN); golden digests unchanged (or changed with a note). |
| **qa-quality** | agent + `requesting-code-review` | No perf/overhead regression (esp. the 24/7 GMS pace poll); no new dependency; review-clean. |
| **qa-principle** | agent | Minimalism + surgical-change: no dead/speculative code, diff in-scope, no premature abstraction. |

**Gate artifacts per stage:** full `./gradlew test` green both roots (proof XML under `docs/sdlc/walk-simulator/proofs/`), the stage's new tests, a recorded 3-QA verdict table, and on-device smoke where the stage touches device surfaces.

**Execution note:** engineer + all QA subagents run on **Opus 4.8** (`model: "opus"`) to save tokens; orchestration/architecture stays in the main session.

---

## 5. Stage ladder (lowest risk first; orchestrator extraction deliberately last)

| # | Stage | Intent | Risk | Primarily serves |
|---|---|---|---|---|
| 0 | Baseline freeze | Golden route + fixed-seed motion digests; route-gen/heap perf baselines; IPC contract table. **Zero production change.** | none | maintainability (safety net) |
| 1 | Geo hygiene | Hoist `Geo` → `:core-model`; delete the `:core-osm` haversine duplicate | trivial | maintainability |
| 2 | IPC contract harden | Single source-of-truth for authority/path/columns + `schemaVersion` + contract test both roots run | low–med | maintainability + safe cross-root evolution |
| 3 | RoadSource inject | Constructor-inject `RoadSource`; add `FixtureRoadSource` + `CompositeRoadSource(primary, fallback)`; bannered/asserted fallback | medium | versatility + testability |
| 4 | Orchestration extract | Pure `WalkSessionController` tested with fake sinks; `WalkService` becomes a thin FGS adapter | medium (last, behind goldens) | maintainability (kills god-class + device-only gap) |
| 5 | Scalability | **Measure-gated — may never run.** Only if Stage 0 baselines breach budget | higher | scalability |

Each stage builds the safety net the next leans on: Stage 0's goldens protect 1–4; Stage 3's injection seam is what lets Stage 4's controller be tested with fakes rather than a device.

---

## 6. Per-stage detail

### Stage 0 — Baseline freeze · *zero production change*

- **Work:** full `./gradlew test` both roots → proof XML. Golden **route fingerprints** (ordered-points hash + `totalLengthM`) for open / closed / shortfall on baked Shibuya **+ a synthetic grid** (hermetic — no live Overpass in goldens). Fixed-seed **motion-sample digests** (sample count, first/last pos, `cumulativeDistanceM`, `stepCount`, pause presence) for a 60 s open run and a closed multi-ring run. AC-24 harvest metrics (coverage@reach, revisit) captured as baseline numbers. **Perf/heap baselines** — host-JVM timing of route-build vs densify vs frame-gen + peak heap on an Okubo-scale graph. IPC contract table (authority/path/columns/types) into traceability.
- **New tests:** `RouteGoldenTest`, `MotionDigestTest`, an env-gated perf harness (JVM). Existing suites stay.
- **Exit:** goldens + baselines committed; **no production code touched**; suite green.

### Stage 1 — Geo hygiene · *trivial*

- **Work:** move `Geo` from `:core-sim` → `:core-model`; delete the private haversine copy in `OverpassGraph`; fix imports.
- **Tests:** `GeoTest` moves with it; the binding check is that **Stage 0 goldens stay byte-identical** (both impls used R = 6 371 008.8, so lengths must not shift).
- **Exit:** one `Geo` definition; `:core-osm` no longer needs a copy; goldens unchanged.

### Stage 2 — IPC contract harden · *low–med* (touches the 24/7 GMS seam)

- **Work (dual-root kept):** one canonical definition of authority/path/column names, copied into each root, with a **contract test in each root asserting its copy matches canonical** — a rename on either side goes RED. Add `schemaVersion` as an **additive, backward-compatible** column (old versionless client still parses; default 1). Freeze the ops envelope: ≤ 2 Hz poll, trivial synchronous query, no `future.get`/timeout redesign — restated as an invariant and asserted.
- **Tests:** dual-root contract test; provider query-shape; client parses old **and** new cursor.
- **Exit:** rename → RED; ops envelope unchanged; **dual-process on-device smoke** (Pikmin still credits steps).

### Stage 3 — RoadSource inject · *medium*

- **Work:** replace the inline `OverpassRoadSource()` in `WalkService.resolveGraph` with an injected `RoadSource` (default wired in `onCreate`, **no DI framework**). Add `FixtureRoadSource` (generalizes today's inline `shibuyaGraph()` asset load; Shibuya only — no speculative fixtures) and `CompositeRoadSource(primary, fallback)` encapsulating the current try/fallback. Keep the process-session cache on Overpass.
- **Tests:** composite primary-ok / primary-fail; fixture load; **fallback banner asserted** (no silent wrong-city teleport).
- **Exit:** failed Overpass still walks via fixture, bannered + tested; route math untouched (Stage 0 goldens hold); on-device smoke: a live city and a forced fallback both inject.

### Stage 4 — Orchestration extract · *medium; done last, behind the goldens*

- **Work:** extract `WalkService`'s sequencing into a pure `WalkSessionController` (in `:app` package `session/` — **not** a new Gradle module) taking injected `RoadSource`, route builder, and a `LocationSink` interface `{ engage(): Boolean; hold(pos); push(sample); restore() }`. It owns the engage→hold→fetch→play→restore order, sequential multi-preset plan, hold mode, and fallback wiring. `WalkService` shrinks to a thin FGS adapter (wakelock, notification, real `LocationInjector` as the sink), target ≤ ~150 LOC, no route/sequence logic.
- **Tests:** the **orchestration sequence matrix with a fake sink**, all off-device — engage-before-fetch (AC-16), hold-before-fetch (AC-12), restore-in-`finally` even on exception/cancel (AC-15), sequential hold-per-city order, hold-mode plays no route + pace not-playing, mock-fail raises the AC-16 banner.
- **Exit:** orchestration covered without a device; `WalkService` thin; all ACs + goldens unchanged; on-device smoke (short walk still credits distance + steps).

### Stage 5 — Scalability · *measure-gated, may never run*

- **Trigger:** only if Stage 0's route-gen p95 or peak heap **breaches the documented budget** on the Okubo-scale graph. Not breached → **do not run**; record the defer decision with the numbers.
- **If triggered:** fix the measured phase only (candidate suspects: the O(N)-per-waypoint snap scan in `sweepRoute`; the eager `ArrayList<MotionFrame>` in `WalkingMotionEngine`) with proven sample-equivalence. Optionally cancelable route build. No speculative redesign.
- **Exit:** baselines back within budget; goldens hold.

---

## 7. Cross-cutting testing strategy

| Layer | What | When |
|---|---|---|
| Unit | controller, composite/fixture source, contract constants, derivations | every stage |
| Golden | route fingerprints + fixed-seed motion digests | Stage 0, verified unchanged 1–4 |
| Contract | Pace IPC schema (authority/path/columns/version) | Stage 2 |
| Module integration | `:core-osm` + `:core-sim` on fixtures | Stage 3 |
| On-device smoke | short preset walk credits distance + steps; forced-fallback; dual-process pace | after Stages 2, 3, 4 |

**Non-regression gate (every stage):** full `./gradlew test` both roots; goldens unchanged unless the stage declares a change (then new goldens + human note); no new dependency; diff surgical; proof artifacts under `docs/sdlc/walk-simulator/proofs/` in the existing naming convention.

---

## 8. Success metrics

- `WalkService` ≤ ~150 LOC with no route/sequence logic (Stage 4).
- Single `Geo` definition (Stage 1).
- IPC rename → RED contract test (Stage 2).
- Offline / forced-fallback walk works + is bannered (Stage 3).
- Orchestration covered off-device (Stage 4).
- Scalability: evidence-based defer, or a measured, equivalent fix (Stage 5).

---

## 9. Explicitly out of scope

No `WalkRequest` / intent-parser consolidation · no `RouteStrategy` interface · no new Gradle modules (`:core-session` / `:core-cli`) · no stephook monorepo absorb (dual-root kept) · no nearest-preset multi-fixture fallback · **no GUI changes (Plan B)** · no weakening of any AC or on-device proof. Each reopens only on a named failing test / defect / approved product change.

---

## 10. Relationship to Plan B

Plan B (Pikmin-style GUI redesign) is a separate spec, brainstormed and planned independently, and executed **after** Plan A completes. Plan A intentionally leaves `MainActivity` and the UI untouched so Plan B starts from the hardened baseline.

---

*Plan A design doc. Next: user review of this spec → `writing-plans` to produce the granular executable plan → staged, 3-QA-gated execution.*
