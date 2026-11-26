# Desktops

A desktop is a whole Linux desktop (a panel, a menu, windows) running on your phone, opened like any
other app. You install it from Settings, open it from the app drawer, and remove it from its own
settings page. There is nothing to paste into a terminal.

Desktops are Linux apps, so they need [the Linux display](X11_Display.md) and are listed alongside
[Linux apps from a distro](Linux_Apps_From_A_Distro.md).

## Which desktops you can install

- **XFCE**
- **LXQt**
- **MATE**
- **Plasma** (its X11 desktop)

Each one shows **Install** or **Installed**. Several can be installed side by side; only one runs
at a time.

## Install one

Go to **Settings → Linux apps → Desktops** and tap **Install** on a desktop. A small terminal opens
under the row so you can watch it work, and the row says **Installed** when it finishes. You can
leave the page while it runs: the install carries on. If it fails, tap **Install** again; nothing
is lost and it picks up from where it stopped.

A desktop you set up before this page existed shows as **Installed** too.

If you use the [set-up guide](Linux_Apps_From_A_Distro.md#set-it-up-in-one-go) on Termux's own
apps, its desktop step is a choice from this same list, or none.

## Desktops inside a container

A desktop you installed inside a `proot-distro` container is listed as installed, under the name of
its distro. Its row opens its settings, but it has no **Install** or **Remove**: manage those from
the container.

## Open one

An installed desktop is in the app drawer under **Desktops**. Tap it to open it. Opening a second
desktop while one is running asks "Stop XFCE and open LXQt instead?", so the phone's memory is not
spent on two at once.

Closing a desktop's window ends the desktop, and a desktop that ends closes its window. Apps you
open inside a desktop stay inside it.

On a desktop's tab, **Full screen** gives it the whole screen. **Back** leaves full screen, and a
swipe up from the bottom edge shows the extra keys for a moment.

## Display and Graphics

Each desktop's settings page has two rows.

- **Display** chooses where it opens: **Default** (follow the main Display setting), **X11 Display**
  or **Wayland Display**, where it opens as a normal window you can split beside a terminal.
- **Graphics** chooses how it draws. It follows the main graphics setting until you pick another,
  so you can try one desktop on one choice and a second desktop on another. See
  [GPU acceleration for your apps](X11_Display.md#gpu-acceleration-for-your-apps).

A graphics choice that does not suit a desktop is never a trap: change it back here without
opening the desktop.

## Remove one

Open the desktop's settings page and tap **Remove**. You are asked first, and if the desktop is
running it is stopped.

Remove takes away what the desktop brought and nothing you installed yourself. A program that
another installed desktop or your starter apps use stays.

**Also delete its settings** is off by default. Leave it off and the desktop comes back as you left
it if you install it again. Turn it on for a clean slate.

## Which desktops are not offered

**Hyprland** and **Plasma's Wayland desktop** are not offered, and cannot be. They need direct
access to the phone's graphics hardware, and Android gives no app that access. Putting them in a
container does not change that, because the container runs on the same phone with the same limit.
**Plasma's X11 desktop** works and is in the list.
