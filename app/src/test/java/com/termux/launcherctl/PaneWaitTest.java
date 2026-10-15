package com.termux.launcherctl;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** {@code POST /v1/panes/{id}/wait}'s request checks and polling, against a scripted pane.read. */
public class PaneWaitTest {

    @Test
    public void matchesOutputAlreadyThereWithoutSleeping() throws JSONException {
        Script pane = new Script().text("$ make\nok 12 tests\n$ ", true);
        JSONObject result = wait(new JSONObject().put("match", "tests")).run(pane, pane);
        assertTrue(result.toString(), result.getBoolean("ok"));
        assertTrue(result.getBoolean("matched"));
        assertEquals("ok 12 tests", result.getString("text"));
        assertTrue(result.getBoolean("running"));
        assertEquals("p1", result.getString("id"));
        assertEquals(0, pane.slept);
        assertEquals(1, pane.reads.size());
    }

    @Test
    public void regexMatchesTheNewestMatchingLineOnceItAppears() throws JSONException {
        Script pane = new Script()
            .text("building", true)
            .text("building\nPASS a\nFAIL b", true);
        JSONObject result = wait(new JSONObject().put("regex", "^(PASS|FAIL) ")).run(pane, pane);
        assertTrue(result.toString(), result.getBoolean("matched"));
        assertEquals("FAIL b", result.getString("text"));
        assertEquals(PaneWait.POLL_MS, pane.slept);
    }

    @Test
    public void timesOutWith408AndSaysTheCommandMayStillRun() throws JSONException {
        Script pane = new Script().text("still going", true);
        JSONObject result = wait(new JSONObject().put("match", "done").put("timeout_ms", 1000))
            .run(pane, pane);
        assertEquals(408, result.getInt("_statusCode"));
        assertEquals("timeout", result.getString("error"));
        assertFalse(result.getBoolean("matched"));
        assertTrue(result.getBoolean("running"));
        assertEquals(1000, pane.slept);
        assertEquals(5, pane.reads.size());
    }

    @Test
    public void aZeroTimeoutLooksOnce() throws JSONException {
        Script pane = new Script().text("nothing", true);
        JSONObject result = wait(new JSONObject().put("match", "x").put("timeout_ms", 0)).run(pane, pane);
        assertEquals(408, result.getInt("_statusCode"));
        assertEquals(1, pane.reads.size());
    }

    @Test
    public void aShellThatEndsFirstIsCheckedOnceMoreThenAnswers409() throws JSONException {
        Script ended = new Script().text("compiling", false).text("compiling\nerror", false);
        JSONObject result = wait(new JSONObject().put("match", "done")).run(ended, ended);
        assertEquals(409, result.getInt("_statusCode"));
        assertEquals("pane_not_running", result.getString("error"));
        assertEquals(2, ended.reads.size());

        Script lastWords = new Script().text("compiling", false).text("compiling\ndone", false);
        JSONObject caught = wait(new JSONObject().put("match", "done")).run(lastWords, lastWords);
        assertTrue(caught.toString(), caught.getBoolean("matched"));
        assertFalse(caught.getBoolean("running"));
    }

    @Test
    public void aPaneThatClosesWhileWaitedOnIsNotRunningButAnUnknownOneIsNotFound() throws JSONException {
        Script closed = new Script().text("working", true).refuse(404, "pane_not_found");
        JSONObject gone = wait(new JSONObject().put("match", "done")).run(closed, closed);
        assertEquals(409, gone.getInt("_statusCode"));
        assertEquals("pane_not_running", gone.getString("error"));

        Script unknown = new Script().refuse(404, "pane_not_found");
        JSONObject notFound = wait(new JSONObject().put("match", "done")).run(unknown, unknown);
        assertEquals(404, notFound.getInt("_statusCode"));
        assertEquals("pane_not_found", notFound.getString("error"));
    }

    @Test
    public void aPaneTheCallerDoesNotOwnIsRefusedAtOnce() throws JSONException {
        Script users = new Script().refuse(403, "not_owned");
        JSONObject result = wait(new JSONObject().put("match", "x")).run(users, users);
        assertEquals(403, result.getInt("_statusCode"));
        assertEquals("not_owned", result.getString("error"));
        assertEquals(0, users.slept);
    }

    @Test
    public void aBusyUiThreadIsRetriedNotReported() throws JSONException {
        Script busy = new Script().refuse(504, "timeout").text("done", true);
        JSONObject result = wait(new JSONObject().put("match", "done")).run(busy, busy);
        assertTrue(result.toString(), result.getBoolean("matched"));
    }

    @Test
    public void linesAreAskedOfEveryRead() throws JSONException {
        Script pane = new Script().text("done", true);
        wait(new JSONObject().put("match", "done").put("lines", "200")).run(pane, pane);
        assertEquals(200, pane.reads.get(0).getInt("lines"));
        assertEquals("p1", pane.reads.get(0).getString("id"));

        Script plain = new Script().text("done", true);
        wait(new JSONObject().put("match", "done")).run(plain, plain);
        assertFalse(plain.reads.get(0).has("lines"));
    }

    @Test
    public void refusesMalformedRequestsWith400() throws JSONException {
        JSONObject[] bodies = {
            new JSONObject(),
            new JSONObject().put("match", ""),
            new JSONObject().put("match", "a").put("regex", "b"),
            new JSONObject().put("regex", "(unclosed"),
            new JSONObject().put("match", "a").put("timeout_ms", -1),
            new JSONObject().put("match", "a").put("timeout_ms", PaneWait.MAX_TIMEOUT_MS + 1),
            new JSONObject().put("match", "a").put("timeout_ms", "soon"),
            new JSONObject().put("match", "a").put("timeout_ms", 1.5),
            new JSONObject().put("match", "a").put("lines", 0),
            new JSONObject().put("match", 42),
        };
        for (JSONObject body : bodies) {
            JSONObject[] error = new JSONObject[1];
            assertNull(body.toString(), PaneWait.parse("p1", body, error));
            assertNotNull(body.toString(), error[0]);
            assertEquals(body.toString(), 400, error[0].getInt("_statusCode"));
            assertEquals(body.toString(), "bad_request", error[0].getString("error"));
        }
    }

    @Test
    public void defaultsAndTheCeilingAreAccepted() throws JSONException {
        JSONObject[] error = new JSONObject[1];
        PaneWait plain = PaneWait.parse("p1", new JSONObject().put("match", "a"), error);
        assertNotNull(plain);
        assertEquals(PaneWait.DEFAULT_TIMEOUT_MS, plain.timeoutMs);
        PaneWait longest = PaneWait.parse("p1",
            new JSONObject().put("regex", "a").put("timeout_ms", String.valueOf(PaneWait.MAX_TIMEOUT_MS)), error);
        assertNotNull(longest);
        assertEquals(PaneWait.MAX_TIMEOUT_MS, longest.timeoutMs);
    }

    @Test
    public void matchingLineLooksAtEachLineNewestFirst() throws JSONException {
        PaneWait literal = wait(new JSONObject().put("match", "x"));
        assertEquals("b x", literal.matchingLine("a x\nb x\nc"));
        assertEquals("x", literal.matchingLine("x"));
        assertNull(literal.matchingLine(""));
        assertNull(literal.matchingLine("a\nb"));
        // A regex sees one line at a time, so ^ and $ are the line's own ends.
        PaneWait anchored = wait(new JSONObject().put("regex", "^b$"));
        assertEquals("b", anchored.matchingLine("a\nb\nc"));
        assertNull(anchored.matchingLine("ab\nbc"));
    }

    @Test
    public void anInterruptedWaitAnswersAtOnce() throws JSONException {
        Script pane = new Script().text("working", true);
        pane.interrupt = true;
        JSONObject result = wait(new JSONObject().put("match", "done")).run(pane, pane);
        assertEquals(503, result.getInt("_statusCode"));
        assertTrue(Thread.interrupted());
    }

    @NonNull
    private static PaneWait wait(@NonNull JSONObject body) {
        JSONObject[] error = new JSONObject[1];
        PaneWait wait = PaneWait.parse("p1", body, error);
        assertNotNull(String.valueOf(error[0]), wait);
        assertSame(null, error[0]);
        return wait;
    }

    /**
     * pane.read's answers in order (the last one repeats), and a clock that only moves when the
     * wait sleeps.
     */
    private static final class Script implements PaneWait.Reader, PaneWait.Clock {
        final Deque<JSONObject> answers = new ArrayDeque<>();
        final List<JSONObject> reads = new ArrayList<>();
        long slept;
        boolean interrupt;

        Script text(@NonNull String text, boolean running) throws JSONException {
            answers.add(new JSONObject().put("ok", true).put("_statusCode", 200).put("id", "p1")
                .put("text", text).put("running", running));
            return this;
        }

        Script refuse(int status, @NonNull String code) throws JSONException {
            answers.add(new JSONObject().put("ok", false).put("_statusCode", status).put("error", code)
                .put("message", code));
            return this;
        }

        @NonNull @Override public JSONObject read(@NonNull JSONObject arguments) throws JSONException {
            reads.add(new JSONObject(arguments.toString()));
            JSONObject next = answers.size() > 1 ? answers.poll() : answers.peek();
            return new JSONObject(next.toString());
        }

        @Override public long nowMs() {
            return slept;
        }

        @Override public void sleepMs(long ms) throws InterruptedException {
            if (interrupt) throw new InterruptedException();
            slept += ms;
        }
    }
}
