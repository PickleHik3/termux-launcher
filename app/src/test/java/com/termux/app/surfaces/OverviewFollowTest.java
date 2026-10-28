package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.termux.app.wall.PageOverviewPolicy.Kind;

import org.junit.Test;

public class OverviewFollowTest {

    @Test
    public void eachCardKindMapsToItsEditorCardOrFolds() {
        assertEquals(EditorCard.HOME, OverviewFollow.of(Kind.HOME_PAGE).card);
        assertEquals(EditorCard.WORKSPACE, OverviewFollow.of(Kind.TERMINAL).card);
        assertEquals(OverviewFollow.FOLD, OverviewFollow.of(Kind.DISPLAY));
        assertNull(OverviewFollow.FOLD.card);
    }
}
