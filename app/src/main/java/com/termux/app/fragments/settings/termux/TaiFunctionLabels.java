package com.termux.app.fragments.settings.termux;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelSpec;

import java.util.Collections;
import java.util.Map;

/**
 * The string-resource words for {@link TaiFunctionRows} and {@link TaiFunctionPickerModel}: the
 * messages, the function names, and model names as the Centre prints them (a catalogue or
 * installed display name, Whisper and Parakeet in their plain form).
 */
final class TaiFunctionLabels implements TaiFunctionRows.Labels {
    private final Context context;
    private final Map<String, TaiModelSpec> installed;

    /** {@code installed} lets an imported model be named by what the person called it. */
    TaiFunctionLabels(@NonNull Context context, @Nullable Map<String, TaiModelSpec> installed) {
        this.context = context.getApplicationContext() == null ? context : context.getApplicationContext();
        this.installed = installed == null ? Collections.<String, TaiModelSpec>emptyMap() : installed;
    }

    @NonNull
    @Override
    public String text(@NonNull TaiFunctionRows.Msg msg, @NonNull Object... args) {
        return context.getString(resource(msg), args);
    }

    private static int resource(@NonNull TaiFunctionRows.Msg msg) {
        switch (msg) {
            case AUTOMATIC: return R.string.tai_fn_automatic;
            case AUTOMATIC_BARE: return R.string.tai_fn_automatic_bare;
            case RULES_ONLY: return R.string.tai_fn_rules_only;
            case RAW_TEXT: return R.string.tai_fn_raw_text;
            case OFF: return R.string.tai_fn_off;
            case NOT_SET: return R.string.tai_fn_not_set;
            case REMOTE: return R.string.tai_fn_remote;
            case REMOTE_SETUP: return R.string.tai_fn_remote_setup;
            case GPU: return R.string.tai_fn_gpu;
            case CPU: return R.string.tai_fn_cpu;
            case READER_DEPTH: return R.string.tai_fn_reader_depth;
            case FIT_FITS: return R.string.tai_fn_fit_fits;
            case FIT_ROOM: return R.string.tai_fn_fit_room;
            case FIT_BIGGER: return R.string.tai_fn_fit_bigger;
            case USED_BY: return R.string.tai_fn_used_by;
            case FOR: return R.string.tai_fn_for;
            case FOR_CUTOUT: return R.string.tai_fn_for_cutout;
            case CHAIN: return R.string.tai_fn_chain;
            case DELETE_IN_USE: return R.string.tai_fn_delete_in_use;
            case DELETE_LINE: return R.string.tai_fn_delete_line;
            case LEVEL_LIGHT: return R.string.tai_fn_level_light;
            case LEVEL_POLISHED: return R.string.tai_fn_level_polished;
            case CHOOSER_READER: return R.string.tai_fn_chooser_reader;
            default: return R.string.tai_fn_chooser_depth;
        }
    }

    @NonNull
    @Override
    public String functionName(@NonNull TaiFunction function) {
        switch (function) {
            case ASSISTANT: return context.getString(R.string.tai_fn_name_assistant);
            case VOICE_TYPING: return context.getString(R.string.tai_fn_name_voice_typing);
            case TIDY_DICTATION: return context.getString(R.string.tai_fn_name_tidy_dictation);
            case READ_ALOUD: return context.getString(R.string.tai_fn_name_read_aloud);
            case APP_CATEGORIES: return context.getString(R.string.tai_fn_name_app_categories);
            case EMBEDDINGS: return context.getString(R.string.tai_fn_name_embeddings);
            default: return context.getString(R.string.tai_fn_name_wallpaper);
        }
    }

    @NonNull
    @Override
    public String modelName(@NonNull String modelId) {
        if (TaiFunctionModels.isRemote(modelId)) {
            return text(TaiFunctionRows.Msg.REMOTE, modelId.substring(TaiFunctionModels.REMOTE_PREFIX.length()));
        }
        String base = TaiFunctionRows.stripVision(modelId);
        TaiModelSpec spec = installed.get(base);
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(base);
        String display = spec != null ? spec.displayName : entry != null ? entry.displayName : base;
        boolean speech = spec != null ? spec.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)
            || spec.capabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)
            : entry != null && entry.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
        return TaiModelCentreFragment.centreName(base, display, spec == null ? null : spec.localPath, speech);
    }
}
