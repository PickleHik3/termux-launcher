# Pane wall, home-screen pane and X11 pane — feasibility study

Research record, 2026-08-31, with the reasoning of the two implementation plans folded in on
2026-09-05 when the work shipped (see *What shipped*, at the end). This exists so the decisions
below don't get re-litigated from scratch.

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
  rendering, shared-memory frame sync) and works with Turnip/Zink or virgl through standard
  Termux GPU packages. GPLv3, same as this app. Actively maintained (nightly builds, ~100
  commits/90 days). *Correction, 2026-09-04:* the earlier claim of "DRI3 via Turnip/Zink" was
  wrong — Xlorie is a software X server with no DRI3 or DMA-BUF import. GPU-accelerated clients
  render into their own buffers and push pixels over MIT-SHM; the server composites the
  framebuffer with GLES. Client-side acceleration is real and useful; server-side is out of reach
  without new Xlorie work, and is not in scope.
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

## What shipped (2026-09-04 → 2026-09-05)

The decisions above held. What the two implementation plans added, and why:

- **The wall is three fixed, full-size places — Widgets, Terminal, Display — of which one is on
  screen at rest** (`app/wall/`). The developer set this layout: the terminal never resizes for
  the wall (no PTY churn), the pane tree is untouched, and the wall has no default keybinds —
  keybinds stay with the multiplexer. Navigation is a sideways drag on the status bar (the window
  strip scrolls its chips first and hands over the surplus), the place switch beside the clock in
  the expanded status bar, Home, and the registry tool `wall.go`. Back never navigates the wall.
  The old FULL status pull-down went with it; the widget grid is the Widgets place.
- **Three places form a ring.** Past Display comes Widgets and the other way round, for swipes and
  taps alike, so every place is one step from every other and a tap always slides towards where
  its segment sits. Two places stay a line with a rubber band: the one other page cannot wait on
  both sides at once.
- **The place switch** replaced two swapping tiles, which read as unnatural and filled the slot.
  It is one pill with all three places and a thumb driven by the wall's own offset, so it is a
  map of the wall rather than an animation of its own. Display reads quieter until a display
  runs, carries a dot while one does, and is held to stop it.
- **Every page sits inside the terminal's frame insets.** The pane host's margins (the surface
  editor's side gap, the border's air) are the wall's page insets; moving the host into the wall
  had silently dropped them once, because a plain `ViewGroup` hands out non-margin layout params.
- **The X server is vendored, not depended on** (`x11-server/`, deviations in `UPSTREAM.md`;
  `libXlorie.so` prebuilts built by CI from one pinned commit and one native patch that renames
  the host class). The server runs as a separate `app_process` started by the launcher's own
  `termux-x11` command — Termux:X11's model, kept because it is what power users expect and it
  keeps an X server crash off the home screen. The loader is built per edition and per build
  type, because both the package and the signature it trusts are that edition's. The launcher
  announces nothing itself: the server broadcasts a Binder to a receiver that is not exported and
  sits behind a signature permission, and the controller that takes it lives and dies with the
  activity, handing the Binder to its successor across a rotation.
- **The Display place is always on the wall; the display is off by default and starts on
  demand.** Decided with the developer on 2026-09-05: a home launcher is a process that never
  dies, so a display server must never be started for it — the page offers Turn on, then Start,
  and the display is stopped by hand (hold the segment, the long-press menu, `pkill`). The
  "start with the launcher" and `DISPLAY` for new shells rows are opt-ins that default off, as
  Termux:X11 never set either.
- **Linux apps are drawer entries, and the display starts on demand for them.** The prefix's
  desktop files are read into the app catalogue under a reserved package (`X11Apps`), ranked,
  pinned and searched like Android apps; a tap starts the server when none is up, runs the app
  with the GPU environment for whatever profile is installed, and shows the Display place. A small
  window manager (openbox, with a launcher-owned maximise rule) starts with the server so one app
  fills the page — the launcher recommends one app at a time over a desktop, which a phone screen
  and an always-running home process both argue for.
- **GPU: detect, recommend, stay out of the way.** `X11GpuProbe` ranks the client-side profiles
  (Adreno → turnip-zink, Mali → virgl over ANGLE and the wrapped Vulkan driver, others → ANGLE,
  emulators → software) with the exact environment each needs, behind `launcherctl x11 gpu
  [--env]` and a settings row. The launcher never writes any of it into a shell.
- **Phase 4 ran on pong (2026-09-05).** `check-display.sh` passes 6/6 on the arm64 library with
  both the software and the `turnip-zink` profile — the `AHardwareBuffer` hand-off and a GPU
  client presenting through the display on a real Adreno — and the launcher's own memory does not
  move for it. `phoc` from a stock Debian proot runs on the display with pixman; its Vulkan
  renderer fails for the documented reason (no DRI3, so no DRM FD; a distro Turnip drives
  DRM/msm, not KGSL), which is what phosh-termux-gpu's patched wlroots and KGSL Turnip exist for.
  Neither is vendored. See `project-docs/verification/x11/results.md`.

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
