package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

/**
 * What the terminal sheet plane has to keep true now that the session browser and its prompts live
 * on it instead of on dialog windows.
 *
 * <p>Two properties carry the whole migration. The plane must never become a text editor — a focused
 * field here would take the {@code InputConnection} off {@code TerminalView} and summon the system
 * IME, which is the swap the browser's old search box cost on every open. And back must close one
 * card, not the stack: the workspace picker opens a confirmation over itself, and a press that took
 * both would drop the user on the terminal.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TerminalSheetControllerTest {

    @Test
    public void aSheetOpensAndCloses() {
        TermuxActivity activity = laidOutActivity();
        TerminalSheetController sheet = activity.getTerminalSheetController();
        assertFalse(sheet.isOpen());

        sheet.show("Sessions", new TextView(activity));

        assertTrue(sheet.isOpen());
        assertEquals(1, sheet.depth());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.terminal_sheet_host).getVisibility());

        sheet.dismiss();

        assertFalse(sheet.isOpen());
        assertEquals(View.INVISIBLE,
            activity.findViewById(R.id.terminal_sheet_host).getVisibility());
    }

    @Test
    public void backClosesTheTopSheetOnly() {
        TermuxActivity activity = laidOutActivity();
        TerminalSheetController sheet = activity.getTerminalSheetController();
        View picker = new TextView(activity);
        sheet.show("Load workspace", picker);
        sheet.show("Delete “work”?", new TextView(activity));
        assertEquals(2, sheet.depth());

        activity.onBackPressed();

        assertEquals("the confirmation went, the picker under it stayed", 1, sheet.depth());
        assertEquals(picker, ((ViewGroup) sheet.topCard()).getChildAt(1));

        activity.onBackPressed();

        assertFalse(sheet.isOpen());
    }

    /**
     * The route back actually travels on a device: KEYCODE_BACK is claimed in the key channel and
     * {@code onBackPressed()} never runs. Both routes have to pop exactly one card.
     */
    @Test
    public void aBackKeystrokeAlsoClosesTheTopSheetOnly() {
        TermuxActivity activity = laidOutActivity();
        TerminalSheetController sheet = activity.getTerminalSheetController();
        sheet.show("Load workspace", new TextView(activity));
        sheet.show("Delete “work”?", new TextView(activity));

        assertTrue(activity.handleTerminalSheetKey(KeyEvent.KEYCODE_BACK,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)));

        assertEquals(1, sheet.depth());
    }

    /** The palette keeps the slot above the plane, so the sheet can never swallow its escape. */
    @Test
    public void thePaletteStillConsumesBackBeforeTheSheet() {
        TermuxActivity activity = laidOutActivity();
        TerminalSheetController sheet = activity.getTerminalSheetController();
        sheet.show("Sessions", new TextView(activity));
        TerminalCommandPaletteController palette = activity.getCommandPaletteController();
        ReflectionHelpers.setField(palette, "mOpen", true);

        activity.onBackPressed();

        assertFalse(palette.isOpen());
        assertTrue("the sheet must survive the back press that collapses the palette",
            sheet.isOpen());
    }

    @Test
    public void dismissClearsTheBrowsersRefreshCallback() {
        TermuxActivity activity = laidOutActivity();

        TerminalSessionBrowser.show(activity);

        assertNotNull("the browser subscribes to foreground refreshes while it is up",
            ReflectionHelpers.getField(activity, "mSessionBrowserRefreshCallback"));

        activity.getTerminalSheetController().dismiss();

        assertNull("a callback left behind would keep reloading a browser that is gone",
            ReflectionHelpers.getField(activity, "mSessionBrowserRefreshCallback"));
    }

    /** A sheet opened over the browser must not clear the browser's own subscription. */
    @Test
    public void aStackedSheetLeavesTheBrowsersRefreshCallbackAlone() {
        TermuxActivity activity = laidOutActivity();
        TerminalSessionBrowser.show(activity);
        TerminalSheetController sheet = activity.getTerminalSheetController();

        sheet.show("Workspace name", new TextView(activity));
        sheet.dismiss();

        assertEquals(1, sheet.depth());
        assertNotNull(ReflectionHelpers.getField(activity, "mSessionBrowserRefreshCallback"));
    }

    @Test
    public void theSheetIsNeverATextEditorAndNeverTakesFocus() {
        TermuxActivity activity = laidOutActivity();

        TerminalSessionBrowser.show(activity);

        View host = activity.findViewById(R.id.terminal_sheet_host);
        assertFalse(host.onCheckIsTextEditor());
        assertFalse(host.isFocusable());
        assertNull("a focused view here would take the InputConnection off TerminalView",
            activity.getCurrentFocus());
        View card = activity.getTerminalSheetController().topCard();
        assertNotNull(card);
        assertFalse(card.onCheckIsTextEditor());
        assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS,
            ((ViewGroup) card).getDescendantFocusability());
        assertNull("the search field must be a label typed from the key channel, not an EditText",
            findEditText(card));
    }

    /** …and the field it types instead really is driven by the key channel. */
    @Test
    public void typingReachesTheSearchFieldThroughTheKeyChannel() {
        TermuxActivity activity = laidOutActivity();
        TerminalSessionBrowser.show(activity);
        TextView search = activity.findViewById(R.id.session_browser_search);

        assertTrue(activity.handleTerminalSheetCodePoint('v', false));
        assertTrue(activity.handleTerminalSheetCodePoint('i', false));

        assertEquals("vi▏", search.getText().toString());

        assertTrue(activity.handleTerminalSheetKey(KeyEvent.KEYCODE_DEL,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)));

        assertEquals("v▏", search.getText().toString());
    }

    // ------------------------------------------------------------ the seam, driven by a fake host

    @Test
    public void aStackYieldsThePlanesOnceAndHoldsTheInterceptorUntilItEmpties() {
        FakeSheetHost host = new FakeSheetHost();
        TerminalSheetController sheet = new TerminalSheetController(host);

        assertTrue(sheet.show("Load workspace", new TextView(host.context())));
        assertTrue(sheet.show("Delete “work”?", new TextView(host.context())));

        assertEquals("only the first card takes over from the drawer and the FULL pane",
            1, host.yields);
        assertEquals(Boolean.TRUE, host.interceptorActive);

        sheet.dismiss();
        assertEquals("the plane still holds the slot while a card is up",
            Boolean.TRUE, host.interceptorActive);

        sheet.dismiss();
        assertEquals(Boolean.FALSE, host.interceptorActive);
        assertEquals(View.INVISIBLE, host.plane.getVisibility());
    }

    /** A confirmation is all buttons; summoning a keyboard for it would just push the terminal around. */
    @Test
    public void onlyASheetWithSomewhereToTypeSummonsTheKeyboard() {
        FakeSheetHost host = new FakeSheetHost();
        TerminalSheetController sheet = new TerminalSheetController(host);

        sheet.show("Delete “work”?", new TextView(host.context()));
        assertEquals(0, host.keyboardRequests);

        sheet.show("Workspace name", new TextView(host.context()), false, new NoopSink(), null);
        assertEquals(1, host.keyboardRequests);
    }

    /** The plane covers the keyboard too, and those keys are how the sheet is typed into. */
    @Test
    public void aTapOnTheKeyboardFallsThroughWhereATapOutsideDismisses() {
        FakeSheetHost host = new FakeSheetHost();
        host.keyboardRect.set(0, 600, 400, 800);
        TerminalSheetController sheet = new TerminalSheetController(host);
        sheet.show("Workspace name", new TextView(host.context()), false, new NoopSink(), null);

        assertFalse("a DOWN on a key must reach the keyboard",
            host.plane.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, 100f, 700f)));
        assertTrue(sheet.isOpen());

        assertTrue(host.plane.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, 100f, 100f)));
        assertTrue("dismissed on the finished tap, never on DOWN", sheet.isOpen());
        assertTrue(host.plane.dispatchTouchEvent(touch(MotionEvent.ACTION_UP, 100f, 100f)));
        assertFalse(sheet.isOpen());
    }

    @Test
    public void aCardWearsTheHostsGlassAndFrost() {
        FakeSheetHost host = new FakeSheetHost();
        TerminalSheetController sheet = new TerminalSheetController(host);

        sheet.show("Sessions", new TextView(host.context()));

        assertSame(host.glass, sheet.topCard().getBackground());
        assertEquals(1, host.frostRequests);
        assertEquals("the host frosted the plane, so the live blur rests",
            View.GONE, host.blur.getVisibility());
    }

    @NonNull
    private static MotionEvent touch(int action, float x, float y) {
        return MotionEvent.obtain(0L, 0L, action, x, y, 0);
    }

    private static final class NoopSink implements TerminalSheetController.TextSink {
        @Override public void onText(@NonNull String text) { }
        @Override public void onBackspace() { }
        @Override public boolean onCommit() { return false; }
    }

    /** The plane's views on a bare root, and a record of every ask the controller made. */
    private static final class FakeSheetHost implements TerminalSheetController.Host {
        final FrameLayout root;
        final FrameLayout plane;
        final ImageView frost;
        final View blur;
        final Drawable glass = new ColorDrawable(0xFF102030);
        final Rect keyboardRect = new Rect();
        int yields;
        int keyboardRequests;
        int frostRequests;
        @Nullable Boolean interceptorActive;

        FakeSheetHost() {
            Context context = RuntimeEnvironment.getApplication();
            root = new FrameLayout(context);
            plane = new FrameLayout(context);
            plane.setId(R.id.terminal_sheet_host);
            frost = new ImageView(context);
            frost.setId(R.id.terminal_sheet_wallpaper_backdrop);
            blur = new View(context);
            blur.setId(R.id.terminal_sheet_blur);
            FrameLayout stack = new FrameLayout(context);
            stack.setId(R.id.terminal_sheet_stack);
            plane.addView(frost);
            plane.addView(blur);
            plane.addView(stack);
            root.addView(plane);
            root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 400, 800);
        }

        @NonNull @Override public Context context() {
            return root.getContext();
        }

        @Nullable @Override public <T extends View> T findView(int viewId) {
            return root.findViewById(viewId);
        }

        @Override public void yieldCompetingPlanes() {
            yields++;
        }

        @Override public void ensureInAppTypingKeyboard() {
            keyboardRequests++;
        }

        @Override public void setSheetInterceptorActive(boolean active) {
            interceptorActive = active;
        }

        @Override public boolean isPointOnInAppKeyboard(float rawX, float rawY) {
            return keyboardRect.contains(Math.round(rawX), Math.round(rawY));
        }

        @Override public boolean applyWallpaperFrost(@NonNull ImageView frost) {
            frostRequests++;
            return true;
        }

        @NonNull @Override public Drawable sheetSurface() {
            return glass;
        }

        @Override public boolean dockBoundsOnScreen(@NonNull Rect out) {
            return false;
        }

        @Override public boolean isReducedMotionEnabled() {
            return true;
        }
    }

    @Nullable
    private static EditText findEditText(@NonNull View view) {
        if (view instanceof EditText) return (EditText) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            EditText found = findEditText(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static TermuxActivity laidOutActivity() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        return activity;
    }
}
