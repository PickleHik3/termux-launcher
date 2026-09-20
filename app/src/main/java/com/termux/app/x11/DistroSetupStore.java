package com.termux.app.x11;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;

/**
 * The one thing the Display place's offer has to remember: that the user has already answered it
 * — by saying no to it, or by taking the command the Get GUI apps screen builds.
 *
 * <p>The offer sits on the Display place, which is somewhere the user goes to run things, so it
 * has to be possible to make it go away for good — and equally it must not stay away when the
 * thing it was offering about has changed. Both come from remembering the
 * {@link DistroSetup.Readiness#signature() situation} rather than a flag:
 *
 * <ul>
 *   <li>Say "not now" to "you have no Linux at all" and the offer is gone. Install a container by
 *       hand a month later and the situation is a different one, so it is offered once more —
 *       which is the moment it is actually useful.
 *   <li>Say "not now" to a container missing its fonts and finish the job yourself in a shell:
 *       nothing is left to offer, so nothing is offered. Fix only half of it and the remaining
 *       half is a new situation and is offered again, once.
 *   <li>Answer it and change nothing — the case of someone who runs their container their own
 *       way, and of someone who copied the command and has not pasted it yet — and the offer
 *       never comes back on its own. Settings → Display is where they can still reach it.
 * </ul>
 *
 * <p>One string in the launcher's own preferences file, in the style of
 * {@link X11ElectronSandboxStore}. There is nothing to migrate and nothing to cache: it is read
 * on arrival at a place, beside a filesystem probe that costs more than it does.
 */
public final class DistroSetupStore {

    private static final String PREFS_KEY_DISMISSED_V1 = "x11_distro_setup_dismissed_v1";

    private final SharedPreferences sharedPreferences;

    public DistroSetupStore(@NonNull Context context) {
        this.sharedPreferences = context.getApplicationContext().getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE);
    }

    /** The situation the user last said "not now" to, or empty when they never have. */
    @NonNull
    public String dismissed() {
        String raw = sharedPreferences.getString(PREFS_KEY_DISMISSED_V1, "");
        return raw == null ? "" : raw;
    }

    /** Remember this situation as one the user has already turned down. */
    public void dismiss(@NonNull DistroSetup.Readiness readiness) {
        sharedPreferences.edit().putString(PREFS_KEY_DISMISSED_V1, readiness.signature()).apply();
    }

    /**
     * Remember the situation the containers are in right now.
     *
     * <p>What counts as having answered the offer is copying the command — that is the moment the
     * user has what they came for, and until then the offer has not been acted on at all. Opening
     * the screen and backing out of it is not an answer, so it must leave the offer where it was.
     *
     * @param containersDir the containers directory to read the situation from
     */
    public static void dismissCurrent(@NonNull Context context, @NonNull File containersDir) {
        new DistroSetupStore(context).dismiss(DistroSetup.read(containersDir));
    }

    /** The same, for the running prefix's containers. */
    public static void dismissCurrent(@NonNull Context context) {
        dismissCurrent(context, ProotDistro.containersDir());
    }

    /**
     * Whether the Display place should be showing the offer. Pure, so the rule itself is testable
     * without a preferences file behind it.
     *
     * @param readiness what the containers were just read as needing
     * @param dismissed what {@link #dismissed()} holds
     * @param resting   whether the place is in its plain "nothing running" state. The offer is
     *                  withheld in the other two: a display that is switched off, or one that
     *                  cannot start for want of its keyboard data, already has its own single
     *                  thing for the user to do, and two calls to action in one empty state is
     *                  how a home screen starts to nag.
     */
    public static boolean shouldOffer(@NonNull DistroSetup.Readiness readiness,
                                      @NonNull String dismissed, boolean resting) {
        if (!resting || !readiness.needsSetup()) return false;
        return !readiness.signature().equals(dismissed);
    }
}
