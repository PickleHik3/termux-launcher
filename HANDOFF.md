# feat/kitty-notify (agent B) — K4 OSC 99, K5 OSC 22, Help TERM_PROGRAM note

Goal: kitty desktop notifications (OSC 99), mouse pointer shape (OSC 22), and one Help passage
about TERM_PROGRAM for Neovim 0.12 pictures.

Done: all three, plus the coordinator's follow-up — title and body are cleaned of control
characters in KittyNotifications.clean before any client sees them, so an escape a program never
closed cannot carry raw bytes into the shade.

Next: nothing. Ready for the orchestrator to merge after agent A.

Blockers: none. Suites green: terminal-emulator 397, app 5465, 0 failures.

Hashes: built on 297303b6 and 029d7586; a59c4247, 35ec3907, 1fd35f83, 55fdd1fc.

Waydroid: gated before the sanitizer (notification posted, shown, tap returned to the pane; OSC
22 consumed silently). Not re-run — the orchestrator's device pass covers the merged state.
