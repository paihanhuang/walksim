package com.pikmin.sim

import com.pikmin.model.WalkProfile
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Env-gated gen/heap perf baseline (Stage 0). Returns early unless WALKSIM_PERF=1, so it adds no default-run
 * cost. When enabled it times the full generation pipeline (sweepRoute -> densify -> motion frames) on an
 * Okubo-scale dense grid and prints QG-GEN / QG-HEAP lines. The captured numbers seed the Stage 5 trigger
 * budget (= measured x 1.5); Stage 5 runs only if a future measurement breaches it.
 *
 * Uses JUnit Jupiter (this module's convention; kotlin-test is not on the classpath).
 */
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
