package com.termux.app.chrome.sheets;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.folder.FolderRenameTitleView;
import com.termux.app.launcher.widget.WidgetPaneController;
import com.termux.app.notice.AppNotice;
import com.termux.app.notice.AppNoticeHostView;
import com.termux.app.notice.AppNoticeItem;
import com.termux.app.notice.TerminalDress;
import com.termux.app.surfaces.SurfaceEditorController;
import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard;
import com.termux.view.TerminalView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The sheets' controller over a fake {@link SheetsController.Host} (ADR 0018 wave H step one,
 * D17): the state the sheets' code kept in the activity, pinned where it now lives.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SheetsControllerTest {

    private FakeHost mHost;
    private SheetsController mSheets;

    @Before
    public void setUp() {
        mHost = new FakeHost(Robolectric.buildActivity(Activity.class).setup().get());
        mSheets = new SheetsController(mHost);
    }

    // ------------------------------------------------------------------ notices

    @Test
    public void noticesAreSuppressedInsideRunWithoutNoticesNestedAndRestoredAfter() {
        assertFalse(mSheets.areNoticesSuppressed());
        boolean[] seen = new boolean[2];
        mSheets.runWithoutNotices(() -> {
            seen[0] = mSheets.areNoticesSuppressed();
            mSheets.runWithoutNotices(() -> seen[1] = mSheets.areNoticesSuppressed());
            assertTrue("still silent after the inner run", mSheets.areNoticesSuppressed());
        });
        assertTrue(seen[0]);
        assertTrue(seen[1]);
        assertFalse("restored after the outer run", mSheets.areNoticesSuppressed());
    }

    @Test
    public void suppressionIsRestoredWhenTheBodyThrows() {
        try {
            mSheets.runWithoutNotices(() -> { throw new IllegalStateException("boom"); });
        } catch (IllegalStateException expected) {
            // the body's own failure
        }
        assertFalse(mSheets.areNoticesSuppressed());
    }

    @Test
    public void aTerminalNoticeRequestedWhileSuppressedIsNotShown() {
        mSheets.runWithoutNotices(() ->
            mSheets.showTerminalNotice("split", AppNoticeItem.Hold.INFO));
        shadowOf(Looper.getMainLooper()).idle();
        AppNoticeHostView pill = AppNotice.hostFor(mHost.activity);
        assertNotNull(pill);
        assertNull("nothing raised while silent", pill.activeItem());

        mSheets.showTerminalNotice("split", AppNoticeItem.Hold.INFO);
        shadowOf(Looper.getMainLooper()).idle();
        assertNotNull("raised once notices are back", pill.activeItem());
    }

    // ------------------------------------------------------------------ the drawer's keyboard

    @Test
    public void theDrawerSearchAsksTheSystemImeOnceWithoutTheBuiltInKeyboard() {
        mSheets.requestAppDrawerSearchKeyboard();
        assertEquals(1, mHost.systemImeRequests);
        assertFalse("the system ask is nobody's raise", mSheets.drawerRaisedKeyboard());

        mSheets.lowerDrawerSearchKeyboard();
        assertEquals("the drawer raised nothing, so nothing is lowered", 0, mHost.frameLowers);
    }

    @Test
    public void theDrawerCloseForgetsItsRaiseAndLowersNothingThatIsNotUp() {
        ReflectionHelpers.setField(mSheets, "mDrawerRaisedKeyboard", true);
        mSheets.lowerDrawerSearchKeyboard();
        assertFalse(mSheets.drawerRaisedKeyboard());
        assertEquals("no keyboard is up to lower", 0, mHost.frameLowers);
    }

    @Test
    public void claimingTheRaisedKeyboardIsRemembered() {
        assertFalse(mSheets.drawerRaisedKeyboardClaimed());
        mSheets.claimDrawerRaisedKeyboard();
        assertTrue(mSheets.drawerRaisedKeyboardClaimed());
    }

    // ------------------------------------------------------------------ interceptors

    @Test
    public void theDrawerInterceptorIsSwitchedOnAndOff() {
        mSheets.setAppDrawerInterceptorActive(true);
        mSheets.setAppDrawerInterceptorActive(false);
        assertEquals(java.util.Arrays.asList("claim palette=false sheet=false drawer=true",
            "claim palette=false sheet=false drawer=false"), mHost.claims);
    }

    @Test
    public void thePaletteInterceptorMovesTheSearchFieldAndClaimsOnlyWithAKeyboard() {
        mSheets.setCommandPaletteInterceptorActive(true);
        mSheets.setCommandPaletteInterceptorActive(false);
        assertEquals(java.util.Arrays.asList(true, false), mHost.promptUnderPalette);
        assertTrue("no in-app keyboard, no claim", mHost.claims.isEmpty());

        mSheets.setTerminalSheetInterceptorActive(true);
        assertTrue(mHost.claims.isEmpty());
    }

    // ------------------------------------------------------------------ folder rename

    @Test
    public void aHomeFolderRenameIsBegunAndCancelled() {
        String[] stored = {"Games"};
        WidgetPaneController.HomeFolderRenameTarget target =
            new WidgetPaneController.HomeFolderRenameTarget() {
                @Override public boolean rename(@NonNull String name) { stored[0] = name; return true; }
                @NonNull @Override public String currentName() { return stored[0]; }
            };
        mSheets.beginHomeFolderRename("Games", new FolderRenameTitleView(mHost.activity), target);
        assertTrue(mSheets.isFolderRenameActive());
        assertEquals("the in-app keyboard was asked for first", 1, mHost.typingKeyboardAsks);
        assertEquals(0, mHost.systemImeRequests);

        mSheets.cancelFolderRename();
        assertFalse(mSheets.isFolderRenameActive());
        assertEquals("the slot went back to whoever owns it", 1, mHost.typingClaimRestores);
        assertEquals("cancel stores nothing", "Games", stored[0]);
    }

    @Test
    public void noTerminalActionsSheetOpensWithoutASession() {
        assertFalse(mSheets.showTerminalActionSheet(null));
        assertNull("nothing is built for nothing", mSheets.terminalActionsSheetIfBuilt());
    }

    @Test
    public void noTouchIsARenameTypingTouchWhileNoRenameIsActive() {
        assertFalse(mSheets.isFolderRenameTypingTouch(10f, 10f));
    }

    // ------------------------------------------------------------------ lazy builders

    @Test
    public void thePaletteTheSheetAndTheDrawerAreBuiltLazilyOnce() {
        assertNull(mSheets.commandPaletteIfBuilt());
        assertNull(mSheets.terminalSheetIfBuilt());
        assertNull(mSheets.appDrawerControllerIfBuilt());
        assertFalse("asking never builds", mSheets.isCommandPaletteOpen());
        assertFalse(mSheets.isTerminalSheetOpen());
        assertFalse(mSheets.isAppDrawerEngaged());
        assertFalse(mSheets.isAppDrawerOpen());
        assertNull(mSheets.appDrawerControllerIfBuilt());
        assertEquals(0, mHost.hostsMade);

        assertSame(mSheets.getCommandPaletteController(), mSheets.getCommandPaletteController());
        assertSame(mSheets.getTerminalSheetController(), mSheets.getTerminalSheetController());
        assertSame(mSheets.getAppDrawerController(), mSheets.getAppDrawerController());
        // Palette (with its Home host), sheet and drawer: one Host each, made once.
        assertEquals(4, mHost.hostsMade);
        assertSame(mSheets.getCommandPaletteController(), mSheets.commandPaletteIfBuilt());

        mSheets.releasePaletteAndSheet();
        assertNull(mSheets.commandPaletteIfBuilt());
        assertNull(mSheets.terminalSheetIfBuilt());
    }

    // ------------------------------------------------------------------ the Home palette

    @Test
    public void theHomePaletteGestureFollowsItsSetting() {
        com.termux.app.launcher.home.HomeSettings.setGestureAction(mHost.activity,
            com.termux.app.launcher.home.HomeGestures.Gesture.SWIPE_UP,
            com.termux.app.launcher.home.HomeGestures.Action.PALETTE);
        assertTrue(mSheets.isHomePaletteGestureEnabled());
        com.termux.app.launcher.home.HomeSettings.setGestureAction(mHost.activity,
            com.termux.app.launcher.home.HomeGestures.Gesture.SWIPE_UP,
            com.termux.app.launcher.home.HomeGestures.Action.NONE);
        assertFalse(mSheets.isHomePaletteGestureEnabled());
    }

    @Test
    public void openingTheHomePaletteWhileItIsOpenDoesNothing() {
        mHost.raises = true;
        mSheets.openHomePalette(false);
        assertEquals("closed, the palette raises the keyboard first", 1, mHost.keyboardRaises);
        shadowOf(Looper.getMainLooper()).idle();

        ReflectionHelpers.setField(mSheets.getCommandPaletteController(), "mOpen", true);
        mSheets.openHomePalette(false);
        mSheets.openHomePalette(false);
        assertEquals("open, nothing is raised again", 1, mHost.keyboardRaises);
    }

    @Test
    public void thePaletteTileRestylesTheDockOncePerChangeOfItsStoredValue() {
        mHost.preferences = com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
            .build(mHost.activity, false);
        mHost.hasBar = true;
        com.termux.app.SuggestionBarView bar = new com.termux.app.SuggestionBarView(mHost.activity, null);
        mSheets.syncPaletteTile(bar);
        mSheets.syncPaletteTile(bar);
        assertEquals("syncing restyles nothing itself", 0, mHost.dockRestyles);

        android.content.SharedPreferences store = mHost.preferences.getSharedPreferences();
        boolean on = store.getBoolean(com.termux.app.surfaces.cards.DockCard.KEY_COMMAND_PALETTE_BUTTON,
            com.termux.app.surfaces.cards.DockCard.DEFAULT_COMMAND_PALETTE_BUTTON);
        store.edit().putBoolean(com.termux.app.surfaces.cards.DockCard.KEY_COMMAND_PALETTE_BUTTON, !on).commit();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("heard once though synced twice", 1, mHost.dockRestyles);

        store.edit().putString("some_other_key", "x").commit();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("another key is not the tile's", 1, mHost.dockRestyles);

        store.edit().putBoolean(com.termux.app.surfaces.cards.DockCard.KEY_COMMAND_PALETTE_BUTTON, on).commit();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(2, mHost.dockRestyles);
    }

    // ------------------------------------------------------------------ glass and shape

    @Test
    public void thePaletteIsGivenCornersThatFollowDockedOrFloating() {
        // No store: the Sheets element's Corners as shipped, on the resting page.
        int shipped = com.termux.app.chrome.ElementGlass.of(null,
            com.termux.shared.termux.settings.preferences.LookGrid.Element.SHEETS, 0).cornersDp;
        assertEquals(shipped, mSheets.commandPaletteCornersDp());
        // Where they follow the Style (CommandPaletteShape.openRadiusDp): square Docked, the
        // rounded-surface token Floating.
        assertEquals(0, com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
            .resolveAutoCornerRadiusDp(com.termux.shared.termux.settings.preferences
                .TermuxAppSharedPreferences.SurfaceSlot.SHEETS, false));
        assertEquals(com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants
                .TERMUX_APP.DEFAULT_ROUNDED_SURFACE_CORNER_RADIUS_DP,
            com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
                .resolveAutoCornerRadiusDp(com.termux.shared.termux.settings.preferences
                    .TermuxAppSharedPreferences.SurfaceSlot.SHEETS, true));

        // Read from the live store when there is one: the same answer for an untouched store.
        mHost.preferences = com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
            .build(mHost.activity, false);
        assertEquals(shipped, mSheets.commandPaletteCornersDp());
    }

    // ------------------------------------------------------------------ the fake

    private static final class FakeHost implements SheetsController.Host {
        final Activity activity;
        int systemImeRequests;
        int frameLowers;
        int typingKeyboardAsks;
        int typingClaimRestores;
        int hostsMade;
        final List<String> claims = new ArrayList<>();
        final List<Boolean> promptUnderPalette = new ArrayList<>();
        boolean raises;
        int keyboardRaises;
        boolean hasBar;
        int dockRestyles;
        com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences preferences;

        FakeHost(Activity activity) { this.activity = activity; }

        @SuppressWarnings("unchecked")
        private <T> T proxy(Class<T> type) {
            hostsMade++;
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (p, method, args) -> {
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r == int.class) return 0;
                    if (r == long.class) return 0L;
                    if (r == float.class) return 0f;
                    if (r == double.class) return 0d;
                    if (method.getName().equals("context")) return activity;
                    return null;
                });
        }

        @NonNull @Override public Activity activity() { return activity; }
        @Override public boolean isActivityVisible() { return true; }
        @Override public boolean hasSuggestionBarView() { return hasBar; }
        @Override public boolean hasPreferences() { return false; }
        @NonNull @Override public TerminalDress.Source terminalDressSource() {
            return new TerminalDress.Source() {
                @Override public float terminalCornerRadiusPx() { return 0f; }
                @Override public int terminalFillColor() { return 0; }
            };
        }
        @Nullable @Override public com.termux.app.statusbar.BackgroundProcessStackView backgroundProcessStack() { return null; }
        @Override public SurfaceEditorController surfaceEditor() { return null; }
        @Override public void showToast(String text, boolean longDuration) { }
        @Nullable @Override public LauncherConfigRepository launcherConfigRepository() { return null; }
        @Override public void restoreInAppKeyboardTypingClaim() { typingClaimRestores++; }
        @Nullable @Override public TermuxInAppKeyboard inAppKeyboard() { return null; }
        @Override public boolean ensureInAppTypingKeyboard() { typingKeyboardAsks++; return true; }
        @Override public boolean handKeysToActivePane() { return false; }
        @Nullable @Override public TerminalView terminalView() { return null; }
        @Override public void onSystemImeRequested() { systemImeRequests++; }
        @Override public boolean isPointOnInAppKeyboard(float rawX, float rawY) { return false; }
        @Override public boolean viewContainsScreenPoint(@Nullable View view, float x, float y) { return false; }
        @Override public boolean isDisplayTouchpadFrame() { return false; }
        @NonNull @Override public com.termux.app.x11.DisplayFrameContentPolicy frameContent() {
            return new com.termux.app.x11.DisplayFrameContentPolicy();
        }
        @Override public void applyDisplayFrameDecision(@NonNull com.termux.app.x11.DisplayFrameContentPolicy.Decision decision) { }
        @Override public void raiseDisplayFrameKeyboard() { }
        @Override public void lowerDisplayFrameKeyboard() { frameLowers++; }
        @Override public void setRaisingDisplayFrameKeyboard(boolean raising) { }
        @NonNull @Override public com.termux.app.terminal.TerminalCommandPaletteController.Host newCommandPaletteHost() {
            return proxy(com.termux.app.terminal.TerminalCommandPaletteController.Host.class);
        }
        @NonNull @Override public com.termux.app.terminal.CommandPaletteHomeHost newPaletteHomeHost() {
            return proxy(com.termux.app.terminal.CommandPaletteHomeHost.class);
        }
        @NonNull @Override public com.termux.app.terminal.TerminalSheetController.Host newTerminalSheetHost() {
            return proxy(com.termux.app.terminal.TerminalSheetController.Host.class);
        }
        @NonNull @Override public com.termux.app.launcher.drawer.AppDrawerController.Host newAppDrawerHost() {
            return proxy(com.termux.app.launcher.drawer.AppDrawerController.Host.class);
        }
        @Nullable @Override public com.termux.app.launcher.drawer.AppDrawerController.AppDrawerDockChoreographyTarget dockChoreographyTarget() { return null; }
        @Nullable @Override public com.termux.terminal.TerminalSession currentSession() { return null; }
        @Override public void setHomeSheetDecorNavStripAlpha(float alpha) { }
        @Override public void prepareWallpaperDoor() { }
        @Override public void releasePreparedWallpaper() { }
        @Override public boolean shouldUseWallpaperPassthroughMode() { return false; }
        @Override public boolean isTerminalStylingAvailable() { return false; }
        @Override public void handleTerminalAction(@NonNull com.termux.app.launcher.home.TerminalActionsSheet.Action action) { }
        @Override public void runHomeAction(@NonNull com.termux.app.launcher.home.HomeActionsSheetPolicy.Item item) { }
        @Nullable @Override public WidgetPaneController widgetPaneController() { return null; }
        @Override public boolean isDefaultHomeApp() { return false; }
        @NonNull @Override public com.termux.app.wall.UseAs useAs() { return com.termux.app.wall.UseAs.TERMINAL; }
        @Override public void setHomeActionsAnchor(float rawX, float rawY) { }
        @Override public boolean isOnHome() { return false; }
        @Override public boolean raiseInAppKeyboard() { keyboardRaises++; return raises; }
        @Override public boolean isHomeKeyboardAlwaysOn() { return false; }
        @NonNull @Override public com.termux.app.place.PlaceLayout.Edge appsEdge() {
            return com.termux.app.place.PlaceLayout.Edge.values()[0];
        }
        @Nullable @Override public com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences preferences() { return preferences; }
        @Override public void restyleDockSurface() { dockRestyles++; }
        @Override public boolean openHomeFloatHere() { return false; }
        @SuppressWarnings("ConstantConditions")
        @NonNull @Override public com.termux.app.chrome.WallpaperFrostPainter chromeFrost() { return null; }
        @SuppressWarnings("ConstantConditions")
        @NonNull @Override public com.termux.app.chrome.GlassSurfaceFactory chromeGlass() { return null; }
        @Override public int restingPage() { return 0; }
        @Override public float dpToPx(float dp) { return dp; }
        @Override public void flushPendingAccessoryGeometry() { }
        @Override public void setPromptFieldUnderPalette(boolean under) { promptUnderPalette.add(under); }
        @Override public void applyInAppKeyboardTypingClaim(boolean palette, boolean sheet, boolean drawer) {
            claims.add("claim palette=" + palette + " sheet=" + sheet + " drawer=" + drawer);
        }
    }
}
