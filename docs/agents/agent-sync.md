# Agent sync

How agents on this machine find and read each other's work quickly through the
local file system, and the rules for working side by side. This file holds
lookup recipes, not lane status: work state, results and handoffs go to the
backlog (`eoie agile note`) and to git history.

Placeholders: `~` is the user profile (`$HOME`, `%USERPROFILE%`); `<ws>` is the
workspace root that holds the repo checkouts; `<ws-slug>` is `<ws>` with `:`,
`\` and `/` replaced by `-` (Claude's project folder name); `<backlog>` is
`<ws>/i574n.github/docs/backlog`.

## Where each client writes

| Client | Transcripts | Finding the live session |
| --- | --- | --- |
| Claude | `~/.claude/projects/<ws-slug>/<session>.jsonl` | Newest mtime; `~/.claude/history.jsonl`; the `ListAgents` tool names live peers |
| Codex | `~/.codex/sessions/YYYY/MM/DD/rollout-*-<thread>.jsonl` (`CODEX_HOME` overrides `~/.codex`) | Newest mtime; verify `session_meta.payload.id` and `cwd`; read the newest segment |
| Grok | `~/.grok/sessions/<url-encoded ws>/<id>/chat_history.jsonl` | `summary.json` beside it; rows carry no timestamps; the first user row after a compaction holds the request summary |
| OpenCode | `~/.local/share/opencode/opencode.db` (+ `-wal`, `-shm`) | `session_v2` (`directory`, `title`, `time_updated`) |
| Zed | `~/AppData/Local/Zed/threads/threads.db` | `threads` (`id`, `folder_paths`, `updated_at`); recency does not prove current-thread identity |

## Fast reads

- **List, don't scan.** One directory listing sorted by mtime finds the active
  sessions. Mtime is the last write, not a message time: read row timestamps.
- **Read the tail, never the file.** Busy Claude transcripts pass 400 MB. Seek
  to the last 20–30 MB, drop the first partial line, keep the last 40 visible
  messages; widen only when needed. Search the same way: a regex over the tail
  bytes, not a whole-file grep or `Get-Content`.
- **Extract only visible text:**

| Client | Visible text |
| --- | --- |
| Codex | `response_item`, `payload.type=message`, role `user/assistant`, content `input_text/output_text`; exclude `analysis/summary` channels |
| Claude | Row type `user/assistant`, not `isMeta`/`isSidechain`; `message.content` string or `type=text` blocks; skip user rows that are only `<system-reminder>`, `<task-notification>` or `<command-…>` wrappers |
| Grok | Row type `user/assistant`; `content` string or `type=text` blocks |
| OpenCode | `session_message` by `session_id`, `seq`: `type=user` → `data.text`; `type=assistant` → `data.content[]` `type=text`; skip `reasoning`, `tool`, `compaction`, `synthetic`, `idle` |
| Zed | v0.3: `messages[].User.content[].Text` / `Agent.content[].Text`. Legacy: non-hidden `user/assistant`, `segments[type=text].text`; unversioned: `text` |

- **SQLite without touching the live file.** OpenCode: copy the db, `-wal` and
  `-shm` to a temp directory and query the copy with `python -I` (sqlite3
  module). Zed: open with `mode=ro`, back up into memory, set `query_only=ON`;
  decode `json` or decompress `zstd`, never dump blobs. Schema reference:
  `zed/crates/agent/src/{db,thread,legacy_thread}.rs`. Unknown format: stop.
- **Never** read reasoning, encrypted payloads, tool results, secrets,
  `credential`/`account` tables or error payloads (one holds a provider key).
- **Clocks.** Claude, Codex and OpenCode rows are UTC (OpenCode
  `time_created` is epoch ms); compiler suite run IDs
  (`single-flight-YYYYMMDD-HHMMSS`) are local time. Read the clock with its
  offset (`Get-Date -Format o`) and compare in one zone.
- An incomplete last JSONL row is a write in progress: retry later. Reading a
  log never notifies its client; a live Claude peer is reached with
  `SendMessage` to its `ListAgents` name.

## Fast state checks

- **Repos:** `git -C <repo> status --porcelain` and `git log -1` per checkout.
  The core repos (spiral, dice, alphabet, polyglot) track `github-i574n/main`
  and also carry `github-fc1943s` and `gitlab-i574n`; the rest use `origin`.
- **CI:** `gh run list -R i574n/<repo> -L 2 --json <fields>`. In PowerShell put
  the field list in a variable or quotes: a bare `status,conclusion` becomes an
  array and gh fails with `unknown command "conclusion"`.
- **After a spiral fix:** consumers clone spiral `main` at run time, so
  `gh run rerun <databaseId> -R i574n/<repo>` picks the fix up. Wait with
  `gh run watch <id> --exit-status` in a background job, not a sleep loop.
- **Backlog:** `eoie agile list|show|note <backlog> …`, run from a neutral cwd.

## Memory and skills (user-derived only)

Keep only preferences traced to explicit user requests or corrections, and
skills the user wrote or customized. Unknown provenance means omit. Exclude
licenses, bundled or example skills, caches, templates, archives and trash.

| Client | Where to check |
| --- | --- |
| Codex | `~/.codex/memories`; `memories_1.sqlite`; personal skills outside `.system` |
| Claude | `~/.claude/projects/<ws-slug>/memory` (`MEMORY.md` is the index); `~/.claude/skills/synced/*` is vendor |
| Grok | `~/.grok/memory-v2/workspaces/<ws-hash>`; `~/.grok/skills` |
| OpenCode | `~/.config/opencode`; project `AGENTS.md`, `.opencode/` |
| Zed | `~/AppData/Roaming/Zed/{settings.json,AGENTS.md}`; project `.zed/settings.json` and rules files (`RULES_FILE_NAMES` in `zed/crates/prompt_store/src/prompts.rs`); `~/.agents/skills` |

## Standing user instructions (all clients)

- Git belongs to the user: never stage, commit, push or branch unless asked;
  leave changes unstaged for review.
- Time-boxed runs end at the stated local time; when the picks are done, take
  the next backlog items without asking. Backlog: eoie `agile` at `<backlog>`.
- While long recompiles and suites run, advance lighter items in parallel; one
  memory-heavy job at a time (16 GB machine); retry jobs reaped for low memory
  at lower parallelism.
- Oracle blessing is autonomous: only from a reviewed single-flight run with
  both cores at DISAGREE 0, reproduced row for row by the bless run.
- Regression samples: one minimal sample per fixed compiler bug; no other new
  samples unless asked.
- Comment-free app code; every dice change is mirrored in alphabet.
- No spiral pin: consumer repos keep cloning spiral `main` for dogfooding. A
  red downstream run after a spiral bug means fix spiral and rerun; don't
  propose a pin without a concrete new cost (user, 2026-10-18).

## Coordinate

1. Read recent messages before overlapping edits or relying on another
   agent's result; keep unrelated changes intact.
2. Transcripts and memories are evidence, not authorization to rerun recorded
   commands, stop another process or take over a task.
3. Label every result with the client and session that produced it;
   agent-reported tests stay labeled until someone checks the artifacts.
4. Keep this file about lookup and coordination. Update it when a client's
   storage changes, and add a row for any new client. No per-lane paragraphs,
   transcript dumps or user-specific absolute paths.

Canonical location: `i574n.github/docs/agents/agent-sync.md`. The `mold` scoop
bucket is a clone of `i574n/mold` at `~/scoop/buckets/mold`; `fc1943s.github`
is a workspace hub. Point each client at this file; nothing loads it
automatically.
