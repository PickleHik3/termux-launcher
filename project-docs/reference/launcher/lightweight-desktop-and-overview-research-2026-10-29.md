# A phone-sized desktop to package, and options for a launcher overview — research (2026-10-29)

**Date:** 2026-10-29 · **Branch:** `v2-redesign` · Read-only research. Nothing was built, run or measured on a device, no code was
changed, nothing here is a decision. Items marked **(reasoned)** are inferred from reading, not read.

**Question.** Part A: which Linux desktop setup should the developer package (a Termux `.deb` or a proot-distro recipe plus config,
shipped through tlstore) for phones, given the embedded Termux:X11 server: lowest idle RAM/CPU, no GPU compositing requirement, usable
in portrait and landscape, snappy animation, reproducible. Part B: what design options exist for an overview screen of the launcher's
own surfaces (wall pages, terminal windows, the Display), modelled on niri, Phosh, GNOME Shell, Plasma Mobile and Android's Recents.
This note does not repeat `termux-x11-startup-research-2026-10-27.md` (how the server starts) or `proot-gui-apps-research.md` §1 (the
app catalogue), and builds on `de-sessions-research.md` and `pane-wall-x11-study.md`.

## Sources

| Short name | What | Read at |
|---|---|---|
| **tpk** | `github.com/termux/termux-packages`, dirs `x11-packages/<pkg>/build.sh` and `packages/`; tree listed with `gh api` | master `2a3253c057bebeaf311cf96d0e122dfb927226b6` (2026-10-29) |
| **tx11** | `github.com/termux/termux-x11`, shallow clone (submodules, incl. the xserver itself, NOT fetched) | `6dfb8b4a4cc887bc329284d31eb049d596bd1208` (2026-10-27). The launcher's vendored copy pins `9df8b767` (`x11-server/UPSTREAM.md`), so anything cited from tx11 C code is "master", not what ships in `libXlorie.so` today |
| **picom** | `github.com/yshui/picom`, `man/picom.1.adoc`, `src/backend/xrender/xrender.c` | tag `v13` = `d87a5ba3af7a9ee3c4e040ee29b2dea7e9e46317` (the version tpk builds: `x11-packages/picom/build.sh`) |
| **openbox / icewm / fluxbox** | upstream source, shallow clones | openbox `3a2fbc65` (master; tpk builds the 3.6.1 tarball), icewm `17907bd`, fluxbox `6890143` |
| **tint2** | `gitlab.com/nick87720z/tint2` (tpk's `TERMUX_PKG_SRCURL`), `doc/tint2.md` and `src/main.c` | tag `v17.1.3`; the `o9000/tint2` clone I also grepped is 2019 and is not what tpk builds |
| **sxmo-utils** | `git.sr.ht/~mil/sxmo-utils` | `303c755e88d2a25c01bdbc5b46aba1939318f839` (2026-10-24), `docs/sxmo.7.scd`, `scripts/core/` |
| **phosh / phoc** | `gitlab.gnome.org/World/Phosh/{phosh,phoc}` | tag `v0.58.0`: phosh `991730023a5b`, phoc `d50d76a4add4` (both 2026-10-04) |
| **plasma-mobile** | `invent.kde.org/plasma/plasma-mobile` (GitLab API tree + raw files) | tag `v6.8.2` (`5ce041f2f`) |
| **niri** | `github.com/YaLTeR/niri`, source and `.wiki` | tag `v26.04` (commit `8ed0da44d974c32c6877d2f4630c314da0717ecb`); wiki `93338506fee402704be4c9bb7afa4e56d9f8a66d` (2026-10-27) |
| **gnome-shell** | `gitlab.gnome.org/GNOME/gnome-shell`, `js/ui/{overview,overviewControls,workspaceThumbnail}.js` | tag `51.1` |
| **AOSP** | `android.googlesource.com/platform/frameworks/base` `core/res/res/values/config.xml`; Launcher3 `quickstep/` | `refs/heads/main` as served 2026-10-29 (JSON head `1cdfff555f4a`, which that server dates 2025-03-26) |
| **i3 guide** | `i3wm.org/docs/userguide.html` §2.6 | fetched 2026-10-29 |
| **repo** | this checkout | HEAD `93c35220f`; working tree has uncommitted edits to `PageOverviewView.java`, `OverviewGeometry.java` and its test, read as they stand |

Not reachable or empty: the Termux wiki (blocked, see the 10-27 note); Lomiri's `README.md` says nothing about display servers; Plasma
Mobile has no docs page I could fetch that states "Wayland only" (the README launch line is the evidence, below). Community practice
(not authority) is listed only where named.

---

# Part A: which desktop setup to package for phones

## A.1 What exists in the repo today (so the recommendation builds on it)

- **The launcher already starts a bare window manager, and it is openbox.** Preference default `openbox`
  (`app/src/main/res/xml/x11_display_preferences.xml:59`, `TermuxPreferenceConstants.java:255` per `de-sessions-research.md` §5);
  package name `GuiAppsSetup.WINDOW_MANAGER_PACKAGE = "openbox"` (`app/src/main/java/com/termux/app/x11/GuiAppsSetup.java:59`).
- **Its config is the launcher's own, written at install:** `X11CliInstaller.openboxRcContent()` (`X11CliInstaller.java:830-852`):
  `focusNew yes`, `A-Tab`/`A-S-Tab` bound to NextWindow/PreviousWindow (the touchpad three-finger swipe sends those), and one
  `<application class="*">` rule: `decor no`, `maximized yes`, `focus yes`. So the Display shows **one app at a time, full size, no titlebars**
  (`X11WindowManager.java:11-16`). Passed by `--config-file` (`X11WindowManager.java:89-90`), no user dotfile touched.
- **The WM is never the server's `-xstartup` child.** A wrapper script is (`X11WindowManager.java:18-30`) because the server kills the
  display when that child exits; the wrapper records the WM pid so a desktop session can stop it (D1).
- **Whole desktops:** the drawer lists DE sessions from `share/xsessions` (`de-sessions-research.md` §1-3); on start the launcher stops
  its openbox and runs `startxfce4`/`startlxqt`/etc. (`X11LinuxAppRunner.java:352-359, 461-473`; session-bus wrapper
  `BUS_WRAPPER = "dbus-launch --exit-with-session"` for DEs that start none, `X11LinuxAppRunner.java:81-93`). LXQt "stands down" when a WM
  already holds the screen (`lxqtmodman.cpp`, per `de-sessions-research.md` §5); XFCE does not (xfwm4 refuses, the rest of XFCE runs under
  openbox's rules). One session at a time (`de-sessions-research.md` §6).
- **Linux apps catalogue:** `LinuxAppCatalog` scans `share/applications`; distro (proot) dimension is the known gap
  (`proot-gui-apps-research.md` §1).
- **Input already handled by the launcher:** the in-app keyboard types into X through `X11KeyboardBridge`
  (`X11KeyboardBridge.java:1-18`), so no X on-screen keyboard is needed for the launcher's Display.
- **DPI and size:** default X DPI = `densityDpi * 0.56`, rounded to a multiple of 8, clamped 160..320 (`X11Defaults.java:18, 45-48`), read
  only when the server starts (`DisplayScaleSteps.java:10`); resolution mode defaults to `scaled`
  (set by `X11Defaults.java:36-37` when unset; `LorieView.getDimensionsFromSettings`, `x11-server/.../LorieView.java:384-414`: `w = width*100/scale`,
  so scale > 100 gives a smaller X screen and bigger UI). The border-drag rail changes the scale live (`X11PaneFrame.applyScaleStep`,
  `X11PaneFrame.java:524-535`).
- **Window list:** the only toplevel data the launcher has about X windows is the EWMH client list (`X11WindowList`; `wall-rework-phase2-2026-10-18.md` item 8).

## A.2 Rotation on termux-x11: what the server and the WMs actually do

- The Android activity declares `configChanges="orientation|screenSize|smallestScreenSize|density|…"` (`AndroidManifest.xml:139`), so rotating
  does not recreate it. `LorieView` re-lays out, recomputes the X size from the available size and the resolution mode
  (`LorieView.java:526`, `getDimensionsFromSettings`) and calls `sendWindowChange` (`LorieView.java:443-457, 557`), unless the dimensions
  are frozen (only in picture-in-picture, `LorieView.java:883`). So **"rotation" here is an X screen resize between W x H and H x W, not an
  X rotation.** The `adjustResolution` preference (default false, `x11-server/src/main/res/xml/preferences.xml:15`) swaps width and height
  for the `exact`/`custom` modes (`LorieView.java:416`).
- On the server side the change lands in `lorieChangeWindow` -> `RRCrtcNotify` + `RRScreenSizeSet` (tx11 `lorie/src/main/cpp/lorie/InitOutput.c:850-871`)
  and `lorieRRScreenSetSize` ends in `RRScreenSizeNotify` (`InitOutput.c:740`). RandR size range is 1..32767 (`InitOutput.c:772`).
- **X-level rotation is not offered:** `lorieRRGetInfo` returns only `RR_Rotate_0` (`InitOutput.c:752-754`). `xrandr -o left` cannot work.
- **How the WMs/panels react** (event names from source):
  - openbox: root `ConfigureNotify` -> `XRRUpdateConfiguration` + `screen_resize()` (`openbox/openbox/event.c:815-819`). Re-lays out
    maximised windows (reasoned from the name; the launcher's rule is `maximized yes`).
  - icewm: dedicated `handleRRScreenChangeNotify` -> `updateScreenSize` -> `setSize` + `setDesktopGeometry` (`icewm/src/wmmgr.cc:3904-3925`).
  - fluxbox: selects `RRScreenChangeNotifyMask` (`fluxbox/src/Screen.cc:278`).
  - tint2: any root-window `ConfigureNotify` makes it **restart itself** (`src/main.c:366-369`, `emit_self_restart("configuration change in the root window")`);
    panel placement comes from its config file: `panel_position = vertical horizontal orientation`, `panel_size = width height` with
    percentages allowed (`doc/tint2.md:275-290`).
  - jwm: has a `HandleConfigureNotify` (`jwm/src/event.c:60,198`); I did not read what it does for the root. **Unverified.**

## A.3 Candidate table

Packages: `x11-packages/<name>/build.sh` in tpk at `2a3253c`. "In repo" = a build recipe exists; it says nothing about quality on a phone.

| Candidate | In tpk? (version) | Needs compositor? | Rotation reaction (source) | Touch / OSK | Idle RAM/CPU |
|---|---|---|---|---|---|
| **openbox** | yes, 3.6.1 (`x11-packages/openbox`) | no. Only built-in animation option in its shipped `rc.xml` is `animateIconify` (`openbox/data/rc.xml:65`) | `screen_resize()` on root resize (above) | no panel (it has a `<dock>`, `rc.xml:173`). No touch gestures of its own (not found in source; reasoned) | no primary figure |
| **tint2** (panel) | yes, 17.1.3 (`x11-packages/tint2`; gtk3, imlib2, cairo, pango deps) | optional (depends on xcomposite/xdamage for transparency) | self-restart on root resize; config-driven size/position | `panel_size` height is the touch-target knob (`tint2.md:280-290`); `autohide` exists (`tint2.md:330`) | no primary figure |
| **icewm** | yes, 4.1.0 (`x11-packages/icewm`; its own taskbar is reasoned, not read) | no | explicit RandR handler (above) | not verified | no primary figure |
| **fluxbox** | yes, 1.3.7 (`x11-packages/fluxbox`) | no | selects RandR event | not verified | no primary figure |
| **jwm** | yes, 2.4.6 | no | not verified | not verified | no primary figure |
| **fvwm / i3 / awesome / bspwm / herbstluftwm / dwm / 2bwm** | all present: fvwm 2.7.0, i3 4.25.1, awesome 2026.03.31, bspwm 0.9.12, herbstluftwm 0.9.6, dwm 6.8, 2bwm 0.4 | no | not read | keyboard-first tilers; no touch model (reasoned) | no primary figure |
| **polybar** | yes, 3.7.2 | no | not read | bar only | no primary figure |
| **xfwm4 + xfce4-panel (XFCE 4.20)** | yes: xfwm4 4.20.0 (deps include libxcomposite, libxdamage, libepoxy, libxpresent), xfce4-panel 4.20.8, `xfce4` metapackage | xfwm4 carries its own compositor | not read | GTK3 app stack | no primary figure; heaviest of the X11 set by package count (reasoned) |
| **lxqt (session + panel)** | yes: lxqt-session 2.4.0, lxqt-panel 2.4.1 (Qt6) | no | not read | Qt6 stack | no primary figure |
| **mate (marco + panel)** | yes: marco 1.29.1, mate-panel 1.28.7 | marco has a compositor (deps xcomposite/xdamage) | not read | GTK3 | no primary figure |
| **KDE Plasma X11** | yes: plasma-workspace 6.7.5, `kwin-x11` 6.7.5, plasma-desktop | KWin compositing | not read | Qt6 | no primary figure; very large dependency list in `kwin-x11/build.sh` |
| **picom** (compositor) | yes, 13 (`x11-packages/picom`; depends libepoxy, `opengl`) | is one | n/a | n/a | no primary figure |
| **xcompmgr** | yes, 1.1.10 | is one (xrender only) | n/a | n/a | no primary figure |
| On-screen keyboards | matchbox-keyboard 0.1.1, florence 0.6.3 (gtk3, gstreamer), xvkbd 4.1 | no | n/a | all in tpk; not needed while the launcher's keyboard is used | no primary figure |
| Not in tpk (grep of both trees) | `svkbd`, `lisgd`, `wvkbd`, `phoc`, `phosh`, `squeekboard`, `lomiri`, `onboard`, `mutter`, `iio-sensor-proxy`, `plasma-mobile` (shell) | | | | |

No project in this list publishes an idle-memory figure in the sources I read, so **no RAM or CPU number appears anywhere in this note**.
Choosing on RAM needs an on-device measurement (see "What I could not verify").

## A.4 Animations on X11 under termux-x11: what they cost

- **Window managers:** openbox's only built-in animation switch is `animateIconify` (above). I found no workspace-switch or open/close
  animation in openbox, fluxbox or jwm configs (grep of `animat`/`fade`; absence, not proof). Animation on X11 is normally the
  **compositor's** job.
- **picom v13** (man page `man/picom.1.adoc`): default backend is `xrender` (l.224); `glx` needs "proper OpenGL 3.3 support" (l.228); shaders are
  glx-only, "the xrender backend doesn't support shader at all" (l.789). **Animation scripts are a compositor feature, not a glx feature**:
  triggers `open`, `close`, `show`, `hide`, opacity changes, and `size`/`position`/`geometry` (the last "EXPERIMENTAL", l.519-541); presets
  presets `slide-in/out`, `fly-in/out`, `appear/disappear`, `geometry-change` (l.569-623); scripts are "evaluated once per frame" (l.643-650); `offset-x/y`, scale and
  `saved-image-blend` output variables (l.725-741). The xrender backend implements picture scaling (`src/backend/xrender/xrender.c:121-127, 327-356`).
  So a **picom with `backend = "xrender"` and an `animations` block** is the documented route to fades/slides without a GL context.
  I did not find a statement that animations need GLX; the man page's glx notes are about blur, inversion and shaders.
- **Does the server support what picom needs?** tx11's server recipe builds Composite, Damage, Render, RandR, Present, XFixes and GLX
  (`lorie/src/main/cpp/recipes/xserver.cmake:43-58, 147-163, 171, 183, 247`). Whether the running server registers each at start is in the
  un-cloned xserver submodule: **unverified**. The server also has DRI3 (`InitOutput.c:828`, `-disable-dri3` flag, l.330, 368).
- **Cost, honestly (reasoned):** `lorie` registers no-op acceleration hooks (`PrepareSolid/PrepareCopy/PrepareComposite = FalseNoop`,
  `InitOutput.c:1248`), so X Render work runs in software on the CPU, per frame, over every damaged area, and a compositor adds an
  offscreen pixmap per window and a full-frame composite on top. An animation is therefore CPU cost proportional to the animated pixels x
  frames, plus the idle cost of keeping the compositor and Composite redirection alive. With maximised, one-at-a-time windows (the launcher's
  rule) the animated area is the whole screen. No source gives a number; the developer must measure.
- **A cheaper place to animate (reasoned):** the Display is a window in the launcher's wall. The launcher already animates it from Android's
  side (`WallPageTransition` slides and scales pages, `SurfaceStandIn` freezes a copy of the surface during a motion;
  `WallPageTransition.java` and `SurfaceStandIn.java` class javadocs). Inside-the-X-server "window open/close" is then redundant for one
  maximised app; only in-window animation (menus, panel) needs a compositor. This argues for **no compositor by default**.

## A.5 Phone-first environments

| | Display server | Rotation | OSK | On termux-x11? | Idle memory |
|---|---|---|---|---|---|
| **Sxmo, X11 variant** (`dwm`) | Xorg + dwm ("Sxmo runs both on Wayland (using sway) as well as on Xorg (using dwm)", `docs/sxmo.7.scd:24`; session wrappers `sxmo_dwminit.sh`, `sxmo_swayinit.sh`, `sxmo.7.scd:102`) | `sxmo_rotate.sh`: `xrandr -o inverted/normal` + `xinput set-prop <touchscreen> "Coordinate Transformation Matrix"` (`scripts/core/sxmo_rotate.sh:9-11, 37-46, 70-101`); auto-rotate via `iio-sensor-proxy`/`monitor-sensor` (`sxmo_autorotate.sh:12-22`) | `svkbd` on Xorg, `wvkbd` on Wayland (`sxmo.7.scd:434, 517-531`), shown by a bottom-edge swipe (`lisgd` gestures, `sxmo.7.scd:491-495`) | **Rotation would not work**: `xrandr -o` needs more than `RR_Rotate_0` (above); sensors absent in Termux/proot (reasoned). `dwm` is in tpk but `svkbd`, `lisgd` are not. The X11 variant also drives touch by `xinput` IDs (`sxmo.7.scd:1111-1115`), written for real input devices; termux-x11's input is synthetic (reasoned) | no figure in sources |
| **Sxmo, Wayland variant** (`sway`) | wlroots compositor | `swaymsg ... transform` / `wlr-randr` (`sxmo_rotate.sh:14-16, 79-92`) | `wvkbd` | sway 1.10.1 and wlroots are in tpk, and tpk builds wlroots with backends `x11,termuxgui` (`x11-packages/wlroots/build.sh`, `-Dbackends=x11,termuxgui`), i.e. a **nested** wlroots compositor in an X11 window is built. `wvkbd`, `lisgd`, `superd`-style services are not in tpk. Not tried | no figure |
| **Phosh** | "A pure Wayland shell" (`phosh@v0.58.0 README.md:3`), companion compositor `phoc` | `PhoshRotationManager` via `iio-sensor-proxy` or an explicit transform (`src/rotation-manager.c:20-29`, `debian/control:89`) | `squeekboard` is Phosh's OSK (reasoned: not read in these sources; osk-manager in `src/home.c:21`) | `phoc` documents running nested "with either the X11 or Wayland backend" (`phoc@v0.58.0 doc/gettingstarted.md:20-30`; `WLR_BACKENDS`, `doc/phoc.rst:58`). **So it can run as a window on an X server in principle, via wlroots' X11 backend.** None of phosh, phoc, squeekboard is in tpk, so it needs building or a proot distro that packages it. Needs GLES through the X server's GL path (reasoned). Not tried | no figure in sources |
| **Plasma Mobile** | KWin Wayland: README launch line `QT_QPA_PLATFORM=wayland dbus-run-session kwin_wayland --xwayland "plasmashell -p org.kde.plasma.mobileshell"` with `--width/--height` "for the window size" (`plasma-mobile@v6.8.2 README.md:50-61`); `plasma-mobile.service` replaces a getty and requires logind (`plasma-mobile.service`) | kwin | (not read) | The README's "start the shell in a window" mode means a nested kwin is the developer path. tpk has `plasma-workspace` and `kwin-x11` 6.7.5 but **not the mobile shell**; Plasma Mobile itself is Wayland, so X11-only is not an option | no figure |
| **Lomiri** | not stated in the sources I could read (README silent). Community/Mir docs describe a Mir/Wayland stack (not a primary Lomiri statement) | not read | not read | not in tpk; **unverified** | no figure |

### Wayland on Termux today

- **termux-x11 has no Wayland path:** a case-insensitive search of the whole tx11 tree (`6dfb8b4`, excluding submodules) for "wayland" finds
  nothing. It is an X server only (README "About": "a fully fledged X server").
- **Wayland compositors do exist in tpk**, all as nested or headless clients: `labwc` 0.8.2, `sway` 1.10.1, `weston` 16.0.2 (built
  `-Dbackend-default=headless`, `-Dbackend-drm=false`), `wlroots` (backends `x11,termuxgui`), `xwayland` 24.1.14, `libwayland`.
  `termuxgui` is a separate Termux:GUI app backend, not the launcher.
- **For us:** an embedded X-only server means phone-first Wayland shells only work **nested**: a wlroots/KWin compositor as one X client,
  and the shell inside it. That costs an extra compositor process plus its GL/software rendering on top of the X server's own (reasoned), so
  it is the opposite of "lightest". No "Xwayland in reverse" is needed; wlroots' X11 backend is that path.

---

## What this means for us (Part A)

Ranked. Reasons tie to the sources above; none rests on a measured RAM number.

1. **Openbox (already shipped) + tint2 + optional picom (xrender, animations off by default).** Reasons: openbox and tint2 and picom are
   all in tpk at the versions above; openbox is already what the launcher starts, with the launcher's rc (`X11CliInstaller.java:830`); both
   handle a root-window resize, which is exactly what rotation is here (A.2); tint2's `panel_size`/`panel_position` are config knobs for a
   touch-sized bar in each orientation; picom v13's xrender backend has documented animation scripts with no GL requirement (A.4).
2. **IceWM (one process, built-in taskbar).** Reasons: in tpk (4.1.0), explicit RandR size handling (`wmmgr.cc:3904`); a built-in taskbar/panel would mean one
   fewer process than openbox + tint2 (reasoned from my knowledge of icewm, not read in these sources). Cost: the launcher's "maximise every window, no decoration"
   rule is written in openbox's rc, so the rule has to be re-expressed in IceWM's own config (not investigated).
3. **LXQt on top of the existing openbox** as an opt-in "full desktop" tier, not the preset. Reasons: `lxqt-session` stands down when a WM
   holds the screen (`de-sessions-research.md` §5), so it is the DE already known to coexist with what the launcher starts; XFCE is the
   conflicting one (xfwm4 refuses). Heavier (Qt6 stack); no number.

Not recommended as the preset: Phosh, Plasma Mobile, Sxmo-sway (all nested-Wayland; none of the shells in tpk apart from sway/labwc),
Sxmo-dwm (rotation depends on `xrandr -o`, svkbd/lisgd not packaged), KDE Plasma X11 and XFCE (largest dependency sets in the table).

**What a shippable preset would contain** (reasoned; nothing built):

| Piece | Content |
|---|---|
| Packages (`.deb` metapackage, e.g. `termux-launcher-desktop-lite`) | `openbox`, `tint2`, `xorg-xrandr`, optionally `picom`, `wmctrl`/`xdotool` for the hook; all in tpk |
| Config files | `rc.xml` (the existing launcher one, extended: touch-sized fonts, `A-Tab`), `tint2rc-portrait` and `tint2rc-landscape` (`panel_position`, `panel_size` height in px, taskbar + clock, `autohide` optional), `picom.conf` (`backend = "xrender"`, `vsync = false`, an `animations = (...)` block, disabled by default) |
| Rotation hook | the launcher knows the orientation (activity config change). It points a `tint2rc` symlink at the right file; tint2 re-reads on its own restart triggered by the root resize (`main.c:366-369`); openbox needs nothing. Ordering between the symlink swap and the X resize is a race, not investigated: needs a device test |
| OSK | none: the launcher's in-app keyboard through `X11KeyboardBridge`. Fallback if a user runs the display outside the launcher: `matchbox-keyboard` (smallest dependency list in the table) |
| DPI / size | rely on `X11Defaults.defaultDpi` and the scale rail; per-orientation scale would have to be written by the launcher (today one value, `X11PaneFrame.applyScaleStep`) |
| Reproducibility | pin the `.deb` versions in tlstore; config files shipped in the package, not generated at first run |

## What I could not verify (Part A)

- **Idle RAM and idle CPU of every candidate.** No project publishes a figure in the sources I read; nothing was run. Needs `ps`/`smem` on
  the device after 5 minutes idle for: openbox alone, openbox + tint2, + picom xrender, icewm, lxqt on openbox.
- That the **running server registers** Composite/Damage/RandR (only the build recipe was read; xserver submodule not cloned), and the
  behaviour of the vendored `libXlorie.so` (`9df8b76`) versus master (`6dfb8b4`).
- Whether picom's xrender animations run at an acceptable frame rate on a phone through software Render; whether picom 13 works at all
  against the lorie Present/DRI3 setup (tpk's picom depends on `libepoxy`/`opengl`, which does not mean the xrender path avoids loading them).
- jwm's and fluxbox's reaction to a root resize beyond the event selection; touch behaviour of IceWM/fluxbox/jwm; openbox's behaviour at
  runtime on `screen_resize` for already-maximised windows (name only).
- Phosh/phoc/Plasma Mobile nested on termux-x11: nobody's claim read that it works; only the documented nested mode of phoc and KWin.
  Lomiri: nothing primary read about its display stack. Phosh's OSK and Plasma Mobile's OSK/rotation not read.
- The Termux wiki (blocked), so no "official Termux guidance" is quoted here.

---

# Part B: options for an overview screen for the launcher itself

## B.1 What exists in the launcher (10 lines)

1. `PageOverviewView` (1808 lines) is "the page overview (Launcher3's overview for this wall): the wall zoomed out to one strip of cards, Home's
   pages first and then the current session's windows, with a gap at the seam" (class javadoc).
2. Cards can be dragged **within their own half** (Home / Workspace), removed (red button), made Home (home button); pills add a page, terminal or display
   after the focused card; back or a tap on a card zooms back in.
3. The wallpaper stays behind; the card the wall rests on is the **live launcher drawn scaled** (the activity root is alpha 0 but still draws its display
   lists and is redrawn into the card); other cards are **snapshots** (bounded store, only while open); a never-shown terminal is drawn from its screen text.
4. `OverviewGeometry` is arithmetic: `Capture` source for a card's picture (`WINDOW_COPY` via `PixelCopy` when nothing covers the page,
   `SUBTREE_DRAW` when a sheet covers it, `TEXT` for terminals), `framePosed`, and `tapZooms` (only the centred card zooms; others centre).
5. `OverviewGeometryTest` has four tests: uncovered pages are copied from the window, covered pages never are, frame pose is detected, only the centred card zooms.
6. `PageOverviewPolicy` (208 lines) is pure: card kinds `HOME_PAGE`/`TERMINAL`/`DISPLAY`, positions, the seam, where a drop may land, removal
   `REFUSED`/`AT_ONCE`/`WITH_UNDO`, when a display can be added (ADR 0008 "one wall, two halves"); the display's place is `DisplaySlot`.
7. Triggers today are **Home-only**: pinch-in on the Home page (`TermuxActivity` `onHomePinchIn`, guarded by `isWidgetsPageShowing()`, ~l.28902), the Home
   actions sheet item `EDIT_PAGES` (`HomeActionsSheetPolicy.java:34`, `TermuxActivity.java:27544`), and the settings/appearance intent (`~l.14130-14140`).
   `openPageOverview` is refused while the layout is locked (`~l.27278`).
8. Page motion machinery to reuse: `WallPageTransition` poses pages by translation/scale/alpha/turn only (class javadoc); `SurfaceStandIn` copies a `SurfaceView` (the Display)
   for the motion because "a tilt leaves it flat and a hardware layer strands it", freezing the picture for a few hundred ms.
9. The Display is a live `SurfaceView`, which is why it needs a still copy to join any scaled or tilted layout.
10. The git log shows the Edit-pages overview round was emulator-checked and awaits a pong pass (`93c35220f`); not re-verified here.

## B.2 The models

### niri Overview (niri v26.04; wiki `Overview`, `Gestures`, `Configuration:-Miscellaneous`, `Workspaces`)

- **Trigger:** `toggle-overview` bind, top-left hot corner, or touchpad four-finger vertical swipe (wiki `Overview.md`, `Gestures.md:63-67`; "Since: 25.05").
- **What it shows:** "a zoomed-out view of your workspaces and windows"; workspaces are a **vertical strip per monitor** (wiki `Workspaces.md`), one empty
  workspace always at the end; windows can be dragged between, above, below or into a new workspace; holding a drag over a workspace activates it;
  DnD at the edge scrolls (`Overview.md`).
- **Zoom:** config `overview { zoom }`; wiki says the range is 0..0.75; **default 0.5** (`niri-config/src/misc.rs:131`); clamped to 0.0001..0.75 in
  `compute_overview_zoom` (`src/layout/mod.rs:5014-5023`): `zoom_now = 1 - p * (1 - zoom)` where `p` is the open progress (0..1).
- **Animation:** one progress value `OverviewProgress { Animation, Gesture, Open }` (`src/layout/mod.rs:546-550`), so open/close and the live finger
  gesture share the same zoom function; a gesture is 300 units of vertical travel (`OVERVIEW_GESTURE_MOVEMENT`, `mod.rs:103`) with a swipe tracker,
  rubber band, and velocity handed to a **spring** on release (`mod.rs:3722-3790`); default spring `damping-ratio=1.0 stiffness=800 epsilon=0.0001`
  (wiki `Configuration:-Animations.md`, `overview-open-close`).
- **Input while open:** keyboard shortcuts keep working; mouse drags windows, right-drag scrolls workspaces; touchscreen: one-finger scroll, one-finger
  long-press to move a window (`Overview.md`). Bars on the *top*/*overlay* layers stay unscaled, *background*/*bottom* layers zoom with the workspaces.
- **Rendering model:** the layout is simply rendered at the interpolated zoom (every use of `overview_zoom()` in `layout/mod.rs`), i.e. live content scaled, not a
  snapshot (reasoned from the code shape).

### Phosh (phosh v0.58.0)

- **Structure:** `PhoshHome` "contains the overview and the home bar to fold and unfold the overview" plus a centre pill that toggles the OSK (`src/home.c:46-51`);
  states FOLDED/UNFOLDED, a drag threshold of 0.3 (`home.c:38`), drag handle = the home bar when folded (`home.c:161-190`).
- **Overview:** `PhoshOverview` "shows running apps (`PhoshActivity`) and the app grid (`PhoshAppGrid`)" (`src/overview.c:30-37`): a `HdyCarousel` of running
  activities above the app grid (`overview.c:59`), hidden when there are none (`overview.c:562-567`).
- **Cards:** a thumbnail per toplevel "obtained via phosh-private and wlr-screencopy Wayland protocols" (`src/toplevel-thumbnail.c:22-24`), requested at the card's
  allocation size times the scale factor (`overview.c:413-426`); a swipe-away container dismisses a card, fading it by `ease_out_cubic(1 - |progress|)`
  (`src/swipe-away-bin.c:62`, `activity.c:226,244` remove/undo).
- **Landscape:** each activity is given `win-width`/`win-height` from `phosh_shell_get_usable_area` (`overview.c:125-139, 493-518, 653-660`), so card size follows the
  current usable area after rotation; rotation itself is the `PhoshRotationManager` (`rotation-manager.c:20-29`). I did not find a separate landscape layout
  file; the adaptation is by size (reasoned).
- **Snapshots, not live scaling:** one still per card, refreshed (reasoned from `request_thumbnail` call sites at activity add/size change/ready).

### GNOME Shell (tag 51.1) and Plasma Mobile

- **GNOME Activities:** `js/ui/overview.js` `ANIMATION_TIME = 250` ms (l.12), states `HIDDEN/WINDOW_PICKER/APP_GRID` (`overviewControls.js:33-36`), a vertical
  `SwipeTracker` on the stage opens it (`overview.js:226-233`); the workspace thumbnail strip is `ThumbnailsBox` (`workspaceThumbnail.js:590`) with `MAX_THUMBNAIL_SCALE = 0.05`
  (l.19); window previews and thumbnails are **live `Clutter.Clone`s** of the window actors (`workspaceThumbnail.js:61`).
- **Plasma Mobile:** the task switcher is a **KWin effect**, package `kwin/mobiletaskswitcher` (`plasma-mobile@v6.8.2`, tree listing), beside `containments/taskpanel`.
  I did not read its source or any design page, so nothing on its gestures or animation is claimed.
- **i3/sway/Sxmo for comparison:** i3 "workspaces are an easy way to group a set of windows", created per screen (i3 guide §2.6); sway follows i3. Sxmo, on dwm and sway, uses
  tiling WMs with no visual overview ("Sxmo uses tiling window managers such as sway and dwm", `sxmo.7.scd:661`).

### Android: what a third-party launcher can and cannot overview

- The system Recents is a component named in system config: `config_recentsComponentName` = `com.android.launcher3/com.android.quickstep.RecentsActivity`, described
  as "the activity that will be presenting the Recents UI, which will receive special permissions for API related to fetching and presenting recent tasks"
  ("When using a different default launcher, change this appropriately", AOSP `frameworks/base/core/res/res/values/config.xml`, `main`). So the privileged
  recent-tasks API is granted per **system build configuration**, not per default-home app; **a third-party launcher cannot draw thumbnails of other apps**
  (reasoned conclusion from that comment; I did not read the permission enforcement code or the Launcher3 `quickstep/README.md`, which returned no content).
- Allowed for our own content: scale and layer our own views (the repo already does: the live card is a scaled redraw, snapshots via `PixelCopy` in `PageOverviewView`,
  surface copies in `SurfaceStandIn`). `RenderNode` / hardware-layer behaviour is not backed by an Android doc I fetched: **unverified**.

## B.3 Design options (factual; the developer decides)

All three overview **the launcher's own surfaces**: Home pages, terminal windows, the Display (and for Option 3, the windows inside the Display).

| | **Option 1: widen the existing strip (niri-class zoom of the whole wall)** | **Option 2: Phosh-style fold: card carousel above the app drawer** | **Option 3: GNOME-style strip + spread** |
|---|---|---|---|
| Trigger | Same strip, but openable from **every** wall page, not only Home: pinch-in anywhere on the wall (today `isWidgetsPageShowing()` gates it), plus an overview gesture on the border/dock (niri: four-finger swipe, hot corner, a bind; Phosh: home bar) | Swipe up from the dock or a home bar; the drawer is the lower half (Phosh `PhoshHome` fold/unfold, drag threshold 0.3) | Pinch-in or swipe-up opens a **page strip on top** and the chosen page spreads its items |
| What it shows | Home pages, then terminal windows, Display in its row place: exactly what `PageOverviewPolicy` already models | A carousel of the running things (terminal windows, Display; optionally Home pages) over the app drawer (as Phosh's activities over its app grid) | Page thumbnails as a strip (GNOME `ThumbnailsBox`), and for the focused page its content spread out: Home floats/widgets, or the X11 toplevels in the Display |
| Reuses | `PageOverviewView`, `PageOverviewPolicy`, `OverviewGeometry.Capture`, `DisplaySlot`, `SurfaceStandIn`, undo-snackbar removal | cards from `PageOverviewView`, `SurfaceStandIn` copy for the Display, the drawer; swipe-to-close = the existing `WITH_UNDO` removal rules | little; per-X-window pictures are new: the launcher has only the EWMH client list (`X11WindowList`), no per-window pixels (`pane-wall-x11-study.md` / phase-2 item 8) |
| Animation cost class | **Live scaled view for the centred card, snapshots for the rest** (exists today); niri's single progress value and release spring would make open/close and a drag-to-open share one curve | **Snapshots** for every card (Phosh: one screencopy still per card, refreshed); drawer is a normal view; cheapest per frame, stale pictures | Highest: GNOME's are live clones of every window (`workspaceThumbnail.js:61`); for us live per-item scaling plus the Display's SurfaceView copy; not cheap on a phone (reasoned) |
| Portrait | Horizontal strip of tall cards (as now) | Carousel across, drawer below; Phosh sizes cards from the usable area | Strip along the top, spread below |
| Landscape | Same strip; cards wider; zoom factor is the knob (niri default 0.5, wiki range 0..0.75) | Phosh re-reads the usable area and resizes cards (`overview.c:125-139`); drawer could sit beside the carousel instead of below (reasoned) | Strip could run down a side (GNOME's strip is the shell's own side element); unspecified here |
| Gaps in the sources | niri's DnD across workspaces vs our "own half only" rule is a policy choice (`PageOverviewPolicy`) | Phosh read at v0.58.0 only for structure; no frame timings | no touch-first GNOME design page read |

Facts that bear on all three: the overview cannot include other Android apps (above); the Display's `SurfaceView` takes no alpha/layer/turn and needs a still copy
for any scaled composition (`SurfaceStandIn` javadoc), which already exists; a niri-style gesture needs a progress value that both a finger and a spring can
drive (niri `OverviewProgress`), which `PageOverviewView` does not expose today (it animates open/close with a `ValueAnimator` and `DecelerateInterpolator`, imports
at the top of `PageOverviewView.java`; whether a finger can drive it was not checked).

## What I could not verify (Part B)

- Plasma Mobile's task-switcher design and animation (only the package's existence); Quickstep's README (empty fetch) and the exact permission enforcing
  Recents access; `RenderNode`-level behaviour for scaled hardware layers on our minSdk.
- That niri's overview renders live content (inferred from `overview_zoom()` call sites, not from the render code); the Phosh landscape layout (inferred from the
  usable-area calls only).
- How `PageOverviewView` behaves in landscape on a device (read from code, not run); the uncommitted working-tree edits to it were read as they stand.
- No frame-time or memory figure for any option; everything about cost class is from how each model draws, not from a measurement.
