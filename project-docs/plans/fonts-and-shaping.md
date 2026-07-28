# Fonts and cluster-aware shaping

Status: four-face configuration delivered and device-verified 2026-07-28; cluster-aware shaping is next.

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

Later slices may add repeatable directives such as:

```text
symbol_map U+E000-U+F8FF path=~/.termux/fonts/SymbolsNerdFontMono.ttf
fallback U+0600-U+06FF path=~/.termux/fonts/NotoSansArabic.ttf
```

An explicit map selects its font for the range. Unmapped clusters use the configured primary face
and Android's normal platform fallback. Family-backed mappings are allowed but are not a substitute
for path-backed mappings.

## Implementation order

1. **Four faces first — complete.** Load regular, bold, italic, and bold-italic independently and teach
   `TerminalRenderer` to select the real face for each SGR combination. Synthetic bold/italic is
   used only when its face is absent. This is independent of the grapheme work and is the first
   visible delivery.
2. **Grapheme and shaping test suite.** Cover Arabic shaping without promising bidi layout, Indic
   conjuncts, combining marks, ZWJ emoji, Nerd symbols, programming ligatures, cursor boundaries,
   selection boundaries, reflow, and styled run boundaries.
3. **Canvas shaping experiment.** `Paint.getTextRunAdvances()` is available below the app's API 26
   minimum and exposes per-character advances/cluster continuations. Test it against the suite and
   use the result to map Android-shaped clusters deterministically back to terminal cells. Port
   HarfBuzz only if those acceptance tests fail on supported Android versions/devices.
4. **`symbol_map` and fallback ranges.** Select fonts per complete grapheme, never in the middle of
   a cluster; retain Android fallback after explicit mappings and primary faces.
5. **Ligature policy.** Support `never`, `cursor`, and `always`. The renderer already breaks at the
   cursor for inversion; cursor-only disabling should use that boundary and a temporary feature
   setting rather than reshape unrelated cells.
6. **OpenType features.** `Paint.setFontFeatureSettings()` exists below minSdk 26. Translate the
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
- One bad optional face cannot disable the other faces or crash Activity creation.
- Cursor, selection, copy, resize, and reflow agree on grapheme boundaries for the test matrix.
- Canvas is retained unless the same acceptance suite demonstrates a concrete Android shaping gap.
