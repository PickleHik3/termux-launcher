package com.termux.app.wall;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.chrome.CornerTabGeometry;
import com.termux.app.chrome.CornerZones;
import com.termux.shared.termux.font.NerdFontSpans;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The tab a wall page drops from the corner that was tapped: the same fill, stroke and motion a
 * terminal pane's tab has, so every place on the wall answers a corner tap the same way. It slides
 * out of whichever edge that corner is on — down from a top corner, up from a bottom one — and
 * lines up with whichever side it is on.
 *
 * <p>What the tab holds is the page's own business — the Display page offers power and its
 * settings, the Widgets page offers its settings and an edit pencil, and while a widget is being
 * edited the same corner reads out the grid's size — so the buttons are handed in as
 * {@link Action}s and this view knows only how to lay them out, draw them and say which one a
 * finger landed on. The page keeps the touches: the view is never clickable, so it cannot stand
 * between a finger and whatever the page is drawing underneath.
 *
 * <p>A button is a 30dp square with a Nerd Font glyph, or a strip as wide as its own text. Both
 * are roomier than a pane's tab on purpose — these sit over a live picture with no other chrome to
 * steady the thumb, and the 22dp pair was missed as often as hit.
 */
public final class PaneControlsView extends View {

    /** Told which button was run; the ids are the page's own. */
    public interface Listener {
        void onPaneControlAction(int id);
    }

    public static final int ACTION_NONE = -1;

    /** One button of the tab: an id the page knows, and either a glyph or a short label. */
    public static final class Action {
        final int id;
        @NonNull final String text;
        final boolean isGlyph;

        private Action(int id, @NonNull String text, boolean isGlyph) {
            this.id = id;
            this.text = text;
            this.isGlyph = isGlyph;
        }

        /** A Nerd Font glyph in a square button. */
        @NonNull
        public static Action glyph(int id, @NonNull String glyph) {
            return new Action(id, glyph, true);
        }

        /** A short read-out — the grid's size — in a button as wide as its text. */
        @NonNull
        public static Action label(int id, @NonNull String text) {
            return new Action(id, text, false);
        }
    }

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final RectF mTab = new RectF();
    private final List<Action> mActions = new ArrayList<>();
    /** This view's own bounds, the frame the tab is laid out inside. */
    private final RectF mBounds = new RectF();
    /** One hit rectangle per action, in this view's coordinates; recomputed with the geometry. */
    private RectF[] mButtons = new RectF[0];
    /** Each action's asked-for width, in the order they are drawn. */
    private float[] mWidths = new float[0];
    /** The ids drawn in the error colour rather than the primary one. */
    private final List<Integer> mAlerted = new ArrayList<>();
    @Nullable private ValueAnimator mAnimator;
    /** How far in from the frame's own edge the tab starts; negative until a page says. */
    private float mCornerInsetPx = -1f;
    /** The corner it comes out of; {@link CornerZones#NONE} until a page or the default says. */
    private int mCorner = CornerZones.NONE;
    private float mProgress;
    private boolean mShown;
    /** Sliding back in; cleared when the slide lands or a show() turns it round. */
    private boolean mRetracting;
    @Nullable private Listener mListener;

    public PaneControlsView(@NonNull Context context) {
        super(context);
        mGlyphPaint.setTypeface(NerdFontSpans.typeface(context));
        mGlyphPaint.setTextAlign(Paint.Align.CENTER);
        mGlyphPaint.setTextSize(dp(14));
        mLabelPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mLabelPaint.setTextAlign(Paint.Align.CENTER);
        mLabelPaint.setTextSize(dp(12));
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /**
     * How far in from the page's own edge the tab sits. A page that clips to its rounded shape has
     * to keep the tab clear of the corner arc, or the arc cuts the tab's own corner off; a page
     * that paints its corners instead leaves this alone.
     */
    public void setCornerInsetPx(float insetPx) {
        if (mCornerInsetPx == insetPx) return;
        mCornerInsetPx = insetPx;
        invalidate();
    }

    /** The corner the tab is out of, or coming out of. */
    public int corner() {
        return mCorner == CornerZones.NONE ? defaultCorner() : mCorner;
    }

    /**
     * Where a tab nobody aimed goes: the top-trailing corner, which is where every place on the
     * wall has always carried its controls.
     */
    private int defaultCorner() {
        return CornerZones.corner(true, false, getLayoutDirection() == LAYOUT_DIRECTION_RTL);
    }

    /**
     * The buttons this tab carries, in reading order. Replacing them keeps whatever the tab is
     * doing — a page that swaps the pair for a read-out while the tab is out does not have to put
     * it away first — and an id that is no longer here loses its alert with it.
     */
    public void setActions(@NonNull Action... actions) {
        mActions.clear();
        mActions.addAll(Arrays.asList(actions));
        mButtons = new RectF[mActions.size()];
        mWidths = new float[mActions.size()];
        for (int i = 0; i < mActions.size(); i++) mButtons[i] = new RectF();
        for (int i = mAlerted.size() - 1; i >= 0; i--) {
            if (indexOf(mAlerted.get(i)) < 0) mAlerted.remove(i);
        }
        invalidate();
    }

    /**
     * Whether one button reads as an alert: the error colour, as a close does. The Display page's
     * power glyph turns while a display is running.
     */
    public void setActionAlert(int id, boolean alert) {
        boolean was = mAlerted.contains(id);
        if (was == alert) return;
        if (alert) mAlerted.add(id);
        else mAlerted.remove(Integer.valueOf(id));
        invalidate();
    }

    /** Out, or on its way out: a retracting tab is already gone to a tap, and show() brings it back. */
    public boolean isControlsShown() {
        return mShown && !mRetracting;
    }

    /**
     * Slide out at the top-trailing corner, for a tab nobody aimed — a mode's own chrome coming
     * out on its own, rather than a finger asking for it somewhere in particular.
     */
    public void show() {
        show(defaultCorner());
    }

    /**
     * Slide out at one corner. A tab still retracting turns round here rather than staying put -
     * the pencil puts the pair away and, in the same touch, editing asks for the grid's size in
     * its place. A tab already out at another corner starts again from the new one, so it always
     * comes out of the corner the finger asked at.
     */
    public void show(int corner) {
        boolean moved = corner() != corner;
        mCorner = corner;
        if (mShown && !mRetracting) {
            if (!moved) return;
            mProgress = 0f;
        }
        animateTo(1f, false);
        mShown = true;
        mRetracting = false;
    }

    public void dismiss() {
        if (!mShown || mRetracting) return;
        animateTo(0f, true);
        mRetracting = true;
    }

    /** Run one button; false when the id is not on this tab. */
    public boolean activate(int id) {
        if (mListener == null || indexOf(id) < 0) return false;
        mListener.onPaneControlAction(id);
        return true;
    }

    /** The button at {@code (x, y)}, or {@link #ACTION_NONE}; nothing answers while half shown. */
    public int actionAt(float x, float y) {
        if (!mShown || mProgress < .35f || mActions.isEmpty()) return ACTION_NONE;
        computeGeometry();
        for (int i = 0; i < mButtons.length; i++) {
            if (mButtons[i].contains(x, y)) return mActions.get(i).id;
        }
        return ACTION_NONE;
    }

    /** Where the tab sits on screen, for a popup that has to hang off it. */
    public void tabBounds(@NonNull RectF out) {
        computeGeometry();
        out.set(mTab);
    }

    private int indexOf(int id) {
        for (int i = 0; i < mActions.size(); i++) {
            if (mActions.get(i).id == id) return i;
        }
        return -1;
    }

    private void animateTo(float target, boolean clearOnEnd) {
        if (mAnimator != null) mAnimator.cancel();
        mAnimator = ValueAnimator.ofFloat(mProgress, target);
        mAnimator.setDuration(190L);
        mAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        mAnimator.addUpdateListener(animation -> {
            mProgress = (Float) animation.getAnimatedValue();
            invalidate();
        });
        mAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (clearOnEnd && mProgress <= 0f) {
                    mShown = false;
                    mRetracting = false;
                }
            }
        });
        mAnimator.start();
    }

    /** How wide one button is: a glyph's square, or the room its own text asks for. */
    private float buttonWidth(@NonNull Action action) {
        float square = dp(30);
        if (action.isGlyph) return square;
        return Math.max(square, mLabelPaint.measureText(action.text) + dp(20));
    }

    /**
     * The tab at its corner: the buttons a finger's width apart in a 32dp tab. Where that lands is
     * {@link CornerTabGeometry}'s to say — the same rule a terminal pane's tab follows — and this
     * view brings only the sizes its own buttons wear.
     */
    private void computeGeometry() {
        if (mActions.isEmpty()) {
            mTab.setEmpty();
            return;
        }
        for (int i = 0; i < mActions.size(); i++) mWidths[i] = buttonWidth(mActions.get(i));
        mBounds.set(0f, 0f, getWidth(), getHeight());
        CornerTabGeometry.layout(corner(), mBounds, mWidths, mActions.size(), dp(8), dp(5), dp(32),
            mCornerInsetPx, dp(3), mProgress, mTab, mButtons);
    }

    /** The frame edge the tab slides out of. */
    private float edgeY() {
        return CornerZones.isTop(corner()) ? 0f : getHeight();
    }

    /** The tab's own far edge, the one that carries the rounded pair. */
    private float innerY() {
        return CornerZones.isTop(corner()) ? mTab.bottom : mTab.top;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (!mShown || mProgress <= 0f || getWidth() <= 0 || mActions.isEmpty()) return;
        computeGeometry();
        Context context = getContext();
        int primary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int surface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanel,
            ContextCompat.getColor(context, R.color.termux_surface_panel));
        int error = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorError, Color.RED);
        float radius = dp(4);
        float edge = edgeY();
        float inner = innerY();
        // Which way the tab's far edge lies from the frame edge it came out of.
        float dir = CornerZones.isTop(corner()) ? 1f : -1f;
        int save = canvas.save();
        // Revealed through the page's own edge, so the closing motion disappears into the frame.
        canvas.clipRect(0f, -dp(1), getWidth(), getHeight() + dp(1));

        mPath.reset();
        mPath.moveTo(mTab.left, edge);
        mPath.lineTo(mTab.right, edge);
        mPath.lineTo(mTab.right, inner - dir * radius);
        mPath.quadTo(mTab.right, inner, mTab.right - radius, inner);
        mPath.lineTo(mTab.left + radius, inner);
        mPath.quadTo(mTab.left, inner, mTab.left, inner - dir * radius);
        mPath.close();
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(ColorUtils.setAlphaComponent(surface, Math.round(232f * mProgress)));
        canvas.drawPath(mPath, mPaint);

        mPath.reset();
        mPath.moveTo(mTab.left - dp(5), edge);
        mPath.lineTo(mTab.left, edge);
        mPath.lineTo(mTab.left, inner - dir * radius);
        mPath.quadTo(mTab.left, inner, mTab.left + radius, inner);
        mPath.lineTo(mTab.right - radius, inner);
        mPath.quadTo(mTab.right, inner, mTab.right, inner - dir * radius);
        mPath.lineTo(mTab.right, edge);
        mPath.lineTo(mTab.right + dp(5), edge);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(1));
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setColor(ColorUtils.setAlphaComponent(primary, Math.round(225f * mProgress)));
        canvas.drawPath(mPath, mPaint);

        int alpha = Math.round(255f * mProgress);
        for (int i = 0; i < mActions.size(); i++) {
            Action action = mActions.get(i);
            int tint = mAlerted.contains(action.id) ? error : primary;
            drawText(canvas, mButtons[i], action,
                ColorUtils.setAlphaComponent(tint, alpha));
        }
        canvas.restoreToCount(save);
    }

    private void drawText(@NonNull Canvas canvas, @NonNull RectF button, @NonNull Action action,
                          int color) {
        Paint paint = action.isGlyph ? mGlyphPaint : mLabelPaint;
        paint.setColor(color);
        float baseline = button.centerY() - (paint.ascent() + paint.descent()) / 2f;
        canvas.drawText(action.text, button.centerX(), baseline, paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mAnimator != null) mAnimator.cancel();
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
