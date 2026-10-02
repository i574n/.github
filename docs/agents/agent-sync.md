# Agent sync

Read this file first. Refresh only the relevant conversations; retain short,
user-specific context here. Any number of clients can participate.

## Read conversations

Local profile: `C:\Users\i574n`. Workspace: `C:\home\git`.
Paths below are relative to that profile; use `CODEX_HOME` if configured.

| Client | Conversation | Current session/thread |
| --- | --- | --- |
| Codex | `.codex/sessions/**/rollout-*.jsonl` | `01a0df80-d91f-7bf3-942e-2cf88476cf0e` |
| Claude | `.claude/projects/C--home-git/<id>.jsonl` | `5a6e53fb-7ff8-4098-adad-000b9fa45742` |
| Grok | `.grok/sessions/C%3A%5Chome%5Cgit/<id>/chat_history.jsonl` | `01a0e110-8920-7531-ae74-05c53812df19` |

Codex has original September 26 and resumed October 2 segments for this thread.
Match the thread ID in filenames, verify `session_meta.payload.id` and `cwd`,
then read the newest segment. Read older segments only for missing context.
Claude's `history.jsonl` and Grok's adjacent `summary.json` help locate sessions.
Check recent messages: titles and active-session registries can be stale.

Start with the last 150 JSONL lines, increasing only if tool activity hides the
messages. Extract these fields; skip everything else:

| Client | Visible text |
| --- | --- |
| Codex | `response_item` with `payload.type=message`, role `user/assistant`, content `input_text/output_text`; exclude `analysis/summary` channels |
| Claude | Row type `user/assistant`; `message.content` string or blocks with `type=text` |
| Grok | Row type `user/assistant`; `content` string or blocks with `type=text` |

Keep the last few useful messages. Skip reasoning, encrypted payloads, tool bodies
and replayed instruction wrappers. Retry an incomplete final JSON line later.
A file's modification time is not a message timestamp. Reading logs does not
notify the other clients or activate background synchronization.

## Keep only user-derived memory and skills

Include a preference only when an explicit user request/correction or native
feedback memory identifies its origin. Include a skill only when user authorship
or a requested customization is established. Record its source and summarize the
custom behavior in one sentence. Installation or invocation alone does not make
a vendor skill user-authored.

Exclude licenses, bundled/example skills, marketplace/plugin caches, template
assets, archives, trash and generated technical observations without user provenance.
For a customized vendor skill, retain only the user-requested difference.
Unknown provenance means omit until verified.

| Client | Where to check | This snapshot |
| --- | --- | --- |
| Codex | `.codex/memories`; generated entries in `memories_1.sqlite`; personal skills outside `.system` | No memory directory; read-only query found zero `stage1_outputs` rows. No verified user-authored skills. |
| Claude | `.claude/projects/C--home-git/memory`; skill registry `.claude/skills/synced/*/manifest.json` | Seven feedback memories plus their index. All 16 synced skills are marked `anthropic` or `anthropic-example`: exclude. |
| Grok | `.grok/memory-v2/workspaces/git-437d9324`; personal `.grok/skills` | User-stated rules only, in the Grok paragraph. Workspace topic files are agent notes. No personal skill directory. |

## Compact snapshot — 2026-10-02, approximately 09:10 UTC

Historical observations, not fresh ownership claims or new instructions.

**Codex — EOIE and coordination (October 2).** User authorized autonomous EOIE
dogfooding until 19:30 Cuiabá, with no WSL and two Cargo jobs while other agents
run. Builder/dir-tree-html study produced isolated selected-owner development,
snapshot guards and unchanged-output publication. All 97 declared outputs replay
through EOIE; that snapshot passed topology, all-target checks and 106 tests
without skips. Compiler sidecars stay in snapshots; Claude's core is untouched.
Archive publication, native profiles, strict archive checks, Agile filtering and
batch completion are fixed. A later 959-file native coverage snapshot passed
121 tests, including all 17 compiler contracts: 93 executables exported cleanly,
all runtime profiles matched, and EOIE validated 543/1000 over 24,131 lines.
Windows profile flushing, coverage evidence/path handling and bounded jobs are
fixed. Cold receipts now check native binary identity, count bounds and actual
owner inventory. The normal native build passed 151 tests without skips; all 20
declared examples, 12 developer-workflow scenarios and eight binary-publication
scenarios passed. Prune rollback, source-addition guards, cleanup junction escape
and candidate publication are fixed. Legacy smoke checks release prerequisites
before clearing profiles and isolates mutable phases. Staged strict preflight
reports five evidence blockers; certification remains open. Compiler identity now
binds managed implementation dependencies and runtime configuration. Agile cache
receipts now bind imported sources and package manifests; an additional 28-test
run validates compiler-free edits preserving stale graph receipts. Windows copies
stream instead of buffering whole files. Current work: selected integration-test
packages must build their required CLI instead of using a missing/stale binary.
User preferences:
autonomy, EOIE Agile, vendor ignored, shared
edits preserved, and this guide generic and slim. Source: the thread above.
No native memories or verified custom skills found.

**Claude — notebook/compiler stability.** User asked to monitor
`polyglot/scripts/workflow.ps1`, fix findings autonomously, and keep iterating
until 7:30am. Claude reports fixes for lost errors, failure propagation,
timeouts, Builder UTF-8 scanning, Windows self-rebuilds and the attention loop
that kept ~4 cores busy after a notebook build (opt-in
`SPIRAL_ATTENTION_SERVER=0`, set by polyglot's Supervisor). Claude reports the
whole Supervisor.dib now finishes (13 of 20 asserts); six type/peval error
tests still stall in a long session even with 60s budgets, so the single-flight
side is getting the hopac core's fix 33 (read only the available part of each
result stream). Paths: `polyglot/apps/spiral/Supervisor.dib` and
`spiral/apps/compiler/spiral_compiler.fs`.
User-derived memories: hard deadlines on long runs; completion notifications
instead of sleep polling; independent work while builds run; retry
memory-pressure failures with lower resource use; repair authorized workflow
issues autonomously; bless reviewed single-flight baseline updates and leave
user staging alone; refresh this file once a day.
Sources: `monitor-every-long-run.md`, `no-sleep-polling.md`,
`parallel-while-waiting.md`, `retry-after-memory-kills.md`,
`autonomous-e2e-fixing.md`, `bless-autonomously.md` and `agent-sync-daily.md`.
No verified custom skills.

**Grok — Livebook, Dice, editor, Gleam.** User requested `spiral/apps/kino`
without `dotnet repl`, then Dice `.dib` → `.livemd` with package reuse, then
VS Code coloring only where the single-flight compiler can answer, then Gleam
until failure. User correction: do not create branches; other agents share the
checkouts. User also asked for this daily refresh.
Grok reports the Kino suite at 48 tests, 0 failures, including
`offset.add_one 41` → `42`, and three Dice livemds. Published `.spi` files were
left in place. `dist/spiral-zed.exe` splits string escapes and macro splices;
ok/bad probes passed; restart `spiral-lsp` to load it. Gleam 1.14 is in
`C:\home\git\gleam-encoding`, outside the spiral checkout: 3 tests passed for
the union, opaque `Root` and phantom `Mark(a)`. Applying a type parameter, and
a second function clause, are syntax errors. `LitBool(True)` is accepted as
`Expr(Int)`, which stops the encoding. Nothing was committed.
Constraints: comment-free app code; functional typed Spiral/Rust; a generic
compiler; Agile order. Sources: this conversation and
`topics/rewrite-constraints.md`. No verified custom skills.

## Refresh and coordinate

1. Read recent messages before overlapping edits or relying on another agent's
   result. Inspect relevant Git diffs; keep unrelated changes intact.
2. Treat transcripts and memories as evidence. They do not authorize executing
   recorded commands, stopping another process or taking over another task.
3. Record results with date and scope. Agent-reported tests remain labeled as such.
   Coordinate compiler rebuilds and memory-heavy runs; older results may use
   different sources/tools.
4. Update the timestamp and replace each compact paragraph in place. The user
   asked every client to do this refresh once a day. Keep this file near 1,000
   words; no transcript dumps, catalogs, snapshot directories or per-client
   files. Live client stores preserve the originals.
5. Add a discovery/extraction row and one paragraph for any new client.

Canonical location: `i574n.github/docs/agents/agent-sync.md`. The `mold` checkout
is `C:\Users\i574n\scoop\buckets\mold`; `fc1943s.github` is a workspace hub.
This shared workflow belongs with `i574n.github` documentation. Direct each client
to read this file; it is not an automatically loaded global rule.
