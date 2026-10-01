# Pinned notifications

A pinned notification is a card in the status bar's widget slot, beside the clock. A notification
gets one because it matches a rule of yours. The rules are called **Essential notification rules**
in Settings; the cards are what you see when a rule matches.

## Turn it on

1. Give the launcher notification access: **Settings → Notifications**, then the access row.
2. In **Settings → Notifications**, open **Essential notification rules** and add a rule. A rule
   needs an app package (such as `com.whatsapp`), keywords, or both. A notification must match
   what the rule has.
3. Each rule has a switch. Off keeps the rule but stops it pinning anything. A rule can also say
   whether dismissing a card clears the notification itself.

When a notification matches, its card appears beside the clock in the status bar.

## What a card shows

- The **sender**, in bold on the first line, and the message on the second. The app's name takes
  the sender's line only when the notification has no title.
- The **sender's picture**, in a circle, with the app's icon as a small badge at its corner. With
  no picture, the app icon sits alone.
- A **tint** washed from the app icon's colour.
- How long ago it arrived (**now**, **4m**, **2h**, **1d**), at the top right.
- A **count**, after the sender, when two or more messages from one conversation are folded into
  one card. A conversation is the same app plus the same chat, shortcut or sender; the card shows
  the latest message.

With two cards or more, they share the slot, one line each as **Sender** · message. Swipe up or down
on them to see the rest when there are more than two.

## What you can do with a card

| Gesture | What it does |
|---|---|
| **Tap** | Opens the notification. |
| **Swipe sideways** | Dismisses the card. It follows your finger and goes past about a third of the way, or on a flick. |
| After a dismiss | **Dismissed · Undo** holds the card's place for 4 seconds; tap it to bring the card back. |
| **Hold** | Shows the whole message, its age, the app and the message count, with **Open**, **Dismiss** and **Mute this rule**. |

**Mute this rule** turns that rule's switch off. The rule stays in the list, dimmed, and you can
switch it back on in **Settings → Notifications → Essential notification rules**.

A dismissed card is unpinned only when the 4 seconds end. If you dismiss another card first, or
the cards go off screen, the dismiss goes through then. If the rule says dismissing also clears the
notification, it is cleared from Android's shade too.

## Good to know

- A sideways swipe on a card is always the card's own; the status bar's fold only answers moves
  across the bar, so the two never fight.
- With reduced motion on, the swipe, the undo and the scrolling jump instead of animating.
- TalkBack reads each card as "Sender, message, App, time", with Open, Dismiss and Mute this rule
  as actions.
- Two notifications with the same app, sender and message are counted once.

See also [Settings map](Launcher_Settings.md#status-bar) for where the rules live, and the
[Using the launcher](Launcher_Usage.md#use-the-status-row) guide for the status bar itself. The
design is in `project-docs/active/pinned-notification/SPEC.md`.
