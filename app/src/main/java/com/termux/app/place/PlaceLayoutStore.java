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

/**
 * Where every place keeps its arrangement. Anything that decides what is on screen and where is a
 * property of a place — the widget grid, the terminal, the Linux display — and, for arrangement, of
 * the orientation as well.
 *
 * <p>Keys are scoped: {@code place.<home|terminal|display>.<portrait|landscape>.<key>} for
 * arrangement, {@code place.<place>.<key>} for the things a place only remembers. A missing scoped
 * key falls back to the shared value the launcher used to keep globally, and then to the shipped
 * default, so nothing has to be written before a place reads the way it always looked.
 *
 * <p>The pinned apps row and the extra keys used to have a global on/off switch on top of the scoped
 * placement; the Layout page replaced both with a per-place, per-orientation Hidden placement, so a
 * switched-off row from before the Layout page migrates to Hidden everywhere, once, and the scoped
 * placement is the only thing {@link #resolve} reads afterwards.
 *
 * <p>No Android views here, on purpose: this is a resolver over {@link SharedPreferences} and it is
 * tested as one.
 */
public final class PlaceLayoutStore {

    /** Bumped when a new set of old keys has to be folded into the scoped ones. */
    @VisibleForTesting static final int MIGRATION_VERSION = 5;

    @VisibleForTesting static final String KEY_MIGRATED = "place.migrated";

    private static final String PREFIX = "place.";

    private static final String KEY_STATUS_BAR = Element.STATUS.storageKey();
    private static final String KEY_APPS_ROW = Element.APPS.storageKey();
    private static final String KEY_AZ_ROW = "az_row";
    private static final String KEY_AZ_BAR = Element.AZ.storageKey();
    private static final String KEY_EXTRA_KEYS = Element.EXTRA_KEYS.storageKey();

    /**
     * An element's position in its edge's stack sits beside its placement:
     * {@code place.<place>.<orientation>.<placement key>_order}. Absent means the stack the
     * launcher has always drawn ({@link Element#defaultOrder}), so an updated install renders
     * identically without anything being written for it.
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

    private static final String KEY_KEYBOARD_ON_ENTER = "keyboard_on_enter";
    private static final String KEY_KEYBOARD_OPEN = "keyboard_open";
    private static final String KEY_KEYBOARD_FLOAT_X = "keyboard_float_x";
    private static final String KEY_KEYBOARD_FLOAT_Y = "keyboard_float_y";

    /** A floating keyboard that has never been moved has no remembered place to come back to. */
    public static final float FLOAT_POSITION_UNSET = -1f;

    /** The launcher's status-bar hide switch for the display, dropped with the hidden state. */
    private static final String LEGACY_KEY_X11_HIDE_STATUS_BAR = "x11_hide_status_bar";

    private static final String[] ARRANGEMENT_KEYS = {
        KEY_STATUS_BAR, KEY_APPS_ROW, KEY_AZ_ROW, KEY_AZ_BAR, KEY_EXTRA_KEYS, KEY_KEYBOARD_MODE,
        KEY_KEYBOARD_FORM, KEY_WIDGET_COLUMNS, KEY_WIDGET_ROWS,
        KEY_DOCK_HEIGHT, KEY_KEYBOARD_HEIGHT, KEY_KEYBOARD_CHIN,
        // The stack positions ride with the placements they belong to, so the Layout editor's
        // Discard and its per-place reset put a re-order back the same way they put a move back.
        orderKeyName(Element.STATUS), orderKeyName(Element.APPS), orderKeyName(Element.AZ),
        orderKeyName(Element.EXTRA_KEYS)
    };

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
     * Any write to the launcher's preferences can change what a place resolves to — the scoped keys
     * and the shared ones a missing key falls back to alike — so every one of them retires a
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
     * The one immutable answer for a place in an orientation. The getters below say where a row is
     * arranged to go; this is where the switches that can still turn a row off entirely are folded
     * in, so a caller reads one effective layout and nothing else.
     */
    @NonNull
    public PlaceLayout resolve(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation) {
        EnumMap<Element, Slot> slots = new EnumMap<>(Element.class);
        for (Element element : Element.values()) slots.put(element, slot(place, orientation, element));
        // The terminal's own toolbar switch can still have put the extra keys away everywhere.
        if (!mPreferences.shouldShowTerminalToolbar()) {
            slots.put(Element.EXTRA_KEYS, slots.get(Element.EXTRA_KEYS).withHidden(true));
        }
        return new PlaceLayout(slots,
            keyboardMode(place, orientation),
            keyboardForm(place, orientation),
            widgetColumns(place, orientation),
            widgetRows(place, orientation));
    }

    // ---------------------------------------------------------------- slots

    /**
     * Where one element stands on a place in an orientation, as the store holds it: the placement
     * key it has always had, and the sibling order key beside it. The toolbar switch is not folded
     * in here — {@link #resolve} does that — so a slot read back is exactly what was written.
     */
    @NonNull
    public Slot slot(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                     @NonNull Element element) {
        int order = slotOrder(place, orientation, element);
        switch (element) {
            case STATUS:
                // Never hidden: the wall's pager rides it, so it only ever moves.
                return new Slot(false, statusBarEdge(place, orientation), order);
            case AZ:
                return new Slot(!azRowShown(place, orientation), azBarEdge(place, orientation),
                    order);
            case APPS:
            case EXTRA_KEYS:
            default:
                return new Slot(VALUE_HIDDEN.equals(readString(place, orientation,
                    element == Element.APPS ? KEY_APPS_ROW : KEY_EXTRA_KEYS)),
                    elementEdge(place, orientation, element), order);
        }
    }

    /** Moves one element: its edge, whether it is put away, and where it sits in the stack. */
    public void setSlot(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                        @NonNull Element element, @NonNull Slot slot) {
        switch (element) {
            case STATUS:
                setStatusBarEdge(place, orientation, slot.edge);
                break;
            case AZ:
                setAzRowShown(place, orientation, !slot.hidden);
                setAzBarEdge(place, orientation, slot.edge);
                break;
            case APPS:
                writeString(place, orientation, KEY_APPS_ROW, rowValue(slot));
                break;
            case EXTRA_KEYS:
            default:
                // Placing the extra keys somewhere is also asking to see them, the same way
                // setExtraKeys means it.
                if (!slot.hidden && !mPreferences.shouldShowTerminalToolbar()) {
                    mPreferences.setShowTerminalToolbar(true);
                }
                writeString(place, orientation, KEY_EXTRA_KEYS, rowValue(slot));
                break;
        }
        setSlotOrder(place, orientation, element, slot.order);
    }

    /**
     * Where an element sits in its edge's stack, 0 outermost. Nothing is written until the user
     * re-orders something: an absent key is the stack the launcher has always drawn, read against
     * the edge the element is actually on.
     */
    public int slotOrder(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                         @NonNull Element element) {
        String key = arrangementKey(place, orientation, orderKeyName(element));
        int fallback = element.defaultOrder(elementEdge(place, orientation, element));
        if (mStore == null || !mStore.contains(key)) return fallback;
        return Math.max(0, mStore.getInt(key, fallback));
    }

    public void setSlotOrder(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                             @NonNull Element element, int order) {
        writeInt(arrangementKey(place, orientation, orderKeyName(element)), Math.max(0, order));
    }

    /**
     * The edge an element's own placement key names, without reading its order. A row that is put
     * away names none: the key holds nothing but {@code hidden}, so the edge it would come back to
     * was never stored and the bottom — where both rows have always started — stands for it.
     */
    @NonNull
    private Edge elementEdge(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                             @NonNull Element element) {
        switch (element) {
            case STATUS: return statusBarEdge(place, orientation);
            case AZ: return azBarEdge(place, orientation);
            case APPS:
            case EXTRA_KEYS:
            default: {
                String key = element == Element.APPS ? KEY_APPS_ROW : KEY_EXTRA_KEYS;
                Edge fallback = element == Element.APPS && orientation == PlaceOrientation.LANDSCAPE
                    ? Edge.LEFT : Edge.BOTTOM;
                String raw = readString(place, orientation, key);
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
    public Edge statusBarEdge(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation) {
        return Edge.parse(readString(place, orientation, KEY_STATUS_BAR), Edge.TOP);
    }

    public void setStatusBarEdge(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                                 @NonNull Edge edge) {
        writeString(place, orientation, KEY_STATUS_BAR, edge.storageValue());
    }

    /**
     * Where the pinned apps stand. Landscape defaults to a column on the left — the rail every
     * landscape session has had — and portrait to the row along the bottom.
     */
    @NonNull
    public RowPlacement appsRow(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation) {
        return placementOf(slot(place, orientation, Element.APPS));
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

    public void setAppsRow(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                           @NonNull RowPlacement placement) {
        writeString(place, orientation, KEY_APPS_ROW, placement.storageValue());
    }

    /** The alphabets row is a switch, not a place: it rides on the apps row wherever that goes. */
    public boolean azRowShown(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation) {
        String key = arrangementKey(place, orientation, KEY_AZ_ROW);
        if (mStore != null && mStore.contains(key)) return mStore.getBoolean(key, true);
        return mPreferences.isAppLauncherAzRowEnabled();
    }

    public void setAzRowShown(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                              boolean shown) {
        writeBoolean(arrangementKey(place, orientation, KEY_AZ_ROW), shown);
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
    public Edge azBarEdge(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation) {
        return Edge.parse(readString(place, orientation, KEY_AZ_BAR), Edge.BOTTOM);
    }

    public void setAzBarEdge(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                             @NonNull Edge edge) {
        writeString(place, orientation, KEY_AZ_BAR, edge.storageValue());
    }

    /** Where the extra keys stand when they are shown at all. */
    @NonNull
    public RowPlacement extraKeys(@NonNull PaneWallPage place,
                                  @NonNull PlaceOrientation orientation) {
        return placementOf(slot(place, orientation, Element.EXTRA_KEYS));
    }

    /**
     * Placing the extra keys somewhere is also asking to see them: the terminal's own toolbar
     * toggle can have hidden them everywhere, and a placement that toggle still vetoes would read
     * back as hidden the moment it was written.
     */
    public void setExtraKeys(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                             @NonNull RowPlacement placement) {
        if (placement != RowPlacement.HIDDEN && !mPreferences.shouldShowTerminalToolbar()) {
            mPreferences.setShowTerminalToolbar(true);
        }
        writeString(place, orientation, KEY_EXTRA_KEYS, placement.storageValue());
    }

    /**
     * The display in landscape is the one place a keyboard should float over rather than squeeze:
     * it has a fixed screen of its own and nothing to reflow.
     */
    @NonNull
    public KeyboardMode keyboardMode(@NonNull PaneWallPage place,
                                     @NonNull PlaceOrientation orientation) {
        KeyboardMode fallback = place == PaneWallPage.DISPLAY
            && orientation == PlaceOrientation.LANDSCAPE ? KeyboardMode.OVERLAY : KeyboardMode.RESIZE;
        return KeyboardMode.parse(readString(place, orientation, KEY_KEYBOARD_MODE), fallback);
    }

    public void setKeyboardMode(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                                @NonNull KeyboardMode mode) {
        writeString(place, orientation, KEY_KEYBOARD_MODE, mode.storageValue());
    }

    /**
     * The shape the keyboard takes here. Docked everywhere until the user asks for something else:
     * floating and split are choices, never a default, so nothing has to be written for a place to
     * keep the keyboard it has always had.
     */
    @NonNull
    public KeyboardForm keyboardForm(@NonNull PaneWallPage place,
                                     @NonNull PlaceOrientation orientation) {
        return KeyboardForm.parse(readString(place, orientation, KEY_KEYBOARD_FORM),
            KeyboardForm.DOCKED);
    }

    public void setKeyboardForm(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                                @NonNull KeyboardForm form) {
        writeString(place, orientation, KEY_KEYBOARD_FORM, form.storageValue());
    }

    public int widgetColumns(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation) {
        return clamp(readInt(place, orientation, KEY_WIDGET_COLUMNS,
                mPreferences.getAppLauncherWidgetGridColumns()),
            TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
            TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS);
    }

    public void setWidgetColumns(@NonNull PaneWallPage place,
                                 @NonNull PlaceOrientation orientation, int columns) {
        writeInt(arrangementKey(place, orientation, KEY_WIDGET_COLUMNS),
            clamp(columns, TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
                TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS));
    }

    public int widgetRows(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation) {
        return clamp(readInt(place, orientation, KEY_WIDGET_ROWS,
                mPreferences.getAppLauncherWidgetGridRows()),
            TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS,
            TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS);
    }

    public void setWidgetRows(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                              int rows) {
        writeInt(arrangementKey(place, orientation, KEY_WIDGET_ROWS),
            clamp(rows, TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS,
                TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS));
    }

    // ---------------------------------------------------------------- the three sizes

    /*
     * How tall the dock, the keyboard and the air under its last key row stand. A size is layout,
     * not look, so all three are the place's and the orientation's like every bar above — see
     * docs/adr/0001-sizes-live-in-the-layout-store.md. They are not folded into PlaceLayout: that
     * value is read on every chrome pass and these three move under a dragging finger, so they are
     * asked for where they are used instead of retiring the cached arrangement per frame.
     */

    /** How tall the pinned apps row stands, as a multiple of its unscaled height. */
    public float dockHeightScale(@NonNull PaneWallPage place,
                                 @NonNull PlaceOrientation orientation) {
        String key = arrangementKey(place, orientation, KEY_DOCK_HEIGHT);
        float value = mStore != null && mStore.contains(key)
            ? mStore.getFloat(key, TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT)
            : sharedDockHeightScale(place);
        return TermuxAppSharedPreferences.clampAppLauncherBarHeightScale(value);
    }

    public void setDockHeightScale(@NonNull PaneWallPage place,
                                   @NonNull PlaceOrientation orientation, float scale) {
        writeFloat(arrangementKey(place, orientation, KEY_DOCK_HEIGHT),
            TermuxAppSharedPreferences.clampAppLauncherBarHeightScale(scale));
    }

    /** How tall the in-app keyboard stands, as a multiple of its unscaled height. */
    public float keyboardHeightScale(@NonNull PaneWallPage place,
                                     @NonNull PlaceOrientation orientation) {
        String key = arrangementKey(place, orientation, KEY_KEYBOARD_HEIGHT);
        float value = mStore != null && mStore.contains(key)
            ? mStore.getFloat(key, mPreferences.getDefaultInAppKeyboardHeightScale())
            : mPreferences.getSharedInAppKeyboardHeightScale(
                orientation == PlaceOrientation.LANDSCAPE);
        return TermuxAppSharedPreferences.clampInAppKeyboardHeightScale(value);
    }

    public void setKeyboardHeightScale(@NonNull PaneWallPage place,
                                       @NonNull PlaceOrientation orientation, float scale) {
        writeFloat(arrangementKey(place, orientation, KEY_KEYBOARD_HEIGHT),
            TermuxAppSharedPreferences.clampInAppKeyboardHeightScale(scale));
    }

    /** Extra air in dp under the last key row, inside the keyboard's own surface. */
    public int keyboardChinDp(@NonNull PaneWallPage place,
                              @NonNull PlaceOrientation orientation) {
        String key = arrangementKey(place, orientation, KEY_KEYBOARD_CHIN);
        int value = mStore != null && mStore.contains(key)
            ? mStore.getInt(key, TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING)
            : mPreferences.getSharedInAppKeyboardBottomPadding();
        return TermuxAppSharedPreferences.clampInAppKeyboardBottomPadding(value);
    }

    public void setKeyboardChinDp(@NonNull PaneWallPage place,
                                  @NonNull PlaceOrientation orientation, int dp) {
        writeInt(arrangementKey(place, orientation, KEY_KEYBOARD_CHIN),
            TermuxAppSharedPreferences.clampInAppKeyboardBottomPadding(dp));
    }

    /**
     * The dock height a place stood at before the size moved here: its own look override where it
     * had taken one, and the shared value otherwise.
     */
    private float sharedDockHeightScale(@NonNull PaneWallPage place) {
        String override = PlaceLookPreferences.lookKey(place,
            TERMUX_APP.KEY_APP_LAUNCHER_BAR_HEIGHT);
        if (mStore != null && mStore.contains(override))
            return mStore.getFloat(override, TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT);
        return mPreferences.getSharedAppLauncherBarHeightScale();
    }

    /** Puts one place's orientation back to whatever the shared values and defaults say. */
    public void clear(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation) {
        if (mStore == null) return;
        SharedPreferences.Editor editor = mStore.edit();
        for (String key : ARRANGEMENT_KEYS) editor.remove(arrangementKey(place, orientation, key));
        editor.apply();
        mRevision++;
    }

    // ---------------------------------------------------------------- memory

    /**
     * Whether the place was left with the status bar compact, in this orientation. Scoped like the
     * sizes beside it rather than per place alone: the bar costs height off the short axis, and a
     * screen turned on its side has a third of the height it had — expanding the bar where there is
     * room for it is not a decision about the screen where there is not.
     *
     * <p>A place and orientation nobody has ever rested the bar in answers with a default, and only
     * with a default: landscape rests compact, portrait keeps the state the launcher's one bar
     * always read. Nothing writes either of them — the store has no way to tell a pinned default
     * from a choice afterwards, so a default that was written is a choice the user can never be
     * given back.
     */
    public boolean isStatusCompact(@NonNull PaneWallPage place,
                                   @NonNull PlaceOrientation orientation) {
        String key = arrangementKey(place, orientation, KEY_STATUS_COMPACT);
        if (mStore != null && mStore.contains(key)) return mStore.getBoolean(key, false);
        return orientation == PlaceOrientation.LANDSCAPE
            ? LANDSCAPE_RESTS_COMPACT : mPreferences.isTopPaneClockCollapsed();
    }

    public void setStatusCompact(@NonNull PaneWallPage place,
                                 @NonNull PlaceOrientation orientation, boolean compact) {
        writeBoolean(arrangementKey(place, orientation, KEY_STATUS_COMPACT), compact);
    }

    /**
     * How the place wants its keyboard on the way in. The widget grid has nothing to type into, so
     * it comes back closed; everywhere else comes back the way it was left.
     */
    @NonNull
    public KeyboardOnEnter keyboardOnEnter(@NonNull PaneWallPage place) {
        KeyboardOnEnter fallback = place == PaneWallPage.WIDGETS
            ? KeyboardOnEnter.CLOSED : KeyboardOnEnter.AS_LEFT;
        return KeyboardOnEnter.parse(readString(memoryKey(place, KEY_KEYBOARD_ON_ENTER)), fallback);
    }

    public void setKeyboardOnEnter(@NonNull PaneWallPage place, @NonNull KeyboardOnEnter onEnter) {
        writeString(memoryKey(place, KEY_KEYBOARD_ON_ENTER), onEnter.storageValue());
    }

    /** Whether the place was left with the keyboard up. */
    public boolean wasKeyboardOpen(@NonNull PaneWallPage place) {
        String key = memoryKey(place, KEY_KEYBOARD_OPEN);
        return mStore != null && mStore.getBoolean(key, false);
    }

    public void setKeyboardOpen(@NonNull PaneWallPage place, boolean open) {
        writeBoolean(memoryKey(place, KEY_KEYBOARD_OPEN), open);
    }

    /**
     * Where a floating keyboard was left on this place, as a fraction of the room it can be moved
     * in — {@code 0} against the left or top edge, {@code 1} against the right or bottom one. A
     * fraction rather than pixels, so the same memory survives a rotation, a font-scale change and
     * a keyboard the user has since made taller; {@link #FLOAT_POSITION_UNSET} until it is dragged
     * for the first time, which is the caller's cue to place it wherever it starts.
     *
     * <p>Remembered per place and orientation, like the arrangement it belongs to: a keyboard
     * parked clear of the widget grid has no business moving the terminal's.
     */
    public float floatingKeyboardX(@NonNull PaneWallPage place,
                                   @NonNull PlaceOrientation orientation) {
        return readFraction(arrangementKey(place, orientation, KEY_KEYBOARD_FLOAT_X));
    }

    public float floatingKeyboardY(@NonNull PaneWallPage place,
                                   @NonNull PlaceOrientation orientation) {
        return readFraction(arrangementKey(place, orientation, KEY_KEYBOARD_FLOAT_Y));
    }

    /** Remembers a dragged position; either fraction outside 0..1 forgets it instead. */
    public void setFloatingKeyboardPosition(@NonNull PaneWallPage place,
                                            @NonNull PlaceOrientation orientation,
                                            float x, float y) {
        writeFraction(arrangementKey(place, orientation, KEY_KEYBOARD_FLOAT_X), x);
        writeFraction(arrangementKey(place, orientation, KEY_KEYBOARD_FLOAT_Y), y);
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

    @VisibleForTesting
    @NonNull
    static String arrangementKey(@NonNull PaneWallPage place,
                                 @NonNull PlaceOrientation orientation, @NonNull String key) {
        return PREFIX + placeKey(place) + "." + orientation.storageValue() + "." + key;
    }

    @VisibleForTesting
    @NonNull
    static String memoryKey(@NonNull PaneWallPage place, @NonNull String key) {
        return PREFIX + placeKey(place) + "." + key;
    }

    // ---------------------------------------------------------------- migration

    /**
     * Folds the launcher's old single-place keys into the scoped ones, once. Each step is gated on
     * the version it was introduced at, so an install already migrated to an earlier version only
     * runs the steps added since — re-running an earlier step would stomp scoped choices the user
     * has made since migrating to it.
     */
    private void migrateIfNeeded() {
        if (mStore == null) return;
        int fromVersion = mStore.getInt(KEY_MIGRATED, 0);
        if (fromVersion >= MIGRATION_VERSION) return;
        SharedPreferences.Editor editor = mStore.edit();

        if (fromVersion < 1) {
            // The landscape rail's edge was the only place-and-orientation setting the launcher
            // had. It described every place, because there was only one rail.
            if (mStore.contains(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_RAIL_SIDE)) {
                RowPlacement side = RowPlacement.parse(
                    mStore.getString(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_RAIL_SIDE, null),
                    RowPlacement.LEFT);
                if (!side.isOnSide()) side = RowPlacement.LEFT;
                for (PaneWallPage place : PaneWallPage.values()) {
                    editor.putString(arrangementKey(place, PlaceOrientation.LANDSCAPE, KEY_APPS_ROW),
                        side.storageValue());
                }
            }

            // The display's extra keys column, which is now every place's.
            if (mStore.contains(TERMUX_APP.KEY_X11_EXTRA_KEYS_SIDE)) {
                RowPlacement side = RowPlacement.parse(
                    mStore.getString(TERMUX_APP.KEY_X11_EXTRA_KEYS_SIDE, null), RowPlacement.BOTTOM);
                if (side == RowPlacement.HIDDEN) side = RowPlacement.BOTTOM;
                for (PlaceOrientation orientation : PlaceOrientation.values()) {
                    editor.putString(arrangementKey(PaneWallPage.DISPLAY, orientation, KEY_EXTRA_KEYS),
                        side.storageValue());
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
                        editor.putBoolean(arrangementKey(place, orientation, KEY_STATUS_COMPACT),
                            compact);
                    }
                }
            }
        }

        if (fromVersion < 2) {
            // The Layout page replaced the global apps-row and extra-keys switches with a Hidden
            // placement every place and orientation can set on its own. A master that was off
            // becomes Hidden everywhere, once; resolve() has no more use for the master afterwards.
            if (!mPreferences.isAppLauncherAppsRowEnabled()) {
                for (PaneWallPage place : PaneWallPage.values()) {
                    for (PlaceOrientation orientation : PlaceOrientation.values()) {
                        editor.putString(arrangementKey(place, orientation, KEY_APPS_ROW),
                            RowPlacement.HIDDEN.storageValue());
                    }
                }
            }
            if (!mPreferences.isAppLauncherExtraKeysRowEnabled()) {
                for (PaneWallPage place : PaneWallPage.values()) {
                    for (PlaceOrientation orientation : PlaceOrientation.values()) {
                        editor.putString(arrangementKey(place, orientation, KEY_EXTRA_KEYS),
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
            // unwritten — the read below still answers with the shipped default, and pinning it
            // would freeze a default that still moves with the dock's style.
            for (PaneWallPage place : PaneWallPage.values()) {
                String dockOverride = PlaceLookPreferences.lookKey(place,
                    TERMUX_APP.KEY_APP_LAUNCHER_BAR_HEIGHT);
                Float dock = storedFloat(dockOverride);
                if (dock == null) dock = storedFloat(TERMUX_APP.KEY_APP_LAUNCHER_BAR_HEIGHT);
                for (PlaceOrientation orientation : PlaceOrientation.values()) {
                    if (dock != null) {
                        editor.putFloat(arrangementKey(place, orientation, KEY_DOCK_HEIGHT),
                            TermuxAppSharedPreferences.clampAppLauncherBarHeightScale(dock));
                    }
                    Float keyboard = storedKeyboardHeightScale(orientation);
                    if (keyboard != null) {
                        editor.putFloat(arrangementKey(place, orientation, KEY_KEYBOARD_HEIGHT),
                            TermuxAppSharedPreferences.clampInAppKeyboardHeightScale(keyboard));
                    }
                    if (mStore.contains(TERMUX_APP.KEY_IN_APP_KEYBOARD_BOTTOM_PADDING)) {
                        editor.putInt(arrangementKey(place, orientation, KEY_KEYBOARD_CHIN),
                            TermuxAppSharedPreferences.clampInAppKeyboardBottomPadding(
                                mStore.getInt(TERMUX_APP.KEY_IN_APP_KEYBOARD_BOTTOM_PADDING,
                                    TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING)));
                    }
                }
                // Dock height has left the scopable look keys, so a place's old override has
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
            // the bar took. It is per place and orientation now, beside the sizes version 3 moved.
            //
            // BOTH orientations are seeded with the one value the place had, so nobody is asked
            // again for a choice they have already made — a bar left expanded stays expanded
            // everywhere it was. Only a place that never stored one is left unwritten, which is the
            // one case the landscape default in isStatusCompact() is allowed to answer.
            for (PaneWallPage place : PaneWallPage.values()) {
                String legacy = memoryKey(place, KEY_STATUS_COMPACT);
                if (!mStore.contains(legacy)) continue;
                boolean compact = mStore.getBoolean(legacy, false);
                for (PlaceOrientation orientation : PlaceOrientation.values()) {
                    editor.putBoolean(arrangementKey(place, orientation, KEY_STATUS_COMPACT),
                        compact);
                }
                editor.remove(legacy);
            }
        }

        editor.putInt(KEY_MIGRATED, MIGRATION_VERSION);
        editor.apply();
        mRevision++;
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
    private String readString(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                              @NonNull String key) {
        return readString(arrangementKey(place, orientation, key));
    }

    @Nullable
    private String readString(@NonNull String key) {
        return mStore == null ? null : mStore.getString(key, null);
    }

    private int readInt(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                        @NonNull String key, int fallback) {
        String scoped = arrangementKey(place, orientation, key);
        return mStore != null && mStore.contains(scoped) ? mStore.getInt(scoped, fallback) : fallback;
    }

    private void writeString(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                             @NonNull String key, @NonNull String value) {
        writeString(arrangementKey(place, orientation, key), value);
    }

    private void writeString(@NonNull String key, @NonNull String value) {
        if (mStore == null) return;
        mStore.edit().putString(key, value).apply();
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
