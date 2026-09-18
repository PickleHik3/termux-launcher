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
        boolean keyCornerRectOnScreen(String name, Rect out);
    }
    public static final class Target {
        public final String id;
        public final Rect rect;
        public final float radius;
        Target(String id, Rect rect, float radius) {
            this.id = id; this.rect = rect; this.radius = radius;
        }
    }
    /** One extra key: the cap it sits on, what it does, and what a swipe up on it does. */
    public static final class KeyLabel {
        public final Rect rect;
        /** What the key does. */
        public final String primary;
        /** What a swipe up on it does, already arrowed, or null when the key has no second one. */
        public final String secondary;
        /** Both lines, for the measurement signature. */
        public final String text;
        KeyLabel(Rect rect, String primary, String secondary) {
            this.rect = rect;
            this.primary = primary;
            this.secondary = secondary;
            this.text = secondary == null ? primary : primary + "\n" + secondary;
        }
    }
    public static final class Snapshot {
        public final Rect wall;
        public final List<Target> targets = new ArrayList<>();
        public final List<KeyLabel> keys = new ArrayList<>();
        Snapshot(Rect wall) { this.wall = wall; }
        public String signature() {
            StringBuilder s = new StringBuilder(wall.toShortString());
            for (Target t : targets) s.append(t.id).append(t.rect.toShortString()).append(t.radius);
            for (KeyLabel k : keys) s.append(k.rect.toShortString()).append(k.text);
            return s.toString();
        }
    }
    /** The keyboard value the launcher's own settings hang off; the cog is how it is drawn. */
    private static final String SETTINGS_KEY = "config";
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
                // The strip's own bounds are the box: it wraps its chips and its +, so a chip the
                // measurement cannot see on its own is still inside the box drawn round the row.
                ViewGroup strip = (ViewGroup) windows.chipStripView();
                Rect chips = rect(strip);
                if (chips == null) {
                    for (int i = 0; i < strip.getChildCount(); i++) chips = union(chips, rect(strip.getChildAt(i)));
                }
                add(s, "windows", chips, radius(strip));
            }
            stats(s);
        }
        // The whole bar, not the peeking place icon at its end: the gestures the hint names are
        // made anywhere along it, and a box on one small icon read as being about that icon.
        View host = finder.findHelpView(R.id.terminal_window_bar_host);
        add(s, "status", rect(host), radius(host));
        // Launcher settings live on a keyboard corner rather than in the chrome, so every place
        // points at the cog itself: the box is the glyph, which is the thing the user swipes off.
        add(s, "settings", keyCornerRect(SETTINGS_KEY), 0);
        if (place == PaneWallPage.TERMINAL) {
            add(s, "sessions", finder.findHelpView(R.id.terminal_sessions_indicator));
            if (finder.paneCount() > 1) {
                View divider = tagged(root);
                add(s, "divider", divider);
            }
            // A dock that is a rail down one side reads off its own measured rect: whoever draws
            // help asks which edge of the wall it is past, so nothing here has to say.
            add(s, "dock", firstShown(R.id.apps_bar_viewpager, R.id.place_apps_bar_host));
            add(s, "az", firstOfType(root, AzScrubRowView.class));
            paneCorner(s);
            // The row as one box, for the topic that is about the row. Each key keeps its own
            // label drawn on its own cap: the box says which row, the labels say which key.
            ExtraKeysView row = firstOfType(root, ExtraKeysView.class);
            add(s, "keys", rect(row), radius(row));
            // The row that was just measured, not a second walk for it: one search, one answer.
            extraKeys(row, s);
            // Two keyboard controls of their own: the prefix keys that start the chords, and the
            // space bar with its swipes. Each is measured on the keys it is about.
            add(s, "prefix", union(keyRect("ctrl"), keyRect("alt")), 0);
            add(s, "space", keyRect("space"), 0);
        } else if (place == PaneWallPage.DISPLAY) {
            DisplayScaleRailView rail = firstOfType(root, DisplayScaleRailView.class);
            if (rail != null && rail.isRailShown()) add(s, "scale", localRect(rail, rail.helpBounds()),
                radius(rail));
            add(s, "touchpad", firstOfType(root, DisplayTouchpadView.class));
            add(s, "start", finder.findHelpView(R.id.x11_pane_start));
            // Only out while the empty state names a missing package; that visibility is the
            // readiness flag already applied to the view, so nothing here re-checks the prefix.
            add(s, "setup", finder.findHelpView(R.id.x11_pane_guide));
        } else {
            WidgetGridView grid = firstOfType(root, WidgetGridView.class);
            if (grid != null) {
                for (int i = 0; i < grid.getChildCount(); i++) {
                    View child = grid.getChildAt(i);
                    if (rect(child) == null) continue;
                    add(s, "widget", child);
                    break;
                }
                add(s, "empty", localRect(grid, largestEmptyRegion(grid)), radius(grid));
            }
        }
        return s;
    }
    /**
     * One corner zone of the pane the user is on: the square a corner hold has to land in, so the
     * box is where the gesture is made rather than a guess at it. It is not a hit area - a tap
     * there goes through to the program, and only the hold belongs to the corner.
     */
    private void paneCorner(Snapshot s) {
        Rect pane = rect(finder.activePane());
        if (pane == null) return;
        float density = context.getResources().getDisplayMetrics().density;
        int size = Math.round(com.termux.app.chrome.CornerZones.clampSize(
            com.termux.app.chrome.CornerZones.paneSizePx(density), pane.width(), pane.height()));
        if (size <= 0) return;
        add(s, "corners", new Rect(pane.left, pane.top, pane.left + size, pane.top + size), 0);
    }

    /** The widgets that are out, as one box: the topic is about the cluster, not about CPU. */
    private void stats(Snapshot s) {
        int[] ids = {R.id.terminal_status_widget_cpu, R.id.terminal_status_widget_ram, R.id.terminal_status_widget_weather};
        Rect bounds = null;
        for (int id : ids) bounds = union(bounds, rect(finder.findHelpView(id)));
        add(s, "stats", bounds, radius(finder.findHelpView(R.id.terminal_status_stats_cluster)));
    }
    private void extraKeys(View view, Snapshot s) {
        if (rect(view) == null || view == overlay) return;
        if (view instanceof ExtraKeysView) {
            ExtraKeysView row = (ExtraKeysView) view;
            int measured = 0, defined = 0;
            for (int i = 0; i < row.getChildCount(); i++) {
                Rect r = rect(row.getChildAt(i));
                ExtraKeyButton key = row.definitionForChild(i);
                if (r != null) measured++;
                if (key != null) defined++;
                if (r == null || key == null) continue;
                String secondary = key.getPopup() == null ? null
                    : context.getString(R.string.help_key_secondary,
                        HelpCopy.keyLabel(context, key.getPopup()));
                s.keys.add(new KeyLabel(r, HelpCopy.keyLabel(context, key), secondary));
            }
            HelpLog.d("extra keys: " + row.getChildCount() + " caps, " + measured + " measured, "
                + defined + " defined, " + s.keys.size() + " labelled");
            if (measured < row.getChildCount()) {
                // Which test each unmeasured cap fails, so a phone can say why its key cards are gone.
                StringBuilder why = new StringBuilder("caps unmeasured:");
                for (int i = 0; i < row.getChildCount(); i++) {
                    View cap = row.getChildAt(i);
                    if (rect(cap) != null) continue;
                    Rect local = new Rect();
                    why.append(' ').append(i).append('[').append(cap.getClass().getSimpleName())
                        .append(" shown=").append(cap.isShown()).append(" vis=").append(cap.getVisibility())
                        .append(" alpha=").append(cap.getAlpha()).append(" size=").append(cap.getWidth())
                        .append('x').append(cap.getHeight()).append(" local=")
                        .append(cap.getLocalVisibleRect(local)).append(local.toShortString()).append(']');
                    int[] at = new int[2]; cap.getLocationOnScreen(at);
                    why.append("@").append(at[0]).append(',').append(at[1]);
                }
                HelpLog.d(why.toString());
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
    private void add(Snapshot s, String id, View view) { add(s, id, rect(view), radius(view)); }
    private void add(Snapshot s, String id, Rect rect, float radius) {
        if (rect == null || rect.isEmpty()) { HelpLog.d("omit " + id + ": not visible"); return; }
        s.targets.add(new Target(id, rect, radius));
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
    /**
     * Where a view is on the overlay, clipped to every ancestor's bounds and to the overlay. Built
     * from {@link View#getLocationOnScreen} and the view's size rather than from
     * {@link View#getLocalVisibleRect}: on the phone the caps of the extra keys row answered the
     * latter with a rectangle half a million pixels to the right while drawing exactly where the
     * former says, which is how the row and its seven key cards went missing from the guide.
     */
    Rect rect(View view) {
        if (view == null || !view.isShown() || view.getAlpha() <= 0 || view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        int[] source = new int[2], origin = new int[2];
        view.getLocationOnScreen(source); overlay.getLocationOnScreen(origin);
        Rect r = new Rect(source[0] - origin[0], source[1] - origin[1],
            source[0] - origin[0] + view.getWidth(), source[1] - origin[1] + view.getHeight());
        // A child scrolled or slid out of an ancestor is not on screen even though it is laid out.
        for (android.view.ViewParent p = view.getParent(); p instanceof View; p = p.getParent()) {
            View ancestor = (View) p;
            if (ancestor == overlay) break;
            ancestor.getLocationOnScreen(source);
            Rect bounds = new Rect(source[0] - origin[0], source[1] - origin[1],
                source[0] - origin[0] + ancestor.getWidth(), source[1] - origin[1] + ancestor.getHeight());
            if (!r.intersect(bounds)) return null;
        }
        return r.intersect(0, 0, overlay.getWidth(), overlay.getHeight()) && !r.isEmpty() ? r : null;
    }
    private Rect localRect(View view, Rect local) {
        if (view == null || local == null || local.isEmpty() || !view.isShown()) return null;
        int[] source = new int[2], origin = new int[2];
        view.getLocationOnScreen(source); overlay.getLocationOnScreen(origin);
        Rect r = new Rect(local); r.offset(source[0]-origin[0],source[1]-origin[1]);
        return r.intersect(0,0,overlay.getWidth(),overlay.getHeight()) ? r : null;
    }
    /** A corner glyph, with room round it: a box drawn tight on a cog reads as part of the cog. */
    private Rect keyCornerRect(String name) {
        Rect r = new Rect();
        if (!finder.keyCornerRectOnScreen(name, r) || r.isEmpty()) return null;
        int[] origin = new int[2]; overlay.getLocationOnScreen(origin); r.offset(-origin[0],-origin[1]);
        int breathing = Math.round(4 * context.getResources().getDisplayMetrics().density);
        r.inset(-breathing, -breathing);
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
