---
status: accepted
date: 2026-10-29
amends: 0015 (the wall slot and its window of neighbours; W3 kept, W4 lifted; the empty Terminal page is always a stop), 0012 (a crossing may land on a still)
---

# The wall lays out one cell per page, with stills

Implemented in #183 to #187. Emulator pass of the sweep's repro and of the extra-keys row: https://amal-build.taild9c5dc.ts.net/reports/wall-cells-2026-10-30/ . The pong checks are in `project-docs/device-checks.md`.

ADR 0015 made the wall one ordered list of pages, but the layout kept the shape it had with two
halves: one view per page kind (`PaneWallLayout`'s `EnumMap`), and a *window* of at most one page
per kind around the current slot, built by `PaneWallPolicy.builtWindow` and installed only when
`refreshPages()` runs. An emulator sweep on 2026-10-29 (feat/page-model b539dd301,
`~/reports/page-transitions-2026-10-29/findings.html`) showed what that costs. A named jump to a
Display page rebuilds the window as `[TERMINAL, DISPLAY]`; nothing rebuilds it when the wall lands;
so afterwards a swipe left on Home slides the Display page in and skips the Terminal page, the
Android home press is refused (`goTo WIDGETS … pages=[TERMINAL, DISPLAY] REFUSED`), the extra-keys
row hides its Home key, and a fling on the Terminal page's seam rubber-bands against a neighbour
that exists. Swipes alone keep the window right; keys, chords and the home press leave it stale.
Three consumers (the swipe's neighbours, `wall.go`, the row's eligibility) were reading an
implementation detail as if it were the wall.

We decided that **the wall lays out one cell per arranged page**, in Edit pages' order, by page id.
A cell shows its page **live** while its kind's one view is bound to it, and otherwise as a
**still**: the page's last picture, else a card with the page's name and kind icon. Landing on a
still binds the kind's view to that page and the still gives way. The arranged page list plus the
current page id is the only answer to "which pages exist and which is next"; the layout's list is
nobody else's to read.

1. **Cells, not a window.** Every page is laid out, whatever its kind and wherever it stands.
   Neighbours are cell neighbours. Two Display pages side by side and a Home run on each side of
   the Terminal page are allowed (W4 lifted, WallPages' adjacency refusal goes).
2. **Stills, one rule for every kind.** A page's picture is taken when it stops being live, at
   half resolution, and dropped when it is live again or removed; one per page, no cap. A page
   with no picture shows its card. `DisplayPagePolicy` becomes the page-still policy for all kinds.
3. **The dress is the page kind's, live or still.** A crossing onto a still morphs the dock,
   status bar, extra keys and keyboard to the arriving kind exactly as onto a live page; the
   landing only swaps the still for the view and changes nothing on the chrome (0012's point).
4. **Far jumps by name keep W3**: a one-width slide from a stand-off, the pages in between never
   announced or started.
5. **Named targets with several pages of a kind**: the Home key and `wall.widgets` go to the home
   page when it is a Home page, else the nearest Home page by cell distance; the Display key and
   `wall.display` to the bound (last used) Display page, else the nearest; the Terminal key to the
   one Terminal page; `wall.go left/right` to the adjacent cell whatever its kind.
6. **The empty Terminal page is always a stop** (0015 §1 as written): the slide onto it shows its
   card, the landing starts the shell. The default-order special case that kept it off the row,
   the over-swipe that started the first shell from Home (`emptyStop`, `seamIntoEmpty`) and W8's
   measuring go.
7. **The row's wall keys show by existence**: a key is on the row iff the wall has a page of that
   kind, the current kind at full strength; the Display key no longer waits for the display to have
   run.

## Considered options

- **Patch the window** (rebuild on `onWallPageSettled`, make keys and `wall.go` read `WallPages`):
  rejected as the long-term shape; it fixes the five symptoms found and leaves the concept and its
  special cases (`emptyStop`, the landing slot, W8) to breed the next one. Acceptable only as a
  stop-gap if the retirement slips.
- **One view per page** (a widget frame and a display frame per page): rejected; the X display
  and the terminal row are single views by nature, and surfaces and memory would multiply.
- **Slide the real distance on a far jump**, stills flying past: rejected; slower, and every page
  between would get a "changed" moment it must ignore.
- **Stills wear the leaving dress** until landing: rejected; the landing would change the chrome,
  which 0012 forbids.
- **Full-resolution pictures**: rejected for memory (about 10 MB a page); a still is seen only in
  motion.

## Consequences

- `PaneWallLayout` lays out N cells and positions each kind's view at its bound page's cell;
  `PaneWallPolicy.builtWindow`, `rowPages`, the landing slot and the kind-keyed
  `relativePosition` go. `PaneWallPage` survives as the page kind only; a rename to page kind is a
  separate mechanical ticket.
- `PaneWallController.refreshPages()` and its ten callers in `TermuxActivity` stop deciding what
  the wall holds; they re-read the page list. `onWallPlacesChanged` and the row's eligibility read
  `WallPages`, not `pages()`.
- Pictures need a capture for Home pages and the Terminal page (the display already keeps one).
- Regression tests at the controller seam (`PaneWallControllerTest`'s fake host): a named jump,
  then a landing, then a drag release must find the arranged neighbour; the row's eligibility test
  reads the page list.
- The glossary gains cell, live page, still and binding; wall slot, place window and slot retire.
- Chords on a live Display page still belong to X (0015); the Ctrl+Alt+Shift+T sheet found in the
  sweep is a separate bug, not this decision's.
