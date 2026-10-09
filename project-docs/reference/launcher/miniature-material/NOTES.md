# Layout miniature assets

All dimensions below are dp-like viewport units. The phone is **240 × 520** in portrait and **520 × 240** in landscape. SVG and Android pairs share identical path geometry. XML contains only a vector root and paths; path attributes are limited to `pathData`, `fillColor`, `strokeColor`, `strokeWidth`, `fillAlpha`, and `strokeAlpha`. Labels, clock digits and glyphs are paths, so no font installation is needed.

## Geometry and touch targets

Padding describes visible internal spacing, not an Android View's hit bounds. The grip occupies a reserved trailing corner, separate from the content. Insets are measured from the asset viewport; outlines sit 1 unit inside it. Small icon strokes range from 0.8–1.5 units. Border widths are specified below.

| Asset | Viewport | Corner radius | Border | Content padding / spacing | Minimum interaction target |
|---|---|---|---|---|---|
| status_bar | 216 × 32 | 10 | 1 | Clock 8 left / 12 top; indicators 31 from right | 48 × 48 grip; full strip accepts drag |
| status_bar_vertical | 40 × 176 | 10 | 1 | Clock 8 left / 12 top; indicators 8 left / 38 from bottom | 48 × 48 grip, extending outside visual width |
| pane_widgets | 216 × 304 | 20 | 1 | 12 sides, 32 top; 8 between tiles; tile radii 10–12 | 48 × 48 grip; whole pane may start drag |
| pane_terminal | 216 × 304 | 20 | 1 | 14 left, 36 top; 13 line pitch; 7 character pitch | 48 × 48 grip; whole pane may start drag |
| apps_row | 216 × 48 | 16 | 1 | First icon centre 22 / 25; 35 centre pitch; radius 9 | 48 × 48 grip; 216 × 48 row |
| apps_column | 48 × 152 | 16 | 1 | First icon centre 23 / 37; 24 centre pitch; radius 9 | 48 × 48 grip; whole column accepts drag |
| alphabets_row | 216 × 36 | 10 | 1 | 16 left; 39.5 letter pitch; vertically centred | 48 × 48 grip; full strip accepts drag |
| alphabets_tab | 48 × 28 | 10 | 1 | A at 10 / 10; reserved trailing grip | 48 × 48, extending above/below tab |
| extra_keys | 216 × 56 | 12 | 1 | Glyph origin 14 left; five 24 × 24 slots with radius 6 | 48 × 48 grip; whole bar accepts drag |
| keyboard | 216 × 128 | 16 | 1 | 24 top for keys; 18 horizontal / 23 vertical pitch; keys radius 4–5; 14 bottom | 48 × 48 grip and separate 48 × 48 swipe region around grabber |
| drag_handle | 24 × 24 | Dots radius 1.6 | None | Six dots; 6 horizontal / 5 vertical pitch; centred at 12 / 12 | **48 × 48**, centred on grip |
| drag_handle_active | 24 × 24 | Dots radius 1.6 | None | Same geometry; accent fill | **48 × 48**, same anchor |
| hidden_tray | 240 × 52 | 14 | 1.5 dashed | Label centred; dash 5 / gap 5; rounded corner segments | Whole 240 × 52 drop zone; at least 48 tall |
| hidden_tray_active | 240 × 52 | 14 | 1.5 dashed | Same layout; accent outline / label; 9% accent fill over container | Same drop bounds |
| phone_frame_portrait | 240 × 520 | 24 | 1.5 | Content 12 sides; 8 top; 16 bottom | Fixed, no interaction target |
| phone_frame_landscape | 520 × 240 | 24 | 1.5 | Content 12 sides; 8 top/bottom | Fixed, no interaction target |
| status_bar_landscape | 496 × 24 | 10 | 1 | Clock 8 left / 12 top; trailing indicators | 48 × 48 grip; whole strip accepts drag |
| pane_widgets_landscape | 440 × 128 | 20 | 1 | 12 sides / 32 top; 8 between tiles | 48 × 48 grip |
| alphabets_row_landscape | 440 × 24 | 10 | 1 | 16 left; letters evenly distributed before grip | 48 × 48 grip |
| extra_keys_landscape | 496 × 28 | 12 | 1 | 14 left; five evenly distributed slots | 48 × 48 grip |

The ten element `_active` pairs (including the vertical status bar) inherit their base asset's viewport, padding, radius and minimum targets. Their stronger border is **1.5 units**. The hidden tray's active state is a drop state, not a picked-up state.

## Drag and lifted treatment

Every movable piece has the same **2 × 3 dot grip at the upper trailing corner**: centre `(width − 13, min(16, height / 2))`. The narrow vertical status bar places it below the clock at `(27, 36)` to avoid a collision. Dots are dim at rest and accent when held. The pane also has a grip so it can be moved if the host editor permits it; the phone outline and Hidden tray are fixed.

Picked-up surfaces use a slightly inset, raised card with a solid surface underlay offset down by 4 units, a container fill, a 9% accent tonal layer, and a 1.5-unit accent outline. The six-dot grip becomes accent. This represents tonal elevation with portable paths rather than filters or platform shadows. The active artwork fits its original viewport. A host may also translate the whole piece upward during drag without moving its touch anchor.

The keyboard has **two distinct affordances**: the common grip at the upper right for layout dragging, and a 36 × 3 rounded pill centred at its top for a downward hide swipe. The pill is never the layout grip. Give the pill its own swipe region and accessible “Hide keyboard” action.

These assets cannot encode hit testing. The editor must provide 48 × 48 dp targets in an overlay or touch delegate, including expansion beyond narrow strips and beyond the phone edge. At 240 px the stacked strips are closer than 48 units, so expanded rectangles can overlap. Resolve candidates by nearest visible grip at pointer-down, retain the chosen element for the entire drag, and expose labelled Move / Hide actions for accessibility. Do not simply make overlapping child Views compete by drawing order. When the miniature is scaled down, preserve the **physical 48 dp** hit size rather than scaling it with the artwork.

## Arrangement and variants

- Portrait Home follows the reference: top status, outlined widget pane, dock, alphabet strip, place bar. Three faint rounded widget tiles and a clock suggest widgets without filling the pane with a dense grid.
- Landscape follows the reference: top status, dock column on the left, wide pane to its right, alphabet strip below the pane and full-width place bar below both. Dedicated wide assets preserve dot, glyph and circle proportions.
- Terminal uses a shorter 216 × 236 pane, dock, compact 216 × 32 place bar and keyboard. Its 48 × 28 alphabet tab overlays the lower left pane edge. Terminal lines use discrete short character marks plus a prompt and cursor block.
- Hidden sits outside the phone, with a 16-unit gap. Portrait composition canvases are 240 × 588, and landscape is 520 × 308; the **phone itself** retains the requested dimensions.
- The horizontal status asset can be translated to either top or bottom. `status_bar_vertical` can be placed on either side with upright content. Move it without rotating its digits. All status variants can be dropped into Hidden just like the other draggable pieces.
- The fifth app disc is selected (accent). Selection is separate from pickup: a selected app does not turn its row's grip accent until the row is held.

## Theme mapping

| Placeholder | Theme role | Usage |
|---|---|---|
| `#FF00FF` | `colorPrimary` | Selected app, picked-up outline/grip, drop-active state; 9% tonal overlays |
| `#808080` at 45% | `colorOnSurfaceVariant` | Resting outlines, text, glyphs, grip dots and key silhouettes |
| `#404040` | `colorSurfaceContainerHigh` | Raised containers; 55% widget tiles and 60% keyboard tint |
| `#202020` | `colorSurface` | Phone, pane, icon slots and lift underlay |

No other colour literals occur in production SVG/VectorDrawable assets. Transparent areas remain transparent. The keyboard's tinted glass is an alpha-composited container fill, not a backdrop blur.

Only `preview.svg` / `preview.png` substitute the review palette: accent `#7FD0E0`, dim `#D8DFE1` still at 45%, container `#343C3F`, surface `#202629`, on the requested `#121212` background. Contact-sheet labels use light greys. Do not ship those substituted colours as production assets.

## Rendering and verification

Run `python out/build_assets.py` from the workspace (requires Python and `rsvg-convert`). It reproduces every vector pair, all three composition SVGs/PNGs, and the preview. `miniature_portrait_terminal.svg` is included as an editable source for the requested Terminal PNG.

The portrait PNGs are rendered at exactly **240 pixels wide**, with no supersampling, and were visually inspected at that size. The outlined pane, sparse widgets, circular dock, five place glyphs, six-dot grips, alphabet tab and keyboard rows remain distinct. The specified dim placeholder is intentionally low contrast against the dark placeholders; final contrast must be judged after theme substitution. The themed preview includes both portrait compositions at native 240 px width. There is no claim that miniature text is body-size accessible text.

Validation checks parse every SVG/XML, verify the allowed path attributes and colour/alpha rules, compare every pair's geometry and paint, and check PNG dimensions. Android device rendering and real drag/touch behaviour require integration in the launcher and were not exercised here.
