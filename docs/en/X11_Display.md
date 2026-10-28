# The Linux display

The launcher can show a Linux desktop or any X11 app in a **display window**: a window of the
Workspace, in the row with your terminal windows, that shows one X display whole. The display's own window
manager arranges the apps inside it. The display server runs in your shell like any other command,
so everything you know from Termux:X11 (its flags, its preferences, `pkill termux-x11`) works the
same way. This page is the short route from installing the pieces to running a desktop in a proot.

There is nothing to switch on. The display starts when something asks for it: a Linux app tapped in
the app drawer, the **Display** entry under **Linux apps**, or `termux-x11` run from a shell. Its
window joins the end of your row, like a new window, and leaves when the display stops. There is
one display window at most. (Older versions had a Display place on the wall, a Display switch in
Settings, and a screen mark at the status bar's edge; the display window replaces all three.)

## Set it up

The launcher puts the `termux-x11` and `termux-x11-preference` commands in your `$PREFIX/bin`, so a
display can be started from any shell.

1. Install the keyboard layouts the server needs:

   ```sh
   pkg install x11-repo
   pkg install xkeyboard-config
   ```

   On a pacman-based install the X11 repository is already configured, so `pacman -S
   xkeyboard-config` is all it takes; on the VAJ edition skip the first line, its repository has
   the package directly. Until the layouts are installed the display window says so.
2. Open the bare display with **Display** under **Linux apps** in the app drawer, or start one from
   any shell:

   ```sh
   termux-x11 :0 &
   export DISPLAY=:0
   ```

3. Run something: `pkg install xorg-xeyes && xeyes`. It shows in the display window.

If you already have the `termux-x11-nightly` package installed, the launcher leaves its
`termux-x11` alone and tells you so. That package can only talk to the separate Termux:X11 app;
`pkg uninstall termux-x11-nightly` and reopen the launcher to use the built-in display instead.

On the default (apt) edition, installing `termux-x11-nightly` afterwards no longer replaces the
launcher's command: the package's copy is set aside as `termux-x11.upstream`. On the pacman
edition that package can't be installed beside the launcher's, and isn't needed.

## Every day

- **Get to it** like any window: drag along the Workspace from a window's border, or tap its chip
  in the status bar. Hold its chip and drag it to move the display window to another place in the
  row, as you would any window. Opening a Linux app or **Display** again brings it in front, and
  moves it into the session you are in if another session's row holds it. In the sessions drawer,
  **Displays** lists each
  display with the apps open on it; tap an app to bring it to the front. **Go to Display** is an
  action like any other: put it on the extra-keys row from its editor, on the in-app keyboard as a
  `tool:wall.display` key, or bind it in your keybinds file
  (`map ctrl+alt+shift+d wall.display`); see [Custom keybindings](Custom_Keybindings.md).
- **Typing.** The launcher's keyboard and its extra-keys row type into the display while it is
  showing, and a hardware keyboard is the display's entirely: every key and every chord goes to
  X, and the launcher's shortcuts and their hints stay out of the way. Leave the display by
  touch, by a window chip, or with the home button.
- **One clipboard.** Copy in an app on the display and the text is on the phone's clipboard,
  ready to paste anywhere; copy anywhere on the phone and it is there for the display's apps.
  The keyboard's copy, cut, paste, select all, undo and redo keys do on the display what they do
  on a desktop, the paste key on the extra-keys row pastes into the display rather than behind
  it, and **Fn** with the paste key types the text out instead, for an app that has its own idea
  of what Ctrl+V means. The two sides share by default; **Settings → Linux apps → Input → Share the
  clipboard** keeps them apart.
- **Switching.** The display window is one of your windows, so **Next window** and **Previous
  window** (the space bar's top corners, the extra-keys row, a key chord, `launcherctl`) step from
  it to the windows beside it and back, as from any window. Over the display the status bar keeps
  your windows' chips, with the display's own selected and wearing its front app's icon. To bring
  one of the display's apps forward, tap it under **Displays** in the sessions drawer, or use the
  display's own window manager.
- **The keyboard follows text fields.** Tapping a text field on the
  display brings the keyboard up and tapping elsewhere puts it down; see
  [The keyboard follows text fields](#the-keyboard-follows-text-fields).
- **The keyboard floats.** In either orientation the keyboard comes up over the display instead
  of squeezing it, so the picture keeps the size it was given and nothing on it moves as you type,
  whether the keyboard came up from a text field or by hand. To make room for the keyboard
  instead, press the keyboard mode button on the touchpad (shown on the pad while the keyboard is
  docked); it switches the orientation the phone is in, and each orientation keeps its own. If an
  older version left the display making room in portrait without you choosing it, the update
  puts it back to floating once; the same button switches it back.
  Settings → Linux apps → Input → Keyboard over the window switches both orientations
  at once.
- **The controls.** Hold a corner of the display and its buttons drop out of it, as they do on a
  terminal pane: power starts or stops the display, and the cog opens its settings. A
  tap in the corner goes to the display, so a maximised window keeps its own controls there. Tap
  anywhere else to put them away.
- **Touch.** **Settings → Linux apps → Input → Touch** says what a finger on the display does.
  **Touch** is the tablet (taps land where you touch): fingers are touches, so a page scrolls, pinches, drags and
  long-presses where you put them, and an app written for a touchscreen behaves as it would on
  one. Apps that only understand a mouse still get taps and drags as clicks. The keyboard follows
  text fields in this mode, and pinching zooms what is on the display rather than the picture of
  it; use the scale rail for that. **Trackpad** (drag to move the pointer, tap to click) turns the whole display into a laptop touchpad
  for apps that want a pointer and nothing else. **Direct** (every touch goes straight to the app) sends the same touches as
  Touch, without the launcher's keyboard and pinch behaviour on top. Apps started from the
  drawer are told to take touch, so Firefox needs no setup.
- **Mouse mode.** The **Mouse mode** action (on the extra-keys row, the in-app keyboard as a
  `tool:mouse.toggle` key, or a chord) swaps the keyboard for a touchpad of the same size while
  the display is showing. Over a split keyboard the parting widens instead and the touchpad
  stands in it, so both halves keep typing. Its gestures are a laptop's: a finger moves the pointer and a tap
  clicks; hold, or tap and touch again, then move, to drag. Two fingers scroll, keep going after a
  fast lift, and pinch to zoom; two fingers tapping together click the right button. Three fingers
  tapping click the middle button, swiping left or right switch windows, and swiping down bring
  the keyboard back. The arrow in the corner does the same. A strip down the pad's trailing edge
  scrolls with one thumb alone, no second finger needed, and coasts on after a flick the same way
  the two-finger scroll does. The keyboard is never far while the touchpad is up: tap a text
  field on the display and it comes to the front, and the touchpad is back the moment the field
  lets go. The keyboard key brings it forward by hand, and the mouse key sends it away again
  without leaving mouse mode. In the terminal the same action makes every touch the mouse
  for programs that take one: a finger is the left button, two fingers are the wheel, and a fast
  lift keeps it turning. A program that has not asked for the mouse gets nothing typed at it;
  two fingers scroll the transcript there instead. A small mouse at the end of the status bar
  says the mode is on; tap it, or the action again, to switch it off.
- **Back** puts the keyboard down when the display was the one that raised it: for a text field,
  or in front of mouse mode's touchpad. With no such keyboard up it goes back inside the app on
  the display instead, the way the button does everywhere else on the phone. A keyboard you opened
  yourself stays where it is.
- **Stop** from the long-press menu on the display, or close the display window from its chip's
  ×. Every app on it is asked to close itself first, the way it would when you close its window,
  and the display and its window go once they have; with an app open you are asked before that.
  A display that has not started yet simply closes. `pkill
  termux-x11` stops it outright instead, without giving apps that chance. The display never starts
  on its own unless you turn on **Start with the launcher**.
- **The display survives switching away.** Your X apps keep running while you are in a
  terminal window or on Home; only the picture is put away.
- **Options.** **Settings → Linux apps** holds them: its own rows, then four pages. A
  running display picks a change up at once.
  - On the page itself: **Show Linux apps in drawer**, **Set up Linux apps**, **Hidden Linux
    apps** and **Window manager**.
  - **Input**: **Touch**, **Use Android keyboard**, **Keyboard pops up** and **Share the clipboard**.
  - **Resolution and scaling**: **Resolution**, **Scale**, **Fixed size** or **Custom size**,
    **Scaling** (filtering) and **Display density**.
  - **Startup**: **Start with the launcher**, **Start command**, and **Connect new shells**
    (`DISPLAY` set in every new shell while a display runs).
  - **Fixes**: **Compatibility drawing** and **Swap colour channels**.

  Where the extra keys stand while the display is showing (along the bottom, or in a column on the
  left or right edge) is the extra keys' **Position** on the Dock card, in Appearance opened on a
  display window.
- **Preferences from a shell.** `termux-x11-preference list` shows every setting;
  `termux-x11-preference touchMode:2` sets one, exactly as in Termux:X11, and the running
  display follows.
- **Server flags.** `termux-x11 :0 -ac -dpi 240 -legacy-drawing -force-bgra -xstartup "xfce4-session"`:
  all Xorg flags and Termux:X11's own are accepted. `-legacy-drawing` (the **Compatibility
  drawing** switch) is the one to try if the picture stays black on an unusual GPU.

## The keyboard follows text fields

Tap a text field on the display and the keyboard comes up; tap anywhere else and it goes away
again. It is on by default (**Settings → Linux apps → Input → Keyboard pops up**) and it
works in every touch mode. A tap counts where its click lands: under your finger in Touch and
Direct, at the pointer in Trackpad.

A keyboard you opened yourself is yours: it stays until you close it, and taps on the desktop
never take it away. Only a keyboard that came up for a text field is put away for one.

With mouse mode on, the keyboard and the touchpad share one frame, so the field brings the keyboard
to the front of it and the touchpad waits behind rather than going away. Tap somewhere that is not
text, or press the mouse key, and the touchpad is back where it was. A tap on the touchpad counts as
a tap on whatever the pointer is standing on.

Two signals tell the launcher that a text field is under your finger.

**The pointer's shape.** Nothing to set up. X tells the launcher nothing about focus, but the
window under the pointer names the cursor it wants (`xterm` over text, `left_ptr` over most other
things) and the display server passes that name on. Only the name seen in the moment after a tap
counts, so a pointer crossing a text field on its way somewhere else opens nothing. An app that
never asks for a text cursor, or a session with no cursor theme installed, cannot be recognised
this way; the keyboard key, the display's Back button and the signal below all still work.
GTK and Qt apps pick up the Adwaita theme that ships with them; core X apps such as xterm only
use it with `XCURSOR_THEME=Adwaita` in their environment (or a `default` cursor theme that
inherits it).

**Your input method's own focus.** Exact, and on by itself once fcitx5 is installed. fcitx5 knows
when a text field takes focus and when it loses it, whether you tapped, pressed Tab, or a dialog
put the cursor in its entry, and the launcher listens to it. **Set up Linux apps** installs it with
the rest; by hand it is:

```sh
pkg install fcitx5 fcitx5-gtk3 fcitx5-qt
```

(`fcitx5-gtk4` and `fcitx5-gtk2` are there too, for apps on those toolkits.) Then stop the display
and start it again. From then on, whenever the display starts:

- a small helper, `termux-x11-im`, starts beside it, with a session bus of the display's own and
  fcitx5 on it. The helper stands in for fcitx5's virtual keyboard, so fcitx5 tells it when a
  field takes and loses focus, and it passes that on to the launcher;
- every app you start from the drawer is given `GTK_IM_MODULE=fcitx`, `QT_IM_MODULE=fcitx`,
  `XMODIFIERS=@im=fcitx` and that bus, so its text fields report to fcitx5.

Without fcitx5 nothing starts and nothing changes. Two limits: an app you start yourself from a
shell gets none of this unless you export the same variables and
`DBUS_SESSION_BUS_ADDRESS=unix:path=$PREFIX/tmp/termux-x11-im-bus-0` (the last number is the
display's), and apps inside a proot container keep the pointer's shape for now. The helper writes
what it did to `$PREFIX/tmp/termux-x11-im-bus-0.log`; it says `focus in` and `focus out` as it
passes them on. This is not yet device-verified.

**Once focus is heard, it decides.** From the first `focus in` or `focus out` that reaches the
launcher while you are on the display window, until you leave it or switch the setting off:

- a field taking focus brings the keyboard up, tap or no tap;
- a field losing focus puts it away 150 ms later, unless another field takes focus first, so
  moving from one field to the next leaves the keyboard where it is;
- a tap over a text cursor only brings the keyboard back if a field has focus, so tapping a
  terminal or the text of a page no longer opens it;
- a tap or a scroll anywhere else leaves the keyboard alone; losing focus is what puts it away.

Until the first focus signal of a visit, and always without fcitx5, the pointer's shape decides as
above. A keyboard you opened yourself, and a keyboard you switched off, win over focus as they do
over taps. Under `adb logcat -s X11TextFocus` every decision names the signal behind it:
`focus in -> show`, `focus out -> hide in 150 ms`, `hide cancelled: focus in`,
`tap over xterm ignored: no focus`.

This section is for the Termux edition. On the Nix edition the helper is not started yet; see
[Exact text-field focus](Nix_Package_Management.md#exact-text-field-focus).

The helper sends the same two commands you can send from a shell yourself, so anything else that
knows when a field takes focus can drive the keyboard too:

```sh
launcherctl keyboard show --source focus
launcherctl keyboard hide --source focus
```

They are read as a signal, not an order: on the display they go through the rules above, and
elsewhere they simply open or close the keyboard. `--source manual` (the default) is you
asking, which pins the keyboard until you put it down again.

## Sound for Linux apps

The launcher runs one PulseAudio server for everything: terminal sessions, Linux apps from the
drawer and proot distros. It needs `pulseaudio`, which "Set up Linux apps" installs
(`pkg install pulseaudio` does too), and two switches under **Settings › Terminal › Linux apps ›
Audio** control it, both on by default.

- **Sound.** The server starts with your first terminal or Linux app and keeps running when you
  close them, and when you choose Exit in the notification; it suspends its output when idle, so it costs next to nothing. It plays through
  AAudio, or OpenSL ES on a phone where AAudio will not open. If it stops, it starts again at
  once; after three stops within a minute it stays off and the page says so, until your next
  terminal or Linux app.
- **Media controls.** A Linux player that speaks MPRIS (kew, Firefox, VLC, mpv with its MPRIS
  plugin and most desktop players) shows in Android's media controls: the notification, the lock
  screen, Bluetooth buttons, and the launcher's own media widgets, with its title, artist and
  local cover art. Play/pause, previous, next and seeking work when the player offers them. While
  anything plays through the server, the launcher holds Android's audio focus: when another app
  starts playing, Linux players pause (and resume after a short interruption such as a navigation
  prompt), and sound from apps without MPRIS is muted, or lowered where Android allows it.

How apps find the server: every terminal session and Linux app gets `PULSE_SERVER`; anything
else in Termux (ssh, Termux:Widget, services) reads
`$PREFIX/etc/pulse/client.conf.d/termux-launcher.conf`; and each proot distro gets
`/etc/pulse/client.conf.d/termux-launcher.conf`, which points at `127.0.0.1:4713` with a copy of
the server's cookie, so other apps on the phone cannot use it. The setup guide also installs
ALSA's PulseAudio plugin in the distro and writes `/etc/asound.conf` (unless you have your own).
Terminal sessions and Linux apps also get a session bus (`DBUS_SESSION_BUS_ADDRESS`) when the
`dbus` package is installed; that bus is what media players announce themselves on.

**Your own setup comes first.** If `~/.config/pulse` holds a configuration of yours
(`client.conf`, `default.pa`, `daemon.conf` and the like), or a PulseAudio you started with options
of your own is running, the launcher starts nothing and writes nothing, and the page says it is
using your own setup. A server that an app merely started for itself, with no configuration, is
replaced by the launcher's. You no longer need `pulseaudio --start` in your shell's startup files;
with the launcher's server running it only prints that it refuses to start a second one.

Turning **Sound** off stops the server and the bus at once and removes the launcher's drop-ins.
With `fcitx5` installed, apps opened from the drawer in the Termux prefix join the display's own
bus (it carries text-field focus), so a player among them does not show in the media controls.

## GPU acceleration for your apps

The display server itself always draws in software: X apps render into their own buffers and hand
the finished pixels to the server. What can be accelerated is the *client* side (a game, a
browser, a compositor) through one of the profiles below. Pick the first one that matches your
phone, install its packages, and export its variables in the shell that starts the app (or in
the proot's login shell). The launcher never sets these for you.

| Profile | Packages | Export before starting the app | Also run | For |
|---|---|---|---|---|
| `turnip-zink` | `mesa`, `mesa-vulkan-icd-freedreno` (the Vulkan loader comes with mesa) | `MESA_LOADER_DRIVER_OVERRIDE=zink TU_DEBUG=noconform` (add `VK_ICD_FILENAMES=$PREFIX/share/vulkan/icd.d/freedreno_icd.aarch64.json` if several ICDs are installed) | nothing | Qualcomm Adreno phones (`/dev/kgsl-3d0` exists) |
| `virgl` | `virglrenderer-android`, `mesa` | `GALLIUM_DRIVER=virpipe MESA_GL_VERSION_OVERRIDE=4.3COMPAT MESA_GLES_VERSION_OVERRIDE=3.2 MESA_NO_ERROR=1 LIBGL_DRI3_DISABLE=1` | `virgl_test_server_android &` | most phones with a working Android GL driver; Mali is often unreliable |
| `virgl-angle` | as `virgl`, plus `angle-android` | as `virgl` | `virgl_test_server_android --angle-gl &` | Mali, Xclipse and PowerVR phones where plain `virgl` misbehaves |
| `vulkan-wrapper` | `vulkan-wrapper-android`, `mesa` | `VK_ICD_FILENAMES=$PREFIX/share/vulkan/icd.d/wrapper_icd.aarch64.json MESA_LOADER_DRIVER_OVERRIDE=zink` (Mali: add `MESA_VK_WSI_PRESENT_MODE=mailbox MESA_VK_WSI_DEBUG=blit`) | nothing | non-Adreno phones with a Vulkan driver |
| `software` | `mesa` | `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe MESA_LOADER_DRIVER_OVERRIDE=llvmpipe` | nothing | everything; the floor |

The launcher can pick for you: `launcherctl x11 gpu` says which row fits this phone and what is
installed, and `launcherctl x11 gpu --env` prints the exports to paste or `eval` (see
[launcherctl](LauncherCtl.md#x11-gpu)). You choose on **Settings → Linux apps → Set up
Linux apps → Graphics acceleration**: the page lists every row that fits this phone with whether it
is installed and its last benchmark score, and you pick one (**Automatic** follows the ranking
above) or switch single apps to **Draw in software**. Where a chip is known to freeze with virgl
(a Mali-G52 so far), the page says so and software is ranked first.

To let the phone decide by trying, tap **Run benchmark** on that page: it opens the terminal and
runs `termux-x11-gpu-setup` (you can also run it yourself). It installs the drivers for every row
that fits this phone, runs a short 3D test with each on a private display, keeps the fastest that
really uses the GPU and is clearly faster than software, removes the rest again, and leaves every
row's result for the page. The list of rows comes from the launcher (`launcherctl x11 gpu
--table`), so the script and the page always agree. It explains each
step as it goes, asks before installing anything, and writes what it decided to
`~/.config/termux-launcher/x11-gpu.env`. With a Debian proot installed it repeats the test inside
the container. `--keep` leaves every package in place, `--skip-proot` skips the container,
`--yes` skips the question, and `--display=N` runs the tests on display `:N` (`:7` by default).

Check what you got with `glmark2-es2` (from `x11-repo`): its first lines name the renderer, and
the score with the display window *showing* is the one that means anything; hidden, the server has
no surface and the number is meaningless.

Mesa 26 picks its own path unless all three of `LIBGL_ALWAYS_SOFTWARE`, `GALLIUM_DRIVER` and
`MESA_LOADER_DRIVER_OVERRIDE` agree, so set every variable a row lists, not just one.

## Apps on the display

The Linux apps installed in your prefix appear in the app drawer under **Linux apps**, beside your
Android apps, with their own icons. Tapping one starts the display if it is not running, opens the
app on it full size, and opens or brings up the display window. Nothing to type. **Display**, in
the same list, opens the bare display. They can be pinned and searched like any other app. `pkg install firefox`, and Firefox is in the
drawer the next time you return to the launcher.

Apps installed inside a distro are listed the same way, under **Linux apps** in the drawer, and
the launcher can set a distro up for you; see [Linux apps from a distro](Linux_Apps_From_A_Distro.md).
An app whose menu entry asks for a terminal opens in a new terminal window, right after the current
one, rather than on the display.

Two things make this work:

- **The window manager.** The launcher starts `openbox` with the display (install it with
  `pkg install openbox`) for single apps, with a launcher-owned rule that maximises every window,
  so windows open full size and dialogs stay on screen. **Settings → Linux apps →
  Window manager** is where another command goes, or an empty box for none. A whole desktop session brings its own
  window manager instead: openbox steps aside while the desktop runs and comes back once it closes.
  Windows fill the display to its rounded corners, as on any rounded desktop, so a terminal that
  wants its first and last cells clear of the arcs pads its own window (`window_padding_width` in
  kitty, `window.padding` in alacritty).
- **Show Linux apps in drawer**, in **Settings → Linux apps**, can be switched off if
  you would rather start apps from a shell.

When an app is started from the drawer, the launcher exports the GPU settings from the table
above for the profile you picked, or on **Automatic** for the best profile whose packages are
installed (other than `software`, except on a chip known to freeze), so a GPU app is accelerated
without any setup. A profile that needs a helper (virgl's `virgl_test_server_android`) gets it
started before the app, once, unless one is already running. A picked profile that is not
installed draws in software, and you are told. After `termux-x11-gpu-setup`, only the winner's packages remain. The first time the display is used, the text size and touch mode are set to values
that suit a phone; both can be changed afterwards.

## A desktop in a proot

The display server's socket lives in the launcher's `$TMPDIR`, so a proot needs to share it:

```sh
pkg install proot-distro
proot-distro install archlinux
termux-x11 :0 &
proot-distro login archlinux --shared-x11 -e DISPLAY=:0
```

On arm64 phones the Docker `archlinux` image is amd64-only; install Arch Linux ARM from the `menci/archlinuxarm` image instead (`proot-distro install menci/archlinuxarm --name archlinux`). The Linux apps setup does this for you.

`--shared-x11` hands the container the socket directory and nothing else; `--shared-tmp` shares
the whole temporary directory and works too. Neither is on by default. `DISPLAY` has to be passed
in with `-e`; exporting it in your Termux shell does not carry it across. The server needs no
`-ac` and the container needs no `xauth`.

Inside, install a single app as usual, or a whole desktop (`pacman -S xfce4`). Neither needs
starting by hand from here: an app appears in the app drawer under Linux Apps, a desktop appears
under Desktops, and a tap opens either one (a desktop takes over the display until you close it);
see [Linux apps from a distro](Linux_Apps_From_A_Distro.md). Starting a desktop by hand still
works too, the same way termux-x11's own instructions do: a desktop needs a message bus and
`startxfce4` starts none of its own, so run `dbus-launch --exit-with-session startxfce4` after
logging in as above.

GPU profiles work inside the proot too: install the *distro's* Mesa, export the same
variables in the proot shell, and, for the `virgl` profiles, keep `virgl_test_server_android`
running in Termux and export `VTEST_SOCKET_NAME=/tmp/.virgl_test` inside the proot.

### Phosh

Phosh's compositor, `phoc`, can run on the display as an X11 client:

```sh
export WLR_BACKEND=x11 WLR_X11_OUTPUTS=1 WLR_NO_HARDWARE_CURSORS=1
WLR_RENDERER=pixman phoc          # works everywhere, software rendering
```

The GPU path (`WLR_RENDERER=vulkan` on an Adreno phone) needs a Mesa built for the Android
kernel driver (`-Dfreedreno-kmds=kgsl`) and the wlroots patches from
[phosh-termux-gpu](https://github.com/Azkali/phosh-termux-gpu), which copies each frame out of
Vulkan into the display over shared memory. Stock distro packages fail with "no DRM FD
available" (the display has no DRI3 to offer) and their Turnip sees only llvmpipe. Keep apps on
`GSK_RENDERER=cairo`.

Checked on a Nothing Phone 2: `phoc` from a Debian trixie proot (`proot-distro install
debian:trixie`, then `apt install phoc`) comes up on the display with the pixman renderer,
unpatched. Newer `proot-distro` builds name images Docker-style (`debian:trixie`, `ubuntu:24.04`);
the container is then just `debian`.

## When something is off

A display that will not start, apps that cannot reach it, a black picture or nothing accelerated:
see [The Linux display will not start, or apps cannot reach it](Launcher_Troubleshooting.md#the-linux-display-will-not-start-or-apps-cannot-reach-it).

## On the VAJ edition

On the VAJ edition the display works the same way, but there is no separate `x11-repo`: its own
repository carries what the display needs directly, so it is `pkg install xkeyboard-config` alone,
and `pkg install openbox` for the window manager. The same repository carries a small set of
graphical apps (a file manager, a text editor, a terminal) but no browser yet.
