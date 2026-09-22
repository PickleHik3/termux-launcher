# Title-led working ring (2026-09-13)

Follow-up to `SPEC.md` (chip indicators). That spec made the chip's outline ring follow
`AgentStatus.WORKING` on agent panes (`TerminalWindowBar.showsRing`). The status itself still
came from regular expressions over the bottom 12 screen rows, so a Claude sitting at its prompt
with a background shell running read as WORKING (`esc to interrupt` / `thinking` in the tail
outranks the `❯` idle rule) and the ring never stopped. Herdr solves the same problem by reading
the OSC terminal title the agent sets; this does the same. Review page: `.lavish/title-led-ring.html`.

## Evidence (agents installed on pong, herdr manifests 2026.09.11)

| Agent | Title signal | Basis |
|---|---|---|
| claude | working `^[⠀-⣿◐-◓] `, idle `^✳ `, no blocked form | herdr claude.toml; live reading of a session |
| codex | blocked: contains `Action Required`; working: braille spinner glyph as a word; idle: any other non-blank | OSC 0/2 present in `codex.bin` on pong; herdr codex.toml |
| amp | blocked: contains `Plugin confirmation needed`; working `^[⠀-⣿] `; idle: contains ` - amp - ` | herdr amp.toml |
| qwen | blocked `^✳︎? `; working `^◐︎? ` | herdr qwen.toml |
| grok | blocked: contains `Action Required`; idle `(?:^\| - )grok$`; working: any other non-blank | herdr grok.toml |
| hermes | blocked `^⚠`, working `^⏳`, idle `^✓` (each optionally followed by FE0E/FE0F, then space or end) | herdr hermes.toml |
| pi | no title. Screen: contains `Working...` → working | pi bundle on pong has no OSC 0/2; herdr pi.toml |
| opencode | no title. Screen: `△ Permission required`, or `esc dismiss` + (`enter confirm`\|`enter submit`\|`enter toggle`) + (`↑↓ select`\|`⇆ tab`) → blocked; `esc to interrupt` / `ctrl+c to interrupt` / `press esc to interrupt` / `(?i)opencode.*esc (again to )?interrupt` → working; `(■\|⬝){4,}` → working | herdr opencode.toml |
| aichat, crush, vibe | unknown; treated as known agents on the generic CPU path | not shown to set a title |

Copies of the herdr manifests used are in the session scratchpad; the originals live in
`~/.local/state/herdr/agent-detection/remote/*.toml` on the dev machine.

## Decisions

| Item | Rule |
|---|---|
| Precedence in `AgentStatusTracker.observe` | hook › title › screen › generic CPU. A WORKING or BLOCKED title is final for the pass and the screen is not read. An IDLE title is written as IDLE; the throttled screen pass may then only raise it to BLOCKED, never to WORKING. No title match falls through to today's path unchanged. |
| `AgentTitleRules` | New class beside `AgentScreenRules`: ordered per-agent patterns over `session.getTitle()`, `classify(agent, title)` → state or null, `hasTitleRules(agent)`. Agents: claude, codex, amp, qwen, grok, hermes, patterns as in the table. Title reads are not throttled (string compare). `AgentStatus.Source` gains `TITLE`. |
| Screen rules grow | pi and opencode get the screen rules in the table (ported from herdr), so `hasScreenRules` covers claude, codex, pi, opencode. |
| Known agents grow | `KNOWN_AGENTS` adds pi, aichat, crush, vibe, amp, qwen, grok, hermes. `ProcessGlyphs` gives the newcomers the robot glyph used for opencode unless a fitting Nerd Font glyph exists. |
| Refresh on title change | `TermuxTerminalSessionActivityClient.onTitleChanged` also schedules the coalesced 150 ms window-bar refresh (`noteShellActivity` path or equivalent) so the ring follows a title within a frame instead of the 2 s label poll. |
| Phase tracker input (D1) | On a pane whose current `AgentStatus.source` is TITLE or HOOK, `observeWindowPhases` feeds `ShellPhaseTracker` `working = state == WORKING` instead of `isShellWorking()`. Other panes unchanged. |
| Chip rendering | Unchanged: `showsRing` = WORKING; `markFor` unchanged. |
| Out of scope | Title rules for ssh panes; agents nested under other programs; any change to the CPU resolver or output tracker for ordinary shells. |

## Build plan

| # | Branch | Delivers | Depends on | Gate |
|---|---|---|---|---|
| 1 | `feat/title-led-ring` | `AgentTitleRules` + tests; `AgentStatus.Source.TITLE`, KNOWN_AGENTS, `hasScreenRules`; tracker precedence + tests; pi/opencode screen rules + tests; `ProcessGlyphs`; `onTitleChanged` refresh; D1 phase-tracker input; this spec | — | unit suites green (`:app:testDebugUnitTest`); Waydroid fake-agent gate; install on pong and one read-only look at a real Claude pane |

Waydroid gate: a shell script named `claude` first on PATH (so the foreground resolver names the
agent) that prints a working title, then an idle title while spawning a CPU burner in the
background, then a permission-shaped prompt. Expected chip: ring on; ring off with the burner
running; red dot on the prompt. A plain shell doing the same work must behave exactly as before.
