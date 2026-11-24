package com.termux.app.statusbar;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.ChromeInk;
import com.termux.app.wall.pageswitch.PageBarModel;
import com.termux.app.wall.pageswitch.PageSwitchTargets;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Drives the status bar's Marks line (Status bar card &rsaquo; Page bar): whether the look is
 * Marks, the line's rows and column, the attention the window chips last reported. Moved out of
 * the activity; the activity implements {@link Host} and calls in.
 */
public final class MarksLineController {

    /** What the controller reads of the activity. */
    public interface Host {
        @Nullable TermuxAppSharedPreferences preferences();
        @Nullable View findView(int id);
        boolean isReducedMotionEnabled();
        boolean statusBarVertical();
        @NonNull PageSwitchTargets.Navigator pageSwitchNavigator();
        @NonNull PageBarModel pageBarModel();
        @Nullable ChromeInk chromeInk();
    }

    @NonNull private final Host mHost;

    public MarksLineController(@NonNull Host host) {
        mHost = host;
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T find(int id) {
        return (T) mHost.findView(id);
    }

    /**
     * Whether the status bar marks the pages with the Marks line (Status bar card › Page bar). The
     * chips' visibility decisions AND its negation, so under Marks the chips are never shown.
     */
    public boolean marksLookShown() {
        TermuxAppSharedPreferences preferences = mHost.preferences();
        return preferences != null
            && com.termux.app.wall.pageswitch.PageBarLook.fromValue(preferences.getPageBarLook())
                .marksShown();
    }

    /** Which of the Terminal's windows want the user, by window index, as the chips last said. */
    @Nullable private boolean[] mMarksWindowAttention;
    @Nullable private com.termux.app.wall.pageswitch.PageSwitchTargets.Navigator mMarksNavigator;

    /**
     * The status bar's Marks line (Status bar card › Page bar): under Marks the line of page marks
     * stands where the window chips do, along the row or down the column on a side edge (the chips
     * and their plus are kept away by {@link #marksLookShown}'s share in their own decisions);
     * under Glyphs the line is put away and the chips are left to those decisions. Called wherever the pages, the windows or the bar's form change; the
     * line compares before it rebuilds, so a call with nothing new costs the model and a compare.
     */
    public void syncMarksLine() {
        com.termux.app.statusbar.MarksLineView row = find(R.id.terminal_status_marks_row);
        com.termux.app.statusbar.MarksLineView column =
            find(R.id.terminal_status_marks_column);
        if (row == null || column == null) return;
        boolean marks = marksLookShown();
        boolean vertical = mHost.statusBarVertical();
        int rowShown = marks && !vertical ? View.VISIBLE : View.GONE;
        int columnShown = marks && vertical ? View.VISIBLE : View.GONE;
        if (row.getVisibility() != rowShown) row.setVisibility(rowShown);
        if (column.getVisibility() != columnShown) column.setVisibility(columnShown);
        if (!marks) return;
        com.termux.app.statusbar.MarksLineView line = vertical ? column : row;
        if (mMarksNavigator == null) {
            mMarksNavigator = mHost.pageSwitchNavigator();
            // The slide's tick and its lift read the page bar's own switches.
            java.util.function.BooleanSupplier haptics =
                () -> {
                    TermuxAppSharedPreferences preferences = mHost.preferences();
                    return preferences != null && preferences.isAppHapticsEnabled();
                };
            row.setSlideSupport(haptics, mHost::isReducedMotionEnabled);
            column.setSlideSupport(haptics, mHost::isReducedMotionEnabled);
        }
        line.setNavigator(mMarksNavigator);
        line.setVertical(vertical);
        line.setChromeInk(mHost.chromeInk());
        com.termux.app.wall.pageswitch.PageBarModel model = mHost.pageBarModel();
        line.setPages(model, marksAttention(model));
        line.refreshInk();
    }

    /** Per entry of {@code model}: whether the Terminal window it stands for wants the user; null when none does. */
    @Nullable
    private boolean[] marksAttention(@NonNull com.termux.app.wall.pageswitch.PageBarModel model) {
        boolean[] windows = mMarksWindowAttention;
        if (windows == null) return null;
        boolean[] out = null;
        java.util.List<com.termux.app.wall.pageswitch.PageBarModel.Entry> entries = model.entries();
        for (int i = 0; i < entries.size(); i++) {
            com.termux.app.wall.pageswitch.PageBarModel.Entry entry = entries.get(i);
            int window = entry.number - 1;
            if (entry.kind != com.termux.app.page.PageKind.TERMINAL || window < 0
                || window >= windows.length || !windows[window]) continue;
            if (out == null) out = new boolean[entries.size()];
            out[i] = true;
        }
        return out;
    }

    /** The chips' marks, by window index: asking for the user, a ring or a blocked agent. */
    public void setWindowAttention(
            @NonNull com.termux.app.chrome.WindowChips<
                com.termux.app.terminal.TerminalPaneController.Window> chips,
            @NonNull java.util.List<com.termux.app.terminal.TerminalWindowBar.WindowItem> items) {
        mMarksWindowAttention = windowAttention(chips, items);
    }

    @NonNull
    private static boolean[] windowAttention(
            @NonNull com.termux.app.chrome.WindowChips<
                com.termux.app.terminal.TerminalPaneController.Window> chips,
            @NonNull java.util.List<com.termux.app.terminal.TerminalWindowBar.WindowItem> items) {
        java.util.List<com.termux.app.chrome.WindowChips.Chip<
            com.termux.app.terminal.TerminalPaneController.Window>> list = chips.chips();
        int windows = 0;
        for (com.termux.app.chrome.WindowChips.Chip<
                com.termux.app.terminal.TerminalPaneController.Window> chip : list) {
            if (chip.window != null && chip.kind == com.termux.app.chrome.WindowChips.Kind.WINDOW)
                windows = Math.max(windows, chip.index + 1);
        }
        boolean[] out = new boolean[windows];
        for (int i = 0; i < list.size() && i < items.size(); i++) {
            com.termux.app.chrome.WindowChips.Chip<
                com.termux.app.terminal.TerminalPaneController.Window> chip = list.get(i);
            if (chip.window == null || chip.kind != com.termux.app.chrome.WindowChips.Kind.WINDOW
                || chip.index < 0 || chip.index >= windows) continue;
            com.termux.app.terminal.TerminalWindowBar.WindowItem item = items.get(i);
            out[chip.index] = item.attention
                || item.agentState == com.termux.app.terminal.AgentStatus.State.BLOCKED;
        }
        return out;
    }
}
