# feat/kitty-notify (agent B) — K4 OSC 99, K5 OSC 22, Help TERM_PROGRAM note

Goal: kitty desktop notifications (OSC 99) end to end, pointer shape (OSC 22), and one Help
passage about TERM_PROGRAM for Neovim 0.12 pictures.

Done: emulator parse + plumbing; app posts a real shade notification (two channels, urgency ->
importance, tap returns to the pane, swipe reports the close); pointer shape via
View.setPointerIcon; new Help topic "Pictures in the terminal".

Next: Waydroid gate (OSC 99 script, screenshot the shade, tap it; then OSC 22).

Blockers: none. Suites green: terminal-emulator 392, app 5465, 0 failures.

Hashes: built on 297303b6 and 029d7586; emulator commit a59c4247.

Finding: neither touch mouse mode draws a pointer overlay, so K5 is hardware-mouse only.
