# Notifications

The launcher can show your Android notifications in four ways: dots on pinned apps, pinned cards in
the status bar, two widgets, and a history your shell can search. All of them are set up under
**Settings → Notifications** ("Dots, pinned alerts and history"), which is also linked from
**Settings → Status bar** and **Settings → Apps**.

## Notification access

Everything on this page needs Android's notification access for the launcher. The first row,
**Access → Notification access**, shows whether it is on and opens Android's setting. The same row
is on **Settings → Permissions & services**.

## Notification dots

**Settings → Notifications → On the home screen → Notification dots** ("Show a dot on apps with
active notifications.") is off by default. A pinned app with a dot also answers the quick-reply
swipe; see [Home screen and apps](Home_Screen_And_Apps.md#quick-reply-from-the-dock).

## Pinned notifications

A pinned notification is a card in the status bar beside the clock. A notification gets one because
it matches a rule of yours.

### Add a rule

1. Open **Settings → Notifications → Pinned to the status bar → Pinned notifications**.
2. Under **Add a rule**, fill in **App package (e.g. com.whatsapp)**, **Keywords in title or text**,
   or both.
3. Turn on **Dismissing the pin also clears the notification** if you want a dismissed card to
   clear the notification from Android's shade too.
4. Tap **Add rule**.

How rules match:

- The package must match exactly, ignoring case. Keywords match anywhere in the title or text,
  ignoring case. A rule with both needs both.
- The first matching rule wins. A list holds up to 32 rules ("Rule list is full").
- Adding the same package and keywords again replaces the old rule: it moves to the end, takes the
  new clear setting and is switched on.

Each saved rule is a card with its own on/off switch, a **Clears** pill when it clears the
notification, and **Remove**. A rule that is off stays in the list but pins nothing.

### What a card shows

- The **sender** in bold, then the message. The app's name takes the sender's place only when the
  notification has no title.
- The sender's picture with the app's icon as a small badge, or the app icon alone.
- A tint taken from the app icon's colour.
- How long ago it arrived: **now**, **4m**, **2h**, **1d**.
- A **count** when several messages from one conversation are folded into one card. The card shows
  the latest message.

The status bar shows two cards at a time. Up to eight matches are kept; past that the oldest goes.
With more than two, swipe up or down on the cards to see the rest. When media is playing, it shares
the space with a single card.

### What you can do with a card

| Gesture | What it does |
|---|---|
| **Tap** | Opens the notification. |
| **Swipe sideways** | Dismisses the card. **Dismissed · Undo** holds its place for 4 seconds. |
| **Hold** | Shows the whole message, with **Open**, **Dismiss** and **Mute this rule**. |

**Mute this rule** turns that rule's switch off. Switch it back on in **Pinned notifications**.

A dismissed card is unpinned when the 4 seconds end, or sooner if you dismiss another card or the
cards leave the screen. A dismissed pin stays gone. With reduced motion on, the swipe and the undo
jump instead of animating. TalkBack reads each card as sender, message, app and time, with **Open**,
**Dismiss** and **Mute this rule** as actions.

## Notification widgets

On Home, the **Notifications** widget lists recent notifications ("All clear" when there are none),
with **Clear all**. The **Media** widget shows what is playing. Both use the same notification
access, and ask for it on the card if it is off. See [Widgets](Widgets.md).

## Notification history

**Settings → Notifications → For the shell → Notification history** records notifications so
commands and agents in Termux can read them. Nothing is recorded until you check an app.

- **Apps**: check each app whose notifications should be saved. **Search apps** filters the list.
- **Keep for**: **7 days**, **30 days** (the default), **90 days** or **1 year**.
- **Mask one-time codes**: "Codes are hidden before a notification is saved." On by default.
- **Clear history**: deletes every saved notification; the checked apps stay checked.

The history is stored on the phone, under `~/.launcherctl`. Query it from the shell:

```sh
launcherctl notifications --app com.whatsapp --since 7d
```

See [launcherctl](LauncherCtl.md) for the other options.

## Notifications from the shell

Programs in the terminal can post their own Android notifications with `launcherctl notify`, or
with an OSC 99 escape sequence; buttons they send become notification actions. Those use the
launcher's **App notifications** permission under **Settings → Permissions & services**. See
[launcherctl](LauncherCtl.md).
