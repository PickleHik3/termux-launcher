package com.termux.ai;

import com.termux.ai.TaiPlatformCaps.Facts;

import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The GPU probe's cache file, which {@code :tai_runtime} writes and the app process reads: the
 * round trip per build, the in-flight marker read as a crash, and the app-side reader that picks up
 * facts written after it first found none.
 * Robolectric only for {@code android.util.Log}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class TaiPlatformCapsProbeCacheTest {
    private static final String BUILD = "118:2.0.0-pre.5";

    private static class FakeStore implements TaiGpuVerdict.Store {
        String value;
        final List<String> writes = new ArrayList<>();

        @Override public String read() { return value; }

        @Override public void write(String v) {
            value = v;
            writes.add(v);
        }
    }

    private static Facts adreno() {
        return new Facts(true, 0x5143, "Adreno (TM) 740", "adreno", "QTI", "SM8550", "Phone",
            "QUALCOMM Adreno(TM) 740", "OpenCL 3.0 Compiler E031.42.00", "fp asimd asimddp i8mm");
    }

    @Test
    public void factsRoundTripForTheirBuild() {
        String stored = TaiPlatformCaps.encodeFacts(BUILD, adreno());
        Facts back = TaiPlatformCaps.decodeFacts(stored, BUILD);
        assertNotNull(back);
        assertEquals(Boolean.TRUE, back.openCl);
        assertEquals(0x5143, back.vulkanVendorId);
        assertEquals("Adreno (TM) 740", back.vulkanDeviceName);
        assertEquals("OpenCL 3.0 Compiler E031.42.00", back.openClDriver);
        assertEquals("fp asimd asimddp i8mm", back.cpuFeatures);
        assertFalse(TaiPlatformCaps.isInflight(stored, BUILD));
    }

    @Test
    public void anotherBuildsFactsAreNotRead() {
        String stored = TaiPlatformCaps.encodeFacts("117:2.0.0-pre.4", adreno());
        assertNull(TaiPlatformCaps.decodeFacts(stored, BUILD));
    }

    @Test
    public void nothingOrDamageReadsAsNoFacts() {
        assertNull(TaiPlatformCaps.decodeFacts(null, BUILD));
        assertNull(TaiPlatformCaps.decodeFacts("", BUILD));
        assertNull(TaiPlatformCaps.decodeFacts("{\"build\":", BUILD));
        assertNull(TaiPlatformCaps.decodeFacts(TaiPlatformCaps.encodeInflight(BUILD), BUILD));
    }

    @Test
    public void aFirstProbeWritesTheMarkerBeforeTheDriversThenTheFacts() {
        FakeStore store = new FakeStore();
        final String[] seenByCollector = new String[1];
        final Boolean[] ranNative = new Boolean[1];
        Facts facts = TaiPlatformCaps.probeFacts(store, BUILD, runNative -> {
            seenByCollector[0] = store.value;
            ranNative[0] = runNative;
            return adreno();
        });
        assertTrue("the marker is stored when the drivers are touched", TaiPlatformCaps.isInflight(seenByCollector[0], BUILD));
        assertEquals(Boolean.TRUE, ranNative[0]);
        assertEquals(Boolean.TRUE, facts.openCl);
        assertNotNull(TaiPlatformCaps.decodeFacts(store.value, BUILD));
        assertFalse(TaiPlatformCaps.isInflight(store.value, BUILD));
    }

    @Test
    public void aMarkerLeftByACrashedProbeSkipsTheDriversAndReadsAsNoOpenCl() {
        FakeStore store = new FakeStore();
        store.value = TaiPlatformCaps.encodeInflight(BUILD);
        final Boolean[] ranNative = new Boolean[1];
        Facts facts = TaiPlatformCaps.probeFacts(store, BUILD, runNative -> {
            ranNative[0] = runNative;
            // What collectFacts(false) reports: no driver was asked.
            return new Facts(runNative ? Boolean.TRUE : Boolean.FALSE, -1, "", "adreno", "QTI", "SM8550", "Phone", "", "", "");
        });
        assertEquals(Boolean.FALSE, ranNative[0]);
        assertEquals(Boolean.FALSE, facts.openCl);
        assertEquals(TaiPlatformCaps.GpuPath.NO, TaiPlatformCaps.classify(facts).path);
        Facts cached = TaiPlatformCaps.decodeFacts(store.value, BUILD);
        assertNotNull("the crash's answer is cached, so the drivers are not tried again this build", cached);
        assertEquals(Boolean.FALSE, cached.openCl);
    }

    @Test
    public void aMarkerFromAnotherBuildProbesAgain() {
        FakeStore store = new FakeStore();
        store.value = TaiPlatformCaps.encodeInflight("117:2.0.0-pre.4");
        final Boolean[] ranNative = new Boolean[1];
        TaiPlatformCaps.probeFacts(store, BUILD, runNative -> {
            ranNative[0] = runNative;
            return adreno();
        });
        assertEquals(Boolean.TRUE, ranNative[0]);
    }

    @Test
    public void storedFactsAreUsedWithoutProbing() {
        FakeStore store = new FakeStore();
        store.value = TaiPlatformCaps.encodeFacts(BUILD, adreno());
        Facts facts = TaiPlatformCaps.probeFacts(store, BUILD, runNative -> {
            throw new AssertionError("no probe with facts on file");
        });
        assertEquals("Adreno (TM) 740", facts.vulkanDeviceName);
        assertTrue(store.writes.isEmpty());
    }

    @Test
    public void theAppSideReaderPicksUpFactsWrittenAfterAnUnprobedRead() {
        FakeStore store = new FakeStore();
        TaiPlatformCaps.ProbeFileReader reader = new TaiPlatformCaps.ProbeFileReader(5_000L);
        assertNull(reader.read(store, BUILD, 1_000L));

        // :tai_runtime finishes its probe.
        store.value = TaiPlatformCaps.encodeFacts(BUILD, adreno());
        assertFalse("within the TTL the miss stands", reader.due(2_000L));
        assertNull(reader.read(store, BUILD, 2_000L));

        Facts later = reader.read(store, BUILD, 6_000L);
        assertNotNull(later);
        assertEquals(Boolean.TRUE, later.openCl);
    }

    @Test
    public void forgettingTheMissReadsTheFileAtOnce() {
        FakeStore store = new FakeStore();
        TaiPlatformCaps.ProbeFileReader reader = new TaiPlatformCaps.ProbeFileReader(5_000L);
        assertNull(reader.read(store, BUILD, 1_000L));
        store.value = TaiPlatformCaps.encodeFacts(BUILD, adreno());
        reader.forget();
        assertNotNull(reader.read(store, BUILD, 1_001L));
    }
}
