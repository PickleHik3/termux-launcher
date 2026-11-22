package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;

import com.termux.app.launcher.widget.builtin.NotificationsWidgetData.Group;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Seam 4 of spec #101, for Notifications: at every span, at the design size, the platform's
 * minimum and a 1×1's logical size, at font scales 1.0, 1.3 and 2.0, with a typical list, long
 * words and a notice: no part overlaps another, every part sits inside the inset, no text is under
 * 11 sp. A body takes both its lines when it has them and the room is there.
 */
public class NotificationsLayoutPlanTest {
    private static final float[] FONT_SCALES = { 1.0f, 1.3f, 2.0f };

    private static final WidgetLayoutPlan.Measure MEASURE = (text, role, numeric, size) -> {
        float em = 0f;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            em += Character.isDigit(c) ? 0.57f : c == ' ' ? 0.28f : 0.55f;
        }
        return em * size;
    };

    private static final NotificationsLayoutPlanner.Words WORDS = new NotificationsLayoutPlanner.Words() {
        @NonNull @Override public String headerMessages() { return "Messages"; }
        @NonNull @Override public String headerNotifications() { return "Notifications"; }
        @NonNull @Override public String clearAll() { return "Clear all"; }
        @NonNull @Override public String count(int count) { return String.valueOf(count); }
    };

    private static NotificationsWidgetData.Item item(String key, String app, String who, String text,
                                                     boolean person) {
        return new NotificationsWidgetData.Item(key, "pkg." + app, app, who, text, 1L, null, 0, person);
    }

    private static List<Group> groups(boolean longWords) {
        String mum = longWords ? "Supercalifragilisticexpialidocious_message_without_any_spaces_at_all"
            : "Are you coming for dinner tonight? Let me know";
        List<NotificationsWidgetData.Item> items = new ArrayList<>(Arrays.asList(
            item("1", "WhatsApp", longWords ? "Bartholomew-Maximilian" : "Mum", mum, true),
            item("2", "WhatsApp", "Mum", "Dinner is at eight", true),
            item("3", "WhatsApp", "Mum", "Call me", true),
            item("4", "Messages", "Alex", "I've sent the updated plans. See you tomorrow.", true),
            item("5", "Messages", "Alex", "Running late", true),
            item("6", "GitHub", "dependabot", "Bump gradle", false)));
        return NotificationsWidgetData.group(items);
    }

    private static WidgetLayoutPlan plan(BuiltinWidgetSpan span, float w, float h, float scale,
                                         List<Group> groups, NotificationsLayoutPlanner.Notice notice) {
        return NotificationsLayoutPlanner.plan(span, w, h, scale, groups, notice, true, WORDS, MEASURE);
    }

    private static List<float[]> sizes(BuiltinWidgetSpan span) {
        List<float[]> out = new ArrayList<>();
        if (span == BuiltinWidgetSpan.ONE_BY_ONE) {
            out.add(new float[] { 88f, 92f });
            out.add(new float[] { 57f, 57f });
            return out;
        }
        out.add(new float[] { span.widthDp, span.heightDp });
        out.add(new float[] { span.minWidthDp, span.minHeightDp });
        return out;
    }

    private static void check(WidgetLayoutPlan plan, String label) {
        List<Part> drawn = new ArrayList<>();
        for (Part part : plan.parts) {
            if (part.type == WidgetLayoutPlan.Type.TOUCH) continue;
            drawn.add(part);
            assertTrue(label + " inside inset: " + part, plan.inset.contains(part.box));
            if (part.role != null) {
                assertTrue(label + " floor: " + part, part.sizeDp >= WidgetTokens.TEXT_FLOOR_SP);
            }
        }
        for (int i = 0; i < drawn.size(); i++) {
            for (int j = i + 1; j < drawn.size(); j++) {
                if (drawn.get(i).box.overlaps(drawn.get(j).box)) {
                    fail(label + " overlap: " + drawn.get(i) + " " + drawn.get(j));
                }
            }
        }
        Part card = plan.part("touch");
        assertNotNull(card);
        assertTrue(label, card.box.width >= 48f && card.box.height >= 48f);
    }

    @Test public void everySpanFontScaleAndContentKeepsTheRules() {
        NotificationsLayoutPlanner.Notice denied = new NotificationsLayoutPlanner.Notice(
            "Allow notification access", "See your notifications here");
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            for (float[] size : sizes(span)) {
                for (float scale : FONT_SCALES) {
                    for (int content = 0; content < 4; content++) {
                        List<Group> groups = content == 0 ? groups(false) : content == 1
                            ? groups(true) : Collections.<Group>emptyList();
                        WidgetLayoutPlan p = plan(span, size[0], size[1], scale, groups,
                            content == 3 ? denied : null);
                        check(p, span + " " + size[0] + "x" + size[1] + " @" + scale + " c" + content);
                        if (content < 2 && span != BuiltinWidgetSpan.ONE_BY_ONE) {
                            assertTrue(p.has("body.0.0"));
                        }
                    }
                }
            }
        }
    }

    @Test public void twoByOneShowsTheButtonsAndATwoLineBody() {
        BuiltinWidgetSpan span = BuiltinWidgetSpan.TWO_BY_ONE;
        WidgetLayoutPlan p = plan(span, span.widthDp, span.heightDp, 1f, groups(false), null);
        assertTrue(p.has("dismiss.0"));
        assertFalse("the buttons keep their room before the icon", p.has("icon.0"));
        assertEquals("WhatsApp", p.part("title.0").text);
        assertEquals(WidgetTokens.Role.TITLE, p.part("title.0").role);
        assertTrue("second line is used", p.has("body.0.1"));
        assertFalse(p.has("body.0.2"));
        assertTrue(p.part("body.0.0").text.startsWith("Mum: Are you"));
    }

    @Test public void twoByTwoListsRowsWithBadgesAndAHeader() {
        BuiltinWidgetSpan span = BuiltinWidgetSpan.TWO_BY_TWO;
        WidgetLayoutPlan p = plan(span, span.widthDp, span.heightDp, 1f, groups(false), null);
        assertEquals("Messages", p.part("header.title").text);
        assertEquals("6", p.part("header.count").text);
        assertEquals("Mum", p.part("title.0").text);
        assertEquals("3", p.part("count.0").text);
        assertTrue(p.has("badge.0"));
        assertTrue(p.has("body.0.1"));
        assertEquals("Alex", p.part("title.1").text);
        assertEquals("2", p.part("count.1").text);
        assertTrue("both rows use both lines", p.has("body.1.1"));
        assertFalse("clear all is for the wide span", p.has("clear"));
    }

    @Test public void fourByTwoOffersClearAllOnlyWhenItCan() {
        BuiltinWidgetSpan span = BuiltinWidgetSpan.FOUR_BY_TWO;
        assertTrue(plan(span, span.widthDp, span.heightDp, 1f, groups(false), null).has("clear"));
        WidgetLayoutPlan none = NotificationsLayoutPlanner.plan(span, span.widthDp, span.heightDp, 1f,
            groups(false), null, false, WORDS, MEASURE);
        assertFalse(none.has("clear"));
    }

    @Test public void aShortCardDropsTheSecondLineBeforeTheTitle() {
        WidgetLayoutPlan p = plan(BuiltinWidgetSpan.TWO_BY_ONE, 184f, 76f, 1.3f, groups(false), null);
        assertTrue(p.has("title.0"));
        assertTrue(p.has("body.0.0"));
        assertFalse(p.has("body.0.1"));
    }

    @Test public void oneByOneIsABellWithTheCountAndTheTopSendersName() {
        WidgetLayoutPlan p = plan(BuiltinWidgetSpan.ONE_BY_ONE, 88f, 92f, 1f, groups(false), null);
        assertTrue(p.has("glyph"));
        assertEquals("6", p.part("header.count").text);
        assertEquals("Mum", p.part("title.0").text);
        assertFalse(p.has("body.0.0"));
    }

    @Test public void noGroupsDrawsTheNoticeNotRows() {
        WidgetLayoutPlan p = plan(BuiltinWidgetSpan.TWO_BY_TWO, 184f, 192f, 1f,
            Collections.<Group>emptyList(), new NotificationsLayoutPlanner.Notice("All clear", ""));
        assertTrue(p.has("glyph"));
        assertEquals("All clear", p.part("message.0").text);
        assertFalse(p.has("title.0"));
    }

    // ----- type from the card's size ---------------------------------------------------------

    @Test public void bodySizeGrowsWithTheRowsRoomWithinItsBounds() {
        float last = 0f;
        for (float row = 0f; row <= 400f; row += 0.5f) {
            float sp = NotificationsLayoutPlanner.bodySp(row);
            assertTrue("floor at " + row, sp >= NotificationsLayoutPlanner.BODY_MIN_SP);
            assertTrue("ceiling at " + row, sp <= NotificationsLayoutPlanner.BODY_MAX_SP);
            assertTrue("monotonic at " + row, sp >= last);
            last = sp;
        }
        assertEquals(NotificationsLayoutPlanner.BODY_MIN_SP, NotificationsLayoutPlanner.bodySp(0f), 0f);
        assertEquals(NotificationsLayoutPlanner.BODY_MAX_SP, NotificationsLayoutPlanner.bodySp(400f), 0f);
    }

    @Test public void rowTextGrowsWithTheCardsHeightAndFollowsTheFontScale() {
        for (BuiltinWidgetSpan span : new BuiltinWidgetSpan[] {
                BuiltinWidgetSpan.FOUR_BY_ONE, BuiltinWidgetSpan.FOUR_BY_TWO }) {
            for (float scale : FONT_SCALES) {
                float last = 0f;
                float first = -1f;
                for (float h = span.minHeightDp; h <= 400f; h += 4f) {
                    WidgetLayoutPlan p = plan(span, span.widthDp, h, scale, groups(false), null);
                    Part body = p.part("body.0.0");
                    assertNotNull(span + " " + h, body);
                    float sp = body.sizeDp / scale;
                    String label = span + " " + h + " @" + scale;
                    if (body.role == WidgetTokens.Role.LABEL_FORCED) continue;
                    assertTrue(label + " floor", sp >= NotificationsLayoutPlanner.BODY_MIN_SP - 1e-3f);
                    assertTrue(label + " ceiling", sp <= NotificationsLayoutPlanner.BODY_MAX_SP + 1e-3f);
                    assertTrue(label + " monotonic", sp >= last - 1e-3f);
                    if (first < 0f) first = sp;
                    last = sp;
                }
                assertTrue(span + " @" + scale + " grows", last > first);
            }
        }
    }

    @Test public void theDesignsOneRowCardIsSmallerThanTheBodyRole() {
        BuiltinWidgetSpan span = BuiltinWidgetSpan.TWO_BY_ONE;
        WidgetLayoutPlan p = plan(span, span.widthDp, span.heightDp, 1f, groups(false), null);
        assertTrue(p.part("body.0.0").sizeDp < WidgetTokens.Role.BODY.sp);
        assertTrue(p.part("title.0").sizeDp < WidgetTokens.Role.TITLE.sp);
    }

    // ----- Reply and Dismiss ------------------------------------------------------------------

    private static List<Group> replyable() {
        return NotificationsWidgetData.group(Arrays.asList(
            new NotificationsWidgetData.Item("1", "pkg.chat", "Chat", "Mum", "Are you coming?", 2L,
                null, 0, true, true),
            new NotificationsWidgetData.Item("2", "pkg.mail", "Mail", "Bill", "Due today", 1L,
                null, 0, false, false)));
    }

    @Test public void aRowEndsInReplyThenDismissWhereItTakesAReply() {
        BuiltinWidgetSpan span = BuiltinWidgetSpan.FOUR_BY_ONE;
        WidgetLayoutPlan p = plan(span, span.widthDp, span.heightDp, 1f, replyable(), null);
        Part reply = p.part("reply.0");
        Part dismiss = p.part("dismiss.0");
        assertNotNull(reply);
        assertNotNull(dismiss);
        assertEquals("dismiss at the far right", p.inset.right(), dismiss.box.right(), 1e-3f);
        assertEquals("reply just before it", dismiss.box.left, reply.box.right(), 1e-3f);
        assertEquals(40f, dismiss.box.width, 1e-3f);
        assertEquals(40f, dismiss.box.height, 1e-3f);
        assertTrue("the wide row keeps its icon", p.has("icon.0"));
        for (Part part : p.parts) {
            if (part.type == WidgetLayoutPlan.Type.TOUCH) continue;
            assertFalse("text clear of the buttons: " + part, part.box.overlaps(reply.box)
                || part.box.overlaps(dismiss.box));
        }
        assertTrue("the row's tap stops before them",
            p.part("row.0").box.right() <= reply.box.left + 1e-3f);
    }

    @Test public void tallRowsGetButtonsAndReplyOnlyWhereItIsOffered() {
        BuiltinWidgetSpan span = BuiltinWidgetSpan.FOUR_BY_TWO;
        WidgetLayoutPlan p = plan(span, span.widthDp, span.heightDp, 1f, replyable(), null);
        assertTrue(p.has("reply.0"));
        assertTrue(p.has("dismiss.0"));
        assertTrue(p.has("dismiss.1"));
        assertFalse(p.has("reply.1"));
        assertTrue(p.part("dismiss.1").box.top >= p.part("dismiss.0").box.bottom() - 1e-3f);
    }

    @Test public void aNarrowCardHidesTheButtonsAndKeepsTheText() {
        BuiltinWidgetSpan span = BuiltinWidgetSpan.TWO_BY_ONE;
        WidgetLayoutPlan p = plan(span, span.minWidthDp, span.minHeightDp, 1f, replyable(), null);
        assertFalse(p.has("reply.0"));
        assertFalse(p.has("dismiss.0"));
        assertTrue(p.has("body.0.0"));
        WidgetLayoutPlan glance = plan(BuiltinWidgetSpan.ONE_BY_ONE, 88f, 92f, 1f, replyable(), null);
        assertFalse(glance.has("dismiss.0"));
        assertFalse(glance.has("reply.0"));
    }
}
