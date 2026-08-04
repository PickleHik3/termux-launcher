package com.termux.app.statusbar;

import android.app.Application;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.termux.app.terminal.SessionBrowserModel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SessionsPanelViewTest {

    @Test
    public void sessionsAreCollapsedByDefaultAndOnlyOneExpands() {
        SessionsPanelView view = panel(new RecordingListener(),
            session(10, 0, false, 100, 2), session(20, 1, true, 200, 1));
        assertEquals(2, rows(view).getChildCount());

        rows(view).getChildAt(0).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(4, rows(view).getChildCount());

        // The second header shifted after two children; expanding it collapses the first.
        rows(view).getChildAt(3).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(3, rows(view).getChildCount());
        assertEquals(Long.valueOf(20), rows(view).getChildAt(1).getTag());
    }

    @Test
    public void childClickDispatchesStableSessionAndWindowIds() {
        RecordingListener listener = new RecordingListener();
        SessionsPanelView view = panel(listener, session(77, 0, true, 901, 1));
        rows(view).getChildAt(0).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        rows(view).getChildAt(1).performClick();

        assertEquals(Collections.singletonList("window:77:901"), listener.events);
    }

    @Test
    public void nonStructuralRefreshRebindsExistingRowsAndPreservesExpansion() {
        SessionsPanelView view = panel(new RecordingListener(),
            namedSession(5, 0, true, 55, "before"));
        View header = rows(view).getChildAt(0);
        header.performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        header = rows(view).getChildAt(0);

        view.bind(Collections.singletonList(namedSession(5, 0, true, 55, "after")));

        assertSame(header, rows(view).getChildAt(0));
        assertEquals(2, rows(view).getChildCount());
        assertEquals("after", titleOfHeader(rows(view).getChildAt(0)).getText().toString());
    }

    @Test
    public void structuralRefreshWaitsUntilPointerGestureEnds() {
        SessionsPanelView view = panel(new RecordingListener(), session(1, 0, true, 2, 1));
        view.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1, 1, 0));

        view.bind(Arrays.asList(session(1, 0, true, 2, 1), session(3, 1, false, 4, 1)));
        assertEquals(1, rows(view).getChildCount());

        view.dispatchTouchEvent(MotionEvent.obtain(0, 1, MotionEvent.ACTION_CANCEL, 1, 1, 0));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(2, rows(view).getChildCount());
    }

    private static TextView titleOfHeader(View view) {
        ViewGroup row = (ViewGroup) view;
        return (TextView) ((ViewGroup) row.getChildAt(1)).getChildAt(0);
    }

    private static ViewGroup rows(SessionsPanelView view) {
        return (ViewGroup) ((ViewGroup) view.getChildAt(2)).getChildAt(0);
    }

    private static SessionsPanelView panel(SessionsPanelView.Listener listener,
                                           SessionBrowserModel.Session... sessions) {
        SessionsPanelView view = new SessionsPanelView(ApplicationProvider.getApplicationContext());
        view.setListener(listener);
        view.bind(Arrays.asList(sessions));
        return view;
    }

    private static SessionBrowserModel.Session session(long id, int index, boolean current,
                                                       long firstWindowId, int windows) {
        List<SessionBrowserModel.Window> children = new ArrayList<>();
        for (int i = 0; i < windows; i++) {
            children.add(new SessionBrowserModel.Window(firstWindowId + i, i, i == 0, 0,
                Collections.singletonList(new SessionBrowserModel.Pane("/home", "bash")),
                "bash in home"));
        }
        return new SessionBrowserModel.Session(id, index, current, null, children);
    }

    private static SessionBrowserModel.Session namedSession(long id, int index, boolean current,
                                                            long windowId, String name) {
        SessionBrowserModel.Window window = new SessionBrowserModel.Window(windowId, 0, true, 0,
            Collections.singletonList(new SessionBrowserModel.Pane("/home", "bash")), "bash");
        return new SessionBrowserModel.Session(id, index, current, name,
            Collections.singletonList(window));
    }

    private static final class RecordingListener implements SessionsPanelView.Listener {
        final List<String> events = new ArrayList<>();
        @Override public void onWindowSelected(long sessionId, long windowId) {
            events.add("window:" + sessionId + ':' + windowId);
        }
        @Override public void onSessionClosed(long sessionId) { events.add("close:" + sessionId); }
        @Override public void onSessionRenameRequested(long sessionId) {
            events.add("rename:" + sessionId);
        }
        @Override public void onNewSession() { events.add("new"); }
        @Override public void onNewSessionPrompt() { events.add("newPrompt"); }
    }
}
