package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.x11.X11GpuProbe;
import com.termux.app.x11.X11GpuProfileStore;
import com.termux.shared.termux.TermuxConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.Collections;
import java.util.List;

/**
 * Graphics acceleration opens in SettingsActivity and lays out its list: the phone's chip, the
 * freeze hint only on a chip known for it, Automatic and one row per profile that fits, and a
 * pick that is kept for the next app.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GraphicsAccelerationFragmentTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE).edit().clear().commit();
    }

    @After
    public void tearDown() {
        GraphicsAccelerationFragment.probeForTest = null;
    }

    private static X11GpuProbe.Result mali(String renderer) {
        X11GpuProbe.Inputs in = new X11GpuProbe.Inputs();
        in.eglVendor = "mali";
        in.glRenderer = renderer;
        in.virglServer = true;
        in.angle = true;
        in.mesaDri = true;
        return X11GpuProbe.evaluate(in);
    }

    private GraphicsAccelerationFragment launch(X11GpuProbe.Result gpu) throws Exception {
        GraphicsAccelerationFragment.probeForTest = gpu;
        Intent intent = SettingsActivity.createFragmentIntent(context, GraphicsAccelerationFragment.class,
            R.string.settings_x11_gpu_title, null, null);
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        ShadowLooper.idleMainLooper();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragment instanceof GraphicsAccelerationFragment);
        GraphicsAccelerationFragment page = (GraphicsAccelerationFragment) fragment;
        // The device is read on the page's own thread and posted back to the main looper.
        for (int i = 0; i < 200 && !page.loadedForTest(); i++) {
            Thread.sleep(25L);
            ShadowLooper.idleMainLooper();
        }
        assertTrue(page.loadedForTest());
        return page;
    }

    private static int count(List<Integer> types, int type) {
        return Collections.frequency(types, type);
    }

    @Test
    public void everyProfileThatFitsIsListedAfterAutomatic() throws Exception {
        X11GpuProbe.Result gpu = mali("Mali-G78 MP14");
        GraphicsAccelerationFragment page = launch(gpu);
        RecyclerView list = page.requireView().findViewById(R.id.x11_gpu_list);
        assertNotNull(list);
        List<Integer> types = page.itemTypesForTest();
        assertEquals("Automatic and each ranked profile", gpu.ranked.size() + 1,
            count(types, GraphicsAccelerationFragment.TYPE_PROFILE));
        assertFalse("no freeze hint on a chip with no report",
            types.contains(GraphicsAccelerationFragment.TYPE_HINT));
    }

    @Test
    public void aMaliG52ShowsTheFreezeHint() throws Exception {
        GraphicsAccelerationFragment page = launch(mali("Mali-G52 MC2"));
        List<Integer> types = page.itemTypesForTest();
        assertTrue(types.contains(GraphicsAccelerationFragment.TYPE_HINT));
        assertEquals("the hint sits right under the phone's card", 1,
            types.indexOf(GraphicsAccelerationFragment.TYPE_HINT));
    }

    @Test
    public void anOlderScriptWithoutTheProfilesOptionIsNotStarted() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("gpu-setup").toFile();
        java.io.File script = new java.io.File(dir, "termux-x11-gpu-setup");
        java.nio.file.Files.write(script.toPath(), "case \"$arg\" in\n  --keep) KEEP=1 ;;\nesac\n"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertFalse(GraphicsAccelerationFragment.knowsProfiles(script));
        java.nio.file.Files.write(script.toPath(), "    --profiles=*) PROFILES=${arg#--profiles=} ;;\n"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(GraphicsAccelerationFragment.knowsProfiles(script));
        assertFalse("no script at all", GraphicsAccelerationFragment.knowsProfiles(new java.io.File(dir, "none")));
    }

    @Test
    public void aPickIsStoredForTheNextApp() throws Exception {
        GraphicsAccelerationFragment page = launch(mali("Mali-G52 MC2"));
        assertEquals(X11GpuProfileStore.AUTO, new X11GpuProfileStore(context).pick());
        page.pick("virgl-angle");
        ShadowLooper.idleMainLooper();
        assertEquals("virgl-angle", new X11GpuProfileStore(context).pick());
        page.pick(X11GpuProfileStore.AUTO);
        assertEquals(X11GpuProfileStore.AUTO, new X11GpuProfileStore(context).pick());
    }
}
