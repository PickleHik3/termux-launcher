package com.termux.app.statusbar;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.ChromeInk;
import com.termux.app.wall.pageswitch.MarksZoomPlan;
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
        /** The scheme's accent as the launcher's chrome resolves it (the Glyphs look's too). */
        int marksAccent();
        /** Where a Linux app's icon and the program glyphs' face come from; null for none. */
        @Nullable com.termux.app.wall.pageswitch.PageMarkIcons pageMarkIcons();
        /** Global's tint, alpha included, as it lies over what is beneath. */
        int globalGlassTint();
        /** Global's glass at {@code cornerRadiusPx} for {@code anchor}, over the stored wallpaper frame; null for none. */
        @Nullable android.graphics.drawable.Drawable globalGlass(float cornerRadiusPx,
                                                                 @NonNull View anchor);
        /** Where a held window mark's controls go; null and no mark opens them. */
        @Nullable default MarksLineView.WindowControls windowControls() {
            return null;
        }
    }

    /** The title capsule's glass, asked of the host at each touch-down. */
    @NonNull private final MarksLineView.TitleGlass mTitleGlass = new MarksLineView.TitleGlass() {
        @Override public int tint() {
            return mHost.globalGlassTint();
        }

        @Nullable @Override
        public android.graphics.drawable.Drawable ground(float cornerRadiusPx, @NonNull View anchor) {
            return mHost.globalGlass(cornerRadiusPx, anchor);
        }
    };

    @NonNull private final Host mHost;

    /** Global's glass for a title capsule, as the line's is: the window chips' controls wear it too. */
    @NonNull
    public MarksLineView.TitleGlass titleGlass() {
        return mTitleGlass;
    }

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
        boolean marks = marksLookShown();
        boolean vertical = mHost.statusBarVertical();
        // The workspace pages' line stands in the Terminal's strip with the window chips it
        // replaces: it slides with the Terminal and rests out of sight on Home, where the pill
        // above the dock marks the pages (TopStackRenderer is the one writer of that slide).
        syncLine(R.id.terminal_status_marks_row, R.id.terminal_status_marks_column, marks,
            vertical);
        syncHomeLine(marks);
    }

    /**
     * Home's pill's line, fed as the status bar's is (the pane shows or hides it with the look):
     * the navigator, haptics and reduced motion, the scheme's accent, the icons, the zoom's ground
     * and the pages with their attention. Its ink is the pill's glass, which the pill tells it.
     */
    private void syncHomeLine(boolean marks) {
        MarksLineView line = find(R.id.widget_page_marks);
        if (line == null) return;
        line.setTitleHost(marks ? mHomeTitle : null);
        if (!marks) return;
        feed(line, false);
    }

    /**
     * Home's status bar as the title host of Home's pill: the page's name at the bar's right end
     * and the bar's readings faded, only while that bar is on screen along a row. With Home's bar
     * hidden (the new default) {@link #roomPx} is 0 and the pill shows no title.
     */
    @NonNull private final MarksLineView.TitleHost mHomeTitle = new MarksLineView.TitleHost() {
        @Nullable @Override public View readings() {
            return mHost.findView(R.id.terminal_status_widgets);
        }

        @Override public float roomPx() {
            View bar = mHost.findView(R.id.terminal_window_bar_host);
            View title = mHost.findView(R.id.terminal_status_home_title);
            View row = mHost.findView(R.id.terminal_status_row);
            if (bar == null || title == null || row == null || mHost.statusBarVertical()
                || !bar.isShown()) return 0f;
            View readings = readings();
            return MarksZoomPlan.titleMaxPx(readings == null || !readings.isShown() ? 0f
                : readings.getWidth(), row.getWidth());
        }

        @Override public void show(@NonNull String text, int color, float sizePx, float progress) {
            View view = mHost.findView(R.id.terminal_status_home_title);
            if (!(view instanceof android.widget.TextView)) return;
            android.widget.TextView title = (android.widget.TextView) view;
            if (!text.contentEquals(title.getText())) title.setText(text);
            if (title.getCurrentTextColor() != color) title.setTextColor(color);
            if (title.getTextSize() != sizePx) {
                title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sizePx);
            }
            View parent = view.getParent() instanceof View ? (View) view.getParent() : null;
            float travel = parent == null ? 0f
                : Math.max(0f, parent.getWidth() - title.getWidth() - 2f * title.getPaddingEnd());
            title.setTranslationX(-progress * travel);
            if (title.getVisibility() != View.VISIBLE) title.setVisibility(View.VISIBLE);
        }

        @Nullable @Override public View titleView() {
            return mHost.findView(R.id.terminal_status_home_title);
        }

        @Override public void dress(@Nullable android.graphics.drawable.Drawable ground) {
            View view = mHost.findView(R.id.terminal_status_home_title);
            if (view != null) view.setBackground(ground);
        }

        @Override public void fade(float alpha) {
            View view = mHost.findView(R.id.terminal_status_home_title);
            if (view != null && view.getAlpha() != alpha) view.setAlpha(alpha);
        }

        @Override public void clear() {
            View view = mHost.findView(R.id.terminal_status_home_title);
            if (view == null) return;
            if (view.getVisibility() != View.INVISIBLE) view.setVisibility(View.INVISIBLE);
            view.setTranslationX(0f);
            view.setAlpha(1f);
        }
    };

    /**
     * The one rule for which of the line's views stands: under Marks the row or the column for
     * the bar's form; otherwise both are gone. Where the Terminal's strip is on screen is the
     * top stack's, not this rule's.
     *
     * @param column whether this is the column's view rather than the row's
     */
    public static int lineVisibility(boolean marks, boolean vertical, boolean column) {
        return marks && vertical == column ? View.VISIBLE : View.GONE;
    }

    private void syncLine(int rowId, int columnId, boolean marks, boolean vertical) {
        com.termux.app.statusbar.MarksLineView row = find(rowId);
        com.termux.app.statusbar.MarksLineView column = find(columnId);
        if (row == null || column == null) return;
        int rowShown = lineVisibility(marks, vertical, false);
        int columnShown = lineVisibility(marks, vertical, true);
        if (row.getVisibility() != rowShown) row.setVisibility(rowShown);
        if (column.getVisibility() != columnShown) column.setVisibility(columnShown);
        if (!marks) return;
        com.termux.app.statusbar.MarksLineView line = vertical ? column : row;
        feed(line, vertical);
        line.setChromeInk(mHost.chromeInk());
        line.refreshInk();
    }

    /** What a line that marks the pages is given, wherever it stands. */
    private void feed(@NonNull MarksLineView line, boolean vertical) {
        if (mMarksNavigator == null) mMarksNavigator = mHost.pageSwitchNavigator();
        if (!line.hasSlideSupport()) {
            // The slide's tick and its lift read the page bar's own switches.
            line.setSlideSupport(() -> {
                TermuxAppSharedPreferences preferences = mHost.preferences();
                return preferences != null && preferences.isAppHapticsEnabled();
            }, mHost::isReducedMotionEnabled);
        }
        line.setNavigator(mMarksNavigator);
        line.setWindowControls(mHost.windowControls());
        line.setVertical(vertical);
        line.setAccent(mHost.marksAccent());
        line.setMarkIcons(mHost.pageMarkIcons());
        line.setTitleGlass(mTitleGlass);
        com.termux.app.wall.pageswitch.PageBarModel model = mHost.pageBarModel();
        line.setPages(model, marksAttention(model));
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
