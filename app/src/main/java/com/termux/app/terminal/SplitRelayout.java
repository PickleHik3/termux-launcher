package com.termux.app.terminal;

import android.os.Trace;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

/**
 * Moves a split's divider without laying out the window. A split's own size does not change when
 * its two weights do, so the new weights are written into the children's existing LayoutParams
 * and only that container is measured and laid out again, at the bounds it already has. The usual
 * {@code setLayoutParams} would request a layout that climbs to the root and re-lays the whole
 * window (the pager, the dock, the keyboard) on every drag move.
 */
final class SplitRelayout {

    private SplitRelayout() {}

    /**
     * Writes {@code weightA}/{@code weightB} onto the first and third child of {@code split} and
     * re-lays it out in place. When the split cannot be re-laid in place (never laid out, no
     * size, or a layout already pending on it or above it) the weights are set the ordinary way,
     * so the next window layout picks them up and nothing is lost.
     */
    static void applyWeights(@NonNull LinearLayout split, float weightA, float weightB) {
        View a = split.getChildAt(0);
        View b = split.getChildAt(2);
        LinearLayout.LayoutParams pa = (LinearLayout.LayoutParams) a.getLayoutParams();
        LinearLayout.LayoutParams pb = (LinearLayout.LayoutParams) b.getLayoutParams();
        if (pa.weight == weightA && pb.weight == weightB) return;
        pa.weight = weightA;
        pb.weight = weightB;
        if (!canRelayInPlace(split)) {
            a.setLayoutParams(pa);
            b.setLayoutParams(pb);
            return;
        }
        Trace.beginSection("Pane.splitRelayout");
        try {
            int left = split.getLeft();
            int top = split.getTop();
            int width = split.getWidth();
            int height = split.getHeight();
            split.forceLayout();
            split.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            split.layout(left, top, left + width, top + height);
            split.invalidate();
        } finally {
            Trace.endSection();
        }
    }

    private static boolean canRelayInPlace(@NonNull LinearLayout split) {
        if (!split.isAttachedToWindow() || split.getWidth() <= 0 || split.getHeight() <= 0) {
            return false;
        }
        if (split.isLayoutRequested()) return false;
        // A request anywhere above sets the root's flag too, and then a full pass is coming.
        View root = split.getRootView();
        if (root != null && root.isLayoutRequested()) return false;
        return true;
    }
}
