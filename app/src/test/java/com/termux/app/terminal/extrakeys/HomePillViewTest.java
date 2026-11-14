package com.termux.app.terminal.extrakeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysClient;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** The Home pill's size, its tap, hold-to-pick-up, drag and drop, and the layer that places it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, application = android.app.Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class HomePillViewTest {

    private static final String THREE = "[['A','B','C']]";

    private android.content.Context context;
    private float density;
    private int slop;
    private HomePillLayer layer;
    private HomePillView pill;
    private RecordingClient client;
    private final List<String> events = new ArrayList<>();
    private final float[] dropped = new float[2];

    @Before
    public void setUp() throws Exception {
        context = new android.view.ContextThemeWrapper(RuntimeEnvironment.getApplication(),
            com.google.android.material.R.style.Theme_Material3_DayNight);
        density = context.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        client = new RecordingClient();
        layer = new HomePillLayer(context);
        pill = layer.pill();
        pill.pageView().setClient(client);
        pill.setKeys(info(THREE), 1f);
        layer.setHost(new HomePillLayer.Host() {
            @Override
            public void onPillDropped(float left, float top) {
                events.add("dropped");
                dropped[0] = left;
                dropped[1] = top;
            }

            @Override
            public void onEditKeys() {
                events.add("edit");
            }
        });
        layoutLayer(1000, 2000);
        layer.placePill(100f, 200f);
    }

    // ---- Size.

    @Test
    public void floatingWrapsThreeKeys() {
        assertEquals(Math.round((8f + 3 * 48f) * density), pill.getWidth());
        assertEquals(Math.round(46f * density), pill.getHeight());
    }

    @Test
    public void scaleGrowsTheWrap() throws Exception {
        pill.setKeys(info(THREE), 1.5f);
        layoutLayer(1000, 2000);
        assertEquals(Math.round((8f + 3 * 48f * 1.5f) * density), pill.getWidth());
        assertEquals(Math.round(46f * 1.5f * density), pill.getHeight());
    }

    @Test
    public void dockedSizeSpreadsTheKeysAndClearGoesBack() {
        pill.setDockedSize(900, 120);
        layoutLayer(1000, 2000);
        assertEquals(900, pill.getWidth());
        assertEquals(120, pill.getHeight());
        pill.clearDockedSize();
        layoutLayer(1000, 2000);
        assertEquals(Math.round((8f + 3 * 48f) * density), pill.getWidth());
    }

    @Test
    public void sameKeysAndScaleAskForNoLayout() throws Exception {
        ExtraKeysInfo keys = info(THREE);
        pill.setKeys(keys, 1f);
        layoutLayer(1000, 2000);
        assertFalse(pill.isLayoutRequested());
        pill.setKeys(keys, 1f);
        assertFalse(pill.isLayoutRequested());
        pill.setDockedSize(900, 120);
        layoutLayer(1000, 2000);
        pill.setDockedSize(900, 120);
        assertFalse(pill.isLayoutRequested());
    }

    @Test
    public void dockedPageActsOnHoldAndFloatingDoesNot() throws Exception {
        pill.setKeys(info("[['UP','B','C']]"), 1f);
        layoutLayer(1000, 2000);
        // A held key repeats only where the page acts on a hold: docked.
        pill.setFloating(false);
        long down = SystemClock.uptimeMillis();
        sendToPill(MotionEvent.ACTION_DOWN, down, keyX(0), keyY());
        ShadowLooper.idleMainLooper(1500, TimeUnit.MILLISECONDS);
        assertFalse(client.clicked.isEmpty());
        sendToPill(MotionEvent.ACTION_CANCEL, down, keyX(0), keyY());
        client.clicked.clear();

        pill.setFloating(true);
        down = SystemClock.uptimeMillis();
        sendToPill(MotionEvent.ACTION_DOWN, down, keyX(0), keyY());
        ShadowLooper.idleMainLooper(1500, TimeUnit.MILLISECONDS);
        assertTrue(client.clicked.isEmpty());
    }

    // ---- Touch on the pill.

    @Test
    public void aTapFiresTheKey() {
        long down = SystemClock.uptimeMillis();
        sendToPill(MotionEvent.ACTION_DOWN, down, keyX(1), keyY());
        sendToPill(MotionEvent.ACTION_UP, down, keyX(1), keyY());
        assertEquals(List.of("B"), client.clicked);
    }

    @Test
    public void aHoldPicksUpAndTheKeyFiresNothing() {
        long down = SystemClock.uptimeMillis();
        sendToPill(MotionEvent.ACTION_DOWN, down, keyX(1), keyY());
        ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout() + 50L,
            TimeUnit.MILLISECONDS);
        assertTrue(pill.isPickedUp());
        sendToPill(MotionEvent.ACTION_UP, down, keyX(1), keyY());
        assertTrue(client.clicked.isEmpty());
        assertFalse(pill.isPickedUp());
    }

    @Test
    public void aHoldBetweenKeysPicksUp() {
        long down = SystemClock.uptimeMillis();
        // The inset left of the first key belongs to the pill, not to a key.
        sendToPill(MotionEvent.ACTION_DOWN, down, 2f * density, keyY());
        ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout() + 50L,
            TimeUnit.MILLISECONDS);
        assertTrue(pill.isPickedUp());
        sendToPill(MotionEvent.ACTION_UP, down, 2f * density, keyY());
        assertTrue(client.clicked.isEmpty());
    }

    @Test
    public void movingPastTheSlopBeforeTheHoldPicksNothingUp() {
        long down = SystemClock.uptimeMillis();
        sendToPill(MotionEvent.ACTION_DOWN, down, keyX(1), keyY());
        sendToPill(MotionEvent.ACTION_MOVE, down, keyX(1) + slop * 3f, keyY());
        ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout() + 50L,
            TimeUnit.MILLISECONDS);
        assertFalse(pill.isPickedUp());
        sendToPill(MotionEvent.ACTION_UP, down, keyX(1) + slop * 3f, keyY());
    }

    @Test
    public void aDownWhileDockedIsNotPickedUp() {
        pill.setFloating(false);
        long down = SystemClock.uptimeMillis();
        sendToPill(MotionEvent.ACTION_DOWN, down, 2f * density, keyY());
        ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout() + 50L,
            TimeUnit.MILLISECONDS);
        assertFalse(pill.isPickedUp());
    }

    // ---- Drag and drop through the layer.

    @Test
    public void dragIsClampedToTheAreaAndDropReportsTheSpot() {
        layer.setArea(HomePillSpot.area(1000f, 2000f, 0f, 0f, 0f, 0f, 16f, 0f, 2000f, null, 12f));
        long down = SystemClock.uptimeMillis();
        float x = layer.pill().getX() + keyX(1);
        float y = layer.pill().getY() + keyY();
        sendToLayer(MotionEvent.ACTION_DOWN, down, x, y);
        ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout() + 50L,
            TimeUnit.MILLISECONDS);
        assertTrue(pill.isPickedUp());
        assertEquals(View.VISIBLE, layer.chip().getVisibility());
        // Far past the window's left and top edges.
        sendToLayer(MotionEvent.ACTION_MOVE, down, x - 5000f, y - 5000f);
        sendToLayer(MotionEvent.ACTION_MOVE, down, x - 5000f, y - 5000f);
        assertEquals(16f, layer.pill().getTranslationX(), 0.5f);
        assertEquals(16f, layer.pill().getTranslationY(), 0.5f);
        sendToLayer(MotionEvent.ACTION_UP, down, x - 5000f, y - 5000f);
        assertEquals(List.of("dropped"), events);
        assertEquals(16f, dropped[0], 0.5f);
        assertEquals(16f, dropped[1], 0.5f);
        assertTrue(client.clicked.isEmpty());
    }

    // ---- The layer.

    @Test
    public void aTouchOutsideThePillPassesThrough() {
        assertFalse(sendToLayer(MotionEvent.ACTION_DOWN, SystemClock.uptimeMillis(), 900f, 1500f));
        assertFalse(layer.claimsDown(900f, 1500f));
    }

    @Test
    public void aTouchOnThePillIsClaimed() {
        float x = layer.pill().getX() + keyX(0);
        float y = layer.pill().getY() + keyY();
        assertTrue(layer.claimsDown(x, y));
        long down = SystemClock.uptimeMillis();
        assertTrue(sendToLayer(MotionEvent.ACTION_DOWN, down, x, y));
        sendToLayer(MotionEvent.ACTION_UP, down, x, y);
        assertEquals(List.of("A"), client.clicked);
    }

    @Test
    public void aHiddenPillClaimsNothing() {
        layer.setPillShown(false);
        assertFalse(layer.claimsDown(layer.pill().getX() + 10f, layer.pill().getY() + 10f));
    }

    @Test
    public void theChipHidesOnATouchOutsideAndOpensTheEditorOnATap() {
        layer.showChip(300f, 600f);
        long down = SystemClock.uptimeMillis();
        float cx = 300f + layer.chip().getWidth() / 2f;
        float cy = 600f + layer.chip().getHeight() / 2f;
        assertTrue(sendToLayer(MotionEvent.ACTION_DOWN, down, cx, cy));
        sendToLayer(MotionEvent.ACTION_CANCEL, down, cx, cy);
        // The layer is not attached here, so a posted click would never run; click it directly.
        layer.chip().performClick();
        assertEquals(List.of("edit"), events);
        assertEquals(View.INVISIBLE, layer.chip().getVisibility());

        layer.showChip(300f, 600f);
        assertFalse(sendToLayer(MotionEvent.ACTION_DOWN, SystemClock.uptimeMillis(), 900f, 1500f));
        assertEquals(View.INVISIBLE, layer.chip().getVisibility());
    }

    // ---- Helpers.

    private ExtraKeysInfo info(String rows) throws Exception {
        return new ExtraKeysInfo(rows, ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
            ExtraKeysConstants.CONTROL_CHARS_ALIASES);
    }

    private void layoutLayer(int w, int h) {
        layer.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        layer.layout(0, 0, w, h);
    }

    /** The centre of key {@code index} in the pill's own coordinates. */
    private float keyX(int index) {
        View cap = pill.pageView().getChildAt(index);
        return pill.pageView().getLeft() + cap.getLeft() + cap.getWidth() / 2f;
    }

    private float keyY() {
        return pill.getHeight() / 2f;
    }

    private boolean sendToPill(int action, long downTime, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0);
        boolean handled = pill.dispatchTouchEvent(event);
        event.recycle();
        return handled;
    }

    private boolean sendToLayer(int action, long downTime, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0);
        boolean handled = layer.dispatchTouchEvent(event);
        event.recycle();
        return handled;
    }

    private static final class RecordingClient implements ExtraKeysClient {
        final List<String> clicked = new ArrayList<>();

        @Override
        public void onExtraKeyClick(View view, ExtraKeyButton key) {
            clicked.add(key.getKey());
        }

        @Override
        public boolean performExtraKeyHapticFeedback(View view, ExtraKeyButton key) {
            return true;
        }
    }
}
