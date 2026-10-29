package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.wall.WallPages.PageKind;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The one-time seeding of the page list from Start in, the default Home page and Use as. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class WallPagesMigrationTest {

    private static final String[] START_INS = {"home", "workspace", "display", null, "bogus"};
    private static final String[] USE_ASES = {"home", "terminal", null, "bogus"};

    private SharedPreferences prefs;

    @Before public void setUp() {
        prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("wall_pages_migration_test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    private static String kinds(WallPages p) {
        StringBuilder sb = new StringBuilder();
        for (WallPages.Page page : p.pages()) sb.append(page.kind.name().charAt(0));
        return sb.toString();
    }

    @Test
    public void homeUsersStartingInHomeLandOnTheDefaultHomePage() {
        WallPages p = WallPagesMigration.seed("home", 1, "home", 3);
        assertEquals("HHHT", kinds(p));
        assertEquals(2, p.home().id);
        assertFalse(p.homeIsDisplayWindow());
        assertTrue(p.detached().isEmpty());
        assertEquals(5, p.nextId());
        assertEquals(4, p.terminal().id);
    }

    @Test
    public void homeUsersStartingInTheWorkspaceLandOnTheTerminalPage() {
        WallPages p = WallPagesMigration.seed("workspace", 2, "home", 3);
        assertEquals(PageKind.TERMINAL, p.home().kind);
    }

    @Test
    public void homeUsersStartingInTheDisplayGetADisplayPageAsHome() {
        WallPages p = WallPagesMigration.seed("display", 1, "home", 2);
        assertFalse(p.homeIsDisplayWindow());
        assertEquals("HHTD", kinds(p));
        assertEquals(PageKind.DISPLAY, p.home().kind);
        assertEquals(4, p.homeId());
        assertEquals(null, p.home().binding);
        assertEquals(5, p.nextId());
    }

    @Test
    public void aDisplayInTheRowAddsAnUnboundDisplayPageAfterTerminalButHomeStays() {
        WallPages p = WallPagesMigration.seed("home", 1, "home", 2, true);
        assertEquals("HHTD", kinds(p));
        assertEquals(2, p.home().id);
        assertEquals(5, p.nextId());
        assertEquals("HHT", kinds(WallPagesMigration.seed("home", 1, "home", 2, false)));
    }

    @Test
    public void terminalOnlyUsersGetTheTerminalAndKeepTheirHomePagesDetached() {
        WallPages p = WallPagesMigration.seed("home", 1, "terminal", 3);
        assertEquals("T", kinds(p));
        assertEquals(PageKind.TERMINAL, p.home().kind);
        assertEquals(3, p.detached().size());
        for (WallPages.Page d : p.detached()) assertEquals(PageKind.HOME, d.kind);
        assertEquals(1, p.detached().get(0).id);
        assertFalse(p.homeIsDisplayWindow());
    }

    @Test
    public void terminalOnlyUsersStartingInTheDisplayGetTheDisplayPage() {
        WallPages p = WallPagesMigration.seed("display", 0, "terminal", 2);
        assertFalse(p.homeIsDisplayWindow());
        assertEquals("TD", kinds(p));
        assertEquals(PageKind.DISPLAY, p.home().kind);
        assertEquals(2, p.detached().size());
        assertEquals(5, p.nextId());
        assertEquals("T", kinds(WallPagesMigration.seed("workspace", 0, "terminal", 2)));
    }

    @Test
    public void anAlreadySeededWallWithTheDisplayWindowHomeIsConvertedOnce() {
        WallPagesStore store = new WallPagesStore(new WallPagesStore.MemoryStorage());
        WallPages old = WallPagesMigration.seed("home", 1, "home", 2);
        store.replace(old.setHomeDisplayWindow().pages);
        prefs.edit().putInt(WallPagesMigration.KEY_VERSION, WallPagesMigration.VERSION).commit();
        assertTrue(WallPagesMigration.migrateIfNeeded(prefs, 2, store));
        WallPages p = store.current();
        assertFalse(p.homeIsDisplayWindow());
        assertEquals("HHTD", kinds(p));
        assertEquals(PageKind.DISPLAY, p.home().kind);
        assertEquals(4, p.homeId());
        assertEquals(2, p.lastHomePageId());
        assertEquals(4, prefs.getInt(WallPagesMigration.KEY_DISPLAY_LOOK_PENDING, 0));
        WallPages again = store.current();
        assertFalse(WallPagesMigration.migrateIfNeeded(prefs, 2, store));
        assertEquals(again, store.current());
    }

    @Test
    public void aSeededWallWithoutTheFlagIsLeftAlone() {
        WallPagesStore store = new WallPagesStore(new WallPagesStore.MemoryStorage());
        store.replace(WallPagesMigration.seed("home", 0, "home", 2));
        prefs.edit().putInt(WallPagesMigration.KEY_VERSION, WallPagesMigration.VERSION).commit();
        assertFalse(WallPagesMigration.migrateIfNeeded(prefs, 2, store));
        assertEquals("HHT", kinds(store.current()));
    }

    @Test
    public void aFreshSeedHonoursTheAutostartKeyAndRemembersTheDisplayPage() {
        WallPagesStore store = new WallPagesStore(new WallPagesStore.MemoryStorage());
        prefs.edit().putBoolean("x11_display_autostart", true).commit();
        assertTrue(WallPagesMigration.migrateIfNeeded(prefs, 2, store));
        assertEquals("HHTD", kinds(store.current()));
        assertEquals(PageKind.HOME, store.current().home().kind);
        assertEquals(4, prefs.getInt(WallPagesMigration.KEY_DISPLAY_LOOK_PENDING, 0));
    }

    @Test
    public void theUpgradedDisplayPageGetsTheTerminalLookOnce() {
        com.termux.app.look.MemoryLookStore look = new com.termux.app.look.MemoryLookStore();
        String key = com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants
            .TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY;
        look.putInt(key + "@3", 42);
        assertFalse(WallPagesMigration.copyLookToUpgradedDisplay(prefs, look, 3));
        prefs.edit().putInt(WallPagesMigration.KEY_DISPLAY_LOOK_PENDING, 4).commit();
        assertTrue(WallPagesMigration.copyLookToUpgradedDisplay(prefs, look, 3));
        assertEquals(42, look.getInt(key + "@4", -1));
        look.putInt(key + "@4", 7);
        assertFalse(WallPagesMigration.copyLookToUpgradedDisplay(prefs, look, 3));
        assertEquals(7, look.getInt(key + "@4", -1));
    }

    @Test
    public void everyStartInUseAsAndCountCombinationSeedsAValidWall() {
        for (String startIn : START_INS) {
            for (String useAs : USE_ASES) {
                for (int count = 0; count <= 4; count++) {
                    for (int def = -1; def <= 5; def++) {
                        WallPages p = WallPagesMigration.seed(startIn, def, useAs, count);
                        String tag = startIn + "/" + useAs + "/" + count + "/" + def;
                        boolean terminalOnly = !UseAs.read(useAs).showsHomePage() || count == 0;
                        boolean display = "display".equals(StartInPolicy.read(startIn));
                        String tail = display ? "TD" : "T";
                        assertEquals(tag, terminalOnly ? tail : repeat('H', count) + tail, kinds(p));
                        assertFalse(tag, p.homeIsDisplayWindow());
                        assertEquals(tag, terminalOnly ? count : 0, p.detached().size());
                        assertTrue(tag, p.home() != null);
                        assertEquals(tag, display, p.home().kind == PageKind.DISPLAY);
                        if (!terminalOnly && "home".equals(StartInPolicy.read(startIn))) {
                            int want = Math.max(0, Math.min(def, count - 1)) + 1;
                            assertEquals(tag, want, p.home().id);
                        }
                        if (!terminalOnly && "workspace".equals(StartInPolicy.read(startIn))) {
                            assertEquals(tag, PageKind.TERMINAL, p.home().kind);
                        }
                        assertEquals(tag, p.pages().size() + p.detached().size() + 1, p.nextId());
                    }
                }
            }
        }
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    @Test
    public void migrateReadsTheStoredKeysSeedsOnceAndRecordsTheVersion() {
        prefs.edit().putString("app_launcher_start_in", "workspace")
            .putInt("app_launcher_home_default_page", 1).putString("use_as", "home").commit();
        WallPagesStore store = new WallPagesStore(new WallPagesStore.MemoryStorage());
        assertTrue(WallPagesMigration.migrateIfNeeded(prefs, 2, store));
        assertEquals("HHT", kinds(store.current()));
        assertEquals(PageKind.TERMINAL, store.current().home().kind);
        assertEquals(1, prefs.getInt("wall_pages_migration_version", 0));
        // The old keys are not touched.
        assertEquals("workspace", prefs.getString("app_launcher_start_in", null));
    }

    @Test
    public void aSecondRunIsANoOp() {
        WallPagesStore store = new WallPagesStore(new WallPagesStore.MemoryStorage());
        assertTrue(WallPagesMigration.migrateIfNeeded(prefs, 2, store));
        WallPages first = store.current();
        store.replace(first.setHome(2).pages);
        assertFalse(WallPagesMigration.migrateIfNeeded(prefs, 5, store));
        assertEquals(2, store.current().homeId());
        assertEquals("HHT", kinds(store.current()));
    }

    @Test
    public void aStoreThatAlreadyHoldsPagesIsNotOverwritten() {
        WallPagesStore.MemoryStorage storage = new WallPagesStore.MemoryStorage();
        new WallPagesStore(storage).replace(WallPagesMigration.seed("home", 0, "home", 1));
        WallPagesStore store = new WallPagesStore(storage);
        assertTrue(WallPagesMigration.migrateIfNeeded(prefs, 4, store));
        assertEquals("HT", kinds(store.current()));
        assertEquals(1, prefs.getInt("wall_pages_migration_version", 0));
    }

    @Test
    public void anEmptyStoreIsSeededAgainEvenWhenTheVersionIsRecorded() {
        prefs.edit().putString("app_launcher_start_in", "workspace").putInt(
            "wall_pages_migration_version", 1).commit();
        // Nothing stored (an unreadable value is kept instead: see the W7 test below).
        WallPagesStore.MemoryStorage storage = new WallPagesStore.MemoryStorage();
        WallPagesStore store = new WallPagesStore(storage);
        assertTrue(store.current().allPages().isEmpty());
        assertTrue(WallPagesMigration.migrateIfNeeded(prefs, 2, store));
        assertEquals("HHT", kinds(store.current()));
        assertEquals(PageKind.TERMINAL, store.current().home().kind);
        assertFalse(WallPagesMigration.migrateIfNeeded(prefs, 2, store));
    }

    @Test
    public void theHomeCountIsAskedOnlyWhenSeeding() {
        WallPagesStore store = new WallPagesStore(new WallPagesStore.MemoryStorage());
        final int[] asked = {0};
        WallPagesMigration.HomeCount count = () -> { asked[0]++; return 2; };
        assertTrue(WallPagesMigration.migrateIfNeeded(prefs, count, store));
        assertFalse(WallPagesMigration.migrateIfNeeded(prefs, count, store));
        assertEquals(1, asked[0]);
    }

    @Test
    public void aMissingPreferencesStoreRunsNothing() {
        assertFalse(WallPagesMigration.migrateIfNeeded(null, 2,
            new WallPagesStore(new WallPagesStore.MemoryStorage())));
    }

    @Test
    public void unreadableStoredPagesAreKeptAndNotSeededOver() {
        // A newer build's value (version 2) and a broken one (W7): neither is "no pages".
        String[] raws = {"{\"version\":2,\"pages\":[],\"home\":1,\"next\":2}", "{not json"};
        for (String raw : raws) {
            prefs.edit().clear().commit();
            WallPagesStore.MemoryStorage storage = new WallPagesStore.MemoryStorage();
            storage.write(raw);
            WallPagesStore store = new WallPagesStore(storage);
            assertEquals(raw, store.unreadable());
            assertFalse(WallPagesMigration.migrateIfNeeded(prefs, 3, store));
            assertEquals("the stored value is left as it was", raw, storage.read());
            assertEquals("and a copy is kept", raw,
                prefs.getString(WallPagesMigration.KEY_UNREADABLE_BACKUP, null));
            // The run still has a wall: the seed an empty store gets, in memory only.
            WallPagesStore seededStore = new WallPagesStore(new WallPagesStore.MemoryStorage());
            WallPagesMigration.migrateIfNeeded(prefs, 3, seededStore);
            assertFalse("this run is not left without pages", store.current().allPages().isEmpty());
            assertEquals(seededStore.current(), store.current());
            assertTrue(store.isMemoryOnly());
            // A change in this run is not saved over the stored value either.
            store.replace(store.current().separateDisplays());
            store.edit(pages -> pages.rename(pages.allPages().get(0).id, "renamed"));
            assertEquals("later changes keep the stored value too", raw, storage.read());
            prefs.edit().clear().commit();
        }
        // Nothing stored: seeded as before, and nothing is unreadable.
        prefs.edit().clear().commit();
        WallPagesStore fresh = new WallPagesStore(new WallPagesStore.MemoryStorage());
        assertNull(fresh.unreadable());
        assertTrue(WallPagesMigration.migrateIfNeeded(prefs, 3, fresh));
        assertFalse(fresh.current().allPages().isEmpty());
        assertNull(fresh.unreadable());
    }
}
