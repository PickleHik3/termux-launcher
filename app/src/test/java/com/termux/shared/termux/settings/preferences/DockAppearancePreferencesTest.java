package com.termux.shared.termux.settings.preferences;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class DockAppearancePreferencesTest {

    private TermuxAppSharedPreferences preferences;
    private SharedPreferences store;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences(
            "dock-appearance-preferences-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    @Test
    public void blurRadiusUsesTheSameBoundsAsItsSlider() {
        preferences.setExtraKeysBlurRadius(-4);
        assertEquals(0, preferences.getExtraKeysBlurRadius());

        preferences.setExtraKeysBlurRadius(31);
        assertEquals(30, preferences.getExtraKeysBlurRadius());
    }

    @Test
    public void iconCountUsesTheSameBoundsAsItsSlider() {
        preferences.setAppLauncherButtonCount(0);
        assertEquals(1, preferences.getAppLauncherButtonCount());

        preferences.setAppLauncherButtonCount(21);
        assertEquals(20, preferences.getAppLauncherButtonCount());
    }

    @Test
    public void dockHeightRejectsValuesOutsideSupportedStorageRange() {
        preferences.setAppLauncherBarHeightScale(-1f);
        assertEquals(0.4f, preferences.getAppLauncherBarHeightScale(), 0f);

        preferences.setAppLauncherBarHeightScale(4f);
        assertEquals(3f, preferences.getAppLauncherBarHeightScale(), 0f);
    }

    @Test
    public void cornerRadiusSupportsStyleDefaultAndSliderBounds() {
        assertEquals(-1, preferences.getAppLauncherDockCornerRadius());

        preferences.setAppLauncherDockCornerRadius(-4);
        assertEquals(-1, preferences.getAppLauncherDockCornerRadius());

        preferences.setAppLauncherDockCornerRadius(18);
        assertEquals(18, preferences.getAppLauncherDockCornerRadius());

        preferences.setAppLauncherDockCornerRadius(41);
        assertEquals(40, preferences.getAppLauncherDockCornerRadius());
    }

    @Test
    public void statusAppearanceInitiallyInheritsExistingDockMaterial() {
        preferences.setExtraKeysBlurRadius(17);
        preferences.setAppBarOpacity(63);
        preferences.setDockGlassGrain(21);
        preferences.setAppLauncherDockCornerRadius(19);

        assertEquals(17, preferences.getStatusBarBlurRadius());
        assertEquals(63, preferences.getStatusBarOpacity());
        assertEquals(21, preferences.getStatusBarGrain());
        assertEquals(19, preferences.getStatusBarCornerRadius());
    }

    @Test
    public void statusAppearanceUsesEditorBoundsOnceCustomized() {
        preferences.setStatusBarBlurRadius(99);
        preferences.setStatusBarOpacity(-1);
        preferences.setStatusBarGrain(101);
        preferences.setStatusBarCornerRadius(44);

        assertEquals(30, preferences.getStatusBarBlurRadius());
        assertEquals(0, preferences.getStatusBarOpacity());
        assertEquals(100, preferences.getStatusBarGrain());
        assertEquals(40, preferences.getStatusBarCornerRadius());
    }

    @Test
    public void surfaceEdgeInsetsDefaultToTheSharedOuterMargin() {
        // A fresh install starts fully linked, so all three read the Base gap. The keyboard used to
        // ship 3dp wider than its neighbours; following Base lines it up with them instead, and an
        // existing install keeps its 13dp because migration sees the difference and detaches it.
        assertEquals(TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET,
            preferences.getDockHorizontalInset());
        assertEquals(TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET,
            preferences.getInAppKeyboardHorizontalInset());
        assertEquals(TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET,
            preferences.getStatusBarHorizontalInset());
    }

    @Test
    public void surfaceEdgeInsetsAreIndependentOnceDetachedAndClampedToTheirSliderBounds() {
        // While linked these three name the same Base gap, so independence is what detaching buys.
        preferences.detachSurfaceValue(TermuxAppSharedPreferences.SurfaceSlot.DOCK,
            TermuxAppSharedPreferences.SurfaceProperty.SIDE_GAP, 0);
        preferences.detachSurfaceValue(TermuxAppSharedPreferences.SurfaceSlot.KEYBOARD,
            TermuxAppSharedPreferences.SurfaceProperty.SIDE_GAP, 0);
        preferences.detachSurfaceValue(TermuxAppSharedPreferences.SurfaceSlot.STATUS,
            TermuxAppSharedPreferences.SurfaceProperty.SIDE_GAP, 0);

        preferences.setDockHorizontalInset(-3);
        preferences.setInAppKeyboardHorizontalInset(96);
        preferences.setStatusBarHorizontalInset(24);

        assertEquals(0, preferences.getDockHorizontalInset());
        assertEquals(TermuxPreferenceConstants.TERMUX_APP.MAX_SURFACE_HORIZONTAL_INSET,
            preferences.getInAppKeyboardHorizontalInset());
        assertEquals(24, preferences.getStatusBarHorizontalInset());
    }

    @Test
    public void surfaceEdgeInsetsMoveTogetherWhileLinked() {
        preferences.setStatusBarHorizontalInset(18);
        assertEquals(18, preferences.getDockHorizontalInset());
        assertEquals(18, preferences.getInAppKeyboardHorizontalInset());
        assertEquals(18, preferences.getStatusBarHorizontalInset());
    }

    @Test
    public void surfaceShapeUsesOneCanonicalPreference() {
        preferences.setAppLauncherDockStyle(
            TermuxPreferenceConstants.TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_ROUNDED);
        preferences.setAppLauncherDockStyle(
            TermuxPreferenceConstants.TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_DEFAULT);
        assertEquals(TermuxPreferenceConstants.TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_DEFAULT,
            preferences.getAppLauncherDockStyle());
    }

    @Test
    public void legacyValarieCapsuleMigratesToRoundedShape() {
        store.edit().putString(TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE,
            "valarie_capsule").commit();

        assertEquals(TermuxPreferenceConstants.TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_ROUNDED,
            preferences.getAppLauncherDockStyle());
    }

}
