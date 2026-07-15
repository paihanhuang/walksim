# Andrej Karpathy on "Harness Engineering": What He Actually Said

> **Confidence note (read first):** There is **no canonical, Karpathy-authored "4 principles of harness engineering."** That framing is a community remix. Karpathy did not coin "harness engineering" (that was **Mitchell Hashimoto**, ~Feb 2026) and did not publish any "4 principles" list (the viral "4 principles" come from a community `CLAUDE.md`, only *inspired by* his rants). What *is* genuinely Karpathy's: **"context engineering"** (June 2025), the **autonomy slider**, the **generation–verification loop / "keep AI on the leash,"** **"LLMs as fallible people spirits,"** and the **"Iron Man suit, not robot"** stance. Everything below separates his real statements from the community framing. Confidence in the core provenance conclusion: **high** (corroborated across multiple independent sources). Some 2026 dates rely on secondary blogs rather than originals.

_Researched 2026-06-18._

---

## TL;DR provenance

| Term / artifact | Real author | Date | Karpathy's? |
|---|---|---|---|
| **"Context engineering"** | Andrej Karpathy (amplifying Tobi Lütke) | June 25, 2025 | **Yes** |
| **Autonomy slider, generate/verify loop, "people spirits," "Iron Man suit"** | Andrej Karpathy ("Software Is Changing (Again)") | June 18, 2025 | **Yes** |
| **"Agentic engineering"** (retiring "vibe coding") | Andrej Karpathy | ~Feb 2026 | **Yes** |
| **"Harness engineering"** (the term) | Mitchell Hashimoto; popularized by OpenAI's Codex post | ~Feb 2026 | **No** |
| **The viral "4 principles" (`CLAUDE.md`)** | A community dev ("Forrest Chang"), *inspired by* Karpathy | 2025–26 | **No** — community-authored |
| **Harness-design "principle lists" (3/4/7/9 components)** | Böckeler, Osmani, LangChain/Trivedy, Sakasegawa, Augment Code, etc. | 2026 | **No** — build on his vocabulary |

The community has converged on the *concept* of an agent harness but **not on a single "N principles" list** — each author offers their own decomposition. Karpathy supplied the vocabulary and ethos; others coined "harness engineering" and its frameworks.

---

## 1. The principles/ideas Karpathy ACTUALLY stated

Drawn from his talk **"Software Is Changing (Again)"** (YC AI Startup School, June 18, 2025), his **Dwarkesh Patel interview** (Oct 17, 2025), and his **X posts**.

1. **Context engineering over prompt engineering** — "the delicate art and science of filling the context window with just the right information for the next step" (X, June 25, 2025). He adds the trade-off: "Too little or of the wrong form and the LLM doesn't have the right context… Too much or too irrelevant, and the LLM costs might go up, and performance might come down." (Related "LLM OS" framing he repeats: the LLM is the CPU, the **context window is RAM**, weights are ROM, external stores are disk — paraphrase, not a single quote.)
2. **The autonomy slider** — LLM apps should let the *user* dial how much autonomy they hand over (Cursor's Tab → Cmd-K → Cmd-I; Tesla Autopilot analogy), and slide it rightward only as fast as reliability allows.
3. **Keep AI "on the leash" + the generation–verification loop** — "they are doing the generation, and we as humans are doing the verification. It is in our interest to make this loop go as fast as possible." Don't let the AI emit huge diffs the human can't audit.
4. **LLMs are "fallible people spirits"** — stochastic simulations of people: encyclopedic recall, but hallucination, "jagged intelligence" (9.11 > 9.9), "anterograde amnesia" (context window = working memory, no consolidation), and gullibility.
5. **Partial-autonomy apps: the app manages context, orchestrates multiple LLM calls, and gives a verification GUI** — his "good properties" shared by Cursor and Perplexity. GUIs exploit human vision so auditing is fast.
6. **Build agent-legible infrastructure** — markdown / `lms.txt` for machine context; replace "click here" docs with `curl`-equivalent actions, because an agent can't click.
7. **"Iron Man suit, not Iron Man robot"** — build augmentation with human oversight now, keep a path to autonomy. "Demo is `works.any()`, product is `works.all()`." It's "the decade of agents," not the year.
8. **(Dwarkesh) Agents have core cognitive deficits** — they "don't have continual learning… you can't just tell them something and they'll remember it." On novel code they have "too much memory from all the typical ways of doing things on the internet" and regress to training-distribution conventions. "The models are not there… it's slop." "You're still very much the architect."

---

## 2. INSIGHTS — the reasoning behind each, and why it matters for harnesses

This is the load-bearing section: *why* each idea drives harness design.

- **Context engineering (why):** The model has no persistent memory; the context window *is* its working memory, and it degrades when stuffed ("anterograde amnesia," "context rot"). So output quality is bounded not by the model's intelligence but by what you place in the window *each step*. **Harness implication:** the harness — not the user — must curate, retrieve, compact, and offload context; this is the single component most cited inside every later harness framework.

- **Autonomy slider (why):** Because the model is fallible and tasks vary in risk, a fixed autonomy level is wrong for most work. Matching autonomy to task complexity minimizes blast radius while preserving speed on easy tasks. **Harness implication:** expose graduated modes (suggest → edit-region → full-agent) and per-action permission granularity; don't hardwire one-shot delegation.

- **Generation–verification loop / "on the leash" (why):** Generation is cheap and getting cheaper; **human verification is the bottleneck.** If the AI emits a 1000-line diff, verification collapses and the human "starts spinning." Speeding generation without speeding verification makes things worse. **Harness implication:** optimize for *small, reviewable increments*, fast visual diffs, concrete prompts, and tight loops — the harness's job is to make verification fast, not to maximize generation volume.

- **"Fallible people spirits" (why):** The model is simultaneously superhuman (recall) and sub-human (reasoning, memory, self-knowledge). Treating it as either a flawless oracle or a dumb tool both fail. **Harness implication:** design for a savant-with-deficits — lean on its recall, but wrap reasoning/memory failures with verification, guardrails against prompt injection, and externalized memory.

- **Partial-autonomy app properties (why):** Apps win not by a better model but by *managing context for the user, orchestrating multiple calls, and surfacing results so verification is fast and auditable.* Fallibility makes auditability the product, not a nicety. **Harness implication:** the harness is an orchestration + verification layer with a human-facing audit surface, not a thin prompt wrapper.

- **Agent-legible infrastructure (why):** Agents are "a new category of consumer of digital information" — human-only GUIs (click here) are dead ends for them. **Harness implication:** ship machine-actionable affordances (markdown docs, `AGENTS.md`/`CLAUDE.md`, structured tools, `curl`/CLI equivalents) so the agent can *act*, not just read.

- **"Iron Man suit, not robot" (why):** A demo only has to work once (`works.any()`); a product must work every time (`works.all()`), which today's fallibility can't guarantee autonomously. **Harness implication:** build for augmentation-with-oversight now; treat full autonomy as a slider you slide as reliability (and evals) improve — over a decade, not a year.

- **Cognitive deficits / "too much memory" (why):** Agents lack continual learning, so they forget project conventions across sessions; and they regress to internet-typical patterns, overriding bespoke architecture (his nanochat/DDP example). **Harness implication:** the harness must *externally supply* memory and *aggressively re-assert local conventions every step* — treat the agent's "common-pattern" bias as a known failure mode to guard against, especially on novel/research code.

---

## 3. How this maps to practical harness design

Karpathy's ideas map cleanly onto the consensus harness components (the community's de-facto checklist):

- **Context engineering** ← directly his term. Curate/retrieve/compact what enters the window each step; offload tool results; progressive disclosure; persist conventions in `AGENTS.md`/`CLAUDE.md` and re-inject them (counters the "forgets conventions" + "too much memory" deficits).
- **Tool / action design** ← his "agent-legible infrastructure." Structured, validated tools; the model never executes directly — the harness validates → checks permissions → executes → injects results. Provide CLI/`curl` equivalents, not "click" docs.
- **The control / agent loop** ← his generation–verification loop and orchestration ("orchestrate multiple LLM calls"). Perceive → plan → act → observe, with retries and graceful recovery; keep increments small so the loop stays fast and auditable.
- **Verification / evals** ← "we humans are doing the verification… make this loop fast." Fast visual diffs for humans; deterministic gates (linters, type-checkers, tests, CI, architectural-fitness checks) for the agent to self-verify. Repeatedly cited as the highest-leverage component.
- **Human-in-the-loop** ← "keep AI on the leash," "you're still the architect," autonomy slider. Plan-before-execute with sign-off, approval gates for destructive ops, interrupts, deny-first permission granularity.
- **Memory & durable state** ← counters "no continual learning." Filesystem + git, session snapshots, progress records re-loaded across sessions.

---

## 4. Provenance / confidence on the "4 principles" framing

- **The "4 principles of harness engineering by Karpathy" do not exist as a canonical, Karpathy-authored artifact.** Confidence: **high.**
- The conflation stitches three separate things: (a) **"context engineering"** — genuinely Karpathy (June 2025); (b) **"harness engineering"** — **Mitchell Hashimoto**, ~Feb 2026, popularized by OpenAI's Codex post; (c) **the viral "4 principles"** (Think Before Coding / Simplicity First / Surgical Changes / Goal-Driven Execution) — a community `CLAUDE.md` only *"derived from Karpathy's observations,"* whose own write-up states "Karpathy did not write it, did not publish it… did not endorse it." Those four are coding-style guidelines, **not a harness framework.**
- Harness *principle lists* that do exist are community-authored and vary in count: Böckeler (Guides/Sensors control-theory framing), Osmani (~9 components), LangChain/Trivedy (7 components), Sakasegawa (7 practices), Augment Code (3 layers — and it *explicitly de-attributes* the term from Karpathy), Firecrawl (4 subsystems). **No single agreed "N."**
- **Honest attribution to use:** credit Karpathy with *context engineering, the autonomy slider, the generate/verify loop, "people spirits," and the augmentation ethos*; credit *harness engineering* to Hashimoto/community; present any "4 principles" as a community synthesis inspired by him.

---

## Sources

**Karpathy — primary**
- [Andrej Karpathy: "Software Is Changing (Again)" — full transcript](https://singjupost.com/andrej-karpathy-software-is-changing-again/) (Singju Post; talk dated 2025-06-18)
- [Same talk — YC Startup Library page](https://www.ycombinator.com/library/MW-andrej-karpathy-software-is-changing-again) (Y Combinator, 2025-06-18)
- [Andrej Karpathy on Software 3.0 — Latent Space write-up](https://www.latent.space/p/s3) (cross-check: "tight leash," "jagged intelligence," "anterograde amnesia," "Iron Man suit," `works.any()`/`works.all()`)
- [Karpathy on "context engineering" (X)](https://x.com/karpathy/status/1937902205765607626) (2025-06-25)
- [Andrej Karpathy — "AGI is still a decade away" (Dwarkesh Podcast)](https://www.dwarkesh.com/p/andrej-karpathy) (2025-10-17)
- [Simon Willison's notes on the Dwarkesh interview](https://simonwillison.net/2025/Oct/18/agi-is-still-a-decade-away/) (2025-10-18)
- [Techmeme — Karpathy's talk, "fallible people spirits"](https://www.techmeme.com/250619/p25) (2025-06-19)

**Provenance of "harness engineering" and the "4 principles"**
- [Alex Rusin — The honest truth about the viral "Karpathy" CLAUDE.md (authorship)](https://blog.alexrusin.com/the-viral-karpathy-claude-md-file-heres-the-honest-truth-about-what-it-actually-is/)
- [Martin Fowler / Birgitta Böckeler — Harness Engineering for Coding Agent Users](https://martinfowler.com/articles/harness-engineering.html) (2026-04-02; no Karpathy attribution)
- [Louis Bouchard — Harness Engineering (credits Mitchell Hashimoto)](https://www.louisbouchard.ai/harness-engineering/)
- [Software Improvement Group — What is harness engineering? (credits Hashimoto, Feb 2026)](https://www.softwareimprovementgroup.com/blog/what-is-harness-engineering/)

**Community harness-design framings (for component consensus)**
- [Addy Osmani — Agent Harness Engineering](https://addyosmani.com/blog/agent-harness-engineering/) (2026-04-19)
- [LangChain / Vivek Trivedy — The Anatomy of an Agent Harness](https://www.langchain.com/blog/the-anatomy-of-an-agent-harness) (2026-03-10)
- [Augment Code — Harness Engineering for AI Coding Agents (de-attributes from Karpathy)](https://www.augmentcode.com/guides/harness-engineering-ai-coding-agents) (2026)
- [Sakasegawa — Harness Engineering Best Practices](https://nyosegawa.com/en/posts/harness-engineering-best-practices-2026/) (2026-03-09)
- [Firecrawl / Ninad Pathak — What Is an Agent Harness?](https://www.firecrawl.dev/blog/what-is-an-agent-harness) (2026-04-16)
