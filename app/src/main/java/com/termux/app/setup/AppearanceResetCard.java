package com.termux.app.setup;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.chrome.ShapeTokens;
import com.termux.app.surfaces.AppearanceReset;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The one card after an appearance reset on upgrade ({@link AppearanceReset}), in the setup cards'
 * sheet style: the line, Dismiss · Open Appearance. Shown by the launcher on a Home page; either
 * button settles it, so it shows once.
 */
public final class AppearanceResetCard {

    private AppearanceResetCard() {}

    /** Whether the card waits to be shown. */
    public static boolean isDue(@Nullable TermuxAppSharedPreferences prefs) {
        return prefs != null && AppearanceReset.isCardDue(prefs.getSharedPreferences());
    }

    /**
     * Shows the card over the launcher when it is due and not already up; the caller has checked
     * that a Home page is in front. Open Appearance runs {@code openEditor}.
     *
     * @return whether the card is up
     */
    public static boolean showIfDue(@NonNull Activity activity,
                                    @Nullable TermuxAppSharedPreferences prefs,
                                    @NonNull Runnable openEditor) {
        if (!isDue(prefs)) return false;
        ViewGroup content = com.termux.app.window.WindowLayers.host(activity, com.termux.app.window.WindowLayer.NOTICES);
        if (content == null) return false;
        if (content.findViewWithTag(AppearanceResetCard.class) != null) return true;
        Context context = activity;

        FrameLayout scrim = new FrameLayout(context);
        scrim.setTag(AppearanceResetCard.class);
        scrim.setBackgroundColor(Color.argb(96, 0, 0, 0));
        scrim.setClickable(true);

        LinearLayout sheet = new LinearLayout(context);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(ColorUtils.setAlphaComponent(SetupUi.surface(scrim), 255));
        float radius = SetupUi.dp(context, 26f);
        background.setCornerRadii(new float[] {radius, radius, radius, radius, 0f, 0f, 0f, 0f});
        sheet.setBackground(background);
        sheet.setElevation(ShapeTokens.elevationPx(context, 3));
        int side = SetupUi.dp(context, 20);
        sheet.setPadding(side, SetupUi.dp(context, 16), side, SetupUi.dp(context, 16));
        SetupUi.header(sheet, R.string.appearance_reset_card_title, R.string.appearance_reset_card_line);

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1f));
        MaterialButton dismiss = new MaterialButton(context, null,
            androidx.appcompat.R.attr.borderlessButtonStyle);
        dismiss.setText(R.string.appearance_reset_card_dismiss);
        dismiss.setAllCaps(false);
        footer.addView(dismiss, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        MaterialButton open = new MaterialButton(context);
        open.setText(R.string.appearance_reset_card_open);
        open.setAllCaps(false);
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        openParams.leftMargin = SetupUi.dp(context, 8);
        footer.addView(open, openParams);
        sheet.addView(footer, SetupUi.matchWrap(SetupUi.dp(context, 8)));

        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM);
        scrim.addView(sheet, sheetParams);
        scrim.setOnApplyWindowInsetsListener((v, insets) -> {
            sheet.setPadding(side, SetupUi.dp(context, 16), side,
                SetupUi.dp(context, 16) + insets.getSystemWindowInsetBottom());
            return insets;
        });

        dismiss.setOnClickListener(v -> {
            AppearanceReset.settleCard(prefs.getSharedPreferences());
            content.removeView(scrim);
        });
        open.setOnClickListener(v -> {
            AppearanceReset.settleCard(prefs.getSharedPreferences());
            content.removeView(scrim);
            openEditor.run();
        });
        content.addView(scrim, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.requestApplyInsets();
        return true;
    }
}
