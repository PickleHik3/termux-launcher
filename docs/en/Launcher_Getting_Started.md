# Getting Started

Main setup guide for Termux Launcher. Reference pages:

- [Illustrated web guide](https://picklehik3.github.io/termux-launcher-site/#wiki) for the current live screenshots and recordings.
- [LauncherCtl API](LauncherCtl_API.md) for app launch, the local OpenAI/Ollama-compatible AI endpoint, and model management.
- [Termux AI](Termux_AI.md) for the local on-device AI endpoint.
- [Developer docs](Developer_Docs.md) for advanced API, runtime, helper-script, and security details.

## 1. Install Termux Launcher

1. Download the latest APK from [Releases](https://github.com/PickleHik3/termux-launcher/releases).
2. Pick the package edition:
   - **Standard (`com.termux`)** replaces a regular Termux installation and works with matching `com.termux` add-ons.
   - **VAJ (`io.vaj.tl`)** installs beside regular Termux and needs add-ons built for the same package/signing family.
3. Open the app once and let the Termux bootstrap finish.
4. Set Termux Launcher as your Home app, from Android settings or from inside the launcher:

```text
Long press Terminal -> More -> Apps Bar -> Home launcher shortcut
```

If terminal drawing or input becomes slow after an update, run:

```sh
termux-reload-settings
```

## 2. Install Helpful Apps

Recommended:

- [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard), especially for terminal and tmux use. It is also available on [Play Store](https://play.google.com/store/apps/details?id=juloo.keyboard2).
- [Shizuku](https://github.com/rikkaapps/shizuku), only if you want optional lock-screen, Shizuku shell, or `btop` helper features.

If you use Termux add-ons, use the matching companion forks:

- [Termux:API](https://github.com/PickleHik3/termux-api/releases)
- [Termux:Styling](https://github.com/PickleHik3/termux-styling/releases)

Mixing differently signed Termux add-ons can cause shared UID or signing errors.

## 3. Learn the Launcher Surface

The terminal is the home screen. Launcher controls sit around the Termux session.

- **Apps row:** Long press an app icon to pin, move, or place it in a folder. Long press empty space in the apps row for list-based management.
- **A-Z row:** Swipe across the row to filter installed apps by letter. Swipe upward from a letter to launch an app from that group.
- **Terminal search:** Type the input split character before a query to search apps from terminal input. The default is `%`, so `%whatsapp` searches for WhatsApp.
- **Lock screen:** Double tapping the alphabet row can lock the phone if you configure a lock method in Apps Bar settings.

Most settings are under:

```text
Long press Terminal -> More
```

Useful places:

- **Appearance:** Terminal opacity, blur, dock size, compact dock spacing, monochrome icons, and Terminal Material colors. The surface editor returns to the Dock, Keyboard, Status, or Other section used last.
- **Apps Bar:** Input split character, app ranking reset, Home launcher shortcut, and lock-screen behavior.
- **TAI / Termux AI:** Local model downloads, imports, runtime settings, API port, and API token.

The terminal also has a native command palette, recursive split panes, windows, a session browser,
and durable layout/CWD workspaces. Long-press the terminal and choose **Command palette**, or press
`Ctrl+Alt+Shift+P` on a hardware keyboard. Split panes are enabled by default; traditional
single-pane behavior is available at **Settings → Termux → Terminal IO → Split panes**.

Press `Ctrl+Alt+F`, or choose **Float / dock pane** in the command palette, to detach the focused
pane above the tiled layout. Drag its top handle to move it and use the bottom-right grip to resize
it. Toggle the action again to dock it. A window must keep at least one tiled pane.

Fresh installs use the built-in terminal keyboard and keep the Android keyboard hidden. Existing
installs keep their current setting. Choose **Settings → Keyboard → Input method** to use the
built-in keyboard, the Android keyboard, or no on-screen keyboard.

See the [Modern terminal guide](Terminal_Modernization.md) for shortcuts, automatic layouts,
workspace restore, custom bindings, advanced fonts, shell prompt navigation, Kitty protocols, and
diagnostics.

Live wallpapers can disable dock blur. If you use two rows of Extra Keys, turn on compact dock spacing so the terminal has more room.

### Change the terminal font

The simple way is unchanged from Termux: drop a font file at `~/.termux/font.ttf`, add
`~/.termux/font-italic.ttf` if you want a real italic face, then run:

```sh
termux-reload-settings
```

[Termux:Styling](https://github.com/PickleHik3/termux-styling/releases) does the same thing with a
picker instead of a file copy. Either route is all most setups need.

For separate bold and italic files, Nerd Font icons on selected Unicode ranges, ligature control,
OpenType features, or variable-font axes, write `~/.termux/fonts.conf` instead:

```sh
# Installed on first run with every directive commented out. Uncomment what you want:
nano ~/.termux/fonts.conf

# A pristine copy to compare against or restore from:
ls ~/.termux/launcher/examples/fonts.conf
```

While `fonts.conf` has no active directives, `font.ttf` and Termux:Styling keep working exactly as
before — the file only takes over the faces you actually set. Delete or rename it to go back.
The [Modern terminal guide](Terminal_Modernization.md) documents every directive.

## 4. Use the Local AI Endpoint From the Shell

The `tai` command is installed by the app when the launcher session starts. It talks to the local OpenAI/Ollama-compatible AI endpoint and manages on-device models.

Try:

```sh
tai status
tai models
tai runtime
```

Useful commands:

```sh
tai preflight MODEL_ID
tai load MODEL_ID
tai unload
tai keep-warm MODEL_ID --minutes 30
tai cancel
tai doctor
```

For endpoint files, authentication, route tables, and scripting examples, see [LauncherCtl API](LauncherCtl_API.md).

## 5. Optional Guarded Shell Setup

The repository contains secret-free templates derived from the live development setup: fish with a commented quick-start guide, two Oh My Posh themes driven by wallpaper Material colors, the `~/.termux` terminal configs, and the Maple Mono font family the terminal's ligature support was tuned against. The shell setup includes:

- fish
- oh-my-posh
- eza
- zoxide
- fzf, yazi, neovim

Terminal Material colors are enabled by default. Leave the toggle on if you want the prompt to follow your wallpaper:

```text
Long press Terminal -> More -> Appearance -> Terminal Material colors
```

Use the guarded installer instead of replacing your dotfiles with direct `curl -o` commands:

```sh
curl -fsSLo ~/setup-launcher \
  "https://raw.githubusercontent.com/PickleHik3/termux-launcher/main/docs/en/examples/setup-launcher"
sed -n '1,260p' ~/setup-launcher
chmod 700 ~/setup-launcher
~/setup-launcher
```

The installer asks what to install:

- **Everything:** packages, fish + Oh My Posh configs, `~/.termux` terminal configs, and Maple Mono.
- **Packages + shell configs:** fish, prompt themes, and the CLI tools the config uses.
- **Terminal configs:** `termux.properties` (extra-key row wired to launcher tools) and the optional custom in-app keyboard layout.
- **Maple Mono fonts:** downloads the variable text faces and the static Nerd Font build from the [maple-font releases](https://github.com/subframe7536/maple-font/releases), places them under `~/.termux/fonts/`, and installs the documented `fonts.conf` that turns on programming ligatures (`->`, `=>`, `!=`) and Nerd Font icons with zero further steps.

Every config file the installer replaces gets a timestamped `.bak` next to it first. Private aliases, hostnames, tokens, and API keys from the development phone are intentionally not included; the fish template documents where to keep such things (`~/.config/fish/secrets.fish`).

If you prefer manual setup, inspect the files in [`docs/en/examples`](examples/) and merge the parts you want into your own configuration after making backups.

### Deprecated: tmux and btop setup

Earlier releases shipped `setup-tmux-btop`, which installed tmux with a Material theme and a Shizuku-backed `btop` helper. The launcher's own windows, panes, workspaces, scratchpad, and status bar have replaced that role, so the script is deprecated. It remains in [`docs/en/examples`](examples/) for existing users, but its fish template is frozen at the tmux-era version and no longer receives updates. tmux itself remains useful when you need Unix processes to survive independently of the Android terminal UI — the native workspace feature restores sessions, windows, panes, titles, and CWDs after process death, but it does not resurrect foreground programs.

## 6. Optional Shizuku and rish Setup

You do not need Shizuku for normal launcher use. Set up Shizuku only if you want Shizuku-backed lock-screen behavior, a Shizuku shell, or the optional `btop-shizuku` and `mini-btop-shizuku` commands.

For `btop`, set up `rish` before choosing the `btop` option in the tmux setup script:

1. Install and start [Shizuku](https://github.com/rikkaapps/shizuku). The [official Shizuku setup guide](https://shizuku.rikka.app/guide/setup/) has the Android-side steps.
2. In the Shizuku app, open **Use Shizuku in terminal apps**.
3. Let Shizuku create `rish` and `rish_shizuku.dex`.
4. Copy both files into a Termux directory that is in your `$PATH`.

For example:

```sh
mkdir -p ~/.local/bin
```

If `~/.local/bin` is not already in your path, add this to your shell startup file:

```sh
export PATH="$HOME/.local/bin:$PATH"
```

Check the current launcher's Android package name:

```sh
printf '%s\n' "$TERMUX_APP__PACKAGE_NAME"
```

Then edit the bottom of `rish` and set `RISH_APPLICATION_ID` to that value. For
the standard edition it is:

```sh
RISH_APPLICATION_ID="com.termux"
```

For the VAJ edition it is:

```sh
RISH_APPLICATION_ID="io.vaj.tl"
```

Make `rish` executable and run it once:

```sh
chmod +x "$(command -v rish)"
rish
```

Check the setup:

```sh
rish -c "id"
```

Now you can run `~/setup-tmux-btop` again and choose **All** or **btop only**.

## 7. Optional Extra Keys

Termux Launcher includes the regular Termux Extra Keys support and adds a convenient paste popup. Extra Keys are configured in:

```sh
~/.termux/termux.properties
```

After changing that file, reload settings:

```sh
termux-reload-settings
```

### Compact tmux Row

This one-row layout is the easiest default for tmux. It assumes the tmux setup above is installed and uses `CTRL b` as the tmux prefix.

```properties
extra-keys = [[ \
  {macro: "CTRL b F12", display: "♼"}, \
  {macro: "CTRL b h", display: "𝍣", popup: {macro: "CTRL b v", display: "𝍬"}}, \
  {macro: "CTRL b 1", display: "⓵"}, \
  {macro: "CTRL b 2", display: "⓶"}, \
  {macro: "CTRL b 3", display: "⓷"}, \
  {macro: "CTRL b [", display: "✎"}, \
  {key: KEYBOARD, popup: PASTE}, \
  {macro: "CTRL b", display: "㋡"} \
]]
```

### Two-Row tmux Layout

Use this if you want dedicated modifier keys and more tmux controls. Turn on compact dock spacing first.

```properties
extra-keys = [[ \
  {macro: "CTRL b h", display: "𝍣"}, \
  {macro: "CTRL b v", display: "𝍬"}, \
  {macro: "ALT LEFT", display: "⬸"}, \
  {macro: "CTRL b c", display: "+"}, \
  {macro: "ALT RIGHT", display: "⤑"}, \
  {macro: "CTRL b [", display: "✏"}, \
  {macro: "CTRL b z", display: "□"}, \
  {macro: "CTRL b x", display: "×", popup: {macro: "CTRL b k", display: "⊠"}} \
], [ \
  {key: ESC, display: "Esc", popup: {macro: "CTRL b F12", display: "⟲"}}, \
  {key: TAB, display: "TAB"}, \
  {key: SHIFT, display: "SHFT"}, \
  {key: CTRL, display: "CTRL"}, \
  {key: ALT, display: "ALT"}, \
  {key: LEFT, popup: DOWN}, \
  {key: RIGHT, popup: UP}, \
  {key: KEYBOARD, popup: PASTE} \
]]
```

## 8. Optional Termux AI

Termux AI, also called TAI, is a local model host built into Termux Launcher. It exposes an OpenAI-compatible localhost endpoint for tools such as `aichat`.

Start here:

```text
Long press Terminal -> More -> TAI / Termux AI
```

Then download or import a model and check the shell helper:

```sh
tai status
tai models
tai runtime
```

For model setup, OpenAI-compatible client configuration, and troubleshooting, see [Termux AI](Termux_AI.md).

## 9. Quick Troubleshooting

If terminal drawing, input, or colors feel stale:

```sh
termux-reload-settings
```

If Shizuku features do not work, confirm Shizuku is running and grant permission to Termux Launcher. Verify `rish` with:

```sh
rish -c "id"
```
