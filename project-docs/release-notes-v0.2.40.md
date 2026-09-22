## New

### Home screen

- The home screen is three places side by side: your widgets on the left, the terminal in the middle and a Linux display on the right. Swipe left or right on the status bar to move between them; past the last place you come back round to the first.
- The place you are on shows its icon beside the clock — a house for Widgets, a prompt for the terminal, a screen for the display — and the other two peek in from the bar's edges. Tap one to go there.
- The row under the clock belongs to the place on screen: your session and windows on the terminal, the apps open on the display, and on Widgets the stats and the weather line up under the clock, with the weather written out in full.
- The launcher comes back to the place you left it on, and opens there after a restart.
- Go to Widgets, Go to Terminal and Go to Display are actions you can put on the extra-keys row, on the in-app keyboard or on a key chord.
- The terminal never changes size when you move between places, and the other two places sit inside the same frame as the terminal.
- Widgets are laid out for the room they really have, so wide widgets no longer lose their edges.
- Drag a widget onto others and they move aside to make room, so rearranging a page no longer means clearing a spot first.
- Tap the edge of the Widgets page for a small tab with its settings and an edit button, the way the Display place already works.
- While you are editing, every widget on the page is outlined: tap any one of them to move or resize it, without leaving and coming back.
- While you are moving widgets about, the same tab reads out the grid's size; tap it and drag the two numbers to change how many columns and rows the page has, with the widgets rearranging as you drag.

### Linux display

- Run a Linux desktop or X11 apps beside the terminal. Turn it on from the Display place or from Settings ▸ Launcher & apps ▸ Linux display, install the keyboard layouts it needs, and start it with the button on the page or with `termux-x11 :0` in a shell. Everything you know from Termux:X11 keeps working, including `termux-x11-preference`. A display only ever starts when you ask for it.
- Linux apps you install (`pkg install firefox`, say) appear in the app drawer with their icons and a small prompt badge. Tapping one starts the display if needed, opens the app full size and takes you there; they can be pinned and searched like Android apps. Switch the listing off in Display options.
- While you are on the Display place the status bar lists the apps open on the display; tap one to bring it to the front.
- Hold the display for a short menu at the bottom edge with Start or Stop and Display settings.
- The Display place wears the same wallpaper blur, tint and grain as your terminal, so it matches the rest of the home screen while no display is running.
- On the Display place the keyboard is the display's: every key and every chord, from the in-app keyboard or a hardware one, reaches the Linux program. Leave the display by swiping the status bar, tapping a place icon or pressing Home.
- The Display place remembers whether the keyboard was up when you left it, and comes back that way.
- Alt+Tab switches between windows on the display with the default window manager.
- Next window and Previous window step through the apps open on the display while you are on the Display place — from the top corners of the space bar, the extra-keys row or a key chord — and the keyboard's own layout-switch swipe works there too.
- Display options (Settings ▸ Launcher & apps): touch as trackpad, touchscreen or direct touch; resolution the same as the screen, scaled, fixed or custom; text and icon size; clipboard sharing; the window manager started with the display; the mark the Display place wears in the status bar; and how the launcher starts a display — with the launcher, with a command of your choosing, pointing new shells at it, and two compatibility switches for GPUs that draw black or with wrong colours.
- `launcherctl x11 gpu` says what your phone's GPU can do for Linux apps and, with `--env`, prints the exact settings to use; the same answer sits at the bottom of Display options.
- `termux-x11-gpu-setup` tries every graphics profile that fits your phone with a short 3D test, keeps the one that works best, removes the rest and explains each step as it goes. It works on apt and pacman installs and, with a Debian proot installed, tests inside the container too.

### Mouse mode

- Mouse mode is an action for the extra-keys row, the in-app keyboard or a chord; the shipped row has it on the keyboard key's swipe-up. In the terminal every touch becomes the mouse for programs that take one: a finger is the left button, two fingers turn the wheel, and a fast lift keeps it turning. A program that does not track the mouse gets nothing typed at it.
- On the Display place, mouse mode swaps the keyboard for a touchpad of the same size with a laptop's gestures: one finger points and taps, two fingers scroll, pinch to zoom and tap for the right button, three fingers tap for the middle button, swipe sideways to switch windows and swipe down to bring the keyboard back.
- A small mouse at the end of the status bar shows the mode is on.
- With a split keyboard on the Display place, mouse mode parts the halves wide enough for the touchpad to stand between them, and both halves keep typing.

### Keyboard

- The in-app keyboard comes in three types: docked, floating and split. Swipe up on the keyboard key of the extra-keys row to switch, and each place remembers its own type for portrait and for landscape.
- A floating keyboard is one solid panel in the theme's colour, dragged by the bar along its top, and it remembers where you left it on each place.
- Drag the handle in the floating keyboard's bottom-left corner: left for wider, up for taller. A height slider sits beside the width one in Settings ▸ Keyboard.
- Settings ▸ Keyboard has a Keyboard type row that sets docked, floating or split on every place at once; opening Settings ▸ Layout for one place still sets its type on its own.
- The split keyboard parts every row down the middle and lets taps in the gap through to what is behind it. The gap's width and the floating keyboard's width are sliders in Settings ▸ Keyboard.
- On the Display place, tapping a text field brings the keyboard up and tapping elsewhere puts it away. Turn it off in Display options if you would rather not.
- The default keyboard theme follows Material 3 now: letter keys sit on the lightest surface, the function keys one tone lower, Enter in your accent colour, and a held modifier in the accent's container tone. No key borders, rounder keys. Imported colour schemes get the same function-key tier.

### Terminal

- Copying a wrapped line drops the padding spaces at its end. Settings ▸ Terminal ▸ Clipboard turns it off if you want the spaces kept.
- The terminal's sixteen colours are drawn from your wallpaper's own hue and saturation at Material tone levels, so the prompt, `ls` and every TUI read as one palette instead of neon accents on a Material background. Black and white stay black and white.
- The oh-my-posh theme and the starship palette speak Material: segments are pills in the container colours with matching text, and starship's palette now carries every Material role name (`primary_container`, `on_surface_variant`, …) next to the usual ones. A ready-made Material prompt sits at the end of the rendered starship file for you to copy.
- herdr joins the tools that follow the terminal colours: its panes, sidebar and status line take the wallpaper palette, and the launcher wires it into your existing herdr config without touching the rest.
- The palette keeps up on its own: a dark/light flip, a new wallpaper or a new Material colour re-renders every followed tool, even while the launcher is not in front.
- The session browser, the workspace panels, scrollback search and quick select rise out of the terminal's bottom edge instead of floating over it.
- A window pill shows one short item — the open file, the process or the directory — and its ring, bell, tick and cross all sit in the icon's spot.
- `tlstore` (or `tl`, `tls`) installs and updates seven things the launcher shows off — the fish setup, the wallpaper prompt and Neovim themes, fastfetch, kitten, a terminal clock and Claude Code — with a picker when you don't name anything.
- A config file of yours is never replaced without showing you the change and asking first.

### Appearance and Layout

- Every bar goes on every edge: the status bar, the apps row, the A–Z index and the extra keys can each sit at the top, bottom, left or right of a place, and bars that share an edge can be reordered. Lift a bar in the Layout editor and drop it between two others; thin lines show where it will land. Side columns are allowed in portrait too, with a short notice when the terminal is getting narrow.
- The apps row on its side is the same row you know: pinning, folders and drag-to-reorder work in the rail too, and it scrolls when you pin more apps than fit.
- Extra keys can carry a colour: tap a key while its Appearance card is up and pick from your theme's accents, containers, black or white. The colour is a Material role, so it changes with your wallpaper and dark mode. The Home, Terminal and Display keys come coloured out of the box.
- Extra keys that do nothing on the place in front of you are dimmed: shell keys on the Home place, pane and session keys on the Display place.
- Every corner tab — Home, Terminal, Display and a lone terminal pane — carries two buttons: Appearance for how the place looks, Layout for where its elements sit and how big they are. The terminal's long-press menu offers the same two.
- The Layout editor shows a miniature of the place you are on; drag a bar to an edge or into the hide tray to move or hide it, with a Portrait/Landscape toggle above the miniature so you can lay out the other orientation without turning the phone.
- Dock height, keyboard height and the keyboard's bottom padding are set per place and per orientation in the Layout editor now; upgrading carries your current values over unchanged.
- Settings ▸ Layout opens onto Home, Terminal and Display — pick one to land on that place with its Layout editor open.
- The terminal pane's corner tab shows the same sliders and ? icons as the other places now.

### Local AI

- TAI sizes its context window to the phone's memory, and a conversation that continues the previous one is not read again from the start.

## Changes

- The pull-down status pane is gone; the widget grid it held is the Widgets place of the home screen.
- The status bar's own edge lines are gone; it wears the same thin rim as the dock.
- The shipped extra-keys row is keyboard, new session, Widgets, Terminal, Display, split and workspaces.
- Keyboards that lie over a place — floating, split, and the docked keyboard on the Display and Widgets places — are solid panels in the theme's colour. The terminal's docked keyboard keeps its glass, and the Keyboard surface's opacity applies to it alone.

## Fixes

- A window pill's working ring no longer keeps turning after the command has finished, and a command that failed shows a cross.
- Swiping across the window pills no longer slides the next place in once the pills run out.
- Editing pinned apps: the search field and the app list stay above the keyboard.
- A large custom wallpaper no longer freezes the launcher while its blur is prepared.
- TAI's Add a model dialog keeps its Import and Cancel buttons on screen.
- The letters of the A–Z bar sit on the bar's centre line, whichever place and orientation it is on.

## Editions

- Nix and VAJ: nothing exclusive in this release. The Linux display's packages come from each edition's own package source; the VAJ repository does not carry them yet.
