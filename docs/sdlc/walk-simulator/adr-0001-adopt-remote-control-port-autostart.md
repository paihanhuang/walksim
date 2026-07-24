# ADR 0001 — Adopt pikmin-remote-control as the walk-sim; port reboot-autostart into it

- Date: 2026-07-24
- Status: Accepted
- Context grilled via grill-with-docs (grilling + domain-modeling), grounded in this repo's docs/code and the live device state.

## Context

The rooted Pixel 7 Pro farm currently runs the **pikmin2** walk-sim (`com.pikmin.walksim`)
with a hands-free **reboot-autostart**: a `walksim-autostart` Magisk module launches
`com.pikmin.walksim/.MainActivity --ez autostart true` on boot, and pikmin2's MainActivity
replays the last preset via a pure `StartPlan.of(...)` seam.

**pikmin-remote-control** is the same app — same package and same Pace IPC contract
(`content://com.pikmin.walksim.pace/current` → `playing/stepsPerMin/schemaVersion`) — but
**hardened** (Plan A: orchestration extracted into a pure `WalkSessionController` + a
`LocationSink` interface; `RoadSource` dependency-injected as `CompositeRoadSource(Overpass →
Fixture)`; contract-tested dual-root Pace IPC via `pace-contract.properties`) and
**Compose-reskinned** (Plan B: "Petal Pop" UI, springy motion, Sprout Pin icon). It is a
drop-in replacement and stays compatible with the deployed `stephook`.

**Problem:** it has **no autostart handling** and no `StartPlan` — its Compose `MainActivity`
ignores `--ez autostart true`. Installing it as-is breaks the hands-free reboot-resume.

## Decision

Adopt pikmin-remote-control as the active walk-sim and **port the reboot-autostart into it**
(rather than accept losing it). The port reuses this repo's existing pure seams — the
`WalkUiLogic.startSpec` / `WalkSessionController` orchestration and the `WalkService`
string-extra START path — instead of reintroducing pikmin2's `StartPlan`. Built via the Matt
Pocock flow: `to-spec` → `tdd` → `code-review`.

The existing `walksim-autostart` Magisk module is reused **unchanged** (it already targets
`com.pikmin.walksim/.MainActivity` and sends `--ez autostart true`).

## Consequences

- Keeps the better GUI + cleaner architecture **and** hands-free reboot.
- Deployed pikmin2 `stephook` stays (Pace-contract-compatible) — no hook change needed.
- Install fresh with `--force-queryable` on Android 17 — this repo's docs say it's unnecessary,
  but pikmin2 empirically proved Android-17 package-visibility blocks the hook without it.
- pikmin2's on-device app is replaced; rollback = reinstall pikmin2's built APK.
- Autostart now exists in two codebases (the pikmin2 branch + here); **remote-control becomes
  canonical** going forward.

## Glossary (delta from pikmin2)

- **WalkSessionController** — pure orchestration of route/sequence/hold/restore driving a
  `LocationSink`; the remote-control counterpart to pikmin2's `StartPlan` (which does not exist here).
- **startSpec** (`WalkUiLogic`) — pure "what walk to start" derivation from UI selection; the
  natural reuse point for autostart.
- **Pace IPC** — the single app↔hook seam: `content://com.pikmin.walksim.pace/current`, asserted
  against `pace-contract.properties` by a `PaceContractTest` in each Gradle root.
