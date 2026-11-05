package com.termux.app.chrome.wallpaper;

import android.content.Context;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A phone-proportioned frame for a scene thumbnail: as wide as it is given, as tall as the
 * screen's portrait aspect makes it (or at most {@link #setMaxHeightPx}, then narrower), with
 * rounded corners that clip what it holds.
 */
public final class LiveAspectFrame extends FrameLayout {

    private float mRatio = 2f;
    private int mMaxHeight = Integer.MAX_VALUE;

    public LiveAspectFrame(@NonNull Context context) {
        this(context, null);
    }

    public LiveAspectFrame(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float radius = 16f * context.getResources().getDisplayMetrics().density;
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
    }

    /** Height over width, e.g. 2.2 for a 1080x2400 screen. */
    public void setRatio(float heightOverWidth) {
        if (heightOverWidth <= 0f || heightOverWidth == mRatio) return;
        mRatio = heightOverWidth;
        requestLayout();
    }

    /** The tallest the frame gets; wider than the ratio allows then narrows to keep the aspect. */
    public void setMaxHeightPx(int px) {
        mMaxHeight = Math.max(1, px);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) w = 0;
        int h = Math.round(w * mRatio);
        if (h > mMaxHeight) {
            h = mMaxHeight;
            w = Math.round(h / mRatio);
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(Math.max(0, w), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(Math.max(0, h), MeasureSpec.EXACTLY));
    }
}
