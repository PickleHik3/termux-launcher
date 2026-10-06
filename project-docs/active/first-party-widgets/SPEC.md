# First-party widgets

Status 2026-10-06: delivered on `feat/first-party-widgets` (all twelve widgets at five spans,
both directions), Waydroid-checked; awaiting the developer's review and the merge to `dev`.

Twelve widgets drawn by the launcher itself on the Widgets page, beside the app widgets the page
already hosts. They are the design handoff of 2026-10-06 (`design-handoff.html` beside this file:
twelve widgets at five spans, two directions, solid and glass boards), implemented as in-process
views rather than app widgets, so they follow the terminal scheme, the Look and the page's glass
without a provider round trip.

## Catalogue

| Kind id        | Label         | Default span | Data                                                                |
|----------------|---------------|--------------|---------------------------------------------------------------------|
| clock.analog   | Analog clock  | 1×1          | minute tick; world zones from settings                              |
| clock.digital  | Digital clock | 2×1          | minute tick; sunrise/sunset from the weather cache; day/year bars   |
| agenda         | Agenda        | 2×1          | `CalendarContract` events (needs `READ_CALENDAR`)                   |
| weather        | Weather       | 2×1          | `WeatherController` (keyless Open-Meteo, shared)                    |
| battery        | Battery       | 1×1          | `ACTION_BATTERY_CHANGED` + a persisted 24-hour ring                 |
| system         | System        | 2×1          | `SystemStatsController` (shared sampler)                            |
| media          | Media         | 2×1          | `TopPaneFeed` media state and transport controls                    |
| notifications  | Notifications | 2×2          | `LauncherCtlNotificationStore` history + notification access state  |
| tasks          | Tasks         | 2×2          | a markdown checklist file (default `~/notes/tasks.md`)              |
| notes          | Scratchpad    | 2×2          | a markdown file (default `~/notes/scratch.md`)                      |
| shell          | Command       | 2×1          | a command run through the login shell on an interval                |
| calendar.month | Calendar      | 2×2          | month grid; event dots from `CalendarContract`                      |

Every kind resizes to any span the grid allows. A span is drawn at the nearest of the five
designed buckets (`BuiltinWidgetSpan.forCells`): 1×1, 2×1, 2×2, 4×1, 4×2 — the platform's own
"size buckets" advice for app widgets, applied to ours.

## Directions

One setting for all of them, Settings → Apps → *Built-in widget style*:

- **Tonal** (default): Material containers, 24 dp corners, sans captions, light mono numerals,
  pill bars, filled clock face.
- **Pane**: dressed like a terminal pane — 12 dp corners, hairline rim, mono path captions led
  by a Nerd glyph, medium numerals, 1 dp bars, outlined face.

On a glass page (the terminal panes' glass is on) the card is the surface container at 62 % /
55 % with a light rim; on a solid page it is the container itself. All colours are scheme roles
read through `M3`, so Material You, the terminal scheme and the scheme-chrome override all apply.
`termux_place_display` is the design's warm accent; `termux_chip_done` its "done" green.

## Where it lives

- `app/launcher/widget/builtin/` — `BuiltinWidgetKind`, `BuiltinWidgetSpan`, `BuiltinWidgetStyle`
  (resolved tokens), `BuiltinWidgetUi` (text, glyph, bar, row, column helpers),
  `BuiltinWidgetView` (base: owns its `android:id/background` outline, rebuilds per span/config,
  start/stop brackets), `BuiltinWidgetServices` (shared tick, weather, stats, io executor,
  activity host), `BuiltinWidgetHost` (grid factory, picker source, preview renderer),
  `BuiltinWidgetConfigSheet`, and one `*WidgetView` per kind.
- `LauncherWidgetRecord.builtin(...)`: negative ids from `LauncherWidgetRepository.allocateBuiltinId()`,
  synthetic provider `com.termux.launcher.builtin/<kind>`, options bundle = the widget's settings.
  The store writes schema version 5 only while a built-in exists (v4 otherwise), so an older build
  goes read-only on a wall it cannot represent rather than dropping half of it.
- `LauncherWidgetHostController.addBuiltin / updateBuiltinConfig`; reconcile and the lost-wall
  check skip built-ins; remove is a plain record removal.
- Picker: `WidgetProviderCatalogLoader.BuiltinSource` lists the launcher as the first app group;
  cards are rendered views at real cell size (88×92 dp cells, 8 dp gaps) shrunk to the card.
- Edit mode: built-ins resize on both axes to 1×1 minimum; the cog opens the config sheet.

## Rules every widget keeps

- One layout per span bucket in `onBuild`; nothing per frame; no continuously running animation.
- Live data only between `onStart` and `onStop`; previews (`isPreview()`) subscribe to nothing and
  show sample values where there is no data.
- Text in sp, single line, ellipsised. Controls are at least 48 dp touch targets, or padded to it.
- Every root has a content description that says the widget's state, not just its name.
- Taps: a widget body tap opens the thing it shows (clock app, calendar, battery settings...).
  Horizontal swipes belong to the page (the platform's rule for widgets); only taps and vertical
  scrolls are consumed.
- Permission and access gates render their own "allow" state inside the card and route through
  `BuiltinWidgetServices.Host`; nothing shows a system screen without the user tapping.
- Strings in `res/values/strings_builtin_widgets.xml`; no mechanism in user-facing copy.
