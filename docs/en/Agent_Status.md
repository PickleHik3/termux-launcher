# Agent status

What an AI coding agent in a terminal pane is doing, shown where you can see it without opening the
pane.

## What is shown

- **Window chips** in the status bar: a small round dot in front of the label, in the working
  accent while the agent works, the warm attention colour when it is waiting for you, and the muted
  label colour when it is idle. Screen readers get one extra sentence after the window name.
- **Overview**: a session with an agent in it shows its name in the agent's colour, the
  attention colour when it needs you and the accent colour while it works, so a waiting agent is
  easy to find from the session list. See
  [Panes, windows and sessions](Panes_Windows_And_Sessions.md#sessions).

The three states:

| State | Word | Means |
| --- | --- | --- |
| working | **Working** | the agent is on a turn |
| blocked | **Needs you** | it is waiting for an answer: a permission prompt or a question |
| idle | **Idle** | it is running, at its prompt, with nothing to do |

A window rolls up its panes and a session rolls up its windows: **Needs you** over **Working** over
**Idle** over nothing at all.

## Where the reading comes from

Three sources, in this order; a hook report always wins while it stands:

1. **Hooks.** The agent reports its own state with `launcherctl agent <state>`. A report holds
   until the next report, until `launcherctl agent clear`, or until the agent process leaves the
   pane's foreground.
2. **The terminal title.** Many agents put their state in the window title they set; a title that
   says working or waiting settles it.
3. **Screen rules.** For agents that report nothing, the bottom dozen or so rows of the pane are
   matched against rules for that agent, at most once per pane every 750 ms and only when the text
   changed. **Needs you** is strict here: it takes a visible approval or question prompt, never a
   quiet pane. Agents with no rules of their own only read as **Working** or **Idle**, from the
   pane's CPU use.

Panes whose foreground is not a known agent are never read.

## The commands

```sh
launcherctl agent working|blocked|idle|clear [--agent NAME] [--pane ID]
launcherctl agent session --agent NAME [--id ID]
launcherctl agent install-hooks
```

`launcherctl agent` reports for the pane it runs in (every shell has `TERMUX_LAUNCHER_PANE`, the
same pane id the `/v1/panes` routes use) unless `--pane` names another. Over HTTP it is
`POST /v1/panes/{id}/agent` with `{"agent":"claude","state":"working"}`. See
[LauncherCtl](LauncherCtl.md).

`launcherctl agent install-hooks` merges five hooks into `~/.claude/settings.json`, creating the
file if it is missing and leaving every hook already there alone. Run it once; it is safe to run
again, and nothing installs hooks for you:

| Claude Code event | Reported state |
| --- | --- |
| `UserPromptSubmit` | working |
| `Notification` | blocked |
| `Stop` | idle |
| `SessionEnd` | clear |
| `SessionStart` | the conversation id (see below) |

## Resuming a conversation

Saved sessions bring an agent back in the conversation it was in. Each agent tells the launcher its
conversation id for the pane it runs in, and the launcher saves it with the pane:

| Agent | How the id arrives | Restore runs |
| --- | --- | --- |
| Claude Code | the `SessionStart` hook above | `claude --resume <id>` |
| Codex | a `SessionStart` hook in `~/.codex/hooks.json` | `codex resume <id>` |
| OpenCode | the plugin `~/.config/opencode/plugins/termux-launcher-session.js` | `opencode --session <id>` |

`launcherctl agent install-hooks` sets up all three; the **Set up resume** button in the save field
sets up the agents running in your panes. Neither touches a hook or plugin you wrote. Codex skips a
hook until you trust it once: open Codex and run `/hooks`. OpenCode only reports a conversation it
starts or updates, so picking an older one inside OpenCode is not followed.

If the conversation no longer exists, the resume fails at once and the pane starts the agent fresh,
printing one line saying it started a new conversation. Over HTTP the report is
`POST /v1/panes/{id}/agent-session` with `{"agent":"claude","session_id":"..."}`.

## Adding a screen rule

For contributors: the rules are one ordered table, `AgentScreenRules`. Add an agent's rules in the
order **blocked, working, idle** (first match wins, so a screen showing both a prompt and a stale
working footer reads as blocked) and add its binary name to `KNOWN_AGENTS` in `AgentStatus` (plus
`hasScreenRules` if it now has rules). Agents run by an interpreter (`node`, `bun`, `npx` and the
like) are recognised from the script name. `AgentScreenRulesTest` takes sample screens as plain
strings, so a new agent is a few fixtures and one assertion each.
