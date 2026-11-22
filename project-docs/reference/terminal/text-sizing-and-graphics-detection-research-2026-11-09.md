# OSC 66 upstream, and how programs detect kitty graphics and sixel (2026-11-09)

Questions: (A) does anyone implement kitty text sizing (OSC 66) on top of libghostty-vt, so the
`exp/ghostty-core` panes could get it? (B) how do image programs decide a terminal supports kitty
graphics or sixel, and how should a terminal that implements kitty graphics but is not kitty
advertise it, given a per-pane choice between the Java core (has sixel) and libghostty (has not)?
Read-only research from source at named commits, PRs, issues and maintainers' own docs. Nothing was
built or run on a device. Items marked (reasoned) are inferences from the cited code, not
measurements. Follows [libghostty-vt-research-2026-11-07.md](libghostty-vt-research-2026-11-07.md),
which first recorded that OSC 66 is parsed and dropped.

Source trees read (shallow clones, scratchpad, nothing committed): Ghostty `vt-1.0.0` (tag, the base
of the branch) and `main` at `cf5519b22` (2026-11-08); kitty `2db470b785`; chafa `c897fce60c`
(2026-11-02); timg `a40148448f` (2026-08-05); notcurses `b26048eebc` (2026-05-18); yazi `6882e1ac27`
(2026-11-05); ratatui-image `5d350c6953` (2026-10-18); viuer `ed329e5c01` (2025-12-09); image.nvim
`365e2ace0f` (2026-09-05); snacks.nvim `882c996cf2` (2026-05-25); Neovim `24e8974431` (2026-11-09);
ranger `46e957af97` (2026-09-09); lf `59cbc97b50` (2026-11-08); md-render.nvim `4f6d73a`
(2026-10-26); monstar `9402d52` (2026-11-09); this repo `166b12053`; tlstore `746d38e`.

## Part A: text sizing upstream

### Answer in short

- Ghostty has not implemented OSC 66 beyond the parser, and nothing planned is scheduled. At
  `vt-1.0.0` and at `main` `cf5519b22` the stream still drops the command
  (`src/terminal/stream.zig:3097-3101`, `log.debug("unimplemented OSC callback")`), so the whole
  sequence, payload text included, disappears (reasoned: the OSC is consumed by the parser and never
  reaches the terminal state).
- The only open work item is issue #10333, whose body says what is left: "associate the OSC metadata
  with cells within the terminal, and then render the cells according to the spec in the renderer.
  We can just implement the width specification for now like Foot". Hashimoto's only comment: "I
  suspect this is going to have dramatically non-trivial consequences in the most core part of
  Ghostty, so I will be explicit that this is not contributor friendly."
- No libghostty-based terminal we could read implements OSC 66 on top of the library. None of the
  cloned hosts mention it, and the library offers the host no hook for it (see "How a host could add
  it" below).
- Terminals that do implement it: kitty (0.40.0, 2025-03-08, full) and foot (1.21.0, width only).
  WezTerm does not.

### Ghostty: what exists

| Item | State | Source |
|---|---|---|
| PR #9845 "terminal: parse kitty text sizing protocol (OSC 66)" | closed 2026-01-10, superseded; body: "OSC 66 is something we *should* implement also on the terminal state/renderer side, but for now we can just parse the code and do nothing with it" | https://github.com/ghostty-org/ghostty/pull/9845 |
| PR #10315 "...(OSC 66), redux" | merged 2026-01-15 (commit `24b0ff05e`); "Implements the VT side of #5563"; adds `src/terminal/osc/parsers/kitty_text_sizing.zig` | https://github.com/ghostty-org/ghostty/pull/10315 |
| Discussion #5563 "Text Sizing Protocol" | opened 2025-02-04 by rockorager (libvaxis author, Ghostty member). Member pluiedev, 2025-11-10: "we can start with *parsing* the text sizing protocol correctly first — actually rendering each glyph in the correct size might be difficult and might need significant restructuring of the current cell renderer, but we can take care of that later". Superseded by #10333 on 2026-01-15 | https://github.com/ghostty-org/ghostty/discussions/5563 |
| Issue #10333 "Implement the Text Sizing Protocol (OSC 66)" | open, last updated 2026-01-15, one comment (Hashimoto, quoted above) | https://github.com/ghostty-org/ghostty/issues/10333 |
| Commits since `vt-1.0.0` | 79 commits to 2026-11-08 on `main`; none touch OSC 66 handling. `git grep` for `kitty_text_sizing` on `main` finds the parser, the OSC command enum (`osc.zig:158,218,552,1172`), the C ABI enum value `KITTY_TEXT_SIZING = 22` (`src/terminal/c/types.zig:1698`) and the stream's drop (`stream.zig:3097`) | `git log`/`git grep` on `FETCH_HEAD` |
| OSC 99 (kitty notifications) | also parse-only. Issue #5634 (open); member jcollie 2026-01-31: "Note PR #10467 for _parsing_ OSC 99 sequences. Actually sending notifications is not implemented yet." | https://github.com/ghostty-org/ghostty/issues/5634 |

Searches run: issues and PRs for "text sizing", "OSC 66", "kitty_text_sizing", "OSC 99", "kitty
notification" on `ghostty-org/ghostty` (`gh search`), plus the commit log subject search on a fetched
`main`. Nothing newer than January 2026 turned up.

### How a host could add it (libghostty-vt 1.0)

- The C API exposes the OSC parser on its own (`include/ghostty/vt/osc.h`: `ghostty_osc_new`,
  `ghostty_osc_next`, `ghostty_osc_end`), and `ghostty_terminal_vt_write_until()` lets the host stop
  at chosen stream points (`include/ghostty/vt/terminal.h:187, 361`). Our wave 3 spec already plans
  this for OSC 99: "sniff OSC strings with `vt_write_until`" (`project-docs/active/ghostty-core-wave3-2026-11-09.md:260`).
- For OSC 66 that route does not work for the visible effect. The spec's scaled text occupies cells
  (multi-cell characters, cursor moves by `s*w`); a sniff can read the sequence but cannot place
  cells in libghostty's grid. The wave 3 spec reaches the same conclusion: "drop: it changes cell
  layout and cannot live outside the grid" (`ghostty-core-wave3-2026-11-09.md:261`).
- A host that wants only the safe subset could rewrite `OSC 66 ; <meta> ; text ST` to its plain
  `text` before `vt_write` (reasoned: loses scale and width, keeps the words). Nothing in the
  sources we read does this.

### Other terminals built on libghostty-vt

Source: [awesome-libghostty](https://github.com/Uzaaft/awesome-libghostty) and Hashimoto's
["Libghostty 1.0"](https://mitchellh.com/writing/libghostty-1-0) (2026-11-06), which names JetBrains
IDEs ("the terminal in JetBrains IDEs now runs on libghostty"), Neovim ("has an approved pull
request") and KDE ("experimenting with a Konsole replacement").

| Project | What we checked | Result |
|---|---|---|
| monstar (rockorager; Wayland terminal) `9402d52` | grep for OSC 66 / text sizing; README | no OSC 66. README lists "Kitty graphics, clipboard/paste, and drag-and-drop protocols; OSC 8 hyperlinks". Sets `effects.device_attributes`, `effects.xtversion` (`src/App.zig:848,852`) |
| libghostty-vaxis (rockorager) `4cfc216` | grep; README | no OSC 66; wraps the library behind the vaxis `widgets/term` API |
| Kwi, KDE's Konsole replacement (`invent.kde.org/utilities/kwi`) `1df8741` | grep OSC 66 / "text sizing" over the tree | none (`ghostty-qt` and `ghostty-qt-quick` libs have kitty keyboard and graphics only) |
| chuchu (Android SSH) `00f9fd2`, umbra (Android) `1691011`, ghosttyfx (JavaFX) `d7db106` | grep | none (chuchu's `textSize` hits are Android font size) |
| Neovim PR #39773 "replace `libvterm` with `libghostty-vt`" | open; description lists what it ports (focus, paste, input encoding, query responses, rendering, scrollback) | no mention of OSC 66 |
| JetBrains terminal | closed source | not verifiable; no primary source found |

Pattern: every host we could read takes what the library hands it through callbacks and does not
re-parse the stream. (reasoned) Not seeing OSC 66 anywhere is what the library's drop predicts.

### Terminals that implement OSC 66

| Terminal | Support | Version or commit | Source |
|---|---|---|---|
| kitty | full (scale, width, fractions, alignment) | 0.40.0, 2025-03-08: "Allow terminal programs to use text in different font sizes" (kitty issue 8226) | `docs/changelog.rst:1612-1615` at `2db470b785`; spec `docs/text-sizing-protocol.rst` |
| foot | width (`w`) only | 1.21.0: "Support for kitty's text-sizing protocol (`w`, width, only), OSC-66." Later fix in 1.28.0: "Kitty text-size protocol: fix crash when text is zero-length" | https://codeberg.org/dnkl/foot `CHANGELOG.md:722-731` and `:173` on master |
| Ghostty | parser only, dropped | `vt-1.0.0` | above |
| WezTerm | no | issues #7814 (open; comment: "Wezterm does not support OSC 66 at all") and #7061 (open) | https://github.com/wezterm/wezterm/issues/7814 |
| Konsole | not determined | the 5.x/6.x tree was not read | open question |

Consumers worth knowing: kitty documents CPR-based detection (send `CR`, `CPR`, `OSC 66;w=2; ST`,
`CPR`, `OSC 66;s=2; ST`, `CPR`; same cursor column in all three = unsupported, moved by 2 = width,
moved by 2 more = scale; `docs/text-sizing-protocol.rst:257-271`). tlstore does exactly this
(`tlstore/ui/src/term/probe.rs:58-60`, and the `OSC 66 text sizing` field at `:17`); ratatui-image
does the three-CPR test (`src/picker.rs:520-535`, `Capability::TextSizingProtocol` at `:54`).
md-render.nvim does not use the spec's probe: it gates on an XTVERSION reply of `kitty(>=0.40)` and
says why: "deliberately strict: WezTerm swallows an OSC 66 sequence *including its payload text*, so a
wrong guess deletes content rather than degrading to unscaled text" (`lua/md-render/text_size.lua:18-21`;
match `kitty%((%d+)%.(%d+)` at `:197`; any other `P>|` answer means no, `:202-205`).

Consequence for the libghostty core (reasoned): the spec's CPR probe gives the honest answer
(the dropped OSC prints nothing, the cursor stays, "unsupported"), so tlstore and ratatui-image
degrade cleanly. A program that emits OSC 66 without probing loses the payload text. The Java core
passes both.

## Part B: detecting kitty graphics and sixel

### What the kitty docs say

- "We intend that any terminal emulator that wishes to support it can do so. To check if a terminal
  emulator supports the graphics protocol the best way is to send the above *query action* followed by
  a request for the primary device attributes. If you get back an answer for the device attributes
  without getting back an answer for the *query action* the terminal emulator does not support the
  graphics protocol." Terminals that support it "**must** reply to *query actions* immediately without
  processing other input" (`docs/graphics-protocol.rst:500-540` at `2db470b785`; example
  `<ESC>_Gi=31,s=1,v=1,a=q,t=d,f=24;AAAA<ESC>\<ESC>[c`).
- kitty's own `kitten icat` follows that: it sends three queries (`t=d`, temp file, shared memory) and
  `CSI c`, reads only APC replies with `OK`, and exits with "This terminal does not support the
  graphics protocol" if the direct query is not answered
  (`kittens/icat/detect.go:55-85,90-110`, `kittens/icat/main.go:285-296`). It reads no
  environment variable and no terminal name.
- Third-party terminals: the docs give no instruction to set `TERM_PROGRAM`, `KITTY_WINDOW_ID` or
  XTVERSION. `KITTY_WINDOW_ID` is documented as the id of the kitty window, "can be used with the
  kitty remote control facility" (`docs/glossary.rst:168-171`).
- kitty itself sets `TERM=xterm-kitty`, `COLORTERM`, `KITTY_PID`, `KITTY_PUBLIC_KEY`,
  `KITTY_LISTEN_ON`, `KITTY_INSTALLATION_DIR`, `TERMINFO` (`kitty/child.py:444-466`) and
  `KITTY_WINDOW_ID` (`kitty/tabs.py:858`). It answers XTVERSION `kitty(<version>)`
  (`kitty/screen.c:3257`). A repo-wide grep finds no `TERM_PROGRAM` in kitty: kitty never sets it, so
  `TERM_PROGRAM=kitty` is a convention some consumers read, not something the terminal publishes.
- `TERM=xterm-kitty` needs the kitty terminfo on the machine: the FAQ describes breakage when the
  terminfo files are missing (`docs/faq.rst:42-80`).

### Program by program: kitty graphics

| Program (commit) | Decides by | Where |
|---|---|---|
| `kitten icat` (kitty `2db470b785`) | APC `a=q` ×3 + DA1 as sentinel; no env, no name | `kittens/icat/detect.go:55-110`; `main.go:285-296` |
| chafa (`c897fce60c`) | Environment database only; no graphics query (a DA1-style query exists only to ask tmux about sixel). Kitty entry matches `TERM=xterm-kitty` or `KITTY_PID` set; ghostty entry matches `TERM=xterm-ghostty`, `TERM_PROGRAM=ghostty`, `GHOSTTY_BIN_DIR`. No entry for `termux-launcher` or `KITTY_WINDOW_ID` | `chafa/chafa-term-db.c:689-694` (kitty), `:664-672` (ghostty), `:862-882` (xterm fallback: colour only); `tools/chafa/chicle-options.c:2178-2191` |
| timg (`a40148448f`) | Env first (`TERM` = `xterm-kitty` or `xterm-ghostty` sets kitty), then XTVERSION (`CSI > q` + `CSI 5 n`) substring match: `kitty` or `ghostty` sets kitty, `iTerm2`/`WezTerm` iTerm2, `foot`/`mlterm` sixel. Deliberately does not send `a=q`: "unfortunately that escape sequence messes up some other terminals, so we don't do that here" | `src/term-query.cc:239-245, 265-267, 275-318` |
| notcurses (`b26048eebc`) | Query: sends `a=q` (`Gi=1`), XTVERSION, DA1, XTSMGRAPHICS in one batch; any kitty graphics reply sets kitty; "kitty trumps sixel". Also matches XTVERSION `kitty(` / `ghostty ` for cell-drawing tweaks. No `TERM_PROGRAM` use except Apple Terminal and Windows | `src/lib/termdesc.c:383, 453-458, 1285`; `src/lib/in.c:1409-1413, 1557-1559` |
| yazi (`6882e1ac27`) | Brand from env first: `TERM` = `xterm-kitty`/`xterm-ghostty`; `TERM_PROGRAM` list; then env-exists list (`KITTY_WINDOW_ID` -> Kitty, `GHOSTTY_RESOURCES_DIR`...). Then XTVERSION substring (`kitty`, `ghostty`, `foot`, ...) overrides. Brand `Kitty`/`Ghostty` -> driver `Kgp` (Unicode placeholders, shared memory only if the `t=s` query was answered, else base64). Brand unknown -> kitty query (`a=q`, magic id 278941603) and DA1 decide | `yazi-emulator/src/brand.rs:34-96`; `emulator.rs:36-37, 77-100`; `yazi-tty/src/sequence/query.rs:35-66`; `yazi-adapter/src/drivers/drivers.rs:21-70`; `kgp.rs:354-365` |
| ratatui-image (`5d350c6953`) | Query: `a=q` (`Gi=31`), compression and shared-memory variants, DA1, text-size CPRs, `CSI 16 t`. Kitty beats sixel. Env hints only for iTerm2/WezTerm outer terminal under tmux; states kitty is deliberately not read from env: "KITTY_WINDOW_ID can be stale in tmux sessions" | `src/picker/cap_parser.rs:181-221, 365-367`; `src/picker.rs:148, 353-366, 484-496` |
| viuer (`ed329e5c01`) | Query: `a=q` remote (`t=d`), then local (`t=t`), each followed by `CSI 5 n`; before that tries sixel (only if built with the `sixel` feature, off by default) and iTerm2 (env `TERM_PROGRAM`/`LC_TERMINAL` contains iTerm, WezTerm, mintty, rio, WarpTerminal) | `src/printer/kitty.rs:80-95, 140-147, 207-218`; `src/lib.rs:151-168`; `src/printer/iterm.rs:94-105`; `Cargo.toml:35-38` |
| image.nvim (`365e2ace0f`) | No detection. The backend is a config choice (`backend = "kitty"` default); no `TERM_PROGRAM`, `KITTY_WINDOW_ID` or query in `lua/` | `lua/image/init.lua:9-18`; grep over `lua/` |
| snacks.nvim image (`882c996cf2`) | Allow-list by name. Sends XTVERSION (`\27[>q`), takes the name from the `P>|name version` reply, substring-matches `kitty`, `ghostty`, `wezterm` -> `supported`; `tmux`, `zellij`, `ssh` by env. No `a=q`. Escape hatches: `SNACKS_KITTY=true` (any `SNACKS_<NAME>`) or the `force` option | `lua/snacks/image/terminal.lua:7-36, 121-145, 290`; `lua/snacks/image/init.lua:218-219`; `docs/image.md:31-33` |
| Neovim builtin `vim.ui.img` (`24e8974431`) | Query: `a=q` through `vim.tty.query_apc`, 1000 ms; only skips Apple Terminal by `TERM_PROGRAM` | `runtime/lua/vim/ui/img/_kitty.lua:166-205` |
| md-render.nvim (`4f6d73a`) | `a=q` probe first; if silent, `TERM_PROGRAM` in {`WezTerm`, `kitty`, `ghostty`} or env-exists `KITTY_WINDOW_ID`, `GHOSTTY_RESOURCES_DIR`, `WEZTERM_EXECUTABLE`. This is the consumer our docs call "trusts TERM_PROGRAM=kitty" | `lua/md-render/image.lua:747-800` |
| ranger (`46e957af97`) | Setting, not detection: `preview_images_method` defaults to `w3m`; choosing `kitty` runs `a=q` with `t=f` plus DA1; reply `OK` -> temp-file mode, `EBADF` -> stream mode, anything else -> unsupported | `ranger/config/rc.conf:131`; `ranger/ext/img_display.py:773-830` |
| lf (`59cbc97b50`) | No detection. Previews come from the user's previewer script; output that looks like sixel is shown as sixel | `ui.go:138-160`; `nav.go:941-958`; `doc.md:1990-2001` |
| tlstore (`746d38e`) | Query: `a=q` (`Gi=31`) answered `OK` -> `kitty_graphics`; DA1 is only the end marker ("every terminal answers it"); reads `TERM_PROGRAM` elsewhere only to detect the launcher for OSC 52 | `tlstore/ui/src/term/probe.rs:1-4, 15-16, 57-64, 92`; `tlstore/ui/src/store/proc.rs:62` |

So: a majority queries (icat, notcurses, ratatui-image, viuer, Neovim, ranger, tlstore, md-render
first); a minority trusts names (chafa and timg by env/XTVERSION substring, snacks by XTVERSION name,
yazi by env then XTVERSION). Of the name-trusting ones, none reads `TERM_PROGRAM=termux-launcher`.

### Program by program: sixel

| Program | Decides by | Where |
|---|---|---|
| chafa | Environment database entry carries sixel or not (`sixel_seqs`); never asks the terminal, except it asks tmux and turns sixel off if the outer terminal says no | `chafa/chafa-term-db.c:282, 589-900`; `chicle-options.c:2178-2191` |
| timg | XTVERSION name list (`foot`, `mlterm`); only if nothing matched, DA1 with a `;4` parameter followed by `;` or `c` | `src/term-query.cc:296-310, 324-341` |
| notcurses | DA1 parameter 4 (`foundsixel`, also defaults 256 registers), XTSMGRAPHICS answers for registers and geometry; XTSMGRAPHICS answers are discarded if DA1 had no 4; kitty graphics wins when both | `src/lib/in.c:1223-1232, 1280-1311, 1174-1195`; `termdesc.c:1285` |
| yazi | DA1 contains 4 (`Report::Da1(attrs) => sixel = attrs.contains(&4)`). For an unknown brand with both kitty and sixel true, the driver order is `[Sixel, KgpOld]`: sixel first | `emulator.rs:82`; `drivers.rs:24-29` |
| ratatui-image | DA1 parameter `4`; used only if kitty was not detected ("Only if kitty is not supported") | `cap_parser.rs:289`; `picker.rs:488-494` |
| viuer | DA1 contains `;4;` or `;4c`; checked before kitty and iTerm2, only when built with the `sixel` or `icy_sixel` feature | `src/printer/sixel_util.rs:18-39`; `lib.rs:151-168` |
| kitten icat, snacks, image.nvim (kitty backend), Neovim builtin, md-render, tlstore | no sixel path | n/a |
| ranger, image.nvim (sixel backend) | user picks the method; no detection | `rc.conf:131-147`; `lua/image/init.lua:9-13` |
| lf | previewer script decides | above |

### What our two cores answer

| Query | Java core (`166b12053`) | libghostty-vt `vt-1.0.0` (branch `exp/ghostty-core`) |
|---|---|---|
| `TERM` | `xterm-256color` (`termux-shared/.../TerminalTerm.java:6`); the `terminal-term` property can set it, e.g. `xterm-kitty` (`docs/en/Terminal_Kitty_Protocols.md`, "Terminal identity and detection") | same: set by the app at spawn, independent of core |
| `TERM_PROGRAM`, `TERM_PROGRAM_VERSION` | `termux-launcher`, installed version (`TermuxShellEnvironment.java:33,118-120`) | same, independent of core |
| XTVERSION `CSI > 0 q` | `DCS > | termux-launcher(<version>) ST` (`TerminalEmulator.java:829-835, 2759-2765`; version set in `TermuxApplication.java:96-98`) | the library's default `libghostty` unless the host sets a callback (`stream_terminal.zig:1815-1824`; option `GHOSTTY_TERMINAL_OPT_XTVERSION`, `terminal.h:1977-1990, 2070-2075`). The branch's JNI does not set it: `terminal-emulator/ghostty/ghostty_jni.c:289-295` sets user data, write-pty, bell, title, pwd, size, clipboard only |
| DA1 `CSI c` | `?64;1;2;4;6;9;15;18;21;22c` (`TerminalEmulator.java:3406-3411`), includes 4 (sixel) | default `?62;22c`, no 4 (`terminal.h:2095-2108`; test `device_attributes.zig:178`). Host can override all of DA1, DA2, DA3 via `GHOSTTY_TERMINAL_OPT_DEVICE_ATTRIBUTES` with a frozen struct; `GHOSTTY_DA_FEATURE_SIXEL = 4` exists (`include/ghostty/vt/device.h:40`). Not set in the branch |
| DA2, DA3 | DA2 handled (`TerminalEmulator.java:2767-`, reply not read in detail) | `CSI > 1;0;0 c`, DA3 `DCS ! | 00000000 ST` by default (`terminal.h:2102-2104`) |
| XTSMGRAPHICS `CSI ? Pi;Pa;Pv S` | answers registers (256) and geometry (`TerminalEmulator.java:2468-2490`) | no handler found (`grep -i xtsmgraphics src/terminal/*.zig` empty): unanswered |
| kitty `a=q` | answered (`KittyGraphicsProtocol.java:338-340, 490-500`; unreadable file -> `EBADF:cannot read transmission file`, `:418-451`) | answered inside the library (`src/terminal/kitty/graphics_exec.zig:151-190`); shared-memory medium returns unsupported on Android (`graphics_image.zig:200-215`) |
| Sixel itself | yes | dropped (DCS other than XTGETTCAP, DECRQSS, tmux ignored) |
| OSC 66 | yes | parsed, dropped |
| For comparison: Ghostty app | n/a | TERM `xterm-ghostty`, `TERM_PROGRAM=ghostty`, `TERM_PROGRAM_VERSION` (`src/termio/Exec.zig:649,666,755-756, 2331`); DA1 `?62;22(;52)c`, no sixel (`src/termio/stream_handler.zig:834-840`); XTVERSION `ghostty <version>` (`:980`) |
| For comparison: monstar (libghostty host, kitty graphics) | n/a | XTVERSION `monstar <version>`; DA1 features `ansi_color`, `clipboard` only (`src/App.zig:848-852, 934-941`); `TERM` `monstar` or `xterm-256color` (test `src/main.zig:505-508`); no `TERM_PROGRAM` anywhere in the repo |

### What each program does with those answers (reasoned from the code above)

Two cores, ours today, no host callbacks on the branch:

| Program | Java core | libghostty core (defaults) |
|---|---|---|
| kitten icat, Neovim builtin, ratatui-image, viuer, tlstore, notcurses | kitty via `a=q` (notcurses and ratatui-image prefer kitty over sixel; viuer prefers sixel only if built with the feature) | kitty via `a=q` |
| yazi | brand unknown, kitty and sixel both true: Sixel driver first, `KgpOld` second | brand: XTVERSION `libghostty` contains `ghostty` -> `Ghostty` -> `Kgp` driver (placeholders) |
| timg | XTVERSION `termux-launcher(...)` matches nothing, DA1 has `;4` -> sixel | XTVERSION `libghostty` contains `ghostty` -> kitty (by accident of substring) |
| snacks image | name `termux-launcher` not on the list -> unsupported unless `SNACKS_KITTY=true` or `force` | name `libghostty` contains `ghostty` -> supported with placeholders (by accident) |
| chafa | `TERM=xterm-256color`: colour only, no pictures; no db entry for `termux-launcher` | same |
| md-render.nvim | `a=q` answered -> pictures; text sizing off (needs `kitty(>=0.40)`) | pictures; text sizing off |
| image.nvim, ranger, lf | user config | user config |

If XTVERSION is set to our own name on the libghostty core as well, timg falls to DA1 (no 4) and
reports no graphics at all (`term-query.cc:324-341` finds no `;4`), snacks and yazi stop matching
(yazi falls to `a=q` + DA1: `KgpOld` only), and chafa is unchanged. That is the cost of consistent
identity without allow-list entries.

Note on our own docs (not changed here): `TermuxShellEnvironment.java:115-117` says `TERM_PROGRAM`
lets "chafa's terminal db and everything built on it" recognise the launcher; chafa `c897fce60c`
has no `termux-launcher` entry and keys on `TERM`, `KITTY_PID`, `TERM_PROGRAM=ghostty`
and similar (`chafa-term-db.c:601-852`). `Terminal_Kitty_Protocols.md` says `kitten icat` "picks
[shared memory] when it believes it is local"; at `2db470b785` it tests by query and falls back by
reply (`detect.go:73-110`), so the answer to the `t=s` query is what matters.

## Advertising options (consequences, no verdict)

Constraint recap: the app sets `TERM`/`TERM_PROGRAM` at spawn, independent of core. XTVERSION, DA1/2/3,
`a=q` and XTSMGRAPHICS come from the core and can differ per pane. The libghostty host can set
XTVERSION and DA1 (and does not yet on the branch).

| Option | What it gets | What it costs or breaks |
|---|---|---|
| 1. Answer `a=q` properly, keep our own name everywhere (today) | Every querying program works: icat, notcurses, ratatui-image, viuer, Neovim 0.13, ranger (when selected), md-render pictures, tlstore. This is the path kitty's docs call "the best way" | Name-trusting programs without an entry stay off: chafa (no pictures), snacks (unless `SNACKS_KITTY`), timg and yazi take sixel on the Java core. `TERM_PROGRAM` stays honest, bug reports keep the launcher's name. Needs the `t=s` and `t=f` replies to be true on both cores (Java: `EBADF`; libghostty: unsupported on Android) |
| 2. Option 1 plus upstream entries (chafa db, timg, yazi brand, snacks list) | Makes the name-trusting programs recognise `termux-launcher` | Slow, per project, needs each maintainer; Ghostty has such entries in chafa (`:664`), timg, yazi (`brand.rs:41,59,69,78`), snacks (`terminal.lua:14-19`), which is the route a new terminal normally takes. (reasoned) |
| 3. `TERM=xterm-kitty` (existing `terminal-term` property) | chafa picks kitty (`chafa-term-db.c:690`), timg picks kitty (`term-query.cc:240`), yazi picks `Kgp` (`brand.rs:67`) | Needs the kitty terminfo in the prefix (kitty FAQ, `docs/faq.rst:42-80`); every `TERM` match then assumes the whole kitty feature set (yazi `Kgp` uses `U=1` placeholders and tries `t=s` only if the query was answered; kitty-only key handling and shell hooks may switch on). Can be per-core: set only for panes whose core has what kitty has |
| 4. `TERM_PROGRAM=kitty` (our docs: per-program wrapper) | md-render pictures even without the probe; nothing else in the sampled set reads it (chafa, timg, yazi, snacks, ratatui-image do not) | Loses the launcher's own name (tlstore uses `termux-launcher` to pick OSC 52, `proc.rs:62`); kitty itself never sets `TERM_PROGRAM` (no hit in kitty), so the value is purely a consumer convention |
| 5. `KITTY_WINDOW_ID` set | yazi brand `Kitty` (`brand.rs:55`), md-render pictures (`image.lua:785`) | Wrong for kitty's own tooling: `kitten @` reads it to address a kitty window (`tools/cmd/at/main.go:272`), the ssh kitten reads it and `KITTY_PID` (`kittens/ssh/main.go:266, 474, 1053`), neither exists here. ratatui-image refuses it because it goes stale in tmux (`picker.rs:148`). chafa uses `KITTY_PID`, not this one. Misdetection risk is highest here (reasoned) |
| 6. XTVERSION says `kitty(0.4x)` | md-render text sizing turns on (`text_size.lua:197`); timg/yazi/snacks/chafa treat it as kitty | A lie about version: programs gate real features on it (shared-memory, file transfer OSC 5113, remote control that we lack; our docs list these as the reasons for not pretending). On the libghostty core text sizing would then be advertised and swallowed |
| 7. Let the libghostty core keep its default XTVERSION `libghostty` | timg, yazi and snacks treat the pane as Ghostty/kitty by substring accident | The two cores then identify differently; fragile; the match depends on a substring, not a contract (reasoned) |
| 8. DA1 sixel flag (`4`) and XTSMGRAPHICS per core | Java core: true today, so timg, viuer (if built with sixel), yazi (unknown brand) pick sixel; notcurses gets registers | libghostty core: leaving `4` out is true. Adding it needs a sixel path (wave 3 spec: "later: sniff DCS with `vt_write_until`, decode with `TerminalSixel`, re-send as a kitty image at ground", `ghostty-core-wave3-2026-11-09.md:257`) and an XTSMGRAPHICS answer, which libghostty lacks. Advertising 4 without sixel makes viuer and yazi emit pictures that vanish |

Per-core summary (reasoned):

| | Java core | libghostty core |
|---|---|---|
| Options 1, 2, 3 | all valid; sixel also true | valid for kitty graphics; sixel consumers (timg without a kitty match, viuer with sixel built) get nothing, which is the honest answer |
| Option 8 | DA1 `4` + XTSMGRAPHICS correct | DA1 must omit 4 until a sixel translator exists |
| Text sizing | CPR probe, `kitty(>=0.40)` gates: spec probe passes, XTVERSION gate fails | spec probe says unsupported (correct); un-probed OSC 66 text is lost |

## Open questions

- Konsole: does 6.x implement OSC 66? Not determined. Neither was JetBrains' terminal checkable.
- Does `exp/ghostty-core` enable libghostty's kitty graphics storage at all, and with what limit? Until
  it does, `a=q` through the library may answer an error. The JNI sets no graphics option
  (`ghostty_jni.c:289-295`), and the default cap is 10 MB per `terminal.h` per the 11-07 note.
- What does the Java core answer to the `t=s` query on Android? `readTransmissionFile` gives `EBADF`
  for unreadable files (`KittyGraphicsProtocol.java:418-451`); the shared-memory path was not traced.
  `kitten icat` and yazi will probe it.
- Which programs besides md-render.nvim read `TERM_PROGRAM=kitty`? None of the twelve sampled do;
  our docs name image.nvim, but image.nvim at `365e2ace0f` has no detection code.
- Can a libghostty host cheaply strip OSC 66 to its plain payload so un-probed text is not lost
  (reasoned, no source tried it)?
- Per-pane core and spawn-time env: does the spawn path know the pane's core when it builds
  `TERM`/`TERM_PROGRAM`, so option 3 could differ per core?
- Pre-release versions in the wild: these are `main` commits; the versions people run (chafa, timg,
  yazi in the package repos the phone uses) can lag the commits read here by months.
- DA2 and DA3 answers from the Java core and any program that reads them were not examined.

## Sources

Ghostty and libghostty-vt:
- `vt-1.0.0` and `main` `cf5519b22`: `src/terminal/stream.zig:3097-3101`, `src/terminal/osc.zig`,
  `src/terminal/osc/parsers/kitty_text_sizing.zig`, `src/terminal/stream_terminal.zig:1712-1826`,
  `src/terminal/device_attributes.zig`, `src/terminal/kitty/graphics_exec.zig:151-190`,
  `src/terminal/kitty/graphics_image.zig:200-215`, `src/termio/Exec.zig`,
  `src/termio/stream_handler.zig:834-840, 980`, `include/ghostty/vt/terminal.h`, `device.h`, `osc.h`
- PR #9845: https://github.com/ghostty-org/ghostty/pull/9845 ; PR #10315: https://github.com/ghostty-org/ghostty/pull/10315
- Issue #10333: https://github.com/ghostty-org/ghostty/issues/10333 ; Discussion #5563: https://github.com/ghostty-org/ghostty/discussions/5563 ; Issue #5634: https://github.com/ghostty-org/ghostty/issues/5634
- Hashimoto, "Libghostty 1.0": https://mitchellh.com/writing/libghostty-1-0
- awesome-libghostty: https://github.com/Uzaaft/awesome-libghostty
- monstar: https://github.com/rockorager/monstar ; libghostty-vaxis: https://github.com/rockorager/libghostty-vaxis ; Kwi: https://invent.kde.org/utilities/kwi ; chuchu: https://github.com/jossephus/chuchu ; umbra: https://github.com/charliesbot/umbra ; ghosttyfx: https://github.com/vlaaad/ghosttyfx
- Neovim PR #39773: https://github.com/neovim/neovim/pull/39773

OSC 66 elsewhere:
- kitty: `docs/text-sizing-protocol.rst`, `docs/changelog.rst` (0.40.0), https://sw.kovidgoyal.net/kitty/text-sizing-protocol/
- foot changelog: https://codeberg.org/dnkl/foot/src/branch/master/CHANGELOG.md (1.21.0); foot PR cited in Discussion #5563: https://codeberg.org/dnkl/foot/pulls/1927 (not fetched)
- WezTerm: https://github.com/wezterm/wezterm/issues/7814 , https://github.com/wezterm/wezterm/issues/7061

Detection (all at the commits listed at the top):
- kitty: `docs/graphics-protocol.rst`, `docs/glossary.rst`, `docs/faq.rst`, `kittens/icat/{detect,main}.go`, `kitty/child.py`, `kitty/tabs.py`, `kitty/screen.c`, `tools/cmd/at/main.go`, `kittens/ssh/main.go`
- chafa `chafa/chafa-term-db.c`, `tools/chafa/chicle-options.c`; timg `src/term-query.cc`; notcurses `src/lib/termdesc.c`, `src/lib/in.c`; yazi `yazi-emulator/src/{brand,emulator}.rs`, `yazi-tty/src/sequence/query.rs`, `yazi-adapter/src/drivers/{drivers,kgp}.rs`; ratatui-image `src/picker.rs`, `src/picker/cap_parser.rs`; viuer `src/lib.rs`, `src/printer/{kitty,iterm,sixel_util}.rs`; image.nvim `lua/image/init.lua`; snacks.nvim `lua/snacks/image/{terminal,init}.lua`, `docs/image.md`; Neovim `runtime/lua/vim/ui/img/_kitty.lua`; md-render.nvim `lua/md-render/{image,text_size}.lua`; ranger `ranger/ext/img_display.py`, `ranger/config/rc.conf`; lf `ui.go`, `nav.go`, `doc.md`
- Repositories: https://github.com/kovidgoyal/kitty , hpjansson/chafa , hzeller/timg , dankamongmen/notcurses , sxyazi/yazi , benjajaja/ratatui-image , atanunq/viuer , 3rd/image.nvim , folke/snacks.nvim , neovim/neovim , ranger/ranger , gokcehan/lf , delphinus/md-render.nvim

This repo:
- `terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java:829-835, 2468-2490, 2759-2765, 3406-3411`; `KittyGraphicsProtocol.java:338-340, 418-451, 490-500`
- `app/src/main/java/com/termux/app/TermuxApplication.java:96-98`; `termux-shared/.../TermuxShellEnvironment.java:33, 115-120`; `termux-shared/.../TerminalTerm.java:6`
- `docs/en/Terminal_Kitty_Protocols.md`, `docs/en/Programs_Inside_The_Terminal.md`
- `project-docs/reference/terminal/libghostty-vt-research-2026-11-07.md`; worktree `termux-launcher-ghostty` (`exp/ghostty-core`): `terminal-emulator/ghostty/ghostty_jni.c:212-295`, `project-docs/active/ghostty-core-wave3-2026-11-09.md:257-261`, `docs/adr/0025-both-terminal-cores-serve-rows-through-one-interface.md`
- `tlstore/ui/src/term/probe.rs`, `tlstore/ui/src/store/proc.rs:62`
