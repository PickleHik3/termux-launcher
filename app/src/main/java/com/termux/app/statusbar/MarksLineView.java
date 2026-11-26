package com.termux.app.statusbar;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.ReducedMotion;
import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.GlassBackdropCache;
import com.termux.app.chrome.GroundUnderGlass;
import com.termux.app.chrome.OnGlass;
import com.termux.app.page.PageKind;
import com.termux.app.terminal.WindowChipInk;
import com.termux.app.wall.pageswitch.MarksLook;
import com.termux.app.wall.pageswitch.MarksNameChip;
import com.termux.app.wall.pageswitch.MarksRowLayout;
import com.termux.app.wall.pageswitch.MarksTouch;
import com.termux.app.wall.pageswitch.MarksTouchLayout;
import com.termux.app.wall.pageswitch.MarksPillPolicy;
import com.termux.app.wall.pageswitch.MarksZoomPlan;
import com.termux.app.wall.pageswitch.PageBarFold;
import com.termux.app.wall.pageswitch.PageBarModel;
import com.termux.app.wall.pageswitch.PageBarSlide;
import com.termux.app.wall.pageswitch.PageMarkIcons;
import com.termux.app.wall.pageswitch.PageSwitchHaptics;
import com.termux.app.wall.pageswitch.PageSwitchTargets;
import com.termux.app.window.WindowLayer;
import com.termux.app.window.WindowLayers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The Marks line: the status bar's pages as a line of marks, Overview's first, then one per entry
 * of the wall in the wall's order, where the window chips stand under the Glyphs look. A row
 * ({@link #setVertical} false) draws along x from the start; a column on a side edge draws the same
 * line along y.
 *
 * <p>At rest the marks are packed ({@link MarksRowLayout}): Home's dots close together, the others
 * on a wider cell, a gap between kinds, a Linux app's own icon where it has one, a full-screen
 * program's glyph in place of its window's square, the current page in the accent (an app's icon at full strength, the others dimmed);
 * runs fold only when the line runs out of room.
 *
 * <p>One touch, no waiting: at touch-down the touch layout ({@link MarksTouch}) spreads every mark
 * under the finger stays where it is and the marks after it spread, the marks near the finger zoom,
 * and they are drawn over the row, with no ground behind them, in a view of their own in the chrome
 * layer ({@link MarksZoomView}), with the name of the page under the finger at the row's end in the readings' place. A lift
 * without a slide is a tap on the touched mark; a lift after a slide goes to the page in focus.
 * All of it is gone at the lift, on cancel, on detach and when the pages change.
 *
 * <p>{@link #setPages} is the one input: it compares and rebuilds only when the entries, the
 * attention or the length changed. Drawing allocates nothing and requests no layout.
 */
public final class MarksLineView extends View implements MarksZoomView.Painter {

    /** The mark's box with no cap (the strip): the spec's 16dp mark. */
    public static final float MARK_DP = 16f;

    /** Global's glass for the title's capsule, as every chrome glass wears it. */
    public interface TitleGlass {
        /** Global's tint, alpha included, as it lies over what is beneath: what the ink is solved on. */
        int tint();

        /** Global's glass at {@code cornerRadiusPx} for {@code anchor}, over the stored backdrop; null for none. */
        @Nullable Drawable ground(float cornerRadiusPx, @NonNull View anchor);
    }

    /** What the activity takes a tap on a mark to: the page switcher's own navigation. */
    @Nullable private PageSwitchTargets.Navigator mNavigator;

    private final MarksLook mLook = new MarksLook();
    private final int mTouchSlop;
    private final float mDensity;
    @Nullable private PageBarModel mModel;
    @Nullable private boolean[] mAttention;
    @Nullable private MarksRowLayout mRest;
    private boolean mVertical;
    private final int mNeutral;
    private final int mError;
    private int mAccent;
    @Nullable private ChromeInk mChromeInk;
    @NonNull private MarksLineInk mInk;
    private int mInkGeneration = -1;
    @NonNull private final Rect mBandRect = new Rect();
    @NonNull private final Rect mMeasuredRect = new Rect();
    private boolean mInkMeasured;
    /** The authored inks are the ones held, for this chrome. */
    private boolean mAuthored;

    // ---- icons and glyphs ---------------------------------------------------------------------
    @Nullable private PageMarkIcons mIcons;
    /** Per entry of {@link #mModel}: its app's icon, asked when the model changes, never in draw. */
    @NonNull private Drawable[] mEntryIcons = new Drawable[0];
    @Nullable private Typeface mGlyphFace;

    // ---- touch ---------------------------------------------------------------------------------
    @Nullable private MarksTouch mTouch;
    @Nullable private MarksZoomPlan mPlan;
    private boolean mDown;
    private float mDownX;
    private float mDownY;
    /** The finger along the line, raw (not the touch layout's shifted position). */
    private float mFinger;
    @Nullable private PageSwitchHaptics mHaptics;
    @Nullable private BooleanSupplier mReducedMotion;

    // ---- the zoom -----------------------------------------------------------------------------
    @Nullable private MarksZoomView mZoom;
    private boolean mZoomShown;
    /** The Display page's dark band under the row, 0 for none: the title's ink is solved over it. */
    private int mZoomBand;
    @Nullable private TitleGlass mTitleGlass;
    /** The title's ink, solved on the capsule's glass at touch-down. */
    private int mTitleInk;
    /** How far the readings have given way to the capsule and its name, 0 to 1: one value for both. */
    private float mFade;
    @Nullable private ValueAnimator mFadeAnim;
    /** The plan and the title the capsule holds while it fades out after a lift; null when no tail runs. */
    @Nullable private MarksZoomPlan mTailPlan;
    @Nullable private String mTailTitle;
    /** The title the capsule holds and its width, as last placed; the zoom view's origin in the line. */
    @Nullable private String mCapsuleTitle;
    private float mCapsuleWidth;
    private float mFrameLeft;
    private float mFrameTop;
    /** What the line stands on, opaque, as the last ink pass measured it; 0 before one. */
    private int mBandSurface;
    private final int[] mLoc = new int[2];
    private final int[] mOtherLoc = new int[2];

    // ---- the title and the readings ----------------------------------------------------------
    @Nullable private View mReadings;
    @NonNull private String[] mTitles = new String[0];
    @NonNull private String mOverviewTitle = "";
    private final TextPaint mTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics mTitleMetrics = new Paint.FontMetrics();
    private boolean mTitleAtStart;
    /** 0 with the title at the row's end, 1 once it went round to the start. */
    private float mTitleProgress;
    @Nullable private ValueAnimator mTitleSlide;

    // ---- back gesture ---------------------------------------------------------------------------
    private final Rect mExclusion = new Rect();

    @NonNull private String[] mSlotLabels = new String[0];
    @NonNull private final Access mAccess;

    /** A ground darker than this luminance takes the pale ink. */
    private static final double PALE_INK_BELOW = 0.5;

    /** The lift a mark is drawn with: none, the marks stand flat. */
    private static final float AT_REST = 0f;

    public MarksLineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mAccess = new Access(this);
        ViewCompat.setAccessibilityDelegate(this, mAccess);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mDensity = context.getResources().getDisplayMetrics().density;
        mNeutral = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mError = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorError,
            ContextCompat.getColor(context, R.color.termux_error));
        mAccent = mNeutral;
        mInk = MarksLineInk.authored(mNeutral, mAccent, mError);
        mTitlePaint.setTextAlign(Paint.Align.LEFT);
    }

    public void setNavigator(@Nullable PageSwitchTargets.Navigator navigator) {
        mNavigator = navigator;
    }

    /** Whether the page's name shows while a finger slides; true until a line that wants none (Home's pill) turns it off. */
    private boolean mTitleShown = true;
    /** Told when the zoom comes up and goes away: Home's pill stands aside while the ground stands for it. */
    @Nullable private Runnable mZoomListener;
    /** The opaque ground the line stands on, when its owner measured it (Home's pill); 0 for the chrome's band. */
    private boolean mFixedGround;
    private int mFixedBand;
    private boolean mFixedPale;

    /**
     * Whether a slide names the page under the finger at the row's end and fades the readings
     * away. On by default; a line with no status row around it (Home's pill) turns it off, and the
     * name shows where its owner puts it. Takes effect from the next touch.
     */
    public void setTitleShown(boolean shown) {
        mTitleShown = shown;
    }

    /**
     * A bar elsewhere that shows this line's title and fades its readings: Home's status bar for
     * Home's pill, which has no row of its own to end in. The line hands it the title and puts
     * it back on every way a touch ends.
     */
    public interface TitleHost {
        /** The bar's readings, faded while the title shows; null for none. */
        @Nullable View readings();

        /** The room along the bar the title may take, in px; 0 or less for none (no bar to show it). */
        float roomPx();

        /**
         * The name for the finger now, in {@code color} at {@code sizePx}; {@code progress} is the
         * title's travel round the bar, 0 with it at the bar's end, 1 at its start. Called at the
         * touch-down and when the name changes, never per move.
         */
        void show(@NonNull String text, int color, float sizePx, float progress);

        /** The touch ended: the title goes away. */
        void clear();

        /** The view the name stands in, for the capsule's glass to anchor on; null for none. */
        @Nullable default View titleView() {
            return null;
        }

        /** Dresses the name with its capsule's glass; null takes it off. */
        default void dress(@Nullable Drawable ground) {
        }

        /** The name and its capsule at {@code alpha}: the one fade value the readings fade by, reversed. */
        default void fade(float alpha) {
        }
    }

    @Nullable private TitleHost mTitleHost;
    /** The host holds a title for the touch under way. */
    private boolean mHostActive;
    @Nullable private String mHostText;

    /** Where this line's title shows when it stands outside a row; null for none. */
    public void setTitleHost(@Nullable TitleHost host) {
        if (mTitleHost == host) return;
        endTouch("look-change");
        mTitleHost = host;
    }

    /** Whether the host holds a title now; for tests. */
    boolean hostActive() {
        return mHostActive;
    }

    /** The name of the entry under the touch's focus, fitted for the host; null for none. */
    @Nullable
    private String focusTitle() {
        MarksTouch touch = mTouch;
        if (touch == null) return null;
        MarksTouchLayout layout = touch.layout();
        int slot = touch.focusSlot();
        if (layout == null || slot < 0 || slot >= layout.slotCount()) return null;
        int entry = layout.entryIndex(slot, touch.focusMember());
        String title = entry == MarksTouch.OVERVIEW_ENTRY ? mTitles.length > 0
            ? mTitles[mTitles.length - 1] : null
            : entry >= 0 && entry < mTitles.length - 1 ? mTitles[entry] : null;
        return title == null || title.isEmpty() ? null : title;
    }

    /** Hands the host the title when it changed since the last handing; compares first. */
    private void syncHostTitle() {
        TitleHost host = mTitleHost;
        if (!mHostActive || host == null) return;
        String title = focusTitle();
        if (title == null ? mHostText == null : title.equals(mHostText)) return;
        mHostText = title;
        if (title == null) host.clear();
        else host.show(title, mTitleInk, mMarkPx * MarksZoomPlan.TITLE_TEXT_SHARE, 0f);
    }

    /** Whether {@link #setTitleShown} is on; for tests. */
    boolean titleShown() {
        return mTitleShown;
    }

    /** {@code listener} runs when the zoom comes up and when it goes away; null for none. */
    public void setZoomListener(@Nullable Runnable listener) {
        mZoomListener = listener;
    }

    /**
     * Where the touched marks end along the line while the zoom stands, else 0: Home's pill widens
     * to hold them.
     */
    public float touchedEndPx() {
        MarksTouch touch = mTouch;
        MarksTouchLayout layout = touch == null || !mZoomShown ? null : touch.layout();
        return layout == null ? 0f : layout.contentStartPx() + layout.contentLengthPx();
    }

    /** Whether the touch's zoom stands now: the ground the zoom draws is on screen. */
    public boolean isZoomShown() {
        return mZoomShown;
    }

    /**
     * Solves the inks on {@code band}, the opaque ground the line stands on as its owner measured
     * it, instead of the status bar's band. Compares before it repaints.
     *
     * @param pale whether the pale ink is the legible one on this ground
     */
    public void setGroundInk(@ColorInt int band, boolean pale) {
        int opaque = OnGlass.opaque(band);
        if (mFixedGround && mFixedBand == opaque && mFixedPale == pale) return;
        mFixedGround = true;
        mFixedBand = opaque;
        mFixedPale = pale;
        if (syncInk()) invalidate();
    }

    /** The ink the thin row's hairline wears: the line's other ink as solved on its band. */
    int hairlineInk() {
        return mInk.other;
    }

    /** The inks held now, for tests. */
    @NonNull
    MarksLineInk ink() {
        return mInk;
    }

    private void zoomChanged() {
        Runnable listener = mZoomListener;
        if (listener != null) listener.run();
    }

    /**
     * What the touch asks of the app: whether haptics are on (the tick on each change of mark and
     * the click on choosing, as the page bar's) and whether the phone plays no animations (the
     * readings and the title then move at once). Without it the touch is silent and reads the
     * system's animator scale.
     */
    public void setSlideSupport(@Nullable BooleanSupplier hapticsEnabled,
                                @Nullable BooleanSupplier reducedMotion) {
        mHaptics = hapticsEnabled == null ? null
            : new PageSwitchHaptics(getContext(), hapticsEnabled);
        mReducedMotion = reducedMotion;
    }

    /** Whether {@link #setSlideSupport} has handed this line its switches. */
    public boolean hasSlideSupport() {
        return mHaptics != null;
    }

    /** Where the line gets a Linux app's icon and the program glyphs' face; null for plain marks. */
    public void setMarkIcons(@Nullable PageMarkIcons icons) {
        if (mIcons == icons) return;
        mIcons = icons;
        resolveIcons(true);
        invalidate();
    }

    /** Global's glass for the title's capsule; null draws the name without one. */
    public void setTitleGlass(@Nullable TitleGlass glass) {
        mTitleGlass = glass;
    }

    /**
     * The band the row lies on over a Display page ({@code argb}, 0 for none): the dark band the
     * title's ink is solved over. Read at the next touch-down.
     */
    public void setZoomBand(int argb) {
        mZoomBand = argb;
    }

    /** The scheme's accent, as the launcher's chrome resolves it: the current page and the focus. */
    public void setAccent(int accent) {
        if (mAccent == accent) return;
        mAccent = accent;
        mInkMeasured = false;
        mAuthored = false;
        mInkGeneration = -1;
        syncInk();
        invalidate();
    }

    /** The largest a mark is drawn, in px; 0 for no cap beyond the line's own (the strip). */
    private float mMarkCapPx;

    /**
     * Caps the marks at {@code capPx} (the thin row's {@code PaneRowPolicy.MARK_DP}); 0 lifts the
     * cap. The row packs on the mark's size, so a change lays the line out again.
     */
    public void setMarkCapPx(float capPx) {
        float cap = Math.max(0f, capPx);
        if (cap == mMarkCapPx) return;
        mMarkCapPx = cap;
        requestLayout();
    }

    /** Along y for a column on a side edge, along x for the row. */
    public void setVertical(boolean vertical) {
        if (mVertical == vertical) return;
        endTouch("presentation-change");
        mVertical = vertical;
        rebuild();
        requestLayout();
    }

    /**
     * Who can say what the line stands on. Null keeps the authored roles. The band is read, never
     * asked: the status ink pass owns the question ({@code ChromeInk#resolution}), as the window
     * column reads it.
     */
    public void setChromeInk(@Nullable ChromeInk ink) {
        if (mChromeInk == ink) return;
        mChromeInk = ink;
        mInkMeasured = false;
        mAuthored = false;
        mInkGeneration = -1;
        mMeasuredRect.setEmpty();
        syncInk();
        invalidate();
    }

    /**
     * Takes the wall's entries and which of them want the user ({@code attention}, per entry; null
     * for none). Rebuilds the line only when something drawn differs from what is held; a change
     * ends a touch under way, since its page list is gone.
     */
    public void setPages(@Nullable PageBarModel model, @Nullable boolean[] attention) {
        if (sameEntries(mModel, model) && Arrays.equals(mAttention, attention)) {
            mModel = model;
            // An icon still unknown may have been found since: asked again only while one is missing.
            if (missingIcon()) {
                resolveIcons(false);
                if (!missingIcon()) invalidate();
            }
            return;
        }
        endTouch("page-change");
        mModel = model;
        mAttention = attention == null ? null : attention.clone();
        resolveIcons(true);
        rebuild();
        // The line sizes to its marks, so a changed list is a new size; never from inside a pass.
        requestLayout();
    }

    /** The rest layout in use, or null while there are no entries or no length; for tests. */
    @Nullable
    MarksRowLayout rowLayout() {
        return mRest;
    }

    /** The touch under way, or null; for tests. */
    @Nullable
    MarksTouch touch() {
        return mTouch;
    }

    /** Whether the zoom stands in the chrome layer now; for tests. */
    boolean zoomShown() {
        return mZoomShown;
    }

    /** Re-reads the band after a pass that could have moved it; compares before it repaints. */
    public void refreshInk() {
        if (syncInk()) invalidate();
    }

    // ---- build ------------------------------------------------------------------------------

    /** What an unbounded measure offers the line: room for every mark open. */
    private static final float UNBOUNDED_PX = 100000f;

    /** The length along the axis the line was last built for: what the parent offered. */
    private float mAvailPx;
    /** The mark's box the line was last built for. */
    private float mMarkPx;

    /**
     * The line asks for the length its marks take and no more: it packs and folds by the room the
     * parent offers along its axis ({@link MarksRowLayout}), then sizes to the packed marks. A
     * fixed line over the status row therefore leaves the readings and Home's float chips their
     * room, and a hidden twin in Home's strip (the spacer) asks for the same. The mark is the
     * line's own size, capped, and never taller than the room across.
     */
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int alongSpec = mVertical ? heightSpec : widthSpec;
        int crossSpec = mVertical ? widthSpec : heightSpec;
        int mode = MeasureSpec.getMode(alongSpec);
        int size = MeasureSpec.getSize(alongSpec);
        float avail = mode == MeasureSpec.UNSPECIFIED ? UNBOUNDED_PX : size;
        float mark = mMarkCapPx > 0f ? mMarkCapPx : MARK_DP * mDensity;
        int crossSize = MeasureSpec.getSize(crossSpec);
        if (MeasureSpec.getMode(crossSpec) != MeasureSpec.UNSPECIFIED && crossSize > 0) {
            mark = Math.min(mark, crossSize);
        }
        if (avail != mAvailPx || mark != mMarkPx) {
            mAvailPx = avail;
            mMarkPx = mark;
            rebuild();
        }
        int content = mRest == null ? 0 : (int) Math.ceil(mRest.contentLengthPx());
        int along = mode == MeasureSpec.EXACTLY ? size
            : mode == MeasureSpec.AT_MOST ? Math.min(size, content) : content;
        int cross = getDefaultSize(0, crossSpec);
        if (mVertical) setMeasuredDimension(cross, along);
        else setMeasuredDimension(along, cross);
    }

    private void rebuild() {
        PageBarModel model = mModel;
        if (model == null || model.entries().isEmpty() || mAvailPx <= 0f || mMarkPx <= 0f) {
            mRest = null;
        } else {
            mRest = new MarksRowLayout(model.entries(), mAttention, true, mMarkPx, mAvailPx,
                chipTextWidth(model.entries()));
        }
        rebuildLabels();
        mAccess.invalidateRoot();
        invalidate();
    }

    /** The chip's name and the width measured for it; measured when either, or the mark, changes. */
    @Nullable private String mChipText;
    private float mChipTextPx;
    private float mChipMarkPx;
    private final TextPaint mChipPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    /** The current window's name width at the mark's size; 0 on a vertical line or with no chip. */
    private float chipTextWidth(@NonNull List<PageBarModel.Entry> entries) {
        int i = mVertical ? -1 : MarksNameChip.entryIndex(entries);
        if (i < 0) {
            mChipText = null;
            mChipTextPx = 0f;
            return 0f;
        }
        String text = MarksNameChip.text(entries.get(i));
        if (!text.equals(mChipText) || mChipMarkPx != mMarkPx) {
            mChipPaint.setTextSize(mMarkPx * MarksNameChip.TEXT_SHARE);
            mChipText = text;
            mChipMarkPx = mMarkPx;
            mChipTextPx = mChipPaint.measureText(text);
        }
        return mChipTextPx;
    }

    private void rebuildLabels() {
        MarksRowLayout rest = mRest;
        int n = rest == null ? 0 : rest.slotCount();
        if (mSlotLabels.length != n) mSlotLabels = new String[n];
        mOverviewTitle = getResources().getString(R.string.overview_title);
        for (int i = 0; i < n; i++) {
            PageBarFold.Slot slot = rest.slot(i);
            if (slot == null) {
                mSlotLabels[i] = mOverviewTitle;
            } else if (slot.folded()) {
                mSlotLabels[i] = getResources().getQuantityString(R.plurals.page_bar_folded,
                    slot.count, getResources().getString(kindName(slot.kind)), slot.count);
            } else {
                mSlotLabels[i] = rest.entries().get(slot.first).label;
            }
        }
    }

    private static int kindName(@NonNull PageKind kind) {
        switch (kind) {
            case HOME: return R.string.page_switch_home;
            case TERMINAL: return R.string.page_switch_terminal;
            case ASSISTANT: return R.string.page_switch_assistant;
            default: return R.string.page_switch_display;
        }
    }

    /** The names the screen reader speaks, one per slot in order; for tests. */
    @NonNull
    String[] slotLabels() {
        return mSlotLabels.clone();
    }

    private static boolean sameEntries(@Nullable PageBarModel a, @Nullable PageBarModel b) {
        if (a == b) return true;
        if (a == null || b == null || a.entries().size() != b.entries().size()) return false;
        for (int i = 0; i < a.entries().size(); i++) {
            PageBarModel.Entry x = a.entries().get(i);
            PageBarModel.Entry y = b.entries().get(i);
            if (x.kind != y.kind || x.number != y.number || x.current != y.current
                || !x.label.equals(y.label)
                || !TextUtils.equals(x.iconKey, y.iconKey) || !TextUtils.equals(x.glyph, y.glyph)
                || x.returnTo != y.returnTo || x.target.action != y.target.action
                || x.target.pageId != y.target.pageId || x.target.index != y.target.index) {
                return false;
            }
        }
        return true;
    }

    /** Asks the icons of the entries that name one, and the glyphs' face; on a model change only. */
    private void resolveIcons(boolean log) {
        PageBarModel model = mModel;
        int n = model == null ? 0 : model.entries().size();
        if (mEntryIcons.length != n) mEntryIcons = new Drawable[n];
        PageMarkIcons icons = mIcons;
        mGlyphFace = icons == null ? null : icons.glyphTypeface();
        for (int i = 0; i < n; i++) {
            String key = model.entries().get(i).iconKey;
            mEntryIcons[i] = icons == null || key == null ? null : icons.icon(key);
            if (log && key != null) {
                android.util.Log.i(TAG, "icon key=" + key + " "
                    + (mEntryIcons[i] != null ? "found" : "missing") + " entry=" + i);
            }
        }
    }

    private boolean missingIcon() {
        PageBarModel model = mModel;
        if (model == null || mIcons == null) return false;
        for (int i = 0; i < model.entries().size() && i < mEntryIcons.length; i++) {
            if (model.entries().get(i).iconKey != null && mEntryIcons[i] == null) return true;
        }
        return false;
    }

    // ---- ink --------------------------------------------------------------------------------

    /**
     * Solves the inks on the band as drawn, when the measurement could have moved (a new sample
     * generation, the band moved, or the palette changed). Int and rect comparisons otherwise.
     *
     * @return whether the inks changed
     */
    private boolean syncInk() {
        if (mFixedGround) {
            // Home's pill: its own glass is the ground, measured by its owner.
            mBandSurface = mFixedBand;
            int surfaceBase = MaterialColors.getColor(this,
                com.termux.shared.R.attr.termuxColorSurfaceBase,
                ContextCompat.getColor(getContext(), R.color.termux_surface_base));
            MarksLineInk solved = MarksLineInk.onBand(mFixedBand, mFixedPale,
                WindowChipInk.neutralSeed(mNeutral, surfaceBase, mFixedPale), mAccent, mError);
            boolean changed = !sameInk(solved, mInk);
            mInk = solved;
            mInkMeasured = true;
            mAuthored = false;
            return changed;
        }
        ChromeInk chrome = mChromeInk;
        OnGlass.Resolution resolved = chrome == null ? null
            : chrome.resolution(GlassBackdropCache.Band.STATUS_BAR);
        if (resolved == null || !chrome.bandRect(GlassBackdropCache.Band.STATUS_BAR, mBandRect)) {
            mBandSurface = 0;
            // Nothing can say what the line stands on yet: the roles themselves, made once.
            if (mAuthored && !mInkMeasured) return false;
            mInkMeasured = false;
            mAuthored = true;
            MarksLineInk authored = MarksLineInk.authored(mNeutral, mAccent, mError);
            boolean changed = !sameInk(authored, mInk);
            mInk = authored;
            return changed;
        }
        if (mInkMeasured && chrome.backdrops().generation() == mInkGeneration
            && mMeasuredRect.equals(mBandRect)) {
            return false;
        }
        mBandSurface = OnGlass.opaque(resolved.surface);
        boolean pale = chrome.polarity() == ChromeInk.Polarity.PALE_INK;
        int surfaceBase = MaterialColors.getColor(this,
            com.termux.shared.R.attr.termuxColorSurfaceBase,
            ContextCompat.getColor(getContext(), R.color.termux_surface_base));
        MarksLineInk solved = MarksLineInk.onBand(resolved.surface, pale,
            WindowChipInk.neutralSeed(mNeutral, surfaceBase, pale), mAccent, mError);
        boolean changed = !sameInk(solved, mInk);
        mInk = solved;
        mInkMeasured = true;
        mAuthored = false;
        mInkGeneration = chrome.backdrops().generation();
        mMeasuredRect.set(mBandRect);
        return changed;
    }

    private static boolean sameInk(MarksLineInk a, MarksLineInk b) {
        return a.current == b.current && a.other == b.other && a.attention == b.attention;
    }

    // ---- draw -------------------------------------------------------------------------------

    private float crossCentre() {
        return (mVertical ? getWidth() : getHeight()) / 2f;
    }

    private float xAt(float along, float cross) {
        if (mVertical) return cross;
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL ? getWidth() - along : along;
    }

    private float yAt(float along, float cross) {
        return mVertical ? along : cross;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        syncInk();
        if (mZoomShown) return;
        if (mTouch != null && mPlan != null) {
            // No chrome layer to stand in: the touch draws inside the line itself.
            drawZoom(canvas);
            return;
        }
        MarksRowLayout rest = mRest;
        if (rest == null) return;
        float cross = crossCentre();
        float size = mMarkPx;
        int save = canvas.save();
        if (rest.overflow()) canvas.clipRect(0, 0, getWidth(), getHeight());
        for (int s = 0, n = rest.slotCount(); s < n; s++) {
            float along = rest.markCentreAt(s);
            float cx = xAt(along, cross);
            float cy = yAt(along, cross);
            PageBarFold.Slot slot = rest.slot(s);
            if (slot == null) {
                mLook.drawOverview(canvas, cx, cy, size, mInk.other);
                continue;
            }
            int ink = slot.current ? mInk.current : mInk.other;
            drawSlot(canvas, rest.entries(), slot, slot.first, slot.current, cx, cy, size, ink,
                mInk.other);
            if (s == rest.chipSlot() && mChipText != null) drawChip(canvas, rest, cy);
            if (rest.attention(s)) mLook.drawBurst(canvas, cx, cy, size, mInk.attention);
        }
        canvas.restoreToCount(save);
    }

    /** The chip's name after its mark, in the row's ink; the row is horizontal. */
    private void drawChip(@NonNull Canvas canvas, @NonNull MarksRowLayout rest, float cy) {
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float start = rest.chipTextStart();
        float x = rtl ? getWidth() - start - rest.chipTextPx() : start;
        mChipPaint.setColor(mInk.other);
        mChipPaint.getFontMetrics(mTitleMetrics);
        float baseline = cy - (mTitleMetrics.ascent + mTitleMetrics.descent) / 2f;
        canvas.drawText(mChipText, x, baseline, mChipPaint);
    }

    /** One slot's mark: a folded stack, or entry {@code entry}'s own mark. */
    private void drawSlot(@NonNull Canvas canvas, @NonNull List<PageBarModel.Entry> entries,
                          @NonNull PageBarFold.Slot slot, int entry, boolean current, float cx,
                          float cy, float size, int ink, int plateInk) {
        if (slot.folded()) {
            mLook.drawStack(canvas, slot.kind, slot.count, current, AT_REST, cx, cy, size, ink,
                plateInk);
            return;
        }
        PageBarModel.Entry e = entries.get(entry);
        Drawable icon = entry < mEntryIcons.length ? mEntryIcons[entry] : null;
        if (icon != null) {
            mLook.drawIcon(canvas, icon, current, cx, cy, size);
        } else if (e.glyph != null && mGlyphFace != null && e.kind == PageKind.TERMINAL) {
            mLook.drawGlyph(canvas, e.glyph, mGlyphFace, cx, cy, size, ink);
        } else {
            mLook.draw(canvas, e.kind, e.number, current, AT_REST, cx, cy, size, ink);
        }
    }

    /**
     * The touch in the line's own coordinates: every mark of the touch layout at its zoom, the
     * current page and the focus in the accent, and the title. The zoom view calls it with its
     * canvas moved onto the line; allocates nothing.
     */
    @Override
    public void drawZoom(@NonNull Canvas canvas) {
        MarksTouch touch = mTouch;
        MarksZoomPlan plan = mPlan;
        MarksTouchLayout layout = touch == null ? null : touch.layout();
        if (touch == null && mTailPlan != null && mTailTitle != null) {
            // The lift's tail: only the name is left, fading out over its capsule.
            paintTitle(canvas, mTailPlan, mTailTitle, mTailPlan.markCross());
            return;
        }
        if (layout == null || plan == null) return;
        // On the glass the marks wear the inks solved on it; drawn in the line, the band's.
        MarksLineInk inks = mInk;
        float cross = mZoomShown ? plan.markCross() : crossCentre();
        float finger = touch.focusPos();
        int focus = touch.focusSlot();
        List<PageBarModel.Entry> entries = mRest == null ? null : mRest.entries();
        float capsuleWidth = mFade > 0f ? mCapsuleWidth : 0f;
        for (int s = 0, n = layout.slotCount(); s < n; s++) {
            float size = mMarkPx * layout.scaleAt(s, finger);
            float along = layout.centreAt(s);
            float cx = xAt(along, cross);
            float cy = yAt(along, cross);
            boolean focused = s == focus;
            if (capsuleWidth > 0f && plan.covers(along, mTitleProgress, capsuleWidth)) continue;
            PageBarFold.Slot slot = layout.slot(s);
            if (slot == null) {
                mLook.drawOverview(canvas, cx, cy, size, focused ? inks.current : inks.other);
                continue;
            }
            if (entries == null) continue;
            boolean strong = slot.current || focused;
            int ink = strong ? inks.current : inks.other;
            drawSlot(canvas, entries, slot, slot.first, strong, cx, cy, size, ink,
                inks.other);
            if (layout.attention(s)) mLook.drawBurst(canvas, cx, cy, size, inks.attention);
        }
        drawTitle(canvas, layout, touch, plan, cross);
    }

    /** The focus's name at the row's end, or going round to its start; clipped to the row. */
    private void drawTitle(@NonNull Canvas canvas, @NonNull MarksTouchLayout layout,
                           @NonNull MarksTouch touch, @NonNull MarksZoomPlan plan, float cross) {
        if (mVertical || plan.titleWidth() <= 0f) return;
        int slot = touch.focusSlot();
        if (slot < 0 || slot >= layout.slotCount()) return;
        int entry = layout.entryIndex(slot, touch.focusMember());
        String title = entry == MarksTouch.OVERVIEW_ENTRY ? mTitles.length > 0
            ? mTitles[mTitles.length - 1] : null
            : entry >= 0 && entry < mTitles.length - 1 ? mTitles[entry] : null;
        if (title == null || title.isEmpty()) return;
        paintTitle(canvas, plan, title, cross);
    }

    /** The name in the capsule's place at the fade's strength, round the row's ends as the plan says. */
    private void paintTitle(@NonNull Canvas canvas, @NonNull MarksZoomPlan plan,
                            @NonNull String title, float cross) {
        float width = mTitlePaint.measureText(title);
        float inset = MarksZoomPlan.CAPSULE_INSET_DP * mDensity;
        float baseline = cross - (mTitleMetrics.ascent + mTitleMetrics.descent) / 2f;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float lineLeft = rtl ? getWidth() - plan.lineEnd() : plan.lineStart();
        float lineRight = rtl ? getWidth() - plan.lineStart() : plan.lineEnd();
        int save = canvas.save();
        canvas.clipRect(lineLeft, plan.frameCrossStart(), lineRight, plan.frameCrossEnd());
        float cw = MarksZoomPlan.capsuleWidthPx(width, mDensity);
        float start = plan.titleStartAt(mTitleProgress, cw) + inset;
        mTitlePaint.setColor(ColorUtils.setAlphaComponent(mTitleInk,
            Math.round(Color.alpha(mTitleInk) * mFade)));
        canvas.drawText(title, rtl ? getWidth() - start - width : start, baseline, mTitlePaint);
        if (mTitleProgress > 0f) {
            float wrap = plan.titleWrapStartAt(mTitleProgress, cw) + inset;
            canvas.drawText(title, rtl ? getWidth() - wrap - width : wrap, baseline, mTitlePaint);
        }
        canvas.restoreToCount(save);
    }

    // ---- touch ------------------------------------------------------------------------------

    /**
     * Whether a press at ({@code x}, {@code y}) in this view lands on a mark: the line then owns
     * the gesture, so the status bar's long-press popup never arms over it (SurfaceLongPress).
     */
    public boolean holdsMarkAt(float x, float y) {
        MarksRowLayout rest = mRest;
        return rest != null && rest.slotAt(along(x, y)) >= 0;
    }

    private float along(float x, float y) {
        if (mVertical) return y;
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL ? getWidth() - x : x;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        MarksRowLayout rest = mRest;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                endTouch("restart");
                float pos = along(event.getX(), event.getY());
                mDown = rest != null && rest.slotAt(pos) >= 0;
                if (!mDown) return false;
                mDownX = event.getX();
                mDownY = event.getY();
                startTouch(rest, pos);
                // The gesture that starts on a mark is the line's: no parent takes it at the
                // slop, so a slow slide is never read as the bar's own drag.
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                MarksTouch touch = mTouch;
                if (!mDown || touch == null) return false;
                float across = Math.abs(mVertical ? event.getX() - mDownX : event.getY() - mDownY);
                if (touch.state() != PageBarSlide.State.SLIDING && across > mTouchSlop) {
                    // Off the line across its axis before sliding: not a tap, not a slide. The
                    // parents may have it again, as they had the old chips' cross swipe.
                    endTouch("gave-up-across");
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    return false;
                }
                mFinger = along(event.getX(), event.getY());
                touch.move(mFinger);
                if (touch.edgeChanged()) {
                    int edge = touch.edge();
                    android.util.Log.i(TAG, edge < 0 ? "past start" : edge > 0 ? "past end"
                        : "back on the marks");
                }
                if (touch.focusChanged()) {
                    android.util.Log.i(TAG, "focus " + focusLabel() + " raw=" + mFinger
                        + " clamped=" + touch.focusPos());
                    if (mHaptics != null) mHaptics.crossed();
                }
                syncTitlePlace();
                syncHostTitle();
                invalidateZoom();
                return true;
            }
            case MotionEvent.ACTION_UP: {
                MarksTouch touch = mTouch;
                boolean wasDown = mDown;
                PageBarSlide.Result result = touch == null ? PageBarSlide.Result.NONE
                    : touch.up(along(event.getX(), event.getY()));
                int entry = touch == null ? -1 : touch.entryIndex();
                List<PageBarModel.Entry> entries = rest == null ? null : rest.entries();
                mEndTarget = entry == MarksTouch.OVERVIEW_ENTRY ? "overview"
                    : entries != null && entry >= 0 && entry < entries.size()
                    ? entries.get(entry).label : null;
                endTouch(result == PageBarSlide.Result.TAP ? "tap"
                    : result == PageBarSlide.Result.COMMIT ? "commit" : "cancel-back-on-start",
                    true);
                if (!wasDown) return false;
                if (result == PageBarSlide.Result.TAP || result == PageBarSlide.Result.COMMIT) {
                    act(entries, entry, result == PageBarSlide.Result.COMMIT);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                endTouch("cancel");
                return false;
            default:
                return mDown;
        }
    }

    /** The chosen entry's page, or Overview; with the click after a slide. The current page does nothing. */
    private void act(@Nullable List<PageBarModel.Entry> entries, int entry, boolean slid) {
        PageSwitchTargets.Navigator nav = mNavigator;
        if (nav == null) return;
        if (entry == MarksTouch.OVERVIEW_ENTRY) {
            if (slid && mHaptics != null) mHaptics.committed();
            nav.openOverview();
            return;
        }
        if (entries == null || entry < 0 || entry >= entries.size()) return;
        PageBarModel.Target target = entries.get(entry).target;
        if (target.action == PageBarModel.Action.CLOSE) return;
        if (slid && mHaptics != null) mHaptics.committed();
        PageSwitchTargets.carryOut(target, nav);
    }

    /**
     * The touch begins: the layout over the whole row, the zoom in the chrome layer, the titles,
     * and the readings fading away. Everything here is made once for the touch.
     */
    private void startTouch(@NonNull MarksRowLayout rest, float pos) {
        View row = rowAncestor();
        float lineStart = 0f;
        float lineEnd = Math.max(rest.contentLengthPx(), mVertical ? getHeight() : getWidth());
        if (row != null) {
            getLocationInWindow(mLoc);
            row.getLocationInWindow(mOtherLoc);
            if (mVertical) {
                float top = mOtherLoc[1] - mLoc[1];
                lineStart = top + row.getPaddingTop();
                lineEnd = top + row.getHeight() - row.getPaddingBottom();
            } else {
                float left = mOtherLoc[0] - mLoc[0] + row.getPaddingLeft();
                float right = mOtherLoc[0] - mLoc[0] + row.getWidth() - row.getPaddingRight();
                boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
                lineStart = rtl ? getWidth() - right : left;
                lineEnd = rtl ? getWidth() - left : right;
            }
            // The marks are the row's: it never starts after them or ends before them.
            lineStart = Math.min(lineStart, 0f);
            lineEnd = Math.max(lineEnd, rest.contentLengthPx());
        } else if (!mVertical && getParent() instanceof View) {
            // Home's pill: the marks spread over the room the pane leaves, and the pill widens.
            lineEnd = Math.max(lineEnd, MarksPillPolicy.touchRoomPx(
                ((View) getParent()).getWidth(), getLeft(), mDensity));
        }
        MarksTouch touch = new MarksTouch(rest, mDensity, mTouchSlop);
        touch.down(pos, lineEnd);
        MarksTouchLayout layout = touch.layout();
        if (layout == null) return;
        mTouch = touch;
        mFinger = pos;
        View readings = mVertical || row == null ? null : row.findViewById(R.id.terminal_status_widgets);
        if (readings != null && !readings.isShown()) readings = null;
        float titleMax = mVertical || !mTitleShown ? 0f : MarksZoomPlan.titleMaxPx(
            readings == null ? 0f : readings.getWidth(), lineEnd - lineStart);
        mPlan = new MarksZoomPlan(layout, mMarkPx, lineStart, lineEnd, titleMax,
            0f, mVertical ? getWidth() : getHeight());
        prepareTitles(rest.entries(), Math.max(0f, mPlan.titleWidth()
            - 2f * MarksZoomPlan.CAPSULE_INSET_DP * mDensity));
        mTitleAtStart = mPlan.titleAtStart(mFinger, touch.focusPos());
        mTitleProgress = mTitleAtStart ? 1f : 0f;
        mReadings = mPlan.titleWidth() > 0f ? readings : null;
        mDownInfo = (mVertical ? "column" : row == null ? "pill" : "row") + " slot="
            + rest.slotAt(pos) + " anchor=" + focusLabel() + " anchorAt="
            + layout.centreAt(layout.anchorSlot()) + " pitch=" + layout.pitchPx()
            + " extent=[" + lineStart + "," + lineEnd + "]"
            + " width=" + (mVertical ? getHeight() : getWidth())
            + " first=" + layout.firstCentre() + " last=" + layout.lastCentre();
        TitleHost host = mTitleHost;
        float room = host == null || mVertical ? 0f : host.roomPx();
        if (room > 0f) {
            // Home's pill: the name shows in Home's bar, whose readings make room for it.
            prepareTitles(rest.entries(), room);
            mReadings = host.readings();
            mHostActive = true;
            mHostText = null;
            syncHostTitle();
        }
        if (mHostActive) dressHost();
        fadeIn();
        showZoom();
        invalidate();
    }

    /** The row the line belongs to (the status row), or null when it stands on its own. */
    @Nullable
    private View rowAncestor() {
        for (ViewParent p = getParent(); p instanceof View; p = p.getParent()) {
            if (((View) p).getId() == R.id.terminal_status_row) return (View) p;
        }
        return null;
    }

    /** Every entry's name fitted to the title's room, Overview's last; once per touch. */
    private void prepareTitles(@NonNull List<PageBarModel.Entry> entries, float widthPx) {
        mTitlePaint.setTextSize(mMarkPx * MarksZoomPlan.TITLE_TEXT_SHARE);
        mTitlePaint.getFontMetrics(mTitleMetrics);
        int n = entries.size();
        if (mTitles.length != n + 1) mTitles = new String[n + 1];
        if (widthPx <= 0f) {
            Arrays.fill(mTitles, null);
            return;
        }
        for (int i = 0; i < n; i++) mTitles[i] = fitted(entries.get(i).label, widthPx);
        mTitles[n] = fitted(mOverviewTitle, widthPx);
    }

    @NonNull
    private String fitted(@NonNull String text, float widthPx) {
        return TextUtils.ellipsize(PageBarModel.plainLabel(text), mTitlePaint, widthPx,
            TextUtils.TruncateAt.END).toString();
    }

    /** The title goes round to the row's start while the finger is over its place, and back. */
    private void syncTitlePlace() {
        MarksZoomPlan plan = mPlan;
        if (plan == null || plan.titleWidth() <= 0f) return;
        MarksTouch touch = mTouch;
        boolean atStart = touch == null ? plan.titleAtStart(mFinger)
            : plan.titleAtStart(mFinger, touch.focusPos());
        if (atStart == mTitleAtStart) return;
        mTitleAtStart = atStart;
        android.util.Log.i(TAG, "title " + (atStart ? "start" : "end"));
        float to = atStart ? 1f : 0f;
        ValueAnimator slide = mTitleSlide;
        if (slide != null) slide.cancel();
        if (still()) {
            mTitleProgress = to;
            return;
        }
        if (slide == null) {
            slide = new ValueAnimator();
            slide.setDuration(MarksZoomPlan.TITLE_SLIDE_MS);
            slide.addUpdateListener(a -> {
                mTitleProgress = (Float) a.getAnimatedValue();
                invalidateZoom();
            });
            mTitleSlide = slide;
        }
        slide.setFloatValues(mTitleProgress, to);
        slide.start();
    }

    private boolean still() {
        BooleanSupplier reduced = mReducedMotion;
        boolean off = reduced != null ? reduced.getAsBoolean() : ReducedMotion.isEnabled(getContext());
        return off || !isAttachedToWindow();
    }

    /**
     * The readings give way to the capsule and its name: one progress value drives both, over the
     * time and curve the readings fade by. At once under reduced motion.
     */
    private void fadeIn() {
        ValueAnimator anim = mFadeAnim;
        if (anim != null) anim.cancel();
        if (still()) {
            setFade(1f);
            return;
        }
        setFade(0f);
        anim = fadeAnim();
        anim.setFloatValues(0f, 1f);
        anim.start();
    }

    private void setFade(float fade) {
        mFade = fade;
        View readings = mReadings;
        if (readings != null) readings.setAlpha(1f - fade);
        TitleHost host = mTitleHost;
        if (mHostActive && host != null) host.fade(fade);
        invalidateZoom();
    }

    /** The lift: the fade runs back, the readings return as the capsule and the name leave. */
    private void fadeOut() {
        ValueAnimator anim = fadeAnim();
        anim.cancel();
        anim.setFloatValues(mFade, 0f);
        anim.start();
    }

    @NonNull
    private ValueAnimator fadeAnim() {
        ValueAnimator anim = mFadeAnim;
        if (anim == null) {
            anim = new ValueAnimator();
            anim.setDuration(MarksZoomPlan.READINGS_FADE_MS);
            anim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            anim.addUpdateListener(a -> setFade((Float) a.getAnimatedValue()));
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                private boolean mCancelled;

                @Override public void onAnimationStart(android.animation.Animator a) {
                    mCancelled = false;
                }

                @Override public void onAnimationCancel(android.animation.Animator a) {
                    mCancelled = true;
                }

                @Override public void onAnimationEnd(android.animation.Animator a) {
                    // Only a fade that ran its course ends the tail; every cut is the caller's.
                    if (!mCancelled && mTouch == null) dropTail();
                }
            });
            mFadeAnim = anim;
        }
        return anim;
    }

    /**
     * Ends the lift's tail at once: the capsule and the name go, the readings are whole, and the
     * host's title is cleared. Safe in any state.
     */
    private void dropTail() {
        boolean tail = mTailPlan != null || (mHostActive && mTouch == null);
        if (!tail) return;
        ValueAnimator fade = mFadeAnim;
        if (fade != null) fade.cancel();
        mTailPlan = null;
        mTailTitle = null;
        mFade = 0f;
        mCapsuleTitle = null;
        mCapsuleWidth = 0f;
        mTitleProgress = 0f;
        View readings = mReadings;
        if (readings != null) readings.setAlpha(1f);
        mReadings = null;
        if (mHostActive) {
            mHostActive = false;
            mHostText = null;
            TitleHost host = mTitleHost;
            if (host != null) {
                host.clear();
                host.dress(null);
            }
        }
        MarksZoomView zoom = mZoom;
        if (zoom != null) {
            leaveLayer(zoom);
            zoom.release();
        }
        invalidate();
    }

    /** The readings come back at the lift; a short fade. */
    private void fadeReadings(boolean away) {
        View readings = mReadings;
        if (readings == null) return;
        float to = away ? 0f : 1f;
        readings.animate().cancel();
        if (still()) {
            readings.setAlpha(to);
        } else {
            readings.animate().alpha(to).setDuration(MarksZoomPlan.READINGS_FADE_MS);
        }
    }

    private void invalidateZoom() {
        MarksZoomView zoom = mZoom;
        if ((mZoomShown || mTailPlan != null) && zoom != null) {
            syncCapsules(zoom);
            zoom.invalidate();
        } else {
            invalidate();
        }
    }

    /**
     * Puts the zoom in the chrome layer, sized once to the plan's frame and moved over the line.
     * With no chrome layer the line draws the touch itself.
     */
    private void showZoom() {
        MarksZoomPlan plan = mPlan;
        if (plan == null || !isAttachedToWindow()) return;
        ViewGroup host = WindowLayers.host(id -> getRootView().findViewById(id), WindowLayer.CHROME);
        if (host == null) return;
        MarksZoomView zoom = mZoom;
        if (zoom == null) {
            zoom = new MarksZoomView(getContext());
            mZoom = zoom;
        }
        boolean rtl = !mVertical && getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        // The frame in the line's own x and y.
        float fs = plan.frameStart();
        float fe = plan.frameEnd();
        float fcs = plan.frameCrossStart();
        float fce = plan.frameCrossEnd();
        float frameLeft = mVertical ? fcs : rtl ? getWidth() - fe : fs;
        float frameRight = mVertical ? fce : rtl ? getWidth() - fs : fe;
        float frameTop = mVertical ? fs : fcs;
        float frameBottom = mVertical ? fe : fce;
        mFrameLeft = frameLeft;
        mFrameTop = frameTop;
        float lineLeft = mVertical ? 0f : rtl ? getWidth() - plan.lineEnd() : plan.lineStart();
        float lineRight = mVertical ? getWidth() : rtl ? getWidth() - plan.lineStart() : plan.lineEnd();
        zoom.place(this, -frameLeft, -frameTop, lineLeft - frameLeft, 0f, lineRight - frameLeft,
            frameBottom - frameTop);
        getLocationInWindow(mLoc);
        host.getLocationInWindow(mOtherLoc);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            (int) Math.ceil(frameRight - frameLeft), (int) Math.ceil(frameBottom - frameTop));
        params.gravity = Gravity.TOP | Gravity.LEFT;
        zoom.setTranslationX(mLoc[0] - mOtherLoc[0] + frameLeft);
        zoom.setTranslationY(mLoc[1] - mOtherLoc[1] + frameTop);
        leaveLayer(zoom);
        if (!WindowLayers.add(id -> getRootView().findViewById(id), WindowLayer.CHROME, zoom,
            params)) {
            zoom.release();
            return;
        }
        mZoomShown = true;
        zoomChanged();
        TitleGlass glass = mTitleGlass;
        mTitleInk = mInk.other;
        if (glass != null && plan.titleWidth() > 0f) {
            float radius = (plan.rowCrossEnd() - plan.rowCrossStart()) / 2f;
            for (int i = 0; i < MarksZoomView.CAPSULES; i++) {
                Drawable ground = glass.ground(radius, zoom.capsuleView(i));
                if (ground != null) GroundUnderGlass.bind(ground, zoom.capsuleView(i));
                zoom.setCapsuleGlass(i, ground);
            }
            mTitleInk = inkOnGlass(glass.tint()).other;
        }
        syncCapsules(zoom);
        android.util.Log.i(TAG, "down " + mDownInfo + " backdrop=none layer="
            + (zoom.getParent() instanceof View ? "chrome" : "none"));
    }

    /**
     * Puts the title's capsule (and its other half, coming in from the row's start) where the
     * title stands now, at the fade's strength; hidden with no title. Moves by translation only.
     */
    private void syncCapsules(@NonNull MarksZoomView zoom) {
        MarksTouch touch = mTouch;
        MarksZoomPlan plan = touch != null ? mPlan : mTailPlan;
        String title = plan == null || mVertical || plan.titleWidth() <= 0f ? null
            : touch != null ? titleOf(touch) : mTailTitle;
        if (title != mCapsuleTitle) {
            mCapsuleTitle = title;
            mCapsuleWidth = title == null ? 0f
                : MarksZoomPlan.capsuleWidthPx(mTitlePaint.measureText(title), mDensity);
        }
        if (title == null || plan == null) {
            zoom.placeCapsule(0, 0f, 0f, 0, 0, 0f, false);
            zoom.placeCapsule(1, 0f, 0f, 0, 0, 0f, false);
            return;
        }
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int width = Math.round(mCapsuleWidth);
        int height = Math.round(plan.rowCrossEnd() - plan.rowCrossStart());
        float top = plan.rowCrossStart() - mFrameTop;
        float start = plan.titleStartAt(mTitleProgress, mCapsuleWidth);
        zoom.placeCapsule(0, (rtl ? getWidth() - start - mCapsuleWidth : start) - mFrameLeft, top,
            width, height, mFade, mFade > 0f);
        float wrap = plan.titleWrapStartAt(mTitleProgress, mCapsuleWidth);
        zoom.placeCapsule(1, (rtl ? getWidth() - wrap - mCapsuleWidth : wrap) - mFrameLeft, top,
            width, height, mFade, mFade > 0f && mTitleProgress > 0f);
    }

    /**
     * Home's bar shows the name in the same glass capsule: built like the row's, over the stored
     * backdrop, its ink solved once now.
     */
    private void dressHost() {
        TitleHost host = mTitleHost;
        TitleGlass glass = mTitleGlass;
        mTitleInk = mInk.other;
        View view = host == null ? null : host.titleView();
        if (view == null || glass == null) return;
        Drawable ground = glass.ground(Math.max(view.getHeight(), 1) / 2f, view);
        if (ground != null) GroundUnderGlass.bind(ground, view);
        host.dress(ground);
        mTitleInk = inkOnGlass(glass.tint()).other;
        mHostText = null;
        syncHostTitle();
    }

    /** The name of the mark in focus, or null for none. */
    @Nullable
    private String titleOf(@NonNull MarksTouch touch) {
        MarksTouchLayout layout = touch.layout();
        int slot = touch.focusSlot();
        if (layout == null || slot < 0 || slot >= layout.slotCount()) return null;
        int entry = layout.entryIndex(slot, touch.focusMember());
        String title = entry == MarksTouch.OVERVIEW_ENTRY ? mTitles.length > 0
            ? mTitles[mTitles.length - 1] : null
            : entry >= 0 && entry < mTitles.length - 1 ? mTitles[entry] : null;
        return title == null || title.isEmpty() ? null : title;
    }

    /**
     * The inks solved on the capsule's glass as the line's are on its band ({@link MarksLineInk#onBand}):
     * Global's tint over what the line stands on (and the Display page's band, when there is one).
     */
    @NonNull
    private MarksLineInk inkOnGlass(int tint) {
        int surfaceBase = MaterialColors.getColor(this,
            com.termux.shared.R.attr.termuxColorSurfaceBase,
            ContextCompat.getColor(getContext(), R.color.termux_surface_base));
        int under = OnGlass.opaque(mBandSurface != 0 ? mBandSurface : surfaceBase);
        if (mZoomBand != 0) under = ColorUtils.compositeColors(mZoomBand, under);
        int ground = OnGlass.opaque(ColorUtils.compositeColors(tint, under));
        boolean pale = ColorUtils.calculateLuminance(ground) < PALE_INK_BELOW;
        return MarksLineInk.onBand(ground, pale,
            WindowChipInk.neutralSeed(mNeutral, surfaceBase, pale), mAccent, mError);
    }

    private static void detachZoom(@NonNull View zoom) {
        ViewParent parent = zoom.getParent();
        if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(zoom);
    }

    /** Takes the zoom out of its layer host, saying so when it was in one. */
    private static void leaveLayer(@NonNull View zoom) {
        boolean was = zoom.getParent() instanceof ViewGroup;
        detachZoom(zoom);
        if (was) android.util.Log.i(TAG, "gone");
    }

    private static final String TAG = "MARKS";
    /** The page the touch went to, for the end's line. */
    @Nullable private String mEndTarget;
    /** What the touch-down line says of the touch, before the zoom is placed. */
    @Nullable private String mDownInfo;

    /** The name of the mark under the touch's focus, or "none". */
    @NonNull
    private String focusLabel() {
        MarksTouch touch = mTouch;
        MarksRowLayout rest = mRest;
        if (touch == null || rest == null) return "none";
        MarksTouchLayout layout = touch.layout();
        int slot = touch.focusSlot();
        if (layout == null || slot < 0 || slot >= layout.slotCount()) return "none";
        int entry = layout.entryIndex(slot, touch.focusMember());
        if (entry == MarksTouch.OVERVIEW_ENTRY) return "overview";
        return entry >= 0 && entry < rest.entries().size() ? rest.entries().get(entry).label
            : "none";
    }

    /** Ends the touch whatever it was doing: the page changed, the row's presentation changed. */
    public void cancelTouch() {
        endTouch("cancel-external");
    }

    /** {@link #cancelTouch()} for a caller that knows why (the keyboard, the look, the presentation). */
    public void cancelTouch(@NonNull String reason) {
        endTouch(reason);
    }

    /** Puts the touch away: the zoom, the title and the readings come back. Safe in any state. */
    private void endTouch(@NonNull String reason) {
        endTouch(reason, false);
    }

    /**
     * The one place a touch is put away. With {@code lift} the capsule and the name fade out as the
     * readings fade back, a short tail that any later end, a new touch or a detach cuts short;
     * every other end removes everything at once.
     */
    private void endTouch(@NonNull String reason, boolean lift) {
        dropTail();
        boolean keepCapsule = lift && mZoomShown && mPlan != null && mZoom != null
            && mCapsuleTitle != null && mFade > 0f && !still();
        boolean keep = keepCapsule || (lift && mHostActive && mFade > 0f && !still());
        if (keepCapsule) {
            mTailPlan = mPlan;
            mTailTitle = mCapsuleTitle;
        }
        MarksTouch touch = mTouch;
        if (touch != null || mZoomShown) {
            android.util.Log.i(TAG, "end " + reason + (mEndTarget == null ? "" : " target="
                + mEndTarget));
        }
        mEndTarget = null;
        if (touch != null) touch.cancel();
        boolean had = touch != null || mZoomShown;
        mTouch = null;
        mPlan = null;
        mDown = false;
        ValueAnimator slide = mTitleSlide;
        if (slide != null) slide.cancel();
        mTitleAtStart = false;
        if (!keep) mTitleProgress = 0f;
        if (keep) {
            fadeOut();
        } else {
            ValueAnimator fade = mFadeAnim;
            if (fade != null) fade.cancel();
            mFade = 0f;
            mCapsuleTitle = null;
            mCapsuleWidth = 0f;
            fadeReadings(false);
            mReadings = null;
            if (mHostActive) {
                mHostActive = false;
                mHostText = null;
                TitleHost host = mTitleHost;
                if (host != null) {
                    host.clear();
                    host.dress(null);
                }
            }
        }
        MarksZoomView zoom = mZoom;
        if (zoom != null && !keepCapsule) {
            leaveLayer(zoom);
            zoom.release();
        }
        boolean wasShown = mZoomShown;
        mZoomShown = false;
        if (wasShown) zoomChanged();
        if (had) invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // Android's Back swipe stays off the row's hit rect over the marks: the marks and, along a
        // row, the touch reach above them, so a slide from the leftmost mark is the line's.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        int w = right - left;
        int h = bottom - top;
        int above = mVertical ? 0 : -Math.round(PaneRowPolicy.TOUCH_ABOVE_DP * mDensity);
        if (mExclusion.right == w && mExclusion.bottom == h && mExclusion.top == above) return;
        mExclusion.set(0, above, w, h);
        android.util.Log.i(TAG, "excl " + mExclusion.toShortString());
        setSystemGestureExclusionRects(w > 0 && h > 0
            ? Collections.singletonList(new Rect(mExclusion)) : Collections.emptyList());
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) endTouch("window-focus");
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE) endTouch("visibility");
    }

    @Override
    protected void onDetachedFromWindow() {
        endTouch("detach");
        super.onDetachedFromWindow();
    }

    // ---- accessibility ------------------------------------------------------------------------

    @Override
    public boolean dispatchHoverEvent(@NonNull MotionEvent event) {
        return mAccess.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    /** One labelled button per slot at rest, Overview's first; a click is a tap on it. */
    private final class Access extends ExploreByTouchHelper {

        Access(@NonNull View host) {
            super(host);
        }

        @Override protected int getVirtualViewAt(float x, float y) {
            MarksRowLayout rest = mRest;
            if (rest == null) return INVALID_ID;
            int slot = rest.slotAt(along(x, y));
            return slot < 0 ? INVALID_ID : slot;
        }

        @Override protected void getVisibleVirtualViews(@NonNull List<Integer> ids) {
            for (int i = 0; i < mSlotLabels.length; i++) ids.add(i);
        }

        @Override protected void onPopulateNodeForVirtualView(int id,
                                                              @NonNull AccessibilityNodeInfoCompat node) {
            MarksRowLayout rest = mRest;
            if (rest == null || id < 0 || id >= mSlotLabels.length || id >= rest.slotCount()) {
                node.setContentDescription("");
                node.setBoundsInParent(new Rect(0, 0, 1, 1));
                return;
            }
            node.setClassName("android.widget.Button");
            node.setContentDescription(mSlotLabels[id]);
            PageBarFold.Slot slot = rest.slot(id);
            boolean current = slot != null && slot.current;
            node.setSelected(current);
            // A tap on the current page does nothing, so it advertises no click.
            if (!current) {
                node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
                node.setClickable(true);
            }
            int start = Math.round(rest.cellStart(id));
            int end = Math.round(rest.cellEnd(id));
            if (mVertical) {
                node.setBoundsInParent(new Rect(0, start, getWidth(), end));
            } else if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
                node.setBoundsInParent(new Rect(getWidth() - end, 0, getWidth() - start, getHeight()));
            } else {
                node.setBoundsInParent(new Rect(start, 0, end, getHeight()));
            }
        }

        @Override protected boolean onPerformActionForVirtualView(int id, int action,
                                                                  @Nullable Bundle arguments) {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false;
            return clickSlot(id);
        }
    }

    /** A tap on slot {@code id} as the touch does it, for a screen reader or a script. */
    boolean clickSlot(int id) {
        MarksRowLayout rest = mRest;
        PageSwitchTargets.Navigator nav = mNavigator;
        if (rest == null || nav == null || id < 0 || id >= rest.slotCount()) return false;
        int entry = rest.tapEntryIndex(id);
        if (entry == MarksRowLayout.OVERVIEW_ENTRY) {
            nav.openOverview();
            return true;
        }
        PageBarModel.Target target = rest.entries().get(entry).target;
        if (target.action == PageBarModel.Action.CLOSE) return false;
        PageSwitchTargets.carryOut(target, nav);
        return true;
    }
}
