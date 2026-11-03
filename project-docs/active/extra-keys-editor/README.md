# Extra keys editor, streamlined (2026-10-25)

Issue #164 reported the Terminal extra keys editor misbehaving on `com.termux`. The developer
confirmed one defect: open Settings → Keyboard → Terminal extra keys, pick Page 2, and there is
no way to enter a new key value. Fix that, then rebuild the editor to the developer's wireframes.

`wireframes.html` is the spec (standalone, open it in a browser; the demos are clickable).
`wireframes-0..2.png` are renders of it. Five screens:

1. **Page editor.** App bar (back, "Extra keys", Reset). Page chips, a page ⋮ and a + to add a
   page. "Tap to edit · Hold to reorder" with the row count. One card per row: drag handle,
   "Row N", ⋮, the keys as 48 dp caps that scroll sideways, and a pinned dashed + at the end.
   "+ Add row" below the rows. Cancel / Save changes pinned at the bottom.
2. **Edit key sheet.** On tap (opens the picker), On swipe up (optional, "None" by default),
   Display label (changes appearance only), Move key ← →, Remove key, Done.
3. **Choose a key sheet.** "Adding to Row N", search over keys and actions, category chips
   (Common, Navigation, Symbols, Other; app actions grouped separately), a grid of 56 dp
   tiles, Cancel / "Add to row: <key>". Replaces the always-visible catalog.
4. **Row options sheet.** Move row up, Move row down, Delete row, Done.
5. **Page options sheet.** Page name, Move page left / right, remove page.

Shared rules: everything edits a draft; Done closes a sheet into the draft, Save changes
commits, Cancel discards; removals offer Undo. 48 dp targets, 7–8 dp gaps, 16 dp side inset.
The editor's runtime contract stays `termux.properties` (`extra-keys`, `extra-keys2`), so the
terminal toolbar reads what it read before.
