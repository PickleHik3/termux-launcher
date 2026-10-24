package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.PaneWallPage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The editor's host: every door opens the editor (on its object), the wallpaper picker stands
 * over it and Back puts it down, Back puts a card down before it leaves, Done keeps the session
 * and the picker's choice, a door naming nothing reopens the card left up, and a relaunch hands
 * the session on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class AppearanceSurfaceControllerTest {

    private Activity activity;
    private FakeEditor editor;
    private FakeHost host;
    private AppearanceSurfaceController surface;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout content = new FrameLayout(activity);
        activity.setContentView(content, new ViewGroup.LayoutParams(1080, 2000));
        editor = new FakeEditor();
        host = new FakeHost(activity);
        surface = new AppearanceSurfaceController(host, editor);
    }

    @Test
    public void aDoorOpensTheEditorOnItsObject() {
        surface.open(AppearanceSurfaceController.PageId.EDITOR, EditorCard.DOCK, null);
        settle();
        assertTrue(surface.isOpen());
        assertTrue(editor.presented);
        assertEquals(EditorCard.DOCK, editor.card);
        assertEquals(AppearanceSurfaceController.PageId.EDITOR, surface.shownPage());
    }

    @Test
    public void aSecondDoorRaisesItsCardInTheSameSession() {
        surface.open(AppearanceSurfaceController.PageId.EDITOR, EditorCard.DOCK, null);
        settle();
        surface.open(AppearanceSurfaceController.PageId.EDITOR, EditorCard.KEYBOARD, null);
        settle();
        assertEquals(EditorCard.KEYBOARD, editor.card);
        assertEquals("one session", 1, editor.sessions);
    }

    @Test
    public void thePickerStandsOverTheEditorAndBackPutsItDown() {
        surface.open(AppearanceSurfaceController.PageId.WALLPAPER, null, null);
        settle();
        assertTrue(editor.presented);
        assertEquals(AppearanceSurfaceController.PageId.WALLPAPER, surface.shownPage());
        assertNotNull(surface.overview());

        assertTrue(surface.onBack());
        settle();

        assertTrue("still open", surface.isOpen());
        assertEquals(AppearanceSurfaceController.PageId.EDITOR, surface.shownPage());
        assertEquals("the editor's session is untouched", 0, editor.leaves);
    }

    @Test
    public void theAppearanceCardsWallpaperRowRaisesThePicker() {
        surface.open(AppearanceSurfaceController.PageId.EDITOR, EditorCard.APPEARANCE, null);
        settle();
        editor.listener.onOpenWallpaper();
        settle();
        assertEquals(AppearanceSurfaceController.PageId.WALLPAPER, surface.shownPage());
    }

    @Test
    public void backPutsTheCardDownBeforeItLeaves() {
        surface.open(AppearanceSurfaceController.PageId.EDITOR, EditorCard.DOCK, null);
        settle();
        assertTrue(surface.onBack());
        assertNull("the card went down", editor.card);
        assertTrue(surface.isOpen());

        assertTrue(surface.onBack());
        settle();
        assertEquals("then the editor's own unsaved question", 1, editor.leaves);
        assertFalse(surface.isOpen());
        assertTrue(host.closedByUser);
    }

    @Test
    public void doneKeepsTheSessionAndAppliesThePickersChoiceThenCloses() {
        surface.open(AppearanceSurfaceController.PageId.WALLPAPER, null, null);
        settle();
        host.page.pending = true;
        editor.done();
        settle();
        assertTrue("the picker's choice was applied", host.page.committed);
        assertFalse(surface.isOpen());
        assertEquals(1, editor.saves);
    }

    @Test
    public void leavingWithAChoicePendingAsksOnce() {
        surface.open(AppearanceSurfaceController.PageId.WALLPAPER, null, null);
        settle();
        host.page.pending = true;
        surface.onBack();
        settle();
        surface.onBack();
        settle();
        assertEquals(1, editor.confirms);
    }

    @Test
    public void aDoorNamingNothingReopensTheCardLeftUp() {
        surface.open(AppearanceSurfaceController.PageId.EDITOR, EditorCard.STATUS, null);
        settle();
        surface.requestExit();
        settle();
        assertFalse(surface.isOpen());

        surface.open(AppearanceSurfaceController.PageId.EDITOR);
        settle();
        assertEquals(EditorCard.STATUS, editor.card);
    }

    @Test
    public void aRelaunchHandsTheSessionAndTheCardOn() {
        surface.open(AppearanceSurfaceController.PageId.EDITOR, EditorCard.KEYBOARD, null);
        settle();
        AppearanceSurfaceController.Relaunch carried = surface.carry();
        assertNotNull(carried);
        assertEquals(EditorCard.KEYBOARD, carried.where.editorCard());

        FakeEditor next = new FakeEditor();
        AppearanceSurfaceController after = new AppearanceSurfaceController(
            new FakeHost(activity), next);
        assertTrue(after.reopen(carried));
        settle();
        assertEquals(EditorCard.KEYBOARD, next.card);
        assertEquals(editor.session, next.adopted);
    }

    @Test
    public void aRelaunchWaitsForASlowWindowRatherThanDropTheSession() {
        surface.open(AppearanceSurfaceController.PageId.EDITOR, EditorCard.APPEARANCE, null);
        settle();
        AppearanceSurfaceController.Relaunch carried = surface.carry();
        assertNotNull(carried);

        FakeEditor next = new FakeEditor();
        next.frameFailures = AppearanceSurfaceController.REOPEN_ATTEMPTS - 1;
        AppearanceSurfaceController after = new AppearanceSurfaceController(
            new FakeHost(activity), next);
        assertTrue(after.reopen(carried));
        settle();

        assertTrue("the editor came back once the window was laid out", next.presented);
        assertTrue(after.isOpen());
        assertEquals(EditorCard.APPEARANCE, next.card);
        assertEquals("the session it carried", editor.session, next.adopted);
    }

    @Test
    public void aDoorThatFindsNoWindowClosesAtOnce() {
        editor.frameFailures = 1;
        surface.open(AppearanceSurfaceController.PageId.EDITOR, EditorCard.DOCK, null);
        settle();
        assertFalse(editor.presented);
        assertFalse(surface.isOpen());
    }

    private static void settle() {
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    // ------------------------------------------------------------------------------- the fakes

    private static final class FakeEditor implements AppearanceSurfaceController.Editor {
        boolean presented;
        @Nullable EditorCard card;
        int sessions;
        int leaves;
        int confirms;
        int saves;
        @Nullable Runnable onDone;
        @Nullable AppearanceSurfaceController.PageListener listener;
        final AppearanceSurfaceController.EditorSession session =
            new AppearanceSurfaceController.EditorSession() {};
        @Nullable AppearanceSurfaceController.EditorSession adopted;

        @Override public void beginSession() {
            sessions++;
        }

        @Override public void awaitWallpaper(@NonNull Runnable ready) {
            ready.run();
        }

        /** How many waits for the launcher's container time out before one answers. */
        int frameFailures;

        @Override public void awaitFrame(@NonNull Runnable ready, @NonNull Runnable failed) {
            if (frameFailures > 0) {
                frameFailures--;
                failed.run();
            } else {
                ready.run();
            }
        }

        @Override public void present(@Nullable EditorCard card, @Nullable PaneWallPage place,
                                      @Nullable Runnable onSettled) {
            presented = true;
            this.card = card;
            if (onSettled != null) onSettled.run();
        }

        @Override public boolean isPresented() {
            return presented;
        }

        @Override public boolean consumeBack() {
            if (card == null)
                return false;
            card = null;
            return true;
        }

        @Override public void requestLeave(@NonNull Runnable proceed) {
            leaves++;
            proceed.run();
        }

        @Override public void confirmLeave(@NonNull Runnable onSave, @NonNull Runnable onDiscard) {
            confirms++;
            onDiscard.run();
        }

        @Override public void endSession() {
            presented = false;
        }

        @Override public void setOnDone(@Nullable Runnable onDone) {
            this.onDone = onDone;
        }

        @Override public void setPageListener(
                @Nullable AppearanceSurfaceController.PageListener listener) {
            this.listener = listener;
        }

        @Override public void undo() {}

        @Override public void done() {
            saves++;
            if (onDone != null) onDone.run();
        }

        @Override public void onStopWhileOpen() {}

        @Nullable @Override public EditorCard shownCard() {
            return card;
        }

        @Nullable @Override public AppearanceSurfaceController.EditorSession carrySession() {
            return presented ? session : null;
        }

        @Override public void adoptSessionNext(
                @Nullable AppearanceSurfaceController.EditorSession session) {
            adopted = session;
        }
    }

    private static final class FakePage implements AppearanceSurfaceController.OverviewPage {
        final View root;
        boolean pending;
        boolean committed;

        FakePage(@NonNull Context context) {
            root = new View(context);
        }

        @NonNull @Override public View root() {
            return root;
        }

        @NonNull @Override public CharSequence title() {
            return "Wallpaper";
        }

        @Override public void onShown() {}

        @Override public void onHidden() {}

        @Override public void release() {}

        @Override public boolean hasPendingChanges() {
            return pending;
        }

        @Override public void commit(@NonNull Runnable done, @NonNull Runnable failed) {
            committed = true;
            pending = false;
            done.run();
        }
    }

    private static final class FakeHost implements AppearanceSurfaceController.Host {
        private final Activity mActivity;
        final FakePage page;
        @Nullable String returnState;
        boolean closedByUser;
        final List<String> closes = new ArrayList<>();

        FakeHost(@NonNull Activity activity) {
            mActivity = activity;
            page = new FakePage(activity);
        }

        @NonNull @Override public Context context() {
            return mActivity;
        }

        @Nullable @Override public ViewGroup content() {
            return mActivity.findViewById(android.R.id.content);
        }

        @Override public void createOverview(@NonNull AppearanceSurfaceController.Navigator navigator,
                                             @Nullable String centredSlot,
                                             @NonNull Consumer<AppearanceSurfaceController.OverviewPage> ready) {
            ready.accept(page);
        }

        @Nullable @Override public String readReturnState() {
            return returnState;
        }

        @Override public void writeReturnState(@NonNull String state) {
            returnState = state;
        }

        @Override public void onClosed() {
            closes.add("closed");
        }

        @Override public void onClosedByUser() {
            closedByUser = true;
        }
    }
}
