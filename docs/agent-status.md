# Agent status

What the AI coding agent in a terminal pane is doing, shown where you can see it without opening
the pane.

## What is shown

- **Window chips** (the status row's pill list): a small round dot in front of the label — the
  working accent while the agent works, the warm attention colour when it is waiting for you, the
  muted label colour when it is idle. Screen readers get one extra sentence after the window name.
- **Sessions browser**: the state word after each pane's foreground name, in the same three
  colours — `claude · Working`, `codex · Needs you`, `claude · Idle` — and the session's header row
  carries the rolled-up dot.

The three states, and the only words a user ever sees:

| State | Word | Means |
| --- | --- | --- |
| working | **Working** | the agent is on a turn |
| blocked | **Needs you** | it is waiting for an answer: a permission prompt or a question |
| idle | **Idle** | it is running, at its prompt, with nothing to do |

A window rolls up its panes and a session rolls up its windows, **Needs you** over **Working** over
**Idle** over nothing at all.

## Where the reading comes from

Two sources, and a hook report always wins while it stands:

1. **Hooks.** The agent reports its own state through `launcherctl agent <state>`. A report holds
   until the next report, until `launcherctl agent clear`, or until the agent process leaves the
   pane's foreground.
2. **Screen rules.** For agents that report nothing, the bottom ~12 rows of the pane are matched
   against a per-agent table (`AgentScreenRules`), throttled to one reading per pane per 750 ms and
   skipped entirely while the text has not changed. **Needs you** is strict here: it takes a visible
   approval or question prompt, never a quiet pane. Agents with no table of their own only ever read
   as **Working** or **Idle**, from the pane's CPU use.

Panes whose foreground is not one of the known agent binaries are never read at all.

## The commands

```sh
launcherctl agent working|blocked|idle|clear [--agent NAME] [--pane ID]
launcherctl agent install-hooks
```

`launcherctl agent` reports for the pane it is run in — every shell gets `TERMUX_LAUNCHER_PANE` in
its environment, holding the same pane id the `/v1/panes` routes address — unless `--pane` names
another. Over HTTP it is `POST /v1/panes/{id}/agent` with `{"agent":"claude","state":"working"}`.

`launcherctl agent install-hooks` merges four hooks into `~/.claude/settings.json`, creating the
file if it is missing and leaving every hook already there alone. It is idempotent, and nothing
installs hooks automatically:

| Claude Code event | Reported state |
| --- | --- |
| `UserPromptSubmit` | working |
| `Notification` | blocked |
| `Stop` | idle |
| `SessionEnd` | clear |

## Adding a screen rule

`AgentScreenRules` is one ordered table. Append the agent's rules **blocked, working, idle** — first
match wins, so a screen showing both a prompt and a stale working footer reads as blocked — and add
the agent's binary name to `KNOWN_AGENTS` in `AgentStatus` (plus `hasScreenRules` if it now has a
table). Interpreted agents (`node`, `bun`, `npx`, …) are recognised from the script name in their
argv. Nothing else in the app knows the rules exist; `AgentScreenRulesTest` takes fixture screens as
plain strings, so a new agent is three fixtures and one assertion each.
