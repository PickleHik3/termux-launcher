package com.termux.app.launcher.folder;

import static org.junit.Assert.*;
import android.app.Application;
import android.os.Build;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import com.termux.app.launcher.data.LauncherFolderMutator;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.model.PinnedItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LauncherFolderPopupTest {
    @Test public void sharedControllerForcesFocuslessImeFlagsAndCleansOnce() {
        LauncherFolderPopupController controller = new LauncherFolderPopupController();
        PopupWindow popup = new PopupWindow(new FrameLayout(RuntimeEnvironment.getApplication()),
            100, 100, true);
        AtomicInteger dismisses = new AtomicInteger();
        controller.show(popup, "folder", () -> {}, dismisses::incrementAndGet);
        assertFalse(popup.isFocusable());
        assertEquals(PopupWindow.INPUT_METHOD_NOT_NEEDED, popup.getInputMethodMode());
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED, popup.getSoftInputMode());
        assertEquals("folder", controller.folderId());
        controller.dismissImmediate();
        controller.dismissImmediate();
        assertEquals(1, dismisses.get());
        assertNull(controller.folderId());
    }

    @Test public void hideForDragKeepsWindowAliveButUntouchableUntilDismissed() {
        LauncherFolderPopupController controller = new LauncherFolderPopupController();
        PopupWindow popup = new PopupWindow(new FrameLayout(RuntimeEnvironment.getApplication()),
            100, 100, true);
        AtomicInteger dismisses = new AtomicInteger();
        controller.show(popup, "folder", () -> {}, dismisses::incrementAndGet);
        controller.hideForDrag();
        // The window must survive the drag it sourced: still owned, just muted.
        assertEquals("folder", controller.folderId());
        assertFalse(popup.isTouchable());
        assertEquals(0f, popup.getContentView().getAlpha(), 0.001f);
        assertEquals(0, dismisses.get());
        controller.dismissImmediate();
        assertEquals(1, dismisses.get());
        assertNull(controller.folderId());
    }

    @Test public void dragOutMovesMemberFromFolderToDockSlot() {
        PinnedAppItem a = app("a");
        PinnedAppItem b = app("b");
        PinnedAppItem c = app("c");
        PinnedFolderItem folder = folder("f1", a, b, c);
        List<PinnedItem> dock = new ArrayList<>();
        dock.add(folder);

        assertEquals(LauncherFolderMutator.AppendResult.MISSING,
            LauncherFolderMutator.moveFolderAppToTopLevel(dock, folder, "missing/", 1));

        assertEquals(LauncherFolderMutator.AppendResult.APPLIED,
            LauncherFolderMutator.moveFolderAppToTopLevel(dock, folder,
                b.appRef.stableId(), 1));
        assertEquals(2, dock.size());
        assertSame(folder, dock.get(0));
        assertSame(b, dock.get(1));
        assertEquals(2, folder.apps.size());
        assertFalse(folder.containsApp(b.appRef));
    }

    @Test public void dragOutLeavingOneMemberCollapsesFolderOnNormalize() {
        PinnedAppItem a = app("a");
        PinnedAppItem b = app("b");
        PinnedFolderItem folder = folder("f1", a, b);
        List<PinnedItem> dock = new ArrayList<>();
        dock.add(folder);
        LinkedHashMap<String, PinnedFolderItem> folders = new LinkedHashMap<>();
        folders.put(folder.id, folder);

        assertEquals(LauncherFolderMutator.AppendResult.APPLIED,
            LauncherFolderMutator.moveFolderAppToTopLevel(dock, folder,
                b.appRef.stableId(), dock.size()));
        LauncherFolderMutator.normalize(dock, folders);

        // The one-member folder collapses: its dock reference becomes the surviving app.
        assertTrue(folders.isEmpty());
        assertEquals(2, dock.size());
        assertSame(a, dock.get(0));
        assertSame(b, dock.get(1));
    }

    private static PinnedAppItem app(String packageName) {
        return new PinnedAppItem(new AppRef(packageName, packageName + ".Main"));
    }

    private static PinnedFolderItem folder(String id, PinnedAppItem... apps) {
        PinnedFolderItem folder = new PinnedFolderItem(id, "Folder");
        for (PinnedAppItem app : apps) folder.apps.add(app);
        return folder;
    }
}
