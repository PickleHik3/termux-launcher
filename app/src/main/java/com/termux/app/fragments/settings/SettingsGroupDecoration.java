package com.termux.app.fragments.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.fragments.settings.SettingsRowGroups.Kind;
import com.termux.app.fragments.settings.SettingsRowGroups.Shape;

/**
 * Draws a settings page's grouped tonal cards: the rows of one section share a
 * {@code colorSurfaceContainerHigh} container with rounded outer corners and a
 * {@code colorOutlineVariant} hairline between rows. Where each row sits comes from
 * {@link SettingsRowGroups}; this class only insets the rows, draws the containers under them
 * and clips a row's press ripple to its rounded corners.
 *
 * <p>Rows keep their own (transparent) backgrounds, so exempt layouts that own their look sit
 * inside the container unchanged. Colours resolve from the list's theme, which carries the
 * launcher's scheme chrome when it is on.
 */
public final class SettingsGroupDecoration extends RecyclerView.ItemDecoration {

    static final float CORNER_RADIUS_DP = 24f;
    static final float HAIRLINE_DP = 1f;

    private final int mGutter;
    private final int mLeadingSpace;
    private final int mHairline;
    /** The hairline stops short of the container's sides by the rows' own content padding. */
    private final int mHairlineInset;
    private final float mRadius;
    private final int mFillColor;
    private final int mLineColor;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLine = new Paint();
    private final Path mPath = new Path();
    private final RectF mRect = new RectF();
    private final float[] mRadii = new float[8];
    private final GroupOutline mOutlineSolo;
    private final GroupOutline mOutlineFirst;
    private final GroupOutline mOutlineLast;
    private final AdapterRows mRows = new AdapterRows();

    @Nullable private RecyclerView mList;
    @Nullable private RecyclerView.Adapter<?> mObserved;
    private boolean mRefreshPosted;
    private final Runnable mRefresh = () -> {
        mRefreshPosted = false;
        if (mList != null && !mList.isComputingLayout()) mList.invalidateItemDecorations();
    };
    /** Rows coming or going change their neighbours' corners; content changes do not. */
    private final RecyclerView.AdapterDataObserver mObserver = new RecyclerView.AdapterDataObserver() {
        @Override public void onChanged() { postRefresh(); }
        @Override public void onItemRangeInserted(int positionStart, int itemCount) { postRefresh(); }
        @Override public void onItemRangeRemoved(int positionStart, int itemCount) { postRefresh(); }
        @Override public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) { postRefresh(); }
    };

    private SettingsGroupDecoration(@NonNull RecyclerView list) {
        Context context = list.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        mGutter = context.getResources().getDimensionPixelSize(R.dimen.settings_gutter);
        mLeadingSpace = context.getResources().getDimensionPixelSize(R.dimen.settings_space_m);
        mHairlineInset = mGutter;
        mHairline = Math.max(1, Math.round(HAIRLINE_DP * density));
        mRadius = CORNER_RADIUS_DP * density;
        mFillColor = MaterialColors.getColor(list, com.google.android.material.R.attr.colorSurfaceContainerHigh);
        mLineColor = MaterialColors.getColor(list, com.google.android.material.R.attr.colorOutlineVariant);
        mOutlineSolo = new GroupOutline(true, true, mRadius);
        mOutlineFirst = new GroupOutline(true, false, mRadius);
        mOutlineLast = new GroupOutline(false, true, mRadius);
    }

    /** Gives the list its group containers, once. */
    public static void attach(@NonNull RecyclerView list) {
        for (int i = 0; i < list.getItemDecorationCount(); i++) {
            if (list.getItemDecorationAt(i) instanceof SettingsGroupDecoration) return;
        }
        SettingsGroupDecoration decoration = new SettingsGroupDecoration(list);
        decoration.mList = list;
        list.addItemDecoration(decoration);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        outRect.setEmpty();
        observe(parent.getAdapter());
        int position = parent.getChildAdapterPosition(view);
        Shape shape = shapeOf(view, position);
        applyClip(view, shape);
        if (!shape.grouped()) return;
        outRect.left = mGutter;
        outRect.right = mGutter;
        if (!shape.opens()) {
            outRect.top = mHairline;
        } else if (position != RecyclerView.NO_POSITION && SettingsRowGroups.needsLeadingSpace(mRows, position)) {
            outRect.top = mLeadingSpace;
        }
    }

    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        observe(parent.getAdapter());
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            Shape shape = shapeOf(child, parent.getChildAdapterPosition(child));
            if (!shape.grouped()) continue;
            float dy = child.getTranslationY();
            float left = child.getLeft();
            float right = child.getRight();
            float top = child.getTop() + dy - (shape.opens() ? 0 : mHairline);
            float bottom = child.getBottom() + dy;
            // A row fading in or out takes its stretch of the container with it.
            int alpha = Math.round(255 * child.getAlpha());
            mFill.setColor(mFillColor);
            mFill.setAlpha(Math.round(Color.alpha(mFillColor) * alpha / 255f));
            mLine.setColor(mLineColor);
            mLine.setAlpha(Math.round(Color.alpha(mLineColor) * alpha / 255f));
            float topRadius = shape.opens() ? mRadius : 0f;
            float bottomRadius = shape.closes() ? mRadius : 0f;
            mRadii[0] = mRadii[1] = mRadii[2] = mRadii[3] = topRadius;
            mRadii[4] = mRadii[5] = mRadii[6] = mRadii[7] = bottomRadius;
            mRect.set(left, top, right, bottom);
            mPath.reset();
            mPath.addRoundRect(mRect, mRadii, Path.Direction.CW);
            canvas.drawPath(mPath, mFill);
            if (!shape.opens()) {
                canvas.drawRect(left + mHairlineInset, top, right - mHairlineInset, top + mHairline, mLine);
            }
        }
    }

    @NonNull
    private Shape shapeOf(@NonNull View view, int position) {
        if (position == RecyclerView.NO_POSITION) {
            // Animating out: keep the place it had.
            Object last = view.getTag(R.id.settings_group_shape);
            return last instanceof Shape ? (Shape) last : Shape.NONE;
        }
        Shape shape = SettingsRowGroups.shapeAt(mRows, position);
        if (view.getTag(R.id.settings_group_shape) != shape) view.setTag(R.id.settings_group_shape, shape);
        return shape;
    }

    /** Clips the row (its press ripple, chiefly) to the container's rounded corners. */
    private void applyClip(@NonNull View view, @NonNull Shape shape) {
        ViewOutlineProvider wanted;
        switch (shape) {
            case SOLO: wanted = mOutlineSolo; break;
            case FIRST: wanted = mOutlineFirst; break;
            case LAST: wanted = mOutlineLast; break;
            default: wanted = null; break;
        }
        ViewOutlineProvider current = view.getOutlineProvider();
        if (wanted == null) {
            // Only undo what this decoration set: a card row keeps its own outline.
            if (current instanceof GroupOutline) {
                view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                view.setClipToOutline(false);
            }
            return;
        }
        if (current == wanted) return;
        view.setOutlineProvider(wanted);
        view.setClipToOutline(true);
    }

    private void observe(@Nullable RecyclerView.Adapter<?> adapter) {
        mRows.adapter = adapter;
        if (adapter == mObserved) return;
        if (mObserved != null) mObserved.unregisterAdapterDataObserver(mObserver);
        mObserved = adapter;
        if (adapter != null) adapter.registerAdapterDataObserver(mObserver);
    }

    private void postRefresh() {
        if (mRefreshPosted || mList == null) return;
        mRefreshPosted = true;
        mList.post(mRefresh);
    }

    /** The preference at an adapter position, through the section filter when there is one. */
    @VisibleForTesting
    @Nullable
    static Preference itemAt(@Nullable RecyclerView.Adapter<?> adapter, int position) {
        if (adapter == null || position < 0 || position >= adapter.getItemCount()) return null;
        if (adapter instanceof SettingsSectionChips.FilteringAdapter) {
            return ((SettingsSectionChips.FilteringAdapter) adapter).getItem(position);
        }
        if (adapter instanceof PreferenceGroupAdapter) {
            return ((PreferenceGroupAdapter) adapter).getItem(position);
        }
        return null;
    }

    private static final class AdapterRows implements SettingsRowGroups.Rows {
        @Nullable RecyclerView.Adapter<?> adapter;

        @Override public int count() {
            return adapter == null ? 0 : adapter.getItemCount();
        }

        @NonNull @Override public Kind kindAt(int position) {
            Preference preference = itemAt(adapter, position);
            return preference == null ? Kind.STANDALONE : SettingsRowGroups.kindOf(preference);
        }

        @Nullable @Override public Object groupAt(int position) {
            Preference preference = itemAt(adapter, position);
            return preference == null ? null : preference.getParent();
        }
    }

    /**
     * A rounded outline for a row at the top, bottom or both of its group. Outlines clip only to
     * a rect or one uniform round rect, so a top row's rect runs a radius past its bottom edge
     * (and a bottom row's past its top) to round just the outer pair of corners.
     */
    static final class GroupOutline extends ViewOutlineProvider {
        private final boolean mTop;
        private final boolean mBottom;
        private final float mRadius;

        GroupOutline(boolean top, boolean bottom, float radius) {
            mTop = top;
            mBottom = bottom;
            mRadius = radius;
        }

        @Override
        public void getOutline(@NonNull View view, @NonNull Outline outline) {
            int width = view.getWidth();
            int height = view.getHeight();
            int overhang = (int) Math.ceil(mRadius);
            int top = mTop ? 0 : -overhang;
            int bottom = mBottom ? height : height + overhang;
            outline.setRoundRect(0, top, width, bottom, mRadius);
        }
    }
}
