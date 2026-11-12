# Terminal notifications on Android (2026-11-12)

Decided with the developer on 2026-11-12. Not built.

## Today

Only kitty's OSC 99 posts an Android notification (`ShellNotifications`, channels
`termux_shell_notification_channel` "Terminal messages" and `termux_shell_notification_channel_urgent`).
OSC 9, OSC 777, OSC 7501 (Fast engine only) and the bell show in the launcher only, and only while it
is visible. Tap routing to a session already exists (`ShellNotifications.tapIntent`,
`TermuxActivity.handleShellNotificationIntent`).

## Decisions

1. **When.** A notice posts to Android only when it is unseen: the launcher is off screen, or that pane is
   not the one in front. When it is seen, the in-app notice and the window pill are enough. OSC 99's
   `occasion` may narrow this (`invisible` = only when the launcher is off screen) but `always` cannot
   widen it.
2. **Channels.** One "Terminal" channel group with six channels:

   | Channel | Source | Importance |
   |---|---|---|
   | Needs input | OSC 7501 blocked | high |
   | Errors | OSC 7501 error | default |
   | Finished | OSC 7501 done | default |
   | Program messages | OSC 9, OSC 777, OSC 99 (normal/low urgency) | default |
   | Urgent program messages | OSC 99 urgency critical | high |
   | Bell | BEL | default |

   Program messages and Urgent program messages keep the existing OSC 99 channel ids (renamed only), so
   users' current Android settings for them carry over. The others get new ids.
3. **Switch.** Android's notification settings are the only on/off switch. Terminal settings gets a
   "Notifications" row that opens the app's notification settings. No in-app toggles per kind.
4. **One per window.** Each terminal window has one notification, updated in place with its most urgent
   unseen notice, under a Terminal group summary. When the kind changes, the notification moves to that
   kind's channel; if that channel is off, nothing shows for it (lower kinds do not stand in). An OSC 99
   id replaces the earlier message with the same id in that window; OSC 99 `close` cancels it.
5. **Clearing.** A notification is cancelled when its pane comes to front, when the state it reported
   ends (blocked → working), or when its session closes.
6. **Tap** opens the launcher on that window and pane; a closed session falls back to the Terminal page.
7. **Wording.** Title: the program and its state ("claude needs input"), falling back to the window name.
   Text: the program's own message. Sub-text: the window. Lock screen: kind only, no message text
   (`VISIBILITY_PRIVATE` with a public version).
8. **Bell** posts on the Bell channel, at most once per window until the pane is seen.
9. **Guarded reply** on Needs input and Bell notifications (RemoteInput):
   - The reply is written as typed text followed by Return, never as a bracketed paste.
   - It is sent only if the program that asked (its process group and name, captured when the
     notification was posted) is still in the foreground of that pane. Otherwise nothing is typed; the
     launcher opens on that pane with a notice ("Not sent: claude is no longer waiting") and the text
     ready to copy.
   - Replying requires an unlocked phone (`setAuthenticationRequired` on Android 12+; below that the
     reply action is hidden on the lock screen).
10. **Not included.** Session or shell exits do not notify. Command-finished notices stay with the later
    shell-integration round.
11. **Engines.** Everything applies to both engines, except OSC 7501, which only the Fast engine parses.

## Surfaces to check

Launcher off screen, launcher visible with another pane in front, the same pane in front; one pane and
many; lock screen; Android 12+ and below for the reply guard; each edition (channel ids are literal,
not package-derived); POST_NOTIFICATIONS denied (fall back to in-app only, as `ShellNotifications.canPost`
does today).
