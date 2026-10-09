# Pinned notification card — spec (2026-09-30)

Status: design "A · Tinted conversation card", chosen by the developer on 2026-09-30. Built on the
worktree branch; device checks are still owed.

## 1. The card

- **No app name on the card.** The sender is the bold first line and the message is the second.
  When two or more cards share the slot, they show "**Sender** · message" on one line. The app's
  name takes the sender line only when the notification has no title. TalkBack always reads it.
- **The sender's picture leads.** It is a circle: 26 dp, or 22 dp in the two-card rows. The app
  icon is a 12 dp badge at its bottom right, with a 1.75 dp ring in the card colour. The picture
  comes from the latest incoming MessagingStyle message's `Person` icon, else from
  `Notification.getLargeIcon()` / `EXTRA_LARGE_ICON`. With no picture, the app icon alone sits at
  picture size, with no badge. Pictures are decoded on a background thread, cropped to a circle
  of at most 128 px, and cached (16 entries, keyed by notification key and post time).
  `PinnedAvatarCache` does this.
- **Tint wash.** Each card is washed in its app icon's colour, `IconColor.tint`. The wash is a
  horizontal gradient from about 22 % to about 8 %, with a 1 dp hairline at about 30 %. It
  replaces the old tertiary fill and stroke. For a near-white or pale winner, the most saturated
  bucket that holds at least 8 % of the winning weight is used instead. A grey, monochrome or
  themed icon uses the theme's tertiary colour, as before. The colour is cached per package.
  The text colours are checked with `GlassInk.legible` against the panel surface washed at the
  wash's average alpha.
- **Age label** at the top right, muted 8.5 sp: "now", "4m", "2h", "1d"
  (`PinnedRelativeTime`). It updates when the soonest label changes, at most once a minute.
  It never animates.
- **Count chip** after the sender, in the card colour, when the card holds two or more messages.
- **Type** stays small: title 10 sp, message 9.5 sp.
- **Layout.** The picture is centred on its own, and the block of text is centred in the card.
  There is no top-padding offset any more. The text now also gets the width that the × took.

## 2. Gestures

| Gesture | What it does |
|---|---|
| Tap | Opens the notification, as before: its `contentIntent`, auto-cancel. An auto-cancel now clears every key in the conversation. |
| Swipe left or right | Dismisses the card. The card follows the finger and fades. It commits past 35 % of the width, or on a fling (6 × the minimum fling velocity, mostly sideways, in the direction of travel). Otherwise it springs back. |
| After a dismiss | "Dismissed · Undo" holds the card's place for 4 s, and a tap there undoes the dismiss. The real dismiss runs only when the 4 s end, when another card is dismissed, or when the cards go off screen (visibility or detach). The real dismiss unpins every key in the card, and cancels them too if the rule says clear. If the notification is removed during the 4 s, the undo row is dropped. |
| Long-press | Haptic tick, then the launcher's anchored glass menu (`AnchoredMenu`, the same one as the dock and drawer). It shows the full sender, the age, the app and message count, up to 4 lines of the message, and then **Open**, **Dismiss** (with the undo) and **Mute this rule**. |
| Up or down | Scrolls the pins, as before, but only when there are more than two. |

**How the gestures are told apart.** The direction is decided once, at touch slop (`PinnedSwipe.decide`):

- **Sideways** belongs to the card. It calls `requestDisallowInterceptTouchEvent(true)`, and
  `canScrollHorizontally` returns true while there are cards.
- **The fold.** On a top or bottom bar, the status bar's fold only claims moves across the bar,
  so a sideways move never reaches it. A drag along the bar falls to `CHILD_OWNED`.
- **The border drag.** It claims only after a still hold, so a moving finger abandons it.
- **Vertical** keeps today's rule: the cards claim it only when they can scroll. Otherwise the
  fold takes it.

## 3. Grouping

A conversation is identified by the package plus the first of these that is present:

1. the shortcut id,
2. `EXTRA_CONVERSATION_TITLE` (or the MessagingStyle conversation title),
3. the sender (the title).

A notification with none of these is a conversation of its own. See `PinnedConversations`.

- **What the card shows.** It shows the latest message. Open acts on that latest message, and
  dismiss clears every key in the card.
- **The count** is the larger of two numbers: how many notifications were folded in, and the
  most MessagingStyle messages any one of them carries.
- **Order.** Cards are ordered by each conversation's latest post. The listener keeps the
  on-screen order by conversation id.
- **Duplicates.** Two notifications with identical package, sender and body are still dropped
  before grouping, so a repeat does not add to the count.

## 4. Rules

- `EssentialNotificationRule.enabled` uses the JSON key `enabled`. When the key is missing it
  defaults to true, so rules saved before this change stay on. A disabled rule never matches.
- **Mute this rule** calls `EssentialNotificationRules.setEnabled(false)` through
  `TopPaneFeed.muteRule`. The listener then rebuilds, via the new `Controls.refreshPinned()`.
- Settings → Essential notifications shows a switch on each rule's card, and a muted rule's text
  is dimmed.

## 5. Slot height budget (overflow fix)

**The bug.** On pong, the card-and-media column filled 66 of the slot's 68 dp. Its top sat 1 dp
from the slot top, which is the bar's content edge, so the card looked as if it spilled out of the
bar. The clock never showed this, because its face is centred with room to spare.

**The fix.** `TopPaneSlotBudget` keeps 5 dp of air above and below the column, inside the slot.
When the column's content does not fit, the gap shrinks first (6 → 4 dp) and then the card, never
the air.

Heights, docked (68 dp slot):

| Layout | Height |
|---|---|
| Card over the media strip | about 34 dp card + 4 dp gap + 20 dp strip |
| One card | 48 dp |
| Two or more rows | 58 dp, shared as two rows |

The capsule slot (72 dp) gets the same air and a taller card. The budget is pure and is tested in
`PinnedCardLogicTest`.

## 6. Accessibility and motion

- Each card on screen is a virtual view (`ExploreByTouchHelper`). Its label is "Sender, message,
  App, time[, N messages]".
- Each card has Open (click), Dismiss and Mute this rule actions. The undo row reads "Dismissed.
  Double-tap to undo".
- The host view keeps its scroll actions and content description. Keyboard focus is not taken.
- With reduced motion (`ReducedMotion.isEnabled`), these all jump instead of animating: the swipe
  commit and spring-back, the undo fades, the pin-scroll settle, and the slot's transitions
  (`TopPaneWidgetSlot.applyFeed`).

## 7. What changed

- New: `chrome/IconColor` (moved out of `TermuxActivity`), `statusbar/PinnedAvatarCache`,
  `PinnedConversations`, `PinnedRelativeTime`, `PinnedSwipe`, `TopPaneSlotBudget`.
- Rewritten: `PinnedNotificationsView`.
- Updated: `PinnedNotification` (conversation id, keys, count, avatar), `TopPaneFeed`,
  `TopPaneWidgetSlot`, `LauncherCtlNotificationListener`, the rule model and its fragment.
