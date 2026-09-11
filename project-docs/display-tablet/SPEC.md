# The Display place as a tablet

Agreed 2026-09-11 on a review page. Terse copy; briefs point here.

## Findings on pong (dev 5cef7ca3)

- Keyboard-follows-text never fired: the only text signal is the X cursor *name*
  (`DisplayTextFocusPolicy`), and pong's prefix has no theme with `cursors/`, so GTK/Firefox use
  unnamed core font cursors. Trace: every tap "over (no name) -> nothing to do".
- Touchscreen mode (touchMode 2) is mouse emulation: one finger moves the pointer, drags only
  after long-press; two fingers = wheel; pinch zooms the launcher's picture
  (`TouchInputHandler.java:705-720, :692-703, :732-774`).
- Android Back on the Display place goes to X as a bare `KEYCODE_BACK`
  (`LorieHost.java:188-191`); the launcher never handles it. Docs promise otherwise.
- Stopping the pane runs `pkill -f "termux-x11 …"` (`TermuxActivity.java:13192-13201`); X clients
  are never told, hence Firefox's "closed unexpectedly" dialog. `X11WindowList.close()`
  (`_NET_CLOSE_WINDOW`) exists and is unused.

## Decisions

| # | Decision |
|---|---|
| D1 | Touchscreen mode delivers **real XI2 touch** (same pipe as Direct touch) plus launcher extras. Trackpad and Direct touch unchanged. |
| D2 | Text signal: **native patch 0003** names core glyph cursors in the server (`xterm`, `left_ptr`, …). No theme dependency. |
| D3 | Back: **keyboard first** (lower an auto-raised keyboard), otherwise Back into the X app (XF86Back; Alt+Left fallback if Firefox ignores it on pong). |
| D4 | Chip ×: **tap the already selected chip again** reveals a 32 dp × for ~4 s; any other touch hides it. Column chips (26 dp) stay select-only. |

## Behaviour

**Touchscreen mode.** Tap, one-finger drag, pinch, two-finger drag and long-press reach the app as
touch; legacy clients get the server's pointer emulation (tap = click, drag = button drag). The
launcher's own pinch zoom is off in this mode (the scale rail covers magnification). Firefox needs
`MOZ_USE_XINPUT2=1`, added in `X11LinuxAppRunner`'s env funnel. Text-focus policy keeps its gate
(`Touchscreen || padUp`); tap detection stays in `X11PaneFrame.watchForTap`.

**Back.** On the Display place: if the text-focus policy raised the keyboard (AUTO_OPEN) or the
frame shows the keyboard, lower it; else send XF86Back to the focused X window.

**Chips.** Second tap on the selected chip → × at the trailing end. Terminal ×: same path as
current-window close (kill sessions, remove window, fall back), existing running-process
confirmation kept; needs a close-by-index seam beside `selectWindow(int)`. Display ×:
`X11WindowList.close(id)`, no confirmation. Reveal never triggers on a horizontal chip scroll or a
status-bar pull (pure `ChipRevealPolicy`, tested).

**Clean stop.** Power tab: `_NET_CLOSE_WINDOW` to every listed window, wait for `_NET_CLIENT_LIST`
to drain (≤ 3 s), then the existing pkill. Confirmation dialog unchanged.

## Build plan

| # | Branch | Delivers | Depends on | Gate |
|---|---|---|---|---|
| 1 | `feat/x11-clean-close` | `X11WindowList.close` reachable from the app (close by window id); `stopEmbeddedDisplay` closes all, drains ≤ 3 s, then pkill; terminal close-by-index seam; unit tests | — | unit; pong: stop pane with Firefox open, reopen, no restore dialog |
| 2 | `feat/chip-close` | `ChipRevealPolicy` + tests, × on row chips for both places, wired to phase 1 seams, strings | 1 | `TerminalWindowBarTest`; pong: scroll/pull never reveal, × closes both kinds |
| 3 | `feat/x11-tablet-touch` | Touchscreen = real touch, launcher pinch off in mode 2, `MOZ_USE_XINPUT2`, Back routing per D3, UPSTREAM.md deviation, docs | — | unit where pure; pong Firefox: one-finger scroll, pinch, Back; modes 1/3 unchanged |
| 4 | `feat/x11-cursor-glyph-names` | `ci/x11-patch/0003-*.patch` naming glyph cursors, README/UPSTREAM.md, prebuilts rebuilt via `app/x11-local-build.sh` and committed | — | pong logcat: xterm and Firefox report `xterm`; tap in a field raises the keyboard |

## Risks

- Under real touch GTK may not update the window cursor as it does for pointer motion; the
  cursor-name signal could still miss in Firefox. Fallback: fcitx5 focus path (already wired).
- Legacy X apps get no touch scrolling; Trackpad mode remains for them.
- Phase 4's pin must match in `x11-server/UPSTREAM.md`, `version.gradle` and the workflow.
- D8 (landscape Display touch acts like Trackpad) lives in the same handler; phase 3 logs
  source/tool type on pong.
