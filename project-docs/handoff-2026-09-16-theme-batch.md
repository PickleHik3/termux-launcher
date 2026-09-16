# Handoff — Material theme batch (2026-09-16, overnight run)

Everything landed on branch **`integ/theme-keys-layout`** in worktree `../termux-launcher-integ`
(15 commits over dev `67454594`). `dev` is untouched because the main checkout held a peer
session's uncommitted help/keyboard edits. Nothing pushed.

To land it: `git merge --no-ff integ/theme-keys-layout` on `dev` once the peer's edits are
committed or stashed (they touch `strings.xml`, `TermuxActivity.java`, `TermuxInAppKeyboard.java`,
`Keyboard2View.java`; the integ branch touches the first three too, expect a small merge in each).
Feature worktrees `../wt-trim-copy`, `../wt-keyboard-theme`, `../wt-extra-keys`,
`../wt-theme-pipeline`, `../wt-material-palette` are merged and can be removed.

## What landed

| Branch | What | Where to read |
|---|---|---|
| feat/trim-copy | Terminal › Clipboard "Trim trailing spaces on wrapped lines" (default on). A wrapped row trims like an unwrapped one; keeps one space when the next row starts mid-word. Fixed-width internal paths untouched. | `TerminalBuffer.getSelectedText`, `TerminalEmulator.setTrimWrappedTrailingSpaces` |
| feat/keyboard-theme | Default keyboard theme as Material 3: tray surfaceContainer(Low), letters surfaceContainerHighest/Lowest, function keys surfaceContainerHigh + onSurfaceVariant, Enter primary/onPrimary, latched primaryContainer, no borders, 10 dp radius. New `Theme.Palette.functionKeyBackground/functionLabelColor`, `Keyboard2View.isEnterKey`. Imported Base16 schemes derive a function tier. | `InAppKeyboardPaletteFactory`, `inapp-keyboard/UPSTREAM.md` |
| feat/extra-keys | `ExtraKeyEligibility` (5 bands, all 90 tools classified, reflection gate test); `ExtraKeyColorRole` (11 tokens, resolved from theme attrs on bind); `color` in the key JSON; place switches default to primary/secondary/tertiary containers on fresh installs; pick mode in the Appearance editor (staged via `ExtraKeyColorStaging`, commits on Done) and a Colour row in `ExtraKeyDetailSheet`. | `ExtraKeyEligibility` javadoc table, `docs/en/Launcher_Settings.md` |
| feat/theme-pipeline | Audit: uiMode flip and Material You recreate already re-apply colours, keyboard and templates; the wallpaper listener covers the foreground. New: `TermuxApplication.onConfigurationChanged` re-exports the palette and re-runs templates on a night-mask change while the launcher is stopped. New built-in template `herdr` (splices `[theme.custom]` into config.toml in a marker block, restores commented keys on undo). Signature test for the day/night flip. | `TermuxApplication`, `assets/theme-templates/herdr/`, `scripts/theme-templates/check.sh` |
| feat/material-palette | ANSI slots from HCT: canonical hues harmonized ≤15° toward primary, primary's chroma clamped [28, 52], tones 80/90 dark and 40/30 light, neutrals from the surface hue. Slots 0 and 7 exempt from the contrast floor, slot 8 at 3:1. oh-my-posh theme as container pills; starship palette gains every Material role name plus a commented Material prompt block. | `MaterialTerminalColorScheme.ansiSlots`, `MaterialTerminalColorSchemeTest` |
| docs | `project-docs/layout-freedom/SPEC.md` (scope A decided), entries in `release-notes-v0.2.40.md` | |

## Verification

- Full `testDebugUnitTest` on the merged tree: 4,271 app tests, one failure —
  `TerminalIOPreferencesDataStoreLazyModeTest`, the order-dependent flake AGENTS.md names; it passes
  alone. terminal-emulator, termux-shared and inapp-keyboard suites green.
  `scripts/theme-templates/check.sh`: all ten PASS.
- Waydroid (x86_64 debug APK, dark mode, teal wallpaper):
  - Extra keys greying on the Home place verified: mouse-mode and add-pane keys dimmed; place
    switches, keyboard and session browser usable. Coloured place-switch keys were not visible
    because the container carries an old saved layout (no migration by design).
  - Keyboard: at first every chip drew in the same translucent grey. Root cause: the shipped
    layout `termux_launcher_qwerty.xml` defines its own bottom row without `role` attributes, so
    ctrl/alt/space/enter parsed as `Role.Normal` and never reached the action/function/space
    paints. Fixed in `517d6307`: `Keyboard2View.tierFor` classifies role-less keys from their
    value and the layout carries the roles. **Verified on pong 2026-09-16 07:15**: Enter in the
    accent colour, function keys one tone below the letters.
  - Not looked at: light mode, the colour popup, the starship/oh-my-posh pills, herdr.

## Decisions taken without the developer (change freely)

- Trim rule keeps one space when a wrapped row ended in spaces and the next row starts mid-word.
- Extra keys colour tokens: primary/secondary/tertiary/error, their containers, surface_variant,
  black, white. DRAWER usable on Home; session browser/panel greyed on Display; `window.next/prev`
  stay usable on Display (they step apps there).
- Keyboard luminance targets for the forced light/dark variants are reasoned from M3 tones.

## Follow-ups

1. Pick mode has no affordance beyond tapping a key while the keyboard card is up; consider a
   hint row on the keyboard card. Pick mode drives extra-keys page 0 only.
2. Layout freedom L1–L5 per `project-docs/layout-freedom/SPEC.md` — scope A, portrait side
   columns allowed, start after this batch is merged.
3. The starship Material prompt ships commented out (apply.sh rewrites the marker block on every
   refresh). For a live version add an opt-in in `template.properties` or a second template id.
4. Remove the five `wt-*` worktrees.

## herdr copy mode exits mid-selection — herdr bug, report upstream

herdr v0.9.0 (`github.com/herdrdev/herdr`, tag v0.9.0). Two mechanisms, both worsened by another
pane redrawing (the braille spinner):

1. `src/client/shell/copy_mode.rs:518-522` — `move_copy_cursor` (every arrow/hjkl) calls
   `exit_copy_mode(false, …)` when `copy_hit()` finds no hit-test entry for the copy-mode pane.
   `src/client/shell/surface_patch.rs:77-78` forces every incoming surface patch onto the slow path
   while `copy_mode.is_some()`, and the slow path (`state.rs:1533-1581`, `self.hits =
   ShellHitMap::default()` at 1552/1580) wipes the hit map for all panes until the matching
   snapshot pair arrives. An arrow key in that window exits copy mode silently.
2. `src/raw_input.rs:22, 361-378` — `RAW_INPUT_IDLE_FLUSH_TIMEOUT_MS = 10`; a lone `0x1b` still
   in the buffer after 10 ms is flushed as a bare Escape, which clears the selection or exits copy
   mode. The launcher writes `ESC [ D` in one write, so this only bites when herdr's loop is busy.

Suggested fix: don't clear `hits` for panes unrelated to the incoming patch (or drop the motion
instead of exiting on a missing hit), and lengthen the lone-ESC idle flush. Launcher side: nothing
to change (`TerminalSession.write` queues the whole sequence; `TerminalKeyEventHandler
.dispatchSlider` sends plain DPAD events, the same path as tapped arrows).
