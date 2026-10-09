package com.termux.app.launcher.data;

import androidx.annotation.NonNull;

import com.termux.app.launcher.data.LauncherCategoryRemoteSort.Answer;
import com.termux.app.launcher.data.LauncherCategorySortPrompt.AppEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How an app sort on the on-device model spends its inferences. The category list is most of a
 * prompt, so apps go up to {@link LauncherCategorySortPrompt#BATCH_SIZE} to a request, as many as
 * fit app sorting's window ({@link LauncherCategorySortPrompt#fitsWindow}). An app a batch reply
 * leaves out is asked once more on its own, in the single-app prompt.
 *
 * <p>Pure: the requests themselves are the caller's {@link Requests}.
 */
public final class LauncherCategoryLocalSort {

    private LauncherCategoryLocalSort() {}

    /** What the sort asks of its caller. */
    public interface Requests {
        boolean cancelled();

        /** One request for these apps in the batch prompt. */
        @NonNull Answer batch(@NonNull List<AppEntry> apps);

        /** One request for one app in the single-app prompt. */
        @NonNull Answer single(@NonNull AppEntry app);

        /**
         * A batch is done: {@code answered} holds the categories its apps got (other included), the
         * rest stay with the drawer's own classifier; {@code settled} is how many apps it held.
         */
        void settled(@NonNull Map<String, String> answered, int settled);
    }

    /** How the run ended. */
    public static final class Result {
        public final int requests;

        Result(int requests) {
            this.requests = requests;
        }
    }

    /**
     * Consecutive batches of at most {@link LauncherCategorySortPrompt#BATCH_SIZE} apps, each as
     * large as fits the window, order kept. An app too long to share a window still goes, alone.
     */
    @NonNull
    static List<List<AppEntry>> batches(@NonNull List<AppEntry> apps) {
        List<List<AppEntry>> batches = new ArrayList<>();
        List<AppEntry> current = new ArrayList<>();
        for (AppEntry app : apps) {
            if (!current.isEmpty()) {
                List<AppEntry> grown = new ArrayList<>(current);
                grown.add(app);
                if (current.size() >= LauncherCategorySortPrompt.BATCH_SIZE
                    || !LauncherCategorySortPrompt.fitsWindow(grown)) {
                    batches.add(current);
                    current = new ArrayList<>();
                }
            }
            current.add(app);
        }
        if (!current.isEmpty()) batches.add(current);
        return batches;
    }

    /** Sorts {@code pending}: each batch, then each app its reply missed, alone. */
    @NonNull
    public static Result run(@NonNull List<AppEntry> pending, @NonNull Requests requests) {
        int sent = 0;
        for (List<AppEntry> batch : batches(pending)) {
            if (requests.cancelled()) break;
            sent++;
            Answer answer = requests.batch(batch);
            // Only what was asked counts, whatever else the reply named.
            Map<String, String> answered = new LinkedHashMap<>();
            List<AppEntry> missing = new ArrayList<>();
            for (AppEntry app : batch) {
                String slug = answer.slugByPackage.get(app.packageName);
                if (slug != null) answered.put(app.packageName, slug);
                else missing.add(app);
            }
            for (AppEntry app : missing) {
                if (requests.cancelled()) break;
                sent++;
                String slug = requests.single(app).slugByPackage.get(app.packageName);
                if (slug != null) answered.put(app.packageName, slug);
            }
            requests.settled(answered, batch.size());
        }
        return new Result(sent);
    }
}
