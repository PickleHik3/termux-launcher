# Launcher settings and appearance editor review

These judgments use only the seven supplied screenshots. They do not assume how navigation, saving, scrolling, or controls work offscreen. Each screen is reviewed first as a new Android user, then as a UI/UX engineer.

Measurements are approximate. The supplied resolution and density imply roughly a 411 × 914 dp viewport. Do not treat rendered icon sizes as measured touch targets: invisible padding, semantics, contrast over changing wallpapers, and actual large-font behavior require implementation checks. Material 3 is a useful baseline, not a requirement that every control copy a stock component.

## 01-editor-rest.png — Editor at rest

### As a new user

- **Outlined phone preview:** “What am I editing?” Four large blue outlines compete for attention. I would tap the terminal, top strip, dock, or keyboard, but nothing explains whether those are selectable areas, current selections, or guides. The terminal content strongly suggests a functioning terminal; I might try typing into it.
- **“Workspace” and two indicators:** I do not know what a workspace means here. Is it a second home page, a terminal mode, or an editing category? The indicators suggest swiping, but do not tell me what the other page contains.
- **“Portrait” button:** I would expect it to rotate the preview or open an orientation choice. It is unclear whether this changes my actual launcher orientation, edits a separate portrait layout, or just changes the preview.
- **Undo, Done, palette:** Done is clear. The palette is probably colors, but I would not predict that it opens a broad Appearance panel. Undo looks disabled; I cannot tell whether edits save immediately or only when I press Done. There is no visible explanation of what Back does to changes.
- **Keyboard preview:** It occupies a large portion of the editor and looks usable. If it is only a sample, the screen needs a clear preview cue; if selectable, it needs a clear route to keyboard settings.

### As an engineer

- **Selection hierarchy:** Outlining almost every region removes the distinction between selectable and selected. Use a quieter treatment for available regions and one prominent outline for the current selection. Give the selected region a name. Do not rely on blue alone to communicate state.
- **Preview framing:** The real system status bar plus a simulated status bar plus a second time/status pill create three competing status presentations. This is plausible inside a miniature preview, but at this scale the boundary between editor chrome and simulated UI needs stronger explanation.
- **Bottom toolbar:** The compact floating grouping and prominent Done action are useful. However, “Portrait” and “Done” already consume much of the width. Larger text or translations will squeeze the two icon actions. Allow the layout to adapt instead of shrinking text or targets.
- **Accessibility:** Give every selectable region a semantic name and role, including keyboard, dock, terminal, and status pill. Expose selection and enabled/disabled state to accessibility services. Ensure undo and palette have at least 48 dp interactive bounds even if their glyphs are small.
- **Preview scaling:** Keep a minimum readable preview or offer enlargement. The next screens shrink this same dense terminal far beyond useful text-reading size; users need enough fidelity to judge their actual edits.

## 02-dock-card.png — Dock panel and overflow menu

### As a new user

- **Dock highlight:** This is the clearest explanation so far of what “Dock” refers to. The outlined area contains both terminal shortcut keys and colored app squares, though; I cannot tell whether Height affects both rows or just the apps.
- **Opacity and Tint:** I would move these to see what happens, but the difference is unclear. Does Tint change the color, its strength, or the wallpaper showing through? The blue dot beside Opacity has no evident meaning: modified, enabled, or selected?
- **“Shown” and “Place”:** “Show dock” and “Position” are easier to scan. Top/Bottom needs a reference: top of the whole screen, terminal, or keyboard? I would expect moving it to demonstrate the answer immediately.
- **Overflow “Reset” and “Also on Home”:** Reset what—dock, this page, or all appearance settings? “Also on Home” could copy the current style once, permanently synchronize both docks, or make this dock visible on Home. I cannot safely predict the result.
- **Close X:** I do not know whether this closes the panel and keeps changes or cancels them. That uncertainty matters because Done exists on the underlying editor.

### As an engineer

- **Preview/panel balance:** The selected dock is a tiny strip in a large preview. Most of the upper half is terminal content unrelated to the current controls. Consider a closer inspection mode while preserving enough surrounding context to explain position.
- **Overflow placement:** The menu obscures the opacity control and its value. Anchoring an overflow menu here is conventional, but essential settings should not depend on that menu. Use explicit scope labels such as “Reset dock settings”; describe whether the Home action copies or links settings.
- **Slider feedback:** Height has a numeric readout; opacity and tint lack comparable visible values. Add meaningful percentages or named values and accessible value announcements. A mysterious modification dot should have a consistent explanation and reset path.
- **Sheet anatomy:** The handle, title, and close control establish a recognizable bottom sheet. The overflow and close icons need independently sized targets and accessible names. The handle alone should not be the only way to resize the panel.
- **Bottom clipping:** A divider and fragments of another segmented control appear above the navigation area. This suggests more content, but the partial controls are unusable in the captured state. Verify scroll range, navigation insets, and bottom padding so the final row can be fully exposed.
- **Large text:** Label/value slider rows need wrapping rules; “Also on Home” may enlarge the menu substantially. Keep labels and controls from colliding, and preserve the full names of position choices.

## 03-pill-card.png — Appearance panel

### As a new user

- **Appearance title:** The filename says “pill,” but the visible panel says “Appearance.” On the screen itself, I cannot tell whether these changes apply to the top status pill, Workspace, Home, or the entire launcher. There is no selected-region outline here to answer that question.
- **“Look” with Clear, Mist, Tint, Solid, Custom:** Clear and Solid are understandable endpoints. Mist is branding I have to learn. Tint overlaps with the Dock panel's Tint control. I would not know which option changes transparency, blur, or color.
- **Slider plus five choices:** Do I drag continuously or choose a preset? Can I stop between Mist and Tint? Why is Custom an endpoint on the same scale? I expect either a preset selector or separately labeled appearance controls.
- **Wallpaper “Change” plus switch:** I infer the switch enables wallpaper and Change selects an image, but neither says whether this affects the preview, this launcher page, or Android's system wallpaper.
- **Theme and Black:** System/Light/Dark is familiar. “Black” alone is not: black wallpaper, black surfaces, or an extra-dark theme? What happens if I turn it on while Light is selected?

### As an engineer

- **Control semantics:** The Look track depicts an ordered scalar value while its labels describe different modes. Custom especially is not a measurable endpoint. Use a discrete selector with named modes, then show the relevant controls for the selected mode. Include a short explanation or visual sample for Mist.
- **Five labels across the width:** This is the most obvious large-font failure candidate. Custom already appears subdued and can be mistaken for disabled. Wrapping, horizontal scrolling with a clear affordance, or a different arrangement is preferable to compressed labels.
- **Wallpaper row:** Two actions on one row can work, but each needs a distinct target and semantic label. When wallpaper is off, show what Change will do and whether the selected image is retained.
- **Theme segments:** The joined group reads as one mutually exclusive choice. Preserve this clarity, including selected-state semantics and visible focus. At larger font scales, let it reflow rather than forcing three long labels into fixed widths.
- **“Black” dependency:** Explain the affected surfaces and any dependency on Dark/System. Disable or adapt incompatible choices with a reason rather than allowing visually contradictory states.
- **Lower “Icon pack” row:** Its visible label and value are cut off at the bottom. This repeats the sheet's scrolling/inset concern. Ensure the full last row and its destination affordance are reachable.

## 04-home-card.png — Home panel

### As a new user

- **Home preview:** This is much easier to recognize than Workspace. The clock, labeled apps, page indicator, and dock make the destination understandable without knowing the app.
- **Whole-preview outline:** I infer that I selected Home, but I do not know whether this includes the top pill and dock, which had their own editable regions elsewhere.
- **Icon size and Label size:** The names are useful; dp and sp are developer units. I care about how large the result looks, not the unit system. The tiny preview makes the label-size change hard to judge.
- **“Glass widgets”:** Does this change all widgets, just the sample clock, or widgets provided by this launcher? “Glass” does not tell me whether the result is translucent, blurred, or merely rounded.
- **Two-line labels and Label shadow:** These are understandable. I would expect a long sample app name and a wallpaper that makes the shadow difference visible. The current short names barely demonstrate either option.
- **Folders Compact/Roomy:** There is no folder in the preview, so I cannot predict the effect. Does this change the folder icon, the opened folder, or spacing between items?

### As an engineer

- **Good control ordering:** Put fundamental sizing before secondary text treatments, as shown. Keep immediate visual feedback close enough to inspect.
- **Preview coverage:** Add representative content for the settings being edited: a folder, a long app name, and a widget whose supported styling is clear. A preview should demonstrate the consequences of each control.
- **Section divider:** The divider after Glass widgets implies a conceptual boundary, but the following controls are still label and folder appearance. Either name the groups or use spacing consistently; an unexplained rule adds little hierarchy.
- **Folder segmented control:** It is visibly cut off at the bottom edge. This is more concerning than merely having additional rows below the viewport because part of an interactive choice is presented without its full bounds.
- **Large text:** Long switch labels need flexible height and enough space before the trailing switch. Numeric slider values should not push titles into clipping. Expose user-friendly value descriptions while retaining precise units where useful.

## 05-settings-root.png — Settings entry page

### As a new user

- **Search settings:** This is the first thing I would use if I knew my goal but not the category. The prompt is direct and the search field is easy to find.
- **Home versus Apps & dock:** I would try Home to change home-screen icons, but Apps & dock also sounds plausible. “Drawer, dock items and search” helps, although I still do not know where app icon size belongs.
- **Appearance:** “Wallpaper, glass, theme, icons and layout” overlaps heavily with Home. If I want bigger icons or different spacing, both destinations claim the task. I would expect these routes to lead to the same settings or clearly explain the distinction.
- **Terminal and Linux apps:** Terminal's description gives a reasonable clue. Linux apps assumes I know what those are and whether setup is required. I would expect a setup/status cue before discovering a separate wizard.
- **“Everywhere” and “On-device AI”:** Everywhere is useful scope language, but keyboard and notification settings may be mistaken for phone-wide Android settings. AI's “Models” is technical; I need a clearer description of what the assistant actually helps me do.

### As an engineer

- **Strong structure:** Large title, search, section labels, leading icons, descriptive secondary text, and chevrons form a coherent settings hierarchy. Rounded containers group related destinations without making every row a separate card.
- **Information architecture:** Resolve the overlap among Home, Apps & dock, and Appearance. Define one canonical location per setting and provide deliberate shortcuts where needed. The preview editor's scope language should match these categories.
- **Vertical economy:** The large blank area above Settings and generous title-to-search spacing consume substantial height. A large-title treatment can be appropriate, but verify that the header collapses during scrolling and that users can reach later categories efficiently.
- **Text wrapping:** Appearance's long subtitle is the most likely row to grow at large font sizes. Rows must use content-driven height; subtitles should wrap without colliding with chevrons or dividers.
- **Insets and semantics:** The final subtitle is close to the gesture area. Confirm adequate scroll padding. Make each complete row one destination target, with decorative icons excluded from duplicate accessibility announcements. Treat search as an actual discoverable input/action, not a decorative row.

## 06-linux-input.png — Linux apps input settings

### As a new user

- **Input / Linux apps:** The parent subtitle is helpful. I understand the setting category even if I arrived here from search.
- **Touch mode:** “How a tap reaches the app” repeats the concept without explaining the choices. Trackpad is somewhat familiar; Touchscreen and Direct touch sound like the same thing. I would need examples of how moving, clicking, dragging, and scrolling differ before choosing.
- **Phone keyboard:** I assume this lets me type with the on-screen keyboard. “Phone” is odd if I use this launcher on a tablet, and I do not know whether it excludes a physical keyboard.
- **Open on tap:** Tap where? The subtitle suggests text fields, but I would expect it to say “Show keyboard for text fields.” What happens to this setting when Phone keyboard is off?
- **Keyboard space / Overlay / Resize:** These are implementation terms. I need the consequence: “Cover part of the app” versus “Shrink the app to fit,” preferably with a small diagram. I would otherwise choose by trial and error.
- **Share clipboard:** This is clear in purpose, though “phone and apps” is vague: Android apps, all Linux apps, or the currently open Linux session? I would expect the direction of sharing to be stated.

### As an engineer

- **Good grouping:** Touch, Keyboard, and Clipboard are distinct, useful sections. Descriptions and nested choices create a better hierarchy than a flat list of technical flags.
- **Three-way touch selector:** These labels are already wide. Direct touch is the clearest large-font collision risk here. Allow an alternative vertical radio layout with descriptions; do not abbreviate away the distinction.
- **Dependent controls:** If the on-screen keyboard is disabled, Open on tap and Keyboard space need an explicit applicable state. Preserve any remembered choice while making its current effect understandable.
- **Mixed row patterns:** The keyboard card combines switch rows and a descriptive row with a segmented group beneath it. This is reasonable if dividers and padding consistently communicate which options belong together. Keep the Keyboard space description and selector in one semantic group.
- **Accessibility:** Announce each choice as part of a named, mutually exclusive group. Switches should expose checked state, not require users to interpret the check glyph. Keep descriptive text available to screen readers without creating multiple confusing focus stops.

## 07-setup-guide.png — Linux apps setup, distro step

### As a new user

- **Pick a distro:** I do not know what a distro is. The surrounding context suggests a kind of Linux, but the title should say that plainly or explain the term.
- **Debian recommendation:** A default recommendation is reassuring, and the short tradeoffs help. “Runs the most apps” is a strong promise without explaining which apps I can actually install. “Pick another only if you already use it” discourages exploration without explaining the consequences.
- **Ubuntu and Arch Linux descriptions:** “Newer packages” and “upkeep” assume Linux experience. Describe practical differences and avoid asking a beginner to infer compatibility or maintenance effort.
- **Source / Distro / Apps / Graphics:** I see a four-step process, but Source and Graphics are ambiguous. Are the labels tappable? The first two bars look identical, so completed and current steps depend on the subtle text-color difference.
- **Next, Back, and close:** Next and Back are easy to find. Before continuing, I would want to know whether Next just advances the form or starts downloading/installing something. Closing should explain whether progress can be resumed if that is supported.

### As an engineer

- **Strong selection cards:** Large full-row choices, a radio indicator, selected background, and a Recommended badge give clear visual redundancy. Keep the entire card tappable and announce the group and checked state.
- **Step indicator:** Expose “Step 2 of 4” and a distinct current-step state. If labels navigate, make that interaction clear and give them proper targets; if they are status only, avoid implying tabs.
- **Responsive cards:** Debian's title and badge share a row with a trailing radio. At large text or in translation, this is a predictable collision. Let the badge move below the title and allow descriptions and cards to grow.
- **Persistent footer:** The bottom Back/Next placement and clear primary action work well. Verify that the content scrolls above it at large font sizes and that it remains clear of the system gesture inset. The large empty middle area at normal size is acceptable for this short step; do not fix it by making card heights rigid.
- **Installation expectations:** Before the step that actually commits work, show the chosen components, download/storage implications, and a concrete action label. The screenshot alone does not establish that Next is a committing action, so no confirmation dialog is warranted solely on this evidence.

## The 5 most important fixes, ranked

1. **Make editor scope and save behavior explicit.** In `01-editor-rest.png` through `04-home-card.png`, name what is being edited, distinguish selected from selectable regions, and establish what Done, panel close, and Back do. Clarify whether “Also on Home” copies or synchronizes settings. Users should not have to experiment to learn the scope of a change.
2. **Replace ambiguous choices with understandable consequences.** Prioritize Look/Mist/Tint/Custom and Black in `03-pill-card.png`, Touchscreen versus Direct touch and Overlay/Resize in `06-linux-input.png`, and distro/package language in `07-setup-guide.png`. Use plain labels, brief explanations, and examples where two modes sound identical.
3. **Fix panel reachability and design for large text.** In `02-dock-card.png`, `03-pill-card.png`, and `04-home-card.png`, prove that every bottom control can scroll fully above system navigation. Reflow five-way options, long segmented labels, title/badge rows, and switch rows at large font sizes. Preserve at least 48 dp touch targets and accessible control states throughout.
4. **Resolve the settings navigation overlap.** In `05-settings-root.png`, distinguish Home, Apps & dock, and Appearance, then use the same scope names in the editor. Give each setting a canonical home, with clear shortcuts and search results that lead to it.
5. **Make previews demonstrate the active setting.** In `02-dock-card.png` through `04-home-card.png`, provide an inspectable view of the selected element and representative examples: long labels, a folder, and a supported widget. Add meaningful slider values. A tiny unchanged-looking preview undermines the editor's main advantage.

## Excellent elements that should stay

- **`05-settings-root.png`:** Search, short destination descriptions, grouped cards, consistent leading icons, and chevrons make the settings entry point approachable.
- **`06-linux-input.png`:** Touch/Keyboard/Clipboard sections and inline choices keep related controls together and reduce unnecessary navigation.
- **`07-setup-guide.png`:** A recommended default, short alternative descriptions, full-width selection cards, and prominent Next reduce the effort of an unfamiliar decision.
- **`02-dock-card.png`:** Highlighting the exact dock being edited is a strong connection between a setting and its effect. Extend that clarity consistently.
- **`01-editor-rest.png`:** The prominent Done action gives the editor an obvious completion point. Keep it while clarifying persistence and dismissal behavior.
- **Across the screens:** The restrained dark palette, repeated blue selection treatment, rounded surfaces, and generally readable text establish visual coherence. Keep the consistency while making state, scope, and responsiveness equally consistent.
