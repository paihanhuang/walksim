---
title: Port reboot-autostart into remote-control (Compose)
status: ready-for-agent
created: 2026-07-24
adr: adr-0001-adopt-remote-control-port-autostart.md
---

# Spec: Port reboot-autostart into the Compose walk-sim

Synthesized via the Matt Pocock flow (no re-interview — grilled in ADR 0001). Publishes to
this project's V-model docs (no issue tracker configured).

## Problem Statement

The rooted Pixel 7 Pro farm resumes the walk hands-free on reboot via a `walksim-autostart`
Magisk module that launches `com.pikmin.walksim/.MainActivity --ez autostart true`. This
(remote-control) build has the better GUI/architecture but **ignores that flag** — its Compose
`MainActivity` has no autostart path — so adopting it silently breaks hands-free reboot-resume.

## Solution

Teach remote-control's `MainActivity` to honor `--ez autostart true`: on a boot launch it
replays the last-used preset by building the *same* `StartSpec` the START button builds and
sending the *same* `WalkService` intent. Selection is persisted when the user taps START. The
Magisk module and the injection stack are unchanged.

## User Stories

1. As a farm operator, I want the walk to resume automatically after a reboot on this build, so that adopting the nicer UI doesn't cost me the hands-free farm.
2. As a farm operator, I want it to replay the preset/duration/pace I last used, so the farm keeps doing what I configured.
3. As a farm operator, I want a fresh device (nothing persisted) to auto-start the default "All areas (sequential)" sweep, so it still farms.
4. As a farm operator, I want auto-start to be a no-op if a walk is already RUNNING/PAUSED, so a re-trigger never double-starts.
5. As a farm operator, I want auto-start to skip safely (never crash) if location permission is somehow missing, so a misconfigured boot degrades gracefully — the OS permission prompt from `onCreate`'s `requestPerms()` provides visibility (and the boot module's `pm grant` makes this path near-unreachable in normal operation).
6. As a developer, I want the autostart "what to start" computation to be a pure function reusing the existing `startSpec`, so it provably builds the same walk the START button does and is unit-tested off-device.
7. As a developer, I want no change to `WalkService`, the Pace IPC, `WalkSessionController`, `core-*`, or the `stephook`, so the port stays surgical.
8. As a farm operator, I want tapping START to persist my selection, so the next reboot resumes it.

## Implementation Decisions

- **Module touched:** `walk-sim/:app` only — `ui/WalkUiLogic.kt` (new pure fn), `ui/WalkUiLogicTest.kt` (tests), `MainActivity.kt` (glue). No other module changes; the `walksim-autostart` Magisk module (in the pikmin2 tree) is reused unchanged.
- **New pure seam `autostartSpec(selectedPosition, durationMin, speedMps): StartSpec`** in `WalkUiLogic`: resolves the preset's **canonical center** (`PRESET_LOCATIONS[selectedPosition-1].at`) and delegates to the existing `startSpec(...)` for the `Single` case; `selectedPosition <= 0` or out-of-range → `StartSpec.Sequential` (never throws). Autostart replays the *preset*, not a hand-dragged pin (only the selection index/duration/pace are persisted) — consistent with ADR 0001 and the pikmin2 port.
- **Persistence:** on START, `MainActivity` writes `{ selectedPosition, durationMin, speedMps }` to SharedPreferences (`walksim_prefs`). Autostart reads them, defaulting to `0 / 60 / 1.3`.
- **Autostart trigger:** `MainActivity.onCreate` sets `pendingAutostart = (savedInstanceState == null && intent.getBooleanExtra(EXTRA_AUTOSTART, false))`; `onResume` consumes it once and, if no walk runs and location permission is granted, calls `sendStart(autostartSpec(...))`. Firing from `onResume` keeps the activity in the FGS-eligible state (Android 14+); `savedInstanceState == null` prevents a config-change re-fire. `sendStart` is the existing shared translation — reused verbatim.
- `EXTRA_AUTOSTART = "autostart"` matches the module's `--ez autostart true`.

## Testing Decisions

- **Good test:** given `(selectedPosition, durationMin, speedMps)`, `autostartSpec` returns the correct `StartSpec` (Sequential for 0/out-of-range; Single with the preset's `at` + `spacingM` + duration/speed otherwise). Assert against `PRESET_LOCATIONS` (domain source of truth). Do NOT test the SharedPreferences/Activity/onResume glue — no framework seam, verified on-device (matches how `MainActivity` is already handled: `WalkUiLogicTest` covers the pure logic, the Activity is untested glue).
- **Module tested:** `:app` `ui/WalkUiLogicTest` (JUnit5), alongside the existing `startSpec` parity tests.
- **Cases:** position 0 → `Sequential`; position k>0 → `Single` with `PRESET_LOCATIONS[k-1].at`/`.spacingM`; last valid index selectable; out-of-range (size+1, negative) → `Sequential`, no throw; duration→`*60`, speed passthrough.
- **On-device (verify-on-device):** reboot → `walksim-autostart` → walk auto-starts (`PaceProvider` playing) → deployed `stephook` credits → proof captured.

## Out of Scope

- Auto-foregrounding Pikmin (landing-page count stays manual; Weekly-Challenge GMS feed is auto).
- The Magisk module / injection stack (unchanged; device-level).
- `WalkService`, the walk engine, `WalkSessionController`, `RoadSource`, the Pace IPC, `core-*`, the `stephook`.
- Persisting a hand-dragged pin (autostart resumes the preset center by design).

## Further Notes

- This mirrors the pikmin2 `StartPlan.of` port but produces remote-control's `StartSpec` type and reuses `startSpec` instead of re-introducing `StartPlan`.
- Install caveat (not code): fresh `--force-queryable` on Android 17 despite the repo docs saying it's unnecessary — empirically required (pikmin2).
