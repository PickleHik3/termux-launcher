# Pane wall, home-screen pane and X11 pane — feasibility study

Research record, 2026-08-31. Nothing here is implemented; the implementation plan is written when
the work starts. This exists so the decisions below don't get re-litigated from scratch.

## The vision

Treat the whole terminal/pane area as one wall with designated sections the user moves between:

- a **home-screen pane** — a traditional Android home surface (app widgets, app icons, folders),
  absorbing what today lives in the status-bar pull-down;
- the **terminal area** — the existing sessions/windows/pane trees, unchanged;
- an **X11 pane** — an embedded X server surface running a Linux desktop environment.

Navigation between sections is undecided (niri-style workspace moves, multi-finger swipes,
buttons); it gets decided at implementation time, with the constraint noted under *Gestures*.

## Findings

### The X server: fork termux-x11 — it is the only viable base

- A non-system, sideloaded HOME app **cannot embed another app's surface** on Android 14–16:
  ActivityView is gone, TaskView needs `INTERNAL_SYSTEM_WINDOW`, trusted virtual displays need
  `ADD_TRUSTED_DISPLAY`, and Jetpack activity-embedding only splits activities from one's own
  manifest. An X11 *pane* therefore means running the display server in our own process — owning
  the code.
- termux-x11 is the right code to own. It is a real Xorg port ("Xlorie" DDX over 16 vendored
  freedesktop submodules) with the only genuinely accelerated path (`AHardwareBuffer` zero-copy
  rendering, shared-memory frame sync, DRI3 via Turnip/Zink or virgl through standard Termux GPU
  packages). GPLv3, same as this app. Actively maintained (nightly builds, ~100 commits/90 days).
- Alternatives were examined and rejected:
  - **Winlator's pure-Java in-process X server** implements only the protocol subset Wine needs
    and is woven into Winlator's internals; pointing a real DE and arbitrary X clients at it means
    filling in extensions indefinitely. Right choice only if the mission were Windows gaming.
  - **Termux:GUI** is not a display server — it renders native Android views for programs written
    against its own socket protocol. Cannot host a DE. Possibly interesting later as a separate
    feature; its abstract-socket protocol is a good pattern reference.
  - **VNC / XSDL** have no GPU path and worse latency; they only made sense as zero-integration
    options.
  - **Wayland-in-a-pane** has no mature implementation — termux-x11 itself began as termux-wayland
    and abandoned the direction; the DE ecosystem we would host is X11-first.
- The embedding refactor is **unprecedented** — no known fork hosts the display as a non-fullscreen
  child view. The cost is concentrated in the Android shell around the server: `LorieView` /
  `MainActivity` assume fullscreen, `singleInstance`, ownership of insets and keyboard workarounds,
  and being the sole broadcast handler. The server core needs little change. That is exactly why
  switching bases doesn't pay: it would trade away the hard-to-replicate core to save work on the
  easy-to-replicate shell.
- Fork shape: mirror the MNN approach — upstream tree plus a small committed patch set (the
  rewiring is on the order of a dozen Java files; the native submodules stay untouched), so
  nightly-cadence upstream merges stay cheap.
- The termux-side `termux-x11` loader signature-checks and class-loads the target APK
  (`shell-loader` bakes in package name + signing hash; `TERMUX_X11_OVERRIDE_PACKAGE` exists).
  It must be rebuilt per edition and shipped through each edition's package channel.

### Ship the server in the APK; the desktop environment is a download

- The arm64-only native X server adds roughly 3–14 MB to the APK (upstream's universal debug APK
  is 14.6 MB across four ABIs). Bundling removes the second-app problem entirely: nothing to
  install, version-sync or re-sign against.
- The DE is unavoidably a post-install download (~400–600 MB for xfce4 + basics, ~1.2–1.5 GB
  installed), so the preferences toggle triggers the package-install flow, never an APK download.
- No store size limits apply (GitHub releases). GPLv3 compliance is already met; the vendored
  Xorg components' MIT notices must be preserved.

### The pane tree will not absorb non-terminal panes as a new leaf type

- `TerminalPaneController`'s tree is `TerminalSession`-typed end to end — leaves, view caches,
  the `Host` seam, workspace persistence, keybinds, IME routing all assume a terminal underneath.
  Generalizing `Leaf` to a pane-type-polymorphic abstraction would touch all of it.
- The cheap, safe shape is a **new outer container of full-screen slots** around the existing
  `terminal_pane_host`: home pane / terminal area (existing controller untouched) / X11 pane.
  The X11 pane wraps the forked display view the way `TerminalView` wraps `TerminalSession`.
- This inverts today's architecture, where the terminal is the one canvas and everything else is
  chrome drawn around it (AGENTS.md: "the terminal is the home screen"). That is a product
  decision being made deliberately, not an accident of implementation.

### Home-screen pane: mostly promotion of what exists

- The widget stack already exists in the pull-down drawer: `LauncherAppWidgetHost` (crash-isolated
  host views), `LauncherWidgetRepository` (durable grid store with pages), `WidgetPaneView`
  (swipe paging with dots), picker and catalog. A full home pane promotes this machinery rather
  than rebuilding it.
- What's new is Launcher3-pattern interaction (long-press lift/drag, grid-snapped resize calling
  `updateAppWidgetSize`, remove target) and icon/folder plumbing (`LauncherApps.Callback` for
  package events, `PinItemRequest` for modern pin-shortcut flow; folders are launcher-internal
  data, no special API).
- Watch-items: Android 14/15 background-activity-launch tightening silently breaks widget clicks
  if any click-proxying sits between the widget and its `PendingIntent`; on every resize the host
  must push `updateAppWidgetOptions` or responsive widgets pick wrong layouts.

### Gestures

- One-finger vertical drags are triple-claimed on the wall: pane navigation vs scrollable
  RemoteViews widgets vs terminal touch. The safe vocabulary is multi-finger or edge swipes for
  section navigation (trivially disambiguated by pointer count before children see the event),
  with one-finger swipes allowed only from empty home-pane space.
- No swipe-navigation exists on the terminal surface today (the upstream fling callback was
  deliberately stripped), so the gesture is a blank slate — but it must be arbitrated against the
  app-drawer pull, status-bar pull-down, widget paging, terminal selection and divider drags.
  Build it as a new top-level claim on the `AppDrawerGestureArbiter` one-way-latch pattern rather
  than ad hoc touch logic.

## Sequencing

The home-screen pane is the natural first milestone: no fork needed, mostly in-repo promotion
work, and it forces the outer-container and gesture decisions the X11 pane will inherit. Fork
termux-x11 when the X11 pane work actually starts; forking earlier buys nothing.

## Sources

- <https://github.com/termux/termux-x11> (architecture, `.gitmodules`, shell-loader gating,
  license, release sizes)
- <https://github.com/brunodev85/winlator> / `winlator-app` (`com.winlator.xserver` — the
  from-scratch Java X server)
- <https://github.com/termux/termux-gui>
- <https://source.android.com/docs/core/display/multi_display/activity-launch> (virtual-display
  launch policy), <https://developer.android.com/develop/ui/views/layout/activity-embedding>
- <https://developer.android.com/about/versions/14/behavior-changes-14> (background activity
  launches from widget PendingIntents)
