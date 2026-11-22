package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    private Activity mActivity;
    private ContextThemeWrapper mThemed;
    private float mDensity;

    @Before
    public void setUp() {
        RuntimeEnvironment.setFontScale(1.3f);
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mThemed = new ContextThemeWrapper(mActivity, R.style.Theme_TermuxActivity_DayNight_NoActionBar);
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
                    assertEquals(View.VISIBLE, row.getVisibility());
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

    // --- the editor page ---

    private int pickTexture;
    private int pickDepth;
    private int sent;

    private ShaderEditorPage editor() {
        return new ShaderEditorPage(mThemed, false, new ShaderEditorPage.Actions() {
            @Override public void onApply(@NonNull ShaderEditorPage.Draft draft) {}
            @Override public void onBackToPhoto() {}
            @Override public void onAlsoPhone(boolean on) {}
            @Override public void onImport() {}
            @Override public void onPickTexture(@NonNull String id) { pickTexture++; }
            @Override public void onPickDepth(@NonNull String id) { pickDepth++; }
            @Override public void onDropFile(@NonNull String id, @NonNull String name) {}
            @Override public void onDelete(@NonNull String id) {}
            @Override public void onSendToAi(@NonNull ShaderEditorPage.Draft draft) { sent++; }
        });
    }

    private static int indexOf(@NonNull ViewGroup content, @NonNull View v) {
        View child = v;
        while (child.getParent() != content) child = (View) child.getParent();
        return content.indexOfChild(child);
    }

    @Test
    public void editorOrderIsKnobsThenPhotoAndDepthThenTheShaderLast() {
        ShaderEditorPage page = editor();
        page.show(null, null, null, false, false, null, false);
        View root = page.root();
        ViewGroup content = root.findViewById(R.id.shader_editor_content);
        int name = indexOf(content, root.findViewById(R.id.shader_name));
        int quality = indexOf(content, root.findViewById(R.id.shader_knob_quality));
        int rate = indexOf(content, root.findViewById(R.id.shader_knob_rate));
        int copy = indexOf(content, root.findViewById(R.id.shader_photo_flow_copy));
        int photo = indexOf(content, root.findViewById(R.id.shader_photo_row));
        int depth = indexOf(content, root.findViewById(R.id.shader_depth_row));
        int also = indexOf(content, root.findViewById(R.id.live_also_row));
        int heading = indexOf(content, root.findViewById(R.id.shader_code_heading));
        int code = indexOf(content, root.findViewById(R.id.shader_code));
        int paste = indexOf(content, root.findViewById(R.id.shader_paste));
        int errors = indexOf(content, root.findViewById(R.id.shader_errors));
        int heavy = indexOf(content, root.findViewById(R.id.shader_too_heavy));
        assertTrue(name < quality && quality < rate && rate < photo && photo < depth && depth < copy);
        assertTrue(copy < also && also < heading && heading < code && code < paste);
        assertTrue(paste < errors && errors < heavy);
        assertEquals(content.getChildCount() - 1, heavy);
    }

    @Test
    public void sendToAiShowsOnlyInThePhotoFlowOnceAPhotoIsChosen() {
        ShaderEditorPage page = editor();
        View send = page.root().findViewById(R.id.shader_photo_flow_copy);
        View heading = page.root().findViewById(R.id.shader_code_heading);
        page.show(null, null, null, false, false, null, false);
        assertEquals(View.GONE, send.getVisibility());
        assertEquals(View.GONE, heading.getVisibility());
        page.setPhotoFlow(true);
        assertEquals("no photo yet: nothing to send", View.GONE, send.getVisibility());
        assertEquals(View.VISIBLE, heading.getVisibility());
        page.setTexture("photo.jpg");
        assertEquals(View.VISIBLE, send.getVisibility());
        assertEquals(mThemed.getString(R.string.photo_shader_send), ((TextView) page.root()
            .findViewById(R.id.shader_photo_flow_copy_button)).getText().toString());
        page.setTexture(null);
        assertEquals(View.GONE, send.getVisibility());
        page.setTexture("photo.jpg");
        page.show(null, null, null, false, false, null, false);
        assertEquals(View.GONE, send.getVisibility());
        assertEquals(View.GONE, heading.getVisibility());
    }

    @Test
    public void sendToAiHandsTheDraftOverAndItsNoteGoesWithTheFlow() {
        ShaderEditorPage page = editor();
        page.show(null, null, null, false, false, null, false);
        page.setPhotoFlow(true);
        page.setTexture("photo.jpg");
        page.root().findViewById(R.id.shader_photo_flow_copy_button).performClick();
        assertEquals(1, sent);
        TextView note = page.root().findViewById(R.id.shader_photo_flow_note);
        page.showSendNote(R.string.photo_shader_send_blocked);
        assertEquals(View.VISIBLE, note.getVisibility());
        assertEquals(mThemed.getString(R.string.photo_shader_send_blocked), note.getText().toString());
        page.setPhotoFlow(true);
        assertEquals(View.GONE, note.getVisibility());
        assertTrue(page.copyPrompt(false));
    }

    @Test
    public void depthShowsNothingUntilAPhotoIsChosenAndGoesWithIt() {
        ShaderEditorPage page = editor();
        page.show(null, null, null, false, false, null, false);
        View depthRoot = page.root().findViewById(R.id.shader_depth_row);
        assertEquals(View.GONE, depthRoot.getVisibility());
        page.setTexture("photo.jpg");
        assertEquals(View.VISIBLE, depthRoot.getVisibility());
        assertEquals(mThemed.getString(R.string.shader_depth_generate),
            ((TextView) page.depthRow().firstButton()).getText().toString());
        assertEquals(mThemed.getString(R.string.shader_depth_choose),
            ((TextView) page.depthRow().secondButton()).getText().toString());
        page.setTexture(null);
        assertEquals(View.GONE, depthRoot.getVisibility());
    }

    @Test
    public void choosingAPhotoLeavesFocusOnThePageNotOnATextField() {
        ShaderEditorPage page = editor();
        page.show(null, null, null, false, false, null, false);
        mActivity.setContentView(page.root());
        View name = page.root().findViewById(R.id.shader_name);
        View content = page.root().findViewById(R.id.shader_editor_content);
        name.requestFocus();
        assertTrue(name.hasFocus());
        page.photoRow().firstButton().performClick();
        assertEquals(1, pickTexture);
        assertFalse("the name field must not take focus back", name.hasFocus());
        assertFalse(page.codeBox().hasFocus());
        assertTrue("the page's content holds focus", content.hasFocus());
        page.setTexture("photo.jpg");
        assertFalse(name.hasFocus());
        assertFalse(page.codeBox().hasFocus());
        page.codeBox().requestFocus();
        page.depthRow().secondButton().performClick();
        assertEquals(1, pickDepth);
        assertFalse(page.codeBox().hasFocus());
        assertTrue(content.hasFocus());
    }
}
