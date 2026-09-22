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
- Carry a widget past the last page and a new page appears for it; a page you empty disappears on its own, and the + on the Widgets tab adds one whenever you want it.
- A tick and a cross sit with the widget you are dragging: the tick keeps it where you dropped it, the cross puts it back.
- The keyboard floats over the widgets when you type on the Home place, so nothing on the page is squeezed or resized.
- The widget picker opens on your apps and has a search field, so a widget you can name is a few letters away.
- Tap the edge of the Widgets page for a small tab with its settings and an edit button, the way the Display place already works.
- While you are editing, every widget on the page is outlined: tap any one of them to move or resize it, without leaving and coming back.
- While you are moving widgets about, the same tab reads out the grid's size; tap it and drag the two numbers to change how many columns and rows the page has, with the widgets rearranging as you drag.

### Linux display

- Run a Linux desktop or X11 apps beside the terminal. Turn it on from the Display place or from Settings ▸ Display, install the keyboard layouts it needs, and start it with the button on the page or with `termux-x11 :0` in a shell. Everything you know from Termux:X11 keeps working, including `termux-x11-preference`. A display only ever starts when you ask for it.
- Linux apps you install (`pkg install firefox`, say) appear in the app drawer with their icons and a small prompt badge. Tapping one starts the display if needed, opens the app full size and takes you there; they can be pinned and searched like Android apps.
- While you are on the Display place the status bar lists the apps open on the display; tap one to bring it to the front.
- Tap the edge of the Display page for its own small tab: a power button that turns the display on, starts it or stops it, and a cog for its settings.
- The Display place wears the same wallpaper blur, tint and grain as your terminal, so it matches the rest of the home screen while no display is running.
- On the Display place the keyboard is the display's: every key and every chord, from the in-app keyboard or a hardware one, reaches the Linux program. Leave the display by swiping the status bar, tapping a place icon or pressing Home.
- Use Android keyboard (Settings ▸ Display) types into Linux apps with your phone's own keyboard instead of the launcher's. The display shrinks to sit above it, and the extra keys and the touchpad ride up with it.
- One clipboard runs across Android, the terminal, the display and the keyboard: copy in one and paste in any of the others, including into the command palette, the drawer's search and a folder rename.
- The Display place remembers whether the keyboard was up when you left it, and comes back that way.
- Alt+Tab switches between windows on the display with the default window manager, and you can pick which window manager starts with the display.
- Next window and Previous window step through the apps open on the display while you are on the Display place — from the top corners of the space bar, the extra-keys row or a key chord — and the keyboard's own layout-switch swipe works there too.
- Display options (Settings ▸ Display): touch as trackpad, touchscreen or direct touch; resolution the same as the screen, scaled, fixed or custom; text and icon size; clipboard sharing; the window manager started with the display; the mark the Display place wears in the status bar; and how the launcher starts a display — with the launcher, with a command of your choosing, pointing new shells at it, and two compatibility switches for GPUs that draw black or with wrong colours.
- `launcherctl x11 gpu` says what your phone's GPU can do for Linux apps and, with `--env`, prints the exact settings to use; the same answer sits at the bottom of Display options.
- `termux-x11-gpu-setup` tries every graphics profile that fits your phone with a short 3D test, keeps the one that works best, removes the rest and explains each step as it goes. It works on apt and pacman installs and, with a Debian proot installed, tests inside the container too.

### Linux apps

- Apps installed inside a full Linux you keep on the phone show up in the drawer beside the ones installed in Termux itself, with their own icons, and a tap runs one on the display. Sharp icons come out at any size.
- Get GUI apps (Settings ▸ Display) sets the whole thing up: pick where the apps come from — Termux's own, or a full Linux inside it — pick which Linux, tick a browser, file manager, text editor and terminal to start with, and copy one command to paste into the terminal.
- The Display place offers the same screen when there is nothing to run yet, and stops asking once you have copied the command.
- Linux apps have their own category in the drawer instead of being scattered through the others.
- Settings ▸ Display ▸ GUI apps lists them with a switch each, so you can keep one out of the drawer.
- Apps that refuse to start the first time are tried again the way they need, and the launcher remembers, so the next tap opens them straight away.
- Settings ▸ Display ▸ Linux apps in the drawer switches the whole listing off.

### Mouse mode

- Mouse mode is a key of its own on the shipped extra-keys row, and an action you can put on the in-app keyboard or a chord. In the terminal every touch becomes the mouse for programs that take one: a finger is the left button, two fingers turn the wheel, and a fast lift keeps it turning. A program that does not track the mouse gets nothing typed at it.
- On the Display place, mouse mode swaps the keyboard for a touchpad of the same size with a laptop's gestures: one finger points and taps, two fingers scroll, pinch to zoom and tap for the right button, three fingers tap for the middle button, swipe sideways to switch windows and swipe down to bring the keyboard back.
- A small mouse at the end of the status bar shows the mode is on.
- With a split keyboard on the Display place, mouse mode parts the halves wide enough for the touchpad to stand between them, and both halves keep typing.

### Keyboard

- The in-app keyboard comes in three types: docked, floating and split. Swipe up on the keyboard key of the extra-keys row to switch, and each place remembers its own type for portrait and for landscape.
- A floating keyboard is one solid panel in the theme's colour, dragged by the bar along its top, and it remembers where you left it on each place.
- Drag the handle in the floating keyboard's bottom-left corner: left for wider, up for taller. A height slider sits beside the width one in Settings ▸ Keyboard.
- Settings ▸ Keyboard has a Keyboard type row that sets docked, floating or split on every place at once; opening Settings ▸ Layout for one place still sets its type on its own.
- The split keyboard parts every row down the middle and lets taps in the gap through to what is behind it. The gap's width and the floating keyboard's width are sliders in Settings ▸ Keyboard.
- The key under your finger lifts clear of the row while you hold it, with its other characters around it in the directions you swipe for them, so you can see what will be typed before you let go. Settings ▸ Keyboard ▸ Feedback ▸ Key popup turns it off.
- On the Display place, tapping a text field brings the keyboard up and tapping elsewhere puts it away. Turn it off in Settings ▸ Display if you would rather not.
- The settings key on the keyboard opens the launcher's settings.
- The default keyboard theme follows Material 3 now: letter keys sit on the lightest surface, the function keys one tone lower, Enter in your accent colour, and a held modifier in the accent's container tone. No key borders, rounder keys. Imported colour schemes get the same function-key tier.

### Extra keys

- Keys can carry a colour: tap a key while its Appearance card is up and pick from your theme's accents, containers, black or white. The colour is a Material role, so it changes with your wallpaper and dark mode. The Home, Terminal and Display keys come coloured out of the box.
- Keys that do nothing on the place in front of you are dimmed: shell keys on the Home place, pane and session keys on the Display place.
- Settings ▸ Keyboard ▸ Extra keys has presets: the launcher's row, the classic Termux row, two rows, and a clear page.
- The extra keys can stand in a column down either side of a place, and the keys size themselves to the room the column has.

### Terminal

- Copying a wrapped line drops the padding spaces at its end. Settings ▸ Terminal ▸ Clipboard turns it off if you want the spaces kept.
- Let programs read the clipboard (Settings ▸ Terminal ▸ Clipboard) decides whether a program in the terminal may paste what you copied.
- Programs can draw text at two to seven times the normal size, and place smaller text inside a block, so a heading in a preview or a document viewer is actually large (kitty's text sizing, OSC 66). The cursor and selection cover a whole block, copying it gives you its text once, and a block too wide for the pane drops to normal size until there is room again.
- Pictures can be sent as a file rather than pasted in as data, which is how Neovim plugins such as md-render.nvim draw images — they used to draw nothing at all.
- A message from a program in the terminal reaches the phone's own notification shade, so you read it with the launcher put away; tapping it comes back to the pane that sent it, and urgent messages can be silenced separately in the phone's notification settings.
- A mouse or trackpad wears the pointer the running program asks for — a text bar in an editor, a busy pointer for a long job.
- A new pane, a split, a clone and the window bar's folder name all open where your shell says you are, not where it started.
- A program that paints its screen in several writes is shown as one frame, so full-screen programs stop flickering; a program can save and restore the terminal's colours, and is told the pane's size in rows, columns and pixels whenever it changes.
- Programs can follow the terminal between dark and light instead of guessing — nvim, fish and tmux restyle themselves when your theme flips.
- Addresses in the output are underlined with a curly line while tap-to-open is on, and one that a program wrapped and indented across two rows opens whole from either half.
- Holding on the terminal makes your finger the mouse for a program that follows one; hold a little longer for Copy, Paste and More.
- The sessions drawer is under half the terminal's width and slides out of the terminal's own edge rather than the screen's, with the agent's colour on the session name and room for the counts under it.
- The terminal's sixteen colours are drawn from your wallpaper's own hue and saturation at Material tone levels, so the prompt, `ls` and every TUI read as one palette instead of neon accents on a Material background. Black and white stay black and white.
- The oh-my-posh theme and the starship palette speak Material: segments are pills in the container colours with matching text, and starship's palette now carries every Material role name (`primary_container`, `on_surface_variant`, …) next to the usual ones. A ready-made Material prompt sits at the end of the rendered starship file for you to copy.
- Neovim, herdr, starship, oh-my-posh and fish all follow the terminal's colours, and each is rendered with a dark and a light set, so a day/night flip restyles them where they stand.
- The palette keeps up on its own: a dark/light flip, a new wallpaper or a new Material colour re-renders every followed tool and repaints every running shell, even while the launcher is not in front.
- Fonts can be configured in kitty's own grammar, and `~/.config/kitty/kitty.conf` is read for them.
- The session browser, the workspace panels, scrollback search and quick select rise out of the terminal's bottom edge instead of floating over it.
- A window pill shows one short item — the open file, the process or the directory — and its ring, bell, tick and cross all sit in the icon's spot.
- `launcherctl pane` keeps working while the launcher is off screen, so a script or an agent can open, write to and close panes without the launcher in front.
- `tlstore` (or `tl`, `tls`) installs and updates the things the launcher shows off — the fish setup, the wallpaper prompt and Neovim themes, fastfetch, kitten, a terminal clock and Claude Code — with a picker when you don't name anything. `tlstore browse` opens a searchable list, and it keeps itself up to date.
- `tlstore` also installs on plain Termux with one command from the guide, and it only offers you what the app you are running it in can actually use.
- A config file of yours is never replaced without showing you the change and asking first.

### Status bar

- Pinned notification cards show two at a time: swipe up or down on them to page through the rest, with a thin line saying where in the run you are.
- Quick reply on a pinned app with a notification is a swipe towards the middle of the screen, from whichever edge the pinned row stands on.
- The bar's detail cards — the system stats and the weather — open towards the middle of the screen, so a bar along the bottom or a side opens its cards where you can read them.
- A status bar down either side stays compact, and its weather chip shows the number on its own so it fits.
- A window pill's ring follows what the agent in that pane says it is doing rather than guessing from the screen, so the ring starts and stops with the turn.

### App drawer

- The open drawer is one sheet from the top of the screen to the bottom, with no outline down its edges.
- Back closes an open category first and the drawer second.
- A pinned apps rail down either side pages like a row, and the recently-used page reaches it.

### Appearance and Layout

- Every bar goes on every edge: the status bar, the apps row, the A–Z index and the extra keys can each sit at the top, bottom, left or right of a place, and bars that share an edge can be reordered. Lift a bar in the Layout editor and drop it between two others; thin lines show where it will land. Side columns are allowed in portrait too, with a short notice when the terminal is getting narrow.
- The apps row on its side is the same row you know: pinning, folders and drag-to-reorder work in the rail too, and it scrolls when you pin more apps than fit.
- The A–Z index can stand on its own, without the pinned apps row above it, on the top edge or down either side. Sliding along it floats a strip of the apps that match, with the one under your thumb ringed, so you can lift onto it.
- Every corner tab — Home, Terminal, Display and a lone terminal pane — carries two buttons: Appearance for how the place looks, Layout for where its elements sit and how big they are. The terminal's long-press menu offers the same two.
- The corner tab is part of the pane's own outline, flush in its corner, in one glass material on every screen.
- Both editors come up as a sheet from the bottom edge, so the place they are editing stays on screen above them, and the sheet cuts at a whole row rather than halfway through one.
- The Layout editor shows a miniature of the place you are on that draws its real bars; drag one to an edge or into the hide tray to move or hide it, with a Portrait/Landscape pill above the miniature so you can lay out the other orientation without turning the phone.
- On a wide screen both editors use two columns, with the live place showing through the air beside them.
- Dock height, keyboard height and the keyboard's bottom padding are set per place and per orientation in the Layout editor now; upgrading carries your current values over unchanged.
- Settings ▸ Layout opens onto Home, Terminal and Display — pick one to land on that place with its Layout editor open.
- Landscape keeps a floor under the terminal, so the keyboard and an opened status bar can no longer squeeze it away, and each place remembers whether its status bar rests open in portrait and in landscape separately.
- Chrome measures the wallpaper it is standing on and dims only as far as it has to, so the bars, the letters and the window pills stay readable over a pale wallpaper and in light mode.
- The terminal's frame is always on.

### Help

- Help is a screen of its own now, with a page per topic, a search, and a catalogue you can read from anywhere — Settings, the command palette, or the ? on a place's corner tab.
- One tap on Search help puts the cursor in the field and brings the keyboard up.
- Explore this screen walks the place in front of you one card at a time, naming each control where it stands, including every extra key.
- Topics carry a short recorded clip of the gesture they describe, bundled with the app so nothing is downloaded.

### First launch

- A fresh install opens on one card that asks for what the launcher needs — reading your wallpaper, the Linux display, the weather's location — a row each, with one Continue, instead of a stack of system prompts.
- After it comes a welcome card and five short lessons that point at the real controls: where help is, pinning an app, the app drawer, the keyboard and the command palette.
- The closing card says where to go next for shortcuts, customising, extras and GUI apps.
- Someone updating is offered the tour once, and can walk out of it at any card; Settings ▸ About & support ▸ Play the tour again starts it whenever you like.
- Someone updating with an extra-keys row of their own is asked once whether to take the new one. Either way the other row is waiting under Settings ▸ Keyboard ▸ Extra keys ▸ Presets, as "Before the update".

### Local AI

- TAI sizes its context window to the phone's memory, and a conversation that continues the previous one is not read again from the start.

## Changes

- The pull-down status pane is gone; the widget grid it held is the Widgets place of the home screen.
- The status bar's own edge lines are gone; it wears the same thin rim as the dock.
- Settings opens on a list of pages — Layout, Look, Terminal, Status bar, Keyboard, Display, Apps, Services & permissions, Advanced & diagnostics, About & support — with a search field over them. The Linux display has its own page rather than sitting under the apps one.
- The shipped extra-keys row is keyboard, mouse, Widgets, Terminal, Display, split pane and session browser, with a second action on the swipe of the last three.
- Keyboards that lie over a place — floating, split, and the docked keyboard on the Display and Widgets places — are solid panels in the theme's colour. The terminal's docked keyboard keeps its glass, and the Keyboard surface's opacity applies to it alone.
- The magnifying loupe on a terminal hold is gone; a hold now aims the mouse, and a longer one opens Copy and Paste.
- `setup-launcher` and `setup-nvim` are gone. `tlstore` installs the same things, one at a time or from its picker.
- In the sessions drawer, the button that ends a session says End, and a row and what opens under it read as one card.

## Fixes

- The blur sliders for the keyboard, dock and status bar work on phones whose home screen serves a plain picture as a live wallpaper, and every Blur row says when the blur may not match what you see, with a tap to pick a wallpaper the launcher can read (#37).
- Full screen no longer strips the terminal's tint in wallpaper mode (#27).
- Closing a pane ends everything it was running, instead of leaving background programs burning the battery.
- A window pill names the tool that is actually running, rather than the program that launched it.
- A window pill's working ring no longer keeps turning after the command has finished, a command that failed shows a cross, and typing no longer lights the ring.
- Swiping across the window pills no longer slides the next place in once the pills run out.
- The clock's seconds keep one width, so the minutes and the cards beside them stop shifting every second.
- The settings search keeps the cursor in the field while the list filters under it.
- Editing pinned apps: the search field and the app list stay above the keyboard, and the list scrolls instead of closing the editor.
- A large custom wallpaper no longer freezes the launcher while its blur is prepared.
- TAI's Add a model dialog keeps its Import and Cancel buttons on screen.
- The letters of the A–Z bar sit on the bar's centre line, whichever place and orientation it is on.
- A widget follows the phone's theme when it changes day for night, keeps its remove chip, and can reopen its own settings.

## Editions

- Nix: graphical apps installed into the nix profile are listed and opened like any other, and the launcher starts openbox on the display for you. Get GUI apps points at `home.nix` and nixpkgs instead of copying a command, and the tour's extras card does the same.
- VAJ: both routes to graphical apps are offered, because the edition's own repository now carries the X11 apps and the keyboard layouts; that route leaves the browser out, since the repository carries none.
