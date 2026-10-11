package com.termux.launcherctl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Notification;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;

import com.termux.app.statusbar.TopPaneFeed;
import com.termux.app.statusbar.TopPaneMediaState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowMediaController;
import org.robolectric.shadows.ShadowMediaSessionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * How a playing session reaches the status bar's media row: the listener has to notice a session
 * that was already active when it starts playing, and an instance the system has already replaced
 * must not wipe what the live one published.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LauncherCtlNotificationListenerMediaTest {

    private static final String MUSIC = "app.morphe.android.apps.youtube.music";
    private static final String READER = "com.adobe.reader";

    private final List<ServiceController<LauncherCtlNotificationListener>> mServices = new ArrayList<>();
    private final List<MediaSession> mSessions = new ArrayList<>();
    private Context mContext;
    private ShadowMediaSessionManager mManager;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mManager = shadowOf((MediaSessionManager)
            mContext.getSystemService(Context.MEDIA_SESSION_SERVICE));
    }

    @After
    public void tearDown() {
        for (ServiceController<LauncherCtlNotificationListener> service : mServices) {
            service.get().onListenerDisconnected();
        }
        for (MediaSession session : mSessions) session.release();
        mManager.clearControllers();
        TopPaneFeed.setListenerConnected(false);
    }

    @Test
    public void aSessionAlreadyActiveThatStartsPlayingReachesTheRow() {
        MediaController music = session(MUSIC, PlaybackState.STATE_STOPPED);
        mManager.addController(music);
        connect();
        assertNull(TopPaneFeed.getMedia());

        // Playback starts in a session that was already active: no sessions change, only the
        // session's own callback says so.
        shadowOf(music).executeOnPlaybackStateChanged(state(PlaybackState.STATE_PLAYING));
        idle();

        TopPaneMediaState media = TopPaneFeed.getMedia();
        assertNotNull(media);
        assertEquals(MUSIC, media.packageName);
    }

    @Test
    public void aStatelessSessionRankedFirstDoesNotHideThePlayingOne() {
        MediaController reader = session(READER, -1);
        MediaController music = session(MUSIC, PlaybackState.STATE_PLAYING);
        mManager.addController(reader);
        mManager.addController(music);
        connect();

        TopPaneMediaState media = TopPaneFeed.getMedia();
        assertNotNull(media);
        assertEquals(MUSIC, media.packageName);
    }

    @Test
    public void thePlayersOwnNotificationCatchesAMissedPlaybackCallback() {
        MediaController music = session(MUSIC, PlaybackState.STATE_NONE);
        mManager.addController(music);
        LauncherCtlNotificationListener listener = connect();
        assertNull(TopPaneFeed.getMedia());

        // The state changed but its callback never arrived; the media notification that comes
        // with playback is the backstop.
        shadowOf(music).setPlaybackState(state(PlaybackState.STATE_PLAYING));
        listener.onNotificationPosted(notification(MUSIC));
        idle();

        assertNotNull(TopPaneFeed.getMedia());
    }

    @Test
    public void aReplacedListenerInstanceDoesNotClearTheLiveOnesMedia() {
        // The system unbound and destroyed one instance but kept calling it (dumpsys lists the
        // listener twice); a second instance is the live connection.
        ServiceController<LauncherCtlNotificationListener> stale = service();
        stale.get().onListenerConnected();
        stale.destroy();

        mManager.addController(session(MUSIC, PlaybackState.STATE_PLAYING));
        connect();
        assertNotNull(TopPaneFeed.getMedia());

        stale.get().onNotificationPosted(notification("com.example.chat"));
        idle();

        assertNotNull(TopPaneFeed.getMedia());
    }

    @Test
    public void theBarComingOnScreenCatchesAMissedPlaybackCallback() {
        MediaController music = session(MUSIC, PlaybackState.STATE_PAUSED);
        MediaController other = session("com.example.podcasts", PlaybackState.STATE_NONE);
        mManager.addController(music);
        mManager.addController(other);
        connect();
        assertEquals(MUSIC, TopPaneFeed.getMedia().packageName);

        shadowOf(other).setPlaybackState(state(PlaybackState.STATE_PLAYING));
        TopPaneFeed.requestMediaResync();
        idle();

        assertEquals("com.example.podcasts", TopPaneFeed.getMedia().packageName);
    }

    private LauncherCtlNotificationListener connect() {
        LauncherCtlNotificationListener listener = service().get();
        listener.onListenerConnected();
        idle();
        return listener;
    }

    private ServiceController<LauncherCtlNotificationListener> service() {
        ServiceController<LauncherCtlNotificationListener> service =
            Robolectric.buildService(LauncherCtlNotificationListener.class).create();
        mServices.add(service);
        return service;
    }

    /** A session of {@code packageName} in {@code playbackState}, or with no state when -1. */
    private MediaController session(String packageName, int playbackState) {
        MediaSession session = new MediaSession(mContext, packageName);
        mSessions.add(session);
        MediaController controller = new MediaController(mContext, session.getSessionToken());
        ShadowMediaController shadow = shadowOf(controller);
        shadow.setPackageName(packageName);
        shadow.setPlaybackState(playbackState < 0 ? null : state(playbackState));
        return controller;
    }

    private static PlaybackState state(int value) {
        return new PlaybackState.Builder().setState(value, 0L, 1f).build();
    }

    private StatusBarNotification notification(String packageName) {
        Notification notification = new Notification();
        return new StatusBarNotification(packageName, packageName, 1, null, Process.myUid(), 0, 0,
            notification, UserHandle.getUserHandleForUid(Process.myUid()),
            System.currentTimeMillis());
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }
}
