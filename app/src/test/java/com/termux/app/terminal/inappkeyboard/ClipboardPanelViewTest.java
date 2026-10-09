package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.termux.R;
import com.termux.app.terminal.ClipboardHistory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** The clipboard panel's rows follow the history, and its controls do what they say. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ClipboardPanelViewTest {

    private static final class MemoryPins implements ClipboardHistory.PinStore {
        @Override public List<String> load() { return new ArrayList<>(); }

        @Override public void save(List<String> pins) { }
    }

    private Context context;
    private ClipboardHistory history;
    private ClipboardPanelView panel;
    private FrameLayout host;
    private final List<String> pasted = new ArrayList<>();
    private int closes;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        history = new ClipboardHistory(new MemoryPins(), Runnable::run);
        panel = new ClipboardPanelView(context,
            new ClipboardPanelView.Palette(0xFFEEEEEE, 0xFF111111, 0xFF555555, 0xFF3366CC), true,
            new ClipboardPanelView.Listener() {
                @Override public void onPasteRequested(String text) { pasted.add(text); }

                @Override public void onCloseRequested() { closes++; }
            });
        host = new FrameLayout(context);
        host.addView(panel, new FrameLayout.LayoutParams(600, 400));
        panel.bind(history);
        // Attach, so the panel listens to the history the way it does inside the keyboard host.
        shadowOf(panel).callOnAttachedToWindow();
    }

    private View byDescription(int stringRes) {
        return byDescription(context.getString(stringRes));
    }

    private View byDescription(String description) {
        View found = find(panel, description);
        assertNotNull(description, found);
        return found;
    }

    private static View find(View root, String description) {
        if (description.contentEquals(String.valueOf(root.getContentDescription()))) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View hit = find(group.getChildAt(i), description);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    @Test
    public void emptyHistoryShowsTheEmptyStateAndNoClear() {
        assertEquals(0, panel.rowCount());
        assertFalse(panel.isClearArmed());
        TextView clear = (TextView) byDescription(R.string.clipboard_panel_clear_description);
        assertEquals(View.GONE, clear.getVisibility());
        assertNotNull(find(panel, context.getString(R.string.clipboard_panel_back_to_keys)));
    }

    @Test
    public void rowsFollowTheHistoryPinnedFirstAndTapPastes() {
        history.record("first");
        history.record("second");
        assertEquals(2, panel.rowCount());

        history.pin("first");
        assertEquals(2, panel.rowCount());
        // The pinned row is first now: its paste description names it.
        View pinnedRow = byDescription(context.getString(R.string.clipboard_panel_paste_item, "first"));
        pinnedRow.performClick();
        assertEquals(1, pasted.size());
        assertEquals("first", pasted.get(0));

        byDescription(R.string.clipboard_panel_unpin).performClick();
        assertEquals(0, history.pinnedCount());
        assertEquals(2, history.recentCount());

        byDescription(R.string.clipboard_panel_remove).performClick();
        assertEquals(1, panel.rowCount());
    }

    @Test
    public void clearTakesTwoTapsKeepsPinsAndDisarmsOnItsOwn() {
        history.record("keep me");
        history.pin("keep me");
        history.record("recent");
        TextView clear = (TextView) byDescription(R.string.clipboard_panel_clear_description);
        assertEquals(View.VISIBLE, clear.getVisibility());

        clear.performClick();
        assertTrue(panel.isClearArmed());
        assertEquals(1, history.recentCount());
        assertEquals(context.getString(R.string.clipboard_panel_clear_confirm), clear.getText().toString());

        shadowOf(Looper.getMainLooper()).idleFor(ClipboardPanelView.CLEAR_ARM_MS + 1, TimeUnit.MILLISECONDS);
        assertFalse("a lone first tap forgets itself", panel.isClearArmed());
        assertEquals(1, history.recentCount());

        clear.performClick();
        clear.performClick();
        assertEquals(0, history.recentCount());
        assertEquals(1, history.pinnedCount());
        assertEquals(1, panel.rowCount());
    }

    @Test
    public void thePillClosesThePanel() {
        byDescription(R.string.clipboard_panel_back_to_keys).performClick();
        assertEquals(1, closes);
    }

    @Test
    public void previewShowsTheFirstLinesOnly() {
        assertEquals("one", ClipboardPanelView.preview("  \n one \n"));
        assertEquals("a b", ClipboardPanelView.preview("a\tb"));
        assertEquals("l1\nl2", ClipboardPanelView.preview("l1\nl2"));
        String cut = ClipboardPanelView.preview("l1\nl2\nl3\nl4");
        assertTrue(cut, cut.startsWith("l1\nl2\n"));
        assertFalse(cut, cut.contains("l4"));
    }
}
