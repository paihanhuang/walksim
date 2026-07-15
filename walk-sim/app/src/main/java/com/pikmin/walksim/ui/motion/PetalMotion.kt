package com.pikmin.walksim.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pikmin.walksim.WalkState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Stage-3 "springy motion" layer for [WalkScreen] (Plan B). Every composable here is a **cosmetic overlay on
 * immediate state**: it only reads [WalkViewState]-derived values and animates them. None of it gates a control
 * action — the START/PAUSE/RESUME/STOP `onClick`s still fire the intent synchronously (AC-20), so the squish is
 * a *reaction* to the press, never a precondition of it.
 *
 * Two pure seams ([progressFraction], [isWalkCompletion]) live Compose-free in `PetalMotionLogic.kt` (so the
 * plain JVM can load + unit-test them — a Compose-compiled class like this one can't be loaded off-device); the
 * visuals below are proven by `@Preview` + the queued on-device visual check.
 */

// ---- Cosmetic motion ----

/**
 * A spring press-squish: while [interactionSource] is pressed the target scales to 0.92 and springs back on
 * release. Applied via a `graphicsLayer` block (draw-phase only — no recomposition per frame). Pass the SAME
 * `interactionSource` the `Button` uses so the squish tracks the real press; the button's `onClick` is untouched.
 */
@Composable
fun Modifier.pressSquish(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pressSquish",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A little sprout that bobs vertically. The [rememberInfiniteTransition] is composed **only** in the RUNNING
 * branch, so PAUSED/IDLE/STOPPED dispose it (no frame subscription → battery-safe). Callers show this only while
 * a walk is active; PAUSED keeps it on-screen but frozen.
 */
@Composable
fun BobbingSprout(status: WalkState, modifier: Modifier = Modifier) {
    val bobDy = if (status == WalkState.RUNNING) {
        val transition = rememberInfiniteTransition(label = "sproutBob")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = -6f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "sproutBobDy",
        ).value
    } else {
        0f
    }
    Text("🌱", fontSize = 26.sp, modifier = modifier.offset(y = bobDy.dp))
}

/**
 * HUD progress rendered as a row of petals that bloom left-to-right as [fraction] (from [progressFraction])
 * rises — the fill is animated with a short spring-less tween. A bud is the pale STOP-pink; a full bloom is the
 * START-red, larger. Purely a display of progress; no control coupling.
 */
@Composable
fun PetalProgress(fraction: Float, modifier: Modifier = Modifier, petals: Int = 10) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "petalFill",
    )
    val bloom = Color(PetalTokens.START)
    val bud = Color(PetalTokens.STOP).copy(alpha = 0.30f)
    Canvas(modifier.height(16.dp)) {
        val slot = size.width / petals
        for (i in 0 until petals) {
            val t = (animated * petals - i).coerceIn(0f, 1f) // 0 = bud, 1 = fully bloomed
            val w = slot * 0.55f * (0.6f + 0.4f * t)
            val h = size.height * (0.55f + 0.45f * t)
            val cx = slot * (i + 0.5f)
            val cy = size.height / 2f
            drawOval(
                color = lerp(bud, bloom, t),
                topLeft = Offset(cx - w / 2f, cy - h / 2f),
                size = Size(w, h),
            )
        }
    }
}

/**
 * A one-shot petal burst fired when a walk completes ([isWalkCompletion] flips true). Eight petals radiate out
 * and fade over ~900 ms, then nothing draws (no lingering animation). The overlay has no pointer input, so it
 * never intercepts a tap — controls stay responsive during the burst.
 */
@Composable
fun CompletionBurst(status: WalkState, modifier: Modifier = Modifier) {
    var burstId by remember { mutableIntStateOf(0) }
    var previous by remember { mutableStateOf(status) }
    LaunchedEffect(status) {
        if (isWalkCompletion(previous, status)) burstId++
        previous = status
    }
    // Positive `if` blocks (no early `return`/`return@key`): a non-local return out of the @Composable inline
    // `key { }` lambda can't be represented in dex (`$$$$$NON_LOCAL_RETURN$$$$$`), so guard with nesting instead.
    if (burstId > 0) {
        key(burstId) {
            val progress = remember { Animatable(0f) }
            LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing)) }
            val p = progress.value
            if (p < 1f) {
                val petal = Color(PetalTokens.STOP)
                Canvas(modifier) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val reach = size.minDimension * 0.32f * p
                    val radius = 10.dp.toPx() * (1f - 0.35f * p)
                    val count = 8
                    for (i in 0 until count) {
                        val angle = (2.0 * PI * i / count).toFloat()
                        drawCircle(
                            color = petal.copy(alpha = 1f - p),
                            radius = radius,
                            center = Offset(cx + cos(angle) * reach, cy + sin(angle) * reach),
                        )
                    }
                }
            }
        }
    }
}

// --- Preview (Stage-3 quality proof; captured visually, not by the headless build) ---

@Preview(showBackground = true, name = "petal progress", widthDp = 240)
@Composable
private fun PetalProgressPreview() = WalkSimTheme {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PetalProgress(0f, Modifier.fillMaxWidth())
        PetalProgress(0.4f, Modifier.fillMaxWidth())
        PetalProgress(1f, Modifier.fillMaxWidth())
    }
}
