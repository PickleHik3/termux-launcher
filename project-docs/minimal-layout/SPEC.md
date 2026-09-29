# Minimal layout - spec v1 (2026-09-29)

Minimal mode ("full screen mode" to the developer; `place.minimal`) used to be an overlay:
`MinimalMode.apply` hid every element but the status bar over whatever the normal layout was. The
Layout editor could not see it, so editing while minimal changed the normal layout and showed
nothing. Minimal now has a layout of its own.

## Model

- `LayoutVariant` is NORMAL or MINIMAL, a dimension beside `PlaceOrientation`.
- Keys: `layout.<o>.<key>` for NORMAL (unchanged, nothing migrated) and
  `layout.minimal.<o>.<key>` for MINIMAL (`PlaceLayoutStore.layoutKey(variant, orientation, key)`).
- The whole per-orientation arrangement is per variant: edges, order, under-keyboard, keyboard
  mode and form, heights, chin, widget grid (`ARRANGEMENT_KEYS`).
- Stays launcher-wide or per orientation as before: `keyboard_turned_off`,
  `place.<place>.keyboard_open`, `status_compact`, the floating keyboard and voice pill positions.
- Seeding: the first time MINIMAL is used for an orientation with no stored keys, the normal keys
  that exist are copied and `MinimalMode.apply` (every element put away, edges kept) is written
  over the slots. Minimal therefore looks as it did until the user edits it. Seeded once; later
  changes to the normal layout do not flow in.
- Reset in the editor for the minimal variant is `clear()` on the pinned store: the keys go and
  the next read seeds again from the normal layout as it is then.

## Store API

- `store.forVariant(v)` pins a store to a variant (shares preferences and revision).
- A store from the public constructor follows minimal mode (`activeVariant()`), so the Settings
  pages and the size preferences edit what the launcher is showing.
- `resolve(orientation, variant)` for an explicit answer.

## Runtime

`TermuxActivity.placeLayout` resolves the variant of `isChromeMinimal()` and caches by variant.
`currentPlaceLayout` no longer folds `MinimalMode.apply` in. Kept: `MinimalMode.bottomOnly` for
the slide pre-roll (over the normal layout), `keyboardOnEnter` and the keyboard-memory rules, the
pane maximising (`setMinimalPresentation`), the zero bottom edge gap, the folded status bar with
no expand swipe. Now following the layout: the status bar (a shown bar keeps its band, a hidden
one goes GONE through `applyStatusBarEdge`; the old zero-thickness trick and fade are gone), and
the dock/A-Z/extra keys, which appear in minimal when the minimal layout shows them.

## Editor

`LayoutEditorPlan.enter` and `PlaceArrangeSnapshot.capture` take the variant (default: the active
one). The plan pins its store to it, so toggling minimal under an open editor does not move what
it edits, and Discard/dirty are per variant. The card title reads "Minimal layout".

## Not in scope

The separate `fullscreen` termux.properties flag (system bars only). Appearance stays one look.
