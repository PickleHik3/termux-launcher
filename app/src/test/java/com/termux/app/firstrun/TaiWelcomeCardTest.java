package com.termux.app.firstrun;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

/**
 * The welcome card's rows, ticks, footer and sizes for each tier (tai-device-tiers spec section 5).
 * The table of ticks is the policy's; what is tested here is what the card adds around it.
 */
public class TaiWelcomeCardTest {

    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;

    private static final String E2B = "gemma-4-e2b-it-litert-lm";
    private static final String E4B = "gemma-4-e4b-it-litert-lm";

    private static final Map<String, Long> SIZES = new HashMap<>();
    static {
        SIZES.put("whisper-acft-base-en", 100L * MIB);
        SIZES.put("whisper-acft-small-en", 286L * MIB);
        SIZES.put("whisper-acft-base", 100L * MIB);
        SIZES.put("whisper-acft-small", 286L * MIB);
        SIZES.put(TaiModelCatalog.KITTEN_TTS_NANO_ID, 94L * MIB);
        SIZES.put(E2B, 2_780_000_000L);
        SIZES.put(E4B, 3_930_000_000L);
        SIZES.put(TaiModelCatalog.EMBEDDING_GEMMA_300M_ID, 183L * MIB);
    }

    private static final TaiWelcomeCard.Sizes SIZE_TABLE = id -> {
        Long size = SIZES.get(id);
        return size == null ? 0L : size;
    };

    private static TaiTierPolicy.Env env(TaiDeviceTier tier, long ramGb, int sdk) {
        return new TaiTierPolicy.Env(tier, ramGb * GIB, sdk, true, true, TaiPlatformCaps.GpuPath.YES, true);
    }

    private static List<TaiWelcomeCard.Row> rows(TaiTierPolicy.Env env, String... installed) {
        return TaiWelcomeCard.rows(env, new HashSet<>(Arrays.asList(installed)), SIZE_TABLE);
    }

    private static List<String> ids(List<TaiWelcomeCard.Row> rows) {
        List<String> ids = new ArrayList<>();
        for (TaiWelcomeCard.Row row : rows) ids.add(row.id);
        return ids;
    }

    private static Set<String> ticked(List<TaiWelcomeCard.Row> rows) {
        return TaiWelcomeCard.initialTicks(rows);
    }

    private static TaiWelcomeCard.Row row(List<TaiWelcomeCard.Row> rows, String id) {
        for (TaiWelcomeCard.Row row : rows) if (row.id.equals(id)) return row;
        return null;
    }

    // ------------------------------------------------------------------------------------ tiers

    @Test
    public void tierOneHasFourUntickedRowsAndTheModelCentreLine() {
        TaiTierPolicy.Env env = env(TaiDeviceTier.TIER_1, 6, 34);
        List<TaiWelcomeCard.Row> rows = rows(env);
        assertEquals(Arrays.asList("voice_typing", "read_aloud", "dawn_notes"), ids(rows));
        assertTrue(ticked(rows).isEmpty());
        assertTrue(TaiWelcomeCard.showsModelCentreLine(env));
        assertEquals("Whisper Base", row(rows, "voice_typing").modelNames);
    }

    @Test
    public void tierTwoAtEightGbTicksEverything() {
        TaiTierPolicy.Env env = env(TaiDeviceTier.TIER_2, 8, 34);
        List<TaiWelcomeCard.Row> rows = rows(env);
        assertEquals(Arrays.asList("voice_typing", "read_aloud", "assistant", "dawn_notes"), ids(rows));
        assertEquals(new HashSet<>(ids(rows)), ticked(rows));
        assertFalse(TaiWelcomeCard.showsModelCentreLine(env));
        assertEquals("Gemma 4 E2B", row(rows, "assistant").modelNames);
    }

    @Test
    public void tierTwoAtTenAndTwelveGbOffersNoE4b() {
        for (long ram : new long[] {10, 12}) {
            List<TaiWelcomeCard.Row> rows = rows(env(TaiDeviceTier.TIER_2, ram, 34));
            assertEquals(Arrays.asList("voice_typing", "read_aloud", "assistant", "dawn_notes"), ids(rows));
            for (TaiWelcomeCard.Row row : rows) assertFalse(row.modelIds.contains(E4B));
            assertTrue(row(rows, "assistant").ticked);
        }
    }

    @Test
    public void tierThreeTicksTheE4bAssistantAndCallsTheE2bRowTidyDictation() {
        List<TaiWelcomeCard.Row> rows = rows(env(TaiDeviceTier.TIER_3, 16, 34));
        assertEquals(Arrays.asList("voice_typing", "read_aloud", "e4b_assistant", "assistant", "dawn_notes"), ids(rows));
        assertTrue(row(rows, "e4b_assistant").ticked);
        assertEquals(R.string.tai_welcome_row_tidy_title, row(rows, "assistant").titleRes);
        assertEquals(R.string.tai_welcome_row_e4b_assistant_title, row(rows, "e4b_assistant").titleRes);
    }

    @Test
    public void tierThreeWithNoGpuPathLeavesTheE4bAssistantUnticked() {
        TaiTierPolicy.Env env = new TaiTierPolicy.Env(TaiDeviceTier.TIER_3, 16 * GIB, 34, true, true,
            TaiPlatformCaps.GpuPath.NO, true);
        assertFalse(row(rows(env), "e4b_assistant").ticked);
    }

    @Test
    public void whisperFollowsTheLanguage() {
        TaiTierPolicy.Env multi = new TaiTierPolicy.Env(TaiDeviceTier.TIER_2, 12 * GIB, 34, true, true,
            TaiPlatformCaps.GpuPath.YES, false);
        assertEquals(Collections.singletonList("whisper-acft-small"), row(rows(multi), "voice_typing").modelIds);
        assertEquals(Collections.singletonList("whisper-acft-small-en"),
            row(rows(env(TaiDeviceTier.TIER_2, 12, 34)), "voice_typing").modelIds);
    }

    // ---------------------------------------------------------------------------------- platform

    @Test
    public void noLocalBackendMeansNoCard() {
        TaiTierPolicy.Env env = new TaiTierPolicy.Env(TaiDeviceTier.TIER_2, 12 * GIB, 34, false, false,
            TaiPlatformCaps.GpuPath.YES, true);
        assertTrue(rows(env).isEmpty());
        assertFalse(TaiWelcomeCard.hasRows(env));
        assertFalse(TaiWelcomeCard.showsModelCentreLine(env(TaiDeviceTier.TIER_2, 12, 34)));
    }

    @Test
    public void imageGenerationNeverAppears() {
        for (TaiDeviceTier tier : TaiDeviceTier.values()) {
            for (TaiWelcomeCard.Row row : rows(env(tier, 16, 34))) {
                for (String id : row.modelIds) {
                    assertFalse(id, id.toLowerCase().contains("diffusion"));
                    assertFalse(id, id.toLowerCase().contains("image"));
                }
                assertFalse(row.id.contains("image"));
            }
        }
    }

    // ----------------------------------------------------------------------------------- installed

    @Test
    public void installedRowIsShownAsInstalledUntickedAndNotTickable() {
        TaiTierPolicy.Env env = env(TaiDeviceTier.TIER_2, 12, 34);
        List<TaiWelcomeCard.Row> rows = rows(env, "whisper-acft-small-en", TaiModelCatalog.KITTEN_TTS_NANO_ID);
        TaiWelcomeCard.Row voice = row(rows, "voice_typing");
        assertTrue(voice.installed);
        assertFalse(voice.ticked);
        assertFalse(voice.tickable());
        assertEquals("", voice.sizeText);
        assertEquals(0L, voice.downloadBytes);
        assertFalse(ticked(rows).contains("voice_typing"));
        assertFalse(ticked(rows).contains("read_aloud"));
        assertTrue(ticked(rows).contains("dawn_notes"));
    }

    @Test
    public void installedRowsAreNotQueuedAgain() {
        TaiTierPolicy.Env env = env(TaiDeviceTier.TIER_2, 12, 34);
        List<TaiWelcomeCard.Row> rows = rows(env, "whisper-acft-small-en");
        List<String> order = TaiWelcomeCard.downloadOrder(rows, ticked(rows), SIZE_TABLE);
        assertFalse(order.contains("whisper-acft-small-en"));
    }

    // ------------------------------------------------------------------------------------ footer

    @Test
    public void downloadIsQueuedSmallestFirst() {
        List<TaiWelcomeCard.Row> rows = rows(env(TaiDeviceTier.TIER_2, 12, 34));
        List<String> order = TaiWelcomeCard.downloadOrder(rows, ticked(rows), SIZE_TABLE);
        for (int i = 1; i < order.size(); i++) {
            assertTrue(SIZE_TABLE.bytesOf(order.get(i - 1)) <= SIZE_TABLE.bytesOf(order.get(i)));
        }
        // E2B is the largest download on 12 GB, so it comes last.
        assertEquals(E2B, order.get(order.size() - 1));
    }

    @Test
    public void footerSumsTheTickedRowsAndEnablesDownloadWhenItFitsWithTheMargin() {
        List<TaiWelcomeCard.Row> rows = rows(env(TaiDeviceTier.TIER_2, 12, 34));
        Set<String> ticks = ticked(rows);
        long expected = 0L;
        for (TaiWelcomeCard.Row row : rows) if (ticks.contains(row.id)) expected += row.downloadBytes;
        long free = expected + TaiWelcomeCard.STORAGE_MARGIN_BYTES;
        TaiWelcomeCard.Footer footer = TaiWelcomeCard.footer(rows, ticks, free);
        assertEquals(expected, footer.selectedBytes);
        assertTrue(footer.downloadEnabled);
        assertEquals(0, footer.disabledReasonRes);
    }

    @Test
    public void footerDisablesDownloadWhenTheMarginDoesNotFit() {
        List<TaiWelcomeCard.Row> rows = rows(env(TaiDeviceTier.TIER_2, 12, 34));
        Set<String> ticks = ticked(rows);
        long selected = TaiWelcomeCard.footer(rows, ticks, Long.MAX_VALUE).selectedBytes;
        TaiWelcomeCard.Footer footer = TaiWelcomeCard.footer(rows, ticks,
            selected + TaiWelcomeCard.STORAGE_MARGIN_BYTES - 1L);
        assertFalse(footer.downloadEnabled);
        assertEquals(R.string.tai_welcome_reason_storage, footer.disabledReasonRes);
        // The ticks themselves never change with storage.
        assertEquals(ticks, ticked(rows));
    }

    @Test
    public void footerWithNothingTickedIsDisabledWithItsOwnReason() {
        List<TaiWelcomeCard.Row> rows = rows(env(TaiDeviceTier.TIER_1, 6, 34));
        TaiWelcomeCard.Footer footer = TaiWelcomeCard.footer(rows, Collections.<String>emptySet(), 50L * GIB);
        assertFalse(footer.downloadEnabled);
        assertEquals(R.string.tai_welcome_reason_nothing, footer.disabledReasonRes);
        assertEquals(0L, footer.selectedBytes);
    }

    @Test
    public void footerIgnoresAnInstalledRowThatIsStillInTheTickedSet() {
        List<TaiWelcomeCard.Row> rows = rows(env(TaiDeviceTier.TIER_2, 12, 34), "whisper-acft-small-en");
        TaiWelcomeCard.Footer footer = TaiWelcomeCard.footer(rows,
            new HashSet<>(Collections.singletonList("voice_typing")), 50L * GIB);
        assertEquals(0L, footer.selectedBytes);
        assertFalse(footer.downloadEnabled);
    }

    // ------------------------------------------------------------------------------------ warning

    @Test
    public void theBackgroundWarningFollowsTheQuarterOfRamRuleOnTheRowsLargestFile() {
        // E4B (3.93 GB) is under a quarter of 16 GB.
        assertFalse(row(rows(env(TaiDeviceTier.TIER_3, 16, 34)), "e4b_assistant").warnsBackground);
        // E2B (2.78 GB) on 8 GB warns, on 12 GB it does not.
        assertTrue(row(rows(env(TaiDeviceTier.TIER_2, 8, 34)), "assistant").warnsBackground);
        assertFalse(row(rows(env(TaiDeviceTier.TIER_2, 12, 34)), "assistant").warnsBackground);
        // Small rows never warn, and an installed row does not.
        assertFalse(row(rows(env(TaiDeviceTier.TIER_2, 8, 34)), "voice_typing").warnsBackground);
        assertFalse(row(rows(env(TaiDeviceTier.TIER_2, 8, 34), E2B), "assistant").warnsBackground);
    }

    // -------------------------------------------------------------------------------------- sizes

    @Test
    public void sizesRoundToTwoSignificantFigures() {
        assertEquals("2.6 GB", TaiWelcomeCard.formatSize(2_780_000_000L));
        assertEquals("3.7 GB", TaiWelcomeCard.formatSize(3_930_000_000L));
        assertEquals("97 MB", TaiWelcomeCard.formatSize(97L * MIB));
        assertEquals("290 MB", TaiWelcomeCard.formatSize(286L * MIB));
        assertEquals("15 MB", TaiWelcomeCard.formatSize(15L * MIB));
        assertEquals("1.0 GB", TaiWelcomeCard.formatSize(1000L * MIB));
        assertEquals("41 GB", TaiWelcomeCard.formatSize(41L * GIB));
        assertEquals("<1 MB", TaiWelcomeCard.formatSize(1000L));
        assertEquals("0 MB", TaiWelcomeCard.formatSize(0L));
    }

    // ------------------------------------------------------------------------------------- header

    @Test
    public void headerCarriesTierRamChipAndAndroid() {
        TaiWelcomeCard.Header header = TaiWelcomeCard.header(env(TaiDeviceTier.TIER_2, 12, 34),
            "Snapdragon 8+ Gen 1", "16");
        assertEquals(2, header.tierNumber);
        assertEquals(12, header.ramGb);
        assertEquals("Snapdragon 8+ Gen 1", header.chip);
        assertEquals("16", header.androidVersion);
    }

    @Test
    public void chipNameJoinsMakerAndModelOrFallsBackToHardware() {
        assertEquals("Qualcomm SM8475", TaiWelcomeCard.chipName("Qualcomm", "SM8475", "qcom"));
        assertEquals("Google Tensor G5", TaiWelcomeCard.chipName("Google", "Google Tensor G5", "tensor"));
        assertEquals("qcom", TaiWelcomeCard.chipName("unknown", "unknown", "qcom"));
        assertEquals("", TaiWelcomeCard.chipName(null, null, null));
    }

    // ------------------------------------------------------------------------------------ showing

    @Test
    public void theHomeScreenRaisesItOnceAndOnlyWhenIdle() {
        assertTrue(TaiWelcomeCard.shouldShowOnHome(false, false, false, false, true));
        assertFalse(TaiWelcomeCard.shouldShowOnHome(true, false, false, false, true));
        assertFalse(TaiWelcomeCard.shouldShowOnHome(false, true, false, false, true));
        assertFalse(TaiWelcomeCard.shouldShowOnHome(false, false, true, false, true));
        assertFalse(TaiWelcomeCard.shouldShowOnHome(false, false, false, true, true));
        assertFalse(TaiWelcomeCard.shouldShowOnHome(false, false, false, false, false));
    }
}
