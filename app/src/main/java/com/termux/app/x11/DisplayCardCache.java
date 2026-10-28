package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The Linux app catalog and each bound Display page's card (name and icon) as last read, so the
 * main thread never scans the app roots or loads an icon (W2). {@link #refresh} reads them again
 * on the background executor and hands the result to the main one; until the first refresh lands
 * the cache answers with an empty catalog and no cards.
 *
 * @param <I> the icon type (a Drawable in the app, anything in a test)
 */
public final class DisplayCardCache<I> {

    /** Reads the catalog; runs on the background executor. */
    public interface Scanner {
        @NonNull List<LinuxAppCatalog.LinuxApp> scan();
    }

    /** Loads one app's icon, null when it has none; runs on the background executor. */
    public interface IconLoader<I> {
        @Nullable I load(@NonNull LinuxAppCatalog.LinuxApp app);
    }

    /** A bound page's card: the app's name and icon. */
    public static final class Card<I> {
        @NonNull public final LinuxAppCatalog.LinuxApp app;
        @Nullable public final I icon;

        Card(@NonNull LinuxAppCatalog.LinuxApp app, @Nullable I icon) {
            this.app = app;
            this.icon = icon;
        }
    }

    @NonNull private final Scanner mScanner;
    @NonNull private final IconLoader<I> mIcons;
    @NonNull private final Executor mBackground;
    @NonNull private final Executor mMain;

    @NonNull private List<LinuxAppCatalog.LinuxApp> mApps = Collections.emptyList();
    @NonNull private Map<String, Card<I>> mCards = Collections.emptyMap();
    private boolean mInFlight;
    private boolean mAgain;
    @NonNull private List<String> mAgainBindings = Collections.emptyList();

    public DisplayCardCache(@NonNull Scanner scanner, @NonNull IconLoader<I> icons,
                            @NonNull Executor background, @NonNull Executor main) {
        mScanner = scanner;
        mIcons = icons;
        mBackground = background;
        mMain = main;
    }

    /** The catalog as last read; never scans. */
    @NonNull
    public List<LinuxAppCatalog.LinuxApp> apps() {
        return mApps;
    }

    /** The app {@code id} names in the catalog as last read; null when unknown or not read yet. */
    @Nullable
    public LinuxAppCatalog.LinuxApp find(@Nullable String id) {
        return id == null ? null : LinuxAppCatalog.find(mApps, id);
    }

    /** The card of the page bound to {@code binding} as last read; null when none. */
    @Nullable
    public Card<I> card(@Nullable String binding) {
        return binding == null ? null : mCards.get(binding);
    }

    /**
     * Read the catalog again, and the cards of {@code bindings}, off the main thread; {@code done}
     * runs on the main executor once the new values are in. A refresh asked while one is running
     * runs once more after it (with the latest bindings), and its {@code done} is dropped for the
     * running one's.
     */
    public void refresh(@NonNull List<String> bindings, @Nullable Runnable done) {
        if (mInFlight) {
            mAgain = true;
            mAgainBindings = bindings;
            return;
        }
        mInFlight = true;
        final List<String> wanted = bindings;
        mBackground.execute(() -> {
            List<LinuxAppCatalog.LinuxApp> apps = mScanner.scan();
            Map<String, Card<I>> cards = new HashMap<>();
            for (String binding : wanted) {
                if (binding == null || cards.containsKey(binding)) continue;
                LinuxAppCatalog.LinuxApp app = LinuxAppCatalog.find(apps, binding);
                if (app != null) cards.put(binding, new Card<>(app, mIcons.load(app)));
            }
            mMain.execute(() -> {
                mApps = Collections.unmodifiableList(apps);
                mCards = Collections.unmodifiableMap(cards);
                mInFlight = false;
                if (done != null) done.run();
                if (mAgain) {
                    mAgain = false;
                    refresh(mAgainBindings, done);
                }
            });
        });
    }
}
