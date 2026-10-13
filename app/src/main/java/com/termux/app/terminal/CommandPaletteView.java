package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.chrome.GlassTokens;
import com.termux.app.FocusOutlineRenderer;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardPaletteFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The command palette's drawn surface: a thin bordered glass rectangle reading as a tmux pane —
 * monospace rows, {@code ── CATEGORY} rules, a right-aligned shortcut column — with a strip of
 * frequent-action keycaps under its bottom border.
 *
 * <p>Everything is drawn on one canvas rather than composed from child views because the whole
 * rectangle is spring-animated from the space bar every frame: a view tree would need a measure
 * and layout pass per frame, and the clipped, bottom-anchored viewport with its focus wash and
 * bottom fade has no natural widget anyway.
 *
 * <p>The backdrop blur is not drawn here. The host stacks a {@code RealtimeBlurView} clipped to
 * this rectangle's outline underneath, exactly as the dock does; this view draws the tint,
 * gradient, specular, rim and content over it.
 *
 * <p>Colors resolve through Material roles and every label is contrast-checked against the
 * composited glass with {@link InAppKeyboardPaletteFactory#ensureContrast}, because the live
 * wallpaper shows through the blur.
 */
public final class CommandPaletteView extends View {

    /**
     * Frequent-action keycaps. Four rather than the handoff's six: at six, the row only fit by
     * scaling the caps down until their labels no longer fit inside them, so the strip read as a
     * crowded band of clipped words instead of four legible shortcuts.
     */
    public static final int KEYCAP_COUNT = 4;

    public interface Callbacks {
        /** A tap landed on the row at {@code index} in the current row list. */
        void onRowTapped(int index);

        /** A tap landed on the keycap at {@code index} in the frequent-action strip. */
        void onKeycapTapped(int index);

        /**
         * A tap landed on the trailing chip of the row at {@code index}: the
         * row's own action, the contact results' call button.
         */
        void onRowActionTapped(int index);

        /** A long press landed on the row at {@code index}: the secondary action for that row. */
        void onRowLongPressed(int index);

        /** A tap landed outside the palette and its strip. */
        void onOutsideTapped();
    }

    public static final int KIND_CATEGORY = 0;
    public static final int KIND_ENTRY = 1;
    public static final int KIND_NOTICE = 2;

    /** One line of the ledger: a category rule, an action row, or a full-width notice. */
    public static final class Row {
        final int kind;
        final String primary;
        @Nullable final String description;
        final String shortcut;
        final boolean enabled;
        /** Leading artwork, for app rows; text rows leave the gutter out entirely. */
        @Nullable final Drawable icon;
        /**
         * Trailing chip the row carries, or {@code null} for rows without one.
         * The contact results' call button: the one row kind with an action
         * beside its name, so it is the only kind that draws one.
         */
        @Nullable final Action action;

        private Row(int kind, @NonNull String primary, @Nullable String description,
                    @NonNull String shortcut, boolean enabled, @Nullable Drawable icon,
                    @Nullable Action action) {
            this.kind = kind;
            this.primary = primary;
            this.description = description;
            this.shortcut = shortcut;
            this.enabled = enabled;
            this.icon = icon;
            this.action = action;
        }

        @NonNull
        public static Row category(@NonNull String label) {
            return new Row(KIND_CATEGORY, label, null, "", true, null, null);
        }

        @NonNull
        public static Row notice(@NonNull String text) {
            return new Row(KIND_NOTICE, text, null, "", true, null, null);
        }

        @NonNull
        public static Row entry(@NonNull String title, @Nullable String description,
                                @NonNull String shortcut, boolean enabled) {
            return entry(title, description, shortcut, enabled, null);
        }

        @NonNull
        public static Row entry(@NonNull String title, @Nullable String description,
                                @NonNull String shortcut, boolean enabled,
                                @Nullable Drawable icon) {
            return entry(title, description, shortcut, enabled, icon, null);
        }

        @NonNull
        public static Row entry(@NonNull String title, @Nullable String description,
                                @NonNull String shortcut, boolean enabled,
                                @Nullable Drawable icon, @Nullable Action action) {
            return new Row(KIND_ENTRY, title, description, shortcut, enabled, icon, action);
        }

        /**
         * A trailing chip: an icon drawn inside its own round, so a
         * contact's call button is a button, not a word. Tapping it
         * opens the dialer on the row's number; the user places the call.
         */
        public static final class Action {
            @NonNull public final Drawable icon;

            public Action(@NonNull Drawable icon) {
                this.icon = icon;
            }
        }

        public boolean isSelectable() {
            return kind == KIND_ENTRY;
        }
    }

    /**
     * One keycap of the strip. Three kinds, one family: an action is a rounded key with a glyph
     * (something you do), an app a pill led by its icon (something you open), a contact the same
     * pill led by a monogram (someone). Every kind shares the height, fill, ramp, hairline and
     * label type.
     */
    public static final class Keycap {
        static final int KIND_ACTION = 0;
        static final int KIND_APP = 1;
        static final int KIND_CONTACT = 2;

        final int kind;
        final String glyph;
        final String label;
        /**
         * An app's icon or a contact's circular photo, resolved when the strip is built. Null
         * draws the monogram instead.
         */
        @Nullable final Drawable icon;
        /** The monogram's letter, for a contact or an app without artwork. */
        final String initial;

        // Measured by the view in setKeycaps, so a draw only reads them.
        float mGlyphWidth;
        float mLabelWidth;
        /** Offset from the mark's centre to the initial's draw origin, centred on its ink. */
        float mInitialDx;
        float mInitialDy;
        /** The label as last drawn, ellipsized to {@link #mFitWidth}; refitted only when that moves. */
        @Nullable String mFitted;
        float mFitWidth = -1f;

        /** An action's key: a glyph, then the label. */
        public Keycap(@NonNull String glyph, @NonNull String label) {
            this(KIND_ACTION, glyph, label, null, "");
        }

        private Keycap(int kind, @NonNull String glyph, @NonNull String label,
                       @Nullable Drawable icon, @NonNull String initial) {
            this.kind = kind;
            this.glyph = glyph;
            this.label = label;
            this.icon = icon;
            this.initial = initial;
        }

        /** An app's pill, led by its icon. */
        @NonNull
        public static Keycap app(@NonNull String label, @Nullable Drawable icon) {
            return new Keycap(KIND_APP, "", label, icon, initialOf(label));
        }

        /**
         * A contact's pill, led by {@code photo} (already circular) when the contact has one, and
         * otherwise by the initial of {@code displayName}. The mark is the same size either way,
         * so a photo landing late swaps in without moving anything.
         */
        @NonNull
        public static Keycap contact(@NonNull String label, @NonNull String displayName,
                                     @Nullable Drawable photo) {
            return new Keycap(KIND_CONTACT, "", label, photo, initialOf(displayName));
        }

        /**
         * This contact's pill with its photo arrived. Same kind, label and initial, so the strip
         * measures it exactly as before and nothing moves.
         */
        @NonNull
        public Keycap withPhoto(@NonNull Drawable photo) {
            return new Keycap(kind, glyph, label, photo, initial);
        }

        /** Whether the mark is the monogram rather than artwork (an app icon or a photo). */
        boolean drawsMonogram() {
            return kind != KIND_ACTION && icon == null;
        }

        boolean hasMark() {
            return kind != KIND_ACTION;
        }

        /** The first letter or digit of {@code name}, upper case; {@code #} when it has none. */
        @NonNull
        static String initialOf(@NonNull String name) {
            for (int i = 0; i < name.length(); ) {
                int point = name.codePointAt(i);
                if (Character.isLetterOrDigit(point))
                    return new String(Character.toChars(point)).toUpperCase(
                        java.util.Locale.getDefault());
                i += Character.charCount(point);
            }
            return "#";
        }
    }

    // Geometry, in dp, from the handoff's 412 x 920 dp reference frame.
    private static final float FILTER_ROW_H = 34f;
    private static final float ARG_ROW_H = 30f;
    private static final float CATEGORY_H = 22f;
    private static final float ROW_PAD_V = 6f;
    private static final float ROW_PAD_LEFT = 14f;
    private static final float ROW_PAD_RIGHT = 12f;
    private static final float CATEGORY_PAD_LEFT = 12f;
    private static final float ICON_SIZE = 16f;
    private static final float ICON_GAP = 7f;
    /** The call chip: a round just big enough to hold a phone. */
    private static final float ACTION_CHIP_DP = 20f;
    /** The phone inside the call chip's round. */
    private static final float ACTION_ICON_DP = 12f;
    /** How far outside its drawn round a tap still lands on the call chip: the round alone is too small for a thumb. */
    private static final float ACTION_TAP_SLOP_DP = 12f;
    private static final float FOCUS_BAR_W = 2f;
    private static final float VIEWPORT_BOTTOM_SLACK = 26f;
    private static final float BOTTOM_FADE_H = 28f;
    private static final float STRIP_GAP = 8f;
    private static final float STRIP_LEFT_EXTRA = 2f;
    static final float CAP_GAP = 5f;
    static final float CAP_PAD_H = 8f;
    private static final float CAP_PAD_V = 6f;
    private static final float CAP_RADIUS = 6f;
    /** An action key: from its glyph to the label. */
    static final float CAP_LEAD_GAP = 4f;
    /**
     * An app or contact pill's leading mark (icon or monogram). Its left inset equals its vertical
     * one, so the mark's round sits concentric in the pill's end.
     */
    static final float MARK_SIZE = 14f;
    /** A pill: from its mark to the label. */
    static final float MARK_GAP = 5f;
    /** The hairline round on an app icon's bounds, at 10 % white or black against the chip. */
    private static final int MARK_OUTLINE_ALPHA = 26;
    /** A contact monogram's disc: the primary at 24 % over the chip. */
    private static final int MONOGRAM_FILL_ALPHA = 61;
    private static final float SIZE_MONOGRAM = 8.5f;
    /** The monogram's ceiling, in dp: the disc does not grow with the font scale, so neither may its letter. */
    private static final float MAX_MONOGRAM = 10f;
    private static final float HAIRLINE = 1f;
    /** Head of the argument row when the caller does not supply one of its own. */
    private static final String DEFAULT_ARGUMENT_PROMPT = "arg ❯";
    private static final float SPECULAR_RADIUS = 320f;

    // Drop shadow, drawn here rather than taken from View elevation: the caster would have to be
    // the full-screen glass pane, whose platform shadow came out as a flat band under the bottom
    // edge with square ends that ignored the rounded corners. These rings are concentric with the
    // animated rounded rect, so the corners stay round and the falloff is smooth. Each ring is
    // faint and they accumulate outward-to-inward into a ramp.
    private static final float SHADOW_SPREAD = 14f;
    private static final float SHADOW_DROP = 5f;
    // Many thin rings rather than a few thick ones: at 7 rings the accumulation stepped in
    // measurable ~4-level plateaus, which is the banding that makes a faked shadow look faked.
    private static final int SHADOW_RINGS = 14;
    private static final int SHADOW_RING_ALPHA = 4;

    /** Fling decay per frame, and the speeds at which a fling starts and stops. */
    private static final float FLING_DECAY = 0.90f;
    private static final float FLING_MIN_START_DP = 60f;
    private static final float FLING_MIN_KEEP_DP = 24f;

    // Type sizes, in dp for a stable ledger grid under any font scale.
    private static final float SIZE_CRUMB = 10.5f;
    private static final float SIZE_META = 10f;
    private static final float SIZE_FILTER = 13f;
    private static final float SIZE_CATEGORY = 9.5f;
    private static final float SIZE_ROW = 12.5f;
    private static final float SIZE_DESCRIPTION = 10.5f;
    private static final float SIZE_SHORTCUT = 10f;
    private static final float SIZE_CAP_GLYPH = 11f;
    private static final float SIZE_CAP_LABEL = 10.5f;
    /**
     * Smallest the search hint shrinks to before it ellipsizes: the category rules' size, the
     * smallest type this view already sets.
     */
    private static final float SIZE_PLACEHOLDER_MIN = SIZE_CATEGORY;
    /**
     * Half a blink. The platform's own text cursor is on for this long and off for this long, and
     * the palette's has to read as the same thing: a line that is taking what you type.
     */
    private static final long CARET_BLINK_MS = 500L;
    /** How wide the caret is. A hairline read as part of the glyph beside it. */
    private static final float CARET_W = 2f;

    private final float mDensity;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mMono = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    /** A contact's initial: the mono face, bold, in the primary. */
    private final TextPaint mMonogram = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    /** The 1 px round on an app icon's bounds. */
    private final Paint mMarkOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Rect mInkBounds = new android.graphics.Rect();
    private final RectF mRect = new RectF();
    private final RectF mFrame = new RectF();
    private final Path mShadowClip = new Path();
    /**
     * The rounded frame as a clip path. Every body and list fill is a square-cornered drawRect, so
     * clipping them to a plain rect let anything flush with the frame's bottom paint into the
     * corner arcs the surface's drawRoundRect leaves empty. Cached because it is rebuilt only when
     * the animated frame moves, not once per fill.
     */
    private final Path mFramePath = new Path();
    private boolean mFramePathDirty = true;
    /**
     * Region the overlay is modal over. The view fills the activity so the sprout can start at
     * the space bar, but only this rectangle may swallow touches — everything below it is the
     * in-app keyboard, and taps there have to reach the keys or the palette could never be typed
     * into.
     */
    private final RectF mModalBounds = new RectF();
    /** The caps' rects for the current draw; only the first {@link #mCapCount} are live. */
    private final List<RectF> mCapRects = new ArrayList<>();
    private int mCapCount;
    /**
     * Per cap, for the current draw: the left inset (padding, or the mark's inset), the gap after
     * the glyph or mark, and the room the label gets. Grown, never shrunk, so a draw allocates
     * nothing.
     */
    private float[] mCapLeft = new float[KEYCAP_COUNT];
    private float[] mCapLeadGap = new float[KEYCAP_COUNT];
    private float[] mCapLabelRoom = new float[KEYCAP_COUNT];
    private float[] mCapSorted = new float[KEYCAP_COUNT];
    /**
     * The call chips of the visible rows, by row index. Rebuilt with every
     * draw of the list, like the keycap rects, so a chip can only be tapped
     * where it was just painted.
     */
    private final SparseArray<RectF> mActionRects = new SparseArray<>();

    @Nullable private Callbacks mCallbacks;

    private int mGlassBase;
    private int mPrimary;
    private int mOnSurface;
    private int mOnSurfaceVariant;
    private int mMeta;
    private int mChipFill;
    private int mCapLabel;
    /** White on a dark chip, black on a light one, at {@link #MARK_OUTLINE_ALPHA}. */
    private int mMarkOutlineColor;
    /** The strip's cap height, and its glyph and label baseline below the cap's top. */
    private float mCapHeight;
    private float mCapBaseline;

    private float mRadius;
    private float mBodyAlpha;
    private float mStripAlpha;
    private float mStripOffset;
    private float mProgress;

    private List<Row> mRows = new ArrayList<>();
    private List<Keycap> mKeycaps = new ArrayList<>();
    private int mFocusIndex = -1;
    private String mMetaText = "";
    private String mCrumb = "";
    private String mQuery = "";
    private String mQueryPlaceholder = "";
    /** Width of the frame once fully open; what the hint is fitted to. 0 until the host says. */
    private float mOpenFrameWidth;
    /** The hint's fitted type size, and the hint and open width it was fitted for. */
    private float mPlaceholderSize;
    @Nullable private String mPlaceholderFitText;
    private float mPlaceholderFitWidth = -1f;
    /** Caret index into the active buffer (query or argument value); values past the end clamp. */
    private int mQueryCursor = Integer.MAX_VALUE;
    /** Whether the caret is in its on half. Always true while it is not blinking at all. */
    private boolean mCaretOn = true;
    /** Whether the blink is running; nothing is posted while the palette is closed. */
    private boolean mCaretBlinking;
    private boolean mArgumentMode;
    private String mArgumentPlaceholder = "";
    /** Prompt drawn at the head of the argument row; capture mode replaces the default. */
    @NonNull private String mArgumentPrompt = DEFAULT_ARGUMENT_PROMPT;
    private String mArgumentValue = "";
    private float mScrollOffset;

    private final float mTouchSlop;
    /** True while the offset is the finger's rather than the focused row's. */
    private boolean mUserScrolled;
    private boolean mDragging;
    /** Pending long-press callback, or null when none is armed. */
    @Nullable private Runnable mLongPress;
    /** Set once a long press has acted, so the following UP does not also tap the row. */
    private boolean mLongPressFired;
    private float mDownX;
    private float mDownY;
    private float mLastTouchY;
    @Nullable private VelocityTracker mVelocity;
    private float mFlingVelocity;
    private final Runnable mFlingStep = this::stepFling;

    public CommandPaletteView(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mMono.setTypeface(Typeface.MONOSPACE);
        mMonogram.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeWidth(HAIRLINE * mDensity);
        mMarkOutline.setStyle(Paint.Style.STROKE);
        mMarkOutline.setStrokeWidth(1f);
        setClickable(true);
        refreshPalette();
    }

    public void setCallbacks(@Nullable Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    /** Re-resolves every role. Called on theme, wallpaper-color and configuration changes. */
    public void refreshPalette() {
        Context context = getContext();
        mGlassBase = InAppKeyboardPaletteFactory.resolveDockGlassBaseColor(context);
        mPrimary = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int onSurface = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        int onSurfaceVariant = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            ColorUtils.blendARGB(onSurface, mGlassBase, 0.28f));
        int surfaceContainerHigh = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            ColorUtils.blendARGB(mGlassBase, onSurface, 0.10f));

        // Reference background for the contrast pass: the glass tint plus its darkening
        // gradient, composited down to something opaque to measure against.
        int overGlass = ColorUtils.compositeColors(
            ColorUtils.setAlphaComponent(GlassTokens.SHADE, 40), mGlassBase);
        mOnSurface = InAppKeyboardPaletteFactory.ensureContrast(onSurface, overGlass);
        mOnSurfaceVariant = InAppKeyboardPaletteFactory.ensureContrast(onSurfaceVariant, overGlass);
        mMeta = ColorUtils.setAlphaComponent(mOnSurfaceVariant, 212);
        mPrimary = InAppKeyboardPaletteFactory.ensureContrast(mPrimary, overGlass);

        // Keycaps use the keyboard's chip recipe, so their labels are checked against the
        // chip composited over the glass rather than over the glass alone.
        mChipFill = ColorUtils.setAlphaComponent(surfaceContainerHigh, 160);
        int capOverGlass = ColorUtils.compositeColors(mChipFill, overGlass);
        mCapLabel = InAppKeyboardPaletteFactory.ensureContrast(onSurface, capOverGlass);
        mMarkOutlineColor = ColorUtils.setAlphaComponent(
            ColorUtils.calculateLuminance(capOverGlass) < 0.5 ? Color.WHITE : Color.BLACK,
            MARK_OUTLINE_ALPHA);
        // A configuration change can move the font scale under the measured caps.
        measureKeycaps();
        invalidate();
    }

    /** Sets the animated rectangle and the fades driven by the sprout progress. */
    public void setFrame(@NonNull RectF frame, float radius, float bodyAlpha, float stripAlpha,
                         float stripOffsetPx, float progress) {
        mFrame.set(frame);
        mRadius = radius;
        mFramePathDirty = true;
        mBodyAlpha = clamp01(bodyAlpha);
        mStripAlpha = clamp01(stripAlpha);
        mStripOffset = stripOffsetPx;
        mProgress = clamp01(progress);
        invalidate();
    }

    /**
     * Sets the rectangle inside which taps belong to the palette. Taps outside it fall through to
     * whatever is underneath — in practice the in-app keyboard.
     */
    public void setModalBounds(@NonNull RectF bounds) {
        mModalBounds.set(bounds);
    }

    public void setRows(@NonNull List<Row> rows, int focusIndex) {
        // Any focus move or result change hands the viewport back to the focused row.
        if (focusIndex != mFocusIndex || rows != mRows) resetScroll();
        mRows = rows;
        mFocusIndex = focusIndex;
        // Chips are laid out by the next draw; until then none is tappable.
        mActionRects.clear();
        invalidate();
    }

    /** Drops a manual scroll and any fling, so the next draw follows the focused row again. */
    public void resetScroll() {
        abortFling();
        mUserScrolled = false;
        mScrollOffset = 0f;
    }

    public void setKeycaps(@NonNull List<Keycap> keycaps) {
        if (keycaps == mKeycaps) return;
        mKeycaps = keycaps;
        measureKeycaps();
        invalidate();
    }

    /**
     * Measures what the strip draws once, when the caps or the type scale change: the cap height
     * and baseline, every glyph and label width, and where each initial's ink is centred.
     */
    private void measureKeycaps() {
        mMono.setLetterSpacing(0f);
        mMono.setTextSize(sp(SIZE_CAP_GLYPH));
        Paint.FontMetrics metrics = mMono.getFontMetrics();
        float line = metrics.descent - metrics.ascent;
        mCapHeight = dp(CAP_PAD_V) * 2f + line;
        mCapBaseline = (mCapHeight - line) / 2f - metrics.ascent;
        mMonogram.setTextSize(Math.min(sp(SIZE_MONOGRAM), dp(MAX_MONOGRAM)));
        for (Keycap keycap : mKeycaps) {
            mMono.setTextSize(sp(SIZE_CAP_GLYPH));
            keycap.mGlyphWidth = mMono.measureText(keycap.glyph);
            mMono.setTextSize(sp(SIZE_CAP_LABEL));
            keycap.mLabelWidth = mMono.measureText(keycap.label);
            keycap.mFitted = null;
            keycap.mFitWidth = -1f;
            if (keycap.initial.isEmpty()) continue;
            // Centred on the letter's ink rather than the font's ascent, so a capital sits in
            // the middle of its disc instead of riding high.
            mMonogram.getTextBounds(keycap.initial, 0, keycap.initial.length(), mInkBounds);
            keycap.mInitialDx = mInkBounds.left + mInkBounds.width() / 2f;
            keycap.mInitialDy = mInkBounds.top + mInkBounds.height() / 2f;
        }
    }

    public void setHeader(@NonNull String metaText, @NonNull String crumb) {
        mMetaText = metaText;
        mCrumb = crumb;
        invalidate();
    }

    public void setQuery(@NonNull String query, @NonNull String placeholder) {
        mQuery = query;
        mQueryPlaceholder = placeholder;
        restartCaret();
        invalidate();
    }

    /**
     * The frame's width once the sprout has settled. The search hint is fitted to this rather than
     * to the animated frame, so it keeps one size while the palette grows.
     */
    public void setOpenFrameWidth(float widthPx) {
        if (widthPx == mOpenFrameWidth) return;
        mOpenFrameWidth = widthPx;
        invalidate();
    }

    public void setQueryCursor(int cursor) {
        mQueryCursor = cursor;
        restartCaret();
        invalidate();
    }

    /**
     * Put the caret back in its on half and start the half-blink over, the way a text cursor stays
     * solid while the keys are coming. Does nothing while the caret is not blinking, so a closed
     * palette still posts nothing.
     */
    private void restartCaret() {
        if (!mCaretBlinking) return;
        removeCallbacks(mCaretBlink);
        mCaretOn = true;
        postDelayed(mCaretBlink, CARET_BLINK_MS);
    }

    /** Advance of the text before the caret, so the caret can sit mid-string. */
    private float measureToCursor(@NonNull TextPaint paint, @NonNull String text) {
        int cursor = Math.max(0, Math.min(mQueryCursor, text.length()));
        return paint.measureText(text.substring(0, cursor));
    }

    /**
     * Argument mode: the rows collapse to one instruction and the value being typed moves from
     * the filter field down to its own row, so the field never shows the argument buffer.
     */
    public void setArgumentMode(boolean argumentMode, @NonNull String placeholder,
                                @NonNull String value) {
        setArgumentMode(argumentMode, placeholder, value, DEFAULT_ARGUMENT_PROMPT);
    }

    /** As above, with a prompt of its own: capture mode reads "key ❯" rather than "arg ❯". */
    public void setArgumentMode(boolean argumentMode, @NonNull String placeholder,
                                @NonNull String value, @NonNull String prompt) {
        mArgumentPrompt = prompt;
        mArgumentMode = argumentMode;
        mArgumentPlaceholder = placeholder;
        mArgumentValue = value;
        restartCaret();
        invalidate();
    }

    /**
     * Transient confirmation line, drawn on its own at {@code left}/{@code baselineY} once the
     * palette has collapsed — the handoff prints it at the prompt, but writing into the shell
     * would land in whatever the user is composing, so it is drawn over the terminal instead.
     */
    /**
     * Height the current content wants, before the host clamps it. Includes the chrome rows so
     * the caller can target the height spring directly.
     */
    public float measuredContentHeight() {
        float height = chromeHeight() + listContentHeight();
        if (mArgumentMode) height += dp(ARG_ROW_H);
        return height;
    }

    /**
     * The filter row: what the palette is with no rows under it, and so the floor the height
     * spring collapses to when the query is empty.
     */
    public float chromeHeight() {
        return dp(FILTER_ROW_H);
    }

    private float listContentHeight() {
        float total = 0f;
        for (int i = 0; i < mRows.size(); i++) total += rowHeight(i);
        return total;
    }

    private float rowHeight(int index) {
        Row row = mRows.get(index);
        if (row.kind != KIND_ENTRY) return dp(CATEGORY_H);
        float height = dp(ROW_PAD_V) * 2f + lineHeight(SIZE_ROW);
        if (index == mFocusIndex && row.description != null && !row.description.isEmpty())
            height += lineHeight(SIZE_DESCRIPTION);
        return height;
    }

    private float lineHeight(float sizeDp) {
        mMono.setTextSize(sp(sizeDp));
        Paint.FontMetrics metrics = mMono.getFontMetrics();
        return metrics.descent - metrics.ascent;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mFrame.isEmpty() || mProgress <= 0.001f) return;

        drawSurface(canvas);
        if (mBodyAlpha > 0.004f) drawBody(canvas);
        drawRim(canvas);
        if (mStripAlpha > 0.004f) drawStrip(canvas);
    }

    // The spec's `0 18px 40px` drop shadow used to be faked with three concentric black rounded
    // rects at 30/18/7dp spread, because hardware canvases only honour setShadowLayer for text.
    // At 30dp the outermost one reached past the keycap strip, so the palette read as three
    // stacked panels behind the search box rather than one floating rectangle. The rim and the
    // backdrop blur carry the separation on their own.

    private void drawSurface(@NonNull Canvas canvas) {
        drawDropShadow(canvas);
        // Glass base at the dock's own tint alpha, then the vertical light model over it.
        mFill.setShader(null);
        mFill.setColor(ColorUtils.setAlphaComponent(mGlassBase, 158));
        canvas.drawRoundRect(mFrame, mRadius, mRadius, mFill);

        mFill.setShader(new LinearGradient(0f, mFrame.top, 0f, mFrame.bottom,
            ColorUtils.setAlphaComponent(GlassTokens.HIGHLIGHT, 20),
            ColorUtils.setAlphaComponent(GlassTokens.SHADE, 46), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(mFrame, mRadius, mRadius, mFill);
        mFill.setShader(null);

        int save = canvas.save();
        clipToFrame(canvas);
        float specularAlpha = 0.35f + 0.5f * mProgress;
        int specular = ColorUtils.setAlphaComponent(GlassTokens.HIGHLIGHT, Math.round(33f * specularAlpha));
        mFill.setShader(new RadialGradient(
            mFrame.left + mFrame.width() * 0.62f, mFrame.top + mFrame.height() * 0.18f,
            dp(SPECULAR_RADIUS),
            new int[] {specular, ColorUtils.setAlphaComponent(specular, 0), Color.TRANSPARENT},
            new float[] {0f, 0.68f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(mFrame, mFill);
        mFill.setShader(null);
        canvas.restoreToCount(save);
    }

    /**
     * Accent-tinted rim: the palette rim is primary, unlike the keycaps' white hairline. Drawn
     * after the body, not as part of the surface — the rim belongs to the surface, so it has to
     * survive the body's fills. Stroked before them, the bottom fade and the last row's focus wash
     * painted over its bottom segment, which is what made the edge read as a band rather than a rim.
     */
    private void drawRim(@NonNull Canvas canvas) {
        mStroke.setColor(ColorUtils.setAlphaComponent(mPrimary, 150));
        float inset = mStroke.getStrokeWidth() / 2f;
        mRect.set(mFrame.left + inset, mFrame.top + inset,
            mFrame.right - inset, mFrame.bottom - inset);
        canvas.drawRoundRect(mRect, mRadius, mRadius, mStroke);
    }

    private void clipToFrame(@NonNull Canvas canvas) {
        canvas.clipPath(framePath());
    }

    /**
     * minSdk is 26 and Canvas has no clipRoundRect, so clipPath is the only option. A
     * uniform-radius addRoundRect is recognised by Skia as an rrect and clipped analytically, so
     * this costs about what the existing clipOutPath in the shadow pass does. Hardware rrect clips
     * are not always antialiased, which can leave the focus wash's corner marginally jaggy —
     * invisible at its alpha of 28.
     */
    @NonNull
    private Path framePath() {
        if (mFramePathDirty) {
            mFramePath.rewind();
            mFramePath.addRoundRect(mFrame, mRadius, mRadius, Path.Direction.CW);
            mFramePathDirty = false;
        }
        return mFramePath;
    }

    /**
     * Soft shadow around the palette, clipped out of the frame itself so it darkens only what the
     * glass floats over. Spread and drop scale with the sprout, so the panel lifts off the
     * terminal as it opens.
     */
    private void drawDropShadow(@NonNull Canvas canvas) {
        if (mProgress <= 0.02f) return;
        int save = canvas.save();
        mShadowClip.rewind();
        mShadowClip.addRoundRect(mFrame, mRadius, mRadius, Path.Direction.CW);
        canvas.clipOutPath(mShadowClip);
        mFill.setShader(null);
        mFill.setColor(ColorUtils.setAlphaComponent(GlassTokens.SHADE,
            Math.max(1, Math.round(SHADOW_RING_ALPHA * mProgress))));
        for (int ring = SHADOW_RINGS; ring >= 1; ring--) {
            float scale = ring / (float) SHADOW_RINGS;
            float spread = dp(SHADOW_SPREAD) * scale * mProgress;
            float drop = dp(SHADOW_DROP) * scale * mProgress;
            mRect.set(mFrame.left - spread, mFrame.top - spread + drop,
                mFrame.right + spread, mFrame.bottom + spread + drop);
            canvas.drawRoundRect(mRect, mRadius + spread, mRadius + spread, mFill);
        }
        canvas.restoreToCount(save);
    }

    private void drawBody(@NonNull Canvas canvas) {
        int save = canvas.save();
        clipToFrame(canvas);
        int alpha = Math.round(255f * mBodyAlpha);

        // One surface, no title bar: the filter row is the top edge, with the crumb and result
        // meta right-aligned inside it instead of on a row of their own.
        float filterBottom = mFrame.top + dp(FILTER_ROW_H);
        drawHairline(canvas, filterBottom, ColorUtils.setAlphaComponent(GlassTokens.HIGHLIGHT, 26), alpha);
        mMono.setTextSize(sp(SIZE_FILTER));
        mMono.setLetterSpacing(0f);
        mMono.setColor(withBodyAlpha(mPrimary, alpha));
        float promptBaseline = baseline(mFrame.top, dp(FILTER_ROW_H), mMono);
        canvas.drawText("❯", mFrame.left + dp(ROW_PAD_LEFT), promptBaseline, mMono);
        float queryStart = mFrame.left + dp(ROW_PAD_LEFT) + mMono.measureText("❯ ");

        mMono.setTextSize(sp(SIZE_META));
        mMono.setColor(withBodyAlpha(mMeta, alpha));
        float metaWidth = mMono.measureText(mMetaText);
        canvas.drawText(mMetaText, mFrame.right - dp(ROW_PAD_RIGHT) - metaWidth,
            promptBaseline, mMono);
        float queryEnd = mFrame.right - dp(ROW_PAD_RIGHT)
            - (metaWidth > 0f ? metaWidth + dp(8f) : 0f);

        if (!mCrumb.isEmpty()) {
            mMono.setTextSize(sp(SIZE_CRUMB));
            mMono.setColor(withBodyAlpha(mOnSurfaceVariant, alpha));
            String crumb = ellipsize(mMono, "/ " + mCrumb, (queryEnd - queryStart) * 0.5f);
            float crumbWidth = mMono.measureText(crumb);
            canvas.drawText(crumb, queryEnd - crumbWidth, promptBaseline, mMono);
            queryEnd -= crumbWidth + dp(8f);
        }

        boolean showPlaceholder = mQuery.isEmpty();
        // An empty line still takes input: the caret sits where the first character will land
        // and the placeholder reads after it, so the blink says "type here" before anything is.
        float textStart = showPlaceholder ? queryStart + dp(CARET_W) + dp(5f) : queryStart;
        if (showPlaceholder) {
            // The hint reads from its start, so it shortens at its end; typed text keeps its end.
            mMono.setTextSize(placeholderTextSize(textStart - mFrame.left,
                metaWidth > 0f ? metaWidth + dp(8f) : 0f));
            mMono.setColor(withBodyAlpha(mMeta, alpha));
            canvas.drawText(ellipsize(mMono, mQueryPlaceholder, queryEnd - textStart),
                textStart, promptBaseline, mMono);
            mMono.setTextSize(sp(SIZE_FILTER));
        } else {
            mMono.setTextSize(sp(SIZE_FILTER));
            mMono.setColor(withBodyAlpha(mOnSurface, alpha));
            canvas.drawText(ellipsizeStart(mMono, mQuery, queryEnd - textStart),
                textStart, promptBaseline, mMono);
        }
        drawCaret(canvas, showPlaceholder ? queryStart
            : queryStart + Math.min(measureToCursor(mMono, mQuery), queryEnd - queryStart),
            promptBaseline, alpha);

        float listBottom = listBottom();
        drawList(canvas, filterBottom, listBottom, alpha);
        if (mArgumentMode) drawArgumentRow(canvas, listBottom, alpha);
        canvas.restoreToCount(save);
    }

    /**
     * The search hint's type size: the query's own when the hint fits the open field, otherwise
     * shrunk to fit, down to {@link #SIZE_PLACEHOLDER_MIN}; past that the draw ellipsizes it.
     * Fitted once per hint and open width, against the meta beside it at the time (the idle one,
     * since a show starts idle), so the hint does not change size when the meta later reads a
     * longer result count; the ellipsis takes up that slack instead.
     *
     * @param lead     frame edge to where the hint starts
     * @param trailing room the meta text takes at the right, gap included
     */
    private float placeholderTextSize(float lead, float trailing) {
        float frameWidth = mOpenFrameWidth > 0f ? mOpenFrameWidth : mFrame.width();
        if (frameWidth == mPlaceholderFitWidth && mQueryPlaceholder.equals(mPlaceholderFitText))
            return mPlaceholderSize;
        float available = frameWidth - lead - dp(ROW_PAD_RIGHT) - trailing;
        float full = sp(SIZE_FILTER);
        mMono.setTextSize(full);
        float natural = mMono.measureText(mQueryPlaceholder);
        mPlaceholderSize = natural <= available || available <= 0f ? full
            : Math.max(sp(SIZE_PLACEHOLDER_MIN), full * available / natural);
        mPlaceholderFitWidth = frameWidth;
        mPlaceholderFitText = mQueryPlaceholder;
        return mPlaceholderSize;
    }

    private float listTop() {
        return mFrame.top + dp(FILTER_ROW_H);
    }

    private float listBottom() {
        return mArgumentMode ? mFrame.bottom - dp(ARG_ROW_H) : mFrame.bottom;
    }

    private float maxScroll() {
        return Math.max(0f, listContentHeight() - (listBottom() - listTop()));
    }

    private void drawList(@NonNull Canvas canvas, float top, float bottom, int alpha) {
        int save = canvas.save();
        canvas.clipRect(mFrame.left, top, mFrame.right, bottom);
        mActionRects.clear();
        float viewportHeight = bottom - top;
        updateScrollOffset(viewportHeight);
        float y = top - mScrollOffset;
        for (int i = 0; i < mRows.size(); i++) {
            float height = rowHeight(i);
            if (y + height >= top && y <= bottom) drawRow(canvas, mRows.get(i), i, y, height, alpha);
            y += height;
        }
        // Bottom fade so a clipped last row never cuts hard.
        if (listContentHeight() > viewportHeight) {
            mFill.setShader(new LinearGradient(0f, bottom - dp(BOTTOM_FADE_H), 0f, bottom,
                Color.TRANSPARENT,
                withBodyAlpha(ColorUtils.setAlphaComponent(mGlassBase, 230), alpha),
                Shader.TileMode.CLAMP));
            canvas.drawRect(mFrame.left, bottom - dp(BOTTOM_FADE_H), mFrame.right, bottom, mFill);
            mFill.setShader(null);
        }
        canvas.restoreToCount(save);
    }

    /** Keeps the focused row inside the clipped viewport, with the spec's bottom slack. */
    private void updateScrollOffset(float viewportHeight) {
        float contentHeight = listContentHeight();
        if (mUserScrolled) {
            // The finger owns the viewport; only re-clamp in case the content shrank under it.
            mScrollOffset = Math.max(0f, Math.min(mScrollOffset, contentHeight - viewportHeight));
            return;
        }
        if (contentHeight <= viewportHeight || mFocusIndex < 0) {
            mScrollOffset = 0f;
            return;
        }
        float focusTop = 0f;
        for (int i = 0; i < mFocusIndex && i < mRows.size(); i++) focusTop += rowHeight(i);
        float focusBottom = focusTop
            + (mFocusIndex < mRows.size() ? rowHeight(mFocusIndex) : 0f) + dp(VIEWPORT_BOTTOM_SLACK);
        float offset = mScrollOffset;
        if (focusTop < offset) offset = focusTop;
        if (focusBottom > offset + viewportHeight) offset = focusBottom - viewportHeight;
        mScrollOffset = Math.max(0f, Math.min(offset, contentHeight - viewportHeight));
    }

    private void drawRow(@NonNull Canvas canvas, @NonNull Row row, int index, float top,
                         float height, int alpha) {
        if (row.kind != KIND_ENTRY) {
            drawRule(canvas, row.primary, top, alpha,
                row.kind == KIND_NOTICE ? mOnSurfaceVariant : mPrimary);
            return;
        }
        boolean focused = index == mFocusIndex;
        if (focused) {
            mFill.setShader(null);
            mFill.setColor(withBodyAlpha(ColorUtils.setAlphaComponent(mPrimary, 28), alpha));
            canvas.drawRect(mFrame.left, top, mFrame.right, top + height, mFill);
            mFill.setColor(withBodyAlpha(mPrimary, alpha));
            canvas.drawRect(mFrame.left, top, mFrame.left + dp(FOCUS_BAR_W), top + height, mFill);
        }
        int titleColor = row.enabled ? mOnSurface : mMeta;
        mMono.setTextSize(sp(SIZE_ROW));
        mMono.setLetterSpacing(0f);
        mMono.setColor(withBodyAlpha(titleColor, row.enabled ? alpha : alpha / 2));
        float titleBaseline = top + dp(ROW_PAD_V) - mMono.getFontMetrics().ascent;
        float textLeft = mFrame.left + dp(ROW_PAD_LEFT);
        if (row.icon != null) {
            drawRowIcon(canvas, row.icon, textLeft, top, row.enabled ? alpha : alpha / 2);
            textLeft += dp(ICON_SIZE) + dp(ICON_GAP);
        }

        mMono.setTextSize(sp(SIZE_SHORTCUT));
        float shortcutWidth = row.shortcut.isEmpty() ? 0f
            : mMono.measureText(row.shortcut) + dp(10f);
        mMono.setTextSize(sp(SIZE_ROW));
        // Right edge of the shortcut column: the row's own, or the chip's left side when the row
        // has one, so a bound contact's chord sits beside its call button rather than under it.
        float shortcutRight = mFrame.right - dp(ROW_PAD_RIGHT);
        // The chip first: its width is what the shortcut and the title have to leave room for.
        if (row.action != null) {
            float chipWidth = dp(ACTION_CHIP_DP);
            float chipRight = shortcutRight;
            float chipTop = top + dp(ROW_PAD_V)
                + (lineHeight(SIZE_ROW) - chipWidth) / 2f;
            mActionRects.put(index, new RectF(
                chipRight - chipWidth, chipTop, chipRight, chipTop + chipWidth));
            shortcutRight -= chipWidth + dp(CAP_GAP);
        }
        float titleWidth = shortcutRight - shortcutWidth - textLeft;
        canvas.drawText(ellipsize(mMono, row.primary, titleWidth), textLeft, titleBaseline, mMono);

        if (!row.shortcut.isEmpty()) {
            mMono.setTextSize(sp(SIZE_SHORTCUT));
            mMono.setColor(withBodyAlpha(mOnSurfaceVariant, alpha));
            canvas.drawText(row.shortcut, shortcutRight - mMono.measureText(row.shortcut),
                titleBaseline, mMono);
        }
        if (focused && row.description != null && !row.description.isEmpty()) {
            mMono.setTextSize(sp(SIZE_DESCRIPTION));
            mMono.setColor(withBodyAlpha(mMeta, alpha));
            float descriptionBaseline = titleBaseline + lineHeightOf(mMono);
            float width = mFrame.right - dp(ROW_PAD_RIGHT) - textLeft;
            canvas.drawText(ellipsize(mMono, "↳ " + row.description, width),
                textLeft, descriptionBaseline, mMono);
        }
        if (row.action != null) {
            drawActionChip(canvas, row.action, mActionRects.get(index), alpha);
        }
    }

    /**
     * Draws a row's trailing chip: a round in the keycap's fill,
     * ramp and hairline, with the row's own icon centred inside
     * it. A contact's call button is a button, not a word.
     */
    private void drawActionChip(@NonNull Canvas canvas, @NonNull Row.Action action,
                                @Nullable RectF chip, int alpha) {
        if (chip == null) return;
        float radius = chip.height() / 2f;
        mFill.setShader(null);
        mFill.setColor(withBodyAlpha(mChipFill, alpha));
        canvas.drawRoundRect(chip, radius, radius, mFill);
        mFill.setShader(new LinearGradient(0f, chip.top, 0f, chip.bottom,
            ColorUtils.setAlphaComponent(GlassTokens.HIGHLIGHT, 26),
            ColorUtils.setAlphaComponent(GlassTokens.SHADE, 56), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(chip, radius, radius, mFill);
        mFill.setShader(null);
        mStroke.setColor(withBodyAlpha(
            ColorUtils.setAlphaComponent(GlassTokens.HIGHLIGHT, 88), alpha));
        float inset = mStroke.getStrokeWidth() / 2f;
        mRect.set(chip.left + inset, chip.top + inset, chip.right - inset, chip.bottom - inset);
        canvas.drawRoundRect(mRect, radius, radius, mStroke);

        // The phone, centred in the round. Bounds are set per frame
        // and the alpha restored, like the row icons: one shared
        // instance is painted for every contact row at once.
        Drawable icon = action.icon;
        float iconSize = dp(ACTION_ICON_DP);
        float iconLeft = chip.centerX() - iconSize / 2f;
        float iconTop = chip.centerY() - iconSize / 2f;
        int previous = icon.getAlpha();
        icon.setBounds(Math.round(iconLeft), Math.round(iconTop),
            Math.round(iconLeft + iconSize), Math.round(iconTop + iconSize));
        icon.setAlpha(alpha);
        icon.draw(canvas);
        icon.setAlpha(previous);
    }

    /**
     * Draws row artwork centred on the title line. The alpha is set on the drawable and put back
     * immediately: these are the launcher's own cached icon instances, shared with the app grid,
     * and {@code mutate()} per frame would copy artwork on every scroll frame.
     */
    private void drawRowIcon(@NonNull Canvas canvas, @NonNull Drawable icon, float left, float top,
                            int alpha) {
        float size = dp(ICON_SIZE);
        float iconTop = top + dp(ROW_PAD_V) + (lineHeight(SIZE_ROW) - size) / 2f;
        icon.setBounds(Math.round(left), Math.round(iconTop),
            Math.round(left + size), Math.round(iconTop + size));
        int previous = icon.getAlpha();
        icon.setAlpha(alpha);
        icon.draw(canvas);
        icon.setAlpha(previous);
    }

    /** {@code ── LABEL} followed by a hairline filling the remaining width. */
    private void drawRule(@NonNull Canvas canvas, @NonNull String label, float top, int alpha,
                          @ColorInt int color) {
        mMono.setTextSize(sp(SIZE_CATEGORY));
        mMono.setLetterSpacing(0.1f);
        mMono.setColor(withBodyAlpha(color, alpha));
        float baseline = top + dp(8f) - mMono.getFontMetrics().ascent;
        String text = "── " + label;
        float left = mFrame.left + dp(CATEGORY_PAD_LEFT);
        float available = mFrame.right - dp(ROW_PAD_RIGHT) - left;
        String drawn = ellipsize(mMono, text, available);
        canvas.drawText(drawn, left, baseline, mMono);
        float ruleStart = left + mMono.measureText(drawn) + dp(6f);
        mMono.setLetterSpacing(0f);
        float ruleEnd = mFrame.right - dp(ROW_PAD_RIGHT);
        if (ruleEnd > ruleStart) {
            mFill.setShader(null);
            mFill.setColor(withBodyAlpha(ColorUtils.setAlphaComponent(mPrimary, 56), alpha));
            float ruleY = baseline + mMono.getFontMetrics().descent * 0.4f;
            canvas.drawRect(ruleStart, ruleY, ruleEnd, ruleY + mDensity, mFill);
        }
    }

    private void drawArgumentRow(@NonNull Canvas canvas, float top, int alpha) {
        mFill.setShader(null);
        mFill.setColor(withBodyAlpha(ColorUtils.setAlphaComponent(mPrimary, 26), alpha));
        canvas.drawRect(mFrame.left, top, mFrame.right, mFrame.bottom, mFill);
        drawHairline(canvas, top, ColorUtils.setAlphaComponent(mPrimary, 56), alpha);

        mMono.setTextSize(sp(SIZE_FILTER));
        mMono.setLetterSpacing(0f);
        float argBaseline = baseline(top, dp(ARG_ROW_H), mMono);
        mMono.setColor(withBodyAlpha(mPrimary, alpha));
        canvas.drawText(mArgumentPrompt, mFrame.left + dp(ROW_PAD_LEFT), argBaseline, mMono);
        float valueStart = mFrame.left + dp(ROW_PAD_LEFT) + mMono.measureText(mArgumentPrompt + " ");
        boolean empty = mArgumentValue.isEmpty();
        mMono.setColor(withBodyAlpha(empty ? mMeta : mOnSurface, alpha));
        float enterWidth = mMono.measureText("⏎") + dp(8f);
        float valueWidth = mFrame.right - dp(ROW_PAD_RIGHT) - enterWidth - valueStart;
        canvas.drawText(ellipsizeStart(mMono, empty ? mArgumentPlaceholder : mArgumentValue,
            valueWidth), valueStart, argBaseline, mMono);
        if (!empty) {
            drawCaret(canvas, valueStart
                + Math.min(measureToCursor(mMono, mArgumentValue), valueWidth), argBaseline, alpha);
        }
        mMono.setColor(withBodyAlpha(mPrimary, alpha));
        canvas.drawText("⏎", mFrame.right - dp(ROW_PAD_RIGHT) - mMono.measureText("⏎"),
            argBaseline, mMono);
    }

    private void drawStrip(@NonNull Canvas canvas) {
        layoutKeycaps();
        if (mCapCount == 0) return;
        int alpha = Math.round(255f * mStripAlpha);
        int save = canvas.save();
        canvas.translate(0f, mStripOffset);
        for (int i = 0; i < mCapCount; i++) {
            RectF cap = mCapRects.get(i);
            Keycap keycap = mKeycaps.get(i);
            // An action is a key; an app or a contact is a pill whose end is concentric with its
            // mark.
            float radius = keycap.hasMark() ? cap.height() / 2f : dp(CAP_RADIUS);
            mFill.setShader(null);
            mFill.setColor(withBodyAlpha(mChipFill, alpha));
            canvas.drawRoundRect(cap, radius, radius, mFill);
            mFill.setShader(new LinearGradient(0f, cap.top, 0f, cap.bottom,
                ColorUtils.setAlphaComponent(GlassTokens.HIGHLIGHT, 26),
                ColorUtils.setAlphaComponent(GlassTokens.SHADE, 56), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(cap, radius, radius, mFill);
            mFill.setShader(null);
            mStroke.setColor(withBodyAlpha(ColorUtils.setAlphaComponent(GlassTokens.HIGHLIGHT, 88), alpha));
            float inset = mStroke.getStrokeWidth() / 2f;
            mRect.set(cap.left + inset, cap.top + inset, cap.right - inset, cap.bottom - inset);
            canvas.drawRoundRect(mRect, radius, radius, mStroke);

            float baseline = cap.top + mCapBaseline;
            float leadLeft = cap.left + mCapLeft[i];
            float labelStart;
            if (keycap.hasMark()) {
                drawCapMark(canvas, keycap, leadLeft, cap.centerY(), alpha);
                labelStart = leadLeft + dp(MARK_SIZE) + mCapLeadGap[i];
            } else {
                mMono.setTextSize(sp(SIZE_CAP_GLYPH));
                mMono.setLetterSpacing(0f);
                mMono.setColor(withBodyAlpha(mPrimary, alpha));
                canvas.drawText(keycap.glyph, leadLeft, baseline, mMono);
                labelStart = leadLeft + keycap.mGlyphWidth + mCapLeadGap[i];
            }
            mMono.setTextSize(sp(SIZE_CAP_LABEL));
            mMono.setLetterSpacing(0f);
            mMono.setColor(withBodyAlpha(mCapLabel, alpha));
            // Ellipsized to the room layoutKeycaps gave this cap, so a long app name shortens
            // inside its own border instead of running into the next cap.
            canvas.drawText(fittedLabel(keycap, mCapLabelRoom[i]), labelStart, baseline, mMono);
        }
        canvas.restoreToCount(save);
    }

    /**
     * A pill's leading mark: the app's icon or the contact's photo, untinted, inside a 1 px round
     * that keeps a white or black image from melting into the chip; or, for a contact without a
     * photo (or an app without artwork), a primary disc holding the initial.
     */
    private void drawCapMark(@NonNull Canvas canvas, @NonNull Keycap keycap, float left,
                             float centerY, int alpha) {
        float size = dp(MARK_SIZE);
        float centerX = left + size / 2f;
        Drawable icon = keycap.icon;
        if (icon != null) {
            float top = centerY - size / 2f;
            icon.setBounds(Math.round(left), Math.round(top),
                Math.round(left + size), Math.round(top + size));
            // Alpha set and put back, as drawRowIcon does: the instance is the launcher's own,
            // shared with the app grid.
            int previous = icon.getAlpha();
            icon.setAlpha(alpha);
            icon.draw(canvas);
            icon.setAlpha(previous);
            mMarkOutline.setColor(withBodyAlpha(mMarkOutlineColor, alpha));
            canvas.drawCircle(centerX, centerY, size / 2f - 0.5f, mMarkOutline);
            return;
        }
        mFill.setShader(null);
        mFill.setColor(withBodyAlpha(ColorUtils.setAlphaComponent(mPrimary, MONOGRAM_FILL_ALPHA),
            alpha));
        canvas.drawCircle(centerX, centerY, size / 2f, mFill);
        // Its size was set with the measuring, in measureKeycaps.
        mMonogram.setColor(withBodyAlpha(mPrimary, alpha));
        canvas.drawText(keycap.initial, centerX - keycap.mInitialDx,
            centerY - keycap.mInitialDy, mMonogram);
    }

    /** The cap's label ellipsized to {@code room}, kept until the room changes. */
    @NonNull
    private String fittedLabel(@NonNull Keycap keycap, float room) {
        if (keycap.mFitted != null && keycap.mFitWidth == room) return keycap.mFitted;
        // Half a pixel of slack: the room for a label that fits is its own measured width, and
        // float noise must not cost it its last letter.
        keycap.mFitted = keycap.mLabelWidth <= room + 0.5f ? keycap.label
            : ellipsize(mMono, keycap.label, room);
        keycap.mFitWidth = room;
        return keycap.mFitted;
    }

    /**
     * Lays the caps out in a row centered under the palette's bottom border. When their labels
     * do not all fit, the room is shared fairly: short labels keep their whole width and the long
     * ones split what is left, so one long app name ellipsizes rather than squeezing every cap.
     * Only when the caps' own furniture overflows does the row scale down, and then only its
     * padding, insets and gaps: a glyph or mark keeps its size.
     */
    private void layoutKeycaps() {
        int count = mFrame.isEmpty() ? 0 : mKeycaps.size();
        mCapCount = count;
        if (count == 0) return;
        if (mCapLeft.length < count) {
            mCapLeft = new float[count];
            mCapLeadGap = new float[count];
            mCapLabelRoom = new float[count];
            mCapSorted = new float[count];
        }
        while (mCapRects.size() < count) mCapRects.add(new RectF());
        float available = mFrame.width() - dp(STRIP_LEFT_EXTRA);
        float markInset = (mCapHeight - dp(MARK_SIZE)) / 2f;
        float gaps = dp(CAP_GAP) * (count - 1);
        // Scalable furniture (insets, gaps, padding) and fixed leads (glyph or mark), in total.
        float furniture = gaps;
        float leads = 0f;
        float labels = 0f;
        for (int i = 0; i < count; i++) {
            Keycap keycap = mKeycaps.get(i);
            mCapLeft[i] = keycap.hasMark() ? markInset : dp(CAP_PAD_H);
            mCapLeadGap[i] = keycap.hasMark() ? dp(MARK_GAP) : dp(CAP_LEAD_GAP);
            furniture += mCapLeft[i] + mCapLeadGap[i] + dp(CAP_PAD_H);
            leads += keycap.hasMark() ? dp(MARK_SIZE) : keycap.mGlyphWidth;
            mCapSorted[i] = keycap.mLabelWidth;
            labels += keycap.mLabelWidth;
        }
        float labelBudget = available - furniture - leads;
        float level = Float.MAX_VALUE;
        float scale = 1f;
        if (labels > labelBudget) {
            if (labelBudget > 0f) {
                level = fairShare(mCapSorted, count, labelBudget);
            } else {
                level = 0f;
                scale = Math.max(0f, (available - leads) / furniture);
            }
        }
        float total = gaps * scale;
        for (int i = 0; i < count; i++) {
            Keycap keycap = mKeycaps.get(i);
            mCapLeft[i] *= scale;
            mCapLeadGap[i] *= scale;
            mCapLabelRoom[i] = Math.min(keycap.mLabelWidth, level);
            total += mCapLeft[i] + (keycap.hasMark() ? dp(MARK_SIZE) : keycap.mGlyphWidth)
                + mCapLeadGap[i] + mCapLabelRoom[i] + dp(CAP_PAD_H) * scale;
        }
        float top = mFrame.bottom + dp(STRIP_GAP);
        float x = mFrame.left + (mFrame.width() - total) / 2f;
        for (int i = 0; i < count; i++) {
            Keycap keycap = mKeycaps.get(i);
            float width = mCapLeft[i] + (keycap.hasMark() ? dp(MARK_SIZE) : keycap.mGlyphWidth)
                + mCapLeadGap[i] + mCapLabelRoom[i] + dp(CAP_PAD_H) * scale;
            mCapRects.get(i).set(x, top, x + width, top + mCapHeight);
            x += width + dp(CAP_GAP) * scale;
        }
    }

    /**
     * The widest a label may be so that {@code count} labels fill exactly {@code budget}: labels
     * narrower than their share keep their width, and what they leave is split among the rest.
     * Sorts the first {@code count} of {@code widths} in place.
     */
    static float fairShare(@NonNull float[] widths, int count, float budget) {
        java.util.Arrays.sort(widths, 0, count);
        float remaining = budget;
        for (int i = 0; i < count; i++) {
            float share = remaining / (count - i);
            if (widths[i] > share) return share;
            remaining -= widths[i];
        }
        return Float.MAX_VALUE;
    }

    /** For tests: cap {@code index} as laid out against the current frame, or null. */
    @Nullable
    RectF keycapRect(int index) {
        layoutKeycaps();
        return index < mCapCount ? new RectF(mCapRects.get(index)) : null;
    }

    /** For tests: the room the label of cap {@code index} was given. */
    float keycapLabelRoom(int index) {
        layoutKeycaps();
        return index < mCapCount ? mCapLabelRoom[index] : 0f;
    }

    private void drawHairline(@NonNull Canvas canvas, float y, @ColorInt int color, int alpha) {
        mFill.setShader(null);
        mFill.setColor(withBodyAlpha(color, alpha));
        canvas.drawRect(mFrame.left, y - mDensity, mFrame.right, y, mFill);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Once collapsed to a confirmation line the overlay stops being modal, so taps fall
        // through to the terminal underneath.
        if (mProgress <= 0.01f || mCallbacks == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!isModalAt(event.getX(), event.getY())) return false;
                abortFling();
                mDragging = false;
                mDownX = event.getX();
                mDownY = event.getY();
                mLastTouchY = mDownY;
                if (mVelocity != null) mVelocity.clear();
                else mVelocity = VelocityTracker.obtain();
                mVelocity.addMovement(event);
                mLongPressFired = false;
                scheduleLongPress(mDownX, mDownY);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (mVelocity != null) mVelocity.addMovement(event);
                float y = event.getY();
                if (Math.abs(y - mDownY) > mTouchSlop) cancelPendingLongPress();
                if (!mDragging && Math.abs(y - mDownY) > mTouchSlop
                    && maxScroll() > 0f && inList(mDownX, mDownY))
                    mDragging = true;
                if (mDragging) {
                    scrollByPx(mLastTouchY - y);
                    mLastTouchY = y;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                cancelPendingLongPress();
                if (mDragging) {
                    startFling();
                    return true;
                }
                releaseVelocity();
                // The long press already acted; releasing must not also launch the row.
                if (!mLongPressFired) handleTap(event.getX(), event.getY());
                mLongPressFired = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelPendingLongPress();
                mLongPressFired = false;
                mDragging = false;
                releaseVelocity();
                return true;
            default:
                return true;
        }
    }

    /**
     * Long press on a row is the palette's one secondary gesture. Posted rather than measured on UP
     * so it fires while the finger is still down, and cancelled by a drag, an UP or a CANCEL so a
     * scroll never turns into one.
     */
    private void scheduleLongPress(float x, float y) {
        cancelPendingLongPress();
        if (!inList(x, y)) return;
        final int index = rowIndexAt(y);
        if (index < 0 || !mRows.get(index).isSelectable()) return;
        mLongPress = () -> {
            mLongPress = null;
            mLongPressFired = true;
            if (mCallbacks != null) mCallbacks.onRowLongPressed(index);
        };
        postDelayed(mLongPress, ViewConfiguration.getLongPressTimeout());
    }

    private void cancelPendingLongPress() {
        if (mLongPress != null) removeCallbacks(mLongPress);
        mLongPress = null;
    }

    /** Whether the palette owns this point, or it belongs to whatever is underneath. */
    private boolean isModalAt(float x, float y) {
        if (mFrame.contains(x, y)) return true;
        for (int i = 0; i < mCapCount; i++) {
            RectF cap = mCapRects.get(i);
            if (x >= cap.left && x <= cap.right
                && y >= cap.top + mStripOffset && y <= cap.bottom + mStripOffset) return true;
        }
        // No bounds set yet: behave as a full-screen scrim rather than leaking every tap.
        return mModalBounds.isEmpty() || mModalBounds.contains(x, y);
    }

    private boolean inList(float x, float y) {
        return mFrame.contains(x, y) && y >= listTop() && y <= listBottom();
    }

    private void handleTap(float x, float y) {
        if (mCallbacks == null) return;
        // A chip owns its own tap before the row it sits on: the call
        // button opens the dialer, the row opens the contact.
        int chipRow = actionRowAt(x, y);
        if (chipRow >= 0) {
            mCallbacks.onRowActionTapped(chipRow);
            return;
        }
        for (int i = 0; i < mCapCount; i++) {
            RectF cap = mCapRects.get(i);
            if (x >= cap.left && x <= cap.right
                && y >= cap.top + mStripOffset && y <= cap.bottom + mStripOffset) {
                mCallbacks.onKeycapTapped(i);
                return;
            }
        }
        if (!mFrame.contains(x, y)) {
            mCallbacks.onOutsideTapped();
            return;
        }
        int index = rowIndexAt(y);
        if (index >= 0 && mRows.get(index).isSelectable()) mCallbacks.onRowTapped(index);
    }

    /**
     * The row whose call chip a tap lands on, or -1. The slop reaches past
     * the drawn round, further than the gap to the next row's chip, so the
     * nearest chip wins; it stays inside the frame and the list viewport,
     * where the chips are drawn.
     */
    private int actionRowAt(float x, float y) {
        if (!mFrame.contains(x, y) || y < listTop() || y > listBottom()) return -1;
        float slop = dp(ACTION_TAP_SLOP_DP);
        int nearest = -1;
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0; i < mActionRects.size(); i++) {
            RectF chip = mActionRects.valueAt(i);
            if (x < chip.left - slop || x > chip.right + slop
                || y < chip.top - slop || y > chip.bottom + slop) continue;
            float distance = Math.abs(y - chip.centerY());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = mActionRects.keyAt(i);
            }
        }
        return nearest;
    }

    /** Row under {@code y} in the scrolled list, or -1 when the point is outside it. */
    private int rowIndexAt(float y) {
        if (y < listTop() || y > listBottom()) return -1;
        float rowY = listTop() - mScrollOffset;
        for (int i = 0; i < mRows.size(); i++) {
            float height = rowHeight(i);
            if (y >= rowY && y < rowY + height) return i;
            rowY += height;
        }
        return -1;
    }

    /** @return true when the offset actually moved, so a fling knows it has not hit an edge. */
    private boolean scrollByPx(float dy) {
        float max = maxScroll();
        float next = Math.max(0f, Math.min(mScrollOffset + dy, max));
        mUserScrolled = true;
        if (next == mScrollOffset) return false;
        mScrollOffset = next;
        invalidate();
        return true;
    }

    private void startFling() {
        mDragging = false;
        float velocityY = 0f;
        if (mVelocity != null) {
            mVelocity.computeCurrentVelocity(1000);
            velocityY = mVelocity.getYVelocity();
        }
        releaseVelocity();
        // A flick up (negative y velocity) walks the list forward, so the offset grows.
        mFlingVelocity = -velocityY;
        if (Math.abs(mFlingVelocity) < dp(FLING_MIN_START_DP)) {
            mFlingVelocity = 0f;
            return;
        }
        postOnAnimation(mFlingStep);
    }

    private void stepFling() {
        if (mFlingVelocity == 0f) return;
        boolean moved = scrollByPx(mFlingVelocity / 60f);
        mFlingVelocity *= FLING_DECAY;
        if (moved && Math.abs(mFlingVelocity) > dp(FLING_MIN_KEEP_DP)) postOnAnimation(mFlingStep);
        else mFlingVelocity = 0f;
    }

    private void abortFling() {
        mFlingVelocity = 0f;
        removeCallbacks(mFlingStep);
    }

    /** One half-blink, and the next one behind it. Nothing else keeps this Runnable alive. */
    private final Runnable mCaretBlink = new Runnable() {
        @Override
        public void run() {
            if (!mCaretBlinking) return;
            mCaretOn = !mCaretOn;
            postDelayed(this, CARET_BLINK_MS);
            invalidate();
        }
    };

    /**
     * Start or stop the caret blinking. The palette turns this on as it opens and off as it
     * closes, so a closed palette posts nothing at all. With animations turned off the caret is
     * shown steady instead of blinking — it still says the line is taking input, without motion
     * nobody asked for.
     */
    public void setCaretBlinking(boolean blinking) {
        removeCallbacks(mCaretBlink);
        mCaretBlinking = blinking && FocusOutlineRenderer.animationsEnabled(getContext());
        // Steady and visible whenever it is not mid-blink: closed, or motion turned off.
        mCaretOn = true;
        if (mCaretBlinking) postDelayed(mCaretBlink, CARET_BLINK_MS);
        invalidate();
    }

    /** Whether the caret is mid-blink. Steady — and false — while closed or while motion is off. */
    boolean isCaretBlinking() {
        return mCaretBlinking;
    }

    /**
     * The caret at {@code caretX} on a text row: a 2dp bar the height of the line, in the accent
     * the palette states focus with, drawn only in the on half of a blink.
     */
    private void drawCaret(@NonNull Canvas canvas, float caretX, float baseline, int alpha) {
        if (!mCaretOn) return;
        mFill.setShader(null);
        mFill.setColor(withBodyAlpha(mPrimary, alpha));
        canvas.drawRect(caretX + dp(1f), baseline - lineHeightOf(mMono) * 0.78f,
            caretX + dp(1f) + dp(CARET_W), baseline + dp(2f), mFill);
    }

    private void releaseVelocity() {
        if (mVelocity == null) return;
        mVelocity.recycle();
        mVelocity = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        abortFling();
        setCaretBlinking(false);
        releaseVelocity();
    }

    private float baseline(float top, float height, @NonNull Paint paint) {
        Paint.FontMetrics metrics = paint.getFontMetrics();
        return top + (height - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent;
    }

    private static float lineHeightOf(@NonNull Paint paint) {
        Paint.FontMetrics metrics = paint.getFontMetrics();
        return metrics.descent - metrics.ascent;
    }

    @NonNull
    private static String ellipsize(@NonNull TextPaint paint, @NonNull String text, float width) {
        if (width <= 0f) return "";
        return TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString();
    }

    /** Keeps the tail of a typed value visible once it outgrows its field. */
    @NonNull
    private static String ellipsizeStart(@NonNull TextPaint paint, @NonNull String text,
                                         float width) {
        if (width <= 0f) return "";
        return TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.START).toString();
    }

    private static int withBodyAlpha(@ColorInt int color, int alpha) {
        return ColorUtils.setAlphaComponent(color, Color.alpha(color) * alpha / 255);
    }

    /** Font-scale-aware px for a size in sp. */
    private float sp(float value) {
        return android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, value,
            getResources().getDisplayMetrics());
    }

    private float dp(float value) {
        return value * mDensity;
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }
}
