package com.termux.view;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A pane's terminal never draws outside its own view (ADR 0019): with its size held and a grid
 * far larger than the pane that shrank under it, every draw of the frame happens inside a clip to
 * the view's bounds, glass or not, travel or not. The frame around the view lets its children draw
 * outside on purpose, so this clip is the only thing keeping one pane's text off its neighbour.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TerminalViewClipToFrameTest {

    private static final int W = 200;
    private static final int H = 150;

    @Test
    public void aHeldOverflowingGridDrawsOnlyInsideTheView() {
        TerminalView view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        view.setTextSize(24);
        view.setTerminalSizeUpdatesPaused(true);
        view.layout(0, 0, W, H);
        TerminalEmulator emulator = new TerminalEmulator(new TerminalOutput() {
            @Override public void write(byte[] data, int offset, int count) { }
            @Override public void titleChanged(String oldTitle, String newTitle) { }
            @Override public void onCopyTextToClipboard(String text) { }
            @Override public void onPasteTextFromClipboard() { }
            @Override public void onBell() { }
            @Override public void onColorsChanged() { }
        }, false, 80, 40, 10, 20, 200, null);
        view.setEmulatorForTest(emulator);
        StringBuilder text = new StringBuilder();
        for (int row = 0; row < 40; row++) {
            text.append("\u001b[4").append(row % 8).append('m');
            for (int c = 0; c < 80; c++) text.append((char) ('A' + (row + c) % 26));
            if (row < 39) text.append("\r\n");
        }
        byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, bytes.length);

        RecordingCanvas canvas = new RecordingCanvas(Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888));
        view.onDraw(canvas);

        assertTrue("the grid drew something", canvas.firstDraw >= 0);
        int clip = -1;
        for (int i = 0; i < canvas.events.size(); i++) {
            if (canvas.events.get(i).equals("clip 0 0 " + W + " " + H)) { clip = i; break; }
        }
        assertTrue("clipped to the view before drawing: " + canvas.events, clip >= 0 && clip < canvas.firstDraw);
        // The save the clip sits in is restored only after the last draw.
        int depth = 0;
        int clipDepth = -1;
        int restoreOfClip = Integer.MAX_VALUE;
        for (int i = 0; i < canvas.events.size(); i++) {
            String e = canvas.events.get(i);
            if (e.equals("save")) depth++;
            else if (e.equals("restore")) {
                if (depth == clipDepth && i > clip) { restoreOfClip = i; break; }
                depth--;
            }
            if (i == clip) clipDepth = depth;
        }
        assertTrue("the clip holds through the last draw: " + canvas.events,
            clipDepth > 0 && restoreOfClip > canvas.lastDraw);
    }

    /** Records saves, clips and draws in order, so the test reads which clip a draw was under. */
    private static final class RecordingCanvas extends Canvas {
        final List<String> events = new ArrayList<>();
        int firstDraw = -1;
        int lastDraw = -1;

        RecordingCanvas(Bitmap bitmap) { super(bitmap); }

        private void draw() {
            if (firstDraw < 0) firstDraw = events.size();
            lastDraw = events.size();
            events.add("draw");
        }

        @Override public int save() { events.add("save"); return super.save(); }
        @Override public void restore() { events.add("restore"); super.restore(); }
        @Override public boolean clipRect(float l, float t, float r, float b) {
            events.add("clip " + (int) l + " " + (int) t + " " + (int) r + " " + (int) b);
            return super.clipRect(l, t, r, b);
        }
        @Override public boolean clipRect(int l, int t, int r, int b) {
            events.add("clip " + l + " " + t + " " + r + " " + b);
            return super.clipRect(l, t, r, b);
        }
        @Override public void drawRect(float l, float t, float r, float b, Paint p) { draw(); super.drawRect(l, t, r, b, p); }
        @Override public void drawRect(RectF rect, Paint p) { draw(); super.drawRect(rect, p); }
        @Override public void drawColor(int color) { draw(); super.drawColor(color); }
        @Override public void drawText(char[] t, int i, int c, float x, float y, Paint p) { draw(); super.drawText(t, i, c, x, y, p); }
        @Override public void drawText(String t, float x, float y, Paint p) { draw(); super.drawText(t, x, y, p); }
        @Override public void drawText(String t, int s, int e, float x, float y, Paint p) { draw(); super.drawText(t, s, e, x, y, p); }
        @Override public void drawTextRun(char[] t, int i, int c, int ci, int cc, float x, float y, boolean rtl, Paint p) {
            draw(); super.drawTextRun(t, i, c, ci, cc, x, y, rtl, p);
        }
        @Override public void drawBitmap(Bitmap b, float l, float t, Paint p) { draw(); super.drawBitmap(b, l, t, p); }
    }
}
