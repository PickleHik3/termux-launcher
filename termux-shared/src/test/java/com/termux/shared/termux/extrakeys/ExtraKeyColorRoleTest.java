package com.termux.shared.termux.extrakeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;

import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * The colour a key can wear: the token it is stored as, the theme attributes it resolves through,
 * and the way a key row reads one back out of its JSON.
 */
@RunWith(RobolectricTestRunner.class)
public class ExtraKeyColorRoleTest {

    @Test
    public void everyRoleRoundTripsThroughItsToken() {
        for (ExtraKeyColorRole role : ExtraKeyColorRole.values()) {
            assertSame(role.token, role, ExtraKeyColorRole.fromToken(role.token));
            assertEquals(role.token, ExtraKeyColorRole.tokenOf(role));
            // A stored file is read back whatever case it was written in.
            assertSame(role.token, role,
                ExtraKeyColorRole.fromToken(role.token.toUpperCase(java.util.Locale.ROOT)));
            assertSame(role.token, role, ExtraKeyColorRole.fromToken("  " + role.token + " "));
        }
    }

    @Test
    public void absentAndUnknownTokensAreNoColour() {
        assertNull(ExtraKeyColorRole.fromToken(null));
        assertNull(ExtraKeyColorRole.fromToken(""));
        assertNull(ExtraKeyColorRole.fromToken("   "));
        assertNull(ExtraKeyColorRole.fromToken("chartreuse"));
        assertNull(ExtraKeyColorRole.tokenOf(null));
    }

    @Test
    public void theElevenTokensAreTheOnesTheEditorOffers() {
        String[] expected = {
            "primary", "secondary", "tertiary", "error",
            "primary_container", "secondary_container", "tertiary_container", "error_container",
            "surface_variant", "black", "white",
        };
        assertEquals(expected.length, ExtraKeyColorRole.values().length);
        for (int i = 0; i < expected.length; i++)
            assertEquals(expected[i], ExtraKeyColorRole.values()[i].token);
    }

    @Test
    public void eachThemedRoleTakesItsOwnAttributeAndTheMatchingOnRole() {
        assertAttrs(ExtraKeyColorRole.PRIMARY,
            com.google.android.material.R.attr.colorPrimary,
            com.google.android.material.R.attr.colorOnPrimary);
        assertAttrs(ExtraKeyColorRole.SECONDARY,
            com.google.android.material.R.attr.colorSecondary,
            com.google.android.material.R.attr.colorOnSecondary);
        assertAttrs(ExtraKeyColorRole.TERTIARY,
            com.google.android.material.R.attr.colorTertiary,
            com.google.android.material.R.attr.colorOnTertiary);
        assertAttrs(ExtraKeyColorRole.ERROR,
            com.google.android.material.R.attr.colorError,
            com.google.android.material.R.attr.colorOnError);
        assertAttrs(ExtraKeyColorRole.PRIMARY_CONTAINER,
            com.google.android.material.R.attr.colorPrimaryContainer,
            com.google.android.material.R.attr.colorOnPrimaryContainer);
        assertAttrs(ExtraKeyColorRole.SECONDARY_CONTAINER,
            com.google.android.material.R.attr.colorSecondaryContainer,
            com.google.android.material.R.attr.colorOnSecondaryContainer);
        assertAttrs(ExtraKeyColorRole.TERTIARY_CONTAINER,
            com.google.android.material.R.attr.colorTertiaryContainer,
            com.google.android.material.R.attr.colorOnTertiaryContainer);
        assertAttrs(ExtraKeyColorRole.ERROR_CONTAINER,
            com.google.android.material.R.attr.colorErrorContainer,
            com.google.android.material.R.attr.colorOnErrorContainer);
        assertAttrs(ExtraKeyColorRole.SURFACE_VARIANT,
            com.google.android.material.R.attr.colorSurfaceVariant,
            com.google.android.material.R.attr.colorOnSurfaceVariant);
    }

    @Test
    public void theTwoFixedRolesAreEachOthersLabels() {
        android.content.Context context = RuntimeEnvironment.getApplication();
        assertEquals(0, ExtraKeyColorRole.BLACK.backgroundAttr());
        assertEquals(0, ExtraKeyColorRole.WHITE.backgroundAttr());
        assertEquals(Color.BLACK, ExtraKeyColorRole.BLACK.background(context));
        assertEquals(Color.WHITE, ExtraKeyColorRole.BLACK.label(context));
        assertEquals(Color.WHITE, ExtraKeyColorRole.WHITE.background(context));
        assertEquals(Color.BLACK, ExtraKeyColorRole.WHITE.label(context));
    }

    @Test
    public void everyRoleResolvesToAnOpaqueCapAndALegibleLabel() {
        android.content.Context context = RuntimeEnvironment.getApplication();
        for (ExtraKeyColorRole role : ExtraKeyColorRole.values()) {
            int background = role.background(context);
            int label = role.label(context);
            assertEquals(role.token, 255, Color.alpha(background));
            assertEquals(role.token, 255, Color.alpha(label));
            // A cap whose label is its own fill would be an unreadable key.
            assertNotEquals(role.token, background, label);
        }
    }

    @Test
    public void aKeyReadsItsColourOutOfItsConfig() throws Exception {
        JSONObject config = new JSONObject();
        config.put(ExtraKeyButton.KEY_KEY_NAME, "ESC");
        config.put(ExtraKeyButton.KEY_COLOR, "tertiary_container");
        ExtraKeyButton button = new ExtraKeyButton(config,
            ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
            ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        assertSame(ExtraKeyColorRole.TERTIARY_CONTAINER, button.getColor());
    }

    @Test
    public void aKeyWithNoColourOrAnUnreadableOneKeepsTheRowsStyling() throws Exception {
        assertNull(colorOf(new JSONObject().put(ExtraKeyButton.KEY_KEY_NAME, "ESC")));
        assertNull(colorOf(new JSONObject()
            .put(ExtraKeyButton.KEY_KEY_NAME, "ESC")
            .put(ExtraKeyButton.KEY_COLOR, "puce")));
    }

    @Test
    public void theShippedRowColoursTheThreePlaceSwitches() throws Exception {
        ExtraKeysInfo info = new ExtraKeysInfo(
            com.termux.shared.termux.settings.properties.TermuxPropertyConstants
                .DEFAULT_IVALUE_EXTRA_KEYS,
            com.termux.shared.termux.settings.properties.TermuxPropertyConstants
                .DEFAULT_IVALUE_EXTRA_KEYS_STYLE,
            ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        ExtraKeyButton[] row = info.getMatrix()[0];
        for (ExtraKeyButton key : row) {
            switch (key.getKey()) {
                case "tool:wall.widgets":
                    assertSame(ExtraKeyColorRole.PRIMARY, key.getColor());
                    break;
                case "tool:wall.terminal":
                    assertSame(ExtraKeyColorRole.SECONDARY, key.getColor());
                    break;
                case "tool:wall.display":
                    assertSame(ExtraKeyColorRole.TERTIARY, key.getColor());
                    break;
                default:
                    // Everything else ships with the row's own styling.
                    assertNull(key.getKey(), key.getColor());
                    break;
            }
        }
    }

    @Nullable
    private static ExtraKeyColorRole colorOf(JSONObject config) throws Exception {
        return new ExtraKeyButton(config,
            ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
            ExtraKeysConstants.CONTROL_CHARS_ALIASES).getColor();
    }

    private static void assertAttrs(ExtraKeyColorRole role, int backgroundAttr, int labelAttr) {
        assertEquals(role.token, backgroundAttr, role.backgroundAttr());
        assertEquals(role.token, labelAttr, role.labelAttr());
        assertTrue(role.token, role.backgroundAttr() != 0 && role.labelAttr() != 0);
    }
}
