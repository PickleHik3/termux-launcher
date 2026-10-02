package com.termux.app.layouteditor;

import android.content.ClipData;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Gravity;
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
import com.termux.R;
import com.termux.app.fragments.settings.LayoutCanvasView;

import java.util.ArrayList;
import java.util.List;

/**
 * What the trash lists: a popup window of Material chips, one per hidden element, standing above
 * the bottom sheet, its end edge on the trash icon's. A chip is tapped to bring its element back where it left, or pulled out of the
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
        // The popup stands above the whole bottom sheet, never over it (the sheet's top row has
        // Done and Undo): its bottom edge is 8dp over the sheet's top, its end edge on the trash's
        // end edge, kept 16dp inside the screen.
        int margin = Math.round(16f * metrics.density);
        int gap = Math.round(8f * metrics.density);
        content.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels - 2 * margin,
            View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0,
            View.MeasureSpec.UNSPECIFIED));
        int width = content.getMeasuredWidth();
        int height = content.getMeasuredHeight();
        int[] at = new int[2];
        mAnchor.getLocationOnScreen(at);
        int[] sheetAt = new int[2];
        View sheet = sheetOf(mAnchor);
        sheet.getLocationOnScreen(sheetAt);
        int left = leftEndAlignedOn(at[0] + mAnchor.getWidth(), width, margin,
            metrics.widthPixels - margin);
        int top = topAbove(sheetAt[1], height, gap);
        window.setWidth(width);
        window.showAtLocation(mAnchor, Gravity.NO_GRAVITY, left, top);
    }

    /** The bottom sheet the anchor stands in (the anchor itself when it is not in one). */
    @NonNull
    private static View sheetOf(@NonNull View anchor) {
        android.view.ViewParent parent = anchor.getParent();
        while (parent instanceof View) {
            View view = (View) parent;
            if (view.getId() == R.id.appearance_editor_panel)
                return view;
            parent = view.getParent();
        }
        return anchor;
    }

    /**
     * Where a popup {@code width} wide stands so its end edge is on {@code endX}, moved only as
     * far as keeps it between {@code minLeft} and {@code maxRight}; the left edge when it is wider
     * than that span.
     */
    static int leftEndAlignedOn(int endX, int width, int minLeft, int maxRight) {
        int left = Math.min(endX - width, maxRight - width);
        return Math.max(left, minLeft);
    }

    /** The top of a popup {@code height} tall whose bottom edge is {@code gap} above {@code sheetTop}. */
    static int topAbove(int sheetTop, int height, int gap) {
        return sheetTop - gap - height;
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
        // The chip lives in this popup's window and the canvas in the activity's. A drag reaches
        // another window only when it is global, and local state never crosses windows, so the
        // element rides in the clip's label, which every window sees from the first event.
        ClipData clip = ClipData.newPlainText(
            LayoutCanvasView.HIDDEN_DRAG_LABEL_PREFIX + block.name(), mCanvas.chipName(block));
        boolean started = chip.startDragAndDrop(clip, new View.DragShadowBuilder(chip), block,
            View.DRAG_FLAG_GLOBAL);
        if (started)
            dismiss();
        return started;
    }
}
