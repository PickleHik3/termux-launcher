package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;

import androidx.annotation.NonNull;

import org.junit.Test;

/**
 * Dictation marks: the exact bytes a program with mode 7727 and bracketed paste gets, and the
 * unchanged bytes every other program gets.
 */
public class DictationMarksTest {

    /** A terminal session: whether it takes marks, and everything written to it. */
    private static final class FakeSession {
        boolean acceptsMarks;
        boolean bracketedPaste;
        final StringBuilder written = new StringBuilder();

        FakeSession(boolean acceptsMarks) {
            this.acceptsMarks = acceptsMarks;
            this.bracketedPaste = acceptsMarks;
        }

        FakeSession(boolean acceptsMarks, boolean bracketedPaste) {
            this.acceptsMarks = acceptsMarks;
            this.bracketedPaste = bracketedPaste;
        }

        String takeWritten() {
            String out = written.toString();
            written.setLength(0);
            return out;
        }
    }

    private static DictationMarks<FakeSession> marks() {
        return new DictationMarks<>(new DictationMarks.Io<FakeSession>() {
            @Override
            public boolean acceptsMarks(@NonNull FakeSession session) {
                return session.acceptsMarks;
            }

            @Override
            public boolean acceptsBracketedPaste(@NonNull FakeSession session) {
                return session.bracketedPaste;
            }

            @Override
            public void write(@NonNull FakeSession session, @NonNull String data) {
                session.written.append(data);
            }
        });
    }

    @Test
    public void sequencesAreFormattedAsTheProtocolSays() {
        assertEquals("\033]7727;listen\033\\", DictationMarks.listenMark());
        assertEquals("\033]7727;phrase;id=1\033\\", DictationMarks.phraseMark(1));
        assertEquals("\033]7727;phrase;id=42\033\\", DictationMarks.phraseMark(42));
        assertEquals("\033]7727;end\033\\", DictationMarks.endMark(false));
        assertEquals("\033]7727;end;reason=cancel\033\\", DictationMarks.endMark(true));
        assertEquals("\033[200~git status\033[201~", DictationMarks.bracketed("git status"));
    }

    @Test
    public void anEscapeCannotEndThePasteEarly() {
        assertEquals("\033[200~a[201~b\033[201~", DictationMarks.bracketed("a\033[201~b"));
    }

    @Test
    public void modeOffIsTodaysBytes() {
        DictationMarks<FakeSession> marks = marks();
        FakeSession session = new FakeSession(false);
        marks.listen(session);
        assertEquals("", session.takeWritten());
        marks.type(session, "hello world");
        assertEquals("hello world", session.takeWritten());
        marks.end(false);
        marks.end(true);
        assertEquals("", session.takeWritten());
    }

    @Test
    public void aDictationIsListenPhraseEnd() {
        DictationMarks<FakeSession> marks = marks();
        FakeSession session = new FakeSession(true);
        marks.listen(session);
        assertEquals(DictationMarks.listenMark(), session.takeWritten());
        marks.end(false);
        assertEquals(DictationMarks.endMark(false), session.takeWritten());
        // ✓ comes after the microphone has closed: the text waited in the panel.
        marks.type(session, "hello world");
        assertEquals("\033]7727;phrase;id=1\033\\\033[200~hello world\033[201~", session.takeWritten());
    }

    @Test
    public void phraseIdsRisePerSession() {
        DictationMarks<FakeSession> marks = marks();
        FakeSession first = new FakeSession(true);
        FakeSession second = new FakeSession(true);
        marks.type(first, "a");
        marks.type(first, "b");
        marks.type(second, "c");
        marks.type(first, "d");
        assertEquals(DictationMarks.phraseMark(1) + DictationMarks.bracketed("a")
            + DictationMarks.phraseMark(2) + DictationMarks.bracketed("b")
            + DictationMarks.phraseMark(3) + DictationMarks.bracketed("d"), first.takeWritten());
        assertEquals(DictationMarks.phraseMark(1) + DictationMarks.bracketed("c"), second.takeWritten());
    }

    @Test
    public void aTextTypedWhileTheModeIsOffTakesNoId() {
        DictationMarks<FakeSession> marks = marks();
        FakeSession session = new FakeSession(false);
        marks.type(session, "plain");
        session.acceptsMarks = true;
        marks.type(session, "marked");
        assertEquals("plain" + DictationMarks.phraseMark(1) + DictationMarks.bracketed("marked"),
            session.takeWritten());
    }

    @Test
    public void endGoesToTheSessionThatHeardListen() {
        DictationMarks<FakeSession> marks = marks();
        FakeSession listened = new FakeSession(true);
        FakeSession other = new FakeSession(true);
        marks.listen(listened);
        listened.takeWritten();
        marks.end(true);
        assertEquals(DictationMarks.endMark(true), listened.takeWritten());
        assertEquals("", other.takeWritten());
    }

    @Test
    public void marksTakeAMarkedBracketedPasteWithItsLineBreaks() {
        DictationMarks<FakeSession> marks = marks();
        FakeSession session = new FakeSession(true);
        marks.type(session, "1. one\n2. two");
        assertEquals(DictationMarks.phraseMark(1) + "\033[200~1. one\n2. two\033[201~", session.takeWritten());
    }

    @Test
    public void bracketedPasteAloneTakesAnUnmarkedPasteWithItsLineBreaks() {
        DictationMarks<FakeSession> marks = marks();
        FakeSession session = new FakeSession(false, true);
        marks.type(session, "1. one\n2. two");
        assertEquals("\033[200~1. one\n2. two\033[201~", session.takeWritten());
    }

    @Test
    public void aBareShellGetsEveryLineBreakAsASpace() {
        DictationMarks<FakeSession> marks = marks();
        FakeSession session = new FakeSession(false, false);
        marks.type(session, "First point.\r\n\r\n1. one\n2. two");
        assertEquals("First point. 1. one 2. two", session.takeWritten());
        marks.type(session, "no breaks here");
        assertEquals("no breaks here", session.takeWritten());
    }

    @Test
    public void endIsSentOnce() {
        DictationMarks<FakeSession> marks = marks();
        FakeSession session = new FakeSession(true);
        marks.listen(session);
        session.takeWritten();
        marks.end(true);
        marks.end(false);
        assertEquals(DictationMarks.endMark(true), session.takeWritten());
    }

    @Test
    public void noEndOnceTheProgramHasTurnedTheModeOff() {
        DictationMarks<FakeSession> marks = marks();
        FakeSession session = new FakeSession(true);
        marks.listen(session);
        session.takeWritten();
        session.acceptsMarks = false;
        marks.end(false);
        assertEquals("", session.takeWritten());
    }

    @Test
    public void noListenWithNoShellInFront() {
        DictationMarks<FakeSession> marks = marks();
        FakeSession earlier = new FakeSession(true);
        marks.listen(earlier);
        earlier.takeWritten();
        // The Dictate key on Home: no shell to tell, but the earlier listen is closed.
        marks.listen(null);
        assertEquals(DictationMarks.endMark(false), earlier.takeWritten());
        marks.end(false);
        assertEquals("", earlier.takeWritten());
    }

    @Test
    public void aSecondListenEndsTheFirst() {
        DictationMarks<FakeSession> marks = marks();
        FakeSession first = new FakeSession(true);
        FakeSession second = new FakeSession(true);
        marks.listen(first);
        marks.listen(second);
        assertEquals(DictationMarks.listenMark() + DictationMarks.endMark(false), first.takeWritten());
        assertEquals(DictationMarks.listenMark(), second.takeWritten());
    }
}
