package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AssistantAnswerStageTest {

    private static final AssistantModelStatus.Kind READY = AssistantModelStatus.Kind.READY;

    @Test
    public void notGeneratingIsNone() {
        assertEquals(AssistantModelPresenter.Stage.NONE, AssistantModelPresenter.stage(false, true, READY));
        assertEquals(AssistantModelPresenter.Stage.NONE, AssistantModelPresenter.stage(false, false, READY));
    }

    @Test
    public void textArrivingIsResponding() {
        assertEquals(AssistantModelPresenter.Stage.RESPONDING, AssistantModelPresenter.stage(true, false, READY));
    }

    @Test
    public void noTextWithReadyModelIsThinking() {
        assertEquals(AssistantModelPresenter.Stage.THINKING, AssistantModelPresenter.stage(true, true, READY));
    }

    @Test
    public void noTextWithUnloadedModelIsLoading() {
        assertEquals(AssistantModelPresenter.Stage.LOADING_MODEL,
            AssistantModelPresenter.stage(true, true, AssistantModelStatus.Kind.DOWNLOADED));
        assertEquals(AssistantModelPresenter.Stage.LOADING_MODEL,
            AssistantModelPresenter.stage(true, true, AssistantModelStatus.Kind.LOADING));
    }
}
