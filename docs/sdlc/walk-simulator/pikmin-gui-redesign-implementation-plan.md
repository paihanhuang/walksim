# Pikmin GUI Redesign (Plan B) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild walk-sim's UI in the "Petal Pop" Pikmin style using Jetpack Compose, plus a restyled notification, app icon, and splash — every control as intuitive as today and every behavior identical.

**Architecture:** UI layer only. The behavior-critical logic (control enablement, START-intent extras, HUD formatting, preset→duration) is extracted into **pure JVM-testable functions** so parity with today is provable without a device; the Compose composables are thin renderers over those functions and over `WalkBus` flows. `WalkService`, `WalkBus`, all intents, `LocationInjector`, `PaceProvider`, and every `:core-*` module are untouched.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), `activity-compose`, osmdroid via `AndroidView`, coroutines/Flow, Android SDK, Gradle.

## Global Constraints

Every task's requirements implicitly include this section. Values copied verbatim from `docs/sdlc/walk-simulator/pikmin-gui-redesign-plan.md`.

- **Runs AFTER Plan A** — do not start Plan B until every Plan A stage has passed its 3-QA gate.
- **3-QA gate, hard rule:** no stage N+1 until stage N is approved by **all three** QA roles (qa-function, qa-quality, qa-principle). A single FAIL blocks the stage; route to the engineer, fix, re-verify, re-run all three.
- **Subagent model:** engineer + QA subagents on **Opus 4.8** (`model: "opus"`). Orchestration in the main session.
- **UI layer only:** no change to `WalkService`/session/core logic, `WalkBus`, `EXTRA_*` intents, `LocationInjector`, or the Pace IPC. (Exception: Stage 4 edits only the cosmetic notification builder inside the service.)
- **Behavior frozen:** controls stay functionally 1:1 (AC-16/20/21/22/23 preserved). No new features/controls/settings.
- **Original art only** — no Nintendo/Niantic characters, branding, or Pikmin Bloom UI copies.
- **Palette (hex):** Start `#F0554E`, Pause `#FFC531`, Resume `#4FA3FF`, Stop `#FF8FB1`, Map `#8FD3B6`, Surface `#FFF5F8`, Text `#7A4A5E`. **Light theme only.**
- **Proof artifacts** under `docs/sdlc/walk-simulator/proofs/`, existing naming convention.
- **Environment:** macOS/zsh, absolute paths, root `/Users/davidhuang/Projects/pikmin-remote-control/walk-sim`. Not a git repo — `git commit` steps are optional (presuppose `git init`); the gate record is the proof + 3-QA verdicts.
- **Test conventions:** match existing modules. JVM logic tests are the primary parity proof; Compose visuals proven by `@Preview` + on-device smoke (no heavy instrumented Compose harness unless a gate demands it).

---

## File Structure

**Stage 0 — Compose enablement + tokens:**
- Modify `app/build.gradle.kts` (Compose deps + `buildFeatures { compose = true }`), `gradle/libs.versions.toml` (versions).
- Create `app/src/main/java/com/pikmin/walksim/ui/PetalTokens.kt` (pure hex constants), `ui/Theme.kt` (Compose theme).
- Create `app/src/test/java/com/pikmin/walksim/ui/PetalTokensTest.kt`.

**Stage 1 — Static Compose main screen (behavior-critical):**
- Create `ui/WalkUiLogic.kt` (pure: `controlsFor`, `startSpec`, `durationForSelection`, `formatHud`, `bannerText`).
- Create `ui/WalkScreen.kt` (the composable), `ui/WalkViewState.kt` (immutable UI state from `WalkBus`).
- Modify `MainActivity.kt` (host Compose, remove `LinearLayout`, keep permissions + intent sending + `WalkBus` collection).
- Create `app/src/test/java/com/pikmin/walksim/ui/WalkUiLogicTest.kt`.

**Stage 2 — Map:**
- Create `ui/WalkMap.kt` (osmdroid `AndroidView` + pin pick + flower marker + petal trail).
- Create `ui/PinMath.kt` (pure pin-selection helper) + test.

**Stage 3 — Motion:** Create `ui/motion/PetalMotion.kt`; modify `WalkScreen.kt`.

**Stage 4 — Notification:** Modify `WalkService.kt` (`buildNotification` only); add `res/drawable/ic_sprout.xml`.

**Stage 5 — Icon + splash:** Create `res/mipmap-anydpi-v26/ic_launcher.xml` (+ foreground/background drawables), `res/values/themes.xml` + `res/values/colors.xml` (splash), modify `AndroidManifest.xml` (icon + splash theme).

---

## Stage 0 — Compose enablement + design tokens

*No screen change yet. Establishes the build + theme + previews.*

### Task 0.1: Enable Compose in `:app`

**Files:** Modify `app/build.gradle.kts`, `gradle/libs.versions.toml`

- [ ] **Step 1: Add versions** to `libs.versions.toml` — `composeBom`, `activityCompose`, and the Kotlin compose compiler matching the project's Kotlin version (from `libs.versions.toml`).

- [ ] **Step 2: Wire the module** — in `app/build.gradle.kts` add:

```kotlin
android {
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get() }
}
dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

- [ ] **Step 3: Verify it builds**

Run: `cd /Users/davidhuang/Projects/pikmin-remote-control/walk-sim && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (no UI change yet).

### Task 0.2: Design tokens + theme

**Files:** Create `ui/PetalTokens.kt`, `ui/Theme.kt`, test `ui/PetalTokensTest.kt`

**Interfaces:**
- Produces: `PetalTokens` (`START`, `PAUSE`, `RESUME`, `STOP`, `MAP`, `SURFACE`, `TEXT` as `Int` ARGB), `WalkSimTheme { }` composable.

- [ ] **Step 1: Write the failing token test**

```kotlin
package com.pikmin.walksim.ui
import kotlin.test.Test
import kotlin.test.assertEquals
class PetalTokensTest {
    @Test fun paletteMatchesSpec() {
        assertEquals(0xFFF0554E.toInt(), PetalTokens.START)
        assertEquals(0xFFFFC531.toInt(), PetalTokens.PAUSE)
        assertEquals(0xFF4FA3FF.toInt(), PetalTokens.RESUME)
        assertEquals(0xFFFF8FB1.toInt(), PetalTokens.STOP)
        assertEquals(0xFF8FD3B6.toInt(), PetalTokens.MAP)
        assertEquals(0xFFFFF5F8.toInt(), PetalTokens.SURFACE)
        assertEquals(0xFF7A4A5E.toInt(), PetalTokens.TEXT)
    }
}
```

- [ ] **Step 2: Run → FAIL.** `./gradlew :app:testDebugUnitTest --tests com.pikmin.walksim.ui.PetalTokensTest`

- [ ] **Step 3: Implement tokens + theme**

```kotlin
// PetalTokens.kt — pure JVM constants (no Compose import), so they are unit-testable.
package com.pikmin.walksim.ui
object PetalTokens {
    const val START = 0xFFF0554E.toInt(); const val PAUSE = 0xFFFFC531.toInt()
    const val RESUME = 0xFF4FA3FF.toInt(); const val STOP = 0xFFFF8FB1.toInt()
    const val MAP = 0xFF8FD3B6.toInt(); const val SURFACE = 0xFFFFF5F8.toInt(); const val TEXT = 0xFF7A4A5E.toInt()
}
```
```kotlin
// Theme.kt
package com.pikmin.walksim.ui
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
private val scheme = lightColorScheme(
    primary = Color(PetalTokens.START), surface = Color(PetalTokens.SURFACE),
    onSurface = Color(PetalTokens.TEXT), background = Color.White,
)
@Composable fun WalkSimTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, shapes = Shapes(extraLarge = RoundedCornerShape(28.dp)), content = content)
```

- [ ] **Step 4: Run → PASS.** Record `walk-sim_gui-s0_function_pass_tokens.xml`.

- [ ] **Step 5: Preview proof** — add a `@Preview` swatch composable; capture the Android Studio preview (or `assembleDebug` + on-device) as `walk-sim_gui-s0_quality_pass_theme-preview.png`.

### Task 0.3: Stage 0 — 3-QA GATE

- [ ] **qa-function:** token test passes; app still builds + launches unchanged (Compose present but UI not swapped).
- [ ] **qa-quality:** dependency set is minimal (BOM + ui + material3 + activity-compose + tooling); no version conflict; APK delta noted + accepted.
- [ ] **qa-principle:** no screen/behavior change yet; tokens are pure; no IP.
- [ ] All three PASS → Stage 1 may start.

---

## Stage 1 — Static Compose main screen (behavior-critical)

*The parity-proving stage. Extract pure UI logic, JVM-test it, then render it in Compose and delete the `LinearLayout`.*

### Task 1.1: Extract pure UI logic + tests

**Files:** Create `ui/WalkUiLogic.kt`, test `ui/WalkUiLogicTest.kt`

**Interfaces:**
- Consumes: `WalkState`, `PRESET_LOCATIONS`, `presetDurationMinutes`, `WalkProfile`, `SimSample`, `LatLng`.
- Produces:
  - `data class Controls(start,pause,resume,stop: Boolean)`; `fun controlsFor(state: WalkState): Controls`
  - `sealed interface StartSpec { data class Sequential(durationS: Long, speedMps: Double); data class Single(durationS: Long, speedMps: Double, start: LatLng, spacingM: Double) }`; `fun startSpec(selectedPosition: Int, startPin: LatLng, durationMin: Long, speedMps: Double): StartSpec`
  - `fun durationForSelection(selectedPosition: Int, speedMps: Double): Long?`
  - `data class Hud(line1: String, line2: String); fun formatHud(sample: SimSample?, durationS: Long): Hud`
  - `fun bannerText(mockAppOk: Boolean, setupError: String?): String?`

- [ ] **Step 1: Write the failing parity tests** (mirror today's `MainActivity` behavior exactly)

```kotlin
package com.pikmin.walksim.ui
import com.pikmin.model.LatLng
import com.pikmin.walksim.WalkState
import kotlin.test.*
class WalkUiLogicTest {
    @Test fun controlsPerState() {
        assertEquals(Controls(true,false,false,false), controlsFor(WalkState.IDLE))
        assertEquals(Controls(false,true,false,true), controlsFor(WalkState.RUNNING))
        assertEquals(Controls(false,false,true,true), controlsFor(WalkState.PAUSED))
        assertEquals(Controls(false,false,false,false), controlsFor(WalkState.STOPPED))
    }
    @Test fun sequentialWhenPositionZero() {
        val s = startSpec(0, LatLng(35.0,139.0), durationMin = 60, speedMps = 1.3)
        assertEquals(StartSpec.Sequential(3600, 1.3), s)
    }
    @Test fun singleCarriesPresetSpacing() {
        val s = startSpec(1, LatLng(35.6595,139.7006), 10, 1.3) as StartSpec.Single
        assertEquals(3600L, s.durationS); assertEquals(500.0, s.spacingM) // Shibuya preset = 500 m
    }
    @Test fun presetSelectionSetsDuration() {
        assertNull(durationForSelection(0, 1.3))
        assertEquals(presetMinutesForShibuya(), durationForSelection(1, 1.3)) // == presetDurationMinutes(10.0,1.3)
    }
    @Test fun bannerPriority() {
        assertTrue(bannerText(false, null)!!.contains("mock"))
        assertEquals("boom", bannerText(true, "boom"))
        assertNull(bannerText(true, null))
    }
}
```

- [ ] **Step 2: Run → FAIL.** `./gradlew :app:testDebugUnitTest --tests com.pikmin.walksim.ui.WalkUiLogicTest`

- [ ] **Step 3: Implement `WalkUiLogic.kt`** — `controlsFor` mirrors `renderControls` (`MainActivity.kt:213-219`); `startSpec` mirrors the START `onClick` branch (`:183-195`: position 0 → sequential extras; else lat/lng + `preset.spacingM`); `durationForSelection` mirrors the spinner listener (`:162-165`); `formatHud` mirrors `renderHud` (`:221-230`); `bannerText` mirrors `refreshBanner` (`:232-240`).

- [ ] **Step 4: Run → PASS.** Record `walk-sim_gui-s1_function_pass_ui-logic-parity.xml`.

### Task 1.2: Compose main screen wired to `WalkBus`

**Files:** Create `ui/WalkViewState.kt`, `ui/WalkScreen.kt`; modify `MainActivity.kt`

**Interfaces:**
- Consumes: `WalkUiLogic`, `WalkBus` (`sample`, `status`, `mockAppOk`, `setupError`, `durationS`), `WalkService` action/extra constants, `PRESET_LOCATIONS`.
- Produces: `@Composable fun WalkScreen(state: WalkViewState, onStart, onPause, onResume, onStop, onPick, onSelectPreset, onEditDuration, onEditPace)`.

- [ ] **Step 1: Build `WalkScreen`** — a `Column` with: banner (from `bannerText`), location dropdown (`ExposedDropdownMenuBox` over `"All areas" + PRESET_LOCATIONS.map{label}`), a map slot (placeholder `Box(Modifier.background(Color(PetalTokens.MAP)))` until Stage 2), start-pin label, duration + pace `OutlinedTextField`s, the big **START** `Button` (full-width pill, `enabled = controls.start`), a `Row` of PAUSE/RESUME/STOP (`enabled` per `controls`), and the HUD `Card` (from `formatHud`). Colors from `PetalTokens`.

- [ ] **Step 2: Host it in `MainActivity`** — replace `setContentView(buildLayout())` with `setContent { WalkSimTheme { WalkScreen(state = collectedState, onStart = { startWalk() }, …) } }`. Keep `onCreate` permission logic, `WalkBus` collection (now via `collectAsStateWithLifecycle`), and the intent-sending helpers. The `onStart` callback builds the real `Intent` from `startSpec(...)` (the only Android-specific glue). **Delete** `buildLayout`, the `View` fields, and the imperative wiring; keep `control(action)`/`requestPerms`.

- [ ] **Step 3: Build + existing suite green**

Run: `cd /Users/davidhuang/Projects/pikmin-remote-control/walk-sim && ./gradlew test`
Expected: BUILD SUCCESSFUL; all existing tests untouched + green; new `WalkUiLogicTest` green.

- [ ] **Step 4: Preview** — `@Preview` composables for idle / running / paused states; capture `walk-sim_gui-s1_quality_pass_states-preview.png`.

- [ ] **Step 5: On-device smoke** — a real walk started from the Compose screen still injects + credits distance/steps; pause/resume/stop work within 1 s. Proof `walk-sim_gui-s1_function_pass_s1-ondevice.md`.

### Task 1.3: Stage 1 — 3-QA GATE

- [ ] **qa-function:** control parity (JVM tests) + START sends the same extras as today (assert in the parity test / on-device); banners show per state; on-device walk credits distance + steps.
- [ ] **qa-quality:** no recomposition storms (state hoisted, stable params); no perf regression; deps unchanged from Stage 0.
- [ ] **qa-principle:** logic is UI-only relocation into pure functions; no behavior added; `LinearLayout` fully removed (no dead view code); no IP.
- [ ] All three PASS → Stage 2 may start.

---

## Stage 2 — Map integration

### Task 2.1: Pure pin-selection helper + test

**Files:** Create `ui/PinMath.kt`, test `ui/PinMathTest.kt`

**Interfaces:** Produces `fun pickedStart(tapLat, tapLng): LatLng` (trivial passthrough today, but the seam lets the map glue stay dumb) and `fun presetCenter(position: Int): LatLng` (position 0 → first preset, per `MainActivity.kt:158`).

- [ ] **Step 1–4:** TDD `presetCenter(0) == PRESET_LOCATIONS.first().at`, `presetCenter(1) == PRESET_LOCATIONS[0].at`; implement; PASS; record proof.

### Task 2.2: osmdroid `AndroidView` wrapper

**Files:** Create `ui/WalkMap.kt`; modify `WalkScreen.kt` (replace the Stage-1 map placeholder)

- [ ] **Step 1: Implement `WalkMap`** — `AndroidView` whose `factory` builds the `MapView` exactly as `MainActivity.setupMap()` does today (MAPNIK, zoom 16, `Marker` draggable, `MapEventsOverlay` tap) and calls `onPick(LatLng(...))` on tap/drag-end; `update` recenters the marker to `state.startPin`. A flower marker icon + a petal-trail polyline (from recent `WalkBus.sample` positions) are drawn as overlays. **Pin picking behavior is preserved 1:1.**

- [ ] **Step 2: Wire** — `WalkScreen` passes `onPick = { viewModelStartPin = it }`; preset selection calls `map.controller.animateTo(presetCenter(pos))`.

- [ ] **Step 3: Build + on-device** — tap + drag still move the start pin; preset select recenters; a running walk shows the petal trail. Proof `walk-sim_gui-s2_function_pass_map.md` + screenshot.

### Task 2.3: Stage 2 — 3-QA GATE
- [ ] **qa-function:** pin tap/drag + preset recenter identical to today; trail is cosmetic (no behavior change).
- [ ] **qa-quality:** map hosted once (no leak across recompositions — `factory` runs once, `update` cheap); no ANR.
- [ ] **qa-principle:** osmdroid glue confined to `WalkMap.kt`; trail overlay is the only (cosmetic) addition, matching the spec's flagged item.
- [ ] All three PASS → Stage 3 may start.

---

## Stage 3 — Motion pass

### Task 3.1: Springy interactions

**Files:** Create `ui/motion/PetalMotion.kt`; modify `WalkScreen.kt`

- [ ] **Step 1:** Add `Modifier.pressSquish()` (a `scale` `animateFloatAsState` on press via `interactionSource`), a bobbing sprout (`rememberInfiniteTransition` vertical offset, active only while `state.status == RUNNING`, still when `PAUSED`), and petal-progress that animates fill from `state.progressPct`. Completion → a one-shot petal-burst.
- [ ] **Step 2: Guard AC-20** — animations are cosmetic; the underlying control action fires immediately on click (no animation gate). Add a note + a quick on-device check that stop takes effect within 1 s.
- [ ] **Step 3: On-device** — capture a short screen recording / frames as `walk-sim_gui-s3_quality_pass_motion.md`.

### Task 3.2: Stage 3 — 3-QA GATE
- [ ] **qa-function:** controls still effective within 1 s (AC-20) despite animation; states visually correct.
- [ ] **qa-quality:** animations run on the compose frame clock (no busy loops); no dropped-frame jank on the target device; battery-safe (infinite transition only while visible + running).
- [ ] **qa-principle:** motion is additive/cosmetic; no logic touched.
- [ ] All three PASS → Stage 4 may start.

---

## Stage 4 — Notification restyle

### Task 4.1: Petal Pop notification

**Files:** Create `res/drawable/ic_sprout.xml` (monochrome vector); modify `WalkService.buildNotification` (the slimmed service from Plan A) only.

- [ ] **Step 1:** Swap `setSmallIcon(android.R.drawable.ic_menu_mylocation)` → `R.drawable.ic_sprout`; add `.setColor(0xFFF0554E.toInt())` + `setColorized(true)`; update the title/copy to the playful strings ("🌸 Strolling…"); keep the existing progress text + `PAUSE`/`STOP` actions **unchanged in behavior**.
- [ ] **Step 2:** Build; on-device — the ongoing notification shows the sprout + pink accent + actions still pause/stop. Proof `walk-sim_gui-s4_function_pass_notification.png`.

### Task 4.2: Stage 4 — 3-QA GATE
- [ ] **qa-function:** notification actions still pause/stop; progress text intact.
- [ ] **qa-quality:** no channel/import regressions; icon is a small monochrome vector (renders correctly in the status bar).
- [ ] **qa-principle:** only the notification builder's cosmetics changed; no service logic touched.
- [ ] All three PASS → Stage 5 may start.

---

## Stage 5 — App icon (Sprout Pin) + splash

### Task 5.1: Adaptive launcher icon

**Files:** Create `res/drawable/ic_launcher_foreground.xml` (sprout-in-pin), `res/drawable/ic_launcher_background.xml` (pink gradient), `res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`; modify `AndroidManifest.xml` (`android:icon`/`roundIcon`).

- [ ] **Step 1:** Author the vector foreground (Sprout Pin) + background; wire the adaptive icon; set it in the manifest.
- [ ] **Step 2:** Build + install; confirm the launcher shows Sprout Pin. Proof `walk-sim_gui-s5_function_pass_icon.png`.

### Task 5.2: Splash screen

**Files:** Create `res/values/themes.xml` + `res/values/colors.xml`; modify `AndroidManifest.xml` (apply the splash theme); add `androidx.core:core-splashscreen` if targeting the compat API.

- [ ] **Step 1:** Configure the Android-12 splash (window background = soft pink, splash icon = sprout). Optional branded wordmark variant.
- [ ] **Step 2:** Build + launch; capture the splash. Proof `walk-sim_gui-s5_function_pass_splash.png`.

### Task 5.3: Stage 5 — 3-QA GATE
- [ ] **qa-function:** app launches; icon + splash render on the target device; no launch regression.
- [ ] **qa-quality:** icon vectors are clean/scalable; splash adds no measurable cold-start delay.
- [ ] **qa-principle:** original art (no IP); resources are the only additions.
- [ ] All three PASS → Plan B complete.

---

## Self-Review

**Spec coverage:** §2 design system → Stage 0 tokens/theme; §3.1 main screen → Stages 1–3; §3.2 notification → Stage 4; §3.3 icon + §3.4 splash → Stage 5; §4 technical approach (Compose UI-only, osmdroid `AndroidView`, untouched service/IPC) → Stages 0–2 + the global constraints; §6 testing (control-parity + behavior-preservation JVM tests, previews, on-device) → Tasks 1.1/1.2/2.1 + each gate; §7 governance (staged, 3-QA, after Plan A) → global constraints + every stage gate.

**Placeholder scan:** the `presetMinutesForShibuya()` reference in the Task 1.1 test is shorthand for `presetDurationMinutes(10.0, 1.3)` — the engineer inlines the literal; every other step shows real code or an exact `file:line` anchor. Compose preview screenshots are visual proofs (the correct idiom for look-and-feel), not placeholders. Resource-authoring steps (icon/splash vectors) describe the exact files + manifest keys.

**Type consistency:** `Controls(start,pause,resume,stop)` used identically in 1.1 and 1.2. `StartSpec.Sequential/Single` fields (`durationS`, `speedMps`, `start`, `spacingM`) consistent across the test and `WalkScreen`'s `onStart` glue. `PetalTokens.*` (Int ARGB) produced in 0.2, consumed by `Theme.kt` and `WalkScreen`. `WalkBus` field names (`sample`, `status`, `mockAppOk`, `setupError`, `durationS`) match the real `WalkBus`.

---

## Execution Handoff

Do **not** begin execution until Plan A is complete (all stages 3-QA-passed) and the user green-lights Plan B. When executing:

- **Subagent-Driven (recommended):** one fresh Opus-4.8 subagent per task, two-stage review between tasks, and the **3-QA gate as a hard stop between stages**.
- Stage 1 is the behavior-critical gate — its JVM parity tests must prove control + intent equivalence with today before any visual polish proceeds.
