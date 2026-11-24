package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SingleAnswerTest {

    @Test
    public void aDismissalWithNoAnswerRunsKeepOnce() {
        SingleAnswer question = new SingleAnswer();
        int[] kept = {0};
        question.dismissed(() -> kept[0]++);
        question.dismissed(() -> kept[0]++);
        assertEquals(1, kept[0]);
    }

    @Test
    public void anAnswerIsNotFollowedByKeepWhenTheDialogDismisses() {
        SingleAnswer question = new SingleAnswer();
        int[] saved = {0};
        int[] kept = {0};
        question.answer(() -> saved[0]++).run();
        question.dismissed(() -> kept[0]++);
        assertEquals(1, saved[0]);
        assertEquals(0, kept[0]);
    }

    @Test
    public void aKeepAnswerAndItsDismissalRunKeepOnce() {
        SingleAnswer question = new SingleAnswer();
        int[] kept = {0};
        question.answer(() -> kept[0]++).run();
        question.dismissed(() -> kept[0]++);
        assertEquals(1, kept[0]);
    }
}
