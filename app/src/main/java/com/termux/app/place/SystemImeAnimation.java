package com.termux.app.place;

import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

/**
 * Feeds the system keyboard's animation into a {@link SystemImeRide} (Android 11 and later; below
 * it the compat library would emulate the callback with a listener that replaces the content's
 * own, so nothing is registered). Per frame it writes one number through {@link Host#writeRide}.
 */
public final class SystemImeAnimation {

    public interface Host {
        /** The ime bottom inset the layout last answered for. */
        int laidForImePx();

        /** The ride's offset for this frame, or 0 when it must not move the stack. */
        void writeRide(int offsetPx);

        /** True while the embedded keyboard suppresses the system keyboard. */
        boolean suppressed();
    }

    private SystemImeAnimation() { }

    public static void install(@NonNull View content, @NonNull SystemImeRide ride,
                               @NonNull Host host) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        ViewCompat.setWindowInsetsAnimationCallback(content,
            new WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                @NonNull
                @Override
                public WindowInsetsAnimationCompat.BoundsCompat onStart(
                        @NonNull WindowInsetsAnimationCompat animation,
                        @NonNull WindowInsetsAnimationCompat.BoundsCompat bounds) {
                    if (isIme(animation)) {
                        // The end state's insets were dispatched before this, so the layout has
                        // answered for it already.
                        ride.onAnimationStart();
                        ride.onAnimationStart(host.laidForImePx());
                    }
                    return bounds;
                }

                @NonNull
                @Override
                public WindowInsetsCompat onProgress(@NonNull WindowInsetsCompat insets,
                        @NonNull List<WindowInsetsAnimationCompat> running) {
                    if (!anyIme(running)) return insets;
                    Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                    ride.onAnimationProgress(ime.bottom);
                    host.writeRide(ride.owns() && !host.suppressed() ? ride.offsetPx() : 0);
                    return insets;
                }

                @Override
                public void onEnd(@NonNull WindowInsetsAnimationCompat animation) {
                    if (!isIme(animation)) return;
                    ride.onAnimationEnd();
                    host.writeRide(0);
                }
            });
    }

    private static boolean isIme(@NonNull WindowInsetsAnimationCompat animation) {
        return (animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0;
    }

    private static boolean anyIme(@NonNull List<WindowInsetsAnimationCompat> running) {
        for (int i = 0; i < running.size(); i++) if (isIme(running.get(i))) return true;
        return false;
    }
}
