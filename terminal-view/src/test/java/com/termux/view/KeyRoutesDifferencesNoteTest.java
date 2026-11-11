package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.terminal.CapturingSession;
import com.termux.terminal.TerminalEmulator;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the note of every input the Fast engine now encodes differently from Standard (the Java
 * encoders) by running the key table and a mouse table through both, and fails when the committed
 * note is not what this run generates, so the note cannot drift from the code. Runs only with the
 * host library (-Pghostty=true).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class KeyRoutesDifferencesNoteTest {

    private static final Path COMMITTED = Paths.get("..", "project-docs", "reference", "terminal",
        "ghostty-parity-input-differences-2026-11-11.md");
    private static final Path GENERATED = Paths.get("build", "key-routes", "ghostty-parity-input-differences-2026-11-11.md");

    @After
    public void restore() {
        CapturingSession.inputPropertyForTests(null);
    }

    @Test
    public void theCommittedNoteIsWhatTheTablesGenerate() throws IOException {
        if (System.getProperty("termux.ghostty.libDir") == null) return;
        String generated = generate();
        Files.createDirectories(GENERATED.getParent());
        Files.write(GENERATED, generated.getBytes(StandardCharsets.UTF_8));
        assertTrue("the note is missing; copy terminal-view/build/key-routes/" + GENERATED.getFileName()
            + " to project-docs/reference/terminal/", Files.exists(COMMITTED));
        String committed = new String(Files.readAllBytes(COMMITTED), StandardCharsets.UTF_8);
        assertEquals("The input differences note is out of date. Regenerate it: run "
            + "./gradlew -Pghostty=true :terminal-view:testDebugUnitTest --tests '*KeyRoutesDifferencesNoteTest' "
            + "and copy terminal-view/build/key-routes/" + GENERATED.getFileName()
            + " to project-docs/reference/terminal/", committed, generated);
    }

    // ---- the mouse table ------------------------------------------------------------------------

    private static final String[] TRACKING = {"1000", "1002", "1003"};
    private static final String[] PROTOCOLS = {"default", "1006", "1006+1016"};

    private static String protocolEscapes(String protocol) {
        switch (protocol) {
            case "1006": return "\u001b[?1006h";
            case "1006+1016": return "\u001b[?1006h\u001b[?1016h";
            default: return "";
        }
    }

    private static final int CELL_W = 10, CELL_H = 20, COLUMN = 5, ROW = 3;
    private static final int PIXEL_X = (COLUMN - 1) * CELL_W + 4, PIXEL_Y = (ROW - 1) * CELL_H + 9;

    private static void mouse(TerminalEmulator e, int button, boolean pressed, int modifiers) {
        e.sendMouseEvent(button, COLUMN, ROW, PIXEL_X, PIXEL_Y, pressed, modifiers);
    }

    /** The mouse table through one engine. */
    private static List<KeyRouteTable.Result> runMouse(boolean fast) {
        List<KeyRouteTable.Result> results = new ArrayList<>();
        String[] mods = {"none", "shift", "ctrl"};
        int[] modBits = {0, TerminalEmulator.MOUSE_MODIFIER_SHIFT, TerminalEmulator.MOUSE_MODIFIER_CTRL};
        String[] names = {"left press", "left release", "middle press", "middle release", "right press",
            "right release", "left drag", "wheel up", "wheel down"};
        for (String tracking : TRACKING) {
            for (String protocol : PROTOCOLS) {
                CapturingSession session = new CapturingSession();
                TerminalEmulator e = session.attach(80, 24);
                if (fast && !e.useGhosttyCore()) throw new IllegalStateException("library did not load");
                String escapes = "\u001b[?" + tracking + "h" + protocolEscapes(protocol);
                byte[] bytes = escapes.getBytes(StandardCharsets.UTF_8);
                e.append(bytes, bytes.length);
                session.drain();
                for (int m = 0; m < mods.length; m++) {
                    for (String name : names) {
                        switch (name) {
                            case "left press": mouse(e, 0, true, modBits[m]); break;
                            case "middle press": mouse(e, 1, true, modBits[m]); break;
                            case "right press": mouse(e, 2, true, modBits[m]); break;
                            case "left release": mouse(e, 0, true, modBits[m]); session.drain(); mouse(e, 0, false, modBits[m]); break;
                            case "middle release": mouse(e, 1, true, modBits[m]); session.drain(); mouse(e, 1, false, modBits[m]); break;
                            case "right release": mouse(e, 2, true, modBits[m]); session.drain(); mouse(e, 2, false, modBits[m]); break;
                            case "left drag":
                                mouse(e, 0, true, modBits[m]);
                                session.drain();
                                e.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, COLUMN + 1, ROW,
                                    PIXEL_X + CELL_W, PIXEL_Y, true, modBits[m]);
                                break;
                            case "wheel up": mouse(e, TerminalEmulator.MOUSE_WHEELUP_BUTTON, true, modBits[m]); break;
                            default: mouse(e, TerminalEmulator.MOUSE_WHEELDOWN_BUTTON, true, modBits[m]); break;
                        }
                        String out = session.drain();
                        // Put the buttons back up for the next row.
                        if (name.equals("left press") || name.equals("left drag")) { mouse(e, 0, false, modBits[m]); session.drain(); }
                        if (name.equals("middle press")) { mouse(e, 1, false, modBits[m]); session.drain(); }
                        if (name.equals("right press")) { mouse(e, 2, false, modBits[m]); session.drain(); }
                        results.add(new KeyRouteTable.Result("mouse", name, mods[m],
                            "tracking " + tracking + ", protocol " + protocol, out));
                    }
                }
            }
        }
        return results;
    }

    // ---- the note ---------------------------------------------------------------------------

    static String generate() {
        List<KeyRouteTable.Result> javaKeys = KeyRouteTable.run(false, null);
        List<KeyRouteTable.Result> libraryKeys = KeyRouteTable.run(true, null);
        List<KeyRouteTable.Result> javaMouse = runMouse(false);
        List<KeyRouteTable.Result> libraryMouse = runMouse(true);
        assertEquals(javaKeys.size(), libraryKeys.size());
        assertEquals(javaMouse.size(), libraryMouse.size());

        // route -> key -> rows, in table order
        Map<String, Map<String, List<String>>> groups = new LinkedHashMap<>();
        int compared = 0, differing = 0;
        for (int pass = 0; pass < 2; pass++) {
            List<KeyRouteTable.Result> a = pass == 0 ? javaKeys : javaMouse;
            List<KeyRouteTable.Result> b = pass == 0 ? libraryKeys : libraryMouse;
            for (int i = 0; i < a.size(); i++) {
                KeyRouteTable.Result j = a.get(i), l = b.get(i);
                assertEquals(j.id(), l.id());
                compared++;
                if (j.bytes.equals(l.bytes)) continue;
                differing++;
                String marker = j.bytes.isEmpty() ? "only the library sends" : l.bytes.isEmpty() ? "only Java sends" : "";
                groups.computeIfAbsent(j.route, r -> new LinkedHashMap<>())
                    .computeIfAbsent(j.key, k -> new ArrayList<>())
                    .add("| " + j.route + " | " + j.key + " + " + j.mods + " | " + j.state + " | " + cell(j.bytes)
                        + " | " + cell(l.bytes) + " | " + marker + " |");
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("# Fast engine input bytes against Standard's (2026-11-11)\n\n");
        out.append("Every keyboard route and mouse event the Fast engine now encodes with libghostty-vt's encoders, set ")
            .append("against what the Java encoders write. Generated by `KeyRoutesDifferencesNoteTest` in terminal-view, which ")
            .append("fails when this file is not what the tables generate; regenerate it as that test says.\n\n");
        out.append("- **Java** is Standard today, and what the Fast engine wrote before libghostty took over the keys ")
            .append("(`debug.tl.terminal_input=java` brings it back). **library** is the Fast engine now.\n");
        out.append("- Bytes are shown as the program reads them: `\\e` is ESC (0x1b), `^X` is the control character Ctrl+X ")
            .append("(`^M` is Enter's 0x0d, `^@` is NUL), `\\x7f` is DEL, `\\xNN` is any byte above 0x7e, ")
            .append("`\\\\`, `\\^` and `\\|` are a literal backslash, caret and bar.\n");
        out.append("- Keys are driven through the view's real entry points (hardware key down, repeat and release, extra-keys ")
            .append("button, in-app keyboard code point, IME text, raw characters, alternate scroll's `handleKeyCode`); ")
            .append("mouse rows call `sendMouseEvent` at column 5, row 3. Terminal state is the kitty flags pushed with ")
            .append("`CSI > n u`, DECCKM (cursor keys application) and DECKPAM (keypad application) for keys, and the mouse ")
            .append("tracking mode and protocol for the mouse. Rows where both engines write the same bytes are left out.\n");
        out.append("- A row marked \"only Java sends\" or \"only the library sends\" is one side writing nothing.\n\n");
        out.append(differing).append(" of ").append(compared).append(" rows differ.\n");

        for (Map.Entry<String, Map<String, List<String>>> route : groups.entrySet()) {
            out.append("\n## ").append(route.getKey()).append("\n");
            for (Map.Entry<String, List<String>> key : route.getValue().entrySet()) {
                out.append("\n### ").append(key.getKey()).append("\n\n");
                out.append("| route | key + mods | terminal state | Java bytes | library bytes | |\n");
                out.append("|---|---|---|---|---|---|\n");
                for (String row : key.getValue()) out.append(row).append("\n");
            }
        }
        return out.toString();
    }

    private static String cell(String bytes) {
        if (bytes.isEmpty()) return "(nothing)";
        return "`" + KeyRouteTable.printable(bytes).replace("`", "\\x60") + "`";
    }
}
