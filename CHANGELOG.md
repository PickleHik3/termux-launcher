# Changelog

## 0.2.32-vaj

VAJ edition (`io.vaj.tl`), pinned to the verified aarch64 APT bootstrap and the signed `https://repo.pathayam.xyz stable main` repository.

### Changed

- **Fresh installs land on the showcase keyboard setup.** The terminal extra-keys row now
  defaults to the launcher tool row — soft-keyboard toggle (paste on long-press), workspace
  picker and save, window previous/next, pane move-to-edge (next-layout on long-press), and
  the scratchpad (float on long-press) — matching the shipped `termux.properties` example. The
  in-app keyboard's default extra-key selection becomes tab, esc, capslock, copy, paste, cut,
  and alt: the clipboard keys are on out of the box, and the navigation keys the extra-keys bar
  already covers stay off. Any explicit selection or `extra-keys` property overrides both, so
  existing setups do not move.
- **`setup-launcher` slimmed down.** The quick-start script no longer downloads fonts — the
  in-app font picker (Settings › Terminal › Font) owns those now — and fzf and unzip leave its
  package list (the shipped config never wired fzf). The README gained a Quick start section
  for the script.
- The `io.vaj.tl` edition is now presented as a side-by-side **demo edition** in the README and
  on the website, steering daily use to the `com.termux` edition.

### Fixed

- **The CPU card's process list no longer sits stale after returning to the launcher.** With the
  A-Z screen-lock method set to Shizuku, every resume tore down the healthy Shizuku backend and
  rebuilt it, so privileged access read as unavailable for the first seconds after each
  home-return — exactly when the card gets opened — and the process list silently kept its last
  snapshot. A ready backend is now left alone (a dead binder still re-initializes), and the
  card's stale marker now shows when the unprivileged fallback cannot read `/proc/stat`, as on
  hardened builds, instead of presenting a frozen reading as fresh.

## 0.2.31-hotfix.1-vaj

VAJ edition (`io.vaj.tl`), pinned to the verified aarch64 APT bootstrap and the signed `https://repo.pathayam.xyz stable main` repository.

### Fixed

- The v0.2.31-vaj APK was built with the `com.termux` application id and Termux manifest placeholders, so it installed into the `com.termux` slot and failed on first start with "failed to get package context for io.vaj.tl". The build now restores the `io.vaj.tl` application id, manifest package name, arm64-v8a-only publishing, and the runtime-downloaded VAJ bootstrap (no embedded Termux bootstraps). If v0.2.31-vaj replaced an existing `com.termux` install, reinstall the com.termux edition APK over it — data is preserved.

## 0.2.31-vaj

VAJ edition (`io.vaj.tl`), pinned to the verified aarch64 APT bootstrap and the signed `https://repo.pathayam.xyz stable main` repository.

### Added

- **First-launch onboarding** — a three-page tour over real footage of the launcher: the essential first commands, the dock with `%` search and alphabet rail, and persistent windows with splits and the live status surfaces. Forceable with `EXTRA_SHOW_ONBOARDING`.
- **Landscape layout** — draws into the display cutout, per-orientation keyboard height with a lower landscape ceiling, and a vertical dock rail of pinned apps on the left edge.
- **Workspace command restore** — saving records each pane's foreground command behind a checkbox, and loading offers to run them again through the normal login shell; plus per-row workspace delete.
- **Terminal font picker** — fourteen curated families with SHA-256 verification, `fonts.d` drop-ins, ordered font fallback, named symbol maps, and geometric box drawing with Powerline separators synthesized by default.
- **Working indication** — a window's pill rim breathes while its shell is actually burning CPU, and lights up in the error colour when a shell rings the bell for attention.
- **Terminal capability advertising** — XTVERSION, XTSMGRAPHICS and `TERM_PROGRAM` identify the launcher to programs that pick features by terminal.
- **Per-pane zoom**, case-sensitive keybindings with per-family group colours, app keybinding from the palette, and every Material container role exported to `~/.termux/material-colors.{sh,properties}`.

### Changed

- The in-app keyboard's colours follow the Material theme unless pinned; settings search reaches every sub-screen; Open-Meteo is credited beside the forecast; bar CPU/RAM readings are smoothed.

### Fixed

- "Customize status appearance" no longer crashes in dark mode below Android 12 (#7).
- Closing a pane tears down its whole process group; the scratchpad no longer shrinks under the keyboard; the CPU card keeps working; the wallpaper is visible again in wallpaper mode; download catalogs no longer flicker.

## 0.2.30-vaj

VAJ edition (`io.vaj.tl`), pinned to the verified aarch64 APT bootstrap and the signed `https://repo.pathayam.xyz stable main` repository.

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
- **VAJ motd** — the message of the day is now VAJ-branded and points at `repo.pathayam.xyz`; upstream Termux links removed. Ships via `termux-tools 1.46.0+really1.45.0-4` from the repository.

## 0.2.29-hotfix.1-vaj

VAJ edition (`io.vaj.tl`), pinned to the verified aarch64 APT bootstrap and the signed `https://repo.pathayam.xyz stable main` repository.

### Changed

- Renamed the keyboard color editor to **Keyboard Colors** and clarified palette editing with **Edit colors** / **Save colors** actions.
- Refreshed the README demo recording and screenshot gallery.
- The standalone release now publishes only arm64-v8a, matching its aarch64-only bootstrap and package repository.

### Fixed

- The welcome tour's quick-setup action now opens the existing website setup section, which uses the maintained `setup-tmux-btop` script.
- Keyboard themes can now be imported by the complete Base16, Base24, or Tinted8 ID shown in the Tinted Gallery, with a direct Gallery link in the import dialog.
- Opening Settings no longer causes the launcher to briefly flash the Settings screen again when Home is pressed.

## 0.2.29-vaj

VAJ edition (`io.vaj.tl`), pinned to the verified aarch64 APT bootstrap and the signed `https://repo.pathayam.xyz stable main` repository.

### Added

- **Built-in terminal keyboard** — an embedded on-screen keyboard (a trimmed Unexpected-Keyboard port) for the terminal: themes, a per-key color-scheme creator, dock-matched glass, size/shape and key-spacing tuning, optional key haptics and press sounds, a custom label font, configurable extra keys, custom `~/.termux/keyboard/layout.xml` support, and a settings page linking the upstream layout docs.
- **Onboarding tour** — a replayable first-run showcase with per-page screen-recording preview clips; reachable from Settings → System & Info → Quick start tour.
- **Glass Labs** — a live appearance tuner for the terminal, dock, and sessions menu (style, size, per-page icon count, blur/opacity/grain).

### Changed

- Unified the glass treatment across dock, keyboard, sessions menu, and navigation strip.
- Improved adaptive light-mode terminal colors.
- Moved the quick-start tour into Settings → System & Info and added a feedback link.

### Fixed

- The rotate/circle gesture now capitalizes letters even when a custom layout binds Fn to every letter (Shift now wins over the Fn modmap for letter keys).
- A-Z rail swipe-up intent is classified from recent motion, with sticky locks, to stop accidental launches.
- Per-icon ripple color extraction and softer ripple rendering; artwork-hugging search focus outline; dock-style pill rendering.

## 0.2.28-vaj

VAJ edition (`io.vaj.tl`), pinned to the verified aarch64 APT bootstrap and the signed `https://repo.pathayam.xyz stable main` repository.

### Fixed

- Rate-limited API responses (HTTP 429) now include `Retry-After` and `RateLimit-*` headers so OpenAI/Ollama clients can back off correctly instead of guessing.
- Attempting to load an embedding model into the generation runtime now returns a clear error; embedding models are served on demand through the embeddings endpoints and no longer need to be loaded.

## 0.2.27-vaj

VAJ edition (`io.vaj.tl`), pinned to the verified aarch64 APT bootstrap and the signed `https://repo.pathayam.xyz stable main` repository.

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

## 0.2.26-vaj

VAJ edition (`io.vaj.tl`), pinned to the verified aarch64 APT bootstrap and the signed `https://repo.pathayam.xyz stable main` repository.

### Added
- Notification popup for pinned apps: when a pinned app has an unread notification, swipe up from its icon in the pinned-icons row to open a popup and interact with the notification directly.
- Pinned app icon pages now loop around instead of stopping at the first or last page.

### Fixed
- Custom app icon bug fixes: icon-pack changes now refresh immediately — including pinned-icon pack changes and resetting per-app icon overrides — without requiring `termux-reload-settings`, and rendered icon caches are invalidated after icon source changes.

## 0.2.25

VAJ edition (`io.vaj.tl`) — a standalone Termux launcher package you can install **alongside** your existing upstream Termux app. It uses its own embedded bootstrap and pulls packages from a self-hosted APT repo at `repo.pathayam.xyz` (packages were rebuilt locally, so updates there are not guaranteed to be frequent). This edition is largely untested; the standard `com.termux` edition remains the recommended one.

Companion add-ons must be the matching `-vaj`-tagged forks (separate `io.vaj.tl` prefix):
- Termux:API — https://github.com/PickleHik3/termux-api/releases
- Termux:Styling — https://github.com/PickleHik3/termux-styling/releases

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
