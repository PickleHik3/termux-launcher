package com.termux.app.launcher.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PinnedArtworkMemoTest {

    @Test
    public void anAnswerComesBackForTheSameKeyAndBaseline() {
        PinnedArtworkMemo<Object, String> memo = new PinnedArtworkMemo<>(4);
        Object global = new Object();
        memo.put("app|pack:1", global, "pinned");
        assertSame("pinned", memo.get("app|pack:1", global));
    }

    @Test
    public void aNewBaselineIsAMissEvenWhenItLooksTheSame() {
        PinnedArtworkMemo<String, String> memo = new PinnedArtworkMemo<>(4);
        memo.put("app", new String("icon"), "pinned");
        assertNull(memo.get("app", new String("icon")));
        // The stale answer is dropped, not kept for a later lookup.
        assertEquals(0, memo.size());
    }

    @Test
    public void aNullBaselineIsRememberedToo() {
        PinnedArtworkMemo<Object, String> memo = new PinnedArtworkMemo<>(4);
        memo.put("app", null, "pinned");
        assertSame("pinned", memo.get("app", null));
        assertNull(memo.get("app", new Object()));
    }

    @Test
    public void aDifferentKeyIsAMiss() {
        PinnedArtworkMemo<Object, String> memo = new PinnedArtworkMemo<>(4);
        Object global = new Object();
        memo.put("app|pack:1", global, "pinned");
        assertNull(memo.get("app|pack:2", global));
    }

    @Test
    public void theLeastRecentlyUsedAnswerGoesFirst() {
        PinnedArtworkMemo<Object, String> memo = new PinnedArtworkMemo<>(2);
        memo.put("a", null, "A");
        memo.put("b", null, "B");
        memo.get("a", null);
        memo.put("c", null, "C");
        assertEquals(2, memo.size());
        assertSame("A", memo.get("a", null));
        assertNull(memo.get("b", null));
        assertSame("C", memo.get("c", null));
    }

    @Test
    public void clearForgetsEverything() {
        PinnedArtworkMemo<Object, String> memo = new PinnedArtworkMemo<>(2);
        memo.put("a", null, "A");
        memo.clear();
        assertNull(memo.get("a", null));
    }
}
