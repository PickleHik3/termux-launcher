package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Model availability over a fake backend: the one place the on-phone list and a Feature's state come from. */
public class ModelAvailabilityTest {
    private static final String E2B = TaiModelRegistry.MODEL_GEMMA_4_E2B_IT;
    private static final String E4B = TaiModelRegistry.MODEL_GEMMA_4_E4B_IT;

    private static final class FakeBackend implements ModelAvailability.Backend {
        Map<String, TaiModelSpec> models = new LinkedHashMap<>();
        int reads;
        TaiRuntimePresence.Snapshot presence = TaiRuntimePresence.empty();
        String defaultId = E2B;
        boolean remoteConfigured;
        String pick = "";
        TaiFunctionModels.Resolution resolution = local(null);
        TaiDownloadHub.Snapshot download;
        Runnable onEvent;
        int stops;

        @NonNull @Override public Map<String, TaiModelSpec> readModels() { reads++; return new LinkedHashMap<>(models); }
        @NonNull @Override public TaiRuntimePresence.Snapshot presence() { return presence; }
        @NonNull @Override public TaiFunctionModels.Resolution resolve(@NonNull TaiFunction f) { return resolution; }
        @NonNull @Override public String pick(@NonNull TaiFunction f) { return pick; }
        @Override public boolean remoteConfigured() { return remoteConfigured; }
        @NonNull @Override public String defaultModelId() { return defaultId; }
        @Nullable @Override public TaiDownloadHub.Snapshot download(@NonNull String id) { return download; }
        @NonNull @Override public Runnable watch(@NonNull Runnable e) { onEvent = e; return () -> { stops++; onEvent = null; }; }
    }

    private FakeBackend backend;
    private long now;
    private List<Runnable> posted;
    private ModelAvailability availability;
    private int heard;

    @Before
    public void setUp() {
        backend = new FakeBackend();
        now = 1_000L;
        posted = new ArrayList<>();
        availability = new ModelAvailability(backend, posted::add, () -> now);
    }

    private void runPosted() {
        List<Runnable> run = new ArrayList<>(posted);
        posted.clear();
        for (Runnable r : run) r.run();
    }

    private static TaiModelSpec spec(String id) {
        return new TaiModelSpec(id, id, "test", "test", "/nowhere/" + id, "test", 1L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT)), false, null,
            TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, "test", "int8", 128, 0, null);
    }

    private static TaiFunctionModels.Resolution local(@Nullable String id) {
        return new TaiFunctionModels.Resolution(id, id == null ? null : "cpu", TaiFunctionModels.Source.PICK,
            TaiTierPolicy.WithoutModel.NONE, Collections.<TaiTierPolicy.Choice>emptyList(), false);
    }

    private static TaiFunctionModels.Resolution remote(String name) {
        return new TaiFunctionModels.Resolution(null, null, TaiFunctionModels.Source.PICK,
            TaiTierPolicy.WithoutModel.NONE, Collections.<TaiTierPolicy.Choice>emptyList(), false, name);
    }

    private static TaiDownloadHub.Snapshot download(String modelId, String status, long read, long total)
        throws Exception {
        return new TaiDownloadHub.Snapshot(new JSONObject().put("id", "t-" + modelId).put("modelId", modelId)
            .put("status", status).put("bytesRead", read).put("totalBytes", total), 0.0, -1L, 0);
    }

    // ------------------------------------------------------------------------------- the list

    @Test
    public void modelsAreTheDownloadedAndTheImportedOnesInOneMap() {
        backend.models.put(E2B, spec(E2B));
        backend.models.put("mine", spec("mine"));
        assertEquals(2, availability.models().size());
        assertTrue(availability.isOnPhone(E2B));
        assertTrue(availability.isOnPhone("mine"));
        assertFalse(availability.isOnPhone(E4B));
        assertFalse(availability.isOnPhone(null));
    }

    @Test
    public void theFirstReadIsNeverTakenForAnEmptyCache() {
        // 54495b6df: a cache never read (Long.MIN_VALUE) overflowed to "fresh" and kept the set empty for good.
        assertTrue(ModelAvailability.isStale(1_000L, Long.MIN_VALUE, 5_000L));
        assertTrue(ModelAvailability.isStale(Long.MAX_VALUE / 2, Long.MIN_VALUE, 5_000L));
        assertFalse(ModelAvailability.isStale(10_000L, 6_000L, 5_000L));
        assertTrue(ModelAvailability.isStale(12_000L, 6_000L, 5_000L));
        now = 5L; // right after boot
        backend.models.put(E2B, spec(E2B));
        assertTrue(availability.isOnPhone(E2B));
    }

    @Test
    public void theListIsReadOnceUntilSomethingChangesOrItGrowsOld() {
        backend.models.put(E2B, spec(E2B));
        availability.models();
        availability.models();
        assertEquals(1, backend.reads);
        backend.models.put(E4B, spec(E4B));
        assertFalse("not told, still young", availability.isOnPhone(E4B));
        availability.onModelsChanged();
        assertTrue(availability.isOnPhone(E4B));
        assertEquals(2, backend.reads);
        backend.models.remove(E4B);
        now += ModelAvailability.BACKSTOP_MS + 1;
        assertFalse("the backstop catches a change made behind the store's back", availability.isOnPhone(E4B));
    }

    // --------------------------------------------------------------------------------- states

    @Test
    public void aModelThatIsNowhereIsMissing() {
        assertEquals(ModelAvailability.State.MISSING, availability.stateOf(E2B));
    }

    @Test
    public void aModelOnThePhoneIsOnPhone() {
        backend.models.put(E2B, spec(E2B));
        assertEquals(ModelAvailability.State.ON_PHONE, availability.stateOf(E2B));
    }

    @Test
    public void aLiveOrPausedDownloadIsDownloadingEvenOverAnInstalledFile() throws Exception {
        backend.download = download(E2B, TaiModelStore.STATE_DOWNLOADING, 10, 100);
        assertEquals(ModelAvailability.State.DOWNLOADING, availability.stateOf(E2B));
        backend.download = download(E2B, TaiModelStore.STATE_PAUSED, 10, 100);
        assertEquals(ModelAvailability.State.DOWNLOADING, availability.stateOf(E2B));
        backend.models.put(E2B, spec(E2B));
        assertEquals(ModelAvailability.State.DOWNLOADING, availability.stateOf(E2B));
        backend.download = download(E2B, TaiModelStore.STATE_INSTALLED, 100, 100);
        assertEquals(ModelAvailability.State.ON_PHONE, availability.stateOf(E2B));
    }

    @Test
    public void theLoadedModelIsLoadedAndAScopedIdOfTheSameFileCountsAsIt() {
        backend.models.put(E2B, spec(E2B));
        backend.models.put(E4B, spec(E4B));
        backend.presence = TaiRuntimePresence.snapshotForTest(true, false, false, E4B + "-vision", 0L, 1L);
        assertEquals(ModelAvailability.State.LOADED, availability.stateOf(E4B));
        assertEquals(ModelAvailability.State.ON_PHONE, availability.stateOf(E2B));
    }

    @Test
    public void loadingNamedAndUnnamed() {
        backend.models.put(E2B, spec(E2B));
        backend.models.put(E4B, spec(E4B));
        backend.presence = TaiRuntimePresence.snapshotForTest(false, true, false, E4B, 0L, 1L);
        assertEquals(ModelAvailability.State.LOADING, availability.stateOf(E4B));
        assertEquals(ModelAvailability.State.ON_PHONE, availability.stateOf(E2B));
        // The runtime's file does not always say which model it loads: the Default model takes it.
        backend.presence = TaiRuntimePresence.snapshotForTest(false, true, false, null, 0L, 1L);
        assertEquals(ModelAvailability.State.LOADING, availability.stateOf(E2B));
        assertEquals(ModelAvailability.State.ON_PHONE, availability.stateOf(E4B));
    }

    @Test
    public void aLoadedModelIsNotLoadedWhenTheRuntimeHoldsAnotherOne() {
        backend.models.put(E2B, spec(E2B));
        backend.presence = TaiRuntimePresence.snapshotForTest(true, false, false, E4B, 0L, 1L);
        assertEquals(ModelAvailability.State.ON_PHONE, availability.stateOf(E2B));
    }

    // ------------------------------------------------------------------------------- features

    @Test
    public void forFeature_eachState() throws Exception {
        TaiFunction f = TaiFunction.ASSISTANT;

        backend.resolution = local(null);
        ModelAvailability.FeatureModel none = availability.forFeature(f);
        assertNull(none.modelId);
        assertEquals(ModelAvailability.State.MISSING, none.state);

        backend.resolution = local(E2B);
        assertEquals(ModelAvailability.State.MISSING, availability.forFeature(f).state);
        assertEquals(E2B, availability.forFeature(f).modelId);

        backend.download = download(E2B, TaiModelStore.STATE_QUEUED, 0, 100);
        assertEquals(ModelAvailability.State.DOWNLOADING, availability.forFeature(f).state);

        backend.download = null;
        backend.models.put(E2B, spec(E2B));
        availability.onModelsChanged();
        assertEquals(ModelAvailability.State.ON_PHONE, availability.forFeature(f).state);

        backend.presence = TaiRuntimePresence.snapshotForTest(false, true, false, E2B, 0L, 1L);
        assertEquals(ModelAvailability.State.LOADING, availability.forFeature(f).state);

        backend.presence = TaiRuntimePresence.snapshotForTest(true, false, false, E2B, 0L, 1L);
        assertEquals(ModelAvailability.State.LOADED, availability.forFeature(f).state);

        backend.resolution = remote("remote/gpt");
        backend.remoteConfigured = true;
        ModelAvailability.FeatureModel ready = availability.forFeature(f);
        assertEquals(ModelAvailability.State.REMOTE_READY, ready.state);
        assertEquals("remote/gpt", ready.modelId);

        // The pick is the remote model but the provider is not set up: resolve fell through to a local one.
        backend.remoteConfigured = false;
        backend.pick = "remote/gpt";
        backend.resolution = local(E2B);
        ModelAvailability.FeatureModel notSetUp = availability.forFeature(f);
        assertEquals(ModelAvailability.State.REMOTE_NOT_SET_UP, notSetUp.state);
        assertEquals("remote/gpt", notSetUp.modelId);
    }

    // ------------------------------------------------------------------------------ listeners

    @Test
    public void aStoreWriteTellsTheListenersOnTheMainThread() {
        availability.addListener(() -> heard++);
        assertNotNull(backend.onEvent);
        backend.models.put(E2B, spec(E2B));
        availability.onModelsChanged();
        assertEquals("delivery waits for the main thread", 0, heard);
        runPosted();
        assertEquals(1, heard);
        assertTrue(availability.isOnPhone(E2B));
    }

    @Test
    public void aDownloadEventAndAPresenceChangeBothTellTheListeners() {
        availability.addListener(() -> heard++);
        backend.onEvent.run();   // the download hub's listener or the presence broadcast
        runPosted();
        assertEquals(1, heard);
        backend.onEvent.run();
        backend.onEvent.run();   // coalesced while one is waiting
        runPosted();
        assertEquals(2, heard);
    }

    @Test
    public void theLastListenerOutStopsTheWatching() {
        ModelAvailability.Listener a = () -> heard++;
        ModelAvailability.Listener b = () -> heard++;
        availability.addListener(a);
        availability.addListener(b);
        availability.removeListener(a);
        assertEquals(0, backend.stops);
        availability.removeListener(b);
        assertEquals(1, backend.stops);
        availability.onModelsChanged();
        assertTrue("nobody to tell", posted.isEmpty());
    }
}
