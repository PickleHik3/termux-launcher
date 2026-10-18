package com.termux.app.surfaces;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

/**
 * Row B's body in the Appearance editor: the heading row over the slider row, and over both the
 * keyboard's opacity link (Key opacity and BG opacity move together while linked). The link
 * button stands in the heading row's height between the two linked sliders' tracks, and a leader
 * line runs from it to each track ({@link OpacityLink#leader}). Both are placed from where the
 * sliders and the heading were laid out, so they follow the columns at any width, density or
 * font scale.
 *
 * <p>The button is centred between the tracks unless the heading (the selection's name) reaches
 * that far, as Large text can; then it stands just past the heading, never past the far track
 * ({@link OpacityLink#buttonCentreX}). A heading too long for even that is capped short of the
 * button while a pair shows, and given its width back when none does.</p>
 *
 * <p>Inks are theme roles, as the sliders' are: the lines are {@code colorPrimary} while linked
 * (the active track's ink) and {@code colorOutline} while not.</p>
 */
public final class SliderLinkFrame extends FrameLayout {

    /** The leader lines' stroke. */
    private static final float LINE_DP = 1.5f;
    /** The radius each leader line's bends round off at, where its segments are long enough. */
    private static final float BEND_DP = 4f;

    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    @Nullable private View mButton;
    @Nullable private View mHead;
    @Nullable private TextView mName;
    /** The heading's own maximum width, given back while no pair shows. */
    private int mNameMaxWidth = -1;
    @Nullable private LegendSlider mFirst;
    @Nullable private LegendSlider mSecond;
    private boolean mLinked;
    private int mLinkedInk;
    private int mQuietInk;

    public SliderLinkFrame(@NonNull Context context) {
        this(context, null);
    }

    public SliderLinkFrame(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeCap(Paint.Cap.ROUND);
        mLinePaint.setStrokeJoin(Paint.Join.ROUND);
        mLinePaint.setStrokeWidth(dp(LINE_DP));
        resolveInks();
    }

    /**
     * The views the link stands among: its button, the heading row whose height it is centred
     * in, and the heading kept clear of it.
     */
    void bind(@NonNull View button, @NonNull View head, @NonNull TextView name) {
        mButton = button;
        mHead = head;
        mName = name;
        mNameMaxWidth = name.getMaxWidth();
    }

    /**
     * The two sliders the link joins, in column order, or nulls for none: the button shows
     * exactly while a pair is set.
     */
    void setPair(@Nullable LegendSlider first, @Nullable LegendSlider second) {
        boolean shown = first != null && second != null;
        if (!shown) {
            first = null;
            second = null;
        }
        if (mButton != null) {
            int visibility = shown ? View.VISIBLE : View.GONE;
            if (mButton.getVisibility() != visibility)
                mButton.setVisibility(visibility);
        }
        if (!shown && mName != null && mNameMaxWidth >= 0 && mName.getMaxWidth() != mNameMaxWidth)
            mName.setMaxWidth(mNameMaxWidth);
        if (first == mFirst && second == mSecond)
            return;
        mFirst = first;
        mSecond = second;
        mPath.reset();
        requestLayout();
        invalidate();
    }

    /** The lines' ink: linked or not. */
    void setLinked(boolean linked) {
        if (mLinked == linked)
            return;
        mLinked = linked;
        invalidate();
    }

    /** Whether a pair is set and its lines were drawn from laid-out sliders, for a test to read. */
    boolean hasLeaders() {
        return mFirst != null && !mPath.isEmpty();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        resolveInks();
        invalidate();
    }

    private void resolveInks() {
        mLinkedInk = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary,
            0xFF6750A4);
        mQuietInk = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOutline, 0xFF79747E);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        placeLink();
    }

    /**
     * Stands the button between the two tracks in the heading row's height (a translation, so
     * no layout is asked for) and draws the leader lines from it to the tracks' tops.
     */
    private void placeLink() {
        mPath.reset();
        View button = mButton;
        View head = mHead;
        LegendSlider first = mFirst;
        LegendSlider second = mSecond;
        if (button == null || head == null || first == null || second == null
            || first.getVisibility() != View.VISIBLE || second.getVisibility() != View.VISIBLE
            || first.getWidth() <= 0 || second.getWidth() <= 0)
            return;
        float[] firstAt = offsetOf(first);
        float[] secondAt = offsetOf(second);
        float[] headAt = offsetOf(head);
        if (firstAt == null || secondAt == null || headAt == null)
            return;
        float firstX = firstAt[0] + first.trackCentreX();
        float secondX = secondAt[0] + second.trackCentreX();
        float half = button.getWidth() / 2f;
        TextView name = mName;
        boolean nameShown = name != null && name.getVisibility() == View.VISIBLE;
        boolean nameOnLeft = getLayoutDirection() != View.LAYOUT_DIRECTION_RTL;
        float nameLeft = nameShown ? headAt[0] + name.getLeft() : Float.NaN;
        float nameRight = nameShown ? headAt[0] + name.getRight() : Float.NaN;
        float centreX = OpacityLink.buttonCentreX(firstX, secondX, half, nameOnLeft,
            nameOnLeft ? nameRight : nameLeft);
        float buttonLeft = centreX - half;
        float buttonTop = headAt[1] + (head.getHeight() - button.getHeight()) / 2f;
        float translationX = buttonLeft - button.getLeft();
        float translationY = buttonTop - button.getTop();
        if (button.getTranslationX() != translationX)
            button.setTranslationX(translationX);
        if (button.getTranslationY() != translationY)
            button.setTranslationY(translationY);
        float buttonBottom = buttonTop + button.getHeight();
        addLeader(OpacityLink.leader(centreX, buttonBottom, firstX, firstAt[1]));
        addLeader(OpacityLink.leader(centreX, buttonBottom, secondX, secondAt[1]));
        if (nameShown)
            holdNameClear(OpacityLink.nameRoom(firstX, secondX, half, nameOnLeft,
                nameOnLeft ? nameLeft : nameRight));
        invalidate();
    }

    /**
     * Caps the heading's width at {@code room}, never above its own maximum. Posted: the cap is a
     * layout change, and this runs inside a layout pass. The room does not depend on the
     * heading's width, so the second pass asks for nothing more.
     */
    private void holdNameClear(float room) {
        TextView name = mName;
        if (name == null || mNameMaxWidth < 0)
            return;
        int wanted = Math.max(0, Math.min(mNameMaxWidth, (int) Math.floor(room)));
        if (name.getMaxWidth() == wanted)
            return;
        post(() -> {
            if (mFirst != null && name.getMaxWidth() != wanted)
                name.setMaxWidth(wanted);
        });
    }

    /** One leader polyline with its bends rounded, into {@link #mPath}. */
    private void addLeader(@NonNull float[] points) {
        float bend = dp(BEND_DP);
        mPath.moveTo(points[0], points[1]);
        int count = points.length / 2;
        for (int i = 1; i < count - 1; i++) {
            float px = points[2 * i - 2], py = points[2 * i - 1];
            float cx = points[2 * i], cy = points[2 * i + 1];
            float nx = points[2 * i + 2], ny = points[2 * i + 3];
            float inLength = (float) Math.hypot(cx - px, cy - py);
            float outLength = (float) Math.hypot(nx - cx, ny - cy);
            float radius = Math.min(bend, Math.min(inLength, outLength) / 2f);
            if (radius <= 0f) {
                mPath.lineTo(cx, cy);
                continue;
            }
            mPath.lineTo(cx - (cx - px) / inLength * radius, cy - (cy - py) / inLength * radius);
            mPath.quadTo(cx, cy, cx + (nx - cx) / outLength * radius,
                cy + (ny - cy) / outLength * radius);
        }
        mPath.lineTo(points[points.length - 2], points[points.length - 1]);
    }

    /** {@code view}'s top-left in this frame's coordinates, or null when it is not inside it. */
    @Nullable
    private float[] offsetOf(@NonNull View view) {
        float x = 0f;
        float y = 0f;
        View at = view;
        while (at != this) {
            x += at.getLeft();
            y += at.getTop();
            ViewParent parent = at.getParent();
            if (!(parent instanceof View))
                return null;
            at = (View) parent;
        }
        return new float[] {x, y};
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mFirst == null || mPath.isEmpty())
            return;
        mLinePaint.setColor(mLinked ? mLinkedInk : mQuietInk);
        canvas.drawPath(mPath, mLinePaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
