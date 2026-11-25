package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.view.MotionEvent;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The numbers ILinuxWindows carries are mirrored by hand in wayland-host/src/protocol.rs; this
 * fails when the two drift (the crate's own protocol test checks the other direction).
 */
public class LinuxWindowsProtocolTest {

    /** Constants with these prefixes are read by the host and must be mirrored. */
    private static final String[] MIRRORED_PREFIXES = {"TOUCH_", "POINTER_", "BTN_", "WINDOW_", "MAX_"};
    private static final Pattern RUST_CONST = Pattern.compile("^pub const ([A-Z0-9_]+): i32 = (-?(?:0x[0-9A-Fa-f]+|\\d+));", Pattern.MULTILINE);

    @Test
    public void everyRustConstantMatchesTheInterface() throws Exception {
        Map<String, Integer> rust = rustConstants();
        assertTrue("no constants parsed from protocol.rs", rust.size() > 10);
        for (Map.Entry<String, Integer> e : rust.entrySet()) {
            Field field = ILinuxWindows.class.getField(e.getKey());
            assertEquals(e.getKey() + " differs between ILinuxWindows.aidl and protocol.rs",
                (int) e.getValue(), field.getInt(null));
        }
    }

    @Test
    public void everyConstantTheHostReadsIsMirrored() throws Exception {
        Map<String, Integer> rust = rustConstants();
        for (Field field : ILinuxWindows.class.getFields()) {
            if (field.getType() != int.class || !Modifier.isStatic(field.getModifiers())) continue;
            if (!mirrored(field.getName())) continue;
            assertEquals(field.getName() + " is not mirrored in protocol.rs",
                Integer.valueOf(field.getInt(null)), rust.get(field.getName()));
        }
    }

    @Test
    public void touchActionsAreMotionEventActions() {
        assertEquals(MotionEvent.ACTION_DOWN, ILinuxWindows.TOUCH_DOWN);
        assertEquals(MotionEvent.ACTION_UP, ILinuxWindows.TOUCH_UP);
        assertEquals(MotionEvent.ACTION_MOVE, ILinuxWindows.TOUCH_MOVE);
        assertEquals(MotionEvent.ACTION_CANCEL, ILinuxWindows.TOUCH_CANCEL);
        assertEquals(MotionEvent.ACTION_POINTER_DOWN, ILinuxWindows.TOUCH_POINTER_DOWN);
        assertEquals(MotionEvent.ACTION_POINTER_UP, ILinuxWindows.TOUCH_POINTER_UP);
    }

    private static boolean mirrored(String name) {
        for (String prefix : MIRRORED_PREFIXES) if (name.startsWith(prefix)) return true;
        return false;
    }

    private static Map<String, Integer> rustConstants() throws IOException {
        String text = new String(Files.readAllBytes(protocolSource()), StandardCharsets.UTF_8);
        Map<String, Integer> out = new LinkedHashMap<>();
        Matcher m = RUST_CONST.matcher(text);
        while (m.find()) out.put(m.group(1), Integer.decode(m.group(2)));
        return out;
    }

    private static Path protocolSource() {
        for (String candidate : new String[]{"../wayland-host/src/protocol.rs", "wayland-host/src/protocol.rs"}) {
            Path path = Paths.get(candidate);
            if (Files.isRegularFile(path)) return path;
        }
        throw new AssertionError("wayland-host/src/protocol.rs not found from " + Paths.get("").toAbsolutePath());
    }
}
