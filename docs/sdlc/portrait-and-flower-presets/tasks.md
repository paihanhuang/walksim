# Tasks — portrait-only UI + flower-tour presets

Bite-size, requirement-linked. Status as of 2026-08-11 (post code-review remediation).

## Build

| # | Task | Req | Test | State |
|---|---|---|---|---|
| T1 | Lock `.MainActivity` to portrait | R1.1 | `PortraitOnlyTest` | DONE |
| T2 | In-Pikmin teleport census of the Haneda vicinity (16 pts) | R2.2 | — (device) | DONE |
| T3 | In-Pikmin teleport census of Enoshima/Katase (12 pts) | R3.2 | — (device) | DONE |
| T4 | `flowerRoute` — near-shortest tour over surveyed sites | R2.2/R3.2 | `FlowerRouteTest` (6 cases) | DONE |
| T5 | `flowerFetchRadiusM` — disc contains the whole survey | R2.2/R3.2 | `FlowerRouteTest.fetchRadiusContainsTheSurvey_withinClamps` | DONE |
| T6 | Tour-only foot-only-way opt-in (`extraWalkable`) | R2.2/R3.2 | `TourPresetReachTest` | DONE |
| T7 | Plumb sites: `NamedLocation` → `StartSpec` → intent → `RunSpec` → `WalkPlayerConfig` | R2.1/R3.1 | `FlowerCodecTest`, `WalkUiLogicTest` | DONE |
| T8 | Degenerate-tour → sweep fallback | R2.4/R3.4 | `FlowerRouteTest.returnsNullWhenNoSurveyedSiteIsReachable`, `WalkSessionControllerTest` | DONE |
| T9 | Scale `speedRange` + no-teleport bound with the requested pace | R4.1 | `HighPaceTest` | DONE |

## Verify

| # | Task | Req | Proof | State |
|---|---|---|---|---|
| V1 | Forced-rotation test on device | R1.1 | `proofs/portrait_*` | DONE |
| V2 | Tour geometry on the live Overpass graph | R2.2/R3.2 | `proofs/flower-route_integration_*` | DONE |
| V3 | Both tours walked on the Pixel 7 Pro, per-site approach measured | R2.3/R3.3 | `proofs/tour_acceptance_*` | DONE |
| V4 | Pace matrix 1.3/5/7/10/20 verified in Pikmin | R4.1/R4.2 | `proofs/pace_acceptance_*` | DONE (5 m/s row unexplained — see below) |
| V5 | `--force-queryable` pace-channel reachability | R4.3 | `proofs/pace_acceptance_*` | DONE |
| V6 | Full-suite no-regression vs the 164/0 baseline | R5.2 | `proofs/regression_*` (JUnit XML) | DONE |

## Remediation from the two-axis code review (2026-08-11)

| # | Finding | Fix | State |
|---|---|---|---|
| R-1 | Two censused sites excluded, narrowing R2.2 | added to the Haneda survey; gated by `TourPresetReachTest` | DONE |
| R-2 | Spec's "~400–700 m spacing" claim false (max 1001 m) | corrected in `requirements.md`; guarantee restated | DONE |
| R-3 | "Shortest" unbounded | measured 3.4% worst case, gated at 5%; wording → "near-shortest" | DONE |
| R-4 | 250 m criterion gated only by a skipped network diagnostic | `TourPresetReachTest` + baked offline fixtures | DONE |
| R-5 | Zero-length-route sentinel | `flowerRoute` returns `Route?` | DONE |
| R-6 | Tour presets had no `spacingM` → fallback swept at 850 m | set to 500 m, asserted | DONE |
| R-7 | Unrelated whitespace edit | reverted (diff vs merge-base now clean) | DONE |
| R-8 | `tasks.md` missing | this file | DONE |
| R-9 | No machine-readable regression proof | JUnit XML committed under `proofs/junit/` | DONE |
| R-10 | `encodeFlowers`/`decodeFlowers` untested | `FlowerCodecTest` | DONE |
| R-11 | Dead default params / no-op regex option / duplicated `FOOT_WAYS` / duplicated tick-bound expression | removed | DONE |
| R-12 | 5 m/s step-credit anomaly explained by an unsupported claim | claim withdrawn; re-measured in isolation ×3. Deficit is REAL and reproducible (~33% of published); two hypotheses tested and rejected. Documented as unexplained. | DONE (finding stands open) |

## Open

- **5 m/s credit deficit** — reproducible at ~33% of published rate while 1.3 credits 104% and 7/10/20 credit 70–77%. Cause unknown; needs a deeper look at Pikmin/GMS-side filtering, not more measurement.
- **Airport decor** (user goal, separate from flowers): the tour spends 58% of its length on the airport island
  but only ~1.7 km within 150 m of a terminal. Empirically check what decor terminal seedlings carry, then decide
  on a concourse-focused `Haneda Terminals` preset.
- **Census gap** — a ≤500 m re-census would close the corridors noted in `requirements.md` assumption 2.
