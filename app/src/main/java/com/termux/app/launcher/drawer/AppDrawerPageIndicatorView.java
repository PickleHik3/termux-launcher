package com.termux.app.launcher.drawer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/** Allocation-free centred page dots. */
public final class AppDrawerPageIndicatorView extends View {

    private static final float DOT_RADIUS_DP = 3f;
    private static final float DOT_GAP_DP = 8f;
    private static final float MIN_RADIUS_PX = 1f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mPreferredRadiusPx;
    private final float mPreferredGapPx;
    private final int mActiveColor;
    private final int mInactiveColor;
    private int mPageCount;
    private int mSelectedPage;

    public AppDrawerPageIndicatorView(@NonNull Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        mPreferredRadiusPx = DOT_RADIUS_DP * density;
        mPreferredGapPx = DOT_GAP_DP * density;
        mActiveColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSurface,
            ContextCompat.getColor(context, R.color.termux_primary));
        mInactiveColor = Color.argb(92, Color.red(mActiveColor), Color.green(mActiveColor),
            Color.blue(mActiveColor));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setVisibility(GONE);
    }

    public void setPageCount(int pageCount) {
        mPageCount = Math.max(0, pageCount);
        mSelectedPage = AppDrawerPageModel.clampPage(mSelectedPage, mPageCount);
        setVisibility(mPageCount <= 1 ? GONE : VISIBLE);
        updateDescription();
        invalidate();
    }

    public int getPageCount() {
        return mPageCount;
    }

    public void setSelectedPage(int page) {
        int selected = AppDrawerPageModel.clampPage(page, mPageCount);
        if (selected == mSelectedPage) return;
        mSelectedPage = selected;
        updateDescription();
        invalidate();
    }

    public int getSelectedPage() {
        return mSelectedPage;
    }

    private void updateDescription() {
        setContentDescription(mPageCount == 0 ? null
            : getResources().getString(R.string.app_drawer_page_description,
                mSelectedPage + 1, mPageCount));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mPageCount <= 1 || getWidth() <= 0 || getHeight() <= 0) return;
        float radius = mPreferredRadiusPx;
        float gap = mPreferredGapPx;
        float required = (2f * radius * mPageCount) + (gap * (mPageCount - 1));
        if (required > getWidth()) {
            float scale = getWidth() / required;
            radius = Math.max(MIN_RADIUS_PX, radius * scale);
            gap = Math.max(0f, (getWidth() - (2f * radius * mPageCount))
                / Math.max(1, mPageCount - 1));
        }
        float total = (2f * radius * mPageCount) + (gap * (mPageCount - 1));
        float x = (getWidth() - total) * 0.5f + radius;
        float y = getHeight() * 0.5f;
        for (int i = 0; i < mPageCount; i++) {
            int page = pageForVisualDot(i, mPageCount, getLayoutDirection());
            mPaint.setColor(page == mSelectedPage ? mActiveColor : mInactiveColor);
            canvas.drawCircle(x, y, radius, mPaint);
            x += (2f * radius) + gap;
        }
    }

    static int pageForVisualDot(int visualDot, int pageCount, int layoutDirection) {
        return layoutDirection == LAYOUT_DIRECTION_RTL ? pageCount - 1 - visualDot : visualDot;
    }
}
