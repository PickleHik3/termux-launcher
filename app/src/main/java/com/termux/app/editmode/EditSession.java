package com.termux.app.editmode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.look.TerlLookStore;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One visit to edit mode: every change applies at once, and this keeps the history of the visit
 * (spec "Edit mode and Settings", History). One slider drag is one Undo step, from where the finger
 * landed to where it lifted; Follow Global, a Tint colour, an icon pack, Revert and a Look pick are
 * one step each, so Undo right after Revert brings the session back and Undo after a Look pick
 * restores the old Look and every own value it cleared. The history ends with the session.
 *
 * <p>The stores stay the truth (ADR 0041): the look store, and the launcher's text size and icon
 * pack through {@link Launcher}. A setting the session wrote that now holds something else was
 * written from outside (launcherctl, terl.conf, Settings, the Assistant) and is the outside's: Undo
 * and Revert skip it, it leaves {@link #changeCount()}, and it is counted in
 * {@link #keptElsewhereCount()} until the session changes it again.
 *
 * <p>Pure: no views, no Android. Used from the main thread.
 */
public final class EditSession {

    /** The launcher's settings the Global card changes beside the look. */
    public interface Launcher {
        /** The shared text size, in percent. */
        int textSizePercent();

        /** Writes the text size, clamped to its range, and has the screen follow at once. */
        void setTextSizePercent(int percent);

        /** The icon pack's package, "" for the system's icons. */
        @NonNull
        String iconPack();

        /** Writes the icon pack and has the launcher's icons follow at once. */
        void setIconPack(@NonNull String pack);
    }

    /**
     * Home's settings the Home card changes: its grid (columns and the label size, for icons,
     * folders and widgets alike), the page dots, and the edge Home's A&#8211;Z letters stand on.
     */
    public interface Home {
        int MIN_COLUMNS = 2;
        int MAX_COLUMNS = 8;
        /** {@link #azEdge} and {@link #setAzEdge}: the letters are not shown. */
        String AZ_OFF = "off";
        String AZ_LEFT = "left";
        String AZ_RIGHT = "right";
        String AZ_BOTTOM = "bottom";
        /** {@link #azEdge} only: v1's top edge, which no chip offers. */
        String AZ_TOP = "top";
        /** {@link #labels}: the label sizes, as Settings stores them. */
        String LABEL_SMALL = "small";
        String LABEL_DEFAULT = "default";
        String LABEL_LARGE = "large";

        /** Whether the launcher has a Home (it has none under Use Terl as Terminal). */
        boolean available();

        /** The grid's columns, {@link #MIN_COLUMNS} to {@link #MAX_COLUMNS}. */
        int columns();

        /** Writes the columns, clamped, and has Home follow at once. */
        void setColumns(int columns);

        /** The label size: {@link #LABEL_SMALL}, {@link #LABEL_DEFAULT} or {@link #LABEL_LARGE}. */
        @NonNull
        String labels();

        void setLabels(@NonNull String size);

        /** Whether the page dots show under the grid. */
        boolean pageDots();

        void setPageDots(boolean on);

        /** Where the A&#8211;Z letters stand: {@link #AZ_OFF}, {@link #AZ_LEFT}, {@link #AZ_RIGHT} or {@link #AZ_BOTTOM}. */
        @NonNull
        String azEdge();

        /** Moves the letters (bare, no glass) and has Home follow at once. */
        void setAzEdge(@NonNull String edge);

        /** Calls {@code tap} when a finger taps empty Home (not a drag, not an icon or widget); null stops. */
        void onEmptyTap(@Nullable Runnable tap);

        /** Opens where Home is switched back on (Settings, Use Terl as). */
        void change();
    }

    /** The wallpaper and colour settings the Wallpaper &amp; colours card changes. */
    public interface Wallpaper {
        /** Opens the wallpaper picker; the picture it sets is not part of the session's history. */
        void changePicture();

        /** The app's theme: "system", "false" (light) or "true" (dark), as night-mode stores it. */
        @NonNull
        String theme();

        /** Wears the theme at once; the screen may be recreated for it. */
        void setTheme(@NonNull String theme);

        /** The dim over the wallpaper behind the pages, 0 to 100. */
        int dim();

        /** Writes the dim, clamped, and has the screen follow at once. */
        void setDim(int percent);

        /** Whether the wallpaper drifts as you move between pages. */
        boolean drift();

        void setDrift(boolean on);

        /** Whether the chrome takes its colours from the wallpaper (off: from the terminal scheme). */
        boolean coloursFromWallpaper();

        /** Writes the choice; the screen may be recreated for it. */
        void setColoursFromWallpaper(boolean on);
    }

    /** One setting a card row changes. */
    public static final class Setting {
        private enum Kind { OWN, CUSTOM, CORNERS, TINT, LOOK, TEXT_SIZE, ICON_PACK, THEME, DIM, DRIFT,
            COLOURS,
            COLUMNS, LABELS, PAGE_DOTS, AZ_EDGE }

        /** Global's corner radius, in dp. */
        public static final Setting CORNERS = new Setting(Kind.CORNERS, null, null);
        /** The launcher's text size, in percent. */
        public static final Setting TEXT_SIZE = new Setting(Kind.TEXT_SIZE, null, null);
        static final Setting TINT = new Setting(Kind.TINT, null, null);
        static final Setting LOOK = new Setting(Kind.LOOK, null, null);
        static final Setting ICON_PACK = new Setting(Kind.ICON_PACK, null, null);
        /** The app's theme (light, dark or the system's). */
        static final Setting THEME = new Setting(Kind.THEME, null, null);
        /** The dim over the wallpaper, in percent. */
        public static final Setting DIM = new Setting(Kind.DIM, null, null);
        static final Setting DRIFT = new Setting(Kind.DRIFT, null, null);
        static final Setting COLOURS = new Setting(Kind.COLOURS, null, null);
        /** Home's grid columns. */
        public static final Setting COLUMNS = new Setting(Kind.COLUMNS, null, null);
        static final Setting LABELS = new Setting(Kind.LABELS, null, null);
        static final Setting PAGE_DOTS = new Setting(Kind.PAGE_DOTS, null, null);
        static final Setting AZ_EDGE = new Setting(Kind.AZ_EDGE, null, null);

        @NonNull private final Kind kind;
        @Nullable private final TerlSurface surface;
        @Nullable private final TerlProperty property;

        private Setting(@NonNull Kind kind, @Nullable TerlSurface surface,
                        @Nullable TerlProperty property) {
            this.kind = kind;
            this.surface = surface;
            this.property = property;
        }

        /** A surface's own value; following Global when it holds none. */
        @NonNull
        public static Setting own(@NonNull TerlSurface surface, @NonNull TerlProperty property) {
            return new Setting(Kind.OWN, surface, property);
        }

        /** Custom's Opacity or Blur, for all glass. */
        @NonNull
        public static Setting custom(@NonNull TerlProperty property) {
            return new Setting(Kind.CUSTOM, null, property);
        }

        /** Whether it is an Opacity or Blur slider, which the legibility meter follows. */
        public boolean changesGlassLegibility() {
            return (kind == Kind.OWN || kind == Kind.CUSTOM)
                && (property == TerlProperty.OPACITY || property == TerlProperty.BLUR);
        }

        /** Whether a slider writes it, a number at a time. */
        private boolean isNumber() {
            return kind == Kind.OWN || kind == Kind.CUSTOM || kind == Kind.CORNERS
                || kind == Kind.TEXT_SIZE || kind == Kind.DIM || kind == Kind.COLUMNS;
        }

        /** The setting as text, for {@link EditSession#save()}. */
        @NonNull
        private String encode() {
            return kind.name() + (surface == null ? "" : ":" + surface.name())
                + (property == null ? "" : ":" + property.name());
        }

        @Nullable
        private static Setting decode(@NonNull String text) {
            String[] parts = text.split(":");
            try {
                Kind kind = Kind.valueOf(parts[0]);
                switch (kind) {
                    case OWN:
                        return parts.length == 3
                            ? new Setting(kind, TerlSurface.valueOf(parts[1]),
                                TerlProperty.valueOf(parts[2])) : null;
                    case CUSTOM:
                        return parts.length == 2
                            ? new Setting(kind, null, TerlProperty.valueOf(parts[1])) : null;
                    case CORNERS: return CORNERS;
                    case TINT: return TINT;
                    case LOOK: return LOOK;
                    case TEXT_SIZE: return TEXT_SIZE;
                    case ICON_PACK: return ICON_PACK;
                    case THEME: return THEME;
                    case DIM: return DIM;
                    case DRIFT: return DRIFT;
                    case COLOURS: return COLOURS;
                    case COLUMNS: return COLUMNS;
                    case LABELS: return LABELS;
                    case PAGE_DOTS: return PAGE_DOTS;
                    case AZ_EDGE: return AZ_EDGE;
                    default: return null;
                }
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (!(other instanceof Setting)) return false;
            Setting that = (Setting) other;
            return kind == that.kind && surface == that.surface && property == that.property;
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, surface, property);
        }
    }

    /** One Undo step: each setting it moved, from (index 0) and to (index 1). */
    private static final class Step {
        final Map<Setting, Object[]> rows = new LinkedHashMap<>();
    }

    @NonNull private final TerlLookStore mStore;
    @Nullable private final Launcher mLauncher;
    @Nullable private final Wallpaper mWallpaper;
    @Nullable private final Home mHome;
    /**
     * What each setting the session touched held when the session (or the outside) last owned it.
     * A value is an Integer or a String; null is an own value's absence (it follows Global).
     */
    private final Map<Setting, Object> mBaseline = new HashMap<>();
    /** What the session last wrote to each setting it still owns. */
    private final Map<Setting, Object> mLastWritten = new HashMap<>();
    /** Settings the session changed that were written from outside since. */
    private final Set<Setting> mKeptElsewhere = new HashSet<>();
    private final Deque<Step> mUndo = new ArrayDeque<>();
    /** Drags in progress: where each started. */
    private final Map<Setting, Object> mDragStart = new HashMap<>();
    private boolean mEnded;
    /** A write of the session's own is landing in a store. */
    private boolean mWriting;

    private EditSession(@NonNull TerlLookStore store, @Nullable Launcher launcher,
                        @Nullable Wallpaper wallpaper, @Nullable Home home) {
        mStore = store;
        mLauncher = launcher;
        mWallpaper = wallpaper;
        mHome = home;
    }

    /** Opens a session over the look store alone: text size and icon pack are left alone. */
    @NonNull
    public static EditSession begin(@NonNull TerlLookStore store) {
        return new EditSession(store, null, null, null);
    }

    /** Opens a session over {@code store} and the launcher's settings: nothing changed yet. */
    @NonNull
    public static EditSession begin(@NonNull TerlLookStore store, @Nullable Launcher launcher) {
        return new EditSession(store, launcher, null, null);
    }

    /** Opens a session that also changes the wallpaper's dim and drift, the theme and the colours. */
    @NonNull
    public static EditSession begin(@NonNull TerlLookStore store, @Nullable Launcher launcher,
                                    @Nullable Wallpaper wallpaper) {
        return new EditSession(store, launcher, wallpaper, null);
    }

    /** Opens a session that also changes Home's grid, page dots and A&#8211;Z edge. */
    @NonNull
    public static EditSession begin(@NonNull TerlLookStore store, @Nullable Launcher launcher,
                                    @Nullable Wallpaper wallpaper, @Nullable Home home) {
        return new EditSession(store, launcher, wallpaper, home);
    }

    // ---------------------------------------------------------------- sliders

    /** A finger landed on the setting's slider: the start of one Undo step. */
    public void dragStarted(@NonNull Setting setting) {
        if (!editable(setting) || !setting.isNumber()) return;
        reconcile();
        mDragStart.put(setting, read(setting));
    }

    /**
     * Writes {@code value} to the setting, at once. Inside a drag it moves the drag along; on its
     * own it is a step of its own.
     */
    public void set(@NonNull Setting setting, int value) {
        if (!editable(setting) || !setting.isNumber()) return;
        boolean alone = !mDragStart.containsKey(setting);
        if (alone) dragStarted(setting);
        else reconcile();
        write(setting, value);
        if (alone) dragEnded(setting);
    }

    /** The finger lifted: the drag becomes one Undo step, unless it ended where it started. */
    public void dragEnded(@NonNull Setting setting) {
        if (!editable(setting)) return;
        reconcile();
        if (!mDragStart.containsKey(setting)) return;
        Object from = mDragStart.remove(setting);
        if (!mLastWritten.containsKey(setting)) return;
        Object to = read(setting);
        if (Objects.equals(to, from)) return;
        push(setting, from, to);
    }

    public void dragStarted(@NonNull TerlSurface surface, @NonNull TerlProperty property) {
        dragStarted(Setting.own(surface, property));
    }

    /** Gives the surface an own value: {@link #set(Setting, int)} on {@link Setting#own}. */
    public void set(@NonNull TerlSurface surface, @NonNull TerlProperty property, int value) {
        set(Setting.own(surface, property), value);
    }

    public void dragEnded(@NonNull TerlSurface surface, @NonNull TerlProperty property) {
        dragEnded(Setting.own(surface, property));
    }

    // ---------------------------------------------------------------- one-step changes

    /** Clears the surface's own value, as one step. One already following Global is left alone. */
    public void followGlobal(@NonNull TerlSurface surface, @NonNull TerlProperty property) {
        Setting setting = Setting.own(surface, property);
        if (!editable(setting)) return;
        reconcile();
        if (read(setting) == null) return;
        oneStep(setting, null);
    }

    /** Sets Global's Tint colour, as one step. */
    public void setTint(@NonNull String tint) {
        if (!editable(Setting.TINT)) return;
        reconcile();
        oneStep(Setting.TINT, tint);
    }

    /** Sets the icon pack ("" for the system's icons), as one step. */
    public void setIconPack(@NonNull String pack) {
        if (!editable(Setting.ICON_PACK)) return;
        reconcile();
        oneStep(Setting.ICON_PACK, pack);
    }

    /** Wears the theme ("system", "false" for light, "true" for dark), as one step. */
    public void setTheme(@NonNull String theme) {
        if (!editable(Setting.THEME)) return;
        reconcile();
        oneStep(Setting.THEME, theme);
    }

    /** Switches the wallpaper's drift, as one step. */
    public void setDrift(boolean on) {
        if (!editable(Setting.DRIFT)) return;
        reconcile();
        oneStep(Setting.DRIFT, on ? 1 : 0);
    }

    /** Sets the label size on Home's grid, as one step. */
    public void setLabels(@NonNull String size) {
        if (!editable(Setting.LABELS)) return;
        reconcile();
        oneStep(Setting.LABELS, size);
    }

    /** Switches Home's page dots, as one step. */
    public void setPageDots(boolean on) {
        if (!editable(Setting.PAGE_DOTS)) return;
        reconcile();
        oneStep(Setting.PAGE_DOTS, on ? 1 : 0);
    }

    /** Moves Home's A&#8211;Z letters ({@link Home#AZ_OFF}, left, right or bottom), as one step. */
    public void setAzEdge(@NonNull String edge) {
        if (!editable(Setting.AZ_EDGE)) return;
        reconcile();
        oneStep(Setting.AZ_EDGE, edge);
    }

    /** Switches the colours from the wallpaper, as one step. */
    public void setColoursFromWallpaper(boolean on) {
        if (!editable(Setting.COLOURS)) return;
        reconcile();
        oneStep(Setting.COLOURS, on ? 1 : 0);
    }

    /**
     * Picks a Look ({@link TerlLookStore#pick}): unconfirmed while a surface holds an own value it
     * changes nothing and names what it would clear, so the card can ask. Applied, it is one step:
     * the Look and every own value it cleared, which Undo puts back together.
     */
    @NonNull
    public TerlLookStore.Pick pick(@NonNull String lookId, boolean confirmed) {
        if (mEnded) return mStore.pick(lookId, confirmed);
        reconcile();
        Object fromLook = read(Setting.LOOK);
        List<TerlLookStore.OwnValue> own = mStore.ownValues();
        boolean applies = mStore.lookIds().contains(lookId) && (confirmed || own.isEmpty());
        if (!applies) return mStore.pick(lookId, confirmed);

        // The settings the pick moves, with what each will hold, recorded before the store writes
        // and tells its listeners.
        Map<Setting, Object[]> moves = new LinkedHashMap<>();
        if (!lookId.equals(fromLook)) moves.put(Setting.LOOK, new Object[] {fromLook, lookId});
        for (TerlLookStore.OwnValue value : own) {
            moves.put(Setting.own(value.surface, value.property), new Object[] {value.value, null});
        }
        for (Map.Entry<Setting, Object[]> move : moves.entrySet()) {
            claim(move.getKey(), move.getValue()[1]);
        }
        TerlLookStore.Pick pick;
        mWriting = true;
        try {
            pick = mStore.pick(lookId, true);
        } finally {
            mWriting = false;
        }
        Step step = new Step();
        for (Map.Entry<Setting, Object[]> move : moves.entrySet()) {
            Object to = read(move.getKey());
            mLastWritten.put(move.getKey(), to);
            step.rows.put(move.getKey(), new Object[] {move.getValue()[0], to});
        }
        if (!step.rows.isEmpty()) mUndo.push(step);
        return pick;
    }

    // ---------------------------------------------------------------- history

    public boolean canUndo() {
        if (mEnded) return false;
        reconcile();
        return !mUndo.isEmpty();
    }

    /** Reverses the latest step still the session's; settings written from outside since are skipped. */
    public void undo() {
        if (mEnded) return;
        reconcile();
        Step step = mUndo.poll();
        if (step == null) return;
        for (Map.Entry<Setting, Object[]> entry : step.rows.entrySet()) {
            write(entry.getKey(), entry.getValue()[0]);
        }
    }

    /**
     * Puts every setting the session changed back to what it held when the session opened, as one
     * step. Settings written from outside since are skipped.
     */
    public void revert() {
        if (mEnded) return;
        reconcile();
        Step step = new Step();
        for (Map.Entry<Setting, Object> entry : new HashMap<>(mLastWritten).entrySet()) {
            Setting setting = entry.getKey();
            if (!mBaseline.containsKey(setting)) continue;
            Object base = mBaseline.get(setting);
            Object now = entry.getValue();
            if (Objects.equals(base, now)) continue;
            step.rows.put(setting, new Object[] {now, base});
            write(setting, base);
        }
        if (!step.rows.isEmpty()) mUndo.push(step);
    }

    /** How many settings this session changed and still holds changed. */
    public int changeCount() {
        if (mEnded) return 0;
        reconcile();
        int count = 0;
        for (Map.Entry<Setting, Object> entry : mLastWritten.entrySet()) {
            if (!Objects.equals(entry.getValue(), mBaseline.get(entry.getKey()))) count++;
        }
        return count;
    }

    /** How many settings this session changed were then set elsewhere, and kept as set there. */
    public int keptElsewhereCount() {
        if (mEnded) return 0;
        reconcile();
        return mKeptElsewhere.size();
    }

    /** Edit mode closed: the changes stay, the history goes. */
    public void end() {
        mEnded = true;
        mUndo.clear();
        mBaseline.clear();
        mLastWritten.clear();
        mKeptElsewhere.clear();
        mDragStart.clear();
    }

    // ---------------------------------------------------------------- surviving a recreate

    /**
     * The visit as text, for the window that is about to be rebuilt (a theme change recreates it):
     * the history, the baselines and what is kept elsewhere. Null once the session has ended.
     */
    @Nullable
    public String save() {
        if (mEnded) return null;
        reconcile();
        try {
            JSONObject out = new JSONObject();
            out.put("baseline", pairs(mBaseline));
            out.put("written", pairs(mLastWritten));
            JSONArray kept = new JSONArray();
            for (Setting setting : mKeptElsewhere) kept.put(setting.encode());
            out.put("kept", kept);
            JSONArray steps = new JSONArray();
            for (Step step : mUndo) {
                JSONArray rows = new JSONArray();
                for (Map.Entry<Setting, Object[]> row : step.rows.entrySet()) {
                    JSONArray one = new JSONArray();
                    one.put(row.getKey().encode());
                    one.put(row.getValue()[0] == null ? JSONObject.NULL : row.getValue()[0]);
                    one.put(row.getValue()[1] == null ? JSONObject.NULL : row.getValue()[1]);
                    rows.put(one);
                }
                steps.put(rows);
            }
            out.put("undo", steps);
            return out.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Opens a session over the stores as {@link #save()} left it: the same history and baselines.
     * What the stores hold now is checked as for any session, so a setting written elsewhere in
     * between is the outside's. Null when {@code saved} cannot be read.
     */
    @Nullable
    public static EditSession restore(@NonNull String saved, @NonNull TerlLookStore store,
                                      @Nullable Launcher launcher, @Nullable Wallpaper wallpaper) {
        return restore(saved, store, launcher, wallpaper, null);
    }

    /** As {@link #restore(String, TerlLookStore, Launcher, Wallpaper)}, with Home's settings too. */
    @Nullable
    public static EditSession restore(@NonNull String saved, @NonNull TerlLookStore store,
                                      @Nullable Launcher launcher, @Nullable Wallpaper wallpaper,
                                      @Nullable Home home) {
        EditSession session = new EditSession(store, launcher, wallpaper, home);
        try {
            JSONObject in = new JSONObject(saved);
            if (!session.readPairs(in.getJSONArray("baseline"), session.mBaseline)
                || !session.readPairs(in.getJSONArray("written"), session.mLastWritten)) return null;
            JSONArray kept = in.getJSONArray("kept");
            for (int i = 0; i < kept.length(); i++) {
                Setting setting = Setting.decode(kept.getString(i));
                if (setting == null) return null;
                session.mKeptElsewhere.add(setting);
            }
            JSONArray steps = in.getJSONArray("undo");
            for (int i = 0; i < steps.length(); i++) {
                Step step = new Step();
                JSONArray rows = steps.getJSONArray(i);
                for (int j = 0; j < rows.length(); j++) {
                    JSONArray one = rows.getJSONArray(j);
                    Setting setting = Setting.decode(one.getString(0));
                    if (setting == null) return null;
                    step.rows.put(setting, new Object[] {value(one, 1), value(one, 2)});
                }
                session.mUndo.addLast(step);
            }
        } catch (JSONException e) {
            return null;
        }
        session.reconcile();
        return session;
    }

    @NonNull
    private static JSONArray pairs(@NonNull Map<Setting, Object> map) throws JSONException {
        JSONArray out = new JSONArray();
        for (Map.Entry<Setting, Object> entry : map.entrySet()) {
            JSONArray one = new JSONArray();
            one.put(entry.getKey().encode());
            one.put(entry.getValue() == null ? JSONObject.NULL : entry.getValue());
            out.put(one);
        }
        return out;
    }

    private boolean readPairs(@NonNull JSONArray in, @NonNull Map<Setting, Object> into)
            throws JSONException {
        for (int i = 0; i < in.length(); i++) {
            JSONArray one = in.getJSONArray(i);
            Setting setting = Setting.decode(one.getString(0));
            if (setting == null) return false;
            into.put(setting, value(one, 1));
        }
        return true;
    }

    /** An Integer, a String or null. */
    @Nullable
    private static Object value(@NonNull JSONArray row, int index) throws JSONException {
        if (row.isNull(index)) return null;
        Object raw = row.get(index);
        if (raw instanceof Number) return ((Number) raw).intValue();
        return raw.toString();
    }

    // ---------------------------------------------------------------- the stores

    private boolean editable(@NonNull Setting setting) {
        if (mEnded) return false;
        switch (setting.kind) {
            case OWN: return setting.surface != null && setting.surface.acceptsOwn;
            case TEXT_SIZE:
            case ICON_PACK: return mLauncher != null;
            case THEME:
            case DIM:
            case DRIFT:
            case COLOURS: return mWallpaper != null;
            case COLUMNS:
            case LABELS:
            case PAGE_DOTS:
            case AZ_EDGE: return mHome != null && mHome.available();
            default: return true;
        }
    }

    /** What the setting holds now: an Integer, a String, or null for an own value's absence. */
    @Nullable
    private Object read(@NonNull Setting setting) {
        switch (setting.kind) {
            case OWN:
                return mStore.holdsOwn(setting.surface, setting.property)
                    ? (Object) mStore.value(setting.surface, setting.property) : null;
            case CUSTOM: return mStore.customValue(setting.property);
            case CORNERS: return mStore.cornerRadiusDp();
            case TINT: return mStore.tint();
            case LOOK: return mStore.look();
            case TEXT_SIZE: return mLauncher == null ? null : mLauncher.textSizePercent();
            case ICON_PACK: return mLauncher == null ? null : mLauncher.iconPack();
            case THEME: return mWallpaper == null ? null : mWallpaper.theme();
            case DIM: return mWallpaper == null ? null : mWallpaper.dim();
            case DRIFT: return mWallpaper == null ? null : (mWallpaper.drift() ? 1 : 0);
            case COLOURS:
                return mWallpaper == null ? null : (mWallpaper.coloursFromWallpaper() ? 1 : 0);
            case COLUMNS: return mHome == null ? null : mHome.columns();
            case LABELS: return mHome == null ? null : mHome.labels();
            case PAGE_DOTS: return mHome == null ? null : (mHome.pageDots() ? 1 : 0);
            case AZ_EDGE: return mHome == null ? null : mHome.azEdge();
            default: return null;
        }
    }

    private void apply(@NonNull Setting setting, @Nullable Object value) {
        switch (setting.kind) {
            case OWN:
                if (value == null) mStore.followGlobal(setting.surface, setting.property);
                else mStore.setOwn(setting.surface, setting.property, (Integer) value);
                break;
            case CUSTOM: mStore.setCustomValue(setting.property, (Integer) value); break;
            case CORNERS: mStore.setCornerRadiusDp((Integer) value); break;
            case TINT: mStore.setTint((String) value); break;
            case LOOK: mStore.wear((String) value); break;
            case TEXT_SIZE: if (mLauncher != null) mLauncher.setTextSizePercent((Integer) value); break;
            case ICON_PACK: if (mLauncher != null) mLauncher.setIconPack((String) value); break;
            case THEME: if (mWallpaper != null) mWallpaper.setTheme((String) value); break;
            case DIM: if (mWallpaper != null) mWallpaper.setDim((Integer) value); break;
            case DRIFT: if (mWallpaper != null) mWallpaper.setDrift((Integer) value != 0); break;
            case COLOURS:
                if (mWallpaper != null) mWallpaper.setColoursFromWallpaper((Integer) value != 0);
                break;
            case COLUMNS: if (mHome != null) mHome.setColumns((Integer) value); break;
            case LABELS: if (mHome != null) mHome.setLabels((String) value); break;
            case PAGE_DOTS: if (mHome != null) mHome.setPageDots((Integer) value != 0); break;
            case AZ_EDGE: if (mHome != null) mHome.setAzEdge((String) value); break;
            default: break;
        }
    }

    /** What a write of {@code value} will leave, as near as the session can tell before it lands. */
    @Nullable
    private static Object expected(@NonNull Setting setting, @Nullable Object value) {
        if (value instanceof Integer && setting.property != null) {
            return setting.property.clamp((Integer) value);
        }
        if (value instanceof Integer && setting.kind == Setting.Kind.DIM) {
            return Math.max(0, Math.min(100, (Integer) value));
        }
        if (value instanceof Integer && setting.kind == Setting.Kind.COLUMNS) {
            return Math.max(Home.MIN_COLUMNS, Math.min(Home.MAX_COLUMNS, (Integer) value));
        }
        return value;
    }

    /** The session takes the setting over: its baseline is kept, and it is no longer the outside's. */
    private void claim(@NonNull Setting setting, @Nullable Object expected) {
        if (!mBaseline.containsKey(setting)) mBaseline.put(setting, read(setting));
        mKeptElsewhere.remove(setting);
        mLastWritten.put(setting, expected(setting, expected));
    }

    private void write(@NonNull Setting setting, @Nullable Object value) {
        // The stores tell their listeners during the write, and the screen asks this session then:
        // what the write will leave is recorded first, and nothing is reconciled until it lands.
        claim(setting, value);
        mWriting = true;
        try {
            apply(setting, value);
        } finally {
            mWriting = false;
        }
        // What the store holds after the write, so it compares equal later.
        mLastWritten.put(setting, read(setting));
    }

    private void oneStep(@NonNull Setting setting, @Nullable Object value) {
        Object from = read(setting);
        write(setting, value);
        Object to = read(setting);
        if (!Objects.equals(from, to)) push(setting, from, to);
    }

    private void push(@NonNull Setting setting, @Nullable Object from, @Nullable Object to) {
        Step step = new Step();
        step.rows.put(setting, new Object[] {from, to});
        mUndo.push(step);
    }

    /**
     * Hands every setting written from outside since the session last wrote it to the outside: its
     * baseline becomes what it holds now, and every step forgets it.
     */
    private void reconcile() {
        if (mWriting) return;
        Iterator<Map.Entry<Setting, Object>> written = mLastWritten.entrySet().iterator();
        List<Setting> handed = new ArrayList<>();
        while (written.hasNext()) {
            Map.Entry<Setting, Object> entry = written.next();
            Setting setting = entry.getKey();
            Object now = read(setting);
            if (Objects.equals(now, entry.getValue())) continue;
            written.remove();
            mBaseline.put(setting, now);
            mKeptElsewhere.add(setting);
            if (mDragStart.containsKey(setting)) mDragStart.put(setting, now);
            handed.add(setting);
        }
        if (handed.isEmpty()) return;
        Iterator<Step> steps = mUndo.iterator();
        while (steps.hasNext()) {
            Step step = steps.next();
            for (Setting setting : handed) step.rows.remove(setting);
            if (step.rows.isEmpty()) steps.remove();
        }
    }
}
