package com.termux.app.terminal;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Contact search for the command palette: one warm cache, filtered per query.
 *
 * <p>Same shape as the palette's app source: the list is loaded once on a
 * worker and cached, and every keystroke filters the cache on the main
 * thread, so a search never blocks on a ContentProvider sweep. The cache
 * holds one entry per contact with every number that contact carries, so a
 * query can match any of them while the row still names the contact once.
 */
public final class CommandPaletteContacts {

    /** One contact: its identity, and every phone number it carries. */
    public static final class Contact {
        public final String name;
        public final long contactId;
        public final String lookupKey;

        private final List<String> mNumbers = new ArrayList<>();

        Contact(@NonNull String name, long contactId, @NonNull String lookupKey) {
            this.name = name;
            this.contactId = contactId;
            this.lookupKey = lookupKey;
        }

        void addNumber(@NonNull String number) {
            if (!number.isEmpty() && !mNumbers.contains(number)) mNumbers.add(number);
        }

        @NonNull
        public List<String> numbers() {
            return Collections.unmodifiableList(mNumbers);
        }

        /** The number the row shows and dials when the query matched by name. */
        @NonNull
        public String firstNumber() {
            return mNumbers.isEmpty() ? "" : mNumbers.get(0);
        }
    }

    /** A contact with the number a query matched, or its first number. */
    public static final class Match {
        public final Contact contact;
        public final String number;

        Match(@NonNull Contact contact, @NonNull String number) {
            this.contact = contact;
            this.number = number;
        }
    }

    /** Delivered on the main thread, from the worker that ran the query. */
    public interface Callback {
        void onContactsLoaded(@NonNull List<Contact> contacts);
    }

    /** How many contact rows the palette shows at once. */
    private static final int MATCH_LIMIT = 8;

    /** Tiers, matching the palette's own ranking: what the name says first. */
    private static final int SCORE_EXACT_NAME = 100;
    private static final int SCORE_NAME_PREFIX = 90;
    private static final int SCORE_NAME_WORD_PREFIX = 80;
    private static final int SCORE_NAME_SUBSTRING = 70;
    /** A number match ranks below every name match: the name is what the user reads. */
    private static final int SCORE_NUMBER = 60;
    private static final int SCORE_NONE = 0;

    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "palette-contacts");
        thread.setDaemon(true);
        return thread;
    });

    /** Main-thread delivery for {@link #load}; null until first used, so the pure halves of this class stay loadable off Android. */
    @Nullable private static Handler main;

    private CommandPaletteContacts() {
    }

    /** Loads the warm cache on a worker; {@code callback} runs on the main thread. */
    public static void load(@NonNull Context context, @NonNull Callback callback) {
        LOADER.execute(() -> {
            List<Contact> contacts = query(context);
            Handler handler = main;
            if (handler == null) {
                handler = new Handler(Looper.getMainLooper());
                main = handler;
            }
            handler.post(() -> callback.onContactsLoaded(contacts));
        });
    }

    /**
     * One row per contact, aggregated from the phone-number table: a contact
     * with several numbers arrives once, carrying all of them. Sorted by
     * display name, which is the order a contact-less cache falls back to.
     */
    @VisibleForTesting
    @NonNull
    static List<Contact> query(@NonNull Context context) {
        Map<Long, Contact> byId = new LinkedHashMap<>();
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    + " COLLATE LOCALIZED ASC");
            if (cursor == null) return Collections.emptyList();
            int idColumn = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID);
            int lookupColumn = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY);
            int nameColumn = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int numberColumn = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.NUMBER);
            while (cursor.moveToNext()) {
                if (idColumn < 0 || lookupColumn < 0 || nameColumn < 0
                    || numberColumn < 0) break;
                long id = cursor.getLong(idColumn);
                String lookupKey = cursor.getString(lookupColumn);
                String name = cursor.getString(nameColumn);
                if (lookupKey == null || name == null) continue;
                Contact contact = byId.get(id);
                if (contact == null) {
                    contact = new Contact(name.trim(), id, lookupKey);
                    byId.put(id, contact);
                }
                String number = cursor.getString(numberColumn);
                if (number != null) contact.addNumber(number.trim());
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * The contacts a query names, best first: an exact name, then a name
     * prefix, a word prefix, a substring, then a phone number whose digits
     * contain the query's digits. A contact that matches both ways is
     * ordered by its name — the name is what the row reads — while the
     * number the digits landed in is still the one it dials; a name-only
     * match dials the contact's first number.
     *
     * <p>Pure, so the ranking is testable without a content provider.
     */
    @NonNull
    public static List<Match> filter(@NonNull List<Contact> contacts,
                                     @Nullable String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) return Collections.emptyList();
        String needle = trimmed.toLowerCase(Locale.US);
        String digits = digitsOnly(needle);

        List<Match> matches = new ArrayList<>();
        List<int[]> scored = new ArrayList<>();
        for (int i = 0; i < contacts.size(); i++) {
            Contact contact = contacts.get(i);
            String matched = numberMatching(contact, digits);
            int score = scoreName(contact.name, needle);
            if (score == SCORE_NONE && matched != null) score = SCORE_NUMBER;
            if (score == SCORE_NONE) continue;
            scored.add(new int[]{i, score});
        }
        scored.sort((a, b) -> {
            if (a[1] != b[1]) return b[1] - a[1];
            int lengthDelta = contacts.get(a[0]).name.length()
                - contacts.get(b[0]).name.length();
            if (lengthDelta != 0) return lengthDelta;
            return a[0] - b[0];
        });
        int limit = Math.min(scored.size(), MATCH_LIMIT);
        for (int i = 0; i < limit; i++) {
            Contact contact = contacts.get(scored.get(i)[0]);
            String matched = numberMatching(contact, digits);
            matches.add(new Match(contact,
                matched != null ? matched : contact.firstNumber()));
        }
        return matches;
    }

    /** The name tiers, or {@code 0} when the query names nothing in it. */
    private static int scoreName(@NonNull String name, @NonNull String needle) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.equals(needle)) return SCORE_EXACT_NAME;
        if (lower.startsWith(needle)) return SCORE_NAME_PREFIX;
        for (String word : lower.split("[\\s/\\-]+")) {
            if (!word.isEmpty() && word.startsWith(needle)) return SCORE_NAME_WORD_PREFIX;
        }
        return lower.contains(needle) ? SCORE_NAME_SUBSTRING : SCORE_NONE;
    }

    /**
     * The first number the query's digits land in, or null. Either
     * direction matches, so a number stored without its country code
     * is still found by a query that spells it with one.
     */
    @Nullable
    private static String numberMatching(@NonNull Contact contact,
                                         @NonNull String digits) {
        if (digits.isEmpty()) return null;
        for (String number : contact.numbers()) {
            String numberDigits = digitsOnly(number);
            if (numberDigits.isEmpty()) continue;
            if (numberDigits.contains(digits) || digits.contains(numberDigits))
                return number;
        }
        return null;
    }

    /** The digits of {@code text}, so {@code +1 (555) 123-4567} reads {@code 15551234567}. */
    @NonNull
    private static String digitsOnly(@NonNull String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') out.append(c);
        }
        return out.toString();
    }
}
