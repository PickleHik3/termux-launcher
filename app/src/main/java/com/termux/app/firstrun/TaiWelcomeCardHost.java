package com.termux.app.firstrun;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiPlatformCaps;
import com.termux.ai.TaiTierPolicy;
import com.termux.app.notice.AppNotice;
import com.termux.R;

import java.util.List;
import java.util.Set;

/**
 * Puts the "What runs on this phone" card on screen, remembers that it was shown, and queues what
 * was ticked. The card is a modal dialog hosting {@link TaiWelcomeCardView}, so the same code
 * serves the home screen (after the tour, or on the next idle home screen) and the On-device AI
 * settings entry; the decisions are {@link TaiWelcomeCard}'s, and the queue is
 * {@link TaiWelcomeDownloads}', which the first-run setup sheet shares.
 */
public final class TaiWelcomeCardHost {
    private TaiWelcomeCardHost() {}

    private static final String KEY_SHOWN = "shown";

    @Nullable private static Dialog sShowing;

    /** Whether the card has been raised once already (Later and Download both count). */
    public static boolean wasShown(@NonNull Context context) {
        try {
            return TaiWelcomeDownloads.prefs(context).getBoolean(KEY_SHOWN, false);
        } catch (RuntimeException e) {
            return true;
        }
    }

    /**
     * Records the card as raised, so the home screen never raises it on its own. The first-run
     * setup sheet asks the same question, so it calls this too; Settings keeps the way back in.
     */
    public static void markShown(@NonNull Context context) {
        TaiWelcomeDownloads.prefs(context).edit().putBoolean(KEY_SHOWN, true).apply();
    }

    public static boolean isShowing() {
        return sShowing != null && sShowing.isShowing();
    }

    /** Whether this phone has anything to put on the card. */
    public static boolean hasRows(@NonNull Context context) {
        return TaiWelcomeCard.hasRows(TaiTierPolicy.Env.forDevice(context));
    }

    /**
     * The probe that settles the GPU path, started early so the card renders with it known. Cached
     * per app version, so calling it at every start is cheap.
     */
    public static void probeEarly(@NonNull Context context) {
        TaiPlatformCaps.probeAsync(context, null);
    }

    /**
     * Raises the card. {@code remember} marks it as shown, which is how the automatic raise never
     * nags; the settings entry passes false and can open it any number of times.
     *
     * @param onClosed run after the card closes, whichever button closed it; may be null
     */
    public static void show(@NonNull Activity activity, boolean remember, @Nullable Runnable onClosed) {
        if (activity.isFinishing() || activity.isDestroyed() || isShowing()) return;
        TaiTierPolicy.Env env = TaiTierPolicy.Env.forDevice(activity);
        Set<String> installed = new TaiModelStore(activity).getInstalledUserModels().keySet();
        TaiWelcomeCard.Sizes sizes = TaiWelcomeDownloads.catalogSizes();
        List<TaiWelcomeCard.Row> rows = TaiWelcomeCard.rows(env, installed, sizes);
        if (rows.isEmpty()) {
            if (onClosed != null) onClosed.run();
            return;
        }
        if (remember) markShown(activity);

        TaiWelcomeCardView view = new TaiWelcomeCardView(activity);
        view.setWifiOnly(TaiWelcomeDownloads.wifiOnly(activity));
        String chip = TaiWelcomeCard.chipName(
            Build.VERSION.SDK_INT >= 31 ? Build.SOC_MANUFACTURER : null,
            Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : null, Build.HARDWARE);
        view.bind(TaiWelcomeCard.header(env, chip, Build.VERSION.RELEASE), rows,
            TaiWelcomeCard.showsModelCentreLine(env), activity.getFilesDir().getUsableSpace());

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setCancelable(false);
        Runnable close = () -> {
            dialog.dismiss();
            sShowing = null;
            if (onClosed != null) onClosed.run();
        };
        view.setCallbacks(new TaiWelcomeCardView.Callbacks() {
            @Override
            public void onWelcomeLater() {
                close.run();
            }

            @Override
            public void onWelcomeDownload(@NonNull Set<String> tickedRowIds, boolean wifiOnly) {
                TaiWelcomeDownloads.setWifiOnly(activity, wifiOnly);
                // On a metered connection with Wi-Fi only on, nothing starts and the card stays up.
                TaiWelcomeDownloads.Start started = TaiWelcomeDownloads.start(activity,
                    TaiWelcomeCard.downloadOrder(rows, tickedRowIds, sizes), wifiOnly);
                if (started == TaiWelcomeDownloads.Start.NEEDS_WIFI) {
                    AppNotice.show(activity, R.string.tai_welcome_need_wifi);
                    return;
                }
                AppNotice.show(activity, R.string.tai_welcome_started);
                close.run();
            }
        });
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = WindowInsetsCompat.toWindowInsetsCompat(insets, v)
                .getInsets(WindowInsetsCompat.Type.systemBars());
            view.setSystemBarInsets(bars.top, bars.bottom);
            return insets;
        });
        dialog.setContentView(view, TaiWelcomeCardView.buildLayoutParams());
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        sShowing = dialog;
        dialog.setOnDismissListener(d -> {
            if (sShowing == dialog) sShowing = null;
        });
        dialog.show();
        view.animateIn();
    }
}
