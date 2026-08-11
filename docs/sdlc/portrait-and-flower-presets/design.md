# Design — portrait-only UI + flower-tour presets

## R1 — Portrait lock

`android:screenOrientation="portrait"` on `.MainActivity`. The manifest attribute *is* the seam: orientation is
resolved by the platform from the declaration, so there is no app-side function to assert on. `PortraitOnlyTest`
pins the declaration; the on-device forced-rotation test is the real acceptance proof.

## R2/R3 — Flower tour

### New route builder — `core-sim/FlowerRoute.kt`

`flowerRoute(graph, start, flowers, closeLoop)` → the shortest closed road walk passing every surveyed site:

1. Snap each site to the start's connected component (`FLOWER_SNAP_M = 400 m` — looser than the sweep's 250 m,
   since a flower can sit in a park or across water from the nearest way).
2. Order by nearest-neighbour, then **2-opt** over true road distances. One Dijkstra per waypoint yields all
   pairwise distances (not one per pair).
3. Chain consecutive waypoints with Dijkstra shortest paths → road-snapped by construction.
4. `closeLoop` appends the path home so the tour is a repeatable circuit.

Deterministic: snap ties by node id, Dijkstra ties by node id over canonically sorted adjacency, fixed 2-opt scan
order, no RNG. Dijkstra/adjacency/snap primitives were made `internal` and shared with `sweepRoute` rather than
duplicated; `sweepRoute`'s own logic is untouched and its golden route test still passes.

**2-opt is justified, not decorative:** `FlowerRouteTest.toursTheFlowersByTheShortestClosedWalk` is a
nearest-neighbour trap with no distance ties, and was **verified to fail (1800 m vs the optimal 1600 m) when
2-opt is disabled**. Two earlier candidate instances were discarded precisely because NN already solved them.

### Foot-only ways — the connectivity fix

The street graph deliberately drops `footway/path/steps/cycleway/track` (they ~double the node count), and
`OverpassGraph` keeps only the **largest connected component**. Both new areas are reached mainly on foot, so:

- Enoshima island's ways are all footway/steps → *no node within 200 m of the shrine*; the route build threw.
- Haneda's terminal decks are footways → T1/T2 sat in a discarded component (missed by 1564 m / 1932 m).

Fix: `RoadSource.graphAround(center, radiusM, extraWalkable = emptySet())`. A tour preset passes
`OverpassGraph.FOOT_ONLY_WAYS`; **every sweep preset passes nothing and gets a byte-identical graph.** Measured
effect (`proofs/flower-route_integration_function_pass_live-overpass.txt`): Haneda 10/10 sites reached, worst
approach 53 m; Enoshima 8/8, worst 223 m — both inside the 250 m harvest reach.

### Start-pin placement

A tour's fetch disc must contain the whole survey, so the start must be **central**, not at one end. Starting
Haneda at Anamori-inari put T1/T2 at the rim of the disc where their roads were clipped into a separate
component; Terminal 3 is central and fixed it. Enoshima starts at Katase-Enoshima station (mainland) because the
island shrine has no street-graph node within the 200 m start-snap.

### Degenerate-tour fallback

If **no** site is reachable (offline-Shibuya fallback graph, or a poor fetch) `flowerRoute` returns a zero-length
route. `WalkPlayer` treats that as "no tour" and walks the normal sweep. Without this, a zero-length route
aborted the whole SEQUENTIAL run — caught by `WalkSessionControllerTest`, which showed every city after Haneda
silently never walked.

### Plumbing

`NamedLocation.flowers` (empty = sweep, unchanged) → `StartSpec.Single.flowers` → intent extra `flowers_s`
(`lat,lng;…`, so a tour is also scriptable from `adb am`) → `RunSpec.flowers` → `WalkPlayerConfig.flowers`.
"All areas" carries each preset's own survey, so a tour preset is toured there too rather than swept.

## R4 — Pace fidelity

Two independent clamps threw away any pace above ~1.8 m/s:

| clamp | was | now |
|---|---|---|
| `WalkProfile.speedRange` | fixed `0.8..1.8` | `meanSpeedMps ± 0.5` |
| `WalkingMotionEngine` no-teleport bound | fixed 4.5 m/tick | `max(4.5, speedRange.end·Δt + 2.0)` |

At the 1.3 m/s default the new formulas evaluate to **exactly** `0.8..1.8` and **exactly** 4.5 m — the shipped
walk is bit-identical, which the golden motion-digest test confirms.

**R4.3 (pace channel reachability)** is an *install* property, not a code one: WalkSim must be installed
`pm install --force-queryable`, otherwise Pikmin's process cannot see the provider and logs
`Failed to find provider info for com.pikmin.walksim.pace` → `PaceClient: query returned null`. This was
reproduced and fixed during this work. The GMS `STEP_COUNTER` feed and counter base-capture hardening were
already present in this repo (`StepInjector.kt` is byte-identical to the post-fix pikmin2 version).
