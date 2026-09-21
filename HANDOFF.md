# feat/kitty-notify (agent B) — K4 OSC 99, K5 OSC 22, Help TERM_PROGRAM note

Goal: kitty desktop notifications (OSC 99) end to end, pointer shape (OSC 22), and one Help
passage about TERM_PROGRAM for Neovim 0.12 pictures.

Done: emulator side complete — KittyNotification/KittyNotifications, OSC 22 + 99 cases in
doOscSetTextParameters, TerminalOutput/TerminalSession/TerminalSessionClient plumbing,
KittyNotificationsTest (23 tests green).

Next: app side — post a real Android notification with a tap intent back to the pane; apply the
pointer shape with View.setPointerIcon; Help passage; Waydroid gate.

Blockers: none.

Hashes: built on 297303b6 (kitty file medium) and 029d7586 (spec).

Finding: neither touch mouse mode draws a pointer overlay, so K5 is hardware-mouse only.
