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

Start with the last 150 JSONL lines (or rows), increasing only if tool activity
hides the messages. Extract these fields; skip everything else:

| Client | Visible text |
| --- | --- |
| Codex | `response_item` with `payload.type=message`, role `user/assistant`, content `input_text/output_text`; exclude `analysis/summary` channels |
| Claude | Row type `user/assistant`; `message.content` string or blocks with `type=text` |
| Grok | Row type `user/assistant`; `content` string or blocks with `type=text` |
| OpenCode | `type=user`: `data.text`; `type=assistant`: `data.content[]` blocks with `type=text`; skip `reasoning`, `tool`, `compaction`, `synthetic`, `idle` |

Skip reasoning, encrypted payloads, tool bodies and replayed instruction
wrappers. Retry an incomplete final JSON line later. A file's modification time
is not a message timestamp. Harness run IDs (`single-flight-YYYYMMDD-HHMMSS`) are
local time (UTC-4); logs and OpenCode use UTC. Reading logs does not notify the
other clients.

## Keep only user-derived memory and skills

Include a preference only when an explicit user request/correction or native
feedback memory identifies its origin. Include a skill only when user authorship
or a requested customization is established. Unknown provenance means omit.
Exclude licenses, bundled/example skills, plugin caches, templates, archives,
trash and technical observations without user provenance.

| Client | Where to check | This snapshot |
| --- | --- | --- |
| Codex | `.codex/memories`; `memories_1.sqlite`; personal skills outside `.system` | No memories, no user-authored skills. |
| Claude | `.claude/projects/C--home-git/memory`; `.claude/skills/synced/*/manifest.json` | 27 memories (22 feedback, 3 project, 2 reference). Synced skills are vendor: exclude. |
| Grok | `.grok/memory-v2/workspaces/git-437d9324`; `.grok/skills` | User-stated rules only, below. No personal skills. |
| OpenCode | `.config/opencode`; project `AGENTS.md`, `.opencode/` | Preferences only (`cli.json`, `service.json`). No memories, agents or skills. |

## Standing user instructions (all clients)

- Git belongs to the user: never stage, commit, push, branch or bless unless
  asked; leave changes unstaged for review ("hey let me control git", "leave the
  changes for me to review instead of staging", OpenCode, 2026-10-11 19:15 UTC).
- Time-boxed runs end at the stated local time: read the clock with its offset
  (`Get-Date -Format o`) and compare in one zone; do not stop early; when the
  current picks are done, take the next backlog items without asking ("you used
  incorrect code to query the current time", "you're getting stuck in local
  minima, be truly autonomous", OpenCode, 2026-10-11 20:00–20:57 UTC).
- While long recompiles and suites run, advance easier backlog items in parallel
  (OpenCode, 2026-10-11 15:43 UTC). Backlog: eoie `agile` at
  `i574n.github/docs/backlog`, Agile order.
- No new samples unless asked; keep existing files (Grok, 2026-10-11).
- Comment-free app code; every dice change is mirrored in alphabet (Claude, 2026-10-09).
- Retry jobs reaped for low memory at lower parallelism; one heavy job at a time.

## Compact snapshot — 2026-10-12, approximately 04:45 UTC

Historical observations, not ownership claims. Results are agent-reported unless
marked verified.

**Codex.** No visible messages after October 3 07:35 UTC.

**Claude — October 8–9, refresh October 12.** Wasm backend (147 samples agree
with C); readable union case names in Rust, Zig, Delphi, TypeScript, Lua, Lean,
Gleam, F#, C, Python and Bend; oracle blessed 2026-10-09 10:15 UTC (last bless);
`dir-tree-html` moved to its own repo; alphabet synced with dice; dice derive fix
(dice ubuntu CI green, verified). October 12: spiral CI was red only on the
`sm'` notebook, which hard-coded the old Lua/Gleam/F#/Python case names; fixed
in `lib/spiral/sm'.livemd`, unstaged.

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

**State, October 12 04:45 UTC.** No commits since October 9 10:56 UTC. CI: dice
ubuntu, polyglot, dir-tree-html and eoie green; spiral red (`sm'`, fixed locally);
dice windows red (WSL contract test); alphabet red (`ALPHABET-CI-XELATEX`).

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
