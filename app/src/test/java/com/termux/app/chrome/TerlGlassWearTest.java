package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.termux.app.glass.GlassEntry;
import com.termux.app.glass.GlassKind;
import com.termux.app.glass.GlassService;
import com.termux.app.window.FramePipeline;
import com.termux.app.window.WindowLayer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Terl's glass in the glass service (ADR 0029, pong 11-28): the control bar is the dock's entry,
 * a sheet a panel that holds its entry while it is in the window, and a policy event or a landed
 * stored frame dresses the glass again in the drawable its view already wears.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class TerlGlassWearTest {

    private FramePipeline frames;
    private GlassService glass;
    private TerlGlassWear wear;
    private FrameLayout window;
    private final List<String> log = new ArrayList<>();
    /** The surfaces the fake recorder holds a picture for, and how many it can hold. */
    private final java.util.Set<View> held = new java.util.HashSet<>();
    private int capacity = Integer.MAX_VALUE;

    /** A builder that counts its builds and samples {@link #frame}. */
    private final class Counting implements TerlGlassWear.Builder {
        int builds;
        int liveBuilds;
        @Nullable Object frame = new Object();

        @Nullable @Override public Drawable build(@Nullable LivePicture live) {
            builds++;
            if (live != null) liveBuilds++;
            return new ColorDrawable(0x80000000);
        }

        @Nullable @Override public Object frameNow() {
            return frame;
        }
    }

    @Before
    public void setUp() {
        frames = new FramePipeline();
        glass = new GlassService(frames);
        glass.setLiveSource(new GlassService.LiveSource() {
            @Override public Object attach(View surface, float radiusPx, int priority) {
                log.add("attach");
                // The recorder's own budget: full, it refuses a surface it does not hold.
                if (!held.contains(surface) && held.size() >= capacity) {
                    log.add("refused");
                    return null;
                }
                held.add(surface);
                log.add("held");
                return null;
            }
            @Override public void detach(View surface) {
                log.add("detach");
                held.remove(surface);
            }
        });
        wear = new TerlGlassWear(() -> glass);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        window = new FrameLayout(activity);
        activity.setContentView(window);
    }

    private View attached() {
        View view = new View(window.getContext());
        window.addView(view);
        return view;
    }

    @Test
    public void theControlBarIsTheDocksEntryInTheChromeLayer() {
        View bar = attached();
        wear.wear(bar, TerlGlassWear.element(true, "CONTROL_BAR", 1), WindowLayer.CHROME, 24f, true,
            new Counting());
        GlassEntry entry = glass.entry(TerlGlassWear.BAR_ELEMENT);
        assertNotNull("the bar holds the dock's entry", entry);
        assertSame(bar, entry.view);
        assertEquals(WindowLayer.CHROME, entry.layer);
        assertEquals(GlassEntry.PRIORITY_DOCK, entry.priority);
        assertEquals(GlassKind.LIVE, entry.wants);
    }

    @Test
    public void aSheetHoldsItsEntryWhileInTheWindowAndLetsItGoWhenItLeaves() {
        View sheet = attached();
        String element = TerlGlassWear.element(false, "SHEETS", System.identityHashCode(sheet));
        wear.wear(sheet, element, WindowLayer.SHEETS, 24f, true, new Counting());
        assertNotNull(glass.entry(element));
        assertEquals(WindowLayer.SHEETS, glass.entry(element).layer);
        window.removeView(sheet);
        assertNull("a closed sheet holds no entry", glass.entry(element));
        assertTrue("and its picture is let go", log.contains("detach"));
    }

    @Test
    public void anOpaqueGlassHoldsNoEntryAndSamplesNothing() {
        View bar = attached();
        Counting builder = new Counting();
        builder.frame = null;
        wear.wear(bar, TerlGlassWear.BAR_ELEMENT, WindowLayer.CHROME, 0f, false, builder);
        assertNull(glass.entry(TerlGlassWear.BAR_ELEMENT));
        assertEquals(1, builder.builds);
    }

    @Test
    public void aPolicyEventDressesTheGlassAgainInTheDrawableTheViewWears() {
        View bar = attached();
        Counting builder = new Counting();
        Drawable worn = wear.wear(bar, TerlGlassWear.BAR_ELEMENT, WindowLayer.CHROME, 24f, true, builder);
        bar.setBackground(worn);
        assertEquals("Live off: the stored frame", GlassKind.WALLPAPER,
            glass.granted(TerlGlassWear.BAR_ELEMENT));
        assertFalse(log.contains("attach"));
        // The Live blur switch: the service grants Live and, at its commit (the frame's last
        // step), the glass asks the recorder for its layers.
        glass.setLiveOn(true);
        frames.flush("test");
        assertEquals(GlassKind.LIVE, glass.granted(TerlGlassWear.BAR_ELEMENT));
        assertEquals("dressed again on the policy event", 2, builder.builds);
        assertTrue("the Live layers are asked for through the registry", log.contains("attach"));
        assertSame("in the drawable the bar already wears", worn, bar.getBackground());
    }

    @Test
    public void aTwinWearsTheStoredFrameWithoutTakingTheEntry() {
        View v1Dock = attached();
        glass.register(new GlassEntry(TerlGlassWear.BAR_ELEMENT, v1Dock, GlassKind.LIVE,
            GlassEntry.PRIORITY_DOCK, WindowLayer.CHROME, null), null);
        View bar = attached();
        Counting builder = new Counting();
        wear.wear(bar, TerlGlassWear.BAR_ELEMENT, WindowLayer.CHROME, 24f, true, builder);
        assertSame("the holder keeps it", v1Dock, glass.entry(TerlGlassWear.BAR_ELEMENT).view);
        assertEquals(1, builder.builds);
        assertEquals(0, builder.liveBuilds);
    }

    @Test
    public void aStoredGlassIsDressedAgainOnlyWhenItsFrameMoves() {
        // Pong 11-28: dressed while the cache was empty, the bar kept a tint alone, content sharp
        // behind it, until a look change; the frame landing never reached it.
        View bar = attached();
        Counting builder = new Counting();
        builder.frame = null;
        wear.wear(bar, TerlGlassWear.BAR_ELEMENT, WindowLayer.CHROME, 24f, true, builder);
        wear.refreshStored();
        assertEquals("nothing landed: nothing is built", 1, builder.builds);
        Object landed = new Object();
        builder.frame = landed;
        wear.refreshStored();
        assertEquals("the frame landed: dressed with it", 2, builder.builds);
        assertTrue(wear.holds(landed));
        wear.refreshStored();
        assertEquals(2, builder.builds);
    }

    /**
     * Review P2: after a cache clear the drawable still draws the old frame until it is dressed
     * again, so the bookkeeping keeps answering for it (the cache must not recycle it under the
     * bar); dressed again on what the cache has, it lets it go.
     */
    @Test
    public void aClearedCachesFrameIsHeldUntilTheGlassLetsItGo() {
        View bar = attached();
        Counting builder = new Counting();
        Object old = builder.frame;
        wear.wear(bar, TerlGlassWear.BAR_ELEMENT, WindowLayer.CHROME, 24f, true, builder);
        assertTrue(wear.holds(old));
        builder.frame = null;
        assertTrue("still drawn: still held", wear.holds(old));
        wear.onCacheCleared();
        assertEquals("dressed again on what the cache has", 2, builder.builds);
        assertFalse("let go by the drawable, then by the bookkeeping", wear.holds(old));
        Object landed = new Object();
        builder.frame = landed;
        wear.refreshStored();
        assertTrue(wear.holds(landed));
    }

    /** Review P1: a glass that loses its Live grant lets its recording go. */
    @Test
    public void aLostLiveGrantLetsTheRecordingGo() {
        View bar = attached();
        wear.wear(bar, TerlGlassWear.BAR_ELEMENT, WindowLayer.CHROME, 24f, true, new Counting());
        glass.setLiveOn(true);
        frames.flush("test");
        assertTrue(held.contains(bar));
        glass.setLiveOn(false);
        frames.flush("test");
        assertFalse("its slot freed for the next surface", held.contains(bar));
    }

    /**
     * Review P1: a glass granted Live while the recorder is full (another surface's picture still
     * held) asks again once that picture goes, with no grant change to tell it.
     */
    @Test
    public void aGlassRefusedAPictureTakesTheSlotWhenItFrees() {
        capacity = 1;
        View stale = attached();
        held.add(stale);
        View bar = attached();
        glass.setLiveOn(true);
        wear.wear(bar, TerlGlassWear.BAR_ELEMENT, WindowLayer.CHROME, 24f, true, new Counting());
        frames.flush("test");
        assertEquals(GlassKind.LIVE, glass.granted(TerlGlassWear.BAR_ELEMENT));
        assertFalse("refused while full", held.contains(bar));
        held.remove(stale);
        wear.refreshStored();
        assertTrue("asked again and held", held.contains(bar));
    }

    @Test
    public void elementsNeverCollideAcrossSheetsAndTheBarIsTheDock() {
        assertEquals("dock", TerlGlassWear.element(true, "CONTROL_BAR", 7));
        assertFalse(TerlGlassWear.element(false, "SHEETS", 7)
            .equals(TerlGlassWear.element(false, "SHEETS", 8)));
        assertFalse(TerlGlassWear.element(false, "CONTROL_BAR", 7).equals("dock"));
    }

    @Test
    public void restoresOnlyAStoredGlassWhoseFrameMoved() {
        Object a = new Object();
        assertFalse(TerlGlassWear.restores(false, a, a));
        assertTrue(TerlGlassWear.restores(false, a, new Object()));
        assertTrue(TerlGlassWear.restores(false, null, a));
        assertFalse("Live draws the recorder's layers", TerlGlassWear.restores(true, null, a));
        assertNull("an opaque glass asks for nothing", TerlGlassWear.wants(Build.VERSION_CODES.S, false));
        assertEquals(GlassKind.LIVE, TerlGlassWear.wants(Build.VERSION_CODES.S, true));
        assertNull("below Android 12 the stored frame", TerlGlassWear.wants(Build.VERSION_CODES.R, true));
    }

    /** Lays {@code view} out at {@code left, top} and {@code size} in the window, as a pass would. */
    private View placed(int left, int top, int width, int height) {
        View view = new View(window.getContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = left;
        params.topMargin = top;
        window.addView(view, params);
        window.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        window.layout(0, 0, 400, 800);
        return view;
    }

    /** ADR 0029 section 4: a Terl sheet at rest covers a Live surface under it, as v1's drawer. */
    @Test
    public void aSheetAtRestFreesTheLiveSlotsOfWhatItHidesUntilItLeaves() {
        View widget = placed(40, 100, 100, 100);
        glass.register(new GlassEntry("widget", widget, GlassKind.LIVE, GlassEntry.PRIORITY_WIDGET,
            WindowLayer.PAGES, null), null);
        glass.setLiveOn(true);
        frames.flush("test");
        assertEquals(GlassKind.LIVE, glass.granted("widget"));

        View sheet = placed(0, 0, 400, 600);
        String element = TerlGlassWear.element(false, "SHEETS", System.identityHashCode(sheet));
        wear.wear(sheet, element, WindowLayer.SHEETS, 24f, true, new Counting());
        frames.flush("test");
        assertEquals("covered while the sheet rests over it", GlassKind.WALLPAPER,
            glass.granted("widget"));
        assertEquals("the sheet itself is Live", GlassKind.LIVE, glass.granted(element));

        window.removeView(sheet);
        frames.flush("test");
        assertEquals("the sheet gone, the widget is Live again", GlassKind.LIVE,
            glass.granted("widget"));
    }

    /**
     * Review P2: a hint wears the sheets' look but stands in the chrome layer; it is registered in
     * the layer it stands in, so it covers nothing a sheet would.
     */
    @Test
    public void aGlassIsRegisteredInTheLayerItStandsIn() {
        View widget = placed(40, 100, 100, 100);
        glass.register(new GlassEntry("widget", widget, GlassKind.LIVE, GlassEntry.PRIORITY_WIDGET,
            WindowLayer.PAGES, null), null);
        glass.setLiveOn(true);
        FrameLayout chrome = new FrameLayout(window.getContext());
        chrome.setId(com.termux.R.id.window_layer_chrome);
        window.addView(chrome, new FrameLayout.LayoutParams(400, 800));
        View hint = new View(window.getContext());
        chrome.addView(hint, new FrameLayout.LayoutParams(400, 600));
        window.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        window.layout(0, 0, 400, 800);
        String element = TerlGlassWear.element(false, "SHEETS", System.identityHashCode(hint));

        wear.wear(hint, element, WindowLayer.SHEETS, 24f, true, new Counting());
        frames.flush("test");

        assertEquals("the layer it stands in", WindowLayer.CHROME, glass.entry(element).layer);
        assertEquals("so it covers nothing", GlassKind.LIVE, glass.granted("widget"));
    }

    @Test
    public void theBarCoversNothing() {
        View widget = placed(40, 700, 100, 50);
        glass.register(new GlassEntry("widget", widget, GlassKind.LIVE, GlassEntry.PRIORITY_WIDGET,
            WindowLayer.PAGES, null), null);
        glass.setLiveOn(true);
        View bar = placed(0, 650, 400, 150);
        wear.wear(bar, TerlGlassWear.BAR_ELEMENT, WindowLayer.CHROME, 24f, true, new Counting());
        frames.flush("test");
        assertEquals(GlassKind.LIVE, glass.granted("widget"));
        assertFalse(TerlGlassWear.covers(WindowLayer.CHROME));
        assertTrue(TerlGlassWear.covers(WindowLayer.SHEETS));
    }

    @Test
    public void aSheetSlidingOpenIsCoveredWhereItComesToRest() {
        View sheet = placed(10, 300, 200, 100);
        sheet.setTranslationY(500f);
        android.graphics.Rect rest = new android.graphics.Rect();
        TerlGlassWear.restRectInWindow(sheet, rest);
        int[] window = new int[2];
        this.window.getLocationInWindow(window);
        assertEquals(new android.graphics.Rect(window[0] + 10, window[1] + 300, window[0] + 210,
            window[1] + 400), rest);
    }

    /** ADR 0033: the bar's Live picture through the program wears one rim, the shader's. */
    @Test
    public void aRefractedPictureLeavesTheRimToTheProgram() {
        ElementGlass global = ElementGlass.of(null, null, -1);
        GlassStack.Spec spec = SheetGlass.terlSpec(global, 24f, null, 12, 60);
        GlassStack.Spec drawn = TerlGlassWear.liveMaterialSpec(spec, true);
        assertFalse("the shader draws the rim: the material draws none", drawn.rim);
        GlassStack.Spec plain = TerlGlassWear.liveMaterialSpec(spec, false);
        assertEquals("a plain picture keeps the material's rim, as v1's panels", spec.rim, plain.rim);
    }
}
