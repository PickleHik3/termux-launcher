package com.termux.launcherctl;

import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class LauncherCtlNotificationStoreTest {
    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final long NOW = 1_800_000_000_000L;

    private File tempDir;
    private LauncherCtlNotificationStore store;

    @Before
    public void setUp() {
        tempDir = new File(ApplicationProvider.getApplicationContext().getFilesDir(),
            "notif-store-test-" + System.nanoTime());
        LauncherCtlStorage.setBaseDirForTesting(tempDir);
        LauncherCtlNotificationStore.resetForTesting();
        store = LauncherCtlNotificationStore.getInstance();
    }

    @After
    public void tearDown() {
        LauncherCtlNotificationStore.resetForTesting();
        LauncherCtlStorage.clearTestBaseDir();
        deleteRecursively(tempDir);
    }

    @Test
    public void insert_storesTheRichFields() {
        List<LauncherCtlNotificationEvent> rows = LauncherCtlNotificationEvent.explode(
            "k1", "com.mail", "Mail", "email", "inbox", "Team", "Alice", "Lunch?", "Lunch at noon?",
            "me@work", NOW, null, null);

        assertEquals(1, store.insertPosted(rows, false, 30, NOW));

        LauncherCtlNotificationEvent row = store.query(new LauncherCtlNotificationStore.Filter()).get(0);
        assertEquals("com.mail", row.packageName);
        assertEquals("Mail", row.appLabel);
        assertEquals("Team", row.conversation);
        assertEquals("Alice", row.title);
        assertEquals("Lunch at noon?", row.text);
        assertEquals("me@work", row.subText);
        assertEquals("email", row.category);
        assertEquals("inbox", row.channelId);
        assertEquals(0L, row.removedTime);
    }

    @Test
    public void insert_dedupesAnIdenticalRepostOfTheSameKey() {
        assertEquals(1, store.insertPosted(plain("k1", "com.mail", "Hi", "Body", NOW), false, 30, NOW));
        assertEquals(0, store.insertPosted(plain("k1", "com.mail", "Hi", "Body", NOW + 5_000), false, 30, NOW + 5_000));
        assertEquals(1, store.countAll());
    }

    @Test
    public void insert_changedContentUnderTheSameKeyIsANewRow() {
        store.insertPosted(plain("k1", "com.mail", "Hi", "Body", NOW), false, 30, NOW);
        store.insertPosted(plain("k1", "com.mail", "Hi", "Body, edited", NOW + 5_000), false, 30, NOW + 5_000);
        assertEquals(2, store.countAll());
    }

    @Test
    public void insert_samePlainContentAfterItLeftTheShadeIsRecordedAgain() {
        store.insertPosted(plain("k1", "com.app", "Backup", "Done", NOW), false, 30, NOW);
        store.markRemoved("k1", NOW + 1_000);

        int later = store.insertPosted(plain("k1", "com.app", "Backup", "Done", NOW + DAY),
            false, 30, NOW + DAY);

        assertEquals(1, later);
        assertEquals(2, store.countAll());
    }

    @Test
    public void insert_aChatLineWithItsOwnTimeIsNotRecordedAgainAfterRemoval() {
        List<LauncherCtlNotificationEvent.Message> messages = Collections.singletonList(
            new LauncherCtlNotificationEvent.Message("Bob", "on my way", NOW - 60_000));
        store.insertPosted(chat("k1", messages, NOW), false, 30, NOW);
        store.markRemoved("k1", NOW + 1_000);

        assertEquals(0, store.insertPosted(chat("k1", messages, NOW + DAY), false, 30, NOW + DAY));
        assertEquals(1, store.countAll());
    }

    @Test
    public void insert_sameContentFromAnotherKeyInsideTheWindowIsOneMessage() {
        store.insertPosted(plain("summary", "com.mail", "Hi", "Body", NOW), false, 30, NOW);
        assertEquals(0, store.insertPosted(plain("child", "com.mail", "Hi", "Body", NOW + 1_000),
            false, 30, NOW + 1_000));
        long farLater = NOW + LauncherCtlNotificationStore.CROSS_KEY_DEDUPE_MS + 1_000;
        assertEquals(1, store.insertPosted(plain("child2", "com.mail", "Hi", "Body", farLater),
            false, 30, farLater));
    }

    @Test
    public void explode_inboxLinesBecomeOneRowEachWithASharedKey() {
        List<LauncherCtlNotificationEvent> rows = LauncherCtlNotificationEvent.explode(
            "gmail:1", "com.google.android.gm", "Gmail", "email", "mail", null, "3 new messages",
            "3 new messages", null, "me@work.com", NOW,
            Arrays.asList("Ann  Invoice due", "Ben  Standup notes", " "), null);

        assertEquals(2, rows.size());
        assertEquals("gmail:1", rows.get(0).key);
        assertEquals("gmail:1", rows.get(1).key);
        assertEquals("Ann  Invoice due", rows.get(0).text);
        assertEquals("Ben  Standup notes", rows.get(1).text);

        store.insertPosted(rows, false, 30, NOW);
        assertEquals(2, store.countAll());
    }

    @Test
    public void explode_messagingStyleMessagesCarrySenderAndTheirOwnTime() {
        List<LauncherCtlNotificationEvent> rows = LauncherCtlNotificationEvent.explode(
            "wa:1", "com.whatsapp", "WhatsApp", "msg", "chats", "Project chat", "Project chat",
            "ignored", null, null, NOW, null,
            Arrays.asList(
                new LauncherCtlNotificationEvent.Message("Ann", "hello", NOW - 120_000),
                new LauncherCtlNotificationEvent.Message("Ben", "hi all", NOW - 60_000)));

        assertEquals(2, rows.size());
        assertEquals("Ann", rows.get(0).sender);
        assertEquals("hello", rows.get(0).text);
        assertEquals(NOW - 120_000, rows.get(0).messageTime);
        assertEquals("Project chat", rows.get(1).conversation);
        assertTrue(rows.get(1).toLine().contains("Ben: hi all"));
    }

    @Test
    public void explode_plainNotificationPrefersTheExpandedText() {
        List<LauncherCtlNotificationEvent> rows = LauncherCtlNotificationEvent.explode(
            "k", "p", "P", null, null, null, "Title", "short", "the long expanded text", null,
            NOW, null, null);
        assertEquals(1, rows.size());
        assertEquals("the long expanded text", rows.get(0).text);
    }

    @Test
    public void remove_isATimestampOnTheExistingRowNotANewRow() {
        store.insertPosted(plain("k1", "com.mail", "Hi", "Body", NOW), false, 30, NOW);

        store.markRemoved("k1", NOW + 9_000);

        assertEquals(1, store.countAll());
        assertEquals(NOW + 9_000,
            store.query(new LauncherCtlNotificationStore.Filter()).get(0).removedTime);
        // A second removal must not move the first one.
        store.markRemoved("k1", NOW + 20_000);
        assertEquals(NOW + 9_000,
            store.query(new LauncherCtlNotificationStore.Filter()).get(0).removedTime);
    }

    @Test
    public void retention_dropsRowsOlderThanTheWindowOnWrite() {
        store.insertPosted(plain("old", "com.app", "Old", "x", NOW - 40 * DAY), false, 30, NOW - 40 * DAY);
        store.insertPosted(plain("mid", "com.app", "Mid", "y", NOW - 10 * DAY), false, 30, NOW - 10 * DAY);

        store.insertPosted(plain("new", "com.app", "New", "z", NOW), false, 30, NOW);

        List<LauncherCtlNotificationEvent> rows = store.query(new LauncherCtlNotificationStore.Filter());
        assertEquals(2, rows.size());
        assertEquals("New", rows.get(0).title);
        assertEquals("Mid", rows.get(1).title);
    }

    @Test
    public void retention_pruneRunsAtMostOncePerHour() {
        long start = NOW - 40 * DAY;
        store.insertPosted(plain("old", "com.app", "Old", "x", start), false, 30, start);
        // The first write pruned (nothing to drop). Ten minutes on, the row is past a 1 day window
        // but the hourly gate has not opened yet, so it stays.
        long soon = start + TimeUnit.MINUTES.toMillis(10);
        store.insertPosted(plain("b", "com.app", "B", "y", soon), false, 0, soon);
        assertEquals(2, store.countAll());
        // Past the hour it is pruned; a retention of 1 day is the floor.
        long later = start + DAY + TimeUnit.HOURS.toMillis(2);
        store.insertPosted(plain("c", "com.app", "C", "z", later), false, 1, later);
        assertEquals(1, store.countAll());
    }

    @Test
    public void mask_hidesCodesAtWriteTimeOnlyWhenAsked() {
        store.insertPosted(plain("k1", "com.bank", "Bank", "Your code is 482913", NOW), true, 30, NOW);
        store.insertPosted(plain("k2", "com.bank", "Bank", "Your code is 555111", NOW + 1_000), false, 30, NOW + 1_000);

        List<LauncherCtlNotificationEvent> rows = store.query(new LauncherCtlNotificationStore.Filter());
        assertEquals("Your code is 555111", rows.get(0).text);
        assertEquals("Your code is " + LauncherCtlNotificationMasker.MASK, rows.get(1).text);
    }

    @Test
    public void query_filtersByAppLabelTimeAndTextNewestFirst() {
        store.insertPosted(plain("a", "com.mail", "Mail", "Invoice 7", "Alpha", NOW - 3 * DAY), false, 30, NOW);
        store.insertPosted(plain("b", "com.mail", "Mail", "Standup", "Beta", NOW - 1 * DAY), false, 30, NOW);
        store.insertPosted(plain("c", "com.chat", "Chat", "Hey", "Gamma", NOW - 2 * DAY), false, 30, NOW);

        LauncherCtlNotificationStore.Filter filter = new LauncherCtlNotificationStore.Filter();
        filter.app = "mail";
        List<LauncherCtlNotificationEvent> byLabel = store.query(filter);
        assertEquals(2, byLabel.size());
        assertEquals("Beta", byLabel.get(0).text);

        filter.app = "com.chat";
        assertEquals(1, store.query(filter).size());

        filter = new LauncherCtlNotificationStore.Filter();
        filter.sinceMs = NOW - 2 * DAY - 1;
        filter.untilMs = NOW - DAY + 1;
        assertEquals(2, store.query(filter).size());

        filter = new LauncherCtlNotificationStore.Filter();
        filter.query = "100%";
        assertEquals(0, store.query(filter).size());
        filter.query = "invoice";
        assertEquals(1, store.query(filter).size());
    }

    @Test
    public void queryApps_countsPerAppWithLastSeen() throws Exception {
        store.insertPosted(plain("a", "com.mail", "Mail", "One", NOW - 2_000), false, 30, NOW);
        store.insertPosted(plain("b", "com.mail", "Mail", "Two", NOW - 1_000), false, 30, NOW);
        store.insertPosted(plain("c", "com.chat", "Chat", "Hey", NOW - 5_000), false, 30, NOW);

        org.json.JSONArray apps = store.queryApps();

        assertEquals(2, apps.length());
        assertEquals("com.mail", apps.getJSONObject(0).getString("package"));
        assertEquals("mail", apps.getJSONObject(0).getString("app"));
        assertEquals(2, apps.getJSONObject(0).getLong("count"));
        assertEquals(NOW - 1_000, apps.getJSONObject(0).getLong("lastSeen"));
    }

    @Test
    public void clear_removesOneAppOrEverything() {
        store.insertPosted(plain("a", "com.mail", "Mail", "One", NOW), false, 30, NOW);
        store.insertPosted(plain("c", "com.chat", "Chat", "Hey", NOW), false, 30, NOW);

        assertEquals(1, store.clear("Mail"));
        assertEquals(1, store.countAll());
        assertEquals(1, store.clear(null));
        assertEquals(0, store.countAll());
    }

    @Test
    public void migration_dropsTheOldTableAndLeftoverJsonl() throws Exception {
        File dbFile = LauncherCtlStorage.getDatabaseFile();
        SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(dbFile.getAbsolutePath(), null);
        old.execSQL("CREATE TABLE notification_events (id INTEGER PRIMARY KEY, event_type TEXT, payload TEXT)");
        old.execSQL("INSERT INTO notification_events (event_type, payload) VALUES ('posted', '{}')");
        old.close();
        File jsonl = LauncherCtlStorage.getLegacyNotificationsJsonlFile();
        assertTrue(jsonl.createNewFile());
        File rotated = new File(jsonl.getParentFile(), jsonl.getName() + ".1");
        assertTrue(rotated.createNewFile());

        assertEquals(0, store.countAll());
        store.insertPosted(plain("k", "com.mail", "Hi", "Body", NOW), false, 30, NOW);
        assertEquals(1, store.countAll());

        assertFalse(jsonl.exists());
        assertFalse(rotated.exists());
        store.close();
        SQLiteDatabase after = SQLiteDatabase.openOrCreateDatabase(dbFile.getAbsolutePath(), null);
        try (android.database.Cursor cursor = after.rawQuery(
                "SELECT name FROM sqlite_master WHERE name = 'notification_events'", null)) {
            assertFalse(cursor.moveToFirst());
        } finally {
            after.close();
        }
    }

    private static List<LauncherCtlNotificationEvent> plain(String key, String pkg, String title,
                                                            String text, long postTime) {
        return plain(key, pkg, pkg.substring(pkg.lastIndexOf('.') + 1), title, text, postTime);
    }

    private static List<LauncherCtlNotificationEvent> plain(String key, String pkg, String label,
                                                            String title, String text, long postTime) {
        return LauncherCtlNotificationEvent.explode(key, pkg, label, null, null, null, title, text,
            null, null, postTime, null, null);
    }

    private static List<LauncherCtlNotificationEvent> chat(String key,
            List<LauncherCtlNotificationEvent.Message> messages, long postTime) {
        return LauncherCtlNotificationEvent.explode(key, "com.chat", "Chat", "msg", null, "Group",
            "Group", null, null, null, postTime, null, new ArrayList<>(messages));
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
