# Ghostty parity probe: Fast engine vs Ghostty 1.4.0 (2026-11-11)

Every reply the Fast engine gives, set against first-party Ghostty 1.4.0 with a default config.
Yardstick and intended differences: [`ghostty-first-party-parity-research-2026-11-11.md`](ghostty-first-party-parity-research-2026-11-11.md).

## How the results were taken

- Probe: [`scripts/terminal-probe/ghostty-parity-probe.sh`](../../../scripts/terminal-probe/ghostty-parity-probe.sh)
  (POSIX sh, stty, dd, od, awk). Each query is sent alone, followed by a DA1 request as a sentinel;
  the reply up to the sentinel's answer is recorded. Raw files:
  [`results/ghostty-1.4.0-arch1.txt`](../../../scripts/terminal-probe/results/ghostty-1.4.0-arch1.txt),
  [`results/fast-c81774202.txt`](../../../scripts/terminal-probe/results/fast-c81774202.txt).
- Ghostty: Arch package `ghostty 1.4.0-1` (XTVERSION `ghostty 1.4.0-arch1`), run headless under
  `cage 0.3.1` with software rendering and an empty config file:

  ```sh
  XDG_RUNTIME_DIR=<short dir> XDG_CONFIG_HOME=<dir with an empty ghostty/config> \
  WLR_BACKENDS=headless WLR_RENDERER=pixman WLR_LIBINPUT_NO_DEVICES=1 LIBGL_ALWAYS_SOFTWARE=1 \
  cage -- ghostty --config-default-files=false -e sh ghostty-parity-probe.sh <out>
  ```

  The Wayland socket path must stay under 108 bytes, so `XDG_RUNTIME_DIR` has to be short.
- Fast: debug build of `c81774202` with `-Pghostty=true`, x86_64, emulator AVD `tl16` (Android 16),
  `debug.tl.terminal_core=ghostty`, probe run in a pane opened with `launcherctl pane open`.
  DA1 answered `\e[?62;4;22c`, the Fast engine's reply. No crash (crash buffer empty).

Values that depend on the window, the cursor position or the colour theme (sizes, CSR, colour
queries) differ in value but not in kind; they are marked "same" with the reason. Long replies are
cut at 60 characters below; the raw files hold them whole.

## Results

109 probes: **96 same, 1 intended difference, 12 gaps.**

| Probe | Ghostty 1.4.0 | Fast | Verdict |
|---|---|---|---|
| da1 | `\e[?62;22;52c` | `\e[?62;4;22c` | gap G1 (52 missing; the 4 is intended) |
| da2 | `\e[>1;10;0c` | `\e[>1;0;0c` | gap G6 |
| da3 | `<none>` | `\eP!\|00000000\e\\` | gap G7 |
| xtversion | `\eP>\|ghostty 1.4.0-arch1\e\\` | `\eP>\|termux-launcher(2.0.0-pre.1) (libghostty-vt 1.0)\e\\` | intended difference |
| decrqm-ansi-2 | `\e[2;2$y` | `\e[2;2$y` | same |
| decrqm-ansi-4 | `\e[4;2$y` | `\e[4;2$y` | same |
| decrqm-ansi-12 | `\e[12;1$y` | `\e[12;1$y` | same |
| decrqm-ansi-20 | `\e[20;2$y` | `\e[20;2$y` | same |
| decrqm-dec-1 | `\e[?1;2$y` | `\e[?1;2$y` | same |
| decrqm-dec-3 | `\e[?3;2$y` | `\e[?3;2$y` | same |
| decrqm-dec-4 | `\e[?4;2$y` | `\e[?4;2$y` | same |
| decrqm-dec-5 | `\e[?5;2$y` | `\e[?5;2$y` | same |
| decrqm-dec-6 | `\e[?6;2$y` | `\e[?6;2$y` | same |
| decrqm-dec-7 | `\e[?7;1$y` | `\e[?7;1$y` | same |
| decrqm-dec-8 | `\e[?8;2$y` | `\e[?8;2$y` | same |
| decrqm-dec-9 | `\e[?9;2$y` | `\e[?9;2$y` | same |
| decrqm-dec-12 | `\e[?12;1$y` | `\e[?12;2$y` | gap G4 |
| decrqm-dec-25 | `\e[?25;1$y` | `\e[?25;1$y` | same |
| decrqm-dec-40 | `\e[?40;2$y` | `\e[?40;2$y` | same |
| decrqm-dec-45 | `\e[?45;2$y` | `\e[?45;2$y` | same |
| decrqm-dec-47 | `\e[?47;2$y` | `\e[?47;2$y` | same |
| decrqm-dec-66 | `\e[?66;2$y` | `\e[?66;2$y` | same |
| decrqm-dec-67 | `\e[?67;2$y` | `\e[?67;2$y` | same |
| decrqm-dec-69 | `\e[?69;2$y` | `\e[?69;2$y` | same |
| decrqm-dec-1000 | `\e[?1000;2$y` | `\e[?1000;2$y` | same |
| decrqm-dec-1002 | `\e[?1002;2$y` | `\e[?1002;2$y` | same |
| decrqm-dec-1003 | `\e[?1003;2$y` | `\e[?1003;2$y` | same |
| decrqm-dec-1004 | `\e[?1004;2$y` | `\e[?1004;2$y` | same |
| decrqm-dec-1005 | `\e[?1005;2$y` | `\e[?1005;2$y` | same |
| decrqm-dec-1006 | `\e[?1006;2$y` | `\e[?1006;2$y` | same |
| decrqm-dec-1007 | `\e[?1007;1$y` | `\e[?1007;1$y` | same |
| decrqm-dec-1015 | `\e[?1015;2$y` | `\e[?1015;2$y` | same |
| decrqm-dec-1016 | `\e[?1016;2$y` | `\e[?1016;2$y` | same |
| decrqm-dec-1035 | `\e[?1035;1$y` | `\e[?1035;1$y` | same |
| decrqm-dec-1036 | `\e[?1036;1$y` | `\e[?1036;1$y` | same |
| decrqm-dec-1039 | `\e[?1039;2$y` | `\e[?1039;2$y` | same |
| decrqm-dec-1045 | `\e[?1045;2$y` | `\e[?1045;2$y` | same |
| decrqm-dec-1047 | `\e[?1047;2$y` | `\e[?1047;2$y` | same |
| decrqm-dec-1048 | `\e[?1048;2$y` | `\e[?1048;2$y` | same |
| decrqm-dec-1049 | `\e[?1049;2$y` | `\e[?1049;2$y` | same |
| decrqm-dec-2004 | `\e[?2004;2$y` | `\e[?2004;2$y` | same |
| decrqm-dec-2026 | `\e[?2026;2$y` | `\e[?2026;2$y` | same |
| decrqm-dec-2027 | `\e[?2027;1$y` | `\e[?2027;1$y` | same |
| decrqm-dec-2031 | `\e[?2031;2$y` | `\e[?2031;2$y` | same |
| decrqm-dec-2033 | `\e[?2033;2$y` | `\e[?2033;2$y` | same |
| decrqm-dec-2048 | `\e[?2048;2$y` | `\e[?2048;2$y` | same |
| decrqm-dec-5522 | `\e[?5522;2$y` | `\e[?5522;0$y` | gap G1 |
| decrqm-dec-9999 | `\e[?9999;0$y` | `\e[?9999;0$y` | same |
| decrqss-sgr | `\eP1$r0m\e\\` | `\eP1$r0m\e\\` | same |
| decrqss-decscusr | `\eP1$r1 q\e\\` | `\eP1$r2 q\e\\` | gap G4 |
| decrqss-decstbm | `\eP1$r1;31r\e\\` | `\eP1$r1;25r\e\\` | same (screen size) |
| decrqss-decslrm | `\eP0$r\e\\` | `\eP0$r\e\\` | same |
| xtgettcap-TN | `\eP1+r544E=787465726D2D67686F73747479\e\\` | `<none>` | gap G5 |
| xtgettcap-Co | `\eP1+r436F=323536\e\\` | `\eP1+r436F=323536\e\\` | same |
| xtgettcap-colors | `\eP1+r636F6C6F7273=323536\e\\` | `\eP1+r636F6C6F7273=323536\e\\` | same |
| xtgettcap-RGB | `\eP1+r524742=38\e\\` | `\eP1+r524742=38\e\\` | same |
| xtgettcap-Tc | `\eP1+r5463\e\\` | `\eP1+r5463\e\\` | same |
| xtgettcap-Smulx | `\eP1+r536D756C78=5C455B343A25703125646D\e\\` | `\eP1+r536D756C78=5C455B343A25703125646D\e\\` | same |
| xtgettcap-Setulc | `\eP1+r536574756C63=5C455B35383A323A3A257031257B3635353336...` | `\eP1+r536574756C63=5C455B35383A323A3A257031257B3635353336...` | same |
| xtgettcap-Ss | `\eP1+r5373=5C455B25703125642071\e\\` | `\eP1+r5373=5C455B25703125642071\e\\` | same |
| xtgettcap-Se | `\eP1+r5365=1B5B302071\e\\` | `\eP1+r5365=1B5B302071\e\\` | same |
| xtgettcap-Ms | `\eP1+r4D73=5C455D35323B25703125733B25703225735C303037\e\\` | `\eP1+r4D73=5C455D35323B25703125733B25703225735C303037\e\\` | same |
| xtgettcap-Sync | `\eP1+r53796E63=5C455B3F32303236253F257031257B317D252D2574...` | `\eP1+r53796E63=5C455B3F32303236253F257031257B317D252D2574...` | same |
| xtgettcap-kbs | `\eP1+r6B6273=7F\e\\` | `\eP1+r6B6273=7F\e\\` | same |
| xtgettcap-smcup | `\eP1+r736D637570=1B5B3F3130343968\e\\` | `\eP1+r736D637570=1B5B3F3130343968\e\\` | same |
| xtgettcap-cnorm | `\eP1+r636E6F726D=1B5B3F31326C1B5B3F323568\e\\` | `\eP1+r636E6F726D=1B5B3F31326C1B5B3F323568\e\\` | same |
| xtgettcap-setrgbf | `\eP1+r73657472676266=5C455B33383A323A25703125643A25703225...` | `\eP1+r73657472676266=5C455B33383A323A25703125643A25703225...` | same |
| xtgettcap-fsl | `\eP1+r66736C=07\e\\` | `\eP1+r66736C=07\e\\` | same |
| xtgettcap-XX | `<none>` | `<none>` | same |
| dsr-5 | `\e[0n` | `\e[0n` | same |
| dsr-6 | `\e[1;1R` | `\e[25;1R` | same (cursor position) |
| dsr-dec-6 | `\e[?1;1;1R` | `\e[?25;1;1R` | same (cursor position) |
| dsr-996 | `\e[?997;2n` | `<none>` | gap G3 |
| dsr-998 | `\e[?999;1n` | `\e[?999;1n` | same |
| kitty-kbd-query | `\e[?0u` | `\e[?0u` | same |
| kitty-kbd-push1-query | `\e[?1u` | `\e[?1u` | same |
| kitty-kbd-pop-query | `\e[?0u` | `\e[?0u` | same |
| osc4-0 | `\e]4;0;rgb:1d1d/1f1f/2121\e\\` | `\e]4;0;rgb:3c3c/3a3a/4343\e\\` | same (theme colours) |
| osc4-1 | `\e]4;1;rgb:cccc/6666/6666\a` | `\e]4;1;rgb:b2b2/6666/7171\a` | same (theme colours) |
| osc4-255 | `\e]4;255;rgb:eeee/eeee/eeee\e\\` | `\e]4;255;rgb:eeee/eeee/eeee\e\\` | same |
| osc5-0 | `<none>` | `<none>` | same |
| osc10 | `\e]10;rgb:ffff/ffff/ffff\e\\` | `\e]10;rgb:ecec/e6e6/f2f2\e\\` | same (theme colours) |
| osc11 | `\e]11;rgb:2828/2c2c/3434\e\\` | `\e]11;rgb:1616/1717/1c1c\e\\` | same (theme colours) |
| osc12 | `\e]12;rgb:ffff/ffff/ffff\e\\` | `\e]12;rgb:b1b1/c6c6/ffff\e\\` | same (theme colours) |
| osc10-bel | `\e]10;rgb:ffff/ffff/ffff\a` | `\e]10;rgb:ecec/e6e6/f2f2\a` | same (theme colours) |
| osc104 | `<none>` | `<none>` | same |
| osc110 | `<none>` | `<none>` | same |
| osc111 | `<none>` | `<none>` | same |
| osc112 | `<none>` | `<none>` | same |
| osc21-fg-bg-cursor | `\e]21;foreground=rgb:ff/ff/ff;background=rgb:28/2c/34;cur...` | `\e]21;foreground=rgb:ec/e6/f2;background=rgb:16/17/1c;cur...` | same (theme colours) |
| osc21-palette | `\e]21;0=rgb:1d/1f/21;15=rgb:ea/ea/ea\e\\` | `\e]21;0=rgb:3c/3a/43;15=rgb:f7/f1/fd\e\\` | same (theme colours) |
| osc22-query | `<none>` | `<none>` | same |
| osc52-read | `\e]52;c;\e\\` | `<none>` | gap G1 |
| osc5522-read | `\e]5522;type=read:status=OK\e\\\e]5522;type=read:status=D...` | `\e]5522;type=read:status=EPERM\e\\` | gap G1 |
| osc7501-query | `<none>` | `<none>` | gap G2 |
| osc133-a | `<none>` | `<none>` | same |
| osc133-b | `<none>` | `<none>` | same |
| osc133-d | `<none>` | `<none>` | same |
| kitty-gfx-query | `\e_Gi=31;OK\e\\` | `\e_Gi=31;OK\e\\` | same |
| csi-14t | `\e[4;651;1270t` | `\e[4;950;988t` | same (screen size) |
| csi-16t | `\e[6;21;10t` | `\e[6;38;19t` | same (cell size) |
| csi-18t | `\e[8;31;127t` | `\e[8;25;52t` | same (screen size) |
| csi-21t | `<none>` | `<none>` | same |
| mode2048-enable | `\e[48;31;127;651;1270t` | `\e[48;25;52;950;988t` | same (screen size) |
| mode2048-disable | `<none>` | `<none>` | same |
| mode2031-enable | `<none>` | `<none>` | same |
| mode2031-dsr996 | `\e[?997;2n` | `<none>` | gap G3 |
| mode2031-disable | `<none>` | `<none>` | same |
| enq | `<none>` | `<none>` | same |

Not probed because nothing answers them in either engine: sixel (intended difference), OSC 66 and
OSC 99 (intended differences), title report CSI 21 t and window resize (both silent, as in Ghostty's
defaults, and probed above as `csi-21t`). ENQ is silent in both (Ghostty's `enquiry-response` is
empty by default).

## Gaps, most programs affected first

All shim lines are in `terminal-emulator/ghostty/ghostty_jni.c` at `c81774202`. The effect
callbacks are installed in the block at lines 499-511; every gap below except G6 and G7 is a
missing `ghostty_terminal_set` there (the option exists in the pinned vt-1.0.0 `terminal.h`).

**G1. Clipboard read is not wired** (`osc52-read`, `osc5522-read`, `decrqm-dec-5522`, the `52` in
`da1`). Fast answers nothing to `OSC 52 ; c ; ?`, so a program waiting for its paste hangs until
its own timeout; 5522 reads are refused with `EPERM`; mode 5522 is reported unknown (`0`); and
DA1 does not advertise `52`. Ghostty answers all four (its OSC 52 read goes through
`clipboard-read = ask`; here it answered at once with the empty clipboard). Affects every program
with an OSC 52 paste path: neovim's OSC 52 clipboard provider, tmux `set-clipboard`, helix,
kakoune, and anything that detects OSC 52 from DA1 `52`.
Change: install `GHOSTTY_TERMINAL_OPT_CLIPBOARD_READ` (`terminal.h:2507`) next to the clipboard
write at `ghostty_jni.c:507`, with a Java callback beside `GhosttyCore.onClipboardWrite`
(`GhosttyCore.java:352`, interface `GhosttyNative.java:130`) that reads through the same
`mSession.onReadTextFromClipboard()` the Standard engine uses (`TerminalEmulator.java:4460`); add
`52` to DA1 in `on_device_attributes` (`ghostty_jni.c:296-306`, feature list at 301-303).

**G2. OSC 7501 support query is unanswered** (`osc7501-query`). Ghostty is silent too, but the
decided target is that Fast answers it. Change: install `GHOSTTY_TERMINAL_OPT_PROGRAM_STATUS`
(`terminal.h:2644`) in the 499-511 block; the core answers `?` only while the effect is set.

**G3. Colour-scheme reports are missing** (`dsr-996`, `mode2031-dsr996`). Fast answers nothing to
`CSI ? 996 n`; Ghostty answers `CSI ? 997 ; 1|2 n` and sends the same report on theme change while
mode 2031 is set. Affects neovim (automatic `background`), helix, and theme-following TUIs.
Change: install `GHOSTTY_TERMINAL_OPT_COLOR_SCHEME` (`terminal.h:2102`) in the 499-511 block,
answered from the app's dark/light state, and push a report on theme change.

**G4. Cursor blink default** (`decrqm-dec-12`, `decrqss-decscusr`). Ghostty reports blinking set
(`?12;1`, DECSCUSR `1`); Fast reports it reset (`?12;2`, DECSCUSR `2`). Programs that save and
restore the cursor shape (vim/neovim `guicursor` restore, fish) restore the wrong one.
Change: set `GHOSTTY_TERMINAL_OPT_DEFAULT_CURSOR_STYLE` / `_DEFAULT_CURSOR_BLINK`
(`terminal.h:2265`, `2275`) in the 499-511 block from the app's cursor setting.

**G5. XTGETTCAP `TN` is unanswered** (`xtgettcap-TN`). Ghostty answers `xterm-ghostty`; Fast answers
nothing because no terminfo name is set. Every other capability probed answers the same as
Ghostty. Change: set `GHOSTTY_TERMINAL_OPT_TERMINFO_NAME` (`terminal.h:2496`) in the 499-511 block
to the `TERM` the session exports (`xterm-256color`, `TerminalTerm.java:6`).

**G6. DA2 firmware version is 0** (`da2`). Ghostty answers `\e[>1;10;0c`, Fast `\e[>1;0;0c`. Vim
uses the DA2 version to decide which xterm features to trust. Change: set
`out->secondary.firmware_version = 10` in `on_device_attributes` (`ghostty_jni.c:304`).

**G7. DA3 is answered** (`da3`). Fast replies `\eP!|00000000\e\\` (the library default); Ghostty
sends nothing. Few programs ask. Change: in `on_device_attributes` (`ghostty_jni.c:296-306`),
make the tertiary request unanswered, if the C API allows it; **unverified** whether
`GhosttyDeviceAttributes.tertiary` (`device.h:146`) can suppress the reply.

## Intended differences seen

- `xtversion`: `termux-launcher(<version>) (libghostty-vt 1.0)` vs `ghostty 1.4.0-arch1`.
- `da1` attribute `4` (sixel); the gap in that row is only the missing `52`.

## Wave 2 rerun (3635c0432)

Raw file: [`results/fast-3635c0432.txt`](../../../scripts/terminal-probe/results/fast-3635c0432.txt).
Debug x86_64 build of `3635c0432` with `-Pghostty=true` on emulator AVD `tl16`, a pane opened in
front with `launcherctl pane open`; DA1 answered `\e[?62;4;22;52c` (Fast). "Always allow sh" was
granted on the consent sheet before the run, so the two clipboard reads were answered without a
sheet; the clipboard held `parity-ok`. Against the first Fast run only the former gap rows changed,
plus rows that depend on the pane's size and cursor (the pane had 4 rows this time).

| Former gap | Probe | Ghostty 1.4.0 | Fast 3635c0432 | Now |
|---|---|---|---|---|
| G1 | da1 | `\e[?62;22;52c` | `\e[?62;4;22;52c` | same (52 present; the 4 is intended) |
| G1 | decrqm-dec-5522 | `\e[?5522;2$y` | `\e[?5522;2$y` | same |
| G1 | osc52-read | `\e]52;c;\e\\` | `\e]52;c;cGFyaXR5LW9r\e\\` | same (clipboard contents; Ghostty's headless clipboard was empty) |
| G1 | osc5522-read | `...status=OK` / `DATA:mime=Lg==` / `DONE` | `...status=OK` / `DATA:mime=Lg==;dGV4dC9wbGFpbgo=` / `DONE` | same (the listing names text/plain; Ghostty's clipboard was empty) |
| G2 | osc7501-query | `<none>` | `<none>` | still open: OSC 7501 is a later wave |
| G3 | dsr-996 | `\e[?997;2n` | `\e[?997;1n` | same (theme: our default terminal scheme is dark, Ghostty's default config light) |
| G3 | mode2031-dsr996 | `\e[?997;2n` | `\e[?997;1n` | same (theme) |
| G4 | decrqm-dec-12 | `\e[?12;1$y` | `\e[?12;2$y` | intended difference: our default cursor is a steady block (`terminal-cursor-blink-rate` 0); Ghostty's default blinks. Ghostty with a steady cursor answers as we do; claiming a blink the user does not see would be wrong |
| G4 | decrqss-decscusr | `\eP1$r1 q\e\\` | `\eP1$r2 q\e\\` | intended difference (as above) |
| G5 | xtgettcap-TN | `xterm-ghostty` | `\eP1+r544E=787465726D2D323536636F6C6F72\e\\` (`xterm-256color`) | intended difference: TN is the TERM the session exports (`terminal-term`, default xterm-256color) |
| G6 | da2 | `\e[>1;10;0c` | `\e[>1;0;0c` | intended difference: the version field is Ghostty's own release number; we are not Ghostty |
| G7 | da3 | `<none>` | `\eP!\|00000000\e\\` | still open, library limit: vt-1.0.0 always answers DA3 (the C wrapper always installs its DA trampoline, c/terminal.zig:872, and the host callback is not told which level was asked) |

Unsolicited mode 2031 reports on a theme flip are covered by JVM tests
(`GhosttyCoreTest.testColorSchemeFlipReportsOnceUnderMode2031`), not by the probe.

### Clipboard reads on Fast

Reads (OSC 52 `?`, OSC 5522 read) ask on a sheet per request, naming the pty's foreground
program: Deny, Allow once, Allow for this session, Always allow <program>. A 5522 listing-only read
and a read that fetches the user's own paste event need no consent; "Let programs read the
clipboard" off refuses without asking. The library's reply is synchronous, so the shim answers it
"denied" with the bytes muted and hands the raw request to the app; after the decision a throwaway
libghostty-vt terminal is fed the same request and its reply bytes are written to the pty, so the
answer is byte-for-byte what the library writes. Only text/plain is served (pictures on the
clipboard are not offered on Fast).
