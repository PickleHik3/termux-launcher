---
status: accepted
date: 2026-11-26
---

# Every shell shares one runtime folder

Research: `docs/research/wayland-desktops-in-a-window-2026-11-26.md` (section 6).

`XDG_RUNTIME_DIR` names the folder where programs leave the sockets other programs find them by.
The launcher never set it. The one time a desktop was started with a value of its own, Herdr looked
in a different folder than the user's other shells and started a second, empty core (41fb70a16),
and the rule became "never set or changed" (`docs/agents/linux-apps.md`, the Wayland Display spec).

That rule now stands in the way of **desktops**. Every Wayland desktop puts its own socket in that
folder for the apps inside it, and sway, labwc, weston, niri and Hyprland each stop at start when
the variable is unset. A user following a desktop's own guide in a launcher terminal gets an error
on the first command.

## Decision

1. The launcher gives every shell the same default: `XDG_RUNTIME_DIR` is the prefix's tmp folder
   (`$TMPDIR`), the value Termux's own guides use and the folder that already holds the Wayland
   Display socket.
2. It is a default, filled in only when the variable is empty. A user who sets their own keeps it.
3. It reaches every shell the same way, not only the ones the launcher starts: terminal windows,
   ssh logins and boot scripts. fish, which does not read `profile.d`, gets it through its own
   start-up folder.
4. Every install gets it from the build that ships it, whether or not Linux apps or Wayland Display
   are in use. It does not follow a setting.
5. It is in place before the first shell of a launcher start is opened.

## Considered

- **Keep it unset and tell users to add a line.** No risk to existing set-ups, but every desktop's
  guide gains a first step that is ours, and a desktop opened from the drawer could not start
  without the launcher setting the variable for it alone.
- **Set it only for the desktops the launcher starts.** Ordinary shells stay untouched, but this is
  the shape that split Herdr, and the desktop's command typed in a terminal still fails.
- **Write it only while Wayland Display is chosen.** Users who never choose it see no change, but
  changing the setting leaves old and new shells disagreeing, and a Wayland desktop chosen per app
  under X11 Display would not start.
- **Ask when the first Wayland desktop is installed.** Only users who want one get the change, but
  the question is about something users should not need to understand.

## Consequences

- A desktop's documented command works as written; a desktop inside Wayland Display takes the next
  free socket name in the same folder and its apps find it there.
- What split Herdr was shells disagreeing. One default for all of them removes the disagreement;
  a later change that gives any group of shells a different value brings it back.
- Any program that looks at the variable starts using that folder, for every user, including those
  who never open a Linux app. Sound is unaffected: the launcher's client file names the sound
  server outright.
- The "never set" lines in `docs/agents/linux-apps.md` and
  `project-docs/active/linux-windows-wayland-2026-11-24.md` are superseded by this record once it
  is built.
- Not settled here: the value inside a proot-distro container (the runner already gives a
  container's desktop a folder of its own), and the folder's permissions, which weston wants
  private; both belong to the spec.
