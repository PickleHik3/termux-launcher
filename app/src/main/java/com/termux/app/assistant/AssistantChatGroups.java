package com.termux.app.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The Chats list's day groups and time labels (sheet 05). Today is since midnight, Yesterday the
 * day before, This week the five days before that, Earlier everything older. Pure and given its
 * clock, so the boundaries are tested.
 */
public final class AssistantChatGroups {

    public enum Group {
        TODAY(R.string.assistant_group_today),
        YESTERDAY(R.string.assistant_group_yesterday),
        WEEK(R.string.assistant_group_week),
        EARLIER(R.string.assistant_group_earlier);

        @StringRes public final int label;

        Group(int label) {
            this.label = label;
        }
    }

    /** A header or a chat of the list. */
    public static final class Item {
        @Nullable public final Group header;
        @Nullable public final AssistantChat chat;

        Item(@Nullable Group header, @Nullable AssistantChat chat) {
            this.header = header;
            this.chat = chat;
        }
    }

    private AssistantChatGroups() {
    }

    /** Whole days from {@code thenMs}'s date to {@code nowMs}'s, in {@code zone}. */
    static int daysAgo(long thenMs, long nowMs, @NonNull TimeZone zone) {
        Calendar then = Calendar.getInstance(zone);
        then.setTimeInMillis(thenMs);
        Calendar now = Calendar.getInstance(zone);
        now.setTimeInMillis(nowMs);
        then.set(Calendar.HOUR_OF_DAY, 12);
        now.set(Calendar.HOUR_OF_DAY, 12);
        then.set(Calendar.MINUTE, 0);
        now.set(Calendar.MINUTE, 0);
        then.set(Calendar.SECOND, 0);
        now.set(Calendar.SECOND, 0);
        then.set(Calendar.MILLISECOND, 0);
        now.set(Calendar.MILLISECOND, 0);
        return (int) Math.round((now.getTimeInMillis() - then.getTimeInMillis()) / 86_400_000d);
    }

    @NonNull
    public static Group groupOf(long updatedMs, long nowMs, @NonNull TimeZone zone) {
        int days = daysAgo(updatedMs, nowMs, zone);
        if (days <= 0) return Group.TODAY;
        if (days == 1) return Group.YESTERDAY;
        if (days <= 6) return Group.WEEK;
        return Group.EARLIER;
    }

    /** {@code chats} newest first, with a header before each group that has any. */
    @NonNull
    public static List<Item> build(@NonNull List<AssistantChat> chats, long nowMs, @NonNull TimeZone zone) {
        List<Item> items = new ArrayList<>(chats.size() + 4);
        Group current = null;
        for (AssistantChat chat : chats) {
            Group group = groupOf(chat.updatedMs, nowMs, zone);
            if (group != current) {
                items.add(new Item(group, null));
                current = group;
            }
            items.add(new Item(null, chat));
        }
        return items;
    }

    /** hh:mm today and yesterday, the weekday this week, d MMM earlier (with the year when not this one). */
    @NonNull
    public static String timeLabel(long updatedMs, long nowMs, @NonNull TimeZone zone, @NonNull Locale locale,
                                   boolean is24Hour) {
        Group group = groupOf(updatedMs, nowMs, zone);
        String pattern;
        if (group == Group.TODAY || group == Group.YESTERDAY) pattern = is24Hour ? "HH:mm" : "h:mm a";
        else if (group == Group.WEEK) pattern = "EEE";
        else {
            Calendar then = Calendar.getInstance(zone);
            then.setTimeInMillis(updatedMs);
            Calendar now = Calendar.getInstance(zone);
            now.setTimeInMillis(nowMs);
            pattern = then.get(Calendar.YEAR) == now.get(Calendar.YEAR) ? "d MMM" : "d MMM yyyy";
        }
        SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
        format.setTimeZone(zone);
        return format.format(new Date(updatedMs));
    }
}
