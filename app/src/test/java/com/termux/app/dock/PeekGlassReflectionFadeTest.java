package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The reflection fades with Home's share across a crossing through its view's own alpha, so a
 * fade records nothing again, and a view stood up later wears the fade already set.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PeekGlassReflectionFadeTest {

    private static FrameLayout host() {
        return new FrameLayout(ApplicationProvider.getApplicationContext());
    }

    @Test public void theFadeIsTheViewsAlpha() {
        PeekGlassReflection reflection = new PeekGlassReflection(2.625f);
        View view = reflection.attachHost(host(), null);
        assertEquals(1f, view.getAlpha(), 0f);
        reflection.setFade(0.4f);
        assertEquals(0.4f, view.getAlpha(), 1e-6f);
        assertEquals(0.4f, reflection.fade(), 1e-6f);
    }

    @Test public void theFadeClamps() {
        PeekGlassReflection reflection = new PeekGlassReflection(2.625f);
        View view = reflection.attachHost(host(), null);
        reflection.setFade(-1f);
        assertEquals(0f, view.getAlpha(), 0f);
        reflection.setFade(3f);
        assertEquals(1f, view.getAlpha(), 0f);
    }

    @Test public void aViewAttachedLaterWearsTheFade() {
        PeekGlassReflection reflection = new PeekGlassReflection(2.625f);
        reflection.setFade(0.25f);
        View view = reflection.attachHost(host(), null);
        assertEquals(0.25f, view.getAlpha(), 1e-6f);
    }

    @Test public void apartWithHomesRowDownTheReflectionIsKeptNeverCleared() {
        // Apart with the Terminal's rows up: kept as Home's rest drew it.
        assertEquals(PeekGlassReflection.Sync.KEEP,
            PeekGlassReflection.syncFor(true, true, true, true, false));
        // Apart with Home's row standing (Home's rest, the slide once it stands): drawn.
        assertEquals(PeekGlassReflection.Sync.DRAW,
            PeekGlassReflection.syncFor(true, true, true, true, true));
        // No card, the knob at 0, or nothing to draw on: put away, apart or not.
        for (boolean apart : new boolean[] {false, true}) {
            assertEquals(PeekGlassReflection.Sync.CLEAR,
                PeekGlassReflection.syncFor(false, true, true, apart, false));
            assertEquals(PeekGlassReflection.Sync.CLEAR,
                PeekGlassReflection.syncFor(true, false, true, apart, false));
            assertEquals(PeekGlassReflection.Sync.CLEAR,
                PeekGlassReflection.syncFor(true, true, false, apart, true));
        }
    }

    @Test public void notApartTheBaseRuleHolds() {
        // The base drew the reflection whenever a card stood, whatever the row: never kept.
        for (boolean rowStands : new boolean[] {false, true}) {
            assertEquals(PeekGlassReflection.Sync.DRAW,
                PeekGlassReflection.syncFor(true, true, true, false, rowStands));
        }
    }

    /** A Home row whose reads are counted: every capture asks whether the icons move. */
    private static final class CountingRow implements PeekGlassReflection.Row {
        final View view = new View(ApplicationProvider.getApplicationContext());
        int reads;

        @Override public View view() {
            return view;
        }

        @Override public int iconBoxes(float[] out) {
            return 0;
        }

        @Override public boolean iconsMoving() {
            reads++;
            return false;
        }
    }

    private static void settle() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofSeconds(2));
    }

    @Test public void heldTheReflectionStaysAsHomesRestDrewIt() {
        float[] boxes = {100f, 22f, 260f, 182f, 400f, 22f, 560f, 182f};
        PeekGlassGeometry.Card card = PeekGlassGeometry.compute(boxes, 2, 0f, 1080, 400, 1080,
            700f, 2.625f);
        PeekGlassReflection reflection = new PeekGlassReflection(2.625f);
        View view = reflection.attachHost(host(), null);
        CountingRow row = new CountingRow();
        // Home's rest: drawn for its card, all of it shown, the mirror on the card's cut.
        reflection.update(card, row, 0f, 0f,
            PeekGlassGeometry.reflectionAlpha(PeekGlassGeometry.REFLECTION_DEFAULT), false);
        reflection.setFade(1f);
        reflection.setDrawnOffsetY(0f);
        settle();
        int restVisibility = view.getVisibility();
        float restAlpha = view.getAlpha();
        float restY = view.getTranslationY();
        assertEquals(View.VISIBLE, restVisibility);

        // The Terminal in front, the cards apart: what KEEP does, with the values the activity
        // reads apart (Home's share 1 under Floating, no drift).
        reflection.setHeld(true);
        reflection.setFade(1f);
        reflection.setDrawnOffsetY(0f);
        int reads = row.reads;
        reflection.onRowContentChanged();
        reflection.onIconsMoved();
        settle();
        assertEquals("nothing is read from a row that does not stand", reads, row.reads);
        assertEquals(restVisibility, view.getVisibility());
        assertEquals(restAlpha, view.getAlpha(), 0f);
        assertEquals(restY, view.getTranslationY(), 0f);
        assertTrue(reflection.held());

        // Home's row stands again: the change asked for meanwhile is captured once.
        reflection.setHeld(false);
        settle();
        assertTrue(row.reads > reads);
        assertEquals(View.VISIBLE, view.getVisibility());
    }

    @Test public void theMirrorFollowsTheRowsThroughItsViewsTranslation() {
        PeekGlassReflection reflection = new PeekGlassReflection(2.625f);
        View view = reflection.attachHost(host(), null);
        reflection.setDrawnOffsetY(-175f);
        assertEquals(-175f, view.getTranslationY(), 0f);
        reflection.setDrawnOffsetY(0f);
        assertEquals(0f, view.getTranslationY(), 0f);
    }
}
