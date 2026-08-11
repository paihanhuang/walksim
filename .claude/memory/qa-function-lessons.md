# qa-function lessons

- 2026-07-15: osmdroid 6.1.20 tap-parity — `Polyline.infoWindow = null` does NOT stop tap-consume;
  `Polyline.onClickDefault` returns true unconditionally. Verify overlay tap-consume claims against the
  pinned lib's bytecode (`javap -c`), never trust the code comment. Fix is `setOnClickListener{_,_,_->false}`.
- 2026-08-11: Gradle's BUILD CACHE replays a cached PASS when you mutate source and revert (`test FROM-CACHE`),
  so mutation testing silently "passes". Always mutate with `--no-build-cache --rerun-tasks` AND read the
  verdict from `build/test-results/**/TEST-*.xml`, not the console `BUILD SUCCESSFUL` line.
- 2026-08-11: an optimisation test proves nothing unless the instance actually defeats the naive algorithm.
  Two nearest-neighbour "traps" for the 2-opt tour test passed with 2-opt DISABLED (collinear points; ties
  broken favourably by float noise). Build the trap with NO distance ties and verify the RED before trusting it.
