# Ghostty parity wave 1: lead report (uncommitted)

Branch feat/ghostty-parity, worktree /home/amalvajdan/Projects/termux-launcher/app/ghostty-parity.
Base c81774202 (ancestor check passed). Commits: 6edadbcdf (probe + raw results), 74acf6a86 (diff note).

## What was done
- Probe: scripts/terminal-probe/ghostty-parity-probe.sh (POSIX sh + stty/dd/od/awk). 109 probes.
  Each query is followed by a DA1 sentinel; the reply up to the sentinel's answer is kept.
  OSC 52 / 5522 reads are tagged `consent`. tty restored by trap on EXIT/INT/TERM/HUP.
- Ghostty reference: pacman ghostty 1.4.0-1 under cage 0.3.1, WLR_BACKENDS=headless,
  WLR_RENDERER=pixman, LIBGL_ALWAYS_SOFTWARE=1, empty config, --config-default-files=false.
  First attempt failed only because the Wayland socket path in the scratchpad exceeded 108 bytes;
  a short /tmp runtime dir fixed it. Ghostty logged "failed to export DMABUF, falling back to CPU
  presentation" (harmless). Run took ~1 min.
- Fast: `./gradlew --no-daemon -Pghostty=true :app:assembleDebug` -> BUILD SUCCESSFUL in 1m 19s;
  x86_64 APK carries lib/x86_64/libtermux-ghostty.so. emulator slot `emu2` (emulator-5556, tl16b)
  was leased by another session, so slot `emu` (tl16) was leased and started -> emulator-5554.
  Every adb call used `-s emulator-5554`. The pong serial (100.101.173.85:5555) was never addressed.
  `setprop debug.tl.terminal_core ghostty`, launcherctl pane open + pane write ran the probe;
  ~6 s per probe on the emulator (~9 min). DA1 in the Fast file: `\e[?62;4;22c` (Fast confirmed).
  logcat crash buffer empty after the run.
- Cleanup: property cleared, app force-stopped, home set back to NexusLauncher, lease released
  ("emu stopped (this lease started it)").

## Counts
96 same (82 byte-identical + 14 differing only by window size, cursor position or theme colours),
1 intended difference (xtversion), 12 gaps.

## Gap rows
da1 | `\e[?62;22;52c` | `\e[?62;4;22c` | gap G1 (52 missing; the 4 is intended)
da2 | `\e[>1;10;0c` | `\e[>1;0;0c` | gap G6
da3 | `<none>` | `\eP!\|00000000\e\\` | gap G7
decrqm-dec-12 | `\e[?12;1$y` | `\e[?12;2$y` | gap G4
decrqm-dec-5522 | `\e[?5522;2$y` | `\e[?5522;0$y` | gap G1
decrqss-decscusr | `\eP1$r1 q\e\\` | `\eP1$r2 q\e\\` | gap G4
xtgettcap-TN | `...=787465726D2D67686F73747479` (xterm-ghostty) | `<none>` | gap G5
dsr-996 | `\e[?997;2n` | `<none>` | gap G3
mode2031-dsr996 | `\e[?997;2n` | `<none>` | gap G3
osc52-read | `\e]52;c;\e\\` | `<none>` | gap G1
osc5522-read | OK/DATA/DONE | `\e]5522;type=read:status=EPERM\e\\` | gap G1
osc7501-query | `<none>` | `<none>` | gap G2 (decided target: Fast answers)

## Packages (per the coordinator's disk instruction)
Installed today in one transaction: ghostty (+ gtk4, libadwaita, gstreamer libs, pipewire,
xdg-desktop-portal etc. as its deps), cage (+ wlroots0.20, libinput, seatd, ...), weston,
xorg-server-xvfb. Cage worked first time with software GL. Removed with `pacman -Rn`, all from
today's transaction only: weston colord polkit libgusb xorg-server-xvfb xorg-xauth libxmu libxt
xorg-server-common xorg-xkbcomp xorg-setxkbmap libxfont2 libfontenc xorg-fonts-encodings.
Kept libsm and libice although `-Rs` offered them, because they predate today's transaction.
Remaining: ghostty 1.4.0-1, cage 0.3.1-1 and their dependencies.

## Odd or unverified
- Ghostty's OSC 52 read answered immediately with an empty payload instead of blocking on the
  `clipboard-read = ask` dialog; presumably because the headless clipboard is empty. Not verified.
- G4: whether the app's own cursor setting (blink on/off) should drive the default is a product
  call; the Fast report does not consult it today. Unverified what the app's default is.
- G7: unverified whether the C API can suppress the DA3 reply.
- Program lists per gap (neovim, tmux, helix, vim ...) are from knowledge of those programs, not
  tested here.
- No device-checker was used: the emulator run was a data collection, done directly under my own
  lease with the serial pinned. No fixers were used: the only code was the probe script, written
  and debugged against the Ghostty run directly.
- No unit tests run (brief: none required).
