# Harness Engineering — Cross-Source Synthesis & Insights

> **What this is:** the synthesis layer over the three source docs in this folder —
> `claude-code-cli-best-practices.md` (Anthropic official), `karpathy-harness-engineering-principles.md`
> (Karpathy), and `garry-tan-gstack-harness-best-practices.md` (gstack). It extracts the *cross-cutting
> insights*, names where the sources **agree, disagree, and where folklore crept in**, and turns it into
> concrete guidance for **this project's V-model-on-Superpowers contract**. Read the three source docs for
> evidence and citations; this doc is the "so what."

_Synthesized 2026-06-28._

---

## The one-sentence thesis

**Rigor didn't disappear when the typist became an AI — it moved up the stack to spec, context curation,
and verification.** A "harness" is the machinery that relocates engineering discipline to those three
places. Every practice across all three sources is a special case of that move.

## The three sources are one argument at three altitudes

| Source | Altitude | Gives you |
|---|---|---|
| **Karpathy** | *Why* | the cognitive model — what the agent is (a fallible savant with no memory) and therefore what a harness must compensate for |
| **Anthropic docs** | *How* | the mechanisms — context tools, hooks, permissions, subagents, verification gates |
| **gstack** | *What, shipped* | one opinionated end-to-end instantiation (roles → gates → pipeline) you can read and fork |

Read in that order, they're theory → mechanism → instance. None is complete alone.

---

## The 6 cross-cutting insights (the load-bearing section)

### 1. There is one scarce resource (context) and one bottleneck (verification). Everything else is detail.
- Anthropic: "context window fills fast, performance degrades as it fills" — most practices exist to keep
  it small/high-signal or add a deterministic check.
- Karpathy: context window = working memory ("anterograde amnesia"); "we do the verification — make that
  loop fast."
- gstack: roles = context scoping; output→input chaining keeps each step small; "taste gates" spend human
  attention only on uncertainty.
→ **A harness is a context-curation + verification-acceleration machine.** When designing any feature of a
harness, ask: *does this shrink/sharpen context, or does it speed verification?* If neither, cut it.

### 2. The model is a fallible savant → design for verification, never trust.
- Karpathy: "fallible people spirits," jagged intelligence, regresses to internet-typical patterns on novel code.
- Anthropic: "Claude stops when work looks done; without a check, *you* become the verification loop." "If
  you can't verify it, don't ship it."
- gstack: layered gates + cross-model `/codex` + real-browser `/qa`; the "exit code 0, all green, but
  wrong" war story.
→ **Generator ≠ verifier, taken literally:** separate context, ideally separate *model*, in the *real
  environment*, behind *deterministic* gates. Trust-then-verify is the only safe posture.

### 3. Push invariants out of the prompt and into code. (Most actionable, most violated.)
- Anthropic: hooks are deterministic; CLAUDE.md is advisory and "can be forgotten as context fills"; use
  hooks for "every time, zero exceptions"; **permissions are enforced by the harness, not the model** — so
  prompt injection can't bypass them. Prose is not a security boundary.
- Karpathy: agents have no continual learning → externalize memory and re-assert conventions every step.
- gstack: coverage audit *blocks* merge; `/careful` intercepts destructive ops.
→ **If something must always happen, it cannot live in prose.** Encode it as a hook, deny-rule, CI gate, or
  required artifact. This is the highest-leverage upgrade available to most harnesses (including ours).

### 4. Autonomy is a dial set by reliability and right-sized to blast radius.
- Karpathy: the autonomy slider; "keep AI on the leash"; "Iron Man suit, not robot"; `works.any()` (demo) vs
  `works.all()` (product).
- Anthropic: permission modes (default → acceptEdits → auto → bypass); "if you can describe the diff in one
  sentence, skip the plan."
- gstack: full pipeline for features; `/freeze`/`/guard` to *tighten* autonomy for risky work.
→ **A fixed autonomy level is wrong for most work.** Match pipeline weight to risk. (This project's
  Trivial/Small/Big **task-sizing is exactly the autonomy slider, operationalized** — a genuine strength.)

### 5. Process is the guardrail that makes parallelism and scale *safe*.
- gstack: 10–15 parallel agents; "process prevents collision"; workspace isolation.
- Anthropic: subagents run in separate context windows (only the summary returns); worktrees; "infinite
  exploration" → scope narrowly or use subagents.
- Karpathy: orchestrate multiple calls; keep increments small and auditable.
→ **You can't safely fan out without per-agent scope + defined handoffs.** Structure unlocks concurrency;
  it doesn't oppose it.

### 6. Optimize for verification bandwidth, not generation volume.
- Karpathy: never emit a 1000-line diff the human can't audit; speeding generation without speeding
  verification makes things *worse*.
- Anthropic: "show evidence (test output, screenshot)"; surgical diffs; specs state what's out of scope.
- gstack: atomic commits, coverage deltas, before/after benchmarks, real screenshots; and Tan's own
  honest metric — **"time to first user, not LOC."**
→ **Small reviewable increments + machine-checkable evidence are the real throughput limiter.** Generation
  is cheap and getting cheaper; auditability is the product.

---

## The convergent core: everyone re-derived the same 4 coding invariants

The community "4 principles" (popularly mis-credited to Karpathy — see provenance note below) turn out to
be what *all four* of these independently enforce. This is the spine of harness practice:

| Folk principle | Karpathy (real) | Anthropic docs | gstack | **This project** |
|---|---|---|---|---|
| **Think before coding** | "you're still the architect"; don't jump to code | Explore→Plan→Code; spec-first; fresh session to execute | `/office-hours`, `/plan-*` lock scope | **SIZE THE TASK first**; research/plan gates |
| **Simplicity first** | "it's slop"; regresses to bloated patterns | short CLAUDE.md; cut speculative; "would removing this cause mistakes?" | `/review` catches unnecessary code | **Minimalistic design** (non-negotiable) |
| **Surgical changes** | orthogonal edits cause regressions | surgical diffs; protected paths; deny-rules | "Confusion Protocol"; `/freeze` | **Surgical changes** (non-negotiable) |
| **Goal-driven / verifiable** | generate→verify loop | "give Claude a check"; Stop hooks; show evidence | `/ship` test-first + coverage gate | **proof-of-pass + traceability**; adversarial `qa-*` |

The reassuring conclusion: **this project is already aligned with the consensus.** The gaps are mechanical
(see "borrow"), not philosophical.

---

## Where the sources DISAGREE (don't paper over this)

- **Maximalism vs minimalism.** gstack runs ~23 gates *every time*; Anthropic, Karpathy, and this project
  preach lean context and right-sizing. These genuinely conflict. **Resolution = task-sizing:** gstack's
  full pipeline is the *Big-tier* behavior; for a typo you run one step, not twenty-three. Importing
  gstack's "always run everything" would violate both Anthropic's "keep context small" and our minimalism.
- **The productivity metric.** gstack's headline leans on (deflated) lines-of-code; *everyone serious,
  including Tan himself*, says LOC is garbage. **Use time-to-first-user, revert rate, post-merge fix rate,
  and test growth instead.**
- **Roles: mechanism or theater?** gstack's critics call personas "role-play theater." The defensible core
  is that a persona = *scoped context + decision criteria*; it's theater only when it's flavor text that
  doesn't change outputs. **Keep the mechanism; drop any costume that doesn't move behavior.**

## Provenance honesty (a harness-culture value in itself)

The viral **"4 principles of harness engineering" are not Karpathy's** — they're a community `CLAUDE.md`
*inspired by* him; "harness engineering" the term is **Mitchell Hashimoto's**. gstack's README even frames
its safeguards as **"Karpathy's four failure modes,"** propagating the same misattribution. The ideas are
excellent; the credit line is folklore. Honest attribution (credit Karpathy with *context engineering, the
autonomy slider, the generate/verify loop, "people spirits"*; credit the "principles" to the community) is
itself a best practice — and it mirrors this project's own rule: **"never fabricate to fit a premise."**

---

## Concrete application to THIS project (V-model on Superpowers)

### Already strong — keep (validated by all three sources)
- **Task-sizing tiers** = the autonomy slider, right-sized. (Karpathy + Anthropic permission modes.)
- **Adversarial `qa-*` with generator≠verifier** = the verification thesis, done right.
- **Proof-of-pass + traceability** = "show evidence" + "verifiable goals."
- **Per-role lessons files** = externalized memory countering "no continual learning."
- **Human gates only at 3 points, auto-gate the rest** = "spend human attention on the uncertain."
- **`vmodel` skill loaded on demand vs bloating CLAUDE.md** = Anthropic's skills-over-CLAUDE.md.

### Borrow (the real gaps), highest-leverage first
1. **Move must-happen invariants from prose to hooks.** The contract currently relies on CLAUDE.md text
   ("each role reads its lessons," "proofs are committed," "tests before done"). Per Insight #3, the
   critical ones should be **`PostToolUse`/`Stop` hooks or deny-rules**, because prose "can be forgotten as
   context fills." *This is the single biggest upgrade.*
2. **Cross-model review for Big-tier / high-blast-radius changes** (gstack `/codex`): an optional
   second-model adversarial pass. Generator≠verifier across vendors.
3. **Real-environment verification** where behavior is runtime (gstack `/qa`): wire the existing `verify`/
   `run` skills into `qa-function` so evidence is a real run/screenshot, not only unit logs.
4. **"Earn into global memory" rule for lessons files** (gstack): promote a lesson to always-loaded only
   after it recurs; prune aggressively with a threshold — prevents the lessons files from becoming the
   bloated-CLAUDE.md failure mode.
5. **Honest metrics in `STATUS.md`** (Tan): track time-to-first-result, revert rate, post-merge fix rate,
   test growth — never LOC.
6. **Safety scoping as rules, not prose** (gstack `/careful`/`/freeze`/`/guard`): destructive ops behind
   **deny-rules**; scope edits via `acceptEdits` + `additionalDirectories`. (Anthropic: prose boundaries
   are not a security boundary.)

### Avoid (anti-patterns the sources warn about)
- **Don't import gstack's maximalism** — 23 always-on gates break minimalism and lean-context. The tier
  system is the correct answer.
- **Don't measure by volume.** Output is not progress.
- **Don't trust persona prose as a behavior change** — verify a role actually alters outputs/gates.
- **Don't let CLAUDE.md grow** to carry invariants that belong in hooks (Insight #3, restated because it's
  the most common regression).

---

## Sources

This synthesis stands on the three source docs in this folder (each fully cited):
- `claude-code-cli-best-practices.md` — Anthropic official Claude Code docs.
- `karpathy-harness-engineering-principles.md` — Karpathy's real ideas + provenance of the "4 principles."
- `garry-tan-gstack-harness-best-practices.md` — gstack deep-dive + honest critique.

Cross-cutting primaries worth re-reading directly:
- [Best practices for Claude Code — Anthropic](https://code.claude.com/docs/en/best-practices)
- [Andrej Karpathy: "Software Is Changing (Again)" — YC](https://www.ycombinator.com/library/MW-andrej-karpathy-software-is-changing-again)
- [garrytan/gstack — repo](https://github.com/garrytan/gstack) and [Martin Fowler / Böckeler — Harness Engineering](https://martinfowler.com/articles/harness-engineering.html)
