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
import com.termux.shared.termux.font.NerdFontSpans;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The tab a wall page drops from its top edge when its border is tapped: the same fill, stroke and
 * motion a terminal pane's tab has, so every place on the wall answers a border tap the same way.
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
    /** One hit rectangle per action, in this view's coordinates; recomputed with the geometry. */
    private final List<RectF> mButtons = new ArrayList<>();
    /** The ids drawn in the error colour rather than the primary one. */
    private final List<Integer> mAlerted = new ArrayList<>();
    @Nullable private ValueAnimator mAnimator;
    private float mProgress;
    private boolean mShown;
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
     * The buttons this tab carries, in reading order. Replacing them keeps whatever the tab is
     * doing — a page that swaps the pair for a read-out while the tab is out does not have to put
     * it away first — and an id that is no longer here loses its alert with it.
     */
    public void setActions(@NonNull Action... actions) {
        mActions.clear();
        mActions.addAll(Arrays.asList(actions));
        mButtons.clear();
        for (int i = 0; i < mActions.size(); i++) mButtons.add(new RectF());
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

    public boolean isControlsShown() {
        return mShown;
    }

    public void show() {
        if (mShown) return;
        mShown = true;
        animateTo(1f, false);
    }

    public void dismiss() {
        if (!mShown) return;
        animateTo(0f, true);
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
        for (int i = 0; i < mButtons.size(); i++) {
            if (mButtons.get(i).contains(x, y)) return mActions.get(i).id;
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
                if (clearOnEnd && mProgress <= 0f) mShown = false;
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
     * The tab at the top-trailing corner: the buttons a finger's width apart in a 32dp tab.
     *
     * <p>Each button's hit rectangle takes half the gap to either side and the full tab height, so
     * a thumb that lands between or just below the glyphs still counts — and the outermost two
     * reach the tab's own edges.
     */
    private void computeGeometry() {
        if (mActions.isEmpty()) {
            mTab.setEmpty();
            return;
        }
        float gap = dp(8);
        float pad = dp(5);
        float width = pad + pad + gap * (mActions.size() - 1);
        for (Action action : mActions) width += buttonWidth(action);
        float right = getWidth() - dp(3);
        float left = Math.max(dp(3), right - width);
        float height = dp(32);
        float top = -height * (1f - mProgress);
        mTab.set(left, top, right, top + height);
        float edge = left + pad;
        for (int i = 0; i < mActions.size(); i++) {
            float start = i == 0 ? left : edge - gap / 2f;
            edge += buttonWidth(mActions.get(i));
            float end = i == mActions.size() - 1 ? right : edge + gap / 2f;
            mButtons.get(i).set(start, top, end, top + height);
            edge += gap;
        }
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
        int save = canvas.save();
        // Revealed through the page's top edge, so the closing motion disappears into the frame.
        canvas.clipRect(0f, -dp(1), getWidth(), getHeight());

        mPath.reset();
        mPath.moveTo(mTab.left, 0f);
        mPath.lineTo(mTab.right, 0f);
        mPath.lineTo(mTab.right, mTab.bottom - radius);
        mPath.quadTo(mTab.right, mTab.bottom, mTab.right - radius, mTab.bottom);
        mPath.lineTo(mTab.left + radius, mTab.bottom);
        mPath.quadTo(mTab.left, mTab.bottom, mTab.left, mTab.bottom - radius);
        mPath.close();
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(ColorUtils.setAlphaComponent(surface, Math.round(232f * mProgress)));
        canvas.drawPath(mPath, mPaint);

        mPath.reset();
        mPath.moveTo(mTab.left - dp(5), 0f);
        mPath.lineTo(mTab.left, 0f);
        mPath.lineTo(mTab.left, mTab.bottom - radius);
        mPath.quadTo(mTab.left, mTab.bottom, mTab.left + radius, mTab.bottom);
        mPath.lineTo(mTab.right - radius, mTab.bottom);
        mPath.quadTo(mTab.right, mTab.bottom, mTab.right, mTab.bottom - radius);
        mPath.lineTo(mTab.right, 0f);
        mPath.lineTo(mTab.right + dp(5), 0f);
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
            drawText(canvas, mButtons.get(i), action,
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
