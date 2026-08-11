package com.termux.app.launcher.folder;

import static org.junit.Assert.*;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;
import com.termux.app.launcher.data.LauncherConfigRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class FolderRenameInputRoutingTest {
    @Test public void focuslessViewNeverClaimsEditorOrInputConnection() {
        FolderRenameTitleView view = new FolderRenameTitleView(RuntimeEnvironment.getApplication());
        assertFalse(view.onCheckIsTextEditor());
        assertNull(view.onCreateInputConnection(null));
        assertFalse(view.isFocusable());
    }

    @Test public void commitCancelDismissPauseDeleteAndStaleAllRestoreExactlyOnce() {
        assertExit(Exit.COMMIT, LauncherConfigRepository.MutationResult.APPLIED, true);
        assertExit(Exit.CANCEL, LauncherConfigRepository.MutationResult.APPLIED, false);
        assertExit(Exit.DISMISS, LauncherConfigRepository.MutationResult.APPLIED, false);
        assertExit(Exit.PAUSE, LauncherConfigRepository.MutationResult.APPLIED, false);
        assertExit(Exit.DELETE, LauncherConfigRepository.MutationResult.APPLIED, false);
        assertExit(Exit.COMMIT, LauncherConfigRepository.MutationResult.STALE, false);
    }

    @Test public void hardwareAndCodePointChannelsSwallowPairedInputWhileActive() {
        Host host = new Host(LauncherConfigRepository.MutationResult.APPLIED);
        FolderRenameController controller = new FolderRenameController();
        controller.begin(3, "f", "X", host);
        assertTrue(controller.handleCodePoint('a', false));
        assertTrue(controller.handleKeyDown(KeyEvent.KEYCODE_DEL,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)));
        assertEquals("X", controller.model().text());
        assertTrue(controller.handleKeyDown(KeyEvent.KEYCODE_A,
            new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A)));
        controller.cancel();
        assertFalse(controller.handleCodePoint('z', false));
    }

    private enum Exit { COMMIT, CANCEL, DISMISS, PAUSE, DELETE }
    private static void assertExit(Exit exit, LauncherConfigRepository.MutationResult result,
                                   boolean committed) {
        Host host = new Host(result);
        FolderRenameController controller = new FolderRenameController();
        controller.begin(7, "folder", "Old", host);
        switch (exit) {
            case COMMIT: controller.commit(); break;
            case CANCEL: controller.cancel(); break;
            case DISMISS: controller.onPopupDismissed(); break;
            case PAUSE: controller.onActivityPaused(); break;
            case DELETE: controller.onFolderDeleted("folder"); break;
        }
        assertFalse(controller.isActive());
        assertEquals(1, host.ends);
        assertEquals(committed, host.committed);
        controller.cancel();
        assertEquals(1, host.ends);
    }

    private static final class Host implements FolderRenameController.Host {
        final LauncherConfigRepository.MutationResult result;
        int ends;
        boolean committed;
        Host(LauncherConfigRepository.MutationResult result) { this.result = result; }
        @Override public LauncherConfigRepository.MutationResult commit(long revision,
                String folderId, String title) { return result; }
        @Override public void onDraftChanged(FolderRenameModel model) {}
        @Override public void onRenameEnded(boolean committed) {
            ends++; this.committed = committed;
        }
    }
}
