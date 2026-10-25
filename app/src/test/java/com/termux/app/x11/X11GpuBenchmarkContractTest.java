package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The contract between the launcher and {@code termux-x11-gpu-setup}, run in a real bash: the
 * script evals {@link X11GpuProbe.Result#toTable} the way it does, and the line its own
 * {@code write_results} writes reads back through {@link X11GpuProfileStore.Results#parse}.
 * Skipped where there is no bash.
 */
public class X11GpuBenchmarkContractTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private String script;
    private String writeResults;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("bash is needed", new File("/bin/bash").canExecute()
            || new File("/usr/bin/bash").canExecute());
        File asset = new File("src/main/assets/x11/x11-gpu-setup.sh");
        if (!asset.isFile()) asset = new File("app/src/main/assets/x11/x11-gpu-setup.sh");
        script = new String(Files.readAllBytes(asset.toPath()), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("(?ms)^write_results\\(\\) \\{\\n.*?^\\}\\n").matcher(script);
        assertTrue("the script has write_results", m.find());
        writeResults = m.group();
    }

    /** Runs {@code body} after the script's own preamble: the arrays declared, the table eval'd. */
    private String bash(X11GpuProbe.Result gpu, String body) throws Exception {
        File table = temp.newFile();
        Files.write(table.toPath(), gpu.toTable().getBytes(StandardCharsets.UTF_8));
        File out = temp.newFolder();
        String program = "set -uo pipefail\n"
            + "declare -A PKGS ENV HELPER WHY SKIP SCORES STATUSES RENDERERS\n"
            + "CANDIDATES=(); GPU_NAME=unknown\n"
            + "eval \"$(cat \"$1\")\"\n"
            + "OUT_DIR=$2; RESULTS_FILE=$2/x11-gpu-results.tsv\n"
            + writeResults
            + body;
        Process process = new ProcessBuilder(Arrays.asList("bash", "-c", program, "bash",
            table.getAbsolutePath(), out.getAbsolutePath())).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals(output, 0, process.exitValue());
        return output;
    }

    private static X11GpuProbe.Inputs mali(String renderer) {
        X11GpuProbe.Inputs in = new X11GpuProbe.Inputs();
        in.eglVendor = "mali";
        in.vulkanVendor = "mali";
        in.glRenderer = renderer;
        return in;
    }

    @Test
    public void theTableEvalsAndTheResultsLineParses() throws Exception {
        X11GpuProbe.Result gpu = X11GpuProbe.evaluate(mali("Mali-G78 MP14"));
        String output = bash(gpu, ""
            + "STATUSES[software]=ok; SCORES[software]=120; RENDERERS[software]='llvmpipe (LLVM 17, 128 bits)'\n"
            + "STATUSES[virgl-angle]=ok; SCORES[virgl-angle]=812; RENDERERS[virgl-angle]=$'virgl\\t(ANGLE)'\n"
            + "STATUSES[vulkan-wrapper]='timed out'; SCORES[vulkan-wrapper]=0\n"
            + "STATUSES[virgl]='not accelerated'; SCORES[virgl]=0; RENDERERS[virgl]=llvmpipe\n"
            + "write_results\n"
            + "printf '%s|%s|%s\\n' \"${CANDIDATES[*]}\" \"${HELPER[virgl-angle]}\" \"${ENV[software]}\"\n"
            + "cat \"$RESULTS_FILE\"\n");

        String[] lines = output.split("\n", 2);
        assertEquals("virgl-angle vulkan-wrapper virgl|virgl_test_server_android --angle-gl|"
            + String.join(" ", gpu.software().env), lines[0]);
        X11GpuProfileStore.Results results = X11GpuProfileStore.Results.parse(lines[1], 1L);
        assertNotNull(lines[1], results);
        assertEquals(Arrays.asList("software", "virgl-angle", "vulkan-wrapper", "virgl"),
            new java.util.ArrayList<>(results.byProfile.keySet()));
        assertEquals(812, results.get("virgl-angle").score);
        assertEquals("a tab in the renderer does not split the line", "virgl (ANGLE)",
            results.get("virgl-angle").renderer);
        assertEquals(X11GpuProfileStore.Outcome.TIMED_OUT, results.get("vulkan-wrapper").outcome);
        assertEquals(X11GpuProfileStore.Outcome.NOT_ACCELERATED, results.get("virgl").outcome);
    }

    @Test
    public void onAKnownFreezeGpuEveryGpuProfileIsSkipped() throws Exception {
        X11GpuProbe.Result gpu = X11GpuProbe.evaluate(mali("Mali-G52 MC2"));
        String output = bash(gpu, ""
            + "for c in \"${CANDIDATES[@]}\"; do [ -n \"${SKIP[$c]:-}\" ] && printf '%s ' \"$c\"; done\n");
        assertEquals("virgl-angle vulkan-wrapper virgl ", output);
    }

    // ---- the test display ----

    /** The script's own definition of shell function {@code name}. */
    private String function(String name) {
        Matcher m = Pattern.compile("(?ms)^" + Pattern.quote(name) + "\\(\\) \\{\\n.*?^\\}\\n").matcher(script);
        assertTrue("the script has " + name, m.find());
        return m.group();
    }

    /** Runs {@code body} in bash with {@code $1} a fresh folder; returns what it printed. */
    private String run(String body) throws Exception {
        File dir = temp.newFolder();
        Process process = new ProcessBuilder(Arrays.asList("bash", "-c", "set -uo pipefail\n" + body,
            "bash", dir.getAbsolutePath())).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS));
        assertEquals(output, 0, process.exitValue());
        return output;
    }

    @Test
    public void theTestDisplayIsAlwaysANewOneTheLauncherIsNeverToldAbout() {
        assertTrue("started headless, without the input-method helper", script.contains(
            "TERMUX_X11_HEADLESS=1 TERMUX_X11_IM=0 termux-x11 \"$TEST_DISPLAY\" -ac"));
        assertTrue("a display that is already running is never reused",
            script.contains("display_in_use \"$n\" || { DISPLAY_NO=$n; break; }"));
        assertTrue("never a broad pkill of display servers", !script.contains("pkill -f termux-x11")
            && !script.contains("pkill termux-x11"));
    }

    @Test
    public void stoppingTheTestDisplayStopsThatServerAndNoOther() throws Exception {
        Assume.assumeTrue("needs /proc", new File("/proc/self/cmdline").canRead());
        String output = run(""
            + "TMPDIR=$1; HOME=$1; DISPLAY_NO=7\n"
            + function("is_x11_server") + function("display_answers") + function("display_in_use")
            + function("stop_display")
            + "mkdir -p \"$TMPDIR/.X11-unix\"; : > \"$TMPDIR/.X11-unix/X7\"; echo 1 > \"$TMPDIR/.X7-lock\"\n"
            + "(exec -a 'termux-x11 com.test :7 -ac' sleep 30) & SERVER_PID=$!\n"
            + "(exec -a 'termux-x11 com.test :8 -ac' sleep 30) & OTHER=$!\n"
            + "sleep 0.3\n"
            + "is_x11_server $SERVER_PID 7 && echo ours-is-7\n"
            + "is_x11_server $SERVER_PID 8 || echo ours-is-not-8\n"
            + "display_in_use 8 && echo 8-in-use\n"
            + "display_in_use 9 || echo 9-free\n"
            + "pid=$SERVER_PID; stop_display\n"
            + "is_x11_server $pid 7 || echo ours-stopped\n"
            + "kill -0 $OTHER && echo other-running\n"
            + "[ -e \"$TMPDIR/.X11-unix/X7\" ] || echo socket-gone\n"
            + "[ -e \"$TMPDIR/.X7-lock\" ] || echo lock-gone\n"
            + "kill $OTHER\n");
        assertEquals("ours-is-7\nours-is-not-8\n8-in-use\n9-free\nours-stopped\nother-running\n"
            + "socket-gone\nlock-gone\n", output);
    }

    // ---- the Vulkan wrapper ----

    /** The wrapper's pin and its functions, with the package manager and the network stubbed. */
    private String wrapperPrelude(String pm, String arch, String download) {
        Matcher m = Pattern.compile("(?ms)^WRAPPER_PKG=.*?^WRAPPER_DIR=\"\"\\n").matcher(script);
        assertTrue("the script pins the wrapper", m.find());
        Matcher opts = Pattern.compile("(?m)^APT_OPTS=.*\\n").matcher(script);
        assertTrue("the script names apt's options", opts.find());
        return "TMPDIR=$1; LOG=$1/log; PM=" + pm + "\n" + opts.group()
            + "detail() { :; }\n"
            + "pacman-conf() { echo " + arch + "; }\n"
            + "dpkg() { echo " + arch + "; }\n"
            + "uname() { echo " + arch + "; }\n"
            + "curl() { local out=''; while [ $# -gt 1 ]; do [ \"$1\" = -o ] && out=$2; shift; done; "
            + "echo \"$1\" > \"$TMPDIR/url\"; printf '%s' '" + download + "' > \"$out\"; }\n"
            + "pacman() { echo \"pacman $*\" >> \"$TMPDIR/calls\"; }\n"
            + "apt-get() { echo \"apt-get $*\" >> \"$TMPDIR/calls\"; }\n"
            + m.group() + function("wrapper_file") + function("drop_wrapper_download")
            + function("install_wrapper");
    }

    @Test
    public void theWrapperIsPinnedToFourFilesByChecksum() throws Exception {
        String output = run(wrapperPrelude("pacman", "aarch64", "")
            + "for k in \"${!WRAPPER_SHA256[@]}\"; do echo \"$k=${WRAPPER_SHA256[$k]}\"; done | LC_ALL=C sort\n"
            + "echo \"$WRAPPER_URL\"\n");
        assertEquals(""
            + "vulkan-wrapper-android_25.0.0-3-aarch64.pkg.tar.xz=407a024fe25c37e34e83d32a199eb82e204aa1c80f391c5eecc88d5d4ca98f96\n"
            + "vulkan-wrapper-android_25.0.0-3-arm.pkg.tar.xz=dec9d90eed2cb4d5e21acd665661e3f20476d13711b9c27eb38d9c276a3d4890\n"
            + "vulkan-wrapper-android_25.0.0-3_aarch64.deb=fc11784fee6a553eae78b30a4bff267981b735be7b1d4fac4e894e09be8058ca\n"
            + "vulkan-wrapper-android_25.0.0-3_arm.deb=2fbbf12d692534e640d270faa8f840c08ef0d38a65cbb191dd1bf896a32b923f\n"
            + "https://github.com/sabamdarif/termux-desktop/releases/download/vulkan-wrapper-android_25.0.0-3\n",
            output);
    }

    @Test
    public void eachPackageManagerAndProcessorGetsItsOwnFile() throws Exception {
        assertEquals("vulkan-wrapper-android_25.0.0-3-aarch64.pkg.tar.xz\n",
            run(wrapperPrelude("pacman", "aarch64", "") + "wrapper_file\n"));
        assertEquals("vulkan-wrapper-android_25.0.0-3_arm.deb\n",
            run(wrapperPrelude("apt", "arm", "") + "wrapper_file\n"));
        assertEquals("none\n",
            run(wrapperPrelude("apt", "x86_64", "") + "wrapper_file || echo none\n"));
    }

    private static boolean hasSha256sum() {
        return new File("/usr/bin/sha256sum").canExecute() || new File("/bin/sha256sum").canExecute();
    }

    @Test
    public void aDownloadThatIsNotThePinnedFileIsNeverInstalled() throws Exception {
        Assume.assumeTrue("needs sha256sum", hasSha256sum());
        String output = run(wrapperPrelude("pacman", "aarch64", "not the wrapper")
            + "install_wrapper || echo refused\n"
            + "[ -e \"$TMPDIR/calls\" ] || echo nothing-installed\n"
            + "ls -d \"$TMPDIR\"/vulkan-wrapper.* 2>/dev/null || echo download-deleted\n");
        assertEquals("refused\nnothing-installed\ndownload-deleted\n", output);
    }

    @Test
    public void thePinnedFileIsInstalledFromDiskAndTheDownloadDeleted() throws Exception {
        Assume.assumeTrue("needs sha256sum", hasSha256sum());
        String body = "name=$(wrapper_file)\n"
            + "WRAPPER_SHA256[$name]=$(printf '%s' 'the wrapper' | sha256sum | awk '{ print $1 }')\n"
            + "install_wrapper && echo installed\n"
            + "sed \"s|$TMPDIR/vulkan-wrapper\\.[^/]*/|<tmp>/|\" \"$TMPDIR/calls\"\n"
            + "cat \"$TMPDIR/url\"\n"
            + "ls -d \"$TMPDIR\"/vulkan-wrapper.* 2>/dev/null || echo download-deleted\n";
        String url = "https://github.com/sabamdarif/termux-desktop/releases/download/vulkan-wrapper-android_25.0.0-3/";
        assertEquals("installed\n"
                + "pacman -U --noconfirm <tmp>/vulkan-wrapper-android_25.0.0-3-aarch64.pkg.tar.xz\n"
                + url + "vulkan-wrapper-android_25.0.0-3-aarch64.pkg.tar.xz\n"
                + "download-deleted\n",
            run(wrapperPrelude("pacman", "aarch64", "the wrapper") + body));
        assertEquals("installed\n"
                + "apt-get install -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold"
                + " <tmp>/vulkan-wrapper-android_25.0.0-3_aarch64.deb\n"
                + url + "vulkan-wrapper-android_25.0.0-3_aarch64.deb\n"
                + "download-deleted\n",
            run(wrapperPrelude("apt", "aarch64", "the wrapper") + body));
    }
}
