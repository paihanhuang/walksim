# engineer — lessons (terse, deduplicated, cap ~15 lines)

- 2026-07-14 (S0): walk-sim/spike declare NO `kotlin-test` dep — write core-osm/core-sim tests with JUnit Jupiter (`org.junit.jupiter.api`), matching neighbours; `assertEquals(expected,actual,msg)` arg-order matches kotlin.test. JUnit's generic `fail` → call `fail<Unit>(...)`.
- 2026-07-14 (S0): Gradle gotchas — env vars (e.g. WALKSIM_PERF) do NOT reach the forked test JVM through a WARM daemon → run perf with `--no-daemon`; a `--tests`-filtered run leaves `:<mod>:test` up-to-date so a later unfiltered `test` is SKIPPED → prove full-suite green with `--rerun-tasks` (confirm "N tasks executed", not up-to-date).
- 2026-07-14 (S0): golden `check()` reads `File("src/test/resources/golden/…")` from the SOURCE tree (not the classpath), so record-then-assert round-trips through source; Gradle test working dir = module root.
