# Wall rework, phase two (feat/wall-rework-phase2, from 2026-10-18)

Phase one is frozen on `feat/wall-rework` at 96525b145 (spec: `wall-rework-2026-10-17.md`, ADR 0008,
glossary "The wall and its chrome"). Phase two builds on it on this branch, one slice at a time, each
slice merged only with its unit tests green and an emulator or Surface pass recorded in
`project-docs/device-checks.md`.

## Slices, in order

1. **The status bar's capsule form.** A third form beside compact and expanded: a thin material
   capsule at the top centre. A continuous status swipe sizes the bar by where the finger lets go;
   a quick swipe steps one form; the bar folds to the capsule after a short idle; the capsule is
   Minimal mode's default form. Everywhere on the wall (Home with floats, the Workspace).
   Status 2026-10-18: merged at 1458207d8 + d3f2fa8e7 (StatusBarFormPolicy, fold motion, gesture,
   view); seen on the emulator; ink fix and the Minimal-mode bar owed.
2. **Style, Corners and Margin on Home** as Home's own settings (today global).
3. **Parallax between Home pages and windows** (the wallpaper pans with the wall).
4. **Saved sessions record the float flag and page**; a saved float loads as a float.
5. **Window reorder** by drag in the window bar; consume/expel of panes into and out of columns.
6. **The first-boot tour**, rewritten for the wall (switched off since 651ea9d7f).
7. **The icon grid on Home pages**: app icons, folders, drag between pages.
8. **Per-toplevel X11 windows** (needs a rootless server).
9. **Rebuilding the row and the floats after a background kill** beyond Saved sessions.

## Rules carried over

- Glossary words only; policy with arithmetic first, views thin; settings pages are fragile.
- Workers branch from pre, which has moved past this branch's base: a worker merges
  `feat/wall-rework-phase2` with `--no-ff` and the orchestrator cherry-picks its commits here.
- Device checks: emulator first (no built-in widgets there: add a Battery widget to its Home before
  a status bar or widget pass), then the Surface (floating keyboard, Launcher3 as default home,
  x86_64), pong last and only when asked.
