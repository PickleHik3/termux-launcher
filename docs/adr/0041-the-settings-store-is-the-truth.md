---
status: accepted
date: 2026-11-27
---

# The settings store is the truth; terl.conf applies once

Spec: `project-docs/active/tiling-desktop-2026-11-27.md`, section "Edit mode and Settings",
terl.conf. Builds on ADR 0040.

terl.conf is watched and applies when saved, and every setting can also be changed in Settings, in
edit mode, from launcherctl and by the Assistant. Two sources of truth disagree as soon as both are
edited, and a dotfile user expects the file to win, as it does in most Linux programs.

## Decision

1. **The settings store is the truth.** terl.conf is a way in, not a mirror: a save is applied once
   and Terl remembers what it applied, so an unchanged file never replays (at start-up or later)
   over edits made since. A file changed while Terl was not running applies at the next start.
2. **Terl never writes into terl.conf by itself.** Export and Copy as config bring the file up to
   date when the user asks.
3. **A file is a patch.** A setting it leaves out keeps its value. `follow` puts a surface's own
   value back on Global and `default` resets any setting, so the file can say everything the
   screens can; export writes every exported setting, with `follow` for a surface on Global.
4. **A save is one update**: every line lands together or none does, rolled back on any failure,
   with a notice naming the line.

## Considered

- **The file is the truth both ways**: every UI change rewrites its line, keeping comments. The file
  in git would always be the setup, but Terl would edit a file the user keeps by hand, and an
  editor's save could collide with a write from the screens.
- **The file wins at every start**, as most Linux configs do. Every UI edit to a setting the file
  names would revert on the next restart, which reads as the app forgetting.
- **Absent means default**: deleting a line would reset it, but a pasted snippet or a short file
  would reset everything else.

## Consequences

- terl.conf can drift behind the screens until it is exported; that is the price of never writing
  into the user's file.
- Terl keeps a record of the last file it applied (its content, not its time) to decide whether a
  file is new.
- `follow` and `default` are reserved words that no setting may take as a value.
