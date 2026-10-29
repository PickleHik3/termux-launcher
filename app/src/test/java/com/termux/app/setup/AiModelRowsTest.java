package com.termux.app.setup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.R;
import com.termux.ai.TaiDeviceTier;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiPlatformCaps;
import com.termux.ai.TaiTierPolicy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The AI card page's rows, ticks, footer sums and download order. */
public class AiModelRowsTest {

    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;
    private static final String E2B = "gemma-4-e2b-it-litert-lm";

    private static final Map<String, Long> SIZES = new HashMap<>();
    static {
        SIZES.put("whisper-acft-base-en", 100L * MIB);
        SIZES.put("whisper-acft-base", 100L * MIB);
        SIZES.put(TaiModelCatalog.KITTEN_TTS_NANO_ID, 94L * MIB);
        SIZES.put(E2B, 2_780_000_000L);
        SIZES.put(TaiModelCatalog.EMBEDDING_GEMMA_300M_ID, 183L * MIB);
        SIZES.put(TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_VISION_440M_ID, 388L * MIB);
        SIZES.put(TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_270M_ID, 165L * MIB);
    }

    private static final AiModelRows.Sizes TABLE = id -> {
        Long size = SIZES.get(id);
        return size == null ? 0L : size;
    };

    private static TaiTierPolicy.Env env(TaiDeviceTier tier, long ramGb) {
        return new TaiTierPolicy.Env(tier, ramGb * GIB, 34, true, true,
            TaiPlatformCaps.GpuPath.YES, true);
    }

    private static AiModelRows.Row row(List<AiModelRows.Row> rows, String id) {
        for (AiModelRows.Row row : rows) if (row.id.equals(id)) return row;
        return null;
    }

    @Test
    public void tierTwoTicksEveryRowAndTierOneTicksNone() {
        List<AiModelRows.Row> two = AiModelRows.rows(env(TaiDeviceTier.TIER_2, 8),
            Collections.emptySet(), TABLE);
        assertEquals(4, two.size());
        assertEquals(4, AiModelRows.initialTicks(two).size());
        List<AiModelRows.Row> one = AiModelRows.rows(env(TaiDeviceTier.TIER_1, 6),
            Collections.emptySet(), TABLE);
        assertTrue(AiModelRows.initialTicks(one).isEmpty());
        assertEquals("Whisper Base", row(one, "voice_typing").modelNames);
    }

    @Test
    public void installedRowIsNotTickedAndCostsNothing() {
        Set<String> installed = new HashSet<>(Collections.singleton(
            TaiModelCatalog.KITTEN_TTS_NANO_ID));
        List<AiModelRows.Row> rows = AiModelRows.rows(env(TaiDeviceTier.TIER_2, 8), installed,
            TABLE);
        AiModelRows.Row read = row(rows, "read_aloud");
        assertTrue(read.installed);
        assertFalse(read.ticked);
        assertEquals(0L, read.downloadBytes);
        assertFalse(AiModelRows.initialTicks(rows).contains("read_aloud"));
    }

    @Test
    public void footerSumsTickedRowsAndChecksTheStorageMargin() {
        List<AiModelRows.Row> rows = AiModelRows.rows(env(TaiDeviceTier.TIER_2, 8),
            Collections.emptySet(), TABLE);
        Set<String> ticked = new HashSet<>(Arrays.asList("voice_typing", "read_aloud"));
        long expected = row(rows, "voice_typing").downloadBytes + row(rows, "read_aloud").downloadBytes;
        AiModelRows.Footer fits = AiModelRows.footer(rows, ticked, expected + 2 * GIB);
        assertEquals(expected, fits.selectedBytes);
        assertTrue(fits.downloadEnabled);
        AiModelRows.Footer tight = AiModelRows.footer(rows, ticked, expected + GIB - 1);
        assertFalse(tight.downloadEnabled);
        assertEquals(R.string.tai_welcome_reason_storage, tight.disabledReasonRes);
        AiModelRows.Footer none = AiModelRows.footer(rows, Collections.emptySet(), 10 * GIB);
        assertFalse(none.downloadEnabled);
        assertEquals(R.string.tai_welcome_reason_nothing, none.disabledReasonRes);
    }

    @Test
    public void downloadOrderIsSmallestFirstWithoutDuplicates() {
        List<AiModelRows.Row> rows = AiModelRows.rows(env(TaiDeviceTier.TIER_2, 8),
            Collections.emptySet(), TABLE);
        List<String> order = AiModelRows.downloadOrder(rows, AiModelRows.initialTicks(rows), TABLE);
        assertEquals(new HashSet<>(order).size(), order.size());
        List<Long> sizes = new ArrayList<>();
        for (String id : order) sizes.add(TABLE.bytesOf(id));
        List<Long> sorted = new ArrayList<>(sizes);
        Collections.sort(sorted);
        assertEquals(sorted, sizes);
        assertEquals(E2B, order.get(order.size() - 1));
    }

    @Test
    public void formatSizeUsesTwoSignificantFigures() {
        assertEquals("94 MB", AiModelRows.formatSize(94L * MIB));
        assertEquals("2.6 GB", AiModelRows.formatSize(2_780_000_000L));
        assertEquals("<1 MB", AiModelRows.formatSize(1000L));
        assertEquals("0 MB", AiModelRows.formatSize(0L));
    }
}
