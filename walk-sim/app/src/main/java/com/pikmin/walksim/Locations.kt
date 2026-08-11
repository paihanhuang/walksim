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
    /**
     * Surveyed big-flower sites (R2/R3). EMPTY (the default) = a harvest-sweep preset, unchanged. Non-empty
     * switches the walk to [com.pikmin.sim.flowerRoute]: the shortest road tour passing every listed site,
     * for places whose flowers sit in a few known clusters rather than spread across a dense core. Sites come
     * from a real in-Pikmin teleport census — see `docs/sdlc/portrait-and-flower-presets/`.
     */
    val flowers: List<LatLng> = emptyList(),
)

/**
 * The location picker's presets. The first ten are v1.9-standardized 500 m / 10 km harvest SWEEPS (see
 * [NamedLocation]); the last two are R2/R3 flower TOURS, which carry a surveyed [NamedLocation.flowers] list
 * instead and whose length is their measured tour, not the standard 10 km.
 * Selecting one recentres the map, moves the start pin, and sets the duration to routeLengthKm/speed (single-preset only).
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
    // --- Flower-tour presets (R2/R3): shortest road tour over CENSUSED big-flower sites, not a sweep. ---
    // Haneda: the terminals are nearly barren (0-1 flowers/frame); the landside west — Anamori-inari, HICity,
    // Tenkubashi, Otorii — carries almost all of them. Census: 16 points, 20 flowers, 1.25/frame. The start is
    // Terminal 3, CENTRAL to the 4 km-wide survey: an edge start (Anamori) put T1/T2 at the rim of the fetch
    // disc, where their roads were clipped into a component the largest-component guard then discarded.
    // Tour measured on the live graph: 17.13 km reaching all 10 sites, worst approach 53 m
    // (docs/sdlc/portrait-and-flower-presets/proofs/flower-route_integration_function_pass_live-overpass.txt).
    NamedLocation(
        "Haneda Airport, Tokyo, Japan",
        LatLng(35.5449, 139.7699),
        routeLengthKm = 17.13,
        flowers = listOf(
            LatLng(35.5468, 139.7462), // Anamori-inari (3)
            LatLng(35.5490, 139.7440), // Haneda Innovation City (3)
            LatLng(35.5533, 139.7452), // Tenkubashi (2)
            LatLng(35.5525, 139.7405), // Otorii (2)
            LatLng(35.5455, 139.7520), // Anamori south / riverside (2)
            LatLng(35.5510, 139.7530), // Haneda 4-chome (1)
            LatLng(35.5570, 139.7480), // Haneda Asahi-cho (1)
            LatLng(35.5449, 139.7699), // Terminal 3 / International (1)
            LatLng(35.5494, 139.7857), // Terminal 1 (1)
            LatLng(35.5533, 139.7876), // Terminal 2 (1)
        ),
    ),
    // Enoshima: densest of the two new areas (1.78 flowers/frame on land). Starts at Katase-Enoshima station
    // on the mainland rather than the island shrine — the island's ways are all footway/steps, so a street-only
    // graph has no node within 200 m of the shrine and the route build fails outright. Tour measured on the
    // live graph: 6.73 km reaching all 8 sites, worst approach 223 m (docs/sdlc/portrait-and-flower-presets/proofs/flower-route_integration_function_pass_live-overpass.txt).
    NamedLocation(
        "Enoshima / Katase-Kaigan, Japan",
        LatLng(35.3095, 139.4838),
        routeLengthKm = 6.73,
        flowers = listOf(
            LatLng(35.2989, 139.4803), // Enoshima island shrine (3)
            LatLng(35.2965, 139.4795), // Iwaya, island south (2)
            LatLng(35.3020, 139.4810), // Benten bridge (2)
            LatLng(35.3060, 139.4830), // Katase-Kaigan beach east (3)
            LatLng(35.3095, 139.4838), // Katase-Enoshima station (1)
            LatLng(35.3130, 139.4880), // Katase (3)
            LatLng(35.3140, 139.4790), // Katase inland (1)
            LatLng(35.3060, 139.4910), // Koshigoe (1)
        ),
    ),
)

/** Wire form of a flower list for an Intent extra / `adb am` argument: `lat,lng;lat,lng;…`. */
fun encodeFlowers(flowers: List<LatLng>): String =
    flowers.joinToString(";") { "${it.lat},${it.lng}" }

/** Inverse of [encodeFlowers]; any malformed pair is skipped rather than failing the whole start. */
fun decodeFlowers(encoded: String?): List<LatLng> =
    encoded.orEmpty().split(';').mapNotNull { pair ->
        val parts = pair.split(',')
        val lat = parts.getOrNull(0)?.trim()?.toDoubleOrNull()
        val lng = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
        if (lat != null && lng != null) LatLng(lat, lng) else null
    }

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
 * The "All areas full-route" schedule: each preset walks its OWN full route ([NamedLocation.routeLengthKm]) to
 * completion before the next begins — so a city is never cut off mid-route; the only jump is city→city. Each
 * preset's seconds = routeLengthKm / [speedMps]; the total is the sum (not bounded by any shared duration).
 */
fun fullRoutePlan(presets: List<NamedLocation>, speedMps: Double): List<Pair<NamedLocation, Long>> {
    val speed = speedMps.takeIf { it > 0.0 } ?: 1.3 // a "0"/negative speed must not divide-by-zero → Long.MAX_VALUE
    return presets.map { it to Math.round(it.routeLengthKm * 1000.0 / speed) }
}
