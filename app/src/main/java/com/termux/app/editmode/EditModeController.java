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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

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
 * under it still moves. The keyboard has a card, and the strip's All glass opens the Global card:
 * the Look filmstrip, Custom's Opacity and Blur, Corners, Tint colour, icon pack and text size.
 */
public final class EditModeController {

    /** The cards edit mode can raise. */
    public enum Card { KEYBOARD, GLOBAL, WALLPAPER, HOME, BAR }

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
    }

    private static final int OUTLINE_STROKE_DP = 2;
    private static final int OUTLINE_RADIUS_DP = 12;
    private static final int CARD_RADIUS_DP = 24;
    private static final int GAP_DP = 12;
    private static final int TOUCH_DP = 40;
    private static final int MARKER_DP = 12;
    private static final float DISABLED_ALPHA = 0.38f;
    private static final int FRAME_WIDTH_DP = 96;
    private static final int FRAME_HEIGHT_DP = 64;
    /** The Global card's tallest, as a share of the screen: past it the card scrolls. */
    private static final float GLOBAL_CARD_MAX_SHARE = 0.55f;
    private static final int CORNERS_MAX_DP = 48;

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

    @NonNull private final Host mHost;

    @Nullable private EditSession mSession;
    @Nullable private FrameLayout mOverlay;
    @Nullable private View mOutline;
    @Nullable private LinearLayout mCard;
    @Nullable private View mGlobalCard;
    @Nullable private View mWallpaperCard;
    @Nullable private View mHomeCard;
    @Nullable private View mBarCard;
    @Nullable private TextView mBarChip;
    @Nullable private android.widget.Switch mBarReadingSwitch;
    @NonNull private final List<Chip> mRailChips = new ArrayList<>();
    /** The Control bar card's tool rows, by place. */
    @NonNull private final java.util.Map<String, ToolRow> mToolRows = new java.util.LinkedHashMap<>();
    /** Whether Home was on when the Home card was built: the card is built again when that changes. */
    private boolean mHomeCardOn;
    @Nullable private TextView mHomeChip;
    @Nullable private android.widget.Switch mDotsSwitch;
    @NonNull private final List<Chip> mLabelChips = new ArrayList<>();
    @NonNull private final List<Chip> mAzChips = new ArrayList<>();
    @Nullable private TextView mWallpaperChip;
    @NonNull private final List<Chip> mThemeChips = new ArrayList<>();
    @Nullable private android.widget.Switch mDriftSwitch;
    @Nullable private android.widget.Switch mColoursSwitch;
    /** A refresh is setting a switch: its listener must not write. */
    private boolean mSettingSwitch;
    @Nullable private RecyclerView mFilmstrip;
    @Nullable private FilmstripAdapter mFilmstripAdapter;
    @Nullable private View mAsk;
    @Nullable private TextView mAskText;
    @Nullable private View mCustomRows;
    @Nullable private TextView mGlobalChip;
    @NonNull private final List<Chip> mTintChips = new ArrayList<>();
    @NonNull private final List<Chip> mIconPackChips = new ArrayList<>();
    @Nullable private LinearLayout mIconPackRow;
    /** The Look the filmstrip landed on that waits for the ask's answer; null while none waits. */
    @Nullable private String mAskedLook;
    /** The Look the filmstrip last centred: it follows the store when the Look changes elsewhere. */
    @Nullable private String mCentredLook;
    /** The icon packs' listing and parsing, off the main thread; made on open, shut on leave. */
    @Nullable private java.util.concurrent.ExecutorService mBackground;
    private final android.os.Handler mMain = new android.os.Handler(android.os.Looper.getMainLooper());
    /** Bumped by every icon pack tap and listing: a late answer for an older one is dropped. */
    private int mIconPackGeneration;
    private boolean mIconPacksAsked;
    @Nullable private TextView mCount;
    @Nullable private TextView mKeyboardChip;
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
                mHost.wallpaper(), mHost.home(), barSettings());
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
            || mBarCard == null) return;
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
            mCentredLook = null;
        }
        setVisible(cardView(card), true);
        refresh();
        place();
    }

    @NonNull
    private View cardView(@NonNull Card card) {
        View view = card == Card.GLOBAL ? mGlobalCard
            : card == Card.WALLPAPER ? mWallpaperCard : card == Card.HOME ? mHomeCard
            : card == Card.BAR ? mBarCard : mCard;
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
        closeCard();
        if (mSession != null) mSession.end();
        mSession = null;
        mHost.looks().removeListener(mLookListener);
        mHost.onWallpaperTap(null);
        EditSession.Home home = mHost.home();
        if (home != null) home.onEmptyTap(null);
        if (mHost instanceof BarHost) ((BarHost) mHost).onBarTap(null);
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
        mCard = null;
        mGlobalCard = null;
        mWallpaperCard = null;
        mHomeCard = null;
        mBarCard = null;
        mBarChip = null;
        mBarReadingSwitch = null;
        mRailChips.clear();
        mToolRows.clear();
        mHomeChip = null;
        mDotsSwitch = null;
        mLabelChips.clear();
        mAzChips.clear();
        mWallpaperChip = null;
        mThemeChips.clear();
        mDriftSwitch = null;
        mColoursSwitch = null;
        mFilmstrip = null;
        mFilmstripAdapter = null;
        mAsk = null;
        mAskText = null;
        mCustomRows = null;
        mGlobalChip = null;
        mTintChips.clear();
        mIconPackChips.clear();
        mIconPackRow = null;
        mAskedLook = null;
        mCentredLook = null;
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
                barSettings());

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
            return insets;
        });

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
        return true;
    }

    @NonNull
    private View buildOutline(@NonNull Context context) {
        View outline = new View(context);
        GradientDrawable stroke = new GradientDrawable();
        stroke.setCornerRadius(dp(OUTLINE_RADIUS_DP));
        int ink = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface,
            0xFFFFFFFF);
        stroke.setStroke(dp(OUTLINE_STROKE_DP), withAlpha(ink, 0x66));
        outline.setBackground(stroke);
        outline.setContentDescription(context.getString(R.string.edit_mode_card_keyboard));
        outline.setOnClickListener(view -> openCard(Card.KEYBOARD));
        outline.setVisibility(View.GONE);
        return outline;
    }

    @NonNull
    private LinearLayout buildKeyboardCard(@NonNull Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad / 2);
        card.setBackground(panel(context, dp(CARD_RADIUS_DP)));
        // A tap on the card's empty space stays on the card.
        card.setClickable(true);

        TextView title = text(context, R.string.edit_mode_card_keyboard, 18,
            com.google.android.material.R.attr.colorOnSurface);
        card.addView(title);
        mRows.add(addOwnRow(context, card, TerlSurface.KEYBOARD, TerlProperty.OPACITY,
            R.string.edit_mode_opacity));
        mRows.add(addOwnRow(context, card, TerlSurface.KEYBOARD, TerlProperty.BLUR,
            R.string.edit_mode_blur));
        return card;
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
     * The Global card: the Look filmstrip (each Look worn on the live desktop as it lands), the
     * ask when a pick would clear own values, Custom's Opacity and Blur while Custom is worn, then
     * Corners, Tint colour, icon pack and text size. Like the keyboard's card it stands above the
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

        card.addView(rowLabel(context, R.string.edit_mode_look));
        mFilmstrip = buildFilmstrip(context);
        card.addView(mFilmstrip, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(FRAME_HEIGHT_DP + 32)));
        mAsk = buildAsk(context);
        card.addView(mAsk);
        setVisible(mAsk, false);

        LinearLayout custom = new LinearLayout(context);
        custom.setOrientation(LinearLayout.VERTICAL);
        for (TerlProperty property : TerlProperty.values()) {
            mRows.add(addSliderRow(context, custom, EditSession.Setting.custom(property), null, null,
                property.min, property.max, 1, valueTextOf(property),
                property == TerlProperty.OPACITY ? R.string.edit_mode_opacity : R.string.edit_mode_blur,
                () -> mHost.looks().customValue(property)));
        }
        mCustomRows = custom;
        card.addView(custom);
        setVisible(custom, false);

        mRows.add(addSliderRow(context, card, EditSession.Setting.CORNERS, null, null, 0,
            CORNERS_MAX_DP, 1, R.string.edit_mode_dp, R.string.edit_mode_corners,
            () -> mHost.looks().cornerRadiusDp()));

        card.addView(rowLabel(context, R.string.edit_mode_tint));
        LinearLayout tints = new LinearLayout(context);
        tints.setOrientation(LinearLayout.HORIZONTAL);
        String[] tintNames = context.getResources().getStringArray(
            R.array.appearance_editor_tint_hue_entries);
        for (int i = 0; i < TerlLookStore.TINTS.size(); i++) {
            String tint = TerlLookStore.TINTS.get(i);
            TextView chip = addChip(context, tints, i < tintNames.length ? tintNames[i] : tint,
                view -> {
                    if (mSession == null) return;
                    dismissAsk(true);
                    mSession.setTint(tint);
                    refresh();
                });
            mTintChips.add(new Chip(tint, chip));
        }
        card.addView(tints);

        if (mHost.launcher() != null) {
            card.addView(rowLabel(context, R.string.edit_mode_icon_pack));
            HorizontalScrollView packs = new HorizontalScrollView(context);
            packs.setHorizontalScrollBarEnabled(false);
            mIconPackRow = new LinearLayout(context);
            mIconPackRow.setOrientation(LinearLayout.HORIZONTAL);
            packs.addView(mIconPackRow);
            card.addView(packs);

            EditSession.Launcher launcher = mHost.launcher();
            mRows.add(addSliderRow(context, card, EditSession.Setting.TEXT_SIZE, null, null,
                com.termux.shared.termux.settings.registry.SettingDeclarations.TEXT_SIZE.min,
                com.termux.shared.termux.settings.registry.SettingDeclarations.TEXT_SIZE.max,
                com.termux.shared.termux.settings.registry.SettingDeclarations.TEXT_SIZE.step,
                R.string.edit_mode_percent, R.string.edit_mode_text_size,
                launcher::textSizePercent));
        }
        scroll.addView(card);
        return scroll;
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
     * <p>Theme offers v1's three: System, Light and Dark. A Black theme is owed to the
     * developer's decision (the spec names it; the app has none), and is not built.
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
        String[] values = {"system", "false", "true"};
        int[] names = {R.string.appearance_theme_system, R.string.appearance_theme_light,
            R.string.appearance_theme_dark};
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

    /** The Home card: the grid, the page dots and where the A–Z letters stand. */
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

        mRows.add(addSliderRow(context, card, EditSession.Setting.COLUMNS, null, null,
            EditSession.Home.MIN_COLUMNS, EditSession.Home.MAX_COLUMNS, 1,
            R.string.edit_mode_dp, R.string.edit_mode_columns, home::columns));

        card.addView(rowLabel(context, R.string.edit_mode_labels));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.HORIZONTAL);
        String[] sizes = {EditSession.Home.LABEL_SMALL, EditSession.Home.LABEL_DEFAULT,
            EditSession.Home.LABEL_LARGE};
        int[] sizeNames = {R.string.edit_mode_label_small, R.string.edit_mode_label_default,
            R.string.edit_mode_label_large};
        for (int i = 0; i < sizes.length; i++) {
            String size = sizes[i];
            mLabelChips.add(new Chip(size, addChip(context, labels, context.getString(sizeNames[i]),
                view -> {
                    if (mSession == null) return;
                    mSession.setLabels(size);
                    refresh();
                })));
        }
        card.addView(labels);

        mDotsSwitch = addSwitchRow(context, card, R.string.edit_mode_page_dots, on -> {
            if (mSession == null) return;
            mSession.setPageDots(on);
            refresh();
        });

        card.addView(rowLabel(context, R.string.edit_mode_az));
        LinearLayout az = new LinearLayout(context);
        az.setOrientation(LinearLayout.HORIZONTAL);
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
        card.addView(az);
        return scroll;
    }

    /** Home may have been switched on or off (Change, in Settings) since the card was built. */
    private void rebuildHomeCardIfSwitched() {
        EditSession.Home home = mHost.home();
        FrameLayout overlay = mOverlay;
        View old = mHomeCard;
        if (home == null || overlay == null || old == null || home.available() == mHomeCardOn) return;
        mLabelChips.clear();
        mAzChips.clear();
        mDotsSwitch = null;
        mRows.removeIf(row -> row.setting == EditSession.Setting.COLUMNS);
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
        } finally {
            mSettingSwitch = false;
        }
        for (Chip chip : mLabelChips) showChip(chip.view, chip.value.equals(home.labels()), accent);
        for (Chip chip : mAzChips) showChip(chip.view, chip.value.equals(home.azEdge()), accent);
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
        row.addView(toggle);
        card.addView(row);
        return toggle;
    }

    @NonNull
    private RecyclerView buildFilmstrip(@NonNull Context context) {
        RecyclerView strip = new RecyclerView(context);
        LinearLayoutManager manager = new LinearLayoutManager(context,
            LinearLayoutManager.HORIZONTAL, false);
        strip.setLayoutManager(manager);
        strip.setOverScrollMode(View.OVER_SCROLL_NEVER);
        strip.setClipToPadding(false);
        mFilmstripAdapter = new FilmstripAdapter(mHost.looks().lookIds());
        strip.setAdapter(mFilmstripAdapter);
        // The first and last frames can come to the middle, where a frame lands.
        strip.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view,
                                       @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                int side = Math.max(0, (parent.getWidth() - dp(FRAME_WIDTH_DP)) / 2);
                int gap = dp(4);
                outRect.left = position == 0 ? side : gap;
                outRect.right = position == state.getItemCount() - 1 ? side : gap;
            }
        });
        LinearSnapHelper snap = new LinearSnapHelper();
        snap.attachToRecyclerView(strip);
        strip.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                View landed = snap.findSnapView(manager);
                if (landed == null) return;
                int position = recyclerView.getChildAdapterPosition(landed);
                List<String> ids = mHost.looks().lookIds();
                if (position < 0 || position >= ids.size()) return;
                onLookLanded(ids.get(position));
            }
        });
        return strip;
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
        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(8), 0, 0);

        TextView label = text(context, labelRes, 15, com.google.android.material.R.attr.colorOnSurface);
        head.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = new TextView(context);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        value.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFB0B0B0));
        value.setSingleLine(true);
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
        card.addView(head);

        SeekBar slider = new SeekBar(context);
        slider.setMax((max - min) / step);
        int accent = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary,
            0xFF8AB4F8);
        slider.setProgressTintList(ColorStateList.valueOf(accent));
        slider.setThumbTintList(ColorStateList.valueOf(accent));
        slider.setContentDescription(context.getString(labelRes));
        card.addView(slider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            dp(TOUCH_DP)));

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
                // Text size is outside the look store, whose listener refreshes the rest.
                if (setting == EditSession.Setting.TEXT_SIZE) refresh();
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

    @NonNull
    private View buildStrip(@NonNull Context context) {
        LinearLayout strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(16), dp(4), dp(4), dp(4));
        strip.setBackground(panel(context, dp(TOUCH_DP)));
        strip.setClickable(true);

        mCount = new TextView(context);
        mCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mCount.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF));
        mCount.setMaxLines(2);
        mCount.setEllipsize(android.text.TextUtils.TruncateAt.END);
        strip.addView(mCount, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // With the keyboard down there is no outline to tap: the strip offers its card.
        mKeyboardChip = action(context, R.string.edit_mode_card_keyboard, view -> openCard(Card.KEYBOARD));
        strip.addView(mKeyboardChip);
        mGlobalChip = action(context, R.string.edit_mode_card_global, view -> openCard(Card.GLOBAL));
        strip.addView(mGlobalChip);
        mWallpaperChip = action(context, R.string.edit_mode_wallpaper_chip,
            view -> openCard(Card.WALLPAPER));
        strip.addView(mWallpaperChip);
        mHomeChip = action(context, R.string.edit_mode_home_chip, view -> openCard(Card.HOME));
        strip.addView(mHomeChip);
        mBarChip = action(context, R.string.edit_mode_bar_chip, view -> openCard(Card.BAR));
        strip.addView(mBarChip);
        mUndo = action(context, R.string.edit_mode_undo, view -> {
            if (mSession == null) return;
            mSession.undo();
            refresh();
        });
        strip.addView(mUndo);
        mRevert = action(context, R.string.edit_mode_revert, view -> {
            if (mSession == null) return;
            mSession.revert();
            refresh();
        });
        strip.addView(mRevert);
        strip.addView(action(context, R.string.edit_mode_done, view -> leave()));
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
        if (mKeyboardChip != null) {
            setVisible(mKeyboardChip, top == EditModePlacement.OFF_SCREEN && mOpenCard == null);
        }
        if (mGlobalChip != null) setVisible(mGlobalChip, mOpenCard == null);
        if (mWallpaperChip != null) setVisible(mWallpaperChip, mOpenCard == null);
        if (mHomeChip != null) setVisible(mHomeChip, mOpenCard == null);
        if (mBarChip != null) setVisible(mBarChip, mOpenCard == null && barSettings() != null);
        liftStripOverBar(overlay);
        if (mOpenCard != null && mStrip != null && overlay.getHeight() > 0) {
            int contentBottom = overlay.getHeight() - overlay.getPaddingBottom();
            ViewGroup.MarginLayoutParams stripParams =
                (ViewGroup.MarginLayoutParams) mStrip.getLayoutParams();
            // The bar's card stands above the strip, which stands above the bar: the bar stays in
            // sight, so the surface it edits is the strip's top.
            int margin = EditModePlacement.cardBottomMarginPx(contentBottom,
                mOpenCard == Card.BAR ? EditModePlacement.OFF_SCREEN : top,
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

    /**
     * While the bar's card is open the strip stands just above the bar, so the bar is not under it;
     * otherwise at the very bottom. A rail (taller than wide) is not stood over: the strip stays low.
     */
    private void liftStripOverBar(@NonNull FrameLayout overlay) {
        if (mStrip == null) return;
        int lift = 0;
        View bar = mOpenCard == Card.BAR && mHost instanceof BarHost ? ((BarHost) mHost).barView() : null;
        if (bar != null && bar.isShown() && bar.getWidth() > bar.getHeight() && overlay.getHeight() > 0) {
            int[] at = new int[2];
            int[] origin = new int[2];
            bar.getLocationOnScreen(at);
            overlay.getLocationOnScreen(origin);
            int contentBottom = overlay.getHeight() - overlay.getPaddingBottom();
            lift = Math.max(0, contentBottom - (at[1] - origin[1]));
        }
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mStrip.getLayoutParams();
        int wanted = dp(GAP_DP / 2) + lift;
        if (params.bottomMargin != wanted) {
            params.bottomMargin = wanted;
            mStrip.setLayoutParams(params);
        }
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

    // ---------------------------------------------------------------- the Global card

    /** The filmstrip came to rest on a Look: it is worn at once, or the card asks first. */
    private void onLookLanded(@NonNull String lookId) {
        EditSession session = mSession;
        if (session == null || mOpenCard != Card.GLOBAL) return;
        mCentredLook = lookId;
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

    /** Takes the ask down; {@code backToWorn} brings the filmstrip back to the Look worn. */
    private void dismissAsk(boolean backToWorn) {
        if (mAskedLook == null) return;
        mAskedLook = null;
        if (mAsk != null) setVisible(mAsk, false);
        if (backToWorn) {
            mCentredLook = null;
            refresh();
        }
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

    /** The Global card as the store holds it: the worn Look centred, Custom's rows, the chips. */
    private void refreshGlobal(@NonNull Context context, @NonNull TerlLookStore looks) {
        if (mGlobalCard == null || mOpenCard != Card.GLOBAL) return;
        String worn = looks.look();
        if (mCustomRows != null) setVisible(mCustomRows, TerlLookStore.CUSTOM.equals(worn));
        if (mFilmstripAdapter != null) mFilmstripAdapter.show(worn,
            looks.customValue(TerlProperty.OPACITY));
        if (mFilmstrip != null && mAskedLook == null && !worn.equals(mCentredLook)
            && mFilmstrip.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
            mCentredLook = worn;
            centreOn(looks.lookIds().indexOf(worn), true);
        }
        int accent = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary,
            0xFF8AB4F8);
        for (Chip chip : mTintChips) showChip(chip.view, chip.value.equals(looks.tint()), accent);
        EditSession.Launcher launcher = mHost.launcher();
        if (launcher != null) {
            String pack = launcher.iconPack();
            for (Chip chip : mIconPackChips) showChip(chip.view, chip.value.equals(pack), accent);
        }
    }

    /** Brings the frame at {@code position} to the filmstrip's middle at once, without a landing. */
    private void centreOn(int position, boolean retry) {
        RecyclerView strip = mFilmstrip;
        if (strip == null || position < 0) return;
        if (strip.getWidth() == 0) {
            // Not laid out yet (the card has just come up): once, after its first layout.
            if (retry) strip.post(() -> centreOn(position, false));
            return;
        }
        RecyclerView.LayoutManager manager = strip.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) return;
        int offset = position == 0 ? 0 : (strip.getWidth() - dp(FRAME_WIDTH_DP)) / 2 - dp(4);
        ((LinearLayoutManager) manager).scrollToPositionWithOffset(position, offset);
    }

    /** A tap on a frame glides it to the middle, where it lands like a swipe. */
    private void glideTo(int position) {
        RecyclerView strip = mFilmstrip;
        if (strip == null || strip.getLayoutManager() == null) return;
        LinearSmoothScroller scroller = new LinearSmoothScroller(strip.getContext()) {
            @Override
            public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd,
                                        int snapPreference) {
                return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2);
            }
        };
        scroller.setTargetPosition(position);
        strip.getLayoutManager().startSmoothScroll(scroller);
    }

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
            case com.termux.app.look.WallpaperLooks.CALM:
                return context.getString(R.string.edit_mode_look_wallpaper_calm);
            case com.termux.app.look.WallpaperLooks.BALANCED:
                return context.getString(R.string.edit_mode_look_wallpaper_balanced);
            case com.termux.app.look.WallpaperLooks.BOLD:
                return context.getString(R.string.edit_mode_look_wallpaper_bold);
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

    /** The filmstrip's frames: each Look's name on a pane of its opacity; the worn one ringed. */
    private final class FilmstripAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull private final List<String> mIds;
        @Nullable private String mWorn;
        private int mCustomOpacity = -1;

        FilmstripAdapter(@NonNull List<String> ids) {
            mIds = new ArrayList<>(ids);
        }

        /** Rebinds only the frames whose look changed. */
        void show(@NonNull String worn, int customOpacity) {
            String was = mWorn;
            mWorn = worn;
            if (!worn.equals(was)) {
                if (was != null && mIds.contains(was)) notifyItemChanged(mIds.indexOf(was));
                if (mIds.contains(worn)) notifyItemChanged(mIds.indexOf(worn));
            }
            if (customOpacity != mCustomOpacity) {
                mCustomOpacity = customOpacity;
                int custom = mIds.indexOf(TerlLookStore.CUSTOM);
                if (custom >= 0) notifyItemChanged(custom);
            }
        }

        @Override
        public int getItemCount() {
            return mIds.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            TextView frame = new TextView(context);
            frame.setGravity(Gravity.CENTER);
            frame.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            frame.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF));
            frame.setSingleLine(true);
            frame.setEllipsize(android.text.TextUtils.TruncateAt.END);
            frame.setPadding(dp(6), 0, dp(6), 0);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(dp(FRAME_WIDTH_DP),
                dp(FRAME_HEIGHT_DP));
            params.topMargin = dp(16);
            params.bottomMargin = dp(16);
            frame.setLayoutParams(params);
            return new RecyclerView.ViewHolder(frame) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TextView frame = (TextView) holder.itemView;
            Context context = frame.getContext();
            String id = mIds.get(position);
            String name = lookName(context, id);
            setText(frame, name);
            frame.setContentDescription(name);
            int opacity = mHost.looks().lookValue(id, TerlProperty.OPACITY);
            int glass = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorSecondaryContainer, 0xFF3A3F4A);
            int accent = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary,
                0xFF8AB4F8);
            GradientDrawable pane = new GradientDrawable();
            pane.setCornerRadius(dp(14));
            // A pane as see-through as the Look's glass, never so faint it disappears.
            pane.setColor(withAlpha(glass, Math.max(0x24, opacity * 255 / 100)));
            boolean worn = id.equals(mWorn);
            if (worn) pane.setStroke(dp(2), accent);
            else pane.setStroke(dp(1), withAlpha(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF), 0x40));
            frame.setBackground(pane);
            frame.setSelected(worn);
            frame.setOnClickListener(view -> {
                int at = holder.getBindingAdapterPosition();
                if (at != RecyclerView.NO_POSITION) glideTo(at);
            });
        }
    }

    // ---------------------------------------------------------------- small views

    @NonNull
    private TextView rowLabel(@NonNull Context context, int res) {
        TextView label = text(context, res, 15, com.google.android.material.R.attr.colorOnSurface);
        label.setPadding(0, dp(12), 0, dp(4));
        return label;
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
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setContentDescription(label);
        chip.setOnClickListener(click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(TOUCH_DP));
        params.rightMargin = dp(8);
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
