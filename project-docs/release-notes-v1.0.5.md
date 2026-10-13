## v1.0.5

Contacts in the command palette, shortcuts for any action, a palette that learns what you use, and a steadier dock.

### New

#### Command Palette

- Search contacts from the command palette: type a name or a number, tap the result to open the contact, or tap its phone chip to dial. The first search offers a row to allow contact access. Thanks to @AbineshDev01 (#52, #53).
- Hold any action or contact in the command palette to give it a keyboard shortcut, the way you already could with apps. Binding a row again moves its shortcut instead of adding a second one.
- Actions, apps and contacts are one search list, best match first, so an app's exact name no longer sits below a loose action match. Start a search with `>` to look through actions only.
- The chips under the search box show what you use most: actions, apps (with their icon) and contacts (with their photo, or their initial). Each chip is named after what it does, so the keyboard's split, docked and floating no longer all read "set".
- A search you repeat puts the result you pick most at the top, rather than the one you picked last.

#### Launcher

- The welcome sheet has a Contacts in search switch, so the command palette can find people from the start.
- A fresh install comes with the current tlstore (0.17): terminal-browser is in the store, and Claude Code and opencode start on Android 12 (tlstore#1).

### Changes

- Look › Custom starts from the Look on screen, so you can tune Clear or Mist without losing it; a quick look at another Look and back keeps your changes, and Undo puts back what you opened with.

### Fixes

- Opening the widget picker no longer closes the launcher on Android 11 and earlier (#62).
- With the status bar hidden on a phone with a camera cutout, the app drawer no longer shows a dark band with square corners behind it (#69).
- Picking a letter just as the last letter's apps fade from the dock no longer closes the launcher.
- A pull on the apps row that you let go past halfway opens the app drawer instead of springing back.
- Swiping down on the apps row opens the app drawer while an A–Z letter's apps are showing, not only from your pinned apps.
- Pinned app icons in a full row no longer overlap, and keep a little space between them.
- A new number of apps on the dock, picked in the Appearance editor, shows at once instead of after your next swipe.

### Editions

Shipped as `nix-v1.0.5` and `vaj-v1.0.5`. Nothing was exclusive to an edition.
