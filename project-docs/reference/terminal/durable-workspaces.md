# Durable terminal workspaces

Status: implemented July 2026. This is the first Phase 3 project from the terminal modernization
study.

User workflows and safe API examples are in
[`docs/en/Terminal_Modernization.md`](../../../docs/en/Terminal_Modernization.md). The engineering
status map is [`terminal-modernization-status.md`](terminal-modernization-status.md).

## Contract

Workspace definitions live at `~/.termux/workspaces/<name>.json`. Version 1 records the ordered
session list, each session's ordered windows and selected window, each window's recursive split
tree, split orientation/weights, focused pane, pane CWD, and pane/session titles.

The document is declarative. It recreates terminals after app/service process death; it does not
claim to resurrect Unix processes. The default load starts a login shell in every recorded CWD.
Foreground argv is best-effort data from the existing procfs resolver and is captured only with
`captureCommands: true`. A load still ignores it unless `runCommands: true` is separately supplied.
This separation matters because the JSON files are user-editable executable input.

Example shape:

```json
{
  "version": 1,
  "name": "project",
  "savedAtEpochMs": 1785240000000,
  "currentSession": 0,
  "sessions": [{
    "name": "code",
    "currentWindow": 0,
    "windows": [{
      "activePane": 1,
      "root": {
        "type": "split",
        "orientation": "horizontal",
        "weightA": 1.0,
        "weightB": 1.0,
        "a": {"type": "pane", "cwd": "/data/data/com.termux/files/home/src"},
        "b": {"type": "pane", "cwd": "/data/data/com.termux/files/home/src/tests"}
      }
    }]
  }]
}
```

Later versions add optional keys only, so every older file still loads: version 2 a window's
`floats` (panes floating inside it, each a pane node plus `left`/`top`/`width`/`height` fractions),
version 3 a window's `name`, version 4 a window's `homeFloat`. Files newer than the build are
refused with `unsupported_version`.

A window saved while it was a Home float (a window of the current session floating over a Home
page) keeps its one pane as `root` and adds:

```json
"homeFloat": {
  "page": 1,
  "minimised": false,
  "portrait": {"left": 0.0, "top": 0.1, "width": 0.8, "height": 0.6},
  "landscape": {"left": 0.4, "top": 0.0, "width": 0.5, "height": 1.0}
}
```

`page` counts Home pages from 0; the shapes are fractions of the room floats are laid out in,
present for each orientation the float was shaped in. The current session's Home floats are saved
after its row's windows, in the order they were opened. On load each floats again on its page, or
on the last Home page when its page is gone, minimised as saved, at its shape for the screen's
orientation (born as a new float is when it has none there). A file without the key, a
`homeFloat` that is malformed, or one on a window of more than one pane loads the window into the
row; so does every float when there are no Home pages. A session whose windows were all floats
loads as its floats alone and adds no session to the row (a session is its row's windows), so a
file saved from an empty Workspace with Home floats loads back to an empty Workspace with those
floats; the wall stays on Home, or the last Home page slides in from the Workspace. Saving with an
empty Workspace and Home floats writes them as such a session. `currentWindow` picks among the
row's windows; a float named there leaves the row on the window before it. Replace closes the live
Home floats too.

## Autosave and recovery

Wall rework phase two, slice 9 (`ArrangementRecovery` decides, the activity does it).

**Autosave.** The arrangement (every session's row of windows with their split trees, then the
Home floats with page, minimised and both orientations' shapes) is written in this same version 4
model to `~/.termux/workspaces/.autosave.json`. No Saved sessions name can start with a dot, so
`list`, `load` and `delete` never see it, and the Saved tab never lists it. It is written off the
main thread 1.5 s after the last change to the windows, their trees, the row or the floats (the
drawer's rebuild and the floats' change are the hooks), as soon as a change leaves nothing open,
and when the activity stops. It is armed only once the activity has settled its start, so a cold
start waiting for its rebuild never overwrites it. Commands are never captured into it. A pane's
working directory is read at each write, so a `cd` made while the launcher is in the background
is caught only by the next write. An empty arrangement is the same document with
`"sessions": []`. **Exit** (the notification's) writes that empty document itself before its
shells die, and the activity's autosave stands down for the rest of that service's life; closing
the last window and the last float writes it too. A missing, unreadable or invalid autosave reads
as none.

**Recreate, service alive.** The pane layout kept with the activity's instance state now carries
the Home floats as well as the row's trees, every pane keyed by its shell's `TerminalSession`
handle (`mHandle`, a UUID per shell in the service). On connect the floats come back first,
around the same running shells, on their page (the last one if it is gone), minimised as they
were, at the shape for the orientation the screen is in (a turn while away is met as a live turn
is: the shape remembered for the new orientation, else the float as it was); then the row's
windows and trees, then any shell the layout does not name is adopted as a window of its own, as
before. With no Home page a float's shell is adopted into the row. The process keeps the last
layout too (on stop, destroy and instance save), so an activity that comes back without instance
state while the service lived (finished, swiped from Recents) rebuilds the same way.

**Cold start, process killed.** With no live shell, a cold process reads the autosave:

| Autosave | Start in = Home | Start in = Workspace |
|---|---|---|
| an arrangement | rebuilt; Home page 1 shows, floats on their pages | rebuilt; the Workspace shows (one fresh window too if every saved window was a float) |
| empty | empty Workspace, Home shows, no shell | one fresh shell window |
| none (first start) | one fresh shell, Home shows | one fresh shell window |

With no Home to show (no Home pages, and the launcher is not the phone's home app, where closing
the last shell ends the app) the start goes by the Workspace column whatever Start in says.

The rebuild is a Load without Replace into the empty service: one login shell per saved pane in
its saved working directory, the windows, trees, floating panes and Home floats, after the
bootstrap answers. A notice says "Windows restored from before the restart". **Saved commands are
never replayed on recovery** (there are none in the autosave): a crashed run is not repeated, and a
`command` belongs only to explicit Saved sessions loaded with "Also start what was running". A
launch that names its own shell (New session, failsafe) never rebuilds. Scrollback and running
programs are not restored: the shells are new. A rebuild that fails falls back to one fresh shell.

An explicit Saved sessions **Load** still works as before and, being a change to the arrangement,
replaces the autosave within 1.5 s.

## Agent and CLI actions

The registry exposes four terminal-executor tools. They are intentionally agent/CLI-only because
the command palette cannot prompt for a required workspace name.

| Tool | Important arguments | Risk |
|---|---|---|
| `workspace.save` | `name`, `overwrite=false`, `captureCommands=false` | medium, confirmed |
| `workspace.load` | `name`, `mode=append|replace`, `runCommands=false` | high, confirmed |
| `workspace.list` | none | low |
| `workspace.delete` | `name` | high, confirmed |

Append retains the live workspace and focuses the restored one. Replace creates and validates all
new terminals and trees first, then removes the old workspace. Creation failures tear down the new
terminals without mutating the old topology. The eight-terminal UI limit is checked against the
eventual topology before work begins.

## Storage and validation

- Names are trimmed, at most 64 Unicode code points, begin with a letter/digit, and then contain
  only letters, digits, spaces, `_`, `-`, or `.`. Callers omit the `.json` suffix.
- Canonical paths must remain below the Termux home directory, including when `.termux` is a
  symlink. Directories/files are made owner-only.
- Saves use a same-directory temporary file, `fsync`, and atomic replacement when the filesystem
  supports it. Files larger than 1 MiB are rejected.
- Definitions are validated before any live mutation: version, indexes, tree types/depth, positive
  finite weights, bounded text/argv, and at most 64 serialized panes.

## Verification

Autosave and recovery are covered by `ArrangementRecoveryTest` (every start),
`HomeFloatRecreateTest` (floats around live shells) and the autosave cases in
`TerminalWorkspaceStoreTest`.

Host coverage lives in `TerminalWorkspaceStoreTest`, `TerminalPaneControllerTest`,
`LauncherToolRegistryTest`, and `TerminalActionDispatcherTest`. It covers JSON and filesystem
round trips, overwrite/delete behavior, hostile names, malformed and unsupported documents,
structural bounds, durable split-tree reconstruction, registry schemas/risks, and dispatch routing.

Device smoke verification on Pong (Nothing A065, Android 16) saved a disposable one-session,
two-window workspace, confirmed its version/topology and mode `600`, listed it, appended it with
`runCommands: false`, observed the drawer grow from one to two sessions, closed the restored
session, observed the original one-session/two-window state return, and deleted the definition.
The final list was empty and bounded app-process logs contained no workspace or crash errors.
Replace mode was deliberately not used against the user's real sessions.
