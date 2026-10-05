package com.termux.app.firstrun;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Build;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiPlatformCaps;
import com.termux.ai.TaiSettings;
import com.termux.ai.TaiSpeechModels;
import com.termux.ai.TaiTierPolicy;
import com.termux.app.notice.AppNotice;
import com.termux.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Puts the "What runs on this phone" card on screen, remembers that it was shown, and queues what
 * was ticked. The card is a modal dialog hosting {@link TaiWelcomeCardView}, so the same code
 * serves the home screen (after the tour, or on the next idle home screen) and the On-device AI
 * settings entry; the decisions are {@link TaiWelcomeCard}'s.
 */
public final class TaiWelcomeCardHost {
    private TaiWelcomeCardHost() {}

    private static final String PREFS = "tai_welcome_card";
    private static final String KEY_SHOWN = "shown";
    private static final String KEY_WIFI_ONLY = "wifi_only";
    /** The window the default Whisper artifact (and the size the card shows) is. */
    private static final int WHISPER_WINDOW_SECONDS = 10;

    @Nullable private static Dialog sShowing;

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Whether the card has been raised once already (Later and Download both count). */
    public static boolean wasShown(@NonNull Context context) {
        try {
            return prefs(context).getBoolean(KEY_SHOWN, false);
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static void markShown(@NonNull Context context) {
        prefs(context).edit().putBoolean(KEY_SHOWN, true).apply();
    }

    /** The Wi-Fi-only switch's last position; on until the user turns it off. */
    public static boolean wifiOnly(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_WIFI_ONLY, true);
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
        List<TaiWelcomeCard.Row> rows = TaiWelcomeCard.rows(env, installed, catalogSizes());
        if (rows.isEmpty()) {
            if (onClosed != null) onClosed.run();
            return;
        }
        if (remember) markShown(activity);

        TaiWelcomeCardView view = new TaiWelcomeCardView(activity);
        view.setWifiOnly(wifiOnly(activity));
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
                prefs(activity).edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply();
                // The download layer has no per-download network rule (it resumes paused work on an
                // unmetered network but starts anything at once), so the switch is honoured here:
                // on a metered connection nothing starts and the card stays up.
                if (wifiOnly && isMetered(activity)) {
                    AppNotice.show(activity, R.string.tai_welcome_need_wifi);
                    return;
                }
                queue(activity, TaiWelcomeCard.downloadOrder(rows, tickedRowIds, catalogSizes()));
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

    private static boolean isMetered(@NonNull Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return manager != null && manager.isActiveNetworkMetered();
    }

    @NonNull
    private static TaiWelcomeCard.Sizes catalogSizes() {
        return modelId -> {
            TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(modelId);
            return entry == null ? 0L : entry.sizeBytes;
        };
    }

    /**
     * Queues the models through the same calls the Model Centre uses, in the order given (smallest
     * first). Off the main thread: starting a download writes its record. Ticking leaves every
     * function on Automatic, so the downloaded model becomes the pick.
     */
    private static void queue(@NonNull Context context, @NonNull List<String> modelIds) {
        Context app = context.getApplicationContext();
        Thread thread = new Thread(() -> {
            boolean noSpeechYet = TaiSpeechModels.installed(new TaiModelStore(app)).isEmpty();
            Set<String> startedSpeech = new HashSet<>();
            for (String id : modelIds) {
                try {
                    TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(id);
                    boolean speech = entry != null
                        && entry.capabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
                    if (!speech) {
                        TaiManager.getInstance(app).downloadCatalogModel(id);
                    } else if (noSpeechYet && startedSpeech.isEmpty() && noPendingVoiceChoice(app)) {
                        // The first speech model on the phone becomes the one voice input uses
                        // when it lands, as in the Model Centre.
                        TaiSpeechModels.startDownload(app, id, WHISPER_WINDOW_SECONDS);
                    } else {
                        TaiManager.getInstance(app).downloadSpeechModel(id, WHISPER_WINDOW_SECONDS);
                    }
                    if (speech) startedSpeech.add(id);
                } catch (Exception ignored) {
                    // One failed start must not stop the rest; its row shows in the Model Centre.
                }
            }
        }, "tai-welcome-download");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean noPendingVoiceChoice(@NonNull Context context) {
        String pending = new TaiSettings(context).getSttPendingDownloadJson();
        return pending == null || pending.isEmpty();
    }
}
