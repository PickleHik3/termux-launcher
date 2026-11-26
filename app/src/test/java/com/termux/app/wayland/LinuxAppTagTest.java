package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LinuxAppTagTest {

    @Test
    public void anAppTagReadsBackItsKey() {
        LinuxAppTag.Parsed p = LinuxAppTag.parse(LinuxAppTag.app("org.xfce.mousepad"));
        assertFalse(p.desktop);
        assertEquals("org.xfce.mousepad", p.key);
        assertEquals(-1, p.display);
    }

    /** Keys may hold the separator (a container app's id); the display is read before it. */
    @Test
    public void aDesktopTagReadsBackItsDisplayAndKey() {
        LinuxAppTag.Parsed p = LinuxAppTag.parse(LinuxAppTag.desktop(2, "session|xfce4"));
        assertTrue(p.desktop);
        assertEquals(2, p.display);
        assertEquals("session|xfce4", p.key);
        assertEquals("a|b", LinuxAppTag.parse(LinuxAppTag.app("a|b")).key);
    }

    /** #409: a Wayland desktop has no X display; its tag says it is one and names its key. */
    @Test
    public void aWaylandDesktopTagReadsBackAsAWaylandDesktop() {
        LinuxAppTag.Parsed p = LinuxAppTag.parse(LinuxAppTag.waylandDesktop("session:sway"));
        assertTrue(p.desktop);
        assertTrue(p.wayland);
        assertEquals("session:sway", p.key);
        assertEquals(-1, p.display);
        assertFalse(LinuxAppTag.parse(LinuxAppTag.desktop(2, "session:xfce")).wayland);
        assertFalse(LinuxAppTag.parse(LinuxAppTag.app("foot")).wayland);
        assertNull(LinuxAppTag.parse("wayland-desktop|"));
    }

    @Test
    public void damagedOrForeignTagsAreDropped() {
        assertNull(LinuxAppTag.parse(null));
        assertNull(LinuxAppTag.parse(""));
        assertNull(LinuxAppTag.parse("app|"));
        assertNull(LinuxAppTag.parse("shell|x"));
        assertNull(LinuxAppTag.parse("desktop|x|xfce"));
        assertNull(LinuxAppTag.parse("desktop|-1|xfce"));
        assertNull(LinuxAppTag.parse("desktop|1|"));
        assertNull(LinuxAppTag.parse("desktop|1"));
    }

    @Test
    public void aNoteKeepsTheAppAndTheScale() {
        LinuxAppTag.Note n = LinuxAppTag.parseNote(LinuxAppTag.note("firefox", 0f));
        assertEquals("firefox", n.key);
        assertEquals(0f, n.scale, 0f);
        n = LinuxAppTag.parseNote(LinuxAppTag.note(null, 1.5f));
        assertNull(n.key);
        assertEquals(1.5f, n.scale, 0f);
        n = LinuxAppTag.parseNote(LinuxAppTag.note("a|b", 2f));
        assertEquals("a|b", n.key);
        assertEquals(2f, n.scale, 0f);
    }

    @Test
    public void anEmptyOrDamagedNoteIsNothing() {
        assertNull(LinuxAppTag.note(null, 0f));
        assertNull(LinuxAppTag.note("", -1f));
        assertNull(LinuxAppTag.note(null, Float.NaN));
        assertNull(LinuxAppTag.parseNote(null));
        assertNull(LinuxAppTag.parseNote("|"));
        assertNull(LinuxAppTag.parseNote("x|"));
        assertNull(LinuxAppTag.parseNote("nobar"));
        assertEquals("k", LinuxAppTag.parseNote("-2|k").key);
        assertEquals(0f, LinuxAppTag.parseNote("-2|k").scale, 0f);
    }

    /** The log file stays in the directory whatever the key holds. */
    @Test
    public void theLogPathIsOneFileInTheDirectory() {
        assertEquals("/t/linux-app-org.xfce.mousepad.log", LinuxAppTag.logPath("/t", "org.xfce.mousepad"));
        assertEquals("/t/linux-app-.._.._x.log", LinuxAppTag.logPath("/t", "../../x"));
        assertEquals("/t/linux-app-_.log", LinuxAppTag.logPath("/t", ""));
    }
}
