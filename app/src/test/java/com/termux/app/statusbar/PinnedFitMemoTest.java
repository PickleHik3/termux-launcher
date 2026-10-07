package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The pinned cards' fitted-text memo fits each line once and hands back the same answer. */
public class PinnedFitMemoTest {

    /** Keeps as many characters as the room allows, one per ten pixels, and records each call. */
    private static final class CountingFitter implements PinnedNotificationsView.FitMemo.Fitter {
        final List<String> asked = new ArrayList<>();

        @Override
        public CharSequence fit(String text, float room) {
            asked.add(text);
            int keep = Math.max(0, Math.min(text.length(), (int) (room / 10f)));
            return keep == text.length() ? text : text.substring(0, keep) + "…";
        }
    }

    @Test
    public void aLineIsFittedOnceWhileItsTextRoomFaceAndSizeHold() {
        PinnedNotificationsView.FitMemo memo = new PinnedNotificationsView.FitMemo();
        CountingFitter fitter = new CountingFitter();
        Object face = new Object();
        CharSequence first = memo.fit("Ada Lovelace", false, 60f, face, 28f, fitter);
        for (int frame = 0; frame < 40; frame++) {
            assertSame(first, memo.fit("Ada Lovelace", false, 60f, face, 28f, fitter));
        }
        assertEquals(1, fitter.asked.size());
        assertEquals("Ada Lo…", first.toString());
    }

    @Test
    public void flatteningIsPartOfTheKeyAndHappensBeforeTheFit() {
        PinnedNotificationsView.FitMemo memo = new PinnedNotificationsView.FitMemo();
        CountingFitter fitter = new CountingFitter();
        Object face = new Object();
        CharSequence flat = memo.fit("one\ntwo", true, 500f, face, 28f, fitter);
        CharSequence raw = memo.fit("one\ntwo", false, 500f, face, 28f, fitter);
        assertEquals("one two", flat.toString());
        assertEquals("one\ntwo", raw.toString());
        assertEquals(2, fitter.asked.size());
    }

    @Test
    public void anyOtherPartOfTheKeyMovingFitsAgain() {
        PinnedNotificationsView.FitMemo memo = new PinnedNotificationsView.FitMemo();
        CountingFitter fitter = new CountingFitter();
        Object face = new Object();
        memo.fit("Grace", false, 30f, face, 28f, fitter);
        memo.fit("Grace", false, 31f, face, 28f, fitter);
        memo.fit("Grace", false, 30f, new Object(), 28f, fitter);
        memo.fit("Grace", false, 30f, face, 29f, fitter);
        memo.fit("Grace Hopper", false, 30f, face, 28f, fitter);
        assertEquals(5, fitter.asked.size());
    }

    @Test
    public void anEvictedLineIsFittedAgainToTheSameText() {
        PinnedNotificationsView.FitMemo memo = new PinnedNotificationsView.FitMemo();
        CountingFitter fitter = new CountingFitter();
        Object face = new Object();
        CharSequence first = memo.fit("Margaret", false, 40f, face, 28f, fitter);
        for (int i = 0; i < PinnedNotificationsView.FitMemo.CAPACITY; i++) {
            memo.fit("filler " + i, false, 40f, face, 28f, fitter);
        }
        assertEquals(first.toString(), memo.fit("Margaret", false, 40f, face, 28f, fitter).toString());
        assertEquals(PinnedNotificationsView.FitMemo.CAPACITY + 2, fitter.asked.size());
    }
}
