---
status: accepted
date: 2026-11-27
---

# One control bar; windows keep every touch

Spec: `project-docs/active/tiling-desktop-2026-11-27.md`. Builds on ADR 0037.

Linux apps take every touch on their own content, so a tiling desktop on a touch screen needs a
place that belongs to the launcher, the touch version of the Super key. Today that job is spread
over a top status row, a dock, an extra-keys row, body swipes on terminals, edge drags, held marks
and corner tabs, and terminals and apps answer differently.

## Decision

1. One **control bar** at the bottom of every place, Home included, replaces the dock, the
   launcher's status row and, by default, the extra-keys row. Portrait: two rows, where you are and
   the window in front above, the tools for what is in front (on Home, the pinned apps) below; one
   row while typing and always in the Terminal launcher mode; a rail down one side in landscape.
   It rides on the keyboard when the keyboard is up.
2. Sliding along the bar's window marks changes window, sliding up raises the Workspace list,
   holding opens Overview, search opens anything. A window's own actions are on its held mark;
   windows carry no title bars or handles.
3. Touches on a window always belong to its content, terminal or app. Navigation is the bar,
   **navigation mode** (windows take no input; one finger slides over the whole stack, a long press
   carries a window to a new place) and the **border hold**, which on any edge of a window steps
   the user one window or Workspace that way.
4. Android's own status bar stays shown; the launcher's expanded status bar retires.
5. Hardware and launcher-keyboard bindings are Super-based (Hyprland and niri defaults), replacing
   Ctrl+Alt; the launcher keyboard gains a Super key with live hints while it is held.

## Considered

- **A top bar like waybar.** Familiar, but out of thumb reach on a phone.
- **Two-finger gestures anywhere.** No bar at all, but hard one-handed, and apps lose pinch.
- **Screen-edge swipes.** No bar, but Android's Back owns both side edges.
- **One row always.** Thinnest, but in portrait only about two tools or pinned apps fit.
- **Terminals keep body swipes.** Keeps a habit, but terminals and apps then answer the same
  gesture differently.

## Consequences

- The dock styles, the status row and its expanded form, the extra-keys row's default place, the
  rim and corner tabs and body-swipe paging are retired; the extra-keys row remains an option for
  people typing with the phone's own keyboard.
- The bar, the keyboard and a window share the screen's height; a portrait terminal with the
  keyboard up gets what the folded bar and keys leave.
- Notifications and the clock live in Android's status bar and Home widgets, not in the launcher's
  chrome.
