# Claude Code CLI — Official Best Practices

> Research compiled 2026-06-18 from Anthropic's official Claude Code documentation
> (`code.claude.com/docs`). The legacy `anthropic.com/engineering/claude-code-best-practices`
> URL now 308-redirects to the official docs best-practices page.

The single organizing principle behind almost every practice: **Claude's context
window fills fast, and model performance degrades as it fills.** Context is the
fundamental constraint. Most practices below exist to keep the context window
small, relevant, and high-signal — or to add a deterministic check that does not
depend on Claude "remembering" anything.

---

## (a) CLAUDE.md / project memory

**Practices**
- Run `/init` to generate a starter CLAUDE.md (it detects build systems, test
  frameworks, code patterns), then refine over time.
- Keep it **short and human-readable**. There is no required format.
- Include only what Claude can't infer: non-guessable Bash commands, code style
  that *differs* from defaults, test instructions/preferred runners, repo
  etiquette (branch/PR conventions), project-specific architecture decisions,
  env quirks (required env vars), and non-obvious gotchas.
- **Exclude**: anything Claude can read from the code, standard language
  conventions, detailed API docs (link instead), frequently-changing info,
  file-by-file descriptions, and self-evident advice ("write clean code").
- For each line ask: *"Would removing this cause Claude to make mistakes?"* If
  not, cut it.
- Use emphasis ("IMPORTANT", "YOU MUST") to raise adherence on critical rules.
- Check it into git so the team contributes; it compounds in value over time.
- Import other files with `@path/to/import` syntax (e.g. `@README.md`,
  `@docs/git-instructions.md`, `@~/.claude/my-project-instructions.md`).
- Use **skills** (`.claude/skills/*/SKILL.md`), not CLAUDE.md, for knowledge
  that's only *sometimes* relevant — skills load on demand without bloating
  every conversation.
- Customize compaction with a line like *"When compacting, always preserve the
  full list of modified files and any test commands."*

**File locations (all auto-loaded as applicable)**
- `~/.claude/CLAUDE.md` — all sessions (user global)
- `./CLAUDE.md` — project root, git-committed, team-shared
- `./CLAUDE.local.md` — personal project notes; add to `.gitignore`
- Parent dirs — pulled in automatically (good for monorepos)
- Child dirs — pulled in on demand when Claude reads a file there

**INSIGHTS**
- *Why short:* "Bloated CLAUDE.md files cause Claude to ignore your actual
  instructions." Important rules get lost in noise. **Failure mode:** the
  over-specified CLAUDE.md — if Claude keeps violating a rule it has, the file
  is probably too long. Fix = ruthless pruning, or convert the rule to a hook.
- *Why loaded every session:* it consumes context on every turn, so every line
  has a recurring cost. That's the economic reason to keep it lean and push
  occasional knowledge into skills.
- *Diagnostic signals:* Claude ignores a rule → file too long. Claude asks about
  something CLAUDE.md answers → phrasing is ambiguous. Treat CLAUDE.md like code:
  review when things break, prune regularly, and verify by watching whether
  behavior actually changes.

---

## (b) Permissions & settings.json

### settings.json file locations & precedence (highest → lowest)
1. **Managed/policy** (IT-deployed; MDM, registry, `/Library/Application Support/ClaudeCode/`) — cannot be overridden by anything, including CLI args.
2. **Command line** args — session-only.
3. **Local** `.claude/settings.local.json` — personal, gitignored.
4. **Project** `.claude/settings.json` — team-shared, git-committed.
5. **User** `~/.claude/settings.json` — you, across all projects.

Most settings *override* at higher precedence; **permission rules merge across
all scopes**, and a `deny` at any scope wins over an `allow` at any other scope.

### Key settings.json keys (selected, with recommended values)
- `$schema`: `"https://json.schemastore.org/claude-code-settings.json"` — enables editor autocomplete/validation.
- `model`: e.g. `"claude-sonnet-4-6"`; `fallbackModel`, `availableModels`, `enforceAvailableModels`.
- `effortLevel`: `"low" | "medium" | "high" | "xhigh"`.
- `permissions`: `{ allow, deny, ask, defaultMode, additionalDirectories, disableBypassPermissionsMode, disableAutoMode }`.
- `env`: env vars for all sessions (e.g. `{"CLAUDE_CODE_ENABLE_AUTO_MODE": "1"}`).
- `hooks`: lifecycle event handlers (see section c).
- `attribution`: `{ "commit": "...", "pr": "..." }` (replaces deprecated `includeCoAuthoredBy`); `includeGitInstructions`.
- `autoCompactEnabled`, `autoMemoryEnabled`, `claudeMd` (managed org-wide), `claudeMdExcludes`.
- `enableAllProjectMcpServers`, `enabledMcpjsonServers`, `disabledMcpjsonServers`.
- `defaultShell` (e.g. `"powershell"`), `editorMode` (`"vim"`), `outputStyle`, `cleanupPeriodDays`.
- `fileCheckpointingEnabled` (snapshots for `/rewind`), `plansDirectory`.
- `disableAllHooks`, `allowedHttpHookUrls`, `httpHookAllowedEnvVars`.

Live-reload: permissions, hooks, credentials. Restart required: `model`, `outputStyle`.

### Permission rules
- Three arrays: `allow`, `deny`, `ask`. Evaluation order is **deny → ask →
  allow**; first match wins; specificity does NOT change order.
- Format: `Tool` (whole tool) or `Tool(specifier)`. Bare tool name in `deny`
  removes the tool from Claude's context entirely; a scoped rule
  (`Bash(rm *)`) leaves the tool but blocks matching calls.
- **Bash patterns**: `*` matches any chars incl. spaces, at any position.
  Space-before-`*` enforces a word boundary (`Bash(ls *)` ≠ `lsof`); `Bash(ls*)`
  matches both. `:*` suffix == trailing ` *`. Claude Code is shell-operator
  aware — a rule must match every subcommand of a compound command (`&&`, `||`,
  `;`, `|`, `&`, newlines). Process wrappers (`timeout`, `time`, `nice`,
  `nohup`, `stdbuf`, bare `xargs`) are stripped before matching.
- **Read/Edit**: gitignore-style; `//abs`, `~/home`, `/project-root`,
  `./cwd-relative` anchors. `Read(.env)` == `Read(**/.env)`. Deny rules cover
  Claude's file tools AND recognized file Bash commands (`cat`, `head`, `sed`)
  but NOT arbitrary scripts — use sandboxing for OS-level enforcement.
- **WebFetch**: `WebFetch(domain:example.com)`, `WebFetch(domain:*.example.com)`.
- **MCP**: `mcp__server`, `mcp__server__*`, `mcp__server__tool`.
- **Agent**: `Agent(Explore)`, `Agent(my-custom-agent)` to gate subagents.
- Built-in **read-only Bash** commands (`ls cat echo pwd head tail grep find wc
  which diff stat du cd`, read-only `git`) never prompt in any mode.

**INSIGHTS**
- *Why deny-first, merge-across-scopes:* security must fail closed. A deny rule
  set centrally (managed) or by a teammate can never be loosened by a more
  specific local allow. **Failure mode prevented:** a repo or user accidentally
  (or maliciously) re-enabling something an admin forbade.
- *Why permissions are enforced by Claude Code, not the model:* prompts and
  CLAUDE.md shape what Claude *tries*; only rules/modes/hooks change what's
  *allowed*. **Failure mode:** trusting prose instructions as a security
  boundary (prompt injection / model error bypasses them).
- *Why argument-constraining Bash patterns are fragile:* `Bash(curl github.com
  *)` is trivially evaded (flags before URL, https vs http, redirects, vars,
  extra spaces). Anthropic explicitly recommends instead: deny `curl`/`wget`,
  use WebFetch with domain allowlist, and/or a PreToolUse hook. **Failure
  mode:** a false sense of a network boundary that doesn't hold.
- *Reducing prompt fatigue is itself a safety practice:* "After the tenth
  approval you're not really reviewing anymore, you're just clicking through."
  Allowlisting known-safe commands preserves genuine review for the rest.

---

## (c) Hooks

**What they are:** user-defined shell commands, HTTP endpoints, MCP tools, or LLM
prompts that fire automatically at lifecycle events. They receive event JSON on
stdin and return decisions. Unlike CLAUDE.md (advisory), **hooks are
deterministic** — the action happens with zero exceptions.

**Config shape** (`.claude/settings.json`), three nesting levels —
event → matcher group → handler:
```json
{
  "hooks": {
    "PreToolUse": [
      { "matcher": "Bash",
        "hooks": [ { "type": "command",
                     "command": "${CLAUDE_PROJECT_DIR}/.claude/hooks/validate.sh" } ] }
    ]
  }
}
```

**Events:** per-session (`SessionStart`, `SessionEnd`), per-turn
(`UserPromptSubmit`, `Stop`, `StopFailure`), per-tool-call (`PreToolUse`,
`PostToolUse`, `PostToolUseFailure`, `PermissionRequest`), plus async
(`FileChanged`, `ConfigChange`, etc.) and `SubagentStart`.

**Handler types:** `command` (shell), `http`, `mcp_tool`, `prompt` (yes/no LLM
check), `agent` (multi-tool verification).

**Matchers:** exact (`"Bash"`), list (`"Edit|Write"`), regex (`"^Notebook"`,
`"mcp__.*__write"`), omitted/`"*"` for all. Match the **canonical** tool name.

**Decision control:**
- Exit 0 → success; JSON on stdout processed as structured output.
- **Exit 2 → blocking error**; stderr becomes the reason; action prevented.
- Other exit → non-blocking; continues.
- JSON fields: `permissionDecision: "deny"` (PreToolUse), `decision: "block"`,
  `additionalContext` (inject info for next model call), `continue: false`
  (stop session), `suppressOutput: true`.

**Common uses:** lint/typecheck after edits (`PostToolUse`), block writes to
protected dirs (`PreToolUse`), inject current branch/open issues at startup
(`SessionStart` → `additionalContext`), run tests as a `Stop` gate, auto-approve
safe patterns (`PermissionRequest`). Claude can write hooks for you ("write a
hook that runs eslint after every file edit"). Browse with `/hooks`.

**Hooks + permissions:** PreToolUse hooks run *before* the prompt and can deny,
force-prompt, or allow. But hook decisions **do not bypass** deny/ask rules —
those still apply. A hook exiting 2 blocks even when an allow rule matches. To
run all Bash without prompts except a blocklist: `allow "Bash"` + a PreToolUse
hook that rejects the specific commands.

**INSIGHTS**
- *Why hooks over CLAUDE.md for "always do X":* CLAUDE.md is advisory and can be
  forgotten as context fills; hooks are guaranteed. **Failure mode:** relying on
  a memory instruction for something that must happen every time (lint, format,
  block a path) and having Claude skip it under context pressure. The docs
  state: use hooks for "actions that must happen every time with zero
  exceptions."
- *Why Stop hooks close the verification loop:* a Stop hook re-runs your check
  (tests/build) and blocks the turn from ending until it passes — letting an
  unattended run finish *correctly*. (Claude Code overrides after 8 consecutive
  blocks to avoid infinite loops.)
- *Why exit-2-with-stderr:* the block reason is fed back to Claude so it can
  self-correct, turning a guardrail into a teaching signal.

---

## (d) Subagents

**What:** specialized assistants in their own context window with own system
prompt, tool access, and permissions. Claude delegates by matching the
subagent's `description`. Built-ins: **Explore** (Haiku, read-only, fast search),
**Plan** (read-only research during plan mode), **general-purpose** (all tools).

**Config** — Markdown + YAML frontmatter in `.claude/agents/` (project) or
`~/.claude/agents/` (user). Use `/agents` to create.
```markdown
---
name: code-reviewer            # required, lowercase-hyphen; hooks get this as agent_type
description: Reviews code for quality and best practices   # required; drives delegation
tools: Read, Glob, Grep        # optional allowlist; inherits all if omitted
model: sonnet                  # optional: sonnet|opus|haiku|fable|<full id>|inherit (default inherit)
permissionMode: default        # optional
---
System prompt body...
```
- Restrict tools via `tools` (allowlist) or `disallowedTools` (denylist).
- `Agent(worker, researcher)` in `tools` scopes which sub-subagents it may spawn.
- Invoke explicitly: *"Use a subagent to review this code for security issues."*
- Disable a built-in via `permissions.deny: ["Agent(Explore)"]`; disable all
  delegation by denying the `Agent` tool.

**INSIGHTS**
- *Why subagents are "one of the most powerful tools available":* context is the
  fundamental constraint, and subagent exploration (reading many files) happens
  in a *separate* window — only the summary returns. **Failure mode prevented:**
  "infinite exploration" that floods the main context with file dumps you'll
  never reference again.
- *Why a fresh-context reviewer matters:* an adversarial review subagent sees
  only the diff + criteria, not the reasoning that produced the change, so it
  grades on the result's own terms (the agent doing the work isn't the one
  grading it). Caveat from the docs: a reviewer told to find gaps *will* find
  some; instruct it to flag only correctness/requirement gaps, or you invite
  over-engineering.
- *Cost lever:* route cheap/bulk work to Haiku via `model:`.

---

## (e) Explore → Plan → Code → Commit, and TDD

**The four-phase workflow:**
1. **Explore** — enter plan mode (`Shift+Tab` or `/plan`); Claude reads files,
   answers questions, makes no changes.
2. **Plan** — ask for a detailed implementation plan; `Ctrl+G` opens it in your
   editor to edit directly before proceeding.
3. **Implement** — leave plan mode; Claude codes and verifies against the plan.
4. **Commit** — descriptive message + PR.

Skip planning for clearly-scoped small changes (typo, log line, rename) — *"If
you could describe the diff in one sentence, skip the plan."* Plan when the
approach is uncertain, the change spans multiple files, or the code is
unfamiliar.

**TDD / verification-first:** Always give Claude a check it can run — a test
suite, build exit code, linter, fixture diff, or screenshot comparison. Have one
Claude write tests, another write code to pass them. Write a failing test that
reproduces a bug, then fix it. Have Claude **show evidence** (test output,
command + result, screenshot) rather than asserting success.

**Gate strength (escalating):** in-one-prompt "run the check and iterate" →
`/goal` condition (re-checked every turn) → **Stop hook** (deterministic gate) →
verification subagent / adversarial review (second opinion).

**Spec workflow for larger features:** "Interview me using the AskUserQuestion
tool... then write a complete spec to SPEC.md." Start a *fresh session* to
execute it. Best specs are self-contained: name files/interfaces, state what's
out of scope, end with an end-to-end verification step.

**INSIGHTS**
- *Why separate explore/plan from code:* "Letting Claude jump straight to coding
  can produce code that solves the wrong problem." **Failure mode:** confidently
  building the wrong thing.
- *Why verification is non-negotiable:* "Claude stops when the work looks done.
  Without a check, 'looks done' is the only signal, and *you* become the
  verification loop." A check closes the loop autonomously. **Failure mode:**
  the trust-then-verify gap — plausible code that silently misses edge cases.
  "If you can't verify it, don't ship it."
- *Why a fresh session for the spec:* clean context focused entirely on
  implementation + a written reference beats a context already cluttered with
  the interview.

---

## (f) Context management

- `/clear` between unrelated tasks — reset context entirely. **The single most
  repeated practice.**
- `/compact <instructions>` for targeted summarization (e.g. `/compact Focus on
  the API changes`); auto-compaction triggers near limits.
- `Esc` to interrupt (context preserved, redirect); `Esc Esc` / `/rewind` for
  checkpoints (restore conversation/code/both, or summarize from/up-to a point).
- `/btw` for side questions that never enter conversation history.
- Delegate research to **subagents** to keep exploration out of main context.
- Track usage with a custom status line.
- Resume work: `claude --continue` / `--resume`; name sessions with `/rename`
  (treat them like branches).

**Named failure patterns (from the docs):**
- *Kitchen-sink session* (mixing unrelated tasks) → `/clear` between tasks.
- *Correcting over and over* → after **two** failed corrections, `/clear` and
  write a better initial prompt incorporating what you learned. "A clean session
  with a better prompt almost always outperforms a long session with accumulated
  corrections."
- *Infinite exploration* → scope narrowly or use subagents.

**INSIGHTS**
- Every failed-correction round leaves the failed approach polluting context,
  which biases Claude toward the same mistakes — clearing is faster than fighting
  the clutter.
- Checkpoints enable a "try something risky, rewind if it fails" style — but
  they track only Claude's changes, **not a git replacement**.

---

## (g) MCP & tools

- **Prefer CLI tools** (`gh`, `aws`, `gcloud`, `sentry-cli`) — most
  context-efficient way to reach external services. Install `gh` so GitHub
  actions are authenticated (avoids rate limits). Claude can learn unknown CLIs:
  *"Use 'foo-cli --help' to learn the tool, then solve A, B, C."*
- **MCP servers** (`claude mcp add`) for issue trackers, databases, monitoring,
  Figma, etc. Approve project `.mcp.json` servers via `enabledMcpjsonServers` /
  `enableAllProjectMcpServers`; gate via `mcp__server__tool` permission rules.
- Provide rich context: `@file` references, paste/drag images, give doc URLs
  (allowlist domains via `/permissions`), pipe data (`cat error.log | claude`).
- Skills (`.claude/skills/*/SKILL.md`) and plugins (`/plugin`) bundle reusable
  knowledge/tools; code-intelligence plugins add symbol navigation for typed
  languages.

**INSIGHTS**
- *Why CLI over raw API/MCP when available:* fewer tokens per interaction; the
  context budget is the constraint, so the leanest integration wins.
- *Why allowlist doc domains:* lets Claude "fetch what it needs" without a prompt
  per URL, keeping you in the loop only for unknown domains.

---

## (h) Safe use of permission modes (incl. bypassPermissions)

| Mode | Runs without asking | Best for |
|---|---|---|
| `default` | reads only | sensitive work, getting started |
| `acceptEdits` | reads + file edits + common fs cmds (`mkdir touch rm mv cp sed`) in-scope | iterating on code you'll review after |
| `plan` | reads only (no edits) | exploring before changing |
| `auto` | everything, with background classifier checks | long tasks, prompt fatigue (research preview) |
| `dontAsk` | only pre-approved (`allow`) tools; ask-rules denied | locked-down CI/scripts |
| `bypassPermissions` | everything | isolated containers/VMs ONLY |

- Set persistently via `permissions.defaultMode`; cycle live with `Shift+Tab`
  (default → acceptEdits → plan). `auto`/`bypassPermissions`/`dontAsk` are not in
  the default cycle.
- In **every mode except bypassPermissions**, writes to **protected paths** are
  never auto-approved (`.git`, `.claude`, `.vscode`, `.idea`, `.husky`, `.cargo`,
  `.devcontainer`, shell rc files, `.npmrc`, `.mcp.json`, etc.). `allow` rules do
  NOT pre-approve protected-path writes.

**bypassPermissions / dangerous modes — guidance:**
- Started only via `--permission-mode bypassPermissions` or
  `--dangerously-skip-permissions` (equivalent). Cannot be entered from a session
  not started with an enabling flag.
- Skips prompts including writes to protected dirs. **Exceptions that still
  prompt:** explicit `ask` rules, and circuit-breaker removals of filesystem
  root / home (`rm -rf /`, `rm -rf ~`).
- Refuses to start as root/sudo on Linux/macOS (unless inside a recognized
  sandbox / dev container running as non-root).
- "Offers **no protection against prompt injection or unintended actions**." Use
  ONLY in isolated environments (containers, VMs, dev containers without
  internet) where Claude cannot damage the host.
- **Block it:** set `permissions.disableBypassPermissionsMode: "disable"`
  (works from any scope; typically managed settings). Likewise
  `permissions.disableAutoMode: "disable"`.
- Repos cannot grant themselves dangerous modes: Claude Code on the web ignores
  `defaultMode: bypassPermissions`/`dontAsk` from checked-in settings, and
  ignores `defaultMode: auto` from project/local files (must be in
  `~/.claude/settings.json`).

**Auto mode safety model (the recommended alternative to bypass):**
- A separate **classifier model** reviews each action before it runs, blocking
  scope escalation, unknown infrastructure, and hostile-content-driven actions,
  while letting routine work through. Blocked by default: `curl | bash`, prod
  deploys/migrations, mass cloud deletion, IAM grants, force-push / push to
  `main`. Allowed: local file ops, declared-dependency installs, read-only HTTP,
  pushing to your own branch.
- On entering auto mode, broad code-exec allow rules (`Bash(*)`,
  `Bash(python*)`, package-manager run, `Agent`) are **dropped**; restored on
  exit. Narrow rules (`Bash(npm test)`) carry over.
- Boundaries you *state in conversation* ("don't push") are treated as blocks —
  but re-read from the transcript each check, so compaction can lose them. For a
  hard guarantee, use a **deny rule** instead.
- Falls back to prompting after 3 consecutive / 20 total blocks; in `-p`
  non-interactive mode, repeated blocks **abort** the session.

**INSIGHTS**
- *Why bypass is container-only:* it removes the only runtime check on a
  prompt-injected or mistaken action. The protected-path list exists precisely
  because accidental writes to `.git`/`.claude` corrupt your repo or the agent's
  own config — bypass discards even that.
- *Why auto mode is the safer "fewer prompts" answer:* it keeps a background
  check and degrades gracefully (falls back to prompts, aborts headless),
  whereas bypass offers nothing. Anthropic explicitly steers users to auto over
  bypass.
- *Why repos can't self-grant dangerous modes:* prevents a malicious or careless
  checked-in `settings.json` from silently disarming a collaborator. Dangerous
  defaults must live in user/managed scope, a deliberate human action.

---

## Sources

- [Best practices for Claude Code](https://code.claude.com/docs/en/best-practices) — Anthropic official docs (accessed 2026-06-18). Note: `https://www.anthropic.com/engineering/claude-code-best-practices` 308-redirects here.
- [Claude Code settings reference](https://code.claude.com/docs/en/settings) — Anthropic (accessed 2026-06-18).
- [Configure permissions](https://code.claude.com/docs/en/permissions) — Anthropic (accessed 2026-06-18).
- [Choose a permission mode](https://code.claude.com/docs/en/permission-modes) — Anthropic (accessed 2026-06-18).
- [Hooks](https://code.claude.com/docs/en/hooks) / [Hooks guide](https://code.claude.com/docs/en/hooks-guide) — Anthropic (accessed 2026-06-18).
- [Create custom subagents](https://code.claude.com/docs/en/sub-agents) — Anthropic (accessed 2026-06-18).
- [CLAUDE.md / memory](https://code.claude.com/docs/en/memory) — Anthropic (referenced).
- [Sandboxing](https://code.claude.com/docs/en/sandboxing), [Configure auto mode](https://code.claude.com/docs/en/auto-mode-config) — Anthropic (referenced).
