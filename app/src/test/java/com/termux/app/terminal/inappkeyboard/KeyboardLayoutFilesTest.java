package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import juloo.keyboard2.KeyboardData;

/**
 * The files behind edited and user-made keyboard layouts: where each id lives, how a user layout
 * is named and numbered, the atomic write and the reset, and the source text the editor opens a
 * bundled layout with.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class KeyboardLayoutFilesTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Context mContext;
    private Resources mResources;
    private File mHome;

    @Before
    public void setUp() throws Exception {
        mContext = RuntimeEnvironment.getApplication();
        mResources = mContext.getResources();
        mHome = temporaryFolder.newFolder(".termux");
    }

    @Test
    public void mainKeepsLayoutXmlAndOthersGoUnderLayouts() {
        assertEquals(new File(mHome, "keyboard/layout.xml"),
            KeyboardLayoutFiles.overrideFile(mHome, LauncherKeyboardLayouts.LAYOUT_MAIN));
        assertEquals(new File(mHome, "keyboard/layouts/latn_dvorak.xml"),
            KeyboardLayoutFiles.overrideFile(mHome, "latn_dvorak"));
        assertEquals(new File(mHome, "keyboard/layouts/user-mine.xml"),
            KeyboardLayoutFiles.overrideFile(mHome, "user-mine"));
        assertEquals("~/.termux/keyboard/layout.xml",
            KeyboardLayoutFiles.displayPath(LauncherKeyboardLayouts.LAYOUT_MAIN));
    }

    @Test
    public void idsThatAreNotFileNamesHaveNoFile() {
        for (String id : Arrays.asList(null, "", "../layout", "a/b", ".hidden", "UPPER", "x.xml"))
            assertNull(id, KeyboardLayoutFiles.overrideFile(mHome, id));
    }

    @Test
    public void slugKeepsAsciiWordsAndNeverComesBackEmpty() {
        assertEquals("my-dvorak-2", KeyboardLayoutFiles.slug("  My Dvorak (2) "));
        assertEquals("layout", KeyboardLayoutFiles.slug("Русская"));
        assertEquals("layout", KeyboardLayoutFiles.slug(""));
        assertTrue(KeyboardLayoutFiles.slug("a very long layout name that goes on and on")
            .length() <= 32);
        assertFalse(KeyboardLayoutFiles.slug("a very long layout name that goes on and on")
            .endsWith("-"));
    }

    @Test
    public void newUserIdsAreNumberedPastTakenOnes() {
        assertEquals("user-mine", KeyboardLayoutFiles.newUserId("Mine", Collections.emptySet()));
        Set<String> taken = new HashSet<>(Arrays.asList("user-mine", "user-mine-2"));
        assertEquals("user-mine-3", KeyboardLayoutFiles.newUserId("Mine", taken));
    }

    @Test
    public void userIdsNeverCollideWithTheCatalogue() {
        for (LauncherKeyboardLayouts.Layout layout : LauncherKeyboardLayouts.catalog(mResources)) {
            assertFalse(layout.id, LauncherKeyboardLayouts.isUserLayout(layout.id));
            assertTrue(layout.id, KeyboardLayoutFiles.isSafeId(layout.id));
        }
        assertTrue(LauncherKeyboardLayouts.isUserLayout("user-mine"));
        assertFalse(LauncherKeyboardLayouts.isUserLayout("user-"));
    }

    @Test
    public void userLayoutsSurviveTheRingAndAreLabelledByName() {
        assertEquals(Arrays.asList("main", "user-mine"),
            LauncherKeyboardLayouts.parseSelection(mResources, "main,user-mine,user-../x"));
        assertEquals("mine", LauncherKeyboardLayouts.labelFor(mResources, "user-mine"));
        LauncherKeyboardLayouts.rememberUserLabel("user-mine", "My layout");
        assertEquals("My layout", LauncherKeyboardLayouts.labelFor(mResources, "user-mine"));
        LauncherKeyboardLayouts.rememberUserLabel("user-mine", null);
    }

    @Test
    public void writeReadDeleteRoundTrip() throws Exception {
        File file = KeyboardLayoutFiles.overrideFile(mHome, "latn_dvorak");
        KeyboardLayoutFiles.writeAtomically(file, "<keyboard/>");
        assertEquals("<keyboard/>", KeyboardLayoutFiles.read(file));
        KeyboardLayoutFiles.writeAtomically(file, "<keyboard name=\"x\"/>");
        assertEquals("<keyboard name=\"x\"/>", KeyboardLayoutFiles.read(file));
        // No temporary file is left next to it.
        assertEquals(1, file.getParentFile().listFiles().length);

        assertEquals(Collections.singleton("latn_dvorak"), KeyboardLayoutFiles.overriddenIds(mHome));
        assertTrue(KeyboardLayoutFiles.delete(file));
        assertFalse(file.exists());
        assertTrue(KeyboardLayoutFiles.overriddenIds(mHome).isEmpty());
    }

    @Test
    public void userLayoutsAreListedApartFromOverrides() throws Exception {
        KeyboardLayoutFiles.writeAtomically(
            KeyboardLayoutFiles.overrideFile(mHome, LauncherKeyboardLayouts.LAYOUT_MAIN), "<keyboard/>");
        KeyboardLayoutFiles.writeAtomically(
            KeyboardLayoutFiles.overrideFile(mHome, "user-b"), "<keyboard name=\"Beta\"/>");
        KeyboardLayoutFiles.writeAtomically(
            KeyboardLayoutFiles.overrideFile(mHome, "user-a"), "<keyboard/>");

        assertEquals(Collections.singleton(LauncherKeyboardLayouts.LAYOUT_MAIN),
            KeyboardLayoutFiles.overriddenIds(mHome));
        List<KeyboardLayoutFiles.UserLayout> users = KeyboardLayoutFiles.userLayouts(mHome);
        assertEquals(2, users.size());
        assertEquals("a", users.get(0).name);   // no name attribute: named from its id
        assertEquals("Beta", users.get(1).name);
    }

    @Test
    public void nameAttributeIsReadAndWrittenOnTheKeyboardTagOnly() {
        String xml = "<?xml version=\"1.0\"?>\n<!-- <keyboard name=\"no\"> -->\n"
            + "<keyboard script=\"latin\" name=\"Old &amp; gold\">\n<row><key c=\"a\" name=\"k\"/></row>\n</keyboard>";
        assertEquals("Old & gold", KeyboardLayoutFiles.nameAttribute(xml));
        String renamed = KeyboardLayoutFiles.withNameAttribute(xml, "A \"new\" <one>");
        assertEquals("A \"new\" <one>", KeyboardLayoutFiles.nameAttribute(renamed));
        assertTrue(renamed.contains("<!-- <keyboard name=\"no\"> -->"));
        assertTrue(renamed.contains("<key c=\"a\" name=\"k\"/>"));

        String unnamed = "<keyboard script=\"latin\"><row/></keyboard>";
        assertNull(KeyboardLayoutFiles.nameAttribute(unnamed));
        assertEquals("<keyboard name=\"Mine\" script=\"latin\"><row/></keyboard>",
            KeyboardLayoutFiles.withNameAttribute(unnamed, "Mine"));
        // A value merely containing name= is not the attribute.
        assertNull(KeyboardLayoutFiles.nameAttribute("<keyboard script=\" name='x'\"/>"));
    }

    @Test
    public void validationUsesTheKeyboardsOwnParser() {
        assertNull(KeyboardLayoutFiles.validationError(
            "<keyboard><row><key c=\"a\"/></row></keyboard>"));
        String error = KeyboardLayoutFiles.validationError("<keyboard>\n<rows/>\n</keyboard>");
        assertNotNull(error);
        assertTrue(error, error.startsWith("Line 2"));
        assertNotNull(KeyboardLayoutFiles.validationError("<board/>"));
    }

    @Test
    public void mainSeedDropsOnlyTheCopyItIntoPlaceParagraph() throws Exception {
        String seed = KeyboardLayoutFiles.mainSource(mContext, mHome);
        assertFalse(seed.contains("THIS FILE IS AN EXAMPLE"));
        assertTrue(seed.contains("KEY SLOTS"));
        assertNull(KeyboardLayoutFiles.validationError(seed));

        File main = KeyboardLayoutFiles.overrideFile(mHome, LauncherKeyboardLayouts.LAYOUT_MAIN);
        KeyboardLayoutFiles.writeAtomically(main, "<keyboard name=\"mine\"/>");
        assertEquals("<keyboard name=\"mine\"/>", KeyboardLayoutFiles.mainSource(mContext, mHome));
    }

    @Test
    public void bundledSourceRebuildsTheSameLayout() throws Exception {
        for (String id : Arrays.asList("latn_qwerty_us", "latn_dvorak", "latn_qwerty_bqn",
            "beng_assamese")) {
            LauncherKeyboardLayouts.Layout layout = LauncherKeyboardLayouts.find(mResources, id);
            assertNotNull(id, layout);
            String source = KeyboardLayoutFiles.bundledSource(mResources, layout.xmlResId);
            KeyboardData fromResource = KeyboardData.load(mResources, layout.xmlResId);
            KeyboardData fromText = KeyboardData.load_string_exn(source);
            // Slots read under the compass names the guide uses, never key0..key8.
            assertTrue(id, source.contains("<key c=\""));
            assertTrue(id, !source.matches("(?s).*\\skey[0-8]=.*"));
            assertEquals(id, fromResource.name, fromText.name);
            assertEquals(id, fromResource.rows.size(), fromText.rows.size());
            for (int row = 0; row < fromResource.rows.size(); row++) {
                List<KeyboardData.Key> expected = fromResource.rows.get(row).keys;
                List<KeyboardData.Key> actual = fromText.rows.get(row).keys;
                assertEquals(id + " row " + row, expected.size(), actual.size());
                for (int key = 0; key < expected.size(); key++) {
                    assertTrue(id + " row " + row + " key " + key, Arrays.equals(
                        expected.get(key).keys, actual.get(key).keys));
                    assertEquals(expected.get(key).width, actual.get(key).width, 0f);
                }
            }
        }
    }
}
