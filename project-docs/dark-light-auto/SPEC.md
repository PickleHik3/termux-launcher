# Automatic dark/light across terminal, keyboard and tools (2026-09-17)

Review page `.lavish/dark-light-theme.html`. Baseline screenshots `.lavish/dark-light-baseline.png`.

## Kitty parity verdict

Detection (Android `uiMode`, activity recreate + `TermuxApplication.onConfigurationChanged`) and live
recolouring (`applyTerminalColors` → `resetAllSessionColors`) exist. Missing vs kitty ≥0.38.1: private
mode 2031 with the unsolicited `CSI ?997;1|2 n` report, the `CSI ?996 n` query, OSC 4 `?` replies, and
any second-mode rendering (renderer `.dark`/`.light` both resolve to the active palette). Consumers at
the recipe versions: nvim 0.12.5, fish 4.8.1, tmux 3.7c, herdr 0.9.1 (`[theme] auto_switch`). Starship
1.26 and oh-my-posh 30.9 have no listener; they re-read config each prompt.

## Decisions (user, 2026-09-17 08:20)

| # | Decision |
|---|---|
| D1 | Both palettes in every export and template. Exports: `material-colors.properties/.sh` stay the active mode (existing consumers), new `material-colors-dark.*` and `material-colors-light.*`; `mode` key unchanged. Renderer: `colors.<token>.dark.*` / `.light.*` resolve to their real palettes, `.default` = active. |
| D2 | The stopped-activity path also pushes the 16 colours and the 2031 report to live sessions through `TermuxService` (same code path as the foreground). Files land first, then the ping. |
| D3 | Starship and oh-my-posh: ANSI names for accents (`blue`, `green`, …), Material surface hex for segment fills. Both palettes rendered once (`launcher-material-dark`/`-light`); the pass flips only the one selector line. |
| D4 | tlstore: retire `omp-theme`, `nvim-theme`, `nvim-palette`, `nvim-colors`; fix and repin `config-fish`. Catalog re-signing is the user's. |
| D5 | New `fish` template: `launcher-material.theme` with `[light]`/`[dark]` sections; fish switches it on the ping. |
| D6 | Pong keeps the blue-sky wallpaper (seed ≈ hue 210). |
| D7 | Retire pong's `~/.local/bin/herdr-material-sync` and the fish `herdr` wrapper; the herdr template renders `auto_switch = true` + `[theme.custom.dark]`/`.light`. |
| D8 | Retire pong's `conf.d/material-terminal-white.fish`; the palette's neutral hue gets a warm nudge in both modes instead (design in phase 2: neutral hue rotated toward ~75° by a bounded amount, chroma ≤ NEUTRAL_CHROMA_MAX; must keep the ladder tests). |

Not taken: single-palette re-render; activity-only push; all-hex or all-ANSI prompts; keeping the store
rows or the pong scripts.

## Review batch (R1–R10, R12; R11 moot with the ping)

See the page §2. Grouped: shell hooks R1 R7 R8 R9 R12 → `fix/theme-scripts`; Java R2 R3 R4 R10 →
`fix/theme-java`; tlstore R5 R6 → `fix/theme-tlstore`.

## Build plan

| # | Branch | Delivers | Depends on | Gate |
|---|---|---|---|---|
| 0a | `fix/theme-scripts` | R1 R7 R8 R9 R12, check.sh combined case | — | `scripts/theme-templates/check.sh` |
| 0b | `fix/theme-java` | R2 R3 R4 R10, one export executor, atomic writes | — | `:app:testDebugUnitTest` |
| 0c | `fix/theme-tlstore` | R5 R6, config.fish repin | — | `scripts/tlstore/test.sh` |
| 1 | `feat/scheme-ping` | mode 2031, `?996n`, OSC 4 `?`, unsolicited report on bg class flip | — | `:terminal-emulator:testDebugUnitTest` |
| 2 | `feat/dual-palette` | both palettes derived per pass (two configuration contexts), dual exports, real renderer modes, D8 warm neutral, D2 service push, ping after files | 0b, 1 | scheme + renderer + app tests |
| 3 | `feat/dual-templates` | nvim (palette module with both tables, colourscheme picks by `vim.o.background`, re-applies on `OptionSet background`, spec stops setting `background`), herdr (auto_switch + subtables), starship/omp (D3 shape), fish (D5) | 0a, 2 | check.sh; Waydroid flip |
| 4 | pong round | templates on, D7/D8 scripts retired, flip the phone: terminal, keyboard, nvim buffer, herdr sidebar, both prompts; screenshots measured | 1–3 merged | user finger test |

Merge each branch into dev as it lands, full suite once on the merged state, one build, one install.
