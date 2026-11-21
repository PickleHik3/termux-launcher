package com.termux.ai;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.zip.Inflater;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DepthMakerTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    /** A graph whose output is a left-to-right ramp, 0 at the left edge to 1 at the right. */
    private static class FakeGraph implements DepthMaker.Graph {
        final int w = 16;
        final int h = 8;
        int closed;
        int runs;
        boolean cancelled;
        Runnable duringRun = () -> { };
        boolean fail;
        /** true: the ramp rises left to right in the raw output. */
        final boolean rising;

        FakeGraph(boolean rising) {
            this.rising = rising;
        }

        @Override public int[] inputSize() { return new int[] {w, h}; }
        @Override public int[] outputPlane() { return new int[] {h, w}; }

        @Override
        public float[] run(float[] nchw) throws IOException {
            runs++;
            assertEquals(3 * w * h, nchw.length);
            duringRun.run();
            if (fail) throw new IOException("boom");
            if (cancelled) throw new IOException("cancelled inside the run");
            float[] out = new float[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float v = x / (float) (w - 1);
                    out[y * w + x] = rising ? v : 1f - v;
                }
            }
            return out;
        }

        @Override public void cancel() { cancelled = true; }
        @Override public void close() { closed++; }
    }

    private static DepthMaker.PhotoDecoder photo(int w, int h) {
        return file -> new DepthMaker.Photo(new int[w * h], w, h);
    }

    private static final DepthMaker.Progress QUIET = (stage, percent) -> { };

    private File model() throws IOException {
        return folder.newFile("model.tflite");
    }

    private File out() {
        return new File(folder.getRoot(), "depth.png");
    }

    /** An 8-bit grey PNG as the maker writes it: filter 0 on every row. */
    private static final class Grey {
        final int width;
        final int height;
        final byte[] pixels;

        Grey(int width, int height, byte[] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }

        int getWidth() { return width; }
        int getHeight() { return height; }
        int at(int x, int y) { return pixels[y * width + x] & 0xFF; }
    }

    private static Grey read(File file) throws Exception {
        byte[] png = Files.readAllBytes(file.toPath());
        int at = 8;
        int w = 0, h = 0;
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        while (at < png.length) {
            int len = ((png[at] & 0xFF) << 24) | ((png[at + 1] & 0xFF) << 16) | ((png[at + 2] & 0xFF) << 8) | (png[at + 3] & 0xFF);
            String type = new String(png, at + 4, 4, "US-ASCII");
            int data = at + 8;
            if (type.equals("IHDR")) {
                w = ((png[data] & 0xFF) << 24) | ((png[data + 1] & 0xFF) << 16) | ((png[data + 2] & 0xFF) << 8) | (png[data + 3] & 0xFF);
                h = ((png[data + 4] & 0xFF) << 24) | ((png[data + 5] & 0xFF) << 16) | ((png[data + 6] & 0xFF) << 8) | (png[data + 7] & 0xFF);
                assertEquals(8, png[data + 8]);
                assertEquals(0, png[data + 9]);
            } else if (type.equals("IDAT")) {
                idat.write(png, data, len);
            }
            at = data + len + 4;
        }
        Inflater inflater = new Inflater();
        inflater.setInput(idat.toByteArray());
        byte[] raw = new byte[(w + 1) * h];
        int got = 0;
        while (got < raw.length && !inflater.finished()) got += inflater.inflate(raw, got, raw.length - got);
        assertEquals(raw.length, got);
        byte[] pixels = new byte[w * h];
        for (int y = 0; y < h; y++) System.arraycopy(raw, y * (w + 1) + 1, pixels, y * w, w);
        return new Grey(w, h, pixels);
    }

    private static int grey(Grey image, int x, int y) {
        return image.at(x, y);
    }

    @Test
    public void distanceModelIsWhiteWhereNear() throws Exception {
        // DA3 reports distance: a high value is far, so the right edge (high) must come out dark.
        FakeGraph graph = new FakeGraph(true);
        DepthMaker.Result result = new DepthMaker(f -> graph, photo(400, 300))
            .make(folder.newFile("p.jpg"), model(), true, out(), QUIET);
        assertTrue(result.code, result.ok);
        Grey image = read(out());
        assertTrue(grey(image, 0, 150) > 240);
        assertTrue(grey(image, 399, 150) < 15);
        assertEquals(1, graph.closed);
    }

    @Test
    public void disparityModelIsWhiteWhereHigh() throws Exception {
        FakeGraph graph = new FakeGraph(true);
        DepthMaker.Result result = new DepthMaker(f -> graph, photo(400, 300))
            .make(folder.newFile("p.jpg"), model(), false, out(), QUIET);
        assertTrue(result.code, result.ok);
        Grey image = read(out());
        assertTrue(grey(image, 0, 150) < 15);
        assertTrue(grey(image, 399, 150) > 240);
        assertEquals(1, graph.closed);
    }

    @Test
    public void sizeFollowsThePhotoAspectAndTheCap() throws Exception {
        int[][] cases = {{4000, 3000, 1024, 768}, {3000, 4000, 768, 1024}, {600, 300, 600, 300}, {300, 600, 300, 600}};
        for (int[] c : cases) {
            File out = new File(folder.getRoot(), "d" + c[0] + "x" + c[1] + ".png");
            DepthMaker.Result result = new DepthMaker(f -> new FakeGraph(true), photo(c[0], c[1]))
                .make(folder.newFile("p" + c[0] + "x" + c[1]), model2(c), false, out, QUIET);
            assertTrue(result.code, result.ok);
            Grey image = read(out);
            assertEquals(c[2], image.getWidth());
            assertEquals(c[3], image.getHeight());
        }
    }

    private File model2(int[] c) throws IOException {
        return folder.newFile("m" + c[0] + "x" + c[1]);
    }

    @Test
    public void cancelBeforeTheRunWritesNothingAndLoadsNothing() throws Exception {
        FakeGraph graph = new FakeGraph(true);
        DepthMaker maker = new DepthMaker(f -> graph, photo(100, 100));
        maker.cancel();
        DepthMaker.Result result = maker.make(folder.newFile("p.jpg"), model(), true, out(), QUIET);
        assertTrue(result.cancelled);
        assertEquals(DepthMaker.CODE_CANCELLED, result.code);
        assertFalse(out().exists());
        assertFalse(new File(out().getPath() + ".tmp").exists());
        assertEquals(0, graph.runs);
    }

    @Test
    public void cancelDuringTheRunWritesNothingAndClosesTheGraph() throws Exception {
        FakeGraph graph = new FakeGraph(true);
        DepthMaker maker = new DepthMaker(f -> graph, photo(100, 100));
        graph.duringRun = maker::cancel;
        DepthMaker.Result result = maker.make(folder.newFile("p.jpg"), model(), true, out(), QUIET);
        assertTrue(result.cancelled);
        assertTrue("the graph's own cancel was called", graph.cancelled);
        assertFalse(out().exists());
        assertFalse(new File(out().getPath() + ".tmp").exists());
        assertEquals(1, graph.closed);
    }

    @Test
    public void cancelAfterAGraphThatFinishedStillWritesNothing() throws Exception {
        FakeGraph graph = new FakeGraph(true) {
            @Override public float[] run(float[] nchw) throws IOException {
                float[] r = super.run(nchw);
                cancelled = false;
                return r;
            }
        };
        DepthMaker maker = new DepthMaker(f -> graph, photo(100, 100));
        graph.duringRun = maker::cancel;
        DepthMaker.Result result = maker.make(folder.newFile("p.jpg"), model(), true, out(), QUIET);
        assertTrue(result.cancelled);
        assertFalse(out().exists());
        assertEquals(1, graph.closed);
    }

    @Test
    public void aFailedRunWritesNothingAndClosesTheGraph() throws Exception {
        FakeGraph graph = new FakeGraph(true);
        graph.fail = true;
        DepthMaker.Result result = new DepthMaker(f -> graph, photo(100, 100))
            .make(folder.newFile("p.jpg"), model(), true, out(), QUIET);
        assertFalse(result.ok);
        assertFalse(result.cancelled);
        assertEquals(DepthMaker.CODE_RUN_FAILED, result.code);
        assertFalse(out().exists());
        assertFalse(new File(out().getPath() + ".tmp").exists());
        assertEquals(1, graph.closed);
    }

    @Test
    public void anUnreadablePhotoAndAnUnusableModelAreStableCodes() throws Exception {
        DepthMaker.Result noPhoto = new DepthMaker(f -> new FakeGraph(true), f -> { throw new IOException("no"); })
            .make(folder.newFile("p.jpg"), model(), true, out(), QUIET);
        assertEquals(DepthMaker.CODE_PHOTO_UNREADABLE, noPhoto.code);
        DepthMaker.Result noModel = new DepthMaker(f -> { throw new IOException("bad"); }, photo(10, 10))
            .make(folder.newFile("q.jpg"), model2(new int[] {1, 1}), true, out(), QUIET);
        assertEquals(DepthMaker.CODE_MODEL_UNUSABLE, noModel.code);
        assertFalse(out().exists());
    }

    @Test
    public void stagesAreReportedInOrder() throws Exception {
        List<String> stages = new ArrayList<>();
        new DepthMaker(f -> new FakeGraph(true), photo(100, 100))
            .make(folder.newFile("p.jpg"), model(), true, out(), (s, p) -> stages.add(s));
        assertEquals(Arrays.asList("loading", "running", "writing", "writing"), stages);
    }

    @Test
    public void sampleSizeKeepsBothSidesAtOrAbove1024() {
        assertEquals(1, DepthMaker.sampleSize(1500, 1100));
        assertEquals(2, DepthMaker.sampleSize(2048, 2048));
        assertEquals(4, DepthMaker.sampleSize(4608, 4096));
        assertEquals(1, DepthMaker.sampleSize(8000, 1500));
    }
}
