package com.pikmin.walksim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pikmin.model.LatLng
import com.pikmin.model.SimSample
import com.pikmin.walksim.PRESET_LOCATIONS
import com.pikmin.walksim.WalkState

/**
 * The "Petal Pop" main screen (Plan B, Stage 1) — a stateless renderer over [WalkViewState] and the pure
 * [WalkUiLogic] functions. `MainActivity` hoists all state (WalkBus flows + picker fields) and owns the only
 * Android glue (permission check + START intent). Behavior is 1:1 with the old `LinearLayout` screen; the map
 * is a Stage-2 placeholder here.
 *
 * @param onPick placeholder this stage (map drag/tap → new pin lands in Stage 2); the seam is kept so the
 *   picker state stays wired.
 */
@Composable
fun WalkScreen(
    state: WalkViewState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onPick: (LatLng) -> Unit,
    onSelectPreset: (Int) -> Unit,
    onEditDuration: (String) -> Unit,
    onEditPace: (String) -> Unit,
) {
    val controls = controlsFor(state.status)
    val banner = state.permissionHint ?: bannerText(state.mockAppOk, state.setupError)
    val hud = formatHud(state.sample, state.durationS)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(PetalTokens.SURFACE))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (banner != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF3CD))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(banner, color = Color(0xFF7A5C00))
            }
        }

        LocationDropdown(selectedPosition = state.selectedPosition, onSelectPreset = onSelectPreset)

        // Map slot — the osmdroid AndroidView, with the one-shot completion petal-burst overlaid on its centre.
        // Weighted so it fills the same space the Stage-1 placeholder did. clipToBounds is REQUIRED: the hosted
        // osmdroid MapView paints its tile canvas past its View bounds, so without clipping it draws over the
        // location dropdown above (hiding the area picker) and the start-pin label below.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds(),
        ) {
            WalkMap(
                startPin = state.startPin,
                selectedPosition = state.selectedPosition,
                sample = state.sample,
                onPick = onPick,
                modifier = Modifier.fillMaxSize(),
            )
            CompletionBurst(state.status, Modifier.fillMaxSize())
        }

        Text(
            "start pin: %.5f, %.5f".format(state.startPin.lat, state.startPin.lng),
            color = Color(PetalTokens.TEXT),
        )

        OutlinedTextField(
            value = state.durationMin,
            onValueChange = onEditDuration,
            label = { Text("duration (min)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.paceMps,
            onValueChange = onEditPace,
            label = { Text("pace (m/s)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        val startInteraction = remember { MutableInteractionSource() }
        Button(
            onClick = onStart,
            enabled = controls.start,
            interactionSource = startInteraction,
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(PetalTokens.START),
                contentColor = Color.White,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .pressSquish(startInteraction),
        ) {
            Text("START", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlButton("PAUSE", PetalTokens.PAUSE, controls.pause, onPause, Modifier.weight(1f))
            ControlButton("RESUME", PetalTokens.RESUME, controls.resume, onResume, Modifier.weight(1f))
            ControlButton("STOP", PetalTokens.STOP, controls.stop, onStop, Modifier.weight(1f))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Bobbing sprout: on-screen only while a walk is active, and it bobs only while RUNNING
                    // (frozen when PAUSED) — so no infinite animation runs at idle/stopped.
                    if (state.status == WalkState.RUNNING || state.status == WalkState.PAUSED) {
                        BobbingSprout(state.status)
                    }
                    Column {
                        Text(hud.line1, color = Color(PetalTokens.TEXT))
                        if (hud.line2.isNotEmpty()) Text(hud.line2, color = Color(PetalTokens.TEXT))
                    }
                }
                // Petal progress fills as the walk advances; only meaningful once samples arrive.
                if (state.sample != null) {
                    PetalProgress(progressFraction(state.sample, state.durationS), Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** One pastel transport button of the PAUSE/RESUME/STOP row. */
@Composable
private fun ControlButton(
    label: String,
    argb: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(argb),
            contentColor = Color(PetalTokens.TEXT),
        ),
        modifier = modifier.pressSquish(interaction),
    ) {
        Text(label)
    }
}

/** The location picker — "All areas (sequential)" followed by every [PRESET_LOCATIONS] label. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationDropdown(selectedPosition: Int, onSelectPreset: (Int) -> Unit) {
    val options = remember { listOf("All areas (sequential)") + PRESET_LOCATIONS.map { it.label } }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options[selectedPosition],
            onValueChange = {},
            readOnly = true,
            label = { Text("location") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelectPreset(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

// --- Previews (Stage 1 quality proof; captured visually in Android Studio, not by the headless build) ---

private val previewSample = SimSample(
    pos = LatLng(35.6595, 139.7006), bearingDeg = 90f, speedMps = 1.3f, accuracyM = 5f,
    stepCount = 1234, tickIndex = 359, cumulativeDistanceM = 468.0,
)

@Preview(showBackground = true, name = "idle", heightDp = 780)
@Composable
private fun WalkScreenIdlePreview() = PreviewWalkScreen(WalkState.IDLE, null)

@Preview(showBackground = true, name = "running", heightDp = 780)
@Composable
private fun WalkScreenRunningPreview() = PreviewWalkScreen(WalkState.RUNNING, previewSample)

@Preview(showBackground = true, name = "paused", heightDp = 780)
@Composable
private fun WalkScreenPausedPreview() = PreviewWalkScreen(WalkState.PAUSED, previewSample)

@Composable
private fun PreviewWalkScreen(status: WalkState, sample: SimSample?) {
    WalkSimTheme {
        WalkScreen(
            state = WalkViewState(
                status = status,
                sample = sample,
                durationS = 3600L,
                mockAppOk = true,
                setupError = null,
                selectedPosition = if (status == WalkState.IDLE) 0 else 1,
                startPin = LatLng(35.6595, 139.7006),
                durationMin = "60",
                paceMps = "1.3",
                permissionHint = null,
            ),
            onStart = {}, onPause = {}, onResume = {}, onStop = {},
            onPick = {}, onSelectPreset = {}, onEditDuration = {}, onEditPace = {},
        )
    }
}
