# Troubleshooting

Start with the symptom below. Avoid uninstalling the app as a generic fix: Android deletes the
private Termux home directory when the app is uninstalled.

## An APK will not install or update

Check all three identities:

1. **Edition:** the Termux edition is `com.termux`, the Nix edition `com.termux.launcher.nix`, the
   VAJ edition `io.vaj.tl`.
2. **Signing family:** Termux, its launcher build, and add-ons must use compatible signatures.
3. **Architecture:** most current phones use `arm64-v8a`; use `universal` when unsure and available.

Android messages such as "App not installed" or an incompatible-update error commonly mean the
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

If the terminal cannot start at all, long-press the app icon and use the **Failsafe** shortcut. A
failsafe shell avoids normal startup files so you can repair `.bashrc`, `.zshrc`, Fish config, or
other shell initialisation.

## Shared-storage files are missing

Run:

```sh
termux-setup-storage
```

Approve Android's prompt, then check `~/storage`. Also open **Settings → Permissions & services →
Files and media** and resolve its status if needed. Android shared storage is separate from Termux's
private home directory.

## The keyboard is missing or the wrong keyboard opens

Open **Settings → Keyboard → On-screen keyboard** and choose **Built-in**, **Android** or **Off**.

Also check:

- **Settings → Keyboard → Hardware keyboard → Hide on-screen keyboard**, which hides it while a
  hardware keyboard is connected;
- whether the keyboard was switched off with the **Keyboard on/off** action, so a tap no longer
  raises it;
- **Settings → Terminal → Compatibility → Keyboard resize workaround** for Android-keyboard overlap;
  and
- the independently remembered portrait and landscape keyboard heights.

If a custom layout fails, temporarily move `~/.termux/keyboard/layout.xml`, reload settings, and test
the bundled layout:

```sh
mv ~/.termux/keyboard/layout.xml ~/.termux/keyboard/layout.xml.disabled
termux-reload-settings
```

More in [Keyboard](Keyboard.md).

## Pane or window actions are unavailable

Open **Settings → Terminal → Split-pane controls**. Native pane and window actions are
disabled in single-pane compatibility behaviour.

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

Compare it with the example under `~/.termux/launcher/examples/`. Reload after editing:

```sh
termux-reload-settings
```

## A workspace did not resume a program

A workspace is a terminal layout definition, not a process checkpoint. By default it restores
sessions, windows, panes, titles, and working directories, then starts normal login shells.

To offer command restart:

1. turn on **Also save what is running** while saving; and
2. approve running the recorded commands while loading.

Approved commands start again from the beginning. They do not continue from the previous instruction
or application state. Review hand-edited workspace JSON before running recorded commands.

Use **Append** when you want to keep the current hierarchy. Use **Replace** only after saving anything
important. Deleting a saved workspace removes its JSON definition but does not close running shells.

## App search does not start

Search is designed for an idle shell prompt. Confirm the current prefix under **Settings → Apps →
App search → App search prefix**; `%` is the default. If the prefix conflicts with shell input,
choose a different single character.

For browsing without terminal input, use the A-Z row, or turn it on in the **Layout** tab of
**Appearance** on the place you want it for.

## CPU, memory, weather, media, or notifications are missing

Open **Settings → Status bar** and turn on the card you want. Then check **Settings → Permissions &
services**:

- weather needs either a city picked under **Settings → Status bar → Weather → Location**, or
  location access;
- pinned notifications need **Notification access** and a matching rule under **Settings →
  Notifications → Pinned notifications**;
- media information depends on an active Android media session; and
- process details are more complete with a privileged backend ([Shizuku](Shizuku.md)).

Weather data is provided by Open-Meteo. See [Status bar](Status_Bar.md) and
[Notifications](Notifications.md).

## The launcher uses CPU or battery while it sits idle

Turn on **Settings → App behavior → Reduce idle activity** ("Lazy mode"), then leave the launcher on
screen and check again.

By default the clock folds its seconds, which means the launcher redraws continuously for as long
as it is on screen. Lazy mode swaps the digits instead, holds the working-window rim lit rather than
breathing it, samples the status readings less often, and rests the weather icon on its last frame,
so the screen only repaints when something changes.

If idle cost stays high with Lazy mode on, something else is drawing. A shell producing output
keeps the terminal repainting, and so does a long-running agent or job holding a window in its
working state. Check what the visible session is running before treating it as a launcher problem.

## Blur, wallpaper colors, or transparency look wrong

Open **Settings → Appearance** and test against the real home screen.

- Live wallpapers can limit or disable dock blur.
- GPU blur requires Android 12 or later; older Android versions use a simpler surface.
- Without **Wallpaper access** (Settings → Permissions & services) the glass bars render flat.
- **Wallpaper colors**, in **Settings → Appearance**, changes the generated palette, while the
  opacity controls decide how much wallpaper remains visible.
- Full-screen TUIs may need a resize after font or surface geometry changes.

See [Look and themes](Look_And_Themes.md).

## A font change is ignored

The terminal resolves font configuration in this order:

1. active directives in your `~/.termux/fonts.conf`;
2. `~/.termux/fonts.d/*.conf`, including the picker-managed `10-launcher.conf`; and
3. `~/.termux/font.ttf`, Termux:Styling, or Android monospace.

A higher item can override the picker. Open **Terminal fonts** (**Settings → Appearance → Terminal
fonts**, or run **Terminal fonts** from the command palette) to see the managed state.
**Use font.ttf / Termux:Styling** removes only `10-launcher.conf`.

After manual changes, run:

```sh
termux-reload-settings
```

The terminal reports configuration errors and uses safe fallbacks instead of refusing to start.

## Shizuku features do not work

Shizuku is optional. Start its service, then open **Settings → Permissions & services → Shizuku**
and grant the requested connection. If you use `rish`, its `RISH_APPLICATION_ID` must match the
installed edition: `com.termux`, `com.termux.launcher.nix` or `io.vaj.tl`.

Loss of Shizuku must not stop ordinary terminal, dock, pane, workspace, or app-launch behaviour.
More in [Permissions and Shizuku](Shizuku.md#troubleshooting).

## On-device AI does not start or a client cannot connect

Open **Settings → On-device AI** and check **Runtime status**, the **Server** group and the model a
function uses. Then run:

```sh
tai doctor
tai status
tai runtime
tai models
```

- **The CLI cannot connect, or `Connection refused`.** Open Termux Launcher once after installing
  so it writes `~/.launcherctl/endpoint` and `~/.launcherctl/token`, check the port in the endpoint
  file, and run `tai status`.
- **401 Unauthorized.** Refresh your client with the current value from `~/.launcherctl/token`, or
  turn off **Require API token** for localhost use. LAN access always requires the token. If the
  token leaked, tap **Recreate token** in **OpenAI endpoint**.
- **The model is not found.** Finish downloading or importing it, then check `tai models`; the
  request must use the exact id.
- **`model_not_loaded`.** Turn on **Model autoload**, or run `tai load MODEL_ID`.
- **The model does not load, or the GPU load crashes.** Check `tai runtime` and `tai logs`, then
  try `tai load MODEL_ID --cpu`. In a function's picker, **Answers look wrong? Use the CPU** does the
  same for that function.
- **Not enough memory.** Close other apps, choose the CPU, or use a smaller model.
- **An MNN model replies with repeated characters.** Run `tai load MODEL_ID --fresh`.
- **Tools are ignored.** Check that the model lists `tool_use` in `tai models`.
- **Image or audio rejected.** Use the model's `-vision` or `-audio` id from `/v1/models`.
- **A gated download fails with "needs a token".** Accept the model's terms on huggingface.co, then
  tap **Add token**.

To report a problem, use **Settings → On-device AI → Share diagnostics log**: loads, evictions,
failures, the runtime history and the last crash, with no prompts or replies. More in
[On-device AI](On_Device_AI.md) and [LauncherCtl API](LauncherCtl_API.md).

## Voice input does not hear or type

- **The voice key opens Google's (or another) recognizer.** **Speech engine** is set to **Android
  system**, either because you chose it or because no speech model is installed yet. Install one,
  or choose **On-device** in **Settings → Keyboard → Voice input**.
- **"No speech model installed".** Install one from **Settings → On-device AI → Model centre → Get
  models → Speech**.
- **Microphone permission.** The first on-device dictation asks for the microphone. Refusing uses
  the Android recognizer instead. To allow it later, open Android's app settings for Termux
  Launcher → **Permissions** → **Microphone**.
- **Nothing heard.** Start speaking just after the sound, hold the phone nearer, and avoid single
  very short words. If you speak softly, set **Mic sensitivity** to **High**. In a noisy room try a
  **Small** Whisper model.
- **Phrases appear that you did not say.** On **Mic sensitivity: High**, a TV or people nearby can
  be heard. Set it back to **Normal**.
- **Media playing.** Voice input does not pause music or videos, and the microphone hears them, so
  **Stop after silence** may never fire. Pause the media while you dictate.
- **The dictation stops when the keyboard hides.** A dictation from the voice key belongs to the
  keyboard. Use the **Dictate** key to dictate with the keyboard down.
- **Memory.** A speech model and a cleanup model can be loaded at once. If the runtime runs short,
  the dictation stops with **Voice input stopped: …**. Close other apps, use a **Base** Whisper
  model, or turn **Clean up dictation** off. **Unload after idle** frees the speech model between
  dictations.
- **The text is "Kept as heard" every time.** Check that **Clean up dictation** is on and that the
  **Cleanup model** picker has a model (on a phone with 6 GB of RAM or less, Automatic means **Raw
  text**). Dictations under four words are always kept as heard, and commands follow fixed rules.

More in [Voice input](Voice_Input.md).

## Text to speech is silent or refuses

- **No Read aloud in the selection toolbar.** Install the voice model from **Model centre → Get
  models → Voice output**.
- **`tts_model_not_installed`.** The same: no voice model is installed.
- **`tts_audio_busy`.** A call or another app holds the phone's audio. Try again in a moment.
- **`tts_voice_not_found`.** Use one of the four voices or an OpenAI voice name.
- **`tts_input_too_long`.** `/v1/audio/speech` takes up to 4,096 characters; split the text, or use
  `tai speak`, which takes up to 20,000.
- **`tts_files_missing` or `tts_load_failed`.** The download is incomplete or damaged. Delete the
  voice model in the Model centre and download it again.
- **Nothing is heard.** Check the media volume; the voice follows it, not the ringer volume.
- **Non-English text sounds wrong.** The voice model reads English only.

More in [Text to speech](Text_To_Speech.md).

## fastfetch does not start after tlstore install

If `fastfetch` exits immediately with a message about `libandroid-glob.so` not being found, the
installed binary was built for another edition. Each edition installs under its own path, and
`fastfetch` finds its libraries through a path fixed when it was built.

Run `tlstore install fastfetch` in the edition you are using; it installs the build for that
edition, or tells you that none is published for it yet. Delete `~/.local/bin/fastfetch` first if
you want a clean check. The Termux `fastfetch` package still works; it just sends only the first
frame of an animated logo.

## The Linux display will not start, or apps cannot reach it

The Display place exists only while **Settings → Display → Display** is on, and it hides its
**Start display** button until the keyboard layouts are installed:
`pkg install x11-repo xkeyboard-config` (on pacman, `pacman -S xkeyboard-config`).

- **"No display is running" although you started one.** The server exits before it opens a port
  when the keyboard layouts are missing; install `xkeyboard-config`. Run `termux-x11 :0` in the
  foreground to read its message.
- **Apps say they cannot open the display, though the server is running.** X programs look for the
  display's socket in `$PREFIX/tmp`. If your shell sets `TMPDIR` somewhere else, a server started
  from that shell puts its socket there and no program finds it; start the server with
  `TMPDIR=$PREFIX/tmp termux-x11 :0`, or leave `TMPDIR` alone. A socket left behind by a server that
  has exited causes the same symptom; remove it from `$PREFIX/tmp/.X11-unix/`.
- **A black display.** Turn on **Settings → Display → Troubleshooting → Compatibility drawing**
  (the server's `-legacy-drawing`).
- **Apps cannot open the display from a proot.** Log in with `--shared-x11`, and check
  `echo $DISPLAY` inside.
- **Nothing is accelerated.** `glmark2-es2` names the renderer in its first lines; `llvmpipe`
  means a profile variable is missing or the profile does not fit this GPU. Run
  `termux-x11-gpu-setup` (or `tlstore display`): it tries every graphics profile that fits the phone
  and keeps the best. `launcherctl x11 gpu` says which profile fits.
- **`termux-x11-gpu-setup` says even software rendering did not finish.** The test could not talk
  to a display at all; the first two causes above are the usual ones, and the full log it names
  shows the test program's own message.
- **A profile's package is "not available here".** Not every package source carries every driver
  (pacman's repositories have no `vulkan-wrapper-android`, for instance). The script skips that
  profile and tries the rest.

More in [The Linux display](X11_Display.md).

## Linux apps from a distro do not show or do not open

- **No Linux apps in the drawer at all.** Either nothing is installed yet (use **Settings → Display
  → Linux apps → Set up Linux apps**), or **Show Linux apps in drawer** is off. Check too that the
  distro was installed by `proot-distro` 5.x.
- **One app is missing.** Look in **Hidden Linux apps** first. Otherwise the app has no menu entry
  of its own: command-line programs usually have none. Write the entry yourself; see
  [Writing an entry yourself](Linux_Apps_From_A_Distro.md#writing-an-entry-yourself).
- **An app opens and closes again, or nothing happens.** Most often a fresh distro missing fonts;
  **Set up Linux apps** installs them. To see the app's own complaint, run it by hand:
  `proot-distro login debian --shared-x11 -e DISPLAY=:0 -- <command>`.
- **`Failed to connect to the bus`, over and over.** Harmless; apps that print it still open. One
  that really needs a session bus wants `dbus-run-session -- <command>` inside the distro.
- **The display never comes up.** That is the display itself, not the app; see the section above.
- **The tile is blank.** The app's icon is in a format the launcher cannot read. PNG and SVG icons
  are used; anything else gets the default tile.

## Collect useful diagnostics

Open **Settings → Diagnostics**:

1. reproduce the issue;
2. choose **Copy diagnostics** for a short environment summary or **Export logs** for a report;
3. review the output for private paths, commands, or other sensitive information; and
4. attach only the relevant material to a GitHub issue.

For On-device AI problems, **Settings → On-device AI → Share diagnostics log** adds the runtime's
own log. Include the Termux Launcher version, edition, Android version, device architecture, and
exact steps. The version is under **Settings → About & help → Version and build**.
