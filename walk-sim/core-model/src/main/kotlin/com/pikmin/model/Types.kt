package com.pikmin.model

/** WGS84 coordinate. */
data class LatLng(val lat: Double, val lng: Double)

/**
 * A directed adjacency edge from its owning node to [toNode], carrying the full polyline [geometry]
 * (geometry.first() == owning node position, geometry.last() == [toNode] position) and its geodesic [lengthM].
 */
data class Edge(val toNode: Long, val geometry: List<LatLng>, val lengthM: Double)

/**
 * Walkable pedestrian graph. [adjacency] lists are canonically ordered by the consumer for determinism.
 * Produced by :core-osm (real OSM) or by test fixtures; consumed by [com.pikmin.sim.GraphRandomWalker].
 */
data class WalkGraph(val nodes: Map<Long, LatLng>, val adjacency: Map<Long, List<Edge>>)

/** A generated walk: an ordered polyline plus its true geodesic length in metres. */
data class Route(val points: List<LatLng>, val totalLengthM: Double)

/** A route densified to ~1 m spacing (output of PathEngine.densify, input to the motion engine). */
typealias DensePath = List<LatLng>

/**
 * Tunable walking dynamics. Defaults are the Gate-1 values: mean 1.3 m/s within [0.8,1.8],
 * acceleration ≤0.5 m/s², stride 0.75 m, ~0.5 pauses/min.
 */
data class WalkProfile(
    val meanSpeedMps: Double = 1.3,
    /**
     * Band the modelled speed varies within. Derived as [meanSpeedMps] ±0.5 m/s so a pace the user asks for is
     * actually walked: the old fixed 0.8..1.8 literal clamped EVERY run to 1.8 m/s, so 5/7/10/20 m/s all played
     * at 1.8 (144 steps/min at a 0.75 m stride — what the on-device pace channel reported). At the 1.3 m/s
     * default this is exactly the old 0.8..1.8, so the shipped walk is unchanged.
     */
    val speedRange: ClosedRange<Double> = maxOf(0.1, meanSpeedMps - 0.5)..(meanSpeedMps + 0.5),
    val maxAccelMpsSq: Double = 0.5,
    val strideM: Double = 0.75,
    val pauseRatePerMin: Double = 0.5,
)

/**
 * One emitted GNSS fix. [pos] carries correlated noise; [bearingDeg]/[speedMps] describe the true
 * (Doppler-like) motion; [accuracyM] reflects the noise magnitude. Produced once per tick.
 */
data class SimSample(
    val pos: LatLng,
    val bearingDeg: Float,
    val speedMps: Float,
    val accuracyM: Float,
    val stepCount: Int,
    val tickIndex: Long,
    /** Cumulative on-path distance walked up to this tick, in metres. */
    val cumulativeDistanceM: Double,
)
