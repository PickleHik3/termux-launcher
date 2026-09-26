---
status: accepted
date: 2026-09-15
amended-by: 0003 (the store is no longer keyed by place; the sizes stay in it, per orientation)
---

# Keyboard height, keyboard chin and dock height live in the per-place, per-orientation layout store

The Layout editor edits one place in one orientation. Three continuous sizes it must offer were
stored elsewhere: keyboard height as a global preference with a separate landscape value, the
keyboard chin allowance as one global value, and dock bar height as a look key overridable per
place but not per orientation. We decided to migrate all three into `PlaceLayoutStore`, keyed per
place and orientation like every other layout value, with a versioned migration that seeds each
place and orientation from the value it was resolving to before.

The alternative was to leave them in their existing stores and let the editor merely surface them.
That avoided a migration but left three rows in a per-place, per-orientation editor whose scope
did not match their label, and it kept dock height entangled with appearance overrides. A size is
layout, not look, so it moves with the editor that owns it.

Consequences: dock height leaves the scopable look keys and the Appearance editor's Dock card; the
chrome geometry readers resolve the sizes through the layout store; the Layout editor's entry
snapshot must cover them so Discard reverts them.
