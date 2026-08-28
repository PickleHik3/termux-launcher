# Changelog — v0.2.36

Covers `dev` since v0.2.35-a.

## New

### Surface Editor

- Rebuilt the surface editor as one page. Global holds the controls every
  surface shares; Fine tune shows one surface at a time behind a selector, so
  the card never jumps.
- Every per-surface value follows the shared Global value until you change it.
  A ✓ marks a row that follows, ↺ marks one with its own value and takes it
  back; each group header takes back the whole surface.
- Added presets as complete looks: Classic, Mist, Slate and Bare. Each card is
  a small mock of the phone wearing that look, and applying one can be undone
  from the notice that confirms it.
- Added a Custom slot: save the look you have from the editor's title bar and
  return to it later.
- Blur, opacity and grain are now set together as a material (Solid, Glass or
  Frost) and an intensity. The individual numbers stay editable per surface.
- Docked terminals can have a rounded frame: a terminal corner radius, with the
  Margin knob giving the terminal room from the screen edge.
- The clock face is back in the editor, as a row that shows the live face and a
  picker that draws all six.
- The sessions indicator and window pills have a corner radius of their own,
  following the status bar until you set it.
- Reset returns every surface to the shipped look. Only Done saves; Back and ✕
  ask before dropping unsaved changes.
- The editor card now uses the full height between the status bar and the
  keyboard, and its preset cards work with TalkBack.

### Terminal

- Tapping a link shows a small Copy / Open strip just above the link instead of
  a page over the terminal; tap anywhere else to dismiss it.
- Copy mode and scrollback search show a legend of their keys in the terminal's
  corner, changing as you type a query, commit it, or anchor a selection.
- Fresh installs open a tapped URL directly (`terminal-onclick-url-open = true`
  in the seeded `~/.termux/termux.properties`). Existing files are untouched.
- Long kitty animations play their whole loop instead of snapping back partway,
  and how much animation the terminal keeps follows the device's memory.
- Showing many animated images in one session no longer stops later ones from
  animating.

### Launcher

- `setup-launcher` can install a build toolchain, and Neovim setups pull it in
  so parsers and language servers build on first launch.
- The generated Neovim colourscheme is transparent by default, matching the
  rest of the launcher; `:MaterialTransparent off` brings back solid surfaces.
- Fastfetch's logo now works when positioned above the module list, not only
  beside it.

## Changes

- The wallpaper opacity slider changed meaning: 0 leaves the wallpaper
  untouched and 100 makes the backdrop fully opaque, the reverse of before. An
  existing value is converted on update, so nothing changes on screen.
- New installs start on a tuned Docked look. Existing installs keep every value
  they have.
- The gap knob is named for what it does: Margin in Docked, Inner padding in
  Floating.
- One switch decides whether the interface follows the wallpaper or the
  terminal colour scheme; the separate chrome colour-source setting is gone.
- Keybind hints no longer take over the A-Z row. While a prefix is held they
  hang off the terminal's edge as a small card, one bind per line, and only the
  keys the card names light up on the in-app keyboard. `?` pulses on its own
  and opens the full table, which now uses the terminal's full width.
- The app drawer closes with a single pull from the top of the list, and the
  categories collapse follows your finger.
- The drawer scrolls smoothly with the live blur behind it.
- Under split panes, Back no longer opens the legacy sessions drawer.
- Animations out of view, or in a hidden pane, pause and resume in step when
  they come back; closing a pane releases its images.
- App icons are kept at the size they are drawn and released when memory is
  needed, so memory no longer grows with the number of installed apps.
- The sessions drawer's opacity moved to Settings → Appearance.

## Fixes

- With the scheme palette selected, the dock, status pill, drawer, keyboard and
  command palette now take the scheme's colours in dark mode too (#16).
- A colour scheme installed by Termux:Styling reaches the launcher chrome on
  reload, without visiting Settings first.
- Running a full-screen program (tmux, an editor, a pager) from a session
  showing a kitty image no longer deletes the image.
- Images displayed from a prompt near the bottom of the screen land where they
  were drawn, without a blank gap above them.
- Long sessions with Shizuku no longer grow memory and file handles with every
  privileged command.
- When the device runs low on memory, animations stop moving rather than the
  app being closed; the images stay in place.
- Ctrl+V and other chords from the in-app keyboard land reliably; a quick tap
  on a modifier no longer turns into a swipe.
- The CPU widget shows its first reading within a second or two of start
  instead of sitting blank.
- Pressing a terminal pane no longer pulls its border away from the terminal's
  edge, and a pane a few rows tall keeps straight edges instead of turning into
  a lozenge.
- Status bar chips no longer get clipped by the corner at large corner radii,
  in Docked or Floating.
- The strip under the gesture pill now matches the keyboard above it when the
  keyboard has its own opacity or colour.
- Grabbing a pane edge no longer draws a second border at a different radius.
- Switching or creating windows no longer flashes black with pane borders off.
- The sessions panel no longer draws two frames around a single-window session.
- `app-categories.conf` now lives at `~/.termux/app-categories.conf`; a file
  left in the home directory is moved across once, edits intact.
- On a fresh install the wallpaper permission prompt waits until the tour is
  done instead of opening over it.
- The date under the clock now starts inset from the pane's edge, matching the
  gap it already leaves before its hairline.
