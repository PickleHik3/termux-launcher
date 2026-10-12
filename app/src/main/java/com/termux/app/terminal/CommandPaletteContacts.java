package com.termux.app.terminal;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
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
        /**
         * The photo set for the contact in Contacts, as a thumbnail uri, or null. Only the uri:
         * the cache stays cheap, and the few photos the keycap strip shows are decoded on their
         * own ({@link CommandPaletteContactPhotos}).
         */
        @Nullable private String mPhotoUri;

        Contact(@NonNull String name, long contactId, @NonNull String lookupKey) {
            this.name = name;
            this.contactId = contactId;
            this.lookupKey = lookupKey;
        }

        /** Keeps the first photo uri any of the contact's numbers carries. */
        void notePhoto(@Nullable String uri) {
            if (mPhotoUri == null && uri != null && !uri.isEmpty()) mPhotoUri = uri;
        }

        @Nullable
        public String photoUri() {
            return mPhotoUri;
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
        /**
         * How well the query matched, on the palette's own score scale, so the row can be ranked
         * against action and app rows.
         */
        public final int score;

        Match(@NonNull Contact contact, @NonNull String number) {
            this(contact, number, SCORE_NONE);
        }

        Match(@NonNull Contact contact, @NonNull String number, int score) {
            this.contact = contact;
            this.number = number;
            this.score = score;
        }
    }

    /** Delivered on the main thread, from the worker that ran the query. */
    public interface Callback {
        void onContactsLoaded(@NonNull List<Contact> contacts);
    }

    /** How many contact rows the palette shows at once. */
    private static final int MATCH_LIMIT = 8;

    /**
     * Tiers, on the palette's own scale ({@link CommandPaletteFilter}): what the name says first,
     * exactly as an action's title.
     */
    private static final int SCORE_EXACT_NAME = CommandPaletteFilter.SCORE_EXACT_TITLE;
    private static final int SCORE_NAME_PREFIX = CommandPaletteFilter.SCORE_TITLE_PREFIX;
    private static final int SCORE_NAME_WORD_PREFIX = CommandPaletteFilter.SCORE_WORD_PREFIX;
    private static final int SCORE_NAME_SUBSTRING = CommandPaletteFilter.SCORE_TITLE_SUBSTRING;
    /**
     * A number match ranks below every name match, the name being what the user reads; level with
     * an action's id match, the other way in by something the row does not say.
     */
    private static final int SCORE_NUMBER = CommandPaletteFilter.SCORE_ID_SUBSTRING;
    private static final int SCORE_NONE = CommandPaletteFilter.SCORE_NONE;

    /** Fewest digits a query needs before it searches numbers: two digits land in nearly every number. */
    private static final int MIN_QUERY_DIGITS = 3;
    /**
     * Fewest digits a stored number needs before a longer query may contain it,
     * so a short number such as an emergency line is not found by every query.
     */
    private static final int MIN_CONTAINED_NUMBER_DIGITS = 6;

    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "palette-contacts");
        thread.setDaemon(true);
        return thread;
    });

    /** Main-thread delivery for {@link #load}; null until first used, so the pure halves of this class stay loadable off Android. */
    @Nullable private static Handler main;

    private CommandPaletteContacts() {
    }

    /** Runs {@code work} on the contacts worker, behind any load already queued. */
    static void runInBackground(@NonNull Runnable work) {
        LOADER.execute(work);
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
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI},
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
            int photoColumn = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI);
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
                if (photoColumn >= 0) contact.notePhoto(cursor.getString(photoColumn));
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * The card a row or a binding opens: the lookup key names the contact, so a rename or a
     * namesake cannot move it, and the id, when known, only speeds the lookup. {@code null} without
     * a key.
     */
    @Nullable
    public static Uri cardUri(long contactId, @NonNull String lookupKey) {
        if (lookupKey.isEmpty()) return null;
        return contactId >= 0
            ? ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
            : Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
    }

    /**
     * The contacts a query names, best first: an exact name, then a name
     * prefix, a word prefix, a substring, then a phone number whose digits
     * contain the query's digits. Only a query that reads as a phone number
     * searches numbers; one with letters in it searches names alone. A contact that matches both ways is
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
        String digits = phoneDigits(trimmed);

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
                matched != null ? matched : contact.firstNumber(), scored.get(i)[1]));
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
     * The first number the query's digits land in, or null. A long
     * enough stored number also matches when the query contains it,
     * so a number stored without its country code is still found by a
     * query that spells it with one.
     */
    @Nullable
    private static String numberMatching(@NonNull Contact contact,
                                         @NonNull String digits) {
        if (digits.isEmpty()) return null;
        for (String number : contact.numbers()) {
            String numberDigits = digitsOnly(number);
            if (numberDigits.isEmpty()) continue;
            if (numberDigits.contains(digits)) return number;
            if (numberDigits.length() >= MIN_CONTAINED_NUMBER_DIGITS
                && digits.contains(numberDigits)) return number;
        }
        return null;
    }

    /**
     * The digits of a query that reads as a phone number — digits with
     * optional {@code + ( ) -} and spaces — or empty for any other query,
     * and for one too short to search numbers with.
     */
    @NonNull
    private static String phoneDigits(@NonNull String query) {
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            boolean phone = (c >= '0' && c <= '9')
                || c == '+' || c == '(' || c == ')' || c == '-' || c == ' ';
            if (!phone) return "";
        }
        String digits = digitsOnly(query);
        return digits.length() >= MIN_QUERY_DIGITS ? digits : "";
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
