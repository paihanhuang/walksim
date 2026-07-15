# Operating Contract — V-Model SDLC on Superpowers

This project uses an **SDLC V-model governance layer** built **on top of the Superpowers plugin**.
Superpowers is the engine (brainstorm → plan → TDD execute → review → finish). This contract is the
governance shell: task-sizing, roles, gates, traceability, persistent memory, proofs.

This file is the always-on summary. The full pipeline (flow, the 3 human gates, cross-critique, defect
routing, verification discipline, proofs) lives in the **`vmodel` skill** — load it for any Small/Big task.

## 1. First action on any coding request: SIZE THE TASK

State the tier out loud, then run the smallest pipeline that fits. When in doubt, size up one tier.

| Tier | Examples | Pipeline |
|---|---|---|
| **Trivial** | rename, typo, one-line fix, comment | engineer only; quick qa-principle glance on the diff |
| **Small** | one module, low blast radius, clear spec | load `vmodel` skill → mini-spec → engineer (TDD) → qa-function + qa-principle |
| **Big** | new subsystem, cross-cutting, ambiguous, high blast radius | load `vmodel` skill → full V pipeline |

## 2. Principles (non-negotiable)

- **Minimalistic design** — no nice-to-have / speculative / dead code. Every line serves a requirement.
- **Surgical changes** — modify only relevant code; never touch unrelated code (avoids new-issue injection).
- **Readable & debuggable** — code a human can follow and trace.

## 3. Roles

Roles marked **(SP)** are realized by invoking a Superpowers skill; **(agent)** live in `.claude/agents/`.
Each role has an exclusive lessons file `.claude/memory/<role>-lessons.md`.

| Role | Realized by | Focus |
|---|---|---|
| orchestrator | this contract + `using-superpowers`, `subagent-driven-development`, `finishing-a-development-branch` | intake, sizing, gating, dispatch, memory feedback |
| researcher | **(SP)** `brainstorming` + web research | best practices, prior art; findings saved under `docs/` |
| architect | **(SP)** `writing-plans` | decomposition, interfaces, bite-size tasks |
| engineer | **(SP)** `subagent-driven-development` | minimal, surgical, debuggable implementation (TDD) |
| qa-function | **(agent)** + `test-driven-development` | functionality vs acceptance criteria; no regression |
| qa-quality | **(agent)** + `requesting-code-review` | performance/overhead; no quality regression |
| qa-principle | **(agent)** | minimalism + surgical-change compliance (no SP equivalent) |
| (debug) | **(SP)** `systematic-debugging` | root-cause when a defect is found |
| (parallel) | **(SP)** `dispatching-parallel-agents`, `using-git-worktrees` | concurrent independent work |

## 4. Persistent memory ("never repeat mistakes")

- At task start, each role reads its own `.claude/memory/<role>-lessons.md`.
- At task end, if it made a mistake or a verifier caught one, it appends ONE dated terse line.
- The orchestrator passes every rejection reason into the offending role so the lesson is recorded.
- Keep lessons short, deduplicated, pruned: cap ~15 lines per role, keep only recurring/load-bearing lessons, and prune on every append. A role reads/writes only its own file.

## 5. macOS / zsh

- Primary shell is **zsh** (bash also available). Use POSIX-shell commands + absolute Unix paths
  (`/Users/...`) in subagent instructions. Capture test output to proof files (naming in the `vmodel` skill).
- Superpowers hooks run on the system shell directly — no Git Bash needed. Keep `git` on PATH
  (Xcode Command Line Tools or Homebrew).

## 6. Research / analysis tasks

Not all work is code. For research/analysis, run the researcher path only (web research → findings saved
with a self-explaining filename under `docs/research/`), no full pipeline. Be honest about uncertainty;
never fabricate to fit a premise.
