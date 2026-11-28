package com.termux.app.launcher.home;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.termux.R;
import com.termux.app.window.WindowLayer;
import com.termux.app.window.WindowLayers;

import java.util.ArrayList;
import java.util.List;

/**
 * The Home hold menu on screen ({@link HomeHoldMenu}): a small glass card in the SHEETS layer,
 * anchored at the press, wearing the sheets' glass from the glass service (the style's
 * {@link Style#glass}). A clear scrim takes a touch outside the card as "close";
 * Back closes it through the sheets' Back.
 */
public final class HomeHoldMenuView extends FrameLayout {

    /** The card's width and one row's height, dp. */
    static final float WIDTH_DP = 224f;
    private static final float ROW_DP = 48f;
    private static final float PAD_DP = 6f;
    private static final float ROW_SIDE_DP = 16f;
    private static final float ICON_DP = 20f;
    private static final float ICON_GAP_DP = 14f;
    private static final float GAP_DP = 8f;

    /** How the card is dressed: the sheets' glass, its corners and the ink its rows wear. */
    public interface Style {
        /** The glass for {@code anchor} at {@code radiusPx}, from the glass service; null for a plain fill. */
        @Nullable Drawable glass(float radiusPx, @NonNull View anchor);

        float cornerRadiusPx();

        int ink();
    }

    /** What the card asks of its owner. */
    public interface Listener {
        /** {@code row} was chosen; the card has closed. */
        void onRow(@NonNull HomeHoldMenu.Row row);
    }

    @NonNull private final Style mStyle;
    @NonNull private final Listener mListener;
    @NonNull private final LinearLayout mCard;
    private final float mDensity;
    @NonNull private final List<TextView> mRows = new ArrayList<>();

    @SuppressLint("ClickableViewAccessibility")
    public HomeHoldMenuView(@NonNull Context context, @NonNull Style style,
                            @NonNull Listener listener) {
        super(context);
        mStyle = style;
        mListener = listener;
        mDensity = context.getResources().getDisplayMetrics().density;
        mCard = new LinearLayout(context);
        mCard.setOrientation(LinearLayout.VERTICAL);
        int pad = px(PAD_DP);
        mCard.setPadding(0, pad, 0, pad);
        mCard.setClickable(true);
        int ink = style.ink();
        for (HomeHoldMenu.Row row : HomeHoldMenu.rows()) {
            TextView view = row(context, row, ink);
            mRows.add(view);
            mCard.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                px(ROW_DP)));
        }
        addView(mCard, new LayoutParams(px(WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.START));
        setContentDescription(context.getString(R.string.home_hold_menu_description));
        // A touch outside the card closes it, and goes no further.
        setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) close();
            return true;
        });
    }

    @NonNull
    private TextView row(@NonNull Context context, @NonNull HomeHoldMenu.Row row, int ink) {
        TextView view = new TextView(context);
        view.setText(labelOf(row));
        view.setTextColor(ink);
        view.setTextSize(15f);
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        int side = px(ROW_SIDE_DP);
        view.setPadding(side, 0, side, 0);
        Drawable icon = ContextCompat.getDrawable(context, iconOf(row));
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(ink);
            int size = px(ICON_DP);
            icon.setBounds(0, 0, size, size);
            view.setCompoundDrawablesRelative(icon, null, null, null);
            view.setCompoundDrawablePadding(px(ICON_GAP_DP));
        }
        view.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(ink, 0x33)),
            null, new android.graphics.drawable.ColorDrawable(0xFFFFFFFF)));
        view.setOnClickListener(v -> {
            close();
            mListener.onRow(row);
        });
        return view;
    }

    /** A row's words. */
    static int labelOf(@NonNull HomeHoldMenu.Row row) {
        switch (row) {
            case EDIT: return R.string.home_hold_menu_edit;
            case NEW_TERMINAL: return R.string.home_hold_menu_new_terminal;
            case WALLPAPER: return R.string.home_hold_menu_wallpaper;
            default: return R.string.home_hold_menu_settings;
        }
    }

    private static int iconOf(@NonNull HomeHoldMenu.Row row) {
        switch (row) {
            case EDIT: return R.drawable.ic_ms_palette;
            case NEW_TERMINAL: return R.drawable.ic_ms_terminal;
            case WALLPAPER: return R.drawable.ic_ms_wallpaper;
            default: return R.drawable.ic_ms_settings;
        }
    }

    /**
     * Opens the card in the SHEETS layer of {@code root}'s window, its corner at the press
     * ({@code rawX}, {@code rawY}, screen pixels). False when there is no sheets layer.
     */
    public boolean show(@NonNull View root, float rawX, float rawY) {
        ViewGroup host = getParent() instanceof ViewGroup ? (ViewGroup) getParent()
            : WindowLayers.host(id -> root.getRootView().findViewById(id), WindowLayer.SHEETS);
        if (host == null || host.getWidth() <= 0 || host.getHeight() <= 0) return false;
        int[] at = new int[2];
        host.getLocationOnScreen(at);
        int width = Math.min(px(WIDTH_DP), Math.max(0, host.getWidth() - 2 * px(GAP_DP)));
        mCard.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int[] place = HomeHoldMenu.place(rawX - at[0], rawY - at[1], width, mCard.getMeasuredHeight(),
            host.getWidth(), host.getHeight(), px(GAP_DP));
        LayoutParams params = (LayoutParams) mCard.getLayoutParams();
        params.width = width;
        params.leftMargin = place[0];
        params.topMargin = place[1];
        mCard.setLayoutParams(params);
        if (getParent() == null) {
            host.addView(this, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        }
        return true;
    }

    /** Whether the card is on screen. */
    public boolean isOpen() {
        return getParent() != null;
    }

    /** Takes the card off the screen; false when it was not on it. */
    public boolean close() {
        ViewGroup parent = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
        if (parent == null) return false;
        parent.removeView(this);
        return true;
    }

    /** The card, for tests: where it stands in its host. */
    @NonNull
    View card() {
        return mCard;
    }

    /** The rows' words in order, for tests. */
    @NonNull
    List<CharSequence> rowLabels() {
        List<CharSequence> labels = new ArrayList<>();
        for (TextView row : mRows) labels.add(row.getText());
        return labels;
    }

    /** Taps row {@code index}, for tests. */
    void clickRow(int index) {
        mRows.get(index).performClick();
    }

    /** The glass, dressed once the card has its size: the sheets' glass, through the glass service. */
    private void dress() {
        float radius = mStyle.cornerRadiusPx();
        Drawable glass = mStyle.glass(radius, mCard);
        if (glass == null) {
            GradientDrawable plain = new GradientDrawable();
            plain.setCornerRadius(radius);
            plain.setColor(ColorUtils.setAlphaComponent(0xFF1B1B1F, 0xE6));
            glass = plain;
        }
        mCard.setBackground(glass);
        mCard.setClipToOutline(true);
        mCard.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mCard.post(this::dress);
    }

    private int px(float dp) {
        return Math.round(dp * mDensity);
    }

    /** The card's background now, for tests. */
    @Nullable
    Drawable glass() {
        return mCard.getBackground();
    }
}
