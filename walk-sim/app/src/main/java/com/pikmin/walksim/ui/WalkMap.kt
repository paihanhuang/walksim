package com.pikmin.walksim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pikmin.model.LatLng
import com.pikmin.model.SimSample
import com.pikmin.walksim.R
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Cap on the cosmetic petal trail: the most-recent WalkBus.sample fixes (~last 200 ticks). */
private const val MAX_TRAIL_POINTS = 200

private fun LatLng.toGeoPoint() = GeoPoint(lat, lng)

/**
 * The Stage-2 osmdroid map hosted in Compose — it replaces the Stage-1 map-slot placeholder in [WalkScreen].
 *
 * The `factory` builds the [MapView] EXACTLY as the old imperative `MainActivity.setupMap()` did (MAPNIK,
 * multi-touch, zoom 16, a draggable [Marker], and a [MapEventsOverlay] for taps) and the tap / drag-end
 * callbacks route through [pickedStart] into [onPick] — so the pin tap/drag behavior is preserved 1:1 with
 * today: the `update` lambda recenters the marker to the current [startPin], and a preset selection (a change
 * to [selectedPosition]) animates the viewport to [presetCenter], mirroring the old `wireLocationSpinner`.
 *
 * The flower marker icon and the pink [Polyline] petal trail (drawn from recent [sample] positions) are the
 * only COSMETIC additions; they never touch injection, the START intent, or `WalkBus` beyond reading `sample`.
 *
 * @param startPin current start pin (from `WalkBus`-independent picker state) — the marker position.
 * @param selectedPosition dropdown index; a change recenters the map to that preset (0 → first preset).
 * @param sample latest injected fix (`WalkBus.sample`); its position is appended to the cosmetic trail.
 * @param onPick called with the tapped / dragged coordinate — the host updates its start pin (already wired).
 */
@Composable
fun WalkMap(
    startPin: LatLng,
    selectedPosition: Int,
    sample: SimSample?,
    onPick: (LatLng) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A Compose @Preview / LayoutLib can't host osmdroid tiles; keep the Stage-1 map-slot colour so the
    // WalkScreen previews still render. No osmdroid object is created in inspection mode.
    if (LocalInspectionMode.current) {
        Box(modifier.background(Color(PetalTokens.MAP)))
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // onPick may be a fresh lambda each recomposition; keep the once-built overlay listeners calling the latest.
    val currentOnPick by rememberUpdatedState(onPick)

    // One-time MapView — built EXACTLY like the old setupMap() (MAPNIK, multi-touch, zoom 16, initial center).
    val mapView = remember {
        // Identify to the tile server before the MapView fetches tiles (restored from the old onCreate).
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            controller.setCenter(startPin.toGeoPoint())
        }
    }

    // Draggable start-pin marker: a flower icon (cosmetic) over the old anchor + drag-end -> setStart behavior.
    val marker = remember {
        Marker(mapView).apply {
            position = startPin.toGeoPoint()
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            isDraggable = true
            icon = context.getDrawable(R.drawable.ic_flower_pin)
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(m: Marker) {}
                override fun onMarkerDragStart(m: Marker) {}
                override fun onMarkerDragEnd(m: Marker) =
                    currentOnPick(pickedStart(m.position.latitude, m.position.longitude))
            })
        }
    }

    // Tap-to-place — the MapEventsOverlay from the old setupMap(): a single tap moves the pin, long press is ignored.
    val tapOverlay = remember {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                currentOnPick(pickedStart(p.latitude, p.longitude)); return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
    }

    // Cosmetic petal trail: a pink polyline of the recent injected-fix positions.
    val trailOverlay = remember {
        Polyline(mapView).apply {
            // Cosmetic-only: a click listener returning false makes a tap within stroke-width tolerance of the
            // trail NOT be consumed (osmdroid 6.1.20 Polyline.click() calls this listener instead of the
            // onClickDefault path that returns true unconditionally). The tap then falls through to the
            // MapEventsOverlay, so tap-to-place stays 1:1 with the old map. Returning false also skips
            // onClickDefault entirely, so the trail's info window is never shown.
            setOnClickListener { _, _, _ -> false }
            outlinePaint.color = PetalTokens.STOP
            outlinePaint.strokeWidth = 12f
            outlinePaint.isAntiAlias = true
        }
    }
    val trail = remember { mutableStateListOf<GeoPoint>() }
    LaunchedEffect(sample) {
        val pos = sample?.pos
        if (pos == null) {
            trail.clear() // walk ended (WalkBus.clear() nulls the sample) -> drop the trail.
        } else {
            trail.add(pos.toGeoPoint())
            while (trail.size > MAX_TRAIL_POINTS) trail.removeAt(0)
        }
    }

    // MapView lifecycle — mirrors the old Activity.onResume/onPause; onDetach on dispose prevents a leak.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Preset selection recenters the viewport — the old wireLocationSpinner's map.controller.animateTo. Keyed on
    // the dropdown index so a tap/drag (which never changes it) does NOT recenter — 1:1 with today's spinner.
    LaunchedEffect(selectedPosition) {
        mapView.controller.animateTo(presetCenter(selectedPosition).toGeoPoint())
    }

    AndroidView(
        factory = {
            // Runs ONCE — attach overlays a single time (no per-recomposition MapView/overlay leak). Draw order:
            // tap overlay at the bottom, trail above it, pin on top; touch dispatch (top-first) reaches the
            // marker before the tap overlay, exactly like the old [marker, MapEventsOverlay@0] setup.
            mapView.overlays.add(tapOverlay)
            mapView.overlays.add(trailOverlay)
            mapView.overlays.add(marker)
            mapView
        },
        update = { map ->
            // Cheap per-recomposition work only: recenter the marker to the current pin + refresh the trail.
            marker.position = startPin.toGeoPoint()
            trailOverlay.setPoints(trail.toList())
            map.invalidate()
        },
        modifier = modifier,
    )
}
