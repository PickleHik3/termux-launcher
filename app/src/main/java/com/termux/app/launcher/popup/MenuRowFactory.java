package com.termux.app.launcher.popup;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

/**
 * Builds and styles the generic rows every text menu in the launcher is made of, plus the shell
 * they live in. Consumers that need bespoke content (an icon grid, a stack of notification cards)
 * build that view themselves and hand it to {@link AnchoredMenu} through
 * {@link MenuSpec#content}; this class exists so no consumer re-derives a row's metrics, glyph
 * tinting, highlight fill or press feedback.
 */
public final class MenuRowFactory {

    @NonNull private final Context context;
    @NonNull private final AnchoredMenuTheme theme;

    public MenuRowFactory(@NonNull Context context, @NonNull AnchoredMenuTheme theme) {
        this.context = context;
        this.theme = theme;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** The vertical container a text menu's header and rows are added to. */
    @NonNull
    public LinearLayout newShell() {
        LinearLayout shell = new LinearLayout(context);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(3), dp(3), dp(3), dp(3));
        return shell;
    }

    /** A plain action row: no glyphs. */
    @NonNull
    public TextView addActionRow(@NonNull LinearLayout shell, @NonNull String title, int tintBase,
                                 @NonNull Runnable action) {
        return addActionRow(shell, title, 0, false, tintBase, action);
    }

    /**
     * An action row with an optional leading glyph and an optional trailing chevron. The chevron is
     * the affordance for a row that opens the side menu.
     */
    @NonNull
    public TextView addActionRow(@NonNull LinearLayout shell, @NonNull String title, int iconRes,
                                 boolean chevron, int tintBase, @NonNull Runnable action) {
        Drawable leading = iconRes != 0 ? loadMenuIcon(iconRes, dp(16), theme.textColor()) : null;
        Drawable trailing = chevron
            ? loadMenuIcon(chevronRes(), dp(13), (theme.textColor() & 0x00FFFFFF) | (0x9E << 24))
            : null;
        return addRow(shell, title, leading, trailing, iconRes != 0 || chevron, tintBase, action);
    }

    /** A single-select row: the trailing glyph is present exactly when the row is the current pick. */
    @NonNull
    public TextView addCheckableRow(@NonNull LinearLayout shell, @NonNull String title,
                                    int checkIconRes, boolean checked, int tintBase,
                                    @NonNull Runnable action) {
        Drawable trailing = checked ? loadMenuIcon(checkIconRes, dp(16), theme.textColor()) : null;
        return addRow(shell, title, null, trailing, checked, tintBase, action);
    }

    @NonNull
    private TextView addRow(@NonNull LinearLayout shell, @NonNull String title,
                            @Nullable Drawable leading, @Nullable Drawable trailing,
                            boolean hasGlyphs, int tintBase, @NonNull Runnable action) {
        TextView actionRow = new TextView(context);
        actionRow.setText(title);
        actionRow.setTextColor(theme.textColor());
        actionRow.setTextSize(12f);
        actionRow.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        actionRow.setPadding(dp(8), dp(7), dp(8), dp(7));
        actionRow.setClickable(true);
        if (hasGlyphs) {
            actionRow.setCompoundDrawablesRelative(leading, null, trailing, null);
            actionRow.setCompoundDrawablePadding(dp(10));
        }
        styleRow(actionRow, false, tintBase);
        actionRow.setOnClickListener(v -> runWithFeedback(actionRow, action));
        shell.addView(actionRow, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return actionRow;
    }

    /** Hairline group separator between two groups of rows. */
    public void addDivider(@NonNull LinearLayout shell) {
        View divider = new View(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.setMargins(dp(8), dp(5), dp(8), dp(4));
        divider.setLayoutParams(lp);
        divider.setBackgroundColor((theme.textColor() & 0x00FFFFFF) | (0x24 << 24));
        shell.addView(divider);
    }

    /** Loads a menu glyph, tints it to {@code color} (alpha respected) and bounds it to {@code sizePx}. */
    @Nullable
    public Drawable loadMenuIcon(int res, int sizePx, int color) {
        Drawable base = ContextCompat.getDrawable(context, res);
        if (base == null) {
            return null;
        }
        Drawable d = DrawableCompat.wrap(base.mutate());
        DrawableCompat.setTint(d, color);
        d.setBounds(0, 0, sizePx, sizePx);
        return d;
    }

    /** Paints a row as idle or as the one under the finger. */
    public void styleRow(@NonNull TextView row, boolean highlighted, int tintBase) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        if (highlighted) {
            int fill = blendColors((0x8C << 24) | (tintBase & 0x00FFFFFF), 0x66FFFFFF, 0.35f);
            bg.setColor(fill);
            bg.setStroke(dp(1), blendColors(0x99FFFFFF, (0xFF << 24) | (tintBase & 0x00FFFFFF), 0.5f));
            row.setTextColor(theme.selectedTextColor());
        } else {
            bg.setColor(0x00000000);
            bg.setStroke(0, 0x00000000);
            row.setTextColor(theme.textColor());
        }
        row.setBackground(bg);
    }

    /** Tap feedback: a short press-in, then the action, then a release back to rest. */
    public void runWithFeedback(@NonNull TextView actionRow, @NonNull Runnable action) {
        actionRow.animate().cancel();
        actionRow.setPivotX(actionRow.getWidth() * 0.5f);
        actionRow.setPivotY(actionRow.getHeight() * 0.5f);
        actionRow.animate()
            .alpha(0.68f)
            .scaleX(0.985f)
            .scaleY(0.985f)
            .setDuration(70L)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> {
                if (actionRow.isAttachedToWindow()) {
                    actionRow.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(110L)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                }
                action.run();
            })
            .start();
    }

    private int chevronRes() {
        return com.termux.R.drawable.ic_dock_menu_chevron;
    }

    static int blendColors(int from, int to, float ratio) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        int fromA = (from >> 24) & 0xFF;
        int fromR = (from >> 16) & 0xFF;
        int fromG = (from >> 8) & 0xFF;
        int fromB = from & 0xFF;
        int toA = (to >> 24) & 0xFF;
        int toR = (to >> 16) & 0xFF;
        int toG = (to >> 8) & 0xFF;
        int toB = to & 0xFF;
        int outA = Math.round(fromA + ((toA - fromA) * clamped));
        int outR = Math.round(fromR + ((toR - fromR) * clamped));
        int outG = Math.round(fromG + ((toG - fromG) * clamped));
        int outB = Math.round(fromB + ((toB - fromB) * clamped));
        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }
}
