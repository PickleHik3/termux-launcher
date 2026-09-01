# project-docs

Durable records: why things are the way they are, what is left, and what shipped. Ephemeral agent
scratch — working notes, todo checklists, research dumps — does not belong here (see AGENTS.md).
Contributor and user documentation lives in [`../docs/`](../docs/) instead.

Two rules keep this directory from rotting again:

- **A finished checklist is deleted, not left ticked.** The merged commit is the implementation
  record. What survives here is the *reasoning*, which git log cannot carry.
- **A document that contradicts AGENTS.md is worse than no document.** Build commands, branch
  models and release process live in AGENTS.md alone; nothing here restates them.

## Start here

| | |
|---|---|
| [`terminal-modernization-status.md`](terminal-modernization-status.md) | Engineering overview of the whole terminal project — what is delivered, the user-file contracts, and links to every owning record. |
| [`plans/backlog.md`](plans/backlog.md) | The one authoritative list of unfinished work, with a stated reason each deferred item waits. |

## Delivered records

Design and rationale for things that shipped. Each is the authority for *why*; the public,
task-oriented guide is [`../docs/en/Terminal_Modernization.md`](../docs/en/Terminal_Modernization.md).

| | |
|---|---|
| [`plans/split-panes.md`](plans/split-panes.md) | Sessions → windows → recursive pane trees, the window strip, single-pane compatibility. |
| [`plans/automatic-pane-layouts.md`](plans/automatic-pane-layouts.md) | The six layout presets, equalize, rotate, move-to-edge. |
| [`plans/durable-workspaces.md`](plans/durable-workspaces.md) | `~/.termux/workspaces/*.json` — the format, atomic storage, safe restore. |
| [`plans/session-browser.md`](plans/session-browser.md) | The searchable session/window/pane browser and clone-with-CWD. |
| [`plans/action-registry-terminal-actions.md`](plans/action-registry-terminal-actions.md) | The terminal action registry, command palette, chords and user bindings. |
| [`plans/kitty-protocol-features.md`](plans/kitty-protocol-features.md) | Kitty graphics, keyboard protocol, OSC 8/133, underlines, cursor trail, parser hardening. |
| [`plans/fonts-and-shaping.md`](plans/fonts-and-shaping.md) | `fonts.conf`, the four faces, fixed-cell shaping, symbol maps, geometric drawing, the font picker. |
| [`inapp-keyboard-design.md`](inapp-keyboard-design.md) | The `:inapp-keyboard` module and its launcher host. Paired with [`../inapp-keyboard/UPSTREAM.md`](../inapp-keyboard/UPSTREAM.md), which owns the vendored deviations. |
| [`plans/launcherctl-agent-platform.md`](plans/launcherctl-agent-platform.md) | The LauncherCtl agent platform: tool registry, agent APIs, event storage, MCP bridge. |

## Open studies

| | |
|---|---|
| [`plans/keyboard-mis-input-correction.md`](plans/keyboard-mis-input-correction.md) | Whether the in-app keyboard can fix mis-taps without a dictionary. Nothing implemented. |
| [`plans/nix-edition-vanilla-study.md`](plans/nix-edition-vanilla-study.md) | Whether the Nix edition could track upstream nix-on-droid and ship only the launcher's extras. What the fork actually carries, what going vanilla would cost new and existing users, and why it was declined on 2026-09-01. Nothing implemented. |
| [`plans/pane-wall-x11-study.md`](plans/pane-wall-x11-study.md) | The pane wall: home-screen pane, terminal area, embedded X11 pane. Why termux-x11 gets forked and bundled, and why the wall is an outer container, not a new leaf type. Nothing implemented. |

## Release notes

[`release-notes.md`](release-notes.md) is the changelog: every shipped release, newest first, with
each version's edition-exclusive items folded into its own `Editions` list. Written from the commit
range, for someone holding the phone.

The release being written keeps its own `release-notes-v<version>.md` so `gh release create
--notes-file` can point at it; once every edition is published it moves into `release-notes.md` and
that file goes — one history, never two. Versions before v0.2.35 live on their GitHub releases only.
AGENTS.md has the full convention.

## Verification

[`verification/`](verification/) holds the runnable probes, not prose: on-device scripts for
keybinds, terminal actions, terminal protocols and OSC 133 shell integration, the JVM escape-parser
fuzz harness, and the agent-platform smoke clients under `verification/tai-ext/`.
