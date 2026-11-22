package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import com.termux.app.assistant.AssistantRest;
import com.termux.app.launcher.home.HomeRest;
import com.termux.app.page.PageKind;
import com.termux.app.page.Rest;
import com.termux.app.terminal.TerminalRest;
import com.termux.app.x11.DisplayRest;

import org.junit.Test;

/**
 * The host's two answers read from a page's rest give what the host computed before: keyboard on
 * enter and the system-IME owner, over every combination of the facts either reads.
 */
public class RestAnswersParityTest {

    /** Today's answer, as the activity computed it: the five facts of wantsKeyboardOnEnter. */
    private static boolean todayKeyboardOnEnter(PageKind kind, boolean restoredFloat,
            boolean overview, boolean remembered, boolean minimal, boolean alwaysOn) {
        if (overview) return false;
        if (restoredFloat && kind == PageKind.HOME) return true;
        return PlaceKeyboard.keyboardOnEnter(kind, remembered, minimal, alwaysOn);
    }

    private static Rest staticRest(PageKind kind, Rest.Inputs in) {
        switch (kind) {
            case HOME: return HomeRest.of(in);
            case TERMINAL: return TerminalRest.of(in);
            case DISPLAY: return DisplayRest.of(in);
            default: return AssistantRest.of(in);
        }
    }

    private static boolean bit(int bits, int i) {
        return (bits & (1 << i)) != 0;
    }

    @Test
    public void keyboardOnEnterFromTheRestIsTodaysAnswerForEveryKindAndEveryFact() {
        // Facts the answer reads: restored float, overview, remembered, minimal, always on; and
        // the ones it must ignore: in-app keyboard, Android keyboard, display running, mouse mode,
        // float focused.
        for (PageKind kind : PageKind.values()) {
            for (int bits = 0; bits < 1 << 10; bits++) {
                Rest.Inputs in = new Rest.Inputs.Builder()
                    .floatAsksKeyboard(bit(bits, 0)).overviewPreviewing(bit(bits, 1))
                    .keyboardRemembered(bit(bits, 2)).minimal(bit(bits, 3)).homeAlwaysOn(bit(bits, 4))
                    .inAppKeyboard(bit(bits, 5)).androidKeyboard(bit(bits, 6))
                    .displayRunning(bit(bits, 7)).mouseMode(bit(bits, 8)).floatFocused(bit(bits, 9))
                    .build();
                assertEquals(kind + " " + in,
                    todayKeyboardOnEnter(kind, bit(bits, 0), bit(bits, 1), bit(bits, 2),
                        bit(bits, 3), bit(bits, 4)),
                    RestAnswers.keyboardOnEnter(kind, in, staticRest(kind, in)));
            }
        }
    }

    /**
     * The Display's rest puts the launcher's keyboard away while the phone's keyboard types; the
     * landing never did, so its answer stays the landing's own rule.
     */
    @Test
    public void theDisplaysRestDiffersFromItsLandingWhileThePhonesKeyboardTypes() {
        Rest.Inputs in = new Rest.Inputs.Builder().keyboardRemembered(true).androidKeyboard(true)
            .displayRunning(true).build();
        assertEquals(false, DisplayRest.of(in).keyboardUp);
        assertEquals(true, RestAnswers.keyboardOnEnter(PageKind.DISPLAY, in, DisplayRest.of(in)));
    }

    /**
     * Today's owner: the committed page is Home, or the Display is in front and typed into with
     * the phone's keyboard ({@code DisplayKeyboardRoute.decide} == SYSTEM_IME).
     */
    private static boolean todayOwner(boolean committedHome, boolean displayInFront,
            boolean androidKeyboard, boolean displayRunning) {
        boolean display = androidKeyboard && displayInFront && displayRunning;
        return committedHome || display;
    }

    @Test
    public void systemImeOwnerFromTheRestIsTodaysAnswerForEveryFact() {
        for (int bits = 0; bits < 1 << 6; bits++) {
            boolean committedHome = bit(bits, 0), displayInFront = bit(bits, 1);
            boolean androidKeyboard = bit(bits, 2), running = bit(bits, 3);
            Rest.Inputs in = new Rest.Inputs.Builder().androidKeyboard(androidKeyboard)
                .displayRunning(running).inAppKeyboard(bit(bits, 4)).mouseMode(bit(bits, 5)).build();
            boolean display = RestAnswers.displayTypesWithSystemIme(displayInFront, DisplayRest.of(in));
            assertEquals(in.toString(), androidKeyboard && displayInFront && running, display);
            assertEquals(in.toString(),
                todayOwner(committedHome, displayInFront, androidKeyboard, running),
                RestAnswers.systemImeOwner(committedHome, HomeRest.of(in), display));
        }
    }

    /**
     * With the launcher's keyboard off the Terminal and Assistant rests say the phone's keyboard
     * types; the owner never said so there (its flag outlives the keyboard being switched on
     * again), so the owner does not read these two.
     */
    @Test
    public void theTerminalAndAssistantRestsDifferFromTheOwnerWithTheLaunchersKeyboardOff() {
        Rest.Inputs in = new Rest.Inputs.Builder().inAppKeyboard(false).build();
        assertEquals(true, TerminalRest.of(in).systemIme);
        assertEquals(true, AssistantRest.of(in).systemIme);
        assertEquals(false, todayOwner(false, false, false, false));
    }
}
