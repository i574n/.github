# Agent sync

How agents on this machine find and read each other's work quickly through the
local file system, and the rules for working side by side. This file holds
lookup recipes, not lane status: work state, results and handoffs go to the
backlog (`eoie agile note`) and to git history.

Placeholders: `~` is the user profile (`$HOME`, `%USERPROFILE%`); `<ws>` is the
workspace root that holds the repo checkouts; `<ws-slug>` is `<ws>` with `:`,
`\` and `/` replaced by `-` (Claude's project folder name); `<backlog>` is
`<ws>/i574n.github/docs/backlog`.

## Get on par fast

A new thread catches up fastest in this order, before opening the backlog's
700+ rows:

1. **Who is live.** `~/.claude/autonomous/standing.txt` maps each Claude
   session id to its team (`<session>=<team>`); a session with no line is kept
   working by the standing gate. `ListAgents` names the live peers and whether
   each is busy or idle. Cross the two and the transcript list becomes "who is
   who." Add your own `<session>=<team>` line.
2. **What fits now.** `eoie agile next <backlog>` ranks machine-fit work by
   WSJF, but it is lane-blind: its top rows are usually the owning lane's
   in-flight work. Overlay step 1 and the item's tags before taking anything,
   and read `agile show <id>` first — an item that looks free can be 20 notes
   deep (it has an owner lane actively on it).
3. **What each lane is doing now.** Tail-read only the active lanes'
   transcripts (the "Read the tail" recipe below), mapped by step 1. Current
   work lives there and in git, not in the backlog titles.
4. **Add value without adding CPU.** The box is usually CPU-bound (one heavy
   job at a time, machine-wide). A fresh session's safe, non-colliding
   contribution is read-only measurement, grooming and drafts — never a compile
   that slows every lane's gates. Record findings as labeled notes on the item
   they inform; do not take over another lane's live task.

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
  `agile list <backlog> all --json` carries `deps`, `tags`, `notes` and
  `last_note` per task, so grooming needs no `show` per item; fix a mistyped
  note with `agile note-edit <backlog> <id> <n> <text>` instead of appending.
- **Many repos at once:** `eoie repo state <repo>...` prints branch, ahead and
  behind, staged/modified/untracked totals, the last commit, and the busiest
  areas (two path levels) per repo in one call.
- **Result tables:** `eoie table query <file.tsv> --where col=value --group col`
  and `eoie table diff <a.tsv> <b.tsv> --key id,backend` answer matrix
  questions (6,994 rows in about 60 ms) without ad-hoc scripts.
- **Long runs:** `eoie job start <label> <timeout-ms> <program> [args...]`
  detaches a supervised job (it breaks away from the shell's job object, so the
  session's idle reaper does not kill it), enforces the wall-clock timeout
  (stopping the whole process tree; the exit detail says if anything survived), and
  keeps stdout/stderr/exit under `%LOCALAPPDATA%\eoie\jobs`; the job's program
  runs without a console window. Scripts that start their own processes stay
  hidden too: `Start-Process -WindowStyle Hidden` or `-NoNewWindow`, or
  `ProcessStartInfo.CreateNoWindow`, never a bare `Start-Process pwsh`. Then
  `job wait <id> <max-ms>` (exits with the job's code, 124 timed out,
  125 lost or spawn failure, 3 still running), `job status`, `job tail`,
  `job list`. `job start --heavy ...` runs the program inside spiral's
  `Invoke-Heavy`, so a gated build is both queued and reaper-proof. Run
  `job wait` itself as a background shell to get the session's completion
  notice; if the reaper kills that waiting shell, the job keeps running, the
  orphaned `job wait` ends within a second (it watches the shell that started
  it), and a new `job wait` picks the job up. A running eoie.exe is locked on Windows: when
  the job rebuilds eoie, run `job wait` from the supervisor copy
  (`%LOCALAPPDATA%\eoie\supervisor\eoie-*.exe`), not `src/target/debug`.
- **Probe what a queued job sees with `job start`:** `job run` supervises
  inside your own shell and environment, while `job start` goes through the
  shared job host, whose environment (until EOIE-JOB-ENV lands) is whoever
  started it. On 10-23 a leaked `EOIE_WINDOWS_HOME` in the host made every
  detached window gate read an empty registry, while every `job run` probe
  looked fine.
- **Read and search:** `eoie read <path>[:from-to]... [--max-lines n]`,
  `eoie lines <path|glob>...` (path, bytes, lines table) and
  `eoie grep <root> <needle> [--glob *.rs] [--context n] [--max n] [--ignore-case]`
  (rayon, skips target/.git/deps). Every one ends with a receipt line
  (`ok  files 2  bytes 33  41ms  17:52:10-04:00`), so no separate `Get-Date`.
- **One-call programs:** `eoie do <root> '<program>'`, or `eoie do <root> -`
  with the program on stdin (a PowerShell here-string piped in keeps every
  quote). Statements are separated by newlines or `;`.
  Edits: `edit <path|glob> [dry-run] [expect files N replacements M]` followed
  by `replace once|all|<n> times "old" with "new"` lines (escapes `\n \t \" \\`;
  an LF anchor also matches a CRLF file), plus `show <path>`. All edits are
  validated before anything is written; a miss writes nothing and names the
  anchor. Each changed file prints its replacement lines and sha256 before and
  after. Queries: `search [literal] "regex" in <glob> [include *.rs,*.spi]
  [exclude <glob>] [context n[,m]] [first n per file] [paths]`,
  `read <path> around "text" [context n[,m]]`, `files <glob>`,
  `head <glob> n`, `tail <glob> n`, `wait <file> ~ "regex" [timeout 5s]`
  (exit 3 on timeout, with the file's last line). Facts: `hash a,b,c` (sha256
  and which are equal), `env A,B`, `exists a,b`, `nearest "re" before lines
  3,6 in <file>`, `json <file> get a.b,c.length`, `lines <file> count by
  "(re)"`, `files <glob> count by extension`, `table <f.tsv> [where col <> "v"]
  count by a,b`, and `dirs <dir>/* having a and b` with indented
  `tag <name> = <file> ~ "re" [and|or|not ...]` lines and `count by <name>`,
  and `diff <a.tsv> <b.tsv> by id,backend [on compile,exit]` (changed cells,
  added and removed keys; replaces ad-hoc Import-Csv review scripts). Runs: `run <cmd> [timeout 10s]` (cmd /c in the root; stops
  the whole tree on timeout), `run <cmd> until output ~ "re" timeout 20s then
  stop`, `run <cmd> background as s1` ... `stop s1`, then `tail output n`,
  `search "re" in output`, `errors in output by "(re)"` (grouped counts with
  the first line each), `processes where command ~ "re"`. A run's output is
  printed only when no later statement reads it. Edits followed by runs work
  in one program (edits are validated and written first; a failed edit runs
  nothing). Writes: `append <file> "- {clock:HH:mm} lane: done"` (the clock
  fills `{clock:FMT}` with yyyy MM dd HH mm ss) and
  `copy <glob> to <dir> [except *.tmp,...]`, each followed by read statements
  (`tail`, `files`, `search`...) that see the written state. Git:
  `git status a, b group by state`, `git log <repo> since <date> count by day`,
  `git history <repo> "text" in <file>` (commits that added or removed it, plus
  the current lines), `search "re" in <glob> with git status`, and
  `git init|commit <repo> all "msg" [at <iso>]` for test repos. Jobs:
  `job start <cmd> as <name> timeout 30s [notify]` starts a real supervised
  eoie job labeled `<name>`; `job wait <name> [timeout 5m]` (also from a later
  call) reports finished, timed out (tree stopped) or still running, with the
  output tail; timeouts exit 3. `lab audit lab against <backlog>` gives each lab
  folder its backlog item, status and a keep, orphan, done or stale verdict,
  and flags non-portable `C:\Users\...` paths. Otherwise one program belongs to
  one family (edits, queries, facts, runs, writes, git, jobs, lab).
  `eoie --help` lists the full grammar.
- **Processes:** `eoie ps [regex] [--tree]` (pid, ppid, start time, MB,
  command line) and `eoie kill <pid>... [--tree]`. kill refuses its own
  calling shell and its parents, and a pid that is already gone fails. PowerShell splits native arguments that contain
  `"`. Put one argument per line in a file and run `eoie --args-from <file>`
  (or `-` for stdin).

## Offloading to the worker node

A heavy **read-only** job (compile, test, review) can run on the remote kit
worker node through `i574n.github/scripts/remote.ps1`, freeing this box.
Results come back to scratch, never into a repo tree: a job that writes back
into the live tree (regen/publish, `lib land.ps1`, matrix accept) races local
edits here and stays local.

1. Sync your tree into a per-job mirror (an isolated worktree; no collision
   with other offloads): `remote.ps1 node sync <repo> -Mirror <job>`. It proves
   the mirror matches your tree by per-file sha256 (staged files included).
2. Run there, requiring the right compiler so a stale node core can't give a
   false result: `remote.ps1 node run -RequireCore <core16> "Set-Location
   C:\home\remote\<job>\<repo>\...; <cmd>"`. `<core16>` is this box's
   `SpiralCompilerCore.dll` sha256, first 16 hex (via the compiler's
   `env.ps1 Get-SpiralCompilerDll`); a mismatch refuses the run.
3. Pull results: `remote.ps1 node fetch <remote path> <local scratch path>`.

Each offload uses its own `-Mirror <job>` (an isolated worktree), so several
lanes can sync the same repo into different mirrors and run at once without
colliding. Machines live in `~/.config/remote-machines.json` (`node`; `laptop`
is a back-compat alias). Node upkeep has no owner and no rotation: whoever
finds it broken fixes it and appends what and why to
`C:\home\remote-queue\node-upkeep.log` (the durable record; `node-status.txt` is
the monitor's live one-line signal, overwritten each tick, so it is not a log).
Long-lived node processes (the utilization monitor, any shared helper) carry an
owner label in their log so a stale one can be traced and reclaimed. The utilization monitor (`node-watch.ps1`,
label `node-watch`, a 24h job) publishes `node-status.txt` every ~75s; if that
signal is stale by more than a few minutes its job timed out, and any lane revives
it with `<eoie> job start node-watch 86400000 pwsh -NoProfile -File i574n.github/scripts/node-watch.ps1`
(use the `%LOCALAPPDATA%\eoie\supervisor\eoie-*.exe` copy when the tree lease blocks the in-tree `eoie.exe`).

Drive the node with direct `remote.ps1 <node> job start ...` calls — one
supervised job per offload, per lane, concurrently. There is no central
dispatcher or queue to route through (the earlier `C:\home\remote-queue\pending`
dispatcher is retired: a single serialising router is exactly the centralisation
we removed, and a down router silently stalls every lane behind it). The durable
next step is `EOIE-REMOTE-EXECUTOR`'s typed coordinator over iggy pub/sub, which
lets lanes subscribe to node state and quorum in real time instead of polling a
file.

Gotcha: `remote.ps1 <node> job start --heavy ...` needs a spiral tree (it calls
`scripts/heavy.ps1`, or honours `EOIE_HEAVY_GATE`); outside one it fails. Plain
`job start` (no `--heavy`) works anywhere.

Gotcha: a `remote.ps1 <node> run "<cmd>"` whose `<cmd>` is more than ~2-3 KB fails
with "The command line is too long" (Invoke-Remote base64s it as UTF-16 into the ssh
`-EncodedCommand`, so it roughly doubles). To put a bigger file on the node, send its
base64 in <=1400-char chunks with `Add-Content -Path <tmp>.b64 -Value '<chunk>' -NoNewline`
(base64 is ASCII, so no quoting issues), then one `run` to
`[IO.File]::WriteAllBytes(<dest>, [Convert]::FromBase64String((Get-Content <tmp>.b64 -Raw)))`.
Small binary payloads (a 14 MB core, a sample dir) that exceed even that go through
`sync` (tar, no limit) or one WriteAllBytes per small file. Also: a path with an apostrophe
(`sm'.spi`) breaks the node pwsh parse unless single-quoted with the apostrophe doubled.

`sync` carries adds, modifies, deletes and moves (collected with `--no-renames`
and `--diff-filter=D`, proven by per-file sha256; emptied directories are
pruned), so a mirror matches your working tree exactly. A spiral `-Mirror` also links
`deps/` (the untracked The-Spiral-Language/polyglot clones) from the node's
shared source after each clean, so `|core-` resolves in the mirror. Cells
resolves the spiral runner through `SPIRAL_EXE` (persisted on the node's User
env), not PATH, so the node's canonical `spiral.exe` must be kept freshly built
for lib-gate results to be valid.

A node job that only one lane needs to run — a rebuild of the node's canonical
`spiral.exe`, a one-time toolchain provisioning — is a **singleton**: label it
`node-singleton-<kind>` (e.g. `node-singleton-spiral-exe`), and before starting
one run `remote.ps1 <node> job list` and skip it if a job with that label is
already running or queued — `job wait` on it and use its result, or chain your
follow-up after it, rather than starting a duplicate. On 2026-10-26 four lanes
rebuilt the same `workspace\spiral.exe` at once under different labels; cargo's
target lock serialized them to one real build, but four jobs held supervisors
and CPU for nothing.

**This is self-serve:
every lane drives its own offloads directly and concurrently — no lane routes
its node work through another.** The shared tooling and one-time node
provisioning are a lane's to maintain, not a per-job router.

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
is a workspace hub. A remote kit worker node (`DESKTOP-6E8C14G`, a LAN machine;
"worker node" / "remote node" for short) runs as a remote eoie worker, driven
with `i574n.github/scripts/remote.ps1 <node> run|job|status|sync|fetch` over SSH
(Tailscale fallback); machines live in `~/.config/remote-machines.json`. Its
bootstrap and the cross-machine job dispatch live in backlog `REMOTE-WORKER-NODE`
and `EOIE-REMOTE-EXECUTOR`. Code moves between the review box and the worker node
by a non-git transport (the user owns git on the review box): the uncommitted
delta is copied over SSH and proven by per-file sha256. Point each client at this
file; nothing loads it automatically.
