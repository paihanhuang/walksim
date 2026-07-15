# Pikmin-Style GUI Redesign — walk-sim (Plan B)

**Tier:** Big · **Date:** 2026-07-14 · **Owner:** orchestrator (architect path)
**Goal:** Rebuild walk-sim's UI in a playful "Petal Pop" Pikmin-inspired style using Jetpack Compose — every control as intuitive as today, every behavior identical.
**Constraint:** Executes **after** Plan A (architecture optimization) completes. **UI layer only** — no change to `WalkService`/core logic, intents, `WalkBus`, or the Pace IPC. **Original garden art, not Nintendo/Niantic IP.** Same staged, 3-QA-gated governance as Plan A.

> Plan B runs on the hardened baseline Plan A produces. It touches `MainActivity` (→ Compose), the foreground-service notification, the launcher icon, and the splash — **not** the engine, session orchestration, or step module. The only file it shares with Plan A is `MainActivity`, which Plan A does not modify, so there is no conflict.

---

## 1. Scope decisions (confirmed with the user, 2026-07-14)

| Question | Decision |
|---|---|
| Aesthetic direction | **Petal Pop** — bright white with the four Pikmin button colors; bubbly, playful, toy-like. |
| Build approach | **Jetpack Compose rebuild** of the UI layer (not a theme reskin) — for genuinely bubbly components + springy animation. |
| Surfaces in scope | **Main control screen + FGS notification + app icon + splash.** |
| App icon concept | **Sprout Pin** — a map-pin with a sprout ("walk + place"). |
| Theme | **Light only** (no dark variant in v1). |

---

## 2. Design system

**Palette (role → hex):**

| Role | Hex |
|---|---|
| Start / primary | `#F0554E` (red) |
| Pause | `#FFC531` (yellow) |
| Resume | `#4FA3FF` (blue) |
| Stop | `#FF8FB1` (pink) |
| Map | `#8FD3B6` (mint) |
| Surface / cards | `#FFF5F8` (soft pink) on `#FFFFFF` |
| Text | `#7A4A5E` (plum) / `#3A2A30` |
| Sprout accent | garden green (walking creature / leaf) |

**Typography:** a rounded, friendly typeface (system rounded, or one bundled font). Legibility first — the HUD numbers and banners must stay crisp.

**Component language:** one big full-width **primary pill** (START) with a chunky "press" drop-shadow; a **secondary pill row** (PAUSE·RESUME·STOP) that dims when not applicable; rounded **chips** (location picker), rounded **cards** (HUD), rounded **inputs** (duration/pace); a **flower map pin**; a **petal route trail**.

**Motion (Compose):** button press squish (spring scale); the sprout 🌱 bobs while walking and sits when paused; petal progress fills one 🌸 at a time; a petal-burst on completion. **Motion must never delay a control's effect** — AC-20 requires start/pause/resume/stop effective within 1 s, so animations are cosmetic overlays on immediate state changes.

---

## 3. Screen-by-screen design

### 3.1 Main control screen
Preserve **all** current controls and data **1:1** (nothing removed, nothing new):
- Location picker — "All areas (sequential)" + the 10 presets.
- osmdroid map with **tap-to-place** and **drag** pin; start-pin lat/lng label.
- Duration (min) + pace (m/s) inputs.
- START / PAUSE / RESUME / STOP.
- Live HUD — speed, distance, steps, elapsed, remaining, progress.
- Setup banners — not-mock-app (AC-16) and setup-error (AC-23).

**Only layout change:** START is promoted to a big primary pill; PAUSE/RESUME/STOP become a secondary row enabled per `WalkState` (mirrors today's `renderControls()`). Idle / running / paused states render from `WalkBus.status` + `WalkBus.sample`.

**Map:** osmdroid `MapView` hosted via Compose `AndroidView`; the existing pin logic (`MapEventsOverlay` tap, `Marker` drag → `setStart`) is preserved inside the factory/update lambdas. Flower marker + petal-trail overlay added.

### 3.2 Notification (foreground service)
Monochrome **sprout small-icon** + pink accent (`setColor`), playful title/copy ("🌸 Strolling through Shibuya"), live progress text, and PAUSE/STOP actions — within Android's notification-styling limits.

### 3.3 App icon — Sprout Pin
Adaptive launcher icon: foreground sprout-in-a-pin over a pink-gradient background; ship the monochrome + themed variants Android expects.

### 3.4 Splash
Android-12 splash-screen API: the sprout icon on a soft-pink background (optional branded "WalkSim" wordmark variant).

---

## 4. Technical approach

- **Compose rebuild of the UI layer only.** `MainActivity` becomes a `ComponentActivity` hosting a Compose tree; the imperative `LinearLayout` construction is removed.
- **Untouched:** `WalkService`, `WalkBus`, all `EXTRA_*` intents, `LocationInjector`, `PaceProvider`, and every `:core-*` module. The Compose UI **observes** `WalkBus` flows (`collectAsState`) and **sends the same intents** it does today.
- **Map interop:** osmdroid `MapView` via `AndroidView`; pin picking preserved in the factory/update lambdas.
- **Dependencies added (scoped to Plan B):** `androidx.activity:activity-compose`, the Compose BOM, `ui`, `material3`, `foundation`, `animation`; `buildFeatures { compose = true }` + the Kotlin compose compiler. This is the one real cost (APK size + build time) and is accepted for the redesign.
- **Light theme only;** current min SDK retained (Compose supports it).

---

## 5. Non-goals / constraints

- **No behavior change** — controls stay functionally 1:1; every AC-16/20/21/22/23 behavior preserved.
- **No new features** — no controls or settings the app lacks today.
- **No Nintendo/Niantic IP** — original art only.
- No dark theme, no localization, no tablet layout in v1.
- Does not reopen any Plan A out-of-scope item.

---

## 6. Testing strategy

- **Compose UI tests:** control presence + enablement per `WalkState` (idle/running/paused) mirrors `renderControls()`; banner visibility per `mockAppOk`/`setupError`; HUD renders each `SimSample` field.
- **Behavior-preservation tests:** the START intent carries the **same `EXTRA_*` extras** as today for each mode (All-areas / preset / custom pin); preset selection still sets duration = `routeLengthKm / speed` (reuses `presetDurationMinutes`).
- **Map interaction:** pin tap/drag updates the start pin (extract the pin-math into a pure helper for a JVM test; instrument the osmdroid glue).
- **Visual:** Compose `@Preview` screenshots for idle/walking/paused + the notification; on-device visual smoke.
- **Non-regression:** all existing core/service tests untouched + green; an on-device functional smoke — a real walk still credits distance + steps driven from the new UI.

---

## 7. Governance & suggested stages

Staged, each stage **3-QA-gated** (qa-function: controls/behavior 1:1 + tests; qa-quality: no jank, sane recomposition, no perf regression, dependency cost justified; qa-principle: UI-only, no logic drift, no IP, minimal deps). Engineer + QA subagents on **Opus 4.8**. **Executes after Plan A's stages all pass.**

Suggested stage breakdown (to be detailed by `writing-plans`):

| # | Stage | Deliverable |
|---|---|---|
| 0 | Compose enablement + design tokens | build config + theme/palette/typography/shapes + `@Preview`s; no screen swap yet |
| 1 | Static Compose main screen | all controls wired to `WalkBus`/intents, behavior 1:1, no animation, `LinearLayout` removed |
| 2 | Map integration | `AndroidView` + pin tap/drag + flower marker / petal trail |
| 3 | Motion pass | springy interactions, bobbing sprout, petal progress, completion burst |
| 4 | Notification restyle | sprout icon + pink accent + copy + actions |
| 5 | App icon (Sprout Pin) + splash | adaptive icon + splash screen |

Each stage is independently shippable and gated; Stage 1 is the behavior-critical one (its tests prove control parity with today).

---

## 8. Relationship to Plan A

Plan A hardens the engine/service/IPC and leaves the UI untouched; Plan B swaps the UI layer on top of that hardened base. Execute **A fully (all gates green) → then B**.

---

*Plan B design doc. Next: user review of this spec → `writing-plans` to produce the granular executable plan → staged, 3-QA-gated execution, after Plan A.*
