---
status: accepted
date: 2026-11-27
amends: 0038 §5 (bindings start from a leader, by default the Terl key, not Super)
---

# The Terl key is the leader

Spec: `project-docs/active/tiling-desktop-2026-11-27.md`, section "Keys". Builds on ADR 0038.

Desktop bindings need a leader that never collides with what the thing in front expects. ADR 0038
chose Super, as Hyprland and niri do, with the keyboard's Meta key as the alternative. On a phone
both fail: Android takes Meta and Super from hardware keyboards for its own shortcuts before an app
sees them, and Linux apps and desktops running in Terl's windows expect to receive Super themselves.
Today's Ctrl+Alt bindings, meanwhile, work only in terminal panes.

## Decision

1. **The Terl key**, a key of Terl's own that no app ever receives, is the default leader. On the
   launcher keyboard it is a north-east swipe on Ctrl (a plain tap stays Ctrl): swipe and let go for
   one chord, swipe and hold for the hint card.
2. **The leader is a setting**: the Terl key, Ctrl+Alt, or Custom (two or more keys; all modifiers are
   held, a chord with a normal key is a prefix). Custom refuses chords Android takes on hardware
   keyboards. With the Terl key as leader, a hardware keyboard uses Ctrl+Alt.
3. **One rule on every screen**: Terl takes a leader chord only when it is bound; anything else goes to
   whatever is in front, whether a terminal, a Linux window, the X11 Display, the Assistant or a field.
4. **Bindings are leader-relative**, so changing the leader keeps them.

## Considered

- **Super, with Meta as the alternative** (ADR 0038 as written). Familiar to Linux users, but Android
  swallows both on hardware keyboards and Linux apps lose their own Super shortcuts.
- **Ctrl+Alt by default.** Works from hardware already, but takes chords Linux apps use, and on the
  launcher keyboard it is two keys to hold.
- **A dedicated key, or a long press on space, for the Terl key.** Visible, but costs every bottom-row
  key width, or delays every press and fires while typing.

## Consequences

- Hardware keyboard users with the default leader press Ctrl+Alt where the screen shows the Terl key;
  the hint card and Settings name the chord for each.
- Every screen's key path has to ask the binding table first, not only the terminal's (today the X11
  Display and Linux windows never do).
- v1 users who customised their bindings keep them, with Ctrl+Alt as their leader.
- The clipboard switch moves from Ctrl's north-east corner to Alt's.
