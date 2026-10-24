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

    private String writeResults;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("bash is needed", new File("/bin/bash").canExecute()
            || new File("/usr/bin/bash").canExecute());
        File asset = new File("src/main/assets/x11/x11-gpu-setup.sh");
        if (!asset.isFile()) asset = new File("app/src/main/assets/x11/x11-gpu-setup.sh");
        String script = new String(Files.readAllBytes(asset.toPath()), StandardCharsets.UTF_8);
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
}
