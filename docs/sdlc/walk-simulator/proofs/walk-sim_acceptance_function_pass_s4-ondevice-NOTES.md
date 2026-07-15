# S4 on-device acceptance — `:app` location injection: ✅ Pikmin credits a Shibuya walk

- **Date:** 2026-06-30 · **Device:** Pixel XL (marlin), Android 10, rooted · **App:** `com.pikmin.walksim` (S4 debug build) · **Consumer:** Pikmin Bloom v147 (throwaway acct)
- **Method:** `adb install`; `appops set … mock_location allow`; launch; tap **START** (default **Shibuya** 35.6595,139.7006, 60 min, 1.3 m/s); foreground Pikmin; observe 2 min; tap **STOP**. Driven headlessly via adb `input tap` + `screencap` + `dumpsys`.

## Results (all PASS)
- **AC-12 — mock all surfaces:** `dumpsys location` shows **gps, network, AND fused** all reporting the SAME moving Shibuya fix with the `mock` flag; both providers `[mock]`. No stale-fix teleport (the prior-art regression). → `…s4-ac12-dumpsys-location-agreement.txt`.
- **AC-13 — fix fields:** emitted `Location` carries `vel`/`bear`/`hAcc`/`vAcc`/`sAcc`/`bAcc` + real `et` (elapsedRealtime) + `mock`.
- **AC-14 — FGS survival:** WalkSim FGS (pid 22115) stayed alive and injected **346 mock fixes** at ~1 Hz while **backgrounded behind Pikmin**.
- **AC-11 — Pikmin credits distance:** avatar teleported to Shibuya and **advanced along the injected route** (35.65894,139.70148 → 35.65924,139.70113); over 2 min Pikmin grew multiple **big flowers** (sunflowers, gerbera, lily, bird-of-paradise) along the walked path — Pikmin's distance-walked reward (none before, several after). → `…s4-ac11-pikmin-shibuya-map-{before,after}.png`. (Numeric flower/coin crediting on v147 was already proven by the prior project.)
- **AC-15 — restore on stop:** after STOP, gps/network/fused providers show **no `[mock]` tag** (test providers removed / `setMockMode(false)`); HUD reset to idle. → `…s4-ac15-providers-restored.txt`.
- **AC-20/22:** start/stop effective within a tick; HUD live (speed/distance/steps/elapsed/remaining/progress).

## Not triggered on-device (unit-tested instead)
- AC-16 (not-mock-app banner) / AC-23 (setup error): we *are* the selected mock app, so the negative path wasn't exercised on-device; the engineer unit-tested the `SecurityException` detection.

## Verdict
**S4 ✅** — walk engine → mock-location injection (gps+network+fused) → **Pikmin credits a simulated Shibuya walk**, end-to-end on the Pixel XL. Steps are NOT yet fed (pedometer = S5).
