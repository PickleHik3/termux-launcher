package com.termux.app.editorshell;

import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

/**
 * Both editors' cards {@code <include>} the shell's header, and both look their header's parts up
 * by the header's own ids. An {@code android:id} on the include tag replaces the included root's
 * id, so the lookups come back null, the panel counts itself incomplete and the editor silently
 * never opens — no crash, no log, nothing on screen. These inflate the real cards and pin every id
 * the controllers ask for.
 */
@RunWith(RobolectricTestRunner.class)
public class EditorShellHeaderIdTest {

    private View inflate(int layoutRes) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout host = new FrameLayout(activity);
        return LayoutInflater.from(activity).inflate(layoutRes, host, false);
    }

    /** The ids {@code SurfaceEditorController.Panel} requires before it will open. */
    @Test
    public void theAppearanceCardCarriesEveryHeaderIdItsPanelAsksFor() {
        View root = inflate(R.layout.surface_editor_pill);
        assertNotNull("header", root.findViewById(R.id.editor_shell_header));
        assertNotNull("glyph", root.findViewById(R.id.editor_shell_header_glyph));
        assertNotNull("title", root.findViewById(R.id.editor_shell_header_title));
        assertNotNull("eyebrow", root.findViewById(R.id.editor_shell_header_eyebrow));
        assertNotNull("save", root.findViewById(R.id.editor_shell_header_save));
        assertNotNull("revert", root.findViewById(R.id.editor_shell_header_revert));
        assertNotNull("done", root.findViewById(R.id.editor_shell_header_done));
        assertNotNull("close", root.findViewById(R.id.editor_shell_header_close));
        assertNotNull("chooser slot", root.findViewById(R.id.editor_shell_chooser_slot));
    }

    /** The ids {@code LayoutEditorController.Card} requires before it will open. */
    @Test
    public void theLayoutCardCarriesEveryHeaderIdItsCardAsksFor() {
        View root = inflate(R.layout.layout_editor);
        assertNotNull("header", root.findViewById(R.id.editor_shell_header));
        assertNotNull("title", root.findViewById(R.id.editor_shell_header_title));
        assertNotNull("eyebrow", root.findViewById(R.id.editor_shell_header_eyebrow));
        assertNotNull("revert", root.findViewById(R.id.editor_shell_header_revert));
        assertNotNull("discard", root.findViewById(R.id.editor_shell_header_discard));
        assertNotNull("done", root.findViewById(R.id.editor_shell_header_done));
    }
}
