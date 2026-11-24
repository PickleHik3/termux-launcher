# Launcher screens: one host for Back, Home and drafts (2026-11-24)

The layout editor (spec #326) and Overview (`overview-2026-11-24.md`) are two screens on one host:
the registry of everything open over the page (`chrome/OverlayRegistry`). The host owns Back, the
home press, the question about unsaved changes, and how things leave. Decided with the developer
on 2026-11-24; the rule for the home press is [ADR 0034](../../docs/adr/0034-a-home-press-clears-then-goes-home.md).

## The problem

- **A home press shows things in the wrong order.** From a Settings page two or three deep, a home
  press shows that page once before Home appears. Settings is started inside the launcher's task
  (no `FLAG_ACTIVITY_NEW_TASK` at its 35 launch sites, e.g. `TermuxActivity.openSettingsHome`), so a
  home press clears it off the top of that task (`TermuxActivity` is `singleTask`,
  `clearTaskOnLaunch`) while its last frame is still on screen. Read from the code; not yet
  confirmed on pong.
- **Every surface leaves its own way.** `OverlayRegistry.closeAll(HOME)` drops each open surface
  with no motion, innermost first, and `onNewIntent` then sends the wall to the home page
  regardless (`TermuxActivity.java`, the `isLauncherHomeIntent` branch). The Appearance editor's
  Home close asks about unsaved changes (`requestExit`), but as written the page switch runs
  under that question.
- **Edit pages skips the window layers.** `PageOverviewView` is added to the content view, not
  to a layer through `WindowLayers`.

## Decisions

### One host

`OverlayRegistry` grows into the host; there is no second stack beside it. Every surface it
already lists (page-bar popup, setup, help, Edit pages, dictation card, rename chip, find,
the Home and terminal actions sheets, command palette, terminal sheet, drawer, Appearance editor,
typed-character palette) and the two new screens go through it.

### The home press

1. **Nothing open:** as today. In front, the wall goes to the home page; from another app, the
   launcher comes back to the page that was left. With Overview's home-press setting on (Overview
   spec, way 4), a press while resting on the home page opens Overview.
2. **Something open that holds unsaved changes** (today only the layout editor): its question
   shows first, over that screen. Nothing else closes and the page does not change while it is up.
   Keep editing leaves everything as it was; Save or Discard continues to step 3.
3. **Something open:** everything open leaves together, in one **Home exit**: the open surfaces
   fade and shrink slightly as one group while the page underneath settles into place, about
   250 ms, the way Pixel-style launchers leave overview and edit mode. Nothing in between is
   drawn. The press lands on the page underneath, the one the screen was opened from; the wall
   does not page.
4. Back and ✕ keep each screen's own steps (a sub-card, a menu, the sheet's height, then the
   screen). A return from another app (`STOP`) and a rotation drop everything at once, as today.

The Home exit moves views by alpha and scale only, never lays out, and is the only writer of those
properties while it runs (ADR 0028). Reduced motion: no tween, the end state at once. Fail-safe: the
end state is applied by a backstop if the animation is cancelled or never gets a frame, so a home
press can never leave a surface half-faded over the home screen.

### Two screens, one purpose each

- **Overview** manages pages and sessions; it acts at once, with Undo for removals (its spec).
- **The layout editor** changes look and layout; it commits only on Done (already decided).
- No link between them: the editor's Pages link is dropped. The editor keeps its way to the
  wallpaper (#336: a wallpaper button in the editor bar and a Wallpaper row on the Global card).

### Look

Both screens are sheets under ADR 0031: they wear Global's look with the sheets' opacity floor,
so they are glass when Global is glass and solid material when it is not. No new setting.

Overview sits on one frosted wallpaper ground (the ground frost view from #316). Its page cards are
tinted panes on that ground, sampling nothing of their own, so scrolling the strip costs nothing
per card; the header, the + card and the menus are glass pieces.

### Settings

Settings opens in its own task (it already declares `${TERMUX_PACKAGE_NAME}.settings` as its
affinity, and `excludeFromRecents`), so a home press leaves it behind instead of tearing it down on
screen. Opened again, it comes back where it was left (it already saves its screen stack in
`onStop`). A door that names a page (`createFragmentIntent`) still lands on that page. One launch
helper sets the flags, so the 35 sites cannot drift.

### Marks

#331's two-cards mark at the left end of the Marks line becomes one more way into Overview and
moves into the Overview work. #332 is closed in favour of #350 (the + card) and #351 (hold menus).

## The host's contract (proposed)

- `Overlay` gains: whether it holds unsaved changes, a way to ask before leaving (continue or
  stay), and the views that leave in the Home exit. Defaults keep today's behaviour for a surface
  that has not said.
- `HomeExitPolicy` (pure, tested): from what is open, the steps of a press: ask a surface, leave as
  a group, or go to the home page / open Overview. `HomePress` stays the answer to "in front".
- One animator runs the Home exit over the group's views; at its end each surface's
  `closeImmediately(HOME)` resets its own views. The backstop calls the same end.

## Changes to the two specs

- **#326 (layout editor):** add that the editor is a screen on this host: a home press asks about the
  draft first, then leaves in the Home exit and stays on the page it was opened from. Its containers
  wear Global like sheets. No editor ticket is blocked on the host; the editor already registers.
- **Overview spec:**
  - Ways in: drop "the Pages link in the Appearance editor"; add the Marks line's two-cards mark
    (from #331).
  - Ways out: a home press is the Home exit and stays on the page Overview was opened from; when
    Overview was opened by a home press on the home page, the second press is the same exit.
  - Look: the frosted ground, tinted cards and glass header described above.
  - Building it: Overview registers with `OverlayRegistry` and joins the pages window layer through
    `WindowLayers`.

## Order of work

`HomeExitPolicy` and the ask-first rule → the Home exit with the editor and the drawer as its first
two members → every other registered surface joins it → Overview and the editor sessions build on
it. Settings' own task is independent and can go first.

## Surfaces to check

Docked and Floating; keyboard up and down (the in-app keyboard and the system IME open during a
home press); one pane and many; light, dark, black, Material You and scheme chrome; Global glass
and Solid; reduced motion; split screen; a home press from each registered surface and from
Settings one, two and three screens deep. Editions: the Settings affinity is per edition already
(`${TERMUX_PACKAGE_NAME}`); nothing else is edition-specific.
