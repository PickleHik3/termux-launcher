package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.settings.preferences.PreferenceDraft;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The Appearance editor's draft over the launcher's own store: the editor's handle writes the
 * draft, every other handle of the store shows it, and a look written from outside while the
 * editor is open (what {@code launcherctl look} does: a handle of its own) survives Done and
 * Discard.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class EditorDraftTest {

    private static final LookGrid.Key OPACITY =
        new LookGrid.Key(LookGrid.Property.OPACITY, null, LookGrid.NO_PAGE);
    private static final LookGrid.Key FROST =
        new LookGrid.Key(LookGrid.Property.FROST, null, LookGrid.NO_PAGE);

    private SharedPreferences mStore;
    /** What the activity's elements read, and what the editor opened over. */
    private TermuxAppSharedPreferences mLive;
    /** What the editor writes: the activity's handle writing the draft. */
    private TermuxAppSharedPreferences mEditor;
    private PreferenceDraft mDraft;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        mStore = context.getSharedPreferences("editor-draft-test", Context.MODE_PRIVATE);
        mStore.edit().clear().commit();
        mLive = new TermuxAppSharedPreferences(context, mStore, null);
        mLive.lookGrid().write(OPACITY, 40);
        mLive.lookGrid().write(FROST, 2);
        mDraft = PreferenceDraft.begin(mStore);
        mEditor = mLive.overDraft(mDraft);
    }

    @After
    public void tearDown() {
        PreferenceDraft.discardCurrent();
    }

    /** An outside writer's own handle, as {@code launcherctl look} builds one. */
    private TermuxAppSharedPreferences outside() {
        return new TermuxAppSharedPreferences(RuntimeEnvironment.getApplication(), mStore, null);
    }

    @Test
    public void whatTheEditorWritesShowsOnEveryOtherHandleAndIsNotStored() {
        mEditor.lookGrid().write(OPACITY, 90);

        assertEquals(90, mLive.lookGrid().value(OPACITY));
        assertEquals(90, outside().lookGrid().value(OPACITY));
        assertEquals("the store is untouched", 40, storedOpacity());
    }

    @Test
    public void aValueThatOnlyExistsInTheDraftIsSeenByAKeyPresenceRead() {
        assertNull(mLive.getHomePillPosition());
        mEditor.setHomePillPosition(0.25f, 0.75f);

        assertNotNull(mLive.getHomePillPosition());
        mDraft.discard();
        assertNull(mLive.getHomePillPosition());
    }

    @Test
    public void doneStoresTheDraftOnce() {
        mEditor.lookGrid().write(OPACITY, 90);
        mEditor.lookGrid().write(FROST, 3);
        assertTrue(mDraft.isDirty());

        mDraft.commit();

        assertEquals(90, storedOpacity());
        assertEquals(3, mLive.lookGrid().value(FROST));
        assertFalse(mDraft.isOpen());
    }

    @Test
    public void discardLeavesNothingToRestore() {
        mEditor.lookGrid().write(OPACITY, 90);

        mDraft.discard();

        assertEquals(40, storedOpacity());
        assertEquals(40, mLive.lookGrid().value(OPACITY));
        assertFalse(mDraft.isDirty());
    }

    @Test
    public void aLookWrittenFromOutsideSurvivesDoneAndDiscard() {
        mEditor.lookGrid().write(OPACITY, 90);
        // launcherctl look set frost 4: a cell the draft does not hold.
        outside().lookGrid().write(FROST, 4);
        assertEquals("shows at once", 4, mLive.lookGrid().value(FROST));

        mDraft.commit();

        assertEquals(4, mLive.lookGrid().value(FROST));
        assertEquals(90, storedOpacity());
    }

    @Test
    public void aLookWrittenFromOutsideSurvivesDiscard() {
        mEditor.lookGrid().write(OPACITY, 90);
        outside().lookGrid().write(FROST, 4);
        outside().lookGrid().write(OPACITY, 55);

        mDraft.discard();

        assertEquals(4, mLive.lookGrid().value(FROST));
        assertEquals("the outside value, not the one before the editor", 55,
            mLive.lookGrid().value(OPACITY));
    }

    @Test
    public void forACellBothTouchedTheDraftWinsUntilDone() {
        mEditor.lookGrid().write(OPACITY, 90);
        outside().lookGrid().write(OPACITY, 55);

        assertEquals(90, mLive.lookGrid().value(OPACITY));
        mDraft.commit();
        assertEquals(90, storedOpacity());
    }

    @Test
    public void settingAValueBackToItsSavedValueClearsTheUnsavedState() {
        mEditor.lookGrid().write(OPACITY, 90);
        assertTrue(mDraft.isDirty());
        mEditor.lookGrid().write(OPACITY, 40);
        assertFalse(mDraft.isDirty());
    }

    @Test
    public void thePerPlaceSizesStayTheLayoutStores() {
        final float[] heights = {1.5f};
        mLive.setPlaceSizes(new TermuxAppSharedPreferences.PlaceSizes() {
            @Override public float dockHeightScale() { return heights[0]; }
            @Override public void setDockHeightScale(float value) { heights[0] = value; }
            @Override public float keyboardHeightScale() { return 1f; }
            @Override public void setKeyboardHeightScale(float value) { }
            @Override public int keyboardChinDp() { return 0; }
            @Override public void setKeyboardChinDp(int value) { }
        });
        TermuxAppSharedPreferences editor = mLive.overDraft(mDraft);

        assertEquals(1.5f, editor.getAppLauncherBarHeightScale(), 0f);
        editor.setAppLauncherBarHeightScale(1.25f);
        assertEquals(1.25f, heights[0], 0f);
        assertFalse("the draft holds none of it", mDraft.isDirty());
    }

    private int storedOpacity() {
        return mStore.getInt(LookGrid.key(LookGrid.Property.OPACITY, null, LookGrid.NO_PAGE), -1);
    }
}
