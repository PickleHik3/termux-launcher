package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.termux.R;
import com.termux.app.chrome.scene.ShaderStore;
import com.termux.app.chrome.scene.WallpaperMotion;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;
import android.app.Application;

/**
 * The Photo | Video | Shader host on Android 13: it opens on the stored motion, the Shader kind
 * lists shaders and opens the editor, Back goes from the editor to the list, an empty shader is
 * not applied, the Video page has its format line and Back to photo only while a motion is in
 * use, and terminal-app mode shows the phone-wallpaper switch. Thumbnails stay blank here (GL
 * does not draw under Robolectric). Android 9 and below: {@link WallpaperLiveHostBelow29Test}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.TIRAMISU}, qualifiers = "w411dp-h891dp-xxhdpi", application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WallpaperLiveHostTest {

    private Activity mActivity;
    private ContextThemeWrapper mThemed;
    private SharedPreferences mStore;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mThemed = new ContextThemeWrapper(mActivity, R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(mActivity, false);
        assertNotNull(prefs);
        mStore = prefs.getSharedPreferences();
        ShaderStore.apply(mActivity, mStore, WallpaperMotion.photo());
    }

    @NonNull
    private WallpaperLiveHost open(boolean homeApp) {
        WallpaperPickerPage.Slots slots = new WallpaperPickerPage.Slots() {
            @NonNull @Override public WallpaperSlots.State read() {
                return new WallpaperSlots.State(WallpaperSlots.Choice.photo(), WallpaperSlots.Choice.sameAsHome());
            }

            @Override public void apply(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice,
                                        @androidx.annotation.Nullable WallpaperSlots.Callback cb) {}

            @Override public boolean isHomeApp() {
                return homeApp;
            }
        };
        WallpaperPickerPage picker = new WallpaperPickerPage(mThemed, slots, new WallpaperPickerPage.Listener() {
            @Override public void onPickPhoto(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperPickerPage.ReturnState back) {}
            @Override public void onApplied(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice) {}
            @Override public void onOpenLook() {}
            @Override public void onOpenIcons() {}
        }, () -> {});
        WallpaperLiveHost host = new WallpaperLiveHost(mThemed, picker, () -> {});
        mActivity.setContentView(host.root());
        return host;
    }

    private static void tap(View v) {
        v.performClick();
    }

    @Test
    public void opensOnPhotoWithNothingInUse() {
        WallpaperLiveHost host = open(true);
        assertEquals(WallpaperMotion.Kind.PHOTO, host.kind());
        assertFalse(host.showingLive());
        assertNotNull(host.list());
        assertEquals(View.VISIBLE, host.picker().root().getVisibility());
        assertEquals(View.GONE, host.list().root().getVisibility());
        assertEquals(View.GONE, host.video().root().getVisibility());
    }

    @Test
    public void theKindRowOffersPhotoVideoAndShader() {
        WallpaperLiveHost host = open(true);
        MaterialButtonToggleGroup group = host.root().findViewById(R.id.segmented_pill_track);
        int visible = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i).getVisibility() == View.VISIBLE) visible++;
        }
        assertEquals(3, visible);
        assertEquals(R.id.segmented_pill_default, group.getCheckedButtonId());

        group.check(R.id.segmented_pill_third);
        assertEquals(WallpaperMotion.Kind.SHADER, host.kind());
        assertTrue(host.showingLive());
        assertEquals(View.GONE, host.picker().root().getVisibility());
        assertEquals(View.VISIBLE, host.list().root().getVisibility());

        group.check(R.id.segmented_pill_capsule);
        assertEquals(WallpaperMotion.Kind.VIDEO, host.kind());
        assertEquals(View.VISIBLE, host.video().root().getVisibility());
        assertEquals(View.GONE, host.list().root().getVisibility());

        group.check(R.id.segmented_pill_default);
        assertEquals(WallpaperMotion.Kind.PHOTO, host.kind());
        assertEquals(View.VISIBLE, host.picker().root().getVisibility());
    }

    @Test
    public void opensOnShaderWhileOneIsInUse_withBackToPhotoOnTheList() {
        ShaderStore.apply(mActivity, mStore, WallpaperMotion.shader("abc"));
        WallpaperLiveHost host = open(true);
        assertEquals(WallpaperMotion.Kind.SHADER, host.kind());
        // The sheet must not wait on the GONE photo picker's readiness (it never comes).
        assertTrue(host.showingLive());
        assertEquals(View.GONE, host.picker().root().getVisibility());
        assertEquals(View.VISIBLE, host.list().backToPhotoButton().getVisibility());
    }

    @Test
    public void opensOnVideoWhileOneIsInUse() {
        ShaderStore.apply(mActivity, mStore, WallpaperMotion.video("loop.mp4"));
        WallpaperLiveHost host = open(true);
        assertEquals(WallpaperMotion.Kind.VIDEO, host.kind());
        assertEquals(View.VISIBLE, host.video().backToPhotoButton().getVisibility());
    }

    @Test
    public void theVideoPageOpensWithTheStoredStatusLine() {
        ShaderStore.apply(mActivity, mStore, WallpaperMotion.video("127.gif"));
        mStore.edit().putString("wallpaper_video_hint", "GIF 320x320").commit();
        WallpaperLiveHost host = open(true);
        host.video().show(WallpaperMotion.video("127.gif"), 30);
        host.video().setHint("In use: 127.gif\nGIF 320x320");
        host.video().setBusy(false);
        assertEquals(View.VISIBLE, host.video().hintLine().getVisibility());
        assertEquals("In use: 127.gif\nGIF 320x320", host.video().hintLine().getText().toString());
        MaterialButtonToggleGroup group = host.root().findViewById(R.id.segmented_pill_track);
        group.check(R.id.segmented_pill_default);
        group.check(R.id.segmented_pill_capsule);
        assertEquals(View.VISIBLE, host.video().hintLine().getVisibility());
        assertTrue(host.video().hintLine().getText().toString().startsWith("In use: 127.gif"));
    }

    @Test
    public void theVideoPage_hasTheFormatLineAndBackToPhotoOnlyWhileInUse() {
        WallpaperLiveHost host = open(true);
        host.video().show(WallpaperMotion.photo(), 30);
        android.widget.TextView format = host.video().root().findViewById(R.id.video_format);
        assertTrue(format.getText().toString().startsWith("Best: an H.265 MP4"));
        assertEquals(View.VISIBLE, host.video().chooseButton().getVisibility());
        assertEquals(View.GONE, host.video().backToPhotoButton().getVisibility());
        assertEquals(View.GONE, host.video().hintLine().getVisibility());
        host.video().show(WallpaperMotion.video("loop.mp4"), 30);
        assertEquals(View.VISIBLE, host.video().backToPhotoButton().getVisibility());
    }

    @Test
    public void newShaderOpensTheEditor_andBackGoesToTheList() {
        MaterialButtonToggleGroup group = null;
        WallpaperLiveHost host = open(true);
        group = host.root().findViewById(R.id.segmented_pill_track);
        group.check(R.id.segmented_pill_third);
        assertFalse(host.consumeBack());
        tap(host.list().newButton());
        assertTrue(host.editorShown());
        ShaderEditorPage editor = host.editor();
        assertTrue(editor.isNew());
        // A new shader has nothing to delete and nothing in use to go back from.
        assertEquals(View.GONE, editor.deleteButton().getVisibility());
        assertEquals(View.GONE, editor.backToPhotoButton().getVisibility());
        assertEquals(View.GONE, editor.errorStrip().getVisibility());
        assertEquals(View.GONE, editor.tooHeavyLine().getVisibility());

        assertTrue(host.consumeBack());
        assertFalse(host.editorShown());
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(View.VISIBLE, host.list().root().getVisibility());
        assertEquals(View.GONE, editor.root().getVisibility());
        assertFalse(host.consumeBack());
    }

    @Test
    public void applyingAnEmptyShader_showsTheMessageAndChangesNothing() {
        WallpaperLiveHost host = open(true);
        ((MaterialButtonToggleGroup) host.root().findViewById(R.id.segmented_pill_track))
            .check(R.id.segmented_pill_third);
        tap(host.list().newButton());
        ShaderEditorPage editor = host.editor();
        tap(editor.applyButton());
        assertEquals(View.VISIBLE, editor.errorStrip().getVisibility());
        assertEquals(mActivity.getString(R.string.shader_empty),
            ((android.widget.TextView) editor.errorStrip()).getText().toString());
        assertEquals(WallpaperMotion.photo(), ShaderStore.readMotion(mStore));
        assertTrue(editor.applyButton().isEnabled());
    }

    @Test
    public void theEditorShowsTooHeavyOnlyWhenTheRunningShaderHoldsForWeight() {
        WallpaperLiveHost host = open(true);
        host.setHeldReasonSource(() -> com.termux.app.chrome.scene.ShaderSafety.REASON_TOO_HEAVY);
        ((MaterialButtonToggleGroup) host.root().findViewById(R.id.segmented_pill_track))
            .check(R.id.segmented_pill_third);
        tap(host.list().newButton());
        assertEquals(View.VISIBLE, host.editor().tooHeavyLine().getVisibility());
        host.setHeldReasonSource(() -> null);
        host.consumeBack();
        tap(host.list().newButton());
        assertEquals(View.GONE, host.editor().tooHeavyLine().getVisibility());
    }

    @Test
    public void alsoPhoneRowShowsInTerminalAppModeOnly() {
        assertEquals(View.GONE, open(true).editor().alsoRow().getVisibility());
        assertEquals(View.VISIBLE, open(false).editor().alsoRow().getVisibility());
    }

    @Test
    public void theVideoSourceUntilWaveB_saysVideoIsNotReady() {
        WallpaperLiveHost.VideoSource.Result r = WallpaperLiveHost.VideoSource.UNAVAILABLE
            .importFile(mActivity, android.net.Uri.parse("content://x/y"));
        assertFalse(r.ok);
        assertEquals("Video arrives in the next build", r.error);
    }
}
