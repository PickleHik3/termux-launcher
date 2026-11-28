package com.termux.app.editmode;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.launcher.data.IconPackChoices;
import com.termux.app.look.TerlLookStore;
import com.termux.app.look.TerlLooks;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;

import java.util.ArrayList;
import java.util.List;

/**
 * Edit mode on the live desktop (ADR 0040): a faint outline on every surface that has a card, a
 * tap raising that surface's card low on the screen, and the strip at the very bottom with the
 * change count, Undo, Revert and Done. Every change applies to the look store at once and stays;
 * the {@link EditSession} keeps the visit's history. Back closes the card, then leaves; nothing
 * asks.
 *
 * <p>Its views are made on {@link #open} and let go on {@link #leave}: nothing is held at rest.
 * They join the editor layer through the host, and the overlay itself takes no touch, so the desktop
 * under it still moves. The keyboard has a card, and the strip's All glass opens the Global card
 * (the prototype's All glass): the Look filmstrip, Custom's Opacity and Blur, Tint colour,
 * Corners, Depth and Shine, the icon pack, text size and a link to Settings.
 */
public final class EditModeController {

    /** The cards edit mode can raise. */
    public enum Card { KEYBOARD, GLOBAL, WALLPAPER, HOME, BAR, WINDOWS }

    /** What edit mode needs from the screen it edits. */
    public interface Host {
        @NonNull
        Context context();

        /** The editor layer's host ({@code WindowLayers}), or null when the window has none. */
        @Nullable
        ViewGroup editorLayer();

        @NonNull
        TerlLookStore looks();

        /** The in-app keyboard's view, or null when there is none. */
        @Nullable
        View keyboardView();

        boolean isKeyboardShown();

        /** Brings the in-app keyboard up for its card; false when it cannot come up. */
        boolean showKeyboard();

        /** Puts the keyboard a card brought up back down. */
        void hideKeyboard();

        /** The launcher's text size and icon pack, or null when they cannot be changed here. */
        @Nullable
        EditSession.Launcher launcher();

        /**
         * The icon packs on offer, the system's icons first (value ""). Slow (a package query):
         * edit mode calls it off the main thread, once per Global card.
         */
        @NonNull
        List<IconPackChoices.Entry> iconPacks();

        /** Parses {@code pack} ahead of its use. Slow: edit mode calls it off the main thread. */
        void warmIconPack(@NonNull String pack);

        /** The wallpaper's dim and drift, the theme and the colours, or null when they cannot change here. */
        @Nullable
        EditSession.Wallpaper wallpaper();

        /** Home's grid, page dots and A–Z edge, or null when they cannot change here. */
        @Nullable
        EditSession.Home home();

        /**
         * Calls {@code tap} when a finger taps empty Workspaces' air (not a drag, not a window); null
         * stops. A tap on empty Home is {@link EditSession.Home#onEmptyTap}.
         * The desktop's own gestures are never held up by it.
         */
        void onWallpaperTap(@Nullable Runnable tap);

        /**
         * What the launcher measures behind the keyboard now (the ink and the faces it stands on),
         * or null when nothing is measured. Cheap: arithmetic on the sample it already holds.
         */
        @Nullable
        LegibilityMeter.Reading legibility();

        /** Edit mode closed: anything its live writes left owed (an icon pack's restyle) runs now. */
        void onLeft();
    }

    /**
     * What edit mode needs besides {@link Host} for the control bar's card (a sub-interface: Host
     * stands at its method cap). A host that is not a BarHost has no Control bar card.
     */
    public interface BarHost extends Host {
        /** The control bar's margin, reading slot, rail side and tools, or null with no bar. */
        @Nullable
        EditSession.Bar bar();

        /** The bar's view, or null when there is none. */
        @Nullable
        View barView();

        /**
         * Brings the bar up while its card is open when it is out of sight (full screen, #426);
         * false when it is already showing.
         */
        boolean raiseBar();

        /** Puts a bar {@link #raiseBar} brought up back out of sight. */
        void lowerBar();

        /**
         * Calls {@code tap} when a finger taps the bar (the bar answers nothing else while edit
         * mode is open); null stops.
         */
        void onBarTap(@Nullable Runnable tap);

        /**
         * Lifts the bar {@code px} above where it is laid out (a translation), so it stands above
         * edit mode's strip; 0 puts it back.
         */
        default void liftBar(int px) {
        }

        /**
         * Home's A–Z letters' view while it shows (at the bottom, a row inside the bar; on the
         * left or right, a column of its own), or null: a card keeps clear of a column.
         */
        @Nullable
        default View azLetters() {
            return null;
        }
    }

    /**
     * What edit mode needs for Copy as config on a card's row (#454; a sub-interface: Host stands at
     * its method cap). A host that is not a ConfigHost offers no Copy as config.
     */
    public interface ConfigHost extends Host {
        /** Puts setting {@code id}'s terl.conf line ({@code id = value}) on the clipboard. */
        void copyAsConfig(@NonNull String id);
    }

    /**
     * What edit mode needs besides {@link BarHost} for the Windows card (#451; a sub-interface: Host
     * stands at its method cap). A host that is not a WindowsHost has no Windows card.
     */
    public interface WindowsHost extends BarHost {
        /** The window gap, rim colour, unfocused dim and the terminals' text look, or null when they cannot change here. */
        @Nullable
        EditSession.Keyed windows();

        /**
         * Calls {@code tap} when a finger taps any window (its body acts on nothing meanwhile);
         * null stops.
         */
        void onWindowTap(@Nullable Runnable tap);

        /** The font families already installed, as {id, name} pairs; none when there are none. */
        @NonNull
        List<String[]> installedFonts();

        /** Opens the Fonts page of Settings, where a family is installed and picked. */
        void openFonts();

        /** The Font size row's range in dp, {min, max}, where the screen gives none. */
        int FONT_SIZE_MIN_DP = 4;
        int FONT_SIZE_MAX_DP = 32;

        /** The terminal font size's range in dp, as {min, max}. */
        @NonNull
        default int[] fontSizeRangeDp() {
            return new int[] {FONT_SIZE_MIN_DP, FONT_SIZE_MAX_DP};
        }

        /** Whether the user has colours of their own (a colors.properties the app did not write) to go back to. */
        default boolean hasOwnColours() {
            return false;
        }
    }

    /**
     * What edit mode needs besides {@link Host} for the Keyboard card's links (a sub-interface: Host
     * stands at its method cap). Its sizes and extra-keys row are {@link WindowsHost#windows}'s.
     */
    public interface KeyboardHost extends Host {
        /** Opens Settings' extra-keys editor. */
        void openExtraKeys();

        /** Opens Settings' keyboard style page: theme, per-key colours and typeface. */
        void openKeyboardStyle();
    }

    /**
     * What edit mode needs besides {@link Host} for its live labels (round 5; a sub-interface: Host
     * stands at its method cap). A host that is not an OutlineHost gets the frame, the bar's and
     * the keyboard's outlines and no others.
     */
    public interface OutlineHost extends Host {
        /** The place in front's windows as they stand on screen (screen px); none on Home. */
        @NonNull
        List<EditOutlines.Window> windowsOnScreen();

        /** Home's page as it stands on screen (screen px) while Home is in front, else null. */
        @Nullable
        EditOutlines.Box homeOnScreen();

        /**
         * Calls {@code atRest} once each time the windows come to rest after moving without a
         * layout (a Workspace's columns scrolled, a move between places); null stops.
         */
        void onWindowsAtRest(@Nullable Runnable atRest);
    }

    /**
     * What edit mode needs besides {@link Host} for the All glass card's Look tiles and its link
     * to Settings (a sub-interface: Host stands at its method cap). A host that is not a LooksHost
     * draws the tiles over a plain fill and offers no link.
     */
    public interface LooksHost extends Host {
        /**
         * The least blurred wallpaper frame the blur cache already holds, which the Look tiles
         * blur again at each Look's radius; null when it holds none. Never captures or blurs.
         */
        @Nullable
        android.graphics.Bitmap wallpaperFrame();

        /** Opens Settings. */
        void openSettings();
    }

    /** The bar's and the keyboard's outline corners, dp, clamped to half their height. */
    private static final float BAR_OUTLINE_RADIUS_DP = 22f;
    private static final float KEYBOARD_OUTLINE_RADIUS_DP = 16f;

    private static final int OUTLINE_STROKE_DP = 2;
    private static final int OUTLINE_RADIUS_DP = 12;
    private static final int CARD_RADIUS_DP = 24;
    private static final int GAP_DP = 12;
    private static final int TOUCH_DP = 40;
    private static final int MARKER_DP = 12;
    private static final float DISABLED_ALPHA = 0.38f;
    /** A Look tile's height and the room between two tiles, dp. */
    static final int TILE_HEIGHT_DP = 48;
    static final int TILE_GAP_DP = 6;
    /** A row label's least width on the prototype's inline rows, dp. */
    private static final int INLINE_LABEL_DP = 92;
    /** A Tint colour dot's size, dp. */
    private static final int DOT_DP = 28;
    /** The Global card's tallest, as a share of the screen: past it the card scrolls. */
    private static final float GLOBAL_CARD_MAX_SHARE = 0.55f;
    private static final int CORNERS_MAX_DP = 48;
    /** The end column a row's switch or glyph stands in, so they line up down a card. */
    private static final int END_COLUMN_DP = 56;
    /** The Keyboard card's Height range, in percent of the standard height. */
    private static final int KEYBOARD_HEIGHT_MIN = Math.round(com.termux.shared.termux.settings
        .preferences.TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE * 100f);
    private static final int KEYBOARD_HEIGHT_MAX = Math.round(com.termux.shared.termux.settings
        .preferences.TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE * 100f);

    /** Reads what a slider row shows. */
    private interface Reader {
        int read();
    }

    /** One slider row of a card. */
    private static final class RowViews {
        final EditSession.Setting setting;
        /** The surface and property of an own value, which shows the Follow Global marker; else null. */
        @Nullable final TerlSurface surface;
        @Nullable final TerlProperty property;
        final int min;
        final int step;
        final int valueText;
        final Reader reader;
        final SeekBar slider;
        final TextView value;
        @Nullable final View marker;
        @Nullable final View markerTarget;
        boolean dragging;
        /** What the marker last showed: null before the first refresh. */
        @Nullable Boolean shownOwn;

        RowViews(EditSession.Setting setting, @Nullable TerlSurface surface,
                 @Nullable TerlProperty property, int min, int step, int valueText, Reader reader,
                 SeekBar slider, TextView value, @Nullable View marker, @Nullable View markerTarget) {
            this.setting = setting;
            this.surface = surface;
            this.property = property;
            this.min = min;
            this.step = step;
            this.valueText = valueText;
            this.reader = reader;
            this.slider = slider;
            this.value = value;
            this.marker = marker;
            this.markerTarget = markerTarget;
        }
    }

    /** A choice chip of the Global card: a Tint colour or an icon pack. */
    private static final class Chip {
        final String value;
        final TextView view;

        Chip(String value, TextView view) {
            this.value = value;
            this.view = view;
        }
    }

    /** A switch over a {@link EditSession.Keyed} setting: a number 1 or 0, or a choice "on…" or "off". */
    private static final class KeyedSwitch {
        final String key;
        final boolean choice;
        final android.widget.Switch view;

        KeyedSwitch(String key, boolean choice, android.widget.Switch view) {
            this.key = key;
            this.choice = choice;
            this.view = view;
        }

        boolean on(@Nullable Object value) {
            if (choice) return value instanceof String && ((String) value).startsWith("on");
            return value instanceof Integer && (Integer) value != 0;
        }
    }

    @NonNull private final Host mHost;

    @Nullable private EditSession mSession;
    @Nullable private FrameLayout mOverlay;
    @Nullable private View mOutline;
    /** The live labels over the scene ({@link EditOutlines}). */
    @Nullable private EditOutlinesView mOutlines;
    /** Their words, read once per open; and the scratch every sync reuses (review P3-2). */
    @Nullable private EditOutlines.Labels mOutlineLabels;
    @NonNull private final int[] mOutlineOrigin = new int[2];
    @NonNull private final int[] mOutlineAt = new int[2];
    @NonNull private final List<EditOutlines.Window> mOutlineWindows = new ArrayList<>();
    @Nullable private View mCard;
    @Nullable private View mGlobalCard;
    @Nullable private View mWallpaperCard;
    @Nullable private View mHomeCard;
    @Nullable private View mBarCard;
    @Nullable private TextView mBarChip;
    @Nullable private View mWindowsCard;
    @Nullable private TextView mWindowsChip;
    @Nullable private View mWindowTab;
    @Nullable private View mTextTab;
    @Nullable private TextView mWindowTabChip;
    @Nullable private TextView mTextTabChip;
    /** The Windows card's open tab: false is Window, true is Text. */
    private boolean mTextTabShown;
    @NonNull private final List<Chip> mRimChips = new ArrayList<>();
    @NonNull private final List<Chip> mContrastChips = new ArrayList<>();
    @NonNull private final List<Chip> mTrailChips = new ArrayList<>();
    @NonNull private final List<Chip> mEffectChips = new ArrayList<>();
    @NonNull private final List<Chip> mFontChips = new ArrayList<>();
    @NonNull private final List<Chip> mSchemeChips = new ArrayList<>();
    @NonNull private final List<Chip> mCursorChips = new ArrayList<>();
    @NonNull private final List<Chip> mTearChips = new ArrayList<>();
    /** The Windows and Keyboard cards' switches over {@link EditSession.Keyed} settings. */
    @NonNull private final List<KeyedSwitch> mKeyedSwitches = new ArrayList<>();
    /** The Text tab's Effects, and its header; folded away on every visit until asked for. */
    @Nullable private View mEffectsBox;
    @Nullable private TextView mEffectsHeader;
    private boolean mEffectsShown;
    @Nullable private android.widget.Switch mBarReadingSwitch;
    @NonNull private final List<Chip> mRailChips = new ArrayList<>();
    /** The Control bar card's tool rows, by place. */
    @NonNull private final java.util.Map<String, ToolRow> mToolRows = new java.util.LinkedHashMap<>();
    /** Whether Home was on when the Home card was built: the card is built again when that changes. */
    private boolean mHomeCardOn;
    @Nullable private TextView mHomeChip;
    @Nullable private android.widget.Switch mDotsSwitch;
    @NonNull private final List<Chip> mAzChips = new ArrayList<>();
    @Nullable private TextView mWallpaperChip;
    @NonNull private final List<Chip> mThemeChips = new ArrayList<>();
    @Nullable private android.widget.Switch mDriftSwitch;
    @Nullable private android.widget.Switch mColoursSwitch;
    /** A refresh is setting a switch: its listener must not write. */
    private boolean mSettingSwitch;
    /** The All glass card's Look tiles, in filmstrip order. */
    @NonNull private final List<LookTile> mLookTiles = new ArrayList<>();
    /** Each Look's own picture of the wallpaper, made from {@link #mTileSource}; let go on leave. */
    @NonNull private final java.util.Map<String, android.graphics.Bitmap> mTileFrames = new java.util.HashMap<>();
    @Nullable private android.graphics.Bitmap mTileSource;
    @Nullable private View mAsk;
    @Nullable private TextView mAskText;
    @Nullable private View mCustomRows;
    @Nullable private TextView mGlobalChip;
    @NonNull private final List<Chip> mTintChips = new ArrayList<>();
    @NonNull private final List<Chip> mIconPackChips = new ArrayList<>();
    @Nullable private LinearLayout mIconPackRow;
    /** The Home card's chips: columns, and its keyed choices ("key=value"). */
    @NonNull private final List<Chip> mColumnChips = new ArrayList<>();
    @NonNull private final List<Chip> mHomeKeyedChips = new ArrayList<>();
    @Nullable private android.widget.Switch mLabelsSwitch;
    @Nullable private android.widget.Switch mMonochromeSwitch;
    /** The Look tapped on the filmstrip that waits for the ask's answer; null while none waits. */
    @Nullable private String mAskedLook;
    /** The icon packs' listing and parsing, off the main thread; made on open, shut on leave. */
    @Nullable private java.util.concurrent.ExecutorService mBackground;
    private final android.os.Handler mMain = new android.os.Handler(android.os.Looper.getMainLooper());
    /** Bumped by every icon pack tap and listing: a late answer for an older one is dropped. */
    private int mIconPackGeneration;
    private boolean mIconPacksAsked;
    @Nullable private TextView mCount;
    @Nullable private TextView mKeyboardChip;
    /** The widest the strip's change count stands, in dp. */
    static final int STRIP_COUNT_CAP_DP = 132;
    @Nullable private TextView mUndo;
    @Nullable private TextView mRevert;
    @Nullable private View mStrip;
    /** The legibility meter (#448): above the open card while an Opacity or Blur drag is live. */
    @Nullable private LinearLayout mMeter;
    @Nullable private TextView mMeterText;
    @Nullable private TextView mMeterDim;
    /** The Opacity and Blur drags live now; the meter shows while there is one. */
    private int mMeterDrags;
    private boolean mMeterPending;
    /** An amber meter stays up after the finger lifts, until the next change, Dim or the card closing. */
    private boolean mMeterHeld;
    /** A refresh that is part of lifting or of Dim wallpaper: it does not clear a held meter. */
    private boolean mKeepMeter;
    /** The dim the meter's Dim wallpaper writes, or -1 for none. */
    private int mMeterDimTo = -1;
    /** The meter measures at most this often while a value moves (arithmetic on a held sample). */
    private static final long METER_INTERVAL_MS = 250;
    @NonNull private final List<RowViews> mRows = new ArrayList<>();
    @Nullable private Card mOpenCard;
    /** The keyboard was down and its card brought it up: closing the card puts it back. */
    private boolean mRaisedKeyboard;
    /** The bar was out of sight and its card brought it up: closing the card puts it back. */
    private boolean mRaisedBar;

    private final TerlLookStore.Listener mLookListener = this::refresh;
    /**
     * Keeps the outline on the keyboard and the card above it after any layout of the window (the
     * keyboard coming up or going down). Only compares positions; it moves a view only when the
     * keyboard moved.
     */
    private final ViewTreeObserver.OnGlobalLayoutListener mPlaceListener = this::place;

    public EditModeController(@NonNull Host host) {
        mHost = host;
    }

    public boolean isOpen() {
        return mOverlay != null;
    }

    /** The card open now, or null at rest. */
    @Nullable
    public Card openCard() {
        return mOpenCard;
    }

    /**
     * The visit as text, for the window about to be rebuilt (a theme change recreates it): the open
     * card and the session's history. Null when edit mode is not open.
     */
    @Nullable
    public String carry() {
        // The activity is going (a recreate): the Copy as config menu's window goes with it.
        dismissCopyMenu();
        EditSession session = mSession;
        if (!isOpen() || session == null) return null;
        String saved = session.save();
        if (saved == null) return null;
        return (mOpenCard == null ? EditModeDoor.AT_REST : mOpenCard.name()) + "\n" + saved;
    }

    /** Opens edit mode again as {@link #carry()} left it; at rest and with no history if it cannot be read. */
    public void reopen(@NonNull String carried) {
        int cut = carried.indexOf('\n');
        Card card = cut < 0 ? null : EditModeDoor.cardOf(carried.substring(0, cut));
        EditSession restored = null;
        if (cut >= 0) {
            restored = EditSession.restore(carried.substring(cut + 1), mHost.looks(), mHost.launcher(),
                mHost.wallpaper(), mHost.home(), barSettings(), windowSettings());
        }
        if (isOpen() || !build(restored)) return;
        if (card != null) openCard(card);
        refresh();
        place();
    }

    @Nullable
    private EditSession.Bar barSettings() {
        return mHost instanceof BarHost ? ((BarHost) mHost).bar() : null;
    }

    @Nullable
    private EditSession.Keyed windowSettings() {
        return mHost instanceof WindowsHost ? ((WindowsHost) mHost).windows() : null;
    }

    /** Opens edit mode, on {@code card} when one is named. Already open, it only raises the card. */
    public void open(@Nullable Card card) {
        if (!isOpen() && !build(null)) return;
        if (card != null) openCard(card);
        refresh();
        place();
    }

    /** Raises {@code card}; a surface that is not on screen comes up while its card is open. */
    public void openCard(@NonNull Card card) {
        if (!isOpen() || mCard == null || mGlobalCard == null || mWallpaperCard == null || mHomeCard == null
            || mBarCard == null || mWindowsCard == null) return;
        if (mOpenCard == card) return;
        closeCard();
        if (card == Card.HOME) rebuildHomeCardIfSwitched();
        mOpenCard = card;
        if (card == Card.KEYBOARD) {
            if (!mHost.isKeyboardShown()) mRaisedKeyboard = mHost.showKeyboard();
        } else if (card == Card.BAR) {
            // A bar that is out of sight comes up while its card is open.
            if (mHost instanceof BarHost) mRaisedBar = ((BarHost) mHost).raiseBar();
        } else if (card == Card.GLOBAL) {
            loadIconPacks();
        }
        setVisible(cardView(card), true);
        refresh();
        place();
    }

    @NonNull
    private View cardView(@NonNull Card card) {
        View view = card == Card.GLOBAL ? mGlobalCard
            : card == Card.WALLPAPER ? mWallpaperCard : card == Card.HOME ? mHomeCard
            : card == Card.BAR ? mBarCard : card == Card.WINDOWS ? mWindowsCard : mCard;
        if (view == null) throw new IllegalStateException("edit mode is not open");
        return view;
    }

    /** Closes the open card; false when none is open. */
    public boolean closeCard() {
        if (mOpenCard == null) return false;
        mOpenCard = null;
        if (mCard != null) setVisible(mCard, false);
        if (mGlobalCard != null) setVisible(mGlobalCard, false);
        if (mWallpaperCard != null) setVisible(mWallpaperCard, false);
        if (mHomeCard != null) setVisible(mHomeCard, false);
        if (mBarCard != null) setVisible(mBarCard, false);
        if (mWindowsCard != null) setVisible(mWindowsCard, false);
        for (RowViews row : mRows) endDrag(row);
        hideMeter();
        dismissAsk(false);
        if (mRaisedKeyboard) {
            mRaisedKeyboard = false;
            mHost.hideKeyboard();
        }
        if (mRaisedBar) {
            mRaisedBar = false;
            if (mHost instanceof BarHost) ((BarHost) mHost).lowerBar();
        }
        refresh();
        place();
        return true;
    }

    /** Back: closes the card first, then leaves. False when edit mode is not open. */
    public boolean onBack() {
        if (!isOpen()) return false;
        if (closeCard()) return true;
        leave();
        return true;
    }

    /** Leaves edit mode. The changes stay; the history and the views go. */
    public void leave() {
        if (!isOpen()) return;
        dismissCopyMenu();
        closeCard();
        if (mSession != null) mSession.end();
        mSession = null;
        mHost.looks().removeListener(mLookListener);
        mHost.onWallpaperTap(null);
        EditSession.Home home = mHost.home();
        if (home != null) home.onEmptyTap(null);
        if (mHost instanceof BarHost) ((BarHost) mHost).onBarTap(null);
        if (mHost instanceof BarHost && mBarLift != 0) ((BarHost) mHost).liftBar(0);
        mBarLift = 0;
        if (mHost instanceof WindowsHost) ((WindowsHost) mHost).onWindowTap(null);
        FrameLayout overlay = mOverlay;
        mOverlay = null;
        if (overlay != null) {
            ViewTreeObserver observer = overlay.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(mPlaceListener);
            if (overlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) overlay.getParent()).removeView(overlay);
            }
        }
        mOutline = null;
        mOutlines = null;
        mOutlineLabels = null;
        mOutlineWindows.clear();
        if (mHost instanceof OutlineHost) ((OutlineHost) mHost).onWindowsAtRest(null);
        mCard = null;
        mGlobalCard = null;
        mWallpaperCard = null;
        mHomeCard = null;
        mBarCard = null;
        mWindowsCard = null;
        mWindowTab = null;
        mTextTab = null;
        mWindowTabChip = null;
        mTextTabChip = null;
        mTextTabShown = false;
        mRimChips.clear();
        mContrastChips.clear();
        mTrailChips.clear();
        mEffectChips.clear();
        mFontChips.clear();
        mSchemeChips.clear();
        mCursorChips.clear();
        mTearChips.clear();
        mKeyedSwitches.clear();
        mEffectsBox = null;
        mEffectsHeader = null;
        mEffectsShown = false;
        mBarChip = null;
        mWindowsChip = null;
        mBarReadingSwitch = null;
        mRailChips.clear();
        mToolRows.clear();
        mHomeChip = null;
        mDotsSwitch = null;
        mAzChips.clear();
        mWallpaperChip = null;
        mThemeChips.clear();
        mDriftSwitch = null;
        mColoursSwitch = null;
        mLookTiles.clear();
        mTileFrames.clear();
        mTileBlur.clear();
        mTileSource = null;
        mAsk = null;
        mAskText = null;
        mCustomRows = null;
        mGlobalChip = null;
        mTintChips.clear();
        mIconPackChips.clear();
        mIconPackRow = null;
        mColumnChips.clear();
        mHomeKeyedChips.clear();
        mLabelsSwitch = null;
        mMonochromeSwitch = null;
        mAskedLook = null;
        mCount = null;
        mKeyboardChip = null;
        mUndo = null;
        mRevert = null;
        mStrip = null;
        mMain.removeCallbacks(mMeterMeasure);
        mMeter = null;
        mMeterText = null;
        mMeterDim = null;
        mMeterDrags = 0;
        mMeterPending = false;
        mMeterHeld = false;
        mMeterDimTo = -1;
        mRows.clear();
        mIconPackGeneration++;
        mIconPacksAsked = false;
        if (mBackground != null) mBackground.shutdown();
        mBackground = null;
        mHost.onLeft();
    }

    /** Runs {@code work} off the main thread, then {@code then} on it while edit mode is open. */
    private void offMain(@NonNull Runnable work, @NonNull Runnable then) {
        if (mBackground == null) mBackground = java.util.concurrent.Executors.newSingleThreadExecutor();
        int generation = mIconPackGeneration;
        mBackground.execute(() -> {
            work.run();
            mMain.post(() -> {
                if (isOpen() && generation == mIconPackGeneration) then.run();
            });
        });
    }

    // ---------------------------------------------------------------- building

    private boolean build(@Nullable EditSession restored) {
        ViewGroup layer = mHost.editorLayer();
        if (layer == null) return false;
        Context context = mHost.context();
        mSession = restored != null ? restored
            : EditSession.begin(mHost.looks(), mHost.launcher(), mHost.wallpaper(), mHost.home(),
                barSettings(), windowSettings());

        FrameLayout overlay = new FrameLayout(context);
        // Takes no touch of its own: the desktop under it still moves while editing.
        overlay.setClickable(false);
        overlay.setFocusable(false);
        ViewCompat.setOnApplyWindowInsetsListener(overlay, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (view.getPaddingBottom() != bars.bottom || view.getPaddingLeft() != bars.left
                || view.getPaddingRight() != bars.right) {
                view.setPadding(bars.left, 0, bars.right, bars.bottom);
            }
            if (mOutlines != null) mOutlines.setTopInset(bars.top);
            return insets;
        });

        // The window's bars are known already: the overlay stands inside them from its first layout,
        // so the bar's lift (and the windows' one resize) is the settled one at once, never a
        // second when the insets are dispatched (review P3).
        WindowInsetsCompat known = ViewCompat.getRootWindowInsets(layer);
        if (known != null) {
            Insets bars = known.getInsets(WindowInsetsCompat.Type.systemBars());
            overlay.setPadding(bars.left, 0, bars.right, bars.bottom);
        }

        // Bottom of the overlay: the live labels, under the keyboard's door, the cards and the strip.
        mOutlines = new EditOutlinesView(context, accentOf(context), () -> openCard(Card.WALLPAPER));
        mOutlineLabels = new EditOutlines.Labels(
            context.getString(R.string.edit_mode_wallpaper_chip),
            context.getString(R.string.edit_mode_card_home),
            context.getString(R.string.edit_mode_card_control_bar),
            context.getString(R.string.edit_mode_card_keyboard));
        // Windows that scroll or move with no layout after them: the outlines follow at rest.
        if (mHost instanceof OutlineHost) ((OutlineHost) mHost).onWindowsAtRest(this::place);
        overlay.addView(mOutlines, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        mOutline = buildOutline(context);
        overlay.addView(mOutline, new FrameLayout.LayoutParams(0, 0));
        mCard = buildKeyboardCard(context);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM);
        cardParams.leftMargin = dp(GAP_DP);
        cardParams.rightMargin = dp(GAP_DP);
        overlay.addView(mCard, cardParams);
        setVisible(mCard, false);
        mGlobalCard = buildGlobalCard(context);
        FrameLayout.LayoutParams globalParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM);
        globalParams.leftMargin = dp(GAP_DP);
        globalParams.rightMargin = dp(GAP_DP);
        overlay.addView(mGlobalCard, globalParams);
        setVisible(mGlobalCard, false);
        mWallpaperCard = buildWallpaperCard(context);
        FrameLayout.LayoutParams wallpaperParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM);
        wallpaperParams.leftMargin = dp(GAP_DP);
        wallpaperParams.rightMargin = dp(GAP_DP);
        overlay.addView(mWallpaperCard, wallpaperParams);
        setVisible(mWallpaperCard, false);
        mHomeCard = buildHomeCard(context);
        FrameLayout.LayoutParams homeParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM);
        homeParams.leftMargin = dp(GAP_DP);
        homeParams.rightMargin = dp(GAP_DP);
        overlay.addView(mHomeCard, homeParams);
        setVisible(mHomeCard, false);
        mBarCard = buildBarCard(context);
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM);
        barParams.leftMargin = dp(GAP_DP);
        barParams.rightMargin = dp(GAP_DP);
        overlay.addView(mBarCard, barParams);
        setVisible(mBarCard, false);
        mWindowsCard = buildWindowsCard(context);
        FrameLayout.LayoutParams windowsParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM);
        windowsParams.leftMargin = dp(GAP_DP);
        windowsParams.rightMargin = dp(GAP_DP);
        overlay.addView(mWindowsCard, windowsParams);
        setVisible(mWindowsCard, false);
        mMeter = buildMeter(context);
        FrameLayout.LayoutParams meterParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM);
        meterParams.leftMargin = dp(GAP_DP);
        meterParams.rightMargin = dp(GAP_DP);
        overlay.addView(mMeter, meterParams);
        setVisible(mMeter, false);
        mStrip = buildStrip(context);
        FrameLayout.LayoutParams stripParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM);
        stripParams.leftMargin = dp(GAP_DP);
        stripParams.rightMargin = dp(GAP_DP);
        stripParams.bottomMargin = dp(GAP_DP / 2);
        overlay.addView(mStrip, stripParams);

        layer.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        mOverlay = overlay;
        overlay.getViewTreeObserver().addOnGlobalLayoutListener(mPlaceListener);
        ViewCompat.requestApplyInsets(overlay);
        mHost.looks().addListener(mLookListener);
        mHost.onWallpaperTap(() -> openCard(Card.WALLPAPER));
        // On Home the wallpaper and Home are one place: a tap on empty Home opens the Home card.
        EditSession.Home home = mHost.home();
        if (home != null) home.onEmptyTap(() -> openCard(Card.HOME));
        // A tap on the bar opens its card; the bar answers nothing else meanwhile.
        if (mHost instanceof BarHost && barSettings() != null) {
            ((BarHost) mHost).onBarTap(() -> openCard(Card.BAR));
        }
        // A tap on any window opens the Windows card; a window's body acts on nothing meanwhile.
        if (mHost instanceof WindowsHost && windowSettings() != null) {
            ((WindowsHost) mHost).onWindowTap(() -> openCard(Card.WINDOWS));
        }
        return true;
    }

    @NonNull
    private View buildOutline(@NonNull Context context) {
        // The keyboard's door: a tap target only, its dashed outline and label are EditOutlines'.
        View outline = new View(context);
        outline.setContentDescription(context.getString(R.string.edit_mode_card_keyboard));
        outline.setOnClickListener(view -> openCard(Card.KEYBOARD));
        outline.setVisibility(View.GONE);
        return outline;
    }

    /**
     * The Keyboard card: Opacity and Blur (the keyboard's own, with Follow Global), its Height and
     * Key spacing, the extra-keys row with a way to its editor, and a way to the keyboard's style
     * page (theme, per-key colours, typeface). The keys' corners follow Global's Corners, so the
     * card has no row for them. It scrolls past the room above the keyboard.
     */
    @NonNull
    private View buildKeyboardCard(@NonNull Context context) {
        ScrollView scroll = scrollPanel(context);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad / 2);
        scroll.addView(card);

        TextView title = text(context, R.string.edit_mode_card_keyboard, 18,
            com.google.android.material.R.attr.colorOnSurface);
        card.addView(title);
        mRows.add(addOwnRow(context, card, TerlSurface.KEYBOARD, TerlProperty.OPACITY,
            R.string.edit_mode_opacity));
        mRows.add(addOwnRow(context, card, TerlSurface.KEYBOARD, TerlProperty.BLUR,
            R.string.edit_mode_blur));
        EditSession.Keyed keyed = windowSettings();
        if (keyed == null) return scroll;
        mRows.add(addSliderRow(context, card,
            EditSession.Setting.keyedNumber(EditSession.Keyed.KEYBOARD_HEIGHT), null, null,
            KEYBOARD_HEIGHT_MIN, KEYBOARD_HEIGHT_MAX, 5, R.string.edit_mode_percent,
            R.string.edit_mode_keyboard_height,
            () -> keyedNumber(keyed, EditSession.Keyed.KEYBOARD_HEIGHT)));
        mRows.add(addSliderRow(context, card,
            EditSession.Setting.keyedNumber(EditSession.Keyed.KEY_SPACING), null, null,
            com.termux.app.settings.KeySpacingAdapter.DECLARATION.min,
            com.termux.app.settings.KeySpacingAdapter.DECLARATION.max, 1, R.string.edit_mode_dp,
            R.string.edit_mode_key_spacing, () -> keyedNumber(keyed, EditSession.Keyed.KEY_SPACING)));
        android.widget.Switch extraKeys = keyedSwitch(context, card, R.string.edit_mode_extra_keys,
            EditSession.Keyed.EXTRA_KEYS, false);
        if (mHost instanceof KeyboardHost) {
            KeyboardHost links = (KeyboardHost) mHost;
            // Edit keys is the row's pencil, beside its switch; the switch keeps the end column.
            ViewGroup row = (ViewGroup) extraKeys.getParent().getParent();
            row.addView(glyphButton(context, R.drawable.ic_ms_edit, R.string.edit_mode_edit_keys,
                view -> links.openExtraKeys()), row.getChildCount() - 1);
            // Keyboard style is a row whose end column holds the glyph that opens it.
            LinearLayout style = new LinearLayout(context);
            style.setOrientation(LinearLayout.HORIZONTAL);
            style.setGravity(Gravity.CENTER_VERTICAL);
            style.setMinimumHeight(dp(TOUCH_DP + 8));
            style.addView(text(context, R.string.edit_mode_keyboard_style, 15,
                com.google.android.material.R.attr.colorOnSurface),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            style.addView(endColumn(context, glyphButton(context, R.drawable.ic_ms_palette,
                R.string.edit_mode_keyboard_style, view -> links.openKeyboardStyle())));
            card.addView(style);
        }
        return scroll;
    }

    /** The end column every row's control stands in, so switches and glyphs line up down a card. */
    @NonNull
    private FrameLayout endColumn(@NonNull Context context, @NonNull View control) {
        FrameLayout column = new FrameLayout(context);
        column.setMinimumWidth(dp(END_COLUMN_DP));
        column.addView(control, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.END));
        return column;
    }

    /** A glyph that is the action: a touch-sized button, its name spoken and shown on a hold. */
    @NonNull
    private android.widget.ImageButton glyphButton(@NonNull Context context, int iconRes, int nameRes,
                                                   @NonNull View.OnClickListener click) {
        android.widget.ImageButton button = new android.widget.ImageButton(context);
        button.setImageResource(iconRes);
        button.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(context,
            androidx.appcompat.R.attr.colorPrimary, 0xFF8AB4F8)));
        button.setBackgroundResource(selectableBorderless(context));
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        button.setMinimumWidth(dp(TOUCH_DP));
        button.setMinimumHeight(dp(TOUCH_DP));
        String name = context.getString(nameRes);
        button.setContentDescription(name);
        androidx.appcompat.widget.TooltipCompat.setTooltipText(button, name);
        button.setOnClickListener(click);
        return button;
    }

    /**
     * A switch row over the {@link EditSession.Keyed} setting {@code key}: a number written 1 or 0,
     * or with {@code choice} a choice written "on" or "off". Each flip is one Undo step.
     */
    @NonNull
    private android.widget.Switch keyedSwitch(@NonNull Context context, @NonNull LinearLayout card,
                                              int labelRes, @NonNull String key, boolean choice) {
        android.widget.Switch toggle = addSwitchRow(context, card, labelRes, on -> {
            if (mSession == null) return;
            if (choice) mSession.setKeyedChoice(key, on ? "on" : "off");
            else mSession.set(EditSession.Setting.keyedNumber(key), on ? 1 : 0);
            refresh();
        });
        mKeyedSwitches.add(new KeyedSwitch(key, choice, toggle));
        return toggle;
    }

    /** The Keyboard card as the settings hold it: its switches. */
    private void refreshKeyboard() {
        EditSession.Keyed keyed = windowSettings();
        if (keyed == null || mOpenCard != Card.KEYBOARD) return;
        refreshKeyedSwitches(keyed);
    }

    /** Every keyed switch as its setting holds it, without writing back. */
    private void refreshKeyedSwitches(@NonNull EditSession.Keyed keyed) {
        mSettingSwitch = true;
        try {
            for (KeyedSwitch toggle : mKeyedSwitches) {
                boolean on = toggle.on(keyed.get(toggle.key));
                if (toggle.view.isChecked() != on) toggle.view.setChecked(on);
            }
        } finally {
            mSettingSwitch = false;
        }
    }

    @NonNull
    private RowViews addOwnRow(@NonNull Context context, @NonNull LinearLayout card,
                               @NonNull TerlSurface surface, @NonNull TerlProperty property,
                               int labelRes) {
        return addSliderRow(context, card, EditSession.Setting.own(surface, property), surface,
            property, property.min, property.max, 1, valueTextOf(property), labelRes,
            () -> mHost.looks().value(surface, property));
    }

    private static int valueTextOf(@NonNull TerlProperty property) {
        return property == TerlProperty.OPACITY ? R.string.edit_mode_percent : R.string.edit_mode_dp;
    }

    /**
     * The Global card, the prototype's All glass: the Look filmstrip (every shipped Look and Custom
     * in one row, each tile drawn as that glass; a tap wears it at once, or the card asks first
     * when it would clear own values), Custom's Opacity and Blur while Custom is worn, then Tint
     * colour (Auto and the wallpaper's colours), Corners, Depth and Shine (app-wide, ADR 0033),
     * the icon pack, text size and All settings. Like the keyboard's card it stands above the
     * keyboard when that is up, so the keyboard wearing the Look stays in sight; it scrolls past a
     * share of the screen, or past the room above the keyboard.
     */
    @NonNull
    private View buildGlobalCard(@NonNull Context context) {
        ScrollView scroll = scrollPanel(context);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad / 2);
        card.addView(text(context, R.string.edit_mode_card_global, 18,
            com.google.android.material.R.attr.colorOnSurface));

        View film = buildFilmstrip(context);
        LinearLayout.LayoutParams filmParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        filmParams.topMargin = dp(12);
        card.addView(film, filmParams);
        mAsk = buildAsk(context);
        card.addView(mAsk);
        setVisible(mAsk, false);

        LinearLayout custom = new LinearLayout(context);
        custom.setOrientation(LinearLayout.VERTICAL);
        for (TerlProperty property : TerlProperty.values()) {
            mRows.add(addSliderRow(context, custom, EditSession.Setting.custom(property), null, null,
                property.min, property.max, 1, valueTextOf(property),
                property == TerlProperty.OPACITY ? R.string.edit_mode_opacity : R.string.edit_mode_blur,
                () -> mHost.looks().customValue(property), true));
        }
        mCustomRows = custom;
        card.addView(custom);
        setVisible(custom, false);

        // Tint colour: Auto, then a dot in each of the wallpaper's colours (the theme takes its
        // accents only from the wallpaper).
        LinearLayout tints = inlineRow(context, card, R.string.edit_mode_tint);
        for (String tint : TerlLookStore.TINTS) {
            View.OnClickListener pick = view -> {
                if (mSession == null) return;
                dismissAsk(true);
                mSession.setTint(tint);
                refresh();
            };
            TextView chip = TerlLookStore.TINT_AUTO.equals(tint)
                ? addChip(context, tints, context.getString(R.string.edit_mode_tint_auto), pick)
                : addDot(context, tints, tintName(context, tint), pick);
            mTintChips.add(new Chip(tint, chip));
        }

        mRows.add(addSliderRow(context, card, EditSession.Setting.CORNERS, null, null, 0,
            CORNERS_MAX_DP, 1, R.string.edit_mode_dp, R.string.edit_mode_corners,
            () -> mHost.looks().cornerRadiusDp(), true));

        // Depth and Shine are app-wide (ADR 0033), written where the Appearance editor writes them.
        mRows.add(addSliderRow(context, card, EditSession.Setting.DEPTH, null, null, 0, 100, 1,
            R.string.edit_mode_percent, R.string.edit_mode_depth, () -> mHost.looks().depth(), true));
        mRows.add(addSliderRow(context, card, EditSession.Setting.SHINE, null, null, 0, 100, 1,
            R.string.edit_mode_percent, R.string.edit_mode_shine, () -> mHost.looks().shine(), true));

        EditSession.Launcher launcher = mHost.launcher();
        if (launcher != null) {
            // The app icon pack stays here until Settings has a page to pick it on.
            card.addView(rowLabel(context, R.string.edit_mode_icon_pack));
            HorizontalScrollView packs = new HorizontalScrollView(context);
            packs.setHorizontalScrollBarEnabled(false);
            mIconPackRow = new LinearLayout(context);
            mIconPackRow.setOrientation(LinearLayout.HORIZONTAL);
            packs.addView(mIconPackRow);
            card.addView(packs);
            mRows.add(addSliderRow(context, card, EditSession.Setting.TEXT_SIZE, null, null,
                com.termux.shared.termux.settings.registry.SettingDeclarations.TEXT_SIZE.min,
                com.termux.shared.termux.settings.registry.SettingDeclarations.TEXT_SIZE.max,
                com.termux.shared.termux.settings.registry.SettingDeclarations.TEXT_SIZE.step,
                R.string.edit_mode_percent, R.string.edit_mode_text_size,
                launcher::textSizePercent, true));
        }
        if (mHost instanceof LooksHost) {
            LooksHost host = (LooksHost) mHost;
            TextView all = action(context, R.string.edit_mode_all_settings, view -> host.openSettings());
            all.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            all.setPadding(0, 0, dp(12), 0);
            all.setCompoundDrawablePadding(dp(2));
            android.graphics.drawable.Drawable chevron = androidx.core.content.ContextCompat.getDrawable(
                context, R.drawable.ic_editor_chevron_right);
            if (chevron != null) {
                chevron = chevron.mutate();
                chevron.setTint(all.getCurrentTextColor());
                int size = dp(18);
                chevron.setBounds(0, 0, size, size);
                all.setCompoundDrawablesRelative(null, null, chevron, null);
            }
            card.addView(all, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        scroll.addView(card);
        return scroll;
    }

    /** "the wallpaper's first colour" and so on: what a Tint dot is called aloud. */
    @NonNull
    private static String tintName(@NonNull Context context, @NonNull String tint) {
        switch (tint) {
            case TerlLookStore.TINT_SECONDARY: return context.getString(R.string.edit_mode_tint_secondary);
            case TerlLookStore.TINT_TERTIARY: return context.getString(R.string.edit_mode_tint_tertiary);
            default: return context.getString(R.string.edit_mode_tint_primary);
        }
    }

    /** The wallpaper's colour a Tint dot shows: the theme's accent for that hue. */
    private static int tintColour(@NonNull Context context, @NonNull String tint) {
        switch (tint) {
            case TerlLookStore.TINT_SECONDARY:
                return MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondary, 0xFF8A93A8);
            case TerlLookStore.TINT_TERTIARY:
                return MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiary, 0xFFA88A93);
            default:
                return MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0xFF8AB4F8);
        }
    }

    /**
     * One of the prototype's inline rows: {@code labelRes} at the start and, at the end, the row
     * it returns for the controls. A hold on the label offers Copy as config.
     */
    @NonNull
    private LinearLayout inlineRow(@NonNull Context context, @NonNull LinearLayout card, int labelRes) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(TOUCH_DP + 8));
        TextView label = text(context, labelRes, 15, com.google.android.material.R.attr.colorOnSurface);
        // Beside a wide row of chips on a narrow phone the label takes a second line, never "…".
        label.setSingleLine(false);
        label.setMaxLines(2);
        copyable(label, EditModeControls.idOfTitle(labelRes));
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout controls = new LinearLayout(context);
        controls.setTag(INLINE_CONTROLS);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.addView(controls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(row);
        return controls;
    }

    /** Marks an inline row's controls: its chips are compact, so the row's label keeps its words. */
    private static final Object INLINE_CONTROLS = new Object();
    /**
     * Marks a segment row's controls: its chips share the row's width equally, their text
     * shrinking to fit, so four chips fit at 360 dp and a large font scale.
     */
    private static final Object SEGMENT_CONTROLS = new Object();

    /**
     * An inline row whose chips are a segment ({@link #SEGMENT_CONTROLS}): the label at the start
     * (two lines at most), the chips sharing the rest.
     */
    @NonNull
    private LinearLayout segmentRow(@NonNull Context context, @NonNull LinearLayout card, int labelRes) {
        LinearLayout controls = inlineRow(context, card, labelRes);
        controls.setTag(SEGMENT_CONTROLS);
        ViewGroup row = (ViewGroup) controls.getParent();
        View label = row.getChildAt(0);
        if (label instanceof TextView) ((TextView) label).setMaxWidth(dp(INLINE_LABEL_DP + 8));
        label.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams share = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        share.leftMargin = dp(8);
        controls.setLayoutParams(share);
        return controls;
    }

    /** A Tint colour dot: a round chip in the colour, named aloud {@code name}. */
    @NonNull
    private TextView addDot(@NonNull Context context, @NonNull LinearLayout row, @NonNull String name,
                            @NonNull View.OnClickListener click) {
        TextView dot = new TextView(context);
        dot.setContentDescription(name);
        androidx.appcompat.widget.TooltipCompat.setTooltipText(dot, name);
        dot.setOnClickListener(click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(TOUCH_DP), dp(TOUCH_DP));
        params.rightMargin = dp(2);
        row.addView(dot, params);
        return dot;
    }

    /** A dot's face: its colour, ringed in the accent while it is the one in force. */
    private void showDot(@NonNull TextView dot, int colour, boolean chosen, int accent) {
        Object shown = dot.getTag();
        int key = colour ^ (chosen ? 1 : 0);
        if (shown instanceof Integer && (Integer) shown == key && dot.getBackground() != null) return;
        dot.setTag(key);
        dot.setSelected(chosen);
        GradientDrawable face = new GradientDrawable();
        face.setShape(GradientDrawable.OVAL);
        face.setColor(colour | 0xFF000000);
        int inset = (dp(TOUCH_DP) - dp(DOT_DP)) / 2;
        if (chosen) face.setStroke(dp(2), accent);
        else face.setStroke(dp(1), withAlpha(MaterialColors.getColor(dot.getContext(),
            com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF), 0x40));
        dot.setBackground(new android.graphics.drawable.InsetDrawable(face, inset));
    }

    /** A card that scrolls past a share of the screen, or past the room above the keyboard. */
    @NonNull
    private ScrollView scrollPanel(@NonNull Context context) {
        int maxHeight = Math.round(context.getResources().getDisplayMetrics().heightPixels
            * GLOBAL_CARD_MAX_SHARE);
        ScrollView scroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                // Above the keyboard there may be less room than the share: the room wins.
                int room = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                    ? maxHeight : MeasureSpec.getSize(heightMeasureSpec) - dp(GAP_DP);
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                    Math.max(0, Math.min(maxHeight, room)), MeasureSpec.AT_MOST));
            }
        };
        scroll.setBackground(panel(context, dp(CARD_RADIUS_DP)));
        scroll.setClipToOutline(true);
        scroll.setClickable(true);
        scroll.setVerticalScrollBarEnabled(false);
        return scroll;
    }

    /**
     * The Wallpaper and colours card: the picture, its dim and drift, the theme and the colours.
     * Everything but the picture applies at once and undoes with the session. A theme or colour
     * change rebuilds the window: the session is carried over it (see {@link #carry}).
     *
     * <p>Theme offers System, Light, Dark and Black (true-black surfaces wherever Dark applies;
     * decided 2026-11-28), stored as termux.properties' night-mode.
     */
    @NonNull
    private View buildWallpaperCard(@NonNull Context context) {
        ScrollView scroll = scrollPanel(context);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad / 2);
        card.addView(text(context, R.string.edit_mode_card_wallpaper, 18,
            com.google.android.material.R.attr.colorOnSurface));
        EditSession.Wallpaper wallpaper = mHost.wallpaper();
        if (wallpaper == null) return scroll;

        card.addView(rowLabel(context, R.string.edit_mode_picture));
        LinearLayout picture = new LinearLayout(context);
        picture.setOrientation(LinearLayout.HORIZONTAL);
        picture.setGravity(Gravity.CENTER_VERTICAL);
        TextView note = new TextView(context);
        note.setText(R.string.edit_mode_picture_note);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        note.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFB0B0B0));
        picture.addView(note, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        picture.addView(action(context, R.string.edit_mode_picture_change, view -> wallpaper.changePicture()));
        card.addView(picture);

        mRows.add(addSliderRow(context, card, EditSession.Setting.DIM, null, null, 0, 100, 1,
            R.string.edit_mode_percent, R.string.edit_mode_dim, wallpaper::dim));
        mDriftSwitch = addSwitchRow(context, card, R.string.edit_mode_drift, on -> {
            if (mSession == null) return;
            mSession.setDrift(on);
            refresh();
        });

        card.addView(rowLabel(context, R.string.edit_mode_theme));
        LinearLayout themes = new LinearLayout(context);
        themes.setOrientation(LinearLayout.HORIZONTAL);
        // Four themes share the card's width, whatever the font scale (no "Bl…").
        themes.setTag(SEGMENT_CONTROLS);
        String[] values = {"system", "false", "true", "black"};
        int[] names = {R.string.appearance_theme_system, R.string.appearance_theme_light,
            R.string.appearance_theme_dark, R.string.edit_mode_theme_black};
        for (int i = 0; i < values.length; i++) {
            String theme = values[i];
            TextView chip = addChip(context, themes, context.getString(names[i]), view -> {
                if (mSession == null) return;
                mSession.setTheme(theme);
                refresh();
            });
            mThemeChips.add(new Chip(theme, chip));
        }
        card.addView(themes);

        mColoursSwitch = addSwitchRow(context, card, R.string.edit_mode_colours_from_wallpaper, on -> {
            if (mSession == null) return;
            mSession.setColoursFromWallpaper(on);
            refresh();
        });
        scroll.addView(card);
        return scroll;
    }

    /**
     * The Home card, as the prototype's: Grid columns (3, 4 or 5), Labels, Icon size, Monochrome
     * icons, the page dots and where the A–Z letters stand. Home's other settings (folders, label
     * size and shadow, two-line labels, numerals, icon packs) are in Settings › Home.
     */
    private View buildHomeCard(@NonNull Context context) {
        ScrollView scroll = scrollPanel(context);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad / 2);
        card.addView(text(context, R.string.edit_mode_card_home, 18,
            com.google.android.material.R.attr.colorOnSurface));
        scroll.addView(card);
        EditSession.Home home = mHost.home();
        if (home == null) return scroll;
        mHomeCardOn = home.available();
        if (!mHomeCardOn) {
            // Use Terl as Terminal: there is no Home to edit.
            LinearLayout off = new LinearLayout(context);
            off.setOrientation(LinearLayout.HORIZONTAL);
            off.setGravity(Gravity.CENTER_VERTICAL);
            off.addView(text(context, R.string.edit_mode_home_off, 15,
                com.google.android.material.R.attr.colorOnSurfaceVariant),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            off.addView(action(context, R.string.edit_mode_home_change, view -> {
                home.change();
                rebuildHomeCardIfSwitched();
            }));
            card.addView(off);
            return scroll;
        }

        // Grid columns as the prototype's three chips; a count set elsewhere lights none.
        LinearLayout columns = segmentRow(context, card, R.string.edit_mode_columns);
        for (int count : HOME_COLUMN_CHOICES) {
            mColumnChips.add(new Chip(String.valueOf(count), addChip(context, columns,
                String.valueOf(count), view -> {
                    if (mSession == null) return;
                    mSession.set(EditSession.Setting.COLUMNS, count);
                    refresh();
                })));
        }

        // Labels, Icon size and Monochrome icons: v2's settings, where Settings writes them.
        EditSession.Keyed keyed = windowSettings();
        if (keyed != null && keyed.get(EditSession.Keyed.LABELS_SHOWN) != null) {
            mLabelsSwitch = addSwitchRow(context, card, R.string.edit_mode_labels, on -> {
                if (mSession == null) return;
                mSession.setKeyedChoice(EditSession.Keyed.LABELS_SHOWN,
                    on ? EditSession.ON : EditSession.OFF);
                refresh();
            });
        }
        if (keyed != null && keyed.get(EditSession.Keyed.ICON_SIZE) != null) {
            LinearLayout sizes = segmentRow(context, card, R.string.edit_mode_icon_size);
            int[] sizeNames = {R.string.edit_mode_icon_size_s, R.string.edit_mode_icon_size_m,
                R.string.edit_mode_icon_size_l, R.string.edit_mode_icon_size_xl};
            for (int i = 0; i < EditSession.HOME_ICON_SIZES.size(); i++) {
                String size = EditSession.HOME_ICON_SIZES.get(i);
                mHomeKeyedChips.add(new Chip(EditSession.Keyed.ICON_SIZE + "=" + size,
                    addChip(context, sizes, context.getString(sizeNames[i]), view -> {
                        if (mSession == null) return;
                        mSession.setKeyedChoice(EditSession.Keyed.ICON_SIZE, size);
                        refresh();
                    })));
            }
        }
        if (keyed != null && keyed.get(EditSession.Keyed.MONOCHROME) != null) {
            mMonochromeSwitch = addSwitchRow(context, card, R.string.edit_mode_monochrome, on -> {
                if (mSession == null) return;
                mSession.setKeyedChoice(EditSession.Keyed.MONOCHROME,
                    on ? EditSession.ON : EditSession.OFF);
                refresh();
            });
        }

        mDotsSwitch = addSwitchRow(context, card, R.string.edit_mode_page_dots, on -> {
            if (mSession == null) return;
            mSession.setPageDots(on);
            refresh();
        });

        // Four places: the label over its segments, so they read whole at a large font scale.
        card.addView(rowLabel(context, R.string.edit_mode_az));
        LinearLayout az = new LinearLayout(context);
        az.setOrientation(LinearLayout.HORIZONTAL);
        az.setTag(SEGMENT_CONTROLS);
        card.addView(az);
        String[] edges = {EditSession.Home.AZ_OFF, EditSession.Home.AZ_LEFT,
            EditSession.Home.AZ_RIGHT, EditSession.Home.AZ_BOTTOM};
        int[] edgeNames = {R.string.edit_mode_az_off, R.string.edit_mode_az_left,
            R.string.edit_mode_az_right, R.string.edit_mode_az_bottom};
        for (int i = 0; i < edges.length; i++) {
            String edge = edges[i];
            mAzChips.add(new Chip(edge, addChip(context, az, context.getString(edgeNames[i]),
                view -> {
                    if (mSession == null) return;
                    mSession.setAzEdge(edge);
                    refresh();
                })));
        }
        return scroll;
    }

    /** The Home card's Grid columns chips, as the prototype's. */
    static final int[] HOME_COLUMN_CHOICES = {3, 4, 5};

    /** Home may have been switched on or off (Change, in Settings) since the card was built. */
    private void rebuildHomeCardIfSwitched() {
        EditSession.Home home = mHost.home();
        FrameLayout overlay = mOverlay;
        View old = mHomeCard;
        if (home == null || overlay == null || old == null || home.available() == mHomeCardOn) return;
        mColumnChips.clear();
        mHomeKeyedChips.clear();
        mAzChips.clear();
        mDotsSwitch = null;
        mLabelsSwitch = null;
        mMonochromeSwitch = null;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) old.getLayoutParams();
        overlay.removeView(old);
        mHomeCard = buildHomeCard(mHost.context());
        overlay.addView(mHomeCard, params);
        setVisible(mHomeCard, mOpenCard == Card.HOME);
    }

    /** The Home card as the settings hold it: the chips in force and the dots switch. */
    private void refreshHome(int accent) {
        EditSession.Home home = mHost.home();
        if (home == null || !home.available() || mOpenCard != Card.HOME) return;
        mSettingSwitch = true;
        try {
            if (mDotsSwitch != null && mDotsSwitch.isChecked() != home.pageDots()) {
                mDotsSwitch.setChecked(home.pageDots());
            }
            EditSession.Keyed keyed = windowSettings();
            if (keyed != null) {
                showSwitch(mLabelsSwitch, keyed, EditSession.Keyed.LABELS_SHOWN);
                showSwitch(mMonochromeSwitch, keyed, EditSession.Keyed.MONOCHROME);
                for (Chip chip : mHomeKeyedChips) showKeyedChip(chip, keyed, accent);
            }
        } finally {
            mSettingSwitch = false;
        }
        String columns = String.valueOf(home.columns());
        for (Chip chip : mColumnChips) showChip(chip.view, chip.value.equals(columns), accent);
        for (Chip chip : mAzChips) showChip(chip.view, chip.value.equals(home.azEdge()), accent);
    }

    /** A switch showing a {@link EditSession.Keyed} on/off choice; only when it changed. */
    private static void showSwitch(@Nullable android.widget.Switch toggle,
                                   @NonNull EditSession.Keyed keyed, @NonNull String key) {
        if (toggle == null) return;
        boolean on = EditSession.ON.equals(keyed.get(key));
        if (toggle.isChecked() != on) toggle.setChecked(on);
    }

    private interface SwitchListener {
        void changed(boolean on);
    }

    @NonNull
    private android.widget.Switch addSwitchRow(@NonNull Context context, @NonNull LinearLayout card,
                                               int labelRes, @NonNull SwitchListener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(TOUCH_DP + 8));
        row.addView(text(context, labelRes, 15, com.google.android.material.R.attr.colorOnSurface),
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.Switch toggle = new android.widget.Switch(context);
        toggle.setContentDescription(context.getString(labelRes));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (!mSettingSwitch) listener.changed(checked);
        });
        row.addView(endColumn(context, toggle));
        copyable(row, EditModeControls.idOfTitle(labelRes));
        card.addView(row);
        return toggle;
    }

    /** One Look's tile on the filmstrip, with its name under it. */
    private static final class LookTile {
        final String id;
        final LookTileView tile;
        final TextView name;

        LookTile(String id, LookTileView tile, TextView name) {
            this.id = id;
            this.tile = tile;
            this.name = name;
        }

        /** The cell (tile and name) reads as chosen to a screen reader while the Look is worn. */
        void cellSelected(boolean worn) {
            if (tile.getParent() instanceof View) {
                View cell = (View) tile.getParent();
                if (cell.isSelected() != worn) cell.setSelected(worn);
            }
        }
    }

    /**
     * The Look filmstrip: every shipped Look and Custom, side by side in one row that never
     * scrolls (each tile takes an equal share of the card's width, its name shrinking to fit), each
     * drawn as that glass over the wallpaper. A tap wears the Look ({@link #onLookPicked}).
     */
    @NonNull
    private View buildFilmstrip(@NonNull Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        List<String> ids = mHost.looks().lookIds();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            String name = lookName(context, id);
            cell.setContentDescription(name);
            cell.setOnClickListener(view -> onLookPicked(id));
            LookTileView tile = new LookTileView(context);
            tile.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            cell.addView(tile, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                dp(TILE_HEIGHT_DP)));
            TextView label = new TextView(context);
            label.setText(name);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            label.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF));
            label.setSingleLine(true);
            label.setGravity(Gravity.CENTER);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            // A long name at a large font scale shrinks rather than truncates.
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(label,
                8, 12, 1, TypedValue.COMPLEX_UNIT_SP);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(20));
            labelParams.topMargin = dp(4);
            cell.addView(label, labelParams);
            LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) cellParams.leftMargin = dp(TILE_GAP_DP);
            row.addView(cell, cellParams);
            mLookTiles.add(new LookTile(id, tile, label));
        }
        return row;
    }

    /** The in-card ask: a line saying what a pick clears, Cancel and Use everywhere. */
    @NonNull
    private View buildAsk(@NonNull Context context) {
        LinearLayout ask = new LinearLayout(context);
        ask.setOrientation(LinearLayout.VERTICAL);
        ask.setPadding(0, dp(4), 0, 0);
        mAskText = new TextView(context);
        mAskText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mAskText.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF));
        ask.addView(mAskText);
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.addView(action(context, R.string.edit_mode_pick_cancel, view -> dismissAsk(true)));
        actions.addView(action(context, R.string.edit_mode_pick_use, view -> {
            String look = mAskedLook;
            mAskedLook = null;
            if (mAsk != null) setVisible(mAsk, false);
            if (look != null && mSession != null) mSession.pick(look, true);
            refresh();
        }));
        ask.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return ask;
    }

    @NonNull
    private RowViews addSliderRow(@NonNull Context context, @NonNull LinearLayout card,
                                  @NonNull EditSession.Setting setting,
                                  @Nullable TerlSurface surface, @Nullable TerlProperty property,
                                  int min, int max, int step, int valueText, int labelRes,
                                  @NonNull Reader reader) {
        return addSliderRow(context, card, setting, surface, property, min, max, step, valueText,
            labelRes, reader, false);
    }

    /**
     * A slider row: its label and value over the slider, or {@code inline} as the prototype's All
     * glass and Home cards have it, the label, the slider and the value on one line.
     */
    @NonNull
    private RowViews addSliderRow(@NonNull Context context, @NonNull LinearLayout card,
                                  @NonNull EditSession.Setting setting,
                                  @Nullable TerlSurface surface, @Nullable TerlProperty property,
                                  int min, int max, int step, int valueText, int labelRes,
                                  @NonNull Reader reader, boolean inline) {
        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(8), 0, 0);

        TextView label = text(context, labelRes, 15, com.google.android.material.R.attr.colorOnSurface);
        if (inline) {
            label.setMinWidth(dp(INLINE_LABEL_DP));
            label.setMaxWidth(dp(INLINE_LABEL_DP + 24));
            head.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            head.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        TextView value = new TextView(context);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        value.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFB0B0B0));
        value.setSingleLine(true);
        SeekBar slider = new SeekBar(context);
        if (inline) {
            head.setPadding(0, 0, 0, 0);
            head.addView(slider, new LinearLayout.LayoutParams(0, dp(TOUCH_DP + 8), 1f));
            value.setGravity(Gravity.END);
            value.setMinWidth(dp(44));
        }
        head.addView(value);

        // Only an own value has a marker: it offers Follow Global.
        FrameLayout target = null;
        View marker = null;
        if (surface != null && property != null) {
            target = new FrameLayout(context);
            marker = new View(context);
            target.addView(marker, new FrameLayout.LayoutParams(dp(MARKER_DP), dp(MARKER_DP),
                Gravity.CENTER));
            target.setBackgroundResource(selectableBorderless(context));
            head.addView(target, new LinearLayout.LayoutParams(dp(TOUCH_DP), dp(TOUCH_DP)));
        } else {
            head.setMinimumHeight(dp(TOUCH_DP));
        }
        EditModeControls.Control control = EditModeControls.of(setting);
        copyable(head, control == null ? null : control.id);
        card.addView(head);

        slider.setMax((max - min) / step);
        int accent = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary,
            0xFF8AB4F8);
        slider.setProgressTintList(ColorStateList.valueOf(accent));
        slider.setThumbTintList(ColorStateList.valueOf(accent));
        slider.setContentDescription(context.getString(labelRes));
        if (!inline) {
            card.addView(slider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                dp(TOUCH_DP)));
        }

        RowViews row = new RowViews(setting, surface, property, min, step, valueText, reader,
            slider, value, marker, target);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                if (mSession == null) return;
                dismissAsk(true);
                row.dragging = true;
                mSession.dragStarted(setting);
                if (setting.changesGlassLegibility()) {
                    mMeterDrags++;
                    mMeterHeld = false;
                    scheduleMeter();
                } else {
                    hideMeter();
                }
            }

            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || mSession == null) return;
                // A drag moves its step along; a key press on the slider is a step of its own.
                mSession.set(setting, min + progress * step);
                if (setting.changesGlassLegibility()) scheduleMeter();
                // The look store's listener refreshes its own settings; the others refresh here.
                if (!setting.writesLookStore()) refresh();
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                endDrag(row);
                mKeepMeter = true;
                try {
                    refresh();
                } finally {
                    mKeepMeter = false;
                }
            }
        });
        if (target != null) {
            target.setOnClickListener(view -> {
                if (mSession == null || !mHost.looks().holdsOwn(surface, property)) return;
                mSession.followGlobal(surface, property);
                refresh();
            });
        }
        return row;
    }

    /**
     * The open card's chip wears the chosen ring (the others none); at rest every chip is plain.
     * Only on a change.
     */
    private void litChips() {
        Card open = mOpenCard;
        TextView[] chips = {mKeyboardChip, mGlobalChip, mWallpaperChip, mHomeChip, mBarChip, mWindowsChip};
        Card[] cards = {Card.KEYBOARD, Card.GLOBAL, Card.WALLPAPER, Card.HOME, Card.BAR, Card.WINDOWS};
        for (int i = 0; i < chips.length; i++) {
            TextView chip = chips[i];
            if (chip == null) continue;
            boolean on = EditStripChips.lit(cards[i], open);
            if (chip.isSelected() != on) {
                chip.setSelected(on);
                dressStripChip(chip, on);
            }
        }
    }

    @NonNull
    private View buildStrip(@NonNull Context context) {
        EditStripLayout strip = new EditStripLayout(context);
        strip.setPadding(dp(16), dp(4), dp(4), dp(4));
        strip.setBackground(panel(context, dp(TOUCH_DP)));
        strip.setClickable(true);

        // Every card has its chip, whole, on as many lines as they take, so each card is in reach
        // from every door; a chip opens its card over the one open.
        // With the keyboard down there is no outline to tap: the strip offers its card.
        mKeyboardChip = stripChip(context, R.string.edit_mode_card_keyboard, view -> openCard(Card.KEYBOARD), false);
        strip.addView(mKeyboardChip);
        mGlobalChip = stripChip(context, R.string.edit_mode_card_global, view -> openCard(Card.GLOBAL), false);
        strip.addView(mGlobalChip);
        mWallpaperChip = stripChip(context, R.string.edit_mode_wallpaper_chip,
            view -> openCard(Card.WALLPAPER), false);
        strip.addView(mWallpaperChip);
        mHomeChip = stripChip(context, R.string.edit_mode_home_chip, view -> openCard(Card.HOME), false);
        strip.addView(mHomeChip);
        mBarChip = stripChip(context, R.string.edit_mode_bar_chip, view -> openCard(Card.BAR), false);
        strip.addView(mBarChip);
        mWindowsChip = stripChip(context, R.string.edit_mode_card_windows, view -> openCard(Card.WINDOWS), false);
        strip.addView(mWindowsChip);

        mCount = new TextView(context);
        mCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mCount.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF));
        mCount.setMaxLines(2);
        mCount.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mCount.setPadding(dp(8), 0, dp(4), 0);
        // However long the count (a session's "kept as set elsewhere"), it takes only what the
        // buttons' line leaves.
        strip.addView(mCount);
        mUndo = stripChip(context, R.string.edit_mode_undo, view -> {
            if (mSession == null) return;
            mSession.undo();
            refresh();
        }, false);
        strip.addView(mUndo);
        mRevert = stripChip(context, R.string.edit_mode_revert, view -> {
            if (mSession == null) return;
            mSession.revert();
            refresh();
        }, false);
        strip.addView(mRevert);
        TextView done = stripChip(context, R.string.edit_mode_done, view -> leave(), true);
        strip.addView(done);
        strip.bind(mCount, java.util.Arrays.asList(mUndo, mRevert, done), dp(STRIP_COUNT_CAP_DP));
        return strip;
    }

    // ---------------------------------------------------------------- state

    private void endDrag(@NonNull RowViews row) {
        if (!row.dragging) return;
        row.dragging = false;
        if (mSession != null) mSession.dragEnded(row.setting);
        if (row.setting.changesGlassLegibility() && mMeterDrags > 0 && --mMeterDrags == 0) {
            // At lift the reading is taken once more: amber stays, readable goes.
            mMain.removeCallbacks(mMeterMeasure);
            mMeterPending = false;
            mMeterHeld = true;
            showMeter();
        }
    }

    /** Hides the meter, held or not. */
    private void hideMeter() {
        mMain.removeCallbacks(mMeterMeasure);
        mMeterPending = false;
        mMeterHeld = false;
        if (mMeter != null) setVisible(mMeter, false);
    }

    // ---------------------------------------------------------------- the legibility meter

    @NonNull
    private LinearLayout buildMeter(@NonNull Context context) {
        LinearLayout meter = new LinearLayout(context);
        meter.setOrientation(LinearLayout.HORIZONTAL);
        meter.setGravity(Gravity.CENTER_VERTICAL);
        meter.setPadding(dp(16), dp(4), dp(4), dp(4));
        meter.setBackground(panel(context, dp(TOUCH_DP)));
        // A tap on the meter stays on the meter.
        meter.setClickable(true);
        mMeterText = new TextView(context);
        mMeterText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        meter.addView(mMeterText, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mMeterDim = action(context, R.string.edit_mode_dim_wallpaper, view -> {
            EditSession session = mSession;
            if (session == null || mMeterDimTo < 0) return;
            // One Undo step of its own; the meter reads again after it.
            session.set(EditSession.Setting.DIM, mMeterDimTo);
            mKeepMeter = true;
            try {
                refresh();
            } finally {
                mKeepMeter = false;
            }
            scheduleMeter();
        });
        meter.addView(mMeterDim);
        return meter;
    }

    /** Asks for a reading soon: at most one per {@link #METER_INTERVAL_MS}, however fast the value moves. */
    private void scheduleMeter() {
        if (mMeterPending) return;
        mMeterPending = true;
        mMain.postDelayed(mMeterMeasure, METER_INTERVAL_MS);
    }

    private final Runnable mMeterMeasure = () -> {
        mMeterPending = false;
        showMeter();
    };

    private void showMeter() {
        if (!isOpen() || mMeter == null || mMeterText == null || mMeterDim == null) return;
        if (mMeterDrags <= 0 && !mMeterHeld) return;
        LegibilityMeter.State state = LegibilityMeter.of(mHost.legibility());
        if (!LegibilityMeter.shows(state, mMeterDrags > 0)) {
            hideMeter();
            return;
        }
        Context context = mHost.context();
        String ratio = String.format(java.util.Locale.getDefault(), "%.1f", state.ratio);
        mMeterText.setText(context.getString(
            state.readable ? R.string.edit_mode_legible_ok : R.string.edit_mode_legible_poor, ratio));
        mMeterText.setTextColor(state.readable
            ? MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface,
                0xFFFFFFFF)
            : AMBER);
        mMeterDimTo = state.dimTo;
        setVisible(mMeterDim, state.offersDim());
        setVisible(mMeter, true);
        place();
    }

    private static final int AMBER = 0xFFFFB300;

    /** A setting changed from outside (launcherctl, terl.conf, Settings): the open card shows it. */
    public void refreshFromOutside() {
        refresh();
    }

    /** Shows the store and the session as they are now. */
    private void refresh() {
        EditSession session = mSession;
        if (!isOpen() || session == null) return;
        if (mMeterHeld && !mKeepMeter && mMeterDrags == 0) hideMeter();
        Context context = mHost.context();
        TerlLookStore looks = mHost.looks();
        int accent = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary,
            0xFF8AB4F8);
        for (RowViews row : mRows) {
            int value = row.reader.read();
            int progress = (value - row.min) / row.step;
            if (!row.dragging && row.slider.getProgress() != progress) {
                row.slider.setProgress(progress);
            }
            setText(row.value, context.getString(row.valueText, value));
            if (row.marker == null || row.markerTarget == null || row.surface == null
                || row.property == null) continue;
            boolean own = looks.holdsOwn(row.surface, row.property);
            if (row.shownOwn != null && row.shownOwn == own) continue;
            row.shownOwn = own;
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            // A filled dot on an own value (a tap follows Global); a hollow ring while following.
            if (own) dot.setColor(accent);
            else dot.setStroke(dp(2), accent);
            row.marker.setBackground(dot);
            row.markerTarget.setClickable(own);
            String says = context.getString(own ? R.string.edit_mode_follow_global
                : R.string.edit_mode_follows_global);
            row.markerTarget.setContentDescription(says);
            androidx.appcompat.widget.TooltipCompat.setTooltipText(row.markerTarget, says);
        }
        refreshGlobal(context, looks);
        refreshWallpaper(accent);
        refreshHome(accent);
        refreshBar(accent);
        refreshWindows(accent);
        refreshKeyboard();
        int changes = session.changeCount();
        int kept = session.keptElsewhereCount();
        String count = changes == 0 ? context.getString(R.string.edit_mode_no_changes)
            : context.getResources().getQuantityString(R.plurals.edit_mode_changes, changes, changes);
        if (kept > 0) {
            count = count + " · " + context.getResources().getQuantityString(
                R.plurals.edit_mode_kept_elsewhere, kept, kept);
        }
        if (mCount != null) setText(mCount, count);
        if (mUndo != null) setEnabled(mUndo, session.canUndo());
        if (mRevert != null) setEnabled(mRevert, changes > 0);
    }

    /**
     * The live labels for the scene as it stands (round 5): measured here, in the layout pass and
     * on a card opening or closing, and drawn only when they changed; nothing per frame.
     */
    private void syncOutlines(@NonNull FrameLayout overlay, @Nullable View keyboard) {
        EditOutlinesView view = mOutlines;
        EditOutlines.Labels labels = mOutlineLabels;
        if (view == null || labels == null || overlay.getWidth() <= 0 || overlay.getHeight() <= 0) return;
        int[] origin = mOutlineOrigin;
        overlay.getLocationOnScreen(origin);
        EditOutlines.Box screen = new EditOutlines.Box(0, 0, overlay.getWidth(), overlay.getHeight());
        List<EditOutlines.Window> windows = mOutlineWindows;
        windows.clear();
        EditOutlines.Box home = null;
        if (mHost instanceof OutlineHost) {
            OutlineHost host = (OutlineHost) mHost;
            for (EditOutlines.Window window : host.windowsOnScreen()) {
                windows.add(new EditOutlines.Window(shifted(window.box, origin), window.radiusPx, window.name));
            }
            EditOutlines.Box onScreen = host.homeOnScreen();
            if (onScreen != null) home = shifted(onScreen, origin);
        }
        View bar = mHost instanceof BarHost ? ((BarHost) mHost).barView() : null;
        EditOutlines.Box barBox = bar != null && bar.isShown() ? boxOf(bar, origin, mOutlineAt) : null;
        EditOutlines.Box keyboardBox = keyboard != null ? boxOf(keyboard, origin, mOutlineAt) : null;
        EditOutlines.Scene scene = new EditOutlines.Scene(screen, home, windows,
            barBox, barBox == null ? 0f : Math.min(dp(BAR_OUTLINE_RADIUS_DP), barBox.height() / 2f),
            keyboardBox, keyboardBox == null ? 0f
                : Math.min(dp(KEYBOARD_OUTLINE_RADIUS_DP), keyboardBox.height() / 2f),
            mHost.context().getResources().getDisplayMetrics().density);
        view.setOutlines(EditOutlines.of(scene, labels, mOpenCard));
    }

    /** {@code view}'s rect as it stands (its translation in), in the overlay at {@code origin}. */
    @NonNull
    private static EditOutlines.Box boxOf(@NonNull View view, @NonNull int[] origin, @NonNull int[] at) {
        view.getLocationOnScreen(at);
        return new EditOutlines.Box(at[0] - origin[0], at[1] - origin[1],
            at[0] - origin[0] + view.getWidth(), at[1] - origin[1] + view.getHeight());
    }

    @NonNull
    private static EditOutlines.Box shifted(@NonNull EditOutlines.Box box, @NonNull int[] origin) {
        return new EditOutlines.Box(box.left - origin[0], box.top - origin[1], box.right - origin[0],
            box.bottom - origin[1]);
    }

    private static int accentOf(@NonNull Context context) {
        return MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0xFFA9C1FF);
    }

    /** Puts the outline on the keyboard and the card above it; moves a view only when it changed. */
    private void place() {
        FrameLayout overlay = mOverlay;
        if (overlay == null || mOutline == null) return;
        View keyboard = mHost.isKeyboardShown() ? mHost.keyboardView() : null;
        int top = EditModePlacement.OFF_SCREEN;
        if (keyboard != null && keyboard.isShown() && keyboard.getWidth() > 0
            && keyboard.getHeight() > 0) {
            int[] at = new int[2];
            int[] origin = new int[2];
            keyboard.getLocationOnScreen(at);
            overlay.getLocationOnScreen(origin);
            int left = at[0] - origin[0];
            top = at[1] - origin[1];
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mOutline.getLayoutParams();
            if (params.leftMargin != left || params.topMargin != top
                || params.width != keyboard.getWidth() || params.height != keyboard.getHeight()) {
                params.leftMargin = left;
                params.topMargin = top;
                params.width = keyboard.getWidth();
                params.height = keyboard.getHeight();
                mOutline.setLayoutParams(params);
            }
        }
        setVisible(mOutline, top != EditModePlacement.OFF_SCREEN);
        syncOutlines(overlay, keyboard != null && top != EditModePlacement.OFF_SCREEN ? keyboard : null);
        // Every card's chip stands, the Keyboard's with the keyboard up too (its outline is a door,
        // not the only one).
        if (mKeyboardChip != null) setVisible(mKeyboardChip, EditStripChips.shown(Card.KEYBOARD, true));
        if (mGlobalChip != null) setVisible(mGlobalChip, EditStripChips.shown(Card.GLOBAL, true));
        if (mWallpaperChip != null) setVisible(mWallpaperChip, EditStripChips.shown(Card.WALLPAPER, true));
        if (mHomeChip != null) setVisible(mHomeChip, EditStripChips.shown(Card.HOME, true));
        if (mBarChip != null) setVisible(mBarChip, EditStripChips.shown(Card.BAR, barSettings() != null));
        if (mWindowsChip != null) {
            setVisible(mWindowsChip, EditStripChips.shown(Card.WINDOWS, windowSettings() != null));
        }
        litChips();
        int barTop = placeBarAboveStrip(overlay);
        if (mOpenCard != null && mStrip != null && overlay.getHeight() > 0) {
            int contentBottom = overlay.getHeight() - overlay.getPaddingBottom();
            ViewGroup.MarginLayoutParams stripParams =
                (ViewGroup.MarginLayoutParams) mStrip.getLayoutParams();
            // Every card stands above the bar, which stands above the strip (with the keyboard up,
            // on the keyboard): the bar and Home's A-Z row it carries stay in sight and in reach,
            // and the card is never over them (decided 2026-11-28).
            int margin = EditModePlacement.cardBottomMarginPx(contentBottom,
                EditModePlacement.highest(top, barTop),
                mStrip.getHeight() + stripParams.bottomMargin, dp(GAP_DP));
            View card = cardView(mOpenCard);
            FrameLayout.LayoutParams cardParams = (FrameLayout.LayoutParams) card.getLayoutParams();
            if (cardParams.bottomMargin != margin) {
                cardParams.bottomMargin = margin;
                card.setLayoutParams(cardParams);
            }
            if (mMeter != null && mMeter.getVisibility() == View.VISIBLE) {
                // Above the card, so it never moves a slider under the finger.
                int above = margin + card.getHeight() + dp(GAP_DP / 2);
                FrameLayout.LayoutParams meterParams =
                    (FrameLayout.LayoutParams) mMeter.getLayoutParams();
                if (meterParams.bottomMargin != above) {
                    meterParams.bottomMargin = above;
                    mMeter.setLayoutParams(meterParams);
                }
            }
        }
    }

    /** How far the bar stands lifted above its strip now ({@link EditModePlacement#barLiftPx}). */
    private int mBarLift;

    /**
     * The strip keeps the very bottom whatever card is open (round 3: it jumped up for the bar's
     * card); a bar along the bottom is lifted to stand just above it, so it stays in sight and in
     * reach, as the prototype has it. Beside a rail (taller than wide) the strip and the bar's card
     * keep to the rail's inner side instead, so the rail stays whole in sight. Returns the bar's
     * top in the overlay as it now stands, or {@link EditModePlacement#OFF_SCREEN}.
     */
    private int placeBarAboveStrip(@NonNull FrameLayout overlay) {
        if (mStrip == null) return EditModePlacement.OFF_SCREEN;
        int lift = 0;
        int leftInset = 0;
        int rightInset = 0;
        int barTop = EditModePlacement.OFF_SCREEN;
        View bar = mHost instanceof BarHost ? ((BarHost) mHost).barView() : null;
        if (bar != null && bar.isShown() && overlay.getHeight() > 0 && bar.getWidth() > 0
                && mStrip.getHeight() > 0) {
            int[] at = new int[2];
            int[] origin = new int[2];
            bar.getLocationOnScreen(at);
            overlay.getLocationOnScreen(origin);
            if (bar.getWidth() > bar.getHeight()) {
                int contentBottom = overlay.getHeight() - overlay.getPaddingBottom();
                ViewGroup.MarginLayoutParams stripParams = (ViewGroup.MarginLayoutParams) mStrip.getLayoutParams();
                int stripTop = contentBottom - stripParams.bottomMargin - mStrip.getHeight();
                // Where the bar is laid out: its settled bottom, without the keyboard's ride, the
                // full-screen raise or this lift, all of which its translation holds (review:
                // read mid-ride the lift was wrong until the next layout).
                int barBottom = at[1] - origin[1] - Math.round(bar.getTranslationY()) + bar.getHeight();
                lift = EditModePlacement.barLiftPx(barBottom, stripTop, dp(GAP_DP / 2));
                // Where the bar is drawn once lifted: its ride on the keyboard counted, so a card
                // stands above a bar the keyboard carries up as well.
                barTop = at[1] - origin[1] - Math.round(bar.getTranslationY())
                    + Math.round(bar.getTranslationY() + mBarLift - lift);
            } else if (mOpenCard != null) {
                int barLeft = at[0] - origin[0];
                int barRight = barLeft + bar.getWidth();
                if (barLeft + bar.getWidth() / 2 < overlay.getWidth() / 2) {
                    leftInset = Math.max(0, barRight - overlay.getPaddingLeft());
                } else {
                    rightInset = Math.max(0, overlay.getWidth() - overlay.getPaddingRight() - barLeft);
                }
            }
        }
        setInsets(mStrip, dp(GAP_DP / 2), leftInset, rightInset);
        // Every card keeps to the rail's inner side, and clear of Home's A–Z letters standing as a
        // column on the left or right, so both stay whole in sight (decided 2026-11-28).
        int[] az = azColumnInsets(overlay);
        int cardLeft = Math.max(leftInset, az[0]);
        int cardRight = Math.max(rightInset, az[1]);
        for (View card : new View[] {mCard, mGlobalCard, mWallpaperCard, mHomeCard, mBarCard, mWindowsCard}) {
            if (card != null) setInsets(card, -1, cardLeft, cardRight);
        }
        if (lift != mBarLift && mHost instanceof BarHost) {
            mBarLift = lift;
            ((BarHost) mHost).liftBar(lift);
        }
        return barTop;
    }

    /**
     * How far the cards keep in from the left and right for Home's A–Z letters when they stand as
     * a column (taller than wide) on that side: {left, right}, overlay px; none for a row.
     */
    @NonNull
    private int[] azColumnInsets(@NonNull FrameLayout overlay) {
        int[] insets = {0, 0};
        View letters = mHost instanceof BarHost ? ((BarHost) mHost).azLetters() : null;
        if (letters == null || !letters.isShown() || letters.getWidth() <= 0
            || letters.getHeight() <= letters.getWidth() || overlay.getWidth() <= 0) return insets;
        int[] at = new int[2];
        int[] origin = new int[2];
        letters.getLocationOnScreen(at);
        overlay.getLocationOnScreen(origin);
        int left = at[0] - origin[0];
        int right = left + letters.getWidth();
        if (left + letters.getWidth() / 2 < overlay.getWidth() / 2) {
            insets[0] = Math.max(0, right - overlay.getPaddingLeft());
        } else {
            insets[1] = Math.max(0, overlay.getWidth() - overlay.getPaddingRight() - left);
        }
        return insets;
    }

    /** Sets a view's side margins to the gap plus the insets, and its bottom margin when {@code bottom} >= 0. */
    private void setInsets(@NonNull View view, int bottom, int leftInset, int rightInset) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        int left = dp(GAP_DP) + leftInset;
        int right = dp(GAP_DP) + rightInset;
        if (params.leftMargin == left && params.rightMargin == right
            && (bottom < 0 || params.bottomMargin == bottom)) return;
        params.leftMargin = left;
        params.rightMargin = right;
        if (bottom >= 0) params.bottomMargin = bottom;
        view.setLayoutParams(params);
    }

    // ---------------------------------------------------------------- the Control bar card

    /**
     * The Control bar card: Opacity and Blur (the bar's own, with Follow Global), Margin (0 stands
     * the bar flush with square bottom corners), the reading slot, the rail's side in landscape, and
     * the tools each place holds: tap to switch one on or off, hold and drag to reorder.
     */
    @NonNull
    private View buildBarCard(@NonNull Context context) {
        ScrollView scroll = scrollPanel(context);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad / 2);
        card.addView(text(context, R.string.edit_mode_card_control_bar, 18,
            com.google.android.material.R.attr.colorOnSurface));
        scroll.addView(card);
        EditSession.Bar bar = barSettings();
        if (bar == null) return scroll;

        mRows.add(addOwnRow(context, card, TerlSurface.CONTROL_BAR, TerlProperty.OPACITY,
            R.string.edit_mode_opacity));
        mRows.add(addOwnRow(context, card, TerlSurface.CONTROL_BAR, TerlProperty.BLUR,
            R.string.edit_mode_blur));
        mRows.add(addSliderRow(context, card, EditSession.Setting.BAR_MARGIN, null, null,
            EditSession.Bar.MIN_MARGIN, EditSession.Bar.MAX_MARGIN, 1, R.string.edit_mode_dp,
            R.string.edit_mode_margin, bar::marginDp));
        mBarReadingSwitch = addSwitchRow(context, card, R.string.edit_mode_reading, on -> {
            if (mSession == null) return;
            mSession.setBarReading(on);
            refresh();
        });

        card.addView(rowLabel(context, R.string.edit_mode_rail_side));
        LinearLayout rail = new LinearLayout(context);
        rail.setOrientation(LinearLayout.HORIZONTAL);
        String[] sides = {EditSession.Bar.RAIL_RIGHT, EditSession.Bar.RAIL_LEFT};
        int[] sideNames = {R.string.edit_mode_rail_right, R.string.edit_mode_rail_left};
        for (int i = 0; i < sides.length; i++) {
            String side = sides[i];
            mRailChips.add(new Chip(side, addChip(context, rail, context.getString(sideNames[i]),
                view -> {
                    if (mSession == null) return;
                    mSession.setBarRailSide(side);
                    refresh();
                })));
        }
        card.addView(rail);

        String[] places = {EditSession.Bar.PLACE_HOME, EditSession.Bar.PLACE_TERMINAL,
            EditSession.Bar.PLACE_LINUX};
        int[] placeNames = {R.string.edit_mode_tools_home, R.string.edit_mode_tools_terminal,
            R.string.edit_mode_tools_linux};
        for (int i = 0; i < places.length; i++) {
            String place = places[i];
            card.addView(rowLabel(context, placeNames[i]));
            ToolRow row = new ToolRow(context, toolStyle(), on -> {
                if (mSession == null) return;
                mSession.setBarTools(place, on.isEmpty() ? "-" : android.text.TextUtils.join(",", on));
                refresh();
            });
            mToolRows.put(place, row);
            card.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return scroll;
    }

    @NonNull
    private ToolRow.Style toolStyle() {
        return new ToolRow.Style() {
            @NonNull @Override public TextView chip(@NonNull Context context, @NonNull CharSequence label) {
                TextView chip = new TextView(context);
                chip.setText(label);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                chip.setTextColor(MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF));
                chip.setSingleLine(true);
                chip.setGravity(Gravity.CENTER);
                chip.setMinHeight(dp(TOUCH_DP));
                chip.setPadding(dp(14), 0, dp(14), 0);
                return chip;
            }

            @Override public void paint(@NonNull TextView chip, boolean on) {
                int accent = MaterialColors.getColor(chip.getContext(),
                    androidx.appcompat.R.attr.colorPrimary, 0xFF8AB4F8);
                chip.setSelected(false);
                chip.setBackground(null);
                showChip(chip, on, accent);
                chip.setAlpha(on ? 1f : 0.6f);
            }

            @NonNull @Override public CharSequence label(@NonNull String tool) {
                return mHost.context().getString(toolName(tool));
            }

            @NonNull @Override public CharSequence spokenState(@NonNull String tool, boolean on) {
                return mHost.context().getString(on ? R.string.edit_mode_tool_on
                    : R.string.edit_mode_tool_off, label(tool));
            }

            @NonNull @Override public CharSequence earlier() {
                return mHost.context().getString(R.string.edit_mode_tool_earlier);
            }

            @NonNull @Override public CharSequence later() {
                return mHost.context().getString(R.string.edit_mode_tool_later);
            }
        };
    }

    private static int toolName(@NonNull String tool) {
        switch (tool) {
            case "drawer": return R.string.edit_mode_tool_drawer;
            case "pinned_apps": return R.string.edit_mode_tool_pinned_apps;
            case "keyboard": return R.string.edit_mode_tool_keyboard;
            case "split": return R.string.edit_mode_tool_split;
            case "new_terminal": return R.string.edit_mode_tool_new_terminal;
            case "window_menu": return R.string.edit_mode_tool_window_menu;
            default: return R.string.edit_mode_tool_mouse_mode;
        }
    }

    /** The Control bar card as the settings hold it: the reading switch, the rail's side, the tools. */
    private void refreshBar(int accent) {
        EditSession.Bar bar = barSettings();
        if (bar == null || mOpenCard != Card.BAR) return;
        mSettingSwitch = true;
        try {
            if (mBarReadingSwitch != null && mBarReadingSwitch.isChecked() != bar.reading()) {
                mBarReadingSwitch.setChecked(bar.reading());
            }
        } finally {
            mSettingSwitch = false;
        }
        for (Chip chip : mRailChips) showChip(chip.view, chip.value.equals(bar.railSide()), accent);
        for (java.util.Map.Entry<String, ToolRow> entry : mToolRows.entrySet()) {
            String stored = bar.tools(entry.getKey());
            List<String> on = new ArrayList<>();
            if (!"-".equals(stored) && !stored.isEmpty()) {
                for (String tool : stored.split(",")) if (!tool.isEmpty()) on.add(tool);
            }
            entry.getValue().show(bar.toolOptions(entry.getKey()), on);
        }
    }

    // ---------------------------------------------------------------- the Windows card

    /**
     * The Windows card (#451), one card for every window kind. Window tab: Opacity and Blur (the
     * windows' own, with Follow Global), Gap, Rim colour and Unfocused dim, each for every window at
     * once. Text tab: the terminals' text size, font, contrast, cursor trail and screen effect,
     * written where Settings writes them, so every terminal wears them as the card changes.
     */
    @NonNull
    private View buildWindowsCard(@NonNull Context context) {
        ScrollView scroll = scrollPanel(context);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad / 2);
        card.addView(text(context, R.string.edit_mode_card_windows, 18,
            com.google.android.material.R.attr.colorOnSurface));
        scroll.addView(card);
        EditSession.Keyed keyed = windowSettings();
        if (keyed == null) return scroll;

        LinearLayout tabs = new LinearLayout(context);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        mWindowTabChip = addChip(context, tabs, context.getString(R.string.edit_mode_tab_window),
            view -> showWindowsTab(false));
        mTextTabChip = addChip(context, tabs, context.getString(R.string.edit_mode_tab_text),
            view -> showWindowsTab(true));
        card.addView(tabs);

        LinearLayout window = new LinearLayout(context);
        window.setOrientation(LinearLayout.VERTICAL);
        mRows.add(addOwnRow(context, window, TerlSurface.WINDOWS, TerlProperty.OPACITY,
            R.string.edit_mode_opacity));
        mRows.add(addOwnRow(context, window, TerlSurface.WINDOWS, TerlProperty.BLUR,
            R.string.edit_mode_blur));
        mRows.add(addSliderRow(context, window, EditSession.Setting.keyedNumber(EditSession.Keyed.GAP),
            null, null, 0, 24, 1, R.string.edit_mode_dp, R.string.edit_mode_gap,
            () -> keyedNumber(keyed, EditSession.Keyed.GAP)));
        keyedSwitch(context, window, R.string.edit_mode_rim, EditSession.Keyed.RIM, false);
        window.addView(rowLabel(context, R.string.edit_mode_rim_colour));
        window.addView(scrolling(context, choiceRow(context, mRimChips, EditSession.Keyed.RIM_COLOUR,
            context.getResources().getStringArray(R.array.edit_mode_rim_colour_values),
            context.getResources().getStringArray(R.array.edit_mode_rim_colour_entries))));
        mRows.add(addSliderRow(context, window,
            EditSession.Setting.keyedNumber(EditSession.Keyed.UNFOCUSED_DIM), null, null, 0, 60, 5,
            R.string.edit_mode_percent, R.string.edit_mode_unfocused_dim,
            () -> keyedNumber(keyed, EditSession.Keyed.UNFOCUSED_DIM)));
        card.addView(window);
        mWindowTab = window;

        LinearLayout textTab = new LinearLayout(context);
        textTab.setOrientation(LinearLayout.VERTICAL);
        EditSession.Launcher launcher = mHost.launcher();
        if (launcher != null) {
            mRows.add(addSliderRow(context, textTab, EditSession.Setting.TEXT_SIZE, null, null,
                com.termux.shared.termux.settings.registry.SettingDeclarations.TEXT_SIZE.min,
                com.termux.shared.termux.settings.registry.SettingDeclarations.TEXT_SIZE.max,
                com.termux.shared.termux.settings.registry.SettingDeclarations.TEXT_SIZE.step,
                R.string.edit_mode_percent, R.string.edit_mode_text_size,
                launcher::textSizePercent));
        }
        int[] fontSizes = mHost instanceof WindowsHost
            ? ((WindowsHost) mHost).fontSizeRangeDp()
            : new int[] {WindowsHost.FONT_SIZE_MIN_DP, WindowsHost.FONT_SIZE_MAX_DP};
        mRows.add(addSliderRow(context, textTab,
            EditSession.Setting.keyedNumber(EditSession.Keyed.FONT_SIZE), null, null, fontSizes[0],
            Math.max(fontSizes[0], fontSizes[1]), 1, R.string.edit_mode_dp,
            R.string.edit_mode_font_size, () -> keyedNumber(keyed, EditSession.Keyed.FONT_SIZE)));
        textTab.addView(rowLabel(context, R.string.edit_mode_font));
        List<String[]> fonts = mHost instanceof WindowsHost
            ? ((WindowsHost) mHost).installedFonts() : new ArrayList<>();
        String[] fontIds = new String[fonts.size()];
        String[] fontNames = new String[fonts.size()];
        for (int i = 0; i < fonts.size(); i++) {
            fontIds[i] = fonts.get(i)[0];
            fontNames[i] = fonts.get(i)[1];
        }
        LinearLayout fontRow = choiceRow(context, mFontChips, EditSession.Keyed.FONT, fontIds, fontNames);
        addChip(context, fontRow, context.getString(R.string.edit_mode_font_more), view -> {
            if (mHost instanceof WindowsHost) ((WindowsHost) mHost).openFonts();
        });
        textTab.addView(scrolling(context, fontRow));
        textTab.addView(rowLabel(context, R.string.edit_mode_colour_scheme));
        textTab.addView(scrolling(context, schemeRow(context)));
        textTab.addView(rowLabel(context, R.string.edit_mode_contrast));
        textTab.addView(choiceRow(context, mContrastChips, EditSession.Keyed.CONTRAST,
            new String[] {"softer", "default", "harder"}, new String[] {
                context.getString(R.string.appearance_contrast_softer),
                context.getString(R.string.appearance_contrast_default),
                context.getString(R.string.appearance_contrast_harder)}));
        textTab.addView(rowLabel(context, R.string.edit_mode_cursor));
        textTab.addView(choiceRow(context, mCursorChips, EditSession.Keyed.CURSOR_STYLE,
            new String[] {"block", "bar", "underline"}, new String[] {
                context.getString(R.string.edit_mode_cursor_block),
                context.getString(R.string.edit_mode_cursor_bar),
                context.getString(R.string.edit_mode_cursor_underline)}));
        keyedSwitch(context, textTab, R.string.edit_mode_cursor_blink, EditSession.Keyed.CURSOR_BLINK,
            true);
        textTab.addView(rowLabel(context, R.string.edit_mode_cursor_trail));
        textTab.addView(scrolling(context, choiceRow(context, mTrailChips,
            EditSession.Keyed.CURSOR_TRAIL,
            context.getResources().getStringArray(R.array.appearance_editor_cursor_trail_values),
            context.getResources().getStringArray(R.array.appearance_editor_cursor_trail_entries))));
        textTab.addView(rowLabel(context, R.string.edit_mode_effect));
        textTab.addView(scrolling(context, choiceRow(context, mEffectChips, EditSession.Keyed.EFFECT,
            context.getResources().getStringArray(R.array.settings_terminal_retro_effect_values),
            context.getResources().getStringArray(R.array.settings_terminal_retro_effect_entries))));
        textTab.addView(effectsHeader(context));
        LinearLayout effects = new LinearLayout(context);
        effects.setOrientation(LinearLayout.VERTICAL);
        keyedSwitch(context, effects, R.string.edit_mode_screen_events, EditSession.Keyed.SCREEN_EVENTS,
            false);
        keyedSwitch(context, effects, R.string.edit_mode_composite, EditSession.Keyed.COMPOSITE, false);
        keyedSwitch(context, effects, R.string.edit_mode_idle_animations,
            EditSession.Keyed.IDLE_ANIMATIONS, false);
        effects.addView(rowLabel(context, R.string.edit_mode_glitch_rate));
        effects.addView(choiceRow(context, mTearChips, EditSession.Keyed.IDLE_TEAR_RATE,
            new String[] {"rare", "sometimes", "often"}, new String[] {
                context.getString(R.string.edit_mode_glitch_rare),
                context.getString(R.string.edit_mode_glitch_sometimes),
                context.getString(R.string.edit_mode_glitch_often)}));
        keyedSwitch(context, effects, R.string.edit_mode_adaptive_legibility,
            EditSession.Keyed.ADAPTIVE_LEGIBILITY, false);
        textTab.addView(effects);
        mEffectsBox = effects;
        setVisible(effects, false);
        card.addView(textTab);
        mTextTab = textTab;
        setVisible(textTab, false);
        return scroll;
    }

    private static int keyedNumber(@NonNull EditSession.Keyed keyed, @NonNull String key) {
        Object value = keyed.get(key);
        return value instanceof Integer ? (Integer) value : 0;
    }

    /**
     * The colour scheme's chips: Wallpaper colours, the user's own colours when there are any to
     * go back to, then the built-in schemes by name.
     */
    @NonNull
    private LinearLayout schemeRow(@NonNull Context context) {
        List<String> values = new ArrayList<>();
        List<String> names = new ArrayList<>();
        values.add(com.termux.app.colours.TerminalColours.WALLPAPER);
        names.add(context.getString(R.string.edit_mode_scheme_wallpaper));
        if (mHost instanceof WindowsHost && ((WindowsHost) mHost).hasOwnColours()) {
            values.add(com.termux.app.colours.TerminalColours.OWN);
            names.add(context.getString(R.string.edit_mode_scheme_own));
        }
        for (com.termux.app.colours.TerminalSchemes.Scheme scheme
                : com.termux.app.colours.TerminalSchemes.all()) {
            values.add(scheme.id);
            names.add(scheme.name);
        }
        return choiceRow(context, mSchemeChips, EditSession.Keyed.SCHEME,
            values.toArray(new String[0]), names.toArray(new String[0]));
    }

    /** The Effects header: a tap shows or folds away the CRT extras and Adaptive legibility. */
    @NonNull
    private View effectsHeader(@NonNull Context context) {
        TextView header = text(context, R.string.edit_mode_effects, 15,
            com.google.android.material.R.attr.colorOnSurface);
        header.setPadding(0, dp(12), 0, dp(4));
        header.setMinHeight(dp(TOUCH_DP));
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundResource(selectableBorderless(context));
        header.setOnClickListener(view -> {
            mEffectsShown = !mEffectsShown;
            showEffects();
            place();
        });
        mEffectsHeader = header;
        showEffects();
        return header;
    }

    /** The Effects box and its header's arrow and spoken state as {@link #mEffectsShown} says. */
    private void showEffects() {
        if (mEffectsBox != null) setVisible(mEffectsBox, mEffectsShown);
        TextView header = mEffectsHeader;
        if (header == null) return;
        Context context = header.getContext();
        setText(header, context.getString(R.string.edit_mode_effects)
            + (mEffectsShown ? "  \u25BE" : "  \u25B8"));
        header.setContentDescription(context.getString(mEffectsShown
            ? R.string.edit_mode_effects_hide : R.string.edit_mode_effects_show));
    }

    /** A row of chips, each writing one value of the {@link EditSession.Keyed} choice {@code key}. */
    @NonNull
    private LinearLayout choiceRow(@NonNull Context context, @NonNull List<Chip> chips,
                                   @NonNull String key, @NonNull String[] values,
                                   @NonNull String[] names) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            TextView chip = addChip(context, row, i < names.length ? names[i] : value, view -> {
                if (mSession == null) return;
                mSession.setKeyedChoice(key, value);
                refresh();
            });
            chips.add(new Chip(key + "=" + value, chip));
        }
        return row;
    }

    @NonNull
    private HorizontalScrollView scrolling(@NonNull Context context, @NonNull View row) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(row);
        return scroll;
    }

    private void showWindowsTab(boolean text) {
        mTextTabShown = text;
        refresh();
        place();
    }

    private void refreshWindows(int accent) {
        EditSession.Keyed keyed = windowSettings();
        if (keyed == null || mOpenCard != Card.WINDOWS) return;
        if (mWindowTab != null) setVisible(mWindowTab, !mTextTabShown);
        if (mTextTab != null) setVisible(mTextTab, mTextTabShown);
        if (mWindowTabChip != null) showChip(mWindowTabChip, !mTextTabShown, accent);
        if (mTextTabChip != null) showChip(mTextTabChip, mTextTabShown, accent);
        for (Chip chip : mRimChips) showKeyedChip(chip, keyed, accent);
        for (Chip chip : mContrastChips) showKeyedChip(chip, keyed, accent);
        for (Chip chip : mTrailChips) showKeyedChip(chip, keyed, accent);
        for (Chip chip : mEffectChips) showKeyedChip(chip, keyed, accent);
        for (Chip chip : mFontChips) showKeyedChip(chip, keyed, accent);
        for (Chip chip : mSchemeChips) showKeyedChip(chip, keyed, accent);
        for (Chip chip : mCursorChips) showKeyedChip(chip, keyed, accent);
        for (Chip chip : mTearChips) showKeyedChip(chip, keyed, accent);
        refreshKeyedSwitches(keyed);
    }

    private void showKeyedChip(@NonNull Chip chip, @NonNull EditSession.Keyed keyed, int accent) {
        int cut = chip.value.indexOf('=');
        Object now = keyed.get(chip.value.substring(0, cut));
        String mine = chip.value.substring(cut + 1);
        // A trail that is off reads "off:style": the off chip is the one lit.
        boolean on = mine.equals(now)
            || (now instanceof String && ((String) now).startsWith(mine + ":"));
        showChip(chip.view, on, accent);
    }

    // ---------------------------------------------------------------- the Global card

    /** A Look's tile was tapped: it is worn at once, or the card asks first. */
    private void onLookPicked(@NonNull String lookId) {
        EditSession session = mSession;
        if (session == null || mOpenCard != Card.GLOBAL) return;
        if (lookId.equals(mHost.looks().look())) {
            dismissAsk(false);
            return;
        }
        TerlLookStore.Pick pick = session.pick(lookId, false);
        if (pick.applied) {
            dismissAsk(false);
            refresh();
            return;
        }
        if (pick.clears.isEmpty() || mAsk == null || mAskText == null) return;
        mAskedLook = lookId;
        Context context = mHost.context();
        setText(mAskText, context.getString(R.string.edit_mode_pick_ask,
            lookName(context, lookId), surfacesOf(context, pick.clears)));
        setVisible(mAsk, true);
    }

    /** Takes the ask down; {@code backToWorn} shows the Look worn again. */
    private void dismissAsk(boolean backToWorn) {
        if (mAskedLook == null) return;
        mAskedLook = null;
        if (mAsk != null) setVisible(mAsk, false);
        if (backToWorn) refresh();
    }

    /** The Wallpaper and colours card as the settings hold it: the switches and the theme chosen. */
    private void refreshWallpaper(int accent) {
        EditSession.Wallpaper wallpaper = mHost.wallpaper();
        if (wallpaper == null || mOpenCard != Card.WALLPAPER) return;
        mSettingSwitch = true;
        try {
            if (mDriftSwitch != null && mDriftSwitch.isChecked() != wallpaper.drift()) {
                mDriftSwitch.setChecked(wallpaper.drift());
            }
            if (mColoursSwitch != null
                && mColoursSwitch.isChecked() != wallpaper.coloursFromWallpaper()) {
                mColoursSwitch.setChecked(wallpaper.coloursFromWallpaper());
            }
        } finally {
            mSettingSwitch = false;
        }
        for (Chip chip : mThemeChips) showChip(chip.view, chip.value.equals(wallpaper.theme()), accent);
    }

    /** The Global card as the store holds it: the worn Look ringed, Custom's rows, the chips. */
    private void refreshGlobal(@NonNull Context context, @NonNull TerlLookStore looks) {
        if (mGlobalCard == null || mOpenCard != Card.GLOBAL) return;
        String worn = looks.look();
        if (mCustomRows != null) setVisible(mCustomRows, TerlLookStore.CUSTOM.equals(worn));
        int accent = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary,
            0xFF8AB4F8);
        int ink = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface,
            0xFFFFFFFF);
        int pane = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface,
            0xFF1B1B1F);
        int fill = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceVariant, 0xFF44474F);
        syncTileFrames(context, looks);
        for (LookTile look : mLookTiles) {
            // Custom wears the values in force; a shipped Look its preset's.
            com.termux.app.look.TerlLookSource.Look preset = looks.preset(look.id);
            int tint = preset != null ? preset.tintPercent : 0;
            look.tile.setFill(fill);
            look.tile.setPane(androidx.core.graphics.ColorUtils.blendARGB(pane, accent, tint / 100f),
                looks.lookValue(look.id, TerlProperty.OPACITY), withAlpha(ink, 0x40));
            look.tile.setSlab(preset != null ? preset.bendDp
                    : com.termux.shared.termux.settings.preferences.GlassMaterial.bendForDepth(looks.depth()),
                preset != null ? preset.shine : looks.shine());
            look.tile.setWorn(look.id.equals(worn), accent);
            look.tile.setFrame(mTileFrames.get(look.id));
            look.cellSelected(look.id.equals(worn));
        }
        String tint = looks.tint();
        for (Chip chip : mTintChips) {
            boolean chosen = chip.value.equals(tint);
            if (TerlLookStore.TINT_AUTO.equals(chip.value)) showChip(chip.view, chosen, accent);
            else showDot(chip.view, tintColour(context, chip.value), chosen, accent);
        }
        EditSession.Launcher launcher = mHost.launcher();
        if (launcher != null) {
            String pack = launcher.iconPack();
            for (Chip chip : mIconPackChips) showChip(chip.view, chip.value.equals(pack), accent);
        }
    }

    /**
     * Each Look's own picture, made again only when the cache's least blurred frame changed (or a
     * Custom blur moved): a small crop blurred at the Look's radius, so no tile shows another's.
     */
    private void syncTileFrames(@NonNull Context context, @NonNull TerlLookStore looks) {
        android.graphics.Bitmap source = mHost instanceof LooksHost ? ((LooksHost) mHost).wallpaperFrame() : null;
        if (source == null || source.isRecycled()) {
            mTileFrames.clear();
            mTileSource = null;
            return;
        }
        if (source != mTileSource) {
            mTileFrames.clear();
            mTileBlur.clear();
            mTileSource = source;
        }
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        for (LookTile look : mLookTiles) {
            int blur = looks.lookValue(look.id, TerlProperty.BLUR);
            String key = look.id;
            android.graphics.Bitmap held = mTileFrames.get(key);
            Object madeAt = held == null ? null : mTileBlur.get(key);
            if (held != null && madeAt != null && (Integer) madeAt == blur) continue;
            mTileFrames.put(key, LookTileFrames.of(source, blur, metrics.density, metrics.widthPixels));
            mTileBlur.put(key, blur);
        }
    }

    /** The blur each Look's picture was made at. */
    @NonNull private final java.util.Map<String, Integer> mTileBlur = new java.util.HashMap<>();

    /** The icon packs, listed off the main thread once per visit; the chips come when it returns. */
    private void loadIconPacks() {
        if (mIconPackRow == null || mIconPacksAsked) return;
        mIconPacksAsked = true;
        List<IconPackChoices.Entry> listed = new ArrayList<>();
        offMain(() -> listed.addAll(mHost.iconPacks()), () -> showIconPacks(listed));
    }

    private void showIconPacks(@NonNull List<IconPackChoices.Entry> entries) {
        LinearLayout row = mIconPackRow;
        if (row == null || !mIconPackChips.isEmpty()) return;
        Context context = mHost.context();
        for (IconPackChoices.Entry entry : entries) {
            String pack = entry.value;
            TextView chip = addChip(context, row, entry.label, view -> pickIconPack(pack));
            mIconPackChips.add(new Chip(pack, chip));
        }
        refresh();
    }

    /**
     * A pack chip: the pack is parsed off the main thread first, then worn (one Undo step), so the
     * redraw that follows finds it parsed. A later tap wins over one still parsing.
     */
    private void pickIconPack(@NonNull String pack) {
        if (mSession == null) return;
        dismissAsk(true);
        mIconPackGeneration++;
        Runnable wear = () -> {
            if (mSession == null) return;
            mSession.setIconPack(pack);
            refresh();
        };
        if (pack.isEmpty()) wear.run();
        else offMain(() -> mHost.warmIconPack(pack), wear);
    }

    @NonNull
    private static String lookName(@NonNull Context context, @NonNull String lookId) {
        switch (lookId) {
            case TerlLooks.CLEAR: return context.getString(R.string.termux_surface_preset_minimal);
            case TerlLooks.FROSTED: return context.getString(R.string.termux_surface_preset_frost);
            case TerlLooks.TINTED: return context.getString(R.string.termux_surface_preset_stock);
            case TerlLooks.SOLID: return context.getString(R.string.termux_surface_preset_solid);
            case TerlLooks.LIQUID: return context.getString(R.string.termux_surface_preset_liquid);
            case TerlLookStore.CUSTOM: return context.getString(R.string.termux_surface_preset_custom);
            default: return lookId;
        }
    }

    /** "the keyboard", "the bar and the keyboard", "the bar, the keyboard and windows". */
    @NonNull
    private static String surfacesOf(@NonNull Context context,
                                     @NonNull List<TerlLookStore.OwnValue> clears) {
        List<String> names = new ArrayList<>();
        for (TerlSurface surface : TerlSurface.values()) {
            for (TerlLookStore.OwnValue value : clears) {
                if (value.surface != surface) continue;
                names.add(context.getString(surface == TerlSurface.CONTROL_BAR
                    ? R.string.edit_mode_surface_control_bar : surface == TerlSurface.KEYBOARD
                    ? R.string.edit_mode_surface_keyboard : R.string.edit_mode_surface_windows));
                break;
            }
        }
        if (names.isEmpty()) return "";
        String last = names.remove(names.size() - 1);
        if (names.isEmpty()) return last;
        return context.getString(R.string.edit_mode_and, android.text.TextUtils.join(", ", names), last);
    }

    // ---------------------------------------------------------------- small views

    @NonNull
    private TextView rowLabel(@NonNull Context context, int res) {
        TextView label = text(context, res, 15, com.google.android.material.R.attr.colorOnSurface);
        label.setPadding(0, dp(12), 0, dp(4));
        copyable(label, EditModeControls.idOfTitle(res));
        return label;
    }

    /**
     * A hold on a card's row ({@code row}: its head or label) offers Copy as config, its terl.conf
     * line, where the control has a registry id ({@link EditModeControls}) and the host can copy.
     */
    private void copyable(@NonNull View row, @Nullable String id) {
        if (id == null || !(mHost instanceof ConfigHost)) return;
        ConfigHost host = (ConfigHost) mHost;
        row.setOnLongClickListener(v -> {
            android.widget.PopupMenu menu = new android.widget.PopupMenu(v.getContext(), v);
            menu.getMenu().add(R.string.terl_settings_copy_as_config);
            menu.setOnMenuItemClickListener(item -> {
                host.copyAsConfig(id);
                return true;
            });
            menu.setOnDismissListener(m -> {
                if (mCopyMenu == m) mCopyMenu = null;
            });
            dismissCopyMenu();
            mCopyMenu = menu;
            menu.show();
            return true;
        });
    }

    /** The Copy as config menu while it is up; it holds its row and the card, so it never outlives them. */
    @Nullable private android.widget.PopupMenu mCopyMenu;

    private void dismissCopyMenu() {
        android.widget.PopupMenu menu = mCopyMenu;
        mCopyMenu = null;
        if (menu != null) menu.dismiss();
    }

    @Nullable
    android.widget.PopupMenu copyMenu() {
        return mCopyMenu;
    }

    @NonNull
    private TextView addChip(@NonNull Context context, @NonNull LinearLayout row,
                             @NonNull CharSequence label, @NonNull View.OnClickListener click) {
        TextView chip = new TextView(context);
        chip.setText(label);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        chip.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF));
        chip.setSingleLine(true);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(dp(TOUCH_DP));
        chip.setMinWidth(0);
        boolean inline = row.getTag() == INLINE_CONTROLS;
        boolean segment = row.getTag() == SEGMENT_CONTROLS;
        int side = segment ? 4 : inline ? 8 : 14;
        chip.setPadding(dp(side), 0, dp(side), 0);
        chip.setContentDescription(label);
        chip.setOnClickListener(click);
        LinearLayout.LayoutParams params = segment
            ? new LinearLayout.LayoutParams(0, dp(TOUCH_DP), 1f)
            : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(TOUCH_DP));
        params.rightMargin = dp(inline || segment ? 4 : 8);
        if (segment) {
            // Its share of the row, the text shrinking to fit it rather than cutting.
            chip.setEllipsize(null);
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(chip,
                9, 14, 1, TypedValue.COMPLEX_UNIT_SP);
        }
        row.addView(chip, params);
        return chip;
    }

    /** The chip's ring, filled while it is the one in force; redrawn only when that changes. */
    private void showChip(@NonNull TextView chip, boolean chosen, int accent) {
        if (chip.getBackground() != null && chip.isSelected() == chosen) return;
        chip.setSelected(chosen);
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(TOUCH_DP) / 2f);
        if (chosen) {
            ring.setColor(withAlpha(accent, 0x33));
            ring.setStroke(dp(2), accent);
        } else {
            ring.setStroke(dp(1), withAlpha(MaterialColors.getColor(chip.getContext(),
                com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF), 0x40));
        }
        chip.setBackground(ring);
    }

    @NonNull
    private TextView text(@NonNull Context context, int res, int sp, int colorAttr) {
        TextView view = new TextView(context);
        view.setText(res);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(MaterialColors.getColor(context, colorAttr, 0xFFFFFFFF));
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    @NonNull
    private TextView action(@NonNull Context context, int res, @NonNull View.OnClickListener click) {
        TextView view = text(context, res, 14, androidx.appcompat.R.attr.colorPrimary);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(TOUCH_DP + 8));
        view.setMinWidth(0);
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setBackgroundResource(selectableBorderless(context));
        view.setOnClickListener(click);
        return view;
    }

    /** The strip's pill inside its touch height, in dp (the prototype's 32 px button). */
    static final int STRIP_PILL_DP = 32;

    /**
     * A strip chip as the prototype's: a pill in the ink's faint fill, or lit (the open card's,
     * and Done) in the accent with the accent's own ink. The touch height stays the strip's.
     */
    @NonNull
    private TextView stripChip(@NonNull Context context, int res, @NonNull View.OnClickListener click,
                               boolean lit) {
        TextView view = action(context, res, click);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        dressStripChip(view, lit);
        return view;
    }

    /** Dresses {@code chip} plain or lit; only when that changed (see {@link #litChips}). */
    private void dressStripChip(@NonNull TextView chip, boolean lit) {
        Context context = chip.getContext();
        int ink = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF);
        int accent = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0xFF6750A4);
        int onAccent = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnPrimary, 0xFFFFFFFF);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(STRIP_PILL_DP) / 2f);
        pill.setColor(lit ? accent : withAlpha(ink, STRIP_PILL_FILL_ALPHA));
        int vertical = Math.max(0, (dp(TOUCH_DP + 8) - dp(STRIP_PILL_DP)) / 2);
        int gap = dp(STRIP_CHIP_GAP_DP);
        android.graphics.drawable.InsetDrawable inset =
            new android.graphics.drawable.InsetDrawable(pill, gap, vertical, gap, vertical);
        chip.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(withAlpha(lit ? onAccent : ink, 0x33)), inset, null));
        // A background with insets puts its own padding on the view: the text keeps the pill's
        // room inside the gap (round 4: chips ran together and Done was a tight circle).
        chip.setPadding(gap + dp(STRIP_CHIP_PAD_DP), 0, gap + dp(STRIP_CHIP_PAD_DP), 0);
        chip.setTextColor(lit ? onAccent : ink);
    }

    /** A plain strip chip's fill: the ink at 14 % (the prototype's 9 % did not read on the dark panel). */
    static final int STRIP_PILL_FILL_ALPHA = 0x24;
    /** The room between two strip chips' pills (each side), and inside a pill around its text, in dp. */
    static final int STRIP_CHIP_GAP_DP = 3;
    static final int STRIP_CHIP_PAD_DP = 10;

    /**
     * The Appearance editor's sheet fill, from the theme's roles (which the launcher's scheme
     * drives when its chrome follows the scheme), outlined in dark themes as its sections are.
     */
    @NonNull
    private GradientDrawable panel(@NonNull Context context, float radius) {
        boolean dark = com.termux.app.surfaces.EditorSheetFill.dark(context);
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(radius);
        fill.setColor(MaterialColors.getColor(context,
            com.termux.app.surfaces.EditorSheetFill.fill(dark), 0));
        if (dark) {
            fill.setStroke(dp(1), MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOutlineVariant, 0));
        }
        return fill;
    }

    private static int selectableBorderless(@NonNull Context context) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,
            value, true);
        return value.resourceId;
    }

    private static void setText(@NonNull TextView view, @NonNull CharSequence text) {
        if (!text.toString().contentEquals(view.getText())) view.setText(text);
    }

    private static void setVisible(@NonNull View view, boolean visible) {
        int wanted = visible ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != wanted) view.setVisibility(wanted);
    }

    private static void setEnabled(@NonNull View view, boolean enabled) {
        if (view.isEnabled() != enabled) view.setEnabled(enabled);
        float alpha = enabled ? 1f : DISABLED_ALPHA;
        if (view.getAlpha() != alpha) view.setAlpha(alpha);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            mHost.context().getResources().getDisplayMetrics()));
    }
}
