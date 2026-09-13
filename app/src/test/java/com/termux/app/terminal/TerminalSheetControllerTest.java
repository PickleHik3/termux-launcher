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
import android.view.Gravity;
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
    public void dismissClearsTheDrawersRefreshCallback() {
        TermuxActivity activity = laidOutActivity();

        TerminalSessionBrowser.toggle(activity);

        assertNotNull("the drawer subscribes to foreground refreshes while it is up",
            ReflectionHelpers.getField(activity, "mSessionBrowserRefreshCallback"));

        activity.getTerminalSheetController().dismiss();

        assertNull("a callback left behind would keep reloading a drawer that is gone",
            ReflectionHelpers.getField(activity, "mSessionBrowserRefreshCallback"));
    }

    /** A sheet opened over the drawer must not clear the drawer's own subscription. */
    @Test
    public void aStackedSheetLeavesTheDrawersRefreshCallbackAlone() {
        TermuxActivity activity = laidOutActivity();
        TerminalSessionBrowser.toggle(activity);
        TerminalSheetController sheet = activity.getTerminalSheetController();

        sheet.show("Workspace name", new TextView(activity));
        sheet.dismiss();

        assertEquals(1, sheet.depth());
        assertNotNull(ReflectionHelpers.getField(activity, "mSessionBrowserRefreshCallback"));
    }

    @Test
    public void theSheetIsNeverATextEditorAndNeverTakesFocus() {
        TermuxActivity activity = laidOutActivity();

        TerminalSessionBrowser.promptSaveWorkspace(activity);

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
        assertNull("every field here is a label typed from the key channel, not an EditText",
            findEditText(card));
    }

    /** …and the field it types instead really is driven by the key channel. */
    @Test
    public void typingReachesTheDrawersFieldThroughTheKeyChannel() {
        TermuxActivity activity = laidOutActivity();
        TerminalSessionBrowser.promptSaveWorkspace(activity);
        TextView field = findCaretField(activity.getTerminalSheetController().topCard());
        assertNotNull("the save field is open, so something has to be holding the caret", field);

        assertTrue(activity.handleTerminalSheetCodePoint('v', false));
        assertTrue(activity.handleTerminalSheetCodePoint('i', false));

        assertEquals("vi▏", field.getText().toString());

        assertTrue(activity.handleTerminalSheetKey(KeyEvent.KEYCODE_DEL,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)));

        assertEquals("v▏", field.getText().toString());
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

    /**
     * The keys are how a sheet with a field is typed into, and the plane covers the whole activity —
     * so the Save-workspace prompt used to ask for a name over a keyboard hidden behind its own
     * frost. The plane stops at the keyboard's top edge instead.
     */
    @Test
    public void thePlaneStopsAboveTheInAppKeyboard() {
        FakeSheetHost host = new FakeSheetHost();
        host.keyboardRect.set(0, 600, 400, 800);
        TerminalSheetController sheet = new TerminalSheetController(host);

        sheet.show("Workspace name", new TextView(host.context()), false, new NoopSink(), null);

        View stack = host.findView(R.id.terminal_sheet_stack);
        assertEquals("the cards stop where the keys start", 200,
            ((ViewGroup.MarginLayoutParams) stack.getLayoutParams()).bottomMargin);
    }

    /**
     * A workspace prompt or the search bar rises out of the terminal's own bottom edge: it spans the
     * terminal's frame, sits on that edge, and the plane is cut off there so the panel is clipped by
     * it on the way in and out instead of sliding across the dock.
     */
    @Test
    public void aFootPanelSitsOnTheTerminalsBottomEdgeAndIsClippedByIt() {
        FakeSheetHost host = new FakeSheetHost();
        host.keyboardRect.set(0, 600, 400, 800);
        TerminalSheetController sheet = new TerminalSheetController(host);

        sheet.show("Save workspace", new TextView(host.context()), false, new NoopSink(), null,
            false, TerminalSheetController.Placement.terminalFoot());

        ViewGroup stack = host.findView(R.id.terminal_sheet_stack);
        assertEquals("the plane stops on the terminal's bottom edge, not the keyboard's top", 240,
            ((ViewGroup.MarginLayoutParams) stack.getLayoutParams()).bottomMargin);
        assertTrue("the panel has to be cut off by that edge as it rises and sinks",
            stack.getClipChildren());

        FrameLayout.LayoutParams card =
            (FrameLayout.LayoutParams) sheet.topCard().getLayoutParams();
        assertEquals("edge to edge inside the terminal's frame", 384, card.width);
        assertEquals(8, card.leftMargin);
        assertEquals(0, card.bottomMargin);
        assertEquals("the terminal's ceiling is what stops a long list", 40, card.topMargin);
        assertEquals(Gravity.BOTTOM | Gravity.START, card.gravity);
    }

    /** A list panel has no height of its own to wrap, so it takes the terminal's. */
    @Test
    public void aFillHeightFootPanelTakesTheWholeTerminal() {
        FakeSheetHost host = new FakeSheetHost();
        TerminalSheetController sheet = new TerminalSheetController(host);

        sheet.show("Sessions", new TextView(host.context()), true, null, null, false,
            TerminalSheetController.Placement.terminalFoot());

        FrameLayout.LayoutParams card =
            (FrameLayout.LayoutParams) sheet.topCard().getLayoutParams();
        assertEquals("a weighted list inside a wrap-height card measures to nothing",
            ViewGroup.LayoutParams.MATCH_PARENT, card.height);
        assertEquals(40, card.topMargin);
    }

    /**
     * The drawer: the terminal area's whole height across every pane, its leading edge, and a width
     * that leaves the terminal visible behind it.
     */
    @Test
    public void aDrawerSpansTheTerminalAreaFromItsLeadingEdge() {
        FakeSheetHost host = new FakeSheetHost();
        TerminalSheetController sheet = new TerminalSheetController(host);

        sheet.show("", new TextView(host.context()), true, null, null, false,
            TerminalSheetController.Placement.terminalLeading());

        FrameLayout.LayoutParams card =
            (FrameLayout.LayoutParams) sheet.topCard().getLayoutParams();
        assertEquals("the area's leading edge, not the plane's", 8, card.leftMargin);
        assertEquals(40, card.topMargin);
        assertEquals("every pane of the split, so it is the terminal's panel and not a pane's",
            520, card.height);
        assertEquals("78% of a 384px area, which bites well before the 340dp cap", 300, card.width);
        assertEquals(Gravity.TOP | Gravity.START, card.gravity);
        assertTrue("a drawer travelling its own width has to be cut off by the plane",
            ((ViewGroup) host.findView(R.id.terminal_sheet_stack)).getClipChildren());
    }

    /** The dimming is the terminal's own area; the dock and the status row stay lit. */
    @Test
    public void aDrawerDimsTheTerminalAreaAndNothingElse() {
        FakeSheetHost host = new FakeSheetHost();
        TerminalSheetController sheet = new TerminalSheetController(host);

        sheet.show("", new TextView(host.context()), true, null, null, false,
            TerminalSheetController.Placement.terminalLeading());

        ViewGroup stack = host.findView(R.id.terminal_sheet_stack);
        assertEquals("the scrim, then the card over it", 2, stack.getChildCount());
        FrameLayout.LayoutParams scrim =
            (FrameLayout.LayoutParams) stack.getChildAt(0).getLayoutParams();
        assertEquals(8, scrim.leftMargin);
        assertEquals(40, scrim.topMargin);
        assertEquals(384, scrim.width);
        assertEquals(520, scrim.height);

        sheet.dismiss();
        assertEquals("both leave together", 0, stack.getChildCount());
    }

    /** In a right-to-left layout the leading edge is the other one, and so is the drawer. */
    @Test
    public void aDrawerFollowsTheLayoutDirection() {
        RuntimeEnvironment.setQualifiers("+ar-rXB-ldrtl");
        FakeSheetHost host = new FakeSheetHost();
        TerminalSheetController sheet = new TerminalSheetController(host);

        sheet.show("", new TextView(host.context()), true, null, null, false,
            TerminalSheetController.Placement.terminalLeading());

        FrameLayout.LayoutParams card =
            (FrameLayout.LayoutParams) sheet.topCard().getLayoutParams();
        assertEquals("flush with the area's right edge, which leads in RTL",
            8 + 384 - 300, card.leftMargin);
        assertEquals(300, card.width);
    }

    /** A drawer is a list first; it must not push the terminal around to open a keyboard. */
    @Test
    public void aDrawerDoesNotSummonTheKeyboardUntilAFieldAsksForIt() {
        FakeSheetHost host = new FakeSheetHost();
        TerminalSheetController sheet = new TerminalSheetController(host);

        sheet.show("", new TextView(host.context()), true, new NoopSink(), null, false,
            TerminalSheetController.Placement.terminalLeading());
        assertEquals(0, host.keyboardRequests);

        sheet.requestTypingKeyboard();
        assertEquals(1, host.keyboardRequests);
    }

    /** With no keyboard up there is nothing to avoid, and the plane keeps the whole screen. */
    @Test
    public void thePlaneKeepsTheScreenWhenNoKeyboardIsUp() {
        FakeSheetHost host = new FakeSheetHost();
        TerminalSheetController sheet = new TerminalSheetController(host);

        sheet.show("Sessions", new TextView(host.context()));

        View stack = host.findView(R.id.terminal_sheet_stack);
        assertEquals(0, ((ViewGroup.MarginLayoutParams) stack.getLayoutParams()).bottomMargin);
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
        /** The terminal's frame: below a status bar, above the dock and keys. */
        final Rect terminalRect = new Rect(8, 40, 392, 560);
        float terminalCornerRadiusPx = 20f;
        @Nullable com.termux.app.notice.TerminalDress.Source dressSource =
            new com.termux.app.notice.TerminalDress.Source() {
                @Override public float terminalCornerRadiusPx() { return 20f; }
                @Override public int terminalFillColor() { return 0xFF102030; }
            };
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

        @Override public boolean inAppKeyboardBoundsOnScreen(@NonNull Rect out) {
            if (keyboardRect.isEmpty()) return false;
            out.set(keyboardRect);
            return true;
        }

        @Override public boolean terminalFrameOnScreen(@NonNull Rect out) {
            if (terminalRect.isEmpty()) return false;
            out.set(terminalRect);
            return true;
        }

        @Override public float terminalCornerRadiusPx() {
            return terminalCornerRadiusPx;
        }

        @Nullable @Override public com.termux.app.notice.TerminalDress.Source terminalDressSource() {
            return dressSource;
        }

        @Override public boolean applyWallpaperFrost(@NonNull ImageView frost) {
            frostRequests++;
            return true;
        }

        @NonNull @Override public Drawable sheetSurface() {
            return glass;
        }


        @Override public boolean isReducedMotionEnabled() {
            return true;
        }
    }

    /** The one label carrying the caret, whichever row of the drawer unfolded it. */
    @Nullable
    private static TextView findCaretField(@Nullable View view) {
        if (view instanceof TextView && !(view instanceof EditText)
            && ((TextView) view).getText().toString().contains("▏")) return (TextView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = findCaretField(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
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
