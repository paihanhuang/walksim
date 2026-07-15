# Tasks — walk-simulator (pikmin2)

Bite-size, requirement-linked tasks per sub-project (Gate 2 ✅). Each task's DoD = an independently testable, green deliverable. Per-sub-project plans are written JIT; S3–S5 expand after their predecessor lands.

## S2 — `:core-model` + `:core-sim` (port + `WalkPlayer` facade) · JVM, no device
Port `../pikmin/walk-sim/{core-model,core-sim}` → `pikmin2/walk-sim/`. JUnit5 (`useJUnitPlatform()`), JDK 17, AGP 8.7.3, Gradle 8.9.

- [ ] **T2.1** Scaffold `pikmin2/walk-sim/` multi-module (copy `settings.gradle.kts` incl. `:core-model`,`:core-sim`; gradle wrapper; `libs.versions.toml`; `gradle.properties`; `local.properties` → `sdk.dir=/opt/homebrew/share/android-commandlinetools`). Verify empty build configures.
- [ ] **T2.2** Port `:core-model` `Types.kt` verbatim, then **surgical edits**: `WalkProfile.meanSpeedMps` default **1.4 → 1.3**; add `SimSample.cumulativeDistanceM: Double`. Add test asserting the configured default mean speed is **1.3**. *(AC-1 default)*
- [ ] **T2.3** Port `:core-sim` (`Geo`, `PathEngine`, `GraphRandomWalker`, `WalkingMotionEngine`) + its tests verbatim; in `WalkingMotionEngine.play` **project `cumulativeDistanceM`** into the emitted `SimSample`; run the **22 ported tests green**. Re-map ported `// AC-N` comments old→new: **6→1, 7→2, 8→3, 9→5, 10→4, 11→7, 12→8, 13→9, 14→10, 27→6**. *(AC-1,2,3,5,7,8,9,10)*
- [ ] **T2.4** Add facade `WalkPlayer(graph: WalkGraph, cfg: WalkPlayerConfig).play(start: LatLng, durationS: Long): Flow<SimSample>` chaining `snapStart → GraphRandomWalker.generate(targetLen = meanSpeed×D, radiusM) → PathEngine.densify(spacingM) → WalkingMotionEngine.play(path, durationS*1000, seed)`. Unit test on a fixture graph: emits a road-snapped `Flow` of the target length. *(AC-1)*
- [ ] **T2.5** Add a **bearing-based no-U-turn test** (consecutive-segment bearing ≤ 150° at every degree>1 node) on a crafted hairpin fixture — the topological proxy alone is insufficient (real-Shibuya-graph version deferred to S3). *(AC-3)*
- **DoD:** `./gradlew test` all green (JAVA_HOME = jdk17); **surgical diff only**; proof → `proofs/walk-sim_unit_function_pass_s2-*.xml`. Gates: qa-function (tests green + no regression) + qa-principle (diff audit).

## S3 — `:core-osm` (GREENFIELD: Overpass → connected foot-walkable `WalkGraph`) · JVM
New module `:core-osm` depending on `:core-model` (NOT `:core-sim`). Minimal deps only. WALKABLE filter **at the use-site**.

- [ ] **T3.1** Scaffold `:core-osm` (kotlin-jvm; depends `:core-model`; add to `settings.gradle.kts`). Define `interface RoadSource { suspend fun graphAround(center: LatLng, radiusM: Int): WalkGraph }`.
- [ ] **T3.2** Overpass fetch: build Overpass-QL for `way["highway"]` in a bbox of side ≈ 2R around center; GET via `HttpURLConnection` (no OkHttp) with `Accept-Encoding: gzip`, connect 10 s / read 20 s timeout, **1 retry w/ backoff**; clear error on failure.
- [ ] **T3.3** Parse + WALKABLE filter *(RED first — non-tautological)*: include only foot-accessible highways; **exclude** `motorway`/`trunk`(+`_link`), `foot=no`, `access=private`, `elevator`, `corridor`. Fixture-JSON test with mixed walkable+excluded ways → assert excluded ways' nodes/edges are **absent**. *(AC-4)*
- [ ] **T3.4** Graph build + stitch *(RED first)*: key nodes by **OSM node id**; split ways at shared nodes; bidirectional `Edge(toNode, geometry, lengthM = Σ Geo.haversine)`; **keep the largest connected component**. Test: two ways sharing a node id → one connected component; an isolated way is dropped. *(AC-4, connectivity)*
- [ ] **T3.5** Baked Shibuya fixture + offline default: fetch the Shibuya bbox (center 35.6595,139.7006, R=800) **once live**, save raw JSON as `core-osm/src/test/resources/shibuya.json`; expose it as the offline default graph. Integration test (offline, from fixture): parse → **one** connected component, non-empty; `WalkPlayer(graph,cfg).play(Shibuya,D)` yields a target-length route; **real-graph bearing check** (no >150° turn at degree>1) — the rigorous AC-3 check on real data.
- [ ] **T3.6** In-memory **session** cache only (bbox→`WalkGraph`); no file/DB (no cut Area Manager).
- **DoD:** `./gradlew test` green; surgical; proofs `walk-sim_unit_function_pass_s3-*.xml` + offline-Shibuya integration proof. Gates: qa-function + qa-principle.

## S4 — `:app` (osmdroid UI + wakelocked FGS + mock-all-surfaces location + `PaceProvider`) · Android, ON-DEVICE
New Android app module `:app` (`namespace com.pikmin.walksim`, `minSdk 29`, `compileSdk 34`). Reuse `../pikmin/spike-s0a` `MockGnssInjector` + `WalkInjectionService` patterns (proven on this device class).

- [ ] **T4.1** Scaffold `:app` (android application; deps `:core-model`,`:core-sim`,`:core-osm`, osmdroid, coroutines, play-services-location). Manifest: `INTERNET`, `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE`(+`location`), `WAKE_LOCK`, `POST_NOTIFICATIONS`. Set osmdroid `userAgentValue = applicationId` before first `MapView`.
- [ ] **T4.2** `LocationInjector` (port `MockGnssInjector`): mock **gps+network+fused consistently** — `addTestProvider(gps,network)`+`setTestProviderLocation` AND `FusedLocationProviderClient.setMockMode(true)`+`setMockLocation`; map `SimSample→Location` (`time`, `elapsedRealtimeNanos`=real uptime, accuracy, speed, bearing +accuracies) *(AC-13)*; `removeTestProvider`/`setMockMode(false)` on stop *(AC-15)*; reactive `SecurityException`→not-mock-app *(AC-16)*. Unit-test the mapping + detection.
- [ ] **T4.3** `WalkService` (FGS `location` type): collects `WalkPlayer.play(start,D)` **off `Dispatchers.Main`**; `PARTIAL_WAKE_LOCK` + keep-awake; ongoing notification; start/pause/resume/stop *(AC-20)*; on D-elapsed/stop → `injector.restore()`. Unit-test the state machine.
- [ ] **T4.4** `PaceProvider` (exported, permission-free, **query-only** `content://com.pikmin.walksim.pace/current` → `{playing:INT, stepsPerMin:REAL}`; derive `playing=speed>0`, `stepsPerMin=speed/stride*60`; record `lastQueried`). Instrumented-test the cursor.
- [ ] **T4.5** UI (osmdroid): pin picker default **Shibuya (35.6595,139.7006)** + duration/pace fields + start/pause/resume/stop + live HUD (speed/distance/steps/elapsed/remaining/progress) *(AC-21,22)*; not-mock-app banner ≤2 s *(AC-16)*; setup-error naming the missing prerequisite *(AC-23, mock-app half)*.
- [ ] **T4.6** Wire: start → `OverpassRoadSource.graphAround(start,800)` (or baked-Shibuya offline default) → `WalkPlayer` → `WalkService` injects; HUD from `SimSample`.
- **DoD (engineer):** `./gradlew :app:assembleDebug` green; unit tests for T4.2/T4.3/T4.4 green.
- **DoD (orchestrator, ON-DEVICE — mandatory):** install; set as mock app; run a Shibuya walk; verify `dumpsys location` gps/network/fused agree+mock *(AC-12)*; **Pikmin credits distance** ("Flowers planted"/"Next coin" screencap, AC-11); FGS survives backgrounded+screen-off *(AC-14)*; restore on stop → `isMock=false` *(AC-15)*. Proofs → `proofs/`.

## S5 — productionize the step injector + wire the pace channel · Xposed module + ON-DEVICE
Evolve `pikmin2/spike-step-hook/` (the proven S1b base) into the production step module: read `:app`'s `PaceProvider`, feed `STEP_DETECTOR` to Pikmin's `FitnessManager` at the live pace.

- [ ] **T5.1** Extract a pure, clock-injectable `PaceScheduler(clock: () -> Long)` with `onTick(playing: Boolean, stepsPerMin: Float): Int` (# `STEP_DETECTOR` pulses this tick). JVM unit-test with a fake clock: `playing=false`→0 (AC-18); pulses scale with `stepsPerMin` (AC-19).
- [ ] **T5.2** `PaceClient`: query `content://com.pikmin.walksim.pace/current` via `AndroidAppHelper.currentApplication()` (**lazy, at tick time**), poll **≤2 Hz**, **≤100 ms timeout**; null/timeout/`playing=0` → `(false, 0)` (never throw into Pikmin's sensor path).
- [ ] **T5.3** Rework `StepInjector`: **drop the `STEP_COUNTER` branch + the `com.google.android.gms` target** (Pikmin + `STEP_DETECTOR` only); deliver via the **captured `Handler`** (not a raw thread); pace from `PaceScheduler(PaceClient)`; **park when `playing=false`** (no busy-loop; the ≤2 Hz presence poll is the only idle wakeup).
- [ ] **T5.4** Thread-leak fix (qa-quality QT-1): hook `SystemSensorManager.unregisterListenerImpl` + per-listener cancellation (`AtomicBoolean`/`WeakReference`) → **≤2 threads** regardless of sensor re-registration.
- [ ] **T5.5** Build APK; `adb install -r`; LSPosed already enabled+scoped to Pikmin → `am force-stop` + relaunch to reload.
- **DoD (engineer):** `assembleDebug` green; `PaceScheduler` unit tests green.
- **DoD (orchestrator, ON-DEVICE):** run a walk in `:app`; **Pikmin's step count rises ≈ distance/stride (±10%, AC-17)** (screenrecord/screencap); steps cease ≤2 s after stop (AC-18); `:stephook` thread count ≤2 across a background/foreground cycle.
