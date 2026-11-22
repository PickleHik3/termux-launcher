package com.termux.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LifecycleTrailTest {

    @Test
    public void firesOnTheLimitthEventOnly() {
        LifecycleTrail.Rate r = new LifecycleTrail.Rate(5, 60_000L);
        for (int i = 0; i < 4; i++) assertFalse(r.onEvent(1_000L * i));
        assertTrue(r.onEvent(4_000L));
    }

    @Test
    public void doesNotFireAgainInTheSameWindow() {
        LifecycleTrail.Rate r = new LifecycleTrail.Rate(5, 60_000L);
        for (int i = 0; i < 5; i++) r.onEvent(1_000L * i);
        assertFalse(r.onEvent(5_000L));
        assertFalse(r.onEvent(6_000L));
    }

    @Test
    public void eventsSpreadWiderThanTheWindowNeverFire() {
        LifecycleTrail.Rate r = new LifecycleTrail.Rate(5, 60_000L);
        for (int i = 0; i < 20; i++) assertFalse(r.onEvent(20_000L * i));
    }

    @Test
    public void firesAgainOnceTheWindowHasMovedOn() {
        LifecycleTrail.Rate r = new LifecycleTrail.Rate(5, 60_000L);
        for (int i = 0; i < 4; i++) r.onEvent(1_000L * i);
        assertTrue(r.onEvent(4_000L));
        long t = 100_000L;
        for (int i = 0; i < 4; i++) assertFalse(r.onEvent(t + 1_000L * i));
        assertTrue(r.onEvent(t + 4_000L));
    }
}
