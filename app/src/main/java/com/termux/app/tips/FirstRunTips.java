package com.termux.app.tips;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The first-run hints (#443): which one stands on screen, and that each goes for good once its
 * gesture is done or it is dismissed. The store is read at every decision, so Settings clearing it
 * (Tips) brings the hints back the next time the screen asks.
 */
public final class FirstRunTips {

    /** Where the hints are kept. */
    public interface Store {
        @Nullable String read();

        void write(@NonNull String done);
    }

    /** What the hints need of the screen. */
    public interface Surface {
        /** Whether a Home page is in front. */
        boolean homeInFront();

        /** Whether there is more than one mark to slide between. */
        boolean canSlide();

        /** Whether the window in front is full screen. */
        default boolean fullScreenInFront() {
            return false;
        }

        /** Whether navigation mode is on. */
        default boolean navigating() {
            return false;
        }

        /** Runs {@code action} on the main thread after {@code delayMs}. */
        default void postDelayed(@NonNull Runnable action, long delayMs) {
        }

        /** Asks for {@link FirstRunTips#refresh} again soon: a layer holds the screen. */
        void refreshSoon();

        /** Whether something else holds the screen (edit mode, a sheet, a menu). */
        boolean busy();

        /** Puts the card for {@code tip} on screen, replacing any other. */
        void show(@NonNull Tip tip);

        /** Whether the card is on screen now. */
        boolean showing();

        /** Takes the card off the screen. */
        void hide();
    }

    /**
     * How long full screen's hint stands before it goes for good, in ms: the prototype's toast.
     * Navigation mode's stands while the mode is on, as the prototype's does.
     */
    public static final long FULL_SCREEN_HINT_MS = 1900L;

    @NonNull private final Store mStore;
    @NonNull private final Surface mSurface;
    @Nullable private Tip mShown;

    public FirstRunTips(@NonNull Store store, @NonNull Surface surface) {
        mStore = store;
        mSurface = surface;
    }

    /** The hint on screen, or null. */
    @Nullable
    public Tip shown() {
        return mShown;
    }

    /** Decides again what stands on screen: after Home came in front, a sheet closed, Tips was used. */
    public void refresh() {
        TipsDone done = TipsDone.parse(mStore.read());
        boolean canSlide = mSurface.canSlide();
        boolean home = mSurface.homeInFront();
        boolean busy = mSurface.busy();
        // A layer a gesture opened holds the screen: look again once it has closed.
        if (home && busy && done.anyLeft(canSlide)) mSurface.refreshSoon();
        boolean fullScreen = mSurface.fullScreenInFront();
        boolean navigating = mSurface.navigating();
        // A situation's hint is shown once: when its situation ends it is gone for good.
        Tip shown = mShown;
        if (shown != null && shown.situational() && shown != done.situational(fullScreen, navigating)) {
            done = done.with(shown);
            mStore.write(done.format());
        }
        Tip situational = done.situational(fullScreen, navigating);
        Tip next = situational != null ? situational : done.next(home, busy, canSlide);
        if (next == mShown && (next == null || mSurface.showing())) return;
        mShown = next;
        if (next == null) mSurface.hide();
        else mSurface.show(next);
        // Full screen's hint is a toast: it goes after its time, gone for good (round 4).
        if (next == Tip.FULL_SCREEN) {
            mSurface.postDelayed(() -> {
                if (mShown == Tip.FULL_SCREEN) onGesture(Tip.FULL_SCREEN);
            }, FULL_SCREEN_HINT_MS);
        }
    }

    /** The gesture {@code tip} teaches was done: it is gone for good, shown or not. */
    public void onGesture(@NonNull Tip tip) {
        TipsDone done = TipsDone.parse(mStore.read());
        if (done.has(tip)) return;
        mStore.write(done.with(tip).format());
        refresh();
    }

    /** The × on the card: the hint on screen is gone for good. */
    public void dismiss() {
        Tip tip = mShown;
        if (tip != null) onGesture(tip);
    }
}
