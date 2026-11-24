package com.termux.launcherctl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.layout.LayoutStore;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The layout documents as registry ids: refused as the {@code layout} verb refuses, saved on main. */
public class LayoutSettingsAdapterTest {

    private final Map<String, String> written = new HashMap<>();
    private final LayoutStore store = new LayoutStore(new LayoutStore.Storage() {
        @Nullable @Override public String read(@NonNull String key) { return written.get(key); }
        @Override public void write(@NonNull Map<String, String> documents) {
            written.putAll(documents);
        }
    }, false);
    private int redressed;

    /** A stand-in main thread: one named thread every call is moved onto. */
    private final ExecutorService mainThread = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "fake-main");
        thread.setDaemon(true);
        return thread;
    });
    private final java.util.List<String> ranOn = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final LayoutSettingsAdapter.Main main = new LayoutSettingsAdapter.Main() {
        @Override
        public <T> T call(@NonNull Callable<T> work) throws Exception {
            return mainThread.submit(() -> {
                ranOn.add(Thread.currentThread().getName());
                return work.call();
            }).get();
        }
    };

    private final SettingsRegistry registry = new SettingsRegistry(which -> {
        throw new AssertionError("a layout id reads no registry store");
    }, Collections.emptyList(), Collections.singletonList(
        new LayoutSettingsAdapter(store, main, () -> redressed++)));

    private boolean searchField(String kind) throws Exception {
        return new JSONObject(LayoutCommand.get(store, kind)).optBoolean("search_field", false);
    }

    @Test
    public void aFieldIsSavedIntoTheDocumentAndTheOthersAreLeft() throws Exception {
        store.load();
        SettingsRegistry.Change change = registry.set("layout.home.search_field", "true").change();
        assertNotNull(change);
        assertEquals("true", change.newValue);
        assertTrue(searchField("home"));
        assertTrue(written.containsKey("home"));
        assertNull("the Terminal's document is not rewritten", written.get("terminal"));
        assertEquals("true", registry.get("layout.home.search_field"));
        assertEquals(1, redressed);
    }

    @Test
    public void anOrientationsFieldIsReachedByItsSectionName() throws Exception {
        store.load();
        SettingsRegistry.Change change =
            registry.set("layout.home.portrait.status_bar", "bottom").change();
        assertNotNull(registry.get("layout.home.portrait.status_bar"));
        assertEquals("bottom", registry.get("layout.home.portrait.status_bar"));
        assertEquals("bottom", change.newValue);
    }

    @Test
    public void aBadValueIsRefusedByNameAndNothingIsWritten() {
        store.load();
        SettingsRegistry.Refusal refusal =
            registry.set("layout.display.search_field", "yes").refusal();
        assertNotNull(refusal);
        assertTrue(refusal.reason, refusal.reason.contains("search_field"));
        assertTrue(written.isEmpty());
        assertEquals(0, redressed);
    }

    @Test
    public void aValueOutsideItsRangeIsRefusedWithTheVerbsWords() {
        store.load();
        SettingsRegistry.Refusal refusal =
            registry.set("layout.sizes.portrait.keyboard_height", "900").refusal();
        assertNotNull(refusal);
        assertTrue(refusal.reason, refusal.reason.startsWith("portrait.keyboard_height: 900 is outside"));
        assertEquals(LayoutCommand.refusal("sizes",
            "{\"version\":1,\"portrait\":{\"keyboard_height\":900}}"), refusal.reason);
        assertTrue(written.isEmpty());
    }

    @Test
    public void theIconSizeIsASizesFieldWithItsClosedSet() throws Exception {
        store.load();
        assertNotNull(registry.set("layout.sizes.icon_size", "l").change());
        assertEquals("l", registry.get("layout.sizes.icon_size"));
        SettingsRegistry.Refusal refusal = registry.set("layout.sizes.icon_size", "huge").refusal();
        assertTrue(refusal.reason, refusal.reason.contains("s, m, l, xl"));
    }

    @Test
    public void aFieldNoDocumentHasIsRefusedNotSavedAsAnUnknownField() {
        store.load();
        SettingsRegistry.Refusal refusal = registry.set("layout.home.sparkle", "1").refusal();
        assertNotNull(refusal);
        assertTrue(refusal.reason, refusal.reason.contains("sparkle"));
        assertTrue(written.isEmpty());
    }

    @Test
    public void checkRefusesAsSetDoesAndWritesNothing() {
        store.load();
        assertTrue(registry.check("layout.display.search_field", "yes").reason
            .contains("search_field"));
        assertNull(registry.check("layout.display.search_field", "true"));
        assertTrue(written.isEmpty());
        assertEquals(0, redressed);
    }

    @Test
    public void anUnknownDocumentIsNoSetting() {
        assertNull(registry.declaration("layout.page2.search_field"));
        assertNull(registry.declaration("layout.home"));
        assertNull(registry.declaration("layout.home."));
    }

    @Test
    public void aSaveFromABackgroundThreadLandsOnTheMainThreadWithoutTheCallerPosting() {
        store.load();
        String caller = Thread.currentThread().getName();
        assertNotNull(registry.set("layout.home.search_field", "true").change());
        assertFalse(ranOn.isEmpty());
        for (String thread : ranOn) {
            assertEquals("fake-main", thread);
            assertFalse(thread.equals(caller));
        }
    }

    @Test
    public void aTimedOutWaitCancelsTheTaskSoALateSaveCannotLand() throws Exception {
        java.util.concurrent.FutureTask<String> task =
            new java.util.concurrent.FutureTask<>(() -> "saved");
        try {
            LayoutSettingsAdapter.await(task, 10, java.util.concurrent.TimeUnit.MILLISECONDS);
            org.junit.Assert.fail("a task nobody ran must time out");
        } catch (java.util.concurrent.TimeoutException expected) {
            assertTrue(task.isCancelled());
        }
        task.run();
        assertTrue("a cancelled task does nothing when the main thread gets to it", task.isCancelled());
    }

    @Test
    public void aFailureInsideTheWorkComesOutAsItsOwnException() throws Exception {
        java.util.concurrent.FutureTask<String> task = new java.util.concurrent.FutureTask<>(() -> {
            throw new org.json.JSONException("bad document");
        });
        task.run();
        try {
            LayoutSettingsAdapter.await(task, 1, java.util.concurrent.TimeUnit.SECONDS);
            org.junit.Assert.fail("the work failed");
        } catch (org.json.JSONException expected) {
            assertEquals("bad document", expected.getMessage());
        }
    }

    @Test
    public void whenTheMainThreadDoesNotAnswerTheRefusalIsPlainCopy() {
        SettingsRegistry silent = new SettingsRegistry(which -> {
            throw new AssertionError("a layout id reads no registry store");
        }, Collections.emptyList(), Collections.singletonList(new LayoutSettingsAdapter(store,
            new LayoutSettingsAdapter.Main() {
                @Override public <T> T call(@NonNull Callable<T> work) throws Exception {
                    throw new java.util.concurrent.TimeoutException("java.util.concurrent noise");
                }
            }, () -> { })));
        assertEquals(LayoutSettingsAdapter.NOT_ANSWERED,
            silent.set("layout.home.search_field", "true").refusal().reason);
        assertEquals(LayoutSettingsAdapter.NOT_ANSWERED,
            silent.check("layout.home.search_field", "true").reason);
    }

    @Test
    public void aSettingsFileCarriesTheFieldsTheDocumentsHoldAndNotTheAbsentOnes() {
        store.load();
        registry.set("layout.home.search_field", "true");
        String file = com.termux.shared.termux.settings.registry.SettingsFile.export(registry);
        assertTrue(file, file.contains("\nlayout.home.search_field = true"));
        assertFalse(file, file.contains("layout.sizes.icon_size"));
        com.termux.shared.termux.settings.registry.SettingsFile.Result result =
            com.termux.shared.termux.settings.registry.SettingsFile.check(
                "layout.home.search_field = false", registry);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
    }

    @Test
    public void anApplyOfManyLayoutLinesMakesOneHopOntoTheMainThread() throws Exception {
        store.load();
        int[] hops = {0};
        int[] depth = {0};
        LayoutSettingsAdapter.Main counting = new LayoutSettingsAdapter.Main() {
            @Override public <T> T call(@NonNull Callable<T> work) throws Exception {
                if (depth[0]++ == 0) hops[0]++;
                try {
                    return work.call();
                } finally {
                    depth[0]--;
                }
            }
        };
        SettingsRegistry counted = new SettingsRegistry(which -> {
            throw new AssertionError("a layout id reads no registry store");
        }, Collections.emptyList(), Collections.singletonList(
            new LayoutSettingsAdapter(store, counting, () -> redressed++)));
        com.termux.shared.termux.settings.registry.SettingsApplier applier =
            new com.termux.shared.termux.settings.registry.SettingsApplier(
                new com.termux.shared.termux.settings.registry.SettingsApplier.Host() {
                    @Override public boolean isLauncherResumed() { return true; }

                    @Override public void run(
                        @NonNull com.termux.shared.termux.settings.registry.SettingRefresh r) { }
                });
        JSONObject answer = ConfigCommand.handleInOneHop(counted, applier, new JSONObject()
            .put("op", "apply").put("text",
                "layout.home.search_field = true\nlayout.display.search_field = true\n"
                    + "layout.terminal.search_field = true"), counting);
        assertTrue(answer.toString(), answer.getBoolean("ok"));
        assertEquals(1, hops[0]);
        assertEquals(3, redressed);
    }

    @Test
    public void listHoldsTheLayoutIdsWithTheirCurrentValues() {
        store.load();
        registry.set("layout.home.search_field", "true");
        SettingsRegistry.Entry found = null;
        boolean sizes = false;
        for (SettingsRegistry.Entry entry : registry.list()) {
            if (entry.declaration.id.equals("layout.home.search_field")) found = entry;
            if (entry.declaration.id.startsWith("layout.sizes.")) sizes = true;
        }
        assertNotNull(found);
        assertEquals("true", found.value);
        assertTrue(sizes);
    }
}
