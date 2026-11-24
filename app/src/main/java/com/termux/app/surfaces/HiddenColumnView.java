package com.termux.app.surfaces;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.layouteditor.LayoutEditorPlan.TrayItem;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hidden column (#339): beside the frame, at its top and height, holding a chip for each bar
 * the layout puts away. A chip dragged onto the frame (or tapped) brings its bar back; a bar
 * dropped on the column hides ({@link BarDragController}). The view fills the editor's panel host
 * and takes no touch of its own; only the chips do. It is placed by translation from the frame's
 * pose ({@link HiddenColumnGeometry}), never laid out again while the card sheet moves.
 *
 * <p>The column's foot is left free ({@link #hintReservePx}) for the editor's one hint (#343).
 */
final class HiddenColumnView extends FrameLayout {

    /** What the column does for its host. */
    interface Listener {
        /** {@code item}'s chip was tapped or dropped on the frame: show the bar again. */
        void onRestore(@NonNull TrayItem item);
    }

    static final int WIDTH_DP = 88;
    static final int GAP_DP = 8;
    static final int HINT_RESERVE_DP = 72;
    private static final int CHIP_DP = 32;
    private static final int PAD_DP = 6;

    @NonNull private final Listener mListener;
    @NonNull private final LinearLayout mList;
    @NonNull private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final RectF mDraw = new RectF();
    @NonNull private final int[] mRect = new int[4];
    @NonNull private final int[] mFrameRect = new int[4];
    @NonNull private final int[] mOnScreen = new int[2];
    @NonNull private final List<TrayItem> mItems = new ArrayList<>(5);
    private final int mOnSurface;
    private final float mRadius;
    private boolean mTarget;
    private boolean mPlaced;

    HiddenColumnView(@NonNull Context context, @NonNull Listener listener, int surface,
                     int onSurface, int primary) {
        super(context);
        mListener = listener;
        mOnSurface = onSurface;
        float density = context.getResources().getDisplayMetrics().density;
        mRadius = 14 * density;
        setWillNotDraw(false);
        setClickable(false);
        mFill.setStyle(Paint.Style.FILL);
        mFill.setColor((0x66 << 24) | (surface & 0x00FFFFFF));
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeWidth(2 * density);
        mStroke.setColor(primary);
        mList = new LinearLayout(context);
        mList.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(PAD_DP * density);
        mList.setPadding(pad, pad, pad, pad);
        addView(mList, new LayoutParams(Math.round(WIDTH_DP * density),
            LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));
        setVisibility(View.GONE);
    }

    /** The chips for {@code items}, rebuilt only when the list changed. */
    void setItems(@NonNull List<TrayItem> items) {
        if (items.equals(mItems))
            return;
        mItems.clear();
        mItems.addAll(items);
        mList.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (TrayItem item : mItems) {
            TextView chip = new TextView(getContext());
            chip.setText(labelOf(item));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            chip.setTextColor(mOnSurface);
            chip.setSingleLine(true);
            chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setIncludeFontPadding(false);
            int side = Math.round(PAD_DP * density);
            chip.setPadding(side, 0, side, 0);
            android.graphics.drawable.GradientDrawable back =
                new android.graphics.drawable.GradientDrawable();
            back.setColor((0x33 << 24) | (mOnSurface & 0x00FFFFFF));
            back.setCornerRadius(CHIP_DP * density / 2f);
            chip.setBackground(back);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.round(CHIP_DP * density));
            params.bottomMargin = Math.round(PAD_DP * density);
            mList.addView(chip, params);
            chip.setOnTouchListener(new ChipTouch(item));
        }
    }

    /** Places the column over {@code rect} (left, top, right, bottom in this view's coordinates). */
    void place(@NonNull int[] rect) {
        if (mPlaced && rect[0] == mRect[0] && rect[1] == mRect[1] && rect[2] == mRect[2]
            && rect[3] == mRect[3])
            return;
        System.arraycopy(rect, 0, mRect, 0, 4);
        mPlaced = true;
        mList.setTranslationX(rect[0]);
        mList.setTranslationY(rect[1]);
        if (getVisibility() != View.VISIBLE)
            setVisibility(View.VISIBLE);
        invalidate();
    }

    /** The frame's rect in this view's coordinates, where a dragged chip brings its bar back. */
    void setFrameRect(@NonNull int[] rect) {
        System.arraycopy(rect, 0, mFrameRect, 0, 4);
    }

    /** Copies the column's rect into {@code out}; false before it is placed. */
    boolean rect(@NonNull int[] out) {
        if (!mPlaced)
            return false;
        System.arraycopy(mRect, 0, out, 0, 4);
        return true;
    }

    /** The column is lit while a bar held over it would be hidden by letting go. */
    void setTarget(boolean target) {
        if (mTarget == target)
            return;
        mTarget = target;
        invalidate();
    }

    /** The column's foot kept free for the hint (#343): its height in px. */
    int hintReservePx() {
        return Math.round(HINT_RESERVE_DP * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (!mPlaced)
            return;
        mDraw.set(mRect[0], mRect[1], mRect[2], mRect[3]);
        canvas.drawRoundRect(mDraw, mRadius, mRadius, mFill);
        if (mTarget)
            canvas.drawRoundRect(mDraw, mRadius, mRadius, mStroke);
    }

    private static int labelOf(@NonNull TrayItem item) {
        switch (item) {
            case STATUS_BAR: return R.string.editor_bar_status;
            case PINNED_APPS: return R.string.editor_bar_apps;
            case AZ_INDEX: return R.string.editor_bar_az;
            case EXTRA_KEYS: return R.string.editor_bar_extra_keys;
            default: return R.string.editor_bar_keyboard;
        }
    }

    /** A chip: a tap brings the bar back; dragged onto the frame it does, anywhere else it springs home. */
    private final class ChipTouch implements OnTouchListener {
        @NonNull private final TrayItem mItem;
        private float mDownX;
        private float mDownY;
        private boolean mDragging;

        ChipTouch(@NonNull TrayItem item) {
            mItem = item;
        }

        @Override
        public boolean onTouch(View chip, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDownX = event.getRawX();
                    mDownY = event.getRawY();
                    mDragging = false;
                    chip.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - mDownX;
                    float dy = event.getRawY() - mDownY;
                    float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    if (!mDragging && Math.abs(dx) + Math.abs(dy) > slop)
                        mDragging = true;
                    if (mDragging) {
                        chip.setTranslationX(dx);
                        chip.setTranslationY(dy);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    chip.getParent().requestDisallowInterceptTouchEvent(false);
                    boolean restore = !mDragging || overFrame(event.getRawX(), event.getRawY());
                    chip.setTranslationX(0f);
                    chip.setTranslationY(0f);
                    mDragging = false;
                    if (restore)
                        mListener.onRestore(mItem);
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    chip.getParent().requestDisallowInterceptTouchEvent(false);
                    chip.setTranslationX(0f);
                    chip.setTranslationY(0f);
                    mDragging = false;
                    return true;
                default:
                    return true;
            }
        }
    }

    private boolean overFrame(float rawX, float rawY) {
        getLocationOnScreen(mOnScreen);
        float x = rawX - mOnScreen[0];
        float y = rawY - mOnScreen[1];
        return x >= mFrameRect[0] && x <= mFrameRect[2] && y >= mFrameRect[1]
            && y <= mFrameRect[3];
    }
}
