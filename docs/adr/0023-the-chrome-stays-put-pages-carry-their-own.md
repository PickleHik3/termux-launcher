---
status: accepted
date: 2026-11-08
amends: 0020 §6 (the status bar's compact or expanded state is global, not per kind; dock design, search field and drawer tile are global), 0018 §4 (a kind's rest no longer holds the dock's design or the status bar's state: those are global; Home-only and Workspace-only elements belong to their page), 0017 (Margin is a Base-only cell)
---

# The chrome stays put; pages carry their own

Spec: [`project-docs/active/swipe-model-2026-11-08.md`](../../project-docs/active/swipe-model-2026-11-08.md).

On 2026-11-08, after rounds 17–22 of swipe measurement on pong, every page swipe still had two
main-thread stalls: a 37–51 ms frame at the first finger movement, where the chrome's look and
position are recomputed, and a 30–46 ms frame at landing, where about 18 ms of layout moves
bars between edge stacks, rebuilds the extra-keys host, installs the A–Z host and re-dresses the
dock, because Home and the Workspace arrange their chrome differently. On the drawing side,
about ten screen-sized glass images were uploaded to the GPU again on every swipe.

ADR 0018 §4 already asked that a crossing lay out nothing. It could not, because the chrome
shared by every page also carried things only one page has. The developer decided the following,
looking at it as a desktop user would: the panel and the dock stay put across workspaces; what
belongs to a workspace travels with it; colour may differ per workspace.

## 1. Shared everywhere

One value for every page (Home, Workspace, Assistant, Display), held by Base:

- Margin (done in round 21, `LookGridMarginMigration`).
- The status bar's edge (top or bottom) and its compact or expanded state.
- The dock's design, search field and drawer tile, and the dock's height.
- Keyboard height and chin (already shared, ADR 0020 §3).

Each per-kind value folds once into Base, taking Home's, as Margin did. The Appearance editor
shows these only on the Global card.

## 2. Page-owned elements travel with their page

What only one kind of page has is part of that page, not of the shared chrome:

- Home: the apps row, the A–Z bar, the widget grid and the search field.
- Workspace: the extra keys and the minimal-layout tab.

They are laid out once on their page and slide with it. Mid-swipe the A–Z bar leaves with Home
instead of switching at landing. The shared chrome never re-lays out on a landing.

## 3. Look per page, as overrides

Wallpaper dim, Frost, Opacity, Tint and Grain follow Global unless a page sets its own (for
example a darker wallpaper on the Workspace). A look difference costs no layout.

## 4. A swipe moves prepared layers

When the finger goes down, the launcher prepares each side's chrome look. Drag frames only
translate and crossfade those layers; no per-frame recompute of the chrome spec. The terminal
keeps drawing live through the whole swipe: no snapshot stands in for it, and no pane freezes.
Content that changes mid-swipe (a clock tick, a notification, a widget) updates as it does today.

## 5. Kept glass, budgeted by RAM

Glass images stay on the GPU between swipes and are uploaded only when their content changes.
The budget follows the device's RAM: ≤4 GB 11 MB, 6 GB 17 MB, 8 GB 34 MB, 12 GB+ 56 MB (about
5.6 MB per screen-sized image). On memory pressure (`onTrimMemory`) or over budget, the images
of the page farthest from the current one go first and are rebuilt on its next swipe. Every
device shows the same glass; a smaller one uploads more often. Keeping glass never stops the
terminal drawing or holds back widget updates on pages off screen.

## 6. Lighter glass while moving (opt-in)

A switch in Settings → Performance, off by default, renders the blur at lower resolution while a
page moves and at full resolution at rest.

## Considered options

- **A hidden second copy of the chrome, laid out for the destination during the swipe.** Rejected:
  a second view tree held for the life of the process, layout done twice per swipe, and a
  one-frame flash whenever the copies differ.
- **Prepare the destination's arrangement during the drag on the shared views.** Rejected
  (round 22): those views are on screen mid-swipe, so moving them early changes what is seen.
- **Keep per-page panel and dock geometry.** Rejected: it is what makes every landing re-lay out,
  and a desktop panel does not move between workspaces.
- **A lower blur for everyone.** Rejected: glass may cost battery, never look (glass UX stance);
  it stays opt-in.
- **One fixed glass budget.** Rejected: one number either starves big phones or pushes apps out
  of memory on small ones.

## Consequences

- The landing frame commits nothing but the page; the drag-start frame stops recomputing chrome.
- Users lose per-page status bar edge, status bar state and dock design; their Home values win.
- Each page holds its own small views (no bitmaps): a few hundred KB.
- Up to 56 MB of GPU memory on large phones; evicted under pressure.
- ADR 0020's storage and migration plan is unchanged; its kind documents simply carry fewer
  per-kind values.
