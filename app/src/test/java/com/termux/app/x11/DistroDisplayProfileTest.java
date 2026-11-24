package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class DistroDisplayProfileTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static final String SCRIPT = DistroDisplayProfile.script("/p/tmp/wayland-0", "/tmp/.X11-unix");

    /** The exact text; checked by hand with {@code shellcheck -s sh} and run under sh and dash. */
    @Test public void theScriptIsExactlyThis() {
        assertEquals("# written by termux-launcher for its display; it rewrites this file.\n"
            + "# Points programs started from a login shell at the launcher's display while it is there.\n"
            + "# WAYLAND_DISPLAY: the launcher's Wayland socket, at the same path as in Termux. A default\n"
            + "# proot-distro login binds the Termux prefix at its own path; an --isolated login does not,\n"
            + "# and then nothing is set.\n"
            + "# DISPLAY: X11 programs need a login that shares Termux's X sockets, made with\n"
            + "# --shared-tmp or --shared-x11. Without one, /tmp/.X11-unix is the distro's own.\n"
            + "# Never sets XDG_RUNTIME_DIR.\n"
            + "if [ -z \"${WAYLAND_DISPLAY:-}\" ] && [ -S \"/p/tmp/wayland-0\" ]; then\n"
            + "    WAYLAND_DISPLAY=\"/p/tmp/wayland-0\"\n"
            + "    export WAYLAND_DISPLAY\n"
            + "fi\n"
            + "if [ -z \"${DISPLAY:-}\" ]; then\n"
            + "    termux_launcher_display=\n"
            + "    for termux_launcher_x in \"/tmp/.X11-unix\"/X*; do\n"
            + "        [ -S \"$termux_launcher_x\" ] || continue\n"
            + "        termux_launcher_n=${termux_launcher_x##*/X}\n"
            + "        case $termux_launcher_n in\n"
            + "            ''|*[!0-9]*) continue ;;\n"
            + "        esac\n"
            + "        if [ -z \"$termux_launcher_display\" ] || [ \"$termux_launcher_n\" -lt \"$termux_launcher_display\" ]; then\n"
            + "            termux_launcher_display=$termux_launcher_n\n"
            + "        fi\n"
            + "    done\n"
            + "    if [ -n \"$termux_launcher_display\" ]; then\n"
            + "        DISPLAY=:$termux_launcher_display\n"
            + "        export DISPLAY\n"
            + "    fi\n"
            + "    unset termux_launcher_display termux_launcher_x termux_launcher_n\n"
            + "fi\n", SCRIPT);
    }

    @Test public void theScriptNeverTouchesTheRuntimeDirOrGraphics() {
        assertFalse(SCRIPT.contains("XDG_RUNTIME_DIR="));
        assertFalse(SCRIPT.contains("LD_LIBRARY_PATH"));
        assertFalse(SCRIPT.contains("MESA"));
        assertFalse(SCRIPT.contains("VK_"));
        assertFalse(SCRIPT.contains("exit"));
    }

    @Test public void theRealScriptNamesThePrefixSocket() {
        String real = DistroDisplayProfile.script(
            com.termux.app.wayland.WaylandAppRunner.socketPath(), DistroDisplayProfile.X_SOCKET_DIR);
        assertTrue(real.contains("[ -S \"" + com.termux.app.wayland.WaylandAppRunner.socketPath() + "\" ]"));
        assertTrue(real.contains("\"/tmp/.X11-unix\"/X*"));
    }

    @Test public void aMissingScriptIsWrittenThenKept() throws IOException {
        File rootfs = temp.newFolder("rootfs");

        assertEquals(DistroDisplayProfile.Result.WRITTEN, DistroDisplayProfile.write(rootfs, SCRIPT));
        File written = new File(rootfs, "etc/profile.d/termux-launcher-display.sh");
        assertEquals(SCRIPT, read(written));
        assertEquals(DistroDisplayProfile.Result.KEPT, DistroDisplayProfile.write(rootfs, SCRIPT));
        assertEquals(1, written.getParentFile().list().length);
    }

    @Test public void anOlderScriptOfOursIsRewritten() throws IOException {
        File rootfs = temp.newFolder("rootfs");
        File file = new File(rootfs, DistroDisplayProfile.PATH);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), DistroDisplayProfile.script("/old/wayland-0", "/tmp/.X11-unix")
            .getBytes(StandardCharsets.UTF_8));

        assertEquals(DistroDisplayProfile.Result.WRITTEN, DistroDisplayProfile.write(rootfs, SCRIPT));
        assertEquals(SCRIPT, read(file));
    }

    @Test public void aFileThatIsNotOursIsLeftAlone() throws IOException {
        File rootfs = temp.newFolder("rootfs");
        File file = new File(rootfs, DistroDisplayProfile.PATH);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), "export DISPLAY=:5\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(DistroDisplayProfile.Result.FOREIGN, DistroDisplayProfile.write(rootfs, SCRIPT));
        assertEquals("export DISPLAY=:5\n", read(file));
    }

    @Test public void aSymlinkIsRefusedNotFollowed() throws IOException {
        File rootfs = temp.newFolder("rootfs");
        File target = temp.newFile("elsewhere");
        File file = new File(rootfs, DistroDisplayProfile.PATH);
        file.getParentFile().mkdirs();
        Files.createSymbolicLink(file.toPath(), target.toPath());

        try {
            DistroDisplayProfile.write(rootfs, SCRIPT);
            fail("wrote through a symlink");
        } catch (IOException expected) {
            assertEquals("", read(target));
        }
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
