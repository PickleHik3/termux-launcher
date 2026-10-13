package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One cleanup part ({@link VoiceCleanupPlan.Pass}) through the gate: its language, its fillers
 * stripped, and a model call for each group of sentences {@link VoiceCleanupGate} flags, with the
 * sentence before it (already cleaned) as read-only context. The sentence after it as well was no
 * better in the eval and cost output tokens, as the model wrote it out. Sentences without
 * a sign of trouble pass through as heard, fillers aside, and a part with none makes no call. When
 * the language is unclear or mixed the part is only stripped of fillers: a model with no language to
 * write in translated it into English in the 2026-10-13 eval. Pure apart from the
 * {@link VoiceTextPolisher} it is handed; runs on the cleanup thread.
 */
public final class VoiceGatedCleanup {

    private VoiceGatedCleanup() {
    }

    /** What came of one part. */
    public static final class Outcome {
        /** The part's text: the groups joined, each cleaned or as heard less its fillers. */
        @NonNull public final String text;
        /** The language the part was taken to be in, or {@code null} when that was unclear. */
        @Nullable public final String language;
        /** How many groups of sentences there were, and how many a model was asked to clean. */
        public final int groups;
        public final int calls;
        /** The first failed call's outcome ({@code fallback:<reason>}), or {@code null}. */
        @Nullable public final String failure;

        Outcome(@NonNull String text, @Nullable String language, int groups, int calls, @Nullable String failure) {
            this.text = text;
            this.language = language;
            this.groups = groups;
            this.calls = calls;
            this.failure = failure;
        }

        /**
         * The part's result for {@link VoiceCleanupPlan#onPassDone}: the text when it changed, else the
         * raw text as a fallback. An early pass whose call found the runtime stuck or full reports that
         * failure instead, so the plan stops early passes and cleans everything once at the end.
         */
        @NonNull
        public VoiceTextPolisher.Result result(@NonNull String raw, boolean early) {
            if (failure != null && early && VoiceCleanupPlan.endsEarlyPasses(failure)) return fallback(raw, failure);
            if (text.equals(raw.trim())) {
                return failure != null ? fallback(raw, failure) : VoiceTextPolisher.Result.fallback(raw, "unchanged");
            }
            return VoiceTextPolisher.Result.polished(text);
        }

        @NonNull
        private static VoiceTextPolisher.Result fallback(@NonNull String raw, @NonNull String outcome) {
            return VoiceTextPolisher.Result.fallback(raw,
                outcome.substring(VoiceTextPolisher.Result.OUTCOME_FALLBACK_PREFIX.length()));
        }
    }

    /** One model call: a flagged group, the sentence before it, and the language to write in. */
    public interface Calls {
        @NonNull
        VoiceTextPolisher.Result clean(@NonNull String text, @NonNull String earlier, @Nullable String language);
    }

    /** {@link #clean(String, String, String, String, Calls)} through {@code polisher}, {@link VoicePolishRules#timeoutMs} a call. */
    @NonNull
    public static Outcome clean(@NonNull VoiceTextPolisher polisher, @NonNull String raw, @NonNull String context,
                                @NonNull String level, @Nullable String languageHint,
                                @Nullable String languageFallback) {
        return clean(raw, context, level, languageHint, languageFallback, (text, earlier, language) ->
            polisher.polish(text, earlier, language, VoicePolishRules.timeoutMs(text)));
    }

    /** {@link #clean(String, String, String, String, String, Calls)} with no fallback language. */
    @NonNull
    public static Outcome clean(@NonNull String raw, @NonNull String context, @NonNull String level,
                                @Nullable String languageHint, @NonNull Calls calls) {
        return clean(raw, context, level, languageHint, null, calls);
    }

    /**
     * Cleans {@code raw}, a part that follows {@code context} (the dictation before it, already
     * cleaned; {@code ""} for none), at {@code level}. {@code languageHint} is the speech model's
     * language; without one it is read from the text ({@link VoiceTextLanguage#guess}), and only
     * when the text is unclear is {@code languageFallback} (the keyboard's or the phone's) taken.
     * After a call that finds the runtime stuck or full, the remaining groups are not sent.
     */
    @NonNull
    public static Outcome clean(@NonNull String raw, @NonNull String context, @NonNull String level,
                                @Nullable String languageHint, @Nullable String languageFallback,
                                @NonNull Calls calls) {
        String language = VoiceTextLanguage.normalize(languageHint);
        if (language == null) language = VoiceTextLanguage.guess((context + " " + raw).trim());
        if (language == null) language = VoiceTextLanguage.normalize(languageFallback);
        String stripped = VoiceFillers.strip(raw.trim(), language);
        if (language == null) return new Outcome(stripped, null, 0, 0, null);
        List<VoiceCleanupGate.Group> groups = VoiceCleanupGate.groups(stripped, level);
        List<String> done = new ArrayList<>();
        int sent = 0;
        String failure = null;
        for (int i = 0; i < groups.size(); i++) {
            VoiceCleanupGate.Group group = groups.get(i);
            boolean stuck = failure != null && VoiceCleanupPlan.endsEarlyPasses(failure);
            if (!group.send || stuck) {
                done.add(group.text);
                continue;
            }
            String earlier = "";
            if (!context.trim().isEmpty() || !done.isEmpty()) {
                List<String> before = new ArrayList<>();
                before.add(context);
                before.addAll(done);
                earlier = VoiceCleanupGate.lastSentence(join(before, level));
            }
            VoiceTextPolisher.Result result = calls.clean(group.text, earlier, language);
            sent++;
            if (result.isPolished()) {
                done.add(result.text);
            } else {
                done.add(group.text);
                if (failure == null) failure = result.outcome;
            }
        }
        return new Outcome(groups.isEmpty() ? stripped : join(done, level), language, groups.size(), sent, failure);
    }

    /**
     * {@code texts} as one: a space between them; Polished starts a new line next to one that has
     * line breaks (a laid-out list), so the list keeps its lines.
     */
    @NonNull
    static String join(@NonNull List<String> texts, @NonNull String level) {
        boolean polished = VoicePolishRules.LEVEL_POLISHED.equals(VoicePolishRules.normalizeLevel(level));
        StringBuilder out = new StringBuilder();
        for (String text : texts) {
            String part = text.trim();
            if (part.isEmpty()) continue;
            if (out.length() > 0) {
                boolean lines = polished && (out.indexOf("\n") >= 0 || part.indexOf('\n') >= 0);
                out.append(lines ? "\n" : " ");
            }
            out.append(part);
        }
        return out.toString();
    }
}
