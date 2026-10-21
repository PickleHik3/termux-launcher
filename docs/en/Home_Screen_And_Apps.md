# Home screen and apps

This page covers Home and launching apps: app icons on Home, the pinned apps row, folders, the A–Z
index, the app drawer, the Terminal entry, searching from the shell prompt and making the launcher your home app.
Widgets have their own page, [Widgets](Widgets.md).

Most of these settings are on **Settings → Dock & drawer** and **Settings → Home**.

## Home and the Workspace

The launcher is one row of pages. **Home** comes first: your widget pages, which you swipe through
anywhere on the page, as on any home screen. The **Workspace** follows: your terminal windows, then
any [Linux display](X11_Display.md). Swiping left past the last Home page reaches the first window;
dragging right from the first window's border comes back. The home button always lands on Home
page 1, and coming back from another app shows the page you left.

Home wears the same chrome as the Workspace: the same status bar (with the same window chips), the
same dock and the same look. Only the extra keys are the Workspace's alone; they fold away as you
swipe to Home while the bar and dock stand still. Home has no borders. The dock floats over the
widgets rather than taking room from them: on the bottom edge it rides up with the keyboard and settles back when
the keyboard closes. Drop it **Under the keyboard** in the Layout editor to keep it below instead.
The Workspace also has the extra keys, borders and corner tabs.

One layout dresses Home and the Workspace. If you want Home laid out differently, open Appearance →
**Layout** and set the **Layout** line to **Home's own**; Home then keeps its own choice of which
rows show, their order and edge, and its own Dock design, Search field and Drawer tile, and the
row's house segment previews it. The sizes stay shared with the Workspace (the apps row's and the
Strip row's heights, the keyboard's height and chin) and so do Style, Corners and Margin, so
nothing resizes on the swipe. It starts as **One layout**. On Home, Margin is the space around the widgets, around the terminals floating
over Home and around the dock, all at once. Minimal mode affects only windows; Home ignores it. See
[Layout and full screen](Layout_And_Full_Screen.md).

**Settings → Home → Start in** says where the launcher opens after a restart: **Home** (the default) or
**Terminal**, which opens the Workspace with one fresh shell window.

(Older versions had a usage mode and three places, Widgets, Terminal and Display, on a pane wall.
Home and the Workspace replace them.)

## App icons on Home

A Home page holds app icons beside its widgets. Each icon takes one cell of the widget grid, with
the app's label under it, and wears the same icon as the drawer and the dock, icon pack included.

- **Add one from the drawer:** hold an app and choose **Add to Home**. The same row is in a pinned
  app's menu.
- **Add one from Home:** hold an empty spot on a Home page and choose **Add an app**, then tap the
  app in the list (it has a search field).
- Either way the icon goes on the Home page you are on, in its first free cell. When that page is
  full it goes on the first page with room, and when every page is full on a new page after the
  last. A message names the page.
- **Tap** an icon to open the app.
- **Hold** an icon to pick it up, as you would a widget: move it on the page, or hold it against
  the page's edge to carry it to another page (past the last page, a new one is made). Its remove
  chip takes it off Home. Icons have no resize handles.
- An app you uninstall or disable leaves its icon where it was, faint, under its old label. Tap it
  to select it and take it off with the remove chip; it comes back to life if the app returns.

Icons follow Home's grid and its **Margin**, exactly as widgets do.

### Folders on Home

Folders on a Home page work like the dock's. While a page is in edit mode (hold an icon), pick up
an icon and rest it on another icon for a moment: the icon under it swells, and letting go there
makes a folder of the two, named after the app you dropped. Rest an icon on a folder the same way
to add it. Let go before the swell and the icon moves as it always does. Nothing merges with a
widget, and a folder holds up to 16 apps (Settings › Home › Folders › Max folder size changes
that, and Folder density how tightly an open folder lays its apps out).

- A folder takes one cell and shows up to four of its apps in a small grid, with its name under
  it.
- **Tap** a folder to open it. Tap an app in it to open the app. Tap the folder's name to rename
  it, as in the dock.
- **Hold** an app in an open folder and choose **Remove from folder**: the app goes back on the
  Home page you are on as an icon (or on the first page with room). When one app is left, the
  folder turns back into that app's icon.
- **Hold** a folder to move it or carry it to another page, as you would an icon; its remove chip
  takes the folder and the apps in it off Home (the apps stay installed).

### Lock Home layout

Turn on **Lock layout** from Home's long-press menu (or Settings › Home › Lock Home layout) and
nothing on your Home pages can be moved by accident. While it is on:

- Holding an icon, a folder or a widget does not pick it up; a short note says the layout is
  locked. Taps still open apps and folders.
- The menu's rows that add or change things (Add a widget, Add an app, Add a page, Edit widgets,
  Edit pages, Remove page) step aside for **Unlock layout**, right under Terminal.
- An open folder cannot be renamed and offers no Remove from folder.
- **Add to Home** from the app drawer's app menu still works: it is a deliberate act, so the lock
  lets it through and the new icon goes on the page with room.

### Edit pages

**Edit pages** (Home's long-press menu, or Settings › Home) lists your Home pages: move each up
or down, choose the one Home opens on (the house), add a page at the end, or remove one. Removing
a page that holds something asks first; its widgets, icons and folders go with it, and a floating
terminal on it moves to another page with its shell still running. The last page cannot be
removed.

## The Terminal entry

**Terminal** is the launcher's own entry in the app drawer, beside the Linux apps, and it is pinned
to the dock.

- **Tap** it to go to the Workspace at the window you left, or to a new window if none is open.
- **Hold** it and choose **New float here** to open a terminal floating over the Home page you are
  on. **Terminal** in a Home page's long-press menu does the same. See
  [Home floats](Panes_Windows_And_Sessions.md#home-floats).

## Pinned apps

The apps row in the dock holds the apps you pin.

- **Tap** an app to open it.
- **Hold an empty spot** in the apps row to choose which apps are pinned, then tap **Done**.
- **Hold an app** for its menu: **App info**, **Uninstall**, **Change app icon**, **Change dock
  icon**, **Unpin** and **Add to Home** (see [App icons on Home](#app-icons-on-home)).
- **Drag** a pinned app along the row to reorder it.
- **Drag one app onto another** to make a folder. Tap a folder to open it; its popup can rename the
  folder.

The full list is also under **Settings → Dock & drawer → Pinned apps** ("Choose and reorder dock
apps."). **Settings → Dock & drawer → Most-used apps page** adds a page ranked by how often you launch apps from the launcher; it is off by default.

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

**Double-tap to lock.** **Settings → Dock & drawer → Double-tap to lock** sets a lock method for a double
tap on the A–Z row: **Off** (the default), **Shizuku** (keeps the normal screen-off animation) or
**Accessibility** (optional, and may flicker). See [Shizuku](Shizuku.md).

## App drawer

Swipe down on the apps row to open the full-screen app drawer, whether the row is at the top or the
bottom. On a side rail, swipe right on a left rail or left on a right rail. The switch is
**Settings → Dock & drawer → Swipe down for app drawer**, on by default.

You can also put an **App drawer** key on the extra-keys row; see
[Extra keys](Extra_Keys.md).

Hold an app in the drawer for its menu; **Add to Home** puts it on a Home page (see
[App icons on Home](#app-icons-on-home)).

Beside your Android apps, the drawer lists the **Terminal** entry and, under **Linux Apps**, the
Linux apps installed in Termux or in a distro, with a **Display** entry that opens the bare Linux
display. See [The Linux display](X11_Display.md#apps-on-the-display).

**Settings → Dock & drawer → Drawer layout** opens the drawer's own page:

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

- **Settings → Dock & drawer → App search → App search prefix** changes the `%` if it clashes with your
  shell.
- **Settings → Dock & drawer → App search → Reset usage ranking** clears the learned ranking without
  changing your pinned apps.

The command palette lists installed apps too. Swipe up on the space bar, or on Home swipe up from
the bottom edge, and type an app's name.

## Bind an app to a key

1. Open the command palette and find the app.
2. Long-press its row, then press a key combination with a modifier. On a hardware keyboard you can
   focus the row and press `Ctrl+Alt+Enter` instead.
3. Confirm. The binding is written to `~/.termux/termux-launcher-bindings.conf` and works at once.

A bare letter is refused so normal typing is never swallowed. The app's row then shows its
shortcut, and you can search by it. For the file format, see
[Custom keybindings](Custom_Keybindings.md).

## Make it the home app

- **Settings → General → Set as default launcher** opens Android's default home app screen.
- **Settings → General → Show in Recents when not the default launcher** keeps the launcher in
  Recents while another home app is active.
- **Settings → Home → Start in** chooses whether a restart opens on Home or in the Workspace.
