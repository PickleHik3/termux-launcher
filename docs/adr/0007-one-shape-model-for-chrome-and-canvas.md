---
status: accepted
date: 2026-10-01
amends: the Layout canvas artwork of `project-docs/active/appearance-layout-editor/SPEC.md` §5 (the `termux-layout-assets` pack supersedes `termux-layout-elements-v2`)
---

# One shape model decides the chrome's shapes, for the live launcher and the layout canvas alike

Docked and Floating were one dock preference (`app_launcher_dock_style`) that `TermuxActivity`
consulted at about forty call sites, each piece of chrome cutting its own outline: the status bar,
the dock plank, the off-dock plank, the A–Z capsule and the keyboard, with the pane's slab and rim
inset on their own rule. Nothing clipped the assembled frame. The layout canvas
(`LayoutCanvasView`) drew a third version: every block a separate 6dp card, the selection a separate
stroke, the lifted copy a re-drawn card. The `termux-layout-assets` pack (developer, 2026-10-01)
widens Style to the whole chrome — Docked joins every piece flush into one frame whose only rounded
corners are its exposed outer ones, and the pane becomes an opening with no rim — and requires that
one shape drive the fill, the selection outline and the lifted copy.

We decided on one pure shape model: from the layout (edges, order, hidden, keyboard form and
shown) and the Style, it yields the shapes of the chrome — which pieces share a card, which edges
join, which corners round, where the opening's edge runs — and both the live chrome's outline
providers and the layout canvas's fill, selection outline, lifted copy and placeholder read it.
A lifted element asks the model for the shape it would have at the drop target it hovers.

The alternatives were to redraw the canvas from the pack first and move the live chrome later, or
the reverse. Either is less work at once, but for a while the editor would show shapes the launcher
does not draw (or the other way round), which is the mismatch the pack exists to remove, and the
forty branches would have to be rewritten twice.

Consequences: `isRoundedDockStyle()` branches in the activity give way to the model's answers;
the stored values become `docked`/`floating`, the per-surface corner and side-gap overrides and
`terminal_flush_dock` are retired by a one-time migration; Corners and Margin apply under Floating
only; the joined pieces drop their hairline separators, and under Docked only the opening's edge
carries rim light and refraction.
