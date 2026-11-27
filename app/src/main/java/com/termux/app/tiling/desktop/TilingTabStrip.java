package com.termux.app.tiling.desktop;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.termux.R;
import com.termux.app.tiling.WindowKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A tabbed Dwindle tile's tab strip (#420): one thin glass line along the tile's top, a tab per
 * window with its icon (a Linux app's own where it has one, else its kind's) and its short name,
 * the shown one lit. A tap on a tab shows that window. Too many tabs for their names show icons
 * only, and too many for their icons scroll sideways ({@link TabStripFit}). Its tabs are rebuilt
 * only when what they show or how they fit changed; nothing here draws per frame.
 */
@SuppressLint("ViewConstructor")
final class TilingTabStrip extends HorizontalScrollView {

    /** What a tap on a tab asks for. */
    interface Listener {
        void onTabPressed(long windowId);
    }

    /** One tab: its window, the kind it is, the name it shows and its own icon, if it has one. */
    static final class Tab {
        final long windowId;
        @NonNull final WindowKind kind;
        @NonNull final String name;
        @Nullable final Bitmap icon;

        Tab(long windowId, @NonNull WindowKind kind, @NonNull String name, @Nullable Bitmap icon) {
            this.windowId = windowId;
            this.kind = kind;
            this.name = name;
            this.icon = icon;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Tab)) return false;
            Tab other = (Tab) o;
            return windowId == other.windowId && kind == other.kind && name.equals(other.name)
                && icon == other.icon;
        }

        @Override
        public int hashCode() {
            return Objects.hash(windowId, kind, name, System.identityHashCode(icon));
        }
    }

    /** A tab's text size, in sp. */
    private static final float TEXT_SP = 12f;
    /** A tab's icon, in dp. */
    private static final float ICON_DP = 14f;
    /** The glass's alpha over the Workspace's ground. */
    private static final int GLASS_ALPHA = 0x38;

    @NonNull private final Listener mListener;
    @NonNull private final LinearLayout mRow;
    private final float mDensity;
    private final int mInk;
    private final int mAccent;
    @NonNull private final List<Tab> mTabs = new ArrayList<>();
    @Nullable private TabStripFit.Mode mMode;
    private long mShown = Long.MIN_VALUE;

    TilingTabStrip(@NonNull Context context, @NonNull Listener listener, int accent) {
        super(context);
        mListener = listener;
        mDensity = getResources().getDisplayMetrics().density;
        mInk = 0xE6FFFFFF;
        mAccent = accent;
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFillViewport(true);
        float radius = TilingDesktopView.CORNER_DP * mDensity;
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadii(new float[] {radius, radius, radius, radius, 0f, 0f, 0f, 0f});
        glass.setColor(ColorUtils.setAlphaComponent(Color.WHITE, GLASS_ALPHA));
        setBackground(glass);
        int pad = Math.round(4f * mDensity);
        setPadding(pad, 0, pad, 0);
        mRow = new LinearLayout(context);
        mRow.setOrientation(LinearLayout.HORIZONTAL);
        addView(mRow, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
    }

    /**
     * Shows {@code tabs} in a strip {@code widthPx} wide with {@code shown}'s lit; unchanged tabs
     * are left as they are.
     */
    void setTabs(@NonNull List<Tab> tabs, long shown, float widthPx) {
        int pad = getPaddingLeft() + getPaddingRight();
        TabStripFit.Mode mode = TabStripFit.of(widthPx - pad, tabs.size(), mDensity);
        if (!tabs.equals(mTabs) || mode != mMode) {
            mTabs.clear();
            mTabs.addAll(tabs);
            mMode = mode;
            mRow.removeAllViews();
            int iconTab = Math.round(TabStripFit.ICON_TAB_DP * mDensity);
            for (Tab tab : tabs) {
                LinearLayout.LayoutParams params = mode == TabStripFit.Mode.SCROLLING
                    ? new LinearLayout.LayoutParams(iconTab, LinearLayout.LayoutParams.MATCH_PARENT)
                    : new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                mRow.addView(tabView(tab, mode == TabStripFit.Mode.NAMED), params);
            }
            mShown = Long.MIN_VALUE;
        }
        if (mShown == shown) return;
        mShown = shown;
        View lit = null;
        for (int i = 0; i < mRow.getChildCount() && i < mTabs.size(); i++) {
            boolean on = mTabs.get(i).windowId == shown;
            light((TextView) mRow.getChildAt(i), on);
            if (on) lit = mRow.getChildAt(i);
        }
        if (mode == TabStripFit.Mode.SCROLLING && lit != null) {
            // Once the row is laid out, bring the shown tab into view (one scroll, not an animation).
            View target = lit;
            post(() -> {
                int left = target.getLeft();
                int right = target.getRight();
                int x = getScrollX();
                int width = getWidth() - getPaddingLeft() - getPaddingRight();
                if (left < x) scrollTo(left, 0);
                else if (right > x + width) scrollTo(right - width, 0);
            });
        } else if (getScrollX() != 0) {
            scrollTo(0, 0);
        }
    }

    /** The row of tabs, in order. */
    @NonNull
    LinearLayout row() {
        return mRow;
    }

    @NonNull
    private TextView tabView(@NonNull Tab tab, boolean named) {
        TextView view = new TextView(getContext());
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setGravity(named ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
        int pad = Math.round((named ? 6f : 2f) * mDensity);
        view.setPadding(pad, 0, pad, 0);
        view.setCompoundDrawablePadding(named ? Math.round(4f * mDensity) : 0);
        view.setText(named ? tab.name : "");
        view.setContentDescription(tab.name);
        Drawable icon = iconOf(tab);
        if (icon != null) {
            int size = Math.round(ICON_DP * mDensity);
            icon.setBounds(0, 0, size, size);
            if (named) view.setCompoundDrawablesRelative(icon, null, null, null);
            else view.setCompoundDrawablesRelative(null, icon, null, null);
        }
        if (!named) {
            // The icon alone stands in the middle of its tab.
            int top = Math.max(0, Math.round((TilingDesktopView.TAB_STRIP_DP - ICON_DP) / 2f * mDensity));
            view.setPadding(pad, top, pad, 0);
            view.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        }
        view.setOnClickListener(v -> mListener.onTabPressed(tab.windowId));
        return view;
    }

    private void light(@NonNull TextView view, boolean lit) {
        int color = lit ? mAccent : ColorUtils.setAlphaComponent(mInk, 170);
        view.setTextColor(color);
        view.setSelected(lit);
        for (Drawable d : view.getCompoundDrawablesRelative()) if (d != null) d.setTint(color);
        if (lit) {
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(8f * mDensity);
            pill.setColor(ColorUtils.setAlphaComponent(mAccent, 0x33));
            view.setBackground(pill);
        } else {
            view.setBackground(null);
        }
    }

    @Nullable
    private Drawable iconOf(@NonNull Tab tab) {
        if (tab.icon != null) return new BitmapDrawable(getResources(), tab.icon);
        Drawable d = ContextCompat.getDrawable(getContext(), kindIcon(tab.kind));
        return d == null ? null : d.mutate();
    }

    @DrawableRes
    private static int kindIcon(@NonNull WindowKind kind) {
        switch (kind) {
            case LINUX_APP: return R.drawable.ic_distro_linux;
            case DISPLAY: return R.drawable.ic_ms_desktop_windows;
            case ASSISTANT: return R.drawable.ic_ms_auto_awesome;
            case SHELL:
            default: return R.drawable.ic_ms_terminal;
        }
    }
}
