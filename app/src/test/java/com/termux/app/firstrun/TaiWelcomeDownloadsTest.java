package com.termux.app.firstrun;

import static org.junit.Assert.assertEquals;

import com.termux.ai.TaiModelStore;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** What the closing card's downloads line adds up, from the download layer's records. */
public class TaiWelcomeDownloadsTest {

    private static final List<String> QUEUED = Arrays.asList("small", "large");
    private static final TaiWelcomeCard.Sizes SIZES = id -> "small".equals(id) ? 100L : 900L;

    private static TaiWelcomeDownloads.Progress progress(
            Map<String, TaiWelcomeDownloads.ModelState> states) {
        return TaiWelcomeDownloads.progress(QUEUED, SIZES, states::get);
    }

    @Test public void nothingStartedIsNothingDoneOfTheCatalogueSizes() {
        TaiWelcomeDownloads.Progress progress = progress(Collections.emptyMap());
        assertEquals(0L, progress.doneBytes);
        assertEquals(1000L, progress.totalBytes);
        assertEquals(0f, progress.fraction(), 0f);
    }

    @Test public void anInstalledModelCountsWholeAndALiveOneByItsBytes() {
        Map<String, TaiWelcomeDownloads.ModelState> states = new HashMap<>();
        states.put("small", new TaiWelcomeDownloads.ModelState(TaiModelStore.STATE_INSTALLED, 0L,
            0L));
        states.put("large", new TaiWelcomeDownloads.ModelState(TaiModelStore.STATE_DOWNLOADING,
            300L, 900L));
        TaiWelcomeDownloads.Progress progress = progress(states);
        assertEquals(400L, progress.doneBytes);
        assertEquals(1000L, progress.totalBytes);
        assertEquals(0.4f, progress.fraction(), 0.0001f);
    }

    @Test public void aRecordThatKnowsItsSizeOverridesTheCatalogue() {
        Map<String, TaiWelcomeDownloads.ModelState> states = new HashMap<>();
        states.put("large", new TaiWelcomeDownloads.ModelState(TaiModelStore.STATE_DOWNLOADING,
            5_000L, 2_000L));
        TaiWelcomeDownloads.Progress progress = progress(states);
        assertEquals("never more done than the file holds", 2_000L, progress.doneBytes);
        assertEquals(2_100L, progress.totalBytes);
    }

    @Test public void theQueuedIdsSurviveTheStoreRoundTrip() {
        List<String> ids = Arrays.asList("whisper-acft-small-en", "kitten-tts-nano");
        assertEquals(ids, TaiWelcomeDownloads.IdList.split(TaiWelcomeDownloads.IdList.join(ids)));
        assertEquals(Collections.emptyList(), TaiWelcomeDownloads.IdList.split(""));
        assertEquals(Collections.emptyList(), TaiWelcomeDownloads.IdList.split(null));
    }
}
