# Home screen and apps

This page covers launching Android apps: the pinned apps row, folders, the A–Z index, the app
drawer, searching from the shell prompt and making the launcher your home app. Widgets have their
own page, [Widgets](Widgets.md).

Most of these settings are on **Settings → Apps** ("Dock, drawer and search").

## Pinned apps

The apps row in the dock holds the apps you pin.

- **Tap** an app to open it.
- **Hold an empty spot** in the apps row to choose which apps are pinned, then tap **Done**.
- **Hold an app** for its menu: **App info**, **Uninstall**, **Change app icon**, **Change dock
  icon** and **Unpin**.
- **Drag** a pinned app along the row to reorder it.
- **Drag one app onto another** to make a folder. Tap a folder to open it; its popup can rename the
  folder.

The full list is also under **Settings → Apps → Pinned apps** ("Choose and reorder dock apps."),
and again under **Settings → Apps → Dock → Pinned apps**. **Settings → Apps → Dock → Most-used apps
page** adds a page ranked by how often you launch apps from the launcher; it is off by default.

The apps row can stand on any edge, or be hidden, in Appearance → **Layout**. In landscape it is a
rail on the left by default. See [Layout and full screen](Layout_And_Full_Screen.md).

## Quick reply from the dock

When a pinned app wears a notification dot, swipe from its icon towards the middle of the screen
(up, with the apps row at the bottom) to open that app's notifications as cards, with a reply
field for apps that accept replies. On the other edges a short flick opens the cards and a longer
drag opens the app drawer instead.

Dots need **Settings → Notifications → Notification dots** (off by default) and notification
access. See [Notifications](Notifications.md).

## A–Z index

Slide along the A–Z index to jump to apps by their first letter, drag up to the app you want, and
lift your finger on it. The index is a browsing control, not a text field.

While the A–Z index and the apps row share an edge, the index rides on the apps row. Move or hide it
in Appearance → **Layout**.

**Double-tap to lock.** **Settings → Apps → Double-tap to lock** sets a lock method for a double
tap on the A–Z row: **Off** (the default), **Shizuku** (keeps the normal screen-off animation) or
**Accessibility** (optional, and may flicker). See [Shizuku](Shizuku.md).

## App drawer

Swipe down on the apps row to open the full-screen app drawer, whether the row is at the top or the
bottom. On a side rail, swipe right on a left rail or left on a right rail. The switch is
**Settings → Apps → Swipe down for app drawer**, on by default.

You can also put an **App drawer** key on the extra-keys row; see
[Extra keys](Extra_Keys.md).

**Settings → Apps → Drawer layout** opens the drawer's own page:

- The layout: **Vertical**, **Horizontal pages** or **Categories**.
- **Open keyboard automatically**: the keyboard comes up when the drawer opens.
- **Search keyboard**: "Use the Android keyboard, with its suggestions and swipe typing."

### Categories

With the **Categories** layout, two more rows appear:

- **Category sorting** ("Choose how apps are grouped.") opens **Sort apps into categories**:
  - **On this device**: a local model sorts your apps, and nothing leaves your phone. It takes a few
    minutes; keep the phone plugged in. On phones that cannot run the model it reads "Not
    available" and says why (no model downloaded, or not enough RAM). The model used is chosen in
    the Model centre; see [On-device AI](On_Device_AI.md).
  - **Copy a prompt for an AI chat**: copies a prompt with your installed-app list to paste into
    any AI chat, then you paste its answer back. This sends your app list to that service.
- **Re-sort apps** shows when it last ran ("Last run · … · N apps") and how many new apps are not
  sorted yet. When six or more new apps are waiting, the drawer offers to sort them once.

## Search from the shell prompt

At an idle prompt, type the app-search prefix and a query:

```text
%settings
%camera
```

The matches replace the normal dock content; tap one to launch it. Clear the query to get the dock
back.

- **Settings → Apps → App search → App search prefix** changes the `%` if it clashes with your
  shell.
- **Settings → Apps → App search → Reset usage ranking** clears the learned ranking without
  changing your pinned apps.

The command palette lists installed apps too. Swipe up on the space bar and type an app's name.

## Bind an app to a key

1. Open the command palette and find the app.
2. Long-press its row, then press a key combination with a modifier. On a hardware keyboard you can
   focus the row and press `Ctrl+Alt+Enter` instead.
3. Confirm. The binding is written to `~/.termux/termux-launcher-bindings.conf` and works at once.

A bare letter is refused so normal typing is never swallowed. The app's row then shows its
shortcut, and you can search by it. For the file format, see
[Custom keybindings](Custom_Keybindings.md).

## Make it the home app

- **Settings → Apps → Set as default launcher** opens Android's default home app screen.
- **Settings → App behavior → Show in Recents when not the default launcher** keeps the launcher in
  Recents while another home app is active.
- **Settings → Apps → Home widgets** ("Show a widget page beside the terminal.") turns the widget
  page on or off.

The **Launcher mode** row at the top of Settings sets several of these at once; see
[Get started](Get_Started.md#choose-how-you-will-use-it).
