package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
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
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.time.Duration;
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
        editor.events = host.events;
        surface = new AppearanceSurfaceController(host, editor);
    }

    @Test
    public void aDoorOpensTheEditorOnItsObject() {
        surface.open(EditorCard.DOCK, null);
        settle();
        assertTrue(surface.isOpen());
        assertTrue(editor.presented);
        assertEquals(EditorCard.DOCK, editor.card);
        assertFalse(surface.isWallpaperShown());
    }

    @Test
    public void aSecondDoorRaisesItsCardInTheSameSession() {
        surface.open(EditorCard.DOCK, null);
        settle();
        surface.open(EditorCard.KEYBOARD, null);
        settle();
        assertEquals(EditorCard.KEYBOARD, editor.card);
        assertEquals("one session", 1, editor.sessions);
    }

    @Test
    public void thePickerStandsOverTheEditorAndBackPutsItDown() {
        surface.open(EditorCard.APPEARANCE, null);
        settle();
        editor.listener.onOpenWallpaper();
        settle();
        assertTrue(editor.presented);
        assertTrue(surface.isWallpaperShown());
        assertNotNull(surface.overview());

        assertTrue(surface.onBack());
        settle();

        assertTrue("still open", surface.isOpen());
        assertFalse(surface.isWallpaperShown());
        assertEquals("the editor's session is untouched", 0, editor.leaves);
    }

    @Test
    public void theAppearanceCardsWallpaperRowRaisesThePicker() {
        surface.open(EditorCard.APPEARANCE, null);
        settle();
        editor.listener.onOpenWallpaper();
        settle();
        assertTrue(surface.isWallpaperShown());
    }

    @Test
    public void thePhotoPicksReturnBringsThePickerBackOverTheAppearanceCard() {
        surface.resumeWallpaper();
        settle();
        assertTrue(editor.presented);
        assertEquals(EditorCard.APPEARANCE, editor.card);
        assertTrue(surface.isWallpaperShown());
    }

    @Test
    public void aWallpaperDoorBuildsThePickerBeforeTheEditorOpensAndVeilsTheCardSheetUnderItsRise() {
        host.page.deferReady = true;
        surface.resumeWallpaper();
        ShadowLooper.runUiThreadTasks();
        assertTrue("the picker is built first", host.events.contains("build"));
        assertFalse("the editor waits for the picker to draw", editor.presented);

        host.page.becomeReady();
        ShadowLooper.runUiThreadTasks();
        assertTrue(editor.presented);
        assertEquals(java.util.Arrays.asList("build", "present"), host.events);
        assertTrue("the card sheet and the bar are unseen as the editor opens", editor.veiledAtPresent);
        assertTrue("the picker rises with the entry", surface.isWallpaperShown());

        // The idle above may already have run the rise to its end here; either way, once up:
        settle();
        assertFalse("shown again once the picker covers them", editor.veiled);
    }

    @Test
    public void aPickerPreparedWhileTheMenuSlidesIsTakenOverAndTheEntryStartsAtOnce() {
        surface.prepareWallpaper();
        assertFalse("nothing opens while the menu slides", surface.isOpen());
        assertTrue(surface.isPrepared());
        assertEquals(1, editor.wallpaperPrepares);
        assertEquals(java.util.Arrays.asList("build"), host.events);
        assertNotNull("held unseen under the host",
            activity.findViewById(com.termux.R.id.appearance_surface_host));

        surface.resumeWallpaper();
        assertTrue("ready first, the entry starts in the door's own call", editor.presented);
        assertEquals("one build, reused", java.util.Arrays.asList("build", "present"), host.events);
        assertTrue(surface.isWallpaperShown());
        assertTrue(editor.veiledAtPresent);
        assertFalse(surface.isPrepared());
        assertFalse(host.page.released);
        assertEquals(0, editor.wallpaperDrops);
    }

    @Test
    public void aDoorBeforeThePreparedPickerIsReadyWaitsForIt() {
        host.page.deferReady = true;
        surface.prepareWallpaper();
        surface.resumeWallpaper();
        ShadowLooper.runUiThreadTasks();
        assertFalse(editor.presented);

        host.page.becomeReady();
        ShadowLooper.runUiThreadTasks();
        assertTrue(editor.presented);
        assertEquals(java.util.Arrays.asList("build", "present"), host.events);
        assertTrue(surface.isWallpaperShown());
    }

    @Test
    public void aPreparedPickerNoDoorTookIsLetGo() {
        surface.prepareWallpaper();
        surface.releasePrepared();
        assertFalse(surface.isPrepared());
        assertTrue(host.page.released);
        assertEquals(1, editor.wallpaperDrops);
        assertNull(activity.findViewById(com.termux.R.id.appearance_surface_host));
        assertFalse(surface.isOpen());

        // The next door builds its own.
        host.page.released = false;
        surface.resumeWallpaper();
        settle();
        assertEquals(java.util.Arrays.asList("build", "build", "present"), host.events);
        assertTrue(surface.isWallpaperShown());
    }

    @Test
    public void anotherDoorLetsAPreparedPickerGo() {
        surface.prepareWallpaper();
        surface.open(EditorCard.DOCK, null);
        settle();
        assertTrue(host.page.released);
        assertTrue(editor.presented);
        assertNull(surface.overview());
        // The door's menu closing afterwards finds nothing left to let go.
        surface.releasePrepared();
        assertTrue(surface.isOpen());
    }

    @Test
    public void aPreparedBuildAnsweringAfterItWasLetGoIsReleased() {
        host.deferBuild = true;
        surface.prepareWallpaper();
        surface.releasePrepared();
        host.finishBuild();
        assertTrue(host.page.released);
        assertNull(surface.overview());
    }

    @Test
    public void aPickerThatCannotDrawInTimeOpensTheEditorWithItsCardSheet() {
        host.page.deferReady = true;
        surface.resumeWallpaper();
        idle(WallpaperDoorMotion.PICKER_WAIT_MS + 50);
        assertTrue(editor.presented);
        assertFalse("nothing is hidden behind a picker that is not there", editor.veiledAtPresent);
    }

    @Test
    public void doneOnTheDoorsPickerRidesItDownWithTheExitBeforeTakingItOff() {
        surface.resumeWallpaper();
        settle();
        assertTrue(surface.isWallpaperShown());
        View hostView = activity.findViewById(com.termux.R.id.appearance_surface_host);
        assertNotNull(hostView);

        host.navigator.done();
        assertFalse(surface.isOpen());
        assertEquals("the editor's exit has begun", 1, editor.saves);
        assertNotNull("the picker is still on its way down", hostView.getParent());
        assertFalse(host.page.released);
        boolean[] touched = {false};
        host.page.root.setOnTouchListener((v, e) -> touched[0] = true);
        assertTrue(hostView.dispatchTouchEvent(
            android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 10, 10, 0)));
        assertFalse("nothing on a leaving picker answers a touch", touched[0]);

        settle();
        assertNull("off screen, it is taken off", hostView.getParent());
        assertTrue(host.page.released);
    }

    @Test
    public void aPickerAlreadyPutDownIsTakenOffAtOnce() {
        surface.resumeWallpaper();
        settle();
        assertTrue(surface.onBack());
        settle();
        View hostView = activity.findViewById(com.termux.R.id.appearance_surface_host);
        editor.done();
        assertNull(hostView.getParent());
        assertTrue(host.page.released);
    }

    @Test
    public void aRelaunchReopensThePickerInPlaceAndSaysWhenItIsOnScreen() {
        surface.resumeWallpaper();
        settle();
        AppearanceSurfaceController.Relaunch carried = surface.carry();
        assertNotNull(carried);
        assertTrue(carried.where.wallpaper);
        surface.onHostDestroyed();
        host.page.root.setTranslationY(500f);

        AppearanceSurfaceController next = new AppearanceSurfaceController(host, editor);
        editor.presented = false;
        editor.inPlace = false;
        int[] shown = {0};
        assertTrue(next.reopen(carried, () -> shown[0]++));
        ShadowLooper.runUiThreadTasks();
        assertTrue("the editor stands where it stood, nothing growing", editor.inPlace);
        assertFalse("nothing is veiled: there is no rise", editor.veiledAtPresent);
        assertTrue(next.isWallpaperShown());
        assertEquals("the picker is up already, not rising", 0f,
            host.page.root.getTranslationY(), 0f);
        assertEquals(View.VISIBLE, host.page.root.getVisibility());
        assertEquals("told once it is on screen", 1, shown[0]);
        settle();
        assertEquals(1, shown[0]);
    }

    @Test
    public void aRelaunchKeepsTheDoorTheSideAndTheScroll() {
        surface.resumeWallpaper();
        settle();
        host.page.side = "LIVE";
        host.page.scrollY = 420;
        AppearanceSurfaceController.Relaunch carried = surface.carry();
        assertNotNull(carried);
        assertTrue(carried.where.pickerIsDoor);
        assertEquals("LIVE", carried.where.wallpaperSide);
        assertEquals(420, carried.where.wallpaperScrollY);
        surface.onHostDestroyed();

        AppearanceSurfaceController next = new AppearanceSurfaceController(host, editor);
        editor.presented = false;
        assertTrue(next.reopen(carried, null));
        ShadowLooper.runUiThreadTasks();
        assertTrue(next.isWallpaperShown());
        assertEquals("the side it stood on", "LIVE", host.page.restoredSide);
        assertEquals("scrolled where it was", 420, host.page.restoredScrollY);

        host.navigator.done();
        assertFalse("the door's Done still closes the editor with the picker", next.isOpen());
        assertEquals(1, editor.saves);
    }

    @Test
    public void aPickerRaisedFromTheCardIsNoDoorAfterARelaunch() {
        openPicker();
        AppearanceSurfaceController.Relaunch carried = surface.carry();
        assertNotNull(carried);
        assertFalse(carried.where.pickerIsDoor);
        surface.onHostDestroyed();

        AppearanceSurfaceController next = new AppearanceSurfaceController(host, editor);
        editor.presented = false;
        assertTrue(next.reopen(carried, null));
        settle();
        host.navigator.done();
        settle();
        assertTrue("Done puts the picker down over the card", next.isOpen());
        assertFalse(next.isWallpaperShown());
        assertEquals(0, editor.saves);
    }

    @Test
    public void thePickerFallsOverTheCardSheetAndLetsItsTouchesThrough() {
        ViewGroup content = activity.findViewById(android.R.id.content);
        View sheet = new View(activity);
        sheet.setElevation(12f);
        content.addView(sheet);
        openPicker();
        View hostView = activity.findViewById(com.termux.R.id.appearance_surface_host);
        assertNotNull(hostView);
        assertTrue(hostView.getZ() > sheet.getZ());

        surface.onBack();
        assertFalse(hostView.isClickable());
        assertTrue("drawn over the sheet while it falls", hostView.getZ() > sheet.getZ());
        assertFalse("a touch goes on to the sheet", hostView.dispatchTouchEvent(
            android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 10, 10, 0)));

        settle();
        assertEquals("down, it rests at the sheet's level", 0f, hostView.getTranslationZ(), 0f);
    }

    @Test
    public void aReopenThatClosesStillLetsTheWindowDraw() {
        surface.open(EditorCard.DOCK, null);
        settle();
        AppearanceSurfaceController.Relaunch carried = surface.carry();
        assertNotNull(carried);
        surface.onHostDestroyed();
        AppearanceSurfaceController next = new AppearanceSurfaceController(host, editor);
        editor.presented = false;
        editor.frameFailures = AppearanceSurfaceController.REOPEN_ATTEMPTS;
        int[] shown = {0};
        assertTrue(next.reopen(carried, () -> shown[0]++));
        settle();
        assertFalse(next.isOpen());
        assertEquals(1, shown[0]);
    }

    @Test
    public void theFirstFrameIsHeldOnlyForAHandoverAndNeverPastItsBackstops() {
        assertFalse(RelaunchReveal.holds(false));
        assertTrue(RelaunchReveal.holds(true));
        assertTrue(RelaunchReveal.stillHeld(false, 0, 0));
        assertFalse("shown as carried", RelaunchReveal.stillHeld(true, 0, 0));
        assertFalse("the time backstop",
            RelaunchReveal.stillHeld(false, 1, RelaunchReveal.BACKSTOP_MS));
        assertFalse("the frame backstop",
            RelaunchReveal.stillHeld(false, RelaunchReveal.MAX_HELD_FRAMES, 1));
        assertNull("no handover, no hold",
            RelaunchReveal.holdFirstDraw(activity.getWindow().getDecorView(), false));
    }

    @Test
    public void aHeldWindowDrawsOnceReleasedOrPastItsBackstop() {
        View decor = activity.getWindow().getDecorView();
        RelaunchReveal.Gate gate = RelaunchReveal.holdFirstDraw(decor, true);
        assertNotNull(gate);
        assertFalse("held", gate.onPreDraw());
        gate.release();
        assertTrue(gate.onPreDraw());

        RelaunchReveal.Gate late = RelaunchReveal.holdFirstDraw(decor, true);
        assertNotNull(late);
        idle(RelaunchReveal.BACKSTOP_MS + 10);
        assertTrue("released by its backstop", late.onPreDraw());
    }

    @Test
    public void backPutsTheCardDownBeforeItLeaves() {
        surface.open(EditorCard.DOCK, null);
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
        surface.open(EditorCard.APPEARANCE, null);
        settle();
        editor.listener.onOpenWallpaper();
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
        surface.open(EditorCard.APPEARANCE, null);
        settle();
        editor.listener.onOpenWallpaper();
        settle();
        host.page.pending = true;
        // The picker goes down, then the Appearance card, then the editor asks.
        surface.onBack();
        settle();
        surface.onBack();
        settle();
        surface.onBack();
        settle();
        assertEquals(1, editor.confirms);
    }

    @Test
    public void aDoorNamingNothingReopensTheCardLeftUp() {
        surface.open(EditorCard.STATUS, null);
        settle();
        surface.requestExit();
        settle();
        assertFalse(surface.isOpen());

        surface.open();
        settle();
        assertEquals(EditorCard.STATUS, editor.card);
    }

    @Test
    public void thePickersDoneAppliesHoldsThenPutsThePickerDownOverTheCard() {
        openPicker();
        host.page.pending = true;
        host.navigator.done();

        assertTrue("the picker's choice was applied", host.page.committed);
        assertTrue("held up while the colours land", surface.isWallpaperShown());
        assertTrue("busy while held", host.page.held);
        AppearanceSurfaceController.Relaunch carried = surface.carry();
        assertNotNull(carried);
        assertFalse("a relaunch now opens on the card", carried.where.wallpaper);
        assertEquals(EditorCard.APPEARANCE, carried.where.editorCard());

        settle();
        assertTrue("still open", surface.isOpen());
        assertFalse(surface.isWallpaperShown());
        assertFalse(host.page.held);
        assertEquals(EditorCard.APPEARANCE, editor.card);
        assertEquals("the editor's session is not saved", 0, editor.saves);
        assertTrue(host.closes.isEmpty());
    }

    @Test
    public void aColoursCallbackHoldsThePickerForTheRelaunch() {
        openPicker();
        host.page.pending = true;
        host.navigator.done();
        surface.onWallpaperColoursChanged();

        idle(WallpaperApplyHold.COLOURS_QUIET_MS + 100L);
        assertTrue("past the quiet window, waiting for the relaunch", surface.isWallpaperShown());

        idle(WallpaperApplyHold.RELAUNCH_WAIT_MS);
        assertFalse("the backstop reveals the card", surface.isWallpaperShown());
        assertTrue(surface.isOpen());
    }

    @Test
    public void thePickersDoneWithNothingPendingPutsItDown() {
        openPicker();
        host.navigator.done();
        settle();
        assertFalse(host.page.committed);
        assertTrue(surface.isOpen());
        assertFalse(surface.isWallpaperShown());
    }

    @Test
    public void aFailedApplyLeavesThePickerUp() {
        openPicker();
        host.page.pending = true;
        host.page.fails = true;
        host.navigator.done();
        settle();
        assertTrue(surface.isWallpaperShown());
        AppearanceSurfaceController.Relaunch carried = surface.carry();
        assertNotNull(carried);
        assertTrue(carried.where.wallpaper);
    }

    @Test
    public void thePickersOwnDoorStillClosesOnDone() {
        surface.resumeWallpaper();
        settle();
        host.page.pending = true;
        host.navigator.done();
        settle();
        assertTrue(host.page.committed);
        assertFalse(surface.isOpen());
        assertEquals(1, editor.saves);
    }

    @Test
    public void aDestroyedActivityLetsGoWithoutATrace() {
        openPicker();
        host.page.pending = true;
        host.page.deferCommit = true;
        host.navigator.done();
        AppearanceSurfaceController.Relaunch carried = surface.carry();
        assertNotNull(carried);
        assertFalse(carried.where.wallpaper);

        surface.onHostDestroyed();
        assertFalse("the apply runs on, its page kept", host.page.released);
        host.page.finishCommit();
        settle();

        assertFalse(surface.isOpen());
        assertTrue("the page lets go once its apply answered", host.page.released);
        assertTrue("no close", host.closes.isEmpty());
        assertNull("no return place written", host.returnState);
        assertTrue("no session ended", editor.presented);
    }

    @Test
    public void aRelaunchHandsTheSessionAndTheCardOn() {
        surface.open(EditorCard.KEYBOARD, null);
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
        surface.open(EditorCard.APPEARANCE, null);
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
        surface.open(EditorCard.DOCK, null);
        settle();
        assertFalse(editor.presented);
        assertFalse(surface.isOpen());
    }

    private static void settle() {
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    private static void idle(long ms) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    private void openPicker() {
        surface.open(EditorCard.APPEARANCE, null);
        settle();
        editor.listener.onOpenWallpaper();
        settle();
        assertTrue(surface.isWallpaperShown());
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

        int wallpaperPrepares;
        int wallpaperDrops;

        @Override public void prepareWallpaper() {
            wallpaperPrepares++;
        }

        @Override public void dropPreparedWallpaper() {
            wallpaperDrops++;
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

        boolean veiled;
        boolean veiledAtPresent;
        boolean inPlace;

        @Override public void presentInPlace(@Nullable EditorCard card,
                                             @Nullable PaneWallPage place,
                                             @Nullable Runnable onSettled) {
            inPlace = true;
            present(card, place, onSettled);
        }
        @Nullable List<String> events;

        @Override public void setChromeVeiled(boolean veiled) {
            this.veiled = veiled;
        }

        @Override public void present(@Nullable EditorCard card, @Nullable PaneWallPage place,
                                      @Nullable Runnable onSettled) {
            if (events != null && !presented) events.add("present");
            veiledAtPresent = veiled;
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
            veiled = false;
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
        boolean fails;
        boolean held;
        boolean released;
        /** The apply answers only when {@link #finishCommit} runs. */
        boolean deferCommit;
        /** {@link #whenReady} answers only when {@link #becomeReady} runs. */
        boolean deferReady;
        @Nullable Runnable readyWaiter;

        @Override public void whenReady(@NonNull Runnable ready) {
            if (deferReady) {
                readyWaiter = ready;
                return;
            }
            ready.run();
        }

        void becomeReady() {
            deferReady = false;
            Runnable ready = readyWaiter;
            readyWaiter = null;
            if (ready != null)
                ready.run();
        }
        @Nullable Runnable answer;

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

        @Override public void release() {
            released = true;
        }

        @Override public boolean hasPendingChanges() {
            return pending;
        }

        @Override public void commit(@NonNull Runnable done, @NonNull Runnable failed) {
            committed = true;
            if (fails) {
                failed.run();
                return;
            }
            pending = false;
            answer = done;
            if (!deferCommit)
                finishCommit();
        }

        void finishCommit() {
            Runnable done = answer;
            answer = null;
            if (done != null)
                done.run();
        }

        @Override public void holdBusy(boolean held) {
            this.held = held;
        }

        @Nullable String side;
        int scrollY;
        @Nullable String restoredSide;
        int restoredScrollY;

        @Nullable @Override public String shownSide() {
            return side;
        }

        @Override public int sideScrollY() {
            return scrollY;
        }

        @Override public void restoreSide(@NonNull String side, int scrollY) {
            restoredSide = side;
            restoredScrollY = scrollY;
        }
    }

    private static final class FakeHost implements AppearanceSurfaceController.Host {
        private final Activity mActivity;
        final FakePage page;
        @Nullable String returnState;
        @Nullable AppearanceSurfaceController.Navigator navigator;
        boolean closedByUser;
        final List<String> closes = new ArrayList<>();
        final List<String> events = new ArrayList<>();

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
            this.navigator = navigator;
            events.add("build");
            if (deferBuild) {
                built = ready;
                return;
            }
            ready.accept(page);
        }

        /** The build answers only when {@link #finishBuild} runs. */
        boolean deferBuild;
        @Nullable Consumer<AppearanceSurfaceController.OverviewPage> built;

        void finishBuild() {
            Consumer<AppearanceSurfaceController.OverviewPage> ready = built;
            built = null;
            if (ready != null)
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
