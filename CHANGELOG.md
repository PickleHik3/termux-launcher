# Changelog

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
