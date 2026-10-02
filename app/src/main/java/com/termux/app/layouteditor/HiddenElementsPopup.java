package com.termux.app.layouteditor;

import android.content.ClipData;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.termux.app.fragments.settings.LayoutCanvasView;

import java.util.ArrayList;
import java.util.List;

/**
 * What the trash lists: a popup window of Material chips, one per hidden element, standing above
 * the trash icon. A chip is tapped to bring its element back where it left, or pulled out of the
 * popup onto the canvas with a system drag, which dismisses the popup and which the canvas turns
 * into its lift, hover placeholder and drop. The popup is a window of its own, so the bottom area
 * and the canvas's bounds clip nothing of it.
 */
final class HiddenElementsPopup {

    interface Callbacks {
        /** A chip was tapped: put its element back on the edge it was hidden from. */
        void onRestoreTapped(@NonNull LayoutCanvasView.Block block);
    }

    @NonNull private final LayoutCanvasView mCanvas;
    @NonNull private final View mAnchor;
    @NonNull private final Callbacks mCallbacks;
    @Nullable private PopupWindow mWindow;
    @Nullable private ChipGroup mChips;

    HiddenElementsPopup(@NonNull LayoutCanvasView canvas, @NonNull View anchor,
                        @NonNull Callbacks callbacks) {
        mCanvas = canvas;
        mAnchor = anchor;
        mCallbacks = callbacks;
    }

    boolean isShowing() {
        return mWindow != null && mWindow.isShowing();
    }

    /** Opens the popup on the elements hidden now, or closes it when it is already up. */
    void toggle() {
        if (isShowing()) {
            dismiss();
            return;
        }
        List<LayoutCanvasView.Block> hidden = mCanvas.hiddenElements();
        if (hidden.isEmpty() || !mAnchor.isAttachedToWindow())
            return;
        Context context = mAnchor.getContext();
        ChipGroup chips = new ChipGroup(context);
        int pad = context.getResources().getDimensionPixelSize(
            com.google.android.material.R.dimen.mtrl_card_spacing);
        FrameLayout content = new FrameLayout(context);
        content.setPadding(pad, pad, pad, pad);
        content.addView(chips, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        PopupWindow window = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, true);
        window.setBackgroundDrawable(EditorM3.surface(mAnchor,
            com.google.android.material.R.attr.shapeAppearanceCornerMedium,
            com.google.android.material.R.attr.colorSurfaceContainer));
        window.setOutsideTouchable(true);
        window.setElevation(context.getResources().getDimension(
            com.google.android.material.R.dimen.m3_comp_menu_container_elevation));
        window.setOnDismissListener(() -> {
            if (mWindow == window) {
                mWindow = null;
                mChips = null;
            }
        });
        mWindow = window;
        mChips = chips;
        fill(chips, hidden);

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        // The popup belongs to the phone frame on the canvas: at most the frame's width, and
        // centred over the trash icon, kept inside the frame.
        int[] canvasAt = new int[2];
        mCanvas.getLocationInWindow(canvasAt);
        android.graphics.RectF frame = mCanvas.frameRect();
        int frameLeft = canvasAt[0] + Math.round(frame.left);
        int frameRight = canvasAt[0] + Math.round(frame.right);
        if (frameRight - frameLeft <= 2 * pad) {
            frameLeft = 0;
            frameRight = metrics.widthPixels;
        }
        content.measure(View.MeasureSpec.makeMeasureSpec(frameRight - frameLeft,
            View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0,
            View.MeasureSpec.UNSPECIFIED));
        int width = content.getMeasuredWidth();
        int height = content.getMeasuredHeight();
        int[] at = new int[2];
        mAnchor.getLocationInWindow(at);
        int left = leftCentredOn(at[0] + mAnchor.getWidth() / 2, width, frameLeft, frameRight);
        window.setWidth(width);
        window.showAsDropDown(mAnchor, left - at[0], -(mAnchor.getHeight() + height));
    }

    /**
     * Where a popup {@code width} wide stands so its centre is on {@code centerX}, moved only as
     * far as keeps it between {@code minLeft} and {@code maxRight}; the left edge when it is wider
     * than that span.
     */
    static int leftCentredOn(int centerX, int width, int minLeft, int maxRight) {
        int left = centerX - width / 2;
        left = Math.min(left, maxRight - width);
        return Math.max(left, minLeft);
    }

    /** The hidden elements changed: restate the chips, or close once nothing is left to list. */
    void update() {
        if (!isShowing() || mChips == null)
            return;
        List<LayoutCanvasView.Block> hidden = mCanvas.hiddenElements();
        if (hidden.isEmpty()) {
            dismiss();
            return;
        }
        fill(mChips, hidden);
    }

    void dismiss() {
        PopupWindow window = mWindow;
        mWindow = null;
        mChips = null;
        if (window != null && window.isShowing())
            window.dismiss();
    }

    private void fill(@NonNull ChipGroup group, @NonNull List<LayoutCanvasView.Block> hidden) {
        group.removeAllViews();
        for (LayoutCanvasView.Block block : new ArrayList<>(hidden))
            group.addView(chipFor(group.getContext(), block));
    }

    @NonNull
    private Chip chipFor(@NonNull Context context, @NonNull LayoutCanvasView.Block block) {
        Chip chip = new Chip(context);
        chip.setText(mCanvas.chipName(block));
        chip.setChipIcon(AppCompatResources.getDrawable(context,
            LayoutCanvasView.chipGlyph(block)));
        chip.setChipIconVisible(true);
        chip.setCheckable(false);
        chip.setOnClickListener(tapped -> mCallbacks.onRestoreTapped(block));
        final int slop = ViewConfiguration.get(context).getScaledTouchSlop();
        chip.setOnTouchListener(new View.OnTouchListener() {
            private float mDownX;
            private float mDownY;
            private boolean mDragging;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        mDownX = event.getRawX();
                        mDownY = event.getRawY();
                        mDragging = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if (!mDragging && Math.hypot(event.getRawX() - mDownX,
                            event.getRawY() - mDownY) > slop) {
                            mDragging = startDrag(view, block);
                        }
                        return mDragging;
                    default:
                        return mDragging;
                }
            }
        });
        return chip;
    }

    /** Pulls the chip out of the popup: the system drag the canvas accepts, the popup gone. */
    private boolean startDrag(@NonNull View chip, @NonNull LayoutCanvasView.Block block) {
        ClipData clip = ClipData.newPlainText(mCanvas.chipName(block), mCanvas.chipName(block));
        boolean started = chip.startDragAndDrop(clip, new View.DragShadowBuilder(chip), block, 0);
        if (started)
            dismiss();
        return started;
    }
}
