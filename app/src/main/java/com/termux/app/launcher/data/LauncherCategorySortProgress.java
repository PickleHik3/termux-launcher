package com.termux.app.launcher.data;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.termux.R;

/**
 * What the categorization run tells the user while it works: one percentage and one line of text.
 *
 * <p>Pure and static so both surfaces — the settings row and the foreground notification — read the
 * same rule, and so the wording thresholds are testable without a model on the device.
 *
 * <p>The tail wording ("almost there", "any minute now") is deliberately reserved for the last
 * stretch. A run over a full catalogue takes minutes; saying "almost there" at 40% would be the one
 * thing that makes the remaining minutes feel longer.
 */
public final class LauncherCategorySortProgress {

    /** Catalogue walk before the model is touched. */
    public static final String PHASE_PREPARING = "preparing";
    /** The model is being loaded into the runtime; no app has been classified yet. */
    public static final String PHASE_LOADING_MODEL = "loading_model";
    /** One inference per app, the long phase. */
    public static final String PHASE_SORTING = "sorting";
    /** Assignments are being merged into app-categories.conf. */
    public static final String PHASE_SAVING = "saving";

    /** Below this fraction the run is simply "categorizing"; the tail wording starts here. */
    private static final float ALMOST_THERE_FRACTION = 0.85f;
    private static final float ANY_MINUTE_FRACTION = 0.95f;
    /** Loading is a real wait, so it owns a visible slice of the bar rather than sitting at zero. */
    private static final int LOADING_PERCENT = 3;

    private LauncherCategorySortProgress() {
    }

    /**
     * @return 0..100 for the progress bar. The sorting phase maps the processed count onto the
     *     whole bar; a total that is not known yet reads as the loading slice rather than as 100.
     */
    public static int percent(@NonNull String phase, int processed, int total) {
        switch (phase) {
            case PHASE_PREPARING:
                return 0;
            case PHASE_LOADING_MODEL:
                return LOADING_PERCENT;
            case PHASE_SAVING:
                return 100;
            default:
                if (total <= 0) return LOADING_PERCENT;
                int value = Math.round(100f * Math.max(0, Math.min(processed, total)) / total);
                return Math.max(LOADING_PERCENT, Math.min(100, value));
        }
    }

    /**
     * @return true while the run has no measurable numerator — reading the catalogue and loading
     *     the model. A determinate bar parked at 3% for a minute reads as a stalled run; a moving
     *     indeterminate one reads as work.
     */
    public static boolean isIndeterminate(@NonNull String phase) {
        return PHASE_PREPARING.equals(phase) || PHASE_LOADING_MODEL.equals(phase);
    }

    /** @return the line shown under the title while the run is in this state. */
    @StringRes
    public static int hint(@NonNull String phase, int processed, int total) {
        switch (phase) {
            case PHASE_PREPARING:
                return R.string.settings_app_drawer_category_sort_hint_preparing;
            case PHASE_LOADING_MODEL:
                return R.string.settings_app_drawer_category_sort_hint_loading_model;
            case PHASE_SAVING:
                return R.string.settings_app_drawer_category_sort_hint_saving;
            default:
                float fraction = total <= 0 ? 0f : (float) processed / total;
                if (fraction >= ANY_MINUTE_FRACTION)
                    return R.string.settings_app_drawer_category_sort_hint_any_minute;
                if (fraction >= ALMOST_THERE_FRACTION)
                    return R.string.settings_app_drawer_category_sort_hint_almost_there;
                return R.string.settings_app_drawer_category_sort_hint_categorizing;
        }
    }
}
