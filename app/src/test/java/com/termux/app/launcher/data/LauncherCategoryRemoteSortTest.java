package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The remote sort's request plan: blocks, the follow-up block, the one-app fallback, and stops. */
public class LauncherCategoryRemoteSortTest {

    @Test
    public void chunks_areAsFewAndAsEvenAsTheCapAllows() {
        assertTrue(LauncherCategoryRemoteSort.chunks(Collections.emptyList()).isEmpty());
        assertEquals(Collections.singletonList(37), sizes(LauncherCategoryRemoteSort.chunks(apps(37))));
        assertEquals(Collections.singletonList(100), sizes(LauncherCategoryRemoteSort.chunks(apps(100))));
        assertEquals(Arrays.asList(51, 50), sizes(LauncherCategoryRemoteSort.chunks(apps(101))));
        assertEquals(Arrays.asList(75, 75), sizes(LauncherCategoryRemoteSort.chunks(apps(150))));
        assertEquals(Arrays.asList(84, 83, 83), sizes(LauncherCategoryRemoteSort.chunks(apps(250))));
    }

    @Test
    public void chunks_keepEveryAppOnceInOrder() {
        List<String> all = apps(233);
        List<String> joined = new ArrayList<>();
        for (List<String> chunk : LauncherCategoryRemoteSort.chunks(all)) {
            assertTrue(chunk.size() <= LauncherCategoryRemoteSort.MAX_APPS_PER_REQUEST);
            joined.addAll(chunk);
        }
        assertEquals(all, joined);
    }

    @Test
    public void maxTokens_growsWithTheBlockAndIsCapped() {
        assertEquals(LauncherCategoryRemoteSort.TOKEN_HEADROOM + LauncherCategoryRemoteSort.TOKENS_PER_APP,
            LauncherCategoryRemoteSort.maxTokens(1));
        assertEquals(10 * LauncherCategoryRemoteSort.TOKENS_PER_APP + LauncherCategoryRemoteSort.TOKEN_HEADROOM,
            LauncherCategoryRemoteSort.maxTokens(10));
        assertEquals(LauncherCategoryRemoteSort.MAX_TOKENS_CAP,
            LauncherCategoryRemoteSort.maxTokens(LauncherCategoryRemoteSort.MAX_APPS_PER_REQUEST));
        // A full block's worth still fits under the cap.
        assertTrue(LauncherCategoryRemoteSort.MAX_APPS_PER_REQUEST * LauncherCategoryRemoteSort.TOKENS_PER_APP
            <= LauncherCategoryRemoteSort.MAX_TOKENS_CAP);
    }

    @Test
    public void aCompleteReply_sortsADrawerInTwoRequests() {
        Fake fake = new Fake();
        LauncherCategoryRemoteSort.Result result = LauncherCategoryRemoteSort.run(apps(150), fake);
        assertEquals(2, result.requests);
        assertEquals(150, result.assigned);
        assertNull(result.error);
        assertEquals(150, fake.settled);
        assertEquals(150, fake.assigned.size());
        assertTrue(fake.singles.isEmpty());
    }

    @Test
    public void missingApps_getOneFollowUpBlock_thenOneAppAtATime() {
        Fake fake = new Fake();
        List<String> all = apps(150);
        // The first answers leave out three apps; the follow-up names one of them.
        fake.drop.addAll(Arrays.asList(all.get(3), all.get(80), all.get(149)));
        fake.dropInFollowUp.addAll(Arrays.asList(all.get(80), all.get(149)));
        fake.singleMiss.add(all.get(149));
        fake.firstRoundBlocks = 2;

        LauncherCategoryRemoteSort.Result result = LauncherCategoryRemoteSort.run(all, fake);

        assertEquals(Arrays.asList(75, 75, 3), sizes(fake.batches));
        assertEquals(Arrays.asList(all.get(3), all.get(80), all.get(149)), fake.batches.get(2));
        assertEquals(Arrays.asList(all.get(80), all.get(149)), fake.singles);
        assertEquals(5, result.requests);
        assertEquals(149, result.assigned);
        assertNull(result.error);
        // Every app is counted once, the one nobody could place included.
        assertEquals(150, fake.settled);
        assertFalse(fake.assigned.containsKey(all.get(149)));
    }

    @Test
    public void appsTheReplyWasNotAskedAbout_areIgnored() {
        Fake fake = new Fake();
        fake.extra.put("com.invented.app", "games");
        LauncherCategoryRemoteSort.Result result = LauncherCategoryRemoteSort.run(apps(5), fake);
        assertEquals(5, result.assigned);
        assertFalse(fake.assigned.containsKey("com.invented.app"));
    }

    @Test
    public void aFailedRequest_stopsTheRunAndKeepsWhatWasSorted() {
        Fake fake = new Fake();
        fake.failBatchNumber = 2;
        LauncherCategoryRemoteSort.Result result = LauncherCategoryRemoteSort.run(apps(150), fake);
        assertEquals("rate limited", result.error);
        assertEquals(2, result.requests);
        assertEquals(75, result.assigned);
        assertEquals(75, fake.settled);
        assertTrue(fake.singles.isEmpty());
    }

    @Test
    public void aFailedSingleRequest_stopsTheFallback() {
        Fake fake = new Fake();
        List<String> all = apps(4);
        fake.drop.addAll(all);
        fake.dropInFollowUp.addAll(all);
        fake.failSingles = true;
        fake.firstRoundBlocks = 1;
        LauncherCategoryRemoteSort.Result result = LauncherCategoryRemoteSort.run(all, fake);
        assertEquals("rate limited", result.error);
        assertEquals(Collections.singletonList(all.get(0)), fake.singles);
        assertEquals(3, result.requests);
        assertEquals(0, result.assigned);
    }

    @Test
    public void cancel_stopsBeforeTheNextRequest() {
        Fake fake = new Fake();
        fake.cancelAfterBatches = 1;
        LauncherCategoryRemoteSort.Result result = LauncherCategoryRemoteSort.run(apps(150), fake);
        assertEquals(1, result.requests);
        assertEquals(75, result.assigned);
        assertNull(result.error);
    }

    // ---------------------------------------------------------------- helpers

    @NonNull
    private static List<String> apps(int count) {
        List<String> apps = new ArrayList<>();
        for (int i = 0; i < count; i++) apps.add("com.example.app" + i);
        return apps;
    }

    @NonNull
    private static List<Integer> sizes(@NonNull List<List<String>> chunks) {
        List<Integer> sizes = new ArrayList<>();
        for (List<String> chunk : chunks) sizes.add(chunk.size());
        return sizes;
    }

    /** Answers every app with "utilities" unless told to drop it, fail, or cancel. */
    private static final class Fake implements LauncherCategoryRemoteSort.Requests {
        final List<List<String>> batches = new ArrayList<>();
        final List<String> singles = new ArrayList<>();
        final Set<String> drop = new HashSet<>();
        final Set<String> dropInFollowUp = new HashSet<>();
        final Set<String> singleMiss = new HashSet<>();
        final Map<String, String> extra = new HashMap<>();
        final Map<String, String> assigned = new LinkedHashMap<>();
        int settled;
        int failBatchNumber;
        boolean failSingles;
        int cancelAfterBatches = -1;
        /** Blocks after this many are the follow-up round, which drops {@link #dropInFollowUp}. */
        int firstRoundBlocks = Integer.MAX_VALUE;

        @Override
        public boolean cancelled() {
            return cancelAfterBatches >= 0 && batches.size() >= cancelAfterBatches;
        }

        @NonNull
        @Override
        public LauncherCategoryRemoteSort.Answer batch(@NonNull List<String> packages) {
            batches.add(packages);
            if (batches.size() == failBatchNumber) return LauncherCategoryRemoteSort.Answer.failed("rate limited");
            boolean followUp = batches.size() > firstRoundBlocks;
            Map<String, String> reply = new LinkedHashMap<>(extra);
            for (String packageName : packages) {
                if ((followUp ? dropInFollowUp : drop).contains(packageName)) continue;
                reply.put(packageName, "utilities");
            }
            return LauncherCategoryRemoteSort.Answer.of(reply);
        }

        @NonNull
        @Override
        public LauncherCategoryRemoteSort.Answer single(@NonNull String packageName) {
            singles.add(packageName);
            if (failSingles) return LauncherCategoryRemoteSort.Answer.failed("rate limited");
            if (singleMiss.contains(packageName)) return LauncherCategoryRemoteSort.Answer.of(Collections.emptyMap());
            return LauncherCategoryRemoteSort.Answer.of(Collections.singletonMap(packageName, "social"));
        }

        @Override
        public void settled(@NonNull Map<String, String> slugs, int count) {
            assigned.putAll(slugs);
            settled += count;
        }
    }
}
