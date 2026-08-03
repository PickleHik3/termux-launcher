package com.termux.app.statusbar;

import android.app.Application;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.termux.app.terminal.SessionBrowserModel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SessionsPanelViewTest {

    @Test
    public void renameButton_dispatchesRenameForItsOwnRow() {
        RecordingListener listener = new RecordingListener();
        SessionsPanelView view = panel(listener, session(0, false), session(1, true));

        buttonWith(view, "Rename session 2").performClick();

        assertEquals(Collections.singletonList("rename:1"), listener.events);
    }

    @Test
    public void closeButton_stillDispatchesCloseAndNotRename() {
        // Both buttons are built from the same template and added back to back, so a swapped
        // add order or a copy-pasted listener would silently retarget one of them.
        RecordingListener listener = new RecordingListener();
        SessionsPanelView view = panel(listener, session(0, true), session(1, false));

        buttonWith(view, "Close session 1").performClick();
        buttonWith(view, "Rename session 1").performClick();

        assertEquals(Arrays.asList("close:0", "rename:0"), listener.events);
    }

    @Test
    public void everyRowCarriesItsOwnIndexedContentDescriptions() {
        SessionsPanelView view = panel(new RecordingListener(),
            session(0, true), session(1, false), session(2, false));

        for (int index = 0; index < 3; index++) {
            assertNotNull(buttonWith(view, "Rename session " + (index + 1)));
            assertNotNull(buttonWith(view, "Close session " + (index + 1)));
        }
    }

    @Test
    public void currentRowTitle_marqueesOnlyWhenItOverflowsByAReadableAmount() {
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 200; i++) tooLong.append("session ");

        SessionsPanelView overflowing = panel(new RecordingListener(),
            named(0, true, tooLong.toString()));
        assertEquals(TextUtils.TruncateAt.MARQUEE, currentTitle(overflowing).getEllipsize());

        SessionsPanelView fitting = panel(new RecordingListener(), named(0, true, "hi"));
        assertEquals(TextUtils.TruncateAt.END, currentTitle(fitting).getEllipsize());
    }

    @Test
    public void nonCurrentRowsNeverMarquee() {
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 200; i++) tooLong.append("session ");

        SessionsPanelView view = panel(new RecordingListener(),
            named(0, false, tooLong.toString()), named(1, true, "now"));

        assertEquals(TextUtils.TruncateAt.END, titleOfRow(view, 0).getEllipsize());
    }

    /** The bound row's title: first child of the row's vertical text block. */
    private static TextView currentTitle(SessionsPanelView view) {
        return titleOfRow(view, 0);
    }

    private static TextView titleOfRow(SessionsPanelView view, int rowIndex) {
        ViewGroup rows = (ViewGroup) ((ViewGroup) view.getChildAt(2)).getChildAt(0);
        ViewGroup row = (ViewGroup) rows.getChildAt(rowIndex);
        return (TextView) ((ViewGroup) row.getChildAt(1)).getChildAt(0);
    }

    private static SessionBrowserModel.Session named(int index, boolean current, String name) {
        return new SessionBrowserModel.Session(index, current, name,
            Collections.singletonList(new SessionBrowserModel.Window(0, true, 0,
                Collections.singletonList(new SessionBrowserModel.Pane("/home", "bash")))));
    }

    private static SessionsPanelView panel(SessionsPanelView.Listener listener,
                                           SessionBrowserModel.Session... sessions) {
        SessionsPanelView view = new SessionsPanelView(
            ApplicationProvider.getApplicationContext());
        view.setListener(listener);
        view.bind(Arrays.asList(sessions));
        return view;
    }

    private static SessionBrowserModel.Session session(int index, boolean current) {
        return new SessionBrowserModel.Session(index, current, null,
            Collections.singletonList(new SessionBrowserModel.Window(0, true, 0,
                Collections.singletonList(new SessionBrowserModel.Pane("/home", "bash")))));
    }

    /** The row buttons are built in code and unaddressable by id, so match on what a11y sees. */
    private static View buttonWith(View root, String contentDescription) {
        List<View> found = new ArrayList<>();
        collect(root, contentDescription, found);
        assertTrue("no view described as \"" + contentDescription + '"', !found.isEmpty());
        assertEquals("ambiguous content description", 1, found.size());
        return found.get(0);
    }

    private static void collect(View view, String contentDescription, List<View> found) {
        CharSequence described = view.getContentDescription();
        if (described != null && contentDescription.contentEquals(described)) found.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++)
            collect(group.getChildAt(i), contentDescription, found);
    }

    private static final class RecordingListener implements SessionsPanelView.Listener {
        final List<String> events = new ArrayList<>();

        @Override public void onSessionSelected(int index) { events.add("select:" + index); }
        @Override public void onSessionClosed(int index) { events.add("close:" + index); }
        @Override public void onSessionRenameRequested(int index) { events.add("rename:" + index); }
        @Override public void onNewSession() { events.add("new"); }
        @Override public void onNewSessionPrompt() { events.add("newPrompt"); }
    }
}
