# The Linux display

The launcher can show a Linux desktop or any X11 app beside the terminal, as the **Display**
place of the pane wall — swipe left on the status bar from the terminal, or tap **Display** in
the expanded status bar. The display server runs in your shell like any other command, so
everything you know from Termux:X11 (its flags, its preferences, `pkill termux-x11`) works the
same way. This page is the short route from switching it on to running a desktop in a proot.

## Turn it on

1. Swipe to the Display place and tap **Turn on**, or open **Settings → Launcher & apps** and
   switch on **Linux display**. The Display place is always there; switching the display on puts
   the `termux-x11` and `termux-x11-preference` commands in your `$PREFIX/bin` so a display can
   be started from it.
2. Install the keyboard layouts the server needs:

   ```sh
   pkg install x11-repo
   pkg install xkeyboard-config
   ```

   Until they are installed the Display place says so and the Start button is hidden.
3. Start a display from any shell:

   ```sh
   termux-x11 :0 &
   export DISPLAY=:0
   ```

   or tap **Start display** on the Display place, which runs the same command.
4. Run something: `pkg install xorg-xeyes && xeyes`. Swipe to the Display place to see it.

Switching the display off in Settings takes the two commands back out of `$PREFIX/bin`; the
Display place stays and offers to turn it on again. Running servers are not stopped.

If you already have the `termux-x11-nightly` package installed, the launcher leaves its
`termux-x11` alone and tells you so. That package can only talk to the separate Termux:X11 app;
`pkg uninstall termux-x11-nightly` and reopen the launcher to use the built-in display instead.

## Every day

- **Move between places** by swiping left or right anywhere on the status bar, or by tapping the
  Widgets and Display buttons in the expanded status bar. Home always returns to the terminal.
  Nothing is bound to the wall by default; to reach the display from the keyboard, bind the
  `wall.go` action in your keybinds file — `map ctrl+alt+shift+d wall.go page=display`
  (`terminal`, `widgets`, `left` and `right` are the other pages) — see
  [Customize keyboard bindings](Terminal_Modernization.md#customize-keyboard-bindings).
- **Typing.** The launcher's keyboard and its extra-keys row type into the display while it is
  showing. With a hardware keyboard, everything goes to the display except the launcher's own
  Ctrl+Alt shortcuts, so the ways back to the terminal always work.
- **Back** is the display's: it toggles the keyboard, as in Termux:X11. Change that with
  `termux-x11-preference`.
- **Stop** by holding the Display segment of the place switch, from the long-press menu on the
  display, or with `pkill termux-x11`. Everything running on the display closes with it. The
  display never starts on its own unless you turn on **Start with the launcher**.
- **The display survives switching away.** Your X apps keep running while you are on the
  terminal or the widget grid; only the picture is put away.
- **Options.** Settings → Launcher & apps → **Display options** holds touch mode, resolution,
  text size, clipboard sharing and how the launcher starts a display: with the launcher, with a
  command of your choosing, pointing new shells at it (`DISPLAY` set in every new shell while a
  display runs), and two compatibility switches. A running display picks a change up at once.
- **Preferences from a shell.** `termux-x11-preference list` shows every setting;
  `termux-x11-preference touchMode:2` sets one, exactly as in Termux:X11, and the running
  display follows.
- **Server flags.** `termux-x11 :0 -ac -dpi 240 -legacy-drawing -force-bgra -xstartup "xfce4-session"`
  — all Xorg flags and Termux:X11's own are accepted. `-legacy-drawing` is the one to try if the
  picture stays black on an unusual GPU.

## GPU acceleration for your apps

The display server itself always draws in software: X apps render into their own buffers and hand
the finished pixels to the server. What can be accelerated is the *client* side — a game, a
browser, a compositor — through one of the profiles below. Pick the first one that matches your
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
installed, and `launcherctl x11 gpu --env` prints the exports to paste or `eval`. The same
answer is at the bottom of Display options in Settings.

Check what you got with `glmark2-es2` (from `x11-repo`): its first lines name the renderer, and
the score with the Display place *showing* is the one that means anything — hidden, the server has
no surface and the number is meaningless.

Mesa 26 picks its own path unless all three of `LIBGL_ALWAYS_SOFTWARE`, `GALLIUM_DRIVER` and
`MESA_LOADER_DRIVER_OVERRIDE` agree, so set every variable a row lists, not just one.

## A desktop in a proot

The display server's socket lives in the launcher's `$TMPDIR`, so a proot needs to share it:

```sh
pkg install proot-distro
proot-distro install archlinux
termux-x11 :0 -ac &
proot-distro login archlinux --shared-tmp -e DISPLAY=:0
```

Inside, install and start any X11 desktop or app as usual (`pacman -S xfce4 && startxfce4`, or a
single app). GPU profiles work inside the proot too: install the *distro's* Mesa, export the same
variables in the proot shell, and — for the `virgl` profiles — keep `virgl_test_server_android`
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
Vulkan into the display over shared memory. Stock distro packages fall back to software. Keep
apps on `GSK_RENDERER=cairo`.

## When something is off

- **"No display is running" although you started one.** The server exits before it opens a port
  when the keyboard layouts are missing — install `xkeyboard-config`. Run `termux-x11 :0` in the
  foreground to read its message.
- **A black display.** Start the server with `-legacy-drawing`; if that helps, keep it in the
  start command.
- **Apps cannot open the display from a proot.** Log in with `--shared-tmp`, and check
  `echo $DISPLAY` inside.
- **Nothing is accelerated.** `glmark2-es2` names the renderer in its first lines — `llvmpipe`
  means a profile variable is missing or the profile does not fit this GPU.

On the VAJ edition the display works the same way, but its package repository does not carry
`x11-repo` yet.
