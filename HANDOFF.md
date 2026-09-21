# HANDOFF — agent A, feat/kitty-modes

Goal: K1 (mode 2026 synchronized output), K3 (colour stack CSI # P/Q/R), K8 (OSC 7 cwd),
K9 (mode 2048 in-band resize) in terminal-emulator, plus the OSC 7 shell snippets and the
new-pane working directory.

Done: all four features implemented with unit tests; `:terminal-emulator:testDebugUnitTest`
green (405 tests, 0 failures). Bash and zsh snippets emit OSC 7 and were checked against real
bash/zsh (spaces and multi-byte paths percent-encode correctly).

Next: Waydroid gates (a) 2026 under nvim/fzf, (b) colour push/pop with a killed script,
(c) new pane opens in the shell's folder, (d) 2048 resize report.

Blockers: none.

Hashes: built on 029d7586 (spec) and 297303b6.
