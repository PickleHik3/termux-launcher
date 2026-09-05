# Troubleshooting

Start with the symptom below. Avoid uninstalling the app as a generic fix: Android deletes the
private Termux home directory when the app is uninstalled.

## An APK will not install or update

Check all three identities:

1. **Edition:** standard is `com.termux`; VAJ is `io.vaj.tl`.
2. **Signing family:** Termux, its launcher build, and add-ons must use compatible signatures.
3. **Architecture:** most current phones use `arm64-v8a`; use `universal` when unsure and available.

Android messages such as “App not installed” or an incompatible-update error commonly mean the
package name matches but the signing certificate does not. Download the matching APK from the
[official project releases](https://github.com/PickleHik3/termux-launcher/releases). Do not uninstall
the existing app until its files are backed up.

## The first shell or bootstrap does not finish

Keep the app open and allow the first bootstrap extraction to complete. Ensure the device has free
internal storage. If a shell opens but package metadata is incomplete, run:

```sh
pkg update
pkg upgrade
```

If the terminal cannot start at all, use the app icon's **New failsafe session** shortcut. A failsafe
shell avoids normal startup files so you can repair `.bashrc`, `.zshrc`, Fish config, or other shell
initialization.

## Shared-storage files are missing

Run:

```sh
termux-setup-storage
```

Approve Android's prompt, then check `~/storage`. Also open **Settings → Services & permissions →
Files and media** and resolve its status if needed. Android shared storage is separate from Termux's
private home directory.

## The keyboard is missing or the wrong keyboard opens

Open **Settings → Keyboard & input → On-screen keyboard** and choose the built-in keyboard, Android
keyboard, or none.

Also check:

- **Hide the on-screen keyboard** if a hardware keyboard is attached;
- the keyboard toggle in the terminal action row;
- **Adjust for on-screen keyboard** under **Terminal & status** for Android-keyboard overlap; and
- the independently remembered portrait and landscape keyboard heights.

If a custom layout fails, temporarily move `~/.termux/keyboard/layout.xml`, reload settings, and test
the bundled layout:

```sh
mv ~/.termux/keyboard/layout.xml ~/.termux/keyboard/layout.xml.disabled
termux-reload-settings
```

## Pane or window actions are unavailable

Open **Settings → Terminal & status → Split-pane controls**. Native pane and window actions are
disabled in single-pane compatibility behavior.

Remember that some actions have structural safeguards:

- the last tiled pane cannot be floated;
- the last shell is replaced with a fresh shell rather than leaving the app empty; and
- close actions may ask for confirmation because they terminate shells.

Search the command palette for the action. It shows disabled actions with a reason.

## A shortcut reaches the shell instead of the launcher

Shortcut meanings change when split-pane controls are off, and a custom binding can override a
default. Open the command palette and use **Key inspector**, then inspect:

```text
~/.termux/termux-launcher-bindings.conf
```

Compare it with the refreshed example under `~/.termux/launcher/examples/`. Reload after editing:

```sh
termux-reload-settings
```

## A workspace did not resume a program

A workspace is a terminal layout definition, not a process checkpoint. By default it restores
sessions, windows, panes, titles, and working directories, then starts normal login shells.

To offer command restart in v0.2.31:

1. enable **Also save what is running** while saving; and
2. approve running the recorded commands while loading.

Approved commands start again from the beginning. They do not continue from the previous instruction
or application state. Review hand-edited workspace JSON before running recorded commands.

Use **Append** when you want to keep the current hierarchy. Use **Replace** only after saving anything
important. Deleting a saved workspace removes its JSON definition but does not close running shells.

## App search does not start

Search is designed for an idle shell prompt. Confirm the current prefix under **Settings → Launcher &
apps → App search prefix**; `%` is the default. If the prefix conflicts with shell input, choose a
different single character.

For browsing without terminal input, use the A–Z row or enable it under **Launcher & apps →
Alphabets row**.

## CPU, memory, weather, media, or notifications are missing

Open **Settings → Terminal & status** and enable the desired status card. Then check **Settings →
Services & permissions**:

- weather requires location access;
- pinned notifications require notification access and matching notification rules;
- media information depends on an active Android media session; and
- some process details are more complete when an optional privileged backend is available.

Tap **Fix** only for features you intend to use. Weather data is provided by Open-Meteo.

## The launcher uses CPU or battery while it sits idle

Turn on **Settings → Terminal & status → Battery → Lazy mode**, then leave the launcher on screen
and check again.

By default the clock folds its seconds, which means the launcher redraws continuously for as long
as it is on screen, whether or not anything else is happening. Lazy mode swaps the digits instead,
holds the working-window rim lit rather than breathing it, samples the status readings less often,
and rests the weather icon on its last frame — so the screen only repaints when something changes.
It is expected to become the default; trying it now and reporting anything that looks stale is what
gets it there.

If idle cost stays high with Lazy mode on, something else is drawing. A shell producing output
keeps the terminal repainting, and so does a long-running agent or job holding a window in its
working state. Check what the visible session is running before treating it as a launcher problem.

## Blur, wallpaper colors, or transparency look wrong

Open **Settings → Appearance → Surface editor** and test against the real home screen.

- Live wallpapers can limit or disable dock blur.
- GPU blur requires Android 12 or later; older Android versions use a simpler surface.
- **Use wallpaper colors** changes the generated palette, while the opacity controls determine how
  much wallpaper remains visible.
- Full-screen TUIs may need a resize after font or surface geometry changes.

## A font change is ignored

The terminal resolves font configuration in this order:

1. active directives in your `~/.termux/fonts.conf`;
2. `~/.termux/fonts.d/*.conf`, including the picker-managed `10-launcher.conf`; and
3. `~/.termux/font.ttf`, Termux:Styling, or Android monospace.

A higher item can override the picker. Open **Settings → Appearance → Terminal fonts** to see the
managed state. **Use font.ttf / Termux:Styling** removes only `10-launcher.conf`.

After manual changes, run:

```sh
termux-reload-settings
```

The terminal reports configuration errors and uses safe fallbacks instead of refusing to start.

## Shizuku features do not work

Shizuku is optional. Start its service, then open **Settings → Services & permissions → Shizuku** and
grant the requested connection. If you use `rish`, its `RISH_APPLICATION_ID` must match the installed
edition: `com.termux` for standard or `io.vaj.tl` for VAJ.

Loss of Shizuku must not stop ordinary terminal, dock, pane, workspace, or app-launch behavior.

## TAI does not start or a client cannot connect

Open **Settings → Services & permissions → TAI · Termux AI** and check the service status, selected
model, port, and authentication setting. Then run:

```sh
tai status
tai models
tai runtime
tai doctor
```

Localhost and LAN security rules differ; LAN bind mode always requires the token. Continue with the
[TAI user guide](Termux_AI.md) or [LauncherCtl API reference](LauncherCtl_API.md).

## fastfetch does not start after setup-launcher

If `fastfetch` exits immediately with a message about `libandroid-glob.so` not being found, the
installed binary was built for another edition. Each edition installs under its own path, and
`fastfetch` finds its libraries through a path fixed when it was built.

Re-run `setup-launcher` in the edition you are using; it installs the build for that edition, or
tells you that none is published for it yet. Delete `~/.local/bin/fastfetch` first if you want a
clean check — the Termux `fastfetch` package still works, it just sends only the first frame of an
animated logo.

## The Linux display will not start, or apps cannot reach it

The Display place hides its Start button until the keyboard layouts are installed:
`pkg install x11-repo xkeyboard-config` (on pacman, `pacman -S xkeyboard-config`). A server that
runs but no program can reach usually has its socket in the wrong place — a shell with `TMPDIR`
set away from `$PREFIX/tmp`, or a socket left behind by an earlier server. If Linux apps draw in
software, run `termux-x11-gpu-setup` in a shell: it tries every graphics profile that fits the
phone and keeps the best. [The Linux display](X11_Display.md#when-something-is-off) has each case
in detail.

## Collect useful diagnostics

Open **Settings → Advanced & diagnostics**:

1. reproduce the issue;
2. choose **Copy diagnostics** for a short environment summary or **Export logs** for a report;
3. review the output for private paths, commands, or other sensitive information; and
4. attach only the relevant material to a GitHub issue.

Include the Termux Launcher version, edition, Android version, device architecture, and exact steps.
The version is under **Settings → About & support → Version and build**.
