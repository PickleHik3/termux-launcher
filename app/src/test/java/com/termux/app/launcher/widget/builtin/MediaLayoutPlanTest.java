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
        assertTrue(plan.has("bar") && !plan.has("elapsed") && !plan.has("total"));
        assertTrue(plan.has("source") && plan.has("artist") && plan.has("title"));
    }

    @Test public void shortRoomDropsTheArtistBeforeTheTitleAndControls() {
        WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.TWO_BY_ONE, 184f, 92f, 1f, playing());
        assertTrue(plan.has("title") && plan.has("play"));
        assertFalse(plan.has("artist"));
    }

    @Test public void theTwoByTwoIsAPolaroid() {
        for (float fs : new float[] { 1.0f, 1.3f }) {
            WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.TWO_BY_TWO, 184f, 280f, fs, playing());
            String what = "2x2 " + fs;
            check(plan, what);
            assertEquals(what + " thin inset", MediaLayoutPlanner.POLAROID_INSET_DP, plan.inset.left,
                0.01f);
            Part art = plan.part("art"), title = plan.part("title"), artist = plan.part("artist");
            Part bar = plan.part("bar"), play = plan.part("play");
            assertNotNull(what, art);
            assertEquals(what + " square", art.box.width, art.box.height, 0.01f);
            assertEquals(what + " centred", plan.inset.centerX(), art.box.centerX(), 0.01f);
            assertEquals(what + " at the top", plan.inset.top, art.box.top, 0.01f);
            assertTrue(what + " title below art", title.box.top >= art.box.bottom() - 0.01f);
            assertTrue(what + " artist below title", artist.box.top >= title.box.bottom() - 0.01f);
            assertTrue(what + " bar below artist", bar.box.top >= artist.box.bottom() - 0.01f);
            assertEquals(what + " bar held in", plan.inset.width - 12f, bar.box.width, 0.01f);
            assertTrue(what + " controls below bar", play.box.top >= bar.box.bottom() - 0.01f);
            assertEquals(what + " controls at the foot", plan.inset.bottom(), play.box.bottom(), 0.01f);
            assertEquals(what + " play centred", plan.inset.centerX(), play.box.centerX(), 0.01f);
            assertTrue(what, plan.has("prev") && plan.has("next"));
        }
        for (float[] size : new float[][] { { 109f, 115f }, { 184f, 200f } }) {
            WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.TWO_BY_TWO, size[0], size[1], 1f, playing());
            check(plan, "2x2 " + size[0] + "x" + size[1]);
            if (plan.has("art")) {
                assertEquals(plan.part("art").box.width, plan.part("art").box.height, 0.01f);
            }
        }
    }

    @Test public void upright2x2sArePolaroidsAndWiderOnesTakeTheColumnLayout() {
        float[][] polaroids = { { 184f, 280f }, { 184f, 200f }, { 200f, 184f }, { 109f, 115f } };
        for (float[] size : polaroids) {
            WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.TWO_BY_TWO, size[0], size[1], 1f, playing());
            assertEquals(size[0] + "x" + size[1], MediaLayoutPlanner.POLAROID_INSET_DP,
                plan.inset.left, 0.01f);
        }
        WidgetLayoutPlan wide = plan(BuiltinWidgetSpan.TWO_BY_TWO, 255f, 160f, 1f, playing());
        check(wide, "255x160");
        assertEquals(16f, wide.inset.left, 0.01f);
        Part art = wide.part("art"), title = wide.part("title");
        assertNotNull(art);
        assertEquals("art at the full inner height", wide.inset.height, art.box.height, 0.01f);
        assertEquals("art on the start side", wide.inset.left, art.box.left, 0.01f);
        assertTrue("column beside the art", title.box.left >= art.box.right() - 0.01f);
    }

    @Test public void twoRowResumeAndIdleStillOfferPlay() {
        for (MediaLayoutPlanner.Content content : new MediaLayoutPlanner.Content[] { resume(), idle() }) {
            for (float fs : FONT_SCALES) {
                WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.TWO_BY_TWO, 184f, 192f, fs, content);
                assertTrue(content.mode + " " + fs, plan.has("play") && plan.has("title"));
                assertFalse(plan.has("bar"));
            }
        }
    }

    @Test public void theControlsRowNeverOverflowsAtTheMinimumSizes() {
        float[][] sizes = { { 109f, 115f }, { 245f, 115f }, { 130f, 150f } };
        BuiltinWidgetSpan[] spans = { BuiltinWidgetSpan.TWO_BY_TWO, BuiltinWidgetSpan.FOUR_BY_TWO,
            BuiltinWidgetSpan.TWO_BY_TWO };
        for (int i = 0; i < sizes.length; i++) {
            for (float fs : FONT_SCALES) {
                WidgetLayoutPlan plan = plan(spans[i], sizes[i][0], sizes[i][1], fs, playing());
                check(plan, spans[i] + " min " + fs);
                Part play = plan.part("play");
                assertNotNull(spans[i] + " keeps play", play);
                assertTrue("play >= 40dp", play.box.width >= 40f - 0.01f);
                if (plan.has("prev")) {
                    assertTrue("buttons >= 40dp", plan.part("prev").box.width >= 40f - 0.01f);
                }
            }
        }
        // Three targets do not fit the 2x2 minimum's 77 dp: play stays alone.
        WidgetLayoutPlan narrow = plan(BuiltinWidgetSpan.TWO_BY_TWO, 109f, 115f, 1f, playing());
        assertFalse(narrow.has("prev") || narrow.has("next"));
    }

    @Test public void theFourByTwoKeepsTheArtSquareAtTheInnerHeightWithTheColumnBeside() {
        for (float[] size : new float[][] { { 376f, 208f }, { 366f, 180f }, { 376f, 192f } }) {
            WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.FOUR_BY_TWO, size[0], size[1], 1f, playing());
            Part art = plan.part("art"), bar = plan.part("bar"), play = plan.part("play");
            assertEquals(plan.inset.height, art.box.height, 0.01f);
            assertEquals(art.box.width, art.box.height, 0.01f);
            assertEquals(plan.inset.left, art.box.left, 0.01f);
            assertTrue(plan.part("title").box.left >= art.box.right());
            assertTrue(bar.box.left >= art.box.right() && play.box.left >= art.box.right());
            assertTrue(plan.has("prev") && plan.has("next"));
        }
    }
}
