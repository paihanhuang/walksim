package com.pikmin.walksim

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.pikmin.model.SimSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/**
 * WalkSim controller UI (T4.5): an osmdroid pin picker (default Shibuya) + duration/pace fields +
 * start/pause/resume/stop, a live HUD fed by [WalkBus.sample], and the AC-16 not-mock-app / AC-23 setup
 * banners. All map/GNSS heavy lifting is in [WalkService]; this screen only issues intents and renders state.
 */
class MainActivity : Activity() {

    private val ui = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private lateinit var map: MapView
    private lateinit var marker: Marker
    private lateinit var banner: TextView
    private lateinit var locationSpinner: Spinner
    private lateinit var startLabel: TextView
    private lateinit var durationField: EditText
    private lateinit var paceField: EditText
    private lateinit var startBtn: Button
    private lateinit var pauseBtn: Button
    private lateinit var resumeBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var hud: TextView

    private var start = WalkService.SHIBUYA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // T4.1: identify to the tile server before the first MapView is created.
        Configuration.getInstance().userAgentValue = packageName

        setContentView(buildLayout())
        setupMap()
        wireLocationSpinner()
        wireButtons()
        observeState()
        requestPerms()
    }

    private fun buildLayout(): View {
        banner = TextView(this).apply {
            setBackgroundColor(Color.parseColor("#FFF3CD"))
            setTextColor(Color.parseColor("#7A5C00"))
            setPadding(24, 16, 24, 16)
            visibility = View.GONE
        }
        locationSpinner = Spinner(this).apply {
            // Position 0 is the default "All areas (sequential)" mode; positions 1.. are the individual presets.
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("All areas (sequential)") + PRESET_LOCATIONS.map { it.label },
            )
        }
        map = MapView(this)
        startLabel = TextView(this).apply { setPadding(24, 12, 24, 0); textSize = 13f }
        durationField = EditText(this).apply {
            hint = "duration (min)"; setText("60")
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        paceField = EditText(this).apply {
            hint = "pace (m/s)"; setText("1.3")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        startBtn = Button(this).apply { text = "START" }
        pauseBtn = Button(this).apply { text = "PAUSE" }
        resumeBtn = Button(this).apply { text = "RESUME" }
        stopBtn = Button(this).apply { text = "STOP" }
        hud = TextView(this).apply { setPadding(24, 12, 24, 24); textSize = 14f; text = "idle" }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(startBtn); addView(pauseBtn); addView(resumeBtn); addView(stopBtn)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(banner)
            addView(locationSpinner)
            addView(map, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(startLabel)
            addView(durationField); addView(paceField)
            addView(buttons)
            addView(hud)
        }
    }

    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(16.0)
        map.controller.setCenter(GeoPoint(start.lat, start.lng))

        marker = Marker(map).apply {
            position = GeoPoint(start.lat, start.lng)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            isDraggable = true
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(m: Marker) {}
                override fun onMarkerDragStart(m: Marker) {}
                override fun onMarkerDragEnd(m: Marker) = setStart(m.position)
            })
        }
        map.overlays.add(marker)
        map.overlays.add(0, MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { setStart(p); return true }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }))
        setStart(GeoPoint(start.lat, start.lng))
    }

    private fun setStart(p: GeoPoint) {
        start = com.pikmin.model.LatLng(p.latitude, p.longitude)
        marker.position = p
        startLabel.text = "start pin: %.5f, %.5f".format(p.latitude, p.longitude)
        map.invalidate()
    }

    /**
     * Location picker: selecting a preset recentres the map, moves the start pin, and (single preset only) sets
     * the duration field to the preset's tuned route length so the tuned (spacing, length) pair is applied
     * together. "All areas" (position 0) centres on the first preset and keeps its own total duration.
     */
    private fun wireLocationSpinner() {
        locationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val preset = if (position == 0) PRESET_LOCATIONS.first() else PRESET_LOCATIONS[position - 1]
                val gp = GeoPoint(preset.at.lat, preset.at.lng)
                setStart(gp)
                map.controller.animateTo(gp)
                if (position != 0) {
                    val speed = paceField.text.toString().toDoubleOrNull() ?: 1.3
                    durationField.setText(presetDurationMinutes(preset.routeLengthKm, speed).toString())
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun wireButtons() {
        startBtn.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                banner("Grant location permission, then START."); requestPerms(); return@setOnClickListener
            }
            val minutes = durationField.text.toString().toLongOrNull() ?: 60L
            val speed = paceField.text.toString().toDoubleOrNull() ?: 1.3
            startForegroundService(Intent(this, WalkService::class.java).apply {
                action = WalkService.ACTION_START
                putExtra(WalkService.EXTRA_DURATION_S, minutes * 60)
                putExtra(WalkService.EXTRA_SPEED_MPS, speed)
                if (locationSpinner.selectedItemPosition == 0) {
                    putExtra(WalkService.EXTRA_SEQUENTIAL, "1") // "All areas": walk every preset in sequence
                } else {
                    putExtra(WalkService.EXTRA_LAT, start.lat)
                    putExtra(WalkService.EXTRA_LNG, start.lng)
                    // Per-preset tuned lane spacing (the measured "optimal route" for this area's density).
                    val preset = PRESET_LOCATIONS[locationSpinner.selectedItemPosition - 1]
                    putExtra(WalkService.EXTRA_SPACING_STR, preset.spacingM.toString())
                    // v1.6: default is the OPEN spiral — it sweeps the most distinct ground (ends at the outer
                    // edge). Closing the loop costs a ~2.5 km return leg home across already-swept rings that
                    // harvests nothing ("back-and-forth"), so it is NOT the default; opt in with `close_s` to
                    // return home at the cost of ~13% of the harvest.
                }
            })
        }
        pauseBtn.setOnClickListener { control(WalkService.ACTION_PAUSE) }
        resumeBtn.setOnClickListener { control(WalkService.ACTION_RESUME) }
        stopBtn.setOnClickListener { control(WalkService.ACTION_STOP) }
    }

    private fun control(action: String) =
        startService(Intent(this, WalkService::class.java).apply { this.action = action })

    private fun observeState() {
        ui.launch { WalkBus.sample.collect { renderHud(it) } }
        ui.launch { WalkBus.status.collect { renderControls(it) } }
        ui.launch { WalkBus.mockAppOk.collect { refreshBanner() } }
        ui.launch { WalkBus.setupError.collect { refreshBanner() } }
    }

    private fun renderControls(status: WalkState) {
        startBtn.isEnabled = status == WalkState.IDLE
        pauseBtn.isEnabled = status == WalkState.RUNNING
        resumeBtn.isEnabled = status == WalkState.PAUSED
        stopBtn.isEnabled = status == WalkState.RUNNING || status == WalkState.PAUSED
        if (status == WalkState.IDLE) hud.text = "idle"
    }

    private fun renderHud(s: SimSample?) {
        if (s == null) return
        val durationS = WalkBus.durationS
        val elapsed = s.tickIndex + 1
        val remaining = (durationS - elapsed).coerceAtLeast(0)
        val pct = if (durationS > 0) (elapsed * 100 / durationS).coerceAtMost(100) else 0
        hud.text = ("speed %.2f m/s   distance %.0f m   steps %d\n" +
            "elapsed %s   remaining %s   progress %d%%")
            .format(s.speedMps, s.cumulativeDistanceM, s.stepCount, mmss(elapsed), mmss(remaining), pct)
    }

    private fun refreshBanner() {
        val text = when {
            !WalkBus.mockAppOk.value ->
                "Not the selected mock-location app. Developer Options → Select mock location app → WalkSim."
            WalkBus.setupError.value != null -> WalkBus.setupError.value
            else -> null
        }
        if (text == null) banner.visibility = View.GONE else banner(text)
    }

    private fun banner(text: String) {
        banner.text = text
        banner.visibility = View.VISIBLE
    }

    private fun mmss(totalS: Long): String = "%02d:%02d".format(totalS / 60, totalS % 60)

    private fun requestPerms() {
        val needed = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) needed += Manifest.permission.POST_NOTIFICATIONS
        val missing = needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { super.onPause(); map.onPause() }
    override fun onDestroy() { ui.cancel(); super.onDestroy() }
}
