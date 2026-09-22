# Home screen widgets — improvement spec

Agreed 2026-09-22 on the review page, from the Lawnchair 16 comparison in
[`lawnchair-16-comparison.md`](lawnchair-16-comparison.md). That document is the evidence — every
file and line behind the claims here is in it, and nothing below restates it. This one is the
build plan.

Base: `dev` @ 6552de81.

## Decisions

| # | Question | Answer |
|---|---|---|
| D1 | Should the widget wall survive a device-to-device restore? | **No.** Set them up again. The only work is making the loss quiet and correct instead of a screen of error tiles. |
| D2 | Should widgets wear the launcher's colours or the system's? | **The system's.** Item 5 is dropped. (First marked build, then resolved to drop on the page.) |
| D3 | Does adding a widget stay a tap or become a drag? | **A drag** from the picker list onto the cell, accepting the size. |
| D4 | Rotation loses the layout. | **Each orientation keeps its own positions.** A schema change, not accepted behaviour. |
| D5 | Work-profile widgets: their own tab? | **No.** The badge is enough. |

## What is being built

Nine items, numbered as on the review page. Item 5 is dropped by D2 and does not appear.

1. **Real previews in the picker.** Implement the platform's precedence — `getWidgetPreview`
   (API 35, gated on `generatedPreviewCategories`), then `previewLayout` (API 31), then today's
   bitmap. Tiers 1–2 render into a real `AppWidgetHostView` with no bound id. Every tier wrapped
   against `NoSuchMethodError`/`NoClassDefFoundError`.
2. **Cards shaped like the widget.** Card size from the item's existing span, snapped to a small
   set of templates so row heights stay uniform; preview scaled in and clipped to the widget radius.
3. **Resize pushes its neighbours.** `WidgetEditPolicy.resize` gains the displacement pass
   `snapMove` already has, returns the displacement map in the `Candidate` field that exists and is
   documented as always empty, previews it with the move path's slide animation, commits through
   the existing atomic batch.
4. **Updates held during a gesture.** `begin/endDeferringUpdates` on
   `SafeLauncherAppWidgetHostView`, stashing the latest `RemoteViews`, released on gesture end or
   after a 1 s timeout. Local only — no provider is told to stop sending.
6. **Corners that respect the widget.** Clip the child with `android:id/background` rather than the
   cell; clip nothing when that view already sets `clipToOutline`; radius is
   `min(ours, system_app_widget_background_radius)`.
7. **Size options checked against the live bundle.** Read `getAppWidgetOptions` back and skip the
   write only when the live `OPTION_APPWIDGET_SIZES` matches. The write wakes the provider process.
8. **Quiet loss after a restore** (D1's small form). No id remapping, no backup agent. A restored
   or otherwise invalid id set must clear to an empty wall, not a grid of error tiles, and the
   reconciliation path must say so once rather than per widget.
9. **Drag from the list onto a cell** (D3). Hold a picker card, carry it onto the grid, bind on the
   way, drop where the finger is. The gesture has to survive the picker sheet dismissing under it.
10. **Per-orientation layouts** (D4). Each orientation stores its own cell assignments. First
    rotation into an orientation still seeds from a reflow of the other; after that each side is
    stable. Schema change with migration from the current single set.

## Build plan

| Phase | Branch | Deliverable | Depends on |
|---|---|---|---|
| P1 | `feat/widget-picker-previews` | Items 1 + 2 — tiered previews, shaped cards | — |
| P2 | `feat/widget-small-fixes` | Items 4, 6, 7 — deferred updates, corner policy, live size check | — |
| P3 | `feat/widget-resize-displace` | Item 3 — resize displaces, with its own tests | — |
| P4 | `feat/widget-orientation-layouts` | Item 10 — per-orientation records + migration | — |
| P5 | `feat/widget-restore-quiet` | Item 8 — quiet loss, no tombstone screen | P4 (same record schema) |
| P6 | `feat/widget-drag-to-add` | Item 9 — drag from the list onto a cell | P1 (picker sheet) |

P1, P2, P3 and P4 are independent and run in parallel. P5 waits for P4's schema. P6 waits for P1
and goes last.

P2 also folds in the six hard-coded English strings in `WidgetPaneController.messageFor` (:933-945),
which sit in a file that phase already opens.

## Gates

- `./gradlew testDebugUnitTest` green on the merged result before each merge.
- P3, P4 and P6 change gestures or persistence: each needs a Waydroid run of its own behaviour
  before merge, not only unit tests.
- P4's gate is specifically portrait → landscape → portrait returning the original arrangement,
  and an existing single-set layout migrating without moving.
- P1's preview work is measured before it is sized: count how many installed providers actually
  offer tier 1 or tier 2 on a real device. The claim that most modern widgets ship `previewLayout`
  is an inference, not a survey.

## Open, not blocking

- D4's rotation loss is **read from the code, never observed**. P4 starts with a test that
  reproduces it; if it does not reproduce, the phase stops and reports before changing the schema.
