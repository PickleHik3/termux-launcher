# Fonts and cluster-aware shaping

Status: four-face configuration, Canvas shaping, the fixed-cell grapheme model, explicit symbol
maps, ligature policy, and OpenType features were delivered and device-verified 2026-07-28;
variable axes are next.

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
7. **Variable axes.** `Paint.setFontVariationSettings()` is available at minSdk 26. Validate axes,
   keep settings face-specific, and degrade to the unmodified face if Android rejects them.
8. **Metrics.** Add bounded cell width/height, baseline, underline, and strikethrough adjustments
   only after shaping and selection geometry are pinned by tests.

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
