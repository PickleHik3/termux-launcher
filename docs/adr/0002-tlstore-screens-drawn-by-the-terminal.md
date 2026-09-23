---
status: accepted
date: 2026-09-23
---

# tlstore's screens are drawn by the terminal itself, by a separate tlstore-ui program

tlstore's redesign wants a store that looks designed (cover pictures, script titles, large type,
motion) and shows off the terminal features the launcher ported from kitty. We decided that a new
`tlstore-ui` program, written in Rust and shipped in the APK, draws every screen with the terminal's
own means: real text, text sizing (OSC 66), kitty pictures placed under and between the text, SGR
mouse for touch, and synchronized output for flicker-free motion. It calls the existing `tlstore`
shell script for every install, update, remove and signature check, and never installs anything
itself. The rich screens exist only inside the launcher; official Termux, ssh and tmux get plain
commands.

This reverses Revision 3 of the tlstore spec (2026-09-21), which chose an fzf browser over a compiled
program; the fzf browser never shipped to anyone, so `tlstore browse` is removed.

Alternatives weighed, in the order they were considered:
- A web inset: a live Android web view the launcher places in terminal rows under a terminal frame.
  Decided first, then dropped the same day once the approved screens turned out to need nothing the
  terminal cannot draw; it would have added launcher-side placement, touch routing and web-view
  failure handling for no visible gain.
- Drawing HTML to kitty pictures with a small engine (Blitz, litehtml): beta or too limited.
- zenbu-labs/terminal-browser or awrit-style Chromium: desktop-only, or hundreds of MB and seconds to
  start, with text turned into pictures.
- The fzf browser of Revision 3: cannot draw sizes, pictures or tap targets.

Consequences: the script gains machine-readable catalog fields and a progress stream for the UI to
read; the launcher ships one more binary per ABI and gains a way for the store to put the in-app
keyboard away. Design and build plan: `project-docs/tlstore/REVISION-5.md`.
