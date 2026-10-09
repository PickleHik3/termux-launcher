package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns written English into the words a person would say before the G2P sees it: dates, years,
 * times, ordinals, money and percentages, and the acronyms that are read as words. Google's
 * {@code KittenG2P.kt} only spells ALL-CAPS runs and reads plain numbers ("2026" becomes "two
 * thousand twenty six"); this pass runs first so a year reads "twenty twenty-six", "27 September
 * 2026" reads "the twenty-seventh of September, twenty twenty-six" and "NASA" is a word while
 * "BBC" stays three letters. Plain numbers are still left to the G2P's own reader
 * ({@link #numberToWords}), which is the Kotlin port, so text this pass does not recognise reads
 * exactly as Google's sample reads it.
 *
 * <p>Everything here is a pure function of the text (and, for acronyms, of whether the dictionary
 * knows a word), so it is unit-tested without a model.
 */
final class TaiTextNormalizer {
    /** Whether the pronunciation dictionary has a lower-case word. */
    interface KnownWords {
        boolean contains(@NonNull String lowerWord);
    }

    static final String[] ONES = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
    static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
    static final String[] SCALES = {"", "thousand", "million", "billion", "trillion"};

    private static final String[] MONTH_NAMES = {"January", "February", "March", "April", "May", "June", "July",
        "August", "September", "October", "November", "December"};
    /** Full names and the usual abbreviations; capitalised only, so "may" the verb is left alone. */
    private static final String MONTH = "(January|February|March|April|May|June|July|August|September|October|"
        + "November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sept|Sep|Oct|Nov|Dec)";
    private static final String YEAR = "(1[1-9]\\d\\d|20\\d\\d)";

    private static final Pattern CURRENCY = Pattern.compile(
        "(?<![\\w$£€])([$£€])\\s?(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d{1,2}))?(?![\\d.]*\\d)");
    private static final Pattern PERCENT = Pattern.compile("(\\d)\\s?%");
    private static final Pattern AMPERSAND = Pattern.compile("\\s*&\\s*");
    private static final Pattern AT_SIGN = Pattern.compile("(?<=\\w)@(?=\\w)");
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");
    private static final Pattern DAY_MONTH = Pattern.compile(
        "\\b(\\d{1,2})(?:st|nd|rd|th)?(?:\\s+of)?\\s+" + MONTH + "\\b\\.?(?:,?\\s+" + YEAR + "\\b)?");
    private static final Pattern MONTH_DAY = Pattern.compile(
        "\\b" + MONTH + "\\b\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b(?![\\d:])(?:,?\\s+" + YEAR + "\\b)?");
    private static final Pattern MONTH_YEAR = Pattern.compile("\\b" + MONTH + "\\b\\.?,?\\s+" + YEAR + "\\b");
    private static final Pattern CLOCK = Pattern.compile(
        "\\b([01]?\\d|2[0-3]):([0-5]\\d)(?::[0-5]\\d)?(?:\\s?([AaPp])\\.?\\s?[Mm]\\b\\.?)?(?![\\w:])");
    private static final Pattern HOUR_MERIDIEM = Pattern.compile("\\b(1[0-2]|0?[1-9])\\s?([AaPp])\\.?\\s?[Mm]\\b\\.?");
    private static final Pattern DECADE = Pattern.compile("\\b(1[1-9]|20)(\\d)0s\\b");
    private static final Pattern YEAR_IN_CONTEXT = Pattern.compile(
        "\\b(in|In|since|Since|by|By|until|Until|till|before|Before|after|After|from|From|during|During|"
            + "circa|around|year|Year|early|late|mid|summer|winter|spring|autumn)\\s+" + YEAR + "\\b(?![.,]\\d)");
    private static final Pattern YEAR_ERA = Pattern.compile("\\b" + YEAR + "\\s?(AD|BC|BCE|CE)\\b");
    private static final Pattern ORDINAL = Pattern.compile("\\b(\\d{1,9})(st|nd|rd|th)\\b");
    private static final Pattern ACRONYM = Pattern.compile("\\b[A-Z]{2,}\\b");
    private static final Pattern SPACES = Pattern.compile("[ \\t]{2,}");

    /**
     * Acronyms said as words, respelled where the neural G2P would otherwise guess badly (it
     * reads "json" as "jiss-ahn"). Anything not here and shorter than four letters is spelled;
     * four letters and longer is read as a word when the dictionary knows it (RADAR, LASER).
     */
    private static final Map<String, String> SAID_AS_WORDS;
    private static final Map<String, String> ORDINAL_IRREGULAR;

    static {
        HashMap<String, String> words = new HashMap<>();
        words.put("OK", "okay");
        words.put("NASA", "nasa");
        words.put("NATO", "nato");
        words.put("UNESCO", "unesco");
        words.put("UNICEF", "unicef");
        words.put("FIFA", "fifa");
        words.put("OPEC", "opec");
        words.put("NASDAQ", "nasdaq");
        words.put("IKEA", "ikea");
        words.put("JSON", "jason");
        words.put("YAML", "yamel");
        words.put("TOML", "tommel");
        words.put("ASCII", "askee");
        words.put("JPEG", "jay peg");
        words.put("GIF", "gif");
        words.put("PIN", "pin");
        words.put("SIM", "sim");
        words.put("RAM", "ram");
        words.put("ROM", "rom");
        words.put("LAN", "lan");
        words.put("WAN", "wan");
        words.put("CAPTCHA", "captcha");
        words.put("LIDAR", "lidar");
        SAID_AS_WORDS = Collections.unmodifiableMap(words);

        HashMap<String, String> ordinals = new HashMap<>();
        ordinals.put("one", "first");
        ordinals.put("two", "second");
        ordinals.put("three", "third");
        ordinals.put("five", "fifth");
        ordinals.put("eight", "eighth");
        ordinals.put("nine", "ninth");
        ordinals.put("twelve", "twelfth");
        ORDINAL_IRREGULAR = Collections.unmodifiableMap(ordinals);
    }

    private TaiTextNormalizer() {}

    /** The written-to-spoken pass; {@code known} may be null (no dictionary: only the fixed table applies). */
    @NonNull
    static String normalize(@NonNull String text, @Nullable KnownWords known) {
        String out = text
            .replace('’', '\'').replace('‘', '\'')
            .replace('“', '"').replace('”', '"')
            .replace('–', '—');
        out = replace(CURRENCY, out, m -> money(m.group(1), m.group(2), m.group(3)));
        out = replace(PERCENT, out, m -> m.group(1) + " percent");
        out = AMPERSAND.matcher(out).replaceAll(" and ");
        out = AT_SIGN.matcher(out).replaceAll(" at ");
        out = replace(ISO_DATE, out, m -> {
            int year = parse(m.group(1)), month = parse(m.group(2)), day = parse(m.group(3));
            if (month < 1 || month > 12 || day < 1 || day > 31 || year < 1000) return m.group();
            return "the " + ordinalWords(day) + " of " + MONTH_NAMES[month - 1] + ", " + yearWords(year);
        });
        out = replace(DAY_MONTH, out, m -> {
            int day = parse(m.group(1));
            if (day < 1 || day > 31) return m.group();
            String said = "the " + ordinalWords(day) + " of " + monthName(m.group(2));
            return m.group(3) == null ? said : said + ", " + yearWords(parse(m.group(3)));
        });
        out = replace(MONTH_DAY, out, m -> {
            int day = parse(m.group(2));
            if (day < 1 || day > 31) return m.group();
            String said = monthName(m.group(1)) + " " + ordinalWords(day);
            return m.group(3) == null ? said : said + ", " + yearWords(parse(m.group(3)));
        });
        out = replace(MONTH_YEAR, out, m -> monthName(m.group(1)) + " " + yearWords(parse(m.group(2))));
        out = replace(CLOCK, out, m -> timeWords(parse(m.group(1)), parse(m.group(2)), m.group(3)));
        out = replace(HOUR_MERIDIEM, out, m -> timeWords(parse(m.group(1)), -1, m.group(2)));
        out = replace(DECADE, out, m -> decadeWords(parse(m.group(1)), parse(m.group(2))));
        out = replace(YEAR_IN_CONTEXT, out, m -> m.group(1) + " " + yearWords(parse(m.group(2))));
        out = replace(YEAR_ERA, out, m -> yearWords(parse(m.group(1))) + " " + m.group(2));
        out = replace(ORDINAL, out, m -> ordinalWords(Long.parseLong(m.group(1))));
        out = replace(ACRONYM, out, m -> acronym(m.group(), known));
        return SPACES.matcher(out).replaceAll(" ").trim();
    }

    @NonNull
    private static String acronym(@NonNull String caps, @Nullable KnownWords known) {
        String said = SAID_AS_WORDS.get(caps);
        if (said != null) return said;
        String lower = caps.toLowerCase(Locale.ROOT);
        // Four letters and up that the dictionary knows (RADAR, LASER, a shouted WARNING) are words;
        // shorter runs stay spelled, since "US" and "IT" are other words in lower case.
        if (caps.length() >= 4 && known != null && known.contains(lower)) return lower;
        return caps;
    }

    // ---- numbers (the Kotlin sample's reader) ----

    /** "1,234.5" → one thousand two hundred thirty four point five; empty for something unreadable. */
    @NonNull
    static List<String> numberToWords(@NonNull String raw) {
        String token = raw.replace(",", "");
        int dot = token.indexOf('.');
        if (dot >= 0) {
            String whole = token.substring(0, dot);
            List<String> words = new ArrayList<>();
            if (whole.isEmpty()) {
                words.add("zero");
            } else {
                try {
                    words.addAll(integerToWords(Long.parseLong(whole)));
                } catch (NumberFormatException e) {
                    for (char c : whole.toCharArray()) if (Character.isDigit(c)) words.add(ONES[c - '0']);
                }
            }
            words.add("point");
            for (int i = dot + 1; i < token.length(); i++) {
                char c = token.charAt(i);
                if (c >= '0' && c <= '9') words.add(ONES[c - '0']);
            }
            return words;
        }
        try {
            return integerToWords(Long.parseLong(token));
        } catch (NumberFormatException e) {
            // Longer than a long: read digit by digit, as the Kotlin reader does past trillions.
            List<String> words = new ArrayList<>();
            for (char c : token.toCharArray()) if (c >= '0' && c <= '9') words.add(ONES[c - '0']);
            return words;
        }
    }

    @NonNull
    static List<String> integerToWords(long value) {
        List<String> words = new ArrayList<>();
        if (value == 0) {
            words.add("zero");
            return words;
        }
        if (value < 0) {
            words.add("minus");
            words.addAll(integerToWords(value == Long.MIN_VALUE ? Long.MAX_VALUE : -value));
            return words;
        }
        List<Integer> groups = new ArrayList<>();
        long n = value;
        while (n > 0) {
            groups.add((int) (n % 1000));
            n /= 1000;
        }
        if (groups.size() > SCALES.length) {
            for (char c : Long.toString(value).toCharArray()) words.add(ONES[c - '0']);
            return words;
        }
        for (int i = groups.size() - 1; i >= 0; i--) {
            int group = groups.get(i);
            if (group == 0) continue;
            underThousand(group, words);
            if (!SCALES[i].isEmpty()) words.add(SCALES[i]);
        }
        return words;
    }

    private static void underThousand(int value, @NonNull List<String> words) {
        int n = value;
        if (n >= 100) {
            words.add(ONES[n / 100]);
            words.add("hundred");
            n %= 100;
        }
        if (n >= 20) {
            words.add(TENS[n / 10]);
            n %= 10;
        }
        if (n > 0) words.add(ONES[n]);
    }

    /** 1 → first, 21 → twenty first, 40 → fortieth, 112 → one hundred twelfth. */
    @NonNull
    static String ordinalWords(long n) {
        List<String> words = integerToWords(n);
        int last = words.size() - 1;
        String word = words.get(last);
        String irregular = ORDINAL_IRREGULAR.get(word);
        if (irregular != null) words.set(last, irregular);
        else if (word.endsWith("y")) words.set(last, word.substring(0, word.length() - 1) + "ieth");
        else words.set(last, word + "th");
        return join(words);
    }

    /** 2026 → twenty twenty six, 2005 → two thousand five, 1905 → nineteen oh five, 1900 → nineteen hundred. */
    @NonNull
    static String yearWords(int year) {
        if (year < 1000 || year > 9999 || (year >= 2000 && year <= 2009)) return join(integerToWords(year));
        int high = year / 100;
        int low = year % 100;
        List<String> words = integerToWords(high);
        if (low == 0) {
            words.add("hundred");
        } else if (low < 10) {
            words.add("oh");
            words.addAll(integerToWords(low));
        } else {
            words.addAll(integerToWords(low));
        }
        return join(words);
    }

    /** 1990s → nineteen nineties, 2010s → twenty tens, 2000s → two thousands, 1800s → eighteen hundreds. */
    @NonNull
    static String decadeWords(int century, int decade) {
        if (decade == 0) return century == 20 ? "two thousands" : join(integerToWords(century)) + " hundreds";
        String tens = decade == 1 ? "tens" : TENS[decade].substring(0, TENS[decade].length() - 1) + "ies";
        return join(integerToWords(century)) + " " + tens;
    }

    /**
     * 10:30 → ten thirty, 9:05 → nine oh five, 10:00 → ten o'clock, 7:15 pm → seven fifteen PM,
     * 5pm ({@code minute} {@code -1}) → five PM. The meridiem comes out in capitals so the G2P
     * spells it as two letters.
     */
    @NonNull
    static String timeWords(int hour, int minute, @Nullable String meridiem) {
        StringBuilder said = new StringBuilder(join(integerToWords(hour)));
        String suffix = meridiem == null ? null : ("a".equalsIgnoreCase(meridiem) ? "AM" : "PM");
        if (minute > 0 && minute < 10) said.append(" oh ").append(ONES[minute]);
        else if (minute >= 10) said.append(' ').append(join(integerToWords(minute)));
        else if (minute == 0 && suffix == null) said.append(" o'clock");
        if (suffix != null) said.append(' ').append(suffix);
        return said.toString();
    }

    @NonNull
    private static String money(@NonNull String symbol, @NonNull String whole, @Nullable String fraction) {
        long amount;
        try {
            amount = Long.parseLong(whole.replace(",", ""));
        } catch (NumberFormatException e) {
            return symbol + whole + (fraction == null ? "" : "." + fraction);
        }
        String unit, subunit, subunits;
        switch (symbol) {
            case "£": unit = amount == 1 ? "pound" : "pounds"; subunit = "penny"; subunits = "pence"; break;
            case "€": unit = amount == 1 ? "euro" : "euros"; subunit = "cent"; subunits = "cents"; break;
            default: unit = amount == 1 ? "dollar" : "dollars"; subunit = "cent"; subunits = "cents"; break;
        }
        StringBuilder said = new StringBuilder().append(whole).append(' ').append(unit);
        if (fraction != null) {
            int cents = parse(fraction.length() == 1 ? fraction + "0" : fraction);
            if (cents > 0) said.append(" and ").append(cents).append(' ').append(cents == 1 ? subunit : subunits);
        }
        return said.toString();
    }

    @NonNull
    private static String monthName(@NonNull String written) {
        String prefix = written.length() >= 3 ? written.substring(0, 3) : written;
        for (String month : MONTH_NAMES) {
            if (month.startsWith(prefix)) return month;
        }
        return written;
    }

    private static int parse(@NonNull String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @NonNull
    private static String join(@NonNull List<String> words) {
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (out.length() > 0) out.append(' ');
            out.append(word);
        }
        return out.toString();
    }

    private interface Replacer {
        @NonNull String apply(@NonNull Matcher match);
    }

    @NonNull
    private static String replace(@NonNull Pattern pattern, @NonNull String text, @NonNull Replacer replacer) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return text;
        StringBuffer out = new StringBuffer(text.length() + 16);
        do {
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacer.apply(matcher)));
        } while (matcher.find());
        matcher.appendTail(out);
        return out.toString();
    }
}
