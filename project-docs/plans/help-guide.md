# Proposed final help guide

Status: proposed, not implemented. 18 September 2026.

## Product decision

One Help centre for the whole launcher, with a search field, a contextual “On this screen” section, a complete guide, a glossary and optional practice. Open this centre first. Keep the visual screen guide as an explicit “Explore this screen” action, showing one explanation at a time. This deliberately replaces the current default of opening the all-controls overlay. It retains the existing topic catalogue, measured targets, four practice lessons and gesture rendering where accurate.

Help must answer three questions: What is this? How do I use it? How do I get back?

## Entry points and lifecycle

- Corner tab → ?: open Help home with the invoking place and pane as context. “On this screen” comes first. Keep a corner/tab route on Terminal, Widgets and Display.
- Settings → Help, or command palette → Help: open the same Help home with the current place as context. Search stays unfocused; opening help does not summon a keyboard.
- A contextual Learn more link: open its topic directly inside the same Help frame. The Home control leads to Help home, and Close returns to the source.
- First-run tour and Try it: keep the four existing lessons. A tour pause while reading help returns to that tour when help closes. Practice from a topic returns to that topic when finished or cancelled.
- A fresh Help invocation starts at Help home, except explicit topic links. Preserve query, scroll position and selected topic while navigating within an invocation. Do not reopen an old search from a previous invocation.
- Opening, reading, searching and closing help do not switch places, edit preferences, execute commands or change terminal geometry. Underlying sessions continue running. Any real action requires an explicit Try it or named action button.

## Where help appears

Help is an overlay above the launcher. Its content belongs to one solid, scheme-aware panel; use a subtle scrim behind it. Use the launcher's typeface, colors, corner treatment and button components. Do not make reading depend on wallpaper contrast or surface transparency settings.

Portrait: an inset panel occupying most of the usable viewport, within system bars, with a pinned header and a scrolling body. Landscape or wide screens: a bounded-width reading panel with navigation beside content when measured width permits. No independently floating close/book capsule. Close stays in the panel header; controls do not cover content. The reading layout may cover launcher controls because it is a document. The exploration layout must keep the control being explained visible.

The overlay must not resize the terminal. Opening search can show the configured text-input method through the existing input integration; if Android's system keyboard is requested, call onSystemImeRequested() before requesting it. Keep Help's header visible and resize/scroll its own content above the keyboard. Restore the pre-help keyboard state on close, and do not mutate the remembered keyboard preference.

Back/Escape first closes a term definition, then dismisses active text entry/IME, then returns through Help navigation. At Help home it closes Help. A visible Close help action exits from any reading page. Outside taps do not dismiss the reading panel or run underlying actions. During exploration, outside taps deselect the current explanation; Back returns to Help home. Actual practice has an always-accessible End practice control.

## Help home

Pinned header: “Help” and Close help.

1. “Search help” field.
2. “On this screen · Terminal / Widgets / Display” with 3–5 relevant topics and “Explore this screen”. This is a shortcut section, not a filter on the full guide.
3. “Browse the guide” with the seven groups below.
4. Two visible utility links: “Glossary” and “Practice the basics”.
5. “Full documentation ↗” and “Get support ↗” near the end. Mark them as external; core help and glossary work offline.

Search and Glossary are first-class destinations, not hidden inside an overflow menu. No bottom navigation competing with Android's navigation or the keyboard. The header's search action remains available on topic and glossary pages; it opens the same Search destination.

## Complete guide contents

### 1. Find your way

- Terminal, Widgets and Display: what each place is, switching and returning.
- Pane and page controls: opening a corner tab; Appearance, Layout and Help.
- Command palette: find actions and understand why an action is unavailable.
- Status bar: place navigation, expand/collapse, and window-strip scrolling versus place switching.
- Status widgets: CPU, memory, weather and where to enable them.

### 2. Apps and widgets

- Open Android apps: dock, drawer and returning to the launcher.
- Find an app: A–Z browsing and app search.
- Organize apps: pin, unpin, reorder and folders.
- Add, move, resize and remove widgets.
- Widget pages and grid size.

### 3. Terminal basics

- Scroll, select, copy and paste.
- Touch and mouse mode: shell versus mouse-aware programs; turning mouse mode off.
- Change text size: pinch, focused-pane scope and restoring the preferred size where supported.
- Find text, links and terminal actions.
- Sessions → windows → panes: the hierarchy and switching versus closing.

### 4. Keyboard and shortcuts

- Show/hide the keyboard and choose an input method.
- Extra keys: tap and secondary gesture, using the user's actual key assignments.
- Keyboard layouts: docked, floating and split; how to return.
- Keyboard shortcuts: Ctrl/Alt behavior and actual resolved bindings. Consolidates Prefix keys and Key chords.
- Space-bar gestures: palette and the configured corner actions.
- Open Settings, including an alternative when the keyboard is hidden.

### 5. Multitasking and workspaces

- Create, focus, resize and close panes.
- Move panes and use automatic layouts.
- Float and dock a pane.
- Create, switch, rename and close windows/sessions.
- Save/load workspaces: saves a layout; optionally restarts commands; does not resume process state.

### 6. Make it yours

- Layout editor: element position/visibility, portrait versus landscape, Done and Discard.
- Appearance editor: pick a surface, preview changes, Done, lowering a card versus leaving the editor.
- Base and independent surface values: follow Base, override a value, and rejoin Base.
- Themes, colors, wallpaper and fonts: concise entries linking to the relevant settings.

### 7. Linux display and recovery

Two subgroups within this group so troubleshooting is easy to find:

Linux display: set up, start/stop, open/switch desktop apps, display scale, touchpad gestures, and keyboard routing. Explain how to return to the launcher when shortcuts go to Linux.

Something missing?: hidden keyboard/dock/extra keys, unavailable actions, unexpected shortcuts, missing status widgets, display setup problems, and where to find diagnostics/support. Each symptom links to its owning topic and a specific setting or existing setup guide. Do not duplicate lengthy installation procedures here.

## Topic anatomy

Header: Back, a short topic title, Search and Close (accessible names on icon buttons). On compact widths, prioritize Back and Close; keep search available in the page body rather than shrinking touch targets.

Body:
1. One sentence explaining what the feature is.
2. One primary instruction, adapted to the current state and geometry.
3. A compact visual only when it explains a gesture, hierarchy or hidden control better than words.
4. At most three short steps for the primary task.
5. An explicit way back or undo instruction when the action changes modes or layout.
6. Contextual state: “Not visible on this screen”, “Already running”, or the actual unavailable reason, followed by a valid route onward.
7. Related topics and inline glossary terms.
8. Full documentation link when deeper detail exists.

Actions: “Show on screen” only when a real target is available; “Show gesture” only when the actual gesture is implemented; “Try it” only when a valid practice lesson exists. Omit unsupported actions instead of filling every topic with disabled buttons. A hidden target must never make its explanatory text unavailable. Do not suggest stopping an already-running display merely to reveal its Start button.

Example — Open Android apps:
“The dock keeps your pinned Android apps close.”
“Swipe inward from the right-hand dock to open the app drawer.” (Only for that actual placement.)
“Tap an app to open it. Return using Home if Termux Launcher is your home app; otherwise return through Android's recent apps.”
Show on screen · Show gesture · Try it.
Related: Find an app, Organize apps.

## Search

Search indexes every topic, glossary entry and recovery symptom, regardless of current place or whether its target is visible. Search is entirely on-device and includes localized titles, action words, short descriptions and curated aliases. Do not index terminal content, app usage or user files.

Rank exact title/term matches first, then aliases and body matches. Current-place relevance breaks ties; it must not hide other results. Label result types “Guide”, “Term” or “Fix”, show a useful one-line excerpt and the section/place when helpful. Avoid duplicate overview/detail results for the same topic.

Examples: “tabs” finds Windows; “split” finds Panes and Keyboard shortcuts; “paste” finds Copy and paste; “can't type” finds keyboard recovery; “base” finds the Base glossary entry and Appearance topic.

Blank search shows suggested tasks without opening the keyboard automatically. Zero results says “No matching help” with links to Browse the guide and Get support; keep the query editable. Back from a result restores the exact query and scroll. No search-history persistence in the first version.

## Glossary

A visible Help-home link opens an A–Z list with “Find a term”. Its entries also appear in global search. Use plain definitions followed by one relevant topic link. Technical mechanisms stay in the full documentation.

Initial terms:
- Place: one of Terminal, Widgets or Display.
- Session: a group of terminal windows.
- Window: a tab containing one or more panes.
- Pane: an individual terminal area with its own shell.
- Workspace: a saved arrangement of sessions, windows and panes; not a suspended running process.
- Dock: the pinned-app area; it may be a row or a side rail.
- App drawer: the list of installed Android apps.
- Extra keys: the configurable action/key strip beside the terminal.
- Command palette: a searchable list of launcher actions.
- Surface: an appearance region such as Dock, Keyboard, Status or Canvas.
- Base: shared appearance values that surfaces can follow.
- Independent value: a surface value that no longer follows Base for that property.
- Docked / Floating: names depend on what is being edited; explain dock style separately from a floating keyboard or pane.
- Mouse mode: direct mouse input in Terminal; the corresponding action opens the touchpad in Display.
- Layout editor / Appearance editor: arrangement versus appearance.

Inside a topic, tapping a glossary term opens an inline expandable definition at that location. It never stacks another floating card over a gesture target. Back collapses it before leaving the topic. Include “Open glossary” for the full entry; return preserves the originating topic and scroll.

## Explore this screen

Enter explicitly from Help home or Show on screen. Hide the reading panel, slightly dim the live launcher and mark available targets. Small adjacent targets may be grouped into one selector to preserve usable touch areas. No full explanations or many colored leaders are displayed together.

Tap a target to show one concise card with its name, primary action and “Read topic”. Keep the selected control visible. Tap another target to switch. Use one highlight and at most one short leader; color is never the only association cue. Keep an accessible list alternative for every marker.

Reserve system insets and an exploration toolbar containing Back to help and Close help. Cards and toolbar never intersect each other or the selected control. Prefer a nearby card; use a clear edge region if available. If the measured content cannot fit, return to its reading page with a compact representative illustration and explain the control there. Never shrink text below the app's readable standard, cover the selected target, or silently omit the topic.

Layout changes remeasure the current target. If it disappears, stop the demonstration and return to the topic with its new state; do not redirect the highlight to another control. Card placement occurs on geometry/content changes, never continuously per frame.

## Practice

Keep the current four lessons: find Help, open an app and return, hide/show the keyboard, open/close the command palette. Offer them in a separate Practice the basics list, with a brief description and Start. Do not expand onboarding into the complete reference.

Try it closes the reading panel and starts the named lesson with a small, unobstructive instruction. Success comes from actual launcher signals. End practice restores the originating Help topic. Do not automatically reverse meaningful actions the user performed; restore only transient help/input UI. Avoid new lessons involving closing live shells or replacing workspaces.

## Implementation boundaries

Keep help behind its existing controller/model and Host seams. One canonical topic record should own title, group, summary, steps, related terms, aliases, documentation link and supported actions. Overview labels, details and search all read it. Resolved target state supplies geometry, visibility and current gesture; it is not a prerequisite for reading.

Keep navigation state (home, search, glossary, topic, explore, practice return) separate from measured target state. Put placement policy in a pure module taking viewport, insets, selected target, reserved controls and measured card size. Return a valid placement or an explicit reading-view fallback. Do not reuse the current fallback that turns hard exclusions into soft ones.

Use text and vector cues. Avoid retained screenshots/live miniature bitmaps. Any future illustration cache must have an explicit bound. Existing screenshots are review evidence, not app assets.

## Acceptance and scope

- Every topic can be reached from Browse and Search, even when hidden or unavailable.
- Every glossary entry is reachable from Glossary and Search; inline definitions preserve reading position.
- Search does not type into the terminal; opening/closing help preserves terminal bounds and pre-help input state.
- Demonstrations agree with the instruction and current orientation/edge; unsupported gestures are not offered.
- Exploration has zero card/selected-target/toolbar intersections and a documented overflow fallback.
- Back, Close, cancelling practice and interrupted practice have explicit tested destinations.
- Verify all four screenshot arrangements, Docked/Floating, keyboard up/down, Android IME, one/multiple panes, all three places, both orientations, large font scale, inherited/detached values, light/dark/black, Material You and launcher scheme. Include TalkBack and hardware keyboard navigation.
- No edition identity, signing, bootstrap or targetSdk changes. Full documentation links must be edition-appropriate when installation steps differ.

This proposal changes how help is presented, not launcher behavior. Build navigation/search/glossary and correct topic copy first; replace the crowded overview with exploration next. Keep existing Help entry points working throughout.

## Build plan (2026-09-18 night, decided by the orchestrating session while the user slept)

The user authorised design, vetting, spec and implementation in one go ("proceed with designing,
vetting, speccing and implementation in your new worktree"). Decisions below are the orchestrator's;
each is flagged for the user's morning review and can be reversed.

### Decisions

- D1 Help home is the default (spec). The corner ? , Settings → Help and the palette all open Help
  home for the current place. The crowded overview survives only as "Explore this screen".
- D2 One canonical topic record, global not per place. Today's per-place ids (dock, az, status,
  stats, corners, sessions, windows, keys, prefix, space, settings, divider, shortcuts, start, setup,
  scale, touchpad, widget, empty) become *target ids*; a topic binds to at most one target id and
  the places where that target exists. "On this screen" = topics whose target measured on this pass.
- D3 The seven groups and the ~35 topics of "Complete guide contents" are all authored now, plus the
  fifteen glossary terms and a "Something missing?" set of symptom topics labelled Fix. Product copy
  standard applies (AGENTS.md "User-facing text"). Prefix keys + Key chords merge into Keyboard
  shortcuts; Display gains a Set up topic bound to the existing `setup` target.
- D4 Explore this screen shows one card at a time (spec). Markers on every measured target; tapping
  one seats a single card via a pure placement module; when the extra keys row is selected the
  seven per-key labels are shown with it (reusing the key-card arrangement from 02dbd613); no other
  cards are on screen. The card, toolbar and selected control never intersect; when the card cannot
  fit, exploration hands back to the topic page.
- D5 Search is a pure module over topics + glossary + fixes with curated aliases; exact title > alias
  > body; current place breaks ties only. No history.
- D6 Full documentation links use the existing pattern
  `https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/<file>#<anchor>` (X11PaneFrame:59).
  Edition-specific install steps are not linked from topics in this version.
- D7 Search text entry follows the app drawer search's input integration; any path that shows the
  system keyboard calls `TermuxActivity.onSystemImeRequested()` first.
- D8 Practice keeps the four lessons. "Practice the basics" lists them on Help home. If the tour has
  no end-of-practice signal, help stays closed after practice (deviation, reported).
- D9 Back: term definition → text entry → help navigation → close. Wired through one controller
  registered where `dismissHelpOverlay` is today (TermuxActivity:12059).
- D10 First version: single-column panel in both orientations (bounded width, centred in landscape).
  Navigation beside content is deferred.
- D11 Process: phases run as manually briefed worktree agents, not the phased-build workflow — the
  workflow starts dependants before the upstream branch is merged into dev, and phases B and C
  compile against phase A's classes.

### Phases

| Phase | Branch | Deliverable | Depends on | Model |
|---|---|---|---|---|
| A model | feat/help-model | `HelpTopics` canonical record (7 groups, target binding, steps, way back, aliases, terms, doc link, gesture, lesson), `HelpGlossary`, `HelpSearch`, `HelpNavigation` (screens + back stack + query/scroll), all topic/glossary strings, tests; overlay and old model kept compiling | — | opus |
| B panel | feat/help-panel | `HelpPanelView` (home, search, glossary, topic, inline term), `HelpController` (navigation, panel + explore overlay, back, IME, practice hand-off), TermuxActivity wiring | A | opus |
| C explore | feat/help-explore | `HelpOverlayView` becomes explore-only with markers, one card, `HelpExplorePlacement` (pure), toolbar, per-topic gesture, remeasure rules; old chooser/topic modes removed | A | opus |
| D tidy | on dev | docs/en/Launcher_Usage.md help section, dedupe styling helpers, receipt | B, C | sonnet / orchestrator |

### The B–C seam (fixed here so B and C can run in parallel)

```java
// owned by C
public final class HelpOverlayView extends FrameLayout {
    public interface ExploreListener {
        void onReadTopic(String topicId);   // "Read topic" on the seated card
        void onBackToHelp();                // toolbar Back to help, or Back with nothing selected
        void onCloseHelp();                 // toolbar Close help
        void onTargetGone(String topicId);  // the selected control vanished after a relayout
        void onCardDoesNotFit(String topicId); // placement found no seat: read it instead
    }
    public HelpOverlayView(Context context, HelpTargets.ViewFinder finder);
    public void setExploreListener(ExploreListener listener);
    /** Enter exploring for a place; selectTopicId pre-selects (Show on screen) or is null. */
    public void explore(PaneWallPage place, String selectTopicId);
    /** Explore with the topic selected and its gesture playing (Show gesture). */
    public void demonstrate(PaneWallPage place, String topicId);
    public boolean isShowing();
    public void dismiss();
    /** True when consumed (a card was deselected); false hands Back to the controller. */
    public boolean onBackPressed();
}
```
B measures on its own: `new HelpTargets(finder, panelView).measure(place).targets` gives the target
ids on screen; `HelpTopics` (phase A) maps target id + place to a topic.
