package com.pikmin.walksim.ui

import com.pikmin.model.LatLng
import com.pikmin.walksim.PRESET_LOCATIONS

/**
 * Pure pin-selection helpers behind the Stage-2 osmdroid [WalkMap]. Extracted so the map glue stays dumb and
 * the pin behavior is provable without a device ([PinMathTest]). Each mirrors the old imperative
 * [com.pikmin.walksim.MainActivity] map code verbatim; no Android or osmdroid imports live here.
 */

/**
 * A map tap / marker-drag lands the start pin exactly where the finger did — the passthrough the old `setStart`
 * did with `LatLng(p.latitude, p.longitude)`. Trivial today, but the seam lets [WalkMap]'s tap/drag callbacks
 * route through one named function instead of building coordinates inline.
 */
fun pickedStart(tapLat: Double, tapLng: Double): LatLng = LatLng(tapLat, tapLng)

/**
 * The map centre a picker selection recentres to — the old `wireLocationSpinner` lookup: position 0
 * ("All areas") centres on the first preset; position N centres on `PRESET_LOCATIONS[N-1]`.
 */
fun presetCenter(position: Int): LatLng =
    (if (position == 0) PRESET_LOCATIONS.first() else PRESET_LOCATIONS[position - 1]).at
