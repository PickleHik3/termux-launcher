package com.termux.app.tiling.desktop;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
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
 * window with its kind's icon and its short name, the shown one lit. A tap on a tab shows that
 * window. Its tabs are rebuilt only when what they show changed; nothing here draws per frame.
 */
@SuppressLint("ViewConstructor")
final class TilingTabStrip extends LinearLayout {

    /** What a tap on a tab asks for. */
    interface Listener {
        void onTabPressed(long windowId);
    }

    /** One tab: its window, the kind it is, and the name it shows. */
    static final class Tab {
        final long windowId;
        @NonNull final WindowKind kind;
        @NonNull final String name;

        Tab(long windowId, @NonNull WindowKind kind, @NonNull String name) {
            this.windowId = windowId;
            this.kind = kind;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Tab)) return false;
            Tab other = (Tab) o;
            return windowId == other.windowId && kind == other.kind && name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(windowId, kind, name);
        }
    }

    /** A tab's text size, in sp. */
    private static final float TEXT_SP = 12f;
    /** A tab's icon, in dp. */
    private static final float ICON_DP = 14f;
    /** The glass's alpha over the Workspace's ground. */
    private static final int GLASS_ALPHA = 0x38;

    @NonNull private final Listener mListener;
    private final float mDensity;
    private final int mInk;
    private final int mAccent;
    @NonNull private final List<Tab> mTabs = new ArrayList<>();
    private long mShown = Long.MIN_VALUE;

    TilingTabStrip(@NonNull Context context, @NonNull Listener listener, int accent) {
        super(context);
        mListener = listener;
        mDensity = getResources().getDisplayMetrics().density;
        mInk = 0xE6FFFFFF;
        mAccent = accent;
        setOrientation(HORIZONTAL);
        float radius = TilingDesktopView.CORNER_DP * mDensity;
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadii(new float[] {radius, radius, radius, radius, 0f, 0f, 0f, 0f});
        glass.setColor(ColorUtils.setAlphaComponent(Color.WHITE, GLASS_ALPHA));
        setBackground(glass);
        int pad = Math.round(4f * mDensity);
        setPadding(pad, 0, pad, 0);
    }

    /** Shows {@code tabs} with {@code shown}'s lit; unchanged tabs are left as they are. */
    void setTabs(@NonNull List<Tab> tabs, long shown) {
        if (!tabs.equals(mTabs)) {
            mTabs.clear();
            mTabs.addAll(tabs);
            removeAllViews();
            for (Tab tab : tabs) addView(tabView(tab), new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
            mShown = Long.MIN_VALUE;
        }
        if (mShown == shown) return;
        mShown = shown;
        for (int i = 0; i < getChildCount() && i < mTabs.size(); i++) {
            light((TextView) getChildAt(i), mTabs.get(i).windowId == shown);
        }
    }

    @NonNull
    private TextView tabView(@NonNull Tab tab) {
        TextView view = new TextView(getContext());
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
        int pad = Math.round(6f * mDensity);
        view.setPadding(pad, 0, pad, 0);
        view.setCompoundDrawablePadding(Math.round(4f * mDensity));
        view.setText(tab.name);
        view.setContentDescription(tab.name);
        Drawable icon = icon(iconOf(tab.kind));
        if (icon != null) {
            int size = Math.round(ICON_DP * mDensity);
            icon.setBounds(0, 0, size, size);
            view.setCompoundDrawablesRelative(icon, null, null, null);
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
    private Drawable icon(@DrawableRes int res) {
        Drawable d = ContextCompat.getDrawable(getContext(), res);
        return d == null ? null : d.mutate();
    }

    @DrawableRes
    private static int iconOf(@NonNull WindowKind kind) {
        switch (kind) {
            case LINUX_APP: return R.drawable.ic_distro_linux;
            case DISPLAY: return R.drawable.ic_ms_desktop_windows;
            case ASSISTANT: return R.drawable.ic_ms_auto_awesome;
            case SHELL:
            default: return R.drawable.ic_ms_terminal;
        }
    }
}
