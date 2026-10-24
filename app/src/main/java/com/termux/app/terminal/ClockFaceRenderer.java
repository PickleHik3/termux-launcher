package com.termux.app.terminal;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.chrome.GlassTokens;
import com.termux.app.chrome.OnGlass;
import com.termux.app.statusbar.TopPaneClockForm;
import com.termux.app.theme.SchemeTone;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Draws the clock faces — Flip, LCD, Minimal, LED matrix, Tape and Slab — into a box its host
 * hands it, out of a palette its host hands it. It owns everything a face is: the time snapshot
 * and the per-digit animation clock, the face's bounds at any size, the palette it derives from
 * the host's Material roles, and the fit plan that scales the FULL face uniformly to a width and
 * height budget. It owns nothing a host is: no measuring, no ticker, no touch, no invalidation.
 * Setters report whether anything changed so the host can re-measure or repaint.
 *
 * <p>Every face shares one grid: a left-aligned time band with the seconds and period folded onto
 * its baseline, and — in the full form — a date row that ends in a hairline running to the right
 * edge of the box it is drawn into. Colors come from the {@link Palette} roles rather than
 * per-face literals, toned against the backdrop the host measured (see {@link #setBandInk}).
 *
 * <p>Two kinds of size live in a face. The time band — digits, cards, segments, cells — scales
 * with the face, and its strokes are fractions of the band's height with a
 * {@link #STROKE_FLOOR_DP} floor ({@link #bandStroke}). The seconds and the date are text at the
 * Label role ({@link #LABEL_SP}) and keep that painted size at every scale: the fit plan shrinks
 * the digits to make room for them, never the labels. A widget's face, drawn far past the bar's
 * size, grows its labels with it beyond scale 1 instead ({@link #mLabelGrows}).
 *
 * <p>Nothing is allocated while drawing; the scratch rects, the fit plan and the flip gradients
 * are kept between frames.
 */
public final class ClockFaceRenderer {

    /**
     * The Material roles every face paints from. The host resolves them (from its theme, or from
     * ink it measured) and hands them over; the renderer derives the rest.
     */
    public static final class Palette {
        public final int onSurface;
        public final int secondary;
        public final int primary;
        public final int surfaceBase;
        public final int surfacePanel;
        public final int surfacePanelHigh;
        public final int surfacePanelHighest;
        public final int outlineVariant;
        public final int onSurfaceVariant;

        public Palette(int onSurface, int secondary, int primary, int surfaceBase,
                       int surfacePanel, int surfacePanelHigh, int surfacePanelHighest,
                       int outlineVariant, int onSurfaceVariant) {
            this.onSurface = onSurface;
            this.secondary = secondary;
            this.primary = primary;
            this.surfaceBase = surfaceBase;
            this.surfacePanel = surfacePanel;
            this.surfacePanelHigh = surfacePanelHigh;
            this.surfacePanelHighest = surfacePanelHighest;
            this.outlineVariant = outlineVariant;
            this.onSurfaceVariant = onSurfaceVariant;
        }

        /** The roles as {@code context}'s theme resolves them, with the app's colours as fallback. */
        @NonNull
        public static Palette fromTheme(@NonNull Context context) {
            return new Palette(
                MaterialColors.getColor(context,
                    com.termux.shared.R.attr.termuxColorOnSurface,
                    ContextCompat.getColor(context, R.color.termux_on_surface)),
                MaterialColors.getColor(context,
                    com.termux.shared.R.attr.termuxColorSecondary,
                    ContextCompat.getColor(context, R.color.termux_secondary)),
                MaterialColors.getColor(context,
                    com.termux.shared.R.attr.termuxColorPrimary,
                    ContextCompat.getColor(context, R.color.termux_primary)),
                MaterialColors.getColor(context,
                    com.termux.shared.R.attr.termuxColorSurfaceBase,
                    ContextCompat.getColor(context, R.color.termux_surface_base)),
                MaterialColors.getColor(context,
                    com.termux.shared.R.attr.termuxColorSurfacePanel,
                    ContextCompat.getColor(context, R.color.termux_surface_panel)),
                MaterialColors.getColor(context,
                    com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
                    ContextCompat.getColor(context, R.color.termux_surface_panel_high)),
                MaterialColors.getColor(context,
                    com.termux.shared.R.attr.termuxColorSurfacePanelHighest,
                    ContextCompat.getColor(context, R.color.termux_surface_panel_highest)),
                MaterialColors.getColor(context,
                    com.termux.shared.R.attr.termuxColorOutlineVariant,
                    ContextCompat.getColor(context, R.color.termux_outline_variant)),
                MaterialColors.getColor(context,
                    com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
                    ContextCompat.getColor(context, R.color.termux_on_surface_variant)));
        }
    }

    private static final long FLIP_DURATION_MS = 560L;
    private static final long SECONDS_FLIP_DURATION_MS = 340L;
    /** Period label size as a share of the seconds it stacks over — the slab's proportion. */
    private static final float PERIOD_SCALE = .64f;
    /** Most tracking (em) the period takes before it grows to fill the seconds' width instead. */
    private static final float MAX_PERIOD_TRACKING = .25f;
    /** Inset between the LCD period box and its label. */
    private static final float BOX_PAD_DP = 3f;
    private static final long LCD_DURATION_MS = 280L;
    private static final long TEXT_DURATION_MS = 350L;
    private static final long LED_DURATION_MS = 320L;

    /**
     * The Label role (spec #101): the seconds and the date on every face, painted at this size
     * whatever the face's scale. 12sp where it fits; {@link #LABEL_MIN_SP} only where a row is
     * forced.
     */
    @VisibleForTesting
    static final float LABEL_SP = 12f;
    /** The Label role where the row is forced: the mono chip's one-line run beside the time. */
    @VisibleForTesting
    static final float LABEL_MIN_SP = 11f;
    /** No stroke, segment or cell paints thinner than this, at any scale. */
    @VisibleForTesting
    static final float STROKE_FLOOR_DP = 1f;
    /** The date hairline, as a share of the band: 1dp on a 32dp band. */
    private static final float RULE_FRACTION = 1f / 32f;
    /** An LCD segment's thickness, as a share of the digit's height (3dp on the full 34dp). */
    @VisibleForTesting
    static final float LCD_SEGMENT_FRACTION = 1f / 11f;
    /** An LCD segment's glow and the colon's dots, as shares of the segment's thickness. */
    private static final float LCD_GLOW_RATIO = .45f;
    private static final float LCD_COLON_RATIO = .6f;
    /**
     * Unlit LCD segments, as a share of the lit ink. They give the panel a body; at the .08 they
     * were, they read as a second, ghost time behind the real one.
     */
    @VisibleForTesting
    static final float LCD_GHOST_ALPHA = .035f;
    /** One LED cell, as a share of the band: seven of them make a digit. */
    @VisibleForTesting
    static final float LED_CELL_FRACTION = 2f / 15f;
    /** Tape's minute track, as a share of the time band. */
    private static final float TAPE_TRACK_FRACTION = 1f / 15f;
    /** Air between Tape's date and its minute track. */
    private static final float TAPE_TRACK_GAP_DP = 3f;
    /** A flip card's one hinge seam and its rim, as shares of the card's height. */
    private static final float FLIP_SEAM_FRACTION = 1f / 36f;
    private static final float FLIP_RIM_FRACTION = 1f / 36f;
    /**
     * A flip card's end pins, at the two ends of its split line, as shares of the card's height:
     * small enough to read as the leaves' axle, never as a line ({@link #flipEndPins}).
     */
    @VisibleForTesting
    static final float FLIP_PIN_HEIGHT_FRACTION = .06f;
    @VisibleForTesting
    static final float FLIP_PIN_WIDTH_FRACTION = .025f;
    /**
     * How much of the backdrop a flip card's two passes (the base, then a leaf gradient, each
     * about half opaque) cover: what its digits are measured against.
     */
    private static final float FLIP_CARD_COVER = .75f;

    /** Shared grid: the gap between the date text and its trailing hairline. */
    private static final float RULE_GAP_DP = 7f;
    /** The shortest hairline the date row keeps beside its text. */
    private static final float MIN_RULE_DP = 24f;
    /**
     * The full flip card's drop shadow: how far it blurs and how far it falls. Nothing else is
     * drawn under the cards, so the date row starts where this shadow ends.
     */
    private static final float FLIP_SHADOW_BLUR_DP = 3.2f;
    private static final float FLIP_SHADOW_DROP_DP = 1.06f;
    /** The full flip card; see drawFullFlipDigit for why it is 26dp and not the handoff's 22. */
    private static final float FULL_FLIP_CARD_DP = 26f;
    /** The full flip card's height (its band), and its digits' size. */
    private static final float FULL_FLIP_CARD_HEIGHT_DP = 35.5f;
    private static final float FULL_FLIP_DIGIT_SP = 30.5f;
    /** The gap between the two cards of the hour, and between the two of the minutes. */
    private static final float FULL_FLIP_DIGIT_GAP_DP = 5.7f;
    private static final float COMPACT_FLIP_CARD_DP = 15f;
    private static final float COMPACT_FLIP_DIGIT_GAP_DP = 1.5f;
    /**
     * The hour pair stands off the minute pair by this many digit gaps, in every flip form: enough
     * to read as two groups, close enough that the time reads as one.
     */
    @VisibleForTesting
    static final float FLIP_PAIR_GAP_RATIO = 1.5f;
    /**
     * The band Slab and Minimal centre their digits' cap height in: Roboto's caps at those sizes
     * (27–27.7dp) with about 1.5dp of air each side. Centring the ascent-to-descent box instead
     * left the descent, which digits never use, as dead room under the time.
     */
    private static final float CAP_BAND_DP = 31f;
    private static final long[] FLIP_LOAD_STAGGER_MS = {300L, 430L, 560L, 690L};
    private static final float[] UPPER_FLIP_STOPS = {0f, .55f, .88f, 1f};
    private static final float[] LOWER_FLIP_STOPS = {0f, .07f, .28f, 1f};

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mMatrix = new Matrix();
    private final Camera mCamera = new Camera();
    private final Rect mTextBounds = new Rect();
    /** What {@link #spacedTextWidth} and the per-glyph faces measure, remembered per frame. */
    private final TextWidths.Measurer mMeasure = text -> mPaint.measureText(text);
    private final TextWidths mTextWidths = new TextWidths();
    private final CharAdvances mTimeAdvances = new CharAdvances();
    /** The seconds' advances at the size they are drawn at inside the scaled canvas. */
    private final CharAdvances mSecondsAdvances = new CharAdvances();
    /**
     * The widest digit at the Label role's painted size, for the fit plan. A table of its own: one
     * shared with the drawn seconds would be re-measured twice a frame, once per size.
     */
    private final CharAdvances mWidestAdvances = new CharAdvances();
    /** {@link #timeText()}, built once per snapshot rather than once per frame. */
    @Nullable private String mTimeText;
    @Nullable private ClockSnapshot mTimeTextSnapshot;
    private final RectF mRect = new RectF();
    private final RectF mFlipMetaCell = new RectF();
    /** The FULL face's measured parts and its plan, refilled in place on every ask. */
    private final FitParts mFitParts = new FitParts();
    private final float[] mPlan = new float[3];
    /**
     * The scale the face is being drawn at, and the Label role's painted size: text drawn at
     * {@code LABEL / mScale} inside the scaled canvas paints at the Label size. 1 outside a draw.
     */
    private float mScale = 1f;
    private float mLabelSp = LABEL_SP;
    /**
     * How much the Label role has grown at the scale being drawn ({@link #labelGrowth}): 1, or the
     * scale itself past 1 where the Label role grows with the face. Label-space lengths
     * ({@link #lp}) grow with it. 1 outside a draw.
     */
    private float mLabelGrowth = 1f;
    /**
     * Scratch rects and cached flip gradients.
     *
     * <p>The flip face repaints at vsync for as long as a digit is turning, and it used to build a
     * fresh {@link LinearGradient} for every card half on every one of those frames — six cards,
     * four shaders each, forty times a second. Each one is a native Skia object held until the
     * collector gets to it, which is what walked the process's native heap up into the hundreds of
     * megabytes while the pane was open. The gradients depend on nothing but the card's vertical
     * extent and the resolved palette, so they are built once and reused.
     */
    private final RectF mFlipHalfClip = new RectF();
    /** A full flip card's two end pins ({@link #flipEndPins}), and whether they are drawn. */
    private final RectF mFlipPinLeft = new RectF();
    private final RectF mFlipPinRight = new RectF();
    private boolean mFlipEndPinsDrawn = true;
    private int mFlipShaderGeneration;
    @Nullable private LinearGradient mUpperFlipShader;
    private float mUpperFlipTop = Float.NaN;
    private float mUpperFlipBottom = Float.NaN;
    private int mUpperFlipShaderGeneration = -1;
    @Nullable private LinearGradient mLowerFlipShader;
    private float mLowerFlipTop = Float.NaN;
    private float mLowerFlipBottom = Float.NaN;
    private int mLowerFlipShaderGeneration = -1;
    private final char[] mDigits = new char[4];
    private final char[] mOldDigits = new char[4];
    private final long[] mChangedAt = new long[4];
    private final char[] mSeconds = new char[2];
    private final char[] mOldSeconds = new char[2];
    private final long[] mSecondsChangedAt = new long[2];

    private String mStyle = TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_STYLE;
    /** Minimal in its medium weight: the stored style is {@code minimal_medium}. */
    private boolean mMinimalMedium;
    private String mAlignment = TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_ALIGNMENT;
    private TopPaneClockForm mForm = TopPaneClockForm.FULL;
    private ClockSnapshot mSnapshot;
    private boolean mUseAmPm;

    private int mPrimary;
    private int mSecondary;
    private int mOnSurface;
    private int mPrimaryLine;
    private int mRuleColor;
    private int mTrackColor;
    private int mSurfaceBase;
    private int mSurfacePanel;
    private int mSurfacePanelHigh;
    private int mSurfacePanelHighest;
    private int mOutlineVariant;
    private int mOnSurfaceVariant;
    /** The inks, toned against the backdrop: digits and graphics at 3:1, labels at 4.5:1. */
    private int mDigitInk;
    private int mAccentInk;
    private int mLcdGhost;
    private int mSecondsInk;
    private int mDateInk;
    private int mPeriodInk;
    private int mFlipDigitInk;
    /** The backdrop the bar measured under the clock, and its ink; theme colours until then. */
    private boolean mHasBand;
    private int mBandSurface;
    private int mBandInk;
    private int mFlipBase;
    private int mFlipRuleColor;
    private final int[] mUpperFlipColors = new int[4];
    private final int[] mLowerFlipColors = new int[4];
    private int mFlipRim;
    private int mFlipSeam;
    private int mFlipShadow;
    private boolean mDarkFlipStock;
    private boolean mColorsResolved;
    private int mColorSignature;

    /** Largest scale the fit mode grows the FULL face to. */
    static final float FIT_MAX_SCALE = 1.35f;
    /**
     * The ceiling a widget host hands the fit ({@link #forWidget}): a Clock widget's face fills a
     * card several times the bar's height, so it grows until its box, not the bar's 1.35, stops it.
     */
    public static final float WIDGET_FIT_MAX_SCALE = 8f;
    /** The largest scale the fit grows the FULL face to; the bar's, unless a host hands another. */
    private float mFitCeiling = FIT_MAX_SCALE;
    /**
     * Whether the FULL face draws its own date row (with the bar's hairline out to the box edge).
     * A widget host draws its date as a part of its own layout and turns it off.
     */
    private boolean mDateRow = true;
    /** Whether the seconds are drawn; the bar always draws them. */
    private boolean mShowSeconds = true;
    /**
     * Whether a 12-hour clock's period is drawn in the meta column, over the seconds. A widget too
     * narrow for the column sheds it; the digits stay 12-hour.
     */
    private boolean mShowPeriod = true;
    /** Whether a seconds digit animates when it changes; a widget's face turns only on the minute. */
    private boolean mSecondsAnimated = true;
    /**
     * Whether the Label role grows with the face once it is drawn past scale 1. A widget's does: its
     * card holds the face at several times the bar's size, and a meta column held at the bar's
     * 12sp shrank to a speck beside the cards, the period over it smaller still. Growing, the
     * seconds and the period keep the expanded bar's proportions to the digits at any size, and
     * never paint under {@link #LABEL_SP}. The bar holds its labels at their painted size.
     */
    private boolean mLabelGrows;
    /** Smallest scale any plan hands out, so a starved budget never inverts the face. */
    private static final float MIN_SCALE = .05f;
    /** Budget the host hands the face; height 0 means no fit (the legacy shrink-only path). */
    private float mFitWidthPx;
    private float mFitHeightPx;
    /** True while the compact face is the scale floor. */
    private boolean mFitCompactFloor;

    /** Supplies the display metrics every dp and sp size is converted with, read on each use. */
    private final Resources mResources;

    public ClockFaceRenderer(@NonNull Resources resources) {
        mResources = resources;
    }

    /**
     * A renderer for a host that is not the status bar: the Clock widget. It draws the FULL face's
     * time band alone, without the bar's date row and its hairline running to the box edge (the
     * widget sets its date in its own layout), fits it to whatever box the host hands it up to
     * {@link #WIDGET_FIT_MAX_SCALE}, and turns the seconds without animating them, so the face
     * moves only on the minute. The host never sees the bar's {@link TopPaneClockForm}.
     */
    @NonNull
    public static ClockFaceRenderer forWidget(@NonNull Resources resources) {
        ClockFaceRenderer renderer = new ClockFaceRenderer(resources);
        renderer.mForm = TopPaneClockForm.FULL;
        renderer.mDateRow = false;
        renderer.mSecondsAnimated = false;
        renderer.mFitCeiling = WIDGET_FIT_MAX_SCALE;
        renderer.mLabelGrows = true;
        return renderer;
    }

    /** Whether the seconds are drawn. Returns true if it changed; the face's width moves with it. */
    public boolean setSeconds(boolean seconds) {
        if (mShowSeconds == seconds) return false;
        mShowSeconds = seconds;
        return true;
    }

    public boolean isShowingSeconds() {
        return mShowSeconds;
    }

    /** Whether a 12-hour period is drawn in the meta column. Returns true if it changed. */
    public boolean setPeriodShown(boolean shown) {
        if (mShowPeriod == shown) return false;
        mShowPeriod = shown;
        return true;
    }

    public boolean isShowingPeriod() {
        return mShowPeriod;
    }

    /** The FULL fit's ceiling, {@link #FIT_MAX_SCALE} unless a host asks for more. */
    public float fitCeiling() {
        return mFitCeiling;
    }

    /**
     * The largest the fit grows the FULL face to, for a host that is neither the bar nor a widget:
     * the Wall strip's row, whose clock grows with the row ({@code WallStripRowPolicy
     * .clockMaxScale}). A ceiling of 0 or less restores the bar's {@link #FIT_MAX_SCALE}.
     * Returns true if it changed; the face's scale and width move with it.
     */
    public boolean setFitCeiling(float ceiling) {
        float next = ceiling > 0f ? ceiling : FIT_MAX_SCALE;
        if (next == mFitCeiling) return false;
        mFitCeiling = next;
        return true;
    }

    /**
     * The FULL face's painted height in a {@code widthPx} by {@code heightPx} box, fitted as
     * {@link #setFitBudget} would fit it there: what a host lays the face out at. Needs a snapshot.
     */
    public float fittedColumnPx(float widthPx, float heightPx) {
        if (mSnapshot == null || widthPx <= 0f || heightPx <= 0f) return 0f;
        float[] plan = fitScalePlan(fullParts(), heightPx, widthPx, 0f, mFitCeiling, mPlan);
        return mFitParts.column(plan[0], plan[1] > 0f);
    }

    /** The FULL face's scale in a {@code widthPx} by {@code heightPx} box; see {@link #fittedColumnPx}. */
    public float fittedScale(float widthPx, float heightPx) {
        if (mSnapshot == null || widthPx <= 0f || heightPx <= 0f) return 0f;
        return fitScalePlan(fullParts(), heightPx, widthPx, 0f, mFitCeiling, mPlan)[0];
    }

    /** The FULL face's painted width in a {@code widthPx} by {@code heightPx} box. */
    public float fittedWidthPx(float widthPx, float heightPx) {
        if (mSnapshot == null || widthPx <= 0f || heightPx <= 0f) return 0f;
        return fitScalePlan(fullParts(), heightPx, widthPx, 0f, mFitCeiling, mPlan)[2];
    }

    /** The Label role's painted size, in sp: what the seconds and the period are drawn at. */
    public static float labelRoleSp() {
        return LABEL_SP;
    }

    /**
     * Selects the face; an unknown name falls back to Flip. {@code minimal_medium} is Minimal in
     * its medium weight. Returns true if it changed.
     */
    public boolean setStyle(@Nullable String style) {
        boolean medium = TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL_MEDIUM
            .equals(style);
        String normalized = medium ? TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL
            : isKnownStyle(style) ? style : TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP;
        if (normalized.equals(mStyle) && medium == mMinimalMedium) return false;
        mStyle = normalized;
        mMinimalMedium = medium;
        return true;
    }

    private static boolean isKnownStyle(@Nullable String style) {
        return TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP.equals(style)
            || TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD.equals(style)
            || TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL.equals(style)
            || TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED.equals(style)
            || TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE.equals(style)
            || TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB.equals(style);
    }

    @NonNull
    public String getStyle() {
        return mMinimalMedium ? TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL_MEDIUM
            : mStyle;
    }

    /**
     * Horizontal placement of the FULL-form time band and date text; compact forms ignore it.
     * An unknown value falls back to left. Returns true if it changed.
     */
    public boolean setAlignment(@Nullable String alignment) {
        String normalized = isKnownAlignment(alignment)
            ? alignment : TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_LEFT;
        if (normalized.equals(mAlignment)) return false;
        mAlignment = normalized;
        return true;
    }

    private static boolean isKnownAlignment(@Nullable String alignment) {
        return TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_LEFT.equals(alignment)
            || TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER.equals(alignment)
            || TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT.equals(alignment);
    }

    @NonNull
    public String getAlignment() {
        return mAlignment;
    }

    /** Which grid form renders. Returns true if it changed; the content width changes with it. */
    public boolean setForm(@NonNull TopPaneClockForm form) {
        if (mForm == form) return false;
        mForm = form;
        return true;
    }

    @NonNull
    public TopPaneClockForm getForm() {
        return mForm;
    }

    /**
     * 12-hour time with a period label. Returns true if it changed; the host then takes a fresh
     * {@link #updateTime} so the digits follow.
     */
    public boolean setUseAmPm(boolean useAmPm) {
        if (mUseAmPm == useAmPm) return false;
        mUseAmPm = useAmPm;
        return true;
    }

    public boolean isUsingAmPm() {
        return mUseAmPm;
    }

    /**
     * The FULL flip face's load flourish: the four cards turn in on a stagger, as if the board
     * just woke. Does nothing on any other face or form. Returns true if it was seeded, so the
     * host knows to repaint.
     */
    public boolean playLoadFlourish(long animationTime) {
        if (mForm != TopPaneClockForm.FULL
            || !TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP.equals(mStyle)) {
            return false;
        }
        seedFlipLoadFlourish(animationTime);
        return true;
    }

    /**
     * Every face paints out of these roles, so a wallpaper or scheme change re-tints the whole
     * grid. Returns true if the palette differs from the one in use; an unchanged palette keeps
     * its cached gradients, since a host may hand the same one over on every ink pass.
     */
    public boolean setPalette(@NonNull Palette palette) {
        mOnSurface = palette.onSurface;
        mSecondary = palette.secondary;
        mPrimary = palette.primary;
        mSurfaceBase = palette.surfaceBase;
        mSurfacePanel = palette.surfacePanel;
        mSurfacePanelHigh = palette.surfacePanelHigh;
        mSurfacePanelHighest = palette.surfacePanelHighest;
        mOutlineVariant = palette.outlineVariant;
        mOnSurfaceVariant = palette.onSurfaceVariant;
        int signature = java.util.Arrays.hashCode(new int[] {mOnSurface, mSecondary, mPrimary,
            mSurfaceBase, mSurfacePanel, mSurfacePanelHigh, mSurfacePanelHighest, mOutlineVariant,
            mOnSurfaceVariant});
        if (mColorsResolved && signature == mColorSignature) return false;
        mColorsResolved = true;
        mColorSignature = signature;
        mRuleColor = alpha(mPrimary, .22f);
        mTrackColor = alpha(mSecondary, .18f);
        resolveFlipColors();
        resolveInks();
        return true;
    }

    /**
     * The backdrop the host measured under the clock — the opaque colour its glass and veil come
     * to — and the ink the host resolved on it, whose side of the backdrop (paler or darker) every
     * face's ink then takes. Until a host calls this, the theme's base surface and its
     * {@code onSurface} stand in. Returns true if either changed.
     */
    public boolean setBandInk(@ColorInt int surface, @ColorInt int ink) {
        if (mHasBand && mBandSurface == surface && mBandInk == ink) return false;
        mHasBand = true;
        mBandSurface = surface;
        mBandInk = ink;
        if (mColorsResolved) resolveInks();
        return true;
    }

    /** The opaque colour the faces' ink is measured against. */
    @ColorInt
    private int backdrop() {
        return OnGlass.opaque(mHasBand ? mBandSurface : mSurfaceBase);
    }

    /**
     * Tones every ink against the backdrop: digits and graphics (LCD segments, the minute track)
     * to {@link OnGlass#TARGET_LARGE_TEXT}, the seconds, date and period to
     * {@link OnGlass#TARGET_BODY_TEXT}. Each keeps its role's hue and moves along its tone axis to
     * the side of the backdrop the bar's own ink is on, so on pale glass the LCD stays dark and on
     * dark glass it goes pale. Runs on a palette or backdrop change only, never in a draw.
     */
    private void resolveInks() {
        int backdrop = backdrop();
        int sideInk = mHasBand ? mBandInk : mOnSurface;
        boolean pale = SchemeTone.tone(sideInk) > SchemeTone.tone(backdrop);
        mDigitInk = faceInk(backdrop, mOnSurface, OnGlass.TARGET_LARGE_TEXT, pale);
        mAccentInk = faceInk(backdrop, mPrimary, OnGlass.TARGET_LARGE_TEXT, pale);
        mLcdGhost = alpha(mAccentInk, LCD_GHOST_ALPHA);
        mSecondsInk = faceInk(backdrop, mSecondary, OnGlass.TARGET_BODY_TEXT, pale);
        mDateInk = faceInk(backdrop, mOnSurfaceVariant, OnGlass.TARGET_BODY_TEXT, pale);
        mPeriodInk = faceInk(backdrop, mPrimary, OnGlass.TARGET_BODY_TEXT, pale);
        mPrimaryLine = alpha(mPeriodInk, .45f);
        // The flip digits stand on their card, not on the glass: measure them on what the card's
        // two passes leave of the backdrop, taking whichever side of it reads.
        int card = ColorUtils.compositeColors(alpha(mSurfacePanelHigh, FLIP_CARD_COVER), backdrop);
        mFlipDigitInk = OnGlass.inkOn(card, mOnSurface, OnGlass.TARGET_LARGE_TEXT);
    }

    /**
     * A role's ink on the backdrop, on the {@code pale} side. A role already on that side keeps
     * its tone and moves only as far as {@code target} asks. A role on the other side (a light
     * theme's dark ink over a dark wallpaper's band) is first mirrored across the tone axis, as
     * the theme's opposite scheme would set it: walked only to the target, a light theme's
     * near-black digits stopped at 3:1, a dim grey on the dark bar.
     */
    @ColorInt
    @VisibleForTesting
    static int faceInk(@ColorInt int backdrop, @ColorInt int seed, double target, boolean pale) {
        double seedTone = SchemeTone.tone(seed);
        if (!OnGlass.onSide(SchemeTone.tone(backdrop), seedTone, pale)) {
            seed = SchemeTone.toneShift(seed, (100d - seedTone) - seedTone);
        }
        return OnGlass.inkOn(backdrop, seed, target, pale);
    }

    /**
     * Glass stock. Both leaves are translucent {@code surfaceContainerHigh} sheets over the bar's
     * own glass, with the rim in {@code outlineVariant}; the digits are {@code onSurface}.
     *
     * <p>The card is painted in two passes — a whole-card base that casts the shadow, then the
     * leaf gradients over it — so every leaf colour here is chosen to stack on {@link #mFlipBase}
     * rather than to stand alone. The leaves meet the seam flat: the upper leaf's foot and the
     * lower leaf's head used to darken and brighten into it, and with the hinge clips over the
     * seam that drew three lines through every digit. One seam is the only line left.
     */
    private void resolveFlipColors() {
        // Any repalette retires the cached gradients; they carry the old colours.
        mFlipShaderGeneration++;
        mDarkFlipStock = ColorUtils.calculateLuminance(mSurfaceBase) < .5;
        int on = mOnSurface;
        // The leaves are plain surface stock, so the face reads as part of the bar's chrome.
        int leaf = mSurfacePanelHigh;
        mFlipBase = alpha(leaf, .5f);
        if (mDarkFlipStock) {
            mUpperFlipColors[0] = alpha(ColorUtils.blendARGB(leaf, GlassTokens.HIGHLIGHT, .08f), .5f);
            mUpperFlipColors[1] = alpha(leaf, .46f);
            mUpperFlipColors[2] = alpha(ColorUtils.blendARGB(leaf, GlassTokens.SHADE, .16f), .5f);
            mLowerFlipColors[1] = alpha(ColorUtils.blendARGB(leaf, GlassTokens.HIGHLIGHT, .06f), .48f);
            mLowerFlipColors[2] = alpha(leaf, .46f);
            mLowerFlipColors[3] = alpha(ColorUtils.blendARGB(leaf, GlassTokens.SHADE, .16f), .5f);
            mFlipRim = alpha(mOutlineVariant, .6f);
            mFlipSeam = alpha(GlassTokens.SHADE, .6f);
            mFlipShadow = ColorUtils.setAlphaComponent(GlassTokens.SHADE, 70);
        } else {
            mUpperFlipColors[0] = alpha(ColorUtils.blendARGB(leaf, GlassTokens.HIGHLIGHT, .5f), .55f);
            mUpperFlipColors[1] = alpha(leaf, .5f);
            mUpperFlipColors[2] = alpha(ColorUtils.blendARGB(leaf, on, .05f), .52f);
            mLowerFlipColors[1] = alpha(ColorUtils.blendARGB(leaf, GlassTokens.HIGHLIGHT, .35f), .5f);
            mLowerFlipColors[2] = alpha(leaf, .5f);
            mLowerFlipColors[3] = alpha(ColorUtils.blendARGB(leaf, on, .05f), .52f);
            mFlipRim = alpha(mOutlineVariant, .8f);
            mFlipSeam = alpha(mOnSurface, .3f);
            mFlipShadow = alpha(mOnSurface, .12f);
        }
        // Flat into the seam: no darker foot above it, no brighter head below it.
        mUpperFlipColors[3] = mUpperFlipColors[2];
        mLowerFlipColors[0] = mLowerFlipColors[1];
        mFlipRuleColor = alpha(mOutlineVariant, .8f);
    }

    /** Width the current form actually paints, so a host can place content beside it. */
    public float contentWidth() {
        if (mSnapshot == null) return 0f;
        switch (mForm) {
            case MONO_CHIP:
                return monoContentWidth();
            case COMPACT:
                return compactPaintedWidth(1f);
            default:
                return fullParts().paintedWidth(1f, true);
        }
    }

    // ---- The FULL face's parts --------------------------------------------

    /**
     * What the FULL face measures, split into what scales with it and what holds its painted
     * size. All in pixels at scale 1; see {@link FitParts}.
     */
    @NonNull
    private FitParts fullParts() {
        FitParts parts = mFitParts;
        float band = dp(fullBandHeightDp());
        parts.band = band;
        parts.timeScaled = fullTimeScaledPx();
        parts.timeFixed = fullTimeLabelPx();
        parts.timeFixedGrows = mLabelGrows;
        if (!mDateRow) {
            // The host sets the date: the column is the band, with Tape's minute track under it.
            parts.columnScaled = band;
            parts.columnFixed = 0f;
            parts.strokeScaled = 0f;
            parts.strokeFloor = 0f;
            if (isStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE)) {
                parts.columnScaled += dp(TAPE_TRACK_GAP_DP);
                parts.strokeScaled = TAPE_TRACK_FRACTION * band;
                parts.strokeFloor = dp(STROKE_FLOOR_DP);
            }
            parts.dateScaled = 0f;
            parts.dateFixed = 0f;
            return parts;
        }
        parts.columnScaled = band + dp(fullDateGapDp());
        parts.columnFixed = labelRowPx(dateTypeface());
        parts.strokeScaled = 0f;
        parts.strokeFloor = 0f;
        if (isStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE)) {
            // The minute track under the date: a band stroke, so it has a floor of its own.
            parts.columnScaled += dp(TAPE_TRACK_GAP_DP);
            parts.strokeScaled = TAPE_TRACK_FRACTION * band;
            parts.strokeFloor = dp(STROKE_FLOOR_DP);
        }
        // Tape's date sits under the time with no hairline; every other date row keeps one.
        parts.dateScaled = isStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE)
            ? 0f : dp(2f * RULE_GAP_DP + MIN_RULE_DP);
        parts.dateFixed = spacedTextWidth(mSnapshot.date, dateTypeface(), LABEL_SP, 0f);
        return parts;
    }

    /** The FULL time band's digits and the gap before the meta column, at scale 1. */
    private float fullTimeScaledPx() {
        float gap = hasMetaColumn() ? dp(fullMetaGapDp()) : 0f;
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
                return dp(94f) + gap;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                return glyphRunWidth(timeText(), minimalTypeface(), 38f, -.02f) + gap;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                return dotTextWidth(timeText(), LED_CELL_FRACTION * dp(26f)) + gap;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE:
                return glyphRunWidth(timeText(), Typeface.MONOSPACE, 28f, -.02f) + gap;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB:
                return glyphRunWidth(timeText(), Typeface.DEFAULT_BOLD, 39f, -.045f) + gap;
            default:
                return dp(flipRowWidthDp(FULL_FLIP_CARD_DP, FULL_FLIP_DIGIT_GAP_DP)) + gap;
        }
    }

    /** The FULL band's gap between the digits and the meta column, per face. */
    private float fullMetaGapDp() {
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                return 7f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE:
                return 5f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP:
                return 6.7f;
            default:
                return 6f;
        }
    }

    /** Whether a meta column follows the digits: the seconds, or a 12-hour clock's period. */
    private boolean hasMetaColumn() {
        return mShowSeconds
            || (mShowPeriod && mSnapshot != null && !mSnapshot.period.isEmpty());
    }

    /** The FULL meta column — the seconds, with the period over them — at its painted size. */
    private float fullTimeLabelPx() {
        if (!hasMetaColumn()) return 0f;
        if (isStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP)) {
            return fullFlipMetaWidth();
        }
        return stackedMetaWidth(secondsTypeface());
    }

    /** The face the seconds are set in, per style; the same in both forms. */
    @NonNull
    private Typeface secondsTypeface() {
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                return Typeface.DEFAULT;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB:
                return mediumTypeface();
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP:
                return mForm == TopPaneClockForm.FULL ? condensedBoldTypeface() : Typeface.DEFAULT;
            default:
                return Typeface.MONOSPACE;
        }
    }

    /** The face the date is set in, per style: each face's own family, in sentence case. */
    @NonNull
    private Typeface dateTypeface() {
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                return Typeface.DEFAULT;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB:
                return mediumTypeface();
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP:
                return condensedMediumTypeface();
            default:
                return Typeface.MONOSPACE;
        }
    }

    private boolean isStyle(@NonNull String style) {
        return style.equals(mStyle);
    }

    // ---- The compact face's parts -----------------------------------------

    /** The compact face's digits and gaps, at scale 1. */
    private float compactScaledWidth() {
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
                return dp(61f) + dp(5f);
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                return glyphRunWidth(timeText(), minimalTypeface(), 26f, -.02f) + dp(5f);
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                return dotTextWidth(timeText(), LED_CELL_FRACTION * dp(16f)) + dp(5f);
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE:
                return glyphRunWidth(timeText(), Typeface.MONOSPACE, 18f, -.02f) + dp(4f);
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB:
                return glyphRunWidth(timeText(), Typeface.DEFAULT_BOLD, 27f, -.045f) + dp(5f);
            default:
                // The cards, then the gap to the meta column plus the ink that glyphs and the card
                // shadow carry past their advance.
                return dp(flipRowWidthDp(COMPACT_FLIP_CARD_DP, COMPACT_FLIP_DIGIT_GAP_DP)) + dp(4f)
                    + dp(2f);
        }
    }

    /** What the compact face paints across at {@code scale}: its digits scale, its seconds hold. */
    private float compactPaintedWidth(float scale) {
        float painted = compactScaledWidth() * scale + stackedMetaWidth(secondsTypeface());
        if (isStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE)) {
            painted = Math.max(dp(100f) * scale, painted);
        }
        return painted;
    }

    /**
     * Scale of the compact face in a box: it never grows, shrinks to the box's height, and — the
     * host can hand it less than it paints (a wide meta column beside a media strip) — shrinks its
     * digits to the width rather than let the seconds fall off the edge.
     */
    private float compactScale(int widthPx, int heightPx) {
        float scale = Math.min(1f, heightPx / dp(compactColumnHeightDp()));
        if (widthPx > 0 && widthPx < compactPaintedWidth(scale)) {
            float fixed = stackedMetaWidth(secondsTypeface());
            scale = Math.min(scale, (widthPx - fixed) / Math.max(.01f, compactScaledWidth()));
            if (isStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE)) {
                scale = Math.min(scale, widthPx / dp(100f));
            }
        }
        return Math.max(MIN_SCALE, scale);
    }

    private float monoContentWidth() {
        float label = LABEL_MIN_SP;
        float width = spacedTextWidth(timeText(), Typeface.MONOSPACE, 14f, .02f) + dp(5f)
            + spacedTextWidth(mSnapshot.ss, Typeface.MONOSPACE, label, 0f) + dp(5f);
        if (!mSnapshot.period.isEmpty()) {
            width += spacedTextWidth(mSnapshot.period, Typeface.MONOSPACE, label * PERIOD_SCALE,
                .16f) + dp(5f);
        }
        return width + dp(2f) + dp(5f)
            + spacedTextWidth(mSnapshot.date, Typeface.MONOSPACE, label, 0f);
    }

    /**
     * Width of a stacked meta column at the Label role's painted size: the seconds set the width
     * and the period is tracked out (or shrunk) to match it, so the column is exactly as wide as
     * the seconds on every face.
     */
    private float stackedMetaWidth(Typeface secondsFace) {
        // Measured on the widest digit pair the face can show, not on this second's digits: a
        // proportional face makes "11" narrower than "44", and a column that breathed with the
        // seconds shoved the minute cards sideways once a second.
        return widestDigitsWidth(2, secondsFace, LABEL_SP);
    }

    /** Width of {@code count} copies of the face's widest digit at this size. */
    private float widestDigitsWidth(int count, Typeface typeface, float textSp) {
        mPaint.setTypeface(typeface);
        mPaint.setLetterSpacing(0f);
        mPaint.setTextSize(sp(textSp));
        float widest = 0f;
        for (char digit = '0'; digit <= '9'; digit++) {
            widest = Math.max(widest, mWidestAdvances.advance(digit, typeface,
                mPaint.getTextSize(), 0f, mMeasure));
        }
        return widest * count;
    }

    /** The full flip's meta column at its painted size: two seconds cells, or the period over them. */
    private float fullFlipMetaWidth() {
        float seconds = widestDigitsWidth(2, condensedBoldTypeface(), LABEL_SP);
        if (mSnapshot.period.isEmpty()) return seconds;
        return Math.max(seconds, spacedTextWidth(mSnapshot.period, condensedBoldTypeface(),
            LABEL_SP * PERIOD_SCALE, .1f));
    }

    /** A Label row's height at its painted size: the face's ascent to its descent. */
    private float labelRowPx(Typeface typeface) {
        mPaint.setTypeface(typeface);
        mPaint.setLetterSpacing(0f);
        mPaint.setTextSize(sp(LABEL_SP));
        return mPaint.descent() - mPaint.ascent();
    }

    // ---- Drawing ----------------------------------------------------------

    /** True once the first {@link #updateTime} has given the faces a time to draw. */
    public boolean hasSnapshot() {
        return mSnapshot != null;
    }

    /**
     * Paints the current form and face into a box {@code widthPx} by {@code heightPx} with its
     * origin at the canvas origin. {@code now} is the animation clock ({@code uptimeMillis}) the
     * digit changes were stamped with. Nothing is drawn before the first snapshot or into an
     * empty box.
     */
    public void draw(@NonNull Canvas canvas, int widthPx, int heightPx, long now) {
        if (mSnapshot == null || widthPx <= 0 || heightPx <= 0) return;
        switch (mForm) {
            case MONO_CHIP:
                drawMonoChip(canvas, heightPx);
                break;
            case COMPACT:
                drawCompact(canvas, widthPx, heightPx, now);
                break;
            default:
                drawFull(canvas, widthPx, heightPx, now);
                break;
        }
        mScale = 1f;
        mLabelSp = LABEL_SP;
        mLabelGrowth = 1f;
    }

    /**
     * True if the time in a box {@code widthPx} by {@code heightPx} is painted under
     * ({@code x}, {@code y}): the time band alone in FULL form — the digits, not the date row
     * beneath them — and the painted column in the compact forms.
     */
    public boolean isOverTime(float x, float y, int widthPx, int heightPx) {
        float[] painted = paintedXRangePx(widthPx, heightPx);
        if (x < painted[0] || x > painted[1]) return false;
        float[] band = tapYRangePx(widthPx, heightPx);
        return y >= band[0] && y <= band[1];
    }

    /**
     * Vertical extent of the tap target, as {top, bottom} in box pixels: the time band alone in
     * FULL form — the digits, not the date row beneath them — and the painted column in the
     * compact forms, which draw no date row of their own.
     */
    private float[] tapYRangePx(int widthPx, int heightPx) {
        if (mSnapshot == null) return new float[] {0f, heightPx};
        switch (mForm) {
            case MONO_CHIP: {
                float rowHeight = dp(17f);
                float top = Math.max(0f, (heightPx - rowHeight) / 2f);
                return new float[] {top, top + rowHeight};
            }
            case COMPACT: {
                float column = dp(compactColumnHeightDp()) * compactScale(widthPx, heightPx);
                float top = Math.max(0f, (heightPx - column) / 2f);
                return new float[] {top, top + column};
            }
            default: {
                float[] plan = fullPlan(heightPx);
                float scale = plan[0];
                boolean date = plan[1] > 0f;
                FitParts parts = mFitParts;
                float top = Math.max(0f, (heightPx - parts.column(scale, date)) / 2f);
                return new float[] {top, top + parts.band * scale};
            }
        }
    }

    /**
     * The trailing edge of the painted clock in a box {@code widthPx} by {@code heightPx}.
     *
     * <p>In FULL form the face paints inside the whole box at its own alignment, so the box's
     * right edge is nowhere near the clock's. Anything placed beside the clock has to ask for
     * this rather than measure the box.
     */
    public float paintedRightPx(int widthPx, int heightPx) {
        return paintedXRangePx(widthPx, heightPx)[1];
    }

    /** The leading edge of the painted clock; see {@link #paintedRightPx(int, int)}. */
    public float paintedLeftPx(int widthPx, int heightPx) {
        return paintedXRangePx(widthPx, heightPx)[0];
    }

    /** Where the clock actually paints across the box, as {left, right} in box pixels. */
    private float[] paintedXRangePx(int widthPx, int heightPx) {
        if (mSnapshot == null) return new float[] {0f, widthPx};
        switch (mForm) {
            case MONO_CHIP:
                return new float[] {0f, contentWidth()};
            case COMPACT:
                return new float[] {0f, compactPaintedWidth(compactScale(widthPx, heightPx))};
            default: {
                float[] plan = fullPlan(heightPx);
                float scale = plan[0];
                float painted = mFitParts.paintedWidth(scale, plan[1] > 0f);
                float left = alignmentDx(widthPx / scale, painted / scale) * scale;
                return new float[] {left, left + painted};
            }
        }
    }

    private void drawFull(Canvas canvas, int widthPx, int heightPx, long now) {
        float[] plan = fullPlan(heightPx);
        float scale = plan[0];
        boolean showsDate = plan[1] > 0f;
        FitParts parts = mFitParts;
        float column = parts.column(scale, showsDate);
        float bandDx = fullBandDx(widthPx / scale, parts.timeWidth(scale) / scale);
        mScale = scale;
        mLabelGrowth = labelGrowth(mLabelGrows, scale);
        mLabelSp = LABEL_SP * mLabelGrowth;
        canvas.save();
        canvas.translate(0f, Math.max(0f, (heightPx - column) / 2f));
        canvas.scale(scale, scale);
        if (!showsDate) {
            // The band alone fits: the date row (and its hairline) is not painted.
            canvas.clipRect(-dp(4f), -dp(4f), widthPx / scale + dp(4f),
                parts.band + dp(1.5f));
        }
        float dateTop = dp(fullBandHeightDp() + fullDateGapDp());
        // The hairline and the tape track run to the box's edge, so they need the unscaled edge.
        float right = widthPx / scale;
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
                drawFullLcd(canvas, now, dateTop, right, bandDx);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                drawFullMinimal(canvas, now, dateTop, right, bandDx);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                drawFullLed(canvas, now, dateTop, right, bandDx);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE:
                drawFullTape(canvas, now, dateTop, right, bandDx);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB:
                drawFullSlab(canvas, now, dateTop, right, bandDx);
                break;
            default:
                drawFullFlip(canvas, now, dateTop, right, bandDx);
                break;
        }
        canvas.restore();
    }

    /**
     * Center Y, in box pixels, of the FULL-form time band — the digits' line, above the date row —
     * in a box {@code heightPx} tall. -1 in any other form or for an empty box.
     */
    public float fullBandCenterYPx(int heightPx) {
        if (mForm != TopPaneClockForm.FULL || heightPx <= 0 || mSnapshot == null) return -1f;
        float[] plan = fullPlan(heightPx);
        float scale = plan[0];
        FitParts parts = mFitParts;
        float translate = Math.max(0f, (heightPx - parts.column(scale, plan[1] > 0f)) / 2f);
        return translate + parts.band * scale / 2f;
    }

    /** Height, in pixels, of the FULL-form time band; -1 in any other form or for an empty box. */
    public float fullBandHeightPx(int heightPx) {
        if (mForm != TopPaneClockForm.FULL || heightPx <= 0 || mSnapshot == null) return -1f;
        float scale = fullPlan(heightPx)[0];
        return mFitParts.band * scale;
    }

    /** The FULL column's painted height — band, gap and date row — in a box {@code heightPx} tall. */
    @VisibleForTesting
    float fullColumnPx(int heightPx) {
        float[] plan = fullPlan(heightPx);
        return mFitParts.column(plan[0], plan[1] > 0f);
    }

    /**
     * Center Y, in box pixels, of the FULL-form date hairline. -1 when the current form/style
     * draws no hairline (compact forms, tape, a dropped date row) or for an empty box.
     */
    public float fullRuleCenterYPx(int heightPx) {
        if (mForm != TopPaneClockForm.FULL || heightPx <= 0 || mSnapshot == null) return -1f;
        if (isStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE)) return -1f;
        float[] plan = fullPlan(heightPx);
        if (plan[1] <= 0f) return -1f;
        float scale = plan[0];
        FitParts parts = mFitParts;
        float translate = Math.max(0f, (heightPx - parts.column(scale, true)) / 2f);
        return translate + parts.columnScaled * scale + parts.columnFixed / 2f;
    }

    /** The FULL-form hairline's half thickness in pixels, as the date row paints it. */
    public float fullRuleHalfThicknessPx(int heightPx) {
        float scale = heightPx <= 0 || mSnapshot == null ? 1f : fullPlan(heightPx)[0];
        return Math.max(RULE_FRACTION * dp(fullBandHeightDp()) * scale, dp(STROKE_FLOOR_DP)) / 2f;
    }

    /** The FULL-form hairline colour. */
    public int fullRuleColor() {
        return isStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP)
            ? mFlipRuleColor : mRuleColor;
    }

    /** Horizontal offset placing a run of {@code width} against {@code right} per the alignment. */
    private float alignmentDx(float right, float width) {
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER.equals(mAlignment))
            return Math.max(0f, (right - width) / 2f);
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT.equals(mAlignment))
            return Math.max(0f, right - width);
        return 0f;
    }

    /**
     * Left edge of a FULL-form date row. The text clears the pane edge by the same gap it leaves
     * around its hairline, so the row reads inset on both sides rather than flush on the left.
     */
    private float dateRowTextX(float right, float width, float gapDp) {
        return Math.max(dp(gapDp), alignmentDx(right, width));
    }

    /** Offset for the FULL time band, {@code band} wide in the scaled canvas's units. */
    private float fullBandDx(float right, float band) {
        return alignmentDx(right, band);
    }

    private void drawCompact(Canvas canvas, int widthPx, int heightPx, long now) {
        float columnDp = compactColumnHeightDp();
        float scale = compactScale(widthPx, heightPx);
        mScale = scale;
        mLabelSp = LABEL_SP;
        mLabelGrowth = 1f;
        canvas.save();
        canvas.translate(0f, Math.max(0f, (heightPx - dp(columnDp) * scale) / 2f));
        canvas.scale(scale, scale);
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
                drawCompactLcd(canvas, now);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                drawCompactMinimal(canvas, now);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                drawCompactLed(canvas, now);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE:
                drawCompactTape(canvas, now);
                break;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB:
                drawCompactSlab(canvas, now);
                break;
            default:
                drawCompactFlip(canvas, now);
                break;
        }
        canvas.restore();
    }

    // ---- Fit ----------------------------------------------------------------

    /**
     * Hands the FULL face the room its host can give it: a width and a height budget in pixels,
     * which the face fills by one uniform scale (see {@link #fitPlan}). A height of 0 turns the
     * fit off, and the face then only ever shrinks to the box it is drawn into.
     * {@code compactFloor} keeps the compact face's size as the floor the scale never goes under,
     * dropping the date row first. Returns true if the budget changed.
     */
    public boolean setFitBudget(float widthPx, float heightPx, boolean compactFloor) {
        if (mFitWidthPx == widthPx && mFitHeightPx == heightPx
            && mFitCompactFloor == compactFloor) return false;
        mFitWidthPx = widthPx;
        mFitHeightPx = heightPx;
        mFitCompactFloor = compactFloor;
        return true;
    }

    private boolean fitActive() {
        return mFitHeightPx > 0f && mFitWidthPx > 0f && mSnapshot != null;
    }

    /**
     * The FULL face's plan in a box {@code heightPx} tall, into the shared scratch (and with
     * {@link #mFitParts} filled for it): in fit mode it fills the budget, otherwise it only ever
     * shrinks to the box and always keeps the date.
     */
    @NonNull
    private float[] fullPlan(int heightPx) {
        FitParts parts = fullParts();
        if (fitActive()) {
            return fitScalePlan(parts, mFitHeightPx, mFitWidthPx, fullFloor(), mFitCeiling,
                mPlan);
        }
        float scale = Math.max(MIN_SCALE, Math.min(1f, parts.solveColumn(heightPx)));
        mPlan[0] = scale;
        mPlan[1] = 1f;
        mPlan[2] = parts.paintedWidth(scale, true);
        return mPlan;
    }

    private float fullFloor() {
        return mFitCompactFloor ? compactColumnHeightDp() / fullBandHeightDp() : 0f;
    }

    /**
     * The FULL face's plan for a width and height budget, for the current style and time:
     * {scale, 1 if the date row stays else 0, painted width in pixels}. Needs a snapshot.
     */
    @NonNull
    public float[] fitPlan(float widthBudgetPx, float heightBudgetPx, boolean compactFloor) {
        float floor = compactFloor ? compactColumnHeightDp() / fullBandHeightDp() : 0f;
        return fitScalePlan(fullParts(), heightBudgetPx, widthBudgetPx, floor, mFitCeiling,
            new float[3]);
    }

    /** Width the FULL face paints inside the given budget, so a host can size the clock to it. */
    public float fitWidthPx(float widthBudgetPx, float heightBudgetPx, boolean compactFloor) {
        if (mSnapshot == null || widthBudgetPx <= 0f || heightBudgetPx <= 0f) return 0f;
        float floor = compactFloor ? compactColumnHeightDp() / fullBandHeightDp() : 0f;
        return fitScalePlan(fullParts(), heightBudgetPx, widthBudgetPx, floor, mFitCeiling,
            mPlan)[2];
    }

    /**
     * A FULL face measured for its fit, in pixels at scale 1. Everything in the time band scales
     * with the face; the Label-role text (the seconds column, the date) keeps its painted size,
     * so each extent is {@code scaled × scale + fixed}.
     *
     * <ul>
     *   <li>{@code band}: the time band's height.</li>
     *   <li>{@code columnScaled}, {@code columnFixed}: the column with its date row — the band
     *   and the gap under it, and the date row's text height.</li>
     *   <li>{@code strokeScaled}, {@code strokeFloor}: a band stroke stacked in the column (Tape's
     *   minute track), which never paints under its floor.</li>
     *   <li>{@code timeScaled}, {@code timeFixed}: the time row's digits and its seconds.</li>
     *   <li>{@code dateScaled}, {@code dateFixed}: the date row's gaps and shortest hairline, and
     *   its text.</li>
     * </ul>
     */
    @VisibleForTesting
    static final class FitParts {
        float band;
        float columnScaled;
        float columnFixed;
        float strokeScaled;
        float strokeFloor;
        float timeScaled;
        float timeFixed;
        /** Whether {@code timeFixed} grows with the scale past 1 (a widget's Label role). */
        boolean timeFixedGrows;
        float dateScaled;
        float dateFixed;

        /** The painted column at {@code scale}: band only, or band, date row and any stroke. */
        float column(float scale, boolean date) {
            if (!date) return band * scale;
            return columnScaled * scale + columnFixed + Math.max(strokeScaled * scale, strokeFloor);
        }

        /** The scale at which the column with its date row is exactly {@code height} tall. */
        float solveColumn(float height) {
            if (strokeScaled <= 0f && strokeFloor <= 0f) {
                return (height - columnFixed) / Math.max(.01f, columnScaled);
            }
            float withStroke = (height - columnFixed) / Math.max(.01f, columnScaled + strokeScaled);
            if (strokeScaled * withStroke >= strokeFloor) return withStroke;
            return (height - columnFixed - strokeFloor) / Math.max(.01f, columnScaled);
        }

        float timeWidth(float scale) {
            return timeScaled * scale + timeFixed * labelGrowth(timeFixedGrows, scale);
        }

        /** The largest scale at which the time row fits {@code width}. */
        float timeWidthScale(float width) {
            if (timeFixedGrows && timeScaled + timeFixed > 0f) {
                // Past 1 the meta column grows with the digits, so the whole row scales together.
                float together = width / (timeScaled + timeFixed);
                if (together >= 1f) return together;
            }
            return widthScale(width, timeScaled, timeFixed);
        }

        float dateWidth(float scale) {
            return dateScaled * scale + dateFixed;
        }

        float paintedWidth(float scale, boolean date) {
            return date ? Math.max(timeWidth(scale), dateWidth(scale)) : timeWidth(scale);
        }
    }

    /**
     * The one uniform scale for the FULL face. The date block stays while the whole column fits
     * above the floor; below it the date is dropped and the band alone is fitted. Width is always
     * honoured, by shrinking what scales. Writes {scale, 1 if the date stays else 0, painted
     * width} into {@code out} and returns it.
     */
    @VisibleForTesting
    @NonNull
    static float[] fitScalePlan(@NonNull FitParts parts, float heightBudget, float widthBudget,
                                float floor, @NonNull float[] out) {
        return fitScalePlan(parts, heightBudget, widthBudget, floor, FIT_MAX_SCALE, out);
    }

    /** {@link #fitScalePlan(FitParts, float, float, float, float[])} under {@code ceiling}. */
    @NonNull
    static float[] fitScalePlan(@NonNull FitParts parts, float heightBudget, float widthBudget,
                                float floor, float ceiling, @NonNull float[] out) {
        float byColumn = parts.solveColumn(heightBudget);
        float scale;
        boolean date;
        if (byColumn >= floor) {
            scale = Math.min(byColumn, ceiling);
            scale = Math.min(scale, parts.timeWidthScale(widthBudget));
            scale = Math.min(scale, widthScale(widthBudget, parts.dateScaled, parts.dateFixed));
            date = true;
        } else {
            float byBand = Math.max(floor,
                Math.min(heightBudget / Math.max(.01f, parts.band), ceiling));
            scale = Math.min(byBand, parts.timeWidthScale(widthBudget));
            date = false;
        }
        scale = Math.max(MIN_SCALE, scale);
        out[0] = scale;
        out[1] = date ? 1f : 0f;
        out[2] = parts.paintedWidth(scale, date);
        return out;
    }

    /** The largest scale at which {@code scaled × scale + fixed} fits {@code width}. */
    private static float widthScale(float width, float scaled, float fixed) {
        if (scaled <= 0f) return Float.MAX_VALUE;
        return (width - fixed) / scaled;
    }

    /**
     * A band stroke — a hairline, a seam, a segment, a cell — in the scaled canvas's units:
     * {@code fraction} of the band, but never thinner than {@code floorPx} once painted at
     * {@code scale}.
     */
    @VisibleForTesting
    static float bandStroke(float fraction, float bandPx, float scale, float floorPx) {
        return Math.max(fraction * bandPx, floorPx / Math.max(.01f, scale));
    }

    private float bandStroke(float fraction, float bandPx) {
        return bandStroke(fraction, bandPx, mScale, dp(STROKE_FLOOR_DP));
    }

    /** The Label role's text size inside the scaled canvas, so it paints at {@link #mLabelSp}. */
    private float labelSp() {
        return mLabelSp / mScale;
    }

    /**
     * A label-space length: {@code value} dp once painted, whatever the face's scale, grown with
     * the Label role where it grows.
     */
    private float lp(float value) {
        return dp(value) * mLabelGrowth / mScale;
    }

    /** The Label role's growth at {@code scale}: none, or the scale itself past 1 where it grows. */
    @VisibleForTesting
    static float labelGrowth(boolean grows, float scale) {
        return grows ? Math.max(1f, scale) : 1f;
    }

    private float fullBandHeightDp() {
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                return 26f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE:
                return 22f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP:
                return FULL_FLIP_CARD_HEIGHT_DP;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB:
                return CAP_BAND_DP;
            default:
                return 34f;
        }
    }

    private float fullDateGapDp() {
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB:
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE:
                return 3f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                return 4f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP:
                return FLIP_SHADOW_BLUR_DP + FLIP_SHADOW_DROP_DP;
            default:
                return 4f;
        }
    }

    /** Compact drops the date row on every face; tape keeps a short minute track instead. */
    private float compactColumnHeightDp() {
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
                return 22f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB:
                return 22f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                return 16f;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE:
                return 21f;
            default:
                return 24f;
        }
    }

    // ---- Split-flap -------------------------------------------------------

    private void drawFullFlip(Canvas canvas, long now, float dateTop, float right, float bandDx) {
        float digitBaseline = capCenteredBaseline(dp(FULL_FLIP_CARD_HEIGHT_DP) / 2f,
            condensedBoldTypeface(), FULL_FLIP_DIGIT_SP);
        canvas.save();
        canvas.translate(bandDx, 0f);
        float x = drawFullFlipCards(canvas, now, digitBaseline);
        drawFullFlipMetaColumn(canvas, x + dp(6.7f), digitBaseline, now);
        canvas.restore();
        if (mDateRow) drawFullFlipDateRow(canvas, dateTop, right);
    }

    private float drawFullFlipCards(Canvas canvas, long now, float digitBaseline) {
        float x = 0f;
        for (int digit = 0; digit < 4; digit++) {
            x = drawFullFlipDigit(canvas, digit, x, digitBaseline, now);
            x += dp(flipGapAfterDp(digit, FULL_FLIP_DIGIT_GAP_DP));
        }
        return x;
    }

    /** What follows card {@code digit} (0-3): a digit gap inside a pair, the pair gap after it. */
    private static float flipGapAfterDp(int digit, float digitGapDp) {
        if (digit == 0 || digit == 2) return digitGapDp;
        return digit == 1 ? flipPairGapDp(digitGapDp) : 0f;
    }

    @VisibleForTesting
    static float flipPairGapDp(float digitGapDp) {
        return digitGapDp * FLIP_PAIR_GAP_RATIO;
    }

    /** Four cards, the two digit gaps and the pair gap: the width the flip time row paints. */
    @VisibleForTesting
    static float flipRowWidthDp(float cardDp, float digitGapDp) {
        return 4f * cardDp + 2f * digitGapDp + flipPairGapDp(digitGapDp);
    }

    private float drawFullFlipDigit(Canvas canvas, int digit, float x, float digitBaseline,
                                    long now) {
        // Wider than the handoff's 22dp: at 30.5dp the widest digits came within a hair of the
        // card's edge, so the card carries 2dp more clearance per side.
        float w = dp(FULL_FLIP_CARD_DP), h = dp(FULL_FLIP_CARD_HEIGHT_DP);
        RectF card = mRect;
        card.set(x, 0f, x + w, h);
        float p = progress(digit, now, FLIP_DURATION_MS);
        boolean animating = p < 1f;

        mFillPaint.setShader(null);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(mFlipBase);
        // Below API 28 hardware setShadowLayer is text-only; API 26/27 are shadowless (accepted).
        mFillPaint.setShadowLayer(dp(FLIP_SHADOW_BLUR_DP), 0f, dp(FLIP_SHADOW_DROP_DP),
            mFlipShadow);
        canvas.drawRoundRect(card, dp(1.5f), dp(1.5f), mFillPaint);
        mFillPaint.clearShadowLayer();

        if (!animating) {
            drawFullFlipHalf(canvas, card, true, mDigits[digit], true, digitBaseline, 0f);
            drawFullFlipHalf(canvas, card, false, mDigits[digit], true, digitBaseline, 0f);
        } else if (p < .5f) {
            float local = p * 2f;
            // The static top is deliberately blank while the old top flap covers it.
            drawFullFlipHalf(canvas, card, true, mDigits[digit], false, digitBaseline, 0f);
            drawFullFlipHalf(canvas, card, false, mOldDigits[digit], true, digitBaseline, 0f);
            drawFullRotatedFlipHalf(canvas, card, true, mOldDigits[digit], digitBaseline,
                -90f * local * local, local);
        } else {
            float local = (p - .5f) * 2f;
            float eased = 1f - (1f - local) * (1f - local);
            drawFullFlipHalf(canvas, card, true, mDigits[digit], true, digitBaseline, 0f);
            // The static bottom is deliberately blank while the new bottom flap covers it.
            drawFullFlipHalf(canvas, card, false, mDigits[digit], false, digitBaseline, 0f);
            drawFullRotatedFlipHalf(canvas, card, false, mDigits[digit], digitBaseline,
                90f * (1f - eased), 1f - eased);
        }

        drawFlipRimAndSeam(canvas, card, dp(1.5f), dp(FULL_FLIP_CARD_HEIGHT_DP), true);
        return card.right;
    }

    /**
     * A card's rim and its one hinge seam, both band strokes, and — on the full card — its two
     * end pins. The hinge clips that used to sit across the seam stay gone: with the leaves' own
     * shading at the fold they drew three lines through every digit, and one clean seam is what
     * says "split flap". The end pins are back by decision (2026-10-25): two small tabs on the
     * rim at the ends of the split line give the leaves an axle without a mark across the digits,
     * since they sit wholly inside the clearance between the card's edge and its digit
     * ({@link #flipEndPins}). The compact card is too small for them to read.
     */
    private void drawFlipRimAndSeam(Canvas canvas, RectF card, float corner, float bandPx,
                                    boolean endPins) {
        mFillPaint.setShader(null);
        mFillPaint.setStyle(Paint.Style.STROKE);
        float rim = bandStroke(FLIP_RIM_FRACTION, bandPx);
        mFillPaint.setStrokeWidth(rim);
        mFillPaint.setColor(mFlipRim);
        canvas.drawRoundRect(card, corner, corner, mFillPaint);
        mFillPaint.setStyle(Paint.Style.FILL);
        float half = bandStroke(FLIP_SEAM_FRACTION, bandPx) / 2f;
        float split = card.centerY();
        mFillPaint.setColor(mFlipSeam);
        canvas.drawRect(card.left, split - half, card.right, split + half, mFillPaint);
        if (!endPins || !mFlipEndPinsDrawn) return;
        flipEndPins(card, rim, fullFlipDigitClearance(card), mFlipPinLeft, mFlipPinRight);
        if (mFlipPinLeft.isEmpty()) return;
        float radius = mFlipPinLeft.width() / 2f;
        mFillPaint.setColor(mFlipRim);
        canvas.drawRoundRect(mFlipPinLeft, radius, radius, mFillPaint);
        canvas.drawRoundRect(mFlipPinRight, radius, radius, mFillPaint);
    }

    /**
     * A flip card's two end pins: {@link #FLIP_PIN_HEIGHT_FRACTION} of the card's height tall and
     * centred on its split line, each standing on the rim from the card's edge inward. They are
     * {@link #FLIP_PIN_WIDTH_FRACTION} of the height wide, but at least the rim's stroke (half of
     * which falls outside the card, so a narrower pin would hide in it), and never wider than
     * {@code clearance}, the room between the card's edge and its digit, so a pin never reaches
     * the digit. Nothing lands outside the card. Empty pins when there is no clearance.
     */
    @VisibleForTesting
    static void flipEndPins(@NonNull RectF card, float rimStroke, float clearance,
                            @NonNull RectF left, @NonNull RectF right) {
        float width = Math.min(Math.max(FLIP_PIN_WIDTH_FRACTION * card.height(), rimStroke),
            Math.max(0f, clearance));
        float half = FLIP_PIN_HEIGHT_FRACTION * card.height() / 2f;
        float split = card.centerY();
        left.set(card.left, split - half, card.left + width, split + half);
        right.set(card.right - width, split - half, card.right, split + half);
    }

    /** The room between a full flip card's edge and the widest digit centred in it. */
    private float fullFlipDigitClearance(@NonNull RectF card) {
        return (card.width() - widestDigitsWidth(1, condensedBoldTypeface(), FULL_FLIP_DIGIT_SP))
            / 2f;
    }

    /**
     * The end pins of full flip card {@code digit} (0-3) in box pixels, as {@link #draw} paints
     * them into a box {@code widthPx} by {@code heightPx}. False, with nothing written, outside
     * the full Flip face or before the first snapshot.
     */
    @VisibleForTesting
    public boolean flipEndPinsPx(int widthPx, int heightPx, int digit, @NonNull RectF left,
                                 @NonNull RectF right) {
        if (mForm != TopPaneClockForm.FULL || mSnapshot == null || widthPx <= 0 || heightPx <= 0
            || !isStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP)) {
            return false;
        }
        float[] plan = fullPlan(heightPx);
        float scale = plan[0];
        FitParts parts = mFitParts;
        float top = Math.max(0f, (heightPx - parts.column(scale, plan[1] > 0f)) / 2f);
        float bandDx = fullBandDx(widthPx / scale, parts.timeWidth(scale) / scale);
        float x = 0f;
        for (int d = 0; d < digit; d++) {
            x += dp(FULL_FLIP_CARD_DP) + dp(flipGapAfterDp(d, FULL_FLIP_DIGIT_GAP_DP));
        }
        RectF card = new RectF(x, 0f, x + dp(FULL_FLIP_CARD_DP), dp(FULL_FLIP_CARD_HEIGHT_DP));
        float rim = bandStroke(FLIP_RIM_FRACTION, card.height(), scale, dp(STROKE_FLOOR_DP));
        flipEndPins(card, rim, fullFlipDigitClearance(card), left, right);
        for (RectF pin : new RectF[] {left, right}) {
            pin.set((bandDx + pin.left) * scale, top + pin.top * scale,
                (bandDx + pin.right) * scale, top + pin.bottom * scale);
        }
        return true;
    }

    /** Whether the full flip card draws its end pins; a test draws without them to find them. */
    @VisibleForTesting
    public void setFlipEndPinsDrawn(boolean drawn) {
        mFlipEndPinsDrawn = drawn;
    }

    /** The stock gradient belongs to the physical half; only the flying half receives fold wash. */
    private void drawFullFlipHalf(Canvas canvas, RectF card, boolean top, char digit,
                                  boolean drawDigit, float digitBaseline, float foldShade) {
        float split = card.centerY();
        if (top) {
            mFlipHalfClip.set(card.left, card.top, card.right, split);
        } else {
            mFlipHalfClip.set(card.left, split, card.right, card.bottom);
        }
        canvas.save();
        canvas.clipRect(mFlipHalfClip);
        mFillPaint.setShader(top
            ? upperFlipShader(card.top, split)
            : lowerFlipShader(split, card.bottom));
        canvas.drawRoundRect(card, dp(1.5f), dp(1.5f), mFillPaint);
        mFillPaint.setShader(null);
        if (drawDigit) {
            mPaint.setTypeface(condensedBoldTypeface());
            mPaint.setLetterSpacing(0f);
            mPaint.setTextSize(sp(FULL_FLIP_DIGIT_SP));
            mPaint.setColor(mFlipDigitInk);
            mPaint.setAlpha(255);
            mPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(CharAdvances.charString(digit), card.centerX(), digitBaseline, mPaint);
        }
        if (foldShade > 0f) {
            mFillPaint.setColor(ColorUtils.setAlphaComponent(GlassTokens.SHADE,
                Math.round(255f * .22f * clamp01(foldShade))));
            canvas.drawRoundRect(card, dp(1.5f), dp(1.5f), mFillPaint);
        }
        canvas.restore();
    }

    private void drawFullRotatedFlipHalf(Canvas canvas, RectF card, boolean top, char digit,
                                         float digitBaseline, float angle, float foldShade) {
        float pivotY = card.centerY();
        mCamera.save();
        mCamera.rotateX(angle);
        mCamera.getMatrix(mMatrix);
        mCamera.restore();
        mMatrix.preTranslate(-card.centerX(), -pivotY);
        mMatrix.postTranslate(card.centerX(), pivotY);
        canvas.save();
        canvas.concat(mMatrix);
        drawFullFlipHalf(canvas, card, top, digit, true, digitBaseline, foldShade);
        canvas.restore();
    }

    private void drawFullFlipMetaColumn(Canvas canvas, float x, float digitBaseline, long now) {
        Typeface face = condensedBoldTypeface();
        float cellWidth = widestDigitsWidth(1, face, mLabelSp) / mScale;
        float secondsSp = labelSp();
        mPaint.setTypeface(face);
        mPaint.setLetterSpacing(0f);
        mPaint.setTextSize(sp(secondsSp));
        mPaint.setTextAlign(Paint.Align.CENTER);
        float secondsAscent = mPaint.ascent();
        float secondsDescent = mPaint.descent();
        float secondsCapHeight = capHeight(face, secondsSp);
        float split = digitBaseline - secondsCapHeight / 2f;
        for (int i = 0; i < 2 && mShowSeconds; i++) {
            mFlipMetaCell.set(x + i * cellWidth, digitBaseline + secondsAscent,
                x + (i + 1) * cellWidth, digitBaseline + secondsDescent);
            drawFullFlipSecond(canvas, i, mFlipMetaCell, split, digitBaseline, now);
        }

        if (!mShowPeriod || mSnapshot.period.isEmpty()) return;
        mPaint.setTypeface(face);
        mPaint.setLetterSpacing(.1f);
        mPaint.setTextSize(sp(secondsSp * PERIOD_SCALE));
        // With the seconds shed the period takes their row.
        float periodBaseline = digitBaseline - mPaint.descent()
            + (mShowSeconds ? secondsAscent - lp(2f) : 0f);
        mPaint.setColor(mPeriodInk);
        mPaint.setAlpha(255);
        mPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(mSnapshot.period, x, periodBaseline, mPaint);
        mPaint.setLetterSpacing(0f);
    }

    private void drawFullFlipSecond(Canvas canvas, int index, RectF cell, float split,
                                    float baseline, long now) {
        float p = secondsProgress(index, now, SECONDS_FLIP_DURATION_MS);
        if (p >= 1f) {
            drawFullFlipSecondHalf(canvas, cell, split, true, mSeconds[index], baseline);
            drawFullFlipSecondHalf(canvas, cell, split, false, mSeconds[index], baseline);
        } else if (p < .5f) {
            float local = p * 2f;
            drawFullFlipSecondHalf(canvas, cell, split, false, mOldSeconds[index], baseline);
            drawFullRotatedSecondHalf(canvas, cell, split, true, mOldSeconds[index], baseline,
                -90f * local * local);
        } else {
            float local = (p - .5f) * 2f;
            float eased = 1f - (1f - local) * (1f - local);
            drawFullFlipSecondHalf(canvas, cell, split, true, mSeconds[index], baseline);
            drawFullRotatedSecondHalf(canvas, cell, split, false, mSeconds[index], baseline,
                90f * (1f - eased));
        }
    }

    private void drawFullFlipSecondHalf(Canvas canvas, RectF cell, float split, boolean top,
                                        char digit, float baseline) {
        canvas.save();
        canvas.clipRect(cell.left, top ? cell.top : split, cell.right,
            top ? split : cell.bottom);
        mPaint.setTypeface(condensedBoldTypeface());
        mPaint.setLetterSpacing(0f);
        mPaint.setTextSize(sp(labelSp()));
        mPaint.setColor(mDateInk);
        mPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(CharAdvances.charString(digit), cell.centerX(), baseline, mPaint);
        canvas.restore();
    }

    private void drawFullRotatedSecondHalf(Canvas canvas, RectF cell, float split, boolean top,
                                           char digit, float baseline, float angle) {
        mCamera.save();
        mCamera.rotateX(angle);
        mCamera.getMatrix(mMatrix);
        mCamera.restore();
        mMatrix.preTranslate(-cell.centerX(), -split);
        mMatrix.postTranslate(cell.centerX(), split);
        canvas.save();
        canvas.concat(mMatrix);
        drawFullFlipSecondHalf(canvas, cell, split, top, digit, baseline);
        canvas.restore();
    }

    private void drawFullFlipDateRow(Canvas canvas, float top, float right) {
        Typeface face = condensedMediumTypeface();
        float rowHeight = mFitParts.columnFixed / mScale;
        float textWidth = spacedTextWidth(mSnapshot.date, face, mLabelSp, 0f) / mScale;
        float gap = dp(7.5f);
        // The rule fills whatever the date does not, so it stays a departure-board row at any
        // alignment: flanked when centered, one long run to the side the date has left free.
        float textX = dateRowTextX(right, textWidth, 7.5f);
        drawLabel(canvas, mSnapshot.date, textX, baseline(top, rowHeight, face, labelSp()),
            labelSp(), face, 0f, mDateInk);
        float ruleY = top + rowHeight / 2f;
        float half = bandStroke(RULE_FRACTION, dp(FULL_FLIP_CARD_HEIGHT_DP)) / 2f;
        mFillPaint.setShader(null);
        mFillPaint.setColor(mFlipRuleColor);
        if (textX - gap > 0f) canvas.drawRect(0f, ruleY - half, textX - gap,
            ruleY + half, mFillPaint);
        if (textX + textWidth + gap < right) canvas.drawRect(textX + textWidth + gap,
            ruleY - half, right, ruleY + half, mFillPaint);
    }

    private void drawCompactFlip(Canvas canvas, long now) {
        float digitBaseline = capCenteredBaseline(dp(24f) / 2f, condensedBoldTypeface(), 19f);
        float x = drawCompactFlipCards(canvas, now, digitBaseline);
        drawStackedMetaColumn(canvas, x + dp(4f), dp(21f), Typeface.DEFAULT,
            mediumTypeface(), false, now, FLIP_DURATION_MS);
    }

    /** Same departure-board face as {@link #drawFullFlipCards}, at compact scale. */
    private float drawCompactFlipCards(Canvas canvas, long now, float digitBaseline) {
        float x = 0f;
        for (int digit = 0; digit < 4; digit++) {
            x = drawCompactFlipDigit(canvas, digit, x, digitBaseline, now);
            x += dp(flipGapAfterDp(digit, COMPACT_FLIP_DIGIT_GAP_DP));
        }
        return x;
    }

    private float drawCompactFlipDigit(Canvas canvas, int digit, float x, float digitBaseline,
                                       long now) {
        float w = dp(COMPACT_FLIP_CARD_DP), h = dp(24f);
        RectF card = mRect;
        card.set(x, 0f, x + w, h);
        float p = progress(digit, now, FLIP_DURATION_MS);
        boolean animating = p < 1f;

        mFillPaint.setShader(null);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(mFlipBase);
        mFillPaint.setShadowLayer(dp(2.2f), 0f, dp(.7f), mFlipShadow);
        canvas.drawRoundRect(card, dp(1f), dp(1f), mFillPaint);
        mFillPaint.clearShadowLayer();

        if (!animating) {
            drawCompactFlipHalf(canvas, card, true, mDigits[digit], true, digitBaseline, 0f);
            drawCompactFlipHalf(canvas, card, false, mDigits[digit], true, digitBaseline, 0f);
        } else if (p < .5f) {
            float local = p * 2f;
            drawCompactFlipHalf(canvas, card, true, mDigits[digit], false, digitBaseline, 0f);
            drawCompactFlipHalf(canvas, card, false, mOldDigits[digit], true, digitBaseline, 0f);
            drawCompactRotatedFlipHalf(canvas, card, true, mOldDigits[digit], digitBaseline,
                -90f * local * local, local);
        } else {
            float local = (p - .5f) * 2f;
            float eased = 1f - (1f - local) * (1f - local);
            drawCompactFlipHalf(canvas, card, true, mDigits[digit], true, digitBaseline, 0f);
            drawCompactFlipHalf(canvas, card, false, mDigits[digit], false, digitBaseline, 0f);
            drawCompactRotatedFlipHalf(canvas, card, false, mDigits[digit], digitBaseline,
                90f * (1f - eased), 1f - eased);
        }

        drawFlipRimAndSeam(canvas, card, dp(1f), dp(24f), false);
        return card.right;
    }

    private void drawCompactFlipHalf(Canvas canvas, RectF card, boolean top, char digit,
                                     boolean drawDigit, float digitBaseline, float foldShade) {
        float split = card.centerY();
        if (top) {
            mFlipHalfClip.set(card.left, card.top, card.right, split);
        } else {
            mFlipHalfClip.set(card.left, split, card.right, card.bottom);
        }
        canvas.save();
        canvas.clipRect(mFlipHalfClip);
        mFillPaint.setShader(top
            ? upperFlipShader(card.top, split)
            : lowerFlipShader(split, card.bottom));
        canvas.drawRoundRect(card, dp(1f), dp(1f), mFillPaint);
        mFillPaint.setShader(null);
        if (drawDigit) {
            mPaint.setTypeface(condensedBoldTypeface());
            mPaint.setLetterSpacing(0f);
            mPaint.setTextSize(sp(19f));
            mPaint.setColor(mFlipDigitInk);
            mPaint.setAlpha(255);
            mPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(CharAdvances.charString(digit), card.centerX(), digitBaseline, mPaint);
        }
        if (foldShade > 0f) {
            mFillPaint.setColor(ColorUtils.setAlphaComponent(GlassTokens.SHADE,
                Math.round(255f * .22f * clamp01(foldShade))));
            canvas.drawRoundRect(card, dp(1f), dp(1f), mFillPaint);
        }
        canvas.restore();
    }

    private void drawCompactRotatedFlipHalf(Canvas canvas, RectF card, boolean top, char digit,
                                            float digitBaseline, float angle, float foldShade) {
        float pivotY = card.centerY();
        mCamera.save();
        mCamera.rotateX(angle);
        mCamera.getMatrix(mMatrix);
        mCamera.restore();
        mMatrix.preTranslate(-card.centerX(), -pivotY);
        mMatrix.postTranslate(card.centerX(), pivotY);
        canvas.save();
        canvas.concat(mMatrix);
        drawCompactFlipHalf(canvas, card, top, digit, true, digitBaseline, foldShade);
        canvas.restore();
    }

    // ---- LCD --------------------------------------------------------------

    private void drawFullLcd(Canvas canvas, long now, float dateTop, float right, float bandDx) {
        canvas.save();
        canvas.translate(bandDx, 0f);
        float thickness = bandStroke(LCD_SEGMENT_FRACTION, dp(34f));
        float x = drawLcdDigits(canvas, now, 19f, 34f, thickness, 3f, 6f, 17f, 4f);
        drawStackedMetaColumn(canvas, x + dp(6f), dp(30f), Typeface.MONOSPACE,
            Typeface.MONOSPACE, true, now, LCD_DURATION_MS);
        canvas.restore();
        if (mDateRow) drawDateRow(canvas, dateTop, right, mSnapshot.date, Typeface.MONOSPACE);
    }

    private void drawCompactLcd(Canvas canvas, long now) {
        float thickness = bandStroke(LCD_SEGMENT_FRACTION, dp(22f));
        float x = drawLcdDigits(canvas, now, 12f, 22f, thickness, 2f, 5f, 11f, 2.75f);
        drawStackedMetaColumn(canvas, x + dp(5f), dp(20f), Typeface.MONOSPACE,
            Typeface.MONOSPACE, false, now, LCD_DURATION_MS);
    }

    private float drawLcdDigits(Canvas canvas, long now, float widthDp, float heightDp,
                                float thickness, float gapDp, float colonWidthDp,
                                float colonCenterDp, float colonOffsetDp) {
        float x = 0f;
        x = drawSevenDigit(canvas, 0, x, widthDp, heightDp, thickness, now) + dp(gapDp);
        x = drawSevenDigit(canvas, 1, x, widthDp, heightDp, thickness, now) + dp(gapDp);
        drawLcdColon(canvas, x + dp(colonWidthDp) / 2f, dp(colonCenterDp),
            thickness * LCD_COLON_RATIO, dp(colonOffsetDp));
        x += dp(colonWidthDp) + dp(gapDp);
        x = drawSevenDigit(canvas, 2, x, widthDp, heightDp, thickness, now) + dp(gapDp);
        return drawSevenDigit(canvas, 3, x, widthDp, heightDp, thickness, now);
    }

    private float drawSevenDigit(Canvas canvas, int index, float x, float widthDp,
                                 float heightDp, float thickness, long now) {
        float alpha = lcdAlpha(progress(index, now, LCD_DURATION_MS));
        mLcdCell.set(x, 0f, x + dp(widthDp), dp(heightDp));
        drawSevenSegments(canvas, mDigits[index], mLcdCell, thickness, alpha);
        return x + dp(widthDp);
    }

    /** The LCD's scratch: one digit's cell, its seven segments, and an on segment's glow. */
    private final RectF mLcdCell = new RectF();
    private final RectF[] mLcdSegments = {
        new RectF(), new RectF(), new RectF(), new RectF(), new RectF(), new RectF(), new RectF()
    };
    private final RectF mLcdGlow = new RectF();

    private void drawLcdColon(Canvas canvas, float x, float y, float radius, float offset) {
        mFillPaint.setShader(null);
        mFillPaint.setColor(mAccentInk);
        canvas.drawCircle(x, y - offset, radius, mFillPaint);
        canvas.drawCircle(x, y + offset, radius, mFillPaint);
    }

    /**
     * Seven segments in the accent ink, toned to hold 3:1 on the measured backdrop: pale on dark
     * glass, dark on pale glass. The unlit ones are a faint body behind them, not a second time.
     */
    private void drawSevenSegments(Canvas canvas, char digit, RectF r, float thickness,
                                   float alpha) {
        int on = sevenSegments(digit);
        float half = thickness / 2f;
        RectF[] segments = mLcdSegments;
        segments[0].set(r.left + thickness, r.top, r.right - thickness, r.top + thickness);
        segments[1].set(r.right - thickness, r.top + thickness, r.right, r.centerY() - half);
        segments[2].set(r.right - thickness, r.centerY() + half, r.right, r.bottom - thickness);
        segments[3].set(r.left + thickness, r.bottom - thickness, r.right - thickness, r.bottom);
        segments[4].set(r.left, r.centerY() + half, r.left + thickness, r.bottom - thickness);
        segments[5].set(r.left, r.top + thickness, r.left + thickness, r.centerY() - half);
        segments[6].set(r.left + thickness, r.centerY() - half, r.right - thickness,
            r.centerY() + half);
        int color = mAccentInk;
        float glowInset = thickness * LCD_GLOW_RATIO;
        mFillPaint.setShader(null);
        for (int i = 0; i < segments.length; i++) {
            if ((on & (1 << i)) == 0) {
                mFillPaint.setColor(mLcdGhost);
                canvas.drawRoundRect(segments[i], half, half, mFillPaint);
                continue;
            }
            RectF glow = mLcdGlow;
            glow.set(segments[i]);
            glow.inset(-glowInset, -glowInset);
            mFillPaint.setColor(withAlpha(color, Math.round(80f * alpha)));
            canvas.drawRoundRect(glow, thickness, thickness, mFillPaint);
            mFillPaint.setColor(withAlpha(color, Math.round(255f * alpha)));
            canvas.drawRoundRect(segments[i], half, half, mFillPaint);
        }
    }

    /** The segments lit for {@code c}: bit {@code i} is segment {@code i}, a to g. */
    private static int sevenSegments(char c) {
        int bits;
        switch (c) {
            case '0': bits = 0x3F; break;
            case '1': bits = 0x06; break;
            case '2': bits = 0x5B; break;
            case '3': bits = 0x4F; break;
            case '4': bits = 0x66; break;
            case '5': bits = 0x6D; break;
            case '6': bits = 0x7D; break;
            case '7': bits = 0x07; break;
            case '8': bits = 0x7F; break;
            case '9': bits = 0x6F; break;
            default: bits = 0;
        }
        return bits;
    }

    private static float lcdAlpha(float progress) {
        if (progress >= 1f) return 1f;
        return .18f + .82f * Math.min(1f, progress / .55f);
    }

    // ---- Minimal ----------------------------------------------------------

    private void drawFullMinimal(Canvas canvas, long now, float dateTop, float right, float bandDx) {
        Typeface face = minimalTypeface();
        float baseline = capCenteredBaseline(dp(CAP_BAND_DP) / 2f, face, 38f);
        canvas.save();
        canvas.translate(bandDx, 0f);
        float x = drawFadingTime(canvas, now, face, 38f, -.02f, baseline);
        drawStackedMetaColumn(canvas, x + dp(6f), baseline, Typeface.DEFAULT,
            mediumTypeface(), false, now, TEXT_DURATION_MS);
        canvas.restore();
        if (mDateRow) drawDateRow(canvas, dateTop, right, mSnapshot.date, Typeface.DEFAULT);
    }

    private void drawCompactMinimal(Canvas canvas, long now) {
        Typeface face = minimalTypeface();
        float baseline = baseline(0f, dp(22f), face, 26f);
        float x = drawFadingTime(canvas, now, face, 26f, -.02f, baseline);
        drawStackedMetaColumn(canvas, x + dp(5f), baseline, Typeface.DEFAULT,
            mediumTypeface(), false, now, TEXT_DURATION_MS);
    }

    /** Minimal's digits: today's thin, or the heavier medium for a busy wallpaper. */
    @NonNull
    private Typeface minimalTypeface() {
        return mMinimalMedium ? mediumTypeface() : thinTypeface();
    }

    // ---- Slab -------------------------------------------------------------

    private void drawFullSlab(Canvas canvas, long now, float dateTop, float right, float bandDx) {
        float baseline = capCenteredBaseline(dp(CAP_BAND_DP) / 2f, Typeface.DEFAULT_BOLD, 39f);
        canvas.save();
        canvas.translate(bandDx, 0f);
        float x = drawFadingTime(canvas, now, Typeface.DEFAULT_BOLD, 39f, -.045f, baseline);
        drawStackedMetaColumn(canvas, x + dp(6f), baseline, mediumTypeface(),
            Typeface.DEFAULT_BOLD, false, now, TEXT_DURATION_MS);
        canvas.restore();
        if (mDateRow) drawDateRow(canvas, dateTop, right, mSnapshot.date, mediumTypeface());
    }

    private void drawCompactSlab(Canvas canvas, long now) {
        float baseline = baseline(0f, dp(22f), Typeface.DEFAULT_BOLD, 27f);
        float x = drawFadingTime(canvas, now, Typeface.DEFAULT_BOLD, 27f, -.045f, baseline);
        drawStackedMetaColumn(canvas, x + dp(5f), baseline, mediumTypeface(),
            Typeface.DEFAULT_BOLD, false, now, TEXT_DURATION_MS);
    }

    // ---- Tape -------------------------------------------------------------

    /**
     * The time, the date directly under it, then the minute track running out to the box's edge.
     * The date used to be pinned to the far right of the time row, a whole bar's look away.
     */
    private void drawFullTape(Canvas canvas, long now, float dateTop, float right, float bandDx) {
        float baseline = baseline(0f, dp(22f), Typeface.MONOSPACE, 28f);
        canvas.save();
        canvas.translate(bandDx, 0f);
        float x = drawFadingTime(canvas, now, Typeface.MONOSPACE, 28f, -.02f, baseline);
        drawStackedMetaColumn(canvas, x + dp(5f), baseline, Typeface.MONOSPACE,
            Typeface.MONOSPACE, false, now, TEXT_DURATION_MS);
        canvas.restore();
        if (!mDateRow) {
            // No date: the minute track sits under the time, as wide as the time row.
            canvas.save();
            canvas.translate(bandDx, 0f);
            drawMinuteTrack(canvas, dp(22f) + dp(TAPE_TRACK_GAP_DP),
                mFitParts.timeWidth(mScale) / mScale, bandStroke(TAPE_TRACK_FRACTION, dp(22f)));
            canvas.restore();
            return;
        }
        float rowHeight = mFitParts.columnFixed / mScale;
        drawLabel(canvas, mSnapshot.date, bandDx,
            baseline(dateTop, rowHeight, Typeface.MONOSPACE, labelSp()), labelSp(),
            Typeface.MONOSPACE, 0f, mDateInk);
        drawMinuteTrack(canvas, dateTop + rowHeight + dp(TAPE_TRACK_GAP_DP), right,
            bandStroke(TAPE_TRACK_FRACTION, dp(22f)));
    }

    private void drawCompactTape(Canvas canvas, long now) {
        float baseline = baseline(0f, dp(15f), Typeface.MONOSPACE, 18f);
        float x = drawFadingTime(canvas, now, Typeface.MONOSPACE, 18f, -.02f, baseline);
        drawStackedMetaColumn(canvas, x + dp(4f), baseline, Typeface.MONOSPACE,
            Typeface.MONOSPACE, false, now, TEXT_DURATION_MS);
        drawMinuteTrack(canvas, dp(20f), dp(100f), bandStroke(TAPE_TRACK_FRACTION, dp(15f)));
    }

    /** The minute as a fill: tape's stand-in for a seconds readout. */
    private void drawMinuteTrack(Canvas canvas, float top, float right, float height) {
        mFillPaint.setShader(null);
        mRect.set(0f, top, right, top + height);
        mFillPaint.setColor(mTrackColor);
        canvas.drawRoundRect(mRect, height / 2f, height / 2f, mFillPaint);
        float fill = right * minuteFraction();
        if (fill <= 0f) return;
        mRect.set(0f, top, fill, top + height);
        mFillPaint.setColor(mAccentInk);
        canvas.drawRoundRect(mRect, height / 2f, height / 2f, mFillPaint);
    }

    // ---- LED matrix -------------------------------------------------------

    private void drawFullLed(Canvas canvas, long now, float dateTop, float right, float bandDx) {
        float cell = bandStroke(LED_CELL_FRACTION, dp(26f));
        canvas.save();
        canvas.translate(bandDx, 0f);
        float x = drawLedTime(canvas, now, cell);
        drawStackedMetaColumn(canvas, x + dp(7f), cell * 7f, Typeface.MONOSPACE,
            Typeface.MONOSPACE, false, now, LED_DURATION_MS);
        canvas.restore();
        if (mDateRow) drawDateRow(canvas, dateTop, right, mSnapshot.date, Typeface.MONOSPACE);
    }

    private void drawCompactLed(Canvas canvas, long now) {
        float cell = bandStroke(LED_CELL_FRACTION, dp(16f));
        float x = drawLedTime(canvas, now, cell);
        drawStackedMetaColumn(canvas, x + dp(5f), cell * 7f, Typeface.MONOSPACE,
            Typeface.MONOSPACE, false, now, LED_DURATION_MS);
    }

    private float drawLedTime(Canvas canvas, long now, float cell) {
        String hm = timeText();
        float x = 0f;
        int digitIndex = 0;
        for (int i = 0; i < hm.length(); i++) {
            char c = hm.charAt(i);
            if (c == ':') {
                drawDotGlyph(canvas, c, x, 0f, cell, mDigitInk, 1f, 1f);
            } else {
                float eased = ease(progress(digitIndex++, now, LED_DURATION_MS));
                drawDotGlyph(canvas, c, x, 0f, cell, mDigitInk,
                    .84f + .16f * eased, 1f + 1.3f * (1f - eased));
            }
            x += dotGlyphAdvance(cell);
        }
        return x - cell;
    }

    private void drawDotGlyph(Canvas canvas, char c, float x, float y, float cell, int color,
                              float scale, float brightness) {
        String[] rows = dotPattern(c);
        float width = cell * 5f, height = cell * 7f;
        canvas.save();
        canvas.scale(scale, scale, x + width / 2f, y + height / 2f);
        mFillPaint.setShader(null);
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length(); col++) {
                if (rows[row].charAt(col) != '1') continue;
                float cx = x + col * cell + cell / 2f;
                float cy = y + row * cell + cell / 2f;
                mFillPaint.setColor(withAlpha(color, Math.min(150, Math.round(65f * brightness))));
                canvas.drawCircle(cx, cy, cell * .62f, mFillPaint);
                mFillPaint.setColor(brighten(color, brightness));
                canvas.drawCircle(cx, cy, cell * .34f, mFillPaint);
            }
        }
        canvas.restore();
    }

    private static String[] dotPattern(char c) {
        switch (c) {
            case '0': return DOT_0;
            case '1': return DOT_1;
            case '2': return DOT_2;
            case '3': return DOT_3;
            case '4': return DOT_4;
            case '5': return DOT_5;
            case '6': return DOT_6;
            case '7': return DOT_7;
            case '8': return DOT_8;
            case '9': return DOT_9;
            case ':': return DOT_COLON;
            default: return DOT_BLANK;
        }
    }

    // Built once: the time draws through these on every frame of a digit's rise.
    private static final String[] DOT_0 = {"01110","10001","10011","10101","11001","10001","01110"};
    private static final String[] DOT_1 = {"00100","01100","00100","00100","00100","00100","01110"};
    private static final String[] DOT_2 = {"01110","10001","00001","00010","00100","01000","11111"};
    private static final String[] DOT_3 = {"11110","00001","00001","01110","00001","00001","11110"};
    private static final String[] DOT_4 = {"00010","00110","01010","10010","11111","00010","00010"};
    private static final String[] DOT_5 = {"11111","10000","10000","11110","00001","00001","11110"};
    private static final String[] DOT_6 = {"01110","10000","10000","11110","10001","10001","01110"};
    private static final String[] DOT_7 = {"11111","00001","00010","00100","01000","01000","01000"};
    private static final String[] DOT_8 = {"01110","10001","10001","01110","10001","10001","01110"};
    private static final String[] DOT_9 = {"01110","10001","10001","01111","00001","00001","01110"};
    private static final String[] DOT_COLON =
        {"00000","00100","00100","00000","00100","00100","00000"};
    private static final String[] DOT_BLANK =
        {"00000","00000","00000","00000","00000","00000","00000"};

    private static float dotGlyphAdvance(float cell) {
        return cell * 6f;
    }

    private static float dotTextWidth(String text, float cell) {
        return text.isEmpty() ? 0f : dotGlyphAdvance(cell) * text.length() - cell;
    }

    // ---- Mono chip --------------------------------------------------------

    /** One row: the time, then its seconds, period and date at the Label role's forced 11sp. */
    private void drawMonoChip(Canvas canvas, int heightPx) {
        mScale = 1f;
        mLabelSp = LABEL_MIN_SP;
        mLabelGrowth = 1f;
        float label = LABEL_MIN_SP;
        float rowHeight = dp(17f);
        float top = Math.max(0f, (heightPx - rowHeight) / 2f);
        float x = 0f;
        drawLabel(canvas, timeText(), x, baseline(top, rowHeight, Typeface.MONOSPACE, 14f), 14f,
            Typeface.MONOSPACE, .02f, mDigitInk);
        x += spacedTextWidth(timeText(), Typeface.MONOSPACE, 14f, .02f) + dp(5f);
        drawLabel(canvas, mSnapshot.ss, x, baseline(top, rowHeight, Typeface.MONOSPACE, label),
            label, Typeface.MONOSPACE, 0f, mSecondsInk);
        x += spacedTextWidth(mSnapshot.ss, Typeface.MONOSPACE, label, 0f) + dp(5f);
        if (!mSnapshot.period.isEmpty()) {
            float periodSp = label * PERIOD_SCALE;
            drawLabel(canvas, mSnapshot.period, x,
                baseline(top, rowHeight, Typeface.MONOSPACE, periodSp), periodSp,
                Typeface.MONOSPACE, .16f, mPeriodInk);
            x += spacedTextWidth(mSnapshot.period, Typeface.MONOSPACE, periodSp, .16f) + dp(5f);
        }
        mFillPaint.setShader(null);
        mFillPaint.setColor(mRuleColor);
        canvas.drawCircle(x + dp(1f), top + rowHeight / 2f, dp(1f), mFillPaint);
        x += dp(2f) + dp(5f);
        drawLabel(canvas, mSnapshot.date, x, baseline(top, rowHeight, Typeface.MONOSPACE, label),
            label, Typeface.MONOSPACE, 0f, mDateInk);
    }

    // ---- Shared refined grid ---------------------------------------------

    /**
     * Meta as a column: seconds on the time baseline, period stacked directly above them, and the
     * two exactly as wide as each other. The seconds are the Label role and set the width; the
     * period is drawn at {@link #PERIOD_SCALE} of their size and tracked out to the same width
     * (or shrunk to fit, if a face's seconds run narrower than the label). Everything in the
     * column is label space: it keeps its painted size whatever the face's scale.
     *
     * <p>Folding the period in beside the seconds pushed the meta run out past the width the widget
     * slot hands the clock — and the period, being last, was the part the media strip clipped.
     * Stacked, the column is only as wide as the seconds, and the period is inside the block
     * instead of hanging off its end. The LCD keeps its boxed period: the box spans the seconds'
     * width with the label centred in it.
     */
    private void drawStackedMetaColumn(Canvas canvas, float x, float baseline,
                                       Typeface secondsFace, Typeface periodFace,
                                       boolean boxedPeriod, long now, long duration) {
        float secondsSp = labelSp();
        mPaint.setTypeface(secondsFace);
        mPaint.setLetterSpacing(0f);
        mPaint.setTextSize(sp(secondsSp));
        mPaint.setTextAlign(Paint.Align.LEFT);
        float secondsAscent = mPaint.ascent();
        float cursor = x;
        for (int i = 0; i < 2 && mShowSeconds; i++) {
            float eased = ease(secondsProgress(i, now, duration));
            mPaint.setColor(mSecondsInk);
            mPaint.setAlpha(Math.round(Color.alpha(mSecondsInk) * eased));
            char c = mSnapshot.ss.charAt(i);
            canvas.drawText(CharAdvances.charString(c), cursor, baseline + lp(4f) * (1f - eased),
                mPaint);
            cursor += mSecondsAdvances.advance(c, secondsFace, mPaint.getTextSize(), 0f, mMeasure);
        }
        mPaint.setAlpha(255);
        if (!mShowPeriod || mSnapshot.period.isEmpty()) return;
        // The column is as wide as the seconds would be even when they are shed.
        // Never wider than the column the fit measured, so a digit pair's rounding cannot push the
        // period past the box.
        float width = stackedMetaWidth(secondsFace) * mLabelGrowth / mScale;
        String period = mSnapshot.period;
        float boxPad = boxedPeriod ? lp(BOX_PAD_DP) : 0f;
        float periodDp = secondsSp * PERIOD_SCALE;
        float natural = spacedTextWidth(period, periodFace, periodDp, 0f);
        float room = width - 2f * boxPad;
        int gaps = Math.max(1, period.length() - 1);
        if (natural > room) {
            periodDp *= room / natural;
            natural = room;
        } else if (!boxedPeriod && room - natural > gaps * sp(periodDp) * MAX_PERIOD_TRACKING) {
            // A narrow label (the monospace faces) would need more tracking than reads as one word;
            // grow it until the cap's worth of tracking fills the width instead. Text width scales
            // with size, so the target size follows from the width-per-pixel-of-size at the current
            // one (both sides in px; the result is converted back to dp).
            float widthPerPx = natural / sp(periodDp);
            periodDp = room / (widthPerPx + gaps * MAX_PERIOD_TRACKING) / sp(1f);
            natural = spacedTextWidth(period, periodFace, periodDp, 0f);
        }
        // Clear of the seconds' own ascent (and the box's stroke), so the rows never touch.
        float gap = lp(2.5f) + (boxedPeriod ? lp(1.5f) : 0f);
        float periodBaseline = periodBaselineAbove(
            mShowSeconds ? baseline + secondsAscent - gap : baseline, periodFace,
            periodDp, boxedPeriod);
        if (boxedPeriod) {
            mPaint.setTypeface(periodFace);
            mPaint.setTextSize(sp(periodDp));
            // The stroke is centred on the rect, so the rect is inset by half of it: the box's
            // outer edge is the column's edge. Edge to edge, half the stroke hung past the width
            // the fit measured, and once a widget's Label role grew with the face that half grew
            // too: the LCD at 2x1 painted 2 px past its box.
            float stroke = lp(STROKE_FLOOR_DP);
            mRect.set(x + stroke / 2f, periodBaseline + mPaint.ascent() - lp(1.5f),
                x + width - stroke / 2f, periodBaseline + mPaint.descent() + lp(1.5f));
            mFillPaint.setShader(null);
            mFillPaint.setColor(mPrimaryLine);
            mFillPaint.setStyle(Paint.Style.STROKE);
            mFillPaint.setStrokeWidth(stroke);
            canvas.drawRoundRect(mRect, lp(2.5f), lp(2.5f), mFillPaint);
            mFillPaint.setStyle(Paint.Style.FILL);
            drawLabel(canvas, period, x + (width - natural) / 2f, periodBaseline, periodDp,
                periodFace, 0f, mPeriodInk);
            return;
        }
        // Letter spacing is applied half before and half after every glyph, so the tracking that
        // makes the glyph run exactly `width` wide is spread over the gaps between glyphs, and the
        // run starts half a gap before x.
        float spacing = (width - natural) / (gaps * sp(periodDp));
        drawLabel(canvas, period, x - spacing * sp(periodDp) / 2f, periodBaseline, periodDp,
            periodFace, spacing, mPeriodInk);
    }

    /**
     * Baseline for a period label that wants to sit at {@code wanted} but must keep its cap height
     * (and box, when it has one) inside the band: the compact faces leave the label less room than
     * its own ascent.
     */
    private float periodBaselineAbove(float wanted, Typeface periodFace, float periodDp,
                                      boolean boxed) {
        float floor = capHeight(periodFace, periodDp) + lp(.5f) + (boxed ? lp(1.5f) : 0f);
        return Math.max(wanted, floor);
    }

    /** Time digits rising into place; shared by every text-drawn face. */
    private float drawFadingTime(Canvas canvas, long now, Typeface typeface, float textDp,
                                 float letterSpacing, float baseline) {
        String hm = timeText();
        mPaint.setTypeface(typeface);
        mPaint.setLetterSpacing(letterSpacing);
        mPaint.setTextSize(sp(textDp));
        mPaint.setTextAlign(Paint.Align.LEFT);
        float x = 0f;
        int digitIndex = 0;
        for (int i = 0; i < hm.length(); i++) {
            char c = hm.charAt(i);
            float eased = c == ':' ? 1f : ease(progress(digitIndex++, now, TEXT_DURATION_MS));
            mPaint.setColor(mDigitInk);
            mPaint.setAlpha(Math.round(255f * eased));
            canvas.drawText(CharAdvances.charString(c), x, baseline + dp(5f) * (1f - eased),
                mPaint);
            x += mTimeAdvances.advance(c, typeface, mPaint.getTextSize(), letterSpacing, mMeasure);
        }
        mPaint.setAlpha(255);
        mPaint.setLetterSpacing(0f);
        return x;
    }

    /**
     * Date text at the Label role plus the hairline(s) carrying it out to the pane gutter:
     * trailing when the text leads, leading when it trails, both when centred. {@link #drawRule}
     * drops any side whose run would be non-positive, so left keeps only the trailing rule and
     * right only the leading one.
     */
    private void drawDateRow(Canvas canvas, float top, float right, String text,
                             Typeface typeface) {
        float rowHeight = mFitParts.columnFixed / mScale;
        float textWidth = spacedTextWidth(text, typeface, mLabelSp, 0f) / mScale;
        float textX = dateRowTextX(right, textWidth, RULE_GAP_DP);
        drawLabel(canvas, text, textX, baseline(top, rowHeight, typeface, labelSp()), labelSp(),
            typeface, 0f, mDateInk);
        float ruleY = top + rowHeight / 2f;
        float thickness = bandStroke(RULE_FRACTION, dp(fullBandHeightDp()));
        drawRule(canvas, 0f, ruleY, textX - dp(RULE_GAP_DP), thickness, mRuleColor);
        drawRule(canvas, textX + textWidth + dp(RULE_GAP_DP), ruleY, right, thickness,
            mRuleColor);
    }

    private void drawLabel(Canvas canvas, String text, float x, float baseline, float textDp,
                           Typeface typeface, float letterSpacing, int color) {
        if (text.isEmpty()) return;
        mPaint.setTypeface(typeface);
        mPaint.setLetterSpacing(letterSpacing);
        mPaint.setTextSize(sp(textDp));
        mPaint.setColor(color);
        mPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(text, x, baseline, mPaint);
        mPaint.setLetterSpacing(0f);
    }

    private void drawRule(Canvas canvas, float x, float centerY, float right, float thickness,
                          int color) {
        if (right - x <= 0f) return;
        mFillPaint.setShader(null);
        mFillPaint.setColor(color);
        canvas.drawRect(x, centerY - thickness / 2f, right, centerY + thickness / 2f, mFillPaint);
    }

    /** Baseline that vertically centres {@code textDp} inside a band of {@code height}. */
    private float baseline(float top, float height, Typeface typeface, float textDp) {
        mPaint.setTypeface(typeface);
        mPaint.setLetterSpacing(0f);
        mPaint.setTextSize(sp(textDp));
        return top + height / 2f - (mPaint.ascent() + mPaint.descent()) / 2f;
    }

    private float spacedTextWidth(String text, Typeface typeface, float textDp, float letterSpacing) {
        if (text.isEmpty()) return 0f;
        mPaint.setTypeface(typeface);
        mPaint.setLetterSpacing(letterSpacing);
        mPaint.setTextSize(sp(textDp));
        float width = mTextWidths.width(text, typeface, mPaint.getTextSize(), letterSpacing,
            mMeasure);
        mPaint.setLetterSpacing(0f);
        return width;
    }

    /**
     * Width of {@code text} as {@link #drawFadingTime} paints it: a glyph at a time, so no pair is
     * kerned. Measuring the whole run kerned it ("11" sits tighter in Roboto), and once the face
     * was scaled up to fill a widget the gap the model missed pushed the period off the box's
     * edge, clipped to "P". The same held for letter spacing a platform leaves off a run's ends:
     * the Wall strip's clock at 128 dp with two terminals lost its period and seconds to its
     * room's clip on the 2026-10-21 emulator check.
     */
    private float glyphRunWidth(String text, Typeface typeface, float textDp, float letterSpacing) {
        mPaint.setTypeface(typeface);
        mPaint.setLetterSpacing(letterSpacing);
        mPaint.setTextSize(sp(textDp));
        float width = glyphRunWidth(text, typeface, mPaint.getTextSize(), letterSpacing,
            mTimeAdvances, mMeasure);
        mPaint.setLetterSpacing(0f);
        return width;
    }

    /** {@code text}'s width as glyphs laid one after another at their own advances. */
    @VisibleForTesting
    static float glyphRunWidth(@NonNull String text, @Nullable Object face, float size,
                               float spacing, @NonNull CharAdvances advances,
                               @NonNull TextWidths.Measurer measurer) {
        float width = 0f;
        for (int i = 0; i < text.length(); i++) {
            width += advances.advance(text.charAt(i), face, size, spacing, measurer);
        }
        return width;
    }

    private String timeText() {
        ClockSnapshot snapshot = mSnapshot;
        String text = mTimeText;
        if (text == null || mTimeTextSnapshot != snapshot) {
            text = snapshot.hh + ":" + snapshot.mm;
            mTimeText = text;
            mTimeTextSnapshot = snapshot;
        }
        return text;
    }

    private float minuteFraction() {
        // Without seconds the face repaints once a minute: the track shows the hour instead.
        if (!mShowSeconds) return clamp01(minutesValue() / 60f);
        return clamp01(secondsValue() / 60f);
    }

    private int minutesValue() {
        try {
            return Integer.parseInt(mSnapshot.mm);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int secondsValue() {
        try {
            return Integer.parseInt(mSnapshot.ss);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Typeface thinTypeface() {
        return cachedTypeface("sans-serif-thin", Typeface.NORMAL);
    }

    private static Typeface mediumTypeface() {
        return cachedTypeface("sans-serif-medium", Typeface.NORMAL);
    }

    /**
     * {@link Typeface#create(String, int)} is not a lookup — it consults the system font list and
     * builds a face — and the flip face asks for one on every card, on every frame. Memoize the
     * handful of families this widget ever names.
     */
    private static final java.util.Map<String, Typeface> TYPEFACES =
        new java.util.concurrent.ConcurrentHashMap<>(8);

    @NonNull
    private static Typeface cachedTypeface(@NonNull String family, int style) {
        String key = family + '/' + style;
        Typeface cached = TYPEFACES.get(key);
        if (cached == null) {
            cached = Typeface.create(family, style);
            TYPEFACES.put(key, cached);
        }
        return cached;
    }

    private static Typeface condensedBoldTypeface() {
        return cachedTypeface("sans-serif-condensed", Typeface.BOLD);
    }

    /** Built once: the condensed-medium alias, the date row's face, is asked for every frame. */
    @Nullable private static volatile Typeface sCondensedMedium;

    private static Typeface condensedMediumTypeface() {
        Typeface face = sCondensedMedium;
        if (face == null) {
            // The condensed-medium family alias was added in API 27; avoid the regular fallback on 26.
            face = Build.VERSION.SDK_INT >= 27
                ? Typeface.create("sans-serif-condensed-medium", Typeface.NORMAL)
                : Typeface.create("sans-serif-condensed", Typeface.NORMAL);
            sCondensedMedium = face;
        }
        return face;
    }

    private float capHeight(Typeface typeface, float textDp) {
        mPaint.setTypeface(typeface);
        mPaint.setLetterSpacing(0f);
        mPaint.setTextSize(sp(textDp));
        mPaint.getTextBounds("H", 0, 1, mTextBounds);
        return Math.min(-mPaint.ascent(), mTextBounds.height());
    }

    private float capCenteredBaseline(float centerY, Typeface typeface, float textDp) {
        return centerY + capHeight(typeface, textDp) / 2f;
    }

    /**
     * Takes the time at {@code wallTime}, stamping each digit that changed with
     * {@code animationTime} (the clock {@link #draw} is later handed) so it animates in.
     */
    public void updateTime(long wallTime, long animationTime) {
        ClockSnapshot next = snapshot(wallTime, TimeZone.getDefault(), mUseAmPm);
        char[] nextDigits = next.digits();
        char[] nextSeconds = next.secondsDigits();
        if (mSnapshot == null) {
            System.arraycopy(nextDigits, 0, mDigits, 0, mDigits.length);
            System.arraycopy(nextDigits, 0, mOldDigits, 0, mOldDigits.length);
            System.arraycopy(nextSeconds, 0, mSeconds, 0, mSeconds.length);
            System.arraycopy(nextSeconds, 0, mOldSeconds, 0, mOldSeconds.length);
        } else {
            for (int i = 0; i < mDigits.length; i++) {
                if (mDigits[i] != nextDigits[i]) {
                    mOldDigits[i] = mDigits[i];
                    mDigits[i] = nextDigits[i];
                    mChangedAt[i] = animationTime;
                }
            }
            for (int i = 0; i < mSeconds.length; i++) {
                if (mSeconds[i] != nextSeconds[i]) {
                    mOldSeconds[i] = mSeconds[i];
                    mSeconds[i] = nextSeconds[i];
                    mSecondsChangedAt[i] = animationTime;
                }
            }
        }
        mSnapshot = next;
    }

    /** What the face shows, for a host's content description; null before the first snapshot. */
    @Nullable
    public String contentDescription() {
        if (mSnapshot == null) return null;
        return mSnapshot.hh + ":" + mSnapshot.mm + ":" + mSnapshot.ss
            + (mSnapshot.period.isEmpty() ? "" : " " + mSnapshot.period)
            + ", " + mSnapshot.date + ", " + styleName() + " clock";
    }

    private String styleName() {
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD: return "LCD";
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL:
                return mMinimalMedium ? "Minimal medium" : "Minimal";
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED: return "LED matrix";
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE: return "Tape";
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB: return "Slab";
            default: return "Flip";
        }
    }

    @NonNull
    private LinearGradient upperFlipShader(float top, float bottom) {
        if (mUpperFlipShader == null || mUpperFlipShaderGeneration != mFlipShaderGeneration
            || mUpperFlipTop != top || mUpperFlipBottom != bottom) {
            mUpperFlipShader = new LinearGradient(0f, top, 0f, bottom, mUpperFlipColors,
                UPPER_FLIP_STOPS, Shader.TileMode.CLAMP);
            mUpperFlipTop = top;
            mUpperFlipBottom = bottom;
            mUpperFlipShaderGeneration = mFlipShaderGeneration;
        }
        return mUpperFlipShader;
    }

    @NonNull
    private LinearGradient lowerFlipShader(float top, float bottom) {
        if (mLowerFlipShader == null || mLowerFlipShaderGeneration != mFlipShaderGeneration
            || mLowerFlipTop != top || mLowerFlipBottom != bottom) {
            mLowerFlipShader = new LinearGradient(0f, top, 0f, bottom, mLowerFlipColors,
                LOWER_FLIP_STOPS, Shader.TileMode.CLAMP);
            mLowerFlipTop = top;
            mLowerFlipBottom = bottom;
            mLowerFlipShaderGeneration = mFlipShaderGeneration;
        }
        return mLowerFlipShader;
    }

    /**
     * Lazy mode swaps the digits instead of folding them. The fold is the launcher's only source of
     * idle frames — a 340ms flip restarting every second on a 119Hz panel is a full-window repaint
     * about forty times a second — so switching it off is what takes the idle cost to nothing.
     *
     * <p>Swapping means every drawer sees its animation as already settled: the one frame a lazy
     * tick paints must be the final pose. Left to the timestamps, that frame lands at progress 0:
     * the seconds' ones digit sat 4dp low at near-zero alpha for the whole second, on every style
     * but the flip. Returns true if it changed.
     */
    public boolean setLazyMode(boolean lazy) {
        if (mLazyMode == lazy) return false;
        mLazyMode = lazy;
        return true;
    }

    private boolean mLazyMode;

    /** True while a digit is still turning at {@code now}, so the host should ask for a frame. */
    public boolean hasRunningAnimation(long now) {
        if (mLazyMode) return false;
        long duration = styleDurationMs();
        for (long changedAt : mChangedAt) {
            if (changedAt > 0L && now - changedAt < duration) return true;
        }
        if (!mSecondsAnimated || !mShowSeconds) return false;
        long secondsDuration = mForm == TopPaneClockForm.FULL
            && TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP.equals(mStyle)
            ? SECONDS_FLIP_DURATION_MS : duration;
        for (long changedAt : mSecondsChangedAt) {
            if (changedAt > 0L && now - changedAt < secondsDuration) return true;
        }
        return false;
    }

    private long styleDurationMs() {
        switch (mStyle) {
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD:
                return LCD_DURATION_MS;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED:
                return LED_DURATION_MS;
            case TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP:
                return FLIP_DURATION_MS;
            default:
                return TEXT_DURATION_MS;
        }
    }

    @VisibleForTesting
    float progress(int digit, long now, long duration) {
        if (mLazyMode || mChangedAt[digit] <= 0L) return 1f;
        return clamp01((now - mChangedAt[digit]) / (float) duration);
    }

    @VisibleForTesting
    float secondsProgress(int digit, long now, long duration) {
        if (mLazyMode || !mSecondsAnimated || mSecondsChangedAt[digit] <= 0L) return 1f;
        return clamp01((now - mSecondsChangedAt[digit]) / (float) duration);
    }

    private void seedFlipLoadFlourish(long now) {
        for (int i = 0; i < mDigits.length; i++) {
            mOldDigits[i] = mDigits[i];
            mChangedAt[i] = now + FLIP_LOAD_STAGGER_MS[i];
        }
    }

    /** Font-scale-aware px for a size in sp. */
    private float sp(float value) {
        return android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, value,
            mResources.getDisplayMetrics());
    }

    private float dp(float value) {
        return value * mResources.getDisplayMetrics().density;
    }

    private static float ease(float progress) {
        return 1f - (1f - progress) * (1f - progress);
    }

    private static int alpha(int color, float fraction) {
        return ColorUtils.setAlphaComponent(color,
            Math.round(Color.alpha(color) * clamp01(fraction)));
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int brighten(int color, float amount) {
        float scale = Math.max(1f, amount);
        return Color.argb(Color.alpha(color),
            Math.min(255, Math.round(Color.red(color) * scale)),
            Math.min(255, Math.round(Color.green(color) * scale)),
            Math.min(255, Math.round(Color.blue(color) * scale)));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    @NonNull
    static ClockSnapshot snapshot(long wallTime, @NonNull TimeZone zone) {
        return snapshot(wallTime, zone, false);
    }

    @NonNull
    static ClockSnapshot snapshot(long wallTime, @NonNull TimeZone zone, boolean useAmPm) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(wallTime);
        String hh = twoDigits(useAmPm ? calendar.get(Calendar.HOUR) == 0
            ? 12 : calendar.get(Calendar.HOUR) : calendar.get(Calendar.HOUR_OF_DAY));
        String mm = twoDigits(calendar.get(Calendar.MINUTE));
        String ss = twoDigits(calendar.get(Calendar.SECOND));
        String period = useAmPm
            ? (calendar.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM") : "";
        return new ClockSnapshot(hh, mm, ss, dateText(wallTime, zone, Locale.getDefault()),
            period);
    }

    /** The skeleton the date is asked for in: abbreviated weekday, day of month, abbreviated month. */
    private static final String DATE_SKELETON = "EEEdMMM";
    /** The order the developer chose, for a platform that has no pattern for the locale. */
    private static final String DATE_FALLBACK = "EEE d MMM";
    private static final Object DATE_LOCK = new Object();
    @Nullable private static Locale sDateLocale;
    @Nullable private static SimpleDateFormat sDateFormat;

    /**
     * The date as the locale writes it — "Wed 21 Oct" in British English, "Wed, Oct 21" in
     * American — in sentence case and without tracking. The pattern is the platform's best one
     * for {@link #DATE_SKELETON}, built once per locale; the date changes once a day and this is
     * asked once a second, never in a draw.
     */
    @VisibleForTesting
    @NonNull
    static String dateText(long wallTime, @NonNull TimeZone zone, @NonNull Locale locale) {
        synchronized (DATE_LOCK) {
            if (sDateFormat == null || !locale.equals(sDateLocale)) {
                String pattern = android.text.format.DateFormat.getBestDateTimePattern(locale,
                    DATE_SKELETON);
                // A platform without the locale's pattern data hands the skeleton back as is,
                // which would run "Wed21Oct" together.
                if (pattern == null || !hasSeparator(pattern)) pattern = DATE_FALLBACK;
                sDateFormat = new SimpleDateFormat(pattern, locale);
                sDateLocale = locale;
            }
            sDateFormat.setTimeZone(zone);
            return sentenceCase(sDateFormat.format(new java.util.Date(wallTime)), locale);
        }
    }

    /** True if a date pattern sets anything between its fields: a space, a comma, a dot. */
    private static boolean hasSeparator(@NonNull String pattern) {
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= 'A' && c <= 'Z')) return true;
        }
        return false;
    }

    /** {@code text} with its first letter capitalised, as a locale that writes "mer." would not. */
    @NonNull
    private static String sentenceCase(@NonNull String text, @NonNull Locale locale) {
        if (text.isEmpty() || !Character.isLowerCase(text.charAt(0))) return text;
        return text.substring(0, 1).toUpperCase(locale) + text.substring(1);
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    /**
     * Text widths the clock measures on every frame of a face's animation — the date row, the
     * time run, the period — remembered by everything a width depends on: the text, the face,
     * the text size in pixels and the letter spacing. Paint flags are fixed for the widget's life,
     * so nothing else moves a width. A few entries, replaced in turn: the faces measure a handful
     * of strings and they change once a second at most.
     */
    static final class TextWidths {

        /** Measures {@code text} on a paint already set up for the key it is filed under. */
        interface Measurer {
            float measure(@NonNull String text);
        }

        static final int CAPACITY = 12;
        private final String[] mTexts = new String[CAPACITY];
        private final Object[] mFaces = new Object[CAPACITY];
        private final float[] mSizes = new float[CAPACITY];
        private final float[] mSpacings = new float[CAPACITY];
        private final float[] mWidths = new float[CAPACITY];
        private int mNext;

        float width(@NonNull String text, @Nullable Object face, float size, float spacing,
                    @NonNull Measurer measurer) {
            for (int i = 0; i < CAPACITY; i++) {
                if (mFaces[i] == face && mSizes[i] == size && mSpacings[i] == spacing
                    && text.equals(mTexts[i])) return mWidths[i];
            }
            float width = measurer.measure(text);
            int slot = mNext;
            mNext = (slot + 1) % CAPACITY;
            mTexts[slot] = text;
            mFaces[slot] = face;
            mSizes[slot] = size;
            mSpacings[slot] = spacing;
            mWidths[slot] = width;
            return width;
        }
    }

    /**
     * One face's per-glyph advances, for the faces that draw the time a glyph at a time so each
     * digit can rise on its own: measured once per glyph while the face, size and spacing hold,
     * instead of once per glyph per frame.
     */
    static final class CharAdvances {

        private static final String[] ASCII = new String[128];

        static {
            for (int c = 0; c < ASCII.length; c++) ASCII[c] = String.valueOf((char) c);
        }

        /** {@code String.valueOf(c)}, without a new string for the ASCII a clock draws. */
        @NonNull
        static String charString(char c) {
            return c < ASCII.length ? ASCII[c] : String.valueOf(c);
        }

        private final float[] mAdvances = new float[ASCII.length];
        @Nullable private Object mFace;
        private float mSize = Float.NaN;
        private float mSpacing = Float.NaN;

        float advance(char c, @Nullable Object face, float size, float spacing,
                      @NonNull TextWidths.Measurer measurer) {
            if (c >= ASCII.length) return measurer.measure(String.valueOf(c));
            if (face != mFace || size != mSize || spacing != mSpacing) {
                mFace = face;
                mSize = size;
                mSpacing = spacing;
                java.util.Arrays.fill(mAdvances, Float.NaN);
            }
            float advance = mAdvances[c];
            if (Float.isNaN(advance)) {
                advance = measurer.measure(ASCII[c]);
                mAdvances[c] = advance;
            }
            return advance;
        }
    }

    static final class ClockSnapshot {
        final String hh;
        final String mm;
        final String ss;
        final String date;
        final String period;

        ClockSnapshot(String hh, String mm, String ss, String date, String period) {
            this.hh = hh;
            this.mm = mm;
            this.ss = ss;
            this.date = date;
            this.period = period;
        }

        char[] digits() {
            return (hh + mm).toCharArray();
        }

        char[] secondsDigits() {
            return ss.toCharArray();
        }
    }
}
