---
status: accepted
date: 2026-11-21
amends: 0030 §4 (the glass under the rows: a card both ends show in the same place stays put; any other card belongs to a side and travels with it, so nothing "stays put and blends" its size), 0018 §1 (each element one surface: the unit of glass is the card a rest draws, not the element)
---

# A glass card keeps its rest size; what differs between rests is two cards

Map: `~/.cache/window-restructure/pieces-map.md` (2026-11-21, at 2f31e7809). It builds on
[ADR 0028](0028-one-frame-pipeline-a-view-property-has-one-writer.md) (a crossing lays out
nothing), [0029](0029-each-glass-surface-keeps-one-picture.md) (one picture per surface),
[0030](0030-pages-sides-and-the-bottom-stack.md) (rows travel with their side) and
[0031](0031-the-chrome-wears-one-look-a-page-carries-what-differs.md) (one look).

After ADR 0031 the chrome has one look, and the Docked landing snaps went with the look blends.
Floating kept snapping. On `fix/floating-crossing-settle` 26 commits (17 fixes, two of them
reverted, +1,712 lines) chased one Home ⇄ Terminal crossing. They all have one cause: the
dock's glass host (`accessory_surface_host`) is the **Peek** card on Home, about 1080 × 951 and
flush with the screen's edge, and the extra-keys bar's card on the Terminal, 1038 × 98 and 21 px
in. Under Floating with the Peek design the dock is placed as Docked on Home
(`DockPlacement.floats`) and as a Floating card on the Terminal. A crossing may not lay out, so
the host stands at one end's size and is reshaped toward the other. The host is laid out at its
new size only when it lands. Everything read from the host's own layout then steps in one frame:

- the frost sampled up to 136 px from where the glass was drawn, because the blur anchors to the
  layout and the renderer draws the host shifted (1d92fba03);
- the card's lower edge stood 42 px low, because the seam overscan was read off the backdrop as
  Home had sized it (3c2fb0b19);
- the shelf's bright band arrived about 70 ms after landing, because the design's shelf is the
  card's while the card stands and the tray's after (cc9a5d3f8);
- the keyboard's card wore a second coat under the blending card, until it was clipped row by
  row below the card's cut (0c0cfec38).

The status bar is the same size on both pages and has never snapped. The Peek card itself has
no view: it is the shared host, cut.

ADR 0030 §4 says the slab under the rows "never slides, and its height and look blend", and
that Floating's card "stays put and blends". The look half is gone (0031). The size half is the
cause above.

## Decision

1. **A view holding glass is laid out once per rest and never changes size in a crossing.**
   What a crossing does to it is translate, clip and fade. Nothing in its glass (anchor, cap,
   seam overscan, shelf, top light) is read from a layout the crossing will change.
2. **The unit of glass is the card a rest draws.** A card is what `ChromeShape` already calls
   one: a run of pieces on one edge under Floating (the apps row and A–Z share one card), the
   joined frame under Docked, the keyboard, a lone bar. Each piece (status bar, apps row, A–Z
   bar, extra-keys bar, keyboard, Peek card, page bar) has its own rect, corners and per-edge
   kind (join, screen, gutter, rim). Each piece stays a piece, but not every piece gets its own
   glass: Floating's one card around neighbouring rows stays one card.
3. **A card both ends draw at the same rect is shared and stays put.** The status bar, a
   keyboard up at both ends, and the foot under the gesture pill are shared cards. Any other card
   belongs to the side whose rows it carries and travels with that side, sliding or fading. Home's
   Peek card and the Terminal's extra-keys card are two cards. The Peek card no longer turns into
   the extra-keys card: one slides out with Home as the other slides in with the Terminal.
4. **One pure answer per rest gives every card's rect, corners and edges.** It grows out of
   `ChromeShapeModel` and takes in what lives beside it today: the laid-out heights and gaps
   (`StackLayout`), the Peek card (`PeekGlassGeometry`, `DockPlacement`) and the foot. The
   views are laid out from it, and the outlines are cut from it, so the rect and the shape have
   one owner. Docked and Floating keep separate formulas inside it, as AGENTS.md requires.
5. **The stack's scene works on cards, not on the shared views.** `StackScene.at(from, to, p)`
   pairs the two rests' cards. A shared card gets its rest; a side's card gets its side's slide,
   riding the keyboard's top (0030 §4). The renderer writes those and reads no Style or page.
   Glass samples where the scene says the card is drawn.
6. **A property test pins it.** Over the layout fixtures × the four dock designs × Style ×
   keyboard state at each end × both directions × the page pairs: at p = 0 and p = 1 every card
   is its rest's; no view's laid-out size differs between the last crossing frame and the landing;
   two cards that touch at a rest meet on a join or a seam, never a gap.

Docked's slab is the open case. Its rows' bands differ in height between rests too, but only its
top edge moves, and no glass value is read from that edge. Today's clip blend may stay until the
property test runs on Docked. If the test passes, the exception is written down. If it fails, the
slab splits into flush cards joined at their edges, like Floating.

Build order (map §5): the property test, then the per-rest answer, then the extra-keys bar's own
card under Floating, the same under Docked, then the Peek card and the apps row's card, and
last the scene and the renderer on cards. A new piece, such as the page bar decided on
2026-11-20, is built as its own card from the start.

## Accepted on 2026-11-21

The developer saw the two cards on pong (the prototype of #300, Floating with Peek, keyboard up
and down, both directions) and accepted the look: Home's Peek card slides out with Home and the
extra-keys bar's card slides in with the Terminal. Nothing fades.

What the prototype and the crossing test (#301, `RestCardsCrossingTest`) settled:

- **Docked's slab fails the test.** The glass host changes height between Home and the Terminal
  in every dock design and both Styles, so the open case above closes: the slab splits into
  flush cards joined at their edges (#304).
- **Floating with Tray, Beads or Strip re-sizes the host too**, in height only. Peek is the one
  design where its sides move as well.
- **Each side keeps its own bottom gap.** Peek stands flush and the Terminal's card keeps
  Floating's 6 dp gap, so the stack's container holds one margin at both rests and Home's side
  carries the difference as a constant. A container margin that follows the front page steps
  16 px one frame after landing.
- **The two-card rule reads Home's dock design**, not the design of the page in front: the
  Terminal wears no design when its layout shows no apps row.
- **The keyboard's card is built once, in the Terminal's look, as the crossing begins.** Dressed
  at landing instead, its glass arrives about a second late.

- **2026-11-24, Docked (#304), option A.** Under Docked the Terminal's card is a second back
  host beside the glass host (`terminal_surface_host`, `RestCards.StackView.TERMINAL_HOST`),
  laid out once per Terminal rest with the Docked dock formula: the one host's rect at that
  rest, the flush air above the keys and the foot under the gesture pill included. The renderer
  carries it on the Terminal's lane; Home's host stays Home's card and travels with Home, wearing
  Home's cut. Neither host changes size in a crossing. A keys card in the row host was tried
  first and refused: it dropped the flush air and left a bare strip under the pill. Layouts the
  two-card rule refuses (the apps row or A–Z beside the keys) keep the one host.
- **2026-11-24, shared rows (#305).** With the apps row or A–Z beside the keys the cards stand
  apart too: the Terminal's card is its own host (`terminal_dock_host`) under either Style, and
  a row both pages show stays put while the two cards slide under it. This replaces the
  2026-11-21 decision "shared-row layouts keep the old morph". To be checked on pong.
- **2026-11-24, the last shared host (#305).** A Terminal with no keys row, with its keys under
  the keyboard, or with its status bar on the dock's plank stands apart as well, and a Terminal
  with no card at its rest shows none. The one layout left on the one host is a Terminal whose
  rest wears the Peek card (no keys row, its apps row alone, keyboard down): it is Home's card at
  Home's rect, a shared card that stays put (§3). The Peek card's blend toward the slab is gone.
- **2026-11-24, the pieces (#307).** The A–Z bar off the dock is its own card on its edge host
  (`RestCards.CardId.AZ` on `StackView.AZ_EDGE`), the shape model's piece; the edge stacks stand
  in the content column, which no crossing moves, so the scene keeps it, the keyboard's column
  and the floating keyboard at their rest as the stack's own cards. The side bars' travel fade is
  deleted: no place is ever minimal, so it never ran. The status content's fade stays: it hides
  the bar's content on the way to a Home that wears none, where the bar cannot ride. Two cases
  the property test names stay open for a decision: an A–Z bar on a side edge runs down to the
  stack's top and so changes length between rests; and under Docked the foot is worn by Home's
  host and by the Terminal's own host (#304 option A), two cards at one rect where §3 wants one
  shared card. One owner for the foot needs a view of its own for the band.
- **2026-11-24, the developer's answers to #307's open cases.**
  - An A–Z bar on a side edge is one card per side, each its own length: Home's is laid out
    once at Home's rest and slides out with Home, the Terminal's once at its rest and slides in
    with the Terminal; neither re-sizes. Where both rests draw it at one rect it stays one shared
    card (§3). Two A–Z cards on screen mid-crossing are accepted.
  - The foot under the gesture pill becomes a view of its own: one shared card that stays put,
    with one owner. Home's host and the Terminal's host (`terminal_dock_host`) both stop at the
    foot's top, and each joins the foot seamlessly (no line, no 1 px gap, the same glass sample).
    This revises #304's "the foot under the gesture pill included" for the Terminal's host.
  - 2026-11-24, revised: the keyboard is two cards, one per side. Sharing one card would raise
    Home's keyboard about 151 px on pong (Docked, keyboard up: Home's column 1742–2412, the
    Terminal's 1591–2412 over its bands under the keyboard), so each side keeps its own keyboard
    rest. Each side's keyboard card is laid out once at its rest and travels with its side; it
    never re-sizes at the landing. With the keyboard up at both ends, Home's keyboard card slides
    out with Home while the Terminal's slides in. The arriving keyboard's card is dressed in its
    look as the crossing begins, not at landing (the Accepted note above). A second keyboard card
    holds no second keyboard's pixels at rest.
  - A Docked A–Z column meets the status bar and the dock on a join over its own width only: a
    card's edge keeps the shape model's runs (`RestCards.Card#kindOver`). Built in #307 round 2.
- **2026-11-24, #307 round 3.** The per-rest answer stands each side's A–Z card on an edge host
  of its own (`StackView.AZ_EDGE` Home's, `TERMINAL_AZ_EDGE` the Terminal's), one shared card
  where both rests draw it at one rect. A Home to Home crossing with Home's keyboard up at one
  end only still re-sizes Home's bar: both ends are Home's side, so one card per side cannot part
  them. The views (the second edge host, the foot's own view, the second keyboard card) are not
  built yet.

## Consequences

- The Floating crossing machinery goes once the extra-keys bar has its own card:
  `holdTerminalRestPill`, `rebuildDockCapForPill`, `settleHostForHome`, the crossing-begin
  layout of the host, `PeekGlassGeometry.blendsAcross`, `DockSurfaceSpec.shelfAcrossCard`,
  `StackRenderer`'s host shift, `clipKeyboardBlendUnderPeek`, and the tests that pin them.
- Peek no longer morphs into the extra-keys card. Users see two cards, one leaving and one
  arriving. That is a visible change and the developer should see it on pong before it is
  accepted.
- One more Live glass surface while the Terminal's extra-keys card is on screen. ADR 0029's
  8-slot budget holds it at about 1.15 MB for its blur layer. Two cards draw while both are
  partly on screen.
- The shape model gains the pieces it does not have (Peek card, foot, A–Z tab, Home's extra-keys
  bar, page bar, dock designs). Its one caller today (`LiveChromeShape`) becomes the layout's
  source as well as the outline's, which is the riskiest step.
- Today's tests cover 160 crossings, all with the Tray design, and check only the stack's
  container. Peek, the default design, is in none of them. The property test is the first cost
  and the first gain.
- What stays deliberately different: Docked joins its cards flush, with square outer corners and
  the screen's rounding. Floating keeps air, Margin and its 6 dp bottom gap. Peek stands flush
  under either Style. Those differences are formulas inside the per-rest answer and nowhere
  else.

## Considered options

- **Keep patching the morph.** Rejected: 26 commits on one path, and each fix moves one value
  derived from the host's layout. Others remain (the top light, the reflection's fade, the
  A–Z row on the card), and the next design or keyboard form starts the series again.
- **Lay the shared host out at the arriving size when a crossing begins.** Tried (f26fec5b0,
  0bce7867d): it needed a named exemption from the "crossing laid out" guard, then a move outside
  the frame table to dodge it. The host then shows the leaving rest wrongly instead, and the cap
  had to be rebuilt to match (e2f24c700).
- **Lay the shared host out at the union of both rests and clip.** Rejected for Floating: the
  extra-keys card is inset and round on every side, so a clip of a bigger view gets the outline
  but not the glass. The cap, overscan and anchor are the bigger view's.
- **Every piece its own glass surface.** Rejected: Floating's rows on one edge would become
  separate capsules, a different look, and the glass budget would be spent per row.
- **Drop Peek under Floating.** Rejected: it is the new-install default design and the setup every
  Floating snap above was found on. It also only removes this one case: any two rests with different cards on one host
  would snap the same way.
