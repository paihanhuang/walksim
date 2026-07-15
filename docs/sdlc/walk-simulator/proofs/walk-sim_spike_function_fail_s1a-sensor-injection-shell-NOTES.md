# Spike S1a — Root SensorService step-injection via shell: ❌ no path on this HAL

- **Date:** 2026-06-30 · **Device:** Pixel XL (marlin), Android 10, Magisk-rooted · **Consumer:** Pikmin Bloom (reinstalled)
- **Goal:** with root, inject `STEP_COUNTER` events via the SensorService shell/dumpsys interface — the prior project's no-root vector #4, retried now that we have root.

## Result — dead via shell
- `cmd sensorservice` exposes **only** `get/set/reset-uid-state` + `help` — **no data-injection command**.
- `dumpsys sensorservice` has no injection argument; malformed args just error (and a stray `cmd sensorservice` no-arg returns "Broken pipe" — benign, service recovers).
- HAL is legacy: **27 h/w sensors, Operating Mode NORMAL** for all — no `SENSOR_HAL_DATA_INJECTION_MODE` exposed. The 2016 BMI160 HAL almost certainly lacks `inject_sensor_data`.
- The real injection API (`SensorManager.injectSensorData` / SensorService data-injection binder) requires a privileged process holding `HARDWARE_TEST`/`LOCATION_HARDWARE` **and** HAL injection support — unreachable from a shell even as root, and HAL support is absent here.
- Step sensors confirmed present + healthy post-probe: `step_counter`=`0x17`, `step_detector`=`0x09`, both `ACTIVITY_RECOGNITION`-gated.

**Evidence:** `walk-sim_spike_s1a-sensorservice-dump.txt` (full `dumpsys sensorservice`).

## Verdict → Stage 2
System-level sensor injection is not a cheap path on this legacy HAL. Proceed to **Stage 2: Zygisk + LSPosed in-process hook** of `SystemSensorManager` (and/or GMS Activity Recognition) — it fabricates step events inside the consumer process, bypassing HAL injection entirely.
