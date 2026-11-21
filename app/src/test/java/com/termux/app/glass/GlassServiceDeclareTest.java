package com.termux.app.glass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.os.Build;
import android.view.View;

import com.termux.app.window.WindowLayer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The declared sets of an owner and the Live picture a held entry draws from the source. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassServiceDeclareTest {

    private GlassService glass;
    private final List<String> log = new ArrayList<>();
    private final Object picture = new Object();

    private final GlassService.LiveSource source = new GlassService.LiveSource() {
        @Override public Object attach(View surface, float radiusPx, int priority) {
            log.add("attach");
            return picture;
        }
        @Override public void detach(View surface) { log.add("detach"); }
    };

    @Before
    public void setUp() {
        glass = new GlassService(new com.termux.app.window.FramePipeline());
        glass.setLiveSource(source);
    }

    private static View view() {
        return new View(RuntimeEnvironment.getApplication());
    }

    private static GlassEntry entry(String element, View view, GlassService.Redress redress) {
        return new GlassEntry(element, view, GlassKind.LIVE, GlassEntry.PRIORITY_WIDGET,
            WindowLayer.PAGES, null, redress);
    }

    @Test
    public void declareRegistersAndUndeclareUnregistersAndDetaches() {
        glass.declare("o", Collections.singletonList(entry("a", view(), null)), false);
        assertNotNull(glass.entry("a"));
        glass.undeclare("o");
        assertNull(glass.entry("a"));
        assertEquals(Arrays.asList("detach"), log);
    }

    @Test
    public void aDroppedElementGoesWhenTheNextSetLeavesItOut() {
        glass.declare("o", Arrays.asList(entry("a", view(), null), entry("b", view(), null)), false);
        glass.declare("o", Collections.singletonList(entry("b", glass.entry("b").view, null)), false);
        assertNull(glass.entry("a"));
        assertNotNull(glass.entry("b"));
    }

    @Test
    public void aDeclaredRedressRunsWhenLiveMovesTheKind() {
        List<GlassKind> kinds = new ArrayList<>();
        glass.declare("o", Collections.singletonList(entry("a", view(), kinds::add)), false);
        GlassKind before = glass.granted("a");
        glass.setLiveOn(true);
        org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper()).idle();
        assertEquals(1, kinds.size());
        assertEquals(glass.granted("a"), kinds.get(0));
        assertNotEquals(before, kinds.get(0));
    }

    @Test
    public void redressNewTellsANewEntryItsKindAndFalseDoesNot() {
        List<GlassKind> kinds = new ArrayList<>();
        glass.declare("o", Collections.singletonList(entry("a", view(), kinds::add)), false);
        assertEquals(0, kinds.size());
        glass.declare("p", Collections.singletonList(entry("b", view(), kinds::add)), true);
        assertEquals(1, kinds.size());
        assertEquals(glass.granted("b"), kinds.get(0));
        // The same entry declared again is not new.
        glass.declare("p", Collections.singletonList(
            entry("b", glass.entry("b").view, kinds::add)), true);
        assertEquals(1, kinds.size());
    }

    @Test
    public void aTwinIsRefusedAndItsUndeclareLeavesTheOtherViewsEntry() {
        View first = view();
        glass.declare("o", Collections.singletonList(entry("a", first, null)), false);
        glass.declare("p", Collections.singletonList(entry("a", view(), null)), false);
        assertSame(first, glass.entry("a").view);
        glass.undeclare("p");
        assertSame(first, glass.entry("a").view);
    }

    @Test
    public void livePictureIsNullAndDetachesWhenNotLive() {
        glass.declare("o", Collections.singletonList(entry("a", view(), null)), false);
        assertNull(glass.livePicture("a", 8f, Object.class));
        assertEquals(Arrays.asList("detach"), log);
        assertNull(glass.livePicture("none", 8f, Object.class));
    }

    @Test
    public void livePictureIsTheSourcesObjectWhenLive() {
        glass.setLiveOn(true);
        glass.declare("o", Collections.singletonList(entry("a", view(), null)), false);
        assertEquals(GlassKind.LIVE, glass.granted("a"));
        assertSame(picture, glass.livePicture("a", 8f, Object.class));
        assertNull(glass.livePicture("a", 8f, String.class));
    }
}
