package com.termux.app.statusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.terminal.TerminalWindowBar.WindowItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The window list as a stack of chips, for the status bar standing in a column.
 *
 * <p>The row's pills carry a whole label; a column that is a finger wide cannot, so a chip here is
 * the window's number with the label's first letter under it, and the marks the row shows after a
 * label — working, asking, finished — become the chip's own rim. The selected window's chip is
 * filled in the place's accent, exactly as its pill is on the row.
 */
public final class StatusBarWindowColumn extends ScrollView {

    public interface OnWindowSelectedListener {
        void onWindowSelected(int index);
    }

    private static final float CHIP_SIZE_DP = 26f;
    private static final float CHIP_GAP_DP = 4f;

    private final LinearLayout mStack;
    @NonNull private List<WindowItem> mItems = new ArrayList<>();
    private int mSelected = -1;
    private int mAccent;
    private float mChipRadiusPx = -1f;
    @Nullable private OnWindowSelectedListener mListener;

    public StatusBarWindowColumn(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(false);
        // The bar's own gesture owns a drag down the column: that is how the wall is paged there,
        // and a scroll container in the way would swallow it before the bar ever saw it.
        setNestedScrollingEnabled(false);
        mStack = new LinearLayout(context);
        mStack.setOrientation(LinearLayout.VERTICAL);
        mStack.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(mStack, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        mAccent = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
    }

    public void setListener(@Nullable OnWindowSelectedListener listener) { mListener = listener; }

    /** The place's colour, so the column reads as part of the place it belongs to. */
    public void setPlaceAccent(int accent) {
        if (mAccent == accent) return;
        mAccent = accent;
        rebuild();
    }

    /** The corner the bar's chips wear, shared with the badge and the lens icons. */
    public void setChipRadiusPx(float radiusPx) {
        if (mChipRadiusPx == radiusPx) return;
        mChipRadiusPx = radiusPx;
        rebuild();
    }

    /** The same list the row's pills are built from, and which one is on screen. */
    public void setWindows(@NonNull List<WindowItem> items, int selected) {
        mItems = new ArrayList<>(items);
        mSelected = selected;
        rebuild();
    }

    private void rebuild() {
        mStack.removeAllViews();
        int size = Math.round(CHIP_SIZE_DP * density());
        int gap = Math.round(CHIP_GAP_DP * density());
        for (int i = 0; i < mItems.size(); i++) {
            final int index = i;
            WindowItem item = mItems.get(i);
            boolean selected = i == mSelected;
            TextView chip = new TextView(getContext());
            chip.setText(chipText(item, i));
            chip.setGravity(Gravity.CENTER);
            chip.setMaxLines(1);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setContentDescription(item.spokenLabel);
            chip.setTextColor(selected
                ? MaterialColors.getColor(this, com.termux.shared.R.attr.termuxColorOnAccentContainer,
                    ContextCompat.getColor(getContext(), R.color.termux_on_surface))
                : ColorUtils.setAlphaComponent(markColor(item), 210));
            chip.setBackground(chipBackground(item, selected));
            chip.setOnClickListener(v -> {
                if (mListener != null) mListener.onWindowSelected(index);
            });
            // A raised font scale is what clips fixed chrome; the chip's one glyph shrinks to fit
            // its box rather than spilling out of it.
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(chip,
                8, 11, 1, TypedValue.COMPLEX_UNIT_SP);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.topMargin = i == 0 ? 0 : gap;
            mStack.addView(chip, params);
        }
    }

    /**
     * The window's number, which is what the user counts them by. A label that starts with a
     * letter of its own says more than the number alone, so it takes the chip instead.
     */
    @NonNull
    private String chipText(@NonNull WindowItem item, int index) {
        String label = item.label.trim();
        if (!label.isEmpty()) {
            char first = label.charAt(0);
            if (Character.isLetter(first)) {
                return String.valueOf(first).toUpperCase(Locale.getDefault());
            }
        }
        return String.valueOf(index + 1);
    }

    /** Working, asking and finished are the rim's colour here, as they are the pill's on a row. */
    private int markColor(@NonNull WindowItem item) {
        if (item.attention) {
            return MaterialColors.getColor(this, com.google.android.material.R.attr.colorError,
                ContextCompat.getColor(getContext(), R.color.termux_error));
        }
        if (item.busy || item.done) return mAccent;
        return MaterialColors.getColor(this,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            ContextCompat.getColor(getContext(), R.color.termux_on_surface_variant));
    }

    @NonNull
    private GradientDrawable chipBackground(@NonNull WindowItem item, boolean selected) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        float radius = mChipRadiusPx >= 0f ? mChipRadiusPx : 8f * density();
        shape.setCornerRadius(Math.min(radius, CHIP_SIZE_DP * density() / 2f));
        int mark = markColor(item);
        if (selected) {
            shape.setColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(mAccent, 74)));
            shape.setStroke(Math.round(density()), ColorUtils.setAlphaComponent(mAccent, 190));
        } else {
            shape.setColor(ColorStateList.valueOf(Color.TRANSPARENT));
            shape.setStroke(Math.round(density()),
                ColorUtils.setAlphaComponent(mark, item.busy || item.attention ? 170 : 70));
        }
        return shape;
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }
}
