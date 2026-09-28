# Task: redesign the Layout editor's phone miniature as Material 3 surfaces

You are working in this directory only. Look at the reference screenshots in `ref/` first:

- `ref/editor-portrait.png` and `ref/editor-landscape.png`: the Android launcher's Layout editor. It is a bottom card with a small phone "miniature" that shows the screen's arrangement. The user drags the elements on the miniature to an edge of the phone, or into the "Hidden" tray below it, to hide them. The `⋮⋮` marks on the right are the drag handles.
- `ref/real-terminal-screen.png` and `ref/real-home-screen.png`: the real screens the miniature stands for.

Today the miniature is a set of flat grey boxes. It doesn't tell users what each part is, and the drag handles are hard to read. Redesign it so that each element looks like a small Material 3 surface that stands for the real thing on screen.

## Elements the miniature shows (each is its own draggable or fixed piece)

1. **Status bar**: a thin strip with a clock at one end and small indicators at the other. It can sit on the top or bottom edge, or on the left or right in landscape. It must be hideable (drag to Hidden).
2. **Pane**: the main place's surface, a large rounded, outlined card with a thin border line like the real pane. On Home it holds a widget grid (show a few faint rounded tiles, not a solid grid of boxes). On Terminal it holds a terminal (show a few short monospace-like text lines and a prompt block). Provide both variants.
3. **Apps row (dock)**: a row of 4–5 circular app-icon placeholders. In landscape it can be a column.
4. **Alphabets row**: a thin row of spaced tiny letters (A F M S Z). Also provide a **minimised pull-tab** variant: a small rounded tab that sits on the edge, overlaying the pane.
5. **Extra keys / place bar**: a row of 5 small glyph slots (keyboard, list, home, prompt, display).
6. **Keyboard**: a block of 3–4 rows of tiny rounded keys on a slightly tinted glass surface, with a short grabber pill centred at its top edge. This grabber is also the hint for "swipe down to hide the keyboard".
7. **Drag handle**: one clear, consistent Material drag-handle affordance (for example a 2×3 dot grip or a short pill) that every draggable element shows in the same spot. Also provide a "picked up" state: the element raised, with a tonal elevation and a stronger outline.
8. **Hidden tray**: a dashed-outline rounded drop zone labelled "Hidden", with a "drop active" highlighted state.
9. **Phone frame**: the rounded device outline around everything.

## Colour rule (important)

Use placeholder colours only, so the app can map them to theme attributes:
- `#FF00FF` = accent (colorPrimary): selected state, the drag handle when picked up, drop-active.
- `#808080` at 45% opacity = dim (colorOnSurfaceVariant): outlines, text lines, handles at rest.
- `#404040` = surface container (colorSurfaceContainerHigh).
- `#202020` = surface (colorSurface).
Use no other colours.

## Output (write all of it into `out/`)

- `out/<element>.svg` and a matching Android `out/<element>.xml` VectorDrawable for each element and state above: status_bar, pane_widgets, pane_terminal, apps_row, apps_column, alphabets_row, alphabets_tab, extra_keys, keyboard, drag_handle, drag_handle_active, hidden_tray, hidden_tray_active, phone_frame_portrait, phone_frame_landscape. Size each on a dp-like grid (the portrait phone frame is 240×520 units). Keep shapes simple: rounded rects, circles, short lines. VectorDrawables must use only pathData, fillColor, strokeColor, strokeWidth, fillAlpha and strokeAlpha.
- `out/miniature_portrait.svg` + `.png` and `out/miniature_landscape.svg` + `.png`: the full composed miniatures, arranged like the reference screenshots. The portrait one shows the status bar on top, pane_widgets, apps row, alphabets row and extra keys along the bottom. Also compose `out/miniature_portrait_terminal.png`, the Terminal arrangement with the keyboard and the alphabets pull-tab.
- `out/preview.png`: every element in a labelled grid on a #121212 background, with the placeholder colours swapped for a teal accent (#7FD0E0) and light greys, so a human can judge it.
- `out/NOTES.md`: for each element, its corner radius, stroke width, padding and minimum touch size in dp (drag handles need at least a 48dp touch target around them), and how the drag handle and picked-up state look.

Render the PNGs with any tool available (rsvg-convert, magick, or python). Check that everything is legible when the portrait miniature is drawn 240 px wide.
