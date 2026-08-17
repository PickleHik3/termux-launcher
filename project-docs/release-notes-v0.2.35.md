# v0.2.35

The user-facing notes for the GitHub release body. `CHANGELOG.md` stays the full technical
record — everything here is a plain-language summary of what changes on the phone, with the
reasoning, file paths and internals left out.

## What's new in 0.2.35

The biggest release since the launcher surfaces landed: a real app drawer, home-screen widgets,
a keyboard that no longer summons the system one, and a landscape layout you can use.

### Home screen

- **A full-screen app drawer.** Swipe down on the pinned apps row for every installed app, with
  a search field and A–Z index. Three view types: vertical, horizontal pages, and categories.
- **Categories you can argue with.** Apps are grouped automatically; long-press to move one, and
  your choice always wins. Re-sort the whole drawer with an on-device model, or copy a prompt into
  any AI chat you already use and paste its answer back. A robot glyph in the status bar shows when
  a model is loaded.
- **Folders anywhere.** Drag an app onto another to make a folder, in the dock or the drawer, and
  drop folders where you want them.
- **App widgets.** A pane that hosts real home-screen widgets, on pages, with an edit mode for
  moving and resizing and a long-press menu per widget.
- **Landscape works.** The dock becomes a vertical rail on the side you choose, the drawer gets
  denser, and nothing hides under a cutout or the navigation bar any more.
- **One switch for how you use it.** Settings › Launcher & apps chooses launcher or terminal-only,
  in one place, instead of a row of related toggles.

### Terminal

- **No more system keyboard for prompts.** The session browser, renames, pickers and the rest open
  as in-app sheets, so the keyboard you configured is the keyboard you type on.
- **Scrollback search** on the dock, with vim-style copy mode over the transcript.
- **Rename sessions, windows and panes** from a chip next to the thing being renamed.
- **A visual extra-keys editor.** Tap a key to change it, hold and drag to move it, with pages,
  macros, swipe-up actions and a glyph picker.
- **The whole interface can follow your terminal colours.** Appearance › Interface colors.
- **The key row and keyboard layout now ship in the app.** Nothing to download, and your own
  `extra-keys` or `layout.xml` still wins.
- **A pull-down status pane** over the running terminal, and the status bar now shows a grabber so
  it is discoverable.
- **Nerd Font icons are the right size.** Icons that were drawn at about half the height of the
  text beside them now use the blank space after them, matching kitty. `narrow_symbols` in
  `~/.termux/fonts.conf` pins ranges back to a single cell if you prefer that.
- **Kitty graphics in tmux**, through Unicode placeholders — the path Neovim image plugins use.
- **A configurable terminal name.** `terminal-term = xterm-kitty` in `~/.termux/termux.properties`
  for programs that check the name instead of asking the terminal what it supports.

### Setup and examples

- **`setup-launcher` asks a better question:** everything, just the shell essentials, or one item
  at a time. It installs fish, Oh My Posh, zoxide, eza, Neovim with AstroNvim, and optionally the
  showcase binaries — sigye, the animated-logo fastfetch and kitten — verified against pinned
  checksums, and it no longer writes into `~/.termux`.
- **`setup-nvim`** picks a Neovim distro (AstroNvim or NvChad) themed from your wallpaper palette.
- **A commented `termux.properties`** is seeded on install so the settings are discoverable
  without a download. Existing files are never touched.
- **Named key bindings.** `map --label "Display name" …`, so the legend reads usefully.

### Fixes

- The volume keys stay volume keys by default.
- Ghost swipes on the pinned apps row, where a swipe slid and then landed back where it started.
- Dropping an app onto a full folder, or one that already held it, could delete the app.
- The pinned-apps editor could overwrite newer folder and pin changes.
- One finger could page and drag at the same time.
- The pull-down status pane could not be opened at all.
- Horizontal drawer pages under-filled the screen, and the first swipe after changing view type
  was ignored.
- The dock and drawer blanked while apps were installed or removed.
- In-app keyboard presses could land on the app icon underneath.
- Rotation resized the terminal twice and left a stale blur crop.
- A hand-written `~/.termux/colors.properties` no longer loses to dynamic colours in silence (#11).
- Reopening Settings from a retained task could crash.
- The clock no longer takes taps in the empty space beside it, and the compact flip clock matches
  the full one.
- The animated fastfetch logo is no longer stretched, and the published fastfetch build reads
  `~/.config/fastfetch` and installs its dependencies with it.

## Upgrading

- The extra-keys row and keyboard layout now come from the app. If you customised either, your
  file still wins and nothing is rewritten.
- Drawer icon size and grid counts are no longer settings — each view sizes itself.
- The terminal long-press menu is the pre-sheet dialog again; the sheet plane stays everywhere else.
- The widget grid default is 5×4 rather than 6×4.
- If you use the showcase binaries, re-run `setup-launcher` to pick up the fixed fastfetch.
