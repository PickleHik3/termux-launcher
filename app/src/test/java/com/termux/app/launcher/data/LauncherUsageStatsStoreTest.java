package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.shared.termux.TermuxConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherUsageStatsStoreTest {
    private static final String KEY = "app_launcher_az_usage_stats_v1";
    private Context context;
    private SharedPreferences preferences;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE);
        preferences.edit().remove(KEY).commit();
    }

    @Test public void suggestionsExcludeZeroAndOrderCountLastLabelThenStableId() {
        LauncherAppEntry countTwo = app("com.example.count", "Zulu", -1);
        LauncherAppEntry recentBeta = app("com.example.beta", "Beta", -1);
        LauncherAppEntry recentAlphaZ = app("com.example.zed", "Alpha", -1);
        LauncherAppEntry recentAlphaA = app("com.example.alpha", "Alpha", -1);
        LauncherAppEntry zero = app("com.example.zero", "Zero", -1);
        put("{\"" + countTwo.appRef.stableId() + "\":{\"count\":2,\"last\":1},"
            + "\"" + recentBeta.appRef.stableId() + "\":{\"count\":1,\"last\":9},"
            + "\"" + recentAlphaZ.appRef.stableId() + "\":{\"count\":1,\"last\":8},"
            + "\"" + recentAlphaA.appRef.stableId() + "\":{\"count\":1,\"last\":8},"
            + "\"" + zero.appRef.stableId() + "\":{\"count\":0,\"last\":99}}");
        List<LauncherAppEntry> ranked = new LauncherUsageStatsStore(context)
            .rankForSuggestions(Arrays.asList(zero, recentAlphaZ, recentBeta,
                recentAlphaA, countTwo));
        assertEquals(Arrays.asList(countTwo, recentBeta, recentAlphaA, recentAlphaZ), ranked);
    }

    @Test public void profileStableIdsRankIndependently() {
        LauncherAppEntry primary = app("com.example.same", "Same", -1);
        LauncherAppEntry profile = app("com.example.same", "Same clone", 10);
        put("{\"" + profile.appRef.stableId() + "\":{\"count\":3,\"last\":7}}");
        assertEquals(Arrays.asList(profile), new LauncherUsageStatsStore(context)
            .rankForSuggestions(Arrays.asList(primary, profile)));
    }

    @Test public void rankForAzStillIgnoresRecencyIncludesNeverUsedAndKeepsStableTies() {
        LauncherAppEntry firstTie = app("com.example.zed", "Alpha", -1);
        LauncherAppEntry secondTie = app("com.example.alpha", "Alpha", -1);
        LauncherAppEntry never = app("com.example.never", "Never", -1);
        put("{\"" + firstTie.appRef.stableId() + "\":{\"count\":1,\"last\":1},"
            + "\"" + secondTie.appRef.stableId() + "\":{\"count\":1,\"last\":99}}");
        List<LauncherAppEntry> ranked = new LauncherUsageStatsStore(context)
            .rankForAz(Arrays.asList(firstTie, secondTie, never));
        assertEquals(Arrays.asList(firstTie, secondTie, never), ranked);
        assertTrue(new LauncherUsageStatsStore(context).rankForSuggestions(
            Arrays.asList(never)).isEmpty());
    }

    private void put(String json) { preferences.edit().putString(KEY, json).commit(); }

    private static LauncherAppEntry app(String pkg, String label, int userId) {
        return new LauncherAppEntry(new AppRef(pkg, "Main", userId, -1L,
            userId >= 0, userId >= 0 ? "Clone" : ""), label, null);
    }
}
