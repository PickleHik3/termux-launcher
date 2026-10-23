package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

/** The user's graphics pick, the per-app software switches and the last benchmark, kept. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class X11GpuProfileStoreTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void thePickDefaultsToAutoAndRoundTripsThroughANewInstance() {
        assertEquals(X11GpuProfileStore.AUTO, new X11GpuProfileStore(context).pick());
        new X11GpuProfileStore(context).setPick("virgl");
        assertEquals("virgl", new X11GpuProfileStore(context).pick());
        new X11GpuProfileStore(context).setPick("no-such-profile");
        assertEquals("an unknown id is auto again", X11GpuProfileStore.AUTO,
            new X11GpuProfileStore(context).pick());
    }

    @Test public void anAppsSoftwareSwitchIsKeptPerApp() {
        X11GpuProfileStore store = new X11GpuProfileStore(context);
        store.setDrawsInSoftware("code-oss", true);
        X11GpuProfileStore again = new X11GpuProfileStore(context);
        assertTrue(again.drawsInSoftware("code-oss"));
        assertFalse(again.drawsInSoftware("firefox"));
        again.setDrawsInSoftware("code-oss", false);
        assertFalse(new X11GpuProfileStore(context).drawsInSoftware("code-oss"));
        assertTrue(new X11GpuProfileStore(context).softwareApps().isEmpty());
    }

    @Test public void theScriptsResultsAreParsedKeptAndReadBack() throws Exception {
        File file = temp.newFile("x11-gpu-results.tsv");
        Files.write(file.toPath(), ("# x11-gpu-setup results\n"
            + "software\tok\t120\tllvmpipe (LLVM 17.0.6, 128 bits)\n"
            + "virgl-angle\tok\t812\tvirgl (ANGLE (ARM, Mali-G52 MC2))\n"
            + "vulkan-wrapper\tcrashed\t0\t\n"
            + "virgl\tskipped\t0\n"
            + "bogus\tok\t5\tnothing\n").getBytes(StandardCharsets.UTF_8));
        X11GpuProfileStore store = new X11GpuProfileStore(context);

        assertTrue(store.importResults(file));
        X11GpuProfileStore.Results kept = new X11GpuProfileStore(context).results();
        assertNotNull(kept);
        assertEquals(file.lastModified(), kept.timestamp);
        assertEquals(Arrays.asList("software", "virgl-angle", "vulkan-wrapper", "virgl"),
            new java.util.ArrayList<>(kept.byProfile.keySet()));
        X11GpuProfileStore.Score angle = kept.get("virgl-angle");
        assertNotNull(angle);
        assertEquals(X11GpuProfileStore.Outcome.OK, angle.outcome);
        assertEquals(812, angle.score);
        assertEquals("virgl (ANGLE (ARM, Mali-G52 MC2))", angle.renderer);
        assertEquals(X11GpuProfileStore.Outcome.SKIPPED, kept.get("virgl").outcome);
        assertEquals("", kept.get("virgl").renderer);
        assertFalse("the same file again changes nothing", store.importResults(file));
    }

    @Test public void aFileWithNothingReadableIsNoResult() {
        assertNull(X11GpuProfileStore.Results.parse("# only a header\n\n", 1L));
        assertFalse(new X11GpuProfileStore(context).importResults(new File(temp.getRoot(), "missing.tsv")));
    }

    @Test public void theProbesJsonGainsThePickAndWhatAnAppIsStartedWith() throws Exception {
        X11GpuProbe.Inputs adreno = new X11GpuProbe.Inputs();
        adreno.kgsl = true;
        adreno.mesaDri = true;
        adreno.virglServer = true;
        adreno.icdFiles = Collections.singletonList("freedreno_icd.aarch64.json");
        X11GpuProbe.Result gpu = X11GpuProbe.evaluate(adreno);
        X11GpuProfileStore store = new X11GpuProfileStore(context);
        store.setPick("virgl");

        JSONObject json = gpu.toJson();
        store.putChoice(json, gpu);

        assertEquals("the probe's own fields stay", "turnip-zink", json.getString("recommended"));
        assertEquals("virgl", json.getString("pick"));
        assertEquals("virgl", json.getString("in_use"));
        assertEquals("GALLIUM_DRIVER=virpipe", json.getJSONArray("in_use_env").getString(0));
    }
}
