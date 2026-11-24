package com.termux.app.surfaces;

import android.app.Application;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Pins the keys the appearance reset removes ({@link AppearanceReset#NUMBER} means "this set"):
 * every key the launcher names, probed through {@code inScope}, equals the list captured before
 * the reset began reading the setting declarations. Changing the set needs a new reset number
 * and a new golden file ({@code appearance-reset-keys.txt}).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppearanceResetKeySetTest {

    private static final String[] EXTRA_PROBES = {
        "fontsize", "fontsize_scratchpad", "fontsize@1", "fontsize_scratchpad@1",
        "compact_fontsize", "compact_fontsize@1", "log_level", "look.dock.opacity",
        "surface_inherit_dock", "layout.home.search_field", "place.dress.home.x"
    };

    @SuppressWarnings("unchecked")
    @Test
    public void theKeySetIsTheCapturedOne() throws Exception {
        TreeSet<String> probes = new TreeSet<>(Arrays.asList(EXTRA_PROBES));
        for (Field field : TERMUX_APP.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class
                && field.get(null) != null) probes.add((String) field.get(null));
        }
        for (String name : new String[] {"NAMED", "SURFACE_KEYS", "PAGE_ABLE"}) {
            Field field = AppearanceReset.class.getDeclaredField(name);
            field.setAccessible(true);
            probes.addAll((Collection<String>) field.get(null));
        }
        TreeSet<String> removed = new TreeSet<>();
        for (String key : probes) if (AppearanceReset.inScope(key)) removed.add(key);

        TreeSet<String> golden = new TreeSet<>();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("appearance-reset-keys.txt")) {
            assertNotNull("golden file missing", in);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) if (!line.isEmpty()) golden.add(line);
        }
        assertEquals(golden, removed);
    }
}
