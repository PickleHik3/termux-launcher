package com.termux.app.fragments.settings.termux;

import static com.termux.app.fragments.settings.termux.ExtraKeysEditorHarness.findDescription;
import static com.termux.app.fragments.settings.termux.ExtraKeysEditorHarness.findEdit;
import static com.termux.app.fragments.settings.termux.ExtraKeysEditorHarness.findText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.EditText;

import com.termux.R;
import com.termux.app.terminal.io.ExtraKeysLayoutModel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

/**
 * Issue #164: on Page 2, which ships empty and absent from {@code termux.properties}, the reader
 * could not find a way to enter a new key. Page 2 is driven here from nothing to a saved key:
 * add the page, add a row, open the picker from the row's +, type a value, take it as text, save.
 * The file is not readable from the JVM sandbox; the page round-trip is covered by
 * ExtraKeysDraftTest and ExtraKeysLayoutModelTest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ExtraKeysEditorFragmentTest {

    private static String s(int res) {
        return ExtraKeysEditorHarness.string(res);
    }

    @Test
    public void pageTwoTakesATypedKeyAndSavesIt() {
        ExtraKeysEditorHarness h = new ExtraKeysEditorHarness();
        assertNull("page 2 is not there until it is added", findText(h.page(), "Page 2"));
        h.click(findDescription(h.page(), s(R.string.settings_extra_keys_add_page_cd)));
        assertNotNull(findText(h.page(), "Page 2"));
        assertNull("the + goes once both pages exist",
            findVisibleDescription(h, s(R.string.settings_extra_keys_add_page_cd)));

        h.click(findText(h.page(), s(R.string.settings_extra_keys_add_row)));
        assertNull("no keys yet", findText(h.page(), "zz9"));
        h.click(findText(h.page(), "+"));

        EditText search = findEdit(h.sheet());
        assertNotNull(search);
        search.setText("zz9");
        h.idle();
        View typed = findText(h.sheet(), "Send \"zz9\" as text");
        assertNotNull("typed text is offered as a key", typed);
        h.click(typed);
        h.click(findText(h.sheet(), "Add to row: zz9"));

        assertNotNull("the new key is a cap on page 2", findText(h.page(), "zz9"));
        assertTrue(h.fragment.hasUnsavedChanges());
        h.click(findText(h.page(), s(R.string.settings_extra_keys_save_changes)));
        assertFalse("Save committed the draft", h.fragment.hasUnsavedChanges());
    }

    @Test
    public void addingThenRemovingTheSamePageLeavesNothingToSave() {
        ExtraKeysEditorHarness h = new ExtraKeysEditorHarness();
        assertFalse(h.fragment.hasUnsavedChanges());
        h.click(findDescription(h.page(), s(R.string.settings_extra_keys_add_page_cd)));
        h.click(findText(h.page(), s(R.string.settings_extra_keys_add_row)));
        assertFalse("an empty row is not an edit", h.fragment.hasUnsavedChanges());
    }

    @Test
    public void aKeyEditedThroughTheDraftMarksTheEditorDirtyAndBackAgainClean() throws Exception {
        ExtraKeysEditorHarness h = new ExtraKeysEditorHarness();
        ExtraKeysLayoutModel.Key[] added = new ExtraKeysLayoutModel.Key[1];
        h.editDraft(draft -> {
            added[0] = new ExtraKeysLayoutModel.Key("Q");
            draft.addKey(0, 0, added[0]);
        });
        assertTrue(h.fragment.hasUnsavedChanges());
        assertNotNull(findText(h.page(), "Q"));
        h.editDraft(draft -> draft.removeKey(0, added[0]));
        assertFalse(h.fragment.hasUnsavedChanges());
        assertEquals(null, findText(h.page(), "Q"));
    }

    /** The + beside the chips is hidden, not removed, once both pages exist. */
    private static View findVisibleDescription(ExtraKeysEditorHarness h, String description) {
        View v = findDescription(h.page(), description);
        return v != null && v.getVisibility() == View.VISIBLE ? v : null;
    }
}
