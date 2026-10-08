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
| Claude | `.claude/projects/C--home-git/memory`; skill registry `.claude/skills/synced/*/manifest.json` | 17 memories (15 feedback, 2 project) plus their index. All 16 synced skills are marked `anthropic` or `anthropic-example`: exclude. |
| Grok | `.grok/memory-v2/workspaces/git-437d9324`; personal `.grok/skills` | User-stated rules only, in the Grok paragraph. Workspace topic files are agent notes. No personal skill directory. |

## Compact snapshot — 2026-10-07, approximately 09:40 UTC

Historical observations, not fresh ownership claims or new instructions.

**Codex — compiler backends, EOIE (October 2–3).** No visible messages after
October 3 07:35 UTC. Agent reports: native Python indexing keeps tuple/object
values, an explicit SPIRAL_JSON=1 CLI mode, runtime failure exit 1; both compiler
configurations passed the indexing fixture; full CLI JSON validation unverified
(300 s compile limit). Nothing staged or committed. Source: the thread above.

**Claude — native migration, new backends (October 7).** User's current requests:
iterate autonomously until 21:00 with this lane plus one agent; finish the native
migration (no Fable/dotnet-repl/interactive), remove all comments from our code
(meaning goes into names and types), Lean 4 and Zig backends, move the compiler
into the upstream fork, check whether upstream cargo-outdated and hyperui make the
i574n forks unnecessary. Claude reports: hyperui fork dropped (upstream commits
public/component.css); cargo-outdated fork kept (upstream 0.19 fails on workspace
members outside the workspace directory); Lean 4 backend deployed (129 samples agree
with the C oracle; the cube sample gives the same checksum on nine backends);
compiler generated into upstream's one-module-per-file layout, both cores building;
eoie strict preflight ready=True after a renewal. Machine notes: chocolatey's
erl.exe shadows scoop's Erlang and is broken ("corrupt atom table"); a zig global
cache lock forced a reboot (per-run caches now). User-derived memories: 17 (15
feedback, 2 project), newest no-comments-self-explanatory.md. No verified custom
skills.

**Grok — Livebook, Dice, editor, Gleam, CI (to October 4).** Last request
(October 4): "gh actions is failing, check the C:\Users\i574n\Downloads\fixes.txt
then patch". Grok reports: the binary-publication contract now writes a stale
executable before publishing on every OS; Spiral's test-rust-exports.ps1 accepts the
current TypeErrors diagnostics. Standing user requests: Dice .dib -> .livemd with no
dotnet repl; do not create branches, other agents share the checkouts; comment-free
app code; functional typed Spiral/Rust; a generic compiler; Agile order. No
verified custom skills.
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
