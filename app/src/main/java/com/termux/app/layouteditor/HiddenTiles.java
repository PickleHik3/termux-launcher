package com.termux.app.layouteditor;

import android.content.ClipData;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import com.termux.R;
import com.termux.app.fragments.settings.LayoutCanvasView;

import java.util.ArrayList;
import java.util.List;

/**
 * The hidden-element tiles (layout editor v2, DECISIONS item 3): one Material chip per hidden
 * element, inline in the editor's sheet, which eye-off opens and closes. It replaces the popup
 * window the trash used to open.
 *
 * <p>Each tile carries the restore arrow ({@code ic_layout_restore}) and the element's name. A tap
 * brings the element back to the edge it left; a drag out of the sheet onto the canvas starts the
 * system drag the canvas turns into its lift, hover placeholder and drop, restoring it at the edge
 * it is dropped on. The canvas and the sheet are in one window, so the element rides as the drag's
 * local state as well as in its clip label.</p>
 */
final class HiddenTiles {

    interface Callbacks {
        /** A tile was tapped: put its element back on the edge it was hidden from. */
        void onRestoreTapped(@NonNull LayoutCanvasView.Block block);
    }

    @NonNull private final LayoutCanvasView mCanvas;
    @NonNull private final ChipGroup mGroup;
    @NonNull private final Callbacks mCallbacks;
    /** What the tiles were last filled for, so they are rebuilt only when that changes. */
    @NonNull private List<LayoutCanvasView.Block> mShown = new ArrayList<>();

    HiddenTiles(@NonNull LayoutCanvasView canvas, @NonNull ChipGroup group,
                @NonNull Callbacks callbacks) {
        mCanvas = canvas;
        mGroup = group;
        mCallbacks = callbacks;
    }

    /** The tiles restated from the elements hidden now, in the canvas's order. */
    void update() {
        List<LayoutCanvasView.Block> hidden = mCanvas.hiddenElements();
        if (hidden.equals(mShown) && mGroup.getChildCount() == hidden.size())
            return;
        mShown = new ArrayList<>(hidden);
        mGroup.removeAllViews();
        for (LayoutCanvasView.Block block : mShown)
            mGroup.addView(tileFor(mGroup.getContext(), block));
    }

    /** The elements the tiles stand for, in order. */
    @NonNull
    @VisibleForTesting
    List<LayoutCanvasView.Block> shown() {
        return new ArrayList<>(mShown);
    }

    /** Whether there is anything to show. */
    boolean isEmpty() {
        return mCanvas.hiddenElements().isEmpty();
    }

    @NonNull
    private Chip tileFor(@NonNull Context context, @NonNull LayoutCanvasView.Block block) {
        Chip chip = new Chip(context);
        String name = mCanvas.chipName(block);
        chip.setText(name);
        chip.setChipIcon(AppCompatResources.getDrawable(context, R.drawable.ic_layout_restore));
        chip.setChipIconVisible(true);
        chip.setChipIconSize(context.getResources().getDisplayMetrics().density * 24f);
        chip.setChipIconTint(ColorStateList.valueOf(EditorM3.color(mGroup,
            androidx.appcompat.R.attr.colorPrimary)));
        chip.setCheckable(false);
        chip.setEnsureMinTouchTargetSize(true);
        chip.setContentDescription(context.getString(R.string.layout_editor_restore_tile, name));
        chip.setTag(block);
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
                        // Up out of the sheet is a drag onto the canvas; sideways is the row's
                        // own pan when it is wider than the sheet.
                        if (!mDragging && mDownY - event.getRawY() > slop
                            && Math.abs(event.getRawY() - mDownY)
                                > Math.abs(event.getRawX() - mDownX)) {
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

    /** Pulls a tile out of the sheet: the drag the canvas accepts. */
    private boolean startDrag(@NonNull View chip, @NonNull LayoutCanvasView.Block block) {
        ClipData clip = ClipData.newPlainText(
            LayoutCanvasView.HIDDEN_DRAG_LABEL_PREFIX + block.name(), mCanvas.chipName(block));
        return chip.startDragAndDrop(clip, new View.DragShadowBuilder(chip), block, 0);
    }

    /** The tile standing for {@code block}, or null; for a test to tap. */
    @VisibleForTesting
    Chip tileOf(@NonNull LayoutCanvasView.Block block) {
        for (int i = 0; i < mGroup.getChildCount(); i++) {
            View child = mGroup.getChildAt(i);
            if (child instanceof Chip && child.getTag() == block)
                return (Chip) child;
        }
        return null;
    }
}
