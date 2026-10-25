package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The graphics setup script's promises that a device check would otherwise be the only proof of. */
public class X11GpuSetupScriptTest {

    private static String script() throws Exception {
        File f = new File("src/main/assets/x11/x11-gpu-setup.sh");
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test public void testDisplayIsHeadlessSoTheLauncherNeverAdoptsIt() throws Exception {
        String s = script();
        assertTrue(s.contains("TERMUX_X11_HEADLESS=1 termux-x11"));
        assertFalse(s.contains("is already running; using it"));
    }

    /** Runs the script's detect_pm with the given environment and returns what it prints. */
    private static String detectPm(String env) throws Exception {
        String s = script();
        int start = s.indexOf("detect_pm() {");
        int end = s.indexOf("\n}\n", start) + 3;
        File prefix = Files.createTempDirectory("pm").toFile();
        File sh = File.createTempFile("detect", ".sh");
        sh.deleteOnExit();
        String body = "PREFIX=" + prefix.getAbsolutePath() + "\n" + s.substring(start, end) + "\ndetect_pm\n";
        Files.write(sh.toPath(), body.getBytes(StandardCharsets.UTF_8));
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", env + " bash " + sh.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        p.waitFor();
        return out;
    }

    /** A pacman Termux also has dpkg installed; Termux's own variables, not the tools, decide. */
    @Test public void packageManagerFollowsTermuxNotWhichToolsExist() throws Exception {
        assertEquals("pacman", detectPm("export TERMUX_APP_PACKAGE_MANAGER=pacman;"));
        assertEquals("apt", detectPm("export TERMUX_APP_PACKAGE_MANAGER=apt;"));
        assertEquals("pacman", detectPm("unset TERMUX_APP_PACKAGE_MANAGER; export TERMUX_MAIN_PACKAGE_FORMAT=pacman;"));
        assertEquals("apt", detectPm("unset TERMUX_APP_PACKAGE_MANAGER; export TERMUX_MAIN_PACKAGE_FORMAT=debian;"));
    }

    /**
     * The published pacman file lists "./.PKGINFO" and a version with no release, which pacman
     * refuses as "missing package metadata": repacked, the metadata comes first under its own
     * name and the version carries the release.
     */
    @Test public void thePacmanFileIsRepackedSoPacmanCanRead() throws Exception {
        String s = script();
        int start = s.indexOf("repack_wrapper() {");
        int end = s.indexOf("\n}\n", start) + 3;
        File dir = Files.createTempDirectory("repack").toFile();
        String body = "set -e\nd=" + dir.getAbsolutePath() + "\n"
            + "src=$d/src; mkdir -p \"$src/data/usr/lib\"; echo so > \"$src/data/usr/lib/libvulkan_wrapper.so\"\n"
            + "printf 'pkgname = vulkan-wrapper-android\\npkgver = 25.0.0\\narch = aarch64\\n' > \"$src/.PKGINFO\"\n"
            + "echo b > \"$src/.BUILDINFO\"; echo m > \"$src/.MTREE\"\n"
            + "(cd \"$src\" && tar -cJf \"$d/published.pkg.tar.xz\" .)\n"
            + s.substring(start, end)
            + "out=$(repack_wrapper \"$d/published.pkg.tar.xz\")\n"
            + "echo \"${out##*/}\"\n"
            + "tar -tf \"$out\" | head -4\n"
            + "tar -xOf \"$out\" .PKGINFO | grep pkgver\n";
        File sh = new File(dir, "t.sh");
        Files.write(sh.toPath(), body.getBytes(StandardCharsets.UTF_8));
        Process p = new ProcessBuilder("bash", sh.getAbsolutePath()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        assertEquals("vulkan-wrapper-android-25.0.0-3.pkg.tar\n"
            + ".PKGINFO\n.BUILDINFO\n.MTREE\ndata/\n"
            + "pkgver = 25.0.0-3\n", out);
    }

    @Test public void vulkanWrapperIsDownloadedAndPinned() throws Exception {
        String s = script();
        for (String sum : new String[] {
            "407a024fe25c37e34e83d32a199eb82e204aa1c80f391c5eecc88d5d4ca98f96",
            "dec9d90eed2cb4d5e21acd665661e3f20476d13711b9c27eb38d9c276a3d4890",
            "fc11784fee6a553eae78b30a4bff267981b735be7b1d4fac4e894e09be8058ca",
            "2fbbf12d692534e640d270faa8f840c08ef0d38a65cbb191dd1bf896a32b923f"}) {
            assertTrue(sum, s.contains(sum));
        }
        assertTrue(s.contains("sha256sum"));
    }
}
