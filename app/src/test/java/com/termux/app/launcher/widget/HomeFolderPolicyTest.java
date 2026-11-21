package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.os.Bundle;

import com.termux.app.launcher.folder.FolderRenameModel;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.widget.LauncherWidgetRecord.FolderApp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The rules of folders on Home: merge, add, remove, rename, the cap and the drop target. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = android.app.Application.class)
public class HomeFolderPolicyTest {
    private static final WidgetCellRect CELL = new WidgetCellRect(0, 0, 1, 1);

    private static FolderApp app(String name) {
        return new FolderApp(new AppRef("com." + name, "com." + name + ".Main"), name);
    }

    private static LauncherWidgetRecord icon(int id, String name) {
        Bundle options = new Bundle();
        options.putString(LauncherWidgetRecord.APP_LABEL, name);
        return LauncherWidgetRecord.app(id, new AppRef("com." + name, "com." + name + ".Main"),
            CELL, 0, options);
    }

    private static LauncherWidgetRecord folder(int id, FolderApp... apps) {
        return LauncherWidgetRecord.folder(id, "Tools", Arrays.asList(apps), CELL, 0);
    }

    private static List<FolderApp> many(int count) {
        List<FolderApp> apps = new ArrayList<>();
        for (int i = 0; i < count; i++) apps.add(app("app" + i));
        return apps;
    }

    @Test public void mergePutsTheDroppedAppFirstAndNamesTheFolderAfterIt() {
        HomeFolderPolicy.Contents made = HomeFolderPolicy.merge(app("Maps"), app("Clock"),
            "Folder");
        assertNotNull(made);
        assertEquals("Maps", made.name);
        assertEquals(2, made.apps.size());
        assertEquals("com.Maps", made.apps.get(0).app().packageName);
        assertEquals("com.Clock", made.apps.get(1).app().packageName);
    }

    @Test public void mergeOfAnAppWithNoLabelFallsBackToTheGivenName() {
        FolderApp bare = new FolderApp(new AppRef("com.bare", "com.bare.Main"), "  ");
        HomeFolderPolicy.Contents made = HomeFolderPolicy.merge(bare, app("Clock"), "Ordner");
        assertNotNull(made);
        assertEquals("Ordner", made.name);
    }

    @Test public void theSameAppTwiceMakesNoFolder() {
        assertNull(HomeFolderPolicy.merge(app("Maps"), app("Maps"), "Folder"));
    }

    @Test public void addAppendsAndRefusesADuplicate() {
        List<FolderApp> two = Arrays.asList(app("A"), app("B"));
        List<FolderApp> three = HomeFolderPolicy.add(two, app("C"));
        assertNotNull(three);
        assertEquals(3, three.size());
        assertEquals("C", three.get(2).label);
        assertNull("already there", HomeFolderPolicy.add(three, app("B")));
        assertEquals("the input is left as it was", 2, two.size());
    }

    @Test public void theCapIsSixteenApps() {
        assertEquals(16, HomeFolderPolicy.MAX_APPS);
        List<FolderApp> fifteen = many(15);
        List<FolderApp> sixteen = HomeFolderPolicy.add(fifteen, app("last"));
        assertNotNull(sixteen);
        assertEquals(16, sixteen.size());
        assertNull("full", HomeFolderPolicy.add(sixteen, app("more")));
        assertFalse(HomeFolderPolicy.canAdd(sixteen, app("more")));
    }

    @Test public void removeLeavesAFolderWhileTwoOrMoreRemain() {
        HomeFolderPolicy.Removal removal = HomeFolderPolicy.remove(
            Arrays.asList(app("A"), app("B"), app("C")), app("B").stableId());
        assertEquals(HomeFolderPolicy.Removal.Outcome.FOLDER, removal.outcome);
        assertEquals(2, removal.remaining.size());
        assertEquals("A", removal.remaining.get(0).label);
        assertEquals("C", removal.remaining.get(1).label);
        assertEquals("B", removal.taken.label);
        assertNull(removal.lastApp);
    }

    @Test public void removeOfOneOfTwoLeavesTheOtherAppsIcon() {
        HomeFolderPolicy.Removal removal = HomeFolderPolicy.remove(
            Arrays.asList(app("A"), app("B")), app("A").stableId());
        assertEquals(HomeFolderPolicy.Removal.Outcome.ICON, removal.outcome);
        assertEquals("B", removal.lastApp.label);
        assertEquals("A", removal.taken.label);
        assertTrue(removal.remaining.isEmpty());
    }

    @Test public void removeOfTheOnlyAppLeavesNothing() {
        HomeFolderPolicy.Removal removal = HomeFolderPolicy.remove(
            Arrays.asList(app("A")), app("A").stableId());
        assertEquals(HomeFolderPolicy.Removal.Outcome.GONE, removal.outcome);
        assertEquals("A", removal.taken.label);
    }

    @Test public void removeOfAnAppNotThereChangesNothing() {
        HomeFolderPolicy.Removal removal = HomeFolderPolicy.remove(
            Arrays.asList(app("A"), app("B")), app("Z").stableId());
        assertEquals(HomeFolderPolicy.Removal.Outcome.MISSING, removal.outcome);
        assertNull(removal.taken);
    }

    @Test public void aNameIsTrimmedCappedAndNeverBlank() {
        assertEquals("Games", HomeFolderPolicy.name("  Games \n", "Folder"));
        assertEquals("Folder", HomeFolderPolicy.name("   ", "Folder"));
        assertEquals("Folder", HomeFolderPolicy.name(null, "Folder"));
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 50; i++) longName.append('é');
        String capped = HomeFolderPolicy.name(longName.toString(), "Folder");
        assertEquals(FolderRenameModel.MAX_CODE_POINTS,
            capped.codePointCount(0, capped.length()));
    }

    @Test public void anIconOverAnotherIconMergesOnlyAfterTheHover() {
        LauncherWidgetRecord dragged = icon(-1, "A");
        LauncherWidgetRecord target = icon(-2, "B");
        assertEquals(HomeFolderPolicy.Drop.MOVE, HomeFolderPolicy.dropAction(dragged, target,
            HomeFolderPolicy.MERGE_HOVER_MS - 1));
        assertEquals(HomeFolderPolicy.Drop.CREATE_FOLDER, HomeFolderPolicy.dropAction(dragged,
            target, HomeFolderPolicy.MERGE_HOVER_MS));
        assertEquals(300L, HomeFolderPolicy.MERGE_HOVER_MS);
    }

    @Test public void anIconOverAFolderIsAdded() {
        LauncherWidgetRecord dragged = icon(-1, "C");
        LauncherWidgetRecord target = folder(-2, app("A"), app("B"));
        assertSame(HomeFolderPolicy.Drop.ADD_TO_FOLDER,
            HomeFolderPolicy.dropAction(dragged, target, 1000L));
    }

    @Test public void aFullFolderOrOneHoldingTheAppIsNoTarget() {
        LauncherWidgetRecord full = LauncherWidgetRecord.folder(-2, "Full", many(16), CELL, 0);
        assertFalse(HomeFolderPolicy.canTarget(icon(-1, "new"), full));
        LauncherWidgetRecord holding = folder(-3, app("A"), app("B"));
        assertFalse(HomeFolderPolicy.canTarget(icon(-1, "A"), holding));
        assertEquals(HomeFolderPolicy.Drop.MOVE,
            HomeFolderPolicy.dropAction(icon(-1, "A"), holding, 1000L));
    }

    @Test public void nothingMergesIntoAWidgetABuiltinOrFromAFolder() {
        LauncherWidgetRecord dragged = icon(-1, "A");
        LauncherWidgetRecord widget = new LauncherWidgetRecord(7, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, CELL, 0, null, null);
        LauncherWidgetRecord builtin = LauncherWidgetRecord.builtin(-5, "battery", CELL, 0, null);
        assertEquals(HomeFolderPolicy.Drop.MOVE,
            HomeFolderPolicy.dropAction(dragged, widget, 1000L));
        assertEquals(HomeFolderPolicy.Drop.MOVE,
            HomeFolderPolicy.dropAction(dragged, builtin, 1000L));
        assertEquals(HomeFolderPolicy.Drop.MOVE,
            HomeFolderPolicy.dropAction(dragged, null, 1000L));
        LauncherWidgetRecord folderDragged = folder(-6, app("X"), app("Y"));
        assertEquals("a folder dragged onto an icon moves", HomeFolderPolicy.Drop.MOVE,
            HomeFolderPolicy.dropAction(folderDragged, icon(-2, "B"), 1000L));
        assertEquals(HomeFolderPolicy.Drop.MOVE,
            HomeFolderPolicy.dropAction(folderDragged, folder(-7, app("A"), app("B")), 1000L));
    }

    @Test public void anIconIsNoTargetForItselfOrTheSameApp() {
        LauncherWidgetRecord dragged = icon(-1, "A");
        assertFalse(HomeFolderPolicy.canTarget(dragged, dragged));
        assertFalse("another icon of the same app", HomeFolderPolicy.canTarget(dragged,
            icon(-2, "A")));
    }

    @Test public void aFolderRecordNeedsANameAndAnApp() {
        try {
            LauncherWidgetRecord.folder(-1, "Empty", new ArrayList<FolderApp>(), CELL, 0);
            throw new AssertionError("an empty folder was built");
        } catch (IllegalArgumentException expected) { }
        try {
            LauncherWidgetRecord.folder(4, "Positive", many(2), CELL, 0);
            throw new AssertionError("a folder with a positive key was built");
        } catch (IllegalArgumentException expected) { }
        LauncherWidgetRecord made = folder(-1, app("A"), app("B"));
        assertTrue(made.isFolder());
        assertFalse(made.hasPlatformId());
        assertFalse(made.isApp());
        assertEquals(LauncherWidgetRecord.FOLDER_PROVIDER, made.provider);
        assertEquals("Tools", made.folderName());
        assertEquals(LauncherWidgetRecord.Kind.FOLDER, made.withCell(
            new WidgetCellRect(2, 2, 3, 3)).withPage(1).kind);
        assertEquals(2, made.withPage(1).folderApps().size());
    }
}
