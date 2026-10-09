package com.termux.app.firstrun;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.ai.TaiDeviceTier;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiPlatformCaps;
import com.termux.ai.TaiTierPolicy;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * The setup sheet's rules: which rows it carries, where each switch starts, what the weather switch
 * reveals, and how the AI switch, its ticks and the size in its sentence move together.
 */
public class WelcomeSheetTest {

    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;
    private static final String E2B = "gemma-4-e2b-it-litert-lm";

    private static final Map<String, Long> SIZES = new HashMap<>();
    static {
        SIZES.put("whisper-acft-base-en", 100L * MIB);
        SIZES.put("whisper-acft-small-en", 286L * MIB);
        SIZES.put(TaiModelCatalog.KITTEN_TTS_NANO_ID, 94L * MIB);
        SIZES.put(E2B, 2_780_000_000L);
        SIZES.put(TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_VISION_440M_ID, 388L * MIB);
        SIZES.put(TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_270M_ID, 165L * MIB);
    }

    private static final TaiWelcomeCard.Sizes SIZE_TABLE = id -> {
        Long size = SIZES.get(id);
        return size == null ? 0L : size;
    };

    private static List<TaiWelcomeCard.Row> rows(TaiDeviceTier tier, String... installed) {
        TaiTierPolicy.Env env = new TaiTierPolicy.Env(tier, 8 * GIB, 34, true, true,
            TaiPlatformCaps.GpuPath.YES, true);
        return TaiWelcomeCard.rows(env, new HashSet<>(Arrays.asList(installed)), SIZE_TABLE);
    }

    /** A first launch on a mid-range phone with the display in the build. */
    private static WelcomeSheet.Start firstLaunch() {
        WelcomeSheet.Start start = new WelcomeSheet.Start();
        start.wallpaperColours = true;
        start.weatherEnabled = true;
        start.displayOffered = true;
        start.aiRows = rows(TaiDeviceTier.TIER_2);
        return start;
    }

    @Test public void aFirstLaunchStartsAsTheHandoffSays() {
        WelcomeSheet sheet = WelcomeSheet.from(firstLaunch());
        assertEquals(Arrays.asList(WelcomeSheet.Row.WALLPAPER, WelcomeSheet.Row.WEATHER,
            WelcomeSheet.Row.DISPLAY, WelcomeSheet.Row.AI), sheet.rows());
        assertTrue(sheet.isOn(WelcomeSheet.Row.WALLPAPER));
        assertFalse("weather with nowhere to be for is off", sheet.isOn(WelcomeSheet.Row.WEATHER));
        assertFalse(sheet.isOn(WelcomeSheet.Row.DISPLAY));
        assertTrue("the policy preselects this phone's models", sheet.isOn(WelcomeSheet.Row.AI));
        assertFalse(sheet.showsWeatherSearch());
        assertTrue(sheet.showsChooseModels());
        assertFalse(sheet.modelsExpanded());
    }

    @Test public void aReplayReadsWhatTheUserHasChosen() {
        WelcomeSheet.Start start = firstLaunch();
        start.wallpaperColours = false;
        start.weatherPlace = "Kuwait City";
        start.displayOn = true;
        WelcomeSheet sheet = WelcomeSheet.from(start);
        assertFalse(sheet.isOn(WelcomeSheet.Row.WALLPAPER));
        assertTrue(sheet.isOn(WelcomeSheet.Row.WEATHER));
        assertTrue(sheet.isOn(WelcomeSheet.Row.DISPLAY));
        start.weatherPlace = "";
        start.locationGranted = true;
        assertTrue(WelcomeSheet.from(start).isOn(WelcomeSheet.Row.WEATHER));
        start.weatherEnabled = false;
        assertFalse(WelcomeSheet.from(start).isOn(WelcomeSheet.Row.WEATHER));
    }

    @Test public void rowsTheBuildOrPhoneCannotOfferAreLeftOut() {
        WelcomeSheet.Start start = firstLaunch();
        start.displayOffered = false;
        start.displayOn = true;
        start.aiRows = Collections.emptyList();
        WelcomeSheet sheet = WelcomeSheet.from(start);
        assertEquals(Arrays.asList(WelcomeSheet.Row.WALLPAPER, WelcomeSheet.Row.WEATHER),
            sheet.rows());
        assertFalse(sheet.isOn(WelcomeSheet.Row.DISPLAY));
        sheet.setOn(WelcomeSheet.Row.AI, true);
        assertFalse(sheet.isOn(WelcomeSheet.Row.AI));
    }

    @Test public void turningTheWeatherOnRevealsTheSearchAndOffHidesIt() {
        WelcomeSheet sheet = WelcomeSheet.from(firstLaunch());
        sheet.setOn(WelcomeSheet.Row.WEATHER, true);
        assertTrue(sheet.showsWeatherSearch());
        sheet.setOn(WelcomeSheet.Row.WEATHER, false);
        assertFalse(sheet.showsWeatherSearch());
    }

    @Test public void theSizeIsTheSumOfTheTickedModels() {
        WelcomeSheet sheet = WelcomeSheet.from(firstLaunch());
        long all = 0L;
        for (TaiWelcomeCard.Row row : sheet.aiRows()) all += row.downloadBytes;
        assertEquals(all, sheet.selectedBytes());
        sheet.toggleModel("assistant");
        assertEquals(all - SIZES.get(E2B), sheet.selectedBytes());
        assertEquals(WelcomeSheet.AiSize.SELECTED, sheet.aiSize());
    }

    @Test public void unTickingTheLastModelTurnsTheSwitchOffAndTickingOneTurnsItOn() {
        WelcomeSheet sheet = WelcomeSheet.from(firstLaunch());
        for (TaiWelcomeCard.Row row : sheet.aiRows()) sheet.toggleModel(row.id);
        assertFalse(sheet.isOn(WelcomeSheet.Row.AI));
        assertFalse(sheet.showsChooseModels());
        assertEquals(0L, sheet.selectedBytes());
        sheet.toggleModel("read_aloud");
        assertTrue(sheet.isOn(WelcomeSheet.Row.AI));
        assertEquals(94L * MIB, sheet.selectedBytes());
    }

    @Test public void turningTheSwitchBackOnRestoresThePreselection() {
        WelcomeSheet sheet = WelcomeSheet.from(firstLaunch());
        for (TaiWelcomeCard.Row row : sheet.aiRows()) sheet.toggleModel(row.id);
        sheet.setOn(WelcomeSheet.Row.AI, true);
        assertEquals(TaiWelcomeCard.initialTicks(sheet.aiRows()), sheet.tickedRowIds());
    }

    @Test public void aPhoneWithNoPreselectionOpensTheListInstead() {
        WelcomeSheet.Start start = firstLaunch();
        start.aiRows = rows(TaiDeviceTier.TIER_1);
        WelcomeSheet sheet = WelcomeSheet.from(start);
        assertFalse(sheet.isOn(WelcomeSheet.Row.AI));
        sheet.setOn(WelcomeSheet.Row.AI, true);
        assertTrue(sheet.isOn(WelcomeSheet.Row.AI));
        assertTrue(sheet.modelsExpanded());
        assertTrue(sheet.downloadsOnLeave(SIZE_TABLE, 100 * GIB).isEmpty());
    }

    @Test public void chooseModelsOpensAndClosesTheList() {
        WelcomeSheet sheet = WelcomeSheet.from(firstLaunch());
        sheet.toggleModelsExpanded();
        assertTrue(sheet.modelsExpanded());
        sheet.toggleModelsExpanded();
        assertFalse(sheet.modelsExpanded());
        sheet.setOn(WelcomeSheet.Row.AI, false);
        sheet.toggleModelsExpanded();
        assertFalse("no list under a switch that is off", sheet.modelsExpanded());
    }

    @Test public void installedModelsCannotBeTickedAndAnAllInstalledPhoneSaysSo() {
        List<TaiWelcomeCard.Row> installed = rows(TaiDeviceTier.TIER_2, "whisper-acft-small-en",
            TaiModelCatalog.KITTEN_TTS_NANO_ID, E2B,
            TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_VISION_440M_ID);
        WelcomeSheet.Start start = firstLaunch();
        start.aiRows = installed;
        WelcomeSheet sheet = WelcomeSheet.from(start);
        assertFalse(sheet.isOn(WelcomeSheet.Row.AI));
        assertEquals(WelcomeSheet.AiSize.INSTALLED, sheet.aiSize());
        sheet.toggleModel("read_aloud");
        assertFalse(sheet.isTicked("read_aloud"));
    }

    @Test public void leavingQueuesTheTickedModelsSmallestFirstOnlyWhileTheSwitchIsOn() {
        WelcomeSheet sheet = WelcomeSheet.from(firstLaunch());
        List<String> queued = sheet.downloadsOnLeave(SIZE_TABLE, 100 * GIB);
        assertEquals(4, queued.size());
        assertEquals(TaiModelCatalog.KITTEN_TTS_NANO_ID, queued.get(0));
        assertEquals(E2B, queued.get(3));
        sheet.setOn(WelcomeSheet.Row.AI, false);
        assertTrue(sheet.downloadsOnLeave(SIZE_TABLE, 100 * GIB).isEmpty());
    }

    @Test public void aSelectionThatDoesNotFitTheStorageQueuesNothing() {
        WelcomeSheet sheet = WelcomeSheet.from(firstLaunch());
        assertFalse(sheet.fitsStorage(2 * GIB));
        assertTrue(sheet.downloadsOnLeave(SIZE_TABLE, 2 * GIB).isEmpty());
        assertTrue(sheet.fitsStorage(100 * GIB));
    }
}
