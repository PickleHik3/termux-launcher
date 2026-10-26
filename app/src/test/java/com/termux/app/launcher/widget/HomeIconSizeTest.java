package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Home's icon size presets (editor-redesign §6): Small 48, Default 54 and Large 60 dp on the key
 * Settings › Home always used, an old percentage of the dock's 48 dp read as the preset nearest
 * the size it drew.
 */
public class HomeIconSizeTest {

    @Test
    public void thePresetsAreLauncher3sSizes() {
        assertEquals(48, HomeIconSize.SMALL.dp);
        assertEquals(54, HomeIconSize.DEFAULT.dp);
        assertEquals(60, HomeIconSize.LARGE.dp);
        assertEquals(1f, HomeIconSize.SMALL.scale(), 0f);
        assertEquals(1.125f, HomeIconSize.DEFAULT.scale(), 0f);
        assertEquals(1.25f, HomeIconSize.LARGE.scale(), 0f);
    }

    @Test
    public void aPresetReadsAsItself() {
        for (HomeIconSize size : HomeIconSize.values())
            assertEquals(size, HomeIconSize.fromStored(size.value));
        assertEquals(HomeIconSize.LARGE, HomeIconSize.fromStored(" large "));
    }

    @Test
    public void anOldPercentageReadsAsThePresetNearestTheSizeItDrew() {
        // 80% drew 38.4 dp, 90% 43.2, 100% 48: all nearest Small.
        assertEquals(HomeIconSize.SMALL, HomeIconSize.fromStored("80"));
        assertEquals(HomeIconSize.SMALL, HomeIconSize.fromStored("90"));
        assertEquals(HomeIconSize.SMALL, HomeIconSize.fromStored("100"));
        // 110% drew 52.8 dp, nearest Default; 120% 57.6, nearest Large.
        assertEquals(HomeIconSize.DEFAULT, HomeIconSize.fromStored("110"));
        assertEquals(HomeIconSize.LARGE, HomeIconSize.fromStored("120"));
        assertEquals(HomeIconSize.LARGE, HomeIconSize.fromStored("400"));
        assertEquals(HomeIconSize.SMALL, HomeIconSize.fromStored("10"));
    }

    @Test
    public void unsetOrUnreadableIsTheDefault() {
        assertEquals(HomeIconSize.DEFAULT, HomeIconSize.fromStored(null));
        assertEquals(HomeIconSize.DEFAULT, HomeIconSize.fromStored("big"));
        assertEquals(HomeIconSize.DEFAULT, HomeIconSize.fromStored(""));
    }

    @Test
    public void aTieGoesToTheSmaller() {
        assertEquals(HomeIconSize.SMALL, HomeIconSize.nearestDp(51f));
        assertEquals(HomeIconSize.DEFAULT, HomeIconSize.nearestDp(57f));
    }
}
