# Home widget redesign — research

**Date:** 2026-10-21 · **Base:** `v2-redesign` at `1bfba4cd9` · **Status:** proposal, nothing in
the app changed.

The question: what should the launcher's own Home widgets become so that they look good, look like
one family, and each tell you something worth a glance, measured against what Google and Apple
publish as widget practice in late 2026? And should the status bar's clock faces become a placeable
clock widget, as an extension of today's clock widgets, a replacement, or as part of a wider
redesign?

Scope: the twelve built-in widgets (`launcher/widget/builtin/`), how they sit beside hosted app
widgets, and the status bar's six clock faces. The terminal look is not a constraint. Live
wallpapers are out (removed 2026-10-06, never to return). Glass may cost battery but never frames,
and any new glass term arrives as an opt-in knob. Widgets keep following the Base Look for glass and
corners (decided 2026-10-19).

Every external claim cites its primary source. Section 10 lists the sources, and section 9 lists
every claim I could not confirm from one. Code references are paths under
`app/src/main/java/com/termux/app/` unless shown otherwise. **Proposal** marks this note's own
suggestions.

## 1. Summary

1. **One family, one set of tokens.** All built-ins share one card. Its corner is the Look's
   Corners. Its content inset is 16 dp, or 12 dp on one-row spans, and anything nested inside uses
   the outer radius minus the inset. Five type roles cover every widget, with 11 sp as the floor.
   Colors come from Material roles plus four semantic roles. Today the secondary text is 10.5 sp
   mono nearly everywhere, the calendar's letters are 9.5 sp, and each widget picks its own sizes
   (§2.3).
2. **Glass that guarantees legibility.** A widget is content, not navigation, so its glass should be
   the thick, more opaque kind. Its ink and veil should be measured by the same `ChromeInk` and
   `OnGlass` engine that already holds the dock and the bars to 4.5:1. Today the widgets are not a
   band in that engine. When contrast settings ask for it, or blur is off, a widget falls back to
   a solid card.
3. **Fewer, better widgets.** Of the 12 kinds, 9 remain. The two clocks become one **Clock**, Agenda
   and Calendar become one **Calendar**, and Battery and System become one **Device**. Weather,
   Media, Notifications, Tasks and Notes are reworked, and Terminal stays as it is. Every stored
   kind id keeps loading.
4. **Three new widgets.** **Glance** is a 4×1 row of date, next event, weather, the next alarm and
   battery when low. **Sessions** shows your terminal sessions and running jobs. **Command** shows a
   shell command's output; the spec planned it as `shell` but it was never built.
5. **Clock: replace, as phase one of the redesign.** One Clock widget offers the six status bar
   faces plus Analog and today's Numerals, picked in its settings and scaled to the span. All of
   them draw through one face renderer taken out of `TerminalClockWidget`, so the bar and the widget
   cannot drift apart. `clock.digital` and `clock.analog` records open as the Clock with the
   matching face. See §7.

## 2. Current state

### 2.1 On the device

*Pending: the emulator capture of every current widget is in progress. This section will reference
the screenshots under
`https://amal-build.taild9c5dc.ts.net/reports/widgets-research/screens/` once the findings are
in.*

### 2.2 Inventory, from the code

| Kind id | Default span | What it shows (by span) | Source |
|---|---|---|---|
| `clock.analog` | 1×1 | A face with a second hand, plus up to two more zones (defaults London and Kolkata) at the wide spans. | `AnalogClockWidgetView` |
| `clock.digital` | 2×1 | Time and date. Day and year progress at 2×2 and 4×2, sunrise and sunset at 4×1, seconds at 4×2. | `DigitalClockWidgetView` |
| `agenda` | 2×1 | Today's date, the count of today's events and the next few. | `AgendaWidgetView` |
| `calendar.month` | 2×2 | A date band at 1×1, the week at 2×1, the month at 2×2, a fortnight at 4×1, and month plus events at 4×2. | `CalendarWidgetView` |
| `weather` | 2×1 | Now at 1×1, the next hours from 2×2, the coming days at 4×2. A tap refetches. | `WeatherWidgetView` |
| `battery` | 1×1 | A ring, then level and charger state, then temperature and power draw, and a 24 h chart at 4×2. | `BatteryWidgetView` |
| `system` | 2×1 | CPU (only with Shizuku or su), RAM, temperature and storage, and a 32-sample CPU history at 4×2. | `SystemWidgetView` |
| `media` | 2×1 | A play button over the art at 1×1, up to the full transport at 4×2. | `MediaWidgetView` |
| `notifications` | 2×2 | A bell with a count, the newest notification, or a short list. | `NotificationsWidgetView` |
| `tasks` | 2×2 | A markdown checklist (`~/notes/tasks.md`) with checkboxes and an add button. | `TasksWidgetView` |
| `notes` | 2×2 | A markdown scratchpad (`~/notes/scratch.md`) in mono, which switches between files when wide. | `NotesWidgetView` |
| `terminal` | 4×2 | A live shell typed into in place, sized in rows and columns by the widget. | `TerminalWidgetView` |

Kinds and default spans come from `launcher/widget/builtin/BuiltinWidgetKind.java:15-26`. The
spec's `shell` "Command" kind (`project-docs/reference/launcher/first-party-widgets-SPEC.md`) was
never built, and `terminal` stands in its catalogue slot.

- **Rendering.** The built-ins are in-process Android Views and Canvas, not `RemoteViews`. They draw
  one layout per bucket, and the buckets are 1×1, 2×1, 2×2, 4×1 and 4×2
  (`BuiltinWidgetSpan`, after the handheld size ranges in [A-SIZE]). A span between buckets draws the
  smaller bucket's layout.
- **Grid.** The default is 4 columns × 5 rows. Cells are separated by an 8 dp gap
  (`launcher/widget/WidgetGridMetrics.java:15`), and the picker renders at a reference cell of
  88×92 dp. At that cell size a 2×1 is about 184×92 dp and a 4×2 about 376×192 dp.
- **Two directions.** Settings has a *Built-in widget style* choice. **Tonal** uses Material
  containers, sans captions and light mono numerals. **Pane** uses a hairline rim, mono captions led
  by a Nerd glyph and medium numerals (`BuiltinWidgetStyle.java:19-38`).
- **Corners.** Every built-in card is cut at the Look's Corners, Base's default being 24 dp
  (`BuiltinWidgetStyle.lookCornerRadiusPx`). Hosted app widgets are clipped at
  `system_app_widget_background_radius` instead, or 16 dp before Android 12
  (`launcher/widget/WidgetCellView.java:204-216`, `WidgetCornerPolicy`). Side by side, the two
  kinds can therefore show different corners.
- **Glass.** Each widget has its own *Glass* switch, off by default (`BuiltinWidgetGlass`). When it
  is on and the launcher sits on the wallpaper, the widget wears the Look's slab: Base blur,
  opacity, tint, grain, rim and Fancier Glass. The card underneath uses fixed alphas, surface
  container at 55 % dark or 62 % light with a white rim at 12 % or 55 %
  (`BuiltinWidgetStyle.java:103-110`).
- **No measured contrast on widgets.** `launcher/widget/` does not use `ChromeInk` or `OnGlass`
  (the only hit is `HomeFolderPopup`), and `GlassBackdropCache.Band` has no widget band. Ink on a
  widget's glass is never checked against what is behind it, although the dock and bars are held
  to 4.5:1 (`chrome/OnGlass.java`, `chrome/LegibilityLevel.java:20-23`).

### 2.3 Type

The app ships no text faces, only `SymbolsNerdFontMono.ttf` for glyphs. Text uses the system sans
and the terminal's mono (`BuiltinWidgetStyle.java:121-134`), with sizes set per widget:

- 10.5 sp mono is the caption size in Battery, Agenda, Analog, Digital, Media, Notifications, Tasks,
  Notes, System, Weather and the calendar support code (`grep '10.5f' launcher/widget/builtin`).
- The calendar's weekday letters are 9.5 sp (`CalendarMonthGridView.java:57`,
  `CalendarWeekStripView.java:56`).

Both sizes are below the 11 pt floor Apple sets for widget text and below Material's smallest
label, Label Small at 11 sp (§3).

### 2.4 The status bar clock faces

`terminal/TerminalClockWidget.java` (2,441 lines) draws six faces, selected by
`top_pane_clock_style` (`termux-shared/.../TermuxPreferenceConstants.java:1387-1396`, default
`slab`):

- **Flip:** split-flap cards with hinge clips, in the primary container pair.
- **LCD:** seven segments with a boxed period.
- **Minimal:** plain numerals.
- **LED matrix:** dot glyphs, with dots for the seconds.
- **Tape:** numerals over a minute track.
- **Slab:** heavy numerals with the seconds and period stacked.

Each face has FULL and COMPACT forms plus a mono chip (`statusbar/TopPaneClockForm`). The class is
already close to a widget:

- It uses Material roles rather than literal colors.
- It fits one uniform scale to a width and height budget, and drops the date row before it shrinks
  past a floor (`setFitBudget` and `fitPlan`, lines 940-1000).

What ties it to the status bar:

- `onMeasure` defaults to the bar slot's height (`SLOT_HEIGHT_DP`, line 71).
- The date row ends in a hairline aligned to the status row below it.
- It reports its painted width so the slot can lay out its neighbours.

It shares no code with `DigitalClockWidgetView` or `AnalogClockWidgetView`, which have their own
numerals (`ClockNumeralView`).

## 3. What the 2026 standards say

### 3.1 Android widget design and quality guidelines

- **Purpose.** Pick a single primary use case per widget. Widgets either act on content in place
  or open the app [A-OVR].
- **Fill the cell.** A widget's container stretches edge to edge at every size, without custom
  padding or fixed squares. Non-rectangular shapes touch the grid on one axis [A-SIZE].
  - Quality tier 3 (low quality) is a widget that doesn't fill its bounds (WL-1).
  - Tier 1 (differentiated) requires rectangular widgets to touch all four edges (WL-1.1)
    [A-QUAL].
- **Sizes.** The handheld ranges run from a 2×1 at 109–306 × 56–130 dp to a 4×3 at 245–624 ×
  185–422 dp, based on Pixels [A-SIZE]. Breakpoints add or drop supplemental content [A-SIZE].
  The quality tiers add:
  - Tier 1: resizable to at least one of 2×2, 4×1 or 4×2 (WL-2).
  - Tier 2: a sensible min and max size, with "set a max size if resizing only adds blank space"
    (WL-4.1), and the minimum still meeting the 48×48 dp touch target (WL-4.2) [A-QUAL].
- **Corners.** `system_app_widget_background_radius` is "never larger than 28 dp", and the inner
  radius is `systemRadiusValue − widgetPadding` [A-GLANCE-RC]. Tier 1 requires the system radius
  for rectangular widgets (WS-2) [A-QUAL].
  - Shapes: minimal-content widgets (photo, weather, the current song) can be one expressive
    shape. Complex widgets can use shape for hierarchy [A-STYLE].
- **Color.** Use Material color roles so contrast, dynamic color and light/dark all hold.
  - Use accent roles for vibrant contrast, and color for new content [A-STYLE].
  - Tier 1 requires theming from device or app context and both light and dark palettes
    (WC-1, WC-2) [A-QUAL].
  - Insufficient contrast on text and icon buttons is tier 3 (WC-3) [A-QUAL].
  - A widget that ignores dark theme "will appear out of place" [A-STYLE].
- **Type.** There are five roles: display, headline, title, subtitle and body. Use the system font.
  For expressive styling use "bolder headlines, labels, and data, and dramatic type scales", and
  watch spacing in small text [A-STYLE].
- **Content quality.**
  - Empty and zero states must be intentional (WT-1).
  - A manual refresh is needed when data changes faster than the UI (WT-2).
  - Consistently stale content is tier 3 (WT-3).
  - Use a header when the widget scrolls or the header adds context. If there is a header, its
    icon is always present and its title appears when there's room (WL-3) [A-QUAL].
- **Canonical layouts.**
  - Text, toolbars, lists (text-and-image, checklist, action list) and grids [A-LAYOUT].
  - An Android 17+ "full bleed image" list snap-scrolls one item per widget height. It needs
    Glance 1.3 alpha, compile SDK 37 and AGP 9.1 [A-LAYOUT]. Glance 1.3.0-alpha01 (2026-05-19)
    "add[s] snap scrolling support when remote compose is used" [A-GLANCE-REL].
- **Previews.** Android 15+ has generated previews, rate-limited to about two
  `setWidgetPreview` calls an hour [A-GENPREV]. Android 16 adds engagement metrics:
  `queryAppWidgetEvents`, taps, scrolls and impressions [A-METRICS].

The built-ins are not app widgets, so the platform hooks don't apply to them: picker previews,
configure activities, launch transitions and metrics. The design rules do apply. They are what
every other widget on the page is held to, so they set the bar the built-ins sit next to.

### 3.2 Material 3 and M3 Expressive

- **Shape scale** [M3-SHAPE]:

  | Token | Corner |
  |---|---|
  | None | 0 |
  | ExtraSmall | 4 dp |
  | Small | 8 dp |
  | Medium | 12 dp |
  | Large | 16 dp |
  | LargeIncreased | 20 dp |
  | ExtraLarge | 28 dp |
  | ExtraLargeIncreased | 32 dp |
  | ExtraExtraLarge | 48 dp |
  | Full | 50 % |

- **Type scale** [M3-TYPE]:

  | Role | Large | Medium | Small |
  |---|---|---|---|
  | Display | 57 sp | 45 sp | 36 sp |
  | Headline | 32 sp | 28 sp | 24 sp |
  | Title | 22 sp | 16 sp (medium) | 14 sp (medium) |
  | Body | 16 sp | 14 sp | 12 sp |
  | Label | 14 sp | 12 sp | 11 sp (all medium) |

  An **emphasized** scale shifts each role one weight up: medium for display, headline and body,
  bold for title medium and small and for label [M3-TYPE].

- **Color roles.**
  - Five surface containers, Lowest to Highest, plus Dim and Bright, are the default container
    colors.
  - Fixed roles (`primaryFixed` and the others) keep the same value in light and dark.
  - With dynamic color on Android 14+, "Material color roles enforce contrast through tone".
    Medium and high contrast overlays exist for custom themes [M3-COLOR].
- **Expressive research** [GD-EXPR]: 46 studies with more than 18,000 participants.
  - People spotted key elements "up to four times faster".
  - Users over 45 matched younger users' fixation times.
  - The team exceeded standard tap-target sizes and contrast.
  - Breaking familiar patterns, such as dropping labels, reduced usability, and "a strong minority
    preferred calmer designs".
  - Emphasis comes from size, color, shape, motion and containment.
- **Android 16 with M3 Expressive** (May 2025): the shade background is "subtly blur[red] … to
  provide a sense of depth" [BG-EXPR].
- **Android 17 and blur.** Android 17 reportedly widens translucency, with the volume panel and
  power menu translucent and blur tinted by dynamic color. It is also said to give the widget
  picker blur, but this rests on press coverage of betas only (§9).
- **Android 17 semantic colors.** Live Updates get semantic colors with a fixed meaning: green for
  safe, orange for caution, red for danger, blue for informational [A17-FEAT].
- **Android 16 outline text.** Outline text replaces high-contrast text. It draws a larger
  contrasting area around text, and `AccessibilityManager` APIs report the mode [A16-FEAT].

### 3.3 Apple: WidgetKit HIG and Liquid Glass

Widgets HIG [HIG-WIDGETS], change log updated 2026-10-13:

- **What to show.**
  - "Choose simple ideas that relate to your app's main purpose", and prefer information that
    changes through the day.
  - Larger sizes add layers of information. Avoid "expanding a smaller widget's content to simply
    fill a larger area".
  - "Balance information density": sparse looks pointless, dense isn't glanceable.
  - Calendar widgets stay "centered on events" at every size.
- **Margins.** 16 pt standard, 11 pt for tighter groupings. Content corners follow the widget's
  corner (concentric, `ContainerRelativeShape`).
- **Text.** Use the system font. A custom font works "for the large text … and SF Pro for the
  smaller text". Text should be 11 pt or larger, and never rasterized.
- **Color.** Never carry meaning by color alone. Keep full-color images for media, such as album
  art, and smaller than the widget.
- **Appearances** (iOS 26 and later):
  - People choose light, dark, **clear** or **tinted**. In clear mode "the system desaturates the
    widget and adds translucency, highlights, and the Liquid Glass material".
  - Accented rendering splits a widget into an accent group and a primary group and paints each a
    solid color.
  - StandBy removes the background and scales the widget up.
  - On rendering modes, the WWDC25 session says to "use either desaturated or accented desaturated"
    for images and fullColor for media [WWDC-WIDGETS].
- **Freshness.** If people check more often than the widget can update, show when the data was last
  updated. Let the system tick dates and times. Animate updates for up to two seconds.
- **Previews and placeholders.**
  - Placeholders are static parts plus semi-opaque shapes where content will go.
  - Descriptions begin with an action verb.
  - All sizes of one widget are grouped under one description.

Materials HIG [HIG-MATERIALS] and WWDC25 "Meet Liquid Glass" [WWDC-GLASS]:

- **Liquid Glass is the navigation and controls layer.** "Don't use Liquid Glass in the content
  layer". Use standard materials there, "thicker … more opaque" ones for text with fine features.
- **Regular and clear variants.** Regular blurs and adjusts luminosity for legibility, and suits
  components with "a significant amount of text". Clear is for media backgrounds and needs "a dark
  dimming layer of 35% opacity" over bright content.
- **No glass on glass.** "Always avoid glass on glass": elements on glass use fills, transparency
  and vibrancy instead [WWDC-GLASS].
- **Accessibility settings.** Reduce Transparency makes glass "frostier", Increase Contrast makes
  elements "predominantly black or white" with a border, and Reduced Motion disables the elastic
  effects [WWDC-GLASS].
- **Contrast targets.** The accessibility HIG uses WCAG AA: 4.5:1 up to 17 pt, 3:1 at 18 pt or bold
  [HIG-A11Y].

### 3.4 Contrast over arbitrary wallpapers (WCAG 2.2)

- **Text** (1.4.3): 4.5:1, or 3:1 for large text, which is 18 pt (about 24 px) or 14 pt bold (about
  18.5 px). The thresholds are strict, so 4.499:1 fails. Text over background images that lack
  contrast is failure F83 [WCAG-143].
- **Graphics and controls** (1.4.11): 3:1 against adjacent colors for control boundaries and for
  "parts of graphics required to understand the content". On a gradient, test the least-contrasting
  area [WCAG-1411].

A wallpaper is an unknown, user-chosen background. Glass or no glass, every widget text color has to
be checked against the worst row behind it, not against the card color it was authored for. That is
the same lesson `OnGlass` records for the bars in light mode, which measured 1.02:1 on the
reporting device (`chrome/OnGlass.java:21-25`).

### 3.5 Blur cost and fallback

Android's window-blur guidance [AOSP-BLUR]:

- Blur is "expensive", supported "only on selected devices", and turned off at runtime in battery
  saver.
- Keep a second, more opaque background for when blur is off.
- Avoid radii above 150 px.

The launcher's glass samples its own pre-blurred shared frame rather than cross-window blur
(ADR 0004), so blur adds no per-frame cost. The fallback advice still applies whenever the frame
is not available.

## 4. Design principles for the redesign (proposal)

### 4.1 Shell: one card for every built-in

| Token | Value | Why |
|---|---|---|
| Card bounds | Fills its cells, less the grid gap | [A-SIZE], WL-1.1 [A-QUAL] |
| Outer corner | The Look's Corners (Base, default 24 dp) | The 10-19 decision; the 24 dp default is under the platform's 28 dp maximum [A-GLANCE-RC] |
| Content inset | 16 dp; 12 dp on one-row spans and at 1×1 | Apple's 16 and 11 pt [HIG-WIDGETS]; a 92 dp row leaves 68 dp at 12 dp |
| Inner radius | `max(0, outer − inset)` for chips, art and progress tracks | [A-GLANCE-RC], [HIG-WIDGETS] concentric |
| Element gap | 4, 8 and 12 dp only | One rhythm across widgets |
| Touch target | 48 dp, or padded to it | WL-4.2 [A-QUAL], already in the spec |
| Header | A glyph and title row only on list widgets (Calendar, Tasks, Notifications, Notes, Sessions) | WL-3 [A-QUAL] |

- **Hosted app widgets.** They should also be clipped at the Look's Corners, capped at the system
  radius, so built-in and app widgets match where they can. A provider that draws its own rounded
  background keeps its own corners, as Launcher3 does (`WidgetCornerPolicy`).
- **Direction.** Drop Tonal versus Pane as a whole-family direction. It doubles every design and
  device check: 12 kinds × 5 buckets × 2 directions × glass on/off is 240 states. Keep a single
  **Numerals: Sans / Mono** choice instead, and let the rim follow the glass. See Q2.

### 4.2 Type: five roles, one scale

| Role | Size | Use |
|---|---|---|
| Hero | Display, fit to the bucket (36–57 sp, larger only by fit), emphasized weight | Time, temperature, battery %, a count |
| Headline | 24 sp | The hero on 1×1 and one-row spans |
| Title | 16 sp medium | Event name, track title, task text at 2×2 and up |
| Body | 14 sp | List rows, notes text |
| Label | 12 sp medium; 11 sp only where space forces it, **never less** | Captions, times, metadata |

Sizes are M3's [M3-TYPE]. The emphasized weights follow Android's "bolder … data" advice
[A-STYLE].

- **Faces.** Use the system sans for words. Numerals take tabular figures through
  `Paint.setFontFeatureSettings("tnum")` (API 21 [A-PAINT]), so a ticking value does not shift
  width. The terminal mono becomes an opt-in numeral face, and stays the face of Notes' file text.
- **Migration.** Every 10.5 sp caption becomes Label 12 sp, and the calendar letters move from
  9.5 sp to 11 sp.

### 4.3 Color

| Role | Use |
|---|---|
| `surfaceContainer` | The solid card |
| `surfaceContainerHigh` | Nested tracks and chips (a fill, never a second glass) |
| `onSurface` | Hero and title |
| `onSurfaceVariant` | Body and label |
| `primary` | The one accent per widget: the next event, the current hour, the playing state |
| Semantic: safe, caution, danger, info | Battery low or charging, storage full, a failed job, rain. Taken from Android 17's Live Update semantic set [A17-FEAT] and mapped to the existing `termux_chip_done` and `error`. Always paired with a glyph or word [HIG-WIDGETS] |

The warm accent stays, but only for the sun.

### 4.4 Glass

1. **The content layer gets the thick material.** A widget's glass should be frostier and more
   opaque than the dock's, because it carries paragraphs and the dock carries icons
   ([HIG-MATERIALS] content layer, regular for text). **Proposal:** a *Widget body* opacity that
   inherits Base's opacity plus a fixed lift, rather than a new knob.
2. **Measured ink.** Add a `WIDGET` band to `GlassBackdropCache.Band`, one rect per widget like
   `TERMINAL_PANE`. Then `ChromeInk` spends the smallest veil that holds 4.5:1 for body and label,
   and 3:1 for hero numerals at 24 sp and up and for graphics. This uses the existing machinery,
   so nothing per frame is added. Widgets join the page's single ink polarity, so a light-ink
   widget never sits under a dark-ink dock.
3. **No glass on glass.** Chips, tracks and art inside a widget are fills.
4. **Solid fallback.** Draw the solid card instead of glass when any of these hold:
   - The launcher is off the wallpaper.
   - The shared frame isn't ready.
   - Battery saver is on (**proposal**, matching [AOSP-BLUR]).
   - Android 16's outline-text mode is on [A16-FEAT].
   - The system contrast level is high [M3-COLOR].

   This is Apple's Reduce Transparency and Increase Contrast behavior, expressed with Android
   signals.
5. **Glass per page, not per widget.** Today every widget has its own switch, off by default, so a
   page can mix glass and solid cards. **Proposal:** one *Widgets on glass* switch in the Look,
   following Base, with the per-widget switch kept as an override. See Q3.
6. **Clear, as an opt-in knob.** A *Clear* appearance removes the card, like Apple's clear and
   StandBy treatments [HIG-WIDGETS]. Ink sits straight on the wallpaper, measured by the same
   band, with a dim of up to 35 % drawn only where the measurement asks for it [HIG-MATERIALS].
   It is meant for Clock, Glance and Weather. Off by default.

### 4.5 Content rules

- **Each span adds a layer.** A bigger span shows more, not the same thing larger [HIG-WIDGETS].
  Every kind declares a **max span**; growing beyond it only adds blank space (WL-4.1 [A-QUAL]).
  Today every kind resizes to any span.
- **Freshness.** Network or sampled data that can go stale shows its age ("12 min ago") once it is
  older than its refresh interval. A tap refreshes (WT-2, WT-3 [A-QUAL], [HIG-WIDGETS]).
- **Placeholders.** While data loads, the layout draws with semi-opaque shapes where values will
  go, never "--" [HIG-WIDGETS].
- **Updates animate** for at most 2 s [HIG-WIDGETS], and only on change. The existing rule of no
  continuously running animation stays.
- **Picker.** One card per kind with its sizes grouped. The description starts with a verb
  ("See …", "Keep …") [HIG-WIDGETS]. The name appears in the design (WD-4.2 [A-QUAL]).

## 5. Per-widget verdicts (proposal)

| Today | Verdict | Becomes |
|---|---|---|
| Analog clock | **Replace** | A face of **Clock** (§7) |
| Digital clock | **Replace** | A face of **Clock**. Day and year progress becomes an optional Clock detail. Sunrise and sunset move to Weather |
| Agenda | **Merge** | **Calendar**, events first |
| Calendar (month) | **Merge** | **Calendar**, with the month grid as a 2×2 option and beside the events at 4×2 |
| Weather | **Overhaul** | See below |
| Battery | **Merge** | **Device** |
| System | **Merge** | **Device** |
| Media | **Overhaul** | See below |
| Notifications | **Overhaul, narrowed** | See below |
| Tasks | **Keep, restyle** | See below |
| Notes (Scratchpad) | **Keep, restyle** | See below |
| Terminal | **Keep** | Only the shell rules: outer corner and inset. Its text is the terminal's |

Kind ids are durable (`BuiltinWidgetKind` javadoc). A merged kind keeps its id as an alias that
opens the new widget with matching settings, for example `calendar.month` → Calendar with
view=month and `system` → Device with CPU first.

**Clock.** See §7.

**Calendar.** It stays centered on events at every size, as Apple's Calendar widgets do
[HIG-WIDGETS]:

| Span | Shows |
|---|---|
| 1×1 | Weekday and date, with a dot for each event today |
| 2×1 | The next event: title, then time and "in 20 min" |
| 2×2 | The next three events with a header, or the month grid if chosen |
| 4×1 | The next event, then the fortnight strip |
| 4×2 | Month and events |

- The next event takes the primary accent.
- When there are no events, it says "Nothing else today", then tomorrow's first event.
- Calendar access is still asked for on the card.

**Weather.** The temperature is the hero, followed by the condition and its glyph, high and low,
and the precipitation chance.

| Span | Adds |
|---|---|
| 2×1 | Sunrise or sunset, whichever is next (moved from Digital clock) |
| 2×2 and 4×1 | The next hours |
| 4×2 | Coming days, with range bars |

- It shows "Updated 40 min ago" once the 30 min cache TTL has passed
  (`statusbar/WeatherController.java:123`).
- It is a candidate for one expressive shape at 1×1 [A-STYLE].

**Device** (Battery + System).

| Span | Shows |
|---|---|
| 1×1 | The battery ring with %, the ring in a semantic color only when low or charging |
| 2×1 | % plus "Full in 1 h 10 m" from `BatteryManager.computeChargeTimeRemaining()` (API 28, −1 when unknown [A-BATT]), or the discharge rate |
| 2×2 | Battery plus storage free, RAM and temperature |
| 4×2 | Adds the 24 h battery chart, plus CPU history when Shizuku or su is available |

- CPU never appears as a dash. Its row is absent until access exists, as today.

**Media.**

- The art is the one full-color image allowed [HIG-WIDGETS], with its corner at the inner radius.
  Title and artist use Title and Body.
- Transport buttons are 48 dp.
- 1×1 is art with play/pause, as an expressive shape candidate [A-STYLE].
- When nothing is playing it shows "Resume <last app>" rather than an empty deck (WT-1 [A-QUAL]).

**Notifications.** It duplicates the shade, so it earns its place only by summarizing.

- Messages and people come first, grouped by app with counts.
- It drops system and silent notifications, and its minimum span is 2×1.
- 1×1 is removed: a bell with a number is what the status bar already shows.
- See Q5 on removing it entirely.

**Tasks.**

- A header shows the open count.
- Rows are 48 dp checkboxes with text at Title or Body.
- Due tags use the semantic colors.
- It is the Android checklist layout [A-LAYOUT].

**Notes.**

- The file text keeps the mono face at Body 14 sp.
- Headings use Title.
- The age label uses Label.

## 6. New widgets (proposal)

| Widget | Spans | What it adds | Data, already in the app or one call away |
|---|---|---|---|
| **Glance** | 4×1 (also 2×1) | One row for the first look of the day: date · next event · weather now · next alarm · battery when under 20 % or charging. Each part hides when it has nothing to say. The row is a Clear-appearance candidate | `CalendarEventsSource`, `WeatherController`, `AlarmManager.getNextAlarmClock()` (API 21 [A-ALARM]), `BatterySource` |
| **Sessions** | 2×1, 2×2, 4×2 | Open terminal sessions and windows, each with its title, cwd and last line. A job that ended shows its exit status, a failure in the danger color. A tap opens that session. Nothing else on the platform can show this | The session and pane controllers that feed the window chips |
| **Command** | 2×1, 2×2, 4×1 | The output of a shell command the user names (`uptime`, `git -C ~/proj status -sb`, a script), rerun on an interval of at least 15 min (**proposal**) and on tap, with its age shown. This is the spec's never-built `shell` kind | The login shell, as the spec planned (`first-party-widgets-SPEC.md`, the `shell` row) |

Ideas considered and left out:

- **Photo frame.** It carries no information.
- **Screen time.** It needs the usage-access permission, and a hosted app widget already does this
  job. The emulator capture includes one (`appwidget-screentime_*`).
- **World clock as its own kind.** It is a Clock option instead (§7).

## 7. The clock widget recommendation

**Recommendation: replace both clock widgets with one Clock widget as part of the redesign, and
build it first.** Its faces are Analog, Numerals (today's digital), Slab, Flip, LCD, LED matrix,
Tape and Minimal, chosen in the widget's settings with a live preview strip. Every face draws
through one face renderer taken out of `TerminalClockWidget`, so the status bar and the widget share
it.

Why replace rather than extend:

- **Two clocks on one menu is the problem.** Apple groups a widget's sizes under one entry
  [HIG-WIDGETS], and Android wants each widget's name distinct from the app's others (WD-4.5
  [A-QUAL]). If the bar faces were added to the Digital clock, the picker would still carry two
  clocks, and the app would have two digital renderers (`ClockNumeralView` and the bar's) to keep
  in step.
- **The renderer is nearly ready.** `TerminalClockWidget` already scales the FULL face uniformly
  to a width and height budget and drops the date before going under a floor
  (`setFitBudget`, `fitPlan`). That is exactly what a resizable widget needs. The work is to
  separate the drawing (face, snapshot, bounds, palette) from the slot behavior (slot-height
  default, the hairline to the gutter, the reported width, media sharing). The bar then keeps its
  view as a thin host of the same renderer.
- **The clock anchors the family.** It is the widget most people place, and its hero numerals set
  the type scale (§4.2). Doing it first fixes the tokens every other widget then uses.
- **Nothing placed is lost.** `clock.digital` and `clock.analog` records open as Clock with
  face=numerals or face=analog and keep their zones. No stored id changes.

Doing it later, as a stand-alone extension, would mean building the faces at widget size twice:
once in the old style, and again when the tokens land.

What scaling the bar faces up needs:

- **Strokes scale with the band.** Hairlines, the LCD segment thickness and the LED cell are set in
  dp for a time band of about 22–26 dp (`fullBandHeightDp`, inside the bar's 65 dp slot), for
  example `FLIP_SHADOW_*`, `BOX_PAD_DP` and `LED_META_GAP_DP`. At 4×2 (about 192 dp tall, less the
  inset and the date row) the band is roughly 100–120 dp, so each of these becomes a fraction of
  band height, with a 1 dp floor. The uniform fit scale already multiplies them; this needs checking
  on the device, because a hairline scaled 4–5× stops being a hairline.
- **Flip cost.** Flip draws 24 cached hinge clips per frame while a digit turns, and the cache is
  sized to the card (`drawCachedHingeClip`). At widget size the bitmap grows with the card squared.
  The flip should play on the minute only. Seconds default off on Flip and LED at widget size;
  where the user turns them on, they repaint only while visible, as Analog does today.
- **Span layers.**

  | Span | Shows |
  |---|---|
  | 1×1 | Time only (Analog: the face) |
  | 2×1 | Time plus date |
  | 2×2 | Adds the next alarm |
  | 4×1 | Time, date and up to two zones in a row |
  | 4×2 | Adds the zones' offsets and, optionally, day progress |

- **Ink.** The faces already use Material roles, so they take the card's ink. In the Clear
  appearance they take `ChromeInk`'s measured ink like the bar does.
- **Tap.** A tap opens the clock app; a long press does what the grid does today.
- **The bar is a separate setting.** Picking a face for a widget does not change the bar's face.
  Q1 asks whether a widget should follow the bar's face by default.

## 8. Phased implementation sketch (proposal)

Each phase is one branch: it builds, gets emulator checks first, and goes to pong in a batch.
Settings pages are fragile, so each phase's settings footprint stays to one control unless noted.

1. **Tokens and legibility.**
   - Pure classes: `WidgetTokens` (inset, inner radius, gap, type roles) and `WidgetInk` (the
     `WIDGET` band).
   - Restyle every built-in to the type scale. This is mechanical: replace the 10.5 and 9.5 sp
     sizes and drop the Tonal/Pane fork behind the Numerals choice.
   - Add the solid fallbacks.
   - JVM tests for the inset, inner radius and contrast arithmetic.
   - Device: every kind at the five buckets on a light and a dark wallpaper, glass on and off.
2. **Clock.**
   - Take the face renderer out of `TerminalClockWidget`; the bar renders through it, with a
     before/after screenshot check of all six bar faces.
   - Add the Clock kind with the face picker and the id aliases.
   - Retire `DigitalClockWidgetView` and `AnalogClockWidgetView`, after which `ClockNumeralView`
     remains only if Numerals still uses it.
3. **Calendar, Weather, Glance.** Merge Agenda and Calendar, overhaul Weather with freshness, then
   add Glance.
4. **Device, Media, Notifications, Tasks, Notes.** Merge Battery and System, apply the max spans,
   and give the picker its descriptions.
5. **New kinds and knobs.**
   - Sessions.
   - Command, which needs a policy for how long and how often a command runs.
   - The Clear appearance and *Widgets on glass* per page, both opt-in.

## 9. Open questions for the developer

1. Should a new Clock widget start on the bar's current face, or on Numerals?
2. Should Tonal versus Pane be retired for one family with a Sans / Mono numerals choice, or
   should Pane survive as a dress?
3. Should glass be one switch for all widgets, on by default when the Look's glass is on, or stay
   per widget?
4. Merges: are Agenda + Calendar and Battery + System into one widget each acceptable, given that
   existing placements keep working through id aliases?
5. Notifications: narrow it as in §5, or remove it?
6. Command widget: what limits on the interval and run time, and should it run only while Home is
   visible?
7. Hosted app widgets: clip them at the Look's Corners (capped at the system radius) for one
   corner per page, or leave them on the system radius?
8. Should the Clear appearance be offered at all, or should glass and solid be enough?

## 10. Unverified or secondary-only claims

- **Android 17 translucency.** The claims that it covers the volume panel and power menu, is tinted
  by dynamic color, and as of Beta 4 reached only the widget picker come from 9to5Google
  ([2026-01-25](https://9to5google.com/2026/01/25/android-17-blur/),
  [2026-05-19](https://9to5google.com/2026/05/19/android-17-beta-1-blur/)). No developer.android.com
  or AOSP page confirmed them. The Android 17 features page says nothing about blur or widgets
  [A17-FEAT].
- **Remote Compose widgets.** The report that I/O 2026 introduced a "Remote Compose" engine under
  Glance, supported on Android 16+, comes from Android Authority
  ([link](https://androidauthority.com/android-widgets-overhaul-remote-compose-3670122/)). The Glance
  release notes confirm only "remote compose" in 1.3.0 alpha snap scrolling [A-GLANCE-REL].
- **The At a Glance toggle.** The report that Android 17 lets people hide Pixel's At a Glance from
  Home comes from Android Authority
  ([link](https://www.androidauthority.com/android-17-beta-1-remove-at-a-glance-home-screen-3641021/)).
  It is only background for the Glance widget.
- **The M3 Expressive widget pages on m3.material.io** could not be fetched (the site is a script
  app and returned 404 to the fetcher). The M3 shape and type values here come from the
  material-components-android docs on GitHub instead [M3-SHAPE], [M3-TYPE].
- **Units in the Android sizing table.** The handheld size table does not state its units; dp is
  assumed [A-SIZE].
- **The system widget radius on the developer's phones** (pong, the emulator) has not been read off
  either device, so how far it differs from the Look's 24 dp is unknown.
- **The device capture** (§2.1) is pending.

## 11. Sources

All fetched 2026-10-21.

| Key | Source |
|---|---|
| A-OVR | https://developer.android.com/design/ui/mobile/guides/widgets |
| A-STYLE | https://developer.android.com/design/ui/mobile/guides/widgets/style |
| A-SIZE | https://developer.android.com/design/ui/mobile/guides/widgets/sizing |
| A-LAYOUT | https://developer.android.com/design/ui/mobile/guides/widgets/layouts |
| A-QUAL | https://developer.android.com/docs/quality-guidelines/widget-quality |
| A-GLANCE-RC | https://developer.android.com/develop/ui/compose/glance/create-app-widget (Implement rounded corners) |
| A-GLANCE-REL | https://developer.android.com/jetpack/androidx/releases/glance |
| A-GENPREV | https://developer.android.com/develop/ui/compose/glance/generated-previews |
| A-METRICS | https://developer.android.com/develop/ui/compose/glance/metrics |
| A16-FEAT | https://developer.android.com/about/versions/16/features (Outline text) |
| A17-FEAT | https://developer.android.com/about/versions/17/features (Live Update semantic color) |
| A-BATT | https://developer.android.com/reference/android/os/BatteryManager#computeChargeTimeRemaining() |
| A-ALARM | https://developer.android.com/reference/android/app/AlarmManager#getNextAlarmClock() |
| A-PAINT | https://developer.android.com/reference/android/graphics/Paint#setFontFeatureSettings(java.lang.String) |
| AOSP-BLUR | https://source.android.com/docs/core/display/window-blurs |
| M3-SHAPE | https://github.com/material-components/material-components-android/blob/master/docs/theming/Shape.md |
| M3-TYPE | https://github.com/material-components/material-components-android/blob/master/docs/theming/Typography.md |
| M3-COLOR | https://github.com/material-components/material-components-android/blob/master/docs/theming/Color.md |
| GD-EXPR | https://design.google/library/expressive-material-design-google-research |
| BG-EXPR | https://blog.google/products/android/material-3-expressive-android-wearos-launch/ |
| HIG-WIDGETS | https://developer.apple.com/design/human-interface-guidelines/widgets |
| HIG-MATERIALS | https://developer.apple.com/design/human-interface-guidelines/materials |
| HIG-A11Y | https://developer.apple.com/design/human-interface-guidelines/accessibility |
| WWDC-GLASS | https://developer.apple.com/videos/play/wwdc2025/219/ (Meet Liquid Glass) |
| WWDC-WIDGETS | https://developer.apple.com/videos/play/wwdc2025/278/ (What's new in widgets) |
| WCAG-143 | https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html |
| WCAG-1411 | https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html |
