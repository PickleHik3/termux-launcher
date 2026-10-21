package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/** When a dictation's parts are cleaned, what each is sent with, and how they join. */
public class VoiceCleanupPlanTest {

    private static final String POLISHED = VoicePolishRules.LEVEL_POLISHED;
    private static final String LIGHT = VoicePolishRules.LEVEL_LIGHT;

    /** Four sentences, 22 words: due on sentence ends before the word count. */
    private static final String FOUR_SENTENCES =
        "We are working on the branch. A user asked for bigger icons. The biggest size is small. Custom packs look off.";
    private static final String MORE = " The second thing is the drawer. Swiping down should open it everywhere.";

    private static VoiceCleanupPlan plan(String level) {
        VoiceCleanupPlan plan = new VoiceCleanupPlan(level);
        plan.allowEarlyPasses(true);
        return plan;
    }

    private static String words(int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) out.append(i == 0 ? "" : " ").append("word").append(i);
        return out.toString();
    }

    // ------------------------------------------------------------------ the trigger

    @Test
    public void fourSentenceEndsStartAnEarlyPassWithNoContext() {
        VoiceCleanupPlan plan = plan(POLISHED);
        assertNull(plan.nextEarlyPass("We are working on the branch. A user asked for bigger icons.", false, false));
        VoiceCleanupPlan.Pass pass = plan.nextEarlyPass(FOUR_SENTENCES, false, false);
        assertNotNull(pass);
        assertTrue(pass.early);
        assertEquals(FOUR_SENTENCES, pass.raw);
        assertEquals("", pass.context);
        assertTrue(plan.isInFlight());
        // One at a time.
        assertNull(plan.nextEarlyPass(FOUR_SENTENCES + MORE, false, false));
    }

    @Test
    public void thirtyWordsUpToASentenceEndStartOneBeforeFourSentences() {
        VoiceCleanupPlan plan = plan(POLISHED);
        String sentence = words(VoiceCleanupPlan.EARLY_PASS_MIN_WORDS - 1) + " end.";
        // Thirty words, but the sentence is still being spoken: it waits.
        assertNull(plan.nextEarlyPass(words(VoiceCleanupPlan.EARLY_PASS_MIN_WORDS + 5), false, false));
        // Twenty words up to the sentence end and fifteen after: not yet.
        assertNull(plan.nextEarlyPass(words(19) + " end. " + words(15), false, false));
        VoiceCleanupPlan.Pass pass = plan.nextEarlyPass(sentence + " and then we", false, false);
        assertNotNull(pass);
        assertEquals(sentence, pass.raw);
        assertFalse(pass.unpunctuated);
    }

    @Test
    public void aRecogniserThatWritesNoFullStopsIsCutAtThePhraseAfterAHundredWords() {
        VoiceCleanupPlan plan = plan(POLISHED);
        assertNull(plan.nextEarlyPass(words(VoiceCleanupPlan.UNPUNCTUATED_WORDS - 1), false, false));
        String raw = words(VoiceCleanupPlan.UNPUNCTUATED_WORDS);
        VoiceCleanupPlan.Pass pass = plan.nextEarlyPass(raw, false, false);
        assertNotNull(pass);
        assertEquals(raw, pass.raw);
        assertTrue(pass.unpunctuated);
        // So is a short sentence followed by a hundred words with no full stop.
        VoiceCleanupPlan other = plan(POLISHED);
        String mixed = "It broke. " + words(VoiceCleanupPlan.UNPUNCTUATED_WORDS);
        VoiceCleanupPlan.Pass cut = other.nextEarlyPass(mixed, false, false);
        assertNotNull(cut);
        assertEquals(mixed, cut.raw);
        assertTrue(cut.unpunctuated);
    }

    @Test
    public void thePartStopsAtTheLastSentenceEndSoTheSentenceBeingSpokenWaits() {
        VoiceCleanupPlan plan = plan(POLISHED);
        String raw = FOUR_SENTENCES + " and then the next one";
        VoiceCleanupPlan.Pass pass = plan.nextEarlyPass(raw, false, false);
        assertNotNull(pass);
        assertEquals(FOUR_SENTENCES, pass.raw);
        plan.onPassDone(pass, VoiceTextPolisher.Result.polished("Cleaned one."));
        // The checkpoint is the sentence end: the rest is the tail at the end.
        VoiceCleanupPlan.Step step = plan.finish(raw);
        assertNotNull(step.pass);
        assertEquals("and then the next one", step.pass.raw);
    }

    @Test
    public void nothingStartsWhileAPhraseIsTranscribingTheTextIsEditedOrEarlyPassesAreOff() {
        VoiceCleanupPlan plan = plan(POLISHED);
        assertNull(plan.nextEarlyPass(FOUR_SENTENCES, true, false));
        assertNull(plan.nextEarlyPass(FOUR_SENTENCES, false, true));
        plan.allowEarlyPasses(false);
        assertNull(plan.nextEarlyPass(FOUR_SENTENCES, false, false));
        plan.allowEarlyPasses(true);
        assertNotNull(plan.nextEarlyPass(FOUR_SENTENCES, false, false));
    }

    @Test
    public void aDictatedCommandNeverGetsAnEarlyPass() {
        VoiceCleanupPlan plan = plan(POLISHED);
        assertNull(plan.nextEarlyPass("git commit dash m " + words(VoiceCleanupPlan.UNPUNCTUATED_WORDS), false, false));
    }

    // ------------------------------------------------------------------ context

    @Test
    public void theNextPartGoesWithTheLastCleanedSentencesAsContext() {
        VoiceCleanupPlan plan = plan(POLISHED);
        VoiceCleanupPlan.Pass first = plan.nextEarlyPass(FOUR_SENTENCES, false, false);
        plan.onPassDone(first, VoiceTextPolisher.Result.polished("One. Two. Three. Four is the last."));
        String raw = FOUR_SENTENCES + MORE + " Third. Fourth sentence here.";
        VoiceCleanupPlan.Pass second = plan.nextEarlyPass(raw, false, false);
        assertNotNull(second);
        assertFalse(second.raw.startsWith(" "));
        assertEquals(raw.substring(FOUR_SENTENCES.length()).trim(), second.raw);
        assertEquals("Two. Three. Four is the last.", second.context);
    }

    @Test
    public void contextIsCappedInWordsAndListItemsAreNotSentenceEnds() {
        assertEquals("1. Update the log.\n2. Bump the version.",
            VoiceCleanupPlan.lastSentences("For the release:\n1. Update the log.\n2. Bump the version.", 2));
        assertEquals("c d e", VoiceCleanupPlan.lastWords("a b c d e", 3));
        assertEquals("a b", VoiceCleanupPlan.lastWords(" a b ", 3));
    }

    @Test
    public void aRejectedPartKeepsItsRawTextAndTheCheckpointMovesOn() {
        VoiceCleanupPlan plan = plan(LIGHT);
        VoiceCleanupPlan.Pass first = plan.nextEarlyPass(FOUR_SENTENCES, false, false);
        plan.onPassDone(first, VoiceTextPolisher.Result.fallback(first.raw, "refusal_or_answer"));
        String raw = FOUR_SENTENCES + MORE;
        VoiceCleanupPlan.Step step = plan.finish(raw);
        assertNotNull(step.pass);
        assertEquals(MORE.trim(), step.pass.raw);
        // The last three sentences, as heard where the part was kept as heard.
        assertEquals(FOUR_SENTENCES.substring(FOUR_SENTENCES.indexOf("A user")), step.pass.context);
        plan.onPassDone(step.pass, VoiceTextPolisher.Result.polished("The drawer opens everywhere."));
        assertTrue(plan.finish(raw).isDone());
        VoiceTextPolisher.Result result = plan.result(raw);
        assertTrue(result.isPolished());
        assertEquals(FOUR_SENTENCES + " The drawer opens everywhere.", result.text);
    }

    // ------------------------------------------------------------------ the end

    @Test
    public void withNothingCleanedEarlyTheEndIsTheSingleContextFreePass() {
        VoiceCleanupPlan plan = plan(POLISHED);
        String raw = "please fix the build again";
        VoiceCleanupPlan.Step step = plan.finish(raw);
        assertNotNull(step.pass);
        assertEquals(raw, step.pass.raw);
        assertEquals("", step.pass.context);
        assertFalse(step.pass.early);
        plan.onPassDone(step.pass, VoiceTextPolisher.Result.fallback(raw, "refusal_or_answer"));
        VoiceTextPolisher.Result result = plan.result(raw);
        assertEquals(raw, result.text);
        assertEquals("fallback:refusal_or_answer", result.outcome);
    }

    @Test
    public void theEndWaitsForThePassInFlightThenCleansTheTail() {
        VoiceCleanupPlan plan = plan(LIGHT);
        VoiceCleanupPlan.Pass first = plan.nextEarlyPass(FOUR_SENTENCES, false, false);
        String raw = FOUR_SENTENCES + MORE;
        assertTrue(plan.finish(raw).waits);
        plan.onPassDone(first, VoiceTextPolisher.Result.polished("First part."));
        VoiceCleanupPlan.Step step = plan.finish(raw);
        assertNotNull(step.pass);
        assertEquals(MORE.trim(), step.pass.raw);
        assertEquals("First part.", step.pass.context);
        plan.onPassDone(step.pass, VoiceTextPolisher.Result.polished("Second part."));
        assertEquals("First part. Second part.", plan.result(raw).text);
    }

    @Test
    public void aShortTailIsCleanedAgainWithTheLastPart() {
        VoiceCleanupPlan plan = plan(LIGHT);
        VoiceCleanupPlan.Pass first = plan.nextEarlyPass(FOUR_SENTENCES, false, false);
        plan.onPassDone(first, VoiceTextPolisher.Result.polished("First part."));
        String raw = FOUR_SENTENCES + " so yes";
        VoiceCleanupPlan.Step step = plan.finish(raw);
        assertNotNull(step.pass);
        assertEquals(raw, step.pass.raw);
        assertEquals("", step.pass.context);
        plan.onPassDone(step.pass, VoiceTextPolisher.Result.polished("First part, so yes."));
        assertTrue(plan.finish(raw).isDone());
        assertEquals("First part, so yes.", plan.result(raw).text);
    }

    @Test
    public void aTimedOutEarlyPassTurnsEarlyPassesOffAndTheEndCleansEverythingOnce() {
        VoiceCleanupPlan plan = plan(POLISHED);
        VoiceCleanupPlan.Pass first = plan.nextEarlyPass(FOUR_SENTENCES, false, false);
        plan.onPassDone(first, VoiceTextPolisher.Result.fallback(first.raw, "tai_runtime_timeout"));
        assertFalse(plan.earlyPassesOn());
        String raw = FOUR_SENTENCES + MORE + " " + FOUR_SENTENCES;
        assertNull(plan.nextEarlyPass(raw, false, false));
        VoiceCleanupPlan.Step step = plan.finish(raw);
        assertNotNull(step.pass);
        assertEquals(raw, step.pass.raw);
        assertEquals("", step.pass.context);
    }

    @Test
    public void aShortPartWithContextIsStillSentButAShortSessionIsNot() {
        VoiceCleanupPlan plan = plan(LIGHT);
        plan.carryOn("Already cleaned text.", true, null);
        VoiceCleanupPlan.Step step = plan.finish("Already cleaned text. and thanks");
        assertNotNull(step.pass);
        assertEquals("and thanks", step.pass.raw);
        assertEquals("Already cleaned text.", step.pass.context);
        assertNull(VoiceCleanupPlan.skipReason(step.pass));
        VoiceCleanupPlan alone = plan(LIGHT);
        VoiceCleanupPlan.Step single = alone.finish("and thanks");
        assertEquals("short", VoiceCleanupPlan.skipReason(single.pass));
    }

    // ------------------------------------------------------------------ carried text

    @Test
    public void finalCarriedTextIsNeverSentAgainAndNotCountedAsCleaned() {
        VoiceCleanupPlan plan = plan(LIGHT);
        plan.carryOn("Already cleaned text.", true, null);
        String raw = "Already cleaned text. and more words here";
        VoiceCleanupPlan.Step step = plan.finish(raw);
        assertEquals("and more words here", step.pass.raw);
        plan.onPassDone(step.pass, VoiceTextPolisher.Result.fallback(step.pass.raw, "unusable_output"));
        VoiceTextPolisher.Result result = plan.result(raw);
        assertFalse(result.isPolished());
        assertEquals(raw, result.text);
        assertEquals("fallback:unusable_output", result.outcome);
    }

    @Test
    public void aResumeTakesOverThePartsTheStoppedCleanupHadCleaned() {
        VoiceCleanupPlan before = plan(LIGHT);
        VoiceCleanupPlan.Pass first = before.nextEarlyPass(FOUR_SENTENCES, false, false);
        before.onPassDone(first, VoiceTextPolisher.Result.polished("First part."));
        String carried = FOUR_SENTENCES + MORE;
        // The end's pass is out when the user resumes: only finished parts carry.
        assertNotNull(before.finish(carried).pass);
        VoiceCleanupPlan after = plan(LIGHT);
        after.carryOn(carried, false, before);
        String raw = carried + " and one more thing to add";
        VoiceCleanupPlan.Step step = after.finish(raw);
        assertEquals(raw.substring(FOUR_SENTENCES.length()).trim(), step.pass.raw);
        assertEquals("First part.", step.pass.context);
        // Text that no longer starts with what was cleaned starts over.
        VoiceCleanupPlan other = plan(LIGHT);
        other.carryOn("Something else entirely.", false, before);
        assertEquals("", other.finish("Something else entirely. more").pass.context);
    }

    // ------------------------------------------------------------------ joining

    @Test
    public void polishedStartsEachPartOnANewLineAndLightJoinsWithASpace() {
        assertEquals("One.\nTwo.", VoiceCleanupPlan.join(Arrays.asList("One.", "Two."), true));
        assertEquals("I need three things:\n1. Log.\n2. Bump.",
            VoiceCleanupPlan.join(Arrays.asList("I need three things:", "1. Log.\n2. Bump."), true));
        assertEquals("1. Log.\n2. Bump.\nAnd reply.",
            VoiceCleanupPlan.join(Arrays.asList("1. Log.\n2. Bump.", "And reply."), true));
        // A part's own paragraphs stay as they are.
        assertEquals("Para one.\n\nPara two.\nNext.",
            VoiceCleanupPlan.join(Arrays.asList(" Para one.\n\nPara two. ", "", "Next."), true));
        assertEquals("1. Log. 2. Bump.", VoiceCleanupPlan.join(Arrays.asList("1. Log.", "2. Bump."), false));
    }
}
