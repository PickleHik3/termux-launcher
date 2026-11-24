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
import androidx.annotation.Nullable;

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
 * <p>The editor's one hint (#343) stands at the column's foot and grows upward over the room
 * the chips leave.
 */
final class HiddenColumnView extends FrameLayout {

    /** What the column does for its host. */
    interface Listener {
        /** {@code item}'s chip was tapped or dropped on the frame: show the bar again. */
        void onRestore(@NonNull TrayItem item);

        /** {@code item}'s chip was tapped: its bar is selected, and offers the way back (#341). */
        void onSelect(@NonNull TrayItem item);
    }

    static final int WIDTH_DP = 88;
    static final int GAP_DP = 8;
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
    @NonNull private final TextView mHint;
    private static final float HINT_SP = 11f;
    private final int mPrimary;
    @Nullable private String mHintTitle;
    @Nullable private String mHintBody;
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
        mHint = new TextView(context);
        mHint.setTextColor(onSurface);
        mPrimary = primary;
        mHint.setGravity(Gravity.START | Gravity.BOTTOM);
        mHint.setIncludeFontPadding(false);
        mHint.setPadding(pad, 0, pad, pad);
        mHint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, HINT_SP);
        mHint.setLineSpacing(0f, 1.35f);
        mHint.setMaxLines(Integer.MAX_VALUE);
        addView(mHint, new LayoutParams(Math.round(WIDTH_DP * density),
            LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));
        // The hint's height is known once laid out; resting on the foot is a translation only.
        mHint.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> anchorHint());
        setVisibility(View.GONE);
    }

    /**
     * The hint at the column's foot: {@code title} (accent, own line) over {@code body}. The text
     * is set, and so laid out, only when it changed.
     */
    void setHint(@Nullable String title, @NonNull String body) {
        if (body.equals(mHintBody) && java.util.Objects.equals(title, mHintTitle))
            return;
        mHintTitle = title;
        mHintBody = body;
        android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder();
        if (title != null) {
            text.append(title);
            text.setSpan(new android.text.style.ForegroundColorSpan(mPrimary), 0, title.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0,
                title.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.append('\n');
        }
        text.append(body);
        mHint.setText(text);
    }

    /** Rests the hint's bottom on the column's foot (translation; no layout is requested). */
    private void anchorHint() {
        if (!mPlaced)
            return;
        mHint.setTranslationX(mRect[0]);
        mHint.setTranslationY(mRect[3] - mHint.getHeight());
    }

    /** Lines the hint may take: the room above the foot that the chips leave free. */
    private void limitHint(int chipCount) {
        float density = getResources().getDisplayMetrics().density;
        int room = HiddenColumnGeometry.hintRoomPx(mRect[3] - mRect[1], chipCount,
            Math.round(CHIP_DP * density), Math.round(PAD_DP * density));
        int lineHeight = Math.max(1, mHint.getLineHeight());
        int lines = Math.max(1, room / lineHeight);
        if (mHint.getMaxLines() != lines)
            mHint.setMaxLines(lines);
    }

    /** The chips for {@code items}, rebuilt only when the list changed. */
    void setItems(@NonNull List<TrayItem> items) {
        if (items.equals(mItems))
            return;
        mItems.clear();
        mItems.addAll(items);
        if (mPlaced)
            limitHint(mItems.size());
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
        limitHint(mItems.size());
        anchorHint();
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
            case PAGE_BAR: return R.string.editor_bar_page_bar;
            default: return R.string.editor_bar_keyboard;
        }
    }

    /** A chip: a tap selects its bar; dragged onto the frame it brings the bar back, anywhere else it springs home. */
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
                    boolean tap = !mDragging;
                    boolean restore = !tap && overFrame(event.getRawX(), event.getRawY());
                    chip.setTranslationX(0f);
                    chip.setTranslationY(0f);
                    mDragging = false;
                    if (tap)
                        mListener.onSelect(mItem);
                    else if (restore)
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
