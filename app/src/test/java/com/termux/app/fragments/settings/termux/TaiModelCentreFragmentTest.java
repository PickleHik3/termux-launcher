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
 * The Model centre opens on the segment a deep link names (the notification, the speech model
 * picker's "Get more", the cleanup screen), and lays out its list: link bar, segments, the
 * segment's rows and the simultaneous-downloads setting.
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
    public void aDeepLinkOpensTheNamedSegment() {
        assertEquals(TaiModelCentreFragment.SEGMENT_SPEECH, launch(TaiModelCentreFragment.SEGMENT_SPEECH).currentSegment());
        assertEquals(TaiModelCentreFragment.SEGMENT_CHAT, launch(TaiModelCentreFragment.SEGMENT_CHAT).currentSegment());
    }

    @Test
    public void theChatSegmentListsTheCatalogueWithTheLinkBarOnTopAndTheSettingAtTheBottom() {
        TaiModelCentreFragment fragment = launch(TaiModelCentreFragment.SEGMENT_CHAT);
        RecyclerView list = (RecyclerView) fragment.getView();
        assertNotNull(list);
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        assertNotNull(adapter);
        // Link bar, segments, two Gemma 4 rows (nothing installed, nothing downloading), the setting.
        assertEquals(TaiModelCentreAdapter.TYPE_LINK, adapter.getItemViewType(0));
        assertEquals(TaiModelCentreAdapter.TYPE_SEGMENTS, adapter.getItemViewType(1));
        assertEquals(TaiModelCentreAdapter.TYPE_MODEL, adapter.getItemViewType(2));
        assertEquals(TaiModelCentreAdapter.TYPE_MODEL, adapter.getItemViewType(3));
        assertEquals(TaiModelCentreAdapter.TYPE_SETTING, adapter.getItemViewType(adapter.getItemCount() - 1));
        assertEquals(5, adapter.getItemCount());
    }

    @Test
    public void withNothingInstalledTheInstalledSegmentSaysWhereToGetOne() {
        TaiModelCentreFragment fragment = launch(TaiModelCentreFragment.SEGMENT_INSTALLED);
        RecyclerView list = (RecyclerView) fragment.getView();
        assertNotNull(list);
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        assertNotNull(adapter);
        assertEquals(TaiModelCentreAdapter.TYPE_EMPTY, adapter.getItemViewType(2));
    }
}
