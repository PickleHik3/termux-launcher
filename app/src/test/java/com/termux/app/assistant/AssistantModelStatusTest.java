package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.ModelAvailability;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AssistantModelStatusTest {
    private static final class FakeSource implements AssistantModelStatus.Source {
        String defaultId = AssistantModelStatus.MODEL_E2B;
        final Set<String> loadedIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final Set<String> loadingIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final Set<String> downloaded = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final Map<String, AssistantModelStatus.Download> downloads = new HashMap<>();

        @NonNull @Override public String defaultModelId() { return defaultId; }
        @NonNull @Override public ModelAvailability.State stateOf(@NonNull String id) {
            if (loadedIds.contains(id)) return ModelAvailability.State.LOADED;
            if (loadingIds.contains(id)) return ModelAvailability.State.LOADING;
            AssistantModelStatus.Download d = downloads.get(id);
            if (d != null && d.inProgress) return ModelAvailability.State.DOWNLOADING;
            return downloaded.contains(id) ? ModelAvailability.State.ON_PHONE : ModelAvailability.State.MISSING;
        }
        @Nullable @Override public AssistantModelStatus.Download download(@NonNull String id) { return downloads.get(id); }
    }

    private static final String E2B = AssistantModelStatus.MODEL_E2B;
    private static final String E4B = AssistantModelStatus.MODEL_E4B;

    private static AssistantModelStatus.Kind kind(AssistantModelStatus.Entry e) {
        return e.state.kind;
    }

    @Test
    public void nothingDownloadedIsNoneForTheDefaultAndNotDownloadedForTheCatalogue() {
        AssistantModelStatus.Snapshot s = AssistantModelStatus.compute(new FakeSource());
        assertEquals(AssistantModelStatus.Kind.NONE, kind(s.defaultModel));
        assertEquals(AssistantModelStatus.Kind.NOT_DOWNLOADED, kind(s.gemmaE2b));
        assertEquals(AssistantModelStatus.Kind.NOT_DOWNLOADED, kind(s.gemmaE4b));
        assertEquals(E2B, s.defaultModel.modelId);
    }

    @Test
    public void defaultNotDownloadedButAnotherIsIsNotDownloadedNotNone() {
        FakeSource src = new FakeSource();
        src.downloaded.add(E4B);
        AssistantModelStatus.Snapshot s = AssistantModelStatus.compute(src);
        assertEquals(AssistantModelStatus.Kind.NOT_DOWNLOADED, kind(s.defaultModel));
        assertEquals(AssistantModelStatus.Kind.DOWNLOADED, kind(s.gemmaE4b));
    }

    @Test
    public void downloadingCarriesPercent() {
        FakeSource src = new FakeSource();
        src.downloads.put(E2B, new AssistantModelStatus.Download(true, 250, 1000));
        AssistantModelStatus.State st = AssistantModelStatus.compute(src).gemmaE2b.state;
        assertEquals(AssistantModelStatus.Kind.DOWNLOADING, st.kind);
        assertEquals(25, st.percent);
        assertEquals(AssistantModelStatus.Kind.DOWNLOADING, kind(AssistantModelStatus.compute(src).defaultModel));
        src.downloads.put(E2B, new AssistantModelStatus.Download(true, 5, 0));
        assertEquals(-1, AssistantModelStatus.compute(src).gemmaE2b.state.percent);
        src.downloads.put(E2B, new AssistantModelStatus.Download(true, 5000, 1000));
        assertEquals(100, AssistantModelStatus.compute(src).gemmaE2b.state.percent);
    }

    @Test
    public void finishedDownloadRecordWithFileIsDownloaded() {
        FakeSource src = new FakeSource();
        src.downloads.put(E2B, new AssistantModelStatus.Download(false, 1000, 1000));
        src.downloaded.add(E2B);
        assertEquals(AssistantModelStatus.Kind.DOWNLOADED, kind(AssistantModelStatus.compute(src).gemmaE2b));
    }

    @Test
    public void loadedModelIsReadyAndOthersStayAsTheyWere() {
        FakeSource src = new FakeSource();
        src.downloaded.add(E2B);
        src.downloaded.add(E4B);
        src.loadedIds.add(E4B);
        AssistantModelStatus.Snapshot s = AssistantModelStatus.compute(src);
        assertEquals(AssistantModelStatus.Kind.READY, kind(s.gemmaE4b));
        assertEquals(AssistantModelStatus.Kind.DOWNLOADED, kind(s.gemmaE2b));
        // The default (E2B) is not the loaded one.
        assertEquals(AssistantModelStatus.Kind.DOWNLOADED, kind(s.defaultModel));
    }

    @Test
    public void loadingIsLoadingWithoutAPercent() {
        FakeSource src = new FakeSource();
        src.downloaded.add(E4B);
        src.loadingIds.add(E4B);
        AssistantModelStatus.Snapshot s = AssistantModelStatus.compute(src);
        assertEquals(AssistantModelStatus.Kind.LOADING, kind(s.gemmaE4b));
        assertEquals(-1, s.gemmaE4b.state.percent);
        assertEquals(AssistantModelStatus.Kind.NOT_DOWNLOADED, kind(s.gemmaE2b));
    }

    @Test
    public void aCustomDefaultModelGetsItsOwnEntry() {
        FakeSource src = new FakeSource();
        src.defaultId = "my-model";
        src.downloaded.add("my-model");
        AssistantModelStatus.Snapshot s = AssistantModelStatus.compute(src);
        assertEquals("my-model", s.defaultModel.modelId);
        assertEquals(AssistantModelStatus.Kind.DOWNLOADED, kind(s.defaultModel));
        assertEquals(AssistantModelStatus.Kind.NOT_DOWNLOADED, kind(s.gemmaE2b));
    }

    @Test
    public void pollerRunsOnlyWhileObserved_andTellsOnlyChanges() throws Exception {
        FakeSource src = new FakeSource();
        List<AssistantModelStatus.Snapshot> heard = new java.util.concurrent.CopyOnWriteArrayList<>();
        AssistantModelStatus status = new AssistantModelStatus(src, null, Runnable::run, Runnable::run);
        AssistantModelStatus.Observer observer = heard::add;
        assertFalse(status.isPolling());
        status.addObserver(observer);
        assertTrue(status.isPolling());
        // The scheduler's own first poll races the test; wait for it, then drive polls by hand.
        long deadline = System.currentTimeMillis() + 3000;
        while (heard.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10);
        int first = heard.size();
        assertEquals(1, first);
        status.pollOnce();
        assertEquals(first, heard.size());
        src.downloaded.add(E2B);
        status.pollOnce();
        assertEquals(first + 1, heard.size());
        assertEquals(AssistantModelStatus.Kind.DOWNLOADED, kind(heard.get(heard.size() - 1).gemmaE2b));
        status.removeObserver(observer);
        assertFalse(status.isPolling());
        src.downloaded.add(E4B);
        status.pollOnce();
        assertEquals(first + 1, heard.size());
    }
}
