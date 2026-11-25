# Compositor window policy: windows the size Linux programs expect (2026-11-25)

Status: **decided 2026-11-25**: the developer chose A for parentless dialogs and the swipe-up for
full screen; the other two were left to the agent (see Decided, at the end). Builds on
[linux-windows-wayland-2026-11-24.md](linux-windows-wayland-2026-11-24.md) (the window contract) and
changes none of its decided points: an app's main window still opens full screen in the row, and
dialogs with a parent still draw over their parent.

## Why

Seen on pong, 2026-11-25:
- **Every top-level gets a page the pane's size.** A small parentless window (an About box, a
  preferences window that names no parent, a splash, a tool palette, an X11 utility window) is
  stretched to the pane. The app either draws a big empty window or stretches its layout across a
  shape it never asked for: the "odd square resolution".
- **An X11 window's own size is overwritten.** `configure_request` (`wayland-host/src/android/xwayland.rs`)
  answers every X11 resize with the view's size, and `map_window_request` maps every non-override-redirect
  window as a page. WM_NORMAL_HINTS, `_NET_WM_WINDOW_TYPE` and fixed sizes are never read.
- **An xdg window learns its size only after it maps.** The first configure goes out with whatever is
  pending when the first commit lands, often no size. The app picks a size, draws it, and is then resized
  to the pane: a wasted frame at the wrong size, and apps that size their first window from the
  screen (Firefox, LibreOffice) guess from the output, not from the pane.
- **Protocols programs ask for are missing.** XFCE's Wayland panel asked for wlr-layer-shell and
  exited with an error (worked around for desktops in 37177acda by keeping a desktop's toolkits on
  its own X server). Text fields don't bring up a keyboard, a video player can't go full screen or
  keep the screen on, and a GTK 4 header bar can't be dragged.

The goal: a Linux program behaves under the launcher the way it does under a tiling desktop on a
phone-shaped monitor (Phosh, Plasma Mobile, Sway on a small screen), so its own assumptions hold.

## What Linux programs expect

A program decides its window's size from four things; a compositor that answers all four never
has to stretch anything.

| Signal | xdg-shell (Wayland apps) | X11 (through Xwayland) |
|---|---|---|
| Room it may use | `xdg_toplevel.configure_bounds` (v4) | the root size, `_NET_WORKAREA` |
| Size it would like | first commit after a 0×0 configure; window geometry | the size at map, `WM_NORMAL_HINTS` base/program size |
| Size limits | `set_min_size` / `set_max_size` (equal means fixed) | `WM_NORMAL_HINTS` min/max (equal means fixed) |
| What kind of window | `set_parent`; xdg_popup for menus | `WM_TRANSIENT_FOR`, `_NET_WM_WINDOW_TYPE` (normal, dialog, utility, splash, toolbar, notification), `_NET_WM_STATE_MODAL`, override-redirect for menus |

A compositor then tells the window its state: tiled on all four sides (no shadows, square corners),
maximised, full screen, activated. The host already sets tiled and activated.

## Design

### 1. Classify before the first configure

The host holds a new toplevel's first configure until its first commit, which carries the title,
app id, parent and size limits (xdg); an X11 window is classified at its map request, when its hints
are set. One pure function, `WindowClass::of(hints)` in `wayland-host/src/`, with unit tests per
row of the table below; `Shell` gathers the hints, the function decides.

| Class | How it is recognised | Where it goes | Size it is told |
|---|---|---|---|
| **Main** | no parent; normal type; resizable (max unset or larger than the pane) | its own window in the row, full screen first (as decided) | the pane, with `configure_bounds` = the pane, in the **first** configure |
| **Dialog** | has a parent (xdg parent, transient-for), or dialog type / modal | centred over the parent's window (today's path); parentless: see 3 | its own size (0×0 configure), clamped to the parent's window; bounds = that window |
| **Fixed** | min = max, or a max smaller than the pane on both axes | its own window in the row, drawn at its size, centred on the window's glass | exactly its fixed size; never stretched |
| **Transient** | splash, notification, toolbar, utility type with no parent | over the page it opened from, no place in the row, no chip | its own size; a splash goes with its app's first Main window |
| **Popup** | xdg_popup, X11 override-redirect | inside the owner's view (today's path, unchanged) | the positioner, constrained to the window (today) |

A Main window whose min size is bigger than the pane (Firefox's 500 logical px on a 519 px-wide
split, #390) is told the pane and draws what fits, as today; the rim tab's Scale is the way to get
more room, and the row never grows a page wider than the screen.

### 2. Tell X11 windows the truth

- `configure_request` honours the requested size for Dialog, Fixed and Transient windows (clamped to
  the pane) and answers Main windows with the pane, as now.
- Each X window carries its class from its map request; a later change of `WM_TRANSIENT_FOR` or
  window type (rare; Java apps do it) reclassifies once, before the window has a page.
- `_NET_WORKAREA` on the shared Xwayland's root is set to the pane size, so X apps that size their
  first window from the work area pick one that fits.

### 3. Parentless dialogs (decided: A)

GTK and Qt apps open some dialogs with no parent (file pickers launched from a shell, `zenity`,
password prompts, "Save changes?" from a closing app whose main window is gone). Two options:

- **A. Over the current page (chosen).** Drawn at its own size, centred over whatever page is in
  front, with a chip while it is open; leaving the page hides it and its chip brings it back. Gain:
  a `zenity` prompt from a terminal shows over that terminal, where the user is looking. Loss: a
  dialog can sit over a page of another app.
- **B. Its own window in the row.** As a Fixed window. Gain: one rule, every top-level has a place.
  Loss: a two-button prompt takes a full page and the user pages away from what asked for it.

### 4. States and requests the host answers

- **Full screen** (`set_fullscreen`, `_NET_WM_STATE_FULLSCREEN`): the window takes the whole screen,
  status bar, extra-keys row and rim hidden. A swipe up from the bottom edge shows the extra-keys
  row over the window for a moment (keys still reach the app; it hides again after a few seconds
  without a press); Back leaves full screen. A video in mpv, a game.
- **Maximise / unmaximise**: a Main window already fills its pane; the request is acknowledged with
  the pane size and nothing changes. **Minimise**: the launcher's Minimise (its chip stays).
- **Move** (`xdg_toplevel.move`, `_NET_WM_MOVERESIZE` move from a GTK 4 / libadwaita header bar):
  starts a Carry from that point, the same as holding Move on the rim tab. **Resize** requests
  are ignored: the pane decides the size.
- **Window menu** (`show_window_menu`, right-click on a header bar): opens the rim tab's menu.
- **Close from the app** (the app destroys its window): as today.
- `xdg_wm_base` v5+ `wm_capabilities`: advertise fullscreen and minimise, not maximise, so apps
  hide a Maximise button that would do nothing.
- Client-side decoration stays allowed. GTK 4 and libadwaita always draw their own header bar
  (they ignore xdg-decoration); the window geometry already crops their shadows, and with the
  tiled states they draw square corners.

### 5. Protocols to add

In order of what users notice, each its own slice:

| Protocol | What the user gets | Notes |
|---|---|---|
| `xdg_toplevel.configure_bounds`, `wm_capabilities` (xdg-shell v5/v6) | first windows the right size, no dead Maximise buttons | smithay has both on `ToplevelState` |
| `zwp_text_input_v3` | tapping a text field in a Linux app raises the launcher keyboard, and it goes away when the field loses focus; the IME's composing text works in GTK and Qt | the launcher keyboard and the system IME both feed it; ties into `onSystemImeRequested` |
| `xdg_activation_v1` | opening an app that is already running goes to its window; an app's "open in existing window" works (Firefox, file managers) | today the launcher matches windows to drawer entries itself |
| `zwp_idle_inhibit_v1` | a video keeps the screen on while its window is in front | maps to `FLAG_KEEP_SCREEN_ON` on the activity while that window is shown |
| `wp_presentation` | smooth video and animation pacing | the host knows each post's time |
| `zwlr_layer_shell_v1`, limited | `wofi`/`fuzzel` launchers, `mako` notifications | only the overlay and top layers with keyboard focus on demand, drawn as a Transient over the current page; background and bottom layers (wallpapers, panels, docks) are refused, since the launcher is the shell. A desktop's own panels stay inside its X server (37177acda) |
| `xdg_toplevel_icon_v1` | the chip shows the icon the app sets at run time | the desktop entry's icon stays the default |
| `wp_cursor_shape_v1`, `zwp_pointer_constraints_v1` + `relative_pointer` | correct cursors with a mouse; games and 3D viewers that lock the pointer | after the above; only with a mouse or the trackpad mode |
| `linux-drm-syncobj-v1` / explicit sync | the end of the #296 re-read window | blocked on KGSL: no fence to import. Re-check on each Mesa/Turnip bump |

### 6. Desktop sessions (rootful Xwayland)

Already built: the desktop's window is the rootful server's toplevel, `-hidpi` makes its root the
window's pixels, it resizes with each configure, and GDK_SCALE / GDK_DPI_SCALE / Xft.dpi tell the
toolkits the scale. What the policy adds:
- The desktop window is always class Main, told the pane in its first configure (no start at the
  screen's size then a step).
- **Scale changes reach a running desktop.** Today a new Scale stop applies at the next start, and
  XFCE's settings daemon writes its own `Xft.dpi` over ours once it starts, which is the likely cause of
  the layout not scaling seen on 2026-11-25. The start block sets XFCE's `/Xft/DPI` and
  `/Gdk/WindowScalingFactor` through `xfconf-query` once the session bus is up, so the session's own
  daemon carries the launcher's values; MATE and LXQt get the same through their settings keys.
- Started the way the developer starts it: from the drawer's xfce-session entry. Device checks use
  that entry, not `launcherctl linux-app open session:xfce`.

## Order of work

1. `WindowClass::of` with its tests; hold the first configure for the class; `configure_bounds`
   and `wm_capabilities`. X11 hints read at map; `configure_request` honours them. (Fixes the stretched
   windows.)
2. Placement on the Java side: Fixed windows centred at their size in their window; Transient over
   the page; parentless dialogs per the developer's choice in 3. `LinuxWindowRow` stays the pure
   owner of the frames (`dialogFrame` grows a Fixed sibling) with tests.
3. Full screen, move-to-carry, window menu.
4. text-input-v3 with the launcher keyboard, then the system IME.
5. xdg-activation, idle-inhibit, presentation-time.
6. Limited layer-shell; toplevel icon; desktop scale changes reaching a running session.

Each slice ships with its unit tests and a device-checks entry; pong rounds as usual
(`-Pwayland=true`, arm64).

## Checks (pong)

- gtk3-demo's About dialog, `zenity --info` from a terminal, GIMP's tool windows, `xmessage`, an
  X11 splash (LibreOffice): each at its own size, none stretched, none taking a page it shouldn't.
- Firefox and LibreOffice open their first window at the pane size with no visible resize.
- mpv full screen hides the chrome and Back leaves it; the screen stays on during playback.
- A GTK 4 app (gnome-text-editor): dragging its header bar lifts the window as a Carry; tapping its
  text area brings up the launcher keyboard.
- XFCE from the drawer's xfce-session: lays out at the Scale stop, and a new stop reaches it without a restart.

## Window class on the wire

The host tells the launcher a window's class in `window_created`'s flags, beside `WINDOW_X11`
(`ILinuxWindows.aidl`): bits 4–7 hold the class, `WINDOW_CLASS_SHIFT = 4`, `WINDOW_CLASS_MASK = 0xF0`;
0 Main, 1 Dialog, 2 Fixed, 3 Transient. A window's natural size reaches the launcher the way its
size does today (the reported size after its first commit). Old hosts send 0, which reads as Main:
today's behaviour.

## Decided

- Parentless dialogs: **A**, over the current page (the developer).
- Full screen: **swipe up from the bottom edge** shows the extra-keys row for a moment; Back leaves
  (the developer).
- Layer-shell: **only the overlay and top layers** (launchers such as wofi, notifications such as
  mako), each drawn as a Transient over the current page; background and bottom layers are refused
  (the agent: panels and wallpapers would fight the launcher, which is the shell).
- A Fixed window bigger than the pane: **scaled down to fit**, whole window visible; the rim tab's
  Scale shows it larger (the agent: panning a window on a touch screen fights the page swipe).
