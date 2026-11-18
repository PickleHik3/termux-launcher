package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.chrome.scene.ShaderStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The two entry buttons and the Depth row at a ~360dp width and font scale 1.3, with real text
 * metrics: the buttons are equal and single line, and the longest Depth row (a picked image after
 * the photo changed, with its second line) keeps its label clear of its buttons.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.TIRAMISU}, qualifiers = "w360dp-h780dp-xxhdpi", application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PhotoShaderPickerLayoutTest {

    private ContextThemeWrapper mThemed;
    private float mDensity;

    @Before
    public void setUp() {
        RuntimeEnvironment.setFontScale(1.3f);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        mThemed = new ContextThemeWrapper(activity, R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        mDensity = mThemed.getResources().getDisplayMetrics().density;
    }

    private void layOut(@NonNull View v) {
        int w = Math.round(360 * mDensity);
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
    }

    @Test
    public void entryButtonsAreEqualWidthAndSingleLine() {
        ShaderListPage page = new ShaderListPage(mThemed, new ShaderListPage.Listener() {
            @Override public void onPick(@NonNull String id) {}
            @Override public void onPickExample(@NonNull String slug) {}
            @Override public void onOpen(@NonNull String id) {}
            @Override public void onOpenExample(@NonNull String slug) {}
            @Override public void onNew() {}
            @Override public void onFromPhoto() {}
            @Override public void onBackToPhoto() {}
        });
        layOut(page.root());
        TextView a = (TextView) page.newButton();
        TextView b = (TextView) page.fromPhotoButton();
        assertTrue(a.getWidth() > 0);
        assertEquals(a.getWidth(), b.getWidth());
        assertEquals(1, a.getLineCount());
        assertEquals(1, b.getLineCount());
        assertEquals(a.getTop(), b.getTop());
    }

    @Test
    public void depthRowWithItsSecondLineKeepsLabelAndButtonsApart() {
        View row = LayoutInflater.from(mThemed).inflate(R.layout.shader_image_row, null, false);
        ImageRowView view = new ImageRowView(mThemed, row, action -> {});
        view.bind(ImageRowLogic.depth(true, "depth.png", true, ImageRowLogic.Run.IDLE, 0, false), "depth.png", null);
        layOut(row);
        View first = view.firstButton();
        View second = view.secondButton();
        View labelColumn = (View) view.labelView().getParent();
        assertTrue(first.getWidth() > 0 && second.getWidth() > 0);
        assertTrue("label clears the first button", labelColumn.getRight() <= first.getLeft());
        assertTrue("buttons do not overlap", first.getRight() <= second.getLeft());
        assertTrue("buttons stay in the row", second.getRight() <= row.getWidth());
        assertTrue("the label keeps some width", labelColumn.getWidth() > 0);
        assertEquals(1, view.labelView().getLineCount());
        assertTrue(view.noteView().getLineCount() <= 2);
    }

    @Test
    public void everyDepthStateLaysOutWithoutOverlap() {
        for (ImageRowLogic.Run run : ImageRowLogic.Run.values()) {
            for (String name : new String[] {null, "depth.png", ShaderStore.MADE_DEPTH_FILE}) {
                for (boolean failed : new boolean[] {false, true}) {
                    View row = LayoutInflater.from(mThemed).inflate(R.layout.shader_image_row, null, false);
                    ImageRowView view = new ImageRowView(mThemed, row, action -> {});
                    view.bind(ImageRowLogic.depth(true, name, true, run, 42, failed), name, null);
                    layOut(row);
                    View labelColumn = (View) view.labelView().getParent();
                    View first = view.firstButton();
                    assertTrue(labelColumn.getRight() <= first.getLeft());
                    if (view.secondButton().getVisibility() == View.VISIBLE) {
                        assertTrue(first.getRight() <= view.secondButton().getLeft());
                    }
                }
            }
        }
    }
}
