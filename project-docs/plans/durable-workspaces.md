# Durable terminal workspaces

Status: implemented July 2026. This is the first Phase 3 project from the terminal modernization
study.

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
