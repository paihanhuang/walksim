package com.pikmin.walksim

import com.pikmin.model.LatLng
import com.pikmin.sim.DEFAULT_LANE_SPACING_M

/**
 * A pickable start location for the walk. [at] is a central, street-dense point so `snapStart` lands.
 * [spacingM] (harvest-sweep lane spacing) + [routeLengthKm]: **v1.9 standardized every preset to 500 m / 10 km**
 * (harvest reach 250 m → lane spacing 2×250 = 500 m tiles both sides gap-free; 10 km ≈ a ~2 h session). Decided
 * by the real in-Pikmin big-flower census: re-spacing is a wash on flowers (a wider spiral's bigger disc cancels
 * its gaps), so the old longer/wider per-city tunings only cost walk time — 500 m reaches the same coverage in
 * less time AND gap-free at 250 m, and 10 km replaces the 4–5 h marathons. Trade-off (user-accepted): the former
 * 20 km presets pass ~half the total flowers of the marathon, in half the time. Selecting a preset sets the
 * duration to [routeLengthKm]/speed. Proof: `proofs/walk-sim_acceptance_function_ueno-route-census.md`.
 */
data class NamedLocation(
    val label: String,
    val at: LatLng,
    val spacingM: Double = DEFAULT_LANE_SPACING_M,
    val routeLengthKm: Double = 20.0,
)

/**
 * The location picker's presets — all v1.9-standardized to a 500 m / 10 km harvest sweep (see [NamedLocation]).
 * Selecting one recentres the map, moves the start pin, and sets the duration to 10 km/speed (single-preset only).
 * Per-preset gap-freeness at 500 m/250 m-reach measured on the live graph (`proofs/preset-standardization-500-10km.txt`):
 * cov@250 Shibuya 1.00, Roppongi 0.97, Azabudai 0.93, Osaka 0.96, Xinyi 0.99, Seoul 0.99, Ueno 0.95 (Okubo 0.65 —
 * its 12k-node alley grid is the one graph that stays gappy even at 500 m; still far better than its old 1100 m/0.64).
 */
val PRESET_LOCATIONS = listOf(
    NamedLocation("Shibuya, Tokyo, Japan", LatLng(35.6595, 139.7006), spacingM = 500.0, routeLengthKm = 10.0),
    NamedLocation("Okubo, Tokyo, Japan", LatLng(35.6975, 139.7005), spacingM = 500.0, routeLengthKm = 10.0),
    NamedLocation("Roppongi, Tokyo, Japan", LatLng(35.6628, 139.7314), spacingM = 500.0, routeLengthKm = 10.0),
    NamedLocation("Azabudai, Tokyo, Japan", LatLng(35.6605, 139.7400), spacingM = 500.0, routeLengthKm = 10.0),
    NamedLocation("Chuo-ku, Tokyo, Japan", LatLng(35.6717, 139.7648), spacingM = 500.0, routeLengthKm = 10.0),
    // Odaiba dropped (user 2026-07-04): sparse bay net re-walks ~35% at any length — not a viable harvest preset.
    NamedLocation("Ueno, Tokyo, Japan", LatLng(35.7089, 139.7745), spacingM = 500.0, routeLengthKm = 10.0),
    NamedLocation("Osaka Minami (Dotonbori), Japan", LatLng(34.6687, 135.5013), spacingM = 500.0, routeLengthKm = 10.0),
    NamedLocation("Osaka Namba, Japan", LatLng(34.6659, 135.5020), spacingM = 500.0, routeLengthKm = 10.0),
    NamedLocation("Xinyi, Taipei, Taiwan", LatLng(25.0339, 121.5645), spacingM = 500.0, routeLengthKm = 10.0),
    NamedLocation("Seoul Myeongdong, Korea", LatLng(37.5636, 126.9848), spacingM = 500.0, routeLengthKm = 10.0),
)

/**
 * Whole minutes to walk [routeLengthKm] at [speedMps] — what the picker writes to the duration field on preset
 * selection. A non-positive [speedMps] (the user can type "0" — `"0".toDoubleOrNull()` is a valid 0.0, not null)
 * falls back to 1.3 m/s so the division never yields Infinity → `Math.round` → `Long.MAX_VALUE` → a garbage /
 * (on the START re-parse) negative duration.
 */
fun presetDurationMinutes(routeLengthKm: Double, speedMps: Double): Long {
    val speed = speedMps.takeIf { it > 0.0 } ?: 1.3
    return Math.round(routeLengthKm * 1000.0 / speed / 60.0)
}

/**
 * The default "All areas (sequential)" schedule: split [totalDurationS] evenly across [presets], one pass, in
 * order. Every preset gets `total / n` seconds; the **last** absorbs the integer-division remainder so the
 * segments sum to exactly [totalDurationS]. Empty [presets] → empty plan.
 */
fun sequencePlan(presets: List<NamedLocation>, totalDurationS: Long): List<Pair<NamedLocation, Long>> {
    if (presets.isEmpty()) return emptyList()
    val each = totalDurationS / presets.size
    return presets.mapIndexed { i, preset ->
        val seg = if (i == presets.lastIndex) totalDurationS - each * (presets.size - 1) else each
        preset to seg
    }
}
