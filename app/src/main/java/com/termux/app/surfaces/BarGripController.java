package com.termux.app.surfaces;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.layouteditor.BarGripPolicy;
import com.termux.app.layouteditor.LayoutEditorController;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * The keyboard's grip on the editor's frame (#340): a pill on the keyboard's inner edge (the one
 * facing the terminal) with a readout of the size and, on a Workspace page, the terminal's rows.
 * Pulling it writes the stored keyboard height scale through the layout session, the value the
 * Height row writes, one for every page; the draft carries it, so Undo and Discard cover it and
 * only Done stores it. A pull holds the re-layout for its release, as the Height slider does.
 *
 * <p>The dock has no grip yet: its stored height scale is the Icon size (see the handoff), so a
 * dock grip would be a second way to size icons, which the spec rules out.
 *
 * <p>The pill is one pooled view of the tap overlay, moved by translation. A pull allocates
 * nothing per move beyond the readout text, which is only rebuilt when the percent or the row
 * count changes.
 */
final class BarGripController {

    interface Host {
        @NonNull Context context();

        @Nullable ViewGroup overlay();

        /** The frame's scale, so the pill keeps its on-screen size. */
        float scale();

        boolean open();

        /** Where the keyboard stands in the overlay (l, t, r, b); false when it is not shown. */
        boolean keyboardRect(@NonNull float[] out);

        @Nullable LayoutEditorController layout();

        /** The Workspace's terminal rows now, or 0 on a page with none. */
        int terminalRows();

        /** The terminal pane's height in pixels, or 0. */
        int paneHeightPx();

        /** The shipped keyboard height scale, where a pull snaps. */
        float defaultScale();

        int surfaceColor();

        int onSurfaceColor();

        /** A pull began or ended; the editor treats it as a slider-like drag. */
        void onPull(boolean pulling);

        /** The draft changed; {@code released} once the finger lets go. */
        void onWritten(boolean released);
    }

    private static final int PILL_HEIGHT_DP = 28;
    private static final int PILL_PAD_DP = 12;

    @NonNull private final Host mHost;
    @NonNull private final float[] mRect = new float[4];
    @Nullable private LinearLayout mPill;
    @Nullable private TextView mReadout;

    private boolean mPulling;
    private boolean mGrowsUp;
    private float mDownRawY;
    private float mStartScale;
    private float mStartHeightPx;
    private int mStartRows;
    private int mPanePx;
    private float mLastScale;
    private int mShownPercent = -1;
    private int mShownRows = -1;

    BarGripController(@NonNull Host host) {
        mHost = host;
    }

    /** Lays the pill on the keyboard's inner edge; hidden while no keyboard stands. */
    void place() {
        ViewGroup overlay = mHost.overlay();
        LayoutEditorController layout = mHost.layout();
        if (overlay == null || !mHost.open() || layout == null || layout.layoutKind() == null
            || !mHost.keyboardRect(mRect)) {
            if (mPill != null && !mPulling) EditorViews.setVisible(mPill, false);
            return;
        }
        LinearLayout pill = pillFor(overlay);
        float scale = Math.max(0.01f, mHost.scale());
        float inverse = 1f / scale;
        if (pill.getScaleX() != inverse) {
            pill.setScaleX(inverse);
            pill.setScaleY(inverse);
        }
        pill.setPivotX(0f);
        pill.setPivotY(0f);
        float width = pill.getMeasuredWidth() * inverse;
        float height = pill.getMeasuredHeight() * inverse;
        // The inner edge is the one facing the middle of the screen.
        boolean upper = (mRect[1] + mRect[3]) / 2f < overlay.getHeight() / 2f;
        if (!mPulling)
            mGrowsUp = !upper;
        float edge = upper ? mRect[3] : mRect[1];
        pill.setTranslationX(mRect[0] + Math.max(0f, (mRect[2] - mRect[0] - width) / 2f));
        pill.setTranslationY(edge - height / 2f);
        EditorViews.setVisible(pill, true);
        if (!mPulling)
            readout(layout.keyboardHeightScale(), mHost.terminalRows());
    }

    void release() {
        ViewGroup overlay = mHost.overlay();
        if (mPill != null && overlay != null)
            overlay.removeView(mPill);
        mPill = null;
        mReadout = null;
        mPulling = false;
        mShownPercent = -1;
        mShownRows = -1;
    }

    private boolean onTouch(@NonNull View view, @NonNull MotionEvent event) {
        LayoutEditorController layout = mHost.layout();
        if (layout == null || !mHost.open())
            return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (!mHost.keyboardRect(mRect))
                    return false;
                float scale = layout.keyboardHeightScale();
                if (scale <= 0f)
                    return false;
                mStartScale = scale;
                mLastScale = scale;
                mStartHeightPx = mRect[3] - mRect[1];
                mStartRows = mHost.terminalRows();
                mPanePx = mHost.paneHeightPx();
                mDownRawY = event.getRawY();
                mPulling = true;
                view.getParent().requestDisallowInterceptTouchEvent(true);
                mHost.onPull(true);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!mPulling)
                    return false;
                // Screen pixels to the frame's: the overlay is scaled about its corner.
                float dy = (event.getRawY() - mDownRawY) / Math.max(0.01f, mHost.scale());
                float growth = mGrowsUp ? -dy : dy;
                float next = BarGripPolicy.scaleFor(mStartScale, mStartHeightPx, growth,
                    mHost.defaultScale(), TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
                    TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE);
                if (Float.compare(next, mLastScale) != 0) {
                    mLastScale = next;
                    layout.setKeyboardHeightScale(next, true);
                    mHost.onWritten(false);
                }
                // The bar's pixel change, from the scales, whether or not the view has re-laid.
                int deltaPx = Math.round(mStartHeightPx * (next / mStartScale - 1f));
                readout(next, BarGripPolicy.terminalRows(mStartRows, mPanePx, deltaPx));
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mPulling)
                    return false;
                mPulling = false;
                mHost.onPull(false);
                layout.relayHeldWrites();
                mHost.onWritten(true);
                return true;
            default:
                return mPulling;
        }
    }

    /** Sets the readout only when what it says changed. */
    private void readout(float scale, int rows) {
        TextView readout = mReadout;
        if (readout == null || scale <= 0f)
            return;
        int percent = BarGripPolicy.percent(scale);
        if (percent == mShownPercent && rows == mShownRows)
            return;
        mShownPercent = percent;
        mShownRows = rows;
        Context context = mHost.context();
        readout.setText(rows > 0
            ? context.getString(R.string.editor_grip_readout_rows, percent, rows)
            : context.getString(R.string.editor_grip_readout, percent));
        readout.setContentDescription(rows > 0
            ? context.getString(R.string.editor_grip_description_rows, percent, rows)
            : context.getString(R.string.editor_grip_description, percent));
    }

    @SuppressLint("ClickableViewAccessibility")
    @NonNull
    private LinearLayout pillFor(@NonNull ViewGroup overlay) {
        LinearLayout existing = mPill;
        if (existing != null)
            return existing;
        Context context = mHost.context();
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(dpToPx(PILL_PAD_DP));
        pill.setPadding(pad, 0, pad, 0);
        GradientDrawable back = new GradientDrawable();
        int surface = mHost.surfaceColor();
        back.setColor((0xE6 << 24) | (surface & 0x00FFFFFF));
        back.setCornerRadius(dpToPx(PILL_HEIGHT_DP / 2f));
        pill.setBackground(back);
        pill.setClickable(true);
        pill.setFocusable(false);

        ImageView dots = new ImageView(context);
        float radius = dpToPx(1.5f);
        float pitch = dpToPx(5f);
        dots.setImageDrawable(new BarGripDrawable(mHost.onSurfaceColor(), radius, pitch));
        dots.setScaleType(ImageView.ScaleType.CENTER);
        LinearLayout.LayoutParams dotsParams = new LinearLayout.LayoutParams(
            Math.round(BarGripDrawable.widthFor(radius, pitch)),
            Math.round(BarGripDrawable.heightFor(radius)));
        dotsParams.setMarginEnd(Math.round(dpToPx(8)));
        pill.addView(dots, dotsParams);

        TextView readout = new TextView(context);
        readout.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        readout.setTextColor(mHost.onSurfaceColor());
        readout.setSingleLine(true);
        readout.setIncludeFontPadding(false);
        readout.setGravity(Gravity.CENTER_VERTICAL);
        pill.addView(readout, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pill.setOnTouchListener(this::onTouch);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(dpToPx(PILL_HEIGHT_DP)),
            Gravity.TOP | Gravity.START);
        overlay.addView(pill, params);
        mPill = pill;
        mReadout = readout;
        // Sized once for the widest text it will show, so a pull never re-lays it.
        readout.setText(context.getString(R.string.editor_grip_readout_rows, 160, 99));
        pill.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(Math.round(dpToPx(PILL_HEIGHT_DP)),
                View.MeasureSpec.EXACTLY));
        params.width = pill.getMeasuredWidth();
        mShownPercent = -1;
        mShownRows = -1;
        return pill;
    }

    private float dpToPx(float dp) {
        return dp * mHost.context().getResources().getDisplayMetrics().density;
    }
}
