package com.termux.app.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.termux.R;

/**
 * What the model chip, the notice above the composer and the Model sheet's rows say for a
 * {@link AssistantModelStatus.Snapshot} (sheets 03 and 04). Pure.
 */
public final class AssistantModelPresenter {

    public enum Chip { READY, LOADING, NOT_LOADED, NO_MODEL }

    /** Failures the status does not hold: remembered by the page from the action that failed. */
    public enum Failure { NONE, DOWNLOAD_FAILED, NO_SPACE, NO_MEMORY }

    private AssistantModelPresenter() {
    }

    @NonNull
    public static Chip chip(@NonNull AssistantModelStatus.Snapshot snapshot) {
        switch (snapshot.defaultModel.state.kind) {
            case READY: return Chip.READY;
            // A download shows on the chip as the ring of 04.2, with the model's name.
            case LOADING:
            case DOWNLOADING: return Chip.LOADING;
            case DOWNLOADED: return Chip.NOT_LOADED;
            default: return Chip.NO_MODEL;
        }
    }

    /** Whether a question can go to the engine now: the runtime loads a downloaded model on demand. */
    public static boolean canAnswer(@NonNull AssistantModelStatus.Snapshot snapshot) {
        AssistantModelStatus.Kind kind = snapshot.defaultModel.state.kind;
        return kind == AssistantModelStatus.Kind.READY || kind == AssistantModelStatus.Kind.DOWNLOADED;
    }

    /** "E2B" or "E4B"; empty for another model. */
    @StringRes
    public static int shortName(@NonNull String modelId) {
        if (AssistantModelStatus.sameModel(modelId, AssistantModelStatus.MODEL_E4B)) {
            return R.string.assistant_model_e4b_short;
        }
        return R.string.assistant_model_e2b_short;
    }

    @StringRes
    public static int fullName(@NonNull String modelId) {
        if (AssistantModelStatus.sameModel(modelId, AssistantModelStatus.MODEL_E4B)) {
            return R.string.assistant_model_e4b;
        }
        return R.string.assistant_model_e2b;
    }

    /** The state words of a Model sheet row; the percent is filled by the caller. */
    @StringRes
    public static int rowState(@NonNull AssistantModelStatus.State state, boolean selected) {
        switch (state.kind) {
            case READY: return R.string.assistant_model_state_ready;
            case LOADING: return R.string.assistant_model_state_loading;
            case DOWNLOADING:
                return state.percent >= 0 ? R.string.assistant_model_state_downloading
                    : R.string.assistant_model_state_downloading_unknown;
            case DOWNLOADED:
                return selected ? R.string.assistant_model_state_downloaded_unloaded
                    : R.string.assistant_model_state_downloaded;
            default: return R.string.assistant_model_state_not_downloaded;
        }
    }

    /** A model that is not on the phone cannot be chosen. */
    public static boolean selectable(@NonNull AssistantModelStatus.State state) {
        switch (state.kind) {
            case DOWNLOADED:
            case LOADING:
            case READY: return true;
            default: return false;
        }
    }
}
