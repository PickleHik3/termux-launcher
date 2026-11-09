---
status: proposed
date: 2026-11-09
supersedes: 0020 §4 (the Display's own layout), 0022 §2 (the Assistant's own dress, layout document and look cells; "minimal mode does not apply")
amends: 0020 §1 and §5 (three live documents, two retired), 0022 §1 (the Assistant page is a Workspace page)
---

# The Workspace is Terminal, Display and Assistant pages

On 2026-11-09 the developer found that the Assistant page could not be reached by the Appearance
and Layout editors as the Terminal and Display pages are, and that the Terminal and the Display
did not share a layout. They asked that Terminal pages, Display pages and the Assistant page be
one entity, the Workspace, so a swipe between any two of them changes no chrome and a swipe
between any of them and Home is the one crossing the Workspace already has.

ADR 0020 §4 gave the Display a layout of its own, copied from the Terminal's at migration and
edited apart from it after. ADR 0022 §2 gave the Assistant its own dress (the status bar alone),
its own layout document seeded from Home's, its own look cells (`@kind:assistant`) and no
minimal mode. Each made a swipe between two Workspace pages a change of layout, and the editors
opened on one of them edited a layout the others never wore.

We decided the following.

## 1. One Workspace

Terminal pages, Display pages and the Assistant page are the Workspace. They wear one layout
document (`terminal`), Workspace-minimal (`terminal_minimal`) while minimal mode is on, one dress
(the Terminal kind's visibility) and one look (the Terminal kind's cells). The editors opened on
any of them edit that one layout and look and call it "Workspace layout" and "Workspace pages".
Home keeps its own (ADR 0020 §1).

A slide between two Workspace pages crosses no seam (`SeamTravel.crosses`); a slide between Home
and any Workspace page is the Home–Workspace crossing, ends dressed by the Workspace's arrangement.

## 2. What stays per page

- Content: the Assistant page's pane keeps its own insets (12 dp sides, 8 dp ends), its body
  swipe, and its ground without the terminal's tint; the Display page keeps its edge-only
  swipe-out.
- The window bar's chips stay off the Assistant page: it is no window of the session's row, so no
  chip could stand for it. It wears the bar itself as the Workspace does.
- The remembered keyboard (open or closed) stays each place's own: the Terminal's in the
  Workspace document, the Display's and the Assistant's in their flat `place.<page>.keyboard_open`
  keys.

## 3. Migration

The Terminal's document wins; nothing is copied. The `display` and `assistant` documents are
retired: never read, never written, left in the `layouts` file as they were so a downgrade finds
them (as ADR 0020 §8 left the flat keys). `LayoutKind.DISPLAY` and `ASSISTANT` stay as names that
resolve to the Workspace's document, so the flat `layout.display.*` and `place.dress.display.*`
spellings and `launcherctl layout get display` keep working. Look cells saved under
`@kind:display` or `@kind:assistant` stay in the preferences and are no longer read; the Workspace
card still writes `@kind:display` beside `@kind:terminal`, for a downgrade. A user who edited the
Display layout or look apart from the Terminal's loses that difference.

## Considered options

- **Fold the Display document's own values into the Terminal's once.** Rejected: the Terminal is
  what most users edited, and a field the Display held alone (a toggle, a bar order) would change
  the terminals on upgrade.
- **Keep the Assistant on Home's dress beside a shared Terminal–Display layout.** Rejected: the
  editors would still edit a third layout from the Assistant page, and a swipe to it would still
  re-lay out the chrome.
- **Share the remembered keyboard too.** Rejected: the keyboard coming up over the Display's
  picture because a terminal was left typing is not wanted.

## Consequences

- The Assistant page now shows what the Workspace shows (extra keys, dock rows, the window bar
  without chips) unless the user turns them off. Its pane stands in the wall's canvas, which ends
  above the bottom chrome as it does for a terminal pane; that the composer clears the extra keys,
  the dock and the soft keyboard is to be checked on a device.
- Minimal mode now dresses the Assistant page as it does the windows.
- `AssistantSeed` and `AssistantLookSeed` are gone; adding the Assistant page seeds nothing.
