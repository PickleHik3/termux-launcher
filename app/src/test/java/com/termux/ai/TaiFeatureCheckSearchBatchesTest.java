package com.termux.ai;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TaiFeatureCheckSearchBatchesTest {

    private static List<String> notes(int count) {
        List<String> notes = new ArrayList<>();
        for (int i = 0; i < count; i++) notes.add("note " + i);
        return notes;
    }

    private static List<Integer> sizes(List<List<String>> batches) {
        List<Integer> sizes = new ArrayList<>();
        for (List<String> batch : batches) sizes.add(batch.size());
        return sizes;
    }

    @Test
    public void sixtyFourNotesGoInEightBatchesOfEightInOrder() {
        List<String> notes = notes(64);
        List<List<String>> batches = TaiFeatureCheckRunner.searchBatches(notes, TaiFeatureCheckRunner.SEARCH_BATCH);
        assertEquals(Arrays.asList(8, 8, 8, 8, 8, 8, 8, 8), sizes(batches));
        List<String> joined = new ArrayList<>();
        for (List<String> batch : batches) joined.addAll(batch);
        assertEquals(notes, joined);
    }

    @Test
    public void theLastBatchIsShorter() {
        assertEquals(Arrays.asList(8, 2), sizes(TaiFeatureCheckRunner.searchBatches(notes(10), 8)));
    }

    @Test
    public void fewerNotesThanABatchAreOneBatch() {
        assertEquals(Arrays.asList(3), sizes(TaiFeatureCheckRunner.searchBatches(notes(3), 8)));
    }

    @Test
    public void aSizeOfZeroIsOneBatch() {
        assertEquals(Arrays.asList(10), sizes(TaiFeatureCheckRunner.searchBatches(notes(10), 0)));
    }
}
