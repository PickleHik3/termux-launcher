package com.termux.app.terminal.extrakeys;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import com.termux.app.wall.pageswitch.PageGlyphPainter;
import com.termux.app.wall.pageswitch.PageSwitchModel;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.font.NerdFontSpans;

import java.util.Locale;

/**
 * One key of the extra-keys bar. It is a label; the page above it owns the touch, and the cap draws
 * what a touch and a modifier do to it: a glow where the finger went down, a rim that brightens
 * with it, a mark for an armed or locked modifier, and a short pulse for each repeat.
 */
public final class ExtraKeyCapView extends AppCompatTextView {

    /** Material's disabled-content opacity, as an alpha channel: 38% of 255. */
    public static final int DISABLED_LABEL_ALPHA = 97;

    public static final float MARK_DOT_DP = 6f;
    public static final float MARK_BAR_WIDTH_DP = 18f;
    public static final float MARK_BAR_HEIGHT_DP = 3f;
    public static final float MARK_GAP_DP = 3f;
    public static final float RIM_DP = 1f;

    private static final int GLOW_CORE_ALPHA = 150;
    private static final int GLOW_MID_ALPHA = 80;
    private static final int RIM_MAX_ALPHA = 220;
    private static final float GLOW_REACH = 0.75f;
    private static final float GLOW_REACH_WIDE = 0.95f;

    @NonNull private ExtraKeyButton mKey;
    @NonNull private final KeyId mKeyId;
    private final float mDensity;

    /** Row and column of the cell this cap stands in, in the page's own (untransposed) matrix. */
    int mRow;
    int mColumn;

    private float mCapInsetH;
    private float mCapInsetV;
    private float mCapRadius;
    private final RectF mCapRect = new RectF();
    private final RectF mRimRect = new RectF();
    private final Path mCapPath = new Path();

    private int mAccent = Color.WHITE;
    private int mLabelColor = Color.WHITE;
    private boolean mGlowWide;
    private boolean mDead;
    private ExtraKeyModifiers.State mState = ExtraKeyModifiers.State.OFF;

    /** The page switcher key's state; null until the host says, when the cap draws its label. */
    @Nullable private PageSwitchModel.Kind mGlyphKind;
    private int mGlyphNumber;
    @Nullable private PageGlyphPainter mGlyphPainter;

    private float mGlow;
    private float mGlowX;
    private float mGlowY;
    @Nullable private RadialGradient mGradient;
    private final Matrix mGradientMatrix = new Matrix();
    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mMarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics mMetrics = new Paint.FontMetrics();
    @Nullable private ValueAnimator mFade;
    private final Runnable mPulseBack = new Runnable() {
        @Override
        public void run() {
            animate().scaleX(1f).scaleY(1f).setDuration(ExtraKeyPressPolicy.PULSE_MS / 2).start();
        }
    };

    public ExtraKeyCapView(@NonNull Context context, @NonNull ExtraKeyButton key, @NonNull KeyId id) {
        super(context);
        mKey = key;
        mKeyId = id;
        mDensity = context.getResources().getDisplayMetrics().density;
        setGravity(Gravity.CENTER);
        setSingleLine(true);
        setMaxLines(1);
        setPadding(0, 0, 0, 0);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        mRimPaint.setStyle(Paint.Style.STROKE);
        mRimPaint.setStrokeWidth(RIM_DP * mDensity);
        mMarkPaint.setStyle(Paint.Style.FILL);
        mCapInsetH = 2f * mDensity;
        mCapInsetV = 3f * mDensity;
        mCapRadius = 12f * mDensity;
    }

    /** Points a cap that carries over to another key set at that set's key of the same id. */
    void rebind(@NonNull ExtraKeyButton key) {
        mKey = key;
    }

    @NonNull
    public ExtraKeyButton key() {
        return mKey;
    }

    @NonNull
    public KeyId keyId() {
        return mKeyId;
    }

    /**
     * Sets the label. Nerd Font code points are routed to the bundled symbols face so an icon key
     * never shows tofu. A plain label keeps the stock all-caps transformation; a label that needed
     * spans is uppercased by hand, because the all-caps transformation re-creates the text as a
     * plain string and drops the typeface spans.
     */
    public void setLabel(@Nullable CharSequence display, boolean allCaps) {
        applyLabel(this, display, allCaps);
    }

    /** The same label rules for any text view, such as the swipe-up bubble. */
    static void applyLabel(@NonNull TextView view, @Nullable CharSequence display, boolean allCaps) {
        CharSequence label = display == null ? "" : display;
        CharSequence spanned = NerdFontSpans.span(view.getContext(), label);
        if (spanned == label) {
            view.setAllCaps(allCaps);
            view.setText(label);
            return;
        }
        view.setAllCaps(false);
        if (allCaps) {
            // Case mapping can change the length, which would misplace the spans.
            spanned = NerdFontSpans.span(view.getContext(),
                label.toString().toUpperCase(Locale.ROOT));
        }
        view.setText(spanned);
    }

    /**
     * The page switcher key's cap: it draws {@code kind}'s glyph with {@code number} in place of
     * its label, sized and inked as a symbol in the label would be. Compared before it repaints.
     */
    public void setPageGlyph(@NonNull PageSwitchModel.Kind kind, int number) {
        if (mGlyphKind == kind && mGlyphNumber == number) return;
        if (mGlyphPainter == null) mGlyphPainter = new PageGlyphPainter(getContext());
        mGlyphKind = kind;
        mGlyphNumber = number;
        invalidate();
    }

    private void drawPageGlyph(@NonNull Canvas canvas, @NonNull PageSwitchModel.Kind kind,
                               @NonNull PageGlyphPainter painter) {
        float size = getTextSize() * NerdFontSpans.DRAWABLE_SYMBOL_EM;
        painter.draw(canvas, kind, mGlyphNumber, getScrollX() + (getWidth() - size) / 2f,
            getScrollY() + (getHeight() - size) / 2f, size, getCurrentTextColor());
    }

    /** The cap's outline inside its cell, which the glow is clipped to and the rim follows. */
    public void setCapShape(float insetHPx, float insetVPx, float radiusPx) {
        if (insetHPx == mCapInsetH && insetVPx == mCapInsetV && radiusPx == mCapRadius) return;
        mCapInsetH = insetHPx;
        mCapInsetV = insetVPx;
        mCapRadius = radiusPx;
        rebuildOutline();
        invalidate();
    }

    /** The press feedback accent: the glow and the rim. */
    public void setAccent(int accent) {
        if (accent == mAccent) return;
        mGradient = null;
        mAccent = accent;
        invalidate();
    }

    /** A wider glow, for when no blur sits behind the keys to soften it. */
    public void setGlowWide(boolean wide) {
        if (wide == mGlowWide) return;
        mGlowWide = wide;
        invalidate();
    }

    /** The label colour at rest; a locked modifier overrides it with the colour readable on its fill. */
    public void setLabelColor(int color) {
        mLabelColor = color;
        applyLabelColor();
        if (mState != ExtraKeyModifiers.State.OFF) invalidate();
    }

    private void applyLabelColor() {
        int color = mState == ExtraKeyModifiers.State.LOCKED && !mDead ? onInk() : mLabelColor;
        if (getCurrentTextColor() != color) setTextColor(color);
    }

    /**
     * The colour of the modifier mark and of a locked cap's fill: the label's own ink, which the
     * host already chose to read on the surface behind the bar. The press accent follows the
     * wallpaper's palette and can match that surface, so a mark in it alone would vanish.
     */
    private int markInk() {
        return mLabelColor | 0xFF000000;
    }

    /** Black or white, whichever reads on the mark ink: the locked label and its bar. */
    private int onInk() {
        int ink = markInk();
        double luma = 0.299 * Color.red(ink) + 0.587 * Color.green(ink) + 0.114 * Color.blue(ink);
        return luma > 150 ? Color.BLACK : Color.WHITE;
    }

    /** A key that cannot act where the bar stands: label at 38%, no glow, nothing to press. */
    public void setDead(boolean dead) {
        if (mDead == dead) return;
        mDead = dead;
        if (dead) {
            cancelFade();
            mGlow = 0f;
        }
        if (isEnabled() == dead) setEnabled(!dead);
        applyLabelColor();
        invalidate();
    }

    public boolean isDead() {
        return mDead;
    }

    public void setModifierState(@NonNull ExtraKeyModifiers.State state) {
        if (state == mState) return;
        mState = state;
        applyLabelColor();
        invalidate();
    }

    @NonNull
    public ExtraKeyModifiers.State modifierState() {
        return mState;
    }

    /** The glow goes to full at once, centred where the finger went down (cap coordinates). */
    public void pressAt(float x, float y) {
        if (mDead) return;
        cancelFade();
        mGlowX = x;
        mGlowY = y;
        mGlow = 1f;
        invalidate();
    }

    /** The glow fades out over {@link ExtraKeyPressPolicy#GLOW_FADE_MS} and then nothing runs. */
    public void release() {
        if (mGlow <= 0f) return;
        if (mFade == null) {
            mFade = new ValueAnimator();
            mFade.setInterpolator(null);
            mFade.setDuration(ExtraKeyPressPolicy.GLOW_FADE_MS);
            mFade.addUpdateListener(a -> {
                mGlow = (Float) a.getAnimatedValue();
                invalidate();
            });
        }
        mFade.cancel();
        mFade.setFloatValues(mGlow, 0f);
        mFade.start();
    }

    /** One repeat's scale pulse, draw only. */
    public void pulse() {
        if (mDead) return;
        animate().cancel();
        animate().scaleX(ExtraKeyPressPolicy.PULSE_SCALE).scaleY(ExtraKeyPressPolicy.PULSE_SCALE)
            .setDuration(ExtraKeyPressPolicy.PULSE_MS / 2).withEndAction(mPulseBack).start();
    }

    /** Glow level 0..1, for tests and the page. */
    public float glowLevel() {
        return mGlow;
    }

    private void cancelFade() {
        if (mFade != null) mFade.cancel();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildOutline();
    }

    private void rebuildOutline() {
        mCapRect.set(mCapInsetH, mCapInsetV, getWidth() - mCapInsetH, getHeight() - mCapInsetV);
        float half = RIM_DP * mDensity / 2f;
        mRimRect.set(mCapRect.left + half, mCapRect.top + half, mCapRect.right - half,
            mCapRect.bottom - half);
        mCapPath.rewind();
        mCapPath.addRoundRect(mCapRect, mCapRadius, mCapRadius, Path.Direction.CW);
    }

    /**
     * The cap draws in its own bounds, but the canvas arrives scrolled: a single-line label scrolls
     * horizontally, and centring it in its very wide layout leaves the view scrolled by about half
     * a million pixels. The fill, glow, rim and mark undo that scroll.
     */
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        boolean locked = mState == ExtraKeyModifiers.State.LOCKED && !mDead;
        boolean lit = mGlow > 0f && !mDead && !mCapRect.isEmpty();
        boolean filled = locked && !mCapRect.isEmpty();
        float sx = getScrollX();
        float sy = getScrollY();
        if (filled || lit) {
            int save = canvas.save();
            canvas.translate(sx, sy);
            if (filled) {
                mMarkPaint.setColor(markInk());
                canvas.drawRoundRect(mCapRect, mCapRadius, mCapRadius, mMarkPaint);
            }
            if (lit) drawGlow(canvas);
            canvas.restoreToCount(save);
        }
        PageSwitchModel.Kind glyphKind = mGlyphKind;
        PageGlyphPainter glyphPainter = mGlyphPainter;
        if (glyphKind != null && glyphPainter != null) drawPageGlyph(canvas, glyphKind, glyphPainter);
        else super.onDraw(canvas);
        boolean marked = !mDead && mState != ExtraKeyModifiers.State.OFF;
        if (lit || marked) {
            int save = canvas.save();
            canvas.translate(sx, sy);
            if (lit) {
                mRimPaint.setColor(mAccent);
                mRimPaint.setAlpha(Math.round(RIM_MAX_ALPHA * mGlow));
                canvas.drawRoundRect(mRimRect, mCapRadius, mCapRadius, mRimPaint);
            }
            if (marked) drawMark(canvas, locked);
            canvas.restoreToCount(save);
        }
    }

    private void drawGlow(@NonNull Canvas canvas) {
        if (mGradient == null) {
            mGradient = new RadialGradient(0f, 0f, 1f,
                new int[] {withAlpha(mAccent, GLOW_CORE_ALPHA), withAlpha(mAccent, GLOW_MID_ALPHA),
                    withAlpha(mAccent, 0)},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        }
        float reach = Math.max(getWidth(), getHeight()) * (mGlowWide ? GLOW_REACH_WIDE : GLOW_REACH);
        mGradientMatrix.setScale(reach, reach);
        mGradientMatrix.postTranslate(mGlowX, mGlowY);
        mGradient.setLocalMatrix(mGradientMatrix);
        mGlowPaint.setShader(mGradient);
        mGlowPaint.setAlpha(Math.round(255 * mGlow));
        int save = canvas.save();
        canvas.clipPath(mCapPath);
        canvas.drawCircle(mGlowX, mGlowY, reach, mGlowPaint);
        canvas.restoreToCount(save);
    }

    /** The mark sits just under the label's baseline: a dot when armed, a short bar when locked. */
    private void drawMark(@NonNull Canvas canvas, boolean locked) {
        getPaint().getFontMetrics(mMetrics);
        float baseline = getHeight() / 2f - (mMetrics.ascent + mMetrics.descent) / 2f;
        float gap = MARK_GAP_DP * mDensity;
        float cx = getWidth() / 2f;
        mMarkPaint.setColor(locked ? onInk() : markInk());
        if (locked) {
            float barH = MARK_BAR_HEIGHT_DP * mDensity;
            float barW = MARK_BAR_WIDTH_DP * mDensity;
            float top = Math.min(baseline + mMetrics.descent + gap, getHeight() - mCapInsetV - barH);
            canvas.drawRoundRect(cx - barW / 2f, top, cx + barW / 2f, top + barH, barH / 2f,
                barH / 2f, mMarkPaint);
        } else {
            float r = MARK_DOT_DP * mDensity / 2f;
            float cy = Math.min(baseline + mMetrics.descent + gap + r, getHeight() - mCapInsetV - r);
            canvas.drawCircle(cx, cy, r, mMarkPaint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        // These work like soft keyboard keys: with this flag set they honour TalkBack's
        // hold-to-activate, double-tap or hybrid setting.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.setTextEntryKey(true);
        // The page sends the keys on touch; accessibility reaches them through click.
        info.setClickable(!isDead());
        if (isDead() || !ExtraKeyModifiers.isModifierKey(key().getKey())) {
            info.setLongClickable(false);
            info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
