# Kitty terminal features — round 1 (2026-09-21)

Decided on the review page `.lavish/kitty-features.html` (Lavish session e2e874ab5bc34507).

## Decisions
- K1 flicker-free redraws (private mode 2026) — go
- K3 colour save/restore (XTPUSHCOLORS / XTPOPCOLORS / XTREPORTCOLORS, `CSI # P / Q / R`) — go
- K4 rich notifications (OSC 99) — go
- K5 mouse pointer shape (OSC 22) — go; the user notes the terminal already has two touch
  mouse modes (short long-press then drag; the mouse-mode extra key turning touches into a pure
  mouse), so the shape applies to whatever pointer those modes present, not only a hardware one
- K8 shell reports its folder (OSC 7) — go
- K9 in-band resize notifications (private mode 2048) — go
- K6 file transfer (OSC 5113) — not selected, deferred
- K7 shared-memory image medium — closed, impossible on bionic (no shm_open)
- K2 text sizing (OSC 66) — option A: spec after round 1 lands, build in a later round
- TERM_PROGRAM — option A: not set by the launcher; documented in Help as a shell-rc line

## Build plan
| Agent | Branch | Deliverable | Depends on |
|---|---|---|---|
| A | feat/kitty-modes | K1, K3, K8, K9 in terminal-emulator (+ shell-integration snippets for OSC 7) | — |
| B | feat/kitty-notify | K4, K5 in emulator OSC region + app; Help note for TERM_PROGRAM | — |

Co-tenancy in `TerminalEmulator.java`: A owns the private-mode table (~700-760), the CSI
dispatch for `#` intermediates, the resize path and `doOscSetTextParameters` case 7; B owns
`doOscSetTextParameters` cases 22 and 99 only. Merge order: A then B; conflicts resolved here.

## Gates
- terminal-emulator unit tests green on the merged state
- Waydroid: Neovim/fzf redraw under 2026; a colour-changing script killed mid-way restores the
  palette; `printf` OSC 99 posts a tappable notification returning to the pane; new pane opens
  in the shell's folder
