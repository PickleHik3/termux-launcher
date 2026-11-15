package com.termux.app.wall.switcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.termux.R;
import com.termux.app.GlassRimDrawable;
import com.termux.app.terminal.PaneShape;
import com.termux.shared.termux.extrakeys.PlaceSwitchGlyph;
import com.termux.shared.termux.font.NerdFontSpans;

import java.util.ArrayList;
import java.util.List;

/**
 * The page switcher's picker: a glass pill beside the dot holding the four page-kind glyphs, and
 * above each glyph a card with that kind's windows ({@link PickerModel}, placed by
 * {@link PickerLayout}). The view fills the layer and draws nothing outside the pill and the
 * cards; while it is visible it takes every touch and hands it to its listener in layer
 * coordinates, because the controller runs the gesture and this view decides nothing.
 *
 * <p>Under a finger a cell glows in its page kind's colour, lifts about 12% and a small name label
 * stands above the finger. Opening is one animator whose elements start a stagger apart
 * ({@link PickerMotion}); nothing animates at rest, and with reduced motion nothing animates at all.
 */
public final class PickerView extends View {

    /** What the picker tells its controller. */
    public interface Listener {
        /** A touch event; this view fills the layer, so its coordinates are the layer's. */
        boolean onPickerTouch(@NonNull MotionEvent event);

        /** TalkBack activated a cell. */
        void onCellActivated(@NonNull PickerModel.Cell cell);
    }

    /** The colours the picker is painted in, resolved by the host. */
    public static final class Palette {
        public final int glass;
        public final int ink;
        /** The four page-kind colours, {@link PickerModel.Kind} order. */
        @NonNull public final int[] kindColors;

        public Palette(int glass, int ink, @NonNull int[] kindColors) {
            this.glass = glass;
            this.ink = ink;
            this.kindColors = kindColors;
        }
    }

    /** The glass is near-opaque: the picker stands over live terminal text. */
    static final int UNBLURRED_ALPHA = 0xF6;
    static final float LIFT = 1.12f;
    static final long LIFT_MS = 110L;
    private static final float PILL_RADIUS_DP = 38f;
    private static final float CARD_RADIUS_DP = 14f;
    private static final float GLYPH_SP = 22f;
    private static final float TEXT_SP = 12f;
    private static final float LABEL_SP = 12f;
    private static final float PILL_START_SCALE = 0.15f;
    private static final float CARD_START_SCALE = 0.7f;
    private static final float CELL_START_SCALE = 0.6f;
    private static final float CELL_SLIDE_DP = 18f;

    private static final int STEADY = 0;
    private static final int OPENING = 1;
    private static final int CLOSING = 2;

    private final float mDensity;
    @NonNull private final Listener mListener;
    @NonNull private Palette mPalette;
    private boolean mReducedMotion;
    private final PathInterpolator mOvershoot = new PathInterpolator(.3f, 1.6f, .5f, 1f);
    @NonNull private final GlassRimDrawable mPillRim;
    @NonNull private final GlassRimDrawable mCardRim;
    private final Paint mPanelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mGlyphPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mLabelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mPill = new RectF();
    private final RectF mRect = new RectF();
    private final Rect mIconBounds = new Rect();
    @Nullable private final Drawable mAssistantIcon;

    @Nullable private PickerModel mModel;
    @Nullable private PickerLayout mLayout;
    /** The model's cells flattened once, in column order, glyph first: virtual view ids. */
    private final List<PickerModel.Cell> mCells = new ArrayList<>();
    /** Each cell's rectangle and drawn text, worked out once per show. */
    private final List<RectF> mCellRects = new ArrayList<>();
    private final List<String> mTexts = new ArrayList<>();
    private int[] mStagger = new int[0];
    /** Per column: the stagger of its stack card, or -1 when it has none. */
    private final int[] mCardStagger = new int[4];
    private final RectF[] mCardRects = {new RectF(), new RectF(), new RectF(), new RectF()};

    private int mPhase = STEADY;
    private long mElapsedMs;
    /** 1 to 0 while closing: every element's progress together. */
    private float mCloseLeft = 1f;
    @Nullable private ValueAnimator mMotion;

    @Nullable private PickerModel.Cell mHot;
    private float mHotX;
    private float mHotY;
    private float mLift;
    @Nullable private ValueAnimator mLiftAnimator;
    @Nullable private String mHint;
    @Nullable private final ExploreByTouchHelper mAccessibility;

    public PickerView(@NonNull Context context, @NonNull Palette palette,
                      @NonNull Listener listener) {
        super(context);
        mPalette = palette;
        mListener = listener;
        mDensity = context.getResources().getDisplayMetrics().density;
        setVisibility(INVISIBLE);
        setClickable(true);
        setFocusable(false);
        setWillNotDraw(false);
        setContentDescription(context.getString(R.string.page_switcher_title));

        mPillRim = new GlassRimDrawable(mDensity, PILL_RADIUS_DP * mDensity);
        mCardRim = new GlassRimDrawable(mDensity, CARD_RADIUS_DP * mDensity);
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(Math.max(1f, mDensity));
        Typeface symbols = NerdFontSpans.typeface(context);
        mGlyphPaint.setTypeface(symbols);
        mGlyphPaint.setTextSize(GLYPH_SP * mDensity);
        mGlyphPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(TEXT_SP * mDensity);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mLabelPaint.setTextSize(LABEL_SP * mDensity);
        mLabelPaint.setTextAlign(Paint.Align.CENTER);
        mLabelPaint.setFakeBoldText(true);
        Drawable icon = ContextCompat.getDrawable(context, R.drawable.ic_wall_assistant);
        mAssistantIcon = icon == null ? null : icon.mutate();
        mAccessibility = new CellAccessibility(this);
        ViewCompat.setAccessibilityDelegate(this, mAccessibility);
    }

    public void setPalette(@NonNull Palette palette) {
        mPalette = palette;
        invalidate();
    }

    public void setReducedMotion(boolean reduced) {
        mReducedMotion = reduced;
    }

    /** A line drawn above the pill until the first touch; null clears it. */
    public void setHint(@Nullable String hint) {
        if (TextUtils.equals(mHint, hint)) return;
        mHint = hint;
        invalidate();
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE && mPhase != CLOSING;
    }

    @Nullable
    public PickerModel.Cell hotCell() {
        return mHot;
    }

    /** The cell under a layer point, or null. */
    @Nullable
    public PickerModel.Cell cellAt(float x, float y) {
        PickerLayout layout = mLayout;
        PickerModel model = mModel;
        if (layout == null || model == null || getVisibility() != VISIBLE) return null;
        return layout.cellAt(model, x, y);
    }

    // ---- showing and hiding -----------------------------------------------------------------

    /** Shows the picker for a model standing where {@code layout} puts it. */
    public void show(@NonNull PickerModel model, @NonNull PickerLayout layout, boolean animate) {
        cancelMotion();
        mModel = model;
        mLayout = layout;
        mHot = null;
        mLift = 0f;
        flatten(model, layout);
        layout.pillRect(mPill);
        int last = 0;
        for (PickerModel.Kind kind : PickerModel.Kind.values()) {
            int i = kind.ordinal();
            if (layout.stackRect(model, kind, mCardRects[i])) {
                mCardStagger[i] = layout.staggerIndex(model.column(kind).cells.get(0));
                last = Math.max(last, mCardStagger[i]);
            } else {
                mCardStagger[i] = -1;
            }
        }
        for (int i = 0; i < mCells.size(); i++) last = Math.max(last, mStagger[i]);
        if (mAccessibility != null) mAccessibility.invalidateRoot();
        setVisibility(VISIBLE);
        if (!animate || mReducedMotion || !isAttachedToWindow()) {
            mPhase = STEADY;
            invalidate();
            return;
        }
        mPhase = OPENING;
        mElapsedMs = 0L;
        final long total = PickerMotion.totalOpenMs(last);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(total);
        a.addUpdateListener(v -> {
            mElapsedMs = Math.round(v.getAnimatedFraction() * total);
            invalidate();
        });
        a.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override public void onAnimationEnd(Animator animation) {
                if (mCancelled) return;
                mMotion = null;
                mPhase = STEADY;
                invalidate();
            }
        });
        mMotion = a;
        a.start();
    }

    /** Hides the picker; the reverse is quick and unstaggered. */
    public void close(boolean animate) {
        if (getVisibility() != VISIBLE) return;
        cancelMotion();
        setHot(null, mHotX, mHotY);
        if (!animate || mReducedMotion || !isAttachedToWindow()) {
            finishClose();
            return;
        }
        mPhase = CLOSING;
        mCloseLeft = 1f;
        ValueAnimator a = ValueAnimator.ofFloat(1f, 0f);
        a.setDuration(PickerMotion.CLOSE_MS);
        a.addUpdateListener(v -> {
            mCloseLeft = (Float) v.getAnimatedValue();
            invalidate();
        });
        a.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override public void onAnimationEnd(Animator animation) {
                if (mCancelled) return;
                mMotion = null;
                finishClose();
            }
        });
        mMotion = a;
        a.start();
    }

    private void finishClose() {
        mPhase = STEADY;
        mHint = null;
        setVisibility(INVISIBLE);
        mModel = null;
        mLayout = null;
        mCells.clear();
        mCellRects.clear();
        mTexts.clear();
        if (mAccessibility != null) mAccessibility.invalidateRoot();
    }

    private void cancelMotion() {
        ValueAnimator a = mMotion;
        mMotion = null;
        if (a != null) a.cancel();
    }

    private void flatten(@NonNull PickerModel model, @NonNull PickerLayout layout) {
        mCells.clear();
        mCellRects.clear();
        mTexts.clear();
        for (PickerModel.Column column : model.columns()) {
            mCells.add(column.glyph);
            mCells.addAll(column.cells);
        }
        if (mStagger.length < mCells.size()) mStagger = new int[mCells.size()];
        float room = (PickerLayout.CELL_W_DP - 8f) * mDensity;
        for (int i = 0; i < mCells.size(); i++) {
            PickerModel.Cell cell = mCells.get(i);
            RectF r = new RectF();
            layout.cellRect(model, cell, r);
            mCellRects.add(r);
            mStagger[i] = layout.staggerIndex(cell);
            mTexts.add(cell.kind == PickerModel.CellKind.GLYPH ? glyphOf(cell)
                : TextUtils.ellipsize(cell.label, mTextPaint, room,
                    TextUtils.TruncateAt.END).toString());
        }
    }

    // ---- drawing ----------------------------------------------------------------------------

    /** How far along an element is, with the overshoot while opening. */
    private float progress(int staggerIndex) {
        switch (mPhase) {
            case OPENING:
                return mOvershoot.getInterpolation(
                    PickerMotion.windowProgress(mElapsedMs, staggerIndex));
            case CLOSING:
                return mCloseLeft;
            default:
                return 1f;
        }
    }

    private float alphaOf(int staggerIndex) {
        switch (mPhase) {
            case OPENING:
                return PickerMotion.alphaOf(PickerMotion.windowProgress(mElapsedMs, staggerIndex));
            case CLOSING:
                return mCloseLeft;
            default:
                return 1f;
        }
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        PickerModel model = mModel;
        PickerLayout layout = mLayout;
        if (model == null || layout == null) return;
        drawPill(canvas, layout);
        for (PickerModel.Kind kind : PickerModel.Kind.values()) {
            int i = kind.ordinal();
            if (mCardStagger[i] >= 0) drawCard(canvas, mCardRects[i], mCardStagger[i]);
        }
        for (int i = 0; i < mCells.size(); i++) {
            PickerModel.Cell cell = mCells.get(i);
            if (cell == mHot) continue;
            drawCell(canvas, layout, cell, i, 0f);
        }
        // The cell under the finger goes last, so its lift and glow stand over its neighbours.
        if (mHot != null) {
            int i = mCells.indexOf(mHot);
            if (i >= 0) drawCell(canvas, layout, mHot, i, mLift);
            drawLabel(canvas, layout, mHot);
        }
        if (mHint != null && mPhase == STEADY) drawHint(canvas);
    }

    private void drawPill(Canvas canvas, PickerLayout layout) {
        float p = progress(0);
        float a = clamp01(alphaOf(0));
        float scale = PILL_START_SCALE + (1f - PILL_START_SCALE) * p;
        float anchor = layout.edge() == Edge.LEFT ? mPill.left : mPill.right;
        canvas.save();
        canvas.scale(scale, 1f, anchor, mPill.centerY());
        mPanelPaint.setColor(ColorUtils.setAlphaComponent(mPalette.glass,
            Math.round(UNBLURRED_ALPHA * a)));
        float corner = PaneShape.radiusForBounds(PILL_RADIUS_DP * mDensity,
            Math.round(mPill.width()), Math.round(mPill.height()));
        canvas.drawRoundRect(mPill, corner, corner, mPanelPaint);
        mPillRim.setBounds(Math.round(mPill.left), Math.round(mPill.top),
            Math.round(mPill.right), Math.round(mPill.bottom));
        mPillRim.setAlpha(Math.round(255 * a));
        mPillRim.draw(canvas);
        canvas.restore();
    }

    private void drawCard(Canvas canvas, RectF card, int stagger) {
        float p = progress(stagger);
        float a = clamp01(alphaOf(stagger));
        float scale = CARD_START_SCALE + (1f - CARD_START_SCALE) * p;
        canvas.save();
        canvas.scale(scale, scale, card.centerX(), card.bottom);
        mPanelPaint.setColor(ColorUtils.setAlphaComponent(mPalette.glass,
            Math.round(UNBLURRED_ALPHA * a)));
        float corner = PaneShape.radiusForBounds(CARD_RADIUS_DP * mDensity,
            Math.round(card.width()), Math.round(card.height()));
        canvas.drawRoundRect(card, corner, corner, mPanelPaint);
        mCardRim.setBounds(Math.round(card.left), Math.round(card.top), Math.round(card.right),
            Math.round(card.bottom));
        mCardRim.setAlpha(Math.round(255 * a));
        mCardRim.draw(canvas);
        canvas.restore();
    }

    private void drawCell(Canvas canvas, PickerLayout layout, PickerModel.Cell cell, int index,
                          float lift) {
        RectF rect = mCellRects.get(index);
        int stagger = mStagger[index];
        float p = progress(stagger);
        float a = clamp01(alphaOf(stagger));
        int color = mPalette.kindColors[cell.pageKind.ordinal()];
        float cx = rect.centerX();
        float cy = rect.centerY();
        int save = canvas.save();
        if (a < 1f) {
            float pad = 14f * mDensity;
            canvas.saveLayerAlpha(rect.left - pad, rect.top - pad, rect.right + pad,
                rect.bottom + pad, Math.round(255 * a));
        }
        if (mPhase != STEADY) {
            float toward = layout.edge() == Edge.LEFT ? -1f : 1f;
            float s = CELL_START_SCALE + (1f - CELL_START_SCALE) * p;
            canvas.translate(toward * CELL_SLIDE_DP * mDensity * (1f - p), 0f);
            canvas.scale(s, s, cx, cy);
        }
        if (lift > 0f) {
            float s = 1f + (LIFT - 1f) * lift;
            canvas.scale(s, s, cx, cy);
            drawGlow(canvas, rect, color, lift);
        }
        switch (cell.kind) {
            case GLYPH:
                drawGlyph(canvas, cell, color, cx, cy);
                if (cell.current) drawUnderline(canvas, rect, color);
                break;
            case START_DISPLAY:
                mTextPaint.setColor(color);
                drawCentredText(canvas, mTexts.get(index), cx, cy);
                break;
            case OVERFLOW:
                mTextPaint.setColor(ColorUtils.setAlphaComponent(mPalette.ink, 200));
                drawCentredText(canvas, mTexts.get(index), cx, cy);
                break;
            default:
                mTextPaint.setColor(cell.current ? color : mPalette.ink);
                drawCentredText(canvas, mTexts.get(index), cx, cy);
                if (cell.current) drawUnderline(canvas, rect, color);
                break;
        }
        canvas.restoreToCount(save);
    }

    private void drawGlyph(Canvas canvas, PickerModel.Cell cell, int color, float cx, float cy) {
        int alpha = cell.dimmed ? 70 : cell.current ? 255 : PlaceSwitchGlyph.UNFOCUSED_ALPHA;
        int tint = ColorUtils.setAlphaComponent(color, alpha);
        if (cell.pageKind == PickerModel.Kind.ASSISTANT) {
            if (mAssistantIcon == null) return;
            int half = Math.round(GLYPH_SP * mDensity * 0.5f);
            mIconBounds.set(Math.round(cx) - half, Math.round(cy) - half,
                Math.round(cx) + half, Math.round(cy) + half);
            mAssistantIcon.setBounds(mIconBounds);
            mAssistantIcon.setTint(tint);
            mAssistantIcon.draw(canvas);
            return;
        }
        mGlyphPaint.setColor(tint);
        Paint.FontMetrics fm = mGlyphPaint.getFontMetrics();
        canvas.drawText(glyphOf(cell), cx, cy - (fm.ascent + fm.descent) / 2f, mGlyphPaint);
    }

    private void drawCentredText(Canvas canvas, String text, float cx, float cy) {
        Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, mTextPaint);
    }

    private void drawUnderline(Canvas canvas, RectF rect, int color) {
        float w = Math.min(rect.width() * 0.5f, 28f * mDensity);
        float y = rect.bottom - 5f * mDensity;
        mFillPaint.setColor(color);
        float h = 2f * mDensity;
        canvas.drawRoundRect(rect.centerX() - w / 2f, y, rect.centerX() + w / 2f, y + h, h, h,
            mFillPaint);
    }

    /** The page kind's colour spreading out from the cell: a few rings fading outward. */
    private void drawGlow(Canvas canvas, RectF rect, int color, float amount) {
        float radius = 10f * mDensity;
        float corner = 14f * mDensity;
        for (int ring = 3; ring >= 0; ring--) {
            float grow = radius * ring / 3f;
            int alpha = Math.round((ring == 0 ? 52 : 14) * amount);
            mFillPaint.setColor(ColorUtils.setAlphaComponent(color, alpha));
            canvas.drawRoundRect(rect.left - grow, rect.top - grow, rect.right + grow,
                rect.bottom + grow, corner + grow, corner + grow, mFillPaint);
        }
        mStrokePaint.setColor(ColorUtils.setAlphaComponent(color, Math.round(160 * amount)));
        canvas.drawRoundRect(rect, corner, corner, mStrokePaint);
    }

    /** The small name above the finger, kept on screen by the layout. */
    private void drawLabel(Canvas canvas, PickerLayout layout, PickerModel.Cell cell) {
        float padH = 10f * mDensity;
        float padV = 6f * mDensity;
        float maxText = getWidth() - 2f * PickerLayout.LABEL_MARGIN_DP * mDensity - 2f * padH;
        String shown = TextUtils.ellipsize(cell.label, mLabelPaint, Math.max(0f, maxText),
            TextUtils.TruncateAt.END).toString();
        float w = mLabelPaint.measureText(shown) + 2f * padH;
        Paint.FontMetrics fm = mLabelPaint.getFontMetrics();
        float h = fm.descent - fm.ascent + 2f * padV;
        float left = layout.labelX(mHotX, w);
        float top = layout.labelTop(mHotY, h);
        mRect.set(left, top, left + w, top + h);
        int color = mPalette.kindColors[cell.pageKind.ordinal()];
        mFillPaint.setColor(ColorUtils.setAlphaComponent(mPalette.glass, 245));
        canvas.drawRoundRect(mRect, h / 2f, h / 2f, mFillPaint);
        mStrokePaint.setColor(ColorUtils.setAlphaComponent(color, 200));
        canvas.drawRoundRect(mRect, h / 2f, h / 2f, mStrokePaint);
        mLabelPaint.setColor(color);
        canvas.drawText(shown, mRect.centerX(), mRect.centerY() - (fm.ascent + fm.descent) / 2f,
            mLabelPaint);
    }

    /** The first-run line, centred over the pill and kept on screen. */
    private void drawHint(Canvas canvas) {
        String hint = mHint;
        if (hint == null) return;
        float padH = 10f * mDensity;
        float padV = 6f * mDensity;
        float margin = PickerLayout.LABEL_MARGIN_DP * mDensity;
        float maxText = getWidth() - 2f * margin - 2f * padH;
        String shown = TextUtils.ellipsize(hint, mLabelPaint, Math.max(0f, maxText),
            TextUtils.TruncateAt.END).toString();
        float w = mLabelPaint.measureText(shown) + 2f * padH;
        Paint.FontMetrics fm = mLabelPaint.getFontMetrics();
        float h = fm.descent - fm.ascent + 2f * padV;
        float left = Math.max(margin, Math.min(getWidth() - w - margin, mPill.centerX() - w / 2f));
        PickerLayout layout = mLayout;
        PickerModel model = mModel;
        float top = layout == null || model == null ? Math.max(margin, mPill.top - margin - h)
            : layout.hintTop(h, model.tallestColumn());
        mRect.set(left, top, left + w, top + h);
        mFillPaint.setColor(ColorUtils.setAlphaComponent(mPalette.glass, 245));
        canvas.drawRoundRect(mRect, h / 2f, h / 2f, mFillPaint);
        mLabelPaint.setColor(mPalette.ink);
        canvas.drawText(shown, mRect.centerX(), mRect.centerY() - (fm.ascent + fm.descent) / 2f,
            mLabelPaint);
    }

    private static float clamp01(float v) {
        return Math.min(1f, Math.max(0f, v));
    }

    @NonNull
    static String glyphOf(@NonNull PickerModel.Cell cell) {
        switch (cell.pageKind) {
            case HOME: return "";
            case TERMINAL: return "";
            case DISPLAY: return "";
            default: return "";
        }
    }

    // ---- the finger -------------------------------------------------------------------------

    /** The cell under the finger lifts and glows; null when it is over none. */
    public void setHot(@Nullable PickerModel.Cell cell, float x, float y) {
        mHotX = x;
        mHotY = y;
        if (cell == mHot) {
            if (cell != null) invalidate();
            return;
        }
        mHot = cell;
        liftTo(cell == null ? 0f : 1f);
        invalidate();
    }

    private void liftTo(float target) {
        if (mLiftAnimator != null) mLiftAnimator.cancel();
        if (mReducedMotion || !isAttachedToWindow()) {
            mLift = target;
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(mLift, target);
        animator.setDuration(LIFT_MS);
        animator.addUpdateListener(a -> {
            mLift = (Float) a.getAnimatedValue();
            invalidate();
        });
        mLiftAnimator = animator;
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mLiftAnimator != null) mLiftAnimator.cancel();
        cancelMotion();
    }

    @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && mHint != null) setHint(null);
        mListener.onPickerTouch(event);
        return true;
    }

    // ---- TalkBack ---------------------------------------------------------------------------

    @Override public boolean dispatchHoverEvent(@NonNull MotionEvent event) {
        if (mAccessibility != null && mAccessibility.dispatchHoverEvent(event)) return true;
        return super.dispatchHoverEvent(event);
    }

    /** Every cell a focusable button with its name; activating one chooses it. */
    private final class CellAccessibility extends ExploreByTouchHelper {
        CellAccessibility(@NonNull View host) {
            super(host);
        }

        @Override protected int getVirtualViewAt(float x, float y) {
            PickerModel.Cell cell = cellAt(x, y);
            return cell == null ? INVALID_ID : mCells.indexOf(cell);
        }

        @Override protected void getVisibleVirtualViews(@NonNull List<Integer> ids) {
            for (int i = 0; i < mCells.size(); i++) ids.add(i);
        }

        @Override protected void onPopulateNodeForVirtualView(int id,
                @NonNull AccessibilityNodeInfoCompat info) {
            if (id < 0 || id >= mCells.size()) {
                info.setContentDescription("");
                info.setBoundsInParent(new Rect(0, 0, 1, 1));
                return;
            }
            PickerModel.Cell cell = mCells.get(id);
            RectF r = mCellRects.get(id);
            info.setBoundsInParent(new Rect(Math.round(r.left), Math.round(r.top),
                Math.round(r.right), Math.round(r.bottom)));
            info.setClassName("android.widget.Button");
            info.setContentDescription(describe(cell));
            info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            info.setClickable(true);
            info.setSelected(cell.current);
        }

        @Override protected boolean onPerformActionForVirtualView(int id, int action,
                @Nullable Bundle arguments) {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK || id < 0
                || id >= mCells.size()) return false;
            mListener.onCellActivated(mCells.get(id));
            return true;
        }
    }

    @NonNull
    private String describe(@NonNull PickerModel.Cell cell) {
        if (cell.kind == PickerModel.CellKind.OVERFLOW)
            return getContext().getString(R.string.page_switcher_more, cell.overflowCount);
        if (cell.kind == PickerModel.CellKind.START_DISPLAY)
            return getContext().getString(R.string.page_switcher_start_display);
        if (cell.dimmed) return getContext().getString(R.string.page_switcher_add, cell.label);
        if (cell.current) return getContext().getString(R.string.page_switcher_current, cell.label);
        return cell.label;
    }
}
