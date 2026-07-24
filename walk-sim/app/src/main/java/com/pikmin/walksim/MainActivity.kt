package com.pikmin.walksim

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pikmin.walksim.ui.StartSpec
import com.pikmin.walksim.ui.autostartSpec
import com.pikmin.walksim.ui.WalkScreen
import com.pikmin.walksim.ui.WalkSimTheme
import com.pikmin.walksim.ui.WalkViewState
import com.pikmin.walksim.ui.durationForSelection
import com.pikmin.walksim.ui.startSpec

/**
 * WalkSim controller UI (Plan B): the "Petal Pop" [WalkScreen] hosted in Compose. This Activity is now just
 * the Android shell — it collects [WalkBus] flows into the screen's [WalkViewState], owns the runtime
 * permission flow, and turns the parity-checked [StartSpec] / control actions into the byte-identical intents
 * [WalkService] already expects. All render logic lives in [WalkScreen]; all decisions live in the pure
 * `WalkUiLogic` functions. The map is a Stage-2 placeholder inside [WalkScreen].
 */
class MainActivity : ComponentActivity() {

    private var pendingAutostart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Petal Pop splash (soft-pink window + garden sprout) via androidx.core:core-splashscreen; must run
        // before super.onCreate so the compat splash installs. Cosmetic only — no walk behaviour is touched.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Boot autostart: the walksim-autostart Magisk module launches us with EXTRA_AUTOSTART. Fresh launch
        // only (not a config-change recreation); the walk actually starts once resumed (onResume).
        pendingAutostart = savedInstanceState == null && intent.getBooleanExtra(EXTRA_AUTOSTART, false)
        setContent {
            WalkSimTheme {
                val status by WalkBus.status.collectAsStateWithLifecycle()
                val sample by WalkBus.sample.collectAsStateWithLifecycle()
                val mockAppOk by WalkBus.mockAppOk.collectAsStateWithLifecycle()
                val setupError by WalkBus.setupError.collectAsStateWithLifecycle()

                // Local picker state — the Compose equivalent of the old spinner/pin/field members.
                var selectedPosition by remember { mutableStateOf(0) }
                var startPin by remember { mutableStateOf(WalkService.SHIBUYA) }
                var durationMin by remember { mutableStateOf("60") }
                var paceMps by remember { mutableStateOf("1.3") }
                var permissionHint by remember { mutableStateOf<String?>(null) }

                WalkScreen(
                    state = WalkViewState(
                        status = status,
                        sample = sample,
                        // Plain @Volatile var (not a Flow); read here so it is re-sampled on every
                        // recomposition — the sample/status emission that drives the HUD guarantees it is
                        // current by then, exactly as the old renderHud read WalkBus.durationS per sample.
                        durationS = WalkBus.durationS,
                        mockAppOk = mockAppOk,
                        setupError = setupError,
                        selectedPosition = selectedPosition,
                        startPin = startPin,
                        durationMin = durationMin,
                        paceMps = paceMps,
                        permissionHint = permissionHint,
                    ),
                    onStart = {
                        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            permissionHint = "Grant location permission, then START."
                            requestPerms()
                        } else {
                            permissionHint = null
                            // Persist so the boot autostart replays this exact preset/duration/pace.
                            persistSelection(selectedPosition, durationMin.toLongOrNull() ?: 60L, paceMps.toDoubleOrNull() ?: 1.3)
                            sendStart(
                                startSpec(
                                    selectedPosition = selectedPosition,
                                    startPin = startPin,
                                    durationMin = durationMin.toLongOrNull() ?: 60L,
                                    speedMps = paceMps.toDoubleOrNull() ?: 1.3,
                                ),
                            )
                        }
                    },
                    onPause = { control(WalkService.ACTION_PAUSE) },
                    onResume = { control(WalkService.ACTION_RESUME) },
                    onStop = { control(WalkService.ACTION_STOP) },
                    onPick = { startPin = it },
                    onSelectPreset = { position ->
                        // Mirrors the old spinner listener: recentre the pin on the preset and, for a single
                        // preset (position>=1), set the duration to its tuned route length; "All areas" (0) keeps it.
                        selectedPosition = position
                        startPin = (if (position == 0) PRESET_LOCATIONS.first() else PRESET_LOCATIONS[position - 1]).at
                        durationForSelection(position, paceMps.toDoubleOrNull() ?: 1.3)?.let { durationMin = it.toString() }
                    },
                    onEditDuration = { durationMin = it },
                    onEditPace = { paceMps = it },
                )
            }
        }
        requestPerms()
    }

    /** The only Android glue: turn a parity-checked [StartSpec] into the byte-identical START intent. */
    private fun sendStart(spec: StartSpec) {
        startForegroundService(
            Intent(this, WalkService::class.java).apply {
                action = WalkService.ACTION_START
                putExtra(WalkService.EXTRA_DURATION_S, spec.durationS)
                putExtra(WalkService.EXTRA_SPEED_MPS, spec.speedMps)
                when (spec) {
                    is StartSpec.Sequential -> putExtra(WalkService.EXTRA_SEQUENTIAL, "1")
                    is StartSpec.Single -> {
                        putExtra(WalkService.EXTRA_LAT, spec.start.lat)
                        putExtra(WalkService.EXTRA_LNG, spec.start.lng)
                        putExtra(WalkService.EXTRA_SPACING_STR, spec.spacingM.toString())
                    }
                }
            },
        )
    }

    private fun control(action: String) =
        startService(Intent(this, WalkService::class.java).apply { this.action = action })

    private fun requestPerms() {
        val needed = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) needed += Manifest.permission.POST_NOTIFICATIONS
        val missing = needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onResume() {
        super.onResume()
        // Fire the boot autostart once, from the FGS-eligible resumed state (Android 14+).
        if (pendingAutostart) {
            pendingAutostart = false
            autostart()
        }
    }

    /** Persist the picker selection so a boot autostart replays the same walk. */
    private fun persistSelection(selectedPosition: Int, durationMin: Long, speedMps: Double) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SELECTION, selectedPosition)
            .putLong(KEY_DURATION_MIN, durationMin)
            .putFloat(KEY_SPEED_MPS, speedMps.toFloat())
            .apply()
    }

    /** Replay the last-used (or default) walk on boot. No-op if a walk already runs or location is denied. */
    private fun autostart() {
        if (WalkBus.status.value != WalkState.IDLE) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sendStart(
            autostartSpec(
                selectedPosition = prefs.getInt(KEY_SELECTION, 0),
                durationMin = prefs.getLong(KEY_DURATION_MIN, 60L),
                speedMps = prefs.getFloat(KEY_SPEED_MPS, 1.3f).toDouble(),
            ),
        )
    }

    companion object {
        /** Boolean intent extra set by the walksim-autostart Magisk boot module to auto-start the walk. */
        const val EXTRA_AUTOSTART = "autostart"
        private const val PREFS = "walksim_prefs"
        private const val KEY_SELECTION = "last_selection"
        private const val KEY_DURATION_MIN = "last_duration_min"
        private const val KEY_SPEED_MPS = "last_speed_mps"
    }
}
