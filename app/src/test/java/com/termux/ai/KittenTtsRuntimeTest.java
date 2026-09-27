package com.termux.ai;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The host glue between the KittenTTS graphs, which runs without a model: the row-repeat
 * alignment ({@code np.repeat(x[0], durations, axis=0)}), the duration clamp, the vocoder tail
 * trim, the style row and the file check.
 */
public class KittenTtsRuntimeTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void repeatRowsRepeatsEachRowByItsDuration() {
        float[] source = {
            1f, 2f,    // row 0
            3f, 4f,    // row 1
            5f, 6f};   // row 2
        float[] out = KittenTtsRuntime.repeatRows(source, 2, new int[] {2, 0, 3});
        assertArrayEquals(new float[] {1f, 2f, 1f, 2f, 5f, 6f, 5f, 6f, 5f, 6f}, out, 0f);
    }

    @Test
    public void repeatRowsOfAllZeroDurationsIsEmpty() {
        assertEquals(0, KittenTtsRuntime.repeatRows(new float[] {1f, 2f}, 2, new int[] {0}).length);
    }

    @Test
    public void negativeDurationsAreClampedToZeroAndTheFramesCounted() {
        int[] durations = {35, -1, 3, 0, 2};
        assertEquals(40, KittenTtsRuntime.clampDurations(durations));
        assertArrayEquals(new int[] {35, 0, 3, 0, 2}, durations);
    }

    @Test
    public void theVocoderTailIsTrimmedAndSamplesClipped() {
        float[] waveform = new float[48_600];
        waveform[0] = 1.5f;
        waveform[1] = -2f;
        waveform[2] = 0.25f;
        float[] trimmed = KittenTtsRuntime.trimTail(waveform);
        assertEquals(48_600 - KittenTtsRuntime.TAIL_TRIM, trimmed.length);
        assertEquals(1f, trimmed[0], 0f);
        assertEquals(-1f, trimmed[1], 0f);
        assertEquals(0.25f, trimmed[2], 0f);
    }

    @Test
    public void aShortWaveformKeepsTheMinimumOrAllOfIt() {
        assertEquals(KittenTtsRuntime.MIN_SAMPLES, KittenTtsRuntime.trimTail(new float[5_500]).length);
        assertEquals(800, KittenTtsRuntime.trimTail(new float[800]).length);
    }

    @Test
    public void theStyleRowIsTheTextLengthCappedAtTheLastRow() {
        assertEquals(12, KittenTtsRuntime.styleRow("Hello world,", 400));
        assertEquals(399, KittenTtsRuntime.styleRow(new String(new char[600]).replace('\0', 'a'), 400));
        assertEquals(0, KittenTtsRuntime.styleRow("", 400));
    }

    @Test
    public void everySidecarMustBeBesideThePredictor() throws IOException {
        File dir = tmp.newFolder("kittentts-nano-0.8");
        File predictor = new File(dir, KittenTtsRuntime.PREDICTOR_FILE);
        Files.write(predictor.toPath(), new byte[] {1});
        assertEquals(Arrays.asList(KittenTtsRuntime.SIDECAR_FILES), KittenTtsRuntime.missingSidecars(predictor));
        for (String name : KittenTtsRuntime.SIDECAR_FILES) Files.write(new File(dir, name).toPath(), new byte[] {1});
        assertEquals(Collections.<String>emptyList(), KittenTtsRuntime.missingSidecars(predictor));
        // An empty file is a download that never finished.
        Files.write(new File(dir, KittenTtsRuntime.VOICES_FILE).toPath(), new byte[0]);
        List<String> missing = KittenTtsRuntime.missingSidecars(predictor);
        assertEquals(Collections.singletonList(KittenTtsRuntime.VOICES_FILE), missing);
        assertTrue(KittenTtsRuntime.SIDECAR_FILES.length == 6);
    }
}
