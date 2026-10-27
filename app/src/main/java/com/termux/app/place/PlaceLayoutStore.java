package com.termux.app.place;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.chrome.DressResolver;
import com.termux.app.dock.DockStyle;
import com.termux.app.dock.DrawerTilePolicy;
import com.termux.app.dock.WallStripRowPolicy;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PaneWallPage;
import com.termux.app.wall.WallPages.PageKind;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Where the launcher keeps its arrangement. Anything that decides what is on screen and where is a
 * property of the orientation, and one arrangement per orientation is shared by every place — the
 * widget grid, the terminal and the Linux display all stand in the same chrome (ADR 0003).
 *
 * <p>Keys are {@code layout.<portrait|landscape>.<key>} for the arrangement,
 * {@code place.<home|terminal|display>.<key>} for the one thing a place still remembers of its own
 * — whether it was left with the keyboard up — and {@code place.minimal} for minimal mode, which
 * is one mode for the whole launcher rather than a place's. A missing arrangement key falls back
 * to the shared value the launcher used to keep globally, and then to the shipped default, so
 * nothing has to be written before the chrome reads the way it always looked.
 *
 * <p>There are two arrangements, one per {@link LayoutVariant}: the normal one, and the one
 * minimal mode stands in, under {@code layout.minimal.<portrait|landscape>.<key>}. A store made
 * with the public constructor reads and writes the variant minimal mode selects; {@link
 * #forVariant} pins it to one. The minimal layout is seeded from the normal one the first time it
 * is used ({@link MinimalMode#apply}), and is the user's from then on.
 *
 * <p>One layout dresses both halves of the wall (ADR 0009): a Home page wears the Workspace's
 * layout, normal or minimal, less what Home leaves out ({@link #resolve(PlaceOrientation,
 * PaneWallPage)}), unless Home holds a <b>Home layout</b> of its own ({@link #hasHomeLayout},
 * {@code place.home_layout}). That is a third arrangement ({@link LayoutVariant#HOME}), under
 * {@code layout.home.<portrait|landscape>.<key>}: the status bar, the dock's rows and the keyboard
 * are its elements, so the extra keys always resolve put away there. Turned on, it
 * starts as a copy of what Home wore ({@link #setHomeLayout}). The sizes are not its own: the
 * apps row's height, the Strip row's, and the keyboard's height and chin are <b>linked</b> keys
 * ({@link #LINKED_KEYS}), which the Home layout reads and writes in the normal layout's scope,
 * so both halves stand at one size and nothing resizes on the swipe (#84). Style, Corners and
 * Margin are linked the same way, in the preferences' per-half accessors. Home's keyboard element is its own
 * whichever layout Home wears ({@code layout.home.keyboard_shown}, absent is put away): shown, it
 * is always on ({@link #isHomeKeyboardAlwaysOn}). The widget grid is Home's alone, so its columns
 * and rows live in the Home layout's keys whichever variant asks for them.
 *
 * <p>Each layout holds its own Dock design, Search field and Drawer tile with its position, one
 * value for both orientations: {@code layout.<variant scope>dock_design}, {@code search_field},
 * {@code drawer_tile} and {@code drawer_tile_position} ({@link #dockStyle}). An absent or unknown
 * design reads as the glass tray.
 *
 * <p>The arrangement used to be kept per place as well as per orientation. Version 6 of the
 * migration folds that into the shared keys, seeding each orientation from the place the value
 * showed on — the terminal's for everything the terminal draws — and then drops every place's own
 * copy. The same step folds the place-scoped look overrides into the shared look, since those were
 * kept in this file's key scheme too. Version 7 folds the per-place minimal flags into the one
 * launcher-wide flag. Version 8 drops the alphabets index's minimised flag: the pull tab is gone,
 * and an index that was minimised reads as shown. Version 9 moves Home onto the shared layout,
 * keeping a Home that differed as a Home layout ({@link #migrateToOneLayout}). Version 10 gives a
 * kept Home layout the Workspace's status bar, which Home now wears ({@link
 * #migrateHomeLayoutToTheBar}). Version 11 turns the Prompt line design into the Tray with the
 * Search field on ({@link #migratePromptLineToTheSearchField}). Version 12 turns the Taskbar
 * design into the Tray with the Drawer tile on where it stood ({@link
 * #migrateTaskbarToTheDrawerTile}). Version 13 drops the Home layout's own sizes and shape, which
 * it now reads linked from the Workspace's ({@link #migrateToLinkedKeys}). Version 14 gives every
 * install a Home layout: the switch is gone, and Home and the Workspace each keep their own
 * arrangement ({@link #migrateToHomeLayoutAlways}).
 *
 * <p>No Android views here, on purpose: this is a resolver over {@link SharedPreferences} and it is
 * tested as one.
 */
public final class PlaceLayoutStore {

    /** Bumped when a new set of old keys has to be folded into the current ones. */
    @VisibleForTesting static final int MIGRATION_VERSION = 15;

    @VisibleForTesting static final String KEY_MIGRATED = "place.migrated";

    /**
     * Set once {@link #resetPortraitKeyboardResizeOnce} has run, on every install, fresh ones
     * included. A flag of its own and not a {@link #MIGRATION_VERSION} step: the pre-release line
     * ships the same reset under this same key at a lower version number, and a version bump there
     * would make a later move to this line skip its own steps. One key for both, so it runs once
     * across them.
     */
    @VisibleForTesting static final String KEY_KEYBOARD_OVERLAY_RESET_DONE =
        "place_keyboard_overlay_reset_done";

    private static final String PREFIX = "place.";
    private static final String LAYOUT_PREFIX = "layout.";

    /** Where a place kept its own value for a shared look key, before the look was one look. */
    private static final String LEGACY_LOOK_INFIX = ".look.";

    private static final String KEY_STATUS_BAR = Element.STATUS.storageKey();
    private static final String KEY_APPS_ROW = Element.APPS.storageKey();
    private static final String KEY_AZ_ROW = "az_row";
    private static final String KEY_AZ_BAR = Element.AZ.storageKey();
    /**
     * Whether a shown alphabets index stood as its pull tab, beside {@code az_row}. The tab was
     * removed on 2026-09-30; version 8 of the migration deletes the key, and {@code az_row} alone
     * says on or off, so an index that was minimised comes back as the band it folded from.
     */
    private static final String LEGACY_KEY_AZ_MINIMISED = "az_minimised";
    private static final String KEY_EXTRA_KEYS = Element.EXTRA_KEYS.storageKey();

    /**
     * An element's position in its edge's stack sits beside its placement:
     * {@code layout.<orientation>.<placement key>_order}. Absent means the stack the launcher has
     * always drawn ({@link Element#defaultOrder}), so an updated install renders identically
     * without anything being written for it.
     */
    private static final String ORDER_SUFFIX = "_order";

    /**
     * Whether a bottom band stands under the keyboard rather than over it, beside its order:
     * {@code layout.<orientation>.<placement key>_under_keyboard}. Absent is over it, which is
     * where every band has always stood, so nothing is written for an install that predates it.
     */
    private static final String UNDER_KEYBOARD_SUFFIX = "_under_keyboard";

    /**
     * The edge a put-away bar comes back to, beside its placement:
     * {@code layout.<orientation>.<placement key>_restore_edge}. The status bar, the pinned apps and
     * the extra keys store {@code hidden} in their placement key, which leaves no room for the edge
     * they went away from, so the Layout editor's tray could only ever bring them back to where
     * each one started (spec §3.6). Written when a bar is put away and read only while it is;
     * absent is the bar's own default edge — the top for the status bar, the bottom for the rows.
     * The alphabets index needs none: its edge has always had a key of its own ({@code az_bar})
     * beside the on/off switch.
     */
    private static final String RESTORE_EDGE_SUFFIX = "_restore_edge";

    /** A row placement that is not an edge at all. */
    private static final String VALUE_HIDDEN = "hidden";
    private static final String KEY_KEYBOARD_MODE = "keyboard_mode";
    private static final String KEY_KEYBOARD_FORM = "keyboard_form";
    private static final String KEY_WIDGET_COLUMNS = "widget_columns";
    private static final String KEY_WIDGET_ROWS = "widget_rows";

    private static final String KEY_DOCK_HEIGHT = "dock_height";
    private static final String KEY_KEYBOARD_HEIGHT = "keyboard_height";
    private static final String KEY_KEYBOARD_CHIN = "keyboard_chin";
    /**
     * The Wall strip's first row's height in dp, beside the dock's: {@code
     * layout.<orientation>.wall_strip_height}, per layout like every size. Absent is the shipped
     * row ({@link WallStripRowPolicy#DEFAULT_HEIGHT_DP}); nothing is written until the Layout
     * editor's Strip handle is dragged.
     */
    private static final String KEY_WALL_STRIP_HEIGHT = "wall_strip_height";

    private static final String KEY_STATUS_COMPACT = "status_compact";

    /**
     * Where the bar has never been rested in landscape, it rests compact: it is the orientation
     * with the least height and the most of it already spoken for. A default, never a write.
     */
    private static final boolean LANDSCAPE_RESTS_COMPACT = true;

    private static final String KEY_KEYBOARD_OPEN = "keyboard_open";
    /**
     * The Home layout's keyboard element, one value for both orientations like the Workspace's:
     * {@code layout.home.keyboard_shown}. Shown is always on; absent is put away, which is what the
     * Home layout is seeded with.
     */
    private static final String KEY_HOME_KEYBOARD = "keyboard_shown";
    /**
     * How many rows always on took from the widget grid when it was turned on, so the grid is
     * applied at the same height on every later pass: {@code layout.home.keyboard_rows}.
     */
    private static final String KEY_HOME_KEYBOARD_ROWS = "keyboard_rows";
    /**
     * Home's dock style before version 9, one value for both orientations:
     * {@code layout.home.dock_style}. Version 9 moves it to every layout's {@link #KEY_DOCK_DESIGN}.
     */
    private static final String LEGACY_KEY_HOME_DOCK_STYLE = "dock_style";
    /**
     * Where the Taskbar's Home tile stood in Home's dock row before version 9:
     * {@code layout.home.taskbar_home_tile}. Version 9 moves it to every layout's
     * {@link #KEY_DRAWER_TILE_POSITION}.
     */
    private static final String LEGACY_KEY_HOME_TASKBAR_HOME_TILE = "taskbar_home_tile";
    /**
     * A layout's Dock design, one value for both orientations: {@code layout.dock_design},
     * {@code layout.minimal.dock_design}, {@code layout.home.dock_design}, a {@link DockStyle}
     * id. Absent or unknown is the glass tray; a new install's first run writes
     * {@link DockStyle#NEW_INSTALL_DEFAULT} into each ({@link #startsOnNewInstallDefault}).
     */
    private static final String KEY_DOCK_DESIGN = "dock_design";
    /**
     * Whether a layout's dock carries the Search field, a toggle of its own beside the design
     * (#96): {@code layout.search_field}, {@code layout.minimal.search_field},
     * {@code layout.home.search_field}. Absent is off, unless the layout still holds the Prompt
     * line design the field came from (version 11 of the migration turns that into the Tray
     * with the field on).
     */
    private static final String KEY_SEARCH_FIELD = "search_field";
    /**
     * Whether a layout's dock carries the Drawer tile among its apps, a toggle of its own beside
     * the design (#97), as {@link #KEY_SEARCH_FIELD} is the Search field: {@code
     * layout.drawer_tile}, {@code layout.minimal.drawer_tile}, {@code layout.home.drawer_tile}.
     * Absent is off, unless the layout still holds the Taskbar design the tile came from (version
     * 12 of the migration turns that into the Tray with the tile on).
     */
    private static final String KEY_DRAWER_TILE = "drawer_tile";
    /**
     * Where a layout's Drawer tile stands in the dock row, a
     * {@link DrawerTilePolicy.Position} value. Absent is the start.
     */
    private static final String KEY_DRAWER_TILE_POSITION = "drawer_tile_position";
    /**
     * Minimal mode, one flag for the whole launcher: {@code place.minimal}. Before version 7 each
     * place kept its own under {@code place.<p>.minimal}, which is the same unscoped key.
     */
    private static final String KEY_MINIMAL = "minimal";
    /**
     * Whether Home holds a layout of its own, one flag for the whole launcher:
     * {@code place.home_layout}. Version 14 sets it on every install and nothing turns it off
     * any more (the editor's Own layout switch is gone); absent is the old shared layout, which
     * only a store not yet migrated reads.
     */
    private static final String KEY_HOME_LAYOUT = "home_layout";
    private static final String KEY_KEYBOARD_FLOAT_X = "keyboard_float_x";
    private static final String KEY_KEYBOARD_FLOAT_Y = "keyboard_float_y";
    /** Where the dictation pill was dragged to, beside the floating keyboard's place. */
    private static final String KEY_VOICE_PILL_X = "voice_pill_x";
    private static final String KEY_VOICE_PILL_Y = "voice_pill_y";

    /**
     * The per-place choice of how the keyboard came back on entry. Gone with version 6: Home always
     * comes back closed and the other two as they were left, so there is nothing left to choose.
     */
    private static final String LEGACY_KEY_KEYBOARD_ON_ENTER = "keyboard_on_enter";

    /** A floating keyboard that has never been moved has no remembered place to come back to. */
    public static final float FLOAT_POSITION_UNSET = -1f;

    /**
     * The launcher's old status-bar hide switch for the display alone. Dropped by the first
     * migration rather than folded: the shared layout starts with the bar shown, and hiding it is
     * the Layout editor's tray now, for every place at once.
     */
    private static final String LEGACY_KEY_X11_HIDE_STATUS_BAR = "x11_hide_status_bar";

    private static final String[] ARRANGEMENT_KEYS = {
        KEY_STATUS_BAR, KEY_APPS_ROW, KEY_AZ_ROW, KEY_AZ_BAR, KEY_EXTRA_KEYS,
        KEY_KEYBOARD_MODE,
        KEY_KEYBOARD_FORM, KEY_WIDGET_COLUMNS, KEY_WIDGET_ROWS,
        KEY_DOCK_HEIGHT, KEY_KEYBOARD_HEIGHT, KEY_KEYBOARD_CHIN, KEY_WALL_STRIP_HEIGHT,
        // The stack positions ride with the placements they belong to, so the Layout editor's
        // Discard and its reset put a re-order back the same way they put a move back.
        orderKeyName(Element.STATUS), orderKeyName(Element.APPS), orderKeyName(Element.AZ),
        orderKeyName(Element.EXTRA_KEYS),
        underKeyboardKeyName(Element.APPS), underKeyboardKeyName(Element.AZ),
        underKeyboardKeyName(Element.EXTRA_KEYS),
        restoreEdgeKeyName(Element.STATUS), restoreEdgeKeyName(Element.APPS),
        restoreEdgeKeyName(Element.EXTRA_KEYS),
        PlaceLayoutStore.KEY_BAR_ORDER
    };

    /**
     * Everything version 6 folds out of the per-place keys, per orientation: the arrangement, and
     * the two things remembered beside it per orientation — the bar's resting state and where a
     * floating keyboard was parked.
     */
    private static final String[] FOLDED_KEYS = concat(ARRANGEMENT_KEYS,
        KEY_STATUS_COMPACT, KEY_KEYBOARD_FLOAT_X, KEY_KEYBOARD_FLOAT_Y);

    /**
     * The <b>linked</b> keys (#84): arrangement keys the Home layout never holds of its own. A
     * store on the Home layout reads and writes them in the normal layout's scope ({@link
     * #variantKey}), so with Home's own layout on, the apps row's height, the Strip row's height
     * and the keyboard's height and chin are still one value on both halves of the wall, edited
     * from either. The normal layout's and not the minimal one's: minimal mode never dresses
     * Home. The keyboard's form is an arrangement, not a size, and stays Home's own.
     */
    private static final String[] LINKED_KEYS = {
        KEY_DOCK_HEIGHT, KEY_WALL_STRIP_HEIGHT, KEY_KEYBOARD_HEIGHT, KEY_KEYBOARD_CHIN
    };

    /**
     * Whether two layouts read their sizes from the same keys: the Home layout and the normal one,
     * through the {@link #LINKED_KEYS}. What a holder of two snapshots asks, so a size edited
     * through one is not taken as the other's starting value.
     */
    public static boolean sizesLinked(@NonNull LayoutVariant a, @NonNull LayoutVariant b) {
        return (a == LayoutVariant.HOME && b == LayoutVariant.NORMAL)
            || (a == LayoutVariant.NORMAL && b == LayoutVariant.HOME);
    }

    /** Whether {@code key} is one of the {@link #LINKED_KEYS}. */
    private static boolean isLinkedKey(@NonNull String key) {
        for (String linked : LINKED_KEYS) {
            if (linked.equals(key)) return true;
        }
        return false;
    }

    /** The unscoped key one element's stack position is stored under. */
    @VisibleForTesting
    @NonNull
    static String orderKeyName(@NonNull Element element) {
        return element.storageKey() + ORDER_SUFFIX;
    }

    /** The unscoped key a put-away bar's edge is remembered under. */
    @VisibleForTesting
    @NonNull
    static String restoreEdgeKeyName(@NonNull Element element) {
        return element.storageKey() + RESTORE_EDGE_SUFFIX;
    }

    /** The unscoped key saying whether one element stands under the keyboard. */
    @VisibleForTesting
    @NonNull
    static String underKeyboardKeyName(@NonNull Element element) {
        return element.storageKey() + UNDER_KEYBOARD_SUFFIX;
    }

    @NonNull private final TermuxAppSharedPreferences mPreferences;
    @Nullable private final SharedPreferences mStore;

    /** Shared by every {@link #forVariant view} of one store, so any write retires every cache. */
    @NonNull private final AtomicInteger mRevision;

    /**
     * The variant this store reads and writes, or null for the one the launcher is standing in
     * ({@link #activeVariant}), which follows minimal mode.
     */
    @Nullable private final LayoutVariant mFixedVariant;

    /**
     * Per variant and orientation, whether a seeded layout (the minimal one, the Home one) has
     * been checked for its first seeding. Shared by every view of one store.
     */
    @NonNull private final boolean[][] mSeeded;

    /** The views {@link #forVariant} hands out, one per variant, shared by every view. */
    @NonNull private final PlaceLayoutStore[] mVariantViews;

    /**
     * The Home layout switch and this view's Dock design as last read, and the revision (and for
     * the design the variant) they were read at: the travel's per-frame reads (the bead melt, the
     * shape each surface wears) compare an int rather than read and parse preferences.
     */
    private int mHomeLayoutRevision = Integer.MIN_VALUE;
    private boolean mHomeLayout;
    /**
     * Minimal mode as last read, cached as the Home layout switch is: a window's Dock design is
     * the minimal layout's or the normal one's, and the key lens asks that design every frame.
     */
    private int mMinimalRevision = Integer.MIN_VALUE;
    private boolean mMinimal;
    private int mDockDesignRevision = Integer.MIN_VALUE;
    @Nullable private LayoutVariant mDockDesignVariant;
    @NonNull private DockStyle mDockDesign = DockStyle.GLASS_TRAY;
    /** This view's Search field as last read, cached as the design is: the dock's pass asks it. */
    private int mSearchFieldRevision = Integer.MIN_VALUE;
    @Nullable private LayoutVariant mSearchFieldVariant;
    private boolean mSearchField;
    /** This view's Drawer tile as last read, cached as the Search field is: the row asks it. */
    private int mDrawerTileRevision = Integer.MIN_VALUE;
    @Nullable private LayoutVariant mDrawerTileVariant;
    private boolean mDrawerTile;

    /** What each page wears, over this view; made the first time a page asks. */
    @Nullable private DressResolver mDresses;

    /**
     * Any write to the launcher's preferences can change what the layout resolves to — the shared
     * keys and the global ones a missing key falls back to alike — so every one of them retires a
     * cached layout.
     */
    private final SharedPreferences.OnSharedPreferenceChangeListener mChangeListener =
        this::onPreferenceChanged;

    private void onPreferenceChanged(@NonNull SharedPreferences preferences, @Nullable String key) {
        mRevision.incrementAndGet();
    }

    public PlaceLayoutStore(@NonNull TermuxAppSharedPreferences preferences) {
        mPreferences = preferences;
        mStore = preferences.getSharedPreferences();
        mRevision = new AtomicInteger();
        mFixedVariant = null;
        mMigrating = new java.util.concurrent.atomic.AtomicBoolean();
        mSeeded = new boolean[LayoutVariant.values().length][PlaceOrientation.values().length];
        mVariantViews = new PlaceLayoutStore[LayoutVariant.values().length];
        migrateIfNeeded();
        resetPortraitKeyboardResizeOnce();
        if (mStore != null) mStore.registerOnSharedPreferenceChangeListener(mChangeListener);
    }

    /** True while the migration runs: slots read what the arrangement keys held. */
    private final java.util.concurrent.atomic.AtomicBoolean mMigrating;

    private PlaceLayoutStore(@NonNull PlaceLayoutStore root, @NonNull LayoutVariant variant) {
        mPreferences = root.mPreferences;
        mStore = root.mStore;
        mRevision = root.mRevision;
        mFixedVariant = variant;
        mMigrating = root.mMigrating;
        mSeeded = root.mSeeded;
        mVariantViews = root.mVariantViews;
    }

    /**
     * The variant the launcher is standing in: the minimal layout while minimal mode is on, the
     * normal one otherwise. What a store made with the public constructor reads and writes.
     */
    @NonNull
    public LayoutVariant activeVariant() {
        return mFixedVariant != null ? mFixedVariant : LayoutVariant.of(isMinimal());
    }

    /**
     * This store pinned to one variant, whatever minimal mode is doing: the Layout editor opens
     * on the variant the launcher was in and keeps editing it. Shares the revision and the
     * preferences with this store, so nothing is copied.
     */
    @NonNull
    public PlaceLayoutStore forVariant(@NonNull LayoutVariant variant) {
        if (mFixedVariant == variant) return this;
        PlaceLayoutStore view = mVariantViews[variant.ordinal()];
        if (view == null) {
            view = new PlaceLayoutStore(this, variant);
            mVariantViews[variant.ordinal()] = view;
        }
        return view;
    }

    /**
     * The variant a page's chrome is arranged by, and the one the Layout editor opened on it
     * edits: the layout the page's dress wears ({@link DressResolver#layoutOf}), the Workspace's
     * (normal or minimal) on a window, the normal one or Home's own on a Home page while it holds a Home layout.
     */
    @NonNull
    public LayoutVariant variantFor(@NonNull PaneWallPage page) {
        if (mDresses == null) mDresses = DressResolver.of(this);
        return mDresses.layoutOf(page);
    }

    // ---------------------------------------------------------------- the Home layout

    /**
     * Whether Home holds a Home layout of its own (the glossary's <b>Home layout</b>): off, Home
     * wears the shared layout; on, its own copy of which rows show, where and in what order, and
     * its own Dock design and toggles. The sizes and Style, Corners and Margin are the shared
     * layout's either way ({@link #LINKED_KEYS}). The same answer from every view. Read on travel
     * frames, so it is read once per revision.
     */
    public boolean hasHomeLayout() {
        int revision = mRevision.get();
        if (mHomeLayoutRevision != revision) {
            mHomeLayout = mStore != null && mStore.getBoolean(homeLayoutKey(), false);
            mHomeLayoutRevision = revision;
        }
        return mHomeLayout;
    }

    /** {@link #hasHomeLayout()} for a reader that holds the launcher's preferences but not the store. */
    public static boolean hasHomeLayout(@Nullable TermuxAppSharedPreferences preferences) {
        SharedPreferences store = preferences == null ? null : preferences.getSharedPreferences();
        return store != null && store.getBoolean(homeLayoutKey(), false);
    }

    /**
     * Turns the Home layout on or off. Turned on, Home's copy starts as what Home wore on the
     * shared layout (the Workspace's normal layout, its Dock design and toggles), so nothing on
     * screen moves until the user edits it; the linked sizes and Style, Corners and Margin are
     * the shared layout's either way and are not copied, and the widget grid and always on are
     * Home's either way and are left alone. Turned off, Home wears the shared layout again and its
     * copy is no longer read.
     */
    public void setHomeLayout(boolean on) {
        if (mStore == null || hasHomeLayout() == on) return;
        if (on) copyIntoHomeLayout(LayoutVariant.NORMAL);
        writeBoolean(homeLayoutKey(), on);
    }

    /** The Home layout's arrangement and design set to what {@code worn} gives Home. */
    private void copyIntoHomeLayout(@NonNull LayoutVariant worn) {
        PlaceLayoutStore source = forVariant(worn);
        // A minimal layout never used is seeded first, so the copy is what Home showed. A source
        // with nothing stored leaves the Home layout empty, to be seeded on first use as before.
        for (PlaceOrientation orientation : PlaceOrientation.values())
            source.variantKey(orientation, KEY_DOCK_HEIGHT);
        Map<String, ?> all = mStore.getAll();
        SharedPreferences.Editor editor = mStore.edit();
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            for (String key : ARRANGEMENT_KEYS) {
                if (isGridKey(key)) continue;
                String home = layoutKey(LayoutVariant.HOME, orientation, key);
                // A linked key is read from the normal layout; a stale copy is only dropped.
                if (isLinkedKey(key)) editor.remove(home);
                else copy(editor, all, layoutKey(worn, orientation, key), home);
            }
        }
        for (String key : LAYOUT_WIDE_KEYS)
            copy(editor, all, layoutWideKey(worn, key), layoutWideKey(LayoutVariant.HOME, key));
        editor.apply();
        mRevision.incrementAndGet();
    }

    /**
     * Copies {@code from}'s arrangement over {@code to}'s: the Match rows' layout half (Match
     * Workspace Styling on the Home card, Match Home Styling on the Workspace card). Where every
     * bar stands in either orientation, which are put away and their order, the keyboard's form,
     * and the Dock design with its Search field and Drawer tile. Not copied: the widget grid
     * (Home's alone), the linked sizes when Home is either side (one value already, {@link
     * #LINKED_KEYS}), and when Home is either side the extra keys, which only the Workspace wears
     * ({@link DressResolver#wornOn}). A key {@code from} does not hold is removed from {@code to},
     * so the copy reads exactly as the source did. {@link #arrangementKeys} names every key this
     * writes, for an undo snapshot taken before it.
     */
    public void copyArrangement(@NonNull LayoutVariant from, @NonNull LayoutVariant to) {
        if (mStore == null || from == to) return;
        // A layout never used is seeded first, so the copy is what that screen showed.
        seed(from);
        Map<String, ?> all = mStore.getAll();
        SharedPreferences.Editor editor = mStore.edit();
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            for (String key : ARRANGEMENT_KEYS) {
                if (copiesArrangementKey(key, from, to))
                    copy(editor, all, layoutKey(from, orientation, key),
                        layoutKey(to, orientation, key));
            }
        }
        for (String key : LAYOUT_WIDE_KEYS)
            copy(editor, all, layoutWideKey(from, key), layoutWideKey(to, key));
        // Which elements show comes along: the source layout's visibility becomes its kind's.
        if (to != LayoutVariant.MINIMAL) {
            PlaceLayoutStore source = forVariant(from);
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                for (Element element : Element.values()) {
                    if (!from.offers(element) || !to.offers(element)) continue;
                    editor.putBoolean(kindKey(kindOfVariant(to), orientation, element),
                        !source.slot(orientation, element).hidden);
                }
            }
        }
        editor.apply();
        // The target is whole now: it must not be seeded over on its next read.
        for (PlaceOrientation orientation : PlaceOrientation.values())
            mSeeded[to.ordinal()][orientation.ordinal()] = true;
        mRevision.incrementAndGet();
    }

    /**
     * Gives {@code variant}'s layout its starting point in every orientation it has not been used
     * in yet ({@link #seedIfNeeded}), so what is read or copied from it, or taken before a copy
     * over it, is what that screen shows. Nothing for the normal layout, which is never seeded.
     */
    public void seed(@NonNull LayoutVariant variant) {
        for (PlaceOrientation orientation : PlaceOrientation.values())
            seedIfNeeded(variant, orientation);
    }

    /** Every key {@link #copyArrangement copyArrangement(from, to)} may write or remove. */
    @NonNull
    public java.util.List<String> arrangementKeys(@NonNull LayoutVariant from,
                                                  @NonNull LayoutVariant to) {
        java.util.List<String> keys = new java.util.ArrayList<>();
        if (from == to) return keys;
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            for (String key : ARRANGEMENT_KEYS) {
                if (copiesArrangementKey(key, from, to))
                    keys.add(layoutKey(to, orientation, key));
            }
        }
        for (String key : LAYOUT_WIDE_KEYS)
            keys.add(layoutWideKey(to, key));
        if (to != LayoutVariant.MINIMAL) {
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                for (Element element : Element.values())
                    keys.add(kindKey(kindOfVariant(to), orientation, element));
            }
        }
        return keys;
    }

    /** Whether a copy between two layouts carries {@code key} ({@link #copyArrangement}). */
    private static boolean copiesArrangementKey(@NonNull String key, @NonNull LayoutVariant from,
                                                @NonNull LayoutVariant to) {
        if (isGridKey(key)) return false;
        boolean home = from == LayoutVariant.HOME || to == LayoutVariant.HOME;
        return !home || (!isLinkedKey(key) && !isWorkspaceOnlyKey(key));
    }

    /** The extra keys' placement keys: the one element only the Workspace wears. */
    private static boolean isWorkspaceOnlyKey(@NonNull String key) {
        return KEY_EXTRA_KEYS.equals(key) || orderKeyName(Element.EXTRA_KEYS).equals(key)
            || underKeyboardKeyName(Element.EXTRA_KEYS).equals(key)
            || restoreEdgeKeyName(Element.EXTRA_KEYS).equals(key);
    }

    /** {@code to} set to what {@code all} holds under {@code from}, or removed where it holds none. */
    private static void copy(@NonNull SharedPreferences.Editor editor, @NonNull Map<String, ?> all,
                             @NonNull String from, @NonNull String to) {
        Object value = all.get(from);
        if (value != null) put(editor, to, value);
        else editor.remove(to);
    }

    /**
     * Whether two layouts arrange what both of them wear differently, in either orientation:
     * where each such element stands (its edge, whether it is put away, its place in the stack,
     * whether it is under the keyboard) and the dock's and the Strip row's heights. The Home
     * layout wears only the dock's rows and the bar, so against it only those count, and its
     * heights are linked to the normal layout's ({@link #LINKED_KEYS}), so against that one they
     * never differ. The Dock design is not an arrangement and never counts; neither do the
     * keyboard's sizes and form.
     */
    public boolean arrangementsDiffer(@NonNull LayoutVariant a, @NonNull LayoutVariant b) {
        return arrangementsDiffer(a, b, null);
    }

    /** {@link #arrangementsDiffer(LayoutVariant, LayoutVariant)}, {@code ignored} left out too. */
    private boolean arrangementsDiffer(@NonNull LayoutVariant a, @NonNull LayoutVariant b,
                                       @Nullable Element ignored) {
        PlaceLayoutStore first = forVariant(a);
        PlaceLayoutStore second = forVariant(b);
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            for (Element element : Element.values()) {
                if (element == ignored || !DressResolver.wornOn(a, element)
                    || !DressResolver.wornOn(b, element))
                    continue;
                if (!first.slot(orientation, element).equals(second.slot(orientation, element)))
                    return true;
            }
            if (Float.compare(first.dockHeightScale(orientation),
                    second.dockHeightScale(orientation)) != 0
                || first.wallStripHeightDp(orientation) != second.wallStripHeightDp(orientation))
                return true;
        }
        return false;
    }

    /**
     * Whether two layouts hold the keyboard in a different form, in either orientation. Apart
     * from {@link #arrangementsDiffer}, which leaves it out, since only the migration to one
     * layout asks, and only while Home's keyboard is always on. The keyboard's height and chin
     * are linked ({@link #LINKED_KEYS}), so they are no difference.
     */
    private boolean keyboardFormsDiffer(@NonNull LayoutVariant a, @NonNull LayoutVariant b) {
        PlaceLayoutStore first = forVariant(a);
        PlaceLayoutStore second = forVariant(b);
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            if (first.keyboardForm(orientation) != second.keyboardForm(orientation))
                return true;
        }
        return false;
    }

    /** {@code place.home_layout}: the launcher's, not a layout's. */
    @VisibleForTesting
    @NonNull
    static String homeLayoutKey() {
        return PREFIX + KEY_HOME_LAYOUT;
    }

    /**
     * One half's Style, Corners and Margin as the preferences hold them now ({@link
     * WallShape#read}): what the Dress resolver shapes a page by. Linked like the sizes, so
     * Home's half answers the Workspace's and a Home layout brings no shape of its own (#84).
     */
    @NonNull
    public WallShape shape() {
        return WallShape.read(mPreferences);
    }

    /**
     * Counts changes to anything a resolved layout is a function of. A caller that holds a
     * {@link PlaceLayout} can keep it while this has not moved.
     */
    public int revision() {
        return mRevision.get();
    }

    // ---------------------------------------------------------------- kind visibility

    /** The kind of page the layout {@code variant} dresses: Home's own, else the Terminal's. */
    @NonNull
    static PageKind kindOfVariant(@NonNull LayoutVariant variant) {
        return variant == LayoutVariant.HOME ? PageKind.HOME : PageKind.TERMINAL;
    }

    /**
     * {@code place.dress.<kind>.<orientation>.<element>}: whether pages of one kind show one element
     * in one orientation (the page model, #171). A boolean; absent reads {@link #kindShowsByDefault}.
     */
    @VisibleForTesting
    @NonNull
    static String kindKey(@NonNull PageKind kind, @NonNull PlaceOrientation orientation,
                          @NonNull Element element) {
        return PREFIX + "dress." + kind.name().toLowerCase(Locale.ROOT) + "."
            + orientation.storageValue() + "." + element.storageKey();
    }

    /**
     * What a kind shows with nothing stored: Home pages the status bar and the dock (the apps row
     * and the A-Z index); Terminal and Display pages the status bar and the extra keys.
     */
    public static boolean kindShowsByDefault(@NonNull PageKind kind, @NonNull Element element) {
        if (element == Element.STATUS) return true;
        if (kind == PageKind.HOME) return element == Element.APPS || element == Element.AZ;
        return element == Element.EXTRA_KEYS;
    }

    /** Whether pages of {@code kind} show {@code element}, as stored. */
    public boolean kindShows(@NonNull PageKind kind, @NonNull PlaceOrientation orientation,
                             @NonNull Element element) {
        boolean fallback = kindShowsByDefault(kind, element);
        return mStore == null ? fallback
            : mStore.getBoolean(kindKey(kind, orientation, element), fallback);
    }

    /** Whether a visibility is stored for the kind, orientation and element (else the default). */
    @VisibleForTesting
    boolean hasKindKey(@NonNull PageKind kind, @NonNull PlaceOrientation orientation,
                       @NonNull Element element) {
        return mStore != null && mStore.contains(kindKey(kind, orientation, element));
    }

    /** Whether {@code kind} shows {@code element} in either orientation: what a dress, which has no orientation, wears. */
    public boolean kindShowsAny(@NonNull PageKind kind, @NonNull Element element) {
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            if (kindShows(kind, orientation, element)) return true;
        }
        return false;
    }

    /** Shows or hides {@code element} on every page of {@code kind}, in one orientation. */
    public void setKindShows(@NonNull PageKind kind, @NonNull PlaceOrientation orientation,
                             @NonNull Element element, boolean shown) {
        if (kindShows(kind, orientation, element) == shown && (mStore == null
            || mStore.contains(kindKey(kind, orientation, element)))) return;
        writeBoolean(kindKey(kind, orientation, element), shown);
    }

    // ---------------------------------------------------------------- arrangement

    /**
     * The one immutable answer for an orientation, which every place stands in. The getters below
     * say where a row is arranged to go; this is where the switches that can still turn a row off
     * entirely are folded in, so a caller reads one effective layout and nothing else.
     */
    @NonNull
    public PlaceLayout resolve(@NonNull PlaceOrientation orientation, @NonNull LayoutVariant variant) {
        return forVariant(variant).resolve(orientation);
    }

    /**
     * The layout {@code page} wears in an orientation, as its dress has it: the variant {@link
     * #variantFor} names, resolved for that page ({@link #resolve(PlaceOrientation,
     * LayoutVariant, PaneWallPage)}).
     */
    @NonNull
    public PlaceLayout resolve(@NonNull PlaceOrientation orientation, @NonNull PaneWallPage page) {
        return resolve(orientation, variantFor(page), page);
    }

    /**
     * {@code variant}'s layout as {@code page} wears it. A window wears it whole, its dock in the
     * Dock design the layout holds (#93). A Home page on the shared layout leaves out what Home
     * never wears (the extra keys, {@link DressResolver#wornBy}), its keyboard element is always
     * on, Home's own whichever layout it wears ({@link #isHomeKeyboardAlwaysOn}), and its dock
     * draws the design the layout holds too. On the Home layout it is that layout as it resolves.
     */
    @NonNull
    public PlaceLayout resolve(@NonNull PlaceOrientation orientation,
                               @NonNull LayoutVariant variant, @NonNull PaneWallPage page) {
        PlaceLayoutStore layout = forVariant(variant);
        PlaceLayout resolved = layout.resolve(orientation);
        if (page != PaneWallPage.WIDGETS || variant == LayoutVariant.HOME) return resolved;
        // A Home page on the shared arrangement shows what the Home kind shows, whatever the
        // shared layout's own kind does.
        PageKind kind = DressResolver.kindOf(page);
        for (Element element : Element.values()) {
            Slot slot = resolved.slot(element);
            boolean shown = kindShows(kind, orientation, element)
                && (element != Element.EXTRA_KEYS || mPreferences.shouldShowTerminalToolbar());
            if (slot.hidden == shown) resolved = resolved.withSlot(element, slot.withHidden(!shown));
        }
        return resolved.withKeyboardShown(isHomeKeyboardAlwaysOn())
            .withDockStyle(DockStyle.effectiveStyle(layout.dockStyle()));
    }

    /** As {@link #resolve(PlaceOrientation, LayoutVariant)}, for the variant this store is on. */
    @NonNull
    public PlaceLayout resolve(@NonNull PlaceOrientation orientation) {
        LayoutVariant variant = activeVariant();
        EnumMap<Element, Slot> slots = new EnumMap<>(Element.class);
        for (Element element : Element.values()) {
            Slot slot = slot(orientation, element);
            // An element the page's kind does not show is not shown, whatever is stored.
            // Minimal mode's layout holds its own visibility; the others follow their kind.
            boolean shown = mMigrating.get() ? variant.offers(element)
                : variant == LayoutVariant.MINIMAL
                    || kindShows(kindOfVariant(variant), orientation, element);
            slots.put(element, shown || slot.hidden ? slot : slot.withHidden(true));
        }
        // The terminal's own toolbar switch can still have put the extra keys away everywhere.
        if (!mPreferences.shouldShowTerminalToolbar()) {
            slots.put(Element.EXTRA_KEYS, slots.get(Element.EXTRA_KEYS).withHidden(true));
        }
        return new PlaceLayout(slots,
            keyboardMode(orientation),
            keyboardForm(orientation),
            isKeyboardShown(),
            widgetColumns(orientation),
            widgetRows(orientation),
            // The Dock design the variant draws: the one it holds, on either half.
            DockStyle.effectiveStyle(dockStyle()),
            // The Dock card's A–Z Show: the pull tab claims no band (editor-redesign §4).
            !com.termux.app.launcher.az.AzShowMode.of(mStore).claimsBand());
    }

    /**
     * Whether the keyboard is part of the arrangement: the Layout editor's Keyboard element,
     * shown or hidden. It is the one switch the palette's Keyboard on/off flips
     * ({@link TermuxAppSharedPreferences#isKeyboardTurnedOff}), read here inverted, so there is
     * one answer to whether the keyboard may come up however the question is asked. One value
     * for both orientations and every place, unlike the rest of the arrangement: the keyboard is
     * switched off because a hardware keyboard is in use, not because the phone was turned, and
     * that stance holds until it is switched on again.
     */
    public boolean isKeyboardShown() {
        if (activeVariant() == LayoutVariant.HOME) return isHomeKeyboardAlwaysOn();
        return !mPreferences.isKeyboardTurnedOff();
    }

    /**
     * Shows or hides the keyboard element, which is the same as turning the keyboard on or off
     * from the palette. Writes the preference only: the keyboard on screen follows on the next
     * layout pass ({@code TermuxActivity}'s), which puts it down when hidden and leaves it to
     * the next tap when shown again.
     */
    public void setKeyboardShown(boolean shown) {
        if (activeVariant() == LayoutVariant.HOME) {
            setHomeKeyboardAlwaysOn(shown);
            return;
        }
        if (mPreferences.isKeyboardTurnedOff() == !shown) return;
        mPreferences.setKeyboardTurnedOff(!shown);
        mRevision.incrementAndGet();
    }

    /**
     * Always on (the Home keyboard): Home's keyboard element shown rather than put away, which
     * keeps the keyboard up on every Home page and gives it rows of the widget grid. Off until the
     * user shows the element in the Layout editor opened on a Home page. Whether the keyboard is
     * up is each half's own, not dress, so this is Home's whichever layout Home wears.
     */
    public boolean isHomeKeyboardAlwaysOn() {
        return mStore != null && mStore.getBoolean(homeKey(KEY_HOME_KEYBOARD), false);
    }

    /** Turns always on on or off: Home's keyboard element shown or put away, on any layout. */
    public void setHomeKeyboardAlwaysOn(boolean on) {
        if (isHomeKeyboardAlwaysOn() != on) writeBoolean(homeKey(KEY_HOME_KEYBOARD), on);
    }

    /** The rows always on took from the grid when it was turned on; 0 while it is off. */
    public int homeKeyboardRows() {
        return mStore == null ? 0 : Math.max(0, mStore.getInt(homeKey(KEY_HOME_KEYBOARD_ROWS), 0));
    }

    public void setHomeKeyboardRows(int rows) {
        writeInt(homeKey(KEY_HOME_KEYBOARD_ROWS), Math.max(0, rows));
    }

    /**
     * The Dock design this view's layout holds, whether or not it is drawn yet; what the Layout
     * editor's picker shows. What a page's dock draws is its dress's ({@link
     * DockStyle#effectiveStyle} of it), on the resolved layout as {@link PlaceLayout#dockStyle}. Read
     * on travel frames (the bead melt), so it is read once per revision.
     */
    @NonNull
    public DockStyle dockStyle() {
        if (mStore == null) return DockStyle.GLASS_TRAY;
        LayoutVariant variant = activeVariant();
        int revision = mRevision.get();
        if (mDockDesignRevision != revision || mDockDesignVariant != variant) {
            mDockDesign = DockStyle.parse(
                mStore.getString(layoutWideKey(variant, KEY_DOCK_DESIGN), null));
            mDockDesignRevision = revision;
            mDockDesignVariant = variant;
        }
        return mDockDesign;
    }

    /**
     * A design picked for this view's layout. The Search field and the Drawer tile are toggles of
     * their own and stay as they are ({@link #setSearchField}, {@link #setDrawerTile}).
     */
    public void setDockStyle(@NonNull DockStyle style) {
        if (mStore == null || style == dockStyle()) return;
        mStore.edit().putString(layoutWideKey(activeVariant(), KEY_DOCK_DESIGN),
            style.storageValue()).apply();
        mRevision.incrementAndGet();
    }

    /**
     * One layout's design as version 9 moved it, with the Drawer tile the Taskbar brought then:
     * version 12 turns that Taskbar into the Tray and keeps the tile.
     */
    private static void putDockDesign(@NonNull SharedPreferences.Editor editor,
                                      @NonNull LayoutVariant variant, @NonNull DockStyle style) {
        editor.putString(layoutWideKey(variant, KEY_DOCK_DESIGN), style.storageValue())
            .putBoolean(layoutWideKey(variant, KEY_DRAWER_TILE), style == DockStyle.TASKBAR);
    }

    /**
     * Whether this view's layout carries the Search field under the dock's apps row, with
     * whichever design it holds (#96). A layout that never stored the toggle reads it off the
     * design: on only for a Prompt line the migration has not turned into the Tray yet.
     */
    public boolean searchField() {
        if (mStore == null) return false;
        LayoutVariant variant = activeVariant();
        int revision = mRevision.get();
        if (mSearchFieldRevision != revision || mSearchFieldVariant != variant) {
            mSearchField = toggle(KEY_SEARCH_FIELD, DockStyle.PROMPT_LINE);
            mSearchFieldRevision = revision;
            mSearchFieldVariant = variant;
        }
        return mSearchField;
    }

    /** The Search field turned on or off for this view's layout, its design left as it is. */
    public void setSearchField(boolean on) {
        if (mStore == null || on == searchField()) return;
        mStore.edit().putBoolean(layoutWideKey(activeVariant(), KEY_SEARCH_FIELD), on).apply();
        mRevision.incrementAndGet();
    }

    /**
     * Whether this view's layout carries the Drawer tile among the dock's apps, with whichever
     * design it holds (#97); read and cached as {@link #searchField} is. A layout that never
     * stored the toggle reads it off the design: on only for a Taskbar the migration has not
     * turned into the Tray yet.
     */
    public boolean drawerTile() {
        if (mStore == null) return false;
        LayoutVariant variant = activeVariant();
        int revision = mRevision.get();
        if (mDrawerTileRevision != revision || mDrawerTileVariant != variant) {
            mDrawerTile = toggle(KEY_DRAWER_TILE, DockStyle.TASKBAR);
            mDrawerTileRevision = revision;
            mDrawerTileVariant = variant;
        }
        return mDrawerTile;
    }

    /**
     * The Drawer tile turned on or off for this view's layout, its design and the tile's place
     * ({@link #drawerTilePosition}) left as they are.
     */
    public void setDrawerTile(boolean on) {
        if (mStore == null || on == drawerTile()) return;
        mStore.edit().putBoolean(layoutWideKey(activeVariant(), KEY_DRAWER_TILE), on).apply();
        mRevision.incrementAndGet();
    }

    private boolean toggle(@NonNull String key, @NonNull DockStyle bringer) {
        if (mStore == null) return false;
        String stored = layoutWideKey(activeVariant(), key);
        if (mStore.contains(stored)) return mStore.getBoolean(stored, false);
        return dockStyle() == bringer;
    }

    /**
     * Where the Drawer tile stands in this view's layout's dock row, whether or not the tile is
     * on, so it comes back where it was; the start until the user drags it.
     */
    @NonNull
    public DrawerTilePolicy.Position drawerTilePosition() {
        if (mStore == null) return DrawerTilePolicy.DEFAULT;
        return DrawerTilePolicy.parse(mStore.getString(
            layoutWideKey(activeVariant(), KEY_DRAWER_TILE_POSITION), null));
    }

    /**
     * The tile dropped at {@code position}. Not a layout change: the dock row re-lays its slots
     * itself.
     */
    public void setDrawerTilePosition(@NonNull DrawerTilePolicy.Position position) {
        if (mStore == null || position == drawerTilePosition()) return;
        mStore.edit().putString(layoutWideKey(activeVariant(), KEY_DRAWER_TILE_POSITION),
            DrawerTilePolicy.persisted(position)).apply();
    }

    /** {@code layout.home.<key>}: the Home layout's own, for both orientations. */
    @VisibleForTesting
    @NonNull
    static String homeKey(@NonNull String key) {
        return layoutWideKey(LayoutVariant.HOME, key);
    }

    /**
     * One layout's value for both orientations: {@code layout.<key>} for the normal layout,
     * {@code layout.minimal.<key>} and {@code layout.home.<key>} for the others.
     */
    @VisibleForTesting
    @NonNull
    static String layoutWideKey(@NonNull LayoutVariant variant, @NonNull String key) {
        return LAYOUT_PREFIX + variant.keyScope() + key;
    }

    /** What each layout holds once for both orientations: its design and the dock's toggles. */
    private static final String[] LAYOUT_WIDE_KEYS = {
        KEY_DOCK_DESIGN, KEY_SEARCH_FIELD, KEY_DRAWER_TILE, KEY_DRAWER_TILE_POSITION
    };

    /** The widget grid's keys, which live in the Home layout's scope but are Home's whatever it wears. */
    private static boolean isGridKey(@NonNull String key) {
        return KEY_WIDGET_COLUMNS.equals(key) || KEY_WIDGET_ROWS.equals(key);
    }

    // ---------------------------------------------------------------- slots

    /**
     * Where one element stands in an orientation, as the store holds it: the placement key it has
     * always had, and the sibling order key beside it. The toolbar switch is not folded in here —
     * {@link #resolve} does that — so a slot read back is exactly what was written.
     */
    @NonNull
    public Slot slot(@NonNull PlaceOrientation orientation, @NonNull Element element) {
        Slot stored = storedSlot(orientation, element);
        // On the normal and Home layouts whether an element shows is its kind's, one value for
        // both orientations (the page model, #171); the stored "hidden" is only legacy.
        if (mMigrating.get() || activeVariant() == LayoutVariant.MINIMAL) return stored;
        boolean shown = kindShows(kindOfVariant(activeVariant()), orientation, element);
        return stored.hidden == !shown ? stored : stored.withHidden(!shown);
    }

    /** {@link #slot} as the arrangement keys hold it, before the kind's visibility. */
    @NonNull
    private Slot storedSlot(@NonNull PlaceOrientation orientation, @NonNull Element element) {
        int order = slotOrder(orientation, element);
        boolean under = slotUnderKeyboard(orientation, element);
        switch (element) {
            case STATUS:
                // Spelled the way the rows have always been: an edge, or "hidden".
                return new Slot(VALUE_HIDDEN.equals(readString(orientation, KEY_STATUS_BAR)),
                    elementEdge(orientation, element), order);
            case AZ:
                return new Slot(!azRowShown(orientation), azBarEdge(orientation), order, under);
            case APPS:
            case EXTRA_KEYS:
            default:
                return new Slot(VALUE_HIDDEN.equals(readString(orientation,
                    element == Element.APPS ? KEY_APPS_ROW : KEY_EXTRA_KEYS)),
                    elementEdge(orientation, element), order, under);
        }
    }

    /** Moves one element: its edge, whether it is put away, and where it sits in the stack. */
    public void setSlot(@NonNull PlaceOrientation orientation, @NonNull Element element,
                        @NonNull Slot slot) {
        // A bar put away keeps the edge it came from, so the tray brings it back there. The
        // index's edge has a key of its own.
        if (slot.hidden && element != Element.AZ)
            writeString(orientation, restoreEdgeKeyName(element), slot.edge.storageValue());
        switch (element) {
            case STATUS:
                writeString(orientation, KEY_STATUS_BAR, rowValue(slot));
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
        syncKind(orientation, element, !slot.hidden);
        setSlotOrder(orientation, element, slot.order);
        // Written only once it is asked for or taken back, so a store that never had it keeps
        // reading as it always did.
        if (slot.underKeyboard || slotUnderKeyboard(orientation, element))
            setSlotUnderKeyboard(orientation, element, slot.underKeyboard);
    }

    /**
     * Whether an element stands under the keyboard on the bottom edge. False for an install that
     * never put one there, and always for the status bar, which may not.
     */
    public boolean slotUnderKeyboard(@NonNull PlaceOrientation orientation,
                                     @NonNull Element element) {
        return element.underKeyboardAllowed() && mStore != null
            && mStore.getBoolean(variantKey(orientation, underKeyboardKeyName(element)), false);
    }

    public void setSlotUnderKeyboard(@NonNull PlaceOrientation orientation,
                                     @NonNull Element element, boolean under) {
        if (!element.underKeyboardAllowed()) return;
        writeBoolean(variantKey(orientation, underKeyboardKeyName(element)), under);
    }

    /**
     * Where an element sits in its edge's stack, 0 outermost. Nothing is written until the user
     * re-orders something: an absent key is the stack the launcher has always drawn, read against
     * the edge the element is actually on.
     */
    public int slotOrder(@NonNull PlaceOrientation orientation, @NonNull Element element) {
        String key = variantKey(orientation, orderKeyName(element));
        int fallback = element.defaultOrder(elementEdge(orientation, element));
        if (mStore == null || !mStore.contains(key)) return fallback;
        return Math.max(0, mStore.getInt(key, fallback));
    }

    public void setSlotOrder(@NonNull PlaceOrientation orientation, @NonNull Element element,
                             int order) {
        writeInt(variantKey(orientation, orderKeyName(element)), Math.max(0, order));
    }

    /**
     * The edge an element's own placement key names, without reading its order. A bar that is put
     * away names none there — the key holds nothing but {@code hidden} — so it answers with the
     * edge remembered when it went away ({@link #restoreEdgeKeyName}), and failing that the
     * bar's own default edge: the top for the status bar, the bottom for the rows.
     */
    @NonNull
    private Edge elementEdge(@NonNull PlaceOrientation orientation, @NonNull Element element) {
        if (element == Element.AZ) return azBarEdge(orientation);
        String key = element == Element.STATUS ? KEY_STATUS_BAR
            : element == Element.APPS ? KEY_APPS_ROW : KEY_EXTRA_KEYS;
        Edge fallback = element == Element.STATUS ? Edge.TOP
            : element == Element.APPS && orientation == PlaceOrientation.LANDSCAPE
                ? Edge.LEFT : Edge.BOTTOM;
        String raw = readString(orientation, key);
        if (!VALUE_HIDDEN.equals(raw)) return Edge.parse(raw, fallback);
        Edge hiddenFallback = element == Element.STATUS ? Edge.TOP : Edge.BOTTOM;
        return Edge.parse(readString(orientation, restoreEdgeKeyName(element)), hiddenFallback);
    }

    /** A slot as the pinned apps and the extra keys have always spelled it. */
    @NonNull
    private static String rowValue(@NonNull Slot slot) {
        return slot.hidden ? VALUE_HIDDEN : slot.edge.storageValue();
    }

    /**
     * The edge the status bar stands on. A bar that is put away ({@link #slot}) names no edge —
     * its key holds nothing but {@code hidden}, like the rows' — and reads as the top, where it
     * has always started.
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

    /** Stands the status bar on an edge, which also brings a hidden one back. */
    public void setStatusBarEdge(@NonNull PlaceOrientation orientation, @NonNull Edge edge) {
        writeString(orientation, KEY_STATUS_BAR, edge.storageValue());
        syncKind(orientation, Element.STATUS, true);
    }

    /** The kind this view dresses shows or hides {@code element}, as its writers say. */
    private void syncKind(@NonNull PlaceOrientation orientation, @NonNull Element element,
                          boolean shown) {
        if (mMigrating.get() || activeVariant() == LayoutVariant.MINIMAL
            || !activeVariant().offers(element)) return;
        setKindShows(kindOfVariant(activeVariant()), orientation, element, shown);
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
     * has no top row, so a slot on the top edge reads as the bottom: anything that has to put a
     * bar back where it was — the editor's snapshot above all — reads {@link #slot} instead.
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
        syncKind(orientation, Element.APPS, placement != RowPlacement.HIDDEN);
    }

    /** The alphabets row is a switch, not a place: it rides on the apps row wherever that goes. */
    public boolean azRowShown(@NonNull PlaceOrientation orientation) {
        String key = variantKey(orientation, KEY_AZ_ROW);
        if (mStore != null && mStore.contains(key)) return mStore.getBoolean(key, true);
        return mPreferences.isAppLauncherAzRowEnabled();
    }

    public void setAzRowShown(@NonNull PlaceOrientation orientation, boolean shown) {
        writeBoolean(variantKey(orientation, KEY_AZ_ROW), shown);
        syncKind(orientation, Element.AZ, shown);
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
        syncKind(orientation, Element.EXTRA_KEYS, placement != RowPlacement.HIDDEN);
    }

    /**
     * Whether an open keyboard floats over the Linux display or shrinks it. Only the display reads
     * this ({@link KeyboardOverlayPolicy}): it has a fixed screen of its own and nothing to reflow,
     * so it floats in both orientations until the user asks otherwise. A stored Resize is kept,
     * whichever orientation it was set for. The terminal's text always takes the room, whatever
     * is stored here.
     */
    @NonNull
    public KeyboardMode keyboardMode(@NonNull PlaceOrientation orientation) {
        return KeyboardMode.parse(readString(orientation, KEY_KEYBOARD_MODE), KeyboardMode.OVERLAY);
    }

    public void setKeyboardMode(@NonNull PlaceOrientation orientation, @NonNull KeyboardMode mode) {
        writeString(orientation, KEY_KEYBOARD_MODE, mode.storageValue());
    }

    /**
     * Whether this orientation's keyboard mode has a key of its own, rather than reading the
     * Overlay every layout falls back to. The Layout editor's snapshot asks, so its revert puts an
     * absent mode back as absent instead of pinning the default it answered with: a pinned
     * default outlives a change of default, which is how portrait kept Resize after Overlay
     * became the default.
     */
    public boolean hasStoredKeyboardMode(@NonNull PlaceOrientation orientation) {
        return mStore != null && mStore.contains(variantKey(orientation, KEY_KEYBOARD_MODE));
    }

    /** Drops this orientation's keyboard mode, so it reads the default again. */
    public void clearKeyboardMode(@NonNull PlaceOrientation orientation) {
        if (mStore == null) return;
        mStore.edit().remove(variantKey(orientation, KEY_KEYBOARD_MODE)).apply();
        mRevision.incrementAndGet();
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
        return clamp(readGridInt(orientation, KEY_WIDGET_COLUMNS,
                mPreferences.getAppLauncherWidgetGridColumns()),
            TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
            TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS);
    }

    public void setWidgetColumns(@NonNull PlaceOrientation orientation, int columns) {
        writeInt(gridKey(orientation, KEY_WIDGET_COLUMNS),
            clamp(columns, TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
                TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS));
    }

    public int widgetRows(@NonNull PlaceOrientation orientation) {
        return clamp(readGridInt(orientation, KEY_WIDGET_ROWS,
                mPreferences.getAppLauncherWidgetGridRows()),
            TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS,
            TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS);
    }

    public void setWidgetRows(@NonNull PlaceOrientation orientation, int rows) {
        writeInt(gridKey(orientation, KEY_WIDGET_ROWS),
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
        String key = variantKey(orientation, KEY_DOCK_HEIGHT);
        float value = mStore != null && mStore.contains(key)
            ? mStore.getFloat(key, TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT)
            : mPreferences.getSharedAppLauncherBarHeightScale();
        return TermuxAppSharedPreferences.clampAppLauncherBarHeightScale(value);
    }

    public void setDockHeightScale(@NonNull PlaceOrientation orientation, float scale) {
        writeFloat(variantKey(orientation, KEY_DOCK_HEIGHT),
            TermuxAppSharedPreferences.clampAppLauncherBarHeightScale(scale));
    }

    /** How tall the in-app keyboard stands, as a multiple of its unscaled height. */
    public float keyboardHeightScale(@NonNull PlaceOrientation orientation) {
        String key = variantKey(orientation, KEY_KEYBOARD_HEIGHT);
        float value = mStore != null && mStore.contains(key)
            ? mStore.getFloat(key, mPreferences.getDefaultInAppKeyboardHeightScale())
            : mPreferences.getSharedInAppKeyboardHeightScale(
                orientation == PlaceOrientation.LANDSCAPE);
        return TermuxAppSharedPreferences.clampInAppKeyboardHeightScale(value);
    }

    public void setKeyboardHeightScale(@NonNull PlaceOrientation orientation, float scale) {
        writeFloat(variantKey(orientation, KEY_KEYBOARD_HEIGHT),
            TermuxAppSharedPreferences.clampInAppKeyboardHeightScale(scale));
    }

    /** Extra air in dp under the last key row, inside the keyboard's own surface. */
    public int keyboardChinDp(@NonNull PlaceOrientation orientation) {
        String key = variantKey(orientation, KEY_KEYBOARD_CHIN);
        int value = mStore != null && mStore.contains(key)
            ? mStore.getInt(key, TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING)
            : mPreferences.getSharedInAppKeyboardBottomPadding();
        return TermuxAppSharedPreferences.clampInAppKeyboardBottomPadding(value);
    }

    public void setKeyboardChinDp(@NonNull PlaceOrientation orientation, int dp) {
        writeInt(variantKey(orientation, KEY_KEYBOARD_CHIN),
            TermuxAppSharedPreferences.clampInAppKeyboardBottomPadding(dp));
    }

    /**
     * How tall the Wall strip's first row stands over the apps row, in dp: the layout's own, the
     * shipped row while it has none. Read only where a dock wears the Wall strip.
     */
    public int wallStripHeightDp(@NonNull PlaceOrientation orientation) {
        String key = variantKey(orientation, KEY_WALL_STRIP_HEIGHT);
        int value = mStore != null && mStore.contains(key)
            ? mStore.getInt(key, WallStripRowPolicy.DEFAULT_HEIGHT_DP)
            : WallStripRowPolicy.DEFAULT_HEIGHT_DP;
        return WallStripRowPolicy.clampHeightDp(value);
    }

    public void setWallStripHeightDp(@NonNull PlaceOrientation orientation, int dp) {
        writeInt(variantKey(orientation, KEY_WALL_STRIP_HEIGHT),
            WallStripRowPolicy.clampHeightDp(dp));
    }

    /**
     * Puts one orientation of the variant this store is on back to whatever the global values and
     * defaults say. For the minimal layout that is its seed again - the normal layout with minimal
     * mode taken off it - which the next read writes afresh.
     */
    public void clear(@NonNull PlaceOrientation orientation) {
        if (mStore == null) return;
        LayoutVariant variant = activeVariant();
        SharedPreferences.Editor editor = mStore.edit();
        for (String key : ARRANGEMENT_KEYS) editor.remove(layoutKey(variant, orientation, key));
        editor.apply();
        mSeeded[variant.ordinal()][orientation.ordinal()] = false;
        mRevision.incrementAndGet();
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
     * Whether the launcher is in minimal mode (CONTEXT.md). One flag for every place, remembered
     * until it is turned off: paging to another place never leaves the mode, since the mode is
     * not any place's. It does not overwrite the keyboard memory: a place simply comes back with
     * the keyboard down while the mode is on, and turning it off brings back what the place
     * remembered.
     */
    public boolean isMinimal() {
        int revision = mRevision.get();
        if (mMinimalRevision != revision) {
            mMinimal = mStore != null && mStore.getBoolean(minimalKey(), false);
            mMinimalRevision = revision;
        }
        return mMinimal;
    }

    public void setMinimal(boolean minimal) {
        writeBoolean(minimalKey(), minimal);
    }

    /** {@code place.minimal}: the launcher's, not a place's. */
    @VisibleForTesting
    @NonNull
    static String minimalKey() {
        return PREFIX + KEY_MINIMAL;
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

    /**
     * Where the dictation pill and its panel were dragged to by their handle, as the same kind of
     * fractions of the room they move in as {@link #floatingKeyboardX}; {@link #FLOAT_POSITION_UNSET}
     * until the first drag, and again after a double tap on the handle puts the pill back in its
     * top right corner. Per orientation and shared by every place, for the same reasons.
     */
    public float voicePillX(@NonNull PlaceOrientation orientation) {
        return readFraction(layoutKey(orientation, KEY_VOICE_PILL_X));
    }

    public float voicePillY(@NonNull PlaceOrientation orientation) {
        return readFraction(layoutKey(orientation, KEY_VOICE_PILL_Y));
    }

    /** Remembers a dragged place; either fraction outside 0..1 forgets it instead. */
    public void setVoicePillPosition(@NonNull PlaceOrientation orientation, float x, float y) {
        writeFraction(layoutKey(orientation, KEY_VOICE_PILL_X), x);
        writeFraction(layoutKey(orientation, KEY_VOICE_PILL_Y), y);
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
        mRevision.incrementAndGet();
    }

    // ---------------------------------------------------------------- keys

    @VisibleForTesting
    @NonNull
    static String placeKey(@NonNull PaneWallPage place) {
        // The widget grid is the home screen everywhere a user can see it; only the enum says
        // WIDGETS.
        return place == PaneWallPage.WIDGETS ? "home" : place.name().toLowerCase(Locale.ROOT);
    }

    /** Where the normal arrangement keeps one value for one orientation. */
    @VisibleForTesting
    @NonNull
    static String layoutKey(@NonNull PlaceOrientation orientation, @NonNull String key) {
        return layoutKey(LayoutVariant.NORMAL, orientation, key);
    }

    /**
     * Where one variant's arrangement keeps one value for one orientation:
     * {@code layout.<o>.<key>} for the normal layout, exactly as it has always been written, and
     * {@code layout.minimal.<o>.<key>} for the minimal one.
     */
    @VisibleForTesting
    @NonNull
    static String layoutKey(@NonNull LayoutVariant variant, @NonNull PlaceOrientation orientation,
                            @NonNull String key) {
        return LAYOUT_PREFIX + variant.keyScope() + orientation.storageValue() + "." + key;
    }

    /**
     * Where this store keeps one arrangement value: the key of the variant it is on, or for a
     * linked key on the Home layout the normal layout's ({@link #LINKED_KEYS}), so every reader
     * and writer of a size, the Layout editor's handles and the snapshot included, lands on the
     * one value both halves share. The minimal layout is seeded before its first key is read, so
     * no caller sees it empty.
     */
    @NonNull
    private String variantKey(@NonNull PlaceOrientation orientation, @NonNull String key) {
        LayoutVariant variant = scopeOf(key);
        seedIfNeeded(variant, orientation);
        return layoutKey(variant, orientation, key);
    }

    /** The variant whose scope holds {@code key} for this view: the normal one for a linked key on Home's. */
    @NonNull
    private LayoutVariant scopeOf(@NonNull String key) {
        LayoutVariant variant = activeVariant();
        return variant == LayoutVariant.HOME && isLinkedKey(key) ? LayoutVariant.NORMAL : variant;
    }

    /**
     * Where the widget grid's columns and rows are kept: the Home layout's keys, whichever variant
     * asks. The grid is Home's content and nothing else draws one, so a Workspace variant changing
     * underneath (minimal mode) never re-cuts it.
     */
    @NonNull
    private String gridKey(@NonNull PlaceOrientation orientation, @NonNull String key) {
        seedIfNeeded(LayoutVariant.HOME, orientation);
        return layoutKey(LayoutVariant.HOME, orientation, key);
    }

    private int readGridInt(@NonNull PlaceOrientation orientation, @NonNull String key,
                            int fallback) {
        String home = gridKey(orientation, key);
        return mStore != null && mStore.contains(home) ? mStore.getInt(home, fallback) : fallback;
    }

    /**
     * Gives a seeded layout of an orientation its starting point, the first time it is used and
     * has stored nothing: the normal layout as it stands, with the variant's own changes taken off
     * it. The minimal layout is the normal one with {@link MinimalMode#apply}, which is what
     * minimal mode looked like before it had a layout of its own; the Home layout is the normal
     * one with the element it does not offer (the extra keys) put away, its
     * keyboard element put away by having no key yet. Only what the normal layout has actually
     * stored is copied, so a default that still moves - the dock's height with its style - keeps
     * moving. Written once; from then on the user's edits are the layout, and {@link #clear} puts
     * it back to this.
     */
    private void seedIfNeeded(@NonNull LayoutVariant variant, @NonNull PlaceOrientation orientation) {
        if (mStore == null || variant == LayoutVariant.NORMAL
            || mSeeded[variant.ordinal()][orientation.ordinal()]) return;
        mSeeded[variant.ordinal()][orientation.ordinal()] = true;
        for (String key : ARRANGEMENT_KEYS) {
            if (mStore.contains(layoutKey(variant, orientation, key))) return;
        }
        Map<String, ?> all = mStore.getAll();
        SharedPreferences.Editor editor = mStore.edit();
        for (String key : ARRANGEMENT_KEYS) {
            // The Home layout reads its linked keys from the normal layout; a copy would be dead.
            if (variant == LayoutVariant.HOME && isLinkedKey(key)) continue;
            Object value = all.get(layoutKey(LayoutVariant.NORMAL, orientation, key));
            if (value != null) put(editor, layoutKey(variant, orientation, key), value);
        }
        editor.apply();
        PlaceLayoutStore seededStore = forVariant(variant);
        // The Home layout's resolve already puts away what it does not offer.
        PlaceLayout seeded = variant == LayoutVariant.MINIMAL
            ? MinimalMode.apply(seededStore.resolve(orientation))
            : seededStore.resolve(orientation);
        for (Element element : Element.values()) {
            Slot stored = seededStore.slot(orientation, element);
            boolean away = seeded.slot(element).hidden || (variant == LayoutVariant.MINIMAL
                && !kindShows(PageKind.TERMINAL, orientation, element));
            if (away && !stored.hidden)
                seededStore.setSlot(orientation, element, stored.withHidden(true));
        }
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
        boolean freshInstall = fromVersion == 0 && !holdsAnyPlaceKeys();
        mMigrating.set(true);
        try {
            migrateFrom(fromVersion, freshInstall);
        } finally {
            mMigrating.set(false);
        }
    }

    /** Whether the store holds any layout or place key: false for an install that never ran it. */
    private boolean holdsAnyPlaceKeys() {
        for (String key : mStore.getAll().keySet()) {
            if (key.startsWith(LAYOUT_PREFIX) || key.startsWith(PREFIX)) return true;
        }
        // The keys the first migration steps fold: an install that predates the store has them.
        return mStore.contains(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_RAIL_SIDE)
            || mStore.contains(TERMUX_APP.KEY_X11_EXTRA_KEYS_SIDE)
            || mStore.contains(TERMUX_APP.KEY_X11_KEYBOARD_SHOWN)
            || mStore.contains(TERMUX_APP.KEY_TOP_PANE_CLOCK_COLLAPSED)
            || !mPreferences.isAppLauncherAppsRowEnabled()
            || !mPreferences.isAppLauncherExtraKeysRowEnabled()
            || !mPreferences.isAppLauncherAzRowEnabled();
    }

    private void migrateFrom(int fromVersion, boolean freshInstall) {
        if (fromVersion < 5) {
            SharedPreferences.Editor editor = mStore.edit();
            migrateBeforeSharedLayout(editor, fromVersion);
            editor.apply();
        }
        SharedPreferences.Editor editor = mStore.edit();
        if (fromVersion < 6) migrateToSharedLayout(editor);
        if (fromVersion < 7) migrateToOneMinimalMode(editor);
        if (fromVersion < 8) migrateAwayFromAzTab(editor);
        editor.apply();
        // Version 9 compares the layouts through the store's own reads, which see only what the
        // steps before it have applied.
        if (fromVersion < 9) migrateToOneLayout();
        if (fromVersion < 10) migrateHomeLayoutToTheBar();
        if (fromVersion < 11) migratePromptLineToTheSearchField();
        if (fromVersion < 12) migrateTaskbarToTheDrawerTile();
        if (fromVersion < 13) migrateToLinkedKeys();
        if (fromVersion < 14) migrateToHomeLayoutAlways();
        // A new install keeps the page kinds' defaults; an upgrade writes what each wore.
        if (fromVersion < 15 && !freshInstall) migrateToKindVisibility();
        // A new install starts on the new default design; an install that ran this store before
        // (fromVersion above 0) or was updated from an earlier build keeps the absent key's tray.
        if (startsOnNewInstallDefault(fromVersion, installTimes())) seedNewInstallDockDesign();
        mStore.edit().putInt(KEY_MIGRATED, MIGRATION_VERSION).apply();
        mRevision.incrementAndGet();
    }

    /**
     * Whether this first run of the store is a new install's, which starts on
     * {@link DockStyle#NEW_INSTALL_DEFAULT} rather than the tray an absent design reads as. Only
     * an install the store has never migrated ({@code migratedFrom} 0: no build since 2026-09-06
     * has run on it) whose package was installed and never updated (the two install times equal,
     * as Android sets them on a fresh install). An install updated from an older build has
     * {@code migratedFrom} 0 too when that build predates the store, but its last update is later
     * than its first install, so it keeps the tray. {@code installTimes} is null where the
     * package manager cannot answer, which keeps the tray.
     */
    @VisibleForTesting
    static boolean startsOnNewInstallDefault(int migratedFrom, @Nullable long[] installTimes) {
        if (migratedFrom != 0 || installTimes == null || installTimes.length < 2) return false;
        long first = installTimes[0];
        long last = installTimes[1];
        return first > 0L && first == last;
    }

    /** This package's first install and last update times, or null where they cannot be read. */
    @Nullable
    private long[] installTimes() {
        try {
            Context context = mPreferences.getContext();
            PackageManager manager = context == null ? null : context.getPackageManager();
            if (manager == null) return null;
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            return info == null ? null : new long[] {info.firstInstallTime, info.lastUpdateTime};
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    /**
     * A new install's layouts hold {@link DockStyle#NEW_INSTALL_DEFAULT}, each written where
     * nothing is stored yet, so the absent key goes on reading as the tray for every install that
     * predates the new default.
     */
    private void seedNewInstallDockDesign() {
        SharedPreferences.Editor editor = mStore.edit();
        boolean wrote = false;
        for (LayoutVariant variant : LayoutVariant.values()) {
            String key = layoutWideKey(variant, KEY_DOCK_DESIGN);
            if (mStore.contains(key)) continue;
            editor.putString(key, DockStyle.NEW_INSTALL_DEFAULT.storageValue());
            wrote = true;
        }
        if (wrote) editor.apply();
    }

    /**
     * Once per install, after the migration: a portrait keyboard mode stored as Resize goes, so the
     * display reads the Overlay default. Before Overlay was the portrait default, the Layout
     * editor's revert wrote back the Resize it was falling back to, so a stored portrait Resize
     * is far more often that visit than a choice; the touchpad's mode button switches it back.
     * Every layout's portrait key, since the minimal and Home layouts seed from the normal one and
     * the display wears the normal or the minimal one. A stored Overlay and landscape are left
     * alone: landscape always defaulted to Overlay, so what it holds was chosen. The flag is set
     * whether or not anything was removed, so a fresh install never runs it and a Resize picked
     * after it is kept.
     */
    private void resetPortraitKeyboardResizeOnce() {
        if (mStore == null || mStore.getBoolean(KEY_KEYBOARD_OVERLAY_RESET_DONE, false)) return;
        String resize = KeyboardMode.RESIZE.storageValue();
        SharedPreferences.Editor editor = mStore.edit();
        for (LayoutVariant variant : LayoutVariant.values()) {
            String key = layoutKey(variant, PlaceOrientation.PORTRAIT, KEY_KEYBOARD_MODE);
            if (resize.equals(mStore.getString(key, null))) editor.remove(key);
        }
        editor.putBoolean(KEY_KEYBOARD_OVERLAY_RESET_DONE, true).apply();
        mRevision.incrementAndGet();
    }

    /**
     * Version 9: one layout dresses both halves (ADR 0009). Home's saved arrangement (in either
     * orientation, where the dock's rows stand, whether each is put away, their order and which
     * stand under the keyboard: {@link #arrangementsDiffer}; and, only while Home's keyboard is
     * always on, the keyboard's form: {@link #keyboardFormsDiffer}) is compared with the
     * Workspace's normal one. Where anything differs the Home layout is turned on and Home keeps
     * its arrangement, so its rows stand where they stood. Where nothing does, Home wears the
     * shared layout and its own arrangement keys go; the widget grid and always on are Home's
     * either way and stay. The linked sizes (the dock's and the Strip row's heights, the
     * keyboard's height and chin) and Style, Corners and Margin are no difference (#84): Home
     * reads them from the Workspace's whatever it stored, and version 13 drops its copies. The
     * Dock design is no difference either: Home's pick becomes the design of the shared layout,
     * normal and minimal, and of the Home layout, with the Taskbar's tile position beside it,
     * under every layout's own keys; Home's old keys for them go.
     */
    private void migrateToOneLayout() {
        String legacyStyle = homeKey(LEGACY_KEY_HOME_DOCK_STYLE);
        String legacyTile = homeKey(LEGACY_KEY_HOME_TASKBAR_HOME_TILE);
        String style = mStore.getString(legacyStyle, null);
        String tile = mStore.getString(legacyTile, null);
        // The status bar is no difference: Home wore none before version 10, so what its layout
        // held for the bar was only ever the seed's "put away" (#90). The sizes are no difference
        // because the Home layout reads them linked, from the normal layout.
        boolean differs = homeHoldsArrangement()
            && (arrangementsDiffer(LayoutVariant.HOME, LayoutVariant.NORMAL, Element.STATUS)
                || (isHomeKeyboardAlwaysOn()
                    && keyboardFormsDiffer(LayoutVariant.HOME, LayoutVariant.NORMAL)));

        SharedPreferences.Editor editor = mStore.edit();
        for (LayoutVariant variant : LayoutVariant.values()) {
            if (style != null) putDockDesign(editor, variant, DockStyle.parse(style));
            if (tile != null)
                editor.putString(layoutWideKey(variant, KEY_DRAWER_TILE_POSITION), tile);
        }
        editor.remove(legacyStyle).remove(legacyTile);
        if (differs) {
            editor.putBoolean(homeLayoutKey(), true);
        } else {
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                for (String key : ARRANGEMENT_KEYS) {
                    if (!isGridKey(key))
                        editor.remove(layoutKey(LayoutVariant.HOME, orientation, key));
                }
            }
        }
        editor.apply();
    }

    /**
     * Version 13: the linked keys (#84). The Home layout holds no sizes and no shape of its own
     * any more: its stored dock and Strip row heights and keyboard height and chin go, in both
     * orientations, and so do Home's own Style, Corners and Margin, so Home wears the Workspace's
     * values for all of them. The Home layout switch is left as it is, since it may be the user's
     * choice; whatever else Home's own layout holds (its rows, their order and edge, its design
     * and toggles, its keyboard form) stays. A step of its own rather than a change to version 9,
     * so a store this branch has already taken to version 12 drops its copies too.
     */
    private void migrateToLinkedKeys() {
        SharedPreferences.Editor editor = mStore.edit();
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            for (String key : LINKED_KEYS)
                editor.remove(layoutKey(LayoutVariant.HOME, orientation, key));
        }
        editor.remove(TERMUX_APP.KEY_HOME_LAYOUT_STYLE)
            .remove(TERMUX_APP.KEY_HOME_SURFACE_CORNER_RADIUS)
            .remove(TERMUX_APP.KEY_HOME_SURFACE_SIDE_GAP);
        editor.apply();
    }

    /**
     * Version 14: Home and the Workspace each keep their own layout (editor-redesign §1, §9). A
     * Home that wore the shared layout gets a copy of the Workspace's normal one, the layout it
     * was wearing, so nothing on screen moves: where every bar stands, which are put away (the
     * hidden stack) and their order, the Dock design and its toggles ({@link #setHomeLayout}).
     * The keyboard's height and chin (Bottom padding) and the other linked sizes need no copy:
     * the Home layout reads them from the normal one ({@link #LINKED_KEYS}). A Home layout
     * already on is left as it is.
     */
    private void migrateToHomeLayoutAlways() {
        // The widget grid is Home's alone and read only from Home's keys. A grid still held in
        // the shared (normal) keys, where the older steps put it, moves to Home's, unless Home
        // already holds its own.
        Map<String, ?> stored = mStore.getAll();
        SharedPreferences.Editor gridEditor = mStore.edit();
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            for (String key : ARRANGEMENT_KEYS) {
                if (!isGridKey(key)) continue;
                String home = layoutKey(LayoutVariant.HOME, orientation, key);
                Object shared = stored.get(layoutKey(LayoutVariant.NORMAL, orientation, key));
                if (shared != null && !stored.containsKey(home)) put(gridEditor, home, shared);
            }
        }
        gridEditor.apply();
        if (mStore.getBoolean(homeLayoutKey(), false)) return;
        copyIntoHomeLayout(LayoutVariant.NORMAL);
        mStore.edit().putBoolean(homeLayoutKey(), true).apply();
    }

    /**
     * Version 15: which elements show becomes a setting of the kind of page (the page model,
     * #171). The Home layout's visibility becomes the Home kind's (the shared layout's while Home
     * holds no layout of its own); the shared layout's becomes the Terminal kind's, and the
     * Display kind's, which a display window shares with it for now, so the dock stays on the
     * Terminal page and nothing moves. Each orientation is copied on its own. The extra keys were never worn on Home, so Home keeps them off. The stored
     * "hidden" values stay where they are, as the legacy arrangement.
     */
    private void migrateToKindVisibility() {
        LayoutVariant homeSource = mStore.getBoolean(homeLayoutKey(), false)
            ? LayoutVariant.HOME : LayoutVariant.NORMAL;
        SharedPreferences.Editor editor = mStore.edit();
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            for (Element element : Element.values()) {
                boolean home = element != Element.EXTRA_KEYS
                    && !forVariant(homeSource).storedSlot(orientation, element).hidden;
                boolean terminal =
                    !forVariant(LayoutVariant.NORMAL).storedSlot(orientation, element).hidden;
                editor.putBoolean(kindKey(PageKind.HOME, orientation, element), home);
                editor.putBoolean(kindKey(PageKind.TERMINAL, orientation, element), terminal);
                editor.putBoolean(kindKey(PageKind.DISPLAY, orientation, element), terminal);
            }
        }
        editor.apply();
    }

    /**
     * Version 10: Home wears the status bar (#90). A Home layout held the bar only as its seed
     * put it away, never as the user placed it, so it takes the Workspace's normal bar instead:
     * where the bar stands, whether it is put away, its place in the stack and the edge it comes
     * back to, in either orientation. A Home layout that holds nothing yet is seeded on first use
     * as before, now with the bar.
     */
    private void migrateHomeLayoutToTheBar() {
        if (!homeHoldsArrangement()) return;
        Map<String, ?> all = mStore.getAll();
        SharedPreferences.Editor editor = mStore.edit();
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            for (String key : new String[] {KEY_STATUS_BAR, orderKeyName(Element.STATUS),
                    restoreEdgeKeyName(Element.STATUS)}) {
                copy(editor, all, layoutKey(LayoutVariant.NORMAL, orientation, key),
                    layoutKey(LayoutVariant.HOME, orientation, key));
            }
        }
        editor.apply();
    }

    /**
     * Version 11: the Prompt line design is the Search field now (#96). Every layout that holds
     * it, normal, minimal or Home's, holds the Tray instead with the field on, so its search
     * field stays where it was. Every other layout keeps its design and its stored toggle.
     */
    private void migratePromptLineToTheSearchField() {
        SharedPreferences.Editor editor = mStore.edit();
        for (LayoutVariant variant : LayoutVariant.values()) {
            String design = layoutWideKey(variant, KEY_DOCK_DESIGN);
            if (!DockStyle.PROMPT_LINE.storageValue().equals(mStore.getString(design, null)))
                continue;
            editor.putString(design, DockStyle.GLASS_TRAY.storageValue())
                .putBoolean(layoutWideKey(variant, KEY_SEARCH_FIELD), true);
        }
        editor.apply();
    }

    /**
     * Version 12: the Taskbar design is the Drawer tile now (#97). Every layout that holds it,
     * normal, minimal or Home's, holds the Tray instead with the tile on, and the tile stays where
     * the user had dragged it ({@link #KEY_DRAWER_TILE_POSITION} is untouched). Every other layout
     * keeps its design and its stored toggle.
     */
    private void migrateTaskbarToTheDrawerTile() {
        SharedPreferences.Editor editor = mStore.edit();
        for (LayoutVariant variant : LayoutVariant.values()) {
            String design = layoutWideKey(variant, KEY_DOCK_DESIGN);
            if (!DockStyle.TASKBAR.storageValue().equals(mStore.getString(design, null)))
                continue;
            editor.putString(design, DockStyle.GLASS_TRAY.storageValue())
                .putBoolean(layoutWideKey(variant, KEY_DRAWER_TILE), true);
        }
        editor.apply();
    }

    /**
     * Whether the Home layout holds any arrangement of its own, beyond the widget grid. One that
     * never did has nothing to differ by, and is not seeded only to be compared.
     */
    private boolean homeHoldsArrangement() {
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            for (String key : ARRANGEMENT_KEYS) {
                if (!isGridKey(key) && mStore.contains(layoutKey(LayoutVariant.HOME, orientation, key)))
                    return true;
            }
        }
        return false;
    }

    /**
     * Version 7: one minimal mode for the whole launcher. The terminal's own flag decides, since
     * the terminal is the place the mode was built for and the one a launch comes back to; a
     * terminal that never stored one defers to the display's. Every place's own flag then goes.
     */
    private void migrateToOneMinimalMode(@NonNull SharedPreferences.Editor editor) {
        String terminal = memoryKey(PaneWallPage.TERMINAL, KEY_MINIMAL);
        String display = memoryKey(PaneWallPage.DISPLAY, KEY_MINIMAL);
        Boolean minimal = null;
        if (mStore.contains(terminal)) minimal = mStore.getBoolean(terminal, false);
        else if (mStore.contains(display)) minimal = mStore.getBoolean(display, false);
        if (minimal != null) editor.putBoolean(minimalKey(), minimal);
        for (PaneWallPage place : PaneWallPage.values())
            editor.remove(memoryKey(place, KEY_MINIMAL));
    }

    /**
     * Version 8: the alphabets index's minimised pull tab is gone. Its flag is deleted from both
     * layouts in both orientations and nothing else is written: {@code az_row} is still true for
     * an index that was minimised, so it reads as shown, standing as a band on the edge the tab
     * was on — the form it folded from.
     */
    private void migrateAwayFromAzTab(@NonNull SharedPreferences.Editor editor) {
        for (LayoutVariant variant : LayoutVariant.values()) {
            for (PlaceOrientation orientation : PlaceOrientation.values())
                editor.remove(layoutKey(variant, orientation, LEGACY_KEY_AZ_MINIMISED));
        }
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

            // The display's own hide switch is not carried over: the shared layout starts with
            // the bar shown, and the Layout editor's tray is where it is put away now.
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
        return mStore == null ? null : mStore.getString(variantKey(orientation, key), null);
    }

    private int readInt(@NonNull PlaceOrientation orientation, @NonNull String key, int fallback) {
        String shared = variantKey(orientation, key);
        return mStore != null && mStore.contains(shared) ? mStore.getInt(shared, fallback) : fallback;
    }

    private void writeString(@NonNull PlaceOrientation orientation, @NonNull String key,
                             @NonNull String value) {
        if (mStore == null) return;
        mStore.edit().putString(variantKey(orientation, key), value).apply();
        mRevision.incrementAndGet();
    }

    private void writeBoolean(@NonNull String key, boolean value) {
        if (mStore == null) return;
        mStore.edit().putBoolean(key, value).apply();
        mRevision.incrementAndGet();
    }

    private void writeInt(@NonNull String key, int value) {
        if (mStore == null) return;
        mStore.edit().putInt(key, value).apply();
        mRevision.incrementAndGet();
    }

    private void writeFloat(@NonNull String key, float value) {
        if (mStore == null) return;
        mStore.edit().putFloat(key, value).apply();
        mRevision.incrementAndGet();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---------------------------------------------------------------- Dock card

    /**
     * The Dock card's Bars list, top to bottom, beside the slots it stacks:
     * {@code layout.<orientation>.bar_order}, the bars' storage keys comma separated. Only bars
     * on edges of their own need it; where bars share an edge their slots' order is the truth.
     */
    private static final String KEY_BAR_ORDER = "bar_order";

    /** The Bars list as stored for {@code orientation}, or null where it never was. */
    @Nullable
    public String barOrder(@NonNull PlaceOrientation orientation) {
        return readString(orientation, KEY_BAR_ORDER);
    }

    /** Stores the Bars list for {@code orientation}; null drops it (the default list). */
    public void setBarOrder(@NonNull PlaceOrientation orientation, @Nullable String order) {
        if (java.util.Objects.equals(order, barOrder(orientation))) return;
        if (order != null) {
            writeString(orientation, KEY_BAR_ORDER, order);
            return;
        }
        if (mStore == null) return;
        mStore.edit().remove(variantKey(orientation, KEY_BAR_ORDER)).apply();
        mRevision.incrementAndGet();
    }
}
