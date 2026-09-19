package com.termux.app.x11;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.notice.AppNotice;

/**
 * D9's offer, as the user meets it: what the launcher would do, and the two answers.
 *
 * <p>There is no wizard and no form. Every question a setup wizard would ask has an answer the
 * launcher can give itself — which distro, what the account is called, which apps to start with —
 * and D1's premise is that the user is never handed a command to run. So the whole flow is one
 * dialog that says what will happen and two buttons, and then a terminal pane they can watch.
 *
 * <p>The same dialog serves the offer on the Display place and the row in Settings → Display. The
 * difference is only what a "not now" means: on the place it takes the offer away (see
 * {@link DistroSetupStore}), and from Settings there is nothing to take away, since the row is
 * always there.
 */
public final class DistroSetupDialog {

    private DistroSetupDialog() {}

    /**
     * Told when the user has answered, either way, so the place can re-read itself. Starting the
     * run is deliberately <em>not</em> remembered as a dismissal: the offer stays out while the
     * pane works, a second tap on it comes back to that pane rather than starting a second run,
     * and a run that fails therefore still has its own way back — which a remembered dismissal
     * would have taken away at the one moment it was needed.
     */
    public interface Listener {
        void onSetupAnswered();
    }

    /**
     * Show the offer for {@code readiness}. A reading with nothing missing says so and stops
     * there: the flow's job is to get a container to the point where its apps work, not to be a
     * package installer for one that already does.
     *
     * @param remember whether a "not now" should be remembered against this situation
     */
    public static void show(@NonNull Context context, @NonNull DistroSetup.Readiness readiness,
                            boolean remember, @Nullable Listener listener) {
        if (!readiness.needsSetup()) {
            AppNotice.show(context, R.string.distro_setup_already_done);
            return;
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.distro_setup_title)
            .setMessage(readiness.startsFromNothing()
                ? R.string.distro_setup_message_new : R.string.distro_setup_message_finish)
            .setPositiveButton(R.string.distro_setup_start, (dialog, which) ->
                start(context, readiness, listener))
            .setNegativeButton(R.string.distro_setup_not_now, (dialog, which) -> {
                if (remember) new DistroSetupStore(context).dismiss(readiness);
                if (listener != null) listener.onSetupAnswered();
            })
            .show();
    }

    /** Read the containers afresh and offer whatever they need. */
    public static void show(@NonNull Context context, boolean remember,
                            @Nullable Listener listener) {
        show(context, DistroSetup.read(), remember, listener);
    }

    private static void start(@NonNull Context context, @NonNull DistroSetup.Readiness readiness,
                              @Nullable Listener listener) {
        DistroSetupRunner.Result result = DistroSetupRunner.run(
            DistroSetup.script(readiness, messages(context)),
            context.getString(R.string.distro_setup_pane_title));
        if (result == DistroSetupRunner.Result.FAILED) {
            AppNotice.show(context, R.string.distro_setup_cannot_start);
            return;
        }
        if (listener != null) listener.onSetupAnswered();
    }

    /** Every sentence the run can print, filled from the resources. */
    @NonNull
    static DistroSetup.Messages messages(@NonNull Context context) {
        return new DistroSetup.Messages(
            context.getString(R.string.distro_setup_running),
            context.getString(R.string.distro_setup_step_distro),
            context.getString(R.string.distro_setup_step_user),
            context.getString(R.string.distro_setup_step_fonts),
            context.getString(R.string.distro_setup_step_graphics),
            context.getString(R.string.distro_setup_step_apps),
            context.getString(R.string.distro_setup_failed_distro),
            context.getString(R.string.distro_setup_failed_user),
            context.getString(R.string.distro_setup_failed_fonts),
            context.getString(R.string.distro_setup_failed_graphics),
            context.getString(R.string.distro_setup_failed_apps),
            context.getString(R.string.distro_setup_try_again),
            context.getString(R.string.distro_setup_done));
    }
}
