# Editor cards: one anatomy, one set of transitions

Design run of 2026-10-30 in OpenDesign (project `tl-editor-cards-2026-10-30`, design system
`termux-launcher-v2`, skills frontend-design + motion). `SPEC.md` is the developer's contract;
`prototype.html` plays the cards, chips, detents, swaps and the wallpaper round trip at 412×915 dp.
The spec is implemented in wave W3 of `surface-look-grid-2026-10-30.md` with these corrections,
which win over the spec where they differ:

1. **Precedence.** The spec's §5 assumes the element beats the page. ADR 0017 says the page wins:
   element on this page → all glass on this page → the element everywhere → Base → shipped.
2. **Frost stops.** The ADR's eight (positions 0, 10, 20, 40, 55, 75, 90, 100 on the old scale;
   `FrostStops`), not the spec's own list.
3. **Editor bar labels** truncate at 412 dp in the prototype ("Appearan…"); the bar keeps the
   existing editor bar's items and sizes, with the Sheets door added as an item of the Elements
   list, not a fourth label.
4. **Frame label** after a tap on the Terminal page's status bar must say "Terminal page"
   (prototype slip).
5. Rows beyond the glass rows keep today's controls where the spec invents values (clock faces,
   cursor trails, effects, dock designs): the spec's picker-row shape, today's option lists.
