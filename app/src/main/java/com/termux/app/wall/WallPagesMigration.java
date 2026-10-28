package com.termux.app.wall;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.look.LookStore;
import com.termux.app.look.PageLook;
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
 * Home, the Terminal page for Workspace, and the Display page for Display (ADR 0015 section 7: a Display page follows Terminal when the
 * display was in the row or Start in was Display).
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
        if (gated && !empty) return convertDisplayWindowHome(prefs, store);
        if (empty) {
            WallPages seeded = seed(prefs.getString(TERMUX_APP.KEY_LAUNCHER_START_IN, null),
                prefs.getInt(KEY_DEFAULT_PAGE, 0), prefs.getString(TERMUX_APP.KEY_USE_AS, null),
                homeCount.get(), prefs.getBoolean(TERMUX_APP.KEY_X11_DISPLAY_AUTOSTART,
                    TERMUX_APP.DEFAULT_X11_DISPLAY_AUTOSTART));
            store.replace(seeded);
            remember(prefs, seeded);
        }
        prefs.edit().putInt(KEY_VERSION, VERSION).apply();
        return true;
    }

    private static void remember(SharedPreferences prefs, WallPages seeded) {
        for (Page p : seeded.allPages()) {
            if (p.kind == PageKind.DISPLAY) {
                prefs.edit().putInt(KEY_DISPLAY_LOOK_PENDING, p.id).apply();
                return;
            }
        }
    }

    /**
     * An install seeded before the Display page existed may hold the temporary display-window home:
     * adds the Display page after Terminal, points home at it and clears the flag, once (the flag
     * is the gate, so a second call finds nothing to do).
     */
    private static boolean convertDisplayWindowHome(SharedPreferences prefs, WallPagesStore store) {
        if (!store.current().homeIsDisplayWindow()) return false;
        WallPages.Result r = store.edit(WallPages::homeOnDisplayPage);
        if (r.ok() && r.change == WallPages.ChangeType.ADDED && r.page != null) {
            prefs.edit().putInt(KEY_DISPLAY_LOOK_PENDING, r.page.id).apply();
        }
        return r.ok();
    }

    /** As above with the count in hand. */
    public static boolean migrateIfNeeded(@Nullable SharedPreferences prefs, final int homeCount,
                                          @NonNull WallPagesStore store) {
        return migrateIfNeeded(prefs, new HomeCount() {
            @Override public int get() { return homeCount; }
        }, store);
    }

    /** The seeded value without a display in the row; see the five-argument form. */
    @NonNull
    public static WallPages seed(@Nullable String startIn, int defaultPage, @Nullable String useAs,
                                 int homeCount) {
        return seed(startIn, defaultPage, useAs, homeCount, false);
    }

    /**
     * The seeded value; pure. Stored strings are read as the policies read them. When the display
     * window was in the row ({@code displayInRow}) or Start in is Display, one unbound Display page
     * follows the Terminal page; Start in = Display makes it home.
     */
    @NonNull
    public static WallPages seed(@Nullable String startIn, int defaultPage, @Nullable String useAs,
                                 int homeCount, boolean displayInRow) {
        int n = Math.max(0, homeCount);
        List<Page> homes = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) homes.add(new Page(i, PageKind.HOME, null, null));
        Page terminal = new Page(n + 1, PageKind.TERMINAL, null, null);
        String start = StartInPolicy.read(startIn);
        boolean startsDisplay = WallRow.START_IN_DISPLAY.equals(start);
        Page display = displayInRow || startsDisplay
            ? new Page(n + 2, PageKind.DISPLAY, null, null) : null;
        int next = n + 2 + (display != null ? 1 : 0);
        if (!UseAs.read(useAs).showsHomePage() || n == 0) {
            List<Page> only = new ArrayList<>();
            only.add(terminal);
            if (display != null) only.add(display);
            return new WallPages(only, Collections.<Integer>emptyList(), homes,
                startsDisplay ? display.id : terminal.id, next, false);
        }
        List<Page> all = new ArrayList<>(homes);
        all.add(terminal);
        if (display != null) all.add(display);
        int defaultId = Math.max(0, Math.min(defaultPage, n - 1)) + 1;
        int home = defaultId;
        if (WallRow.START_IN_WORKSPACE.equals(start)) {
            home = terminal.id;
        } else if (startsDisplay) {
            home = display.id;
        }
        return new WallPages(all, Collections.<Integer>emptyList(), Collections.<Page>emptyList(),
            home, next, false);
    }

    /**
     * Remembers the Display page the upgrade created until its look is copied (see
     * {@link #copyLookToUpgradedDisplay}): 0 or absent for none.
     */
    @VisibleForTesting static final String KEY_DISPLAY_LOOK_PENDING = "wall_pages_display_look_pending";

    /**
     * After {@code HalfLookUpgrade} has run: gives the Display page this upgrade created a copy of
     * the Terminal page's look overrides, once. A no-op when no page was created.
     *
     * @return whether it copied
     */
    public static boolean copyLookToUpgradedDisplay(@Nullable SharedPreferences prefs,
                                                    @NonNull LookStore look, int terminalPageId) {
        if (prefs == null) return false;
        int display = prefs.getInt(KEY_DISPLAY_LOOK_PENDING, 0);
        if (display <= 0) return false;
        prefs.edit().remove(KEY_DISPLAY_LOOK_PENDING).apply();
        if (terminalPageId < 0) return false;
        PageLook.copyOverrides(look, terminalPageId, display);
        return true;
    }
}
