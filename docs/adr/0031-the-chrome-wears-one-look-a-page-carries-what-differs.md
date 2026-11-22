---
status: accepted
date: 2026-11-19
supersedes: 0013 (each half wears its own Look); 0017 §2 where it gives a look value a page row; 0018 §3 (the kind row of the look grid)
amends: 0017 §1 (an element no longer carries every property: a surface may own Opacity and Blur, nothing else) and §3 (presence is still the override, in two places not six); 0029 §8 (a surface both ends share has one look, so nothing moves between looks; its second sentence stands); 0025 (the Terminal's pages share one background, not one look of the chrome)
---

# The chrome wears one look; a page carries what differs

Decided with the developer on 2026-11-19, in a review of the Appearance settings and a grilling
round after it. The rows, card by card, are in
`project-docs/active/appearance-one-look-2026-11-19.md`.

Since ADR 0013 a surface's glass could differ between Home and the Workspace, and the look grid of
ADR 0017 and 0018 kept that as a row per kind of page. The chrome stays put while pages move
(ADR 0023), so every chrome surface whose look differed had to be carried from one look to the
other in step with the swipe: the dock's sheet and frost, the bars' frost and ink, the keyboard's
bed, the top band. On `fix/wr-peek-blend`, nine of the fourteen crossing fixes of 11-18 and 11-19
correct something that can only happen when the two ends' looks differ, in about a thousand lines
of blend code and as many of tests. The editor paid as well: ten glass rows repeated on eight
cards, an Applies to row on four of them, about 170 places a value could be stored, and four
different answers to "what does a Global change do to a narrower value". What users had asked for
with a look per side was a darker terminal (2026-10-24), which is a property of the terminal's own
pages, not of the chrome around them. The shipped looks used the kind row for one value, the
keyboard's frost on Home.

We decided that **the chrome wears one look on every page, and a page carries what differs**.

## 1. Two places

A look value lives on **Global** or on one **surface**. There is no row for a page or a kind of
page. A surface's value is read from its own cell if it holds one, else Global, else the shipped
look. Presence is still the override (ADR 0017 §3): an absent key follows, and Reset deletes the
key.

## 2. What a surface may own

The status bar, the dock and the keyboard may each hold their own **Opacity** and **Blur** (the
Frost stop). A surface holding its own Blur wears that stop's grain; one following Global's Blur
wears Global's Grain (developer, 2026-11-19). The keyboard keeps its keys' opacity. Nothing ships
per surface: on a fresh install the keyboard wears Global's glass like the dock and the status bar
(developer, 2026-11-19). Tint and its colour, Grain,
Rim, Edge, Depth, Corners, Vibrancy and Margin are Global's alone. Widgets hold nothing: they wear
Global's glass while Glass widgets is on, and each widget still chooses for itself. Sheets (the
drawer, folders, menus, the command palette, notices) hold nothing either and wear Global with a
floor on opacity, set in code, so text over a busy page stays readable. The keyboard has no floor
and no rule of its own: floating or docked, it wears the same look.

## 3. Own stays

Nothing set on Global, a preset included, clears a surface's own value. This replaces "last change
wins" (2026-11-04), which existed because a moved slider became an own value without saying so.
An own value now shows a reset arrow in a fixed slot at its row's end; the same arrow in a surface
card's header puts that surface back on Global, and in the Global card's header every surface,
after asking once.

## 4. The Terminal's background is the page's own

The terminal's panes have **Opacity** and **Blur**, and the Terminal's pages have a **Wallpaper
dim** for everything around the panes. None of the three follows Global or takes a preset, and
they travel with the page as the ground and the pane slabs already do. The Terminal ships darker
than the chrome's glass. Home's pages have no wallpaper dim.

## 5. Glass shows the ground beneath it

Chrome glass draws the wallpaper dim of the page under it, the seam included while a page edge
passes beneath. A dimmed Terminal page therefore darkens the bars around it without the bars
holding a second look. This section is accepted as the intent and is prototyped before it is
built: if it cannot be drawn within the frame budget, the chrome keeps the same picture on every
page and this section is withdrawn, not the decision.

## 6. Words

**Global** replaces Base, in the app, the code, the docs and `launcherctl`. **Terminal** replaces
Workspace as the name of the side, its card and its rows.

## 7. Upgrading

Nothing is converted. The change ships with a one-time Appearance reset (`AppearanceReset.NUMBER`),
which drops every stored look value; the per-kind and per-page keys are deleted with it.

## Considered options

- **One look, plus a "Solid around the terminal" switch.** Rejected: the chrome would still have
  two looks, so every blend stays, for one case in place of ten properties.
- **Keep a look per side.** Rejected for the cost above.
- **A surface owns nothing.** Rejected: a keyboard that has to be more solid than the dock would
  need a rule in code where a slider is plainer.
- **A surface owns every property, everywhere.** Rejected: ten rows on each of three cards, most
  never touched, and a tint or rim per surface is not what makes a surface readable.
- **Global wins over own values.** Rejected once own values are marked and can be reset one by one
  or all at once.
- **The terminal's background follows Global until changed.** Rejected: it is the 10-24 complaint
  again, with the terminal darkening when something else was meant.

## Consequences

- `LookGrid` loses its kind and page rows; only Opacity and Frost keep an element column, for the
  status bar, the dock and the keyboard. `launcherctl look` loses `--kind` and `--page`.
- The crossing has no look to blend for a shared surface. What remains differs by shape or by
  presence, not by look: the Peek dock's card against the slab, the keyboard up at one end and
  down at the other, the rows each side shows.
- The editor has six cards (Global, Home, Terminal, Status bar, Dock, Keyboard) and no Applies to
  row. The Workspace, Widgets and Menus and dialogs cards go.
- A dock that is clear on Home and solid around the terminal is no longer possible, nor a surface
  with its own tint, grain, rim or corners.
