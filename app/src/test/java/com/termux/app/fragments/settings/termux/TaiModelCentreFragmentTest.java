package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.ai.TaiSettings;
import com.termux.app.activities.SettingsActivity;

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

/**
 * The Model centre opens on Functions, or on the segment a deep link names (the notification, the
 * speech model picker's "Get more", the cleanup screen), and lays out its list: the device line,
 * segments, the segment's rows (the import bar leads Get models) and the simultaneous-downloads setting.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TaiModelCentreFragmentTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        // The hub and the engine are process singletons: a download another test left in them would
        // show up here as a Downloads section and throw every row count off.
        com.termux.ai.TaiDownloadEngine.resetForTesting();
        com.termux.ai.TaiDownloadHub.resetForTesting();
        com.termux.ai.TaiModelCatalog.resetForTesting();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("termux_ai_model_store", Context.MODE_PRIVATE).edit().clear().commit();
    }

    private TaiModelCentreFragment launch(String segment) {
        Intent intent = SettingsActivity.createFragmentIntent(context, TaiModelCentreFragment.class,
            R.string.tai_model_centre_title, segment, null);
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        ShadowLooper.idleMainLooper();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragment instanceof TaiModelCentreFragment);
        return (TaiModelCentreFragment) fragment;
    }

    @Test
    public void theCentreOpensOnFunctionsAndADeepLinkOpensTheNamedSegment() {
        assertEquals(TaiModelCentreFragment.SEGMENT_FUNCTIONS, launch(null).currentSegment());
        assertEquals(TaiModelCentreFragment.SEGMENT_INSTALLED, launch(TaiModelCentreFragment.SEGMENT_INSTALLED).currentSegment());
        assertEquals(TaiModelCentreFragment.SEGMENT_GET, launch(TaiModelCentreFragment.SEGMENT_GET).currentSegment());
        // The old segment names land on Get models.
        assertEquals(TaiModelCentreFragment.SEGMENT_GET, launch(TaiModelCentreFragment.SEGMENT_SPEECH).currentSegment());
    }

    @Test
    public void functionsListsTheDeviceLineThenTheSegmentsThenTheFunctionRows() {
        TaiModelCentreFragment fragment = launch(TaiModelCentreFragment.SEGMENT_FUNCTIONS);
        RecyclerView list = (RecyclerView) fragment.getView();
        assertNotNull(list);
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        assertNotNull(adapter);
        assertEquals(TaiModelCentreAdapter.TYPE_HEADER, adapter.getItemViewType(0));
        assertEquals(TaiModelCentreAdapter.TYPE_SEGMENTS, adapter.getItemViewType(1));
        assertEquals(TaiModelCentreAdapter.TYPE_FUNCTION, adapter.getItemViewType(2));
        // No import bar and no downloads setting on this segment.
        for (int i = 0; i < adapter.getItemCount(); i++) {
            assertTrue(adapter.getItemViewType(i) != TaiModelCentreAdapter.TYPE_LINK);
            assertTrue(adapter.getItemViewType(i) != TaiModelCentreAdapter.TYPE_SETTING);
        }
    }

    @Test
    public void getModelsLeadsWithTheImportBarAndEndsWithTheDownloadsSetting() {
        TaiModelCentreFragment fragment = launch(TaiModelCentreFragment.SEGMENT_GET);
        RecyclerView list = (RecyclerView) fragment.getView();
        assertNotNull(list);
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        assertNotNull(adapter);
        assertEquals(TaiModelCentreAdapter.TYPE_HEADER, adapter.getItemViewType(0));
        assertEquals(TaiModelCentreAdapter.TYPE_SEGMENTS, adapter.getItemViewType(1));
        assertEquals(TaiModelCentreAdapter.TYPE_LINK, adapter.getItemViewType(2));
        assertEquals(TaiModelCentreAdapter.TYPE_SETTING, adapter.getItemViewType(adapter.getItemCount() - 1));
    }

    @Test
    public void withNothingInstalledTheInstalledSegmentSaysWhereToGetOne() {
        TaiModelCentreFragment fragment = launch(TaiModelCentreFragment.SEGMENT_INSTALLED);
        RecyclerView list = (RecyclerView) fragment.getView();
        assertNotNull(list);
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        assertNotNull(adapter);
        assertEquals(TaiModelCentreAdapter.TYPE_HEADER, adapter.getItemViewType(0));
        assertEquals(TaiModelCentreAdapter.TYPE_EMPTY, adapter.getItemViewType(2));
    }
}
