# Theme templates: the terminal colours reach the tools inside it (2026-09-13)

The launcher already derives a Material terminal palette from the wallpaper
(`MaterialTerminalColorScheme`, three contrast levels softer/default/harder) and exports it to
`~/.termux/material-colors.properties` and `.sh`. Only fish, oh-my-posh and nvim read it, each
by hand. Noctalia shell (MIT, `/usr/share/noctalia/assets/templates/`) solves the general case:
a flat token map, plain-text templates with `{{colors.<token>.<mode>.<format>}}` placeholders, a
per-app manifest (output path, apply hook, undo hook), applied ids persisted so a disable runs
the undo. This ports that machinery. It does **not** port noctalia's palette math: its
wallpaper-derived ANSI colours are role colours in ANSI slots (green = primary, blue = tertiary,
bright = normal), which is worse than what the launcher has. The launcher palette is the source
of truth; only the active contrast level is rendered. Review page: `.lavish/theme-templates.html`.

## Decisions

| Item | Rule |
|---|---|
| Token map | The exported Properties grows to the full Material 3 set noctalia exposes (48 roles: adds `primary_fixed`, `primary_fixed_dim`, `on_primary_fixed`, `on_primary_fixed_variant` and the secondary/tertiary equivalents, `surface_dim`, `surface_bright`, `surface_container_lowest`, `surface_container_low`, `background`, `on_background`, `inverse_surface`, `inverse_on_surface`, `inverse_primary`, `shadow`, `scrim`) plus noctalia's 22 `terminal_*` names as aliases of the existing keys: `terminal_normal_black..white` = `terminal_color0..7`, `terminal_bright_*` = `color8..15`, `terminal_cursor_text` = `terminal_background`, `terminal_selection_fg` = `on_surface_variant`, `terminal_selection_bg` = `surface_variant`. Existing keys stay. New key `mode` = `dark` or `light` from the terminal background tone. |
| Template syntax | `{{ colors.<token>.<mode>.<format> }}` (spaces optional). Modes `default`, `dark`, `light` all resolve to the active palette. Formats: `hex` (`#rrggbb`), `hex_stripped`, `rgb` (`rgb(r, g, b)`), `rgba` (`rgba(r, g, b, 1.0)`), `red`, `green`, `blue` (decimal). `{{ mode }}` renders the mode. Any other `{{ … }}` is left verbatim (oh-my-posh themes are Go templates). A `{{ colors.… }}` with an unknown token or format, or any `<*` block: the template is skipped and logged, nothing is written. Filters, loops, `image`, `closest_color`: out of scope. |
| Template layout | One directory per template: `template.properties` (`name`, `summary`, `input`, `output`, `post_hook`, `undo_hook`), the input file, `apply.sh`, `undo.sh`. `output` allows `~` and `$VAR`; `XDG_CONFIG_HOME` and `XDG_CACHE_HOME` default to `$HOME/.config` and `$HOME/.cache` when unset. Hooks are relative paths run as `bash <dir>/<hook>`. `name` and `summary` are product copy. |
| Where templates live | Built-ins ship in the APK under `assets/theme-templates/<id>/` and are extracted write-if-changed to `$PREFIX/libexec/termux-launcher/theme-templates/<id>/` before use. User templates in `~/.termux/theme-templates/<id>/`, same layout, enabled by presence. A user id shadows a built-in id. |
| Enablement | Settings › Look, under Terminal contrast: a multi-select list "Tools that follow the terminal colours" (`theme_templates_enabled`, entries = built-in `name`s, default none). Enabling or disabling applies at once. User templates need no toggle. |
| Apply pass | Runs on the existing `MATERIAL_COLOR_FILE_EXECUTOR` right after `writeMaterialColorFiles`, in both the wallpaper and the `colors.properties` branch. Per enabled template: render, write the output only if the bytes changed, run `post_hook` when the output changed or the template was not applied before. Then for every id in the applied set that is no longer enabled or present: run `undo_hook` if its directory still exists, else delete the recorded output; forget the id. Applied ids and their output paths persist in `~/.termux/theme-templates/.applied` (tsv: id, output). A pass superseded by a newer one stops between templates. |
| Hooks | `AppShell.execute` with `TermuxShellEnvironment`, synchronous on the executor, 30 s timeout, env `TERMUX_THEME_ID`, `TERMUX_THEME_DIR`, `TERMUX_THEME_OUTPUT`, `TERMUX_THEME_MODE`. A failing hook is logged and does not stop the pass. Hooks never run on the UI thread. |
| Hook contract | apply.sh is idempotent: it wires the rendered file into the tool's own config with one include line or a marker block (`# >>> launcher-material >>>` … `# <<< launcher-material <<<`), writes only when content changes, writes through symlinks, reloads a running instance when the tool allows. undo.sh removes exactly what apply.sh added and the rendered file. Theme id everywhere is `launcher-material` (matches the nvim colorscheme already shipped). |
| Built-in pack | Ported from noctalia: `starship` (palette block + `palette = "launcher-material"`), `helix` (theme file; apply sets `theme = "launcher-material"` and keeps the previous line as a marker comment for undo). New: `tmux` (`source-file` line, live `tmux source-file`), `bat` (tmTheme, `--theme` in bat config, `bat cache --build`), `yazi` (flavor dir, `[flavor]` in theme.toml), `fzf` and `lazygit` (env-based: a `conf.d/launcher-material-<id>.fish` drop-in for fish and a marker block in `~/.bashrc` and `~/.zshrc`, editing only rc files that exist; lazygit uses `LG_CONFIG_FILE`, fzf `FZF_DEFAULT_OPTS`). Added on review (2026-09-13): `ohmyposh` (the shipped aliens-material theme with the palette rendered in, so it works in every shell without the per-prompt env dance; apply sets `POSH_THEME` through the fish conf.d drop-in and the `~/.bashrc`/`~/.zshrc` marker blocks; an init line that passes `--config` explicitly wins, and the docs say so) and `nvim` (renders `lua/plugins/launcher-material.lua`: a static palette table plus a spec that selects the colorscheme under LazyVim or AstroNvim; the template carries the `launcher-material` colorscheme files and apply installs them write-if-changed into `~/.config/nvim`; undo removes them only if unmodified; for a plain config apply adds a `colorscheme` marker block to `init.lua`). The tlstore `omp-theme` and `nvim-theme` items were a stopgap and retire once this ships (user-side, needs the catalog key). Desktop terminals (kitty, alacritty, foot, ghostty, wezterm) are not ported: the launcher is the terminal. |
| Setup command | A manifest may declare `setup_hook` (relative path). When the user turns such a built-in on, Settings offers a dialog: title = the tool's name, "One line in your shell setup finishes this. Copy the command, paste it in the terminal and press Enter.", buttons Copy / Not now. Copy puts `bash "<extracted dir>/<setup_hook>"` on the clipboard. The script detects the shell it was pasted into (bash, zsh, fish) and adds the tool's init line inside a marker block in `~/.bashrc`, `~/.zshrc` or a fish conf.d file, never touching an existing init line; undo removes it. `ohmyposh` and `starship` ship one. |
| Existing consumers | fish, oh-my-posh and nvim keep reading `material-colors.sh` as today; nothing there changes. |
| Out of scope | Light/dark dual rendering, template filters and loops, a tlstore item kind with hooks, per-app contrast overrides, any change to the ANSI derivation. |

## Build plan

| # | Branch | Delivers | Depends on | Gate |
|---|---|---|---|---|
| 1 | `feat/theme-tokens` | Full 48-role export, `terminal_*` aliases, `mode`, in `MaterialTerminalColorScheme.createMaterialRoleProperties`; `MaterialTerminalColorSchemeTest` covers every new key and the alias identities | — | `:app:testDebugUnitTest` green |
| 2 | `feat/theme-templates` | `ThemeTemplateRenderer`, `ThemeTemplate` manifest loader (assets + user dir), `ThemeTemplateApplier` (pass, applied set, hooks, timeout, supersede), settings preference + strings, wiring after `writeMaterialColorFiles`, `docs/en/Launcher_Settings.md` section, tests with fixture templates and a fake hook runner | token names from this spec only | `:app:testDebugUnitTest` green; Waydroid: enable Starship, see the palette block land in `~/.config/starship.toml`, disable, see it removed |
| 3 | `feat/theme-template-pack` | the nine template dirs under `app/src/main/assets/theme-templates/`, `scripts/theme-templates/check.sh` (renders each with a fixture palette via a dev-only python renderer, runs each tool's own validation where installed: `bat cache --build`, `tmux -f`, `hx --health`-style parse, TOML/YAML parse otherwise), CREDITS line for noctalia | token names from this spec only | `check.sh` green locally; on-device look at bat, yazi, fzf after 1–3 merge |

Phases 1–3 run in parallel; 2 and 3 meet only on merge. Nothing here touches applicationId,
bootstrap or manifests, so the three editions need no per-edition decision.

## Side queue (user-side, small)

| Item | Why |
|---|---|
| ~~`docs/en/examples/config.fish` honours `$POSH_THEME` when set~~ (done, 541c4b53), else the aliens theme as today; then bump the `config-fish` pin in `scripts/tlstore/items.tsv`, rebuild and sign the catalog (signing key is the maintainer's, `~/.config/vaj-apt/tlstore-minisign.key`) | the shipped fish config passes `--config` explicitly, which makes oh-my-posh ignore `POSH_THEME`; without this the `ohmyposh` template only reaches bash and hand-written fish configs |
| Retire `omp-theme`, `nvim-theme`, `nvim-palette`, `nvim-colors` from `scripts/tlstore/items.tsv` (and drop `omp-theme` from the `fish-shell` bundle), rebuild and sign the catalog | they were a stopgap for what the settings list now does; user decision 2026-09-13 |

btop was dropped from the pack on 2026-09-13: it is not available on Android and the app does not ship it.
