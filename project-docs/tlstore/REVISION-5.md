# tlstore Revision 5 — tlstore-ui

Status: agreed design (2026-09-23), build not started. Supersedes SPEC.md Revision 3's "fzf browser, not
a compiled TUI". Design: https://claude.ai/artifact/NwnkCF8w2Dzmhw3cAxLjX2, page **Unified flow** (the
system) and page **Broadsheet** (every screen, the smaller-terminal layouts, the item spread rules).
Decisions page: `.lavish/tlstore-redesign.html`. Item page copy rules: `item-spread-rules.md`.

## What the person sees

Running `tlstore` (or `tl`, `tls`) with no arguments inside the launcher opens a full-screen store drawn
by the terminal itself: real text, text sizing (OSC 66), kitty pictures under and between the text, SGR
mouse so every line, chip and key hint is tappable. Everywhere else (official Termux, ssh, tmux) the same
command prints the item list and one line on how to install; there is no browser there.

Every screen shares one structure:

- **Masthead line** — small pixel TLSTORE mark (tap: home), a breadcrumb (`/ apps`, `/ apps / kitten`,
  `/ updates`, `/ installing`), one context item on the right (`↑ N updates` only when N > 0, `★ starred`,
  `1 of 2`). It never moves between screens.
- **Hairline rule.**
- **Hero** at the same rows on every screen: a spaced lead line over one script word, drawn as a picture
  (default tagline TERMINAL / *goodies*; item pages NO. NN · CATEGORY / *name*).
- **Content**, then the **key row**.

Screens: **Apps** (cover picture of the featured item, category chips All / Note taking / Tools / AI, the
list with number · name · CATEGORY tag · dot leaders · status, paging when it does not fit, `␣` multi-select
for install/remove), **Item** (written by us from the item spread rules; never the upstream README),
**Updates** (list first, update one or all), **Installing** (counting number, dot bar, steps, a phone
notice when it finishes in the background), **Starring needs gh** (a footnote: install gh and run
`gh auth login` yourself, or open the repo).

Stars: `s` stars the item's upstream through `gh`, silently when gh is signed in. Marks stay hidden until gh
works. Setups (fish-shell) are never starred, nor the programs they bring. No `tlstore star` command.

Colour: Material tones taken from the wallpaper palette the launcher already exports; colour stays sparse
(cursor, selection, new versions, key letters). Light and dark follow the terminal.

Layout follows the live grid on every resize: 40+ rows full cover; 28–39 a picture strip; under 28 (keyboard
open) no cover, one-line hero, `f fullscreen` hint; under 44 columns category tags drop. `f` asks the
launcher to put its in-app keyboard away while tlstore is open; it returns on `esc` or a tap on the terminal.

Motion (design page note has the table): 160 ms leave, breadcrumb decodes from ░▒▓ glyphs, rule draws,
hero script rises out of a mask on a spring, picture wipes down, rows arrive 45 ms apart with leaders
drawing out, install number counts up. Every frame is one synchronized update (mode 2026). A setting turns
motion off (`TLSTORE_MOTION=0`, or the launcher's animations-off).

## Shape

- **`tlstore` (shell script) stays the engine**: catalog, signatures, install, update, remove. It gains:
  the new catalog fields (below) in `info --tsv`/`list --tsv`; a machine progress stream for install/update
  (`--progress`: one `step\t<name>\t<pct>` line per change); bare `tlstore` execs `tlstore-ui` when the host
  is `launcher` and stdin/stdout are a tty, otherwise prints the list. `tlstore browse` and all fzf code go.
- **`tlstore-ui` (new, Rust, one static binary per ABI, shipped in the APK)** draws the screens and calls
  the script for every action. It never installs anything itself. Modules: terminal (raw mode, SGR mouse,
  2026/2048 modes, capability probe), renderer (cell diff + OSC 66 runs + kitty placements), pictures
  (script words rasterised from a bundled OFL script face and cached; pixel mark; catalog pictures),
  screens, motion (timeline + spring), actions (script, `gh`, launcher keyboard).
- **Launcher**: `TlstoreInstaller` also puts `tlstore-ui` in `libexec`; a keyboard hide/show request the
  store can make (a `launcherctl` route or an escape; the phase decides after reading the code).

## Catalog fields (items.tsv → catalog.tsv, signed as today)

`category` (one of Note taking, Tools, AI), `upstream` (owner/repo, empty for setups), `setup` (bool),
`standfirst`, `does1..3`, `try`, `notes` (0–2, `|`-separated), `author`, `licence`, `size`, `picture`
(signed digest + path in termux-launcher-binaries), `demo` (optional), `featured` (bool, one item).
Rules for each: `item-spread-rules.md`.

## Build plan

| Phase | Branch | Deliverable | Depends on |
|---|---|---|---|
| P1 engine | `feat/tlstore-r5-engine` | catalog fields + copy for the 7 items, `--tsv` rows, `--progress`, bare-command routing, fzf/browse removed, script tests green | — |
| P2 launcher | `feat/tlstore-r5-launcher` | ships `tlstore-ui` from the APK, keyboard hide/show for the store, installer tests green | — |
| P3 ui core | `feat/tlstore-ui-core` | Rust crate: terminal, renderer, pictures, input, layout tiers; contract (≤ 40 lines) for P4 | — |
| P4 screens | `feat/tlstore-ui-screens` | the five screens wired to the script and gh | P1, P3 |
| P5 motion | `feat/tlstore-ui-motion` | the transitions, motion-off setting | P4 |
| P6 ship | `feat/tlstore-r5-ship` | ABI builds into the APK, docs (docs/en/Tlstore.md), Waydroid + pong gates | P2, P5 |

Gates per phase: `scripts/tlstore/test.sh` (P1), app unit tests incl. `TlstoreInstallerTest` (P2),
`cargo test` (P3–P5), Waydroid screenshots at full / keyboard-open / zoomed sizes (P4–P6), pong install
only with the user's go.

## Decisions for the build (2026-09-23)

- B1 A: pure terminal; the web inset is dropped (ADR 0002 rewritten to say so).
- B2 A: rustup and the Android Rust targets are installed on the dev machine; the phone build is local.
- B3 A: P1–P3 run now; what follows is decided after they land (account budget).
- B4 B: pictures are the upstream GitHub hero images or screenshots; where none exist we take our own.

## TSV contract, Revision 5

`catalog.tsv` (and `items.tsv`, minus the two digest columns) has the ten columns it always
had — `name kind version prefixes source digest target requires options summary` — plus, in this
order, `category upstream setup standfirst does1 does2 does3 try notes author licence size picture
picture-digest demo featured`. New columns only ever land at the end: the tlstore already on a
phone reads columns 1–10 by fixed position and requires at least 10 (`rows()`'s `NF < 10` gate), so
it keeps working against this catalog unchanged — proved by `scripts/tlstore/test.sh`'s "the
dev-branch tlstore reads the Revision 5 catalog" case, which runs the pre-Revision-5 script
(`git show dev:...`) against a Revision 5 fixture. `picture` is a `launcher:` source exactly like
any other file source (see `config-fish` in `items.tsv`); `picture-digest` is its digest, computed
by `build-catalog.sh` the same way the item's own `source`/`digest` pair is. A hidden item (a part)
carries `-` in every one of these columns and `0` in `setup`/`featured`.

tlstore-ui reads all of it from two commands:

- `tlstore info --tsv <name>` — the existing `Kind`/`Version`/.../`Summary` lines, then one more
  line per Revision 5 field whose value is not `-` (`Setup` and `Featured` always print, since `0`
  is itself the answer): `Category`, `Upstream`, `Setup`, `Standfirst`, `Does1`, `Does2`, `Does3`,
  `Try`, `Notes` (still `|`-separated), `Author`, `Licence`, `Size`, `Picture`, `Picture-digest`
  (only alongside `Picture`), `Demo`, `Featured`.
- `tlstore list --tsv` / `tlstore search --tsv <term>` — the existing six columns (`name state
  version installed kind summary`) plus `category` and `featured`, the two the Apps screen needs
  for every row without an `info` call each.

## Progress stream, Revision 5

`tlstore install`, `update` and `remove` take `--progress`, which implies `-y` and reads/writes
nothing a person would (human text that would otherwise go to stdout goes to stderr instead in this
mode). A config file question is never asked in `--progress`: the user's file is always kept, and
that shows up in the item's `done` line. stdout carries only:

```
step<TAB><item><TAB><percent 0-100><TAB><step words>
```

one or more times per item, its words being `fetched`, `signature checked`, `putting files in
place`, `ready` in that order (the same four words for install, update and remove, so tlstore-ui
has one vocabulary to animate), then exactly one line closing that item out:

```
done<TAB><item><TAB>ok|failed<TAB><message>
```

The steps are coarse milestones around the whole per-item operation, not a byte-level download
progress (tlstore has no hook into curl for that); P4 should treat the percentages as a small,
fixed sequence to animate through rather than a literal transfer fraction.
