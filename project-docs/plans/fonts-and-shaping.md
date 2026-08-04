# Fonts and cluster-aware shaping

Status: the bounded fixed-cell font project is complete. Four-face configuration, Canvas shaping,
the fixed-cell grapheme model, explicit symbol maps, ligature policy, OpenType features, variable
axes, and configurable metrics were delivered and device-verified 2026-07-28.

A second batch landed 2026-08-03 and is recorded from
[`fonts.d` autoload](#delivered-fontsd-autoload) onwards: `~/.termux/fonts.d/*.conf` drop-ins, named
symbol maps, an ordered `fallback_font` chain, geometric box/block/braille/Powerline rendering, and
the in-app font picker that writes `~/.termux/fonts.d/10-launcher.conf`. Per-map features and axes
were carried through the render path on 2026-08-04, closing the one gap the batch had left open. What
the batch deliberately did not do is in
[Not delivered in the 2026-08-03 batch](#not-delivered-in-the-2026-08-03-batch).

User configuration, examples, reload behavior, and troubleshooting are in
[`../../docs/en/Terminal_Modernization.md`](../../docs/en/Terminal_Modernization.md). The engineering
status map is [`../terminal-modernization-status.md`](../terminal-modernization-status.md).

This is the remaining Phase 4 project from the Kitty feasibility study. It extends Termux's native
`~/.termux/font.ttf` contract instead of replacing it, and keeps the Canvas renderer unless device
tests prove that Android's shaping APIs cannot satisfy the cluster contract.

## Configuration decision

Use a separate, line-oriented `~/.termux/fonts.conf`. `termux.properties` is intentionally not used:
font and symbol mappings need repeatable keys and Unicode ranges, while Java properties collapse a
key to one value. The loader will reuse the existing Termux property/styling plumbing for discovery,
`termux-reload-settings`, lifecycle reload, logging, and user-visible parse errors. It must not add a
second filesystem watcher.

Paths are the primary selection mechanism because Android users normally install Nerd Fonts and
other terminal fonts as files rather than system families. A family name is a convenience when
`Typeface.create()` can resolve it. The grammar will make the distinction explicit rather than
guessing whether a missing path was intended as a family. The initial face syntax is:

```text
font_family path=~/.termux/font.ttf
bold_font path=~/.termux/font-bold.ttf
italic_font path=~/.termux/font-italic.ttf
bold_italic_font path=~/.termux/font-bold-italic.ttf

# Optional Android family lookup:
# bold_font family="Roboto Mono"
```

With no `fonts.conf`, `font.ttf` remains the regular face, `font-italic.ttf` remains the optional
italic face, and Android monospace remains the final default. Existing Termux:Styling and manual
font replacement workflows therefore retain their current behavior.

Repeatable symbol maps use Kitty's range syntax:

```text
symbol_map U+E000-U+F8FF path=~/.termux/fonts/SymbolsNerdFontMono.ttf
symbol_map U+E0A0-U+E0D7,U+F0001 family="Symbols Nerd Font Mono"
disable_ligatures cursor
font_features regular +zero -liga cv01=2
font_features symbols +ss01
font_variations regular wght=425 wdth=92.5
modify_font cell_width 90%
modify_font cell_height 2px
modify_font baseline 1px
modify_font underline_thickness 150%
modify_font strikethrough_position -1px
```

An explicit map selects its font for the range. Unmapped clusters use the configured primary face
and Android's normal platform fallback. Family-backed mappings are allowed but are not a substitute
for path-backed mappings.

## Implementation order

1. **Four faces first — complete.** Load regular, bold, italic, and bold-italic independently and teach
   `TerminalRenderer` to select the real face for each SGR combination. Synthetic bold/italic is
   used only when its face is absent. This is independent of the grapheme work and is the first
   visible delivery.
2. **Grapheme and shaping test suite — complete for the fixed-cell model.** Cover Arabic shaping without promising bidi layout, Indic
   conjuncts, combining marks, ZWJ emoji, Nerd symbols, programming ligatures, cursor boundaries,
   selection boundaries, reflow, and styled run boundaries.
3. **Canvas shaping experiment — complete; retain Canvas.** `Paint.getTextRunAdvances()` is available below the app's API 26
   minimum and exposes per-character advances/cluster continuations. Test it against the suite and
   use the result to map Android-shaped clusters deterministically back to terminal cells. Port
   HarfBuzz only if those acceptance tests fail on supported Android versions/devices.
4. **`symbol_map` and fallback ranges — complete.** Select fonts per complete grapheme, never in the middle of
   a cluster; retain Android fallback after explicit mappings and primary faces.
5. **Ligature policy — complete.** Support `never`, `cursor`, and `always`. The renderer already breaks at the
   cursor for inversion; cursor-only disabling should use that boundary and a temporary feature
   setting rather than reshape unrelated cells.
6. **OpenType features — complete.** `Paint.setFontFeatureSettings()` exists below minSdk 26. Translate the
   user-facing feature syntax to Android's feature-settings form, scope settings to a face/run, and
   restore `Paint` state after every draw.
7. **Variable axes — complete.** `Paint.setFontVariationSettings()` is available at minSdk 26. Validate axes,
   keep settings face-specific, and degrade to the unmodified face if Android rejects them.
8. **Metrics — complete.** Add bounded cell width/height, baseline, underline, and strikethrough
   adjustments only after shaping and selection geometry are pinned by tests.

## Delivered: four independent faces

`TerminalFontConfig` now parses a bounded `~/.termux/fonts.conf` with explicit `path=` and
`family=` sources. The four supported directives are `font_family`, `bold_font`, `italic_font`, and
`bold_italic_font`; a later duplicate replaces the earlier value. Quoted values, comments, and
`~/` expansion are supported. The parser caps the file at 64 KiB, 512 lines, and 4096 characters
per line.

`TerminalFontLoader` resolves each face independently. Path-backed files are the primary contract;
Android family lookup is best-effort. A missing config retains native Termux compatibility:
`font.ttf`, optional `font-italic.ttf`, then Android monospace. Individual path loads are checked for
readability, emptiness, and a 64 MiB size limit, and Android font-parser failures are caught. Parse
and load failures are logged and summarized in a bounded toast without preventing terminal startup.

`TerminalRenderer` carries all four real faces across size changes and pane creation. Regular-face
metrics continue to own the fixed terminal grid. Styled faces are measured and scaled into that
grid, and synthetic bold or skew is applied only for a missing component. Existing two-face callers
remain source-compatible.

Device verification on a Nothing A065 running Android 16 covered:

- live `termux-reload-settings` application of four deliberately distinct path-backed system fonts;
- visible regular, bold, italic, and bold-italic selection in the same terminal grid;
- a malformed bold font falling back safely while the other real faces remained active; and
- restoration to the legacy no-`fonts.conf` state after the test.

## Canvas shaping decision

The Android instrumentation suite now exercises Arabic in logical-LTR and RTL run directions,
Devanagari conjuncts, multiple combining marks, ZWJ emoji, regional-indicator flags, Nerd Font
private-use characters, and programming-ligature input. It checks ICU extended-grapheme boundaries,
UTF-16 safety, repeatability, finite/non-negative per-character advances, and exact recovery of each
run's total advance by summing grapheme spans.

On the Android 16 device, `Paint.getTextRunAdvances()` exposed the information the renderer needs:
Devanagari and ZWJ sequences formed one grapheme, combining and surrogate continuations had zero
independent advance, and Arabic lam-alef and the platform font's `->` ligature exposed zero-advance
glyph continuations. Disabling `liga` and `calt` changed `->` from `[61, 0]` to `[29, 42]`, and
restoring the `Paint` setting restored the original advances exactly.

Decision: retain Android Canvas and do not port HarfBuzz. ICU supplies grapheme boundaries and
Canvas supplies shaped per-character advances, including glyph-cluster continuations. The next
slice mapped those two signals to fixed terminal-cell spans and added cursor, selection, style-run,
copy, resize, and reflow integration tests. HarfBuzz remains only a fallback if later font-map or
feature work uncovers a Canvas result that cannot be mapped deterministically.

## Delivered: fixed-cell grapheme model

`TerminalRow` now stores the decided display width at each code-point start instead of recomputing
`wcwidth` whenever text is copied or reflowed. This lets a positive-width code point be recorded as
a zero-cell continuation of an existing extended grapheme without changing global width tables.
The metadata moves atomically with UTF-16 text through overwrite, block copy, selection, resize,
and reflow. Regional-indicator pairs are promoted from one to two cells as a bounded special case,
matching their emoji presentation width.

`TerminalEmulator` incrementally asks Android ICU whether the next non-ASCII code point continues
the current grapheme. Ordinary printable ASCII never enters ICU. Tracking resets across explicit
cursor movement, screen switches, resize, and terminal reset, and clusters are capped before they
can grow unbounded. The renderer consumes the stored widths and measures each final styled run with
`getTextRunAdvances()` before scaling it into the regular-face cell grid.

This is not the deferred multicell-text project. A normal Unicode extended grapheme owns the width
of its first base (with the standard two-cell flag promotion); there is no arbitrary scale, height,
following-cell claim, `narrow_symbols`, or Kitty multicell protocol metadata.

Verification now includes 245 passing terminal-emulator JVM tests, the terminal-view unit suite,
eight Android 16 shaping/buffer/renderer instrumentation tests, an APK build, and a live PTY render
smoke test. The device suite proves atomic selection and reflow for ZWJ emoji, flags, Indic
conjuncts, and combining sequences in addition to the Canvas advance fixtures.

## Delivered: explicit symbol maps

`fonts.conf` now accepts repeatable `symbol_map` directives containing comma-separated `U+...`
points and inclusive ranges followed by a `path=` or `family=` source. Later mappings win where
ranges overlap, matching Kitty. Parsing is atomic per line and bounded to 256 directives and 1024
ranges; font loading is bounded to 64 distinct sources and retains the existing 64 MiB per-file
limit and exception boundary.

The loader resolves mapped fonts during the existing settings reload, then applies the complete
face/map set atomically to every pane. The renderer selects a map from the first code point of each
complete grapheme and splits shaped draw runs at that boundary. Continuations can never select a
different font, SGR synthesis is not applied to explicit symbol fonts, and all mapped glyphs remain
scaled into the primary face's fixed cell grid. Unmapped text still uses the selected primary face
and Android's normal fallback.

Android 16 instrumentation covers family loading, later-overlap precedence, and first-code-point
grapheme ownership. A live reload also loaded a path-backed Noto Serif copy from
`~/.termux/fonts/`, kept the foreground activity and process healthy, emitted no font errors, and
restored the original absent-config state after the test.

## Delivered: ligature policy

`disable_ligatures` accepts Kitty's `never`, `cursor`, and `always` values, defaults to `never`, and
uses last-value-wins reload semantics. It controls the programming-ligature `calt` feature rather
than changing terminal cells or disabling general-script shaping. `cursor` adds `-calt` only to the
visible cursor run; `always` adds it to every text run.

The renderer preserves any existing feature string, applies the temporary `-calt` override for
both advance measurement and drawing, and restores the previous `Paint` setting in a `finally`
boundary. Policy state survives text-size changes and is replaced atomically with faces and symbol
maps during the existing settings reload.

Pure policy tests cover every mode/cursor combination. Android 16 verifies that `-calt` alone
breaks the platform programming-ligature probe, that restoring `Paint` restores identical shaping,
and that cursor mode leaves runs away from the cursor unchanged. A live `disable_ligatures always`
reload kept the foreground process healthy and the absent-config state was restored afterward.

## Delivered: OpenType features

`font_features` targets `regular`, `bold`, `italic`, `bold_italic`, or `symbols`, followed by one or
more Kitty/HarfBuzz-style feature tokens. `+zero`, `-liga`, and `cv01=2` translate to Android's
`'zero' 1, 'liga' 0, 'cv01' 2` form. Commas and whitespace are both accepted, `none` clears a prior
target, a later directive replaces the target, and each target is bounded to 32 validated
four-character tags with values from 0 through 65535.

Face aliases are deliberate: Android does not reliably expose the PostScript name Kitty uses to
target features after a font is loaded from an arbitrary path. Aliases remain deterministic for
the four configured SGR faces, while `symbols` covers all explicit mapped-font runs. A setting is
applied for both run measurement and drawing, composed with a later temporary `-calt` ligature
override when required, and restored in the same `finally` boundary.

Android 16 accepts the generated setting string, uses `font_features regular -calt` to break the
programming-ligature fixture, and restores the original shaping afterward. A live reload with
separate regular and bold features kept Termux healthy and the test config was removed afterward.

## Delivered: variable font axes

`font_variations` uses the same five deterministic face aliases and accepts comma- or
whitespace-separated four-character `tag=value` axes. Values may be fractional or negative, must
be finite and within a defensive ±1,000,000 bound, and each target is capped at 16 axes. `none`
clears a target and later directives replace earlier ones. The parser emits Android form such as
`'wght' 425, 'wdth' 92.5`.

Every requested setting is probed against the resolved face with
`Paint.setFontVariationSettings()` during the existing off-render-path load. Android accepts some
valid settings on static faces as no-ops, so the loader can report explicit API rejection but does
not claim axis-capability discovery. At render time, settings cover measurement and drawing, are
restored in a `finally` boundary, and any late runtime rejection falls back to the unmodified face.
The primary unvaried face still defines the terminal grid.

Android 16 verification loads path-backed Roboto Flex, accepts `wght` and `wdth`, proves that
weights 100 and 900 produce different glyph pixels inside the same cell geometry, and proves a
malformed direct setting cannot crash rendering. A live reload copied Roboto Flex under
`~/.termux/fonts/`, applied axes, kept the process healthy, and restored the absent-config state.

## Delivered: bounded font metrics

`modify_font` accepts Kitty's seven fixed-cell metric names: `cell_width`, `cell_height`,
`baseline`, `underline_position`, `underline_thickness`, `strikethrough_position`, and
`strikethrough_thickness`. A value ending in `%` replaces the font-derived metric by percentage;
`px` and bare values are additive pixels. Bare values deliberately mean pixels on Android rather
than Kitty's desktop point unit, because this renderer has no separate configurable font DPI.
`none` clears a prior value and later directives replace earlier ones.

Pixel deltas are bounded to -256 through 256 and percentages to 10% through 500%. Final cell width
is clamped to 2–1000 pixels, height to 4–1000 pixels, and decoration thickness and positions remain
inside their owning cell. Positive baseline values raise the glyph and both decorations, while a
positive explicit decoration-position value moves that decoration downward, matching Kitty's
coordinate behavior.

The regular face still defines the starting grid. Adjusted cell dimensions flow through terminal
resize, hit testing, selection, cursor geometry, sixel cells, split panes, and text-size changes.
Underlines and strikethroughs are drawn as bounded geometry instead of relying on Android Paint's
fixed strikethrough metrics, so position and thickness settings affect all runs consistently. The
same existing settings reload parses and atomically applies metrics to every initialized pane.

Android 16 instrumentation compares default and adjusted cell/baseline rendering and independently
checks underline/strikethrough position and thickness geometry. All 17 font, shaping, grapheme,
buffer, and renderer device tests pass. A live reload applied all seven metric directives without a
font error or process restart, then restored the original absent-`fonts.conf` state with the same
Termux PID.

## Delivered: fonts.d autoload

`TerminalFontConfig.load()` now reads `~/.termux/fonts.d/*.conf` before `~/.termux/fonts.conf`.
Drop-ins are ordered by a byte-wise filename comparison rather than a locale collator, so the order
is the same on every device. Because the parser already used last-duplicate-wins, reading the user's
own file last is the whole of the precedence contract: `fonts.conf` beats a drop-in, a drop-in beats
`font.ttf`/Termux:Styling, and a directive the user does not restate keeps the fragment's value.

Selection rules: top-level entries only, `*.conf` suffix matched case-insensitively, regular readable
files only, and an entry is skipped when its canonical parent is not the canonical `fonts.d` — a
symlink cannot pull a file from outside the directory into the terminal's font configuration.

Bounds are split deliberately. The drop-ins share a 256 KiB aggregate budget and a 32-file cap; when
the next fragment would exceed the remaining budget, it and every remaining fragment are skipped with
one bounded error. `fonts.conf` is read outside that budget under its own 64 KiB allowance, so no set
of fragments — hostile, accidental or app-written — can push the user's own file out of the load. Each
file additionally keeps the existing 64 KiB / 512-line / 4,096-character limits.

Diagnostics stay attributable: every error from a fragment is prefixed `fonts.d/<file>: ` before the
line number, and messages for `fonts.conf` are byte-identical to before this change, so existing
troubleshooting text and user muscle memory still apply. `Result.filePresent` is true when either
`fonts.conf` exists or at least one fragment loaded; faces a fragment leaves unset still fall through
to `font.ttf` and Android monospace exactly as with no configuration at all.

JVM coverage: `TerminalFontConfigFilesTest` (ordering, the symlink escape, both bounds, the
prefixing) and `TerminalFontConfigTest`.

## Delivered: named symbol maps

`symbol_map` accepts an optional `name=<ident>` token so `font_features` and `font_variations` can
address one map instead of the shared `symbols` target. `name=` may sit on either side of the ranges
because the parser partitions the line's words rather than reading fixed positions, but only one
`name=` is allowed per line. An identifier is 1 to 32 characters of `[A-Za-z0-9_-]`, stored as written
and matched case-insensitively; the five reserved `FontTarget` words (`regular`, `bold`, `italic`,
`bold_italic`, `symbols`) are rejected as names so a typo cannot silently retarget a face.

Name resolution is deferred to the end of the load, not done per line. A `fonts.d` fragment and
`fonts.conf` may legitimately be written in either order, so a `font_features <name>` line is allowed
to precede the `symbol_map name=<name>` that declares it, even across files. Only after every file is
parsed are still-undeclared names reported and their settings dropped. Named settings are bounded at
256 targets, on top of the existing 256 `symbol_map` directives, 1,024 ranges and 64 distinct font
files.

Unnamed maps are untouched: they keep resolving against the shared `symbols` features and axes, so
every pre-existing config behaves identically.

Per-map settings reach the screen. `TerminalRenderer.SymbolMap` carries `@Nullable features` and
`@Nullable variations` through a new widest constructor; the three-argument constructor delegates with
nulls, so every existing caller is unchanged and an empty string is normalised to null. Per cell the
matched map's own setting wins and falls back to the shared `symbols` slot when the map declares none,
which is exactly what an unnamed map has always got. `disable_ligatures` still appends `'calt' 0` to
the resolved feature string in the same place, so the ligature policy composes with a named map's
features rather than replacing them. Axes are not set on the `Paint`: they ride the run's typeface
through the existing `variationTypeface` cache, keyed by base-typeface identity plus the axis string,
so a named map costs one more cached instance and no per-frame instantiation.

Run segmentation had to change with it. The draw loop used to break a run when the symbol *typeface*
changed, which is not sufficient once settings are per map: two maps may name one font file and
declare different features, and the second map's cells would then have been drawn with the first
map's settings. The break condition now also compares the resolved settings
(`sameSymbolSettings`), so a settings-only difference starts a new run.

`TerminalFontLoader.loadSymbolMaps` passes each spec's features straight through and validates its
axes against the resolved face. A rejected axis is dropped for that map alone — the map still draws,
at the font's default instance — and is reported once. The probe is memoized on face identity plus
label plus axis string, so a map with several ranges, or two maps sharing a face and the same
inherited axes, produce one message rather than one per range. The label follows authorship: the map's
own name when the axes are its own, `symbols` when they were inherited from the shared target, so the
message names the line the user actually wrote. Features are deliberately not validated at the loader:
`Paint.setFontFeatureSettings` has no boolean result to check, and the parser has already bounded and
canonicalised every tag.

JVM coverage: `TerminalFontConfigTest` (name placement on both sides, case folding, the reserved
words, the cross-file forward reference, the undeclared-name error), `TerminalFontLoaderTest` (the
per-map/inherited axis labels, the memoized single report), and `TerminalRendererPolicyTest`
(`symbolSetting` fallback and `sameSymbolSettings` segmentation).

## Delivered: ordered fallback chain

`fallback_font path=…|family=…` is repeatable to 8 entries, and order of appearance is the try order.
It exists to answer the one font complaint no amount of `symbol_map` tuning could: Android
substituting a CJK or emoji face the user never chose, with no way to name a preference.

The effective per-cell precedence is explicit `symbol_map`, then box-drawing synthesis, then the
configured face for the cell's SGR style, then the chain in order, then Android's own platform
fallback. `FallbackFontResolver` owns the decision and is deliberately a separate class in
`terminal-view` with coverage probing behind a `Coverage` interface, so ordering, precedence and
eviction are unit-testable on the JVM with no real `Paint` or `Typeface`.

Cost control was the design constraint. `Paint.hasGlyph` shapes a string, and the render loop asks
per cell, so every answer is memoized in a fixed open-addressed table keyed by code point and SGR
face (512 entries, dropped wholesale at 3/4 load and repopulated on demand). A renderer is rebuilt
whenever the faces or the chain change, so there is no other invalidation path to get wrong. Probing
is on the cluster's base code point only, never a continuation — the same rule `symbol_map` follows,
so combining marks inherit their base's decision.

A chain entry is one face with no declared variants, so the SGR style the primary face would have
shown is synthesized on top of it rather than dropped. Fallback runs are measured and scaled into the
regular face's cell grid like any other run, so a fallback face never changes cell width.

JVM coverage: `FallbackFontResolverTest` (order, primary-face-wins, memo hits, eviction),
`TerminalFontConfigTest` and `TerminalFontLoaderTest` (the 8-entry cap at both layers, unloadable
entries being dropped rather than shifting the chain).

## Delivered: geometric box drawing, blocks, braille and Powerline

`BoxGeometry` computes the ink of a geometric cell from the cell's own integer pixel bounds instead of
shaping a glyph. The motivation is joins, not fidelity: `BoxGeometry.edge()` derives a boundary as
`Math.round(origin + index * cellSize)`, and both cells meeting at that boundary evaluate the same
expression, so cell N's far edge and cell N+1's near edge are the identical integer however the
fractional cell size falls. Frames, block ramps and braille graphs therefore stay seamless at any
cell size, including after `modify_font cell_width`/`cell_height`, where a font's own glyph metrics
leave hairline background gaps.

Three directives control it, all last-value-wins like every other directive:

- `box_drawing synthesize|font`, default `synthesize`. `font` restores glyph rendering for every one
  of these code points exactly as before this batch.
- `box_drawing_scale <thin>,<light>,<heavy>,<very_heavy>`, default `0.001,1,1.5,2`, exactly four
  comma- or space-separated values, each greater than 0 and at most 8. The base stroke is
  `max(1, cellHeight / 16)`; each weight is clamped to at least one pixel and at most a third of the
  cell's smaller dimension so a heavy cross still leaves its cell readable.
- `powerline_symbols font|synthesize`, default `font`, because a patched Nerd Font draws the
  separators the way its author intended.

Synthesized ranges: `U+2500-U+257F` (box drawing), `U+2580-U+259F` (blocks, eighths, quadrants,
shades), `U+25E2-U+25E5` (corner triangles), `U+2800-U+28FF` (braille), `U+1FB00-U+1FB3B` (sextants),
`U+1FB70-U+1FB8F` (legacy eighth blocks and half shades), plus `U+E0B0-U+E0B7` and `U+E0BA-U+E0BD`
under `powerline_symbols synthesize`.

`BoxDrawingPolicy.synthesizes()` gates on the mode first, so `powerline_symbols synthesize` has no
effect while `box_drawing font` is set. That is intentional — `box_drawing font` means "hand the
geometric repertoire back to the font" — and it is documented in the public guide rather than left as
a surprise.

An explicit `symbol_map` covering a code point wins over synthesis in both the glyph pass and the
Kitty multiple-cursors overlay pass, since asking for that font was a deliberate choice. Shades are
emitted as foreground-at-alpha (25/50/75% for `U+2591`-`U+2593`, 50% for the `U+1FB8C`-`U+1FB8F` half
shades) rather than a stipple, so they follow SGR colour, dim, selection and block-cursor inversion
like any other cell. Segments are flat primitive arrays reused across cells, so the synthesis pass
allocates nothing per frame.

JVM coverage: `BoxGeometryTest` (edge sharing between adjacent cells, the claimed and excluded
ranges, weight clamping, braille dot placement) and `TerminalRendererPolicyTest`.

## Delivered: in-app font picker

**Settings → Appearance → Terminal fonts** (`TermuxFontsPreferencesFragment`, reachable as
`fonts.pick`) installs a complete multi-face font with no shell. `fonts.install` performs the same
install headlessly for the palette, keybindings and agents; it is `MEDIUM`/confirmed and requires an
`id`, which keeps it out of the palette's argument-free tool rows the way `app.launch` does.

`app/src/main/assets/fonts/catalog.json` is bundled, so the picker works with no network and no apt
repository, and every `url`/`sha256`/`sizeBytes` in it was verified by downloading the artifact. Seven
families: Maple Mono (recommended, variable `wght` 100-800), Hack, JetBrains Mono, Fira Code
(regular + bold only, upstream ships no italic), Victor Mono, Cascadia Code (variable) and
Maple Mono NF. License text and download size are shown before anything is fetched, downloads are
SHA-256 verified, and a metered connection is confirmed separately.

`FontInstaller` owns a layout and nothing else: faces plus a `LICENSE.txt` under
`~/.termux/fonts/<familyId>/`, the bundled `SymbolsNerdFontMono.ttf` extracted from assets into
`~/.termux/fonts/symbols/`, and `~/.termux/fonts.d/10-launcher.conf`. Everything is written through a
temp file plus rename, and the config is written last so it can never name a face that is not on disk
yet.

The install path is the family list, and only the family list. There is no one-tap "Recommended
setup" entry: `FontCatalog.Family.recommended` now only draws a star on Maple Mono's list row, and
`FontInstaller.Options.recommendedFor` supplies the per-family defaults (icons on, the family's
`defaultLigatures`, its font features, no weight override) that any install from the list starts
with.

Three decisions are worth keeping:

- **The legacy files are not mirrored, and must not be.** An earlier revision wrote the installed
  regular face to `~/.termux/font.ttf` and the italic to `font-italic.ttf`, on the theory that plain
  Termux, other forks and Termux:Styling know only those names. That shipped and caused a real
  regression on the user's device: it silently replaced a hand-built Nerd Font and took every icon
  glyph on every surface drawing with the regular face down with it. The mirroring is gone.
  `FontInstaller` now creates, overwrites and deletes nothing outside `~/.termux/fonts/<id>/`,
  `~/.termux/fonts/symbols/` and `~/.termux/fonts.d/10-launcher.conf`; `font.ttf` and
  `font-italic.ttf` belong to the user and are only ever *read*, by the loader's legacy fallback when
  no `font_family` is configured. The managed config names all four faces by path, so mirroring
  bought nothing it could have destroyed something for. The class comment states this so the next
  reader does not reintroduce it as a convenience.
- **The symbols path is re-checked at every config write, not just at install.** The config outlives
  the install, a user can delete `~/.termux/fonts/symbols/`, and a future bundled face could arrive
  under a different name. A dangling `symbol_map` is the worst failure mode available, because the
  terminal falls back silently for every icon cell while the config still reads as correct. So the
  path is written only when the file exists, matches the catalog's byte size, and parses as a font.

`buildManagedConfig` is pure and static because the exact text is the contract between the installer,
the loader and the user reading the file; it is unit-tested character for character. It emits a
"generated, do not edit" header that states the precedence and names the way out, the face directives
padded to the shipped example's column, the two private-use `symbol_map` ranges when icons are on
(`U+E000-U+F8FF` plus `U+F0000-U+FFFFD`, because mapping only the BMP range would silently drop the
`nf-md-*` Material set real prompts lean on), `disable_ligatures`, per-face `font_features` from the
family's recommended set, and per-face `font_variations wght` for a variable family — bold tracking
the slider by the family's own regular-to-bold delta rather than collapsing at the top of the axis.

`uninstallManagedConfig` deletes exactly one file. Installed faces stay, because re-selecting a family
should not re-download it, and `~/.termux/fonts.conf` is never read, written or deleted by any code
path in this class — not even on uninstall.

JVM coverage: `FontCatalogTest`, `FontDownloaderTest`, `FontInstallerTest`, `ManagedFontConfigParseTest`
(the generated file is pushed back through the real parser), `FontToolsRegistryTest`.

### Fixed while delivering the picker

Both of these were found on-device and are fixed; neither is open work.

- **The pick looked inert until the next cold start.** `FontInstallCoordinator` asked the terminal to
  re-read its styling through `TermuxActivity.updateTermuxActivityStyling`, a plain broadcast. The
  picker always runs from the settings activity, so TermuxActivity is stopped and has already
  unregistered its reload receiver — the broadcast was dropped and nothing changed until the process
  restarted. `FontInstallCoordinator.requestTerminalReload` now calls
  `TermuxActivity.requestTermuxActivityStylingOnNextResume(context, false)`, which leaves a pending
  flag behind and is what every other settings screen already used. `recreateActivity=false` is
  enough, because the reload path runs `checkForFontAndColors()`.
- **`symbol_map` icons in tab labels rendered as tofu.** `TerminalWindowBar` loaded
  `~/.termux/font.ttf` itself with `Typeface.createFromFile`, bypassing the font config entirely, so a
  window row drew with a face the terminal was not using — and an icon code point routed to the
  symbols font by `symbol_map` had nowhere to come from. The bar now takes the resolved regular face
  and the configured `symbol_map` ranges from `TerminalLabelFaces` (published by the side that already
  resolved the config, self-resolving the same config until then) and spans mapped code points onto
  the symbols face via `TerminalLabelSymbolSpans`. Covered by `TerminalWindowBarSymbolFaceTest` and
  `TerminalLabelSymbolSpansTest`.

## Not delivered in the 2026-08-03 batch

Recorded so none of it is mistaken for an oversight:

- **The excluded legacy-computing sub-ranges.** `BoxGeometry.isSynthesizable` claims only the
  sub-ranges `fill` actually implements. `U+1FB3C-U+1FB6F` (wedges and diagonal fills) and
  `U+1FB90-U+1FBFF` (inverse shades, pattern fills, segmented digits) still come from the font, as do
  the remaining Powerline variants `U+E0B8-U+E0B9` and `U+E0BE-U+E0BF`.
- **Octants.** `U+1CD00-U+1CDE5` is in Symbols for Legacy Computing Supplement, outside the block the
  geometric work was scoped to, and is not synthesized at all.
- **No bidi reordering.** Unchanged from the first batch and still deliberate: text is shaped in
  logical terminal order.
- **No instrumentation coverage for `Paint.hasGlyph`.** `terminal-view` has only `main` and `test`
  source sets — there is no `androidTest` — so the real-`Paint` coverage probe is exercised only
  through `FallbackFontResolverTest`'s `Coverage` fake. Adding an `androidTest` source set to that
  module is the prerequisite, not a test to be written in the existing one.
- **Iosevka.** Considered for the catalog and dropped: upstream publishes no small per-face artifact,
  so a single family would have cost a multi-hundred-megabyte download for four faces. Nothing in the
  code records this, which is why it is recorded here.

## Scope boundaries

- **No `narrow_symbols` and no symbols consuming following cells.** Those change terminal cell
  ownership and belong with the deferred multicell/variable-sized text project. Implementing them
  only in the renderer would corrupt insert, erase, selection, and reflow semantics.
- **No bidi layout.** Kitty itself does not implement full bidi. This project shapes Arabic and
  other scripts correctly within the terminal's logical cell order; it does not promise Unicode
  bidirectional paragraph reordering.
- **No GPU glyph atlas or custom gamma/contrast compositor.** Phase 0 counters now exist. Those
  projects remain gated on measured Canvas frame time, allocation, power, or visual failures.

## Safety contract

Font files are untrusted input parsed by Android system font code. Every face and mapping load must
be isolated behind exception handling and resource limits. A malformed, missing, unreadable, or
unsupported font must produce a bounded error and fall back to the previous valid face or native
default; it must never prevent the terminal from opening. Parse and validate a complete candidate
configuration before atomically applying it to every pane, cap file count/size and mapping count,
and do disk/font work off the render path.

## Exit gates

- Existing users without `fonts.conf` see no behavior change.
- Real bold, italic, and bold-italic faces reload across every existing and newly created pane.
- Repeatable path-backed symbol ranges reload atomically, with later overlaps taking precedence.
- One bad optional face cannot disable the other faces or crash Activity creation.
- Cursor, selection, copy, resize, and reflow agree on grapheme boundaries for the test matrix.
- Canvas is retained unless the same acceptance suite demonstrates a concrete Android shaping gap.
- A hand-written `~/.termux/fonts.conf` always wins over every `fonts.d` fragment, and no set of
  fragments can prevent it from being read.
- Nothing the picker does ever reads, writes or deletes `~/.termux/fonts.conf`.
- `box_drawing font` reproduces the pre-batch glyph rendering for every geometric code point.
- Adjacent geometric cells share the identical integer boundary at every cell size.
