package com.termux.app.launcher.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiFeaturePlan;
import com.termux.app.launcher.drawer.AppDrawerCategory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure prompt construction and reply parsing for app categorization: no Android, no I/O, no
 * threads, so the half of the feature that actually decides things stays unit-testable.
 *
 * <p>The wording here was benchmarked at 78.8% accuracy on-device (Gemma 4 E4B, 113 real apps).
 * Small edits to phrasing measurably move that number, so treat the literal strings as the
 * artefact and re-benchmark before rewording.
 *
 * <p>Category ids are derived from {@link AppDrawerCategory} rather than copied, so the taxonomy
 * has exactly one definition; synthetic categories are skipped because they are computed views
 * ("suggestions", "recently added") that no model may assign an app to, and so are the two Linux
 * groups ({@link #NOT_OFFERED}). The descriptions live
 * here and are deliberately not string resources: they are prompt text, not UI text, and a
 * localized prompt would change model behaviour.
 */
public final class LauncherCategorySortPrompt {

    private static final Map<String, String> DESCRIPTION_BY_SLUG = buildDescriptions();
    /**
     * Real categories no model is offered. They hold only what lives inside {@code x11:linux} (a
     * Linux app, a whole desktop session), which the drawer files by identity and which never
     * reaches a sort; no Android package belongs in either, so offering them only gave a model two
     * ways to be wrong. A drag can still put an app there.
     */
    private static final Set<AppDrawerCategory> NOT_OFFERED =
        Collections.unmodifiableSet(EnumSet.of(AppDrawerCategory.DESKTOPS, AppDrawerCategory.LINUX_APPS));
    /** The reply cap of one app's answer: a category id, with room for a model that prefixes it with filler. */
    public static final int MAX_TOKENS = 24;

    private LauncherCategorySortPrompt() {
    }

    private static Map<String, String> buildDescriptions() {
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("social", "messaging, chat, calls, contacts and social networks");
        descriptions.put("productivity",
            "work, documents, notes, email, calendar, cloud storage, AI assistants");
        descriptions.put("utilities",
            "system tools, browsers, files, security, developer tools, personalisation");
        descriptions.put("games", "games and game platforms");
        descriptions.put("entertainment", "music, video and streaming");
        descriptions.put("shopping_food", "shopping, delivery, food and recipes");
        descriptions.put("finance", "banking, payments and investing");
        // "sport, workouts" added 2026-10-05: on pong E2B went from 15/18 to 17/18 on an 18-app set (it fixed
        // Strava and Calm) with no regressions.
        descriptions.put("health", "health, fitness, sport, workouts and medical");
        descriptions.put("photo_video", "camera, gallery and photo or video editing");
        descriptions.put("travel", "maps, navigation, transport and travel booking");
        descriptions.put("information_reading", "news, search, reading, books and reference");
        descriptions.put("other", "anything that fits none of the above");
        return Collections.unmodifiableMap(descriptions);
    }

    /**
     * @return the category slugs a model is offered and may answer with, in enum order: synthetic
     *     categories and {@link #NOT_OFFERED} excluded.
     */
    @NonNull
    public static List<String> categorySlugs() {
        ArrayList<String> slugs = new ArrayList<>();
        for (AppDrawerCategory category : AppDrawerCategory.values()) {
            if (offered(category)) slugs.add(category.slug);
        }
        return Collections.unmodifiableList(slugs);
    }

    private static boolean offered(@Nullable AppDrawerCategory category) {
        return category != null && !category.synthetic && !NOT_OFFERED.contains(category);
    }

    /** @return "- slug: description" lines for every assignable category, in enum order. */
    @NonNull
    private static String categoryLines() {
        StringBuilder builder = new StringBuilder();
        for (String slug : categorySlugs()) {
            String description = DESCRIPTION_BY_SLUG.get(slug);
            builder.append("- ").append(slug);
            // A slug with no description still ships: a bare id beats dropping a whole category.
            if (description != null) builder.append(": ").append(description);
            builder.append("\n");
        }
        return builder.toString();
    }

    /** Prompt for the on-device model, one app per inference. */
    @NonNull
    public static String singleAppPrompt(@NonNull String label, @NonNull String packageName) {
        return "Assign this Android app to exactly one category.\n"
            + "\n"
            + "Categories:\n"
            + categoryLines()
            + "\n"
            + "App name: " + label + "\n"
            + "Package: " + packageName + "\n"
            + "\n"
            + "Answer with the category id only, nothing else.";
    }

    /**
     * Extracts a category id from a free-form model reply.
     *
     * <p>Matching is whole-word and case-insensitive, and the earliest match in the reply wins,
     * because small models prefix the answer with filler ("The category is social."). A reply that
     * contains no known id — a bare number, a refusal, invented prose — is a miss and returns null
     * rather than being coerced into a category: benchmarking showed silent coercion produces
     * confidently wrong assignments the user then has to hunt down.
     *
     * @return the matched slug, or null when the reply names no known category.
     */
    @Nullable
    public static String parseCategory(@Nullable String reply) {
        if (reply == null || reply.trim().isEmpty()) return null;
        String best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (String slug : categorySlugs()) {
            // \b keeps "social" from matching inside "socialize"; '_' counts as a word character,
            // so multi-word ids like "photo_video" are still matched as one unit.
            Pattern pattern = Pattern.compile(
                "\\b" + Pattern.quote(slug) + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(reply);
            if (!matcher.find()) continue;
            if (matcher.start() < bestIndex) {
                bestIndex = matcher.start();
                best = slug;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ on-device batches

    /** Apps per on-device request: the category list, most of a prompt, goes once for all of them. */
    public static final int BATCH_SIZE = 8;
    /** Labels are cut to this many characters: a label only hints, and a long one only spends the window. */
    static final int LABEL_MAX_CHARS = 32;
    /** Window left free of prompt and reply: the chat template's turn markers and the estimate's error. */
    static final int WINDOW_MARGIN_TOKENS = 64;
    /** Reply room beyond the answer lines, for a model that opens with a line of its own. */
    static final int REPLY_HEADROOM_TOKENS = 16;

    /**
     * Prompt for the on-device model, several apps per inference: {@link #singleAppPrompt}'s category
     * block once, then one line per app, answered one {@code package: category} line per app.
     */
    @NonNull
    public static String batchPrompt(@NonNull List<AppEntry> apps) {
        StringBuilder builder = new StringBuilder();
        builder.append("Assign each Android app below to exactly one category.\n")
            .append("\n")
            .append("Categories:\n")
            .append(categoryLines())
            .append("\n")
            .append("Apps (package name, then the app name in brackets):\n");
        for (AppEntry app : apps) {
            builder.append("- ").append(app.packageName).append(" (").append(shortLabel(app.label)).append(")\n");
        }
        builder.append("\n")
            .append("Answer with one line per app, in the same order, in the form package.name: category_id\n")
            .append("Use only the category ids above. Nothing else.");
        return builder.toString();
    }

    /**
     * A deliberately pessimistic token count for the window arithmetic, since no tokenizer runs on
     * this side: printable ASCII at 2.5 characters a token (package names are the worst English-ish
     * text, near 3), anything else at 2 tokens a character (CJK, emoji).
     */
    static int estimateTokens(@NonNull String text) {
        int ascii = 0;
        int other = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (codePoint < 0x80) ascii++;
            else other++;
            i += Character.charCount(codePoint);
        }
        return (ascii * 2 + 4) / 5 + 2 * other;
    }

    /** The reply cap of a batch: every app's {@code package: longest_category_id} line, plus headroom. */
    public static int batchMaxTokens(@NonNull List<AppEntry> apps) {
        String longest = "";
        for (String slug : categorySlugs()) if (slug.length() > longest.length()) longest = slug;
        int tokens = REPLY_HEADROOM_TOKENS;
        for (AppEntry app : apps) tokens += estimateTokens(app.packageName + ": " + longest + "\n");
        return tokens;
    }

    /** True when the batch's prompt and its reply cap fit app sorting's window with the margin to spare. */
    public static boolean fitsWindow(@NonNull List<AppEntry> apps) {
        return estimateTokens(batchPrompt(apps)) + batchMaxTokens(apps)
            <= TaiFeaturePlan.SORTING_WINDOW - WINDOW_MARGIN_TOKENS;
    }

    /** The label as a prompt line shows it: one line, at most {@link #LABEL_MAX_CHARS} characters. */
    @NonNull
    static String shortLabel(@NonNull String label) {
        String line = label.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        if (line.codePointCount(0, line.length()) <= LABEL_MAX_CHARS) return line;
        return line.substring(0, line.offsetByCodePoints(0, LABEL_MAX_CHARS)).trim();
    }

    /**
     * Reads a batch reply back into package → category slug, as tolerant as a small model needs:
     * bullets, numbering, markdown, case and stray prose are ignored; a line is matched to an asked
     * app by its package (or, failing that, by its label as the prompt showed it), and its category
     * is the first known id after the package, brackets skipped. An app the reply misses, names
     * twice (the first answer stands) or files under an unknown id is simply absent, and a package
     * nobody asked about is never returned.
     */
    @NonNull
    public static Map<String, String> parseBatchReply(@Nullable String reply, @NonNull List<AppEntry> asked) {
        Map<String, String> slugByPackage = new LinkedHashMap<>();
        if (reply == null) return slugByPackage;
        Map<String, String> packageByLabel = new HashMap<>();
        Set<String> repeatedLabels = new HashSet<>();
        for (AppEntry app : asked) {
            String label = shortLabel(app.label).toLowerCase(Locale.US);
            if (packageByLabel.put(label, app.packageName) != null) repeatedLabels.add(label);
        }
        for (String label : repeatedLabels) packageByLabel.remove(label);

        for (String rawLine : reply.split("\n")) {
            String line = rawLine.replace("`", "").replace("*", "").trim()
                .replaceFirst("^(?:[-+•>]+|\\d+[.)])\\s*", "");
            if (line.isEmpty()) continue;
            String lower = line.toLowerCase(Locale.US);
            String packageName = null;
            int answerFrom = -1;
            for (AppEntry app : asked) {
                int end = tokenEnd(lower, app.packageName.toLowerCase(Locale.US));
                if (end < 0) continue;
                // The longest package that matches wins: "com.foo" never claims a "com.foo.bar" line.
                if (packageName == null || app.packageName.length() > packageName.length()) {
                    packageName = app.packageName;
                    answerFrom = end;
                }
            }
            String answer;
            if (packageName != null) {
                answer = lower.substring(answerFrom);
            } else {
                int colon = line.lastIndexOf(':');
                if (colon < 0) continue;
                String key = line.substring(0, colon).replaceAll("\\([^)]*\\)", " ").replace("\"", "").trim();
                packageName = packageByLabel.get(key.toLowerCase(Locale.US));
                if (packageName == null) continue;
                answer = line.substring(colon + 1);
            }
            if (slugByPackage.containsKey(packageName)) continue;
            // A label echoed in brackets ("(Games Hub)") must not answer for the app.
            String slug = parseCategory(answer.replaceAll("\\([^)]*\\)", " "));
            if (slug != null) slugByPackage.put(packageName, slug);
        }
        return slugByPackage;
    }

    /** The end of {@code needle}'s first whole-token occurrence in {@code text}, or -1. */
    private static int tokenEnd(@NonNull String text, @NonNull String needle) {
        if (needle.isEmpty()) return -1;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            int end = at + needle.length();
            if ((at == 0 || !packageChar(text.charAt(at - 1)))
                && (end == text.length() || !packageChar(text.charAt(end)) || endsSentence(text, end))) {
                return end;
            }
        }
        return -1;
    }

    private static boolean packageChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }

    /** A full stop after a package that no package character follows ends the sentence, not the name. */
    private static boolean endsSentence(@NonNull String text, int at) {
        return text.charAt(at) == '.' && (at + 1 == text.length() || !packageChar(text.charAt(at + 1)));
    }

    /**
     * Prompt the user copies into an external AI chat when they would rather not run the on-device
     * model. The reply format is the same one {@link LauncherCategoryFile} already parses, so the
     * user can also paste the answer straight into {@code app-categories.conf}.
     */
    @NonNull
    public static String pasteablePrompt(@NonNull List<AppEntry> apps) {
        StringBuilder builder = new StringBuilder();
        builder.append("Assign every Android app below to exactly one category.\n")
            .append("\n")
            .append("Categories:\n")
            .append(categoryLines())
            .append("\n")
            .append("Apps (package name, then a tab, then the app name):\n");
        for (AppEntry app : apps) {
            if (app == null) continue;
            builder.append(app.packageName).append("\t").append(app.label).append("\n");
        }
        builder.append("\n")
            .append("Reply ONLY with this block format, nothing before or after it:\n")
            .append("[category_id]\n")
            .append("package.name.one\n")
            .append("package.name.two\n")
            .append("\n")
            .append("Rules:\n")
            .append("- Use only the category ids listed above as section names.\n")
            .append("- Every app above must appear exactly once, under exactly one category.\n")
            .append("- Copy package names character for character; never invent a package name.\n")
            .append("- Do not add comments, explanations, numbering or markdown fences.");
        return builder.toString();
    }

    /**
     * Parses a pasted reply back into package → category slug.
     *
     * <p>Reuses {@link LauncherCategoryFile#parse} — the reply shape is exactly that file's
     * grammar, and that parser already never throws on junk lines, so stray prose around the
     * blocks degrades into ignored lines instead of a failed import.
     *
     * <p>Two filters run on top of it. Sections naming a category the prompt does not offer are dropped, and any
     * package the caller did not list is dropped: fabricated package ids were the dominant failure
     * mode in benchmarking, and an invented id would otherwise land in the config file forever.
     */
    @NonNull
    public static Map<String, String> parsePastedReply(@NonNull String reply,
                                                       @NonNull Set<String> knownPackages) {
        Map<String, String> slugByPackage = new LinkedHashMap<>();

        LauncherCategoryFile parsed;
        try {
            parsed = LauncherCategoryFile.parse(new StringReader(reply));
        } catch (IOException ignored) {
            // Unreachable for a StringReader, but the signature declares it.
            return slugByPackage;
        }

        Map<String, String> knownByLowercase = new HashMap<>();
        for (String packageName : knownPackages) {
            if (packageName == null) continue;
            knownByLowercase.put(packageName.trim().toLowerCase(Locale.US), packageName);
        }

        for (Map.Entry<String, List<String>> section : parsed.sections().entrySet()) {
            String slug = section.getKey().trim().toLowerCase(Locale.US);
            AppDrawerCategory category = AppDrawerCategory.fromSlug(slug);
            if (!offered(category)) continue;
            for (String packageName : section.getValue()) {
                String known = knownByLowercase.get(packageName.trim().toLowerCase(Locale.US));
                if (known == null) continue;
                slugByPackage.put(known, category.slug);
            }
        }
        return slugByPackage;
    }

    /**
     * Minimal app identity for prompt building, kept separate from {@code LauncherAppEntry} so
     * this class carries no Android types and its tests need no Robolectric.
     */
    public static final class AppEntry {
        @NonNull public final String packageName;
        @NonNull public final String label;

        public AppEntry(@NonNull String packageName, @NonNull String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }
}
