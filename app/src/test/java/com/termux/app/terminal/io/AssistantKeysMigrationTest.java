package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Assistant keeps the row it had built in, written once; Display writes nothing; Global is global. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AssistantKeysMigrationTest {

    private static final String ASSISTANT = TermuxPropertyConstants.KEY_EXTRA_KEYS_ASSISTANT;

    private final Map<String, String> file = new HashMap<>();
    private final List<String> writes = new ArrayList<>();

    private SharedPreferences prefs() {
        return RuntimeEnvironment.getApplication().getSharedPreferences("assistant-keys-test", Context.MODE_PRIVATE);
    }

    private boolean run(SharedPreferences prefs) {
        return AssistantKeysMigration.run(prefs, file::get, (k, v) -> {
            file.put(k, v);
            writes.add(k);
        });
    }

    @Test
    public void writesTheFormerBuiltInRowAsAssistantsOwn() {
        assertTrue(run(prefs()));
        assertEquals(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_ASSISTANT, file.get(ASSISTANT));
        ExtraKeySetResolver.Resolved r = ExtraKeySetResolver.resolve(ExtraKeysTarget.ASSISTANT, file::get);
        assertEquals(ExtraKeySetResolver.Source.OWN, r.source);
    }

    @Test
    public void writesOnlyAssistantsRow() {
        run(prefs());
        assertEquals(1, writes.size());
        assertEquals(ASSISTANT, writes.get(0));
        assertEquals(ExtraKeySetResolver.Source.GLOBAL,
            ExtraKeySetResolver.resolve(ExtraKeysTarget.DISPLAY, file::get).source);
    }

    @Test
    public void doesNotWriteAgainAfterTheUserClearsIt() {
        SharedPreferences prefs = prefs();
        assertTrue(run(prefs));
        file.remove(ASSISTANT);
        assertFalse(run(prefs));
        assertFalse(file.containsKey(ASSISTANT));
        assertEquals(1, writes.size());
    }

    @Test
    public void keepsARowTheUserAlreadyHasAndNeverWritesItLater() {
        SharedPreferences prefs = prefs();
        file.put(ASSISTANT, "[[ESC]]");
        assertFalse(run(prefs));
        assertEquals("[[ESC]]", file.get(ASSISTANT));
        file.remove(ASSISTANT);
        assertFalse("the flag was set on the first run", run(prefs));
        assertTrue(writes.isEmpty());
    }

    @Test
    public void aBlankLineCountsAsNoRow() {
        file.put(ASSISTANT, "   ");
        assertTrue(run(prefs()));
        assertEquals(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_ASSISTANT, file.get(ASSISTANT));
    }

    @Test
    public void ownPagesListsOnlyPagesWithANonBlankRow() {
        file.put(ASSISTANT, "[[ESC]]");
        file.put(TermuxPropertyConstants.KEY_EXTRA_KEYS_HOME, "[[TAB]]");
        file.put(TermuxPropertyConstants.KEY_EXTRA_KEYS_DISPLAY, " ");
        List<ExtraKeysTarget> own = ExtraKeysOwnPages.of(file::get);
        assertEquals(2, own.size());
        assertEquals(ExtraKeysTarget.HOME, own.get(0));
        assertEquals(ExtraKeysTarget.ASSISTANT, own.get(1));
    }
}
