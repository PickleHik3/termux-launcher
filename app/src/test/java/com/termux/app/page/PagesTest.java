package com.termux.app.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.view.View;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PagesTest {

    /** A page that logs its on-front and off-front and spends Back when told to. */
    static final class FakePage implements Page {
        final PageKind kind;
        final List<String> log;
        boolean spendsBack;
        int on, off, back;

        FakePage(PageKind kind, List<String> log) {
            this.kind = kind;
            this.log = log;
        }

        @NonNull @Override public PageKind kind() { return kind; }
        @NonNull @Override public View view() { throw new UnsupportedOperationException(); }
        @NonNull @Override public Rest rest(@NonNull Rest.Inputs in) { throw new UnsupportedOperationException(); }
        @Override public void onFront() { on++; log.add(kind + ":on"); }
        @Override public void offFront() { off++; log.add(kind + ":off"); }
        @Override public boolean onBack() { back++; return spendsBack; }
    }

    private int asks;
    private int homeCalls;
    private boolean homeMoves;
    private List<String> log;
    private Pages pages;

    @Before
    public void setUp() {
        asks = 0;
        homeCalls = 0;
        homeMoves = true;
        log = new ArrayList<>();
        pages = new Pages(PageHost.NONE, () -> asks++);
        pages.setHomeDefault(() -> { homeCalls++; return homeMoves; });
    }

    @Test
    public void landingRecordsTheFrontAndAsksOnce() {
        pages.landed(7, PageKind.ASSISTANT);
        assertEquals(7, pages.frontId());
        assertEquals(PageKind.ASSISTANT, pages.frontKind());
        assertEquals(1, asks);
    }

    @Test
    public void theSameLandingTwiceAsksOnce() {
        pages.landed(7, PageKind.ASSISTANT);
        pages.landed(7, PageKind.ASSISTANT);
        assertEquals(1, asks);
    }

    @Test
    public void frontIsTheRegisteredPageFromTheLandingOn() {
        FakePage a = new FakePage(PageKind.ASSISTANT, log);
        pages.put(7, a);
        assertNull(pages.front());
        pages.landed(7, PageKind.ASSISTANT);
        assertSame(a, pages.front());
        assertSame(a, pages.page(7));
        pages.remove(7);
        assertNull(pages.front());
    }

    @Test
    public void applyRunsOffAndOnOncePerChange() {
        FakePage a = new FakePage(PageKind.ASSISTANT, log);
        FakePage b = new FakePage(PageKind.TERMINAL, log);
        pages.put(1, a);
        pages.put(2, b);
        pages.landed(1, PageKind.ASSISTANT);
        pages.applyFront();
        pages.applyFront();
        assertEquals(1, a.on);
        assertEquals(0, a.off);
        pages.landed(2, PageKind.TERMINAL);
        pages.applyFront();
        pages.applyFront();
        assertEquals(1, a.off);
        assertEquals(1, b.on);
        assertEquals(List.of("ASSISTANT:on", "ASSISTANT:off", "TERMINAL:on"), log);
    }

    @Test
    public void comingBackToTheSameFrontBeforeApplyRunsNothing() {
        FakePage a = new FakePage(PageKind.ASSISTANT, log);
        FakePage b = new FakePage(PageKind.TERMINAL, log);
        pages.put(1, a);
        pages.put(2, b);
        pages.landed(1, PageKind.ASSISTANT);
        pages.applyFront();
        log.clear();
        pages.landed(2, PageKind.TERMINAL);
        pages.landed(1, PageKind.ASSISTANT);
        pages.applyFront();
        assertTrue(log.isEmpty());
    }

    @Test
    public void aRemovedFrontStillGetsItsOffFront() {
        FakePage a = new FakePage(PageKind.ASSISTANT, log);
        FakePage b = new FakePage(PageKind.TERMINAL, log);
        pages.put(1, a);
        pages.put(2, b);
        pages.landed(1, PageKind.ASSISTANT);
        pages.applyFront();
        pages.remove(1);
        pages.landed(2, PageKind.TERMINAL);
        pages.applyFront();
        assertEquals(1, a.off);
        assertEquals(1, b.on);
    }

    @Test
    public void aFrontThatIsNotAContractPageRunsOnlyTheOldOffFront() {
        FakePage a = new FakePage(PageKind.ASSISTANT, log);
        pages.put(1, a);
        pages.landed(1, PageKind.ASSISTANT);
        pages.applyFront();
        pages.landed(9, PageKind.HOME);
        pages.applyFront();
        assertEquals(1, a.off);
        assertEquals(1, a.on);
        assertNull(pages.front());
        pages.applyFront();
        assertEquals(1, a.off);
    }

    @Test
    public void travelIsClamped() {
        assertEquals(0f, pages.progress(), 0f);
        pages.travelled(0.4f);
        assertEquals(0.4f, pages.progress(), 0f);
        pages.travelled(-1f);
        assertEquals(0f, pages.progress(), 0f);
        pages.travelled(3f);
        assertEquals(1f, pages.progress(), 0f);
    }

    @Test
    public void aSpentBackStopsAtTheFrontPage() {
        FakePage a = new FakePage(PageKind.ASSISTANT, log);
        a.spendsBack = true;
        pages.put(1, a);
        pages.landed(1, PageKind.ASSISTANT);
        assertTrue(pages.onBack());
        assertEquals(0, homeCalls);
    }

    @Test
    public void anUnspentBackFallsToTheHomeDefault() {
        FakePage a = new FakePage(PageKind.ASSISTANT, log);
        pages.put(1, a);
        pages.landed(1, PageKind.ASSISTANT);
        assertTrue(pages.onBack());
        assertEquals(1, a.back);
        assertEquals(1, homeCalls);
        homeMoves = false;
        assertFalse(pages.onBack());
    }

    @Test
    public void aNonContractFrontLeavesBackAlone() {
        pages.landed(9, PageKind.HOME);
        assertFalse(pages.onBack());
        assertEquals(0, homeCalls);
    }
}
