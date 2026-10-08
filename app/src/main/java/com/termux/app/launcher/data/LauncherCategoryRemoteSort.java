package com.termux.app.launcher.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * How an app sort on the remote model spends its requests. Free plans cap requests per minute and
 * per day (OpenRouter's is 50 a day), so one request per app cannot sort a 150-app drawer; the
 * whole pending list goes in blocks of up to {@link #MAX_APPS_PER_REQUEST}, each answered in the
 * clipboard prompt's block format. Apps a reply leaves out get one follow-up block; apps still
 * missing then are asked one at a time, as the on-device sort asks every app. A request that fails
 * outright (rate limited, refused key, unreachable) stops the run: every later request would fail
 * the same way, and on a daily cap each would only spend the cap further.
 *
 * <p>Pure: the requests themselves are the caller's {@link Requests}.
 */
public final class LauncherCategoryRemoteSort {
    /** The most apps one request carries. */
    static final int MAX_APPS_PER_REQUEST = 100;
    /** A package name and its newline, generously; a section header shares the headroom. */
    static final int TOKENS_PER_APP = 40;
    static final int TOKEN_HEADROOM = 256;
    /** Many hosted models refuse a larger {@code max_tokens}; a full block fits under it. */
    static final int MAX_TOKENS_CAP = 4096;

    private LauncherCategoryRemoteSort() {}

    /** One request's outcome: the categories it named by package, or why it failed. */
    public static final class Answer {
        @NonNull public final Map<String, String> slugByPackage;
        @Nullable public final String error;

        private Answer(@NonNull Map<String, String> slugByPackage, @Nullable String error) {
            this.slugByPackage = slugByPackage;
            this.error = error;
        }

        /** A reply, possibly naming only some of the apps asked about, or none. */
        @NonNull
        public static Answer of(@NonNull Map<String, String> slugByPackage) {
            return new Answer(slugByPackage, null);
        }

        /** The request itself failed; {@code message} is the server's or the transport's words. */
        @NonNull
        public static Answer failed(@NonNull String message) {
            return new Answer(Collections.emptyMap(), message);
        }
    }

    /** What the sort asks of its caller. */
    public interface Requests {
        boolean cancelled();

        /** One request for these apps in the block format. */
        @NonNull Answer batch(@NonNull List<String> packages);

        /** One request for one app, the on-device sort's prompt. */
        @NonNull Answer single(@NonNull String packageName);

        /**
         * {@code settled} more apps are done: {@code assigned} holds those that got a category, the
         * rest were given up on and stay with the drawer's own classifier.
         */
        void settled(@NonNull Map<String, String> assigned, int settled);
    }

    /** How the run ended. */
    public static final class Result {
        public final int assigned;
        public final int requests;
        /** The failed request's message when one stopped the run, else {@code null}. */
        @Nullable public final String error;

        Result(int assigned, int requests, @Nullable String error) {
            this.assigned = assigned;
            this.requests = requests;
            this.error = error;
        }
    }

    /**
     * The fewest blocks of at most {@link #MAX_APPS_PER_REQUEST}, as even as they come (150 apps
     * are two blocks of 75), order kept.
     */
    @NonNull
    static List<List<String>> chunks(@NonNull List<String> packages) {
        List<List<String>> chunks = new ArrayList<>();
        int total = packages.size();
        if (total == 0) return chunks;
        int count = (total + MAX_APPS_PER_REQUEST - 1) / MAX_APPS_PER_REQUEST;
        int start = 0;
        for (int i = 0; i < count; i++) {
            // The first (total % count) blocks take one app more.
            int size = total / count + (i < total % count ? 1 : 0);
            chunks.add(new ArrayList<>(packages.subList(start, start + size)));
            start += size;
        }
        return chunks;
    }

    /** The reply cap for a block of {@code apps}. */
    static int maxTokens(int apps) {
        return Math.min(MAX_TOKENS_CAP, Math.max(0, apps) * TOKENS_PER_APP + TOKEN_HEADROOM);
    }

    /** The apps of {@code asked} that {@code answered} has no category for, order kept. */
    @NonNull
    static List<String> missing(@NonNull List<String> asked, @NonNull Map<String, String> answered) {
        List<String> missing = new ArrayList<>();
        for (String packageName : asked) if (!answered.containsKey(packageName)) missing.add(packageName);
        return missing;
    }

    /** Sorts {@code pending}: the blocks, one follow-up block round, then one app at a time. */
    @NonNull
    public static Result run(@NonNull List<String> pending, @NonNull Requests requests) {
        Run run = new Run(requests);
        List<String> missing = run.blocks(pending);
        if (run.stopped()) return run.result();
        List<String> stillMissing = run.blocks(missing);
        if (run.stopped()) return run.result();
        for (String packageName : stillMissing) {
            if (requests.cancelled()) break;
            Answer answer = run.ask(() -> requests.single(packageName));
            if (answer.error != null) break;
            String slug = answer.slugByPackage.get(packageName);
            Map<String, String> assigned = slug == null ? Collections.emptyMap()
                : Collections.singletonMap(packageName, slug);
            run.settle(assigned, 1);
        }
        return run.result();
    }

    private static final class Run {
        private final Requests requests;
        private int assigned;
        private int sent;
        @Nullable private String error;

        Run(@NonNull Requests requests) {
            this.requests = requests;
        }

        boolean stopped() {
            return error != null || requests.cancelled();
        }

        @NonNull
        Answer ask(@NonNull Supplier<Answer> request) {
            sent++;
            Answer answer = request.get();
            if (answer.error != null) error = answer.error;
            return answer;
        }

        /** One round of blocks over {@code packages}; returns the apps no reply named. */
        @NonNull
        List<String> blocks(@NonNull List<String> packages) {
            List<String> missing = new ArrayList<>();
            for (List<String> chunk : chunks(packages)) {
                if (stopped()) break;
                Answer answer = ask(() -> requests.batch(chunk));
                if (answer.error != null) break;
                // Only what was asked counts, whatever else the reply named.
                Map<String, String> assigned = new LinkedHashMap<>();
                for (String packageName : chunk) {
                    String slug = answer.slugByPackage.get(packageName);
                    if (slug != null) assigned.put(packageName, slug);
                }
                missing.addAll(missing(chunk, assigned));
                settle(assigned, assigned.size());
            }
            return missing;
        }

        void settle(@NonNull Map<String, String> slugs, int settled) {
            assigned += slugs.size();
            requests.settled(slugs, settled);
        }

        @NonNull
        Result result() {
            return new Result(assigned, sent, error);
        }
    }
}
