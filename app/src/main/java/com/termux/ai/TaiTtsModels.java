package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The speech-output models on the phone and which one speaks. Everything goes by the
 * {@code text_to_speech} capability, never by name, the way {@link TaiSpeechModels} does for
 * speech-to-text. There is one speech-output entry in the catalogue today, so "the one that
 * speaks" is simply the first installed one, the catalogue's own entry first; a second engine
 * would add a stored choice here, as speech-to-text has.
 */
public final class TaiTtsModels {
    private TaiTtsModels() {}

    public static boolean isTtsModel(@NonNull TaiModelSpec spec) {
        return spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH);
    }

    /** Every readable speech-output model, downloads first then the registry (which wins on a shared id). */
    @NonNull
    public static List<TaiModelSpec> installed(@NonNull TaiModelStore store) {
        LinkedHashMap<String, TaiModelSpec> models = new LinkedHashMap<>(store.getDownloadedReadableModels());
        models.putAll(store.getInstalledUserModels());
        List<TaiModelSpec> tts = new ArrayList<>();
        for (TaiModelSpec spec : models.values()) {
            if (isTtsModel(spec)) tts.add(spec);
        }
        return tts;
    }

    /** The model that speaks: the catalogue's KittenTTS entry when installed, else the first installed one. */
    @Nullable
    public static TaiModelSpec chooseActive(@NonNull Collection<TaiModelSpec> installed) {
        for (TaiModelSpec spec : installed) {
            if (TaiModelCatalog.KITTEN_TTS_NANO_ID.equals(spec.id)) return spec;
        }
        for (TaiModelSpec spec : installed) return spec;
        return null;
    }

    @Nullable
    public static TaiModelSpec resolveActive(@NonNull TaiModelStore store) {
        return chooseActive(installed(store));
    }

    /**
     * The model that speaks: read aloud's {@link TaiFeaturePlan} (on the CPU, as every voice model
     * runs), and when it names none installed the {@link #chooseActive} rule over what is.
     */
    @Nullable
    public static TaiModelSpec resolveActive(@NonNull android.content.Context context, @NonNull TaiModelStore store) {
        List<TaiModelSpec> installed = installed(store);
        String resolved = TaiFeaturePlans.forContext(context).plan(TaiFunction.READ_ALOUD).modelId;
        if (resolved != null) {
            for (TaiModelSpec spec : installed) {
                if (resolved.equals(spec.id)) return spec;
            }
        }
        return chooseActive(installed);
    }
}
