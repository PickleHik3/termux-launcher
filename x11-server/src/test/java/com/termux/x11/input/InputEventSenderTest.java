package com.termux.x11.input;

import static org.junit.Assert.assertEquals;

import android.graphics.Matrix;
import android.view.MotionEvent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class InputEventSenderTest {
    private static final int BEGIN = 18, END = 20;

    /** Begin and End calls only, as "action:id"; the Updates between them are not under test. */
    private final List<String> wire = new ArrayList<>();
    private final RenderData render = new RenderData();
    private InputEventSender sender;

    @Before
    public void setUp() {
        render.screenWidth = 1000;
        render.screenHeight = 1000;
        render.setInputTransform(new Matrix());
        InputStub stub = (InputStub) Proxy.newProxyInstance(InputStub.class.getClassLoader(),
            new Class<?>[]{InputStub.class}, (proxy, m, args) -> {
                if (m.getName().equals("sendTouchEvent") && ((int) args[0] == BEGIN || (int) args[0] == END))
                    wire.add(args[0] + ":" + args[1]);
                return null;
            });
        sender = new InputEventSender(null, stub);
    }

    private void send(int action, int... ids) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[ids.length];
        for (int i = 0; i < ids.length; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = ids[i];
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = 10 + 10 * ids[i];
            coords[i].y = 20;
        }
        MotionEvent e = MotionEvent.obtain(0, 0, action, ids.length, props, coords, 0, 0, 1, 1, 0, 0, 0, 0);
        sender.sendTouchEvent(e, render);
        e.recycle();
    }

    private static int pointerDown(int index) {
        return MotionEvent.ACTION_POINTER_DOWN | (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
    }

    private static String begin(int id) { return BEGIN + ":" + id; }
    private static String end(int id) { return END + ":" + id; }

    private void twoFingersMoving() {
        send(MotionEvent.ACTION_DOWN, 0);
        send(pointerDown(1), 0, 1);
        send(MotionEvent.ACTION_MOVE, 0, 1);
        wire.clear();
    }

    @Test
    public void cancelWithTwoPointersEndsBoth() {
        twoFingersMoving();
        send(MotionEvent.ACTION_CANCEL, 0, 1);
        assertEquals(List.of(end(0), end(1)), wire);
    }

    @Test
    public void downAfterLostUpEndsStaleTouchBeforeBegin() {
        twoFingersMoving();
        send(MotionEvent.ACTION_DOWN, 0);
        assertEquals(List.of(end(0), end(1), begin(0)), wire);
    }

    @Test
    public void moveEndsTouchTheEventLacks() {
        twoFingersMoving();
        // The POINTER_UP for id 1 was lost: the next move carries only id 0.
        send(MotionEvent.ACTION_MOVE, 0);
        assertEquals(List.of(end(1)), wire);
        // Nothing is held for id 1 any more.
        send(MotionEvent.ACTION_UP, 0);
        assertEquals(List.of(end(1), end(0)), wire);
    }

    @Test
    public void lostPointerUpThenNewGestureLeavesNothingDown() {
        twoFingersMoving();
        send(MotionEvent.ACTION_DOWN, 0);
        send(MotionEvent.ACTION_MOVE, 0);
        send(MotionEvent.ACTION_UP, 0);
        assertEquals(List.of(end(0), end(1), begin(0), end(0)), wire);
        for (boolean held : sender.down)
            assertEquals(false, held);
    }

    @Test
    public void plainTapIsBeginEnd() {
        send(MotionEvent.ACTION_DOWN, 0);
        send(MotionEvent.ACTION_UP, 0);
        assertEquals(List.of(begin(0), end(0)), wire);
    }
}
