package com.pikmin.walksim

import android.Manifest
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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Petal Pop splash (soft-pink window + garden sprout) via androidx.core:core-splashscreen; must run
        // before super.onCreate so the compat splash installs. Cosmetic only — no walk behaviour is touched.
        installSplashScreen()
        super.onCreate(savedInstanceState)
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
}
