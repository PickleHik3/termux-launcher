package com.termux.app.terminal;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Which channel and title each kind gets, and what the Android poster puts in the shade. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = Build.VERSION_CODES.TIRAMISU)
public class ShellNotificationsPosterTest {

    @Test
    public void everyKindHasItsOwnChannelAndTheTwoOldIdsAreKept() {
        assertEquals("termux_terminal_needs_input", ShellNotifications.channelIdFor(TerminalNoticeKind.NEEDS_INPUT));
        assertEquals("termux_terminal_errors", ShellNotifications.channelIdFor(TerminalNoticeKind.ERROR));
        assertEquals("termux_terminal_finished", ShellNotifications.channelIdFor(TerminalNoticeKind.FINISHED));
        assertEquals("termux_shell_notification_channel", ShellNotifications.channelIdFor(TerminalNoticeKind.MESSAGE));
        assertEquals("termux_shell_notification_channel_urgent",
            ShellNotifications.channelIdFor(TerminalNoticeKind.URGENT_MESSAGE));
        assertEquals("termux_terminal_bell", ShellNotifications.channelIdFor(TerminalNoticeKind.BELL));
    }

    @Test
    public void onlyNeedsInputAndUrgentAreHighImportance() {
        for (TerminalNoticeKind kind : TerminalNoticeKind.values()) {
            boolean high = kind == TerminalNoticeKind.NEEDS_INPUT || kind == TerminalNoticeKind.URGENT_MESSAGE;
            assertEquals(kind.name(), high ? NotificationManager.IMPORTANCE_HIGH
                : NotificationManager.IMPORTANCE_DEFAULT, ShellNotifications.importanceFor(kind));
        }
    }

    @Test
    public void titleFallsBackFromMessageTitleToNameToLabel() {
        assertEquals(ShellNotifications.TitleSource.MESSAGE_TITLE,
            ShellNotifications.titleSourceFor(TerminalNoticeKind.MESSAGE, "Build", "claude"));
        assertEquals(ShellNotifications.TitleSource.NAME,
            ShellNotifications.titleSourceFor(TerminalNoticeKind.MESSAGE, "", "claude"));
        assertEquals(ShellNotifications.TitleSource.LABEL,
            ShellNotifications.titleSourceFor(TerminalNoticeKind.URGENT_MESSAGE, "", ""));
        assertEquals(ShellNotifications.TitleSource.STATE_WITH_NAME,
            ShellNotifications.titleSourceFor(TerminalNoticeKind.NEEDS_INPUT, "", "claude"));
        assertEquals(ShellNotifications.TitleSource.LABEL,
            ShellNotifications.titleSourceFor(TerminalNoticeKind.BELL, "", ""));
    }

    private static TerminalNoticeContent content(TerminalNoticeKind kind, String program) {
        return new TerminalNoticeContent(kind, "w1", "handle", new Object(), "claude", "", "ok?",
            "[1] build", true, program == null ? 0 : 42, program, null);
    }

    @Test
    public void postCreatesSixChannelsInOneGroupAndRepliesOnlyWhereAsked() {
        Context app = ApplicationProvider.getApplicationContext();
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        TerminalNotifier.Poster poster = ShellNotifications.poster(app);

        poster.post(content(TerminalNoticeKind.NEEDS_INPUT, "claude"));
        assertEquals(6, manager.getNotificationChannels().size());
        assertNotNull(manager.getNotificationChannelGroup("termux_terminal_notification_group"));
        for (android.app.NotificationChannel channel : manager.getNotificationChannels())
            assertEquals("termux_terminal_notification_group", channel.getGroup());

        Notification withReply = find(manager, "terminal/w1");
        assertEquals(1, withReply.actions.length);
        assertNotNull(withReply.actions[0].getRemoteInputs());
        assertEquals("terminal_reply", withReply.actions[0].getRemoteInputs()[0].getResultKey());
        assertEquals(Notification.VISIBILITY_PRIVATE, withReply.visibility);
        assertNotNull(withReply.publicVersion);
        assertNotNull(find(manager, "terminal/summary"));

        poster.post(content(TerminalNoticeKind.ERROR, null));
        assertNull(find(manager, "terminal/w1").actions);
        poster.post(content(TerminalNoticeKind.NEEDS_INPUT, null));
        assertNull(find(manager, "terminal/w1").actions);

        poster.cancel("w1", 0);
        assertTrue(manager.getActiveNotifications().length == 0);
    }

    @Test
    public void aReplyWithoutTextSendsNothingAndShowsTheNotificationAgainAsItWas() {
        Context app = ApplicationProvider.getApplicationContext();
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        ShellNotifications.poster(app).post(content(TerminalNoticeKind.NEEDS_INPUT, "claude"));
        Notification before = find(manager, "terminal/w1");
        long postedAt = postTime(manager, "terminal/w1");
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(5));

        // No result at all, then a reply that is only blanks and line breaks.
        android.content.Intent missing = new android.content.Intent()
            .putExtra(ShellNotifications.EXTRA_WINDOW_KEY, "w1")
            .putExtra(ShellNotifications.EXTRA_SESSION_HANDLE, "handle");
        new ShellNotifications.ReplyReceiver().onReceive(app, missing);
        android.content.Intent blank = new android.content.Intent(missing);
        android.os.Bundle results = new android.os.Bundle();
        results.putCharSequence("terminal_reply", " \n ");
        androidx.core.app.RemoteInput.addResultsToIntent(new androidx.core.app.RemoteInput[]{
            new androidx.core.app.RemoteInput.Builder("terminal_reply").build()}, blank, results);
        new ShellNotifications.ReplyReceiver().onReceive(app, blank);

        Notification after = find(manager, "terminal/w1");
        assertEquals(before.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
            after.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals(1, after.actions.length);
        assertTrue("shown again, which clears the reply spinner",
            postTime(manager, "terminal/w1") > postedAt);
        assertNull("the launcher is not opened for an empty reply",
            org.robolectric.Shadows.shadowOf((Application) app).getNextStartedActivity());
    }

    @Test
    public void repostingAWindowThatIsNotUpDoesNothing() {
        Context app = ApplicationProvider.getApplicationContext();
        assertTrue(!ShellNotifications.repostUnchanged(app, "gone"));
    }

    private static long postTime(NotificationManager manager, String tag) {
        for (StatusBarNotification n : manager.getActiveNotifications())
            if (tag.equals(n.getTag())) return n.getPostTime();
        throw new AssertionError("no notification tagged " + tag);
    }

    private static Notification find(NotificationManager manager, String tag) {
        for (StatusBarNotification n : manager.getActiveNotifications())
            if (tag.equals(n.getTag())) return n.getNotification();
        throw new AssertionError("no notification tagged " + tag);
    }
}
