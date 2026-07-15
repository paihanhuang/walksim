# walk-sim traceability

## Pace IPC contract (Stage 0 baseline — frozen)

The cross-process pace channel between the two Gradle roots, documented **verbatim from the
current code** as the Stage 0 safety-net baseline. This is the pre-Stage-2 contract: there is
**no schema-version column today**. Stage 2 hardens this seam (adds an *additive* `schemaVersion`
column + dual-root contract tests) and will update this table with a human note.

**Producer:** `walk-sim/app/src/main/java/com/pikmin/walksim/PaceProvider.kt`
**Consumer:** `spike-step-hook/app/src/main/java/com/pikmin/stephook/PaceClient.kt`

| Aspect | Value (today) | Source of truth |
|---|---|---|
| Authority | `com.pikmin.walksim.pace` | `PaceProvider.AUTHORITY` |
| URI | `content://com.pikmin.walksim.pace/current` (path segment `current`) | `PaceProvider.CURRENT_URI` / `PaceClient` `CURRENT_URI` |
| Column 1 | `playing` : `INT` (0/1) | `PaceProvider.COL_PLAYING = "playing"` |
| Column 2 | `stepsPerMin` : `REAL` (Float) | `PaceProvider.COL_STEPS_PER_MIN = "stepsPerMin"` |
| Schema version | **none** (no version column emitted) | — (added additively in Stage 2) |
| Cursor shape | single-row `MatrixCursor` (exactly one `addRow`) | `PaceProvider.query()` |
| Column order emitted | `{ playing, stepsPerMin }` | `PaceProvider.query()` |
| Poll cadence | ≤ 2 Hz (`POLL_INTERVAL_MS = 500L`) | `PaceClient.POLL_INTERVAL_MS` |
| Access | exported, permission-free, **query-only** | `PaceProvider` (insert/update/delete/getType stubbed → `null`/`0`) |
| Client read discipline | by **column name** (`getColumnIndexOrThrow`), `projection = null` — tolerant of column order/additions | `PaceClient.queryProvider()` |
| Liveness signal | each query stamps `WalkBus.lastQueriedElapsedMs` (AC-23) | `PaceProvider.query()` |
| Pace derivation | `PaceDerivation.derive(WalkBus.sample.value, WalkBus.strideM)` | `PaceProvider.query()` |
| Client failure semantics | null app / null cursor / empty / any exception → `NOT_PLAYING` (`playing=false, stepsPerMin=0f`); never throws into the driver thread | `PaceClient.poll()` / `queryProvider()` |

**Invariants later stages must not weaken:** authority + path stable; `playing`/`stepsPerMin` column
names stable and read by name; query-only + exported + permission-free; poll ≤ 2 Hz; the query stays
trivial and synchronous (no `future.get`/timeout worker — an earlier such design hung the driver).
Stage 2 changes are **additive only** (append `schemaVersion`; never remove/reorder existing columns).
