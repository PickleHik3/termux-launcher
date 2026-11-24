package com.termux.app.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Intent;
import android.os.Bundle;

import com.termux.app.fragments.settings.SettingsSearchFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** The Assistant's "No matching setting" link opens Settings search with the person's words typed in. */
@RunWith(RobolectricTestRunner.class)
public class SettingsSearchDeepLinkTest {

    @Test
    public void theQueryReachesTheInitialFragmentAsAnArgument() {
        Intent intent = new Intent().putExtra(SettingsActivity.EXTRA_SEARCH_QUERY, "make the text bigger");
        Bundle arguments = SettingsActivity.deepLinkArguments(intent);
        assertEquals("make the text bigger", arguments.getString(SettingsSearchFragment.ARG_QUERY));
    }

    @Test
    public void anIntentWithoutOneCarriesNoArguments() {
        assertNull(SettingsActivity.deepLinkArguments(new Intent()));
    }
}
