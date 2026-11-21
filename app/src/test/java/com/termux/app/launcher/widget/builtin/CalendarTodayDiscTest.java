package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CalendarTodayDiscTest {
    private final float[] out = new float[3];

    private float[] fit(float cx, float cy, float w, float h, float left, float cellW,
                        float pitch) {
        CalendarTodayDisc.fit(cx, cy, w, h, left, cellW, pitch, 2f, out);
        return out.clone();
    }

    @Test public void centresOnTheInkNotTheColumn() {
        float[] d = fit(13.5f, 10f, 14f, 9f, 0f, 26f, 30f);
        assertEquals(13.5f, d[0], 0.001f);
        assertEquals(10f, d[1], 0.001f);
    }

    @Test public void containsTwoDigitsAndGrowsPastASingleDigit() {
        float[] two = fit(13f, 10f, 14f, 9f, 0f, 26f, 30f);
        float[] one = fit(13f, 10f, 7f, 9f, 0f, 26f, 30f);
        assertTrue(two[2] > one[2]);
        assertTrue(Math.hypot(7f, 4.5f) <= two[2]);
    }

    @Test public void staysInsideItsCellAndClearOfNeighbours() {
        float[] d = fit(10f, 10f, 20f, 12f, 0f, 20f, 16f);
        assertTrue(d[2] * 2f <= 18f + 0.001f);
        assertTrue(d[2] * 2f <= 14f + 0.001f);
        assertTrue(d[0] - d[2] >= -0.001f && d[0] + d[2] <= 20.001f);
    }

    @Test public void edgeColumnsAreNudgedInsteadOfSpilling() {
        float[] first = fit(6f, 10f, 14f, 9f, 0f, 26f, 30f);
        assertTrue(first[0] - first[2] >= -0.001f);
        float[] last = fit(26f * 7 - 6f, 10f, 14f, 9f, 26f * 6, 26f, 30f);
        assertTrue(last[0] + last[2] <= 26f * 7 + 0.001f);
    }

    @Test public void aCellTooSmallForAnythingGivesAnEmptyDisc() {
        assertEquals(0f, fit(1f, 1f, 10f, 8f, 0f, 1f, 1f)[2], 0f);
    }
}
