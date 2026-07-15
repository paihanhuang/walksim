# Garry Tan's "gstack": Harness Engineering Lessons from a Shipped Setup

> **Scope & confidence (read first):** "Gstack" refers to **`github.com/garrytan/gstack`** —
> Garry Tan's (YC President & CEO) personal Claude Code configuration, open-sourced (MIT) in
> ~March 2026. It is the most-watched *real, in-use* coding harness from a non-vendor, which is
> exactly why it's worth studying. **High confidence** on what it *is* and *how it's structured*
> (drawn from the repo README + Tan's own `ON_THE_LOC_CONTROVERSY.md`). **Lower confidence** on the
> productivity numbers (self-reported; Tan himself concedes they're not fully proven — see §7) and on
> exact adoption counts (they climbed from ~20K to ~90K GitHub stars over weeks, so any single figure
> is stale). Treated honestly below: the *design* is the signal; the *hype* is noise.

_Researched 2026-06-28._

---

## TL;DR

gstack turns Claude Code from a reactive autocomplete into a **role-based software process**. It ships
~23 "skills" + ~8 "power tools" as Markdown slash-commands that play the parts of a startup team —
**CEO, Eng Manager, Designer, DevEx, Staff Engineer (review), QA, Security Officer, SRE, Release
Engineer, Technical Writer** — chained into a **Think → Plan → Build → Review → Test → Ship → Monitor →
Reflect** pipeline where each stage's output feeds the next. The load-bearing bet: **"structured
prompts, not custom tooling, are the right abstraction layer for AI-assisted development."** Everything
is Markdown on Claude Code's native skill mechanism — no proprietary runtime. The discipline (forced
scoping before code, adversarial review after) *is* the product, not the commands.

**Why it matters to this project:** gstack is, independently, almost the same idea as this repo's
**V-model SDLC governance layer** — impose roles, gates, scoping-before-build, and adversarial review on
an AI agent. Studying it is studying a parallel-evolved cousin of our own contract. (See the synthesis
doc for the full mapping.)

---

## 1. What gstack actually is (the facts)

- **Repo:** `garrytan/gstack`, **MIT** ("Free forever. Go build something."). No premium tier, no waitlist.
- **Form factor:** Markdown skills + a few shell/TS tools, installed to `~/.claude/skills/gstack`,
  invoked as slash-commands inside Claude Code. *Co-authored with Claude Opus itself* (visible in the
  commit history) — the harness was largely built by the agent it harnesses.
- **Multi-host:** one declarative `HostConfig` installs the same skills into **~10 agents** — Claude
  Code (primary), OpenAI Codex CLI, Cursor, OpenCode, Factory Droid, Slate, Kiro, Hermes, GBrain,
  OpenClaw. Vendor-portable by design.
- **Requirements:** Git, Bun ≥1.0, Node (Windows fallback only). One-line install + `./setup`; a
  `--team` mode commits gstack into a repo and auto-updates per-checkout.
- **Adoption:** went viral fast — tens of thousands of stars within days, ~90K within weeks; Tan reports
  ~15K unique installs, ~305K skill invocations, 95% success rate at the time he published metrics. (Exact
  numbers vary by source/date; the *trajectory* is the reliable part.)

> Note: a few early secondary write-ups said "six skills." That was an early version; the repo's own
> README documents ~23 skills + 8 power tools. Trust the repo over the recaps.

---

## 2. The pipeline and the "team"

The whole system is one opinionated flow. Each phase has defined inputs/outputs and hands off to the next:

```
THINK    /office-hours      "Six forcing questions that reframe your product before you write code"
                            → design doc, challenge memos
PLAN     /plan-ceo-review    strategic scope (Expansion / Selective / Hold / Reduction)
         /plan-eng-review    architecture, data-flow diagrams, edge cases, test matrix
         /plan-design-review design audit, 0–10 ratings, "what a 10 looks like"
         /plan-devex-review  developer-friction triage
         /autoplan           runs the above automatically; surfaces only high-uncertainty "taste" calls
                            → implementation plan + test matrix
BUILD    /spec               vague intent → precise executable spec (reads code first)
         (implementation; optional checkpoint WIP commits)
REVIEW   /review             Staff Engineer: auto-fix obvious bugs, flag judgment calls
         /codex              OPTIONAL cross-review from OpenAI (independent second model)
         /design-review      post-launch design audit with atomic commits + screenshots
TEST     /qa                 real Chromium browser: find bugs → fix → generate regression test → re-verify
         /benchmark          Core Web Vitals + resource sizes, before/after
SHIP     /ship               sync main, run tests, audit coverage, push, open PR
         /land-and-deploy    merge, wait CI, deploy, verify prod health
         /document-release   auto-update docs to match the diff (Diataxis coverage map)
MONITOR  /canary             post-deploy watch for console errors / perf regressions
REFLECT  /retro  /learn      shipping-streak retro; prune & promote durable project patterns
```

Safety/scope power-tools cut across every phase: **`/careful`** (warn before `rm -rf`, `DROP TABLE`,
force-push), **`/freeze`** (restrict edits to one directory), **`/guard`** (both), **`/investigate`**
(systematic root-cause, *stops after 3 failed fixes* instead of thrashing), **`/cso`** (OWASP Top-10 +
STRIDE threat model as a pre-ship gate).

---

## 3. Design philosophy (the bets), in Tan's framing

1. **"Process over autocomplete."** Structure enforces rigor rather than hoping the model guesses right.
2. **Structured prompts > custom tooling** as the abstraction layer. Every skill is a declarative
   Markdown file on the agent's existing mechanism — "no proprietary runtime," so teams *fork the
   opinions* instead of patching infrastructure.
3. **Scope before you build; review after.** `/office-hours` interrogates assumptions first; `/plan-*`
   locks architecture before a line is written; `/review` + `/qa` + `/cso` gate before merge.
4. **Roles create cognitive boundaries.** Don't ask one model to juggle every concern at once — give it
   one hat at a time, each with its own context and decision tree. This "surfaces disagreements early"
   (a flaw caught in eng-review before implementation).
5. **Constraint propagation, not isolated prompts.** "Each skill feeds output into the next" — a race
   condition flagged in review becomes a QA test case. The handoffs are the point.
6. **Multi-model review as a first-class feature, not a platform choice** (`/codex` bridges Claude↔OpenAI).
7. **Everything human-readable.** Markdown state + slash-commands = a process a human can audit, fork, and version.

Tan's own framing of the mindset: AI effectiveness *"hinges on structured processes, defined roles, and
rigorous review, much like human teams operate"*; agents are *"extensions of human judgment, not
replacements"*; the founder's job is **strategic direction and verification**, not typing code.

---

## 4. How it handles the hard parts of harness engineering

- **Context / memory:** `/learn` reviews, searches, and *prunes* project patterns; a persistent
  knowledge base ("gbrain") is indexed from the repo and kept in sync (`/sync-gbrain`, which also updates
  `CLAUDE.md`). Domain tricks are saved per-hostname and *promoted to global only after 3 successful uses*
  — a nice "earn your place in global context" rule that fights context bloat.
- **Crash/continuity:** opt-in checkpoint mode auto-commits `WIP:` commits with a structured
  `[gstack-context]` body; `/context-restore` rebuilds session state from them; `/ship` squashes the WIP
  commits before the PR so bisect history stays clean.
- **Verification (the core):** layered gates — auto-fix+flag (`/review`), independent cross-model opinion
  (`/codex`), **real-browser** behavioral test (`/qa`, ~100ms/command), coverage delta (`/ship`),
  security gate (`/cso`), perf baseline (`/benchmark`). "Taste gates" (`/autoplan`) escalate *only*
  high-uncertainty decisions to the human — humans review what matters, not everything.
- **Parallelism:** runs **10–15 parallel Claude Code sessions** via **Conductor**, each in an isolated
  workspace; the per-sprint Think→…→Ship process is what *keeps the parallel agents from colliding*.
  `/pair-agent` shares one browser across vendors with per-agent tab isolation + scoped tokens + rate limiting.

**The shape of the insight:** parallelism is unlocked *by* process, not in spite of it. Structure is the
guardrail that makes 15 simultaneous agents safe.

---

## 5. INSIGHTS — what a harness builder should actually take

- **The harness, not the model, is the leverage.** gstack adds zero model capability; it adds *process*.
  The reproducible win is "structured handoffs enforce reviews a developer would skip in real-time." If
  your harness's value disappears when you remove the discipline, you built the wrong thing.
- **Roles = context scoping in disguise.** A "persona" is really a way to put *only the relevant context +
  decision criteria* in the window for one step. This is Karpathy's "context engineering" wearing a
  costume. The role framing is memorable; the mechanism is curation.
- **Output→input chaining beats one mega-prompt.** Treating each stage's artifact (design doc → plan →
  diff → review findings → QA tests) as the next stage's input is "constraint propagation." It's also how
  you keep each step's context small and verifiable.
- **Earn-your-way-into-global-memory.** Promote a learned pattern to always-loaded context only after it
  proves useful N times. A concrete, copyable rule for fighting `CLAUDE.md`/memory bloat.
- **Make humans review the *uncertain*, not the *routine*.** "Taste gates" are the practical answer to
  Karpathy's "verification is the bottleneck": auto-pass the deterministic, escalate the judgment calls.
- **Cross-model review is cheap insurance.** A second model (`/codex`) catches what the generating model
  is blind to — generator ≠ verifier, taken literally across vendors.
- **Real-environment verification > asserted success.** `/qa` opening an actual browser is the antidote to
  the "exit code 0, all green, but wrong" failure (a real gstack-thread war story: a 70-minute loop that
  wrote a staging URL into prod config while reporting success).
- **Build the harness *in* the harness.** gstack was largely written by Claude Opus. A good harness should
  be able to extend itself (cf. "Claude can write hooks for you" in the Anthropic docs).

---

## 6. The honest critique (don't skip this)

gstack drew as much heat as praise. The criticisms are real and instructive for anyone copying it:

- **"It's just a bunch of prompts."** Vlogger Mo Bitar ("AI is making CEOs delusional") and others argue
  there's no novel tech — devs already write ad-hoc versions. *Rebuttal:* true, and that's the point;
  the value is the *curated, sequenced* discipline, not invention. But it means **the moat is taste, not code**.
- **Credibility / distribution bias.** Founder Sherveen Mashayekhi: *"if you weren't the CEO of YC, this
  wouldn't be on PH."* The reach is partly positional, not purely meritocratic.
- **Context explosion / token cost.** Running 23 specialist skills sequentially multiplies tokens and
  latency. More gates ≠ free. For a small change, the full pipeline is overkill (this project's own
  *task-sizing* tiers exist precisely to avoid that trap).
- **Prompt brittleness.** The "no custom tooling" bet *depends on prompt stability*; as models change,
  the skills need maintenance. Declarative is cheap to fork but not free to keep working.
- **Process rigidity.** Teams with non-linear workflows may find the sprint structure constraining; the
  git-clone-and-copy install "works great solo" but strains when different repos need divergent gates.
- **False-green is still possible.** Even with gates, an agent reported "all green" while looping on a
  wrong edit — verification gates reduce but don't eliminate the trust-then-verify gap.
- **Telemetry concern.** Community asked whether usage signal flows back to YC (opt-out exists).

**Net:** adopt the *patterns* (roles, scoping gate, adversarial+cross-model review, real-env QA, scoped
safety tools, earn-into-memory). Be skeptical of the *productivity headline* and the *one-size pipeline*.

---

## 7. The productivity claim, treated honestly

Tan's headline ("~810× my 2013 pace; 11,417 logical lines/day") is the most-attacked part — and to his
credit, `ON_THE_LOC_CONTROVERSY.md` is unusually self-critical:

- He **concedes "LOC is a garbage metric"** (Dijkstra "lines spent," the Gates aircraft-weight aphorism),
  valid for 40 years.
- He measures **logical SLOC** (no blanks/comments) across 41 repos, then applies a **conservative
  deflation** (assume AI code is 2× verbose → ~408×; even at 100× deflation it's still ~8×).
- He pivots to **quality signals**: revert rate 2.0% (healthy 1–3% OSS range), 6.3% post-merge fix rate,
  tests grown to 2,000+ ("testing infrastructure is **the unlock**"), third-party "slop-scan" violations
  cut 62% after refactor.
- **Caveats he volunteers:** numbers are **greenfield, not maintenance** ("scaling to 10M-line systems —
  my number doesn't prove that"); quality "a skeptic can discount"; *"if two years from now 80% of what I
  shipped is dead, the critique will have teeth."*
- **His real claim:** *"Time to first user is the metric that matters, not LOC"* — desire-to-deployment
  compressed "from 3 weeks to 3 hours." And the systemic bet: *"One engineer in 2026 has the output of a
  small team in 2013… I'm betting the ground moved for you too."*

**Takeaway for us:** the *honest* productivity metrics to track are **time-to-first-user, revert rate,
post-merge fix rate, and test growth** — not lines. That trio maps cleanly onto our `qa-*` proof discipline.

---

## 8. Cross-links to the other two sources

- **gstack ↔ Karpathy:** the README says gstack guards against **"Karpathy's four failure modes"** (wrong
  assumptions → `/office-hours`; overcomplexity → `/review`; orthogonal edits → "Confusion Protocol";
  imperative-over-declarative → `/ship`). **Honesty flag:** our Karpathy provenance research shows these
  "four" are the *community-synthesized* "4 principles" (Think Before Coding / Simplicity First / Surgical
  Changes / Goal-Driven Execution) — *inspired by* Karpathy but **not authored by him**. gstack
  propagates the popular misattribution. The ideas are sound; the credit line is folklore. (See
  `karpathy-harness-engineering-principles.md`.)
- **gstack ↔ Anthropic docs:** gstack is a maximalist instantiation of the official advice — skills over a
  bloated `CLAUDE.md`, subagent/role context isolation, verification-first, "show evidence," cross-model
  review, safety scoping. (See `claude-code-cli-best-practices.md`.)
- **gstack ↔ this project:** near-isomorphic to our V-model — roles, human gates, adversarial QA,
  scoping-before-build, traceability. What we can borrow is detailed in the synthesis doc.

---

## Sources

**Primary (Garry Tan / repo)**
- [garrytan/gstack — repository & README](https://github.com/garrytan/gstack) (accessed 2026-06-28)
- [gstack — `docs/ON_THE_LOC_CONTROVERSY.md`](https://github.com/garrytan/gstack/blob/main/docs/ON_THE_LOC_CONTROVERSY.md) (Tan's metrics defense + caveats)
- [Garry Tan on Building with AI Agents — StartupHub.ai](https://www.startuphub.ai/ai-news/artificial-intelligence/2026/garry-tan-on-building-with-ai-agents) (his stated philosophy)
- [Inside Garry Tan's AI Coding Setup — YC Startup Library](https://www.ycombinator.com/library/OW-inside-garry-tan-s-ai-coding-setup) (primary, but body is JS-rendered; not fully extractable via fetch)

**Analysis & criticism (balance)**
- [Why Garry Tan's Claude Code setup has gotten so much love, and hate — TechCrunch](https://techcrunch.com/2026/03/17/why-garry-tans-claude-code-setup-has-gotten-so-much-love-and-hate/)
- [gstack — Hacker News discussion](https://news.ycombinator.com/item?id=47355173)
- [Garry Tan open-sources gstack: what developers should know — Augment Code](https://www.augmentcode.com/learn/garry-tan-gstack-claude-code)
- [What Is GStack? — MindStudio](https://www.mindstudio.ai/blog/what-is-gstack-gary-tan-claude-code-framework)
- [GStack Tutorial (10K LOC/week) — SitePoint](https://www.sitepoint.com/gstack-garry-tan-claude-code/)
- [The Garry Tan Stack: A Definitive Guide — Little Might](https://www.littlemight.com/garry-tan-gstack-definitive-guide/)
