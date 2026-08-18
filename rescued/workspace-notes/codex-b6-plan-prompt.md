You are a senior Android engineer planning one slice of work in an existing codebase. PLAN ONLY — write no production code, edit no source file. Your single output artifact is a new file `/home/amal/termux-launcher/features-b6-plan.md`.

Read first:
- `/home/amal/termux-launcher/features.md` — the product owner's plain-language brief.
- `/home/amal/termux-launcher/features-scope.md` — section "B7. Icons, long-press, folders" is the slice being planned (delivery slice B-6). Read the whole doc for locked decisions and cross-cutting risks.
- `/home/amal/termux-launcher/features-b2-plan.md`, `features-b3-plan.md`, `features-b4-plan.md`, `features-b5-plan.md` — the four preceding slice plans. Match their structure and depth, and reuse their seams rather than inventing parallel ones.
- The shipped drawer code in `app/termux-launcher/app/src/main/java/com/termux/app/launcher/drawer/`. Three view types now exist and all must keep working: VERTICAL, HORIZONTAL (paginated pager) and CATEGORIES (tiles + expandable detail). Note B-5 is uncommitted in the working tree.
- The existing dock/pinned-row code in `app/termux-launcher/app/src/main/java/com/termux/app/SuggestionBarView.java`, especially the context popup binding the drawer already reuses, and the `PinnedFolderItem` model.

## Slice B-6: drag-to-folder, folder popup, tap-to-rename

- Long-press an icon and drag it onto another icon to create a folder containing both.
- A folder has its own popup showing its contents, and a title that can be tapped to rename.
- The folder model already exists as `PinnedFolderItem` and is currently capped at 3x3 / 6 items. The scope doc locks folders as the SHARED model between the dock and the drawer — this is not a drawer-only concept. The cap likely needs raising for drawer folders; decide the new cap and justify it, and state exactly what happens to a dock folder whose contents exceed what the dock can render.

Answer these explicitly, because they determine the design:
- Which of the three view types support drag-to-folder, and what happens in the ones that do not.
- Whether a drawer folder is the same persisted entity as a dock folder, or a separate collection sharing the model class. The scope doc says shared — say what that means concretely for persistence, schema and migration of existing user data.
- What renaming does to persistence and how an empty or single-item folder is collapsed.
- How drag interacts with the horizontal pager's page edges (does dragging to the edge page-turn?) and with the categories view's expansion state.

## Hard constraints — each is a real defect from an earlier slice

- The drawer plane stays OUTSIDE `AccessoryStackLayoutPolicy.computeCombinedHeight()`. Nothing may trigger `TerminalView.updateSize()` / SIGWINCH. That is the entire reason the plane is a separate full-screen overlay sibling.
- Gesture arbitration through nested scrolling only, never touch stealing. Drag-and-drop is a FOURTH gesture competing with scroll, close, and the per-view-type gestures (page swipe in horizontal, tile expand/collapse in categories). Specify the arbitration exactly and state where the drag latch is taken.
- Slice B-4 shipped a P1 where a neutral diagonal could both page AND close, because the axis stayed live mid-stream. The DOWN snapshot must be frozen for the whole stream and every claim one-way.
- Slice B-4 shipped a second P1 where a fast closing swipe launched the cell it ended on, because a reentrant interactivity reset cleared the click-suppression latch before terminal dispatch. Any new latch must survive reentrant resets.
- Long-press already binds the dock's context popup (`bindContextLongPressGesture`) and drag begins from that same long press — say exactly how pickup and popup are disambiguated, since today the long press opens a popup.
- No `androidx.dynamicanimation`. House `com.termux.app.Spring` only, including the drag-return and folder-open animations.
- No `EditText` in the drawer — search is deliberately focusless because an EditText steals `TerminalView`'s `InputConnection`. **Renaming a folder needs text input, so this is the hardest constraint in the slice.** State precisely how rename takes text: whether it reuses the focusless three-channel intake (in-app keyboard interceptor / hardware `onKeyDown` / IME `onCodePoint`), and if the rename UI lives in a popup or dialog OUTSIDE the drawer plane, prove that surface does not touch `TerminalView`'s InputConnection or trigger `requestAccessoryGeometrySync()`.
- Rendered-icon memory: dragging and folder previews must respect the existing byte-budgeted `LruCache`.
- Java 11, no Kotlin. JUnit4 + Robolectric.
- The three shipped view types must stay behaviourally identical outside the new feature.

## The plan must contain

A survey of reusable seams with file:line references you actually verified (not guessed); the class-by-class design; the drag mechanism chosen and why over the alternatives; the exact four-way gesture arbitration; the folder persistence/schema decision including migration of existing pinned folders; the rename input mechanism with its InputConnection proof; a numbered implementation order; the unit tests and what each asserts; and a risks section naming what could regress in the dock's pinned row and in all three drawer view types.

Be decisive — pick approaches and justify them, do not present a menu. Put anything genuinely undecidable that changes the design in an "Open questions for the project lead" section at the end rather than guessing silently.
