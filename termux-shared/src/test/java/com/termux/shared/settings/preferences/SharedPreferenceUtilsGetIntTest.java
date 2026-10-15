package com.termux.shared.settings.preferences;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * An int setting that a list row once stored as its entry's string reads as that number, not as
 * the default with an error logged on every read.
 */
@RunWith(RobolectricTestRunner.class)
public class SharedPreferenceUtilsGetIntTest {

    @Test
    public void aNumberStoredAsAStringReadsAsThatNumber() {
        SharedPreferences store = store();
        store.edit().putString("pause", "900").commit();
        assertEquals(900, SharedPreferenceUtils.getInt(store, "pause", 600));
    }

    @Test
    public void aStringThatIsNoNumberReadsAsTheDefault() {
        SharedPreferences store = store();
        store.edit().putString("pause", "slow").commit();
        assertEquals(600, SharedPreferenceUtils.getInt(store, "pause", 600));
    }

    @Test
    public void anIntReadsAsItself() {
        SharedPreferences store = store();
        store.edit().putInt("pause", 1200).commit();
        assertEquals(1200, SharedPreferenceUtils.getInt(store, "pause", 600));
    }

    private static SharedPreferences store() {
        SharedPreferences store = RuntimeEnvironment.getApplication()
            .getSharedPreferences("get-int", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        return store;
    }
}
