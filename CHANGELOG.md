# Changelog

## Unreleased

### Added

- **Reproducible Termux recipes for Sigye and animated-Kitty Fastfetch** — pinned upstream builds
  carry the Android clipboard compatibility fix Sigye v0.6.0 still needs and the Fastfetch GIF
  frame upload/playback patch used to exercise the launcher's terminal-driven Kitty animation.
  Both install under `~/.local` without replacing APT-owned files.
- **Terminal font picker** — Settings → Appearance → Terminal fonts installs a complete multi-face font with no shell: pick from seven curated families with download sizes, SHA-256 verification and license text shown up front, with a star on the suggested one (Maple Mono, the variable pair) and each family's own icon, ligature and feature defaults applied on install. Toggles for icons, ligature policy and the `wght` axis rewrite the config in place, and **Use font.ttf / Termux:Styling** hands control back by deleting exactly one file. The picker writes only under `~/.termux/fonts/`, plus `~/.termux/fonts.d/10-launcher.conf`; it never creates, overwrites or deletes `~/.termux/font.ttf` or `font-italic.ttf`, so installing a family changes the terminal only. Reachable from the palette, keybindings and agents as `fonts.pick` and `fonts.install`.
- **`~/.termux/fonts.d/*.conf` drop-ins** — font config fragments load in filename order before your own `fonts.conf`, so a hand-written file always overrides an app-managed or third-party fragment while still inheriting what it does not restate. Bounded to 32 files and 256 KiB across all of them, which never eats into `fonts.conf`'s own 64 KiB allowance.
- **Ordered font fallback** — `fallback_font path=…|family=…`, repeatable up to 8, is the controllable answer to Android substituting a CJK or emoji face you did not choose. Coverage is probed on the cluster's base code point and memoized, and a fallback face never changes cell width.
- **Named symbol maps** — `symbol_map name=<ident> …` lets `font_features` and `font_variations` target one specific map instead of the shared `symbols` target.
- **Geometric box drawing** — box-drawing, block, shade, braille and sextant cells are drawn as geometry snapped to shared integer cell edges, so frames, block ramps and braille graphs join seamlessly at any cell size instead of showing the hairline gaps a font's glyph metrics leave. `box_drawing font` restores glyph rendering, `box_drawing_scale` tunes the four line weights, and `powerline_symbols synthesize` claims the separator ranges too.
- **Bind a key to an app from the palette** — long-press an app row (or `Ctrl+Alt+Enter` on the focused one) and press a combination; it is written to `~/.termux/termux-launcher-bindings.conf` with your comments and ordering preserved, and takes effect immediately. App rows also show the chord already bound to them, and are searchable by it.
- **Rename any session from the palette** — a Rename row per session, so renaming a session other than the current one needs neither the browser nor the panel (`session.rename_at_index`).
- **Working indication** — a sweeping underline on the pill of each window whose shell is producing output, plus three pulsing dots in the status row for the current session. Unprivileged, a silent foreground process (`sleep 300`, an idle editor) reads as not working.
- **Settings cog in the status row**, opening Terminal & status settings directly.
- **Rename and close buttons on sessions-panel rows** — the long press still works, but it was the only way to know it was possible.
- **A window whose shell asked for you lights up.** The pill rim of a window turns the Material error colour and pulses with a halo once a shell in it rings the terminal bell — a permission prompt, an agent handing its turn back, a build that stopped for input. The bell is the signal, so no cooperation from the program is needed, and it is recorded even when the launcher is in the background. Focusing the window clears it, and a bell from the window you are already in is not news.
- **Terminal contrast in the surface editor** — Edit surfaces → Terminal now carries the Softer/Default/Harder control alongside opacity and border, applied live like everything else there, with Reset tab and dismiss behaving as they do for the rest of the tab. Disabled with a reason when wallpaper colours are off, since contrast only grades the generated palette.

### Changed

- **Shipped terminal defaults now match the maintained live setup.** The bundled keyboard and its
  editable copies use the revised navigation/voice gestures, while the Fish template and guarded
  installer select the compact, status-aware Aliens Material prompt.
- **An upper-case letter in a binding is Shift.** `map Ctrl+Alt+R …` and `map Ctrl+Alt+r …` are now two bindings rather than the same one — case on the key was previously discarded, so a config file had no short way to name the shifted stroke. Modifier names and multi-character key names (`left`, `pageup`) stay case-insensitive. Using it: `Ctrl+Alt+R` renames the shell session on any layout, while `Ctrl+Alt+r` renames the current window (and still renames the session when split panes are off, where there is no window to rename). The palette prints a shifted letter the same way: `C-A-R`.
- **One group colour per action family, everywhere a binding is shown.** The keybind hint legend and the lit caps on the in-app keyboard now colour each key by its action's namespace — panes, windows, session, workspace, terminal, clipboard, appearance, app — as hue rotations of the live Material primary instead of three shared roles, so windows and session no longer wear the same colour.
- **Pressing a lit cap on the in-app keyboard runs its binding.** A character key under a latched `Ctrl+Alt` is resolved as a stroke instead of being written to the shell as an escape-prefixed control byte, so `termux-launcher-bindings.conf` governs the soft keyboard exactly as it governs a hardware one.
- The Rounded surface style's follow-the-style corner radius is 20dp (was 16–26dp by surface height), and the status surface honours it instead of squaring off.
- Session names are capped at 8 characters instead of 5, and the scratchpad shell is named `scratch`; an existing scratchpad is re-adopted rather than duplicated.
- The transient notice chip moved to the top-right, is quieter, and now carries every terminal notice — window positions, "no session to split", the max-terminals refusal — which were previously stock toasts. Creating a pane reports the new pane count.
- **Background commands report per session, not per pane.** Opening a window, splitting a pane, or moving between the windows of one session no longer raises a corner notice, even when you leave a command running: those windows are still on screen and their pills carry the working and waiting states. Only leaving the session hides them, and that is what now puts a standing row in the top-right corner.
- **A changing window title no longer raises a notice.** It fired on every progress write — several times a second for one background job — to say what the window's own pill and the corner stack already say.
- **The transient notice chip is a Material surface.** Label-medium type on a surface-container-high fill with an outline hairline and elevation, entering on the emphasized-decelerate curve, instead of 9.5sp text on a black scrim. The standing background rows match it one step lower in the elevation scale, and the two share one column: the rows sit directly under the notice and slide up into its slot when it expires.

### Fixed

- **Every key a latched prefix binds now lights up and appears in the hint legend.** The 18-row legend cap also ended the loop that built the keyboard lighting, so the strokes registered last — `Ctrl+Alt+R` among them, behind the nine session-index digits — were neither listed nor lit. Lighting is no longer capped at all, and a run of keys one action claims (the four arrows, `1`–`9`) collapses to a single legend row.
- **Terminal text is no longer clipped by the rounded corners of the terminal border**, at 20dp or at 40dp: the arc's depth is now padding inside the clip rather than a margin around it, which moved the box without moving its corners off the arc.
- **Closing a pane no longer leaves its background jobs running.** Teardown hangs up the shell's whole process group and escalates to a kill only if the leader is still alive, so `sleep 300 &` dies with the pane it was started from. `nohup` and `setsid` still detach, as intended.
- **The scratchpad no longer shrinks every time the keyboard opens and closes**, and no longer paints under the dock.
- **Showing or hiding a float no longer reflows the terminal behind it** — the frame-line owner, and so the pane inset, no longer depends on whether a float is present, and floats are attached without rebuilding the tiled tree.
- **The CPU card keeps working.** A failed privileged read no longer wipes the process list, a wedged command no longer stops sampling for good, output is drained concurrently so a large process list cannot deadlock the read, sampling resumes after leaving the app, and a stale sample is labelled rather than blanked.
- **Swipe-to-reply opens the newest reply-capable notification** instead of only firing when an app had exactly one, and an incoming notification no longer throws away a half-typed reply.
- The command palette's focus highlight and bottom fade stay inside its rounded corners, and its accent rim is no longer painted over.
- The floating pane's grab pill no longer draws a near-black slab across the top of the float.
- The sessions pop-down accounts for its own row chrome, and only autoscrolls a title that overflows by a readable amount.
- **The wallpaper is visible again in wallpaper mode.** With wallpaper colours on, the terminal surface was raising its own opacity to keep every generated glyph legible over any wallpaper — a floor that only full opacity could satisfy, because a mid-tone grey ANSI colour cannot meet a contrast target over both a dark and a light backdrop at once. That colour is painted on the full-screen root, so the whole launcher went opaque behind the dock and keyboard. The opacity slider is the contract again.
- **The background-command chips no longer blink.** Every bind rebuilt all the rows, which handed the layout transition a full turnover on a view that is re-bound on every title change of every background shell. Rows are reused now, so only real arrivals and departures animate.
- **A new window no longer flashes a chip for its own startup.** Anything a shell's rc files spawn is a non-idle foreground with no shell-integration mark yet, indistinguishable from a background command; a foreground now has to survive one resolver poll before it earns a row.
- **Working indication counts a wrapper script's child.** CPU was read from the foreground process-group leader alone, so `sh build.sh` — which only waits while its child works — reported zero and every wrapped command read as idle. The whole process group is summed now, matched on `pgrp` in one pass over `/proc`.
- **A shell that exits no longer leaves its foreground process behind in the cache**, where a reused pid would inherit it.
- **The exported palette files are built on the main thread.** The writer thread was resolving theme attributes and re-deriving the palette itself, so the files could describe different colours than the terminal took.
- **The terminal surface no longer rebuilds the whole generated palette to read one colour** — a 101-tone contrast search per foreground, cursor and ANSI colour, every time the surface was restyled.

## 0.2.30

### Added

- **Split panes and windows** — tmux-style recursive split panes and windows in the native terminal, with pane controls in the status bar, a per-window pane-layout policy, and a fork-native sessions panel replacing the drawer.
- **Floating panes** — detach the focused pane above the tiled layout (`Ctrl+Alt+F` or the command palette), drag it by the top handle, resize from the grip, dock it back.
- **Workspaces** — durable layout + CWD workspaces with save/picker tools; restores sessions, windows, panes, titles, and working directories after process death.
- **Scratchpad terminal** — toggleable overlay terminal (``Ctrl+Alt+` ``).
- **Command palette and unified keybinds** — terminal-ledger palette overlay (long-press → Command palette, or `Ctrl+Alt+Shift+P`), user-configurable key bindings, keybind hints, and a key inspector overlay.
- **Interactive status bar** — expanded top pane with a clock grid, media widget, and pinned notifications, plus a session-switch indicator.
- **Kitty graphics protocol** — stored images, placements, crop, z-index, delete forms, and terminal-driven GIF animation.
- **Font engine** — `~/.termux/fonts.conf` with four-face configuration, variable-font axes, face-scoped OpenType features, explicit symbol font maps, fixed-cell grapheme shaping, and bounded font metrics.
- **Spacebar swipe gestures and extra-key tools** — swipe bindings on the built-in keyboard's space bar, `tool:` extra keys with key=value arguments, an `app.launch` tool, and app search in the palette.
- **Bundled QWERTY layout** — the built-in keyboard ships a launcher-tuned QWERTY layout by default, with an absolute key-cap opacity control.
- **Shipped configs and `setup-launcher`** — example key bindings, `fonts.conf`, and keyboard layout installed to `~/.termux`; a new guarded `setup-launcher` installer for the fish + Oh My Posh + Maple Mono setup.
- **GPU glass blur** — wallpaper blur runs on the GPU via RenderEffect on Android 12+.
- **`launcherctl`** — launch-only CLI client for launching apps from the shell.

## 0.2.29-hotfix.1

### Changed

- Renamed the keyboard color editor to **Keyboard Colors** and clarified palette editing with **Edit colors** / **Save colors** actions.
- Refreshed the README demo recording and screenshot gallery.

### Fixed

- The welcome tour's quick-setup action now opens the existing website setup section, which uses the maintained `setup-tmux-btop` script.
- Keyboard themes can now be imported by the complete Base16, Base24, or Tinted8 ID shown in the Tinted Gallery, with a direct Gallery link in the import dialog.
- Opening Settings no longer causes the launcher to briefly flash the Settings screen again when Home is pressed.

## 0.2.29

### Added

- **Built-in terminal keyboard** — an embedded on-screen keyboard (a trimmed Unexpected-Keyboard port) you can use in the terminal instead of the Android soft keyboard. Includes themes, a per-key color-scheme creator, dock-matched glass, size/shape and key-spacing tuning, optional key haptics and press sounds, a custom label font, configurable extra keys, custom `~/.termux/keyboard/layout.xml` support, and a settings page linking the upstream layout docs.
- **Onboarding tour** — a replayable first-run showcase with per-page screen-recording preview clips; reachable any time from Settings → System & Info → Quick start tour.
- **Glass Labs** — a live appearance tuner for the terminal, dock, and sessions menu (style, size, per-page icon count, blur/opacity/grain), tuned in-context against the real UI.

### Changed

- Unified the glass treatment across dock, keyboard, sessions menu, and navigation strip.
- Improved adaptive light-mode terminal colors.
- Moved the quick-start tour into Settings → System & Info and added a feedback link.

### Fixed

- The rotate/circle gesture now capitalizes letters even when a custom layout binds Fn to every letter (Shift now wins over the Fn modmap for letter keys).
- A-Z rail swipe-up intent is classified from recent motion, with sticky locks, to stop accidental launches.
- Per-icon ripple color extraction and softer ripple rendering; artwork-hugging search focus outline; dock-style pill rendering.

## 0.2.28

### Fixed

- Rate-limited API responses (HTTP 429) now include `Retry-After` and `RateLimit-*` headers so OpenAI/Ollama clients can back off correctly instead of guessing.
- Attempting to load an embedding model into the generation runtime now returns a clear error; embedding models are served on demand through the embeddings endpoints and no longer need to be loaded.

## 0.2.27

### Changed

- Added **Fullscreen** toggle in Settings → Termux → Terminal View.
- Terminal receive buffer increased from 4 KB to 64 KB for smoother, faster output on heavy streams (build logs, TUIs, AI token streaming).

### Fixed

- Fixed a rare terminal "bounce" where the view could oscillate during relayout (e.g. while running full-screen CLIs); terminal geometry is now derived structurally instead of from its own shifting height.
- Fixed a fullscreen-mode crash on Android 8-10, made the fullscreen toggle apply live, and stabilized the dock lift over the soft keyboard.
- Fixed a terminal freeze that could occur after relaunching the launcher. (all hail Fable, this demon has been a bug since the inception)

### Termux AI

- Upgraded the LiteRT runtime to 0.14.0 and rebuilt the MNN native libraries with embedding support.
- Fixed a LiteRT generation deadlock and corrected MNN memory-mapping for more stable on-device inference.
- Broader OpenAI/Ollama API conformance: token-usage accounting, error shapes, and stop-sequence handling.
- Audit-driven correctness fixes across both backends versus the official specs; retained automatic tool use for mobile-action specialist models.

## 0.2.26

### Added
- Notification popup for pinned apps: when a pinned app has an unread notification, swipe up from its icon in the pinned-icons row to open a popup and interact with the notification directly.
- Pinned app icon pages now loop around instead of stopping at the first or last page.

### Fixed
- Custom app icon bug fixes: icon-pack changes now refresh immediately — including pinned-icon pack changes and resetting per-app icon overrides — without requiring `termux-reload-settings`, and rendered icon caches are invalidated after icon source changes.

## 0.2.25

### Added
- **Termux AI** — run LLMs locally, on-device, right inside the terminal. Two native backends, Google **LiteRT** and Alibaba's **MNN**, serve models over OpenAI- and Ollama-compatible APIs. Works on devices with a supported SoC and enough RAM (Snapdragon 8+ Gen 1 or newer recommended). Quickest start: `pkg i -y aichat`.
- New **Valerie capsule** dock, with better AGSL glass blur, smoother dock physics, and refreshed animations and lighting.
- New app icon.

### Changed
- The optional one-script setup now installs **oh-my-posh** as the shell prompt.
- Dynamic terminal colors and app-name labels are now on by default.
- Reworked open-source attribution and license notices; replaced the fuzzy app-search library with an in-house ranking engine.

## 0.2.23

First release shipped in two editions: the **Termux edition** (`com.termux`, tag `v0.2.23`) compatible with the upstream Termux package ecosystem, and the **VAJ edition** (`io.vaj.tl`, tag `v0.2.23-vaj`) installable alongside official Termux with its own embedded aarch64 bootstrap and VAJ APT repository. See the README's Editions section.

### Added
- Exposed multimodal Gemma 4 (LiteRT) models as modality-scoped OpenAI ids that share one downloaded file: the canonical id loads text-only, `<id>-vision` loads text+image, and `<id>-audio` loads text+audio. This mirrors Google AI Edge Gallery's per-task loading and keeps each GPU load small enough to fit. Select the id from the shell; switching ids reloads the runtime scoped to that modality.
- TAI model import by Hugging Face repo URL with auto backend detection, per-model modality/capability configuration, and imported/downloaded models listed in Browse Catalog.
- LiteRT embedding runtime, LauncherCtl MCP documentation, and OpenAI Responses / Ollama client compatibility for the local model host.
- Per-key glass refraction, glyph glow feedback, dock-glass grain control, and an Apps & Access settings overhaul.

### Changed
- Updated MNN native libraries to 3.6.0 with a UTF-8 continuation-byte patch (fixes emoji/UTF-8 streaming crashes).
- Refined dock styling: glow tiers, capsule icon sizing, page indicator, popup, and wallpaper-mode dock style.

### Fixed
- Bound the isolated `:tai_runtime` process with `BIND_IMPORTANT` so a GPU model load inherits the launcher's foreground priority and is no longer SIGKILLed by Android's low-memory killer during OpenCL initialization (previously surfaced as a runtime "crash" loading large models such as Gemma 4 E4B on GPU).
- Fixed TAI generation streaming, vision autoload, completions on on-disk models, a TAI settings ANR, and restored dock page swipe, extra-keys text-input swipe, and icon contour/pack precedence.

## 0.2.22

### Added
- Added `launcherctl update-scripts` to refresh optional shell/tmux helper scripts without rerunning Getting Started.

### Changed
- Removed the redundant arbitrary `rish` wrapper; use `rish -c` directly for custom Shizuku shell commands and `launcherctl tty-doctor` for setup checks.

### Fixed
- Fixed tmux CPU/RAM helper behavior to prefer efficient `launcherctl resources` data, with a bounded `rish` fallback for plain Termux setups.
- Fixed Shizuku btop helper wrappers to preserve an explicit `RISH_BIN` path.

## 0.2.21

### Added
- Added launcher permission access settings and an accessibility lock prompt.
- Added a guided optional tmux and Shizuku btop setup helper.

### Fixed
- Fixed launch failure when Android denies access to the system wallpaper backdrop.

## 0.2.20

### Added
- Added an optional app-name preview pill while scrubbing the A-Z dock.

### Changed
- Improved A-Z dock scrubbing, page dwell feedback, preview animations, and overflow handling.
- Refined dock, wallpaper, extra keys, and text-selection colors for light and dark themes.
- Settings changes now refresh launcher styling automatically without manually running `termux-reload-settings`.

### Fixed
- Fixed first-run defaults for wallpaper mode and the A-Z row.
- Fixed app-name preview placement, sizing, wrapping, and alignment.
- Fixed sticky extra-key pressed state visibility.

## 0.2.18

### Changed
- Enabled wallpaper mode and the A-Z row by default for fresh installs.

## 0.2.17

### Added
- Added notification dots.
- Added a compact dock toggle for users who need two rows of extra keys, available in Settings > Appearance.

### Changed
- Reworked the apps bar page indicator.
- Removed some items for better security.
- Refined the UI.

## 0.2.16

### Added
- Added global icon pack support for the apps bar and pinned dock.
- Added per-pinned app icon overrides, including apps inside folders.
- Added visual icon selection from installed icon packs.

### Changed
- Simplified launcher icon preferences and moved icon pack settings into Apps Bar.
- Updated icon picker, icon pack picker, wallpaper picker, and launcher popup surfaces to better match the app Material color theme.
- Improved dock background color when transparency or wallpaper is disabled.

### Fixed
- Fixed icon changes requiring a swipe before refreshing.
- Fixed custom icons being lost when apps move into or out of folders.
- Fixed folder previews and folder popup icons using stale system icons.
- Fixed themed icon controls that did not affect launcher icons.
- Fixed app launch reliability for default launch activities.

## 0.2.15

### Changed
- Refreshed launcher documentation and README links around getting started, usage, Material colors, shell integration, tmux setup, and optional Shizuku helpers.
- Restored the GitHub nightly debug build workflow for hosted APK validation.
- Removed stale bundled status helper scripts now covered by documented examples.

### Fixed
- Fixed intermittent first-attempt app launches by preferring normal launcher intents before falling back to `LauncherApps.startMainActivity()`.
- Improved Material color refresh behavior for terminal and shell integrations.

## 0.2.14

### Changed
- Improved dock blur implementation and wallpaper sampling so the phone is not a hand warmer anymore.
- Improved dock motion, IME restore, and return-home animation.
- Improved Material theming across terminal surfaces, dock surfaces, extra keys, and app UI surfaces.
- Added an Appearance toggle to apply Material colors to the Termux shell.
- Exposed Material colors in `~/.termux/material-colors.sh` and `~/.termux/material-colors.properties` for shell integrations such as tmux status bars.

### Fixed
- Fixed the text input field in the extra keys bar/dock so Android keyboard text input can target the field correctly.
- Fixed dock blur flashes and blur pauses during IME transitions.
- Fixed managed/system wallpaper blur alignment and fallback handling.

## 0.2.13

### Fixed
- Fixed Shizuku reconnect after launcher restarts.
- Fixed dock blur state with live wallpapers.
- Improved terminal exit/relaunch behavior.
- Performance refinements and cleanup.

## 0.2.10

### Changed
- Improved launcher search, duplicate app labeling, folder popup sizing, and `launcherctl` status/notification metadata.

### Fixed
- Fixed `launcherctl /v1/apps` to match the launcher’s real app catalog.
- Fixed pinned-page resets during reorder and folder creation.
- Fixed stale pinned and folder app references.
- Fixed folder editor search and package-only folder refs.
- Fixed immediate folder updates for `Move to dock` and `Delete`.
- Fixed collapsed folder previews not refreshing after folder changes.
- Fixed extra right-side padding in the folder popup.
- Removed the pinned-row bloom overlay while keeping page indicators.
