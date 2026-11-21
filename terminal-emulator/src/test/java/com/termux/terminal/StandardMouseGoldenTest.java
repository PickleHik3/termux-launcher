package com.termux.terminal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * The bytes the Standard (Java core) emulator writes for mouse events, recorded from the code
 * before the library encoders were wired in. Standard must keep writing exactly these.
 * Regenerate with {@code TERMUX_MOUSE_GOLDEN_OUT=<file>} set.
 */
public class StandardMouseGoldenTest extends TerminalTestCase {

    static final String GOLDEN_RESOURCE = "mouse-golden.txt";

    static final int COLUMNS = 250;
    static final int ROWS = 20;

    static final int[] BUTTONS = {
        TerminalEmulator.MOUSE_LEFT_BUTTON, 1, 2,
        TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, TerminalEmulator.MOUSE_MOVED_NO_BUTTON,
        TerminalEmulator.MOUSE_WHEELUP_BUTTON, TerminalEmulator.MOUSE_WHEELDOWN_BUTTON,
        TerminalEmulator.MOUSE_WHEEL_LEFT, TerminalEmulator.MOUSE_WHEEL_RIGHT};

    static final int[] MODIFIERS = {0, TerminalEmulator.MOUSE_MODIFIER_SHIFT, TerminalEmulator.MOUSE_MODIFIER_ALT,
        TerminalEmulator.MOUSE_MODIFIER_CTRL,
        TerminalEmulator.MOUSE_MODIFIER_SHIFT | TerminalEmulator.MOUSE_MODIFIER_CTRL};

    /** Tracking modes as DECSET numbers; 0 is none. Java has no mode 9. */
    static final int[] TRACKINGS_JAVA = {0, 1000, 1002, 1003};
    static final int[] TRACKINGS_ALL = {0, 9, 1000, 1002, 1003};

    /** Format as the DECSET numbers set after the tracking mode. */
    static final int[][] FORMATS_JAVA = {{}, {1006}, {1006, 1016}};
    static final int[][] FORMATS_ALL = {{}, {1005}, {1006}, {1015}, {1016}, {1006, 1016}};

    /** One interior cell with a pixel inside it, then a cell beyond column 223 (the X10 limit). */
    static final int[][] POSITIONS = {
        {5, 3, 4 * INITIAL_CELL_WIDTH_PIXELS + 7, 2 * INITIAL_CELL_HEIGHT_PIXELS + 9},
        {240, 10, 239 * INITIAL_CELL_WIDTH_PIXELS + 3, 9 * INITIAL_CELL_HEIGHT_PIXELS + 14}};

    static String buttonName(int b) {
        switch (b) {
            case 0: return "left";
            case 1: return "middle";
            case 2: return "right";
            case TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED: return "left-moved";
            case TerminalEmulator.MOUSE_MOVED_NO_BUTTON: return "moved";
            case TerminalEmulator.MOUSE_WHEELUP_BUTTON: return "wheel-up";
            case TerminalEmulator.MOUSE_WHEELDOWN_BUTTON: return "wheel-down";
            case TerminalEmulator.MOUSE_WHEEL_LEFT: return "wheel-left";
            default: return "wheel-right";
        }
    }

    /** A new emulator of the table's size with the tracking mode and format set by DECSET. */
    static TerminalEmulator newEmulator(TerminalOutput output, int tracking, int[] format) {
        TerminalEmulator emulator = new TerminalEmulator(output, false, COLUMNS, ROWS,
            INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS, ROWS * 2, null);
        configure(emulator, tracking, format);
        return emulator;
    }

    static void configure(TerminalEmulator emulator, int tracking, int[] format) {
        StringBuilder sb = new StringBuilder();
        if (tracking != 0) sb.append("\033[?").append(tracking).append('h');
        for (int mode : format) sb.append("\033[?").append(mode).append('h');
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, bytes.length);
    }

    static String printable(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte value : bytes) {
            int c = value & 0xff;
            if (c == 0x1b) sb.append("\\e");
            else if (c >= 0x20 && c < 0x7f && c != '\\') sb.append((char) c);
            else sb.append(String.format("\\x%02x", c));
        }
        return sb.toString();
    }

    static String modName(int mods) {
        if (mods == 0) return "-";
        StringBuilder sb = new StringBuilder();
        if ((mods & TerminalEmulator.MOUSE_MODIFIER_SHIFT) != 0) sb.append('S');
        if ((mods & TerminalEmulator.MOUSE_MODIFIER_ALT) != 0) sb.append('A');
        if ((mods & TerminalEmulator.MOUSE_MODIFIER_CTRL) != 0) sb.append('C');
        return sb.toString();
    }

    static String label(int tracking, int[] format, int button, boolean pressed, int mods, int pos) {
        StringBuilder label = new StringBuilder("t=").append(tracking).append(" f=");
        for (int i = 0; i < format.length; i++) label.append(i == 0 ? "" : "+").append(format[i]);
        if (format.length == 0) label.append("none");
        return label.append(' ').append(buttonName(button)).append(pressed ? " down " : " up ")
            .append(modName(mods)).append(" pos").append(pos).toString();
    }

    /** The bytes written for one table row. */
    static byte[] send(MockTerminalOutput out, TerminalEmulator emulator, int button, boolean pressed,
                       int mods, int pos) {
        out.baos.reset();
        int[] p = POSITIONS[pos];
        emulator.sendMouseEvent(button, p[0], p[1], p[2], p[3], pressed, mods);
        return out.baos.toByteArray();
    }

    static List<String> record() {
        List<String> lines = new ArrayList<>();
        for (int tracking : TRACKINGS_JAVA) {
            for (int[] format : FORMATS_JAVA) {
                MockTerminalOutput out = new MockTerminalOutput();
                TerminalEmulator emulator = newEmulator(out, tracking, format);
                for (int button : BUTTONS) {
                    for (boolean pressed : new boolean[] {true, false}) {
                        for (int mods : MODIFIERS) {
                            for (int pos = 0; pos < POSITIONS.length; pos++) {
                                lines.add(label(tracking, format, button, pressed, mods, pos) + " -> "
                                    + printable(send(out, emulator, button, pressed, mods, pos)));
                            }
                        }
                    }
                }
            }
        }
        return lines;
    }

    public void testStandardMouseBytesMatchTheRecording() throws IOException {
        String writeTo = System.getenv("TERMUX_MOUSE_GOLDEN_OUT");
        List<String> now = record();
        if (writeTo != null && !writeTo.isEmpty()) {
            Files.write(Paths.get(writeTo), (String.join("\n", now) + "\n").getBytes(StandardCharsets.UTF_8));
            return;
        }
        InputStream stream = StandardMouseGoldenTest.class.getResourceAsStream(GOLDEN_RESOURCE);
        assertNotNull("missing test resource " + GOLDEN_RESOURCE, stream);
        List<String> golden = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) golden.add(line);
        }
        assertEquals("rows", golden.size(), now.size());
        for (int i = 0; i < now.size(); i++) assertEquals("row " + i, golden.get(i), now.get(i));
    }
}
