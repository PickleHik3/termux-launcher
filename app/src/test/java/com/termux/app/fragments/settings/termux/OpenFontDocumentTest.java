package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

/**
 * The keyboard font picker must offer the device's whole shared storage, not only Downloads: the
 * primary storage root is an advanced root that the system picker hides unless asked.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OpenFontDocumentTest {

    private Intent pickerIntent() {
        Context context = ApplicationProvider.getApplicationContext();
        return new OpenFontDocument().createIntent(context, OpenFontDocument.mimeTypes());
    }

    @Test
    public void opensADocumentAnyProviderCanServe() {
        Intent intent = pickerIntent();
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.getAction());
        assertEquals("*/*", intent.getType());
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE));
    }

    @Test
    public void asksThePickerToShowInternalStorage() {
        Intent intent = pickerIntent();
        assertTrue(intent.getBooleanExtra(OpenFontDocument.EXTRA_SHOW_ADVANCED, false));
        assertTrue(intent.getBooleanExtra(OpenFontDocument.EXTRA_SHOW_ADVANCED_LEGACY, false));
        assertEquals("android.provider.extra.SHOW_ADVANCED", OpenFontDocument.EXTRA_SHOW_ADVANCED);
    }

    @Test
    public void filtersToFontTypesIncludingUnmappedFiles() {
        String[] types = pickerIntent().getStringArrayExtra(Intent.EXTRA_MIME_TYPES);
        assertArrayEquals(OpenFontDocument.FONT_MIME_TYPES, types);
        assertTrue(Arrays.asList(types).containsAll(Arrays.asList(
            "font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf",
            "application/octet-stream")));
    }

    @Test
    public void mimeTypesIsADefensiveCopy() {
        assertNotSame(OpenFontDocument.FONT_MIME_TYPES, OpenFontDocument.mimeTypes());
    }
}
