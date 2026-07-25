---
title: "All areas" walks each city's full route before the next
status: ready-for-agent
created: 2026-07-24
---

# Spec: "All areas" = full route per city, then the next

Synthesized from the 2026-07-24 conversation (no re-interview).

## Problem Statement

"All areas (sequential)" splits the **total** duration across the 10 presets
(`sequencePlan(PRESET_LOCATIONS, durationS)` → each gets ~1/N), so each city is walked only
**partially** before the mock teleports to the next. The operator wants each city **fully
harvested** — its full route walked to completion — before moving on, with the only jumps
being the unavoidable city→city hops.

## Solution

Change `SEQUENTIAL` mode so each preset walks its **full closed-loop route** (sized to its own
`routeLengthKm`, returning to its start = "complete"), then teleports to the next city.
Continuous within a city; teleport **only** at city boundaries. Total run time = the sum of all
cities' full routes (a long unattended farm that covers every city fully; the reboot-autostart
restarts it).

## User Stories

1. As a farm operator, I want each city in "All areas" fully harvested (its whole route) before the next begins, so no city is left half-walked.
2. As a farm operator, I want the walk continuous within a city and teleporting only when the city changes, so the only discontinuities are the unavoidable inter-city hops.
3. As a farm operator, I want each city's walk to end back where it started (closed loop), so "complete" is a clean boundary before the jump.
4. As a farm operator, I want "All areas" to cover all 10 cities' full routes in order (Shibuya → … → Seoul), so a long farm sweeps every city.
5. As a developer, I want the per-city route-length → duration mapping to be a pure, unit-tested function mirroring `sequencePlan`, so the change is provable off-device.
6. As a developer, I want single-preset, HOLD, the walk engine, and the injector untouched, so the change is surgical.

## Implementation Decisions

- **New pure `fullRoutePlan(presets, speedMps): List<Pair<NamedLocation, Long>>`** in `Locations.kt`
  (**replaces** the even-split `sequencePlan`, which becomes dead and is removed with its test): each
  preset → `round(routeLengthKm * 1000 / speedMps)` seconds — the time
  to walk that preset's full route at the mean speed. A non-positive speed falls back to 1.3 (same
  guard as `presetDurationMinutes`). Order preserved.
- **`WalkSessionController.SEQUENTIAL`** iterates `fullRoutePlan(PRESET_LOCATIONS, meanSpeed)` instead of
  `sequencePlan(…, durationS)`. Per preset (unchanged otherwise): `onNotify(label)` → `sink.hold(preset.at)`
  → `resolveGraph` → play the route with **`closeLoop = true`** (full closed loop, run to completion via the
  engine's `runUntilPathEnd`), then the next. `spec.seed + i` per preset stays.
- **`playRoute` gains a `closeLoop: Boolean = spec.closeLoop` parameter** so the sequential path forces
  `closeLoop = true` (each city returns to its start) without touching the single-preset call site.
- The UI **duration field is not used** for "All areas" (each preset uses its own `routeLengthKm`). Single-preset
  still uses the field.
- No change to `Mode.SINGLE` / `Mode.HOLD`, the engine, the Pace IPC, or the injector.

## Testing Decisions

- **Pure `fullRoutePlan`** (JUnit5, `FullRoutePlanTest`): each preset → `routeLengthKm*1000/speed`
  seconds; order preserved; empty → empty; non-positive speed → 1.3 fallback; real `PRESET_LOCATIONS`
  (all 500 m/10 km → equal per-city seconds).
- **On-device (verify-on-device):** "All areas" served location stays within one city for its full route,
  then jumps to the next (notification advances `i/N` only at the boundary).

## Out of Scope

- The inter-city teleports themselves (inherent — the "only jump" the operator accepts).
- Single-preset and HOLD modes; the walk engine; the injector.
- Bounding the total time (each city runs its full route; total = the sum).
