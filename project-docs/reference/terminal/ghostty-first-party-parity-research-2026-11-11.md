# Ghostty first-party parity vs libghostty-vt (2026-11-11)

Question: where does an embedder of libghostty-vt (our "Fast" engine) fall short of the Ghostty
desktop app on modern terminal protocols, and what is "OSC 7501"? Read-only research; nothing was
built or run, no code changed. Complements
[`libghostty-vt-research-2026-11-07.md`](libghostty-vt-research-2026-11-07.md).

Sources: a blobless clone of `ghostty-org/ghostty` at `main` = `2693982f8` (2026-11-11), the tags
`vt-1.0.0` (`0fa3d577a`, 2026-11-06), `v1.3.0` (2026-03-09), `v1.4.0` (2026-10-27), the release
notes at `https://ghostty.org/docs/install/release-notes/1-3-0` and `.../1-4-0`, the VT reference
at `https://ghostty.org/docs/vt/reference`, and the OSC 7501 spec page. Paths below are in that
tree (`path@2693982f8`). Anything not settled by a source is marked **unverified**.

## 1. OSC 7501

It exists. It is the **Program Status Protocol**.

- Spec: `https://www.superlogical.com/rex/docs/build/program-status`, owned by Superlogical, the
  maker of the Rex terminal. Current revision 0.4 (2026-10-10); earlier 0.1 (2026-09-28),
  0.2 (2026-10-06), 0.3 (2026-10-07). The page names no individual author. It calls itself "a
  generic terminal protocol" and mentions iTerm2 (OSC 21337) and Orca (OSC 9999) only as earlier
  similar ideas, not as implementers.
- What it does: a program tells the terminal what it is doing. Wire form
  `ESC ] 7501 ; state=working:id=sync:progress=40 ESC \`. Keys: `state` (required: `idle`,
  `working`, `done`, `blocked`, `error`, plus `clear`, which removes a record and its children),
  `id` (slash-separated hierarchy; omitted means the root record), `kind` (only for `blocked`:
  `permission`, `question`, `auth`), `progress` (0-100), `app`, `title` and `msg` (both base64
  UTF-8). One record per id; each report replaces its record. Support query: body `?`; a
  supporting terminal replies `?:states=...:kinds=...`.
- Implementers: the spec page names only Rex. No other terminal was found; **unverified** beyond
  that page (no terminal-wg or foot/kitty/wezterm/iTerm2 document mentions 7501 that I found; I did
  not search those trackers exhaustively).
- Ghostty: parsed by the core. Added by Mitchell Hashimoto in `bae2c3cdb` (2026-10-06,
  "libghostty-vt: program status protocol (OSC 7501)"), updated to revision 0.4 in `b7207b2ef`
  (2026-10-10), malformed ids rejected in `dcaf6984e` (2026-10-20). All three are inside the
  `vt-1.0.0` tag and `v1.4.0`. Parser: `src/terminal/osc/parsers/program_status.zig`; handler:
  `src/terminal/stream_terminal.zig` (`programStatus`, effect `program_status`); C API:
  `include/ghostty/vt/terminal.h` (`GHOSTTY_TERMINAL_OPT_PROGRAM_STATUS`).
- The commit message says: the terminal keeps no records, "the same as OSC 9;4"; the embedder keeps
  one record per id and applies the spec's lifetime rules; RIS reports a clear of every record; the
  support query is answered only while the effect is set; "The Ghostty app ignores these reports for
  now." The 1.4.0 notes say the same. The app's handler lists `.program_status` under
  "Unimplemented" (`src/termio/stream_handler.zig`, near line 391).
- Limits the parser enforces (`program_status.zig`): 4096 bytes per sequence, key 16, `msg` 2048
  decoded, `title` 192 decoded, `app` 32, id 128 with segments of 32 and depth 8.

### Every OSC number the Ghostty parser accepts

From the dispatch at the end of `src/terminal/osc.zig`: **0, 1, 2, 4, 5, 7, 8, 9, 10-19, 21, 22,
52, 66, 72, 99, 104, 105, 110-119, 133, 777, 1337, 3008, 5522, 7501.** Other numbers (including the
prefix states 30, 300, 55, 75, 750, 77, 552) fall to `unknown`, which is delivered to the embedder
as `osc_unknown` only if a byte limit is set (`Stream.Options.osc_unknown_max_bytes`). OSC 3008 is
the UAPI Hierarchical Context spec (`https://uapi-group.org/specifications/specs/osc_context/`),
added `079f5d202` (2026-10-14).

## 2. What Ghostty implements, and where

Legend: **core** = handled entirely inside libghostty-vt; **effect** = parsed and tracked by the
core but needs a host callback or action to do anything; **app** = implemented only in the app
layer (apprt, renderer, termio, shell-integration scripts), absent from libghostty-vt; **parsed**
= parsed, then dropped (logged "unimplemented OSC callback", `src/terminal/stream.zig` near line
3090).

| Sequence / protocol | Class | Evidence |
|---|---|---|
| OSC 0/1/2 title | core stores title, effect `title_changed`; title read via `terminal.getTitle()` | `stream_terminal.zig` `windowTitle` |
| OSC 7 cwd | core stores pwd, effect `pwd_changed` | `stream_terminal.zig` `reportPwd`; 1.3 notes: URI edge cases (MAC addresses) |
| OSC 8 hyperlinks | core (cells carry the link); hover and open are app | `stream_terminal.zig` `.start_hyperlink`; C `ghostty_grid_ref_hyperlink_uri` |
| OSC 9 notification | effect `desktop_notification` | `stream_terminal.zig` line ~179 |
| OSC 9;1-12 ConEmu other subcommands | parsed; dropped by core and app | `osc.zig` `Command`; 1.3 notes ("fully parsed, GUI implements a subset") |
| OSC 9;4 progress | effect `progress_report` | `stream_terminal.zig` line ~222 |
| OSC 4/5/10-19/104/105/110-119 colours | core (palette, dynamic colours, queries answered via write_pty) | `stream_terminal.zig` `colorOperation` |
| OSC 21 kitty colour stack | core | `src/terminal/kitty/color.zig`; `.kitty_color_report` |
| OSC 22 pointer shape | core stores `terminal.mouse_shape`; app applies the cursor | `stream_terminal.zig` `.mouse_shape` |
| OSC 52 clipboard | effects `clipboard_write` / `clipboard_read` (read is synchronous and the embedder mediates consent) | `stream_terminal.zig` lines ~274-300 |
| OSC 66 kitty text sizing | **parsed only**, in core and app | `kitty_text_sizing.zig` (added `916b99df7`, 2026-01-15); 1.3 notes |
| OSC 72 kitty drag and drop | core state machine (`src/terminal/kitty/dnd*.zig`) plus effect `drag_and_drop`; app ignores (`.kitty_dnd` under "Unimplemented") | `stream_terminal.zig` line ~188; 1.4 notes "parsed, not implemented" |
| OSC 99 kitty notifications | **parsed only**, in core and app | `kitty_desktop_notification.zig` (added `c67a1db99`, 2026-08-21); no consumer in `src/termio` or `Surface.zig` |
| OSC 133 semantic prompt | core tracks prompt/input/output marks and click mode; effect `semantic_prompt` reports steps; app owns jump, click-to-move, command-finished | `osc/parsers/semantic_prompt.zig` (commands A, B, C, D, I, L, N, P; options aid, cl, prompt_kind, err, cmdline, cmdline_url, redraw, special_key, click_events, exit_code); `stream_terminal.zig` `semanticPrompt` |
| OSC 777 notify | effect `desktop_notification` (shares OSC 9 path) | `osc/parsers/rxvt_extension.zig` |
| OSC 1337 | **only `Copy` (clipboard write) and `CurrentDir` (cwd)**; every other iTerm2 key (File, SetMark, SetUserVar, RequestAttention, ReportCellSize, ...) is parsed and dropped | `osc/parsers/iterm2.zig` |
| OSC 3008 context | **parsed only** | `hierarchical_context.zig`; `stream.zig` unimplemented list |
| OSC 5522 kitty clipboard | core transaction state (write buffering, aliases, grants, size cap `kitty_clipboard.max_write_size`) plus effects `clipboard_write`/`clipboard_read`; mode 5522 paste events when `clipboard_read` is installed | `stream_terminal.zig` `kittyClipboard*`; 1.3 notes said parse-only, 1.4 notes say implemented on macOS and GTK |
| OSC 7501 | effect `program_status`; app ignores | section 1 |
| Other 7xxx | none (7501 is the only one) | dispatch list above |
| APC 25a1 Glyph Protocol (experimental) | core, off unless `GHOSTTY_TERMINAL_OPT_GLYPH_PROTOCOL`; answers queries, no renderer yet | `src/terminal/apc/glyph.zig`; 1.4 notes |
| Kitty keyboard protocol | core flags and stack (`CSI > u`, `< u`, `= u`, `? u`); key encoder in the C API turns events into bytes | `stream_terminal.zig` `.kitty_keyboard_*`; `include/ghostty/vt/key/encoder.h` |
| Kitty graphics (APC G) | core parse, storage, placements, animation (1.4), unicode placeholders; the build option `kitty_graphics` gates it; drawing is the host's job | `src/terminal/kitty/graphics_*.zig`; `include/ghostty/vt/kitty_graphics.h`, `render.h` image iterator |
| Sixel | **not implemented.** The only mention is the DA1 attribute enum (`device_attributes.zig:53`); no sixel decoder exists in `src/terminal` | grep of `src` for "sixel" |
| Mode 2026 synchronized output | core flag; effect `render_hold` tells the host to stop drawing | `stream_terminal.zig` line ~333 |
| Mode 2027 grapheme clusters | core | `modes.zig` line 413 |
| Mode 2031 colour-scheme reports | core tracks the mode; effect `color_scheme` answers DSR 996 and mode 2031 notifications; the app sends 2031 reports from `Termio.changeConfig` | `modes.zig` line 414; `stream_handler.zig` lines 133, 904 |
| Mode 2048 in-band size reports | core; effect `size` supplies geometry | `stream_terminal.zig` `reportMode2048` |
| Mode 1016 SGR-pixels mouse | core flag; mouse encoder in the C API | `modes.zig` line 403 |
| Mode 2033 / `CSI ? 998 n` visibility reports | core (1.4) | 1.4 notes |
| Modes 5522, 2004, 1004 | core flags | `modes.zig` |
| XTGETTCAP | core (DCS handler, answers from the built-in terminfo) | `stream_terminal.zig` `.xtgettcap`; `src/terminfo` |
| DECRQM / DECRQSS | core | `stream_terminal.zig` `requestMode`, `.decrqss` |
| XTVERSION | effect `xtversion` (string, 256 bytes max) | `stream_terminal.zig` line ~306 |
| DSR 996 | effect `color_scheme` | `stream_terminal.zig` `.color_scheme` |
| CSI 14/16/18/19 t, in-band size | effect `size` | line ~209 |
| CSI 8 t window resize | effect `resize_window`; the embedder decides (app default off, `vt-window-resize-allowed`) | line ~271; 1.4 notes |
| CSI 21 t title report | core gated by `Handler.title_report` (default off) | `stream_terminal.zig` line ~66 |
| DECRQCRA / XTCHECKSUM | core, gated by `xt_checksum_report` (default off) | same file |
| ENQ, bell | effects `enquiry`, `bell` | lines ~174, ~203 |
| tmux control mode | parsed in core; app: `.tmux` hooked only for the DCS entry, not the GUI | 1.3 and 1.4 notes |

## 3. The libghostty-vt C API today

Headers: `include/ghostty/vt/*.h` at `2693982f8`. The first tag is `vt-1.0.0` (2026-11-06). Header
first-add dates (git log of the file): `key/encoder.h` 2025-10-06, `paste.h` 2025-10-06, `osc.h`
2025-10-06, `sgr.h` 2025-10-25, `terminal.h` 2026-03-13, `mouse/encoder.h` 2026-03-15,
`render.h` 2026-03-19, `selection.h` 2026-04-04, `kitty_graphics.h` 2026-04-06,
`grid_ref_tracked.h` 2026-05-23, `snapshot.h` 2026-08-03, `search.h` 2026-08-31.

Terminal options (`GHOSTTY_TERMINAL_OPT_*`, `terminal.h` lines ~2034-2510): USERDATA, WRITE_PTY,
BELL, ENQUIRY, XTVERSION, TITLE_CHANGED, SIZE, COLOR_SCHEME, DEVICE_ATTRIBUTES, TITLE, PWD,
COLOR_FOREGROUND, COLOR_BACKGROUND, COLOR_CURSOR, COLOR_PALETTE, KITTY_IMAGE_STORAGE_LIMIT,
KITTY_IMAGE_MEDIUM_FILE, _TEMP_FILE, _SHARED_MEM, APC_MAX_BYTES, APC_MAX_BYTES_KITTY, SELECTION,
DEFAULT_CURSOR_STYLE, DEFAULT_CURSOR_BLINK, GLYPH_PROTOCOL, PWD_CHANGED, CLIPBOARD_WRITE (added
2026-07-10), SCROLLBACK_MAX_BYTES, SCROLLBACK_MAX_LINES, DESKTOP_NOTIFICATION and PROGRESS_REPORT
(both 2026-07-27), CONTINUATION_MAX_BYTES, TITLE_REPORT, MODE_DEFAULT, MODE, UNKNOWN_SEQUENCE
(2026-08-08), UNKNOWN_MAX_BYTES, TERMINFO_NAME, CLIPBOARD_READ (2026-08-21). Further effects seen in
the header and the handler: SEMANTIC_PROMPT (2026-09-30), RENDER_HOLD (2026-09-19), RESIZE_WINDOW
(2026-10-10), PROGRAM_STATUS (2026-10-06), RESET. Option-add dates come from `git log -S` on
`terminal.h`; the ordering of options is by enum value, not date.

Other surfaces:

- Key encoder (`key/encoder.h`, kitty keyboard, modifyOtherKeys, legacy, DECCKM), mouse encoder
  (`mouse/encoder.h`, X10/normal/button/any, UTF-8/SGR/URXVT/SGR-pixels), focus encode
  (`focus.h`), paste (`paste.h`: `ghostty_paste_is_safe`, `ghostty_paste_encode`,
  `ghostty_terminal_paste`, bracketed-paste aware).
- Standalone parsers: OSC (`osc.h`: new/next/end/command_type/command_data) and SGR (`sgr.h`).
- Render state (`render.h`): row and cell iterators, dirty tracking, colours, cursor, and an image
  iterator for kitty graphics.
- Kitty graphics storage (`kitty_graphics.h`): image get, placement iterators, rect, pixel and grid
  size, viewport position, source rect, render info, animation tick.
- Formatter (`formatter.h`), search (`search.h`), selection and selection gestures
  (`selection.h`), grid refs and tracked grid refs, snapshot encode/decode (`snapshot.h`),
  size-report and colour-scheme report encoders, build info, modes (`modes.h`), unicode helpers.
- Examples: `example/c-vt-*` (effects, stream, osc, sgr, paste, render, kitty-graphics, search,
  snapshot, size-report, selection and more).

After the 1.0 tag (2026-11-06) up to 2026-11-11 the repo has few libghostty changes, and only one
touches `include/ghostty/vt`: `ea675302b` (2026-11-06) "start 1.1.0-dev, add the ABI baseline and
version gate". The rest are build and fix commits: Windows x86_64/aarch64 prebuilt zips (PR #15151,
2026-11-08), a restored snapshot keeping its page capacity (PR #15195, 2026-11-10), a replaced
kitty image kept until its transmission ends (PR #15184, 2026-11-10), memset quirk export fixes
(PR #15189), and `-Demit-lib-vt` with `-Demit-exe` together (PR #15196, 2026-11-11). So everything in
the table in section 2 that is core or effect is in `vt-1.0.0` unless the row says otherwise. A
tip changelog for libghostty-vt was not found (the repo's only GitHub release is the rolling `tip`);
the PR list above is from `git log vt-1.0.0..HEAD`.

## 4. App-level features an embedder must build

| Feature | How Ghostty does it | Source |
|---|---|---|
| Shell-integration scripts and injection | Scripts per shell (bash, zsh, fish, elvish, nushell). Injection is by environment: bash via `ENV` (POSIX mode) with the old value kept in `GHOSTTY_BASH_ENV`; zsh by pointing `ZDOTDIR` at the resources dir and keeping `GHOSTTY_ZSH_ZDOTDIR`; fish, elvish and nushell via data-dir paths. `GHOSTTY_SHELL_FEATURES` lists `cursor`, `path`, `ssh-env`, `ssh-terminfo`, `sudo`, `title`. The scripts emit OSC 133 and OSC 7. | `src/termio/shell_integration.zig` (`setup`, `setupFeatures`, `setupBash`, `setupZsh`, `setupNushell`); `src/shell-integration/` |
| Prompt jump | Keybinding action `jump_to_prompt = +-N`, queued to the IO thread, which scrolls the viewport to OSC 133 prompt marks (the marks live in the core). | `src/Surface.zig` line ~5429; `src/config/Config.zig` default bindings; `Config.zig` shell-integration docs (line ~2924) |
| Click-to-move cursor | On a click in a prompt, if the screen's `semantic_prompt.click` mode is set and `cursor-click-to-move` is on, either send kitty `click_events` (mode 1 absolute, mode 2 prompt-relative, 1.4) or synthesise arrow-key presses. Shell must set `cl=line` or `click_events`. | `src/Surface.zig` `maybePromptClick`, line ~4300; 1.3 and 1.4 notes |
| Command-finished notifications | The core's `semantic_prompt` effect reports command end; the app times it and runs the action when the exit/duration/focus policy matches. Config `notify-on-command-finish` (`never` default), `-action`, `-after` (5 s). | `src/Surface.zig` line ~1205 (`.command_finished`); `Config.zig` lines 1214-1265 |
| Link detection and hover | Regex link rules (`link`, `link-url` default true, `link-previews`) are matched against the cell grid on mouse move; OSC 8 links come from the cell hyperlink and `link-osc8` (1.4) can turn them off. Hover state and open action are apprt. | `src/Surface.zig` `linkAtPos` line ~4446; `src/input/Link.zig`; `Config.zig` lines 1463-1492 |
| Password-input detection | The IO thread polls the pty termios: canonical mode and echo off means password input; a flag on the terminal is set and the surface is told. The libghostty-vt core never sees the pty, so it has no equivalent. | `src/termio/Exec.zig` lines ~365-384; `src/Surface.zig` `passwordInput` line 1457 |
| Title report policy | CSI 21 t is refused unless `title-report = true` (default false), because a title is attacker-controlled text that would be typed into the foreground process. | `src/Surface.zig` line ~1040; `Config.zig` line 2515; core `Handler.title_report` |
| Clipboard read/write confirmation | `clipboard-read` default `ask`, `clipboard-write` default `allow` (each `allow`/`deny`/`ask`); asks show a confirm dialog. OSC 52 read and kitty 5522 read both go through it; a paste that triggered a 5522 event is pre-granted. | `src/Surface.zig` lines ~1101, ~2295, ~6035-6064; `Config.zig` lines 2460-2461 |
| Paste safety | `clipboard-paste-protection` default true: unsafe pastes prompt; 1.3 added control-character sanitising. The core gives `ghostty_paste_is_safe` and `ghostty_paste_encode`. | `Config.zig` line 2498; 1.3 notes (CVE-2026-26982) |
| OSC 52 limits | The OSC parser caps payloads at 8 MiB (1.4 notes); app write limit `clipboard-write-limit-bytes` default 64 MiB, also used for 5522 writes. | 1.4 notes; `Config.zig` line 2487 |
| Notification policy | `desktop-notifications` switch; at most one notification per second app-wide; identical title+body suppressed for 5 s; then the apprt posts it. The core only calls the `desktop_notification` effect. | `src/Surface.zig` `showDesktopNotification` line 6612; `Surface.zig` line 1143 |
| Progress, mouse shape, window resize, bell | Apprt actions; the core only tracks or calls effects. | `Surface.zig`, `src/apprt/action.zig` |
| Renderer for kitty graphics and glyphs | Renderer layer in the app; libghostty-vt exposes placements only. | `src/renderer`, `render.h` |

## Biggest gaps for an embedder (summary)

1. Clipboard consent: reads are synchronous and blocking in the C callback; the embedder must write
   its own policy, dialogs and limits (`CLIPBOARD_READ`, `CLIPBOARD_WRITE`).
2. Shell integration: nothing is shipped in the library; scripts, injection, and prompt-mark UX
   (jump, click-to-move, selecting command output) are all the embedder's.
3. Notifications: OSC 99 and OSC 66 are parsed but dropped even in Ghostty itself; OSC 9/777 need
   a rate limit and OS posting.
4. Sixel is absent; kitty graphics needs a host renderer.
5. OSC 1337 is only Copy and CurrentDir; OSC 3008, 72, 7501 have no app behaviour.
6. Password-input detection needs pty access.
7. Link detection and hover, title report, window-resize policies are embedder-level policy.
8. The library is v1.0 with an ABI gate just added; breaking changes are still expected (1.4 notes).

## Unverified

- Any OSC 7501 implementer other than Rex; any wider adoption.
- Whether the tip has a libghostty-vt changelog beyond `git log`.
- Exact app handling of kitty graphics deletion/eviction policy versus the core default options.
- Key/mouse encoder option-by-option behaviour (headers were listed, not diffed).
- `kitty_graphics` and `glyph_protocol` build-option defaults in the published libghostty-vt
  binary (our build may differ).
