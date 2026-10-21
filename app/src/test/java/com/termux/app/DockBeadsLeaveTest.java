package com.termux.app;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import com.termux.R;
import com.termux.app.dock.LensBeadGeometry;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Leaving Home's Lens beads (a settle onto the Workspace, whose dock is the plain slab) gives the
 * dock back every piece of its slab: the host's outline clip and rim, its slab-only children, no
 * bead clip or sideways cut, no shelf beads, and no swipe's moving beads kept for the next time.
 * And the glass host's own resize re-runs the accessory render, so a settle that moves the slab's
 * top (the dashes band of a Home style) re-cuts the glass at the host's new height.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class DockBeadsLeaveTest {

    /** Seven 120 px icons, 154 px apart, on a 1080 x 200 host: the default row on a 411 dp phone. */
    private static LensBeadGeometry.Beads sevenBeads() {
        float[] boxes = new float[7 * 4];
        for (int i = 0; i < 7; i++) {
            float cx = 77f + 154f * i;
            boxes[i * 4] = cx - 60f;
            boxes[i * 4 + 1] = 40f;
            boxes[i * 4 + 2] = cx + 60f;
            boxes[i * 4 + 3] = 160f;
        }
        LensBeadGeometry.Beads beads = LensBeadGeometry.compute(boxes, 7, 0f, 0f, 1080f, 200f, 2.625f);
        assertNotNull("the fixture row must wear beads", beads);
        return beads;
    }

    @Test
    public void leavingBeadsRestoresTheSlabsClipRimShelfAndChildren() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(activity, false);
        assertNotNull(preferences);
        ReflectionHelpers.setField(activity, "mPreferences", preferences);
        // The Workspace: its dock is the plain slab whatever Home's style is.
        ReflectionHelpers.setField(activity, "mLastWallPage", com.termux.app.wall.PaneWallPage.TERMINAL);

        DockSurfaceHostLayout host = activity.findViewById(R.id.accessory_surface_host);
        DockShelfView shelf = activity.findViewById(R.id.accessory_shelf_fx);
        assertNotNull(host);
        assertNotNull(shelf);
        host.setVisibility(View.VISIBLE);

        // The dock as Home's beads left it: the host cut as beads (no rim, no outline clip), the
        // beads clipped and cut sideways mid-swipe, the shelf shadowing each bead.
        LensBeadGeometry.Beads beads = sevenBeads();
        LensBeadGeometry.Beads moving = beads.offset(-40f, 0f);
        ReflectionHelpers.setField(activity, "mDockBeads", beads);
        ReflectionHelpers.setField(activity, "mDockBeadsMoving", moving);
        ReflectionHelpers.callInstanceMethod(activity, "applyDockSurfaceShape",
            ReflectionHelpers.ClassParameter.from(View.class, host),
            ReflectionHelpers.ClassParameter.from(boolean.class, true),
            ReflectionHelpers.ClassParameter.from(int.class, 200),
            ReflectionHelpers.ClassParameter.from(boolean.class, true),
            ReflectionHelpers.ClassParameter.from(boolean.class, true));
        host.setSlabOnlyChildren(activity.findViewById(R.id.accessory_top_edge_fx),
            activity.findViewById(R.id.accessory_edge_glow_fx));
        host.setBeadSpan(20f, 1000f);
        host.setBeads(moving);
        shelf.setBeads(moving);
        // Control: the bead cut really took the slab's clip and rim away.
        assertNull("control: beads draw no rim", host.getBackground());
        assertTrue("control: beads clip themselves, not to the outline", !host.getClipToOutline());

        boolean changed = ReflectionHelpers.callInstanceMethod(activity, "refreshDockBeads");

        assertTrue("leaving the beads is a change", changed);
        assertNull(ReflectionHelpers.getField(activity, "mDockBeads"));
        assertNull("a swipe's moving beads outlived the beads",
            ReflectionHelpers.getField(activity, "mDockBeadsMoving"));
        assertNull("the host still clips to beads", host.beads());
        assertNull("the shelf still shadows beads", shelf.beads());
        assertNotNull("the slab's rim did not come back", host.getBackground());
        assertTrue("the slab is not clipped to its outline again", host.getClipToOutline());
        assertSame(ViewOutlineProvider.BACKGROUND, host.getOutlineProvider());
    }

    @Test
    public void theSlabDrawsItsOwnChildrenAgainAndKeepsNoSidewaysCut() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        DockSurfaceHostLayout host = new DockSurfaceHostLayout(activity);
        int[] drawn = new int[1];
        View slabOnly = new View(activity) {
            @Override
            protected void onDraw(Canvas canvas) {
                drawn[0]++;
            }
        };
        slabOnly.setWillNotDraw(false);
        host.addView(slabOnly, new FrameLayout.LayoutParams(1080, 200));
        host.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, 1080, 200);
        host.setSlabOnlyChildren(slabOnly);
        Canvas canvas = new Canvas(Bitmap.createBitmap(1080, 200, Bitmap.Config.ARGB_8888));

        host.setBeadSpan(20f, 1000f);
        host.setBeads(sevenBeads());
        host.draw(canvas);
        assertEquals("control: beads skip the slab's own children", 0, drawn[0]);

        // Mid-melt across the seam the beads are also cut to the slab's outline.
        host.setSlabClip(30f);
        host.setBeads(null);
        assertTrue("the slab kept a melt's slab cut", Float.isNaN(host.slabClipRadius()));
        host.draw(canvas);
        assertEquals("the slab's own children are drawn again", 1, drawn[0]);
        float[] span = {
            ReflectionHelpers.<Float>getField(host, "mSpanLeft"),
            ReflectionHelpers.<Float>getField(host, "mSpanRight")};
        assertTrue("the slab kept a swipe's sideways cut", Float.isNaN(span[0]) && Float.isNaN(span[1]));
    }

    @Test
    public void theGlassHostsResizeReRunsTheAccessoryRender() {
        boolean watched = false;
        for (int id : TermuxActivity.ACCESSORY_LAYOUT_WATCH_IDS) {
            if (id == R.id.accessory_surface_host) watched = true;
        }
        assertTrue("a settle that moves the slab's top leaves the glass cut at the old height",
            watched);
    }
}
