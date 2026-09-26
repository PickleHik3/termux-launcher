package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import com.termux.ai.TaiModelStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Arrays;
import java.util.EnumSet;

/**
 * The Model centre's row-state mapping: one download record in, the row's pill, meta line, bar and
 * buttons out. Each test names the moment from the design's interaction table it pins down.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TaiModelCentreRowsTest {
    private static final long MB = 1024L * 1024L;
    private static final long GB = 1024L * MB;
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    private static TaiModelCentreRows.Input input(String status) {
        TaiModelCentreRows.Input input = new TaiModelCentreRows.Input();
        input.status = status;
        return input;
    }

    @Test
    public void aDownloadingRowShowsBytesSpeedAndTimeLeftWithPauseAndCancel() {
        TaiModelCentreRows.Input input = input(TaiModelStore.STATE_DOWNLOADING);
        input.bytesRead = 2 * GB + 266 * MB;
        input.totalBytes = 3 * GB + 717 * MB;
        input.bytesPerSecond = 8.4 * MB;
        input.etaSeconds = 180;

        TaiModelCentreRows.State state = TaiModelCentreRows.stateFor(context, input);

        assertEquals(TaiModelCentreRows.Phase.DOWNLOADING, state.phase);
        // The bar and the meta line say it all; a pill would only repeat them.
        assertEquals("", state.pill);
        assertEquals("2.3 GB / 3.7 GB · 8.4 MB/s", state.metaStart);
        assertEquals("~3 min", state.metaEnd);
        assertEquals(TaiModelCentreRows.Bar.DETERMINATE, state.bar);
        assertEquals(6107, state.progress);
        assertEquals(EnumSet.of(TaiModelCentreRows.Action.PAUSE, TaiModelCentreRows.Action.CANCEL), state.actions);
    }

    @Test
    public void aDownloadWithNoSizeYetSweepsAndSaysItIsStarting() {
        TaiModelCentreRows.State state = TaiModelCentreRows.stateFor(context, input(TaiModelStore.STATE_DOWNLOADING));
        assertEquals(TaiModelCentreRows.Bar.INDETERMINATE, state.bar);
        assertEquals("starting…", state.metaStart);
        assertEquals("", state.metaEnd);
    }

    @Test
    public void aQueuedRowWaitsInLineAndOffersStartNowNotPause() {
        TaiModelCentreRows.Input input = input(TaiModelStore.STATE_QUEUED);
        input.queuePosition = 1;
        TaiModelCentreRows.State first = TaiModelCentreRows.stateFor(context, input);
        assertEquals(TaiModelCentreRows.Phase.WAITING, first.phase);
        assertEquals("Waiting · 1st in line", first.pill);
        assertEquals(EnumSet.of(TaiModelCentreRows.Action.START_NOW, TaiModelCentreRows.Action.CANCEL), first.actions);

        input.queuePosition = 2;
        assertEquals("Waiting · 2nd in line", TaiModelCentreRows.stateFor(context, input).pill);
        input.queuePosition = 0;
        assertEquals("Waiting", TaiModelCentreRows.stateFor(context, input).pill);
    }

    @Test
    public void aPauseAfterTheAppClosedSaysSoAndOffersResume() {
        TaiModelCentreRows.Input input = input(TaiModelStore.STATE_PAUSED);
        input.pausedReason = TaiModelStore.PAUSED_APP_CLOSED;
        input.bytesRead = 604 * MB;
        input.totalBytes = 2150 * MB;

        TaiModelCentreRows.State state = TaiModelCentreRows.stateFor(context, input);

        assertEquals(TaiModelCentreRows.Phase.PAUSED, state.phase);
        assertEquals("Paused · app closed", state.pill);
        assertEquals(TaiModelCentreRows.Tone.WARN, state.tone);
        assertEquals("604.0 MB / 2.1 GB · paused when the app closed", state.metaStart);
        // The bar freezes where it stopped; nothing restarts from zero.
        assertEquals(TaiModelCentreRows.Bar.DETERMINATE, state.bar);
        assertEquals(2809, state.progress);
        assertEquals(EnumSet.of(TaiModelCentreRows.Action.RESUME, TaiModelCentreRows.Action.CANCEL), state.actions);
    }

    @Test
    public void aPauseForSpaceNamesWhatItNeedsAndWhatIsFree() {
        TaiModelCentreRows.Input input = input(TaiModelStore.STATE_PAUSED);
        input.pausedReason = TaiModelStore.PAUSED_NO_SPACE;
        input.requiredBytes = 3789 * MB;
        input.freeBytes = 1229 * MB;

        TaiModelCentreRows.State state = TaiModelCentreRows.stateFor(context, input);

        assertEquals("Paused · no space", state.pill);
        assertEquals("Needs 3.7 GB, 1.2 GB free", state.metaStart);
    }

    @Test
    public void aPauseByThePersonIsPlainPaused() {
        TaiModelCentreRows.Input input = input(TaiModelStore.STATE_PAUSED);
        input.pausedReason = TaiModelStore.PAUSED_USER;
        assertEquals("Paused", TaiModelCentreRows.stateFor(context, input).pill);
        assertEquals("paused by you", TaiModelCentreRows.stateFor(context, input).metaStart);
    }

    @Test
    public void aSwapOutPauseReadsAsWaitingBecauseTheEngineRequeuesItAtOnce() {
        TaiModelCentreRows.Input input = input(TaiModelStore.STATE_PAUSED);
        input.pausedReason = "swap_out";
        assertEquals(TaiModelCentreRows.Phase.WAITING, TaiModelCentreRows.phaseOf(input));
    }

    @Test
    public void checkingTheFileShowsTheHashProgressAndOnlyCancel() {
        TaiModelCentreRows.Input input = input(TaiModelStore.STATE_VERIFYING);
        input.bytesRead = 273 * MB;
        input.totalBytes = 273 * MB;

        TaiModelCentreRows.State before = TaiModelCentreRows.stateFor(context, input);
        assertEquals("Checking file…", before.pill);
        assertEquals("checking file… sha256", before.metaStart);
        // Nothing hashed yet: a sweep, not a full bar that would read as done.
        assertEquals(TaiModelCentreRows.Bar.INDETERMINATE, before.bar);
        assertEquals(EnumSet.of(TaiModelCentreRows.Action.CANCEL), before.actions);

        input.verifiedBytes = 136 * MB + MB / 2;
        TaiModelCentreRows.State during = TaiModelCentreRows.stateFor(context, input);
        assertEquals(TaiModelCentreRows.Bar.DETERMINATE, during.bar);
        assertEquals(5000, during.progress);
    }

    @Test
    public void aFailureSaysWhyInPlainWordsAndOffersRetry() {
        TaiModelCentreRows.Input input = input(TaiModelStore.STATE_FAILED);
        input.error = "Unable to resolve host \"huggingface.co\"";

        TaiModelCentreRows.State state = TaiModelCentreRows.stateFor(context, input);

        assertEquals("Failed · network lost", state.pill);
        assertEquals(TaiModelCentreRows.Tone.ERROR, state.tone);
        assertEquals("Unable to resolve host \"huggingface.co\"", state.metaStart);
        assertEquals(TaiModelCentreRows.Bar.NONE, state.bar);
        assertEquals(EnumSet.of(TaiModelCentreRows.Action.RETRY, TaiModelCentreRows.Action.CANCEL), state.actions);
    }

    @Test
    public void failureReasonsMapToTheWordsTheDesignUses() {
        assertEquals(TaiModelCentreRows.Failure.TOKEN, TaiModelCentreRows.failureOf("HTTP 401"));
        assertEquals(TaiModelCentreRows.Failure.TOKEN, TaiModelCentreRows.failureOf("gated_model_requires_auth"));
        assertEquals(TaiModelCentreRows.Failure.EXPIRED, TaiModelCentreRows.failureOf("HTTP 403"));
        assertEquals(TaiModelCentreRows.Failure.EXPIRED, TaiModelCentreRows.failureOf("HTTP 404"));
        assertEquals(TaiModelCentreRows.Failure.SPACE, TaiModelCentreRows.failureOf("No space left on device"));
        assertEquals(TaiModelCentreRows.Failure.CHECK, TaiModelCentreRows.failureOf("sha256 mismatch"));
        assertEquals(TaiModelCentreRows.Failure.NETWORK, TaiModelCentreRows.failureOf("connection reset"));
        assertEquals(TaiModelCentreRows.Failure.NETWORK, TaiModelCentreRows.failureOf("HTTP 503"));
        assertEquals(TaiModelCentreRows.Failure.GENERIC, TaiModelCentreRows.failureOf(""));
        assertEquals(TaiModelCentreRows.Failure.GENERIC, TaiModelCentreRows.failureOf(null));
    }

    @Test
    public void installedAndCancelledRecordsLeaveTheDownloadsSection() {
        assertFalse(TaiModelCentreRows.isShown(input(TaiModelStore.STATE_INSTALLED)));
        assertFalse(TaiModelCentreRows.isShown(input(TaiModelStore.STATE_CANCELLED)));
        assertFalse(TaiModelCentreRows.isShown(input("")));
        assertTrue(TaiModelCentreRows.isShown(input(TaiModelStore.STATE_FAILED)));
        assertTrue(TaiModelCentreRows.isShown(input(TaiModelStore.STATE_PAUSED)));
        assertTrue(TaiModelCentreRows.actionsFor(TaiModelCentreRows.Phase.HIDDEN).isEmpty());
    }

    @Test
    public void ordinalsReadRightPastTheTeens() {
        assertEquals("1st", TaiModelCentreRows.ordinal(1));
        assertEquals("2nd", TaiModelCentreRows.ordinal(2));
        assertEquals("3rd", TaiModelCentreRows.ordinal(3));
        assertEquals("4th", TaiModelCentreRows.ordinal(4));
        assertEquals("11th", TaiModelCentreRows.ordinal(11));
        assertEquals("12th", TaiModelCentreRows.ordinal(12));
        assertEquals("13th", TaiModelCentreRows.ordinal(13));
        assertEquals("21st", TaiModelCentreRows.ordinal(21));
        assertEquals("112th", TaiModelCentreRows.ordinal(112));
    }

    @Test
    public void etaReadsInSecondsMinutesAndHours() {
        assertEquals("", TaiModelCentreRows.eta(context, -1));
        assertEquals("~40 s", TaiModelCentreRows.eta(context, 40));
        assertEquals("~3 min", TaiModelCentreRows.eta(context, 170));
        assertEquals("~1 h 5 min", TaiModelCentreRows.eta(context, 3900));
    }

    @Test
    public void theMainScreenSummaryCountsWhatIsOnItsWayAndWeighsTheBarBySize() {
        TaiModelCentreRows.Input big = input(TaiModelStore.STATE_DOWNLOADING);
        big.bytesRead = 0;
        big.totalBytes = 3000;
        TaiModelCentreRows.Input small = input(TaiModelStore.STATE_VERIFYING);
        small.bytesRead = 1000;
        small.totalBytes = 1000;
        TaiModelCentreRows.Input waiting = input(TaiModelStore.STATE_QUEUED);
        TaiModelCentreRows.Input paused = input(TaiModelStore.STATE_PAUSED);
        paused.pausedReason = TaiModelStore.PAUSED_USER;
        TaiModelCentreRows.Input failed = input(TaiModelStore.STATE_FAILED);
        TaiModelCentreRows.Input done = input(TaiModelStore.STATE_INSTALLED);

        TaiModelCentreRows.Summary summary = TaiModelCentreRows.summarize(
            Arrays.asList(big, small, waiting, paused, failed, done));

        assertEquals(3, summary.downloading);
        assertEquals(1, summary.paused);
        assertEquals(1, summary.failed);
        // 1000 of 4000 bytes: a finished small file does not make the big one look nearly done.
        assertEquals(2500, summary.progress);

        assertEquals(-1, TaiModelCentreRows.summarize(Arrays.asList(waiting)).progress);
    }

    @Test
    public void theInstallPreCheckRefusesWhatWouldEatTheReserve() {
        long total = 128 * GB;
        long reserve = com.termux.ai.TaiDownloadQueue.reserveBytes(total);
        assertNull(TaiModelCentreFragment.spaceRefusal(context, reserve + 3 * GB, total, 3 * GB));
        String refusal = TaiModelCentreFragment.spaceRefusal(context, reserve + 3 * GB - 1, total, 3 * GB);
        assertTrue(refusal, refusal != null && refusal.startsWith("Needs "));
    }

    @Test
    public void speechModelsReadAsEngineThenPlainName() {
        assertEquals("Whisper Base · English",
            TaiModelCentreFragment.centreName("whisper-acft-base-en", "Whisper ACFT Base (English)", null, true));
        assertEquals("Parakeet · Many languages",
            TaiModelCentreFragment.centreName("parakeet-tdt-0.6b-v3", "Parakeet TDT 0.6B v3", null, true));
        assertEquals("Gemma 4 E2B IT", TaiModelCentreFragment.centreName("gemma", "Gemma 4 E2B IT", null, false));
    }

    @Test
    public void deepLinkSegmentsResolveAndAnUnknownOneOpensInstalled() {
        assertEquals(0, TaiModelCentreFragment.segmentIndex(TaiModelCentreFragment.SEGMENT_INSTALLED));
        assertEquals(1, TaiModelCentreFragment.segmentIndex(TaiModelCentreFragment.SEGMENT_CHAT));
        assertEquals(2, TaiModelCentreFragment.segmentIndex(TaiModelCentreFragment.SEGMENT_SPEECH));
        assertEquals(0, TaiModelCentreFragment.segmentIndex("downloads"));
        assertEquals(0, TaiModelCentreFragment.segmentIndex(null));
    }
}
