package com.termux.app.help;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import com.termux.R;
import com.termux.app.AzScrubRowView;
import com.termux.app.launcher.widget.WidgetCellRect;
import com.termux.app.launcher.widget.WidgetGridMetrics;
import com.termux.app.launcher.widget.WidgetGridView;
import com.termux.app.terminal.TerminalActionDispatcher;
import com.termux.app.terminal.TerminalKeyBindingResolver;
import com.termux.app.terminal.TerminalWindowBar;
import com.termux.app.wall.PaneWallPage;
import com.termux.app.x11.DisplayScaleRailView;
import com.termux.app.x11.DisplayTouchpadView;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import java.util.ArrayList;
import java.util.List;

/** Reacquires visible controls on each layout; never retains a chrome view across passes. */
public final class HelpTargets {
    public interface ViewFinder {
        View findHelpView(int id);
        View activePane();
        int paneCount();
        boolean keyRectOnScreen(String name, Rect out);
    }
    public static final class Target {
        public final String id;
        public final Rect rect;
        public final float radius;
        public final HelpCopy copy;
        Target(String id, Rect rect, float radius, HelpCopy copy) {
            this.id = id; this.rect = rect; this.radius = radius; this.copy = copy;
        }
    }
    public static final class KeyLabel {
        public final Rect rect;
        public final String text;
        KeyLabel(Rect rect, String text) { this.rect = rect; this.text = text; }
    }
    public static final class Snapshot {
        public final Rect wall;
        public final List<Target> targets = new ArrayList<>();
        public final List<KeyLabel> keys = new ArrayList<>();
        Snapshot(Rect wall) { this.wall = wall; }
        public String signature() {
            StringBuilder s = new StringBuilder(wall.toShortString());
            for (Target t : targets) s.append(t.id).append(t.rect.toShortString())
                .append(t.radius).append(t.copy.title).append(t.copy.body);
            for (KeyLabel k : keys) s.append(k.rect.toShortString()).append(k.text);
            return s.toString();
        }
    }
    private final ViewFinder finder;
    private final View overlay;
    private final Context context;

    public HelpTargets(ViewFinder finder, View overlay) {
        this.finder = finder; this.overlay = overlay; this.context = overlay.getContext();
    }
    public Snapshot measure(PaneWallPage place) {
        Rect wall = rect(finder.findHelpView(R.id.terminal_pane_wall));
        if (wall == null) wall = new Rect(0, 0, overlay.getWidth(), overlay.getHeight());
        Snapshot s = new Snapshot(wall);
        View root = finder.findHelpView(android.R.id.content);
        View bar = finder.findHelpView(R.id.terminal_window_bar);
        if (place != PaneWallPage.WIDGETS) {
            if (bar instanceof TerminalWindowBar) {
                TerminalWindowBar windows = (TerminalWindowBar) bar;
                // The chips and the + are one box and one hint: the + is the chip strip's own
                // trailing button, and a quick reference reads better as one line than two.
                Rect chips = null;
                ViewGroup strip = (ViewGroup) windows.chipStripView();
                for (int i = 0; i < strip.getChildCount(); i++) chips = union(chips, rect(strip.getChildAt(i)));
                add(s, "windows", chips, radius(strip), place == PaneWallPage.TERMINAL
                    ? copy(R.string.help_windows_title, R.string.help_windows_body)
                    : copy(R.string.help_display_apps_title, R.string.help_display_apps_body));
            }
            stats(s);
        }
        // The whole bar, not the peeking place icon at its end: the gestures the hint names are
        // made anywhere along it, and a box on one small icon read as being about that icon.
        View host = finder.findHelpView(R.id.terminal_window_bar_host);
        add(s, "status", rect(host), radius(host), copy(R.string.help_status_title, R.string.help_status_body));
        if (place == PaneWallPage.TERMINAL) {
            add(s, "sessions", finder.findHelpView(R.id.terminal_sessions_indicator),
                copy(R.string.help_sessions_title, R.string.help_sessions_body));
            if (finder.paneCount() > 1) {
                View divider = tagged(root);
                add(s, "divider", divider, copy(R.string.help_divider_title, R.string.help_divider_body));
            }
            View dock = firstShown(R.id.apps_bar_viewpager, R.id.dock_rail_scroll);
            add(s, "dock", dock, copy(R.string.help_dock_title,
                dock != null && dock.getId() == R.id.dock_rail_scroll ? R.string.help_rail_body : R.string.help_dock_body));
            add(s, "az", firstOfType(root, AzScrubRowView.class), copy(R.string.help_az_title, R.string.help_az_body));
            extraKeys(root, s);
            // Two keyboard hints: the prefix keys with the chords they start, and the space bar
            // with its swipes. Each is boxed on the keys it is about.
            Rect prefix = union(keyRect("ctrl"), keyRect("alt"));
            if (prefix != null) {
                String chords = chords();
                String hold = context.getString(R.string.help_prefix_body);
                add(s, "prefix", prefix, 0, new HelpCopy(context.getString(R.string.help_prefix_title),
                    chords.isEmpty() ? hold : hold + "\n" + chords));
            }
            add(s, "space", keyRect("space"), 0, copy(R.string.help_space_title, R.string.help_space_body));
        } else if (place == PaneWallPage.DISPLAY) {
            DisplayScaleRailView rail = firstOfType(root, DisplayScaleRailView.class);
            if (rail != null && rail.isRailShown()) add(s, "scale", localRect(rail, rail.helpBounds()),
                radius(rail), copy(R.string.help_scale_title, R.string.help_scale_body));
            add(s, "touchpad", firstOfType(root, DisplayTouchpadView.class),
                copy(R.string.help_pad_title, R.string.help_pad_one, R.string.help_pad_two, R.string.help_pad_three));
            add(s, "start", finder.findHelpView(R.id.x11_pane_start), copy(R.string.help_start_title, R.string.help_start_body));
        } else {
            WidgetGridView grid = firstOfType(root, WidgetGridView.class);
            if (grid != null) {
                for (int i = 0; i < grid.getChildCount(); i++) {
                    View child = grid.getChildAt(i);
                    if (rect(child) == null) continue;
                    add(s, "widget", child, copy(R.string.help_widget_title, R.string.help_widget_body));
                    break;
                }
                add(s, "empty", localRect(grid, largestEmptyRegion(grid)), radius(grid),
                    copy(R.string.help_empty_title, R.string.help_empty_body));
            }
        }
        return s;
    }
    private String chords() {
        String[] tools = {"pane.split", "window.new", "session.new"};
        int[] sentences = {R.string.help_split_chord, R.string.help_window_chord, R.string.help_session_chord};
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < tools.length; i++) {
            List<String> strokes = TerminalKeyBindingResolver.getInstance().getStrokesForTool(tools[i],
                TerminalActionDispatcher.getInstance().actionContext());
            if (strokes.isEmpty()) continue;
            String stroke = strokes.get(0);
            if (result.length() > 0) result.append('\n');
            result.append(context.getString(sentences[i], displayChord(stroke)));
        }
        return result.toString();
    }
    static String displayChord(String stroke) {
        StringBuilder out = new StringBuilder();
        for (String key : stroke.split("\\+")) {
            if (out.length() > 0) out.append(", ");
            if (!key.isEmpty()) out.append(Character.toUpperCase(key.charAt(0))).append(key.substring(1));
        }
        return out.toString();
    }
    private void stats(Snapshot s) {
        int[] ids = {R.id.terminal_status_widget_cpu, R.id.terminal_status_widget_ram, R.id.terminal_status_widget_weather};
        int[] labels = {R.string.help_stat_cpu, R.string.help_stat_ram, R.string.help_stat_weather};
        Rect bounds = null;
        StringBuilder title = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            Rect r = rect(finder.findHelpView(ids[i]));
            if (r == null) continue;
            bounds = union(bounds, r);
            if (title.length() > 0) title.append(context.getString(R.string.help_stat_separator));
            title.append(context.getString(labels[i]));
        }
        add(s, "stats", bounds, radius(finder.findHelpView(R.id.terminal_status_stats_cluster)),
            new HelpCopy(title.toString(), context.getString(R.string.help_stats_body)));
    }
    private void extraKeys(View view, Snapshot s) {
        if (rect(view) == null || view == overlay) return;
        if (view instanceof ExtraKeysView) {
            ExtraKeysView row = (ExtraKeysView) view;
            for (int i = 0; i < row.getChildCount(); i++) {
                Rect r = rect(row.getChildAt(i));
                ExtraKeyButton key = row.definitionForChild(i);
                if (r == null || key == null) continue;
                String label = HelpCopy.keyLabel(context, key);
                if (key.getPopup() != null) label += "\n" + context.getString(R.string.help_key_secondary,
                    HelpCopy.keyLabel(context, key.getPopup()));
                s.keys.add(new KeyLabel(r, label));
            }
            return;
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            extraKeys(((ViewGroup) view).getChildAt(i), s);
    }
    /** Largest rectangle of unoccupied cells; ties stay at the first reading-order cell. */
    static Rect largestEmptyRegion(WidgetGridView grid) {
        WidgetGridMetrics metrics = grid.metrics();
        int rows = metrics.definition().rows, columns = metrics.definition().columns;
        int[][] occupied = new int[rows+1][columns+1];
        for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++) {
            Rect cell = metrics.boundsFor(new WidgetCellRect(x,y,x+1,y+1));
            boolean filled = false;
            for (int i = 0; i < grid.getChildCount(); i++) {
                View child = grid.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE && Rect.intersects(cell,
                    new Rect(child.getLeft(), child.getTop(), child.getRight(), child.getBottom()))) {
                    filled = true; break;
                }
            }
            occupied[y+1][x+1] = (filled ? 1 : 0) + occupied[y][x+1] + occupied[y+1][x] - occupied[y][x];
        }
        Rect best = null;
        long area = 0;
        for (int top=0; top<rows; top++) for (int left=0; left<columns; left++)
            for (int bottom=top+1; bottom<=rows; bottom++) for (int right=left+1; right<=columns; right++) {
                if (occupied[bottom][right]-occupied[top][right]-occupied[bottom][left]+occupied[top][left] != 0) continue;
                Rect r = metrics.boundsFor(new WidgetCellRect(left,top,right,bottom));
                long size = (long) r.width()*r.height();
                if (size > area) { area = size; best = r; }
            }
        return best;
    }
    private HelpCopy copy(int title, int... lines) { return HelpCopy.of(context, title, lines); }
    private void add(Snapshot s, String id, View view, HelpCopy copy) { add(s,id,rect(view),radius(view),copy); }
    private void add(Snapshot s, String id, Rect rect, float radius, HelpCopy copy) {
        if (rect == null || rect.isEmpty()) { HelpLog.d("omit " + id + ": not visible"); return; }
        s.targets.add(new Target(id, rect, radius, copy));
    }
    private View firstShown(int... ids) {
        for (int id : ids) { View view = finder.findHelpView(id); if (rect(view) != null) return view; }
        return null;
    }
    public <T extends View> T firstOfType(View view, Class<T> type) {
        if (view == overlay || rect(view) == null) return null;
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            T found = firstOfType(((ViewGroup) view).getChildAt(i),type);
            if (found != null) return found;
        }
        return null;
    }
    private View tagged(View view) {
        if (view == overlay || rect(view) == null) return null;
        if (Boolean.TRUE.equals(view.getTag(R.id.help_split_divider))) return view;
        if (view instanceof ViewGroup) for (int i=0; i<((ViewGroup)view).getChildCount(); i++) {
            View found = tagged(((ViewGroup)view).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    Rect rect(View view) {
        if (view == null || !view.isShown() || view.getAlpha() <= 0 || view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        Rect local = new Rect();
        if (!view.getLocalVisibleRect(local)) return null;
        return localRect(view,local);
    }
    private Rect localRect(View view, Rect local) {
        if (view == null || local == null || local.isEmpty() || !view.isShown()) return null;
        int[] source = new int[2], origin = new int[2];
        view.getLocationOnScreen(source); overlay.getLocationOnScreen(origin);
        Rect r = new Rect(local); r.offset(source[0]-origin[0],source[1]-origin[1]);
        return r.intersect(0,0,overlay.getWidth(),overlay.getHeight()) ? r : null;
    }
    private Rect keyRect(String name) {
        Rect r = new Rect();
        if (!finder.keyRectOnScreen(name,r) || r.isEmpty()) return null;
        int[] origin = new int[2]; overlay.getLocationOnScreen(origin); r.offset(-origin[0],-origin[1]);
        return r.intersect(0,0,overlay.getWidth(),overlay.getHeight()) ? r : null;
    }
    private static Rect union(Rect a, Rect b) {
        if (a == null) return b == null ? null : new Rect(b);
        if (b != null) a.union(b);
        return a;
    }
    private static float radius(View view) {
        if (view == null || view.getOutlineProvider() == null) return 0;
        Outline outline = new Outline(); view.getOutlineProvider().getOutline(view,outline);
        return Math.max(0,outline.getRadius());
    }
}
