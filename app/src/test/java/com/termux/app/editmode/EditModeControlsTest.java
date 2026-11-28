package com.termux.app.editmode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.look.TerlLookIds;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditModeControlsTest {

    @Test
    public void aSurfacesOwnValuesLiveOnItsCard() {
        assertEquals(EditModeController.Card.BAR,
            EditModeControls.cardOf(TerlLookIds.of(TerlSurface.CONTROL_BAR, TerlProperty.OPACITY)));
        assertEquals(EditModeController.Card.KEYBOARD,
            EditModeControls.cardOf(TerlLookIds.of(TerlSurface.KEYBOARD, TerlProperty.BLUR)));
        assertEquals(EditModeController.Card.WINDOWS,
            EditModeControls.cardOf(TerlLookIds.of(TerlSurface.WINDOWS, TerlProperty.OPACITY)));
    }

    @Test
    public void theLookCornersTintAndCustomLiveOnTheGlobalCard() {
        assertEquals(EditModeController.Card.GLOBAL, EditModeControls.cardOf(TerlLookIds.LOOK));
        assertEquals(EditModeController.Card.GLOBAL, EditModeControls.cardOf(TerlLookIds.CORNERS));
        assertEquals(EditModeController.Card.GLOBAL, EditModeControls.cardOf(TerlLookIds.TINT));
        assertEquals(EditModeController.Card.GLOBAL,
            EditModeControls.cardOf(TerlLookIds.ofCustom(TerlProperty.BLUR)));
    }

    @Test
    public void everyRowAndItsCard() {
        assertEquals(EditModeController.Card.BAR, EditModeControls.cardOf("bar.margin"));
        assertEquals(EditModeController.Card.WINDOWS, EditModeControls.cardOf("windows.gap"));
        assertEquals(EditModeController.Card.HOME, EditModeControls.cardOf("home.az_edge"));
        assertEquals(EditModeController.Card.HOME,
            EditModeControls.of(EditSession.Setting.COLUMNS).card);
        assertEquals(EditModeController.Card.WALLPAPER,
            EditModeControls.of(EditSession.Setting.DIM).card);
        assertNull(EditModeControls.cardOf("look.sheets.opacity"));
    }

    private static String source() throws IOException {
        for (String root : new String[] {"src/main", "app/src/main"}) {
            Path path = Paths.get(root, "java/com/termux/app/editmode/EditModeController.java");
            if (Files.exists(path)) return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
        throw new IOException("EditModeController.java not found");
    }

    private static String body(String source, String method) {
        int start = source.indexOf(method + "(@NonNull Context context)");
        assertTrue(method, start >= 0);
        return source.substring(start, source.indexOf("\n    }\n", start));
    }

    private void assertPlaces(String body, EditModeController.Card card) throws Exception {
        Matcher setting = Pattern.compile("EditSession\\.Setting\\.([A-Z_]+)\\b(?!\\()").matcher(body);
        while (setting.find()) {
            java.lang.reflect.Field field = EditSession.Setting.class.getDeclaredField(setting.group(1));
            field.setAccessible(true);
            EditModeControls.Control control = EditModeControls.of((EditSession.Setting) field.get(null));
            assertNotNull(setting.group(1) + " is on the " + card + " card but not in EditModeControls", control);
            assertTrue(setting.group(1), control.card == card || control.alsoOn == card);
        }
        Matcher own = Pattern.compile("TerlSurface\\.([A-Z_]+), TerlProperty\\.([A-Z_]+)").matcher(body);
        while (own.find()) {
            EditModeControls.Control control = EditModeControls.of(EditSession.Setting.own(
                TerlSurface.valueOf(own.group(1)), TerlProperty.valueOf(own.group(2))));
            assertNotNull(own.group(), control);
            assertEquals(own.group(), card, control.card);
        }
        Matcher keyed = Pattern.compile("Keyed\\.([A-Z_]+)\\b").matcher(body);
        while (keyed.find()) {
            String key = (String) EditSession.Keyed.class.getField(keyed.group(1)).get(null);
            boolean found = false;
            for (EditModeControls.Control control : EditModeControls.all()) {
                found |= control.card == card && (control.setting.equals(EditSession.Setting.keyedNumber(key))
                    || control.setting.equals(EditSession.Setting.keyedChoice(key)));
            }
            assertTrue(keyed.group() + " is on the " + card + " card but not in EditModeControls", found);
        }
        if (card == EditModeController.Card.GLOBAL) {
            for (TerlProperty property : TerlProperty.values()) {
                assertNotNull(property.name(), EditModeControls.of(EditSession.Setting.custom(property)));
            }
        }
    }

    /** The card builders place only what the one list puts on their card (Custom included). */
    @Test
    public void theCardBuildersPlaceWhatTheListPutsOnTheirCard() throws Exception {
        String source = source();
        assertPlaces(body(source, "buildKeyboardCard"), EditModeController.Card.KEYBOARD);
        assertPlaces(body(source, "buildGlobalCard"), EditModeController.Card.GLOBAL);
        assertPlaces(body(source, "buildWallpaperCard"), EditModeController.Card.WALLPAPER);
        assertPlaces(body(source, "buildHomeCard"), EditModeController.Card.HOME);
        assertPlaces(body(source, "buildBarCard"), EditModeController.Card.BAR);
        assertPlaces(body(source, "buildWindowsCard"), EditModeController.Card.WINDOWS);
    }
}
