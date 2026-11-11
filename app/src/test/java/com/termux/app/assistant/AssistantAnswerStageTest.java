package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import static com.termux.app.assistant.AssistantEngine.AnswerPhase.NONE;

public class AssistantAnswerStageTest {

    private static final AssistantModelStatus.Kind READY = AssistantModelStatus.Kind.READY;

    @Test
    public void notGeneratingIsNone() {
        assertEquals(AssistantModelPresenter.Stage.NONE, AssistantModelPresenter.stage(false, true, READY, NONE));
        assertEquals(AssistantModelPresenter.Stage.NONE, AssistantModelPresenter.stage(false, false, READY, NONE));
    }

    @Test
    public void textArrivingIsResponding() {
        assertEquals(AssistantModelPresenter.Stage.RESPONDING, AssistantModelPresenter.stage(true, false, READY, NONE));
    }

    @Test
    public void noTextWithReadyModelIsThinking() {
        assertEquals(AssistantModelPresenter.Stage.THINKING, AssistantModelPresenter.stage(true, true, READY, NONE));
    }

    @Test
    public void noTextWithUnloadedModelIsLoading() {
        assertEquals(AssistantModelPresenter.Stage.LOADING_MODEL,
            AssistantModelPresenter.stage(true, true, AssistantModelStatus.Kind.DOWNLOADED, NONE));
        assertEquals(AssistantModelPresenter.Stage.LOADING_MODEL,
            AssistantModelPresenter.stage(true, true, AssistantModelStatus.Kind.LOADING, NONE));
    }

    @Test
    public void waitingPhaseIsWaitingForEveryKind() {
        AssistantEngine.AnswerPhase w = AssistantEngine.AnswerPhase.WAITING;
        for (AssistantModelStatus.Kind kind : AssistantModelStatus.Kind.values()) {
            assertEquals(AssistantModelPresenter.Stage.WAITING, AssistantModelPresenter.stage(true, true, kind, w));
            assertEquals(AssistantModelPresenter.Stage.RESPONDING, AssistantModelPresenter.stage(true, false, kind, w));
            assertEquals(AssistantModelPresenter.Stage.NONE, AssistantModelPresenter.stage(false, true, kind, w));
        }
    }

    @Test
    public void phaseDecidesWhileTheTextIsEmpty() {
        AssistantModelStatus.Kind notReady = AssistantModelStatus.Kind.DOWNLOADED;
        assertEquals(AssistantModelPresenter.Stage.LOADING_MODEL,
            AssistantModelPresenter.stage(true, true, READY, AssistantEngine.AnswerPhase.LOADING));
        assertEquals(AssistantModelPresenter.Stage.THINKING,
            AssistantModelPresenter.stage(true, true, notReady, AssistantEngine.AnswerPhase.STARTED));
        assertEquals(AssistantModelPresenter.Stage.RESPONDING,
            AssistantModelPresenter.stage(true, false, notReady, AssistantEngine.AnswerPhase.LOADING));
        assertEquals(AssistantModelPresenter.Stage.NONE,
            AssistantModelPresenter.stage(false, true, READY, AssistantEngine.AnswerPhase.STARTED));
    }
}
