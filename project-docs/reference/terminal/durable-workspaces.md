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
