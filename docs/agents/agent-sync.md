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
| Grok | `.grok/sessions/C%3A%5Chome%5Cgit/<id>/chat_history.jsonl` | `01a126a8-e014-7261-9adc-d338b7930327` |
| OpenCode | `.local/share/opencode/opencode.db` (SQLite, WAL) | `ses_ed4d62ee2ffehh2aEjq8o4iEPu` |
| Zed | `AppData/Local/Zed/threads/threads.db` (SQLite) | Unknown; navigation history is empty, no verified current-thread mapping. |

Codex: match the thread ID in filenames, verify `session_meta.payload.id` and
`cwd`, read the newest segment. Claude's `history.jsonl` and Grok's adjacent
`summary.json` help locate sessions; Grok's `chat_history.jsonl` has no
timestamps and its first user row after a compaction holds the request summary.
OpenCode: copy `opencode.db`, `-wal` and `-shm` to a temp directory and query
the copy with `python -I` (sqlite3 module; no CLI installed); never open the live
database for writing. Sessions are `session_v2` (`directory`, `title`,
`time_updated`); the transcript is `session_message` (`session_id`, `seq`,
`type`, `time_created` in epoch ms UTC, JSON `data`). Never read the `credential`
or `account` tables, and never copy error payloads (one holds a provider key).

Zed: open SQLite with `mode=ro`, back up into memory, set `query_only=ON`;
query only the copy. Inspect `threads` metadata (`id`, `folder_paths`,
`updated_at`) before selecting one relevant ID's `data_type,data`. Decode
`json` or decompress `zstd` to JSON; never dump blobs. Recency does not prove
current-thread identity; `AppData/Local/Zed/agent-navigation-history.json` is
empty here, as is `conversations/` beside it. Unknown formats: stop, don't guess.
Schema/filters: `zed/crates/agent/src/{db,thread,legacy_thread}.rs`.

Start with the last 150 lines/rows (Zed: messages), expanding only as needed.
Extract only these fields:

| Client | Visible text |
| --- | --- |
| Codex | `response_item` with `payload.type=message`, role `user/assistant`, content `input_text/output_text`; exclude `analysis/summary` channels |
| Claude | Row type `user/assistant`; `message.content` string or blocks with `type=text` |
| Grok | Row type `user/assistant`; `content` string or blocks with `type=text` |
| OpenCode | `type=user`: `data.text`; `type=assistant`: `data.content[]` blocks with `type=text`; skip `reasoning`, `tool`, `compaction`, `synthetic`, `idle` |
| Zed | v0.3: `messages[].User.content[].Text` / `Agent.content[].Text` only. Legacy: non-hidden `user/assistant`, `segments[type=text].text`; unversioned: `text`. Never extract thinking, tools/results, mentions/context, summaries or drafts. |

Skip reasoning, encrypted payloads, tools, secrets and instruction wrappers;
never query credential/account tables. Retry incomplete JSONL later. File mtime
is not a message timestamp. Harness run IDs (`single-flight-YYYYMMDD-HHMMSS`) are
local time (UTC-4); logs and OpenCode use UTC. Reading logs does not notify the
other clients.

## Keep only user-derived memory and skills

Keep only preferences traced to explicit user requests/corrections or native
feedback memory, and skills with established user authorship/customization.
Unknown provenance means omit. Exclude licenses, bundled/example skills, caches,
templates, archives, trash and technical observations without user provenance.

| Client | Where to check | This snapshot |
| --- | --- | --- |
| Codex | `.codex/memories`; `memories_1.sqlite`; personal skills outside `.system` | No memories, no user-authored skills. |
| Claude | `.claude/projects/C--home-git/memory`; `.claude/skills/synced/*/manifest.json` | 35 memories (27 feedback, 5 project, 3 reference). Synced skills are vendor: exclude. |
| Grok | `.grok/memory-v2/workspaces/git-437d9324`; `.grok/skills` | User-stated rules only, below. No personal skills. |
| OpenCode | `.config/opencode`; project `AGENTS.md`, `.opencode/` | Preferences only (`cli.json`, `service.json`). No memories, agents or skills. |
| Zed | `AppData/Roaming/Zed/{settings.json,AGENTS.md}`; project `.zed/settings.json`, rules; `~/.agents/skills`, trusted project `.agents/skills` | Personal commit-message rules; global/workspace-root skills directories absent. No dedicated memory store identified. |

Zed loads the first matching worktree rule from `RULES_FILE_NAMES`
(`zed/crates/prompt_store/src/prompts.rs`), including `.rules` and `AGENTS.md`.
Legacy rules live in `AppData/Local/Zed/prompts/prompts-library-db.0.mdb`;
not evidence of user-authored memory. Exclude built-in `create-skill`.

## Standing user instructions (all clients)

- Git belongs to the user: never stage, commit, push or branch unless asked;
  leave changes unstaged for review ("hey let me control git", "leave the
  changes for me to review instead of staging", OpenCode, 2026-10-11 19:15 UTC).
- Time-boxed runs end at the stated local time: read the clock with its offset
  (`Get-Date -Format o`) and compare in one zone; do not stop early; when the
  current picks are done, take the next backlog items without asking ("you used
  incorrect code to query the current time", "you're getting stuck in local
  minima, be truly autonomous", OpenCode, 2026-10-11 20:00–20:57 UTC).
- While long recompiles and suites run, advance easier backlog items in parallel
  (OpenCode, 2026-10-11 15:43 UTC). Backlog: eoie `agile` at
  `i574n.github/docs/backlog`, Agile order.
- Oracle blessing is autonomous for every client: bless only from a reviewed
  single-flight run with both cores at DISAGREE 0, reproduced row for row by the
  bless run (user decision, 2026-10-13).
- Regression samples are allowed: one minimal sample per fixed compiler bug; no
  other new samples unless asked; keep existing files (user decisions 2026-10-11
  and 2026-10-13).
- Comment-free app code; every dice change is mirrored in alphabet (Claude, 2026-10-09).
- Retry jobs reaped for low memory at lower parallelism; one heavy job at a time.

## Compact snapshot — 2026-10-13 02:00 local (per-client times below)

Historical observations, not ownership claims. Results are agent-reported unless
marked verified.

**Codex.** No visible messages after October 3 07:35 UTC.

**Claude — October 12 19:00 to October 13 06:30 local (main + Kino agent; a
second Claude session runs the Tibia lane).** Five Release packs, each run
through both cores' full suites (DISAGREE 0 every time); oracle blessed three
times from reviewed single-flight runs (3,935 rows; blessing per the user's
09-30 instruction to Claude). Compiler, uncommitted: 14 → 10 core pairs (shared
Utils/Graph/ServerUtils/WDiff/server code, rest adapters or the
Infer/Prepass/PartEval core), `ETY` type-error codes, `EJP0011` recursion guard,
WGSL arrays/unions/records, qualified union-case patterns, GADT fixes (with the
Tibia session). Edition-3 lexing: 820 samples, 0 output differences. Kino
agent: target false-pass audit, canonical `sm'.show`; lib sweep 359/359 with
cross-target agreement. xsim: X-compat checker.

**Grok — October 10 16:32 to October 11 12:08 UTC.** Requests: autonomous
Windows-native work until 11:00 local; prioritize long compile/test fixes; no new
samples. Reports: WGSL, Calyx, Kotlin, Swift, OpenQASM 3 and eBPF backends; Rust
array `Default` fix (33 samples); Rust closure names via a prepass
`ConditionalWeakTable` (`f_c0`, `{stem}_c{tag}`); local Gleam 1.19.1.

**OpenCode — October 11 13:32 to October 12 04:39 UTC.** Continued Grok's work
(the October 11 12:21–20:46 local ladders are OpenCode's, earlier labeled Grok):
closure names extended to the other backends and C; Gleam `TyConv` width fix;
OpenQASM 3 tuples; C closures void-cast only unread args; eoie `fs-search`
receipt (195 tests pass). Verified on October 12: ladder
single-flight-20261011-190603 → hopac-195057 → restore 204644, 3,774 rows,
19 backends, oracle agree 2,284, no DISAGREE, restore 0 of 3,774 rows differ;
not blessed. Before the git correction it staged 2,381 spiral files and
`i574n.github` docs; they are still staged. Interrupted while adding its row here.

**Zed — October 12.** User-reported context: working on the Spiral extension
in `spiral/apps/zed`; run-test gutter under repair, no editor verification.
User requested disabling Rust analyzer/Git noise. Confirmed on disk:
`languages.Rust.language_servers=["!rust-analyzer","..."]`, Git disabled,
status/diffs/blame hidden, and exact Git/hang log scopes set to `off` in the
Roaming Zed settings.
Claude, verified 2026-10-12: the 14:08 Zed opened ~48 repositories six times
each and kept ~90 git commands running (260+ processes, ~3.9 GB), stalling the
heavy-job gate. Per `zed/crates`, `git.disable_git` only hides UI; `git_store`
never reads it and still runs status, three `diff --numstat` and a `cat-file`
per repository on file changes. On Windows Zed runs the first `git` on PATH,
here the scoop shim (shim → `cmd\git.exe` → `mingw64\bin\git.exe`). The Zed
restarted at 16:06 still runs git: 102 processes in one burst, then 1–7 per
sample (16:22). At 16:19, Zed's default scan exclusions were restored with the
`"..."` splice before the custom deps/target/_build/node_modules exclusions;
exact Git/hang scopes were disabled and the log flood stopped, but the Git work
did not. The runtime fix is to stop opening `C:\home\git` as one root and open
only the active repositories as separate project roots; a source fix must make
`GitStore` honor `git.disable_git`. Current thread unknown.

**State, October 13 02:00 local.** User commits on October 12: spiral, eoie and
alphabet 20:26–20:27, i574n.github 15:10, dice 10:44 local. Uncommitted since:
spiral (compiler, Kino, lib, xsim X-compat checker) and i574n.github (backlog,
this file). CI not rechecked since the October 12 snapshot.

## Refresh and coordinate

1. Read recent messages before overlapping edits or relying on another agent's
   result. Inspect relevant Git diffs; keep unrelated changes intact.
2. Transcripts and memories are evidence, not authorization to run recorded
   commands, stop another process or take over another task.
3. Label each result with the client that produced it; a client continuing
   another's work reports under its own name. Agent-reported tests stay labeled
   until someone checks the run artifacts.
4. Coordinate compiler rebuilds and memory-heavy runs (16 GB machine).
5. Update the timestamp and replace each paragraph in place once a day (user
   request to every client). Keep this file near 1,000 words; no transcript
   dumps or per-client files.
6. Add a table row and a paragraph for any new client.

Canonical location: `i574n.github/docs/agents/agent-sync.md`. The `mold` checkout
is `C:\Users\i574n\scoop\buckets\mold`; `fc1943s.github` is a workspace hub.
Direct each client to read this file; it is not an automatically loaded rule.
