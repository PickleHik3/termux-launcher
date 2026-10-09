package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.data.LauncherCategoryRemoteSort.Answer;
import com.termux.app.launcher.data.LauncherCategorySortPrompt.AppEntry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LauncherCategoryLocalSortTest {

    private static List<AppEntry> apps(int count) {
        List<AppEntry> apps = new ArrayList<>();
        for (int i = 0; i < count; i++) apps.add(new AppEntry("com.example.app" + i, "App " + i));
        return apps;
    }

    private static List<Integer> sizes(List<List<AppEntry>> batches) {
        List<Integer> sizes = new ArrayList<>();
        for (List<AppEntry> batch : batches) sizes.add(batch.size());
        return sizes;
    }

    @Test
    public void appsGoEightToARequestInOrder() {
        List<List<AppEntry>> batches = LauncherCategoryLocalSort.batches(apps(20));
        assertEquals(Arrays.asList(8, 8, 4), sizes(batches));
        assertEquals("com.example.app8", batches.get(1).get(0).packageName);
        assertTrue(LauncherCategoryLocalSort.batches(Collections.<AppEntry>emptyList()).isEmpty());
    }

    @Test
    public void aBatchShrinksToWhatFitsTheWindow() {
        List<AppEntry> apps = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            apps.add(new AppEntry("com.example.an.unusually.long.package.name.for.an.app" + i,
                "一二三四五六七八九十一二三四五六"));
        }
        List<List<AppEntry>> batches = LauncherCategoryLocalSort.batches(apps);
        int total = 0;
        for (List<AppEntry> batch : batches) {
            assertTrue(LauncherCategorySortPrompt.fitsWindow(batch));
            total += batch.size();
        }
        assertEquals(8, total);
        assertTrue(batches.size() > 1);
    }

    @Test
    public void appsABatchReplyMissesAreAskedOnceAlone() {
        Fake fake = new Fake();
        fake.batchAnswers.put("com.example.app0", "social");
        fake.batchAnswers.put("com.example.app2", "other");
        fake.batchAnswers.put("com.unasked.app", "games");
        fake.singleAnswers.put("com.example.app1", "finance");

        LauncherCategoryLocalSort.Result result = LauncherCategoryLocalSort.run(apps(4), fake);

        // One batch, then app1 and app3 alone; app3's single answer is a miss and is not retried.
        assertEquals(3, result.requests);
        assertEquals(Arrays.asList("com.example.app1", "com.example.app3"), fake.singles);
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("com.example.app0", "social");
        expected.put("com.example.app2", "other");
        expected.put("com.example.app1", "finance");
        assertEquals(expected, fake.settled);
        assertEquals(4, fake.settledCount);
    }

    @Test
    public void cancellingStopsBeforeTheNextRequest() {
        Fake fake = new Fake();
        fake.cancelAfterBatches = 1;
        LauncherCategoryLocalSort.Result result = LauncherCategoryLocalSort.run(apps(20), fake);
        // The first batch, nothing it missed (cancelled meanwhile), no second batch.
        assertEquals(1, result.requests);
        assertEquals(8, fake.settledCount);
    }

    static final class Fake implements LauncherCategoryLocalSort.Requests {
        final Map<String, String> batchAnswers = new HashMap<>();
        final Map<String, String> singleAnswers = new HashMap<>();
        final List<String> singles = new ArrayList<>();
        final Map<String, String> settled = new LinkedHashMap<>();
        int settledCount;
        int batches;
        int cancelAfterBatches = Integer.MAX_VALUE;

        @Override public boolean cancelled() {
            return batches >= cancelAfterBatches;
        }

        @Override public Answer batch(List<AppEntry> apps) {
            batches++;
            return Answer.of(new HashMap<>(batchAnswers));
        }

        @Override public Answer single(AppEntry app) {
            singles.add(app.packageName);
            String slug = singleAnswers.get(app.packageName);
            return Answer.of(slug == null ? Collections.<String, String>emptyMap()
                : Collections.singletonMap(app.packageName, slug));
        }

        @Override public void settled(Map<String, String> answered, int count) {
            settled.putAll(answered);
            settledCount += count;
        }
    }
}
