package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.extrakeys.ExtraKeyModifiers;
import com.termux.app.terminal.extrakeys.ExtraKeysBarView;
import com.termux.app.terminal.extrakeys.ExtraKeysBarView.Transition;
import com.termux.app.terminal.extrakeys.ExtraKeysPageView;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.SpecialButton;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, application = android.app.Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ExtraKeySetControllerTest {

    private static final int WIDTH = 500;

    private ExtraKeysBarView bar;
    private ExtraKeysPageView edge;
    private final ExtraKeyModifiers modifiers = new ExtraKeyModifiers();
    private final Map<String, String> lines = new HashMap<>();
    private PaneWallPage place = PaneWallPage.TERMINAL;
    private boolean shown = true;
    private int edgeChanged;
    private ExtraKeySetController controller;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        activity.setContentView(root);
        bar = new ExtraKeysBarView(activity, null);
        bar.setModifiers(modifiers);
        root.addView(bar);
        bar.setRowMetrics(100, 1f);
        edge = new ExtraKeysPageView(activity, null);
        controller = new ExtraKeySetController(new ExtraKeySetController.Host() {
            @NonNull @Override public PaneWallPage settledPlace() { return place; }
            @Override public boolean homeFloatFocused() { return false; }
            @Override public boolean homeFloatIsDisplay() { return false; }
            @Nullable @Override public String rawLine(@NonNull String key) { return lines.get(key); }
            @Nullable @Override public ExtraKeysInfo parsePage(@NonNull String value,
                                                               @NonNull String fallback, int page) {
                return info(value);
            }
            @Nullable @Override public ExtraKeysBarView bar() { return bar; }
            @Nullable @Override public ExtraKeysPageView edgePage() { return edge; }
            @Override public boolean barShown() { return shown; }
            @NonNull @Override public ExtraKeyModifiers modifiers() { return modifiers; }
            @Override public void onEdgeKeysChanged() { edgeChanged++; }
        });
    }

    private void layout() {
        bar.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        bar.layout(0, 0, WIDTH, 300);
    }

    private static ExtraKeysInfo info(String rows) {
        try {
            return new ExtraKeysInfo(rows, ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
                ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void firstApplyIsAReloadAndAnUnchangedRefreshDoesNothing() {
        controller.refresh(true);
        assertEquals(Transition.RELOAD, controller.lastTransition());
        ExtraKeysPageView page = bar.keyPage(0);
        controller.refresh(true);
        assertSame(page, bar.keyPage(0));
    }

    @Test
    public void aTargetChangeClearsArmedModifiers() {
        controller.refresh(false);
        modifiers.tap(SpecialButton.CTRL);
        place = PaneWallPage.ASSISTANT;
        controller.refresh(false);
        assertEquals(ExtraKeyModifiers.State.OFF, modifiers.state(SpecialButton.CTRL));
    }

    @Test
    public void transitionsFollowMotionAndTheBarBeingShown() {
        controller.refresh(true);
        layout();
        ExtraKeysPageView front = bar.keyPage(0);
        place = PaneWallPage.ASSISTANT;
        controller.refresh(true);
        assertEquals(Transition.MORPH, controller.lastTransition());
        assertSame(front, bar.keyPage(0));

        place = PaneWallPage.TERMINAL;
        shown = false;
        controller.refresh(true);
        assertEquals(Transition.SWAP, controller.lastTransition());

        shown = true;
        place = PaneWallPage.ASSISTANT;
        controller.refresh(false);
        assertEquals(Transition.SWAP, controller.lastTransition());

        controller.reload();
        assertEquals(Transition.RELOAD, controller.lastTransition());
    }

    @Test
    public void pageTwoIsAbsentWhenEmptyAndPresentWhenItHasKeys() {
        lines.put("extra-keys", "[['A']]");
        lines.put("extra-keys2", "[]");
        controller.refresh(false);
        assertEquals(1, bar.keyPageCount());
        assertEquals(1, controller.pages().length);

        lines.put("extra-keys2", "[['B']]");
        controller.reload();
        assertEquals(2, bar.keyPageCount());
        assertEquals(2, controller.pages().length);
    }

    @Test
    public void theEdgePageGetsPageOneAndTheBarGetsTheScale() {
        lines.put("extra-keys", "[['A','B'],['C','D']]");
        lines.put("terminal-toolbar-height", "1.5");
        controller.refresh(false);
        assertEquals(2, edge.rowCount());
        assertEquals(1, edgeChanged);
        assertEquals(1.5f, bar.committedScale(), 0.001f);
        assertEquals(1.5f, controller.scale(), 0.001f);
        controller.reload();
        assertEquals(1, edgeChanged);
    }
}
