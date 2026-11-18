package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import com.termux.app.page.PageKind;
import com.termux.app.page.Rest;

import org.junit.Test;

public class ResumeRestTest {

    private static Rest rest(boolean keyboardUp) {
        return new Rest(keyboardUp, PageKind.TERMINAL, false, true, true, PageKind.TERMINAL);
    }

    @Test public void anUnchangedRestInTheSameRoomKeepsEverySize() {
        ResumeRest resume = new ResumeRest();
        resume.record(rest(true), 42L);
        assertEquals(ResumeRest.Decision.KEEP, resume.decide(rest(true), 42L));
    }

    @Test public void aFrontThatIsNotAContractPageCountsAsTheSameWhenItStaysSo() {
        ResumeRest resume = new ResumeRest();
        resume.record(null, 7L);
        assertEquals(ResumeRest.Decision.KEEP, resume.decide(null, 7L));
    }

    @Test public void aChangedRestResizes() {
        ResumeRest resume = new ResumeRest();
        resume.record(rest(true), 42L);
        assertEquals(ResumeRest.Decision.RESIZE, resume.decide(rest(false), 42L));
        assertEquals(ResumeRest.Decision.RESIZE, resume.decide(null, 42L));
    }

    @Test public void aChangedRoomResizes() {
        ResumeRest resume = new ResumeRest();
        resume.record(rest(true), 42L);
        assertEquals(ResumeRest.Decision.RESIZE, resume.decide(rest(true), 43L));
    }

    @Test public void nothingRecordedResizes() {
        assertEquals(ResumeRest.Decision.RESIZE, new ResumeRest().decide(rest(true), 42L));
    }
}
