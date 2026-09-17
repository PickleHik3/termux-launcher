package com.termux.app.editorshell;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The control column of one editor row, which declares its own width instead of taking whatever the
 * card had left.
 *
 * <p>This is the one place the shell's spine is enforced against a view tree. The row hands this
 * host the leftover width; the host measures itself at what {@link EditorShellMetrics} says the
 * control may actually use and reports that, so a wider card leaves air at the row's trailing edge
 * rather than a 892 dp track for a 41-position value. The controls inside are {@code match_parent},
 * so a toggle group's segments come out equal without either editor counting pixels.
 */
public class EditorShellControlHost extends FrameLayout {

    /** Zero for a track; otherwise the number of segments the group inside is showing. */
    private int mSegmentCount;

    public EditorShellControlHost(@NonNull Context context) {
        this(context, null);
    }

    public EditorShellControlHost(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * How many segments the group inside is showing, so each one comes out at the shell's segment
     * width. Set it after taking the unused slots off, or the widths are computed for slots that
     * are no longer there.
     */
    public void setSegmentCount(int count) {
        if (mSegmentCount == count)
            return;
        mSegmentCount = count;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int offered = MeasureSpec.getSize(widthMeasureSpec);
        float density = getResources().getDisplayMetrics().density;
        int wanted = mSegmentCount > 0
            ? EditorShellMetrics.segmentWidthPx(offered, mSegmentCount, density) * mSegmentCount
            : EditorShellMetrics.trackWidthPx(offered, density);
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(Math.max(0, Math.min(offered, wanted)),
                MeasureSpec.EXACTLY),
            heightMeasureSpec);
    }
}
