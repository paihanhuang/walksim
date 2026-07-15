# docs/sdlc — V-model artifacts

One folder per feature: `docs/sdlc/<feature-slug>/`. Copy the files from `_templates/` to start a feature.
The methodology contract lives in the repo-root `CLAUDE.md`.

## Per-feature files
| File | Purpose | Owner |
|---|---|---|
| `requirements.md` | EARS / Given-When-Then acceptance criteria + `[NEEDS CLARIFICATION]` | researcher + qa-function |
| `design.md` | architecture, interfaces, stage decomposition, cross-critique log | architect |
| `tasks.md` | bite-size tasks linked to requirement IDs | architect |
| `traceability.md` | requirement ↔ test ↔ proof ↔ status matrix | orchestrator |
| `STATUS.md` | stage ledger + gate status + tier | orchestrator |
| `proofs/` | committed evidence (test logs, benchmarks) | qa-* |

## Proof naming
`<feature>_<level>_<aspect>_<result>_<sha-or-phase>.<ext>`

- `level` ∈ {unit, integration, system, acceptance}
- `aspect` ∈ {function, quality, principle}
- `result` ∈ {pass, fail}
- Examples: `auth_unit_function_pass_a1b2c3d.xml`, `auth_system_quality_pass_before.json` / `_after.json`

Prefer machine-readable formats (JUnit XML, JSON benchmarks). Proofs are evidence — they are committed, not git-ignored.

## Tiers (see CLAUDE.md §1)
- **Trivial** → engineer only. **Small** → mini-spec → engineer → qa-function + qa-principle.
- **Big** → full V pipeline with all three human gates.
