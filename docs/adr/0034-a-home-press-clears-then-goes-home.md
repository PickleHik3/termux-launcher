---
status: accepted
date: 2026-11-24
---

# A home press clears what is open, then goes home

Spec: `project-docs/active/launcher-screens-host-2026-11-24.md`.

A home press used to mean different things to different surfaces. `OverlayRegistry.closeAll(HOME)`
dropped each open surface with no motion, the wall then went to the home page whatever was open, the
Appearance editor asked about unsaved changes while that page switch ran under its question, and a
Settings page two or three deep was torn down on screen and flashed before Home appeared.

## Decision

1. With nothing open, a home press does what it did: the home page when the launcher is in front,
   the page that was left when it returns from another app (or Overview, by its setting).
2. With something open, a surface holding unsaved changes asks first, over itself; nothing else
   moves while it asks.
3. Then everything open leaves together in one **Home exit**: one group fades and shrinks slightly
   while the page underneath settles, about 250 ms, alpha and scale only. The press lands on the
   page underneath; the wall does not page. A backstop applies the end state if the motion never
   runs.
4. `OverlayRegistry` is the one owner of this; there is no second host for full-screen screens.
5. Settings runs in its own task, so a home press leaves it behind rather than clearing it on
   screen.

Back and ✕ are unchanged: they step out one layer at a time.

## Considered

- **Unwind each layer with its own exit.** Every close looks the same, but each layer underneath is
  seen in turn: the wrong order this decision exists to remove.
- **A new host beside `OverlayRegistry`.** A clean start, but two owners of Back and Home, and the
  ordering bugs live in the hand-off between them.
- **A home press from a screen goes to the home page.** Home would mean one place everywhere, but
  the developer wants a press that closes a screen to stay on the page the screen was opened from.

## Consequences

- Every registered surface changes how it leaves on a home press and needs a check.
- A surface that holds a draft must say so, or a home press discards nothing and asks nothing.
- The Home exit is the only writer of its views' alpha and scale while it runs (ADR 0028).
