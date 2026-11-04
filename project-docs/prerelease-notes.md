<!--
Rolling notes for the pre-release channel (vX.Y.Z-pre.N, nix-vX.Y.Z-pre.N, vaj-vX.Y.Z-pre.N):
everything the channel carries beyond the latest stable release, one file for all three editions.
This whole file is the body of every drop (HTML comments do not render on GitHub), so it restates
the full list each time; when a stable ships, the lines that shipped move into that release's notes
and leave this file. docs/agents/releasing.md, "The pre-release channel", has the rules; the voice is the
release-notes voice (one line per item, what you can now do, no mechanism).
-->
# Pre-release channel

Latest stable: v1.2.2.

## New since pre.2

### Glass
- Real pass-through glass for floating terminals, widgets and the dock. It's off by default: turn on "Live blur" in Settings › Battery & performance (Android 13 and up).
- The app drawer, command palette and terminal glass no longer use software blur, so animations should stay smooth without fps dips.

### Settings
- Battery & performance now sits on the main Settings page, in the System section.

### App drawer
- Smoother app drawer open and close animation.
- Refined the drawer's glass containers when the keyboard is open (only tested with the in-app keyboard).
- The keyboard closes as soon as you start scrolling, when "Open keyboard automatically" is on in Drawer layout settings.
- Redesigned the menu that appears when you long-press an app icon.

### Command palette
- The command palette follows the Menus and dialogs corner radius.
- It has a taller filter field and Material 3 colours and type.

### Dock
- New A–Z style, Beads: each letter has its own bead that lifts under your finger.
- You can now drag the Drawer and command palette icons to any column.
- The old Strip height slider is replaced by Icon size (S to XL), which sizes Home and dock icons together. It's on the Home card and first on the Dock card.
- On a phone in landscape, Home's dock stands as a side bar on the long edge.

### Home
- In landscape, Home keeps your portrait grid and widget arrangement instead of reflowing.
- Turning the phone no longer rewrites your widget layout.
- Media widgets are redrawn: the upright 2x2 is a polaroid, and the two-row cards put art, text, progress and controls in order.
- Home widgets no longer snap when the notification panel's keyboard comes and goes.
- Home and the Workspace turn side by side as you swipe across the seam, and the status bar, window bar and keyboard strip change style under your finger.
- The Home key goes to the first Home page when Start in is Terminal or a Display page.
- A trip to Terminal only, the Workspace or the display brings you back to the same Home page.

### Workspace pages
- You can swipe across terminals normally now, fast swipes included (if a terminal runs an app that grabs mouse input, swipe along the top or bottom border of the pane).
- The windows beside the one in front slide in live while you swipe (turn off "Live windows while swiping" in Settings › Battery & performance if you have performance issues).

### Terminal
- A window flooded with output, kitty image streams included, no longer freezes the launcher (#208).
- Contrast changes in the Appearance editor now apply to the terminal live, custom schemes included.
- Dragging a split divider or a floating window's corner resizes the panes as soon as your finger pauses, instead of only when you let go.
- Panes can have their own border colour, a focus colour and a steady glow: Appearance › Terminal › Pane borders (#206).
- Lazy mode no longer leaves a pane showing stale output until you resize it.
- A full-screen program keeps its top line when its pane resizes.
- The cursor trail snaps to a pane's new size instead of sliding.
- The More menu opens on Home's bottom sheet.
- Panes have less padding above and below, and a little more at the sides.

### Terminal effects
- The CRT effect is richer and sharper: colour split, a dark tube face with a vignette, and a glow on bright text only.
- The CRT bezel is thinner, with depth, and can be a solid bezel in your theme's colour.
- The CRT reacts to the terminal: a bell tears the picture, a new window powers on, focus wobbles it, and a closing window dies in snow.
- The Screen events and Idle animations switches turn those reactions off.
- Under a terminal effect, the keyboard, status strip and window bar get a matching Deck look.
- The cursor trail has a glow halo and longer fades.

### Keyboard
- Fixed the docked keyboard's background not matching the row under Android's gesture nav pill.
- A physical keyboard types into the X11 display.

### Appearance editor
- Another Appearance editor change, I know, but hopefully a better one. It works much like before, but:
    - opened from a Home page, it edits everything on Home;
    - opened from the terminal, it edits everything in the terminal and Workspace;
    - choosing Global edits everything;
    - on any screen, tap an item (status bar, dock, a widget, a terminal pane…) to edit its look and options.
- Widgets and Sheets (the drawer, folders, menus, the command palette and notices) have cards of their own.
- "Where it applies" offers Everywhere, Home pages, Terminal pages or Display pages.
- The keyboard ships its own look per page kind: Mist on Home pages, Clear on Display pages.
- A coloured dot after a row's name shows where its value is set; long-press the name to reset that row.
- Picking a Look on a card resets the looks beneath it, so the change shows on every page it covers.
- Every Wallpaper entry opens the wallpaper picker, and Workspace › More has Wallpaper.
- Global has a Layout list for the whole app, with Cancel or Override, and Reset everything.
- Apps in the dock, the Dock icon pack and a Links box close out the cards.
- The Home and Workspace buttons on the editor's pill slide the mini screen to that page.
- Opening Appearance while the setup cards are showing no longer stacks the two sheets.
- Closing the launcher before Done no longer keeps your changes.

### Setup
- First-run setup is a set of cards: permissions, default launcher, Linux apps, the Display, on-device AI and Learn the basics.
- Settings has a Setup row, a Run setup again row and a Dismissed list.
- A fresh install opens on the Clear look.

### Status bar
- The status bar's long-press menu expands or compacts it.
- A tap on the status bar folds or unfolds it.
- The two-finger status pull is gone, since it fought Android's own system gestures.

### On-device AI
- A model that would leave the phone short of memory is refused before it loads, instead of slowing everything down.
- After Android closes the AI to free memory, it tries the accelerator again on its own once memory recovers.

### Linux apps
- Display pages sit on the wall like your other pages, and a Linux app opens on its own Display page.

### General
- A short sound ducks the music that's playing instead of pausing it (#179).

## Since 1.2.2 (from pre.1 and pre.2)
- Home pages follow your finger, with five transitions: Slide, Depth, Fade, Carousel and Stack.
- Home's new widget family, the dock styles and the Home ⇄ Workspace crossing.
- The Peek dock style, page overview and dragging your layout live.
- Text on a glass terminal stays readable over bright wallpapers, and dialog and whiptail colours stand out.
- Copy and paste pictures through the terminal clipboard, and paste a screenshot you just took from the paste key.
- Edit and create keyboard layouts, edit the extra keys (#164), and the Auto keyboard.
- Adaptive touch accuracy: the keyboard learns where your taps land.
- Faster, smarter dictation cleanup, Auto-detect for the voice language, and you can fix a word before inserting.
- Launcher sound for Linux apps, with media controls, that keeps playing after Exit.
- A new terminal greets you with a rounded welcome card in your wallpaper's colours.

## Editions
<!-- Only what is exclusive to the Nix or VAJ build of this drop; say so when nothing is. -->

Nothing exclusive to the Nix or VAJ build in this drop.
