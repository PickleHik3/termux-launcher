package com.termux.app.setup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.termux.ai.TaiDeviceTier;
import com.termux.ai.TaiPlatformCaps;
import com.termux.ai.TaiTierPolicy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** The AI card re-ticks from the probed GPU path when nothing was chosen, and ignores a late result. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class AiCardPageProbeTest {

    private static final long GIB = 1024L * 1024L * 1024L;
    private static final String E4B = "e4b_assistant";
    private static final String E2B = "assistant";

    private Context context;
    private AiCardPage.ProbeRequest oldProbe;
    private Function<Context, TaiTierPolicy.Env> oldEnv;
    private final AtomicReference<TaiPlatformCaps.GpuPath> path = new AtomicReference<>();
    private Runnable landed;

    @Before
    public void setUp() {
        context = new android.view.ContextThemeWrapper(
            (Context) ApplicationProvider.getApplicationContext(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
        oldProbe = AiCardPage.sProbeRequest;
        oldEnv = AiCardPage.sEnvSource;
        path.set(TaiPlatformCaps.GpuPath.UNKNOWN);
        AiCardPage.sEnvSource = c -> new TaiTierPolicy.Env(TaiDeviceTier.TIER_3, 12 * GIB, 34, true,
            true, path.get(), true);
        AiCardPage.sProbeRequest = (app, onProbed) -> landed = onProbed;
    }

    @After
    public void tearDown() {
        AiCardPage.sProbeRequest = oldProbe;
        AiCardPage.sEnvSource = oldEnv;
    }

    private View show(AiCardPage page) {
        return page.createView(context, null, new SetupCardPage.Host() {
            @NonNull @Override public Context context() { return context; }
            @Override public void back() {}
            @Override public void completeStep(@NonNull String cardId, int step) {}
            @Override public void completeCard(@NonNull String cardId) {}
            @Override public void startSafely(@NonNull Intent intent) {}
        });
    }

    private static CheckBox firstBox(View v) {
        if (v instanceof CheckBox) return (CheckBox) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                CheckBox b = firstBox(g.getChildAt(i));
                if (b != null) return b;
            }
        }
        return null;
    }

    private void land() {
        landed.run();
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void unprobedTierThreeTicksE4bThenProbeNoSwitchesToE2b() {
        AiCardPage page = new AiCardPage();
        show(page);
        assertTrue(page.tickedForTest().contains(E4B));
        path.set(TaiPlatformCaps.GpuPath.NO);
        land();
        assertFalse(page.tickedForTest().contains(E4B));
        assertTrue(page.tickedForTest().contains(E2B));
    }

    @Test
    public void userTicksSurviveTheProbeResult() {
        AiCardPage page = new AiCardPage();
        View root = show(page);
        CheckBox box = firstBox(root);
        box.setChecked(!box.isChecked());
        Set<String> chosen = page.tickedForTest();
        path.set(TaiPlatformCaps.GpuPath.NO);
        land();
        assertEquals(chosen, page.tickedForTest());
        assertTrue(page.tickedForTest().contains(E4B));
    }

    @Test
    public void resultAfterHiddenIsIgnored() {
        AiCardPage page = new AiCardPage();
        show(page);
        page.onHidden();
        path.set(TaiPlatformCaps.GpuPath.NO);
        land();
        assertTrue(page.tickedForTest().contains(E4B));
    }
}
