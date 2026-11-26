# Linux windows: the launcher as a Wayland compositor (2026-11-24)

Status: Wayland Display is built and merged into `v2-redesign` behind `-Pwayland=true` (kept out of
CI builds, 2026-11-24). The choices under "Decided" and "The window contract" are the developer's;
the rest of "Design" is the plan they rest on. Next: the window contract's slices.

## Why

Linux apps reach the screen today through one X display shown whole in one `SurfaceView`
(`x11-server/`, `app/.../x11/`, about 16,800 lines in the app). The developer finds it tacked on in
three places: looks and motion, window handling, and starting and set-up. The code agrees:

- The launcher sees one screen, not apps. Openbox maximises every app inside it; the launcher reads
  the window list back as an X client (`X11WindowList`).
- The surface sits outside the view tree, so it cannot be rounded, clipped or moved with the page.
  `X11PaneFrame` (1,780 lines) covers for that with a corner mask, a stand-in and a still frame. It
  is opaque, so a transparent Linux app shows black, never the glass.
- `TermuxActivity` refers to the display about 175 times (`isDisplayWindowInFront`, `isDisplayUp`),
  where a terminal goes through seams.
- The native server is a committed prebuilt with four patches; its source is not in the tree.

## Decided (the developer, 2026-11-23)

1. **One Linux app window is one launcher window**, with a pill, splittable beside terminals, wearing
   the same glass, corners and motion, with per-pixel transparency.
2. **Wayland.** The launcher is the compositor; X11 apps run through rootless Xwayland.
3. **The compositor core is smithay (Rust).**
4. **A graphics driver or compositor crash never takes the home screen down.**
5. **No backend choice is put to the user.** The launcher detects and picks; settings are overrides.
6. **Terminals stay on the launcher's own renderer.** What is shared is the window contract, not the
   backend.

Decided 2026-11-24:

7. **The old display stays, behind a switch.** Settings offers the two backends by name, **X11
   Display** and **Wayland Display**; nothing is removed until the developer says so.
8. **Whole desktop sessions are in the first release** of Wayland Display.
9. **32-bit phones if it is cheap.** Build the 32-bit ABIs; drop them without further asking if
   they cost real code or time.
10. **35 MB for the compositor process is accepted for now**; a leaner process is a later topic.
11. **Names.** The backends are **X11 Display** and **Wayland Display** (glossary to follow).

## What the spike measured

Branch `spike/wayland-windows` (`2e62b888c`), staging build, on pong (Snapdragon 8+ Gen 1,
Adreno 730, Android 16, 1080×2412) unless marked.

| Question | Result |
|---|---|
| Per-pixel alpha from a client to the wallpaper behind a `TextureView` | Exact |
| One full-screen `wl_shm` window, copied on the CPU | 123–124 frames/s; 1.7 ms copy, 2.9 ms post |
| Same, with the compositor in another process | Unchanged |
| Native Wayland apps | gtk3-demo and herdr-app (GPUI) each came up as their own view |
| X11 apps | xterm came up as its own view through rootless Xwayland |
| Keys | Hardware key events reached xterm |
| Apps when the screen closes, reopens, rotates | Keep running; views re-attach |
| Forced abort of the compositor process | Launcher pid unchanged; clients and Xwayland ended with it |
| Compositor process memory | 35 MB PSS on pong (73 MB on the x86_64 emulator) |
| Library size | `libtlwayland.so` 2.3 MB arm64, of which about 0.5 MB is Xwayland support |
| Stock Adreno 730 driver | No dmabuf import in Vulkan or EGL; AHardwareBuffer import in both |

Not measured: Herdr in glass mode (it drew, but its core was not found from the test shell); text
input; a GPU-heavy client; any non-Adreno phone; memory with several large windows.

## Design

### Processes

- **The compositor runs in its own process**, `:linuxwindows`, started on demand and bound by the
  launcher. It owns the Wayland socket, the protocol state, Xwayland, and later any bundled driver.
- The launcher process owns only views and policy. It speaks to the compositor over one AIDL
  interface whose calls are all `oneway`, with results as callbacks, so a hung compositor cannot
  block the UI thread.
- The compositor outlives the activity. On connect it replays its window list; the launcher hands
  each window a fresh `Surface`.
- If the process dies, the launcher drops the Linux windows with a notice and offers to start
  again. Shell sessions are untouched.
- Open: it must run as part of the existing foreground service's guarantee, or Android may kill it
  under memory pressure. Open: whether a native-only process (no ART) is worth it to cut the 35 MB.

### Windows

- A Linux window is a kind of launcher window under the page contract of ADR 0030, beside the
  terminal window. It joins the current session's row, has a pill, can be dragged, split and
  floated, and its × asks the app to close.
- `PageKind.DISPLAY`, `X11PaneFrame` and the display special cases in `TermuxActivity` are replaced
  by this kind behind a Host seam; none of the new behaviour is added to `TermuxActivity`.
- Each toplevel is a `TextureView` in the pages window layer, non-opaque, so the window's glass
  shows through transparent pixels and outline clipping, alpha and page motion apply as they do to
  any view. No corner mask, stand-in or still frame.
- The launcher tells the app its size (configure) from the pane's bounds; the app's own title bar
  is suppressed (server-side decoration), and the xdg window geometry crops client shadows.
- Popups, menus and subsurfaces draw into their parent window's view. X override-redirect windows
  the same. (Not built in the spike.)
- **Whole desktop sessions** (first release). A desktop is one window. Proposed route, to verify
  on a phone before building on it: the window hosts a second, rootful Xwayland (its own display
  number) with the X11 desktop running inside it, so an existing XFCE set-up carries over with no
  new packages. A Wayland-native desktop (labwc, KWin) would run nested the same way, as one
  client window. tlstore can ship a ready-made session (a desktop entry plus its start script,
  gated with `min-launcher`) so the user installs one item instead of assembling it; whether the
  store builds any binaries of its own for this depends on what the check finds missing.
- **Memory.** Each window holds its buffer queue: about 9 MB per full-screen frame. A bounded store
  with an explicit budget decides how many hidden windows keep their surface; a window past the
  budget keeps running and gets its surface back when shown. The compositor keeps each window's
  last frame so a re-attached idle window is not blank (seen with gtk3-demo).

### Presentation and the GPU

Two independent choices, both made by the launcher.

1. **How the app renders** (Turnip+Zink, virgl, the Vulkan wrapper, software). `X11GpuProbe`
   already ranks these; it carries over, renamed, and its top result is applied without asking.
2. **How the finished frame reaches the view.**
   - **Copy** (`wl_shm`) is the baseline on every phone, and is what ships first. At 1.7 ms a frame
     on pong it does not limit ordinary apps.
   - **Direct** paths are later fast lanes, chosen by a first-start probe run in the compositor
     process and cached against the driver version; a failed or crashing probe means copy. Two
     candidates, neither built: a bundled Turnip loaded through libadrenotools (Adreno only, a
     second driver in the compositor process), or client buffers backed by AHardwareBuffer, which
     the stock driver can import on pong (this would also serve Mali through the Vulkan wrapper;
     unverified).
   - No device lists.

### Input

- Touch maps to `wl_touch` per window; the touchpad mode maps to `wl_pointer`.
- The in-app keyboard and hardware keys map to `wl_keyboard`; the keymap comes from the prefix's
  xkb data.
- Text entry uses the text-input protocol, so the keyboard rises when a Linux text field takes
  focus and falls when it leaves it. This replaces `DisplayTextFocusPolicy` and the IM helper
  script. Every path that shows the system IME still goes through
  `TermuxActivity.onSystemImeRequested()`.
- The clipboard bridges both ways through `wl_data_device`, on only while a Linux window has focus.

### Starting and set-up

- There is no display to start. Tapping a Linux app starts the compositor if needed and the app;
  its window appears when it maps. The compositor stops when the last Linux window closes, after a
  short grace period.
- The socket is `wayland-0` in the prefix's tmp (`$TMPDIR`), beside X's `.X11-unix/`:
  `<prefix>/tmp/wayland-0`, named through `TermuxConstants`. Termux guides already use that
  directory as the runtime directory (`XDG_RUNTIME_DIR=$TMPDIR`), so there the usual
  `$XDG_RUNTIME_DIR/$WAYLAND_DISPLAY` finds it.
- Apps find the compositor through `WAYLAND_DISPLAY` and Xwayland through `DISPLAY=:n`.
  `WAYLAND_DISPLAY` is `wayland-0` for a client whose `XDG_RUNTIME_DIR` is the prefix's tmp, and
  the socket's absolute path otherwise; libwayland takes that path as it is (`LinuxWindowsEnv`).
  Xwayland takes the lowest free display number: `:0` when X11 Display is not running.
- `XDG_RUNTIME_DIR` defaults to the prefix's tmp folder in every shell, filled in only when empty
  (ADR 0036, `RuntimeFolderProfile`): `etc/profile.d` for sh, `etc/fish/conf.d` for fish, and the
  block the launcher's own scripts begin with. All shells agreeing is what keeps a tool that keeps
  a socket there (Herdr, 41fb70a16) on one core.
- Launches get both variables. New terminal sessions get them while the compositor runs and
  Connect new shells is on (the setting X11 Display's `DISPLAY` already follows); with no
  compositor running they get what they got before. A client typed in a terminal before any app
  was launched finds no socket: starting the compositor on the first connection is the next slice.
- A proot-distro container sees the same absolute path with no bind of ours: a default login binds
  the whole prefix at its own path. Its `/tmp` is its own (proot-distro 5.x shares the prefix's
  tmp there only with `--shared-tmp`, whatever its help says), and `--shared-x11` carries the X
  sockets, so `DISPLAY=:n` works too. A shell opened by hand with `proot-distro login` gets neither
  variable: the login starts from a clean environment.
- Xwayland starts on the first X connection, not with the compositor.

### Build

- `wayland-host/` is a Rust crate in the tree, built by Gradle through `cargo-ndk` for arm64-v8a
  and x86_64, and for armeabi-v7a and x86 unless that proves costly, with CI caching for the libxkbcommon static build
  (about ten minutes cold).
- smithay 0.7 needs one local patch today (X11 socket directory, and ending Xwayland with its
  parent). It goes upstream or into a small fork recorded like `inapp-keyboard/UPSTREAM.md`.
- Editions: the socket path and package name come from `TermuxConstants`; nothing else is
  edition-specific.

## Order of work

1. **Window contract.** The Linux window as a page kind behind a Host seam, hosted by the real wall
   (pill, split, float, glass, corners), still fed by the spike's compositor. Old display untouched.
2. **Compositor to product quality.** Popups and subsurfaces, cursor, output scale and density,
   last-frame retention, the surface budget, foreground guarantee, text input, clipboard.
3. **Launching.** Linux app taps go to the backend the switch names; env for shells; proot;
   Xwayland on demand; whole desktop sessions.
4. **Parity sweep** against `docs/en/X11_Display.md`, then the switch flips for the pre-release
   channel.
5. **The switch.** Settings chooses X11 Display or Wayland Display; both ship. Removing
   `x11-server/`, `libXlorie.so`, the loader, the openbox set-up and `X11PaneFrame` is a separate
   later decision of the developer's, not part of this plan.
6. **Fast lanes**, only if a real app needs them: the direct-path probe and one candidate.

## Step 1 in slices (from the window-contract map, 2026-11-24)

Superseded by "The window contract" below where they differ; slices 1 and 4 are built.

What the code offers today: `page/Page` (kind, side, view, `rest`, `onFront`/`offFront`, `onBack`,
`declareGlass`) is built and registered through `Pages`; `AssistantPageView` (268 lines,
`PaneContentFrame` + `Page` + `TypingPage`) is the working model of a non-terminal window that is an
ordinary view: clipped by the frame's outline, dressed by `PaneGlass.apply`, moved by
`WallPageTransition`, typed into through `TypingPage`. The display window needs `SurfacePage`,
`SurfaceStandIn` and page stills only because its surface is outside the view tree; a
`TextureView` page needs none of them.

1. **Host in the tree.** `wayland-host/`, its build script, the AIDL pair and the `:linuxwindows`
   service move from the spike to a feature branch off `v2-redesign`, behind a Gradle property like
   `-Pghostty` so a checkout without Rust still builds. No UI.
2. **The page.** A new page kind for a window under Wayland Display, implemented like
   `AssistantPageView`: a `PaneContentFrame` holding one `TextureView`, `Page` + `TypingPage`. One
   page per toplevel, joining the session's row after the terminal windows. Every `PageKind` switch
   outside `wall/`, `terminal/` and `x11/` gets a decision (71 files reference the kind;
   `TermuxActivity` 315 times), written down per site, none defaulted.
3. **Pill, focus, close.** A `WindowChips` entry per page with the app's title and icon; × asks the
   app to close; touch and typed keys go through the page.
4. **The switch and launching.** Settings: X11 Display or Wayland Display. Linux app taps and shell
   env follow it.
5. **Later slices**: a Wayland window as a leaf in a terminal window's split tree
   (`TerminalPaneController.Leaf` is terminal-only today) and as a Home float; persistence in
   `TerminalWorkspace` (the display window is absent from it today); desktop sessions; text input;
   clipboard; popups.

## Decided on 2026-11-24 (the developer, after the overnight run)

- **Zero copy:** the Android-allocated buffer route (`exp/wayland-ahb`) is the route; it is built
  after the Linux windows screen is finished. The copy path carries everything until then. The
  bundled-driver route (`exp/wayland-turnip`) stays an unmerged branch, as the fallback and the
  number to beat (118 fps, 2.3 ms at 1080x2000 on pong).
- **Mesa:** the patch ships through tlstore only, as a one-library overlay in its own folder. The
  launcher adds it to a Wayland app's library path when present, offers it in Linux apps set-up
  when missing, and on a Mesa version mismatch leaves it out (software rendering) with a notice.
  No upstream PR.
- **The Linux windows screen is finished first**, in this order: typing through the launcher
  keyboard as plain keys, with one small tab-row pass; scale; the tlstore Mesa item; clipboard;
  whole desktops as one window in a rootful Xwayland; proot-distro apps on the copy path
  (software rendering inside the distro).
- Opening an app that is already open brings its window forward.
- Composed text (the system keyboard, swipe, emoji, other scripts) waits until after the wall page.
- **Then** the developer runs Wayland Display as the only display on pong for about a week and
  what they find is fixed; then the wall page; then zero copy.

## The window contract (the developer, 2026-11-24, evening)

Grilled after Wayland Display merged into `v2-redesign` (behind `-Pwayland=true`, out of CI). It
replaces the Linux windows screen with Linux windows in the wall, and it comes **before** the
developer's week of daily use, not after it. It supersedes "Step 1 in slices" above and the
"Windows" bullets where they differ. Everything here applies under Wayland Display only; X11
Display does not change.

### What a Linux window is

- **A Linux window** is one window in the Terminal's row for one app window (one xdg toplevel, or
  one X11 top-level window under Xwayland). It wears the window dress every window wears (rim,
  glass, corners, the active colour) and has a window chip with the **app's name and icon** from
  its desktop entry, not the window's changing title.
- **Full screen first.** Opening an app, from the drawer or a shell, opens a new Linux window right
  after the current window, even between two terminal windows (the developer, 2026-11-24; #392 makes
  a session's window list hold both kinds), and the wall pages to it, as a new terminal window does. Nothing is
  split or floated automatically: an X11 app split by itself is too small to use. The user moves it
  into a split or a Float on purpose.
- **Dialogs** that name a parent (xdg parent, X11 transient-for) are drawn centred over their
  parent's window and move with it; a parentless top-level gets its own Linux window.
- **The Linux windows screen retires**: `LinuxWindowsActivity` and `LinuxWindowTabs` go; the row and
  the window chips replace the tabs. Opening an app that is already open goes to its window.
- Persistence across a launcher restart is not part of this contract.

### The rim tab

- A small glass tab on the window's rim at the top-right corner, outside the app's pixels, so the
  app keeps every tap on its own content (apps keep menus and close buttons in their corners).
- A tap on it shows **Close, Minimise, Move and Scale**. Long presses are not used here.
  - **Close** asks the app to close (xdg close; `WM_DELETE_WINDOW` for X11). If the window is
    still there 5 s later and the app is not showing a dialog of its own, a notice offers **Force
    close**, which kills the app.
  - **Minimise** sends the window to its chip in the status bar; a tap on the chip brings it back
    to its place in the row.
  - **Move** is a handle: holding it picks the window up (see Carry).
  - **Scale** brings out the scale rail built for Wayland Display; a stop is still the app's own
    Scale (`X11AppSettingsStore`).
- A Linux window has no corner tab of its own (Appearance, Layout, Wallpaper and the rest stay on
  terminal windows and Home); the corner hold that brought out the scale rail goes.
- As a split pane it keeps the rim tab. As a Float it wears the Float titlebar every Float wears
  (drag, Minimise, Maximise, Close; Dock in the menu) and no rim tab.

### Carry

- Holding Move lifts the window and it follows the finger, as a widget does when it is moved across
  Home pages. The wall pages under it by a **border hold** at the side, or by a swipe with a second
  finger.
- Where it is dropped decides what happens, with a preview while it is over a target:
  - over a terminal window's pane or another Linux window: a **split**, on the half the finger is
    over (left, right, top or bottom);
  - over a Home page: a **Float** on that page;
  - between two windows in the row: its **own window** in that spot (reordering the row).
- The way back out of a split is the same carry from the pane's rim tab, dropped between windows.
- Overview (in flight) will be another way to arrange windows; noted, not designed here.

### Paging

- On a Linux window the **border drag** stops at the window's edge, as on the display window: the
  picture is the app's to the rim, so only the air and the frame line page. A body swipe never
  pages from a Linux window.
- **Border hold**, on every window (terminal, Linux, display) and during a carry: a finger held
  still on the left or right border for about 1.5 s pages the row once, with a vibration and a
  small `<` or `>` glyph at that border as it fills. A hold that moves before then is a border drag
  as today. On a terminal the hold yields to text selection that started in the text.

### Focus, keyboard, attention, memory

- A tap in a Linux pane of a split focuses it and still reaches the app. The launcher keyboard
  types into the focused pane, whatever its kind (Linux panes take keys through
  `LinuxWindowKeys`).
- When an app asks for attention (an xdg-activation request it is not allowed to act on, or X11's
  urgency hint), its rim and chip glow in the attention colour (`PaneAttention`) until the user goes
  to it. The window is never brought forward by itself.
- Only windows on screen or next to it hold surfaces (`LiveNeighbourPolicy`); a window further away
  gives its `TextureView` buffers back and keeps running, and the compositor's last frame repaints
  it when it returns.

### Slices

Each slice is a short branch off `v2-redesign`, merged back when its tests pass.

1. **The Linux window in the row**: page kind, frame and `TextureView` (as `AssistantPageView`),
   window chip, rim tab with Close, Minimise and Scale, dialogs over their parent, surfaces only
   near the screen, border drag to the window's edge, launching into the row, the screen retired.
2. **Carry and the border hold**: Move on the rim tab, the lifted window, paging under it, drop
   between windows; the border hold on every window.
3. **Into a split**: a split tree leaf that holds a Linux window (`TerminalPaneController.Leaf` holds
   only a `TerminalSession` today), drop halves, focus and keys, carry back out.
4. **Onto Home as a Float**: a Float holding a Linux window (Floats are leaves too, so this builds
   on slice 3's leaf), the titlebar, its chip, Dock into the row.
5. **Attention and Force close**.

## State on 2026-11-24, end of day

Built on `feat/wayland-display` and checked on pong unless marked otherwise.

- **The Linux windows screen:** tabs (40dp, tinted selection, 200dp cap); the launcher keyboard as
  plain keys, the window shrinking above it; text clipboard both ways; second tap brings the open
  window forward (native apps by process; an X11 app only within 10 s of a single launch).
- **Scale (the developer, 2026-11-24):** every window follows the screen by default: the largest
  rail stop that is not above the density and leaves the window 520 points wide, never under
  0.7 x density (2x on pong). Holding a corner brings out the same rail X11 Display has; a stop
  is the app's per-app Scale, so the per-app page and the rail agree. X11 apps in the shared
  Xwayland share one size and the user is told so; no workaround (the developer's ruling).
  Bitmap-font apps (xterm) stay at 1x.
- **Desktops:** a prefix desktop is one window in its own rootful Xwayland (`-hidpi`, real pixels,
  size through GDK/Xft settings). Desktops inside a container stay on X11 Display.
- **proot-distro apps:** on the copy path, no GPU variables or prefix library path passed in.
  Measured: proot-distro 5.10.3 binds the whole prefix at its own path in a default login, and
  binds tmp to `/tmp` (and the X11 socket folder) only with `--shared-tmp`/`--shared-x11`,
  whatever its help text says. Seen from a shell; not yet from the drawer (no distro app is listed
  in pong's drawer: open question).
- **The display's address:** socket `$PREFIX/tmp/wayland-0`; `WAYLAND_DISPLAY` is `wayland-0` when
  the client's runtime directory is that folder, else the full path. `XDG_RUNTIME_DIR` defaults to
  that folder in every shell (ADR 0036). The shared Xwayland takes the lowest free
  number (`:0` unless X11 Display holds it). While Wayland Display is chosen the launcher listens
  on the socket from process start; the first connection starts the host, which adopts it; 30 s
  with no window stops the host and its process. `DISPLAY` exists only while Xwayland runs.
- **Set-up:** installs `xwayland` when Wayland Display is chosen; notices for missing Xwayland
  and missing keyboard layout data (not yet seen on a device).
- **Graphics add-on:** the launcher uses `$PREFIX/opt/tl-mesa-wayland` when its Mesa release
  matches the installed one (dpkg or pacman). The tlstore item `mesa-wayland-egl` is on tlstore
  branch `feat/mesa-wayland-egl`, unmerged; its real build has never run.
- **Decided, to build:** set-up may write two launcher-owned files into each distro (display
  variables, sound server); the sound server is made findable without variables (PulseAudio
  stays; PipeWire waits for a screen-sharing and camera feature); a set-up row offers the add-on.

Not checked on a device: sound from a Linux app; a launcher terminal's variables; the X11
"share one size" line. Known defects are GitHub issues under #384 (#390 Firefox new-tab overflow, #391
xfconfd left running); what is owed on a device is in `project-docs/device-checks.md` and
`developer-checks.md` (2026-11-24 Wayland lines). Known: Firefox's own new-tab page overflows the window (ordinary pages
fit); Herdr's glass mode is opaque (Herdr's side).

## Open

- How a window under Wayland Display is named in the glossary and in copy (the backend is "Wayland
  Display"; the per-app window still needs its word).
- Desktop sessions: verified on pong 2026-11-24. A rootful `Xwayland :1` ran as one window and
  `startxfce4` came up inside it (panel, dock) with the packages already installed. Still needed:
  output scale (it draws at scale 1, too small to use), resize with the window, and its own start
  entry.
- **GPU apps under Wayland Display.** herdr-app ran fully as a Wayland window but logged
  `wgpu_hal::vulkan::instance enumerate_adapters: Initialization of an object has failed`, and its
  canvas was opaque in glass mode. Rerun on pong with the launcher's Turnip env
  (`TU_DEBUG=noconform`, `MESA_LOADER_DRIVER_OVERRIDE=zink`, the freedreno ICD): the same Vulkan
  error and an opaque canvas; and `glmark2-es2-wayland` under the same env reported
  `GL_RENDERER: llvmpipe` (score 67). **So under a compositor that offers only `wl_shm`, Mesa's
  hardware paths do not engage and GPU apps render in software.** GPU apps therefore need
  `linux-dmabuf` on our side, and a way to show those buffers (bundled Turnip in the compositor
  process, or AHardwareBuffer-backed buffers the stock driver imports), before Wayland Display
  matches X11 Display for them. This is a required slice, not an optional fast lane. Herdr's
  see-through canvas depends on it too. To design before slice 4.
- 32-bit: armeabi-v7a and x86 build with no code changes (1.74 MB and 2.47 MB); armeabi-v7a is in
  the default list. Nothing has run on a 32-bit device.

## Measured on the night of 2026-11-24 (pong)

This section supersedes the earlier notes on GPU apps above where they differ.

- **GL apps could not use the GPU on Wayland with stock Termux Mesa.** EGL's hardware path needs a
  DRM render fd KGSL cannot give; the only env route to the kopper path makes Zink demand a CPU
  Vulkan device. A patch to `src/egl/drivers/dri2/platform_wayland.c` (about 20 lines, trigger
  `LIBGL_KOPPER_DRI2=1`, as on X11) fixes it; only `libEGL_mesa.so` differs from stock. It must
  ship in a Mesa package. The launcher sets `LIBGL_KOPPER_DRI2=1` and `VK_DRIVER_FILES` for
  Wayland launches on the turnip-zink profile (`LinuxWindowsEnv.gpuEnv`).
- **Baseline, on `feat/wayland-display`:** `zwp_linux_dmabuf_v1` v3, four 32-bit formats, linear
  modifier only, shown by `mmap` and copy through the paced path. glmark2-es2 at 800x600: llvmpipe
  119, Turnip with `wl_shm` presentation 194, Turnip with dmabufs 301.
- **Route 1, `exp/wayland-turnip`:** Turnip built from Mesa 26.3.1 for bionic, loaded with
  libadrenotools in the host process; client dmabufs (including UBWC) imported and blitted to a
  swapchain on the window's `Surface`. Same GL scene at 1080x2000: direct 118 frames/s, 2.3 ms host
  time per frame, 26 % of a core; copy 70-108 frames/s, about 6 ms, 57 %. Composite alpha: INHERIT
  only. No fence available on KGSL. Needs `useLegacyPackaging true` (about 50 MB more installed)
  and a 13.9 MB driver.
- **Route 2 probe, `exp/wayland-ahb`:** an `AHardwareBuffer` yields a linear dma-buf fd through
  `AHardwareBuffer_sendHandleToUnixSocket` (stride 4352 bytes at 1080 px); Termux Turnip imports it
  as a LINEAR image and renders into it (read-back matched); the launcher draws the
  `HardwareBuffer` in a plain `View` through `Bitmap.wrapHardwareBuffer` with alpha, and later
  writes to the buffer show. A full route needs a protocol to hand buffers to clients and a Mesa
  WSI patch. Proposed as the zero-copy route; the view technique may also replace `TextureView`
  for the copy path.
- **Host cost today:** the compositor process is 26-35 MB PSS; a full-rate full-screen window on
  the copy path costs 30-57 % of a core in it, most of it the `Surface` post.
- **Herdr:** runs on the GPU under Wayland Display; its glass canvas stays opaque because its wgpu
  Vulkan path fails to enumerate and it renders through GL. Herdr-side.
- **Scale:** the Wayland output scale follows the screen's density (to the nearest quarter, ties
  down: 2.5 on pong) until the user chooses a Display scale stop, which then wins
  (`LinuxDisplayScale`). X apps under Xwayland keep their pixels and are told the scale the X
  way: XSETTINGS `Xft/DPI`, `Gdk/WindowScalingFactor`, `Gdk/UnscaledDPI` (GTK, Chromium) and the
  `Xft.dpi` resource (Xft clients: xterm with an Xft face, Qt's xcb, Emacs); `wayland-host`
  `xscale.rs`. Xwayland does not see xdg_output, so its screen is the real pixels at any scale.
  Not scaled: core-font X apps (plain xterm, xclock), Motif, and widgets of Qt 5 on X.
- **Built since the plan above was written:** the Settings choice (X11 Display / Wayland Display),
  `WaylandAppRunner`, the tabbed Linux windows screen (the stand-in for the in-wall page), X display
  reporting (interface version 2), subsurface and popup compositing. Whole desktops and proot-distro
  apps still go to X11 Display.

## References

- Spike report: scratch, summarised above. Branch `spike/wayland-windows`, worktree
  `app/wt-wayland-spike`.
- Projects read: localdesktop (smithay in an Android process), trierarch (Turnip-in-process dmabuf
  presenter), anland-termux (Android-allocated buffers). None maps toplevels to separate views.
- ADR 0030 (pages of one contract), ADR 0025, `docs/en/X11_Display.md`.
