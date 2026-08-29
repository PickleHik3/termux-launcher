package com.termux.app.onboarding;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.BuildConfig;
import com.termux.R;

/**
 * The one-shot notice for the VAJ edition ({@code io.vaj.tl}): this is the demo build, its
 * packages come from a repository that is updated when it is updated, and anyone who wants a
 * repository they can rely on should be on the Nix or Termux edition.
 *
 * <p>It was a deprecation notice until v0.2.37-vaj, when the edition stopped being frozen to
 * security fixes. Its subject never changed with that — the packages, not the app — so the notice
 * stayed and only the framing moved from "this edition is going away" to "this edition is the
 * demo".
 *
 * <p>The notice shows once per notice version — that is, once after the update that introduces
 * it — and the "do not show again" box suppresses every later version of it too. The edition
 * check is deliberate rather than implicit in the branch: this file must stay inert if it ever
 * reaches another edition through a merge.
 */
public final class VajDemoNotice {

    /** The package family this notice belongs to; every other edition ignores it. */
    @VisibleForTesting
    static final String VAJ_APPLICATION_ID = "io.vaj.tl";

    /**
     * Deliberately still the deprecation notice's store name. Someone who ticked "do not show
     * again" opted out of being told about this repository, and renaming the file would ask them
     * all over again.
     */
    @VisibleForTesting
    static final String PREFS_NAME = "termux_vaj_deprecation_notice";
    @VisibleForTesting
    static final String KEY_SHOWN_VERSION = "notice_shown_version";
    @VisibleForTesting
    static final String KEY_SUPPRESSED = "notice_suppressed";

    /**
     * Bump only to re-notify users who dismissed an earlier wording without opting out. 3 is the
     * demo wording; 1 and 2 were the deprecation ones.
     */
    @VisibleForTesting
    static final int NOTICE_VERSION = 3;

    private static final String EDITIONS_GUIDE_URL =
        "https://github.com/PickleHik3/termux-launcher/blob/main/docs/en/VAJ_To_Nix_Migration.md";

    private VajDemoNotice() {}

    /** Shows the notice if this build is the VAJ edition and the user has not seen or muted it. */
    public static void showIfNeeded(@NonNull Activity activity) {
        showIfNeeded(activity, BuildConfig.APPLICATION_ID);
    }

    @VisibleForTesting
    static void showIfNeeded(@NonNull Activity activity, @NonNull String applicationId) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        SharedPreferences preferences =
            activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!shouldShow(preferences, applicationId)) return;
        show(activity, preferences);
    }

    @VisibleForTesting
    static boolean shouldShow(@NonNull SharedPreferences preferences,
                              @NonNull String applicationId) {
        if (!VAJ_APPLICATION_ID.equals(applicationId)) return false;
        if (preferences.getBoolean(KEY_SUPPRESSED, false)) return false;
        return preferences.getInt(KEY_SHOWN_VERSION, 0) < NOTICE_VERSION;
    }

    private static void show(@NonNull Activity activity,
                             @NonNull SharedPreferences preferences) {
        LinearLayout frame = dialogFrame(activity);
        CheckBox suppress = new CheckBox(activity);
        suppress.setText(R.string.vaj_demo_notice_do_not_show_again);
        frame.addView(suppress, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.vaj_demo_notice_title)
            .setMessage(R.string.vaj_demo_notice_message)
            .setView(frame)
            .setNegativeButton(R.string.vaj_demo_notice_dismiss, null)
            .setPositiveButton(R.string.vaj_demo_notice_open_guide,
                (dialog, which) -> openEditionsGuide(activity))
            // The box is honoured however the dialog closes — button, back press or outside tap —
            // so a user who ticks it and taps away is not asked again.
            .setOnDismissListener(dialog -> {
                if (suppress.isChecked()) {
                    preferences.edit().putBoolean(KEY_SUPPRESSED, true).apply();
                }
            })
            .show();

        // Marked as shown on display, not on dismissal: the point is one notice per update, and a
        // user who swipes the app away mid-dialog has still seen it.
        preferences.edit().putInt(KEY_SHOWN_VERSION, NOTICE_VERSION).apply();
    }

    private static void openEditionsGuide(@NonNull Activity activity) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(EDITIONS_GUIDE_URL));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, EDITIONS_GUIDE_URL, Toast.LENGTH_LONG).show();
        }
    }

    /** A dialog body with the inset a Material dialog expects around its content. */
    @NonNull
    private static LinearLayout dialogFrame(@NonNull Context context) {
        int density = Math.round(context.getResources().getDisplayMetrics().density);
        LinearLayout frame = new LinearLayout(context);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setPadding(24 * density, 8 * density, 24 * density, 0);
        return frame;
    }
}
