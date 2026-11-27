# #411: sway and labwc against XFCE on pong (2026-11-27)

The figures ticket #411 asks for, measured on pong (Nothing Phone 2, Android 16) on staging builds of
v2-redesign 03d2e15b2, by an agent driving the phone over adb. The developer's verdict by eye is
not recorded here yet; the switch (`DesktopCatalogue.WAYLAND_DESKTOPS_OFFERED`) stays off until it is.

Groups: **desktop** = the desktop's own processes (session, compositor, panel, terminal, browser);
**host** = the launcher's Linux-window host (`com.termux:linuxwindows`) plus the X server
(Xwayland or termux-x11); **launcher** = `com.termux`. Memory is PSS at rest after 60 s idle
(`dumpsys meminfo` for Android processes, `smaps_rollup` for Linux ones). CPU is % of one core,
averaged over 10–13 s of `top` while the action runs. Single runs, no repeats: read differences of
a few points as noise.

## Memory at rest (MB)

| Set-up | Desktop | Host | Launcher |
|---|---|---|---|
| XFCE on X11 Display | 175 | 91 | 229 |
| XFCE under Wayland Display | 130 | 68 | 209 |
| sway | 39 | 37 | 213 |
| labwc | 31 | 58¹ | 155 |

With Firefox open, the desktop group grows to 748 MB (XFCE X11), 930 MB (XFCE Wayland),
730–890 MB (sway) and 818 MB (labwc).

¹ 91 MB as measured; about 33 MB of it was a stray `termux-x11 :0` left from the X11 run.

## CPU during each action (% of one core: desktop / host / launcher)

| Action | XFCE X11 | XFCE Wayland | sway | labwc |
|---|---|---|---|---|
| Idle | 0.2 / 14.0 / 4.2 | 0.2 / 0.9 / 3.8 | 0.7 / 3.1 / 20.3 | 0.0 / 18.0² / 2.3 |
| Open a terminal | 6.6 / 18.6 / 12.9 | 7.9 / 3.9 / 11.6 | 4.3 / 3.6 / 25.8 | 2.2 / 17.2² / 4.4 |
| Scroll a long file by swipe | no scroll³ | no scroll³ | 0.4 / 2.6 / 20.4 | 5.2 / 11.6 / 62.5 |
| Drag a window | no move³ | no move³ | 0.5 / 2.8 / 21.2 (floating) | 0.9 / 19.4 / 9.8 |
| Load example.org | 9.3 / 13.9 / 3.3 | 55.3 / 23.8 / 27.7 | 5.6 / 3.2 / 23.5 | 77 / 59 / 63 |
| Load a Wikipedia article | 39.2 / 12.9 / 5.2 | 57.9 / 12.7 / 18.3 | not valid⁴ | 112 / 41 / 55 |

² Includes the stray `termux-x11 :0` (about 14% idle).
³ Under XFCE (both displays) swipes neither scroll `less` nor drag a title bar, with the touch row
on "Follow display setting (Touch)". They work under sway and labwc.
⁴ The measuring scan slowed under Firefox; only post-load figures were caught.

## Touch and keys

- sway: taps act where the finger is; a floating window follows a drag; swipe scrolls.
- labwc: a tap opens the root menu where the finger is and its Terminal item opens foot; swipe
  scrolls; a title-bar drag moves the window about 340 px with the finger. `adb input text` does not
  reach Firefox under labwc (Ctrl+L does).
- Super key: not checked (no hardware keyboard attached; the launcher keyboard's Super+Enter was not
  driven).
- Scrollbar drag (research-note check 7): inconclusive under sway (the overlay scrollbar is
  invisible), not run under labwc.

## Also seen

- XFCE under Wayland Display came up blank on its first start twice this day (session, xfwm4,
  panel, xfdesktop and Xwayland all alive; nothing in the logs); a second start drew.
- With sway idle the launcher used about 20% of a core, against 2–4% with XFCE or labwc idle. Not
  investigated.

## Against the admission rule

The rule (#411): sway and labwc join the list if they use no more CPU and memory than XFCE under
Wayland Display, touch and the Super key work, and the developer finds them as smooth by eye.

- Memory: both use far less than XFCE under Wayland Display (desktop 31–39 MB against 130 MB).
- CPU: sway's own processes stay under 5% in every action; labwc's rise above XFCE's on page loads
  (77–112% against 55–58%). The launcher's 20% beside idle sway is the open question.
- Touch: works in both, better than XFCE. Super key: owed.
- By eye: owed to the developer.
