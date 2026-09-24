# tlstore Revision 6 — the streamline round

Status: agreed design (2026-09-24, decisions D1–D7 and E1–E4 taken on the review page
`.lavish/tlstore-streamline.html`), build starting. Supersedes Revision 5's screens, hero, item
spread rules (`item-spread-rules.md` is retired by this document) and motion timeline. The engine,
launcher hooks, TSV contract and progress stream of Revision 5 stay as they are unless a section
below changes them.

## Decisions

| # | Decision |
|---|---|
| D1 | The UI stays in Rust. A dev-only PNG preview renderer is built first so review pages carry real frames. |
| D2 | Three screens: **Front**, **Item**, **Installing**. Updates becomes a filter on Front; the "starring needs gh" screen becomes a one-line notice. |
| D3 | Front's header shows the item under the cursor. On touch, a tap on a row moves the cursor there; a tap on the header, or a second tap on the row, opens it. |
| D4 | No category chips. The category is a small tag after the name. |
| D5 | The Item page is the upstream README rendered under the rules below, plus our one standfirst line and the facts strip. A per-item `readme-skip` field drops named sections. |
| D6 | With 40 rows or more, list rows are two lines (name, standfirst) with a blank between items. |
| D7 | Motion: leave 120 ms, body rows enter 30 ms apart, settled by 300 ms; the header never moves; the header picture swaps after the cursor has rested 150 ms; input is never dropped. |
| E1 | The header name is the script face (Pinyon) as a picture, three rows tall. The typed name stays in the row and the facts strip. |
| E2 | The cursor is a rounded tonal pill drawn under the row as an RGBA picture; the name under it is bold. |
| E3 | Header pictures fade into the wallpaper along their bottom edge (alpha gradient) and letterbox with transparent pixels. |
| E4 | Installing: shared header, 3× count-up percentage, the steps beside it with the live one dotted, a drawn progress line. |

Gone from Revision 5: tagline and "goodies" script word, breadcrumb, hairline rule, star rule and ☆,
facts box, "What it does" / "Try it" / "Good to know", chips, dot leaders, the Updates and NoGh views,
the 820 ms Flow choreography, `does1..3`, `try` and `notes` as displayed fields (the columns stay in
the TSV until the next catalog cut; nothing reads them).

## Where each terminal feature is used (one job each)

| Feature | Use |
|---|---|
| Script face as a picture | The item name in the header, every screen. |
| Curly underline, accent (SGR 4:3 + 58) | The upstream repo link in the masthead. |
| Dotted underline, accent | Links inside the README; the live step on Installing. |
| Double underline, accent | Names of rows selected with `␣`. |
| Dashed underline, rule colour | README H3 headings. |
| Strikethrough (SGR 9) | The old version in the facts strip when an update exists. |
| OSC 66 whole scales | Name at 3× when pictures are unavailable; README H2 at 2×; Installing percentage 3× with a 2× `%`; Tall list numerals at 2× over both rows of an item. |
| OSC 66 fractional scale | Category tags at 2/3, baseline aligned. |
| RGBA pictures we draw | Header picture bottom fade; the cursor pill (z below text); the Installing progress line. |

Nothing else is decorated. The launcher's emulator supports all of the above (checked:
`TextStyle.UNDERLINE_STYLE_*` + SGR 58, `CHARACTER_ATTRIBUTE_STRIKETHROUGH`, `KittyTextSizing`
numerator/denominator, `KittyGraphicsProtocol` format 32).

## The header (every screen, same rows)

Gutter: 2 columns each side (1 under 44 columns). Content columns at 53 wide: 2–50, 49 columns.

| Row | Content |
|---|---|
| 0 | Masthead. Front: `tlstore` in two rows of half-block letters at the gutter, as dawn draws its name (rows 0–1; tap: home; plain bold `tlstore` in one row where the header has no blank row or no room), and, right-aligned, `↑ N updates` in the accent when N > 0 (tap: the updates filter) with `N items · M installed` dim on row 1 under it; with no updates the counts sit on row 0, level with the mark's top. Item and Installing: `‹ apps` at the gutter (`‹` accent, `apps` dim; tap: back) and the upstream `owner/repo ↗` right-aligned, accent, curly underline, OSC 8 link; a setup shows `our setup` dim instead. |
| 1 | Blank. |
| 2 … 1+P | Picture, P rows, fitted to the full 49-column width (cropped evenly top and bottom when taller than P rows; Revision 6 round 3, was contain-fit), bottom 38 % faded to alpha 0. P = spare rows after the fixed rows, clamped to at most 12 and to the picture's aspect height; under 4 the picture is not drawn and its rows go to the body. The catalog picture on Front; the README's first image on Item and Installing (falling back to the catalog picture). Fetched through `tlstore picture` / `tlstore readme-asset` off the draw path; nothing is drawn where a picture has not arrived. |
| 2+P | Blank (only when a picture is drawn). |
| next 3 | Name: the script face rasterised at exactly 3 × cell height pixels in the ink colour, left at the gutter. If wider than 49 columns, or pictures are unavailable, mono OSC 66 scale 3 (scale 2 under 44 columns or if 3 does not fit). |
| next | Standfirst, dim italic, `fit_line` to the content width. |
| next | Facts strip, dim: `[installed \| installing \| updating \| removing] <version>` or plain `<version>`; then `· <licence>`, `· <author>`, `· <size>`, `· starred` as known. With an update: `<old>` struck through, ` → <new>`. |
| next | Blank. |
| … | Body (per screen). |
| last−1 | Notice row (blank unless a notice or the selection bar is showing). |
| last | Key row. |

Compact (under 26 rows): no picture, no blank after it; name two rows tall (script at 2 × cell
height, or mono scale 2). Fixed rows at Base: Front 11 + body, Item 11 + body, Installing 11 + body.
At 53×26 that gives Front an 8-row picture over 7 single-line rows; at 53×40 a 9-row picture over
seven two-line items. Header geometry is one pure function in `layout.rs`, tested at 53×26, 53×40,
40×24 and 44×30.

## Front

- Rows, Base tier: `NN` dim at the gutter, name at gutter+4 (bold under the cursor), the category
  tag one space after at OSC 66 2/3 scale (plain small text when sizing is unsupported; dropped
  under 44 columns), status right-aligned: `↑ <new>` and `new` in the accent, `installed` dim,
  nothing otherwise. During a job the status is the step word in the accent: `fetching…`,
  `checking…`, `placing…`, `ready`; `failed` bold on failure.
- Rows, Tall tier (40+ rows): three rows per item — numeral at 2× in the rule colour spanning the
  first two rows at the gutter, name and tag at gutter+5 on the first row, standfirst dim italic
  on the second, the third blank. Fewer than 40 rows but more than the Base list needs: extra rows
  go to the picture (up to its cap), then stay blank before the notice row.
- Paging when the rows do not fit: existing pager (`‹ ● ○ ›`) on the last body row.
- Cursor: a tonal pill (accent at 18 % alpha over the surface, corner radius half a cell height)
  drawn as one RGBA picture spanning columns 1 to cols−2 and the item's rows, z = −1, one fixed
  placement id, moved by re-placement. Without pictures: an accent bar in column 0.
- `␣` selects: double underline in the accent under the name; the notice row shows
  `N selected · i installs them, ␣ clears` (or `r removes them` when all selected are installed);
  the verb slot follows.
- Updates filter: `u` or the masthead item shows only items with an update; the masthead reads
  `updates · all` (tap `all` or `esc`: back to all); `u` again updates every shown item
  (→ Installing).
- `s` with gh not ready: notice row `starring needs gh: pkg install gh, then gh auth login`
  until the next key. Otherwise stars silently and the facts strip gains `· starred`.
- Keys: slots `⏎ open` · `i install` / `r remove` / `u update` · `␣ select` · `f keyboard` · `q quit`.

## Item

The whole page (header included) scrolls by rows: wheel, finger drag (already SGR), `↑`/`↓` one
row, `PgUp`/`PgDn` a body height, `Home`. Body = the README rendered under these rules:

| Markdown | Rendered as |
|---|---|
| Everything before the first H2 | Dropped, except images: the first becomes the header picture. |
| Sections titled Installation, Install, Installing, Building, Build, Requirements, Contributing, Contributors, License, Licence, Changelog, Sponsors, Sponsoring, Acknowledgements, Acknowledgments, Star history, and any heading listed in the item's `readme-skip` | Dropped with their content up to the next heading of the same or higher level. Matching is case-insensitive on the heading text with trailing punctuation removed. |
| H1 | Dropped. |
| H2 | OSC 66 scale 2, bold, ink; one blank row above. Plain bold when sizing is unsupported. |
| H3 and deeper | Upper-case, bold, dim, dashed underline in the rule colour. |
| Paragraph | Wrapped at the content width on word boundaries. Bold, italic, inline code (tonal background) kept. |
| Link | Accent, dotted underline, OSC 8 target, tap region. A bare URL shows its host. |
| Image (png, jpg) | Inline, contain-fit, at most 6 rows, transparent letterbox, fetched lazily as it scrolls into view via `tlstore readme-asset`; a one-row dim `[picture]` stand-in until it arrives; nothing when it fails. SVG and shields/badge URLs skipped. GIF: a link with the file name. |
| List | `•` or `1.` with a two-column hang; task items `☐` / `☑`; one nesting level, deeper items flattened. |
| Code block | Tonal background block, wrapped, no highlighting; over 12 lines cut with `… on GitHub` (link). |
| Table | Rendered with a hairline under the header when the columns fit the content width; otherwise one dim line `table: N columns, on GitHub` (link). |
| Blockquote | Dim italic with `▎` in the rule colour at the gutter. |
| Raw HTML | `<img>` → image, `<h1>`–`<h3>` → headings, `<details>` dropped, `<br>` → line break, everything else stripped to its text. `<picture>`/`<source>` → the `<img>` inside. |
| Horizontal rule | One blank row. |
| Length | The first 400 rendered rows, then `read the rest on GitHub` (link). |

Offline with no cached README, or no upstream: header plus one dim line `read about it on GitHub`
(link) — or for a setup, the standfirst alone.

Keys: `i install` / `r remove` · `u update` (empty slot when none) · `s star` (dim until gh works;
absent for setups) · `o repo` · `esc back`.

## Installing

Header of the item in progress (facts strip verb: installing/updating/removing). Body, from the
first body row b:

- `NN` at 3× in the accent at the gutter over rows b–b+2; `%` at 2× dim at gutter+6 on row b+1.
  The number counts up to each new percentage from what is shown, 200 + 6·|Δ| ms (≤ 700), out easing.
- Steps at gutter+12, rows b…b+3: `fetched`, `signature checked`, `putting files in place`,
  `ready`. Done steps dim, the live step accent with a dotted underline, pending steps rule colour.
- Row b+5: progress line, an RGBA picture 49 columns wide and about a sixth of a cell tall, rounded
  ends, track in the rule colour at 50 % alpha, fill in the accent with a 2-px soft glow. Redrawn
  only when the percentage target changes (at most a handful of uploads per item), never per frame.
- Row b+6: `also bringing <parts>` dim italic, or `then <next names>` when more items are queued.
- Finished: steps replaced by the job summary sentences (existing `Job::summary`); the number reads
  `100`; keys become `⏎ done` · … · `esc back`. Failure: `failed` in the facts strip and the message
  in the notice row.
- Keys while running: `x cancel` · (empty) · (empty) · (empty) · `esc back` (the job continues; the
  Front row shows its step word; the finish notice to the phone stays as today).

## Key row: fixed slots

Five slots at columns 2, 12, 24, 34, 44 (at 53 columns; under 44 columns the slots are 1, 8, 16,
24, 32 and words are `fit_line`d to 7). A slot's position never changes; its content may
(`i install` / `r remove` / `u update` share slot 2 on Front and slot 1 on Item). An unavailable
hint stays in place, dim. Tapping a slot sends its key.

## Motion (D7)

- Leave: body elements fade alpha 1 → 0 over 120 ms (`ease::out`). The header, notice and keys stay.
- Enter: body element i fades 0 → 1 over 200 ms starting at min(30·i, 100) ms; everything is at
  rest by 300 ms. Header text (name picture, standfirst, facts) swaps instantly with its item.
  The header picture is placed only when its item has been under the cursor for 150 ms and the
  picture is loaded; it is never moved or cropped by motion.
- Every input event is handled by the current view the frame it arrives, during Leaving too.
- `TLSTORE_MOTION=0` keeps everything at rest, as today.

## Preview renderer (D1, first)

`tlstore-ui --shot <cols>x<rows> --screen <spec> --out <file.png> [--store <dir>]`, behind the
cargo feature `shot` (never enabled by `build-ui.sh`, so the shipped binary does not carry the
font). `<spec>`: `front[:cursor]`, `item:<name>[:scroll]`, `installing:<name>:<pct>`,
`front:selected=<a,b>`, `front:updates`. `--store` points at a fixture like
`tests/fixtures/store` (default). It runs the real Router with `Caps::all()` and a fixed cell size
(12×26 px), takes the resting frame (motion off), and paints it: a dark surface, every cell's
glyph from a bundled OFL mono face (JetBrains Mono, under `assets/fonts/`) with bold/italic/dim,
underline styles and colours, strikethrough, sized runs at their scale and fraction, and every
kitty placement composited in z order with its alpha. `--shot-all --out <dir>` writes the
standard set (front, item:dawn, installing:dawn:64 at 53×26, 53×40, 40×24). One test checks a
PNG is produced with ink where the name is. About 300 lines in `src/shot.rs`; `render::Frame`
and the `Renderer` may need read access to cells and placements, nothing more.

## Engine (P1)

- `tlstore readme <name>`: prints the path of the cached README for the item, fetching
  `https://raw.githubusercontent.com/<upstream>/<ref>/README.md` when the cache is missing or
  older than a day. `<ref>` is the commit in a `+<hash>` version suffix if present, else
  `v<version>` when the version is `x.y.z`-like, else `HEAD`; a 404 on the tag falls back to
  `HEAD`. Cache: `$CACHE_DIR/readme/<name>-<version>.md`. Exit 1 with one stderr line when nothing
  can be fetched and nothing is cached; exit 2 for an item with no upstream (setups).
- `tlstore readme-asset <name> <src>`: downloads an image referenced by the README into
  `$CACHE_DIR/readme/<name>/` and prints its path. `<src>` may be relative (resolved against the
  raw base of the README's `<ref>`) or an absolute `https://` URL on `github.com`,
  `raw.githubusercontent.com`, `user-images.githubusercontent.com`, `*.githubusercontent.com` or
  `github.io`; anything else exits 1. 5 MB cap. Same freshness rule as the README.
- `items.tsv` / `catalog.tsv` gain one column at the end, `readme-skip`: `|`-separated heading
  texts, `-` when none (dawn: `Portability`). `info --tsv` prints it as `Readme-skip` when set.
  Parts carry `-`. `build-catalog.sh` and `test.sh` follow; the catalog is re-signed at merge.
- `tests/fixtures/store` in the crate gets stub `readme` and `readme-asset` behaviour and a
  fixture README per item (short, exercising every rule row above).

## Pinned content (addendum, engine side)

`items.tsv`/`catalog.tsv` gain three more columns at the end, after `readme-skip`: `readme`,
`readme-digest`, `demo-digest`. `-` in `readme` (every item today) keeps `tlstore readme`'s
Revision 5 behaviour — fetch the upstream README, cache it a day, fall back to `HEAD` when the
version's tag is not there. A `launcher:`/`binaries:` source in `readme` instead makes `tlstore
readme` fetch, digest-verify against `readme-digest` and cache that pinned copy — the exact
convention `tlstore picture` already uses for `picture`/`picture-digest` — and upstream is never
consulted for that item again. A digest mismatch is a hard failure, not a silent fall back to
upstream, the same as a picture whose digest does not match is never silently served from
somewhere else. `demo-digest` closes the one gap Revision 5 left: `tlstore picture <name> demo`
now verifies the demo the same way the cover picture always was.

A `binaries:` source may now name a path instead of a bare asset —
`binaries:<path-with-slash>@<tag>` resolves to `$BINARIES_RAW/<tag>/<path>` (a file at that exact
place in the binaries repository, e.g. a pinned `readme/dawn.md` or a hero `hero/sigye.png`) —
while a bare `binaries:<asset>@<tag>` keeps resolving to the per-processor
`$BINARIES_RAW/<tag>/bin/<asset>-aarch64` it always did. `build-catalog.sh` looks a path asset up
in the binaries repository's `SHA256SUMS` by that exact repo-relative path; a bare asset keeps
being looked up as `<asset>-aarch64`.

`scripts/tlstore/make-hero.sh <video|gif> <out.png>` turns a short clip into the looping APNG a
pinned hero picture is: 4 seconds, 12 fps, 600 px wide, full frames (ffmpeg's apng encoder has no
delta/blend-region option to begin with, so nothing here relies on the partial-frame blend ops
some APNG decoders do not support).

See `docs/agents/tlstore-catalog.md`, "Pinning a readme or a hero picture", for the workflow:
write the trimmed readme, make the hero, commit both to the binaries repository under
`readme/<name>.md` / `hero/<name>.png` with a `SHA256SUMS` line each, point `items.tsv` at them,
rebuild, test, sign.

## Launcher (P3)

- G2: `launcherctl window open --title <t>` must show `<t>` on the window's chip; today it shows
  `home`. Find where the title is set versus where the chip reads its label, fix, and test.
- G3: when the last pane of a window closes, the window closes with it (unless it is the last
  window, which becomes an empty home as today). tlstore-ui quitting must leave no `home` chip
  behind. Unit-test the policy where the pane/window model is pure.

## Build plan

| Phase | Branch / worktree | Deliverable | Depends on |
|---|---|---|---|
| P0 shot | `feat/tlstore-shot` · `../tl-wt-tlstore-shot` | the preview renderer, PNGs of today's screens for the review page | — |
| P1 readme | `feat/tlstore-readme` · `../tl-wt-tlstore-readme` | `readme`, `readme-asset`, `readme-skip`, fixtures, `test.sh` green | — |
| P2 screens | `feat/tlstore-two-screens` · `../tl-wt-tlstore-screens` | header, Front, Item (markdown renderer), Installing, key slots, motion, cargo tests and snapshots; `CONTRACT.md` rewritten | — (readme via fixtures) |
| P3 window | `feat/tlstore-window-close` · `../tl-wt-tlstore-window` | G2, G3 with tests | — |
| merge | dev | P1 → P0 → P2 → P3; rebuild binaries once (`build-ui.sh --install`), re-sign catalog, `docs/en/Tlstore.md`, `CONTEXT.md` terms (Header, Facts strip, Front) | all |
| gate | — | frames from `--shot-all` on the review page; then one pong install with the developer's go | merge |

Rules for the workers: never commit rebuilt `tlstore-ui-<abi>` assets (the merge does that once);
never run `./gradlew --stop`; a compile error in a file you did not touch is someone else's
in-flight edit — retry, do not fix it. Markdown parsing: `pulldown-cmark` with default features
off is fine (static, no_std-friendly); keep the release binary under 1.5 MB per ABI.
