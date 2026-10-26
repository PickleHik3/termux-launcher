package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;

import org.junit.Test;

import java.util.List;

/**
 * Seam 4 of spec #101, for Media (#119): at every span, at the design size, a narrow card and at
 * font scales 1.0, 1.3 and 2.0, in each of the three modes: nothing overlaps, every part sits
 * inside the inset, no text is under 11 sp and every transport button is a 48 dp target. Nothing
 * playing with a last app offers "Resume <app>".
 */
public class MediaLayoutPlanTest {
    private static final float[] FONT_SCALES = { 1.0f, 1.3f, 2.0f };
    private static final WidgetLayoutPlan.Measure MEASURE = (text, role, numeric, size) -> {
        float em = 0f;
        for (int i = 0; i < text.length(); i++) em += Character.isDigit(text.charAt(i)) ? 0.57f : 0.6f;
        return em * size;
    };

    private static MediaLayoutPlanner.Content playing() {
        return new MediaLayoutPlanner.Content(MediaLayoutPlanner.Mode.PLAYING, "Low Tide",
            "Halcyon Tapes", "Spotify", true);
    }

    private static MediaLayoutPlanner.Content resume() {
        return new MediaLayoutPlanner.Content(MediaLayoutPlanner.Mode.RESUME, "Resume Spotify", "",
            "Spotify", false);
    }

    private static MediaLayoutPlanner.Content idle() {
        return new MediaLayoutPlanner.Content(MediaLayoutPlanner.Mode.IDLE, "Nothing playing", "",
            "", false);
    }

    @NonNull private static WidgetLayoutPlan plan(BuiltinWidgetSpan span, float w, float h, float fs,
                                                  MediaLayoutPlanner.Content content) {
        return MediaLayoutPlanner.plan(span, w, h, fs, content, MEASURE);
    }

    private static void check(@NonNull WidgetLayoutPlan plan, @NonNull String what) {
        List<Part> parts = plan.parts;
        for (Part part : parts) {
            assertTrue(what + ": " + part + " inside " + plan.inset, plan.inset.contains(part.box));
            if (part.role != null) {
                assertTrue(what + ": " + part + " text floor",
                    part.sizeDp >= 11f * plan.fontScale - 0.01f);
            }
        }
        // The 1x1's play button sits on the art by design.
        if (plan.span == BuiltinWidgetSpan.ONE_BY_ONE) return;
        for (int i = 0; i < parts.size(); i++) {
            for (int j = i + 1; j < parts.size(); j++) {
                Part a = parts.get(i), b = parts.get(j);
                assertFalse(what + ": " + a + " overlaps " + b, a.box.overlaps(b.box));
            }
        }
    }

    @Test public void everySpanFontScaleAndModeFitsItsInset() {
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            for (float fs : FONT_SCALES) {
                for (MediaLayoutPlanner.Content content : new MediaLayoutPlanner.Content[] {
                    playing(), resume(), idle() }) {
                    check(plan(span, span.widthDp, span.heightDp, fs, content),
                        span + " " + fs + " " + content.mode);
                }
            }
        }
        check(plan(BuiltinWidgetSpan.TWO_BY_ONE, 109f, 92f, 1f, playing()), "2x1 narrow");
        check(plan(BuiltinWidgetSpan.FOUR_BY_ONE, 245f, 92f, 1f, playing()), "4x1 narrow");
    }

    @Test public void transportButtonsAreFortyEightDp() {
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            for (float fs : FONT_SCALES) {
                WidgetLayoutPlan plan = plan(span, span.widthDp, span.heightDp, fs, playing());
                for (String id : new String[] { "prev", "play", "next" }) {
                    Part button = plan.part(id);
                    if (button == null) continue;
                    assertTrue(span + " " + id, button.box.width >= 48f - 0.01f
                        && button.box.height >= 48f - 0.01f);
                }
                assertNotNull(span + " keeps play", plan.part("play"));
            }
        }
        WidgetLayoutPlan deck = plan(BuiltinWidgetSpan.FOUR_BY_TWO, 360f, 192f, 1f, playing());
        assertTrue(deck.has("prev") && deck.has("next"));
    }

    @Test public void artIsPresentAtEverySpanAtDesignSize() {
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            WidgetLayoutPlan plan = plan(span, span.widthDp, span.heightDp, 1f, playing());
            Part art = plan.part("art");
            assertNotNull(span.toString(), art);
            assertTrue(span + " art " + art.box, art.box.width >= MediaLayoutPlanner.ART_MIN_DP - 0.01f
                && art.box.height >= MediaLayoutPlanner.ART_MIN_DP - 0.01f);
        }
    }

    @Test public void nothingPlayingOffersToResumeTheLastApp() {
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            WidgetLayoutPlan plan = plan(span, span.widthDp, span.heightDp, 1f, resume());
            assertTrue(span + " play", plan.has("play"));
            assertFalse(span + " no prev", plan.has("prev"));
            assertFalse(span + " no next", plan.has("next"));
            assertFalse(span + " no bar", plan.has("bar"));
            assertTrue(span + " icon", plan.has("art"));
            if (span != BuiltinWidgetSpan.ONE_BY_ONE) {
                assertEquals("Resume Spotify", plan.part("title").text);
            }
        }
    }

    @Test public void deckKeepsProgressAndSourceOnTheBigCard() {
        WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.FOUR_BY_TWO, 360f, 192f, 1f, playing());
        assertTrue(plan.has("bar") && plan.has("elapsed") && plan.has("total"));
        assertTrue(plan.has("source") && plan.has("artist") && plan.has("title"));
    }

    @Test public void shortRoomDropsTheArtistBeforeTheTitleAndControls() {
        WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.TWO_BY_ONE, 184f, 92f, 1f, playing());
        assertTrue(plan.has("title") && plan.has("play"));
        assertFalse(plan.has("artist"));
    }
}
