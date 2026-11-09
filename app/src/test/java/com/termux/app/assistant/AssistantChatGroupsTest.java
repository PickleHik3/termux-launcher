package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class AssistantChatGroupsTest {

    private static final TimeZone ZONE = TimeZone.getTimeZone("UTC");

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance(ZONE);
        c.clear();
        c.set(year, month - 1, day, hour, minute, 0);
        return c.getTimeInMillis();
    }

    /** Thursday 5 November 2026, 12:00. */
    private static final long NOW = at(2026, 11, 5, 12, 0);

    private static AssistantChat chat(long id, long updated) {
        return new AssistantChat(id, "c" + id, updated, updated);
    }

    @Test
    public void groupsFollowTheCalendarDayNotTheHourCount() {
        assertEquals(AssistantChatGroups.Group.TODAY, AssistantChatGroups.groupOf(at(2026, 11, 5, 0, 0), NOW, ZONE));
        assertEquals(AssistantChatGroups.Group.YESTERDAY, AssistantChatGroups.groupOf(at(2026, 11, 4, 23, 59), NOW, ZONE));
        assertEquals(AssistantChatGroups.Group.YESTERDAY, AssistantChatGroups.groupOf(at(2026, 11, 4, 0, 0), NOW, ZONE));
        assertEquals(AssistantChatGroups.Group.WEEK, AssistantChatGroups.groupOf(at(2026, 11, 3, 23, 59), NOW, ZONE));
        assertEquals(AssistantChatGroups.Group.WEEK, AssistantChatGroups.groupOf(at(2026, 10, 30, 8, 0), NOW, ZONE));
        assertEquals(AssistantChatGroups.Group.EARLIER, AssistantChatGroups.groupOf(at(2026, 10, 29, 23, 59), NOW, ZONE));
        assertEquals(AssistantChatGroups.Group.EARLIER, AssistantChatGroups.groupOf(at(2025, 12, 1, 8, 0), NOW, ZONE));
    }

    @Test
    public void aShortNightStillCountsAsYesterday() {
        long justAfterMidnight = at(2026, 11, 5, 0, 5);
        assertEquals(AssistantChatGroups.Group.YESTERDAY,
            AssistantChatGroups.groupOf(at(2026, 11, 4, 23, 50), justAfterMidnight, ZONE));
    }

    @Test
    public void headersAppearOnlyForGroupsThatHaveChats() {
        List<AssistantChat> chats = new ArrayList<>();
        chats.add(chat(1, at(2026, 11, 5, 8, 15)));
        chats.add(chat(2, at(2026, 11, 5, 7, 0)));
        chats.add(chat(3, at(2026, 11, 2, 9, 0)));
        chats.add(chat(4, at(2026, 10, 1, 9, 0)));
        List<AssistantChatGroups.Item> items = AssistantChatGroups.build(chats, NOW, ZONE);
        assertEquals(7, items.size());
        assertEquals(AssistantChatGroups.Group.TODAY, items.get(0).header);
        assertNull(items.get(1).header);
        assertEquals(AssistantChatGroups.Group.WEEK, items.get(3).header);
        assertEquals(AssistantChatGroups.Group.EARLIER, items.get(5).header);
        assertEquals(4, items.get(6).chat.id);
    }

    @Test
    public void timeLabelsFollowTheirGroup() {
        assertEquals("18:42", AssistantChatGroups.timeLabel(at(2026, 11, 5, 18, 42), NOW, ZONE, Locale.ENGLISH, true));
        assertEquals("21:03", AssistantChatGroups.timeLabel(at(2026, 11, 4, 21, 3), NOW, ZONE, Locale.ENGLISH, true));
        assertEquals("Mon", AssistantChatGroups.timeLabel(at(2026, 11, 2, 9, 0), NOW, ZONE, Locale.ENGLISH, true));
        assertEquals("28 Oct", AssistantChatGroups.timeLabel(at(2026, 10, 28, 9, 0), NOW, ZONE, Locale.ENGLISH, true));
        assertEquals("3 Dec 2025", AssistantChatGroups.timeLabel(at(2025, 12, 3, 9, 0), NOW, ZONE, Locale.ENGLISH, true));
    }
}
