# Changelog

## 0.2.35-a

Fix release.

### Fixed

- **The Style row was missing on rebranded editions (#13).** `TERMUX_STYLING_ACTIVITY_NAME` was
  derived from the styling plugin's package name, but rebranding changes the applicationId and not
  the Java packages, so the Nix edition looked for `com.termux.launcher.nix.styling.TermuxStyleActivity`
  while TLNix:Styling's only activity is `com.termux.styling.TermuxStyleActivity` — and the
  launch-intent fallback returned null because the plugin ships no launcher activity at all. The
  class names are literals now, pinned by a test.
- **Tinted square corners behind the status cards (#13).** The card container carried 10dp of
  elevation inside a popup window sized exactly to the card, so the cast shadow could only render
  inside the four corner notches beyond the rounded arc, clipped square at the window edge. The
  elevation is gone; the 1dp outline stroke carries the edge.
- **The Lazy Mode toggle did not persist.** The switch wrote `lazy_mode` into the terminal-status
  data store's default branch, which drops the value — the setting shipped with no setter and no
  store case. The window bar also only read the flag at setup; the settings-return refresh
  re-applies it now, like the clock already did. Round-trip pinned by a test.

## 0.2.35

The largest release since the launcher surfaces landed: a full app drawer with three view
types, an app-widget pane, and a pass over every terminal prompt that used to summon the
system keyboard. Landscape is usable for the first time.

### Added

- **Narrow symbols.** `narrow_symbols U+E0A0-U+E0A3,U+E0C0-U+E0C7 1` in `~/.termux/fonts.conf` caps
  how many cells a private-use symbol may be drawn across, kitty's directive with kitty's syntax.
  The count defaults to one, the maximum is five, and for a code point matched by several lines the
  last one wins.
- **Kitty graphics Unicode placeholders.** `U=1` virtual placements and U+10EEEE
  row/column/image-id decoding now draw stored images through ordinary text cells, enabling the
  tmux path used by Neovim image plugins while retaining animation-frame updates.
- **A configurable terminal name.** Set `terminal-term = xterm-kitty` (or another terminfo name) in
  `~/.termux/termux.properties` for new sessions, then run `termux-reload-settings` and open a new
  session. Unconfigured installs remain `TERM=xterm-256color`, and an explicit per-session
  environment value still wins. Capability detection continues to work through XTVERSION,
  `TERM_PROGRAM` and XTSMGRAPHICS, so this is only for programs that string-match the name.
- **The key row and keyboard layout ship in the app.** The launcher's extra-keys row is the built-in
  default, now including session switching as swipe-up popups on the window keys, and the in-app
  keyboard layout is bundled as before. Neither needs a file, a download, or a setup script. An
  `extra-keys` value in `~/.termux/termux.properties` — which is what the in-app editor writes — and
  a `~/.termux/keyboard/layout.xml` both still win outright, and nothing in an app update rewrites
  either, so an upgrade cannot cost you a customised row or layout. The second key page now ships
  empty, since the in-app keyboard's Fn layer already reaches F1-F12; add keys to `extra-keys2` to
  bring it back.
- **A seeded `~/.termux/termux.properties`.** The app now ships a commented properties file
  alongside the bindings and fonts examples, documenting `terminal-term` and the rest of the
  launcher-relevant properties in place, so no download is needed to discover them. It is written
  only when absent, every line is a comment so seeding changes no behaviour, and it is skipped
  entirely when `~/.config/termux/termux.properties` exists — seeding the primary path would
  otherwise silently disable a file kept at the secondary one.
- **Host cross-build recipes**, in `recipes/cross`. The on-device recipes now have a counterpart
  that builds the same pinned sources for `aarch64` Termux from a Linux host, using the Android NDK
  and a sysroot assembled from published Termux `.deb` packages — no Docker image and no
  `termux-packages` checkout. They add `kitten`, kitty's client binary, which cannot practically be
  built on a phone: kitty's generated Go sources come from a generator that needs a built kitty.
  `kitten icat` therefore works here, including through Unicode placeholders inside tmux.

- **A full-screen app drawer.** Swipe down on the pinned apps row for an alphabetical grid of
  everything installed, with a search field and an A-Z rope down the side that scrubs to a
  section and dims the letters you are not on. The drawer is an overlay sibling of the root
  layout rather than part of the accessory stack, so opening it never resizes the terminal or
  sends a SIGWINCH, and search types through the same focusless intake the command palette
  uses — no EditText, so the terminal keeps its InputConnection and no system IME appears.
- **Three drawer view types.** Vertical (the default, unchanged), horizontal paginated pages
  with a dot indicator, and a categories view of folder-style cards that expand into a full
  app list. Every view resolves its own geometry from the plane's width, so icons fill their
  cards instead of floating in them.
- **Categories that can be argued with.** Assignment is a ranked pipeline — your own choice,
  a curated force row, the platform's own `ApplicationInfo.category`, a curated fill row, the
  device's default-role packages, a name heuristic, then Other — and every app carries which
  stage decided it. Any assignment can be overruled per app from the expanded category list,
  and a Games bucket joins the taxonomy. Suggestions rank by a 14-day half-life decay rather
  than raw launch count, so a burst of launches last month stops outranking this week's.
- **Folders shared by the dock and the drawer.** Drag an app onto another to merge them, drag
  one back out to the top level, and rename a folder in place; membership rises from six to
  36 and existing pinned folders migrate as they are. Rename is focusless like drawer search,
  because an EditText here would take the terminal's InputConnection.
- **Drawer settings** — icon size, per-view grid size and view type — applied live to an
  already-built drawer, without recreating the activity or entering the styling reload path.
- **An app-widget pane.** A real `AppWidgetHost`: bind and configure flows, provider
  reconciliation, size options, crash-isolated host views, and widget ids allocated inside one
  durable transaction committed before any activity launches, so a decline, a cancelled
  configure or process death mid-add cannot leak or duplicate one. The pane holds multiple
  pages, and long-pressing a widget hands it over from the provider to a launcher edit drag:
  snap ghost, collision-checked move, per-axis resize handles, remove chip. Long-pressing
  empty surface opens an anchored menu that can only offer to remove a page that is empty.
  A terminal-only install never registers a host at all.
- **A FULL status pane over a live terminal.** Pull down anywhere along the status bar — chips
  included — to expand it, tracking the finger 1:1, and pull up anywhere on the open pane to
  close it. The terminal underneath keeps running with frozen geometry: no per-frame resize,
  exactly one when the spring settles open and one when it settles back, pinned by a test that
  drives real layout frames and counts both `TerminalView.updateSize()` and
  `TerminalSession.updateSize()`.
- **A visual extra-keys row editor.** Tap a key to edit it, hold and drag to move it, add and
  remove rows and pages, and save back to `termux.properties` — a write replaces the matching
  uncommented line in place, so comments, ordering and every unrelated setting survive. Keys
  carry macros and a swipe-up action with its own label. Behind both label fields is a glyph
  picker: a curated ~300-glyph catalogue grouped into arrows, blocks, shapes, powerline,
  technical and terminal marks, searchable by name and keyword, with every candidate filtered
  through `Paint.hasGlyph()` so a glyph the device cannot draw is never offered.
- **Rename sessions, windows and panes from an anchored chip.** A glass chip next to the thing
  being renamed, typed with the in-app keyboard — where renaming used to raise a dialog that
  took focus, swapped the IME in and out and resized the terminal twice for two words of
  typing. A window's name is now held per window rather than per shell, so it survives
  splitting, closing and refocusing panes, and rides the workspace format (version 3) and the
  saved instance state.
- **Scrollback search as a strip on the dock, with vim copy-mode over the transcript.** One
  text line above the dock in the terminal's own face, every hit highlighted in place behind
  it with the current one lit brighter and a match counter at the end. Committing a query
  drops into normal mode over the transcript: `n`/`N` walk matches and scroll the least amount
  that brings one into view, `hjkl`/`w`/`b`/`0`/`$`/`gg`/`G` move a copy-mode cursor, `v`, `V`
  and `Ctrl-V` select charwise, linewise and blockwise, and `y` yanks to the clipboard.
  Escape unwinds one layer at a time, so a mis-started block select costs one key.
- **One in-app sheet plane for every terminal prompt.** The session browser and its seven
  prompts, the hints list, the OSC 8 hyperlink question, the URL picker and the kill-process
  confirmation all moved onto a focusless plane inside the activity. Cards stack, so a
  confirmation opened over the workspace picker gives the picker back rather than dropping you
  on the terminal.
- **One switch for launcher versus terminal-only.** Settings › Launcher & apps opens on a
  two-way pill: Terminal only turns off every home surface — pinned apps row, A-Z index, app
  drawer, widget pane — and turns on show-in-recents-when-not-the-default-launcher, which is
  what a terminal-only install needs to stay reachable from the task switcher. It is not a
  lock: every surface stays individually settable afterwards, and switching back restores the
  states captured when the switch was made rather than a blanket default.
- **A usable landscape layout.** The dock becomes a vertical rail whose side is a preference,
  padded by the cutout inset plus its own column so its icons no longer sit against the
  display edge or pass under the status bar. The drawer opens on a swipe off the rail, away
  from the edge it is docked to — in landscape the portrait pull-down gesture reached nothing
  at all. Landscape categories go from three over-tall tiles to six narrow ones with the next
  row in reach.
- **A background swatch in keyboard colour schemes**, with its own surface opacity independent
  of the key caps, and a rebuilt eight-column swatch grid in the scheme editor.
- **The status bar advertises its own pull-down.** A tap on the bar's chrome blooms a grabber
  pill below the row for a moment — silenced whenever the full pane is unavailable, because a
  hint must never advertise a dead gesture. Weather can be shown in Fahrenheit, and the flip
  clock animates seconds on their own pair with a metadata cell beside the digits.
- **`setup-nvim`,** a separate Neovim distro chooser installed by `setup-launcher` — AstroNvim
  by default, or NvChad, LazyVim, kickstart or stock — adding OSC 52 clipboard, always-on line
  wrap, and on AstroNvim and NvChad a colourscheme generated from `~/.termux/material-colors.sh`
  that retints live on a wallpaper change. The shipped fish config splits in two so a re-run
  can no longer eat personal edits: `config.fish` is launcher-owned and replaced every install,
  while `conf.d/personal.fish` is copied once and never overwritten.
- **A Style row in the terminal long-press menu when Termux:Styling is installed**, opening the
  companion directly. It is offered only when the plugin is actually there — a row that opens the
  Appearance settings under a plugin's name is a broken promise rather than a shortcut — and the
  package is named in `<queries>` so the check answers truthfully under Android 11 package
  visibility. A companion that has renamed its entry activity still gets the row through its own
  launcher activity.
- **A Shizuku user guide**, links to the published TLNix companion releases, and documentation
  of how terminal touch actually behaves — a drag scrolls as wheel events inside mouse-aware
  apps, a brief press-and-hold-then-drag holds the mouse button down so vim selections and
  tmux splits can be dragged by finger, a still long-press keeps ordinary text selection.
- **The whole interface can follow the terminal color scheme.** Appearance › Interface colors
  switches the dock, status bar, app drawer, in-app keyboard, command palette and glass rims from
  the wallpaper palette to one derived from `~/.termux/colors.properties` — the file Termux:Styling
  writes. The scheme is the anchor rather than a seed: its background is the surface, its foreground
  is the text color, its blue (or a distinctly colored cursor) is the accent, and only container
  elevations and their text colors are derived, as a lightness ladder with contrast repaired
  afterwards, so gruvbox comes out gruvbox. Every derived token can be overridden a line at a time
  in `~/.termux/launcher-theme.properties` — hex, another token, a scheme key, or `lighten` /
  `darken` / `mix` / `alpha` over any of those — the way a colorscheme links highlight groups. The
  resolved roles are exported to `material-colors.properties` / `.sh` as before, so prompts, tmux
  and Neovim follow the same palette. Android 11 and newer; below that the terminal still takes the
  scheme and the chrome keeps the wallpaper palette.

- **App categories generated by the on-device model.** The bundled classifier scored 37% on a
  real 113-app phone — its whole heuristic stage assigned nothing, and both browsers landed in
  Communication because the Play Store's own category hint outranks the curated table. Gemma 4
  E4B scores 79% on the same phone, so Settings › Launcher & apps › Drawer layout can hand the
  catalogue to it: a foreground run writes `~/.termux/app-categories.conf`, a hand-editable file
  whose sections are categories and whose lines are packages, and re-runs only classify packages
  the file does not mention, so they stay cheap and never overwrite an edit. A drag in the drawer
  still outranks the file, and the file outranks every classifier stage.
- **…or by any AI chat you already use.** The second route copies a prompt to the clipboard, and
  the answer comes back either through a dialog that keeps a *Copy prompt* button — coming back
  from a chat app, the clipboard holds the answer, and re-opening the chooser used to overwrite
  it — or straight from the notification shade: copying the prompt posts a notification with an
  inline reply, which survives leaving Settings entirely, the way this trip actually goes. Package
  lines for apps you do not have are counted and reported rather than dropped in silence.
- **An AI indicator in the status bar.** A robot glyph with the countdown to the idle unload,
  shown only while a model is really resident and greyed for a few seconds after it unloads
  before it goes — vanishing on the tick would read as a glitch. The runtime lives in its own
  process where any status query would *start* that process, so it publishes a small snapshot
  instead and the countdown ticks locally against an absolute deadline; a snapshot that stops
  being republished is treated as a killed runtime rather than a model that is still there.
- **Folders go where you put them in the drawer.** Long-press a folder tile to pick it up and drop
  it anywhere in the alphabetical list, or past the end to park it last. The position is stored as
  the app the folder sits in front of, not an index, so installing and uninstalling apps does not
  drift it, and a folder whose anchor app is uninstalled falls back to sitting beside its first
  member.
- **Named key bindings.** `map --label "Display name" …` in
  `~/.termux/termux-launcher-bindings.conf` gives a binding the name the keybind hint legend prints
  while Ctrl+Alt is latched. It matters for the generic actions: every app chord runs the one
  `app.launch` action, so the legend used to read "Launch app" for all of them, whichever app each
  one actually started. Bindings captured by long-pressing an app row in the command palette are
  written with that row's app name as their label, so the chords most people have get named without
  editing anything. Labels are capped at 32 characters, the width of a legend row, and an
  unlabelled binding still shows its action's own title.

### Changed

- **The apt `setup-launcher` is gone from this edition.** It installed packages with `pkg` or
  `pacman`, neither of which exists here, so it failed on the first item. Nothing replaces it:
  the shell environment comes from the flake template, and `setup-toolkits` picks the toolkits —
  shell, eye candy, editor, build, node, go, python — by editing `toolkits.nix` and switching.
  `setup-nvim` is unaffected.
- **The volume rocker stays the volume rocker.** Upstream Termux turns the volume keys into
  virtual Ctrl and Fn by default, and a launcher that swallows the rocker leaves no way to
  change the volume from the home screen. `volume-keys = virtual` restores upstream's
  behaviour, and a test pins the value because an upstream merge could quietly take it back.
- **`setup-launcher` asks a better question.** Three choices now: everything, the shell essentials
  (fish, Oh My Posh, zoxide, eza), or one item at a time. "Everything" adds Neovim with AstroNvim
  themed from the wallpaper palette, and the showcase binaries — sigye, the animated-logo fastfetch,
  and kitten — installed into `~/.local/bin` from
  [termux-launcher-binaries](https://github.com/PickleHik3/termux-launcher-binaries), each checked
  against a pinned sha256. An unpublished binary is skipped with the recipe that builds it, never
  installed unverified. That repository carries the licences, the patches and the build recipes,
  including the corresponding source for the GPL-3.0-only `kitten`.
- **`setup-launcher` no longer writes to `~/.termux`.** The script installed `termux.properties` and
  the keyboard layout by replacing whichever files were there; now that the app seeds the former and
  the in-app editor writes an `extra-keys` row into it, a wholesale rewrite would throw that row
  away. It also stopped installing `termux-launcher.omp.json` — `aliens-material.omp.json` is the
  prompt theme, and the older one is kept only for the deprecated `setup-tmux-btop`. Four pinned
  templates remain, down from seven.
- **The dock moves as one plane.** A single slab transform everything on it inherits, replacing
  a glass slab and an icon row on channels of their own: the capsule free-floats with its press
  dip, the edge-to-edge bar hinges at the screen edge, and the tilt cap comes down from 4 to 3
  degrees. Every spring is now actually critically damped — the tilt, press and specular
  channels were all under `c = 2·sqrt(k)` and rang by 2–8% of their amplitude on every settle,
  while the class documentation claimed critical damping throughout.
- **The terminal long-press menu goes back to the pre-sheet dialog.** The sheet plane stays for
  the session browser, the prompts and the palette; the long press returns to starting text
  selection and reaching the menu through the system toolbar's More button.
- **The widget grid default drops from 6×4 to 5×4** to match the home-screen cells widget
  providers design against; an empty repository adopts the new grid on load.
- **Drawer icon size and per-view column and row counts are no longer preferences.** Every view
  resolves its geometry from the plane's width and sizes previews to fill, so a pinned value
  could only put the dead space back. Values left behind by an older install are neutralized.
- **The drawer settings cog is gone** at the product owner's request; drawer settings live at
  Settings › Launcher & apps › App drawer › Drawer layout, pinned by a preferences-tree test.
- **The category overview is on one 12dp rhythm** — it had a 12dp outer gap beside a 20dp gap
  between columns and a 26dp gap between rows, plus a heading band two lines tall for labels
  that never wrap. Preview icons now fill their half of the card, and whatever they cannot take
  is split evenly on all four sides.
- **The categorization run says what it is doing.** A determinate bar on the settings row and the
  same numbers on the notification, with phases behind them: reading the app list, loading the
  model — an explicit load, so a cold runtime is a visible phase instead of a stall at 0 of N —
  categorizing *n* of *m*, then saving; the tail wording ("almost there", "any minute now") is
  reserved for the last stretch, because a run of a full catalogue takes minutes and saying it at
  40% only makes them feel longer. Progress survives leaving the screen and coming back, the
  outcome is reported on the row afterwards and as a dismissible notification for the user who
  left, and a re-run with nothing new to classify now says so instead of loading a model for
  fifteen seconds to sort zero apps.
- **The rename chip shows one greyed target word** instead of a bold target tag beside a
  separate greyed fallback hint — two labels for one idea, in a pill with room for neither.

### Fixed

- **Nerd Font icons drawn at half their height.** A Nerd Font glyph sits on a full em square while a
  text cell is narrower than its em — Maple Mono's is 0.6 em — so a symbol squeezed into one cell
  came out markedly shorter than the capitals beside it. Following kitty, a private-use symbol whose
  glyph is wider than one cell now spreads into the blank cells after it: it asks for
  `ceil(advance / cell width)` cells, takes as many as there are blanks, and never exceeds five.
  The expansion existed before but required the trailing blank to carry the whole same style, which
  no real icon does — fetch tools and prompts colour the icon and not the space after it. Only what
  a blank can actually show now has to match: its background, and any underline or strikethrough
  drawn across it. Powerline separators are unaffected, being geometry rather than shaped text.
- **The clock claimed clicks from empty space.** Its slot lays the view out at the pane's full
  width so alignment can place the face left, centre or right inside it, which left the click
  listener covering blank pane either side of the painted clock. Touches are now gated to the
  painted region for each form.
- **The compact flip clock looked nothing like the full one.** It kept an older evenly-spaced card
  row with flat halves, while the full face had moved on to the departure-board look: paired cards
  with a wider hour/minute gap, per-half convex lighting, a cast shadow down the lower half, hinge
  clips at the pivot, and the stacked meta column. The compact face is drawn from the same face
  now, at compact scale, and its measured width follows the cards it actually draws.
- **A cross-built Fastfetch ignored `~/.config/fastfetch`.** Bionic answers `getpwuid()` for an app
  uid with `pw_dir="/data"`, and Fastfetch trusts passwd over `$HOME`, so the host-built binary
  looked for its config in `/data/.config/fastfetch`, never found it, and fell back to the built-in
  ASCII logo with no error. Termux's own package builds avoid this because termux-packages patches
  `pwd.h` inside its copy of the NDK sysroot; `recipes/cross/build-fastfetch.sh` now force-includes
  an equivalent polyfill and fails the build if it did not take effect.
- **The animated Fastfetch logo was stretched vertically.** The kitty-animation patch sent both a
  column and a row count, and a terminal given both stretches the image to fill exactly that cell
  box — the row count being a whole number of cells, a 294 px logo was stretched into a 310 px box.
  It now sends the column count alone and lets the terminal derive the height from the image's own
  aspect ratio. The image cache key moved with it, so a stretched cached logo is not replayed.
- **`setup-launcher` installed Fastfetch without its runtime dependencies.** The published build
  links `libandroid-glob` and dlopens ImageMagick, Chafa and zlib; without the first it does not
  start at all, and without the others there is no image logo. They are installed with it now, and
  a build reporting no `imagemagick7` support says so.

- **Ghost swipes on the pinned apps row.** A swipe played its whole slide and then landed back on
  the page it came from. Both page-switch animations committed the new page only from their end
  callback, and both drop that callback when something cancels them mid-settle — a second finger
  on the row, the host's transient-state reset, the stable-draw release that follows a re-render.
  The swipe had already qualified and the haptic had already fired, so everything about it looked
  committed except the result. A qualified swipe is now committed exactly once from whichever
  path the animation ends on, cancel included.
- **A hand-written `~/.termux/colors.properties` no longer loses in silence (#11).** Dynamic
  Material colours default on, and that branch never opens the file; applying a scheme from
  Termux:Styling now turns *Use wallpaper colors* off by itself, and a hint under the switch
  states the precedence in both directions. The contrast level also stopped travelling inside
  the palette: it was handed to `updateWith()`, which throws on any key that is not foreground,
  background, cursor or `colorN`, mid-iteration over an unordered map — so some colours landed,
  the rest did not, and the session colour reset never ran. Six times per session on the device
  this was found on. The Style entry missing from the long-press menu is back with it.
- **Reopening Settings from a retained task could crash.** The Intent carries a title resource
  id and a fragment class name from the previous process, and either can be stale after an
  upgrade. Both now fall back to the root screen — narrowly, so a current fragment's
  constructor throwing is still rethrown rather than hidden behind a silent jump.
- **Dropping an app onto a full folder, or one that already contained it, deleted the app.** The
  folder was left unchanged but the top-level pin was removed anyway, on both the menu move
  path and the dock drag path. The source is now removed only after a successful append.
- **The pinned-apps editor could overwrite newer folder and pin state**, because the activity
  and the editor each held their own repository with independent caches; there is now one
  application-scoped repository. A folder migration rollback could also not detect failure — the
  preference setters discarded the result of `commit()`, so a disk-full write reported success
  and cached an undurable snapshot.
- **One finger could page and drag at once.** The drag arbitration policy existed and was
  unit-tested but production code never consulted it: the horizontal pager latched a page swipe
  and the cell's pickup began a drag regardless. Pager and cell now share one irreversible
  stream policy, and the test drives the pager rather than the policy class, because that is the
  only way this class of defect is visible.
- **The FULL status pane could not be opened at all.** A press inside the window bar resolved as
  child-owned and never became a long press — and the window bar is exactly where the gesture is
  documented to start. The tests passed because they asserted the eligibility rule as written
  rather than the product behaviour, and exercised long presses without a real window bar.
- **Horizontal drawer pages under-filled the screen**, because automatic row counts were capped
  by the explicit preference maximum of six: a 1080×2412 device rendered six rows and left about
  a third of each page empty. Measured on device, pages went from six rows to eight and the
  catalogue from four pages to three.
- **Scrolling grid content painted into the search pill.** Raising the pill last settled only who
  drew on top; the grids were still laying out and painting inside the pill rectangle, which a
  slow scroll hid and a fling did not. All three content surfaces now carry hard bounds clips.
- **Rotation resized the terminal twice and left a stale glass crop.** The geometry pass ran
  before the window was re-laid out, so it read the orientation being left, sized the accessory
  stack from stale metrics and posted a resize, then did it again after the real layout. It is
  deferred to a pre-draw listener now, the blur cache is cleared on rotation and its validity
  check carries the orientation, and the accessory render state is rebuilt — without which the
  rows a landscape session collapsed stayed collapsed after rotating back.
- **The rename tool ids were rotated one step**: `window.rename` renamed the session and
  `session.rename` renamed the shell, which made every keybinding and palette row read as a lie
  and left the window itself unnameable. A bound rename that refuses to run now says so through
  the key chord overlay, where before a failing binding was indistinguishable from a dead key.
- **The first drawer-open swipe after a view-type change was swallowed**, because the move that
  claimed the gesture did not apply its own travel and cold content construction consumed the
  rest of the input frame.
- **The dock and drawer blanked during a package-change refresh.** The catalogue now rebuilds in
  place — current apps keep rendering, then everything swaps at once — instead of wiping the
  cache synchronously and reloading.
- **In-app keyboard presses landed on the app icon underneath.** Outline clipping affects
  rendering, not hit testing, so the visually shortened drawer plane still contained a
  full-height interactive grid claiming keyboard-area touches.
- **The extra-keys editor's caps showed raw action ids**, wrapped and clipped inside a 56dp cap,
  because a cap fell back to the action id when no display label was set while the live row used
  a mapping the editor did not reuse. Caps now resolve through that same mapping and never wrap.
- **The expanded category header read as three things at three heights** — the back control was a
  `<` character, typeset small and high in its em box; the count was centred in its own box while
  the title was centred in the band; and the arrow followed the band rather than the title's ink.
  The arrow is a drawn vector now, the two runs share a baseline, and cap height is measured off
  the paint rather than assumed, since this app ships a font picker.
- **An app could not be dragged out of a folder — on the dock either.** A drag started without
  the cross-window flags is delivered only to the window it began in, and the folder popup is a
  window of its own, so nothing outside it ever saw the drop. The drag now crosses this app's own
  windows, and since drag-local state does not travel with it, the dock and the drawer identify
  it by its clip label instead. Dropping a folder member on the drawer takes it out of the folder
  and back into the list.
- **A finished categorization run could leave a full progress bar in the shade forever.** The sort
  service and the AI runtime service posted under the same notification id from two different
  processes, so they overwrote and cancelled each other, and the sort's last frame — bar full,
  "saving the categories" — ended up owned by a service that had no reason to remove it. They have
  their own ids now, and the runtime clears a stale one on startup so an affected install heals.
- **The A-Z scrub lit letters going down but not coming back up.** Scrolling upward reattaches
  cells from the recycler's view cache, which skips binding, so a cell that scrolled off dimmed
  came back still dimmed under the letter it belongs to. The scrub rule is applied on attach as
  well as on bind.
- **Dragging to the edge of the drawer only auto-scrolled while a cell was under the finger**,
  which is exactly what the top and bottom edges do not have once you reach them. Edge dwell and
  auto-scroll gate on the scrolling surface's bounds instead.
- Widget remove chips no longer sit half outside the pane against an edge; the FULL pane rounds
  off at the bottom when it lands above the keyboard or dock; the glyph picker opens on the glyph
  it is about to replace instead of an empty cap; the categories overview scrolls clear of the
  plane's bottom edge; and opening drawer search no longer reveals the extra keys row.


### Security

Findings from an external static review of the launcher's security boundaries, all fixed here.

- **Browser pages can no longer reach the local API.** Responses carried
  `Access-Control-Allow-Origin: *` and neither the `Origin` nor the `Host` header was checked, so
  with localhost authentication turned off any website could launch apps or download, load and
  delete models. A CORS grant is now issued only to loopback origins and echoed back per origin;
  any other origin is refused outright, token or no token. The `Host` header must name an address
  the server actually bound, which closes the DNS-rebinding path into the loopback listener.
- **Media in an inference request is no longer an arbitrary read primitive.** OpenAI-compatible
  content parts accepted absolute paths, `file://` URLs and any HTTP(S) destination with redirects
  followed blindly — enough to read UID-accessible files or pivot into the LAN, link-local
  metadata addresses and the device's own loopback services. Local paths now resolve canonically
  under the Termux home or shared storage and never under `~/.launcherctl`; remote fetches follow
  redirects by hand and re-validate every hop against non-public address ranges.
- **Notification history is opt-in and off by default.** Every posted and removed notification —
  title, text, subtext, expanded text — was persisted under `~/.launcherctl`, where any package or
  script running in the shell can read SMS, email and 2FA previews. Notification access is granted
  for dots, the status bar and the top pane, none of which need anything on disk, so history is
  now a separate decision. Turning it off deletes what was already captured.
- **LAN exposure is time-boxed.** LAN mode speaks plain HTTP with a long-lived bearer token in
  every request header, so anyone on the network can lift it off the wire. The exposure window now
  ends 12 hours after it is enabled: the server rebinds to loopback and rotates the token, and the
  settings copy says plainly that this is unencrypted development-only exposure.
- **`notifications.jsonl` is bounded.** The SQLite history was capped at 10,000 rows while the
  JSONL mirror was append-only and never rotated, so notification spam could retain sensitive
  content indefinitely and eventually fill internal storage. It rotates at 4 MB and keeps one
  rotation.
- **The exported Settings activity only instantiates settings screens.** It accepted any fragment
  class name from an Intent, so any installed app could pick any instantiable fragment in the APK.
  Names are now checked against an allowlist before the class is constructed.
- **`setup-launcher` verifies what it installs.** It fetched shell startup configuration and an
  executable `setup-nvim` from a mutable branch with no signature or digest, which made repository
  compromise into persistent code execution on every shell open. It now fetches from a pinned
  release tag and checks every file against an embedded SHA-256 table, failing closed on a
  mismatch. `docs/en/examples/update-setup-launcher-digests.sh` regenerates the table.

## 0.2.32

### Changed

- **Fresh installs land on the showcase keyboard setup.** The terminal extra-keys row now
  defaults to the launcher tool row — soft-keyboard toggle (paste on long-press), workspace
  picker and save, window previous/next, pane move-to-edge (next-layout on long-press), and
  the scratchpad (float on long-press) — matching the shipped `termux.properties` example. The
  in-app keyboard's default extra-key selection becomes tab, esc, capslock, copy, paste, cut,
  and alt: the clipboard keys are on out of the box, and the navigation keys the extra-keys bar
  already covers stay off. Any explicit selection or `extra-keys` property overrides both, so
  existing setups do not move.
- **`setup-launcher` slimmed down.** The quick-start script no longer downloads fonts — the
  in-app font picker (Settings › Terminal › Font) owns those now — and fzf and unzip leave its
  package list (the shipped config never wired fzf). The README gained a Quick start section
  for the script.
- The `io.vaj.tl` edition is now presented as a side-by-side **demo edition** in the README and
  on the website, steering daily use to the `com.termux` edition.

### Fixed

- **The CPU card's process list no longer sits stale after returning to the launcher.** With the
  A-Z screen-lock method set to Shizuku, every resume tore down the healthy Shizuku backend and
  rebuilt it, so privileged access read as unavailable for the first seconds after each
  home-return — exactly when the card gets opened — and the process list silently kept its last
  snapshot. A ready backend is now left alone (a dead binder still re-initializes), and the
  card's stale marker now shows when the unprivileged fallback cannot read `/proc/stat`, as on
  hardened builds, instead of presenting a frozen reading as fresh.

## 0.2.31

### Added

- **A first-launch tour over real footage.** Three pages on first open: the two commands worth
  running before anything else, the dock as an app launcher with its `%` search and alphabet
  rail, and persistent windows with splits and the live status surfaces — each over a short
  looping clip of the launcher actually doing it. Skippable from any page, shown once, and
  forceable with `EXTRA_SHOW_ONBOARDING` for demos.
- **A usable landscape layout.** The launcher draws into the display cutout and pads content by
  its insets, ending the unscrimmed wallpaper strip on the camera edge. The in-app keyboard's
  height scale is stored per orientation (landscape follows portrait until adjusted), and its
  landscape ceiling drops so the keyboard can no longer starve the terminal to a single row.
  The horizontal dock becomes a vertical rail of pinned apps on the left edge, which the window
  bar, terminal and keyboard all inset past. Portrait is unchanged.
- **A workspace can restore what was running.** Saving records the foreground command of each
  pane behind a checkbox, and loading a workspace that carries commands offers to run them
  again, labelled with how many — asked on both sides, because workspace files are hand-editable
  and a hand-written one should execute nothing unasked. A restored command runs through the
  normal Termux login shell, so it lands in your shell with your config and your PATH, and a
  pane whose command ends or fails is left on a usable shell instead of vanishing. The picker
  also gains a per-row delete, confirmed by name.
- **Per-pane zoom.** Pinch (or the font-size keys) now sizes only the pane you did it in and pins
  that size to the pane — switching windows or re-rendering no longer folds every pane back to
  one shared size, and no pane in any other window moves with it. A new split starts at the size
  of the pane it was split from, and a new window at the size of the pane you came from; panes
  you never zoomed keep following the settings default. Pinned sizes survive activity recreation.
  The scratchpad stays on its own remembered size, neither inheriting nor donating.

- **Reproducible Termux recipes for Sigye and animated-Kitty Fastfetch** — pinned upstream builds
  carry the Android clipboard compatibility fix Sigye v0.6.0 still needs and the Fastfetch GIF
  frame upload/playback patch used to exercise the launcher's terminal-driven Kitty animation.
  Both install under `~/.local` without replacing APT-owned files.
- **Terminal font picker** — Settings → Appearance → Terminal fonts installs a complete multi-face font with no shell: pick from fourteen curated families with download sizes, SHA-256 verification and license text shown up front, with a star on the suggested one (Maple Mono, the variable pair) and each family's own icon, ligature and feature defaults applied on install. Toggles for icons, ligature policy and the `wght` axis rewrite the config in place, and **Use font.ttf / Termux:Styling** hands control back by deleting exactly one file. The picker writes only under `~/.termux/fonts/`, plus `~/.termux/fonts.d/10-launcher.conf`; it never creates, overwrites or deletes `~/.termux/font.ttf` or `font-italic.ttf`, so installing a family changes the terminal only. Reachable from the palette, keybindings and agents as `fonts.pick` and `fonts.install`.
- **`~/.termux/fonts.d/*.conf` drop-ins** — font config fragments load in filename order before your own `fonts.conf`, so a hand-written file always overrides an app-managed or third-party fragment while still inheriting what it does not restate. Bounded to 32 files and 256 KiB across all of them, which never eats into `fonts.conf`'s own 64 KiB allowance.
- **Ordered font fallback** — `fallback_font path=…|family=…`, repeatable up to 8, is the controllable answer to Android substituting a CJK or emoji face you did not choose. Coverage is probed on the cluster's base code point and memoized, and a fallback face never changes cell width.
- **Named symbol maps** — `symbol_map name=<ident> …` lets `font_features` and `font_variations` target one specific map instead of the shared `symbols` target.
- **Geometric box drawing** — box-drawing, block, shade, braille and sextant cells are drawn as geometry snapped to shared integer cell edges, so frames, block ramps and braille graphs join seamlessly at any cell size instead of showing the hairline gaps a font's glyph metrics leave. `box_drawing font` restores glyph rendering, `box_drawing_scale` tunes the four line weights, and `powerline_symbols synthesize` claims the separator ranges too.
- **Bind a key to an app from the palette** — long-press an app row (or `Ctrl+Alt+Enter` on the focused one) and press a combination; it is written to `~/.termux/termux-launcher-bindings.conf` with your comments and ordering preserved, and takes effect immediately. App rows also show the chord already bound to them, and are searchable by it.
- **Rename any session from the palette** — a Rename row per session, so renaming a session other than the current one needs neither the browser nor the panel (`session.rename_at_index`).
- **Working indication** — the pill rim of a window breathes while the shell in it is actually working. The signal is CPU burned by the foreground process group between polls, not output, so a shell echoing keystrokes or a TUI repainting its clock once a second does not read as work, and typing silences the indication outright. Where no privileged reading exists it falls back to sustained output, excluding alternate-buffer applications.
- **Settings cog in the expanded status panel**, opening the launcher's settings; the top-pane clock opens the device's clock app.
- **The terminal identifies itself to capability detectors.** XTVERSION replies `termux-launcher(version)`, XTSMGRAPHICS answers with the sixel colour-register count and screen-following geometry instead of swallowing the query, and `TERM_PROGRAM`/`TERM_PROGRAM_VERSION` are exported to every shell — so programs that pick features by asking which terminal this is (chafa, notcurses and everything on their databases) stop falling back to their most conservative path.
- **Rename and close buttons on sessions-panel rows** — the long press still works, but it was the only way to know it was possible.
- **A window whose shell asked for you lights up.** The pill rim of a window turns the Material error colour and pulses with a halo once a shell in it rings the terminal bell — a permission prompt, an agent handing its turn back, a build that stopped for input. The bell is the signal, so no cooperation from the program is needed, and it is recorded even when the launcher is in the background. Focusing the window clears it, and a bell from the window you are already in is not news.
- **Terminal contrast in the surface editor** — Edit surfaces → Terminal now carries the Softer/Default/Harder control alongside opacity and border, applied live like everything else there, with Reset tab and dismiss behaving as they do for the rest of the tab. Disabled with a reason when wallpaper colours are off, since contrast only grades the generated palette.
- **Every Material container role is exported.** `on_error_container`, `on_tertiary`, `on_tertiary_container`, `outline` and the `primary`/`secondary`/`tertiary_container` families with their `on-` partners join `~/.termux/material-colors.{sh,properties}`, so a prompt or script can draw a filled chip with a guaranteed-contrast pairing instead of borrowing an accent role. The bundled Aliens Material prompt uses them: its Python/virtualenv chip is `on_tertiary_container` on `tertiary_container` (7.25:1) and only turns red when there is an actual error, where it was painting every virtualenv in the error colours (5.34:1).

### Changed

- **Shipped terminal defaults now match the maintained live setup.** The bundled keyboard and its
  editable copies use the revised navigation/voice gestures, while the Fish template and guarded
  installer select the compact, status-aware Aliens Material prompt.
- **An upper-case letter in a binding is Shift.** `map Ctrl+Alt+R …` and `map Ctrl+Alt+r …` are now two bindings rather than the same one — case on the key was previously discarded, so a config file had no short way to name the shifted stroke. Modifier names and multi-character key names (`left`, `pageup`) stay case-insensitive. Using it: `Ctrl+Alt+R` renames the shell session on any layout, while `Ctrl+Alt+r` renames the current window (and still renames the session when split panes are off, where there is no window to rename). The palette prints a shifted letter the same way: `C-A-R`.
- **One group colour per action family, everywhere a binding is shown.** The keybind hint legend and the lit caps on the in-app keyboard now colour each key by its action's namespace — panes, windows, session, workspace, terminal, clipboard, appearance, app — as hue rotations of the live Material primary instead of three shared roles, so windows and session no longer wear the same colour.
- **Pressing a lit cap on the in-app keyboard runs its binding.** A character key under a latched `Ctrl+Alt` is resolved as a stroke instead of being written to the shell as an escape-prefixed control byte, so `termux-launcher-bindings.conf` governs the soft keyboard exactly as it governs a hardware one.
- The Rounded surface style's follow-the-style corner radius is 20dp (was 16–26dp by surface height), and the status surface honours it instead of squaring off.
- Session names are capped at 8 characters instead of 5, and the scratchpad shell is named `scratch`; an existing scratchpad is re-adopted rather than duplicated.
- The transient notice chip moved to the top-right, is quieter, and now carries every terminal notice — window positions, "no session to split", the max-terminals refusal — which were previously stock toasts. Creating a pane reports the new pane count.
- **Background commands report per session, not per pane.** Opening a window, splitting a pane, or moving between the windows of one session no longer raises a corner notice, even when you leave a command running: those windows are still on screen and their pills carry the working and waiting states. Only leaving the session hides them, and that is what now puts a standing row in the top-right corner.
- **A changing window title no longer raises a notice.** It fired on every progress write — several times a second for one background job — to say what the window's own pill and the corner stack already say.
- **The transient notice chip is a Material surface.** Label-medium type on a surface-container-high fill with an outline hairline and elevation, entering on the emphasized-decelerate curve, instead of 9.5sp text on a black scrim. The standing background rows match it one step lower in the elevation scale, and the two share one column: the rows sit directly under the notice and slide up into its slot when it expires.
- **The palette is only rebuilt when it actually changed.** Resume, configuration changes and wallpaper-colour callbacks all forced a full refresh — an HCT palette build, a recolour and repaint of every session, a font-config re-read with typefaces rebuilt and re-applied to every pane, and two file writes — whether or not any Material role had moved. They now consult a fingerprint first, and that fingerprint covers all 25 roles the export is derived from rather than six accents plus the contrast level.
- **Colour and font refreshes are separate.** A wallpaper change no longer re-reads font config from disk, rebuilds typefaces or re-measures panes; only a font change does.
- **The in-app keyboard's colours follow the Material theme.** Every swatch re-resolves its source role on load, on a wallpaper-colour change and on the way back from hidden, so the keyboard moves with the system theme instead of freezing whatever the roles were when the scheme was first written. A swatch you edit is pinned and left alone, an imported Tinted/Base16 palette pins the whole set, and Follow theme hands pinned colours back. Repaints are gated on the resolved roles actually moving.
- **The font catalog doubles** — Meslo LGS NF, Iosevka Term, Monaspace Neon, Commit Mono, 0xProto, Intel One Mono, Recursive Mono Casual and Comic Shanns Mono join, every download verified as before. The Nerd-Font-patched Maple Mono build is dropped: the bundled Symbols Nerd Font already routes the icon planes for every family, so its 20 MB download bought nothing.
- **Powerline separators are synthesized by default**, the way kitty draws them itself: geometry is the only way their edges sit flush with the cell-aligned background rectangles, since a font glyph never quite fills the cell. `powerline_symbols font` hands them back to a patched Nerd Font whose author drew their own.
- **Settings search reaches every sub-screen.** Root search matched only the destination rows' own titles and summaries, so "fonts", "shizuku" or "ligatures" found nothing; each destination now indexes every preference reachable under it, and a row matched through its children says what it contains. The root screen groups its destinations under Launcher and System & Info headers, a duplicated Recents toggle is gone, and the three surface-editor entries are named in parallel.
- **Open-Meteo is credited beside the forecast** — their CC BY 4.0 licence asks for the credit next to the data, not only in a notices file — and hidden when none of their data is on screen. The notices file and in-app licences screen gain a data-sources section recording what actually leaves the device: no account or key, the last known coordinates to four decimal places, and only once the reading is enabled and location granted.
- Bar CPU/RAM readings are smoothed instead of repainting every raw sample, and with the mini-btop card closed sampling slows to halve the privileged `/proc` reads; the card still gets every sample.

### Fixed

- **Every key a latched prefix binds now lights up and appears in the hint legend.** The 18-row legend cap also ended the loop that built the keyboard lighting, so the strokes registered last — `Ctrl+Alt+R` among them, behind the nine session-index digits — were neither listed nor lit. Lighting is no longer capped at all, and a run of keys one action claims (the four arrows, `1`–`9`) collapses to a single legend row.
- **Terminal text is no longer clipped by the rounded corners of the terminal border**, at 20dp or at 40dp: the arc's depth is now padding inside the clip rather than a margin around it, which moved the box without moving its corners off the arc.
- **Closing a pane no longer leaves its background jobs running.** Teardown hangs up the shell's whole process group and escalates to a kill only if the leader is still alive, so `sleep 300 &` dies with the pane it was started from. `nohup` and `setsid` still detach, as intended.
- **The scratchpad no longer shrinks every time the keyboard opens and closes**, and no longer paints under the dock.
- **Showing or hiding a float no longer reflows the terminal behind it** — the frame-line owner, and so the pane inset, no longer depends on whether a float is present, and floats are attached without rebuilding the tiled tree.
- **The CPU card keeps working.** A failed privileged read no longer wipes the process list, a wedged command no longer stops sampling for good, output is drained concurrently so a large process list cannot deadlock the read, sampling resumes after leaving the app, and a stale sample is labelled rather than blanked.
- **Swipe-to-reply opens the newest reply-capable notification** instead of only firing when an app had exactly one, and an incoming notification no longer throws away a half-typed reply.
- The command palette's focus highlight and bottom fade stay inside its rounded corners, and its accent rim is no longer painted over.
- The floating pane's grab pill no longer draws a near-black slab across the top of the float.
- The sessions pop-down accounts for its own row chrome, and only autoscrolls a title that overflows by a readable amount.
- **The wallpaper is visible again in wallpaper mode.** With wallpaper colours on, the terminal surface was raising its own opacity to keep every generated glyph legible over any wallpaper — a floor that only full opacity could satisfy, because a mid-tone grey ANSI colour cannot meet a contrast target over both a dark and a light backdrop at once. That colour is painted on the full-screen root, so the whole launcher went opaque behind the dock and keyboard. The opacity slider is the contract again.
- **The background-command chips no longer blink.** Every bind rebuilt all the rows, which handed the layout transition a full turnover on a view that is re-bound on every title change of every background shell. Rows are reused now, so only real arrivals and departures animate.
- **A new window no longer flashes a chip for its own startup.** Anything a shell's rc files spawn is a non-idle foreground with no shell-integration mark yet, indistinguishable from a background command; a foreground now has to survive one resolver poll before it earns a row.
- **Working indication counts a wrapper script's child.** CPU was read from the foreground process-group leader alone, so `sh build.sh` — which only waits while its child works — reported zero and every wrapped command read as idle. The whole process group is summed now, matched on `pgrp` in one pass over `/proc`.
- **A shell that exits no longer leaves its foreground process behind in the cache**, where a reused pid would inherit it.
- **The exported palette files are built on the main thread.** The writer thread was resolving theme attributes and re-deriving the palette itself, so the files could describe different colours than the terminal took.
- **The terminal surface no longer rebuilds the whole generated palette to read one colour** — a 101-tone contrast search per foreground, cursor and ANSI colour, every time the surface was restyled.
- **Returning to the launcher no longer makes every shell re-read the palette.** The exported colour files were rewritten byte-for-byte identically on every resume, and shells watch them by modification time — the bundled fish config re-sources the palette when it moves and re-runs the tmux theme script inside tmux. Identical content is now left alone.
- **"Customize status appearance" no longer crashes in dark mode below Android 12** (#7). The dark pre-API-31 base theme was the last style in the project on a Material 2 parent, which defines no `materialSwitchStyle`, and measuring a MaterialSwitch without one throws. Found alongside it: the recents-visibility policy silently stopped applying on Android 8–9, and the notification-access row was a dead button below Android 11 — both fixed. A new sweep measures a MaterialSwitch under every activity theme, in both night modes, at both ends of the supported API range, so a theme drifting off Material 3 fails as a test rather than as a report from someone else's phone.
- **The download catalogs no longer flicker while downloading.** The font and TAI model lists re-bound every visible row on every progress tick; a pure progress tick now lands directly on the bound progress bar, a row only re-binds when something visible moved, and the ghost insertion cursor Nothing OS painted over re-bound buttons is gone.
- **Box-drawing synthesis outranks a `symbol_map` match**, so the managed nerd-font map — which spans the whole private-use area — cannot silently turn geometry off; and a narrow private-use icon whose neighbour is a same-styled blank gets both cells, so an icon `wcwidth()` calls narrow is no longer stamped into a single square.

## 0.2.30

### Added

- **Split panes and windows** — tmux-style recursive split panes and windows in the native terminal, with pane controls in the status bar, a per-window pane-layout policy, and a fork-native sessions panel replacing the drawer.
- **Floating panes** — detach the focused pane above the tiled layout (`Ctrl+Alt+F` or the command palette), drag it by the top handle, resize from the grip, dock it back.
- **Workspaces** — durable layout + CWD workspaces with save/picker tools; restores sessions, windows, panes, titles, and working directories after process death.
- **Scratchpad terminal** — toggleable overlay terminal (``Ctrl+Alt+` ``).
- **Command palette and unified keybinds** — terminal-ledger palette overlay (long-press → Command palette, or `Ctrl+Alt+Shift+P`), user-configurable key bindings, keybind hints, and a key inspector overlay.
- **Interactive status bar** — expanded top pane with a clock grid, media widget, and pinned notifications, plus a session-switch indicator.
- **Kitty graphics protocol** — stored images, placements, crop, z-index, delete forms, and terminal-driven GIF animation.
- **Font engine** — `~/.termux/fonts.conf` with four-face configuration, variable-font axes, face-scoped OpenType features, explicit symbol font maps, fixed-cell grapheme shaping, and bounded font metrics.
- **Spacebar swipe gestures and extra-key tools** — swipe bindings on the built-in keyboard's space bar, `tool:` extra keys with key=value arguments, an `app.launch` tool, and app search in the palette.
- **Bundled QWERTY layout** — the built-in keyboard ships a launcher-tuned QWERTY layout by default, with an absolute key-cap opacity control.
- **Shipped configs and `setup-launcher`** — example key bindings, `fonts.conf`, and keyboard layout installed to `~/.termux`; a new guarded `setup-launcher` installer for the fish + Oh My Posh + Maple Mono setup.
- **GPU glass blur** — wallpaper blur runs on the GPU via RenderEffect on Android 12+.
- **`launcherctl`** — launch-only CLI client for launching apps from the shell.

## 0.2.29-hotfix.1

### Changed

- Renamed the keyboard color editor to **Keyboard Colors** and clarified palette editing with **Edit colors** / **Save colors** actions.
- Refreshed the README demo recording and screenshot gallery.

### Fixed

- The welcome tour's quick-setup action now opens the existing website setup section, which uses the maintained `setup-tmux-btop` script.
- Keyboard themes can now be imported by the complete Base16, Base24, or Tinted8 ID shown in the Tinted Gallery, with a direct Gallery link in the import dialog.
- Opening Settings no longer causes the launcher to briefly flash the Settings screen again when Home is pressed.

## 0.2.29

### Added

- **Built-in terminal keyboard** — an embedded on-screen keyboard (a trimmed Unexpected-Keyboard port) you can use in the terminal instead of the Android soft keyboard. Includes themes, a per-key color-scheme creator, dock-matched glass, size/shape and key-spacing tuning, optional key haptics and press sounds, a custom label font, configurable extra keys, custom `~/.termux/keyboard/layout.xml` support, and a settings page linking the upstream layout docs.
- **Onboarding tour** — a replayable first-run showcase with per-page screen-recording preview clips; reachable any time from Settings → System & Info → Quick start tour.
- **Glass Labs** — a live appearance tuner for the terminal, dock, and sessions menu (style, size, per-page icon count, blur/opacity/grain), tuned in-context against the real UI.

### Changed

- Unified the glass treatment across dock, keyboard, sessions menu, and navigation strip.
- Improved adaptive light-mode terminal colors.
- Moved the quick-start tour into Settings → System & Info and added a feedback link.

### Fixed

- The rotate/circle gesture now capitalizes letters even when a custom layout binds Fn to every letter (Shift now wins over the Fn modmap for letter keys).
- A-Z rail swipe-up intent is classified from recent motion, with sticky locks, to stop accidental launches.
- Per-icon ripple color extraction and softer ripple rendering; artwork-hugging search focus outline; dock-style pill rendering.

## 0.2.28

### Fixed

- Rate-limited API responses (HTTP 429) now include `Retry-After` and `RateLimit-*` headers so OpenAI/Ollama clients can back off correctly instead of guessing.
- Attempting to load an embedding model into the generation runtime now returns a clear error; embedding models are served on demand through the embeddings endpoints and no longer need to be loaded.

## 0.2.27

### Changed

- Added **Fullscreen** toggle in Settings → Termux → Terminal View.
- Terminal receive buffer increased from 4 KB to 64 KB for smoother, faster output on heavy streams (build logs, TUIs, AI token streaming).

### Fixed

- Fixed a rare terminal "bounce" where the view could oscillate during relayout (e.g. while running full-screen CLIs); terminal geometry is now derived structurally instead of from its own shifting height.
- Fixed a fullscreen-mode crash on Android 8-10, made the fullscreen toggle apply live, and stabilized the dock lift over the soft keyboard.
- Fixed a terminal freeze that could occur after relaunching the launcher. (all hail Fable, this demon has been a bug since the inception)

### Termux AI

- Upgraded the LiteRT runtime to 0.14.0 and rebuilt the MNN native libraries with embedding support.
- Fixed a LiteRT generation deadlock and corrected MNN memory-mapping for more stable on-device inference.
- Broader OpenAI/Ollama API conformance: token-usage accounting, error shapes, and stop-sequence handling.
- Audit-driven correctness fixes across both backends versus the official specs; retained automatic tool use for mobile-action specialist models.

## 0.2.26

### Added
- Notification popup for pinned apps: when a pinned app has an unread notification, swipe up from its icon in the pinned-icons row to open a popup and interact with the notification directly.
- Pinned app icon pages now loop around instead of stopping at the first or last page.

### Fixed
- Custom app icon bug fixes: icon-pack changes now refresh immediately — including pinned-icon pack changes and resetting per-app icon overrides — without requiring `termux-reload-settings`, and rendered icon caches are invalidated after icon source changes.

## 0.2.25

### Added
- **Termux AI** — run LLMs locally, on-device, right inside the terminal. Two native backends, Google **LiteRT** and Alibaba's **MNN**, serve models over OpenAI- and Ollama-compatible APIs. Works on devices with a supported SoC and enough RAM (Snapdragon 8+ Gen 1 or newer recommended). Quickest start: `pkg i -y aichat`.
- New **Valerie capsule** dock, with better AGSL glass blur, smoother dock physics, and refreshed animations and lighting.
- New app icon.

### Changed
- The optional one-script setup now installs **oh-my-posh** as the shell prompt.
- Dynamic terminal colors and app-name labels are now on by default.
- Reworked open-source attribution and license notices; replaced the fuzzy app-search library with an in-house ranking engine.

## 0.2.23

First release shipped in two editions: the **Termux edition** (`com.termux`, tag `v0.2.23`) compatible with the upstream Termux package ecosystem, and the **VAJ edition** (`io.vaj.tl`, tag `v0.2.23-vaj`) installable alongside official Termux with its own embedded aarch64 bootstrap and VAJ APT repository. See the README's Editions section.

### Added
- Exposed multimodal Gemma 4 (LiteRT) models as modality-scoped OpenAI ids that share one downloaded file: the canonical id loads text-only, `<id>-vision` loads text+image, and `<id>-audio` loads text+audio. This mirrors Google AI Edge Gallery's per-task loading and keeps each GPU load small enough to fit. Select the id from the shell; switching ids reloads the runtime scoped to that modality.
- TAI model import by Hugging Face repo URL with auto backend detection, per-model modality/capability configuration, and imported/downloaded models listed in Browse Catalog.
- LiteRT embedding runtime, LauncherCtl MCP documentation, and OpenAI Responses / Ollama client compatibility for the local model host.
- Per-key glass refraction, glyph glow feedback, dock-glass grain control, and an Apps & Access settings overhaul.

### Changed
- Updated MNN native libraries to 3.6.0 with a UTF-8 continuation-byte patch (fixes emoji/UTF-8 streaming crashes).
- Refined dock styling: glow tiers, capsule icon sizing, page indicator, popup, and wallpaper-mode dock style.

### Fixed
- Bound the isolated `:tai_runtime` process with `BIND_IMPORTANT` so a GPU model load inherits the launcher's foreground priority and is no longer SIGKILLed by Android's low-memory killer during OpenCL initialization (previously surfaced as a runtime "crash" loading large models such as Gemma 4 E4B on GPU).
- Fixed TAI generation streaming, vision autoload, completions on on-disk models, a TAI settings ANR, and restored dock page swipe, extra-keys text-input swipe, and icon contour/pack precedence.

## 0.2.22

### Added
- Added `launcherctl update-scripts` to refresh optional shell/tmux helper scripts without rerunning Getting Started.

### Changed
- Removed the redundant arbitrary `rish` wrapper; use `rish -c` directly for custom Shizuku shell commands and `launcherctl tty-doctor` for setup checks.

### Fixed
- Fixed tmux CPU/RAM helper behavior to prefer efficient `launcherctl resources` data, with a bounded `rish` fallback for plain Termux setups.
- Fixed Shizuku btop helper wrappers to preserve an explicit `RISH_BIN` path.

## 0.2.21

### Added
- Added launcher permission access settings and an accessibility lock prompt.
- Added a guided optional tmux and Shizuku btop setup helper.

### Fixed
- Fixed launch failure when Android denies access to the system wallpaper backdrop.

## 0.2.20

### Added
- Added an optional app-name preview pill while scrubbing the A-Z dock.

### Changed
- Improved A-Z dock scrubbing, page dwell feedback, preview animations, and overflow handling.
- Refined dock, wallpaper, extra keys, and text-selection colors for light and dark themes.
- Settings changes now refresh launcher styling automatically without manually running `termux-reload-settings`.

### Fixed
- Fixed first-run defaults for wallpaper mode and the A-Z row.
- Fixed app-name preview placement, sizing, wrapping, and alignment.
- Fixed sticky extra-key pressed state visibility.

## 0.2.18

### Changed
- Enabled wallpaper mode and the A-Z row by default for fresh installs.

## 0.2.17

### Added
- Added notification dots.
- Added a compact dock toggle for users who need two rows of extra keys, available in Settings > Appearance.

### Changed
- Reworked the apps bar page indicator.
- Removed some items for better security.
- Refined the UI.

## 0.2.16

### Added
- Added global icon pack support for the apps bar and pinned dock.
- Added per-pinned app icon overrides, including apps inside folders.
- Added visual icon selection from installed icon packs.

### Changed
- Simplified launcher icon preferences and moved icon pack settings into Apps Bar.
- Updated icon picker, icon pack picker, wallpaper picker, and launcher popup surfaces to better match the app Material color theme.
- Improved dock background color when transparency or wallpaper is disabled.

### Fixed
- Fixed icon changes requiring a swipe before refreshing.
- Fixed custom icons being lost when apps move into or out of folders.
- Fixed folder previews and folder popup icons using stale system icons.
- Fixed themed icon controls that did not affect launcher icons.
- Fixed app launch reliability for default launch activities.

## 0.2.15

### Changed
- Refreshed launcher documentation and README links around getting started, usage, Material colors, shell integration, tmux setup, and optional Shizuku helpers.
- Restored the GitHub nightly debug build workflow for hosted APK validation.
- Removed stale bundled status helper scripts now covered by documented examples.

### Fixed
- Fixed intermittent first-attempt app launches by preferring normal launcher intents before falling back to `LauncherApps.startMainActivity()`.
- Improved Material color refresh behavior for terminal and shell integrations.

## 0.2.14

### Changed
- Improved dock blur implementation and wallpaper sampling so the phone is not a hand warmer anymore.
- Improved dock motion, IME restore, and return-home animation.
- Improved Material theming across terminal surfaces, dock surfaces, extra keys, and app UI surfaces.
- Added an Appearance toggle to apply Material colors to the Termux shell.
- Exposed Material colors in `~/.termux/material-colors.sh` and `~/.termux/material-colors.properties` for shell integrations such as tmux status bars.

### Fixed
- Fixed the text input field in the extra keys bar/dock so Android keyboard text input can target the field correctly.
- Fixed dock blur flashes and blur pauses during IME transitions.
- Fixed managed/system wallpaper blur alignment and fallback handling.

## 0.2.13

### Fixed
- Fixed Shizuku reconnect after launcher restarts.
- Fixed dock blur state with live wallpapers.
- Improved terminal exit/relaunch behavior.
- Performance refinements and cleanup.

## 0.2.10

### Changed
- Improved launcher search, duplicate app labeling, folder popup sizing, and `launcherctl` status/notification metadata.

### Fixed
- Fixed `launcherctl /v1/apps` to match the launcher’s real app catalog.
- Fixed pinned-page resets during reorder and folder creation.
- Fixed stale pinned and folder app references.
- Fixed folder editor search and package-only folder refs.
- Fixed immediate folder updates for `Move to dock` and `Delete`.
- Fixed collapsed folder previews not refreshing after folder changes.
- Fixed extra right-side padding in the folder popup.
- Removed the pinned-row bloom overlay while keeping page indicators.
