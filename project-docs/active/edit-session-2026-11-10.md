# One edit session for Done, Undo and Back (2026-11-10)

Status: designed, not built. Decided with the developer 2026-11-10: build it once, with the
wallpaper included, after the ADR 0018 waves free `TermuxActivity.java` and the narrowing of
`SurfaceEditorController.Host`. Until then only the card-looks gaps are fixed on their own
(`fix/card-looks-session`). Source: candidate 4 of the architecture review
(`~/reports/architecture-review-2026-11-10/`).

## Problem

The Appearance editor promises one thing: what you changed is kept on Done and put back on Undo,
Discard or a killed launcher. Behind that promise the session's parts are listed by hand in five
places in `SurfaceEditorController`, and the wallpaper sits outside altogether.

| Part | Captured on open | Dirty | Reverted | Carried over a relaunch | Killed-session copy |
|---|---|---|---|---|---|
| Look (`AppearanceSnapshot`) | yes | yes | yes | yes | yes |
| Saved Custom | yes | no (not checked whether that is intended) | yes | yes | yes |
| Card looks (`card_custom_looks_v1`) | yes | **no** | yes | **no** | **no** |
| App-wide choices (`EditorSettingsSnapshot`) | yes | yes | yes | yes | yes |
| Minimal mode | yes | yes | yes | yes | no |
| Theme preview | (pending value) | yes | yes | relaunch commits it | no |
| Layout (`LayoutEditorPlan`) | yes, per variant | yes | yes | yes | no |
| Wallpaper (`WallpaperLiveHost`) | yes (`mOpened`, rate) | "Done has work" | on release | no | no |

The lists are `open` (capture), `carrySession` / `CarriedSession`, `AppearanceEntryStore.save`,
`isDirty` and `revertToEntry`. A part added to one and forgotten in another is a silent loss; the
card-looks row is the example found.

The wallpaper is wired through three closures in the activity (`hasPendingChanges`, `commit`,
`release`), and `AppearanceSurfaceController.leave` asks it separately and picks one of two dialog
routes (`confirmLeave` when the wallpaper has work, `requestLeave` otherwise).

What the four implementations are not: the same operation. Look, choices and layout write through
as the user edits and undo by restoring a captured copy. The wallpaper writes only its preview
choice, applies to the phone on Done, asynchronously, and reverts when the sheet is released. The
design has to hold both.

## Rules kept

From AGENTS.md, unchanged: the editor commits only on Done; Back above peek collapses to peek, at
peek it goes through the unsaved-changes dialog; dirtiness compares against the entry snapshot.
Undo stays single-level (back to the open), as today.

## Design

```java
/** One thing the edit session keeps on Done and puts back on Undo. */
interface EditPart {
    void capture();                    // on open
    boolean isDirty();                 // against what capture() took
    void revert();                     // Undo, Discard
    void commit(@NonNull Runnable then); // Done, Save; then runs on main when the part is stored
    @Nullable Object carry();          // across a relaunch; null carries nothing
    void adopt(@NonNull Object carried);
    void saveLeftover(@NonNull SharedPreferences.Editor out);   // the killed-session copy
    void restoreLeftover(@NonNull SharedPreferences in);
}

final class EditSession {
    EditSession(@NonNull List<EditPart> parts);   // order is revert order
    void open(@Nullable Carried carried);
    boolean isDirty();                 // any part
    void revert();                     // each part, then one sync
    void commit(@NonNull Runnable then); // each part in order, then clear the leftover
    Carried carry();
}
```

- `commit` takes a callback because the wallpaper's Done is asynchronous
  (`LiveWallpaperApplier.useMotion`). Every other part calls `then` at once.
- Parts, in today's revert order: layout, look, saved Custom, card looks, app-wide choices, theme,
  minimal, widget glass choices, wallpaper. The order is the list, not five method bodies.
- A part that is deliberately never dirty answers false and says why in its comment (saved
  Custom may be one; decide when it is built); the table above becomes a test: every part is exercised through capture, change, dirty, revert,
  carry and leftover by one shared contract test.
- `SurfaceEditorController.isDirty`, `revertToEntry`, `commitSession`, `carrySession` and the
  open-time capture become calls on the session. The one `syncAfterBulkWrite` stays in the
  controller, after `revert`.
- The wallpaper part wraps `WallpaperLiveHost` (`hasPendingChanges`, `commit`, `revertPreview`).
  `AppearanceSurfaceController.leave` loses its branch: one `isDirty`, one dialog. The three
  activity closures become one part handed to the session.
- `AppearanceEntryStore` keeps its stored keys; each part reads and writes its own field, and a
  field absent from an older build's leftover leaves that part alone.

## What it needs that is not free yet

- `TermuxActivity.java`: the wallpaper closures (about :24290-24315) and the construction of the
  editor and layout hosts.
- `SurfaceEditorController.Host`: minimal mode, theme, app-wide choices and widget glass parts call
  it (`setMinimalMode`, `previewTheme`, `commitTheme`, `applyHomeSettings`,
  `restoreWidgetGlassChoices`). Its narrowing is queued behind the ADR 0018 waves; the parts are
  where those calls should land, so the two are one piece of work.

## Slices, when it starts

1. The contract test and `EditPart` for look, saved Custom and card looks (pure prefs), with the
   session walking them; behaviour unchanged.
2. App-wide choices, theme, minimal and widget glass as parts, together with the Host narrowing.
3. Layout as a part (wraps `LayoutEditorController.isDirty` / `revert` / `end`).
4. Wallpaper as a part; `leave` and the activity closures collapse.
5. Emulator: Done, Undo, Back at peek with each part changed alone; a theme relaunch mid-session
   then Undo; a force-stop mid-session then a cold start. Pong once at the end.

A builder stops and reports if a part needs more of the controller than its own captured value
and the Host calls listed above: twice this month a "move it out" premise turned out to be glue
over many methods.
