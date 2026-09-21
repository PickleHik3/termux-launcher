# feat/kitty-notify (agent B) — K4 OSC 99, K5 OSC 22, Help TERM_PROGRAM note

Goal: kitty desktop notifications (OSC 99), mouse pointer shape (OSC 22), and one Help passage
about TERM_PROGRAM for Neovim 0.12 pictures.

Done: all three. Emulator parse + plumbing; app posts a real shade notification (two channels,
urgency -> importance, tap returns to the pane, swipe reports the close); pointer shape via
View.setPointerIcon; Help topic "Pictures in the terminal".

Next: nothing. Ready for the orchestrator to merge after agent A.

Blockers: none. Suites green: terminal-emulator 392, app 5465, 0 failures.

Hashes: built on 297303b6 and 029d7586; commits a59c4247 (emulator), 35ec3907 (app).

Waydroid: notification posted, shown in the shade, tap returned to the posting pane; OSC 22
consumed silently. Neither touch mouse mode draws a pointer, so K5 is hardware-mouse only.
