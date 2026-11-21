package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LiveGroundTest {

    @Test
    public void noSceneEveryViewDrawsItself() {
        assertEquals(LiveGround.Draw.CHILD, LiveGround.choose(false, false, false));
        assertEquals(LiveGround.Draw.CHILD, LiveGround.choose(false, true, false));
        assertEquals(LiveGround.Draw.CHILD, LiveGround.choose(false, false, true));
    }

    @Test
    public void sceneLayerIsReplacedByItsFrame() {
        assertEquals(LiveGround.Draw.SCENE_FRAME, LiveGround.choose(true, true, false));
    }

    @Test
    public void photoIsLeftOutWhileASceneIsOn() {
        assertEquals(LiveGround.Draw.SKIP, LiveGround.choose(true, false, true));
    }

    @Test
    public void otherViewsAreUntouchedWithASceneOn() {
        assertEquals(LiveGround.Draw.CHILD, LiveGround.choose(true, false, false));
    }
}
