package com.termux.app.terminal;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.ChromeShade;
import com.termux.shared.termux.font.NerdFontSpans;

import java.util.ArrayList;
import java.util.List;

/**
 * The row of chips minimised Home floats come back from ({@link HomeFloatChipsPolicy}), drawn in
 * the float layer just above the dock. As wide as its chips, so a press beside them reaches the
 * page under it; each chip stands on glass of its own, built by the activity from the dock's
 * surface values ({@link #setGlassGround}).
 *
 * <p>Dumb: the activity hands it the chips and places it; the policy lays them out; this draws and
 * reports taps. Nothing here repaints at rest.
 */
public final class HomeFloatChipsView extends View implements HomeFloatLayer.PartlyHeld {

    /** A chip was tapped. */
    public interface Listener {
        void onChipTapped(int index);
    }

    /** Builds the glass one chip stands on, for a chip of {@code cornerRadiusPx}. */
    public interface GlassGround {
        @Nullable Drawable chip(float cornerRadiusPx);
    }

    private static final float GLYPH_SP = 14f;
    private static final float NAME_SP = 12f;

    private final float mDensity;
    private final TextPaint mGlyphPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mNamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final ChipsAccessibility mAccessibility;

    @Nullable private Listener mListener;
    @NonNull private List<HomeFloatChipsPolicy.Chip> mChips = new ArrayList<>();
    @NonNull private HomeFloatChipsPolicy.Layout mLayout = HomeFloatChipsPolicy.EMPTY;
    /** Glyph widths and each name ellipsized to its room, from the last relayout. */
    @NonNull private float[] mGlyphWidths = new float[0];
    @NonNull private CharSequence[] mNames = new CharSequence[0];
    @NonNull private float[] mNameWidths = new float[0];
    /** The width the row was last laid out to fill; the layer's. */
    private int mLayerWidth;

    @ColorInt private int mOnSurface;
    @ColorInt private int mSurfaceBase;
    @ColorInt private int mPanel;
    @Nullable private GlassGround mGround;
    /** Each chip's own glass, built when the ground or the number of chips changes. */
    @NonNull private Drawable[] mGlass = new Drawable[0];
    @Nullable private ChromeInk.Polarity mPolarity;
    private boolean mInkResolved;
    private int mDownChip = -1;
    private float mDownX;
    private float mDownY;
    private final int mTouchSlop;

    public HomeFloatChipsView(@NonNull Context context) {
        super(context);
        mDensity = getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mGlyphPaint.setTypeface(NerdFontSpans.typeface(context));
        mNamePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(Math.max(1, Math.round(mDensity)));
        applyTextSizes();
        readThemeColors();
        mAccessibility = new ChipsAccessibility(this);
        ViewCompat.setAccessibilityDelegate(this, mAccessibility);
        setVisibility(GONE);
    }

    public void setListener(@Nullable Listener listener) { mListener = listener; }

    /**
     * The minimised floats' chips, in the order the floats were opened. Shown while there is one;
     * a list equal to the last one changes nothing.
     */
    public void setChips(@NonNull List<HomeFloatChipsPolicy.Chip> chips) {
        int visibility = chips.isEmpty() ? GONE : VISIBLE;
        if (getVisibility() != visibility) setVisibility(visibility);
        if (chips.equals(mChips)) return;
        boolean countChanged = chips.size() != mChips.size();
        mChips = new ArrayList<>(chips);
        if (countChanged) buildGlass();
        relayout(mLayerWidth);
        requestLayout();
        invalidate();
        mAccessibility.invalidateRoot();
    }

    /** Only a chip is held: a press between chips, or a swipe there, is the page's. */
    @Override public boolean holdsLocalPoint(float x, float y) {
        return y >= 0f && y < getHeight() && HomeFloatChipsPolicy.chipAt(mLayout, x) >= 0;
    }

    /**
     * The theme's neutrals and panel read again, for a scheme or Material You change made without
     * recreating the activity; repaints only when one moved.
     */
    public void refreshTheme() {
        int onSurface = mOnSurface;
        int surfaceBase = mSurfaceBase;
        int panel = mPanel;
        boolean resolved = mInkResolved;
        readThemeColors();
        if (onSurface != mOnSurface || surfaceBase != mSurfaceBase || panel != mPanel) {
            invalidate();
        } else {
            mInkResolved = resolved;
        }
    }

    /** The chips shown now, for tests. */
    @NonNull List<HomeFloatChipsPolicy.Chip> chips() { return mChips; }

    /** The row as laid out now, for tests. */
    @NonNull HomeFloatChipsPolicy.Layout layoutNow() { return mLayout; }

    /**
     * The glass each chip stands on, or null for none (the chips then read their ink against the
     * plain panel). The caller hands a new ground when a glass value or the wallpaper frame moves.
     */
    public void setGlassGround(@Nullable GlassGround ground) {
        mGround = ground;
        mInkResolved = false;
        buildGlass();
        invalidate();
    }

    /** Whether the chips stand on glass, for tests. */
    boolean hasGlass() {
        for (Drawable glass : mGlass) if (glass != null) return true;
        return false;
    }

    private void buildGlass() {
        for (Drawable glass : mGlass) if (glass != null) glass.setCallback(null);
        mGlass = new Drawable[mChips.size()];
        if (mGround == null) return;
        float radius = chipHeight() / 2f;
        for (int i = 0; i < mGlass.length; i++) {
            Drawable glass = mGround.chip(radius);
            // A crossfading frame redraws itself through the view it is on.
            if (glass != null) glass.setCallback(this);
            mGlass[i] = glass;
        }
    }

    @Override protected boolean verifyDrawable(@NonNull Drawable who) {
        for (Drawable glass : mGlass) if (who == glass) return true;
        return super.verifyDrawable(who);
    }

    /**
     * Chip {@code index}'s place in a parent {@code parentWidthPx} wide, as this row lays it out
     * there centred: the place Minimise's flight lands on, known before the row has been laid out
     * with its new chip. False when that chip is not shown.
     */
    public boolean chipBoundsInParent(int index, int parentWidthPx, @NonNull Rect out) {
        if (parentWidthPx != mLayerWidth) relayout(parentWidthPx);
        HomeFloatChipsPolicy.Layout layout = mLayout;
        if (index < 0 || index >= layout.shown) return false;
        float rowLeft = (parentWidthPx - layout.rowWidth) / 2f;
        int top = Math.round(getTop() + getTranslationY());
        out.set(Math.round(rowLeft + layout.left[index]), top,
            Math.round(rowLeft + layout.right[index]), top + chipHeight());
        return true;
    }

    // ---------------------------------------------------------------- layout

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parent = MeasureSpec.getSize(widthMeasureSpec);
        if (parent != mLayerWidth) relayout(parent);
        setMeasuredDimension(Math.round(mLayout.rowWidth), chipHeight());
    }

    @Override protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyTextSizes();
        readThemeColors();
        relayout(mLayerWidth);
        requestLayout();
    }

    /** The chips laid out for a layer {@code layerWidth} wide; text measured here, never in a draw. */
    private void relayout(int layerWidth) {
        mLayerWidth = layerWidth;
        int count = mChips.size();
        float[] glyphs = new float[count];
        float[] names = new float[count];
        for (int i = 0; i < count; i++) {
            HomeFloatChipsPolicy.Chip chip = mChips.get(i);
            glyphs[i] = mGlyphPaint.measureText(chip.glyph);
            names[i] = mNamePaint.measureText(chip.name);
        }
        mGlyphWidths = glyphs;
        mLayout = HomeFloatChipsPolicy.layout(mDensity, layerWidth, glyphs, names);
        CharSequence[] shown = new CharSequence[count];
        float[] shownWidths = new float[count];
        for (int i = 0; i < count; i++) {
            shown[i] = mLayout.names && mLayout.nameRoom > 0f
                ? TextUtils.ellipsize(mChips.get(i).name, mNamePaint, mLayout.nameRoom,
                    TextUtils.TruncateAt.END)
                : "";
            shownWidths[i] = shown[i].length() == 0 ? 0f
                : mNamePaint.measureText(shown[i], 0, shown[i].length());
        }
        mNames = shown;
        mNameWidths = shownWidths;
    }

    private int chipHeight() {
        return Math.round(HomeFloatChipsPolicy.CHIP_HEIGHT_DP * mDensity);
    }

    // ---------------------------------------------------------------- drawing

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        HomeFloatChipsPolicy.Layout layout = mLayout;
        if (layout.shown == 0) return;
        syncInk();
        int height = getHeight();
        float half = mStrokePaint.getStrokeWidth() / 2f;
        float radius = height / 2f;
        float centerY = height / 2f;
        float padding = HomeFloatChipsPolicy.CHIP_PADDING_DP * mDensity;
        float nameGap = HomeFloatChipsPolicy.NAME_GAP_DP * mDensity;
        for (int i = 0; i < layout.shown && i < mChips.size(); i++) {
            float left = layout.left[i];
            float right = layout.right[i];
            Drawable glass = i < mGlass.length ? mGlass[i] : null;
            if (glass != null) {
                glass.setBounds(Math.round(left), 0, Math.round(right), height);
                glass.draw(canvas);
            }
            mRect.set(left + half, half, right - half, height - half);
            canvas.drawRoundRect(mRect, radius - half, radius - half, mStrokePaint);
            int saved = canvas.save();
            canvas.clipRect(left, 0, right, height);
            // The content centred in the chip: the glyph, then the name while names show.
            CharSequence name = i < mNames.length ? mNames[i] : "";
            float nameWidth = i < mNameWidths.length ? mNameWidths[i] : 0f;
            float content = mGlyphWidths[i] + (nameWidth > 0f ? nameGap + nameWidth : 0f);
            float x = Math.max(left + Math.min(padding, (right - left - content) / 2f),
                (left + right - content) / 2f);
            canvas.drawText(mChips.get(i).glyph, x, baseline(mGlyphPaint, centerY), mGlyphPaint);
            if (nameWidth > 0f) {
                canvas.drawText(name, 0, name.length(), x + mGlyphWidths[i] + nameGap,
                    baseline(mNamePaint, centerY), mNamePaint);
            }
            canvas.restoreToCount(saved);
        }
    }

    private void syncInk() {
        ChromeInk.Polarity polarity = ChromeShade.polarity();
        if (mInkResolved && polarity == mPolarity) return;
        mInkResolved = true;
        mPolarity = polarity;
        boolean glass = mGround != null;
        int nominal = ChromeShade.nominalGlass();
        int surface = glass && nominal != Color.TRANSPARENT ? nominal : mPanel;
        int ink = HomeFloatChipsPolicy.ink(glass, polarity, mOnSurface, mSurfaceBase, surface);
        mGlyphPaint.setColor(ink);
        mNamePaint.setColor(ink);
        mStrokePaint.setColor(HomeFloatChipsPolicy.stroke(glass, polarity, mOnSurface,
            mSurfaceBase, surface, ink));
    }

    private void readThemeColors() {
        Context context = getContext();
        mOnSurface = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mSurfaceBase = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSurfaceBase,
            ContextCompat.getColor(context, R.color.termux_surface_base));
        mPanel = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSurfacePanel,
            ContextCompat.getColor(context, R.color.termux_surface_panel));
        mInkResolved = false;
    }

    private void applyTextSizes() {
        mGlyphPaint.setTextSize(sp(GLYPH_SP));
        mNamePaint.setTextSize(sp(NAME_SP));
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
            getResources().getDisplayMetrics());
    }

    private static float baseline(@NonNull Paint paint, float centerY) {
        return centerY - (paint.ascent() + paint.descent()) / 2f;
    }

    // ---------------------------------------------------------------- touch

    /** A tap on a chip reports it; a press between chips is not taken, so the page keeps it. */
    @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownChip = HomeFloatChipsPolicy.chipAt(mLayout, event.getX());
                mDownX = event.getX();
                mDownY = event.getY();
                return mDownChip >= 0;
            case MotionEvent.ACTION_MOVE:
                // Past the slop it is a swipe, not a tap: the chip lets it go.
                if (mDownChip >= 0 && Math.hypot(event.getX() - mDownX, event.getY() - mDownY)
                    > mTouchSlop) mDownChip = -1;
                return true;
            case MotionEvent.ACTION_UP: {
                int chip = HomeFloatChipsPolicy.chipAt(mLayout, event.getX());
                int down = mDownChip;
                mDownChip = -1;
                if (down >= 0 && chip == down && event.getY() >= 0f
                    && event.getY() < getHeight()) {
                    performClick();
                    if (mListener != null) mListener.onChipTapped(chip);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                mDownChip = -1;
                return true;
            default:
                return mDownChip >= 0;
        }
    }

    @Override public boolean performClick() { return super.performClick(); }

    @Override protected boolean dispatchHoverEvent(@NonNull MotionEvent event) {
        return mAccessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    /** Each chip is a node a screen reader can step to and tap. */
    private final class ChipsAccessibility extends ExploreByTouchHelper {

        ChipsAccessibility(@NonNull View host) { super(host); }

        @Override protected int getVirtualViewAt(float x, float y) {
            int chip = HomeFloatChipsPolicy.chipAt(mLayout, x);
            return chip >= 0 ? chip : INVALID_ID;
        }

        @Override protected void getVisibleVirtualViews(List<Integer> ids) {
            for (int i = 0; i < mLayout.shown && i < mChips.size(); i++) ids.add(i);
        }

        @Override protected void onPopulateNodeForVirtualView(int id,
                                                              @NonNull AccessibilityNodeInfoCompat node) {
            Rect bounds = new Rect();
            if (id < mChips.size() && id < mLayout.shown) {
                node.setContentDescription(mChips.get(id).spoken);
                bounds.set(Math.round(mLayout.left[id]), 0, Math.round(mLayout.right[id]),
                    getHeight());
            } else {
                node.setContentDescription("");
            }
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            node.setClickable(true);
            if (bounds.isEmpty()) bounds.set(0, 0, 1, 1);
            node.setBoundsInParent(bounds);
        }

        @Override protected boolean onPerformActionForVirtualView(int id, int action,
                                                                  @Nullable Bundle arguments) {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK || id >= mChips.size()) {
                return false;
            }
            if (mListener != null) mListener.onChipTapped(id);
            return true;
        }
    }
}
