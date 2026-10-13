package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which parts of a dictation are cleaned when, so the wait after the user stops is only the text
 * since the last pause that was cleaned. Pure and main-thread only; {@link VoiceSessionCleanup}
 * runs the passes it hands out, each through the gate ({@link VoiceGatedCleanup}), which sends only
 * the part's sentences that show a sign of trouble.
 *
 * <p><b>Early passes.</b> After a phrase lands in the panel, {@link #nextEarlyPass} hands out the
 * raw text since the last checkpoint when all of these hold: early passes are on (the cleanup model
 * answers quickly beside speech-to-text, {@link VoiceTextPolisher#cleansWhileListening}, and no
 * early pass has timed out or run out of memory), no pass is in flight, no phrase is still
 * transcribing, the user has not edited the text, the text so far is not a dictated command, and
 * the new text up to its last sentence end has {@link #EARLY_PASS_SENTENCES} sentence ends or
 * {@link #EARLY_PASS_MIN_WORDS} words, whichever comes first. A part ends only at a sentence end, so
 * the sentence still being spoken waits for the next part: a part that starts mid-sentence made E2B
 * finish the earlier sentence first and repeat words across the boundary. The one exception is a
 * recogniser that writes no full stops: {@link #UNPUNCTUATED_WORDS} new words with none, cut where
 * the last phrase landed (a pause, the most natural break there is; {@link Pass#unpunctuated}).
 * {@link VoicePolishRules#skipReason} must let the part through. Each part goes with the
 * cleaned text before it, its last {@link #CONTEXT_SENTENCES} sentences up to
 * {@link #CONTEXT_MAX_WORDS} words, as read-only context.
 *
 * <p><b>At the end.</b> {@link #finish} waits for a pass in flight, then hands out what is left:
 * everything in one context-free pass when nothing was cleaned early (the request is today's single
 * pass, byte for byte), the tail alone, or, for a tail under {@link VoicePolishRules#MIN_WORDS}, the
 * last part and the tail again together so no raw fragment dangles. {@link #result} joins the
 * parts in order ({@link #join}).
 *
 * <p><b>Carried text.</b> A new dictation that carries on from text in the panel either starts
 * after it, when that text is already final (a cleanup or the user's edit; it is never cleaned
 * again), or takes over the parts a stopped plan had cleaned of it ({@link #carryOn}).
 */
public final class VoiceCleanupPlan {

    /**
     * Sentence ends in the new text before an early pass starts: about four sentences is the unit
     * transcript cleanup holds its structure best on (DRES, arXiv 2509.20321), and batching a few
     * sentences per request curbs over-correction (arXiv 2609.10810); see
     * {@code project-docs/reference/voice-ai/voice-cleanup-quality-research-2026-10-13.md}, part 4.
     */
    public static final int EARLY_PASS_SENTENCES = 4;
    /**
     * New words before an early pass starts when the recogniser writes few full stops: about 12 s of
     * speech. Each pass pays about 0.9 s to its first word on pong, so fewer words spend that on a
     * fragment with little of its own sentence to read; more leave a longer tail to clean after the
     * user stops.
     */
    public static final int EARLY_PASS_MIN_WORDS = 30;
    /**
     * New words with no sentence end at all after which an early pass cuts where the last phrase
     * landed. Parakeet and Whisper both punctuate, so this is a recogniser that does not, never a
     * long sentence.
     */
    public static final int UNPUNCTUATED_WORDS = 100;
    /**
     * Cleaned sentences sent ahead of a part as context. One to three: more earlier text raised the
     * error rate of LLM correction in arXiv 2505.16212, and the part needs only the thread it continues.
     */
    public static final int CONTEXT_SENTENCES = 3;
    /** The backstop on the context, for sentences the recogniser ran together. */
    public static final int CONTEXT_MAX_WORDS = 80;
    /**
     * A part longer than this goes without context, so the window holds it and its answer. Gemma
     * tokens: Polished's system turn with the context sentence is 395, {@link #CONTEXT_MAX_WORDS}
     * words of context about 110 and the tags 21, a part this long about 330, and its output budget
     * ({@link VoicePolishRules#maxTokens}) 766: about 1620 of the 2048-token window.
     */
    public static final int MAX_WORDS_WITH_CONTEXT = 250;

    /** Fallbacks after which no more early passes are tried this session: the runtime is stuck or full. */
    private static final Set<String> ENDS_EARLY_PASSES = new HashSet<>(Arrays.asList(
        "tai_runtime_timeout", "remote_timeout", "insufficient_memory", "model_not_loaded", "model_not_found",
        "tai_runtime_unavailable", "runtime_client_unavailable"));
    /** A numbered or bulleted list item's marker ("1.", "2)", "-", "•"), with the space before it. */
    private static final Pattern LIST_MARKER = Pattern.compile("\\s*(?:\\d{1,3}[.)]|[-*•])");
    private static final Pattern WORD = Pattern.compile("\\S+");
    /** Where a sentence ends: a full stop, question or exclamation mark (and closing quotes) before a space or the end. */
    private static final Pattern SENTENCE_END = Pattern.compile("[.?!…][\"'”’)]*(?=\\s|$)");
    /** Where a sentence starts in cleaned text: after a sentence end, or at a line's start. */
    private static final Pattern SENTENCE_START = Pattern.compile("(?<=[.?!…][\"'”’)]{0,3})\\s+(?=\\S)|\\n\\s*(?=\\S)");

    /** One request: the dictation's raw text from {@code start} on, and the context it goes with. */
    public static final class Pass {
        final int start;
        /** The dictation's raw text up to where the pass stops reading: the checkpoint once it is done. */
        @NonNull final String through;
        @NonNull public final String raw;
        @NonNull public final String context;
        /** Cleaned while the user still speaks. */
        public final boolean early;
        /** An early part cut at a phrase rather than a sentence end: the recogniser writes no full stops. */
        public final boolean unpunctuated;
        /** A short tail joined onto the last part, whose cleaned text this replaces. */
        final boolean replacesLast;

        Pass(int start, @NonNull String through, @NonNull String raw, @NonNull String context, boolean early,
             boolean replacesLast, boolean unpunctuated) {
            this.unpunctuated = unpunctuated;
            this.start = start;
            this.through = through;
            this.raw = raw;
            this.context = context;
            this.early = early;
            this.replacesLast = replacesLast;
        }
    }

    /** What {@link #finish} wants next: wait for the pass in flight, run {@link #pass}, or deliver {@link #result}. */
    public static final class Step {
        static final Step WAIT = new Step(null, true);
        static final Step DONE = new Step(null, false);

        @Nullable public final Pass pass;
        public final boolean waits;

        private Step(@Nullable Pass pass, boolean waits) {
            this.pass = pass;
            this.waits = waits;
        }

        public boolean isDone() {
            return pass == null && !waits;
        }
    }

    private static final class Part {
        /** Where the part's raw text starts in the dictation's. */
        final int start;
        /** What the part says: the cleaned text, or the raw text when it was kept as heard. */
        @NonNull final String text;
        /** The pass's outcome ({@code polished}, {@code fallback:…}); {@code null} for carried, final text. */
        @Nullable final String outcome;

        Part(int start, @NonNull String text, @Nullable String outcome) {
            this.start = start;
            this.text = text;
            this.outcome = outcome;
        }
    }

    private final boolean polished;
    private final List<Part> parts = new ArrayList<>();
    /** The raw text the parts cover, {@code [0, checkpoint)}; new text is checked against it. */
    @NonNull private String covered = "";
    private boolean earlyAllowed;
    private boolean earlyEnded;
    @Nullable private Pass inFlight;

    /** @param level the cleanup level; Polished keeps a part's line breaks and starts each part on a new line. */
    public VoiceCleanupPlan(@Nullable String level) {
        this.polished = VoicePolishRules.LEVEL_POLISHED.equals(VoicePolishRules.normalizeLevel(level));
    }

    /**
     * The new dictation starts from {@code base}, the text already in the panel ({@code ""} for none).
     * Final text (a cleanup or the user's edit) is taken as it is and never sent again; otherwise the
     * parts {@code previous} had cleaned of it carry over, when it still starts with them.
     */
    public void carryOn(@NonNull String base, boolean baseFinal, @Nullable VoiceCleanupPlan previous) {
        reset();
        String text = base.trim();
        if (text.isEmpty()) return;
        if (baseFinal) {
            parts.add(new Part(0, text, null));
            covered = text;
        } else if (previous != null && !previous.covered.isEmpty() && text.startsWith(previous.covered)) {
            parts.addAll(previous.parts);
            covered = previous.covered;
        }
    }

    /** Whether early passes may run: the model answers quickly beside speech-to-text. */
    public void allowEarlyPasses(boolean allowed) {
        earlyAllowed = allowed;
    }

    /** Whether early passes may still start this session. */
    public boolean earlyPassesOn() {
        return earlyAllowed && !earlyEnded;
    }

    /**
     * The early pass due now that a phrase has landed in {@code raw}, marked in flight, or {@code null}.
     *
     * @param transcriptionPending a phrase is still transcribing, so another is about to land
     * @param byHand the user has edited the text, which no cleanup may replace
     */
    @Nullable
    public Pass nextEarlyPass(@NonNull String raw, boolean transcriptionPending, boolean byHand) {
        if (!earlyPassesOn() || inFlight != null || transcriptionPending || byHand) return null;
        if (!raw.startsWith(covered)) reset();
        String fresh = raw.substring(covered.length());
        int sentenceEnds = 0;
        int lastEnd = -1;
        Matcher end = SENTENCE_END.matcher(fresh);
        while (end.find()) {
            sentenceEnds++;
            lastEnd = end.end();
        }
        String through;
        boolean unpunctuated = false;
        if (lastEnd > 0 && (sentenceEnds >= EARLY_PASS_SENTENCES
                || VoicePolishRules.wordCount(fresh.substring(0, lastEnd)) >= EARLY_PASS_MIN_WORDS)) {
            // Up to the last sentence end: the sentence being spoken is cleaned whole in the next part.
            through = raw.substring(0, covered.length() + lastEnd);
        } else if (VoicePolishRules.wordCount(fresh.substring(Math.max(0, lastEnd))) >= UNPUNCTUATED_WORDS) {
            through = raw;
            unpunctuated = true;
        } else {
            return null;
        }
        if (VoiceCommandFormatter.isCommand(raw)) return null;
        if (VoicePolishRules.skipReason(through.substring(covered.length())) != null) return null;
        inFlight = passFor(covered.length(), through, true, false, unpunctuated);
        return inFlight;
    }

    /** Whether a pass is out. */
    public boolean isInFlight() {
        return inFlight != null;
    }

    /**
     * {@code pass} has come back with {@code result}. A cleaned part moves the checkpoint on; a
     * fallback keeps the part as heard. An early pass that timed out or ran out of memory turns early
     * passes off and drops every part, so the end runs the single pass over the whole text.
     */
    public void onPassDone(@NonNull Pass pass, @NonNull VoiceTextPolisher.Result result) {
        if (pass != inFlight) return;
        inFlight = null;
        if (pass.early && endsEarlyPasses(result.outcome)) {
            earlyEnded = true;
            reset();
            return;
        }
        String text = result.isPolished() ? result.text.trim() : pass.raw;
        if (text.isEmpty()) text = pass.raw;
        if (pass.replacesLast && !parts.isEmpty()) parts.remove(parts.size() - 1);
        parts.add(new Part(pass.start, text, result.outcome));
        covered = pass.through;
    }

    /**
     * The session has ended on {@code raw}: what comes next. A pass it hands out is in flight until
     * {@link #onPassDone}, after which {@code finish} is asked again.
     */
    @NonNull
    public Step finish(@NonNull String raw) {
        if (inFlight != null) return Step.WAIT;
        if (!raw.startsWith(covered)) reset();
        String tail = raw.substring(covered.length());
        if (tail.trim().isEmpty()) return Step.DONE;
        if (parts.isEmpty()) {
            inFlight = new Pass(0, raw, raw.trim(), "", false, false, false);
        } else {
            Part last = parts.get(parts.size() - 1);
            boolean merge = VoicePolishRules.wordCount(tail) < VoicePolishRules.MIN_WORDS && last.outcome != null;
            inFlight = merge
                ? passFor(last.start, raw, false, true, false)
                : passFor(covered.length(), raw, false, false, false);
        }
        return new Step(inFlight, false);
    }

    /**
     * Why {@code pass} is kept as heard without a model call, or {@code null} to send it:
     * {@link VoicePolishRules#skipReason}, except that a short part with context is still sent, since
     * the context gives a few words their sentence.
     */
    @Nullable
    public static String skipReason(@NonNull Pass pass) {
        String skip = VoicePolishRules.skipReason(pass.raw);
        if ("short".equals(skip) && !pass.context.isEmpty()) return null;
        return skip;
    }

    /**
     * The whole dictation, {@code raw}, as the parts cleaned it: polished when any part of this
     * session was, else a fallback with the last pass's reason (or {@code unchanged} when nothing was
     * sent).
     */
    @NonNull
    public VoiceTextPolisher.Result result(@NonNull String raw) {
        List<String> texts = new ArrayList<>();
        boolean anyPolished = false;
        String lastOutcome = null;
        for (Part part : parts) {
            texts.add(part.text);
            if (part.outcome == null) continue;
            lastOutcome = part.outcome;
            if (VoiceTextPolisher.Result.OUTCOME_POLISHED.equals(part.outcome)) anyPolished = true;
        }
        if (!anyPolished) {
            String reason = lastOutcome != null && lastOutcome.startsWith(VoiceTextPolisher.Result.OUTCOME_FALLBACK_PREFIX)
                ? lastOutcome.substring(VoiceTextPolisher.Result.OUTCOME_FALLBACK_PREFIX.length()) : "unchanged";
            return VoiceTextPolisher.Result.fallback(raw, reason);
        }
        return VoiceTextPolisher.Result.polished(join(texts, polished));
    }

    /** How many passes of this session have come back, for the log. */
    public int partCount() {
        int count = 0;
        for (Part part : parts) if (part.outcome != null) count++;
        return count;
    }

    /**
     * {@code texts} as one: Light joins with a space; Polished keeps each part's own line breaks
     * (its paragraphs and list items) and starts each part on a new line, so a part of about four
     * sentences is at least a paragraph of its own, about as often as the single pass breaks one.
     */
    @NonNull
    static String join(@NonNull List<String> texts, boolean polished) {
        StringBuilder out = new StringBuilder();
        for (String text : texts) {
            String part = text.trim();
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(polished ? "\n" : " ");
            out.append(part);
        }
        return out.toString();
    }

    /**
     * The last {@code maxWords} words of {@code text}, cut at a word and keeping the line breaks
     * between them.
     */
    @NonNull
    static String lastWords(@NonNull String text, int maxWords) {
        List<Integer> starts = new ArrayList<>();
        Matcher word = WORD.matcher(text);
        while (word.find()) starts.add(word.start());
        if (starts.size() <= maxWords) return text.trim();
        return text.substring(starts.get(starts.size() - maxWords)).trim();
    }

    /** The last {@code count} sentences of {@code text}, a list item or a line counting as one. */
    @NonNull
    static String lastSentences(@NonNull String text, int count) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        Matcher start = SENTENCE_START.matcher(text);
        while (start.find()) {
            // "1. " opens a list item; it does not end a sentence.
            String before = text.substring(text.lastIndexOf('\n', start.start() - 1) + 1, start.start());
            if (LIST_MARKER.matcher(before).matches()) continue;
            starts.add(start.end());
        }
        if (starts.size() <= count) return text.trim();
        return text.substring(starts.get(starts.size() - count)).trim();
    }

    @NonNull
    private Pass passFor(int start, @NonNull String raw, boolean early, boolean replacesLast, boolean unpunctuated) {
        String text = raw.substring(start).trim();
        int before = replacesLast ? parts.size() - 1 : parts.size();
        String context = "";
        if (before > 0 && VoicePolishRules.wordCount(text) <= MAX_WORDS_WITH_CONTEXT) {
            List<String> texts = new ArrayList<>();
            for (Part part : parts.subList(0, before)) texts.add(part.text);
            context = lastWords(lastSentences(join(texts, polished), CONTEXT_SENTENCES), CONTEXT_MAX_WORDS);
        }
        return new Pass(start, raw, text, context, early, replacesLast, unpunctuated);
    }

    private void reset() {
        parts.clear();
        covered = "";
    }

    /** Whether {@code outcome} says the runtime is stuck or full, so no more calls are worth making this session. */
    static boolean endsEarlyPasses(@NonNull String outcome) {
        if (!outcome.startsWith(VoiceTextPolisher.Result.OUTCOME_FALLBACK_PREFIX)) return false;
        return ENDS_EARLY_PASSES.contains(outcome.substring(VoiceTextPolisher.Result.OUTCOME_FALLBACK_PREFIX.length()));
    }
}
