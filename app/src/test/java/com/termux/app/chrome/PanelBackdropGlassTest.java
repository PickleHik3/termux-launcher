package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.glass.GlassEntry;
import com.termux.app.glass.GlassKind;
import com.termux.app.glass.GlassService;
import com.termux.app.window.WindowLayer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

/**
 * Panels take their Live slot from the glass registry (ADR 0029 section 10): the registry
 * is the only budget, so a panel opening never pushes a widget off Live behind its back, and the
 * recorder never has to choose a victim of its own.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Application.class)
public class PanelBackdropGlassTest {

    private GlassService glass;
    private LiveBackdrop recorder;
    private LiveBackdropFrameLayout root;
    private Activity activity;
    private final List<String> redressed = new ArrayList<>();

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        root = new LiveBackdropFrameLayout(activity);
        activity.setContentView(root);
        shadowOf(Looper.getMainLooper()).idle();
        glass = new GlassService(new com.termux.app.window.FramePipeline());
        glass.setLiveOn(true);
        recorder = new LiveBackdrop(root);
        PanelBackdrop.install(activity.getWindow().getDecorView(), new PanelBackdrop.Source() {
            @Nullable @Override public LiveBackdrop recorder() {
                return recorder;
            }

            @Nullable @Override public GlassService glass() {
                return glass;
            }
        });
    }

    private ImageView surface() {
        ImageView surface = new ImageView(activity);
        // Not laid out: a Snapshot waits for its first layout, so no GPU is asked for.
        root.addView(surface, new FrameLayout.LayoutParams(0, 0));
        return surface;
    }

    /** A widget on Live the way the activity gives one: in the registry and in the recorder. */
    private View widget(int n) {
        View slab = new View(activity);
        root.addView(slab);
        String element = "widget" + n;
        glass.register(new GlassEntry(element, slab, GlassKind.LIVE, GlassEntry.PRIORITY_WIDGET),
            kind -> {
                redressed.add(element + "=" + kind);
                if (kind != GlassKind.LIVE) recorder.detach(slab);
            });
        if (glass.granted(element) == GlassKind.LIVE) assertNotNull(recorder.attach(slab, 20f,
            LiveBackdrop.RANK_WIDGET));
        return slab;
    }

    @Test
    public void aPanelOpensLiveFromTheRegistryAndShutsBackToIt() {
        PanelBackdrop panel = new PanelBackdrop(surface(), root, "drawer");
        assertTrue(panel.open(20f));
        assertEquals(PanelBackdropPolicy.Kind.LIVE, panel.kind());
        assertEquals(GlassKind.LIVE, glass.granted("drawer"));
        assertEquals(1, recorder.size());
        panel.close();
        assertNull(glass.granted("drawer"));
        assertEquals(0, recorder.size());
    }

    @Test
    public void aPanelTakesTheWidgetsSlotThroughTheRegistryAndTheWidgetLetsGoFirst() {
        for (int i = 0; i < GlassRegistryLive.SLOTS; i++) widget(i);
        assertEquals(GlassRegistryLive.SLOTS, recorder.size());
        PanelBackdrop panel = new PanelBackdrop(surface(), root, "palette");
        assertTrue(panel.open(20f));
        // The registry moved the newest widget to Wallpaper and told it before the panel recorded:
        // the recorder held eight pictures all along and never evicted on its own.
        assertEquals(java.util.Arrays.asList("widget7=WALLPAPER"), redressed);
        assertEquals(PanelBackdropPolicy.Kind.LIVE, panel.kind());
        assertEquals(GlassRegistryLive.SLOTS, recorder.size());
        // Shut: the slot goes back to the widget that gave way (at the next frame's commit).
        panel.close();
        assertEquals(GlassKind.LIVE, glass.granted("widget7"));
    }

    @Test
    public void aPanelNotGrantedLiveIsASnapshotAndFollowsAPolicyEventWhileOpen() {
        glass.setLiveOn(false);
        PanelBackdrop panel = new PanelBackdrop(surface(), root, "sheet");
        assertTrue(panel.open(20f));
        assertEquals(PanelBackdropPolicy.Kind.SNAPSHOT, panel.kind());
        assertEquals(0, recorder.size());
        // Live comes on while the panel is open: it is put up again as Live, at the next commit.
        glass.setLiveOn(true);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(GlassKind.LIVE, glass.granted("sheet"));
        assertEquals(PanelBackdropPolicy.Kind.LIVE, panel.kind());
        assertEquals(1, recorder.size());
        // And a trim takes it off again.
        glass.setMemoryShort(true);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(PanelBackdropPolicy.Kind.SNAPSHOT, panel.kind());
        assertEquals(0, recorder.size());
        panel.close();
    }

    @Test
    public void theDrawersLayeredGlassIsASurfacesPictureAndTheStoredFrameWhenNotLive() {
        ImageView surface = surface();
        PanelBackdrop panel = new PanelBackdrop(surface, root, "drawer");
        panel.setLayered(30f);
        assertTrue(panel.open(20f));
        assertEquals(PanelBackdropPolicy.Kind.LIVE, panel.kind());
        // Attached as a surface at the stored frame's blur: it draws the backdrop layers, not a
        // panel's one picture.
        LivePicture picture = (LivePicture) ReflectionHelpers.callInstanceMethod(recorder, "find",
            ReflectionHelpers.ClassParameter.from(View.class, surface));
        assertNotNull(picture);
        assertFalse(picture.panelRadius());
        assertEquals(30f, picture.radiusPx(), 0f);
        assertEquals(WindowLayer.SHEETS, glass.entry("drawer").layer);
        // Live off: the stored frame, as every other surface; never a Snapshot.
        glass.setLiveOn(false);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(PanelBackdropPolicy.Kind.STORED, panel.kind());
        assertNull(surface.getDrawable());
        assertEquals(0, recorder.size());
        // And back on, while it is open.
        glass.setLiveOn(true);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(PanelBackdropPolicy.Kind.LIVE, panel.kind());
        panel.close();
        assertNull(glass.granted("drawer"));
    }

    @Test
    public void aPanelIsEntryInTheSheetsLayerAtTheDocksPriority() {
        PanelBackdrop panel = new PanelBackdrop(surface(), root, "drawer");
        panel.open(20f);
        assertEquals(WindowLayer.SHEETS, glass.entry("drawer").layer);
        assertEquals(GlassEntry.PRIORITY_DOCK, glass.entry("drawer").priority);
        panel.close();
    }

    /** The registry's Live slots, named for the test. */
    private static final class GlassRegistryLive {
        static final int SLOTS = com.termux.app.glass.GlassRegistry.LIVE_SLOTS;
    }
}
