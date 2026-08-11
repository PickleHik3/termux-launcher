package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.*;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TermuxStylePreferencesDataStoreDrawerTest {
    @Test public void sixKeysRoundTripAndRapidWritesCoalesceToDrawerOnlySignal() {
        Context context = RuntimeEnvironment.getApplication();
        TermuxStylePreferencesDataStore store = TermuxStylePreferencesDataStore.getInstance(context);
        AtomicInteger drawer = new AtomicInteger();
        AtomicInteger style = new AtomicInteger();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (TERMUX_ACTIVITY.ACTION_RELOAD_APP_DRAWER.equals(i.getAction())) drawer.incrementAndGet();
                if (TERMUX_ACTIVITY.ACTION_RELOAD_STYLE.equals(i.getAction())) style.incrementAndGet();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_APP_DRAWER);
        filter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        context.registerReceiver(receiver, filter);
        store.putString("app_launcher_drawer_view_type", "horizontal");
        store.putString("app_launcher_drawer_icon_size_dp", "44");
        store.putInt("app_launcher_drawer_grid_columns_vertical", 6);
        store.putInt("app_launcher_drawer_grid_columns_horizontal", 5);
        store.putInt("app_launcher_drawer_grid_rows_horizontal", 4);
        store.putInt("app_launcher_drawer_grid_columns_categories", 3);
        assertEquals("horizontal", store.getString("app_launcher_drawer_view_type", ""));
        assertEquals(44, store.getInt("app_launcher_drawer_icon_size_dp", -1));
        assertEquals(6, store.getInt("app_launcher_drawer_grid_columns_vertical", -1));
        assertEquals(5, store.getInt("app_launcher_drawer_grid_columns_horizontal", -1));
        assertEquals(4, store.getInt("app_launcher_drawer_grid_rows_horizontal", -1));
        assertEquals(3, store.getInt("app_launcher_drawer_grid_columns_categories", -1));
        Robolectric.getForegroundThreadScheduler().advanceBy(200);
        assertEquals(1, drawer.get());
        assertEquals(0, style.get());
        context.unregisterReceiver(receiver);
    }
}
