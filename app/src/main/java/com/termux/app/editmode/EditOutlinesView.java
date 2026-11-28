package com.termux.app.editmode;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws {@link EditOutlines} over the live scene: dashed outlines in white, the open card's solid
 * in the accent, each with its label in a small dark pill (the prototype's {@code .eo}). It takes
 * no touch but the Wallpaper chip's, which opens the Wallpaper card. Its outlines are set only when
 * they change; nothing here runs per frame.
 */
@SuppressLint("ViewConstructor")
final class EditOutlinesView extends View {

    /**
     * The prototype's dashed line: 1.5dp in the scheme's on-surface ink at 55 % (the screen's
     * frame at 35 %), so it reads in the light theme too (review P3-2).
     */
    private static final float DASH_STROKE_DP = 1.5f;
    private static final float DASH_ON_DP = 5f;
    private static final float DASH_OFF_DP = 4f;
    private static final int DASH_ALPHA = 0x8C;
    private static final int FRAME_ALPHA = 0x59;
    private static final float LIT_STROKE_DP = 2f;
    /** The label pill: 10sp semi-bold in the on-surface ink on the scheme's surface at 72 %. */
    private static final float LABEL_SP = 10f;
    private static final float CHIP_SP = 11f;
    private static final int PILL_ALPHA = 0xB8;
    private static final float PILL_PAD_X_DP = 7f;
    private static final float PILL_PAD_Y_DP = 2f;
    /** An inside label's place from its outline's top-left; an edge label's from its outline's left. */
    private static final float INSIDE_LEFT_DP = 8f;
    private static final float INSIDE_TOP_DP = 5f;
    private static final float EDGE_LEFT_DP = 14f;
    /** The Wallpaper chip: 26dp tall, 30dp from the top and 12dp from the right of the screen. */
    private static final float CHIP_HEIGHT_DP = 26f;
    private static final float CHIP_TOP_DP = 30f;
    private static final float CHIP_RIGHT_DP = 12f;
    private static final float CHIP_PAD_DP = 10f;
    /** The chip's gap under the system bar. */
    private static final float CHIP_UNDER_BAR_DP = 6f;
    private static final int CHIP_RIM_ALPHA = 0x33;

    private final float mDensity;
    @NonNull private final Runnable mOnWallpaperChip;
    @NonNull private final Paint mLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final Paint mPill = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final DashPathEffect mDash;
    @NonNull private final RectF mScratch = new RectF();
    @NonNull private final RectF mChip = new RectF();
    @NonNull private final Paint.FontMetrics mMetrics = new Paint.FontMetrics();
    /** The scheme's ink and surface, read once: the dashes, the labels and their pills. */
    private final int mInk;
    private final int mSurface;
    @NonNull private List<EditOutlines.Outline> mOutlines = new ArrayList<>();
    private int mAccent;
    private boolean mChipDown;
    /** The system bar's height over the overlay: the chip stands under it. */
    private int mTopInset;

    EditOutlinesView(@NonNull Context context, int accent, @NonNull Runnable onWallpaperChip) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        mAccent = accent;
        mInk = com.google.android.material.color.MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF);
        mSurface = com.google.android.material.color.MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurface, 0xFF0A0E16);
        mOnWallpaperChip = onWallpaperChip;
        mDash = new DashPathEffect(new float[] {DASH_ON_DP * mDensity, DASH_OFF_DP * mDensity}, 0f);
        mLine.setStyle(Paint.Style.STROKE);
        mPill.setStyle(Paint.Style.FILL);
        mText.setColor(mInk);
        mText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
    }

    /** The system bar's height at the overlay's top; the Wallpaper chip stands clear of it. */
    void setTopInset(int px) {
        if (px == mTopInset) return;
        mTopInset = px;
        invalidate();
    }

    /** Takes {@code outlines}; redraws only when they differ from the ones drawn. */
    void setOutlines(@NonNull List<EditOutlines.Outline> outlines) {
        if (same(outlines, mOutlines)) return;
        mOutlines = outlines;
        // The labels are cut to their outlines here, once per change: onDraw allocates nothing.
        float d = mDensity;
        float scaled = getResources().getDisplayMetrics().scaledDensity;
        mShown = new String[outlines.size()];
        for (int i = 0; i < outlines.size(); i++) {
            EditOutlines.Outline outline = outlines.get(i);
            boolean chip = outline.labelAt == EditOutlines.LabelAt.CHIP;
            mText.setTextSize((chip ? CHIP_SP : LABEL_SP) * scaled);
            float padX = (chip ? CHIP_PAD_DP : PILL_PAD_X_DP) * d;
            float room = Math.max(0f, outline.box.width() - 2f * INSIDE_LEFT_DP * d - 2f * padX);
            mShown[i] = ellipsize(outline.label, room);
        }
        invalidate();
    }

    /** Each outline's label as drawn, cut to its outline ({@link #setOutlines}). */
    @NonNull private String[] mShown = new String[0];

    private static boolean same(@NonNull List<EditOutlines.Outline> a, @NonNull List<EditOutlines.Outline> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            EditOutlines.Outline x = a.get(i);
            EditOutlines.Outline y = b.get(i);
            if (x.surface != y.surface || !x.box.equals(y.box) || x.radiusPx != y.radiusPx
                || !x.label.equals(y.label) || x.labelAt != y.labelAt || x.lit != y.lit) return false;
        }
        return true;
    }

    /** The outlines drawn now, for tests. */
    @NonNull
    List<EditOutlines.Outline> outlines() {
        return mOutlines;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        mChip.setEmpty();
        float d = mDensity;
        for (int i = 0; i < mOutlines.size() && i < mShown.length; i++) {
            EditOutlines.Outline outline = mOutlines.get(i);
            float stroke = (outline.lit ? LIT_STROKE_DP : DASH_STROKE_DP) * d;
            mLine.setStrokeWidth(stroke);
            mLine.setPathEffect(outline.lit ? null : mDash);
            mLine.setColor(outline.lit ? mAccent : withAlpha(mInk,
                outline.surface == EditOutlines.Surface.WALLPAPER ? FRAME_ALPHA : DASH_ALPHA));
            float half = stroke / 2f;
            mScratch.set(outline.box.left + half, outline.box.top + half,
                outline.box.right - half, outline.box.bottom - half);
            float radius = Math.max(0f, Math.min(outline.radiusPx,
                Math.min(mScratch.width(), mScratch.height()) / 2f));
            canvas.drawRoundRect(mScratch, radius, radius, mLine);
            if (!mShown[i].isEmpty()) drawLabel(canvas, outline, mShown[i]);
        }
    }

    private void drawLabel(@NonNull Canvas canvas, @NonNull EditOutlines.Outline outline,
                           @NonNull String text) {
        float d = mDensity;
        float scaled = getResources().getDisplayMetrics().scaledDensity;
        boolean chip = outline.labelAt == EditOutlines.LabelAt.CHIP;
        mText.setTextSize((chip ? CHIP_SP : LABEL_SP) * scaled);
        Paint.FontMetrics metrics = mMetrics;
        mText.getFontMetrics(metrics);
        float textHeight = metrics.descent - metrics.ascent;
        float padX = (chip ? CHIP_PAD_DP : PILL_PAD_X_DP) * d;
        float height = chip ? CHIP_HEIGHT_DP * d : textHeight + 2f * PILL_PAD_Y_DP * d;
        float width = mText.measureText(text) + 2f * padX;
        float left;
        float top;
        if (chip) {
            left = getWidth() - CHIP_RIGHT_DP * d - width;
            top = Math.max(CHIP_TOP_DP * d, mTopInset + CHIP_UNDER_BAR_DP * d);
        } else if (outline.labelAt == EditOutlines.LabelAt.ON_EDGE) {
            left = outline.box.left + EDGE_LEFT_DP * d;
            top = outline.box.top - height / 2f;
        } else {
            left = outline.box.left + INSIDE_LEFT_DP * d;
            top = outline.box.top + INSIDE_TOP_DP * d;
        }
        mScratch.set(left, top, left + width, top + height);
        mPill.setColor(chip && outline.lit ? mAccent : withAlpha(mSurface, PILL_ALPHA));
        canvas.drawRoundRect(mScratch, height / 2f, height / 2f, mPill);
        if (chip) {
            mChip.set(mScratch);
            mLine.setPathEffect(null);
            mLine.setStrokeWidth(d);
            mLine.setColor(withAlpha(mInk, CHIP_RIM_ALPHA));
            canvas.drawRoundRect(mScratch, height / 2f, height / 2f, mLine);
        }
        canvas.drawText(text, left + padX, top + (height - textHeight) / 2f - metrics.ascent, mText);
    }

    @NonNull
    private String ellipsize(@NonNull String label, float room) {
        if (mText.measureText(label) <= room) return label;
        int end = mText.breakText(label, true, Math.max(0f, room - mText.measureText("…")), null);
        return end <= 0 ? "" : label.substring(0, end) + "…";
    }

    private static int withAlpha(int colour, int alpha) {
        return (colour & 0x00FFFFFF) | (alpha << 24);
    }

    /** Only the Wallpaper chip takes a touch; everything else reaches the scene under it. */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        boolean inChip = !mChip.isEmpty() && mChip.contains(event.getX(), event.getY());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mChipDown = inChip;
                return inChip;
            case MotionEvent.ACTION_UP:
                if (mChipDown && inChip) mOnWallpaperChip.run();
                mChipDown = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                mChipDown = false;
                return true;
            default:
                return mChipDown;
        }
    }

    /** The Wallpaper chip's rect as last drawn, for tests; empty before a draw. */
    @NonNull
    RectF chipRect() {
        return mChip;
    }

}
