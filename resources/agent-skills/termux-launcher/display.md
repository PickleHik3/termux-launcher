# Apps on the display

Depth: `docs/X11_Display.md`.

There is **no screenshot route**: you cannot see what the display renders, so say so plainly
rather than implying you will check it. For a web page, use terminal-browser (SKILL.md), never a
browser on the display.

## Opening an Android app

```sh
launcherctl launch <name>       # see launcherctl.md
```

## Opening a Linux (X11) app

The launcher has an embedded X11 display (no separate Termux:X11 app needed). Two ways in:

```sh
launcherctl launch gimp                     # starts the display if it was off, switches to it
```

or by hand:

```sh
termux-x11 :0 &
export DISPLAY=:0
gimp &
```

`launcherctl launch <query>` matches a Linux app on the display the same way it matches an
Android one, and starts the display first if needed; this is the route to prefer since it also
switches the user to the Display place.

## GPU acceleration

```sh
launcherctl x11 gpu           # which acceleration row fits this phone, JSON
launcherctl x11 gpu --env     # same, as KEY=value lines
eval "$(launcherctl x11 gpu --env)"
```

Or let it self-test: `termux-x11-gpu-setup`.

## The on-screen keyboard following an X11 text field

```sh
launcherctl keyboard show --source focus
launcherctl keyboard hide --source focus
launcherctl keyboard hide --hold     # keep it down while a full-screen program runs
```

## What you cannot do

- No screenshot of the display or of anything else; you cannot verify what it looks like.
- No clipboard read from an X11 app beyond the same OSC 52 rules as the terminal.
- No layout/theme control routes — those are user settings, not something `launcherctl` exposes.
