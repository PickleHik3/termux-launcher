---
status: accepted
date: 2026-10-21
supersedes: 0008's "Home wears no status bar" amendment (2026-10-19), and 0008's two layouts
---

# One chrome across the wall: Home and the Workspace share one layout, bar and dock

Home and the Workspace are meant to read as one launcher, but each half wore chrome of its own:
Home had no launcher status bar, its own layout and a dock with five styles, while the Workspace
had the bar, the extra keys and a dock with one fixed look. A swipe between the halves changed the
top and the bottom of the screen at once.

We decided that one layout dresses both halves: the same status bar (with the same window chips,
Home floats included) and the same dock (one set of rows, one height, one **Dock design**) stand
still while the wall moves. Only the extra keys stay the Workspace's own, folding away with the
travel toward Home. A user who wants Home laid out differently opts into a **Home layout**, much as
a surface holds an override of **Base**; only then may the bar and dock change on the swipe.
Minimal mode stays the Workspace's: Home has no corner tab to leave it by, so it ignores minimal
(amended the same day; the command palette gains a Minimal action). The design, the **Search field**
and the **Drawer tile** are held by the layout. Even under a Home layout the sizes and the shape stay
linked to the shared layout (the apps row and #3 Strip row heights, the keyboard's height and chin,
Style, Corners and Margin; amended the same day), so a Home layout only changes which rows show and
where, the design and the toggles, and nothing resizes on the swipe.

Considered options: keeping the bar Workspace-only and unifying the dock alone (still a seam at the
top), folding the bar's contents into the dock (the developer's aim for a later design, but a much
larger rework of the status swipe and chips), and keeping two layouts with a status slot added to
Home's (freedom that makes the bar jump by default). The opt-in Home layout keeps that freedom for
those who want it without making it the default.

Consequences: the float chips above the dock retire, minimised floats become chips in the bar, and
the bar's CPU and RAM readings return to Home in place of the float titlebar's. The status swipe
works on Home. The house glyph leaves the bar. The Prompt line and Taskbar designs become the Search
field and Drawer tile toggles and Taskbar's run strip goes, leaving #1 Tray, #2 Beads and #3 Strip.
Upgrading users whose saved Home layout differs from the Workspace's get it as a Home layout.
The spec is `project-docs/active/chrome-unification-2026-10-21.md`.
