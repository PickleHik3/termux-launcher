package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The first buffer's colour: the wallpaper's primary made opaque, black when unknown. */
public class SceneFirstBufferTest {

    @Test
    public void unknownColoursAreBlack() {
        assertEquals(0xff000000, SceneFirstBuffer.colour(null));
    }

    @Test
    public void thePrimaryIsMadeOpaque() {
        assertEquals(0xff336699, SceneFirstBuffer.colour(0x00336699));
        assertEquals(0xff336699, SceneFirstBuffer.colour(0x80336699));
        assertEquals(0xffabcdef, SceneFirstBuffer.colour(0xffabcdef));
    }
}
