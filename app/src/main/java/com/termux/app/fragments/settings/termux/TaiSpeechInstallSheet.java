package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiSpeechGpuAdvice;

/**
 * The small sheet Install opens for a Whisper model: size, language and window, each a segmented
 * choice with one plain line under it saying what the choice means. It moved here from the old
 * Speech model screen's download dialog (D5), minus the engine question: the Speech list already
 * has a row per engine. It starts on the row the person tapped, so an unchanged sheet installs
 * exactly that row.
 */
final class TaiSpeechInstallSheet {
    interface OnInstall {
        void install(@NonNull String modelId, int windowSeconds);
    }

    private TaiSpeechInstallSheet() {}

    static void show(@NonNull Context context, @NonNull String tappedModelId, int storedWindowSeconds,
                     long deviceMemoryBytes, @NonNull OnInstall onInstall) {
        BottomSheetDialog sheet = new BottomSheetDialog(context);
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * density);
        content.setPadding(pad, Math.round(18 * density), pad, Math.round(20 * density));

        boolean[] small = {TaiSpeechActions.isSmall(tappedModelId)};
        boolean[] english = {tappedModelId.endsWith("-en")};
        boolean[] gpu = {TaiSpeechActions.isGpuModel(tappedModelId)};
        TaiSpeechGpuAdvice.Advice advice = TaiSpeechGpuAdvice.forContext(context, TaiModelCatalog.WHISPER_BASE_GPU_ID);
        int[] window = {storedWindowSeconds == 5 ? 5 : 10};

        TextView title = new TextView(context);
        appearance(title, com.google.android.material.R.attr.textAppearanceTitleLarge);
        title.setTextColor(color(context, com.google.android.material.R.attr.colorOnSurface));
        title.setText(context.getString(R.string.tai_centre_sheet_title, context.getString(R.string.speech_model_engine_whisper)));
        content.addView(title);

        TextView licenseHint = hint(context);
        licenseHint.setMovementMethod(LinkMovementMethod.getInstance());
        content.addView(licenseHint);
        TextView sizeHint = hint(context);
        TextView windowHint = hint(context);
        MaterialButton install = new MaterialButton(context);
        List<View> cpuOnlyViews = new ArrayList<>();
        Runnable refresh = () -> {
            String id = gpu[0] ? TaiModelCatalog.WHISPER_BASE_GPU_ID : TaiSpeechActions.whisperCatalogId(small[0], english[0]);
            for (View view : cpuOnlyViews) view.setVisibility(gpu[0] ? View.GONE : View.VISIBLE);
            TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(id);
            long bytes = entry == null ? 0L : entry.withWindow(window[0]).sizeBytes;
            String hint = context.getString(gpu[0] ? R.string.speech_gpu_size_hint
                : small[0] ? R.string.speech_model_size_small_hint : R.string.speech_model_size_base_hint);
            if (gpu[0]) hint = hint + "\n" + TaiSpeechActions.gpuAdviceLine(context, advice, true);
            if (!gpu[0] && small[0] && deviceMemoryBytes > 0L && deviceMemoryBytes < TaiSpeechActions.SMALL_MIN_MEMORY_BYTES) {
                hint = hint + "\n" + context.getString(R.string.tai_centre_small_ram_note);
            }
            CharSequence license = TaiModelCentreRows.licenseFact(context, entry == null ? null : entry.license);
            licenseHint.setText(license);
            licenseHint.setVisibility(license.length() == 0 ? View.GONE : View.VISIBLE);
            sizeHint.setText(hint);
            windowHint.setText(window[0] == 5 ? R.string.speech_model_window_5_hint : R.string.speech_model_window_10_hint);
            install.setText(context.getString(R.string.tai_centre_sheet_install, TaiModelCentreRows.formatBytes(bytes)));
        };

        content.addView(label(context, R.string.speech_model_size_label));
        TaiSegmentedTabs size = tabs(context);
        size.setLabels(context.getString(R.string.tai_centre_sheet_size_base, estimate("whisper-acft-base")),
            context.getString(R.string.tai_centre_sheet_size_small, estimate("whisper-acft-small")),
            context.getString(R.string.tai_centre_sheet_size_gpu, estimate(TaiModelCatalog.WHISPER_BASE_GPU_ID)));
        size.select(gpu[0] ? 2 : small[0] ? 1 : 0, false);
        size.setOnSegmentSelectedListener(index -> {
            gpu[0] = index == 2;
            small[0] = index == 1;
            refresh.run();
        });
        content.addView(size, tabParams(density));
        content.addView(sizeHint);

        TextView languageLabel = label(context, R.string.speech_model_language_label);
        content.addView(languageLabel);
        cpuOnlyViews.add(languageLabel);
        TaiSegmentedTabs language = tabs(context);
        language.setLabels(context.getString(R.string.tai_centre_sheet_language_english),
            context.getString(R.string.tai_centre_sheet_language_many));
        language.select(english[0] ? 0 : 1, false);
        language.setOnSegmentSelectedListener(index -> {
            english[0] = index == 0;
            refresh.run();
        });
        content.addView(language, tabParams(density));
        cpuOnlyViews.add(language);

        TextView windowLabel = label(context, R.string.speech_model_window_label);
        content.addView(windowLabel);
        cpuOnlyViews.add(windowLabel);
        TaiSegmentedTabs windows = tabs(context);
        windows.setLabels(context.getString(R.string.tai_centre_sheet_window_10),
            context.getString(R.string.tai_centre_sheet_window_5));
        windows.select(window[0] == 5 ? 1 : 0, false);
        windows.setOnSegmentSelectedListener(index -> {
            window[0] = index == 1 ? 5 : 10;
            refresh.run();
        });
        content.addView(windows, tabParams(density));
        content.addView(windowHint);
        cpuOnlyViews.add(windows);
        cpuOnlyViews.add(windowHint);

        // A stock filled button: its shape, colour and text style are the theme's.
        LinearLayout.LayoutParams installParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        installParams.topMargin = Math.round(20 * density);
        content.addView(install, installParams);
        install.setOnClickListener(view -> {
            TaiMotion.tick(view);
            sheet.dismiss();
            // The GPU model has one 30 s graph; the stored window stays the preference for the others.
            onInstall.install(gpu[0] ? TaiModelCatalog.WHISPER_BASE_GPU_ID
                : TaiSpeechActions.whisperCatalogId(small[0], english[0]), window[0]);
        });
        refresh.run();

        sheet.setContentView(content);
        sheet.show();
    }

    @NonNull
    private static String estimate(@NonNull String modelId) {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(modelId);
        if (entry == null) return "?";
        return entry.sizeEstimate == null || entry.sizeEstimate.isEmpty()
            ? TaiModelCentreRows.formatBytes(entry.sizeBytes) : entry.sizeEstimate;
    }

    @NonNull
    private static TaiSegmentedTabs tabs(@NonNull Context context) {
        return new TaiSegmentedTabs(context);
    }

    @NonNull
    private static LinearLayout.LayoutParams tabParams(float density) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Math.round(8 * density);
        return params;
    }

    @NonNull
    private static TextView label(@NonNull Context context, int textRes) {
        TextView label = new TextView(context);
        label.setText(textRes);
        appearance(label, com.google.android.material.R.attr.textAppearanceTitleSmall);
        label.setTextColor(color(context, androidx.appcompat.R.attr.colorPrimary));
        label.setPadding(0, Math.round(18 * context.getResources().getDisplayMetrics().density), 0, 0);
        return label;
    }

    @NonNull
    private static TextView hint(@NonNull Context context) {
        TextView hint = new TextView(context);
        appearance(hint, com.google.android.material.R.attr.textAppearanceBodySmall);
        hint.setTextColor(color(context, com.google.android.material.R.attr.colorOnSurfaceVariant));
        hint.setPadding(0, Math.round(6 * context.getResources().getDisplayMetrics().density), 0, 0);
        hint.setVisibility(View.VISIBLE);
        return hint;
    }

    private static void appearance(@NonNull TextView view, int attr) {
        TypedValue value = new TypedValue();
        if (view.getContext().getTheme().resolveAttribute(attr, value, true)) view.setTextAppearance(value.resourceId);
    }

    private static int color(@NonNull Context context, int attr) {
        TypedValue value = new TypedValue();
        return context.getTheme().resolveAttribute(attr, value, true) ? value.data : 0xFF808080;
    }
}
