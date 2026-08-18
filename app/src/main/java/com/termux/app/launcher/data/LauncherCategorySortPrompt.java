package com.termux.app.launcher.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.drawer.AppDrawerCategory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
 * ("suggestions", "recently added") that no model may assign an app to. The descriptions live
 * here and are deliberately not string resources: they are prompt text, not UI text, and a
 * localized prompt would change model behaviour.
 */
public final class LauncherCategorySortPrompt {

    private static final Map<String, String> DESCRIPTION_BY_SLUG = buildDescriptions();

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
        descriptions.put("health", "health, fitness and medical");
        descriptions.put("photo_video", "camera, gallery and photo or video editing");
        descriptions.put("travel", "maps, navigation, transport and travel booking");
        descriptions.put("information_reading", "news, search, reading, books and reference");
        descriptions.put("other", "anything that fits none of the above");
        return Collections.unmodifiableMap(descriptions);
    }

    /** @return the assignable category slugs, in enum order, synthetic categories excluded. */
    @NonNull
    public static List<String> categorySlugs() {
        ArrayList<String> slugs = new ArrayList<>();
        for (AppDrawerCategory category : AppDrawerCategory.values()) {
            if (category.synthetic) continue;
            slugs.add(category.slug);
        }
        return Collections.unmodifiableList(slugs);
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
     * <p>Two filters run on top of it. Sections naming an unknown category are dropped, and any
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
            if (category == null || category.synthetic) continue;
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
