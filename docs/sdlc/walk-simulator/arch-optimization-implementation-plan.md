# walk-sim Architecture Optimization (Plan A) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the finished, on-device-proven walk-sim tool (versatility · maintainability · scalability) through six small, independently-shippable, 3-QA-gated stages — without changing any observed behavior or weakening any acceptance criterion.

**Architecture:** Characterization-first. Stage 0 freezes a golden/perf baseline; Stages 1–4 make one surgical change each (Geo hygiene → IPC contract → RoadSource injection → orchestrator extraction), every one proven behavior-equivalent against Stage 0; Stage 5 is measure-gated and may never run. Two Gradle roots stay separate; no new modules.

**Tech Stack:** Kotlin, Gradle (`./gradlew`), JUnit/kotlin.test, coroutines/Flow, Android SDK (`:app`), LSPosed/Xposed (`spike-step-hook`), Overpass/osmdroid.

## Global Constraints

Every task's requirements implicitly include this section. Values copied verbatim from `docs/sdlc/walk-simulator/arch-optimization-plan.md`.

- **3-QA gate, hard rule:** no stage N+1 work begins until stage N is approved by **all three** QA roles (qa-function, qa-quality, qa-principle). A single FAIL blocks the stage; route the reason to the engineer, fix, re-verify, re-run all three.
- **Subagent model:** engineer + all QA subagents run on **Opus 4.8** (`model: "opus"`). Orchestration stays in the main session.
- **Surgical + minimal:** diff confined to the stage's scope; no drive-by cleanup, no abstraction without a present consumer, no speculative/dead code.
- **Behavior frozen** unless a stage explicitly declares a change — then new goldens + a human note. Never weaken an AC or an on-device proof.
- **Dual-root kept:** no new Gradle modules; no monorepo absorb of `spike-step-hook`.
- **Proof artifacts:** every stage writes proofs under `docs/sdlc/walk-simulator/proofs/` using the existing naming convention (`walk-sim_<scope>_<role>_<pass|fail>_<slug>.<ext>`).
- **Environment:** macOS/zsh, absolute paths. Two Gradle roots: `/Users/davidhuang/Projects/pikmin-remote-control/walk-sim` and `/Users/davidhuang/Projects/pikmin-remote-control/spike-step-hook`. **This tree is not a git repository** — the `git commit` steps below are optional and presuppose `git init`; the mandatory gate record is the proof artifact + the 3-QA verdict table, not the commit.
- **Test conventions:** match each module's existing test framework and style (see neighboring `*Test.kt`). Run commands: JVM modules `./gradlew :<mod>:test`; the Android `:app` module `./gradlew :app:testDebugUnitTest`; full root gate `./gradlew test`.

---

## File Structure

**Stage 0 — new test/proof only (zero production change):**
- Create `walk-sim/core-osm/src/test/kotlin/com/pikmin/osm/RouteGoldenTest.kt` — route fingerprints (route math lives behind core-osm's graph fixtures).
- Create `walk-sim/core-sim/src/test/kotlin/com/pikmin/sim/MotionDigestTest.kt` — fixed-seed motion-sample digests.
- Create `walk-sim/core-sim/src/test/kotlin/com/pikmin/sim/PerfBaselineTest.kt` — env-gated gen/heap harness.
- Create golden resources under `walk-sim/core-osm/src/test/resources/golden/` and `walk-sim/core-sim/src/test/resources/golden/`.
- Modify `docs/sdlc/walk-simulator/traceability.md` (or create the Pace contract table there).

**Stage 1 — Geo hoist:**
- Create `walk-sim/core-model/src/main/kotlin/com/pikmin/model/Geo.kt` (moved).
- Delete `walk-sim/core-sim/src/main/kotlin/com/pikmin/sim/Geo.kt`; move `GeoTest.kt` → `core-model` test.
- Modify `walk-sim/core-osm/src/main/kotlin/com/pikmin/osm/OverpassGraph.kt` (drop private haversine, use `Geo`).

**Stage 2 — IPC contract:**
- Create `docs/sdlc/walk-simulator/pace-contract.properties` (canonical source-of-truth).
- Modify `walk-sim/app/src/main/java/com/pikmin/walksim/PaceProvider.kt` (+`schemaVersion`).
- Create `walk-sim/app/src/test/java/com/pikmin/walksim/PaceContractTest.kt`.
- Create `spike-step-hook/app/src/test/java/com/pikmin/stephook/PaceContractTest.kt`.
- Modify `spike-step-hook/app/src/main/java/com/pikmin/stephook/PaceClient.kt` (tolerate version column).

**Stage 3 — RoadSource injection:**
- Create `walk-sim/core-osm/src/main/kotlin/com/pikmin/osm/FixtureRoadSource.kt`.
- Create `walk-sim/core-osm/src/main/kotlin/com/pikmin/osm/CompositeRoadSource.kt`.
- Create tests for both in `walk-sim/core-osm/src/test/kotlin/com/pikmin/osm/`.
- Modify `walk-sim/app/src/main/java/com/pikmin/walksim/WalkService.kt` (inject `RoadSource`).

**Stage 4 — Orchestration extraction:**
- Create `walk-sim/app/src/main/java/com/pikmin/walksim/session/LocationSink.kt`.
- Create `walk-sim/app/src/main/java/com/pikmin/walksim/session/WalkSessionController.kt`.
- Create `walk-sim/app/src/test/java/com/pikmin/walksim/session/WalkSessionControllerTest.kt` (+ `FakeLocationSink`).
- Modify `walk-sim/app/src/main/java/com/pikmin/walksim/WalkService.kt` (thin adapter) and `LocationInjector.kt` (implement `LocationSink`).

**Stage 5 — Scalability (conditional):** files determined by the measured hot phase; none created unless the Stage 0 baseline breaches budget.

---

## Stage 0 — Baseline freeze

*Zero production change. Establishes the safety net every later stage is verified against.*

### Task 0.1: Record the green baseline

**Files:** Test: none new. Proof: `docs/sdlc/walk-simulator/proofs/walk-sim_baseline_function_pass_s0-suite.txt`

- [ ] **Step 1: Run the full walk-sim suite**

Run: `cd /Users/davidhuang/Projects/pikmin-remote-control/walk-sim && ./gradlew test`
Expected: BUILD SUCCESSFUL; note the total test count (STATUS.md records 77).

- [ ] **Step 2: Run the full spike-step-hook suite**

Run: `cd /Users/davidhuang/Projects/pikmin-remote-control/spike-step-hook && ./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Record the proof**

Save both console outputs + the resulting `**/build/test-results/test/*.xml` counts into the proof file above (suite inventory: module → test count, and the exact toolchain versions from `gradle/libs.versions.toml`).

### Task 0.2: Route fingerprint goldens

**Files:**
- Create: `walk-sim/core-osm/src/test/kotlin/com/pikmin/osm/RouteGoldenTest.kt`
- Create (on first run): `walk-sim/core-osm/src/test/resources/golden/*.txt`

**Interfaces:**
- Consumes: `OverpassGraph.fromOverpassJson(String): WalkGraph`; `com.pikmin.sim.sweepRoute(graph, start, targetLengthM, laneSpacingM, closeLoop): Route`.
- Produces: golden files later stages assert against unchanged.

- [ ] **Step 1: Write the fingerprint test (record-then-assert bootstrap)**

```kotlin
package com.pikmin.osm

import com.pikmin.model.Edge
import com.pikmin.model.LatLng
import com.pikmin.model.Route
import com.pikmin.model.WalkGraph
import com.pikmin.sim.sweepRoute
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class RouteGoldenTest {

    private val shibuyaStart = LatLng(35.6595, 139.7006)

    private fun shibuyaGraph(): WalkGraph {
        val json = javaClass.getResource("/shibuya.json")!!.readText()
        return OverpassGraph.fromOverpassJson(json)
    }

    /** Small deterministic square lattice so a golden exists independent of the baked asset. */
    private fun gridGraph(n: Int = 12, stepDeg: Double = 0.0009): WalkGraph {
        val nodes = HashMap<Long, LatLng>()
        val adj = HashMap<Long, MutableList<Edge>>()
        fun id(r: Int, c: Int) = (r * 1000 + c).toLong()
        for (r in 0 until n) for (c in 0 until n) nodes[id(r, c)] = LatLng(35.0 + r * stepDeg, 139.0 + c * stepDeg)
        fun link(a: Long, b: Long) {
            val ga = listOf(nodes.getValue(a), nodes.getValue(b))
            val len = com.pikmin.sim.Geo.haversineMeters(ga[0], ga[1])
            adj.getOrPut(a) { ArrayList() }.add(Edge(b, ga, len))
            adj.getOrPut(b) { ArrayList() }.add(Edge(a, ga.reversed(), len))
        }
        for (r in 0 until n) for (c in 0 until n) {
            if (c + 1 < n) link(id(r, c), id(r, c + 1))
            if (r + 1 < n) link(id(r, c), id(r + 1, c))
        }
        return WalkGraph(nodes, adj)
    }

    private fun fingerprint(route: Route): String {
        val sb = StringBuilder().append(route.points.size).append('|')
            .append("%.3f".format(route.totalLengthM)).append('|')
        for (p in route.points) sb.append("%.6f,%.6f;".format(p.lat, p.lng))
        return MessageDigest.getInstance("SHA-256").digest(sb.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun check(name: String, route: Route) {
        val golden = File("src/test/resources/golden/$name.txt")
        val actual = "${route.points.size} ${"%.3f".format(route.totalLengthM)} ${fingerprint(route)}"
        if (!golden.exists()) {
            golden.parentFile.mkdirs(); golden.writeText(actual)
            fail("golden '$name' recorded (was absent) — re-run to verify")
        }
        assertEquals(golden.readText().trim(), actual, "route golden '$name' drifted")
    }

    @Test fun shibuyaOpen() =
        check("shibuya-open-500-10k", sweepRoute(shibuyaGraph(), shibuyaStart, 10_000.0, 500.0, closeLoop = false))
    @Test fun shibuyaClosed() =
        check("shibuya-closed-500-10k", sweepRoute(shibuyaGraph(), shibuyaStart, 10_000.0, 500.0, closeLoop = true))
    @Test fun gridShortfall() =
        check("grid-open-500-50k", sweepRoute(gridGraph(), LatLng(35.0049, 139.0049), 50_000.0, 500.0, closeLoop = false))
}
```

- [ ] **Step 2: Run once to record goldens**

Run: `cd /Users/davidhuang/Projects/pikmin-remote-control/walk-sim && ./gradlew :core-osm:test --tests com.pikmin.osm.RouteGoldenTest`
Expected: FAIL with "golden … recorded — re-run to verify" (three golden files now written).

- [ ] **Step 3: Run again to verify goldens are stable**

Run: same command.
Expected: PASS (3/3). If any fail on the second run, `sweepRoute` is non-deterministic — stop and raise a defect (violates AC-5/AC-24d) before proceeding.

- [ ] **Step 4: Record proof** — copy the JUnit XML to `docs/sdlc/walk-simulator/proofs/walk-sim_baseline_function_pass_s0-route-goldens.xml`.

### Task 0.3: Motion-sample digest golden

**Files:** Create `walk-sim/core-sim/src/test/kotlin/com/pikmin/sim/MotionDigestTest.kt`; golden under `walk-sim/core-sim/src/test/resources/golden/`.

**Interfaces:** Consumes `WalkingMotionEngine.frames(path, profile, durationMs, seed, runUntilPathEnd): List<MotionFrame>` (internal — same module) or the public `WalkPlayer.play`. Use `frames` for a pure, delay-free digest.

- [ ] **Step 1: Write the digest test**

```kotlin
package com.pikmin.sim

import com.pikmin.model.LatLng
import com.pikmin.model.WalkProfile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class MotionDigestTest {
    private fun straightPath(meters: Int): List<LatLng> =
        (0..meters).map { Geo.destinationPoint(LatLng(35.66, 139.70), 90.0, it.toDouble()) }

    private fun digest(frames: List<MotionFrame>): String {
        val first = frames.first(); val last = frames.last()
        val pauses = frames.count { it.paused }
        return listOf(
            frames.size,
            "%.6f,%.6f".format(first.emittedPos.lat, first.emittedPos.lng),
            "%.6f,%.6f".format(last.emittedPos.lat, last.emittedPos.lng),
            "%.3f".format(last.cumDistM),
            last.stepCount,
            pauses,
        ).joinToString("|")
    }

    private fun check(name: String, frames: List<MotionFrame>) {
        val golden = File("src/test/resources/golden/$name.txt")
        val actual = digest(frames)
        if (!golden.exists()) { golden.parentFile.mkdirs(); golden.writeText(actual); fail("golden '$name' recorded — re-run") }
        assertEquals(golden.readText().trim(), actual, "motion digest '$name' drifted")
    }

    @Test fun open60s() =
        check("motion-open-60s-seed42", WalkingMotionEngine.frames(
            PathEngine.densify(com.pikmin.model.Route(straightPath(120), 120.0), 1.0),
            WalkProfile(), durationMs = 60_000, seed = 42L))
}
```

- [ ] **Step 2–3: Record then verify** — `./gradlew :core-sim:test --tests com.pikmin.sim.MotionDigestTest` (first run records + fails; second run PASS). If the second run drifts, the motion engine is non-deterministic — raise a defect (AC-5).

- [ ] **Step 4: Record proof** → `walk-sim_baseline_function_pass_s0-motion-digest.xml`.

### Task 0.4: Gen/heap perf baseline (env-gated)

**Files:** Create `walk-sim/core-sim/src/test/kotlin/com/pikmin/sim/PerfBaselineTest.kt`; proof `docs/sdlc/walk-simulator/proofs/walk-sim_baseline_quality_pass_s0-gen-heap.txt`.

- [ ] **Step 1: Write the harness (skipped unless `WALKSIM_PERF=1`)**

```kotlin
package com.pikmin.sim

import com.pikmin.model.LatLng
import com.pikmin.model.Route
import com.pikmin.model.WalkProfile
import kotlin.test.Test
import kotlin.test.assertTrue

class PerfBaselineTest {
    @Test fun genAndHeapBaseline() {
        if (System.getenv("WALKSIM_PERF") != "1") return // env-gated; no CI cost
        val graph = SyntheticGraphs.denseGrid(nodes = 12_000) // Okubo-scale; add builder alongside Fixtures.kt
        val start = graph.nodes.values.first()
        val rt = Runtime.getRuntime()
        System.gc(); val h0 = rt.totalMemory() - rt.freeMemory()
        val t0 = System.nanoTime()
        val route = sweepRoute(graph, start, 10_000.0, 500.0, closeLoop = false)
        val t1 = System.nanoTime()
        val path = PathEngine.densify(route, 1.0)
        val t2 = System.nanoTime()
        val frames = WalkingMotionEngine.frames(path, WalkProfile(), 600_000, 7L)
        val t3 = System.nanoTime()
        val h1 = rt.totalMemory() - rt.freeMemory()
        println("QG-GEN route=${(t1-t0)/1e6}ms densify=${(t2-t1)/1e6}ms frames=${(t3-t2)/1e6}ms points=${path.size} frames=${frames.size}")
        println("QG-HEAP deltaMB=${(h1-h0)/1e6}")
        assertTrue(route.points.isNotEmpty())
    }
}
```

- [ ] **Step 2: Run and capture numbers**

Run: `cd /Users/davidhuang/Projects/pikmin-remote-control/walk-sim && WALKSIM_PERF=1 ./gradlew :core-sim:test --tests com.pikmin.sim.PerfBaselineTest -i`
Expected: PASS; capture the `QG-GEN` / `QG-HEAP` lines.

- [ ] **Step 3: Record the budget** — write the measured ms/heap into the proof file and set the **Stage 5 trigger budget** = these numbers × 1.5 (documented explicitly; Stage 5 runs only if a future measurement breaches it).

### Task 0.5: Pace IPC contract table

**Files:** Modify/create `docs/sdlc/walk-simulator/traceability.md` (or a `## Pace IPC contract` section).

- [ ] **Step 1: Document the current contract verbatim** — authority `com.pikmin.walksim.pace`, path `/current`, columns `playing:INT(0/1)`, `stepsPerMin:REAL`, single-row cursor, no schema version today, poll ≤2 Hz, query-only/exported/permission-free. Cross-reference `PaceProvider.kt` and `PaceClient.kt`.

### Task 0.6: Stage 0 — 3-QA GATE

- [ ] **qa-function:** goldens actually pin behavior (non-tautological: mutate a spacing constant locally → a golden must fail, then revert); all existing ACs still pass (`./gradlew test` both roots green).
- [ ] **qa-quality:** perf harness produces meaningful, reproducible numbers; env-gate adds no default-run cost; no new production dependency.
- [ ] **qa-principle:** **zero production code changed** in Stage 0 (diff is test/doc/proof only).
- [ ] Record verdicts in a Stage-0 table in the design doc's cross-critique section. **All three PASS → Stage 1 may start.** (Optional commit: `git add -A && git commit -m "test(walk-sim): stage 0 baseline goldens + perf budget"`.)

---

## Stage 1 — Geo hygiene

*Trivial. Single behavioral invariant: Stage 0 goldens stay byte-identical.*

### Task 1.1: Hoist `Geo` to `:core-model` and delete the duplicate

**Files:**
- Create: `walk-sim/core-model/src/main/kotlin/com/pikmin/model/Geo.kt`
- Delete: `walk-sim/core-sim/src/main/kotlin/com/pikmin/sim/Geo.kt`
- Modify: `walk-sim/core-osm/src/main/kotlin/com/pikmin/osm/OverpassGraph.kt:137-155` (remove private haversine + `EARTH_RADIUS_M`, use `Geo`)
- Move: `walk-sim/core-sim/src/test/kotlin/com/pikmin/sim/GeoTest.kt` → `walk-sim/core-model/src/test/kotlin/com/pikmin/model/GeoTest.kt`

**Interfaces:**
- Produces: `com.pikmin.model.Geo` with the **identical** API `haversineMeters(a,b)`, `bearingDegrees(a,b)`, `destinationPoint(from,bearingDeg,distM)` and constant `R = 6_371_008.8`.
- Consumes: nothing new (`:core-sim`, `:core-osm`, `:app` already depend on `:core-model`).

- [ ] **Step 1: Move the file, change only the package**

Copy `core-sim/.../sim/Geo.kt` to `core-model/.../model/Geo.kt`; change `package com.pikmin.sim` → `package com.pikmin.model`. Body unchanged (same `R`, same formulas). Delete the `:core-sim` original.

- [ ] **Step 2: Repoint `:core-sim` references**

`:core-sim` files call `Geo.…` unqualified within package `com.pikmin.sim`. After the move, add `import com.pikmin.model.Geo` to each `:core-sim` file that uses it (`SweepRoute.kt`, `GraphRandomWalker.kt`, `PathEngine.kt`, `WalkingMotionEngine.kt`, `WalkPlayer.kt`). Update the Stage-0 test grid builder's `com.pikmin.sim.Geo` → `com.pikmin.model.Geo`.

- [ ] **Step 3: Delete the `:core-osm` private copy**

In `OverpassGraph.kt`, delete `EARTH_RADIUS_M` and the private `haversineMeters` (lines ~137-155) and its section comment; add `import com.pikmin.model.Geo`; change `polylineLength` to call `Geo.haversineMeters`.

- [ ] **Step 4: Move `GeoTest.kt`** — change its package to `com.pikmin.model`; imports follow.

- [ ] **Step 5: Compile + full suite**

Run: `cd /Users/davidhuang/Projects/pikmin-remote-control/walk-sim && ./gradlew test`
Expected: BUILD SUCCESSFUL, same test count as Stage 0.

- [ ] **Step 6: The binding equivalence check — goldens byte-identical**

Run: `./gradlew :core-osm:test --tests com.pikmin.osm.RouteGoldenTest :core-sim:test --tests com.pikmin.sim.MotionDigestTest`
Expected: PASS with **no golden rewritten**. (A rewrite means the `:core-osm` copy differed numerically from `:core-sim` `Geo` — investigate before proceeding; do not update goldens.)

- [ ] **Step 7: Record proof** → `walk-sim_s1_function_pass_geo-hoist-goldens-identical.xml`.

### Task 1.2: Stage 1 — 3-QA GATE

- [ ] **qa-function:** goldens unchanged; full suite green both roots.
- [ ] **qa-quality:** no new dependency; `:core-osm` still depends only on `:core-model` (not `:core-sim`); review-clean.
- [ ] **qa-principle:** diff is exactly {move Geo, delete duplicate, repoint imports} — no drive-by edits.
- [ ] All three PASS → Stage 2 may start.

---

## Stage 2 — IPC contract harden

*Low–med. Touches the 24/7 GMS pace seam. Full 3-QA + dual-process on-device smoke.*

### Task 2.1: Canonical contract source-of-truth + walk-sim contract test

**Files:**
- Create: `docs/sdlc/walk-simulator/pace-contract.properties`
- Create: `walk-sim/app/src/test/java/com/pikmin/walksim/PaceContractTest.kt`

**Interfaces:**
- Produces: canonical keys `authority`, `path`, `col.playing`, `col.stepsPerMin`, `col.schemaVersion`, `schemaVersion`, `pollIntervalMsMax` — the single reference both roots' contract tests assert against.

- [ ] **Step 1: Write the canonical properties file**

```properties
# Canonical Pace IPC contract — the single source of truth. Both Gradle roots
# ship a copy of these values in code; each root's PaceContractTest asserts its
# code matches THIS file. A rename on either side => that root's test goes RED.
authority=com.pikmin.walksim.pace
path=current
col.playing=playing
col.stepsPerMin=stepsPerMin
col.schemaVersion=schemaVersion
schemaVersion=1
pollIntervalMsMax=500
```

- [ ] **Step 2: Write the walk-sim contract test (RED — `COL_SCHEMA_VERSION`/`SCHEMA_VERSION` don't exist yet)**

```kotlin
package com.pikmin.walksim

import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class PaceContractTest {
    private val canonical = Properties().apply {
        File("/Users/davidhuang/Projects/pikmin-remote-control/docs/sdlc/walk-simulator/pace-contract.properties")
            .inputStream().use { load(it) }
    }
    @Test fun providerConstantsMatchCanonical() {
        assertEquals(canonical.getProperty("authority"), PaceProvider.AUTHORITY)
        assertEquals(canonical.getProperty("path"), "current")
        assertEquals("content://${canonical.getProperty("authority")}/current", PaceProvider.CURRENT_URI.toString())
        assertEquals(canonical.getProperty("col.playing"), PaceProvider.COL_PLAYING)
        assertEquals(canonical.getProperty("col.stepsPerMin"), PaceProvider.COL_STEPS_PER_MIN)
        assertEquals(canonical.getProperty("col.schemaVersion"), PaceProvider.COL_SCHEMA_VERSION)
        assertEquals(canonical.getProperty("schemaVersion").toInt(), PaceProvider.SCHEMA_VERSION)
    }
}
```

- [ ] **Step 3: Run — verify it fails to compile/pass**

Run: `cd /Users/davidhuang/Projects/pikmin-remote-control/walk-sim && ./gradlew :app:testDebugUnitTest --tests com.pikmin.walksim.PaceContractTest`
Expected: FAIL (unresolved `COL_SCHEMA_VERSION`/`SCHEMA_VERSION`).

### Task 2.2: Add the additive `schemaVersion` column to `PaceProvider`

**Files:** Modify `walk-sim/app/src/main/java/com/pikmin/walksim/PaceProvider.kt`

**Interfaces:**
- Produces: `PaceProvider.COL_SCHEMA_VERSION = "schemaVersion"`, `PaceProvider.SCHEMA_VERSION = 1`; cursor now `{playing, stepsPerMin, schemaVersion}`.

- [ ] **Step 1: Add constants + emit the column** (edit `PaceProvider.kt:30-33` and companion `:40-45`)

```kotlin
// query(): add schemaVersion as a trailing, additive column
return MatrixCursor(arrayOf(COL_PLAYING, COL_STEPS_PER_MIN, COL_SCHEMA_VERSION)).apply {
    addRow(arrayOf<Any>(if (pace.playing) 1 else 0, pace.stepsPerMin, SCHEMA_VERSION))
}
```
```kotlin
// companion object: add
const val COL_SCHEMA_VERSION = "schemaVersion"
const val SCHEMA_VERSION = 1
```

- [ ] **Step 2: Run the contract test → PASS**

Run: `./gradlew :app:testDebugUnitTest --tests com.pikmin.walksim.PaceContractTest`
Expected: PASS.

- [ ] **Step 3: Record proof** → `walk-sim_s2_function_pass_provider-contract.xml`.

### Task 2.3: Spike-side contract test + version-tolerant client

**Files:**
- Create: `spike-step-hook/app/src/test/java/com/pikmin/stephook/PaceContractTest.kt`
- Modify: `spike-step-hook/app/src/main/java/com/pikmin/stephook/PaceClient.kt:64-70`

**Interfaces:**
- Consumes: canonical properties file (same absolute path).
- Produces: `PaceClient` reads by **column name** (already does) and tolerates the extra column; add a `SCHEMA_VERSION_KNOWN = 1` constant + name-based read of `schemaVersion` when present.

- [ ] **Step 1: Write the spike contract test (RED)**

```kotlin
package com.pikmin.stephook

import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class PaceContractTest {
    private val canonical = Properties().apply {
        File("/Users/davidhuang/Projects/pikmin-remote-control/docs/sdlc/walk-simulator/pace-contract.properties")
            .inputStream().use { load(it) }
    }
    @Test fun clientConstantsMatchCanonical() {
        assertEquals("content://${canonical.getProperty("authority")}/${canonical.getProperty("path")}", PaceClient.CURRENT_URI_STR)
        assertEquals(canonical.getProperty("col.playing"), PaceClient.COL_PLAYING)
        assertEquals(canonical.getProperty("col.stepsPerMin"), PaceClient.COL_STEPS_PER_MIN)
        assertEquals(canonical.getProperty("pollIntervalMsMax").toLong(), PaceClient.POLL_INTERVAL_MS)
    }
}
```

- [ ] **Step 2: Expose the constants the test reads** — in `PaceClient.kt` promote the private companion vals to `internal`/`const` with stable names (`CURRENT_URI_STR`, `COL_PLAYING`, `COL_STEPS_PER_MIN`, `POLL_INTERVAL_MS`). No behavior change.

- [ ] **Step 3: Add a version-tolerance parse test (old cursor without the column still works)**

```kotlin
@Test fun parsesVersionlessCursor() {
    val c = android.database.MatrixCursor(arrayOf("playing", "stepsPerMin"))
    c.addRow(arrayOf<Any>(1, 90f))
    // parseRow is a small extracted pure helper over a Cursor row (see Step 4)
    assertEquals(Pace(true, 90f), PaceClient.parseRow(c.apply { moveToFirst() }))
}
```

- [ ] **Step 4: Extract `parseRow(Cursor): Pace`** from `queryProvider()` (name-based reads it already does at `PaceClient.kt:58-60`), so it's unit-testable and provably ignores unknown columns. `queryProvider` calls it.

- [ ] **Step 5: Run both spike tests → PASS**

Run: `cd /Users/davidhuang/Projects/pikmin-remote-control/spike-step-hook && ./gradlew :app:testDebugUnitTest --tests com.pikmin.stephook.PaceContractTest`
Expected: PASS.

- [ ] **Step 6: Record proof** → `walk-sim_s2_function_pass_client-contract.xml`.

### Task 2.4: Stage 2 — 3-QA GATE (+ dual-process on-device smoke)

- [ ] **qa-function:** rename `playing`→`playingX` in `PaceProvider` locally → walk-sim contract test RED; revert. Full suites green. Old versionless cursor still parses.
- [ ] **qa-quality:** poll cadence still ≤2 Hz (`POLL_INTERVAL_MS==500`), query still trivial + synchronous, no `future.get`/timeout reintroduced, one driver thread/process; no new dependency.
- [ ] **qa-principle:** additive-only (no column removed/reordered), **no new Gradle module** (canonical is a docs file both tests read), no drift into unrelated code.
- [ ] **On-device smoke:** install both APKs; short walk; confirm Pikmin step count climbs (detector) and Google Fit weekly challenge advances (counter). Proof `walk-sim_s2_function_pass_s2-dual-process-steps.png/.md`.
- [ ] All three PASS → Stage 3 may start.

---

## Stage 3 — RoadSource inject

*Medium. 3-QA + on-device (live + forced fallback).*

### Task 3.1: `FixtureRoadSource`

**Files:**
- Create: `walk-sim/core-osm/src/main/kotlin/com/pikmin/osm/FixtureRoadSource.kt`
- Create: `walk-sim/core-osm/src/test/kotlin/com/pikmin/osm/FixtureRoadSourceTest.kt`

**Interfaces:**
- Produces: `class FixtureRoadSource(private val load: (LatLng, Int) -> String) : RoadSource` — returns `OverpassGraph.fromOverpassJson(load(center, radiusM))`, ignoring center/radius (a fixture is a fixed baked graph). A convenience secondary ctor takes a `() -> String`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.pikmin.osm

import com.pikmin.model.LatLng
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class FixtureRoadSourceTest {
    @Test fun loadsBakedShibuya() = runBlocking {
        val json = javaClass.getResource("/shibuya.json")!!.readText()
        val src = FixtureRoadSource { json }
        val g = src.graphAround(LatLng(35.6595, 139.7006), 800)
        assertTrue(g.nodes.size > 100 && g.adjacency.isNotEmpty())
    }
}
```

- [ ] **Step 2: Run → FAIL** (`FixtureRoadSource` undefined). `./gradlew :core-osm:test --tests com.pikmin.osm.FixtureRoadSourceTest`

- [ ] **Step 3: Implement**

```kotlin
package com.pikmin.osm

import com.pikmin.model.LatLng
import com.pikmin.model.WalkGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A [RoadSource] backed by a baked JSON graph (offline / fallback / tests). The fixed graph ignores center/radius. */
class FixtureRoadSource(private val load: (LatLng, Int) -> String) : RoadSource {
    constructor(load: () -> String) : this({ _, _ -> load() })
    private var cached: WalkGraph? = null
    override suspend fun graphAround(center: LatLng, radiusM: Int): WalkGraph =
        cached ?: withContext(Dispatchers.IO) { OverpassGraph.fromOverpassJson(load(center, radiusM)) }.also { cached = it }
}
```

- [ ] **Step 4: Run → PASS.** Record `walk-sim_s3_function_pass_fixture-source.xml`.

### Task 3.2: `CompositeRoadSource(primary, fallback)`

**Files:**
- Create: `walk-sim/core-osm/src/main/kotlin/com/pikmin/osm/CompositeRoadSource.kt`
- Create: `walk-sim/core-osm/src/test/kotlin/com/pikmin/osm/CompositeRoadSourceTest.kt`

**Interfaces:**
- Produces: `class CompositeRoadSource(primary, fallback, onFallback: (Throwable) -> Unit = {}) : RoadSource`. Primary success → return it (no fallback call); primary throws → `onFallback(e)` then `fallback.graphAround(...)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.pikmin.osm

import com.pikmin.model.LatLng
import com.pikmin.model.WalkGraph
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompositeRoadSourceTest {
    private val g1 = WalkGraph(mapOf(1L to LatLng(0.0, 0.0)), emptyMap())
    private val g2 = WalkGraph(mapOf(2L to LatLng(1.0, 1.0)), emptyMap())
    private fun src(g: WalkGraph) = object : RoadSource { override suspend fun graphAround(c: LatLng, r: Int) = g }
    private fun failing() = object : RoadSource { override suspend fun graphAround(c: LatLng, r: Int): WalkGraph = throw java.io.IOException("net") }

    @Test fun primaryOkSkipsFallback() = runBlocking {
        var fellBack = false
        val out = CompositeRoadSource(src(g1), src(g2), onFallback = { fellBack = true }).graphAround(LatLng(0.0,0.0), 800)
        assertEquals(g1, out); assertFalse(fellBack)
    }
    @Test fun primaryFailUsesFallbackAndSignals() = runBlocking {
        var signalled = false
        val out = CompositeRoadSource(failing(), src(g2), onFallback = { signalled = true }).graphAround(LatLng(0.0,0.0), 800)
        assertEquals(g2, out); assertTrue(signalled)
    }
}
```

- [ ] **Step 2: Run → FAIL.** **Step 3: Implement**

```kotlin
package com.pikmin.osm

import com.pikmin.model.LatLng
import com.pikmin.model.WalkGraph

/** Tries [primary]; on any failure signals [onFallback] and returns [fallback]. Encapsulates WalkService's inline try/fallback. */
class CompositeRoadSource(
    private val primary: RoadSource,
    private val fallback: RoadSource,
    private val onFallback: (Throwable) -> Unit = {},
) : RoadSource {
    override suspend fun graphAround(center: LatLng, radiusM: Int): WalkGraph =
        try { primary.graphAround(center, radiusM) }
        catch (t: Throwable) { onFallback(t); fallback.graphAround(center, radiusM) }
}
```

- [ ] **Step 4: Run → PASS.** Record `walk-sim_s3_function_pass_composite-source.xml`.

### Task 3.3: Inject `RoadSource` into `WalkService`

**Files:** Modify `walk-sim/app/src/main/java/com/pikmin/walksim/WalkService.kt` (`:49-53` fields, `:114` onCreate/injector wiring, `:205-219` `resolveGraph`/`shibuyaGraph`).

**Interfaces:**
- Consumes: `CompositeRoadSource`, `FixtureRoadSource`, `OverpassRoadSource`.
- Produces: `WalkService` holds a `roadSource: RoadSource` field; `resolveGraph` calls it instead of `OverpassRoadSource()`; the Shibuya fallback is reached via `CompositeRoadSource`'s `onFallback` (which sets the existing `WalkBus.setupError` banner).

- [ ] **Step 1: Add the field + wire it in `onCreate`** — build once: `CompositeRoadSource(OverpassRoadSource(), FixtureRoadSource { assets.open("shibuya.json").bufferedReader().use { it.readText() } }, onFallback = { WalkBus.setupError.value = "Map fetch failed — using the offline Shibuya map." })`. Keep it a field so a test/alt build can substitute it.

- [ ] **Step 2: Simplify `resolveGraph`** — replace the inline `runCatching { OverpassRoadSource()… }.getOrElse { … shibuyaGraph() to SHIBUYA }` (`:205-213`) with `roadSource.graphAround(start, radiusM) to start`. The fallback now lives in the composite. Note: on fallback the composite returns the Shibuya graph but the **effective start stays `start`**; preserve today's behavior by having the fixture fallback pin re-home to `SHIBUYA` — keep a small post-check: if the returned graph is the fixture, use `SHIBUYA` as `effStart` (retain current semantics; add a test-visible flag or compare node identity). Simplest faithful port: keep `resolveGraph` returning `Pair<WalkGraph, LatLng>` and set `effStart = SHIBUYA` only in the `onFallback` path via a captured flag.

- [ ] **Step 3: Delete the now-dead `shibuyaGraph()` + `bakedShibuya` cache** (replaced by `FixtureRoadSource`'s own cache) — `:215-219`, `:300-302`.

- [ ] **Step 4: Build + full suite + goldens**

Run: `cd /Users/davidhuang/Projects/pikmin-remote-control/walk-sim && ./gradlew test`
Expected: green; route goldens unchanged (route math untouched).

- [ ] **Step 5: Record proof** → `walk-sim_s3_function_pass_roadsource-inject.xml`.

### Task 3.4: Stage 3 — 3-QA GATE (+ on-device)

- [ ] **qa-function:** live city still fetches + walks; a forced Overpass failure (airplane mode) falls back to Shibuya **with the banner shown** (assert on-device); no silent wrong-city.
- [ ] **qa-quality:** the process-session cache still holds (no double-fetch); no extra allocation on the hot path; no new dependency.
- [ ] **qa-principle:** only the injection seam added; **no** nearest-preset multi-fixture speculation; `resolveGraph` behavior identical.
- [ ] **On-device:** one live preset + one forced fallback both inject correctly. Proof `walk-sim_s3_function_pass_s3-ondevice-NOTES.md`.
- [ ] All three PASS → Stage 4 may start.

---

## Stage 4 — Orchestration extract

*Medium; the biggest file, done last behind the goldens. Full 3-QA + on-device smoke.*

### Task 4.1: `LocationSink` interface + `FakeLocationSink`

**Files:**
- Create: `walk-sim/app/src/main/java/com/pikmin/walksim/session/LocationSink.kt`
- Create (in test): `FakeLocationSink` inside `WalkSessionControllerTest.kt`
- Modify: `walk-sim/app/src/main/java/com/pikmin/walksim/LocationInjector.kt` (add `: LocationSink`)

**Interfaces:**
- Produces: `interface LocationSink { fun engage(): Boolean; fun hold(pos: LatLng); fun push(sample: SimSample); fun restore() }` — exactly mirrors `LocationInjector.start()/holdAt()/push()/restore()`.

- [ ] **Step 1: Write the interface**

```kotlin
package com.pikmin.walksim.session

import com.pikmin.model.LatLng
import com.pikmin.model.SimSample

/** The platform location surface the session drives. Real impl: [com.pikmin.walksim.LocationInjector]; tests use a fake. */
interface LocationSink {
    fun engage(): Boolean   // == LocationInjector.start()
    fun hold(pos: LatLng)   // == LocationInjector.holdAt(pos)
    fun push(sample: SimSample)
    fun restore()
}
```

- [ ] **Step 2: Make `LocationInjector` implement it** — add `: LocationSink` to the class, rename `start()`→`engage()` and `holdAt(p)`→`hold(p)` (or add thin `override` aliases to avoid churn at call sites; prefer aliases: `override fun engage() = start()`, `override fun hold(pos: LatLng) = holdAt(pos)`). `push`/`restore` already match.

- [ ] **Step 3: Compile** `./gradlew :app:testDebugUnitTest` — expect green (no logic change yet).

### Task 4.2: Extract `WalkSessionController` (pure orchestration)

**Files:**
- Create: `walk-sim/app/src/main/java/com/pikmin/walksim/session/WalkSessionController.kt`
- Modify: `WalkService.kt` (`runWalk`/`playRoute`/sequential/hold move out; `:120-213`)

**Interfaces:**
- Consumes: `RoadSource`, `LocationSink`, `WalkStateMachine`, `sweepFetchRadiusM`, `WalkPlayer`, `sequencePlan`, `PRESET_LOCATIONS`.
- Produces: `class WalkSessionController(roadSource, sink, machine, config)` with `suspend fun run(spec: RunSpec)` where `RunSpec` carries `{start, durationS, profile, seed, mode: Single|Sequential|Hold, laneSpacingM, closeLoop, radiusOverrideM}`; emits progress via a passed `onProgress`/`onSample` callback (keeps `WalkBus` writes in the Service). Preserves order: `engage()` → (per segment) `hold()` before fetch → `resolveGraph` → play → `restore()` in `finally`.

- [ ] **Step 1: Write the sequence-matrix tests first (RED)** — `WalkSessionControllerTest.kt` with a `FakeLocationSink` recording an ordered `log: List<String>` of `engage/hold/push/restore`, a fake `RoadSource` returning a tiny fixture graph, and a `RunSpec` per case:

```kotlin
package com.pikmin.walksim.session
// ...imports...
class WalkSessionControllerTest {
    private class FakeSink(var engageOk: Boolean = true) : LocationSink {
        val log = mutableListOf<String>()
        override fun engage(): Boolean { log += "engage"; return engageOk }
        override fun hold(pos: LatLng) { log += "hold(${"%.4f".format(pos.lat)})" }
        override fun push(sample: SimSample) { log += "push" }
        override fun restore() { log += "restore" }
    }
    @Test fun engageBeforeAnyFetchOrHold() { /* assert log.first()=="engage" */ }
    @Test fun holdBeforeFetchThenPlays() { /* assert a hold precedes the first push */ }
    @Test fun restoreRunsEvenOnFetchException() { /* failing RoadSource → log.last()=="restore" */ }
    @Test fun sequentialHoldsEachCityBeforeItsSegment() { /* N presets → N holds interleaved before pushes */ }
    @Test fun holdModePlaysNoRouteAndPaceNotPlaying() { /* mode=Hold → holds, zero push of moving samples */ }
    @Test fun engageFailRaisesBannerNoFetch() { /* engageOk=false → mockAppOk callback false, no hold/push */ }
}
```

- [ ] **Step 2: Run → FAIL** (controller undefined).

- [ ] **Step 3: Implement `WalkSessionController`** by lifting the bodies of `runWalk` (`WalkService.kt:120-164`), `playRoute` (`:171-193`), `awaitRunnable` (`:196-199`), and `resolveGraph` (`:205-213`, now delegating to the injected `RoadSource`) verbatim, swapping `injector` for the `LocationSink`, `System.currentTimeMillis()` seed passed in via `RunSpec`, and `WalkBus.*` writes routed through injected callbacks. **No logic edits** — pure relocation.

- [ ] **Step 4: Run → PASS** all six. `./gradlew :app:testDebugUnitTest --tests com.pikmin.walksim.session.WalkSessionControllerTest`

- [ ] **Step 5: Record proof** → `walk-sim_s4_function_pass_session-controller.xml`.

### Task 4.3: Slim `WalkService` to a thin FGS adapter

**Files:** Modify `walk-sim/app/src/main/java/com/pikmin/walksim/WalkService.kt`

- [ ] **Step 1: Delegate** — `WalkService` keeps: intent parsing → `RunSpec`, FGS/notification, wakelock, the real `LocationInjector` as `LocationSink`, the injected `RoadSource`, and `WalkBus` writes via the controller's callbacks. `startWalk` builds a `RunSpec` and launches `scope.launch { controller.run(spec) }`. Target ≤ ~150 LOC; no route/sequence/hold logic remains inline.

- [ ] **Step 2: Full suite + goldens + build**

Run: `cd /Users/davidhuang/Projects/pikmin-remote-control/walk-sim && ./gradlew test`
Expected: green; goldens unchanged; `WalkService` line count ≤ ~150 (verify with `wc -l`).

- [ ] **Step 3: Record proof** → `walk-sim_s4_quality_pass_walkservice-slim.txt` (before/after `wc -l`).

### Task 4.4: Stage 4 — 3-QA GATE (+ on-device)

- [ ] **qa-function:** every AC-11..16 ordering preserved (proven by the fake-sink matrix); goldens + full suites green; sequence tests are non-tautological (reorder a call in the controller → a test fails).
- [ ] **qa-quality:** no added latency/threads; `WalkService` ≤ ~150 LOC with no route/sequence logic; no new dependency.
- [ ] **qa-principle:** extraction is pure relocation — only `LocationSink` + `WalkSessionController` added, no behavior change, no interface zoo.
- [ ] **On-device:** a short real preset walk still credits distance + steps; stop restores providers. Proof `walk-sim_s4_function_pass_s4-ondevice-NOTES.md`.
- [ ] All three PASS → Stage 4 complete.

---

## Stage 5 — Scalability (measure-gated)

*May never run. Decision first.*

### Task 5.0: Decision gate

- [ ] **Step 1: Re-measure** — `WALKSIM_PERF=1 ./gradlew :core-sim:test --tests com.pikmin.sim.PerfBaselineTest -i` on the current tree.
- [ ] **Step 2: Compare to the Stage 0 budget** (Task 0.4, baseline × 1.5). **Within budget → STOP:** record the defer decision + numbers in `walk-sim_s5_quality_pass_defer-decision.md` and close Plan A. **Breach → proceed to 5.1.**

### Task 5.1 (only if triggered): fix the measured hot phase

- [ ] **Step 1:** Identify the breaching phase from `QG-GEN`/`QG-HEAP` (candidates: O(N)-per-waypoint `snapWaypoint` scan in `SweepRoute.kt:135-146`; eager `ArrayList<MotionFrame>` in `WalkingMotionEngine.kt:101`).
- [ ] **Step 2:** Write an equivalence test — the optimized phase must reproduce the Stage 0 route/motion goldens exactly (or, if sampling changes, within the AC-1/7/8/9/10 bands with new goldens + a human note).
- [ ] **Step 3:** Implement the minimal fix for that phase only; re-run goldens + perf.
- [ ] **Step 4: 3-QA GATE** — function (goldens/ACs hold), quality (measured phase back within budget, no regression elsewhere), principle (only the hot phase touched).

---

## Self-Review

**Spec coverage:** Stage 0 ↔ §6 Stage 0 (goldens + perf + contract table ✓); Stage 1 ↔ Geo hygiene ✓; Stage 2 ↔ IPC contract + schemaVersion + dual-root contract test ✓; Stage 3 ↔ RoadSource inject + Fixture + Composite + bannered fallback ✓; Stage 4 ↔ WalkSessionController + LocationSink + fake-sink matrix + thin Service ✓; Stage 5 ↔ measure-gated decision ✓. Success metrics (§8) each map to a gate check. Out-of-scope (§9) respected (no WalkRequest/RouteStrategy/new modules/nearest-preset/GUI).

**Placeholder scan:** golden/perf values are recorded-at-runtime by the bootstrap pattern (not placeholders — the correct golden idiom). Stage 4's line-level relocation references exact `WalkService.kt` line ranges rather than reproducing the 304-line file (per the skill's `path:lines` guidance); the controller's method bodies are "lift verbatim," which is precise, not vague. Stage 5 conditionality is inherent to a measure-gated stage, not an omission.

**Type consistency:** `RoadSource.graphAround(center, radiusM)` used identically in Fixture/Composite/Service. `LocationSink {engage,hold,push,restore}` matches `LocationInjector` method names via the Step-2 aliases. `PaceProvider.COL_SCHEMA_VERSION`/`SCHEMA_VERSION` produced in 2.2, consumed in 2.1's assertions. `CompositeRoadSource(primary, fallback, onFallback)` signature consistent across 3.2 and 3.3.

---

## Execution Handoff

Do **not** begin execution until the user green-lights it (Plan A executes first; Plan B follows). When executing:

- **Subagent-Driven (recommended):** one fresh subagent per task on **Opus 4.8**, two-stage review between tasks, and the **3-QA gate as a hard stop between stages** — the three QA roles must all PASS before the next stage's first task is dispatched.
- The 3-QA gate is non-negotiable per the user's constraint: *no new changes until the previous stage is approved by all three QA*.
