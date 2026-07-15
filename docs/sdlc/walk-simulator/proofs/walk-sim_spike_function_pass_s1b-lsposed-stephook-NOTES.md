# Spike S1b — Root LSPosed step-injection into Pikmin Bloom: ✅ CREDITED

- **Date:** 2026-06-30 · **Device:** Pixel XL (marlin), Android 10, Magisk 30.7 + Zygisk + LSPosed 1.9.2 (zygisk) · **Consumer:** Pikmin Bloom **v147.0** (throwaway account)
- **Goal:** prove root-injected steps are credited by Pikmin with the phone physically still.

## Result — ✅ WORKS
Pikmin's in-app step count climbs from injected `STEP_DETECTOR` events, phone motionless (user-confirmed). Pace tuned live 60 → 72 steps/min with no code path changes.

## How Pikmin v147 reads steps (key finding)
- Pikmin registers a **`STEP_DETECTOR` (type 18)** listener **in its own process** — `com.nianticproject.ichigo.fitness.FitnessManager$1` (Pikmin Bloom internal pkg = `com.nianticproject.ichigo`, "ichigo").
- **Detector-only injection is credited** — it does NOT require also satisfying GMS Activity Recognition; no "are you actually walking?" gate blocked it. **GMS scope NOT needed.**
- Pikmin used the step **DETECTOR** (type 18), not the step **COUNTER** (type 19), this session.
- (Confirms prior art: Health Connect / Google Fit are dead ends — Pikmin reads the raw sensor.)

## Mechanism (proven, reproducible)
- Magisk Zygisk + **LSPosed 1.9.2** → module `com.pikmin.stephook` (source: `pikmin2/spike-step-hook/`), enabled + scoped to `com.nianticlabs.pikmin` via the LSPosed Manager UI.
- Hook `android.hardware.SystemSensorManager#registerListenerImpl` (API 29 sig: `SensorEventListener, Sensor, int, Handler, int, int`) → capture the step listener → feed fabricated `SensorEvent`s (package-private `SensorEvent(int)` ctor via reflection; set `sensor/accuracy/timestamp/values[0]`). Tunable pace.
- Build: JDK 17 + AGP 8.7.3 + Gradle 8.9; iterate via `adb install -r` then `am force-stop` + relaunch Pikmin to reload the module code.

## Evidence
LSPosed-Bridge logcat:
```
StepHook: com.nianticlabs.pikmin registered step sensor type=18
StepInjector: feeding type=18 -> com.nianticproject.ichigo.fitness.FitnessManager$1
```
+ user-confirmed in-app step-count climb, phone still.

## Implications for the build
Step injection is now a **solved mechanism**. Production work: tie injected step **pace to the walk-sim's distance/speed** (steps = distance / stride), add start/stop + configurable pace, and run alongside (root, now un-flaggable) **location injection**.
