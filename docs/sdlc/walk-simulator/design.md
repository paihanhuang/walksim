# Design — walk-simulator (pikmin2)

Tier: **big** · Date: 2026-06-30 · Owner: architect · Revision **v2 (cross-critique applied)** · Gate: **Gate 2 (pending)**
Implements `requirements.md` (Gate 1 ✅). Grounded in Spike **S1b** (LSPosed step injection proven). v2 folds the 4-reviewer cross-critique (log at bottom).

**Goal:** a personal, one-device tool that makes Pikmin Bloom credit a realistic simulated walk — **location + steps** — on the rooted Pixel XL, phone still.

**Architecture:** A pure-Kotlin **walk engine** (ported from `../pikmin`, JUnit5-tested) turns a start pin + duration into a road-snapped `Flow<SimSample>` via a thin `WalkPlayer` facade. An Android **controller app** plays that stream as a mock-location fix stream (gps+network+fused) from a wakelocked foreground service, drives an osmdroid pin UI, and publishes live `{playing, stepsPerMin}` via an exported query-only `ContentProvider`. The proven **LSPosed step module** (in Pikmin's process) polls that provider (≤2 Hz) and feeds `STEP_DETECTOR` events to Pikmin's `FitnessManager` via the sensor's captured `Handler`.

**Tech stack:** Kotlin 2.1.0 · Gradle 8.9 · AGP 8.7.3 · JDK 17 · coroutines (`Flow`) · **JUnit5 (Jupiter, `useJUnitPlatform()`)** · osmdroid (no Google API key) · Overpass API · Xposed API 82 · **UiAutomator** for `:app` cross-process on-device checks (reads Pikmin's own UI). `compileSdk 34`, **`minSdk 29`** (single-device scope — raised from 26 per cross-critique).

## Global constraints (apply to every task)
- **Minimalistic + surgical** (non-negotiable): every line traces to an AC; touch only relevant code.
- Kotlin, `jvmTarget=17`; determinism within-device (AC-5).
- Location v1 = standard mock (`isMock()==true`); un-flagging is a later follow-up.
- Target = Pixel XL / Android 10 (API 29) only; throwaway account.
- **Not ported (explicit negative scope):** runtime speed-multiplier ({0.5,1,2,3}×), offline area manager, file-cache subsystem, Google Maps SDK, API-level matrix.
- **GMS step path / `STEP_COUNTER` branch — RESTORED (bug-fix 2026-07-13, see §"Weekly-Challenge fix").** Originally dropped as negative scope; a device bug proved the Weekly-Challenge "Step Challenges" reads Google Fit's `STEP_COUNTER` in the **`com.google.android.gms`** process (a different process from Pikmin), so a Pikmin-only detector feed can never move it. The counter feed + GMS scope are now required.

---

## Module structure (Gradle multi-module)

| Module | Responsibility | Type | Key ACs |
|---|---|---|---|
| `:core-model` | Value types: `LatLng`, `Edge`, `WalkGraph`, `WalkProfile`, `SimSample`. Port of `../pikmin/walk-sim/core-model`. Kept **separate** from `:core-sim` so `:core-osm` can consume `WalkGraph`/`LatLng` **without** pulling in coroutines/sim code. | Pure Kotlin/JVM | — |
| `:core-sim` | Walk engine: `Geo`, `GraphRandomWalker`, `PathEngine`, `WalkingMotionEngine` (ported, 22 JUnit5 tests) **+ new `WalkPlayer` facade**. Consumes `WalkGraph`, emits `Flow<SimSample>`. | Pure Kotlin/JVM | AC-1,2,3,5,7,8,9,10 |
| `:core-osm` | On-demand Overpass fetch → build **connected foot-walkable** `WalkGraph` for the bbox around start. WALKABLE filter **at the use-site**; **stitch by OSM node id → keep largest connected component**. **In-memory session cache only** + a **baked Shibuya graph fixture** (offline default + deterministic test fixture). | Kotlin/JVM (HTTP) | AC-4 (AC-2 coverage-only) |
| `:app` | osmdroid pin UI (default Shibuya) + duration/pace + start/pause/resume/stop + live status; **wakelocked FGS (`location`)** playing `Flow<SimSample>` → mock **gps+network+fused**; exposes `PaceProvider`; setup checks. | Android app | AC-11–16, 20–23 |
| `:stephook` | Productionized `spike-step-hook`: poll `PaceProvider`, feed the captured step listeners at live pace via their captured `Handler`, **park when idle**, **cancel feed on `unregisterListenerImpl`** (no thread leak). **Per-process narrow feed (bug-fix 2026-07-13):** scoped to **`com.nianticlabs.pikmin`** (feed `STEP_DETECTOR` → `FitnessManager` → live/landing-page count) **and `com.google.android.gms`** (feed `STEP_COUNTER` → Google Fit's `LocalSensorAdapter` → Weekly-Challenge "Step Challenges" + reconciliation). Counter = captured hardware base + running injected total, delivered as cumulative-since-boot. | Xposed module | AC-17,18,19 |

### Corrected interface block (matches the actual ported code)
```kotlin
// :core-model  (ported — Float fields; add cumulativeDistanceM; NO `paused` field)
data class LatLng(val lat: Double, val lng: Double)
data class Edge(val toNode: Long, val geometry: List<LatLng>, val lengthM: Double)
data class WalkGraph(val nodes: Map<Long, LatLng>, val adjacency: Map<Long, List<Edge>>)
data class WalkProfile(                       // meanSpeedMps default 1.4 -> 1.3 on port
    val meanSpeedMps: Double = 1.3, val speedRange: ClosedRange<Double> = 0.8..1.8,
    val maxAccelMpsSq: Double = 0.5, val strideM: Double = 0.75, val pauseRatePerMin: Double = /*ported*/ )
data class SimSample(                          // ported fields + cumulativeDistanceM (2-line projection)
    val pos: LatLng, val speedMps: Float, val bearingDeg: Float, val accuracyM: Float,
    val stepCount: Int, val tickIndex: Long, val cumulativeDistanceM: Double)
//   playing := speedMps > 0f ;  stepsPerMin := speedMps / strideM * 60   (derive; no `paused` field)

// :core-sim  (ported object kept as-is; NEW thin facade)
object WalkingMotionEngine { fun play(path: DensePath, profile: WalkProfile, durationMs: Long, seed: Long): Flow<SimSample> }
class WalkPlayer(private val graph: WalkGraph, private val cfg: WalkPlayerConfig) {
    fun play(start: LatLng, durationS: Long): Flow<SimSample>   // snapStart→generate(targetLen=mean×D, radiusM)→densify(spacingM)→play(·, durationS*1000, ·)
}
data class WalkPlayerConfig(val profile: WalkProfile, val radiusM: Double = 800.0, val spacingM: Double = 1.0, val seed: Long)

// :core-osm
interface RoadSource { suspend fun graphAround(center: LatLng, radiusM: Int): WalkGraph }  // bbox side ~2R; circular R enforced later by the walker

// :app  — the pace channel the step module reads
// content://com.pikmin.walksim.pace/current -> cursor{ playing:INT(0/1), stepsPerMin:REAL }  (query-only; insert/update/delete/getType stubbed)

// :stephook  — pure, clock-injectable core extracted from the Xposed glue for fast RED->GREEN tests
class PaceScheduler(private val clock: () -> Long) {   // AC-18/19 unit-testable with a fake clock
    fun onTick(playing: Boolean, stepsPerMin: Float): Int   // returns # STEP_DETECTOR pulses to emit this tick
}
```

## Coordination transport & robustness (cross-critique decisions)
- **Channel:** `:app` exposes an **exported, permission-free** `PaceProvider` (`authorities=com.pikmin.walksim.pace`, `query()`-only). `:stephook` (using Pikmin's context via `AndroidAppHelper.currentApplication()`, read **lazily at tick time**, never in `handleLoadPackage`) polls it. Android 10 (API 29) needs **no `<queries>`** package-visibility. Rejected: file (uid/scoped-storage friction), socket (heavier), broadcasts (chatty).
- **Poll rate:** ≤ 2 Hz (every ≥500 ms), cached between polls; on null-app / null-cursor / exception / `playing==0` → **feed 0 steps** (never propagate into the sensor hot path). [qa-quality QP-1/QP-3] **Implementation note (as-built):** the planned **≤100 ms `future.get` timeout was removed** — it *hung permanently* when the first cold binder query outran the timeout (the cancelled worker stayed blocked and later polls queued behind it; see `PaceClient` KDoc). The query is therefore **synchronous with no timeout** on the driver thread. See §"Weekly-Challenge fix" for the accepted blast-radius risk now that this also runs in `com.google.android.gms.persistent`.
- **AC-23 presence detection (mechanism gap fix):** `:stephook` polls `PaceProvider` **continuously whenever loaded** (even idle, to detect play-start); `PaceProvider` records `lastQueriedElapsedMs`. `:app` infers "step module active" iff queried within the last few seconds while Pikmin is foregrounded; else shows the AC-23 setup warning. No new channel needed. [qa-function #2]
- **Thread-leak fix (CRITICAL):** hook `unregisterListenerImpl` and gate each feed loop with a per-listener cancellation (`AtomicBoolean` + `WeakReference` liveness) so threads never accumulate on sensor re-registration (reproduced live 1→2). Budget: ≤2 `:stephook` threads for process life. [qa-quality #1/QT-1]
- **Handler delivery:** deliver `onSensorChanged` via the sensor's captured `Handler`, not a raw thread (avoids a race inside Pikmin). [qa-quality #6/QT-3]
- **Idle parking:** when `playing==false`, **park on a condition** (don't busy-poll `onSensorChanged`); the ≤2 Hz presence poll is the only idle wakeup. [qa-quality QT-5]
- **FGS liveness (`:app`):** `PARTIAL_WAKE_LOCK` for the 1 Hz loop + request battery-optimization exemption; **keep-screen-on / charge-ceiling** strategy to avoid the prior soak's silent credit truncation (20 km→12.5 km). Playback + walk-gen run **off `Dispatchers.Main`** (engine materializes all ticks eagerly). [qa-quality #2/#5, QF-2b]
- **Overpass failure:** connect 10 s / read 20 s timeout, **1 retry w/ backoff**, then an AC-23-style setup error; the **baked Shibuya fixture** means the default walk works with zero network. [qa-quality QO-5, engineer R3]

## AC → module traceability (corrected; full matrix in `traceability.md`)
- **AC-1,2,3,5,7,8,9,10 → `:core-sim`** — note **AC-2 radius is enforced by the ported `GraphRandomWalker.walk()`**, already tested; `:core-osm` supports AC-2 only by **fetching a bbox covering R** (coverage, not clipping — don't duplicate). [qa-principle #3, qa-function #10]
- **AC-4 → `:core-osm`** (WALKABLE filter at use-site + stitching).
- **AC-11–16 → `:app`** (mock **gps+network+fused**; teleport-leak fix per prior lesson).
- **AC-17–19 → `:stephook` + `:app`** (`PaceProvider` + `PaceScheduler`).
- **AC-20–23 → `:app`** (AC-16 detection = reactive `try/catch SecurityException` on `addTestProvider`).

## Build sequence (each a working, testable increment; per-sub-project JIT bite-sized plans)
1. **S2 — `:core-model` + `:core-sim`** *(near-verbatim port + `WalkPlayer` facade)*: JUnit5 tests green. Port edits only: `meanSpeedMps`1.4→1.3 (+assert default), add `SimSample.cumulativeDistanceM`, re-map old `// AC-N` comments, add facade + a **bearing-based ≤150° no-U-turn test** (topological proxy is insufficient). No device.
2. **S3 — `:core-osm`** *(GREENFIELD — largest new-code chunk; NOT a port)*: Overpass parse + foot-filter + **OSM-node-id stitching + largest-connected-component**; **non-tautological exclusion test** (fixture JSON with motorway/trunk/foot=no/access=private → assert absent); baked Shibuya fixture; connectivity assertion; `:core-sim` generates a real Shibuya route on it.
3. **S4 — `:app` location + FGS + osmdroid** *(reuses proven `../pikmin/spike-s0a` MockGnssInjector/WalkInjectionService)*: **this run is the real de-risk spike for location on this device/OS** — signal = Pikmin's **"Flowers planted" / "Next coin in"** via before/after `screencap` (AC-11); `dumpsys location` gps/network/fused agreement (AC-12); screen-off/backgrounded delivery (AC-14).
4. **S5 — `:stephook` + `PaceProvider`** *(real behavioral rewrite of the spike, not a copy)*: extract pure `PaceScheduler` (fake-clock unit tests for AC-18/19); dynamic pace from `PaceProvider`; thread-leak + Handler + idle-park fixes; on-device step-credit proof via `screenrecord` + correlated distance log.

## Performance budgets (qa-quality; measured live where noted — full set to `proofs/` per sub-project)
Route-gen <1 s (D=60 min host) / <5 s (D=360 min on-device); Overpass ≤4 MB decoded *(v1.4 re-baseline: AC-2's duration-derived fetch radius caps at 2000 m → projected ≈3.9 MB decoded / ≈590 KB gzip from the measured 1.54 MB @ 1257 m (qa-quality 2026-07-01, two independent projections); parse transient ≈40 MB ≈ 15% of the Pixel XL's 256 MB heap; was "≤2 MB" measured in the fixed-300 m era)*, fetch <8 s Wi-Fi; FGS <4%/h battery (unit measured 3575 mAh, health GOOD), fix jitter p95∈[900,1100] ms; PaceProvider IPC p95<15 ms; `:stephook` **1 driver thread per hooked process** (2026-07-13: 2 processes → 2 threads total, ≤1 each), **<1% of one core per driver** (measured 2026-07-13 mid-walk: GMS ≈0.27%, Pikmin ≈0.33%, combined ≈0.60%/core); injector off-Main, zero ANR.

## Cross-critique log (Stage 3) — accept/reject
| # | Reviewer | Concern | Disposition |
|---|---|---|---|
| F1–F6 | engineer + qa-function#5 + qa-principle#7 | Interface block ≠ ported code (SimSample Float/no-cumDist/no-paused; WalkingMotionEngine object w/ ms; SimConfig undefined; WalkGraph data-class; JUnit5; meanSpeed 1.4) | **ACCEPT** — interface block rewritten; `WalkPlayer` facade added; derive `playing`/pace from `speedMps`; add `cumulativeDistanceM`; JUnit5; 1.3 m/s. |
| R1 | engineer | Provider IPC feasible on API29 (no `<queries>`), lazy `currentApplication()` | **ACCEPT** — encoded in Coordination section. |
| R2 | engineer + qa-function#3 | `:core-osm` node-id stitching / connectivity (silent-failure) | **ACCEPT** — stitch + largest-CC + connectivity test mandated. |
| R3/QO-5/6 | engineer + qa-quality | Overpass reliability / cache model | **ACCEPT (reconciled)** — in-memory session cache + **baked Shibuya fixture**; timeout+1 retry; **no file-cache subsystem** (honors qa-principle#2 minimalism). |
| #1/QT-1 | qa-quality | **CRITICAL thread leak into Pikmin (live)** | **ACCEPT** — `unregisterListenerImpl` hook + per-listener cancellation; ≤2-thread budget. |
| #2/QF-2b | qa-quality | Wakelock/Doze + screen-off credit truncation | **ACCEPT** — wakelock + keep-awake/charge-ceiling in `:app`. |
| #6/QT-3 | qa-quality | Deliver via captured `Handler` | **ACCEPT.** |
| #5 | qa-quality | Engine materializes ticks eagerly → off-Main | **ACCEPT** — playback/gen off `Dispatchers.Main`. |
| QP-1 | qa-quality | Poll ≤2 Hz not per-tick | **ACCEPT** — ≤2 Hz + cache + 100 ms timeout→0. |
| #2 (qa-function) | qa-function | **AC-23 has no detection mechanism** | **ACCEPT** — inferred presence via `PaceProvider.lastQueried`; requirements AC-23 reworded. |
| #3/#7 (qa-function) | qa-function | Tautological AC-4 test; topological AC-3 proxy | **ACCEPT** — non-tautological exclusion fixture; bearing-based AC-3 test on real Shibuya graph. |
| #8 (qa-function) | qa-function | `:stephook` needs a pure testable seam | **ACCEPT** — extract `PaceScheduler`. |
| qa-principle #1 | qa-principle | Drop GMS target + `STEP_COUNTER` on port | ~~ACCEPT — explicit negative scope.~~ **REVERSED 2026-07-13** — device bug proved the Weekly Challenge reads Fit's `STEP_COUNTER` in the GMS process; both restored (see §"Weekly-Challenge fix"). |
| qa-principle #3/#10 | qa-principle+qa-function#10 | AC-2 mis-assigned to `:core-osm` | **ACCEPT** — traceability corrected. |
| qa-principle #4/#8/#9 | qa-principle | No speed-multiplier; minSdk 29; "gps+network+fused" wording | **ACCEPT.** |
| qa-principle #5 | qa-principle | Document why `:core-model` is separate | **ACCEPT** — rationale added to module table. |
| qa-principle #12 | qa-principle | OkHttp maybe transitive via osmdroid | **NOTED** — S3 checks; else `HttpURLConnection`. |
| AC-18/19 latency, AC-1 pause-accounting, AC-2 "configurable", AC-16 detection | qa-function#8/#11/#14/#13 | AC ambiguities | **ACCEPT** — folded into `requirements.md` (pending Gate-2 confirmation). |

> **Gate 2:** human approves this v2 architecture (cross-critique applied) before implementation begins.

---

## Weekly-Challenge fix (2026-07-13) — restore GMS `STEP_COUNTER` feed

**Bug (user report):** the Weekly-Challenge *"Step Challenges"* count *"sometimes stops increasing"* for a running walk, and the landing-page daily count *"sometimes resets mid-walk; when it resets, the Step Challenges stops increasing."*

**Root cause (from `dumpsys sensorservice`):** two independent step sources.
- `STEP_DETECTOR` (type 18) is registered by Pikmin's `com.nianticproject.ichigo.fitness.FitnessManager` **inside the Pikmin process** → drives the game's live / landing-page count.
- `STEP_COUNTER` (type 19) is registered by `com.google.android.gms.fitness.sensors.local.LocalSensorAdapter` **inside the `com.google.android.gms` process** (Google Fit) → drives the **Weekly-Challenge "Step Challenges"** and the count reconciliation.

The productionized `:stephook` fed only Pikmin's `STEP_DETECTOR`. The challenge reads Google Fit's counter in a **different process**, so a Pikmin-only hook could never move it → the challenge stalled, and Pikmin's periodic reconciliation against the unmoving Fit counter pulled the landing page back down (the "reset").

**Fix (surgical):**
- Scope the module to **both** `com.nianticlabs.pikmin` and `com.google.android.gms` (`StepHook.WANTED_SENSOR` maps each process to the one sensor it registers — detector for Pikmin, counter for GMS — so we never inject a sensor a process didn't ask for).
- `STEP_COUNTER` is cumulative-since-boot: synthetic value = **captured hardware base** (read once via a self-unregistering one-shot listener, tagged so we don't feed our own reader; falls back to `0` if absent) **+ running injected total**, advanced in lockstep with the detector so the reconciliation baseline agrees.

**On-device verification (Pixel 7 Pro `cheetah`, account Palantir, Xinyi preset walk @ ~100 spm).** Step-Challenge "You" value, read directly from *Weekly Challenges → Step Challenges* (all four other participants stayed flat throughout — the entire group-total change is attributable to the injected account):

| time | "You" | Δ | group total |
|---|---|---|---|
| 11:31 baseline | **12695** | — | 46847 |
| 11:54 | **12923** | +228 | 47075 |
| 12:03 | **13740** | +817 | 47892 |

Net **+1045** picked up by the challenge, monotonic. Proofs: `proofs/stepchallenge_acceptance_function_{baseline_You12695_1131,after_You12923_1154,after_You13740_1203}.png`.

**Operational notes / caveats:**
- The on-screen challenge number is **server-synced and cached**; it does not tick live during play. It refreshes when Pikmin re-fetches challenge state from the server — reliably on a **Pikmin app restart** (used above), or after enough elapsed time. The injected steps accumulate server-side throughout and surface on refresh. (Confirmed by the user: the landing-page number and the Step-Challenges number are decoupled — the landing page is **not** a reliable proxy for the challenge.)
- Portion credited (+1045 over ~40 min vs ~4000 raw injected) is bounded by Google Fit's own server-side aggregation/validation + sync lag; the value nonetheless climbs reliably and monotonically.
- **Boot reliability:** the module now loads in `com.google.android.gms` automatically at boot (observed `StepHook: loaded in com.google.android.gms (feeds sensor type=19)` + counter `attach` at the GMS-persistent fork). No manual GMS restart was required on a clean reboot.

### QA regression pass (2026-07-13, commit `9204eb0` + follow-up)
Three adversarial QA agents audited the fix for regression to other features (location setting, road-path/clap, detector feed):
- **qa-function → PASS.** Detector feed byte-for-byte unchanged; `detach()` intact; walk-sim is an **independent Gradle root** (no cross-coupling) and its **full suite ran green fresh** (56 tests, incl. location-mapping + route/motion-engine "clap" suites). Found one real robustness gap in the *new* code (not a regression): `captureCounterBaseIfNeeded()` only caught a *thrown* `registerListener`, not its `false` **return** → `counterBase` could wedge at −1. **Fixed** in follow-up (check the boolean, clear `baseReader`, fall back to base 0).
- **qa-principle → PASS.** Scope confirmed surgical (only `spike-step-hook/**` + docs); all prior findings resolved. Fixed its readability nit (`it`-shadowing in `deliver()`).
- **qa-quality → FAIL (measured perf within budget; concerns are blast-radius + docs).** Threads bounded (1/process), CPU ≈0.60%/core combined, one-shot base-capture — all in budget. FAIL driven by: (a) the ≤100 ms poll timeout **does not exist** (deliberately removed — hung); the `PaceClient.poll()` provider query is **synchronous/unbounded** and now also runs in the **always-on `com.google.android.gms.persistent`** process; (b) cross-process poll pressure on `PaceProvider` **doubles and becomes 24/7** (GMS-persistent lives from boot vs. Pikmin's session-scoped game process). Docs (b)/(c) reconciled above.

**Accepted risk (eyes-open):** the synchronous, no-timeout `PaceProvider` poll now runs 24/7 in GMS-persistent. Accepted rather than re-adding a timeout because: (1) `PaceProvider.query()` is a **trivial in-memory read** (returns `{playing, stepsPerMin}`) that does not block; a down provider returns `null` fast, not a hang; (2) if it ever *did* hang, it blocks only **our own `step-driver` daemon thread** (isolated — it does not hold GMS binder threads or stall GMS itself); worst case is our injection silently stops until GMS restarts; (3) the planned `future.get(100 ms)` timeout was **removed precisely because it caused a worse *permanent* hang** (`PaceClient` KDoc). A proper bounded-wait is a follow-up if this ever manifests; it has not (base captured cleanly, drivers stable across the whole verification). Measured steady-state cost of the doubled poll is inside the CPU budget above.
