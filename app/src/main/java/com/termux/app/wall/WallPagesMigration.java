package com.termux.app.wall;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.wall.WallPages.Page;
import com.termux.app.wall.WallPages.PageKind;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Seeds {@link WallPages} from what an install has before the page list (ADR 0015), once, in the
 * place of {@code WallReworkMigration}: Start in, the default Home page, Use as and the number of
 * Home pages. Gated by its own key. Nothing is deleted: the old keys stay.
 *
 * <p>Home screen users get [Home 1..n][Terminal]; home is the default Home page for Start in
 * Home, the Terminal page for Workspace, and the display window (transitional) for Display.
 * Terminal only users get [Terminal] as home and their n Home pages detached, kept for Edit
 * pages' Add. Page ids are 1..n for the Home pages, then the Terminal page, so the widget
 * repository's page index i becomes id i + 1.
 */
public final class WallPagesMigration {

    /** Bumped when another set of old keys has to be folded in. */
    @VisibleForTesting static final int VERSION = 1;
    @VisibleForTesting static final String KEY_VERSION = "wall_pages_migration_version";
    /** {@code HomeSettings.KEY_DEFAULT_PAGE}. */
    @VisibleForTesting static final String KEY_DEFAULT_PAGE = "app_launcher_home_default_page";

    private WallPagesMigration() {}

    /** The Home page count, asked only when a seed is needed. */
    public interface HomeCount {
        int get();
    }

    /**
     * Seeds {@code store} from {@code prefs} when this install has not been through it, or when
     * the store holds no pages (unreadable or newer data: an empty wall never reaches the
     * activity). Otherwise a no-op.
     *
     * @param homeCount the widget repository's Home page count, read only when seeding
     * @return whether it seeded
     */
    public static boolean migrateIfNeeded(@Nullable SharedPreferences prefs,
                                          @NonNull HomeCount homeCount,
                                          @NonNull WallPagesStore store) {
        if (prefs == null) return false;
        boolean gated = prefs.getInt(KEY_VERSION, 0) >= VERSION;
        boolean empty = store.current().allPages().isEmpty();
        if (gated && !empty) return false;
        if (empty) {
            store.replace(seed(prefs.getString(TERMUX_APP.KEY_LAUNCHER_START_IN, null),
                prefs.getInt(KEY_DEFAULT_PAGE, 0), prefs.getString(TERMUX_APP.KEY_USE_AS, null),
                homeCount.get()));
        }
        prefs.edit().putInt(KEY_VERSION, VERSION).apply();
        return !gated || empty;
    }

    /** As above with the count in hand. */
    public static boolean migrateIfNeeded(@Nullable SharedPreferences prefs, final int homeCount,
                                          @NonNull WallPagesStore store) {
        return migrateIfNeeded(prefs, new HomeCount() {
            @Override public int get() { return homeCount; }
        }, store);
    }

    /** The seeded value; pure. Stored strings are read as the policies read them. */
    @NonNull
    public static WallPages seed(@Nullable String startIn, int defaultPage, @Nullable String useAs,
                                 int homeCount) {
        int n = Math.max(0, homeCount);
        List<Page> homes = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) homes.add(new Page(i, PageKind.HOME, null, null));
        Page terminal = new Page(n + 1, PageKind.TERMINAL, null, null);
        int next = n + 2;
        if (!UseAs.read(useAs).showsHomePage() || n == 0) {
            // The display stays Start in for them too (as UseAs.startIn keeps it).
            return new WallPages(Collections.singletonList(terminal), Collections.<Integer>emptyList(),
                homes, terminal.id, next,
                WallRow.START_IN_DISPLAY.equals(StartInPolicy.read(startIn)));
        }
        List<Page> all = new ArrayList<>(homes);
        all.add(terminal);
        int defaultId = Math.max(0, Math.min(defaultPage, n - 1)) + 1;
        String start = StartInPolicy.read(startIn);
        int home = defaultId;
        boolean display = false;
        if (WallRow.START_IN_WORKSPACE.equals(start)) {
            home = terminal.id;
        } else if (WallRow.START_IN_DISPLAY.equals(start)) {
            display = true;
        }
        return new WallPages(all, Collections.<Integer>emptyList(), Collections.<Page>emptyList(),
            home, next, display);
    }
}
