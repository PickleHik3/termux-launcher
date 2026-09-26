package com.termux.app.place;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Where the launcher keeps its arrangement. Anything that decides what is on screen and where is a
 * property of the orientation, and one arrangement per orientation is shared by every place — the
 * widget grid, the terminal and the Linux display all stand in the same chrome (ADR 0003).
 *
 * <p>Keys are {@code layout.<portrait|landscape>.<key>} for the arrangement and
 * {@code place.<home|terminal|display>.<key>} for the two things a place still remembers of its own:
 * whether it was left with the keyboard up, and whether it is in minimal mode. A missing arrangement key falls back to the shared value
 * the launcher used to keep globally, and then to the shipped default, so nothing has to be written
 * before the chrome reads the way it always looked.
 *
 * <p>The arrangement used to be kept per place as well as per orientation. Version 6 of the
 * migration folds that into the shared keys, seeding each orientation from the place the value
 * showed on — the terminal's for everything the terminal draws — and then drops every place's own
 * copy. The same step folds the place-scoped look overrides into the shared look, since those were
 * kept in this file's key scheme too.
 *
 * <p>No Android views here, on purpose: this is a resolver over {@link SharedPreferences} and it is
 * tested as one.
 */
public final class PlaceLayoutStore {

    /** Bumped when a new set of old keys has to be folded into the current ones. */
    @VisibleForTesting static final int MIGRATION_VERSION = 6;

    @VisibleForTesting static final String KEY_MIGRATED = "place.migrated";

    private static final String PREFIX = "place.";
    private static final String LAYOUT_PREFIX = "layout.";

    /** Where a place kept its own value for a shared look key, before the look was one look. */
    private static final String LEGACY_LOOK_INFIX = ".look.";

    private static final String KEY_STATUS_BAR = Element.STATUS.storageKey();
    private static final String KEY_APPS_ROW = Element.APPS.storageKey();
    private static final String KEY_AZ_ROW = "az_row";
    private static final String KEY_AZ_BAR = Element.AZ.storageKey();
    private static final String KEY_EXTRA_KEYS = Element.EXTRA_KEYS.storageKey();

    /**
     * An element's position in its edge's stack sits beside its placement:
     * {@code layout.<orientation>.<placement key>_order}. Absent means the stack the launcher has
     * always drawn ({@link Element#defaultOrder}), so an updated install renders identically
     * without anything being written for it.
     */
    private static final String ORDER_SUFFIX = "_order";

    /** A row placement that is not an edge at all. */
    private static final String VALUE_HIDDEN = "hidden";
    private static final String KEY_KEYBOARD_MODE = "keyboard_mode";
    private static final String KEY_KEYBOARD_FORM = "keyboard_form";
    private static final String KEY_WIDGET_COLUMNS = "widget_columns";
    private static final String KEY_WIDGET_ROWS = "widget_rows";

    private static final String KEY_DOCK_HEIGHT = "dock_height";
    private static final String KEY_KEYBOARD_HEIGHT = "keyboard_height";
    private static final String KEY_KEYBOARD_CHIN = "keyboard_chin";

    private static final String KEY_STATUS_COMPACT = "status_compact";

    /**
     * Where the bar has never been rested in landscape, it rests compact: it is the orientation
     * with the least height and the most of it already spoken for. A default, never a write.
     */
    private static final boolean LANDSCAPE_RESTS_COMPACT = true;

    private static final String KEY_KEYBOARD_OPEN = "keyboard_open";
    /** Minimal mode, kept beside the keyboard memory it overrides: {@code place.<p>.minimal}. */
    private static final String KEY_MINIMAL = "minimal";
    private static final String KEY_KEYBOARD_FLOAT_X = "keyboard_float_x";
    private static final String KEY_KEYBOARD_FLOAT_Y = "keyboard_float_y";

    /**
     * The per-place choice of how the keyboard came back on entry. Gone with version 6: Home always
     * comes back closed and the other two as they were left, so there is nothing left to choose.
     */
    private static final String LEGACY_KEY_KEYBOARD_ON_ENTER = "keyboard_on_enter";

    /** A floating keyboard that has never been moved has no remembered place to come back to. */
    public static final float FLOAT_POSITION_UNSET = -1f;

    /** The launcher's status-bar hide switch for the display, dropped with the hidden state. */
    private static final String LEGACY_KEY_X11_HIDE_STATUS_BAR = "x11_hide_status_bar";

    private static final String[] ARRANGEMENT_KEYS = {
        KEY_STATUS_BAR, KEY_APPS_ROW, KEY_AZ_ROW, KEY_AZ_BAR, KEY_EXTRA_KEYS, KEY_KEYBOARD_MODE,
        KEY_KEYBOARD_FORM, KEY_WIDGET_COLUMNS, KEY_WIDGET_ROWS,
        KEY_DOCK_HEIGHT, KEY_KEYBOARD_HEIGHT, KEY_KEYBOARD_CHIN,
        // The stack positions ride with the placements they belong to, so the Layout editor's
        // Discard and its reset put a re-order back the same way they put a move back.
        orderKeyName(Element.STATUS), orderKeyName(Element.APPS), orderKeyName(Element.AZ),
        orderKeyName(Element.EXTRA_KEYS)
    };

    /**
     * Everything version 6 folds out of the per-place keys, per orientation: the arrangement, and
     * the two things remembered beside it per orientation — the bar's resting state and where a
     * floating keyboard was parked.
     */
    private static final String[] FOLDED_KEYS = concat(ARRANGEMENT_KEYS,
        KEY_STATUS_COMPACT, KEY_KEYBOARD_FLOAT_X, KEY_KEYBOARD_FLOAT_Y);

    /** The unscoped key one element's stack position is stored under. */
    @VisibleForTesting
    @NonNull
    static String orderKeyName(@NonNull Element element) {
        return element.storageKey() + ORDER_SUFFIX;
    }

    @NonNull private final TermuxAppSharedPreferences mPreferences;
    @Nullable private final SharedPreferences mStore;

    private int mRevision;

    /**
     * Any write to the launcher's preferences can change what the layout resolves to — the shared
     * keys and the global ones a missing key falls back to alike — so every one of them retires a
     * cached layout.
     */
    private final SharedPreferences.OnSharedPreferenceChangeListener mChangeListener =
        (preferences, key) -> mRevision++;

    public PlaceLayoutStore(@NonNull TermuxAppSharedPreferences preferences) {
        mPreferences = preferences;
        mStore = preferences.getSharedPreferences();
        migrateIfNeeded();
        if (mStore != null) mStore.registerOnSharedPreferenceChangeListener(mChangeListener);
    }

    /**
     * Counts changes to anything a resolved layout is a function of. A caller that holds a
     * {@link PlaceLayout} can keep it while this has not moved.
     */
    public int revision() {
        return mRevision;
    }

    // ---------------------------------------------------------------- arrangement

    /**
     * The one immutable answer for an orientation, which every place stands in. The getters below
     * say where a row is arranged to go; this is where the switches that can still turn a row off
     * entirely are folded in, so a caller reads one effective layout and nothing else.
     */
    @NonNull
    public PlaceLayout resolve(@NonNull PlaceOrientation orientation) {
        EnumMap<Element, Slot> slots = new EnumMap<>(Element.class);
        for (Element element : Element.values()) slots.put(element, slot(orientation, element));
        // The terminal's own toolbar switch can still have put the extra keys away everywhere.
        if (!mPreferences.shouldShowTerminalToolbar()) {
            slots.put(Element.EXTRA_KEYS, slots.get(Element.EXTRA_KEYS).withHidden(true));
        }
        return new PlaceLayout(slots,
            keyboardMode(orientation),
            keyboardForm(orientation),
            widgetColumns(orientation),
            widgetRows(orientation));
    }

    // ---------------------------------------------------------------- slots

    /**
     * Where one element stands in an orientation, as the store holds it: the placement key it has
     * always had, and the sibling order key beside it. The toolbar switch is not folded in here —
     * {@link #resolve} does that — so a slot read back is exactly what was written.
     */
    @NonNull
    public Slot slot(@NonNull PlaceOrientation orientation, @NonNull Element element) {
        int order = slotOrder(orientation, element);
        switch (element) {
            case STATUS:
                // Never hidden: the wall's pager rides it, so it only ever moves.
                return new Slot(false, statusBarEdge(orientation), order);
            case AZ:
                return new Slot(!azRowShown(orientation), azBarEdge(orientation), order);
            case APPS:
            case EXTRA_KEYS:
            default:
                return new Slot(VALUE_HIDDEN.equals(readString(orientation,
                    element == Element.APPS ? KEY_APPS_ROW : KEY_EXTRA_KEYS)),
                    elementEdge(orientation, element), order);
        }
    }

    /** Moves one element: its edge, whether it is put away, and where it sits in the stack. */
    public void setSlot(@NonNull PlaceOrientation orientation, @NonNull Element element,
                        @NonNull Slot slot) {
        switch (element) {
            case STATUS:
                setStatusBarEdge(orientation, slot.edge);
                break;
            case AZ:
                setAzRowShown(orientation, !slot.hidden);
                setAzBarEdge(orientation, slot.edge);
                break;
            case APPS:
                writeString(orientation, KEY_APPS_ROW, rowValue(slot));
                break;
            case EXTRA_KEYS:
            default:
                // Placing the extra keys somewhere is also asking to see them, the same way
                // setExtraKeys means it.
                if (!slot.hidden && !mPreferences.shouldShowTerminalToolbar()) {
                    mPreferences.setShowTerminalToolbar(true);
                }
                writeString(orientation, KEY_EXTRA_KEYS, rowValue(slot));
                break;
        }
        setSlotOrder(orientation, element, slot.order);
    }

    /**
     * Where an element sits in its edge's stack, 0 outermost. Nothing is written until the user
     * re-orders something: an absent key is the stack the launcher has always drawn, read against
     * the edge the element is actually on.
     */
    public int slotOrder(@NonNull PlaceOrientation orientation, @NonNull Element element) {
        String key = layoutKey(orientation, orderKeyName(element));
        int fallback = element.defaultOrder(elementEdge(orientation, element));
        if (mStore == null || !mStore.contains(key)) return fallback;
        return Math.max(0, mStore.getInt(key, fallback));
    }

    public void setSlotOrder(@NonNull PlaceOrientation orientation, @NonNull Element element,
                             int order) {
        writeInt(layoutKey(orientation, orderKeyName(element)), Math.max(0, order));
    }

    /**
     * The edge an element's own placement key names, without reading its order. A row that is put
     * away names none: the key holds nothing but {@code hidden}, so the edge it would come back to
     * was never stored and the bottom — where both rows have always started — stands for it.
     */
    @NonNull
    private Edge elementEdge(@NonNull PlaceOrientation orientation, @NonNull Element element) {
        switch (element) {
            case STATUS: return statusBarEdge(orientation);
            case AZ: return azBarEdge(orientation);
            case APPS:
            case EXTRA_KEYS:
            default: {
                String key = element == Element.APPS ? KEY_APPS_ROW : KEY_EXTRA_KEYS;
                Edge fallback = element == Element.APPS && orientation == PlaceOrientation.LANDSCAPE
                    ? Edge.LEFT : Edge.BOTTOM;
                String raw = readString(orientation, key);
                return VALUE_HIDDEN.equals(raw) ? Edge.BOTTOM : Edge.parse(raw, fallback);
            }
        }
    }

    /** A slot as the pinned apps and the extra keys have always spelled it. */
    @NonNull
    private static String rowValue(@NonNull Slot slot) {
        return slot.hidden ? VALUE_HIDDEN : slot.edge.storageValue();
    }

    /**
     * Always an edge: the bar moves, it never goes away, so the wall's pager always has a grip.
     *
     * <p>Every edge is offered in both orientations. A column down the side of a portrait screen
     * used to be refused here, because it takes width the terminal does not have; it is allowed
     * now and the Layout editor warns when the canvas it leaves gets narrow, so the model no
     * longer overrules a choice the user can see the cost of.
     */
    @NonNull
    public Edge statusBarEdge(@NonNull PlaceOrientation orientation) {
        return Edge.parse(readString(orientation, KEY_STATUS_BAR), Edge.TOP);
    }

    public void setStatusBarEdge(@NonNull PlaceOrientation orientation, @NonNull Edge edge) {
        writeString(orientation, KEY_STATUS_BAR, edge.storageValue());
    }

    /**
     * Where the pinned apps stand. Landscape defaults to a column on the left — the rail every
     * landscape session has had — and portrait to the row along the bottom.
     */
    @NonNull
    public RowPlacement appsRow(@NonNull PlaceOrientation orientation) {
        return placementOf(slot(orientation, Element.APPS));
    }

    /**
     * A slot as the three-way row placement the pinned apps and the extra keys were stored as. A
     * column down the side of a portrait screen used to be refused here; it is allowed in both
     * orientations now and the Layout editor warns about a narrow canvas instead. The old spelling
     * has no top row, so a slot on the top edge reads as the bottom until the views that draw
     * them learn the edge.
     */
    @NonNull
    private static RowPlacement placementOf(@NonNull Slot slot) {
        if (slot.hidden) return RowPlacement.HIDDEN;
        switch (slot.edge) {
            case LEFT: return RowPlacement.LEFT;
            case RIGHT: return RowPlacement.RIGHT;
            default: return RowPlacement.BOTTOM;
        }
    }

    public void setAppsRow(@NonNull PlaceOrientation orientation,
                           @NonNull RowPlacement placement) {
        writeString(orientation, KEY_APPS_ROW, placement.storageValue());
    }

    /** The alphabets row is a switch, not a place: it rides on the apps row wherever that goes. */
    public boolean azRowShown(@NonNull PlaceOrientation orientation) {
        String key = layoutKey(orientation, KEY_AZ_ROW);
        if (mStore != null && mStore.contains(key)) return mStore.getBoolean(key, true);
        return mPreferences.isAppLauncherAzRowEnabled();
    }

    public void setAzRowShown(@NonNull PlaceOrientation orientation, boolean shown) {
        writeBoolean(layoutKey(orientation, KEY_AZ_ROW), shown);
    }

    /**
     * Where the alphabets bar stands while it rides on its own — with the apps row under it, it
     * always rides along the bottom and this choice is ignored ({@link PlaceChromePolicy#azBarEdge}).
     *
     * <p>Every edge is offered in both orientations, same as the status bar: a column down the
     * side of a portrait screen is allowed now, and the Layout editor warns when the canvas it
     * leaves gets narrow.
     */
    @NonNull
    public Edge azBarEdge(@NonNull PlaceOrientation orientation) {
        return Edge.parse(readString(orientation, KEY_AZ_BAR), Edge.BOTTOM);
    }

    public void setAzBarEdge(@NonNull PlaceOrientation orientation, @NonNull Edge edge) {
        writeString(orientation, KEY_AZ_BAR, edge.storageValue());
    }

    /** Where the extra keys stand when they are shown at all. */
    @NonNull
    public RowPlacement extraKeys(@NonNull PlaceOrientation orientation) {
        return placementOf(slot(orientation, Element.EXTRA_KEYS));
    }

    /**
     * Placing the extra keys somewhere is also asking to see them: the terminal's own toolbar
     * toggle can have hidden them everywhere, and a placement that toggle still vetoes would read
     * back as hidden the moment it was written.
     */
    public void setExtraKeys(@NonNull PlaceOrientation orientation,
                             @NonNull RowPlacement placement) {
        if (placement != RowPlacement.HIDDEN && !mPreferences.shouldShowTerminalToolbar()) {
            mPreferences.setShowTerminalToolbar(true);
        }
        writeString(orientation, KEY_EXTRA_KEYS, placement.storageValue());
    }

    /**
     * Whether an open keyboard floats over the Linux display or shrinks it. Only the display reads
     * this ({@link KeyboardOverlayPolicy}): it has a fixed screen of its own and nothing to reflow,
     * so in landscape it floats until the user asks otherwise. The terminal's text always takes the
     * room, whatever is stored here.
     */
    @NonNull
    public KeyboardMode keyboardMode(@NonNull PlaceOrientation orientation) {
        KeyboardMode fallback = orientation == PlaceOrientation.LANDSCAPE
            ? KeyboardMode.OVERLAY : KeyboardMode.RESIZE;
        return KeyboardMode.parse(readString(orientation, KEY_KEYBOARD_MODE), fallback);
    }

    public void setKeyboardMode(@NonNull PlaceOrientation orientation, @NonNull KeyboardMode mode) {
        writeString(orientation, KEY_KEYBOARD_MODE, mode.storageValue());
    }

    /**
     * The shape the keyboard takes. Docked until the user asks for something else: floating and
     * split are choices, never a default, so nothing has to be written for the launcher to keep
     * the keyboard it has always had.
     */
    @NonNull
    public KeyboardForm keyboardForm(@NonNull PlaceOrientation orientation) {
        return KeyboardForm.parse(readString(orientation, KEY_KEYBOARD_FORM), KeyboardForm.DOCKED);
    }

    public void setKeyboardForm(@NonNull PlaceOrientation orientation, @NonNull KeyboardForm form) {
        writeString(orientation, KEY_KEYBOARD_FORM, form.storageValue());
    }

    public int widgetColumns(@NonNull PlaceOrientation orientation) {
        return clamp(readInt(orientation, KEY_WIDGET_COLUMNS,
                mPreferences.getAppLauncherWidgetGridColumns()),
            TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
            TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS);
    }

    public void setWidgetColumns(@NonNull PlaceOrientation orientation, int columns) {
        writeInt(layoutKey(orientation, KEY_WIDGET_COLUMNS),
            clamp(columns, TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
                TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS));
    }

    public int widgetRows(@NonNull PlaceOrientation orientation) {
        return clamp(readInt(orientation, KEY_WIDGET_ROWS,
                mPreferences.getAppLauncherWidgetGridRows()),
            TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS,
            TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS);
    }

    public void setWidgetRows(@NonNull PlaceOrientation orientation, int rows) {
        writeInt(layoutKey(orientation, KEY_WIDGET_ROWS),
            clamp(rows, TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS,
                TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS));
    }

    // ---------------------------------------------------------------- the three sizes

    /*
     * How tall the dock, the keyboard and the air under its last key row stand. A size is layout,
     * not look, so all three are the orientation's like every bar above — see
     * docs/adr/0001-sizes-live-in-the-layout-store.md, whose per-place scope ADR 0003 retired. They
     * are not folded into PlaceLayout: that value is read on every chrome pass and these three move
     * under a dragging finger, so they are asked for where they are used instead of retiring the
     * cached arrangement per frame.
     */

    /** How tall the pinned apps row stands, as a multiple of its unscaled height. */
    public float dockHeightScale(@NonNull PlaceOrientation orientation) {
        String key = layoutKey(orientation, KEY_DOCK_HEIGHT);
        float value = mStore != null && mStore.contains(key)
            ? mStore.getFloat(key, TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT)
            : mPreferences.getSharedAppLauncherBarHeightScale();
        return TermuxAppSharedPreferences.clampAppLauncherBarHeightScale(value);
    }

    public void setDockHeightScale(@NonNull PlaceOrientation orientation, float scale) {
        writeFloat(layoutKey(orientation, KEY_DOCK_HEIGHT),
            TermuxAppSharedPreferences.clampAppLauncherBarHeightScale(scale));
    }

    /** How tall the in-app keyboard stands, as a multiple of its unscaled height. */
    public float keyboardHeightScale(@NonNull PlaceOrientation orientation) {
        String key = layoutKey(orientation, KEY_KEYBOARD_HEIGHT);
        float value = mStore != null && mStore.contains(key)
            ? mStore.getFloat(key, mPreferences.getDefaultInAppKeyboardHeightScale())
            : mPreferences.getSharedInAppKeyboardHeightScale(
                orientation == PlaceOrientation.LANDSCAPE);
        return TermuxAppSharedPreferences.clampInAppKeyboardHeightScale(value);
    }

    public void setKeyboardHeightScale(@NonNull PlaceOrientation orientation, float scale) {
        writeFloat(layoutKey(orientation, KEY_KEYBOARD_HEIGHT),
            TermuxAppSharedPreferences.clampInAppKeyboardHeightScale(scale));
    }

    /** Extra air in dp under the last key row, inside the keyboard's own surface. */
    public int keyboardChinDp(@NonNull PlaceOrientation orientation) {
        String key = layoutKey(orientation, KEY_KEYBOARD_CHIN);
        int value = mStore != null && mStore.contains(key)
            ? mStore.getInt(key, TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING)
            : mPreferences.getSharedInAppKeyboardBottomPadding();
        return TermuxAppSharedPreferences.clampInAppKeyboardBottomPadding(value);
    }

    public void setKeyboardChinDp(@NonNull PlaceOrientation orientation, int dp) {
        writeInt(layoutKey(orientation, KEY_KEYBOARD_CHIN),
            TermuxAppSharedPreferences.clampInAppKeyboardBottomPadding(dp));
    }

    /** Puts one orientation back to whatever the global values and defaults say. */
    public void clear(@NonNull PlaceOrientation orientation) {
        if (mStore == null) return;
        SharedPreferences.Editor editor = mStore.edit();
        for (String key : ARRANGEMENT_KEYS) editor.remove(layoutKey(orientation, key));
        editor.apply();
        mRevision++;
    }

    // ---------------------------------------------------------------- memory

    /**
     * Whether the status bar was left compact, in this orientation. Per orientation rather than
     * once: the bar costs height off the short axis, and a screen turned on its side has a third of
     * the height it had — expanding the bar where there is room for it is not a decision about the
     * screen where there is not. Every place shares it, like the rest of the arrangement.
     *
     * <p>An orientation nobody has ever rested the bar in answers with a default, and only with a
     * default: landscape rests compact, portrait keeps the state the launcher's one bar always
     * read. Nothing writes either of them — the store has no way to tell a pinned default from a
     * choice afterwards, so a default that was written is a choice the user can never be given
     * back.
     */
    public boolean isStatusCompact(@NonNull PlaceOrientation orientation) {
        String key = layoutKey(orientation, KEY_STATUS_COMPACT);
        if (mStore != null && mStore.contains(key)) return mStore.getBoolean(key, false);
        return orientation == PlaceOrientation.LANDSCAPE
            ? LANDSCAPE_RESTS_COMPACT : mPreferences.isTopPaneClockCollapsed();
    }

    public void setStatusCompact(@NonNull PlaceOrientation orientation, boolean compact) {
        writeBoolean(layoutKey(orientation, KEY_STATUS_COMPACT), compact);
    }

    /**
     * Whether the place was left with the keyboard up, which is what it comes back with. This is
     * the one thing the places still keep apart: the widget grid has nothing to type into, so it
     * always answers closed, while the terminal and the display each remember their own.
     */
    public boolean wasKeyboardOpen(@NonNull PaneWallPage place) {
        if (place == PaneWallPage.WIDGETS) return false;
        return mStore != null && mStore.getBoolean(memoryKey(place, KEY_KEYBOARD_OPEN), false);
    }

    public void setKeyboardOpen(@NonNull PaneWallPage place, boolean open) {
        // Home has no memory to keep: it comes back closed whatever it was left with.
        if (place == PaneWallPage.WIDGETS) return;
        writeBoolean(memoryKey(place, KEY_KEYBOARD_OPEN), open);
    }

    /**
     * Whether the place is in minimal mode (CONTEXT.md). Remembered per place until it is turned
     * off, like the keyboard beside it, and never for Home, which has no pane to give the screen
     * to ({@link MinimalMode#available}). It does not overwrite the keyboard memory: a minimal
     * place simply comes back with the keyboard down, and turning the mode off brings back what
     * the place remembered.
     */
    public boolean isMinimal(@NonNull PaneWallPage place) {
        if (!MinimalMode.available(place)) return false;
        return mStore != null && mStore.getBoolean(memoryKey(place, KEY_MINIMAL), false);
    }

    public void setMinimal(@NonNull PaneWallPage place, boolean minimal) {
        if (!MinimalMode.available(place)) return;
        writeBoolean(memoryKey(place, KEY_MINIMAL), minimal);
    }

    /**
     * Where a floating keyboard was left, as a fraction of the room it can be moved in — {@code 0}
     * against the left or top edge, {@code 1} against the right or bottom one. A fraction rather
     * than pixels, so the same memory survives a rotation, a font-scale change and a keyboard the
     * user has since made taller; {@link #FLOAT_POSITION_UNSET} until it is dragged for the first
     * time, which is the caller's cue to place it wherever it starts.
     *
     * <p>Remembered per orientation, like the arrangement it belongs to, and shared by every place
     * for the same reason: the keyboard stands in the same chrome wherever the wall is.
     */
    public float floatingKeyboardX(@NonNull PlaceOrientation orientation) {
        return readFraction(layoutKey(orientation, KEY_KEYBOARD_FLOAT_X));
    }

    public float floatingKeyboardY(@NonNull PlaceOrientation orientation) {
        return readFraction(layoutKey(orientation, KEY_KEYBOARD_FLOAT_Y));
    }

    /** Remembers a dragged position; either fraction outside 0..1 forgets it instead. */
    public void setFloatingKeyboardPosition(@NonNull PlaceOrientation orientation,
                                            float x, float y) {
        writeFraction(layoutKey(orientation, KEY_KEYBOARD_FLOAT_X), x);
        writeFraction(layoutKey(orientation, KEY_KEYBOARD_FLOAT_Y), y);
    }

    private float readFraction(@NonNull String key) {
        if (mStore == null || !mStore.contains(key)) return FLOAT_POSITION_UNSET;
        float value = mStore.getFloat(key, FLOAT_POSITION_UNSET);
        if (Float.isNaN(value) || value < 0f || value > 1f) return FLOAT_POSITION_UNSET;
        return value;
    }

    private void writeFraction(@NonNull String key, float value) {
        if (mStore == null) return;
        if (Float.isNaN(value) || value < 0f || value > 1f) {
            mStore.edit().remove(key).apply();
        } else {
            mStore.edit().putFloat(key, value).apply();
        }
        mRevision++;
    }

    // ---------------------------------------------------------------- keys

    @VisibleForTesting
    @NonNull
    static String placeKey(@NonNull PaneWallPage place) {
        // The widget grid is the home screen everywhere a user can see it; only the enum says
        // WIDGETS.
        return place == PaneWallPage.WIDGETS ? "home" : place.name().toLowerCase(Locale.ROOT);
    }

    /** Where the shared arrangement keeps one value for one orientation. */
    @VisibleForTesting
    @NonNull
    static String layoutKey(@NonNull PlaceOrientation orientation, @NonNull String key) {
        return LAYOUT_PREFIX + orientation.storageValue() + "." + key;
    }

    /** Where a place kept its own copy of an arrangement value, before version 6 shared them. */
    @VisibleForTesting
    @NonNull
    static String legacyArrangementKey(@NonNull PaneWallPage place,
                                       @NonNull PlaceOrientation orientation,
                                       @NonNull String key) {
        return PREFIX + placeKey(place) + "." + orientation.storageValue() + "." + key;
    }

    @VisibleForTesting
    @NonNull
    static String memoryKey(@NonNull PaneWallPage place, @NonNull String key) {
        return PREFIX + placeKey(place) + "." + key;
    }

    /** Where a place kept its own value for a shared look key, before version 6 folded them. */
    @VisibleForTesting
    @NonNull
    static String legacyLookKey(@NonNull PaneWallPage place, @NonNull String key) {
        return PREFIX + placeKey(place) + LEGACY_LOOK_INFIX + key;
    }

    // ---------------------------------------------------------------- migration

    /**
     * Folds the launcher's old keys into the current ones, once. Each step is gated on the version
     * it was introduced at, so an install already migrated to an earlier version only runs the
     * steps added since — re-running an earlier step would stomp choices the user has made since
     * migrating to it.
     *
     * <p>Steps 1 to 5 predate the shared layout and still write the per-place keys they always
     * wrote; step 6 then folds those into the shared ones. They are applied first, on an editor of
     * their own, because an editor's puts are invisible to reads until it is applied and step 6
     * reads what they wrote.
     */
    private void migrateIfNeeded() {
        if (mStore == null) return;
        int fromVersion = mStore.getInt(KEY_MIGRATED, 0);
        if (fromVersion >= MIGRATION_VERSION) return;
        if (fromVersion < 5) {
            SharedPreferences.Editor editor = mStore.edit();
            migrateBeforeSharedLayout(editor, fromVersion);
            editor.apply();
        }
        SharedPreferences.Editor editor = mStore.edit();
        if (fromVersion < 6) migrateToSharedLayout(editor);
        editor.putInt(KEY_MIGRATED, MIGRATION_VERSION);
        editor.apply();
        mRevision++;
    }

    /** Versions 1 to 5, exactly as they ran when the arrangement was still kept per place. */
    private void migrateBeforeSharedLayout(@NonNull SharedPreferences.Editor editor,
                                           int fromVersion) {
        if (fromVersion < 1) {
            // The landscape rail's edge was the only place-and-orientation setting the launcher
            // had. It described every place, because there was only one rail.
            if (mStore.contains(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_RAIL_SIDE)) {
                RowPlacement side = RowPlacement.parse(
                    mStore.getString(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_RAIL_SIDE, null),
                    RowPlacement.LEFT);
                if (!side.isOnSide()) side = RowPlacement.LEFT;
                for (PaneWallPage place : PaneWallPage.values()) {
                    editor.putString(legacyArrangementKey(place, PlaceOrientation.LANDSCAPE,
                        KEY_APPS_ROW), side.storageValue());
                }
            }

            // The display's extra keys column, which then became every place's.
            if (mStore.contains(TERMUX_APP.KEY_X11_EXTRA_KEYS_SIDE)) {
                RowPlacement side = RowPlacement.parse(
                    mStore.getString(TERMUX_APP.KEY_X11_EXTRA_KEYS_SIDE, null), RowPlacement.BOTTOM);
                if (side == RowPlacement.HIDDEN) side = RowPlacement.BOTTOM;
                for (PlaceOrientation orientation : PlaceOrientation.values()) {
                    editor.putString(legacyArrangementKey(PaneWallPage.DISPLAY, orientation,
                        KEY_EXTRA_KEYS), side.storageValue());
                }
            }

            // There is no hidden status bar any more: the bar moves instead, so the wall's paging
            // gesture survives every arrangement.
            editor.remove(LEGACY_KEY_X11_HIDE_STATUS_BAR);

            if (mStore.contains(TERMUX_APP.KEY_X11_KEYBOARD_SHOWN)) {
                editor.putBoolean(memoryKey(PaneWallPage.DISPLAY, KEY_KEYBOARD_OPEN),
                    mStore.getBoolean(TERMUX_APP.KEY_X11_KEYBOARD_SHOWN, false));
                editor.remove(TERMUX_APP.KEY_X11_KEYBOARD_SHOWN);
            }

            if (mStore.contains(TERMUX_APP.KEY_TOP_PANE_CLOCK_COLLAPSED)) {
                boolean compact = mStore.getBoolean(TERMUX_APP.KEY_TOP_PANE_CLOCK_COLLAPSED, false);
                // Straight into the per-orientation keys version 5 moved this to. An install
                // that migrated before version 5 kept it under the place's memory key, and the
                // step below folds that one; writing it here as well would leave the old key
                // behind, since every removal an editor carries is applied before every put.
                for (PaneWallPage place : PaneWallPage.values()) {
                    for (PlaceOrientation orientation : PlaceOrientation.values()) {
                        editor.putBoolean(legacyArrangementKey(place, orientation,
                            KEY_STATUS_COMPACT), compact);
                    }
                }
            }
        }

        if (fromVersion < 2) {
            // The Layout page replaced the global apps-row and extra-keys switches with a Hidden
            // placement. A master that was off becomes Hidden everywhere, once; resolve() has no
            // more use for the master afterwards.
            if (!mPreferences.isAppLauncherAppsRowEnabled()) {
                for (PaneWallPage place : PaneWallPage.values()) {
                    for (PlaceOrientation orientation : PlaceOrientation.values()) {
                        editor.putString(legacyArrangementKey(place, orientation, KEY_APPS_ROW),
                            RowPlacement.HIDDEN.storageValue());
                    }
                }
            }
            if (!mPreferences.isAppLauncherExtraKeysRowEnabled()) {
                for (PaneWallPage place : PaneWallPage.values()) {
                    for (PlaceOrientation orientation : PlaceOrientation.values()) {
                        editor.putString(legacyArrangementKey(place, orientation, KEY_EXTRA_KEYS),
                            RowPlacement.HIDDEN.storageValue());
                    }
                }
            }
        }

        if (fromVersion < 3) {
            // Dock height, keyboard height and the keyboard's chin became layout values. Each
            // place and orientation is seeded with the number it was already resolving to, so an
            // upgrade changes nothing on screen: the keyboard's height from the global for that
            // orientation, the chin from the one global there was, and the dock's height from the
            // place's own look override where it had taken one. A value nobody ever set is left
            // unwritten — the read still answers with the shipped default, and pinning it would
            // freeze a default that still moves with the dock's style.
            for (PaneWallPage place : PaneWallPage.values()) {
                String dockOverride = legacyLookKey(place, TERMUX_APP.KEY_APP_LAUNCHER_BAR_HEIGHT);
                Float dock = storedFloat(dockOverride);
                if (dock == null) dock = storedFloat(TERMUX_APP.KEY_APP_LAUNCHER_BAR_HEIGHT);
                for (PlaceOrientation orientation : PlaceOrientation.values()) {
                    if (dock != null) {
                        editor.putFloat(legacyArrangementKey(place, orientation, KEY_DOCK_HEIGHT),
                            TermuxAppSharedPreferences.clampAppLauncherBarHeightScale(dock));
                    }
                    Float keyboard = storedKeyboardHeightScale(orientation);
                    if (keyboard != null) {
                        editor.putFloat(legacyArrangementKey(place, orientation,
                                KEY_KEYBOARD_HEIGHT),
                            TermuxAppSharedPreferences.clampInAppKeyboardHeightScale(keyboard));
                    }
                    if (mStore.contains(TERMUX_APP.KEY_IN_APP_KEYBOARD_BOTTOM_PADDING)) {
                        editor.putInt(legacyArrangementKey(place, orientation, KEY_KEYBOARD_CHIN),
                            TermuxAppSharedPreferences.clampInAppKeyboardBottomPadding(
                                mStore.getInt(TERMUX_APP.KEY_IN_APP_KEYBOARD_BOTTOM_PADDING,
                                    TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING)));
                    }
                }
                // Dock height left the scopable look keys, so a place's old override has
                // nothing left to answer and goes with them.
                editor.remove(dockOverride);
            }
        }

        // Version 4 stood every bar on a stack it can be re-ordered in. There is nothing to fold:
        // the placement keys keep their values, and an absent order key already reads as the stack
        // the launcher has always drawn, so an upgraded install renders identically. Writing the
        // shipped orders out would only freeze numbers that still move with the default stack.

        if (fromVersion < 5) {
            // The status bar's resting state was remembered per place and nothing else, so the
            // state a portrait screen was left in decided how much of a landscape screen's height
            // the bar took. It became per place and orientation, beside the sizes version 3 moved.
            //
            // BOTH orientations are seeded with the one value the place had, so nobody is asked
            // again for a choice they have already made. Only a place that never stored one is
            // left unwritten, which is the one case the landscape default is allowed to answer.
            for (PaneWallPage place : PaneWallPage.values()) {
                String legacy = memoryKey(place, KEY_STATUS_COMPACT);
                if (!mStore.contains(legacy)) continue;
                boolean compact = mStore.getBoolean(legacy, false);
                for (PlaceOrientation orientation : PlaceOrientation.values()) {
                    editor.putBoolean(legacyArrangementKey(place, orientation, KEY_STATUS_COMPACT),
                        compact);
                }
                editor.remove(legacy);
            }
        }
    }

    /**
     * Version 6: one layout per orientation and one look, shared by every place (ADR 0003).
     *
     * <p>Each orientation is seeded from the place that value was actually showing on, so an
     * upgrade never moves what the user sees on the terminal: the terminal's for everything the
     * terminal draws, the home place's for the widget grid — the terminal has no grid and never
     * offered one — and the display's for the keyboard mode, which nothing but the display ever
     * read. A value the source place never stored is left unwritten, so the shared read answers
     * with the same global or default the source place was answering with. Every place's own copy
     * is then dropped, whether or not it was the source.
     *
     * <p>The look folds the same way: where the terminal had taken a value of its own, that value
     * becomes the shared one; everywhere else the shared value it was already falling back to
     * stands. Every place's own look key goes.
     */
    private void migrateToSharedLayout(@NonNull SharedPreferences.Editor editor) {
        Map<String, ?> all = mStore.getAll();
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            for (String key : FOLDED_KEYS) {
                Object seed = all.get(legacyArrangementKey(sourceOf(key), orientation, key));
                if (seed != null) put(editor, layoutKey(orientation, key), seed);
                for (PaneWallPage place : PaneWallPage.values())
                    editor.remove(legacyArrangementKey(place, orientation, key));
            }
        }
        for (PaneWallPage place : PaneWallPage.values())
            editor.remove(memoryKey(place, LEGACY_KEY_KEYBOARD_ON_ENTER));
        // Home comes back closed whatever it was left with, so its memory has nothing to answer.
        editor.remove(memoryKey(PaneWallPage.WIDGETS, KEY_KEYBOARD_OPEN));

        for (Map.Entry<String, ?> entry : all.entrySet()) {
            PaneWallPage owner = ownerOfLegacyLookKey(entry.getKey());
            if (owner == null) continue;
            if (owner == PaneWallPage.TERMINAL && entry.getValue() != null) {
                String shared = entry.getKey().substring(
                    (PREFIX + placeKey(owner) + LEGACY_LOOK_INFIX).length());
                put(editor, shared, entry.getValue());
            }
            editor.remove(entry.getKey());
        }
    }

    /** The place whose copy of an arrangement value is the one every place shares from version 6. */
    @NonNull
    private static PaneWallPage sourceOf(@NonNull String key) {
        switch (key) {
            case KEY_WIDGET_COLUMNS:
            case KEY_WIDGET_ROWS:
                return PaneWallPage.WIDGETS;
            case KEY_KEYBOARD_MODE:
                return PaneWallPage.DISPLAY;
            default:
                return PaneWallPage.TERMINAL;
        }
    }

    /** The place a stored per-place look key belonged to, or null for any other key. */
    @Nullable
    private static PaneWallPage ownerOfLegacyLookKey(@NonNull String key) {
        if (!key.startsWith(PREFIX)) return null;
        for (PaneWallPage place : PaneWallPage.values()) {
            if (key.startsWith(PREFIX + placeKey(place) + LEGACY_LOOK_INFIX)) return place;
        }
        return null;
    }

    /** Writes a value read back from {@link SharedPreferences#getAll()} under a new key. */
    @SuppressWarnings("unchecked")
    private static void put(@NonNull SharedPreferences.Editor editor, @NonNull String key,
                            @NonNull Object value) {
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
    }

    @NonNull
    private static String[] concat(@NonNull String[] head, @NonNull String... tail) {
        String[] out = new String[head.length + tail.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(tail, 0, out, head.length, tail.length);
        return out;
    }

    /** The keyboard height stored for one orientation; landscape falls back to portrait's. */
    @Nullable
    private Float storedKeyboardHeightScale(@NonNull PlaceOrientation orientation) {
        if (orientation == PlaceOrientation.LANDSCAPE) {
            Float landscape = storedFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE_LANDSCAPE);
            if (landscape != null) return landscape;
        }
        return storedFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE);
    }

    /** A float the file actually holds, or null where it never held one. */
    @Nullable
    private Float storedFloat(@NonNull String key) {
        if (mStore == null || !mStore.contains(key)) return null;
        float value = mStore.getFloat(key, Float.NaN);
        return Float.isNaN(value) || Float.isInfinite(value) ? null : value;
    }

    // ---------------------------------------------------------------- plumbing

    @Nullable
    private String readString(@NonNull PlaceOrientation orientation, @NonNull String key) {
        return mStore == null ? null : mStore.getString(layoutKey(orientation, key), null);
    }

    private int readInt(@NonNull PlaceOrientation orientation, @NonNull String key, int fallback) {
        String shared = layoutKey(orientation, key);
        return mStore != null && mStore.contains(shared) ? mStore.getInt(shared, fallback) : fallback;
    }

    private void writeString(@NonNull PlaceOrientation orientation, @NonNull String key,
                             @NonNull String value) {
        if (mStore == null) return;
        mStore.edit().putString(layoutKey(orientation, key), value).apply();
        mRevision++;
    }

    private void writeBoolean(@NonNull String key, boolean value) {
        if (mStore == null) return;
        mStore.edit().putBoolean(key, value).apply();
        mRevision++;
    }

    private void writeInt(@NonNull String key, int value) {
        if (mStore == null) return;
        mStore.edit().putInt(key, value).apply();
        mRevision++;
    }

    private void writeFloat(@NonNull String key, float value) {
        if (mStore == null) return;
        mStore.edit().putFloat(key, value).apply();
        mRevision++;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
