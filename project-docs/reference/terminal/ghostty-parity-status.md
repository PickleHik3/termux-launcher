# Fast engine parity with first-party Ghostty: status

Status as of 2026-11-12. Entry point for anyone continuing this work; each fact lives in the
record it points to.

**State:** built on `feat/ghostty-parity` (code through `f5d65c469`, base `c81774202` on
`v2-redesign`). Emulator-checked. Awaiting the developer: the pong pass, four decisions below,
and the merge, which is theirs to make.

## The yardstick

Parity means the Fast engine (libghostty-vt 1.0.0, pinned) gives the reply first-party Ghostty
1.4.0 gives to every query it answers. It is proven by a **probe**: one script run in real
Ghostty and in a Fast pane, and the diff of the two result files.

- Probe and result files: `scripts/terminal-probe/`.
- The diff, the gap list and every rerun: [`ghostty-parity-probe-diff-2026-11-11.md`](ghostty-parity-probe-diff-2026-11-11.md).
  A change to Fast's protocol surface ends with a probe rerun added to that note.
- How to run it (Ghostty headless under `cage`, Fast on the emulator): [`ghostty-parity-wave1-report.md`](ghostty-parity-wave1-report.md).
  Ghostty 1.4.0 and `cage` are installed on the development PC for this.

## What Fast does now

| Area | Behaviour | Record |
|---|---|---|
| Clipboard read (OSC 52 `?`, OSC 5522) | A consent sheet per request, shown only while that terminal is on screen: Deny, Allow once, Allow for this session, Always allow `<program>`. The program is the pty's foreground process. Settings › Terminal › Clipboard & compatibility › Reset clipboard permissions clears the "always" grants. Plain text only. | [wave 2](ghostty-parity-wave2-report.md), [wave 3](ghostty-parity-wave3-report.md) |
| Colour scheme (DSR 996, mode 2031) | Answered by the core from the terminal background's light/dark class | [wave 2](ghostty-parity-wave2-report.md) |
| OSC 7501 program status, spec 0.4 | Query answered; a record store per pane; the window pill shows the most urgent record as `<name> <word> [NN%]`; an in-app notice for needs input, done and error when the pane is not in front | [checklist](osc7501-program-status-checklist-2026-11-11.md), [wave 3](ghostty-parity-wave3-report.md) |
| Keys, mouse, focus | Encoded by libghostty's encoders from the live terminal state. `debug.tl.terminal_input=java` (read when a pane opens) restores the Java encoder. | [wave 4](ghostty-parity-wave4-report.md), [byte differences](ghostty-parity-input-differences-2026-11-11.md) |

## Decisions that bind later work

Made by the developer on 2026-11-11.

- Parity work lands on Fast only. The Standard engine keeps its bytes; Fast stays opt-in.
- Fast identifies as itself: XTVERSION is the app's name, XTGETTCAP `TN` is the session's `TERM`.
- Title report (CSI 21 t), window resize requests and checksum reports stay unanswered, as in
  Ghostty's defaults, with no setting.
- Where Fast does more than Ghostty (sixel, OSC 99, OSC 66 plain text, OSC 7501) it keeps doing
  so. The full list of intended differences is in the diff note, "Intended differences seen".
- Clipboard trust is keyed to the local foreground program, the one identity terminal output
  cannot supply.
- Several OSC 7501 records in one pane: the pill shows needs input, then error, working, done, idle.
- The Java encoder fallback lives for one pre-release, then the property and
  `KittyKeyEncoder`'s Fast route are removed.

## Open, for the developer

1. **Pong pass.** Nothing from this branch has run on a phone. Owed checks are in
   `project-docs/device-checks.md` and `project-docs/developer-checks.md` under "Ghostty parity".
2. **OSC 7501 notice.** It is an in-app banner, shown while the launcher is visible, as OSC 9 is.
   A program that needs input while another app is in front reaches nobody. Recommended: an
   Android notification for needs input when the launcher is off screen.
3. **Consent sheet look.** It uses the same glass sheet as Select URL and is hard to read over a
   busy wallpaper. Changing it changes every sheet.
4. **DA3.** Fast answers it; Ghostty stays silent. The library always replies; the shim could
   drop the reply. Recommended: leave it.
5. **Merge** into `v2-redesign` after the pong pass.

## Next round: the app layer

Ghostty features that sit above the protocols and are the embedder's to build, in the order
recommended. Sources for each are in [`ghostty-first-party-parity-research-2026-11-11.md`](ghostty-first-party-parity-research-2026-11-11.md).

1. Shell-integration scripts and their injection (the rest depend on the prompt marks they emit)
2. Command-finished notification
3. Jump between prompts
4. Link detection with tap-to-open beyond OSC 8
5. Tap to move the cursor on the prompt line
6. Password-input detection
