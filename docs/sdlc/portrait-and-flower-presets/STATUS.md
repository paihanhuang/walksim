# STATUS — portrait-only UI + flower-tour presets

**Tier:** Big · **Branch:** main (walk-sim, canonical repo `pikmin-remote-control`)
**Device of record:** Pixel 7 Pro (cheetah), Pikmin Bloom v150.0.

| Req | State | Evidence |
|---|---|---|
| R1 portrait only | **DONE, device-verified** | forced `user_rotation=1` → display stayed `ROTATION_0`, activity config `port` |
| R2 Haneda preset | **DONE, device-verified** | tour 17.13 km; 10/10 censused sites passed ≤250 m on the phone (worst 246 m) |
| R3 Enoshima preset | **DONE, device-verified** | tour 6.73 km; 8/8 censused sites passed ≤250 m on the phone (worst 238 m) |
| R4 pace 1.3/5/7/10/20 | **DONE, device-verified** | see `proofs/pace_acceptance_function_pass_matrix.md` |
| R5 no regression | **DONE** | 164/0 baseline → 176/0; golden route + motion digest tests unchanged |

## Defects found and fixed during this work

1. **Pace channel unreachable** — plain `adb install` broke package visibility; Pikmin's hook logged
   `Failed to find provider info for com.pikmin.walksim.pace`. WalkSim must be installed
   **`pm install --force-queryable`**. This is the historical "pace not picked up" defect, reproduced and fixed.
2. **Pace above ~1.8 m/s silently ignored** — two independent clamps (`WalkProfile.speedRange` literal
   `0.8..1.8`, and a fixed 4.5 m/tick no-teleport bound). 5/7/10/20 m/s all played at 1.8 m/s (144 steps/min).
   Both now scale with the requested pace; the 1.3 m/s default is bit-identical.
3. **Tour sites silently dropped** — foot-only ways are excluded from the street graph and the largest-component
   guard then discards whole areas: Enoshima island (route build threw outright) and Haneda T1/T2 (missed by
   1.5–1.9 km). Fixed by a **tour-only** `extraWalkable` opt-in; sweep presets keep their exact graph.
4. **Edge start clipped the survey** — a tour's fetch disc must contain every site, so the start must be central.
   Haneda moved to Terminal 3; Enoshima to Katase-Enoshima station.
5. **Degenerate tour aborted the whole session** — an unreachable survey produced a zero-length route that killed
   SEQUENTIAL mode for every city after it. Now falls back to the sweep.

## Known limitations (honest)

- Both new areas are **low big-flower density** by this project's standards: Haneda 1.25 flowers/frame,
  Enoshima 1.78, versus Ueno's 5.9. Haneda was requested by name; Enoshima measured as the better beach option.
- "All big flowers" is at **census-sample granularity** — Pikmin exposes no flower coordinates, so a site is a
  sampled point observed to bear flowers, not an individual flower. See `requirements.md` assumption 2.
- Counts are a 2026-08-11 snapshot; big flowers bloom/wither with player planting. The wayspot *sites* are stable.
- The Haneda tour is 17.13 km (~3.7 h at 1.3 m/s) because the survey spans the whole 4 km-wide airport.
- **Overpass outages degrade a tour into a Shibuya sweep.** During verification `overpass-api.de` returned
  `HTTP 504` for several minutes (reproduced independently with `curl`, and on the kumi mirror), so the walk fell
  back to the baked Shibuya graph and the avatar walked Shibuya instead of Haneda. That is the pre-existing
  documented fallback and it does raise the "Map fetch failed — using the offline Shibuya map." banner, but for a
  tour preset the fallback is more misleading than for a sweep: check the banner before trusting a tour run.
  A tour's larger fetch disc (up to 4 km) makes it likelier to hit an Overpass timeout than a sweep's.
