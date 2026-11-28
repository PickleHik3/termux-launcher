package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingStack;
import com.termux.app.tiling.WindowSpec;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** The Workspace list's card: its rows, the row a finger lights, rename (#429). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w400dp-h800dp-mdpi")
public class WorkspaceListViewTest {

    private final List<String> heard = new ArrayList<>();
    private WorkspaceListView view;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        view = new WorkspaceListView(activity, new WorkspaceListView.Style() {
            @Nullable @Override public Drawable glass(float radiusPx, @NonNull View anchor) { return null; }
            @Override public float cornerRadiusPx() { return 0f; }
            @Override public int ink() { return 0xFFFFFFFF; }
            @Override public int accent() { return 0xFF00FF00; }
            @Nullable @Override public Drawable appIcon(@NonNull String appId) { return null; }
        }, new WorkspaceListView.Listener() {
            @Override public void onGo(@NonNull Place place) { heard.add("go " + place); }
            @Override public void onRename(int number, @NonNull String name) {
                heard.add("rename " + number + " " + name);
            }
            @Override public void onDismiss() { heard.add("dismiss"); }
            @Override public void onTextInput(@NonNull EditText field, boolean begin) {
                heard.add(begin ? "keyboard up" : "keyboard down");
            }
        });
        TilingStack stack = TilingStack.firstStart(1, "/home");
        stack.renameWorkspace(1, "Build");
        stack.openIn(Place.workspace(2), WindowSpec.shell("/home"), false);
        view.bind(WorkspaceListRows.of(stack));
        FrameLayout host = new FrameLayout(activity);
        host.addView(view, new FrameLayout.LayoutParams(400, 800));
        host.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, 400, 800);
        activity.setContentView(host);
        host.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, 400, 800);
    }

    /** Workspace row {@code index} (Home's row stands first, at -1). */
    private LinearLayout row(int index) {
        View card = ((FrameLayout) view).getChildAt(0);
        android.widget.ScrollView scroll = (android.widget.ScrollView) ((LinearLayout) card).getChildAt(0);
        return (LinearLayout) ((LinearLayout) scroll.getChildAt(0)).getChildAt(index + 1);
    }

    @Test public void homeStandsFirstAndEachWorkspaceSaysHowManyWindows() {
        // Round 2: the list had no Home row and no window counts, as the prototype has.
        assertEquals("Home", ((TextView) row(-1).getChildAt(2)).getText().toString());
        assertEquals("Home", row(-1).getContentDescription().toString());
        assertEquals("1 window", ((TextView) row(0).getChildAt(3)).getText().toString());
        assertEquals("no count on New Workspace", 3, row(2).getChildCount());
        row(-1).performClick();
        assertEquals(java.util.Arrays.asList("go Home page 1"), heard);
    }

    @Test public void eachRowShowsItsNumberItsIconAndItsName() {
        LinearLayout first = row(0);
        assertEquals("1", ((TextView) first.getChildAt(0)).getText().toString());
        assertEquals(android.widget.ImageView.class, first.getChildAt(1).getClass());
        assertEquals("Build", ((TextView) first.getChildAt(2)).getText().toString());
        assertEquals("Workspace 2", ((TextView) row(1).getChildAt(2)).getText().toString());
        assertEquals("New Workspace", ((TextView) row(2).getChildAt(2)).getText().toString());
    }

    @Test public void rowsAreSpokenWithTheirNumberAndName() {
        assertEquals("Workspace 1, Build, 1 window", row(0).getContentDescription().toString());
        assertEquals("Workspace 2, 1 window", row(1).getContentDescription().toString());
        assertTrue(row(0).isFocusable());
    }

    @Test public void aTapOnARowGoesThere() {
        row(1).performClick();
        assertEquals(java.util.Arrays.asList("go Workspace 2"), heard);
    }

    @Test public void theFingerLightsTheRowUnderItAndLettingGoOnItGoesThere() {
        int[] at = new int[2];
        row(1).getLocationOnScreen(at);
        view.beginFinger();
        view.track(at[0] + 20f, at[1] + 10f);
        assertEquals("Workspace 2's row, after Home's", 2, view.lit());
        assertEquals(Place.workspace(2), view.release(at[0] + 20f, at[1] + 10f));
    }

    @Test public void lettingGoOutsideTheRowsGoesNowhere() {
        view.beginFinger();
        view.track(5f, 5f);
        assertEquals(-1, view.lit());
        assertNull(view.release(5f, 5f));
    }

    @Test public void aLongPressOnANamedRowOpensItsNameAndDoneKeepsIt() {
        row(0).performLongClick();
        EditText field = (EditText) row(0).getChildAt(2);
        assertEquals("Build", field.getText().toString());
        assertTrue(view.holdsOpen());
        field.setText("Ship");
        view.commitRename();
        assertEquals(java.util.Arrays.asList("keyboard up", "keyboard down", "rename 1 Ship", "dismiss"), heard);
        assertFalse(view.holdsOpen());
    }

    @Test public void theWaitingWorkspaceHasNoNameToChange() {
        row(2).performLongClick();
        assertFalse(view.holdsOpen());
        assertEquals(new ArrayList<String>(), heard);
    }

    @Test public void abandoningARenameDropsTheName() {
        row(0).performLongClick();
        view.abandonRename();
        assertFalse(view.holdsOpen());
        assertEquals(java.util.Arrays.asList("keyboard up", "keyboard down"), heard);
    }

    @Test public void aTapOnTheNameFieldAsksForTheKeyboardAgain() {
        row(0).performLongClick();
        EditText field = (EditText) row(0).getChildAt(2);
        heard.clear();
        field.performClick();
        assertEquals(java.util.Arrays.asList("keyboard up"), heard);
    }

    @Test public void aCarriedWindowLightsTheRowsButAStillFingerRenamesNothing() {
        int[] at = new int[2];
        row(0).getLocationOnScreen(at);
        view.beginCarry();
        view.track(at[0] + 20f, at[1] + 10f);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(
            android.view.ViewConfiguration.getLongPressTimeout() * 2L, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("Workspace 1's row, after Home's", 1, view.lit());
        assertFalse(view.holdsOpen());
        assertEquals(Place.workspace(1), view.release(at[0] + 20f, at[1] + 10f));
    }

    /** Round 3: a finger that rested on a row on its way went into a rename; only Rename renames. */
    @Test public void aFingerRestingOnARowNeverRenamesItAndLettingGoGoesThere() {
        int[] at = new int[2];
        row(1).getLocationOnScreen(at);
        view.beginFinger();
        view.track(at[0] + 20f, at[1] + 10f);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(
            android.view.ViewConfiguration.getLongPressTimeout() * 3L, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertFalse(view.holdsOpen());
        assertEquals(Place.workspace(2), view.release(at[0] + 20f, at[1] + 10f));
        assertEquals(new ArrayList<String>(), heard);
    }

    /** Round 3: an unnamed Workspace's rename read "Name"; it reads as the Workspace does. */
    @Test public void anUnnamedWorkspacesRenameReadsWorkspaceN() {
        row(1).performLongClick();
        EditText field = (EditText) row(1).getChildAt(2);
        assertEquals("", field.getText().toString());
        assertEquals("Workspace 2", field.getHint().toString());
    }

    @Test public void theCardStandsAtTheLeftWhateverTheLayoutDirection() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) ((FrameLayout) view).getChildAt(0).getLayoutParams();
        assertEquals(android.view.Gravity.LEFT, params.gravity & android.view.Gravity.HORIZONTAL_GRAVITY_MASK);
    }

    @Test public void rowsAreFoundOnScreenEvenWhenTheWindowIsOffsetFromTheCorner() {
        ((View) view.getParent()).setTranslationY(300f);
        int[] at = new int[2];
        row(1).getLocationOnScreen(at);
        assertEquals("Workspace 2's row, after Home's", 2, view.rowAt(at[0] + 20f, at[1] + 10f));
        assertEquals(-1, view.rowAt(at[0] + 20f, at[1] - 300f));
    }
}
