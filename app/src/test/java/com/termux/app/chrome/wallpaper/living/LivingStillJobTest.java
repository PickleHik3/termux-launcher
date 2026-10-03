package com.termux.app.chrome.wallpaper.living;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

/**
 * The process-owned living-still job with a fake engine and inline executors: progress mapping,
 * one run at a time, listeners that come and go while it runs, cancel, failure, and the analysis
 * folder being deleted whatever happens.
 */
public class LivingStillJobTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final Executor INLINE = Runnable::run;

    /** Scripted engine; each hook runs inside the matching step, on the "worker" (inline here). */
    private static final class FakeEngine implements LivingStillJob.Engine {
        final List<String> calls = new ArrayList<>();
        boolean gemma;
        LivingStillJob.Outcome outcome = new LivingStillJob.Outcome(true, false, null, "");
        IOException buildFailure;
        Runnable duringAnalysis = () -> {};
        Runnable duringBuild = () -> {};
        File analysisDirSeen;
        File manifestDir;

        @NonNull @Override
        public LivingStillJob.Outcome analyze(@NonNull File photo, @NonNull File outDir,
                                              @NonNull LivingStillJob.StageSink sink) {
            calls.add("analyze");
            analysisDirSeen = outDir;
            try {
                assertTrue("the folder exists while it runs", outDir.isDirectory());
                assertTrue(new File(outDir, "depth.png").createNewFile());
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            sink.onStage(LivingStillJob.STAGE_DEPTH, 50);
            sink.onStage(LivingStillJob.STAGE_DEPTH, 100);
            sink.onStage(LivingStillJob.STAGE_SCENE, 100);
            duringAnalysis.run();
            sink.onStage(LivingStillJob.STAGE_SUBJECT, 100);
            return outcome;
        }

        @NonNull @Override
        public Manifest build(@NonNull File photo, @NonNull File outDir, @NonNull LivingStillBuilder.Progress progress)
            throws IOException {
            calls.add("build");
            progress.onProgress(LivingStillJob.STAGE_RECIPE, 20);
            duringBuild.run();
            if (progress.isCancelled()) throw new CancellationException("cancelled");
            if (buildFailure != null) throw buildFailure;
            progress.onProgress(LivingStillJob.STAGE_RECIPE, 100);
            return new Manifest(manifestDir, new LivingRecipe());
        }

        @Override public void cancelAnalysis() { calls.add("cancel"); }

        @Override public boolean usesGemma() { return gemma; }
    }

    private static final class Recorder implements LivingStillJob.Listener {
        final List<LivingStillJob.Progress> progress = new ArrayList<>();
        final List<LivingStillJob.Result> results = new ArrayList<>();

        @Override public void onProgress(@NonNull LivingStillJob.Progress p) { progress.add(p); }
        @Override public void onFinished(@NonNull LivingStillJob.Result r) { results.add(r); }

        int lastOverall() { return progress.isEmpty() ? -1 : progress.get(progress.size() - 1).overallPercent; }
    }

    private FakeEngine mEngine;
    private LivingStillJob mJob;
    private File mPhoto;
    private File mRoot;

    @Before
    public void setUp() throws IOException {
        mEngine = new FakeEngine();
        mEngine.manifestDir = tmp.newFolder("living", "0123456789abcdef");
        mRoot = new File(tmp.getRoot(), "living-analysis");
        mJob = new LivingStillJob(mEngine, INLINE, INLINE, INLINE, mRoot);
        mPhoto = tmp.newFile("photo.png");
        try (FileOutputStream out = new FileOutputStream(mPhoto)) {
            out.write(new byte[] {1, 2, 3, 4});
        }
    }

    @Test
    public void stagesShareTheBarAsAgreed() {
        assertEquals(0, LivingStillJob.overallPercent("depth", 0));
        assertEquals(40, LivingStillJob.overallPercent("depth", 100));
        assertEquals(40, LivingStillJob.overallPercent("scene", 0));
        assertEquals(50, LivingStillJob.overallPercent("scene", 100));
        assertEquals(50, LivingStillJob.overallPercent("subject", 0));
        assertEquals(70, LivingStillJob.overallPercent("subject", 100));
        assertEquals(70, LivingStillJob.overallPercent("recipe", 0));
        assertEquals(100, LivingStillJob.overallPercent("recipe", 100));
        assertEquals(85, LivingStillJob.overallPercent("recipe", 50));
        assertEquals("clamped", 100, LivingStillJob.overallPercent("recipe", 400));
        assertEquals("unknown stage", -1, LivingStillJob.overallPercent("nope", 10));
    }

    @Test
    public void aRunReportsClimbingProgressThenTheManifest() {
        Recorder rec = new Recorder();
        mJob.attach(rec);
        assertTrue(mJob.start(mPhoto));

        assertFalse("done", mJob.isRunning());
        assertEquals(1, rec.results.size());
        LivingStillJob.Result r = rec.results.get(0);
        assertTrue(r.ok());
        assertNotNull(r.manifest);
        assertEquals(mPhoto, r.photo);
        assertFalse(r.cancelled);
        assertNull(r.error);
        int last = -1;
        for (LivingStillJob.Progress p : rec.progress) {
            assertTrue("never goes back", p.overallPercent >= last);
            last = p.overallPercent;
        }
        assertEquals(100, rec.lastOverall());
        assertEquals(java.util.Arrays.asList("analyze", "build"), mEngine.calls);
        assertFalse("the analysis folder is gone", mEngine.analysisDirSeen.exists());
        assertEquals(new File(mRoot, mEngine.analysisDirSeen.getName()), mEngine.analysisDirSeen);
    }

    @Test
    public void onlyOneRunAtATime() {
        final boolean[] second = {true};
        File other = new File(tmp.getRoot(), "other.png");
        mEngine.duringAnalysis = () -> second[0] = mJob.start(other);
        Recorder rec = new Recorder();
        mJob.attach(rec);
        assertTrue(mJob.start(mPhoto));
        assertFalse("refused while running", second[0]);
        assertEquals(1, rec.results.size());
        assertEquals(mPhoto, rec.results.get(0).photo);
        assertTrue("free again", mJob.start(mPhoto));
    }

    @Test
    public void aListenerAttachedMidRunHearsTheLatestProgressAtOnce() {
        Recorder late = new Recorder();
        mEngine.duringAnalysis = () -> {
            assertTrue(mJob.isRunning());
            assertEquals(mPhoto, mJob.runningPhoto());
            mJob.attach(late);
            assertEquals("told where the run is", 1, late.progress.size());
            assertEquals(LivingStillJob.STAGE_SCENE, late.progress.get(0).stage);
            assertEquals(50, late.progress.get(0).overallPercent);
        };
        assertNull("nothing before a run", mJob.lastProgress());
        mJob.start(mPhoto);
        assertEquals("and it hears the end", 1, late.results.size());
        assertNull(mJob.lastProgress());
    }

    @Test
    public void aDetachedListenerHearsNothingMoreButTheRunGoesOn() {
        Recorder gone = new Recorder();
        Recorder stays = new Recorder();
        mJob.attach(gone);
        mJob.attach(stays);
        mEngine.duringAnalysis = () -> mJob.detach(gone);
        mJob.start(mPhoto);
        assertTrue("the page closing does not stop the run", stays.results.get(0).ok());
        assertTrue(gone.results.isEmpty());
        assertTrue(gone.progress.size() < stays.progress.size());
    }

    @Test
    public void attachingTwiceCountsOnce() {
        Recorder rec = new Recorder();
        mJob.attach(rec);
        mJob.attach(rec);
        mJob.start(mPhoto);
        assertEquals(1, rec.results.size());
    }

    @Test
    public void cancelDuringTheAnalysisStopsItAndSkipsTheBuild() {
        mEngine.duringAnalysis = () -> mJob.cancel();
        mEngine.outcome = new LivingStillJob.Outcome(false, true, "cancelled", "");
        Recorder rec = new Recorder();
        mJob.attach(rec);
        mJob.start(mPhoto);
        LivingStillJob.Result r = rec.results.get(0);
        assertTrue(r.cancelled);
        assertFalse(r.ok());
        assertEquals(java.util.Arrays.asList("analyze", "cancel"), mEngine.calls);
        assertFalse(mEngine.analysisDirSeen.exists());
        assertFalse(mJob.isRunning());
    }

    @Test
    public void cancelAfterTheAnalysisStillStopsTheRun() {
        // The analysis finished just as the user cancelled: the build must not run.
        mEngine.duringAnalysis = () -> mJob.cancel();
        Recorder rec = new Recorder();
        mJob.attach(rec);
        mJob.start(mPhoto);
        assertTrue(rec.results.get(0).cancelled);
        assertFalse(mEngine.calls.contains("build"));
    }

    @Test
    public void cancelDuringTheBuildEndsCancelled() {
        mEngine.duringBuild = () -> mJob.cancel();
        Recorder rec = new Recorder();
        mJob.attach(rec);
        mJob.start(mPhoto);
        assertTrue(rec.results.get(0).cancelled);
        assertFalse(mEngine.analysisDirSeen.exists());
    }

    @Test
    public void cancelWithNothingRunningDoesNothing() {
        mJob.cancel();
        assertTrue(mEngine.calls.isEmpty());
    }

    @Test
    public void aFailedAnalysisCarriesItsCodeAndMessage() {
        mEngine.outcome = new LivingStillJob.Outcome(false, false, "vision_model_missing", "Not installed: u2net.");
        Recorder rec = new Recorder();
        mJob.attach(rec);
        mJob.start(mPhoto);
        LivingStillJob.Result r = rec.results.get(0);
        assertFalse(r.ok());
        assertFalse(r.cancelled);
        assertEquals("vision_model_missing", r.error);
        assertEquals("Not installed: u2net.", r.message);
        assertFalse(mEngine.calls.contains("build"));
        assertFalse(mEngine.analysisDirSeen.exists());
    }

    @Test
    public void aFailedBuildIsReportedAndCleanedUp() {
        mEngine.buildFailure = new IOException("disk full");
        Recorder rec = new Recorder();
        mJob.attach(rec);
        mJob.start(mPhoto);
        LivingStillJob.Result r = rec.results.get(0);
        assertEquals("build_failed", r.error);
        assertEquals("disk full", r.message);
        assertFalse(mEngine.analysisDirSeen.exists());
        assertFalse(mJob.isRunning());
    }

    @Test
    public void aMissingPhotoFailsWithoutRunningAnything() {
        Recorder rec = new Recorder();
        mJob.attach(rec);
        mJob.start(new File(tmp.getRoot(), "gone.png"));
        assertEquals("read_failed", rec.results.get(0).error);
        assertTrue(mEngine.calls.isEmpty());
    }

    @Test
    public void theGemmaStepIsNamedOnlyInsideItsPercentRange() {
        mEngine.gemma = true;
        assertTrue(mJob.usesGemma());
        Recorder rec = new Recorder();
        mJob.attach(rec);
        mJob.start(mPhoto);
        LivingStillJob.Progress recipeEarly = null;
        for (LivingStillJob.Progress p : rec.progress) if (p.stage.equals("recipe")) recipeEarly = p;
        assertNotNull(recipeEarly);
        assertTrue("every progress knows Gemma is in the run", recipeEarly.gemma);
        assertTrue("20 is inside the Gemma range", rec.progress.stream()
            .anyMatch(p -> p.stage.equals("recipe") && p.stagePercent == 20 && p.asksGemma()));
        assertFalse("100 is the end of the build", rec.progress.stream()
            .anyMatch(p -> p.stage.equals("recipe") && p.stagePercent == 100 && p.asksGemma()));
        assertFalse("the depth stage never asks Gemma", rec.progress.stream()
            .anyMatch(p -> p.stage.equals("depth") && p.asksGemma()));
    }

    @Test
    public void withoutGemmaTheBuildIsJustBuilding() {
        mEngine.gemma = false;
        Recorder rec = new Recorder();
        mJob.attach(rec);
        mJob.start(mPhoto);
        assertFalse(rec.progress.stream().anyMatch(LivingStillJob.Progress::asksGemma));
    }
}
