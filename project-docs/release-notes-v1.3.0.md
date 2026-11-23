## v1.3.0

The finished 1.x launcher: Linux apps that follow your typing, a drawer you can drive from the
keyboard, and the fixes from months of 2.0 testing brought back to the launcher you already use.

### New

#### Linux apps

- The keyboard follows text fields in every touch mode: it comes up when an app asks for typing and
  goes away when it doesn't. Set up Linux apps installs what this needs.
- The touchpad follows the keyboard mode and can switch it.
- When the display can't start, its window says why, with a link to Linux apps settings.
- New shells connect to a running display by themselves.
- Apps opened on the display get a proper mouse cursor.
- Settings › Linux apps has a Benchmark graphics row.
- The Linux display works on 32-bit phones and with the 32-bit APK.

#### App drawer

- Arrow keys walk the search results.
- The search keyboard slides away as you scroll the list.
- Pressing Home clears the search and goes back to the top.
- Apps can be picked up from the Categories view and dropped on Home.

#### Terminal

- Dragging a pane divider or a floating pane's corner resizes the panes whenever your finger
  pauses, not only when you let go.
- Dim text stays readable on a transparent terminal over a dark wallpaper.

#### Settings

- Each section's rows sit together in one rounded card.

### Fixes

#### Terminal

- A shell that Android kills keeps its pane open with a "Process completed" line instead of the
  window vanishing; Enter closes it.
- Fast or heavy output no longer freezes the launcher or the screen.
- Full-screen programs keep their top rows when the terminal changes size.
- A short session no longer shows a blank band above its output when its pane grows.
- Coloured bars at a terminal's top or bottom no longer leave square blocks above rounded tabs.
- Panes that are off screen show new output again.
- Softer, Default and Harder contrast make dim text visibly different.
- The split divider is easy to grab even when the gap between panes is tiny.
- Typing after a long cursor move no longer smears the cursor.
- The sessions drawer's Saved list fits its tab.

#### Linux apps

- After a display session, the Home palette and the terminal take typing again without a restart.
- A physical keyboard types into the display, and typing letters, digits and symbols is steady.
- Volume keys work on the Display page.
- Closing the display ends the desktop started with it and leaves your other sessions alone.
- Stop closes the display server on every Linux setup.
- Desktops started from Termux get the on-screen input method, and their shells find the display.
- Opening or closing the display no longer flashes the bare wallpaper.
- The display's empty state stays readable over any wallpaper, and its help link opens the right
  part of the guide.

#### App drawer and keyboard

- Drawer search raises the launcher's keyboard on every screen when it is your chosen keyboard,
  also over a running display, and closing the drawer puts things back as they were.
- Key bindings work from the in-app keyboard, also while the drawer search or palette is open.
- A press you slide off the keyboard types nothing, and sliding on the space bar no longer adds a
  space.
- A sideways swipe on the navigation handle switches apps while the keyboard is open.
- The keyboard theme preview shows the layout you type on.
- Home's labels no longer show through the drawer while it opens.
- Scrolling the drawer's pages no longer crashes, and rows fill the page.
- Icons stay on screen while the app list reloads.
- Categories stay put while the drawer closes, and wide screens get more columns.
- The A–Z letters spread over the whole card, also with the keyboard up.
- Labels in app cells keep clear of each other and of the card edge.
- Uninstall from an app's menu works on Android 9 and later.
- Tapping a category sort again no longer stops it.

#### Home, dock, widgets and status bar

- The pinned row no longer asks you to pin apps when you already have pins.
- The 1×1 Battery and analog Clock fit their card.
- The status bar clock is no longer clipped.
- The status bar's stats use less battery.
- The edge glow stays inside the frame you pressed.
- Menus, the floating strip and the extra keys' bubble sit in the right place in split screen.

#### Settings

- Choices and pill labels stay on one line at large text sizes.
- The selected section chip stays whole on screen, and the search text sits beside its icon.
- The Linux apps Input page says plainly whether the keyboard covers or shrinks the app.
- The extra keys editor's save bar fits in landscape.
- Diagnostics › Export logs can save to a file.
- Screen readers can read the command palette.

#### On-device AI

- The launcher keeps some memory free before loading a model, so a big model no longer gets it
  closed.
- After a low-memory stop, the faster accelerator is tried again once memory is back.
- Dictation works in every build.
- Imported models show readable names.

#### launcherctl, Shizuku and root

- launcherctl answers as soon as the launcher starts, also after a package update (#205).
- A command's exit is reported even when a background process keeps its output open.
- Locking the A–Z strip no longer turns off root.
- The privileged helper installs on x86_64 devices.

### Editions

Nothing is exclusive to the Nix or VAJ build in this release.
