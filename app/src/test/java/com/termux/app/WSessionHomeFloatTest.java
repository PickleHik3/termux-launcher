package com.termux.app;

import android.app.Application;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.app.terminal.HomeFloatLayer;
import com.termux.app.terminal.TerminalPaneController;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A session's row of windows while one of them floats onto Home and back (issue #79): the row is
 * read, as the drawer's rebuild reads it, from inside every change the pane controller announces,
 * so it must be whole whenever the controller calls out.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WSessionHomeFloatTest {

    private RowHost mHost;
    private TerminalPaneController mController;
    private TermuxActivity.WSession mRow;

    @Before
    public void setUp() {
        android.provider.Settings.Global.putFloat(
            RuntimeEnvironment.getApplication().getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        mRow = new TermuxActivity.WSession();
        mHost = new RowHost();
        mController = new TerminalPaneController(mHost,
            new FrameLayout(RuntimeEnvironment.getApplication()),
            LayoutInflater.from(RuntimeEnvironment.getApplication()));
        HomeFloatLayer layer = new HomeFloatLayer(RuntimeEnvironment.getApplication());
        layer.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
        layer.layout(0, 0, 600, 1000);
        mController.setHomeFloatLayer(layer);
        mController.setHomeFloatObstructions(80, 880, -1);
    }

    @Test
    public void ctrlAltFOnTheLastOnePaneWindowOfTheRowFloatsItOntoHome() {
        TerminalPaneController.Window first = mController.newWindow(terminal());
        TerminalPaneController.Window last = mController.newWindow(terminal());
        mRow.windows.addAll(Arrays.asList(first, last));
        mRow.current = 1;
        mController.showWindow(last);

        assertEquals(TerminalPaneController.FLOAT_TOGGLE_FLOATED,
            mController.toggleFloatActivePane());

        assertTrue(last.isHomeFloat());
        assertEquals(Arrays.asList(first), mRow.windows);
        assertEquals("its neighbour takes its place", 0, mRow.current);
        assertSame(first, mRow.currentWindow());
    }

    @Test
    public void floatDockAndFloatAgainKeepsTheRowWhole() {
        TerminalPaneController.Window first = mController.newWindow(terminal());
        TerminalPaneController.Window last = mController.newWindow(terminal());
        mRow.windows.addAll(Arrays.asList(first, last));
        mRow.current = 1;
        mController.showWindow(last);

        assertEquals(TerminalPaneController.FLOAT_TOGGLE_FLOATED,
            mController.toggleFloatActivePane());
        // On the focused Home float the same key docks it back into the row.
        assertEquals(TerminalPaneController.FLOAT_TOGGLE_DOCKED,
            mController.toggleFloatActivePane());
        assertFalse(last.isHomeFloat());
        assertEquals(Arrays.asList(first, last), mRow.windows);
        assertEquals(1, mRow.current);
        mController.showWindow(last);

        assertEquals(TerminalPaneController.FLOAT_TOGGLE_FLOATED,
            mController.toggleFloatActivePane());
        assertTrue(last.isHomeFloat());
        assertEquals(Arrays.asList(first), mRow.windows);
        assertSame(first, mRow.currentWindow());
    }

    @Test
    public void closingAHomeFloatThatLeftTheRowLeavesTheRowReadable() {
        TerminalPaneController.Window first = mController.newWindow(terminal());
        TerminalSession shell = terminal();
        TerminalPaneController.Window last = mController.newWindow(shell);
        mRow.windows.addAll(Arrays.asList(first, last));
        mRow.current = 1;
        mController.showWindow(last);
        mController.toggleFloatActivePane();

        assertEquals(TerminalPaneController.FINISHED_PANE, mController.onSessionFinished(shell));

        assertTrue(mController.homeFloats().isEmpty());
        assertSame(first, mRow.currentWindow());
    }

    @Test
    public void aSelectionPastTheEndOfTheRowReadsTheLastWindow() {
        TerminalPaneController.Window only = mController.newWindow(terminal());
        mRow.windows.add(only);
        mRow.current = 1;

        assertSame(only, mRow.currentWindow());
        assertEquals("the selection is pulled back into the row", 0, mRow.current);
    }

    @Test
    public void anEmptyRowHasNoCurrentWindow() {
        mRow.current = 1;
        assertNull(mRow.currentWindow());
    }

    @Test
    public void aRefusedMoveLeavesTheRowAsItWas() {
        TerminalPaneController.Window first = mController.newWindow(terminal());
        TerminalPaneController.Window last = mController.newWindow(terminal());
        mRow.windows.addAll(Arrays.asList(first, last));
        mRow.current = 1;

        assertEquals(TermuxActivity.WSession.REFUSED, mRow.takeOutFor(last, () -> false));

        assertEquals(Arrays.asList(first, last), mRow.windows);
        assertEquals(1, mRow.current);
    }

    /** The activity's half: the row moves as TermuxActivity moves it, and is read on every change. */
    private final class RowHost implements TerminalPaneController.Host {
        @Override public TerminalSession createShell(String cwd) { return terminal(); }
        @Override public void configurePaneView(TerminalView view) {}
        @Override public void removeShell(TerminalSession session) {}
        @Override public void onActivePaneChanged() {}
        @Override public String defaultCwd() { return "/"; }

        /** As rebuildDrawerSessions reads it. */
        @Override public void onTreesChanged() {
            if (!mRow.windows.isEmpty()) mRow.currentWindow();
        }

        @Override public void onHomeFloatDocked(@NonNull TerminalPaneController.Window window) {
            mRow.dockAfterCurrent(window);
        }

        @Override public boolean onFloatWindowOntoHome(
                @NonNull TerminalPaneController.Window window) {
            return mRow.takeOutFor(window, () -> mController.floatWindowOntoHome(window, 0))
                != TermuxActivity.WSession.REFUSED;
        }
    }

    @NonNull
    private static TerminalSession terminal() {
        return new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);
    }
}
