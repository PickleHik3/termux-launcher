package com.termux.app.wall;

import androidx.annotation.NonNull;

/**
 * The AI page under Terl (ADR 0043): the Assistant as a page to the left of Home 1, behind the
 * Home setting "AI page" ({@code home.ai_page}, off by default). Every way into the Assistant
 * (search's Assistant row, the drawer's entry, a bound leader key, the open-Assistant intent)
 * comes here: with the page on it lands on it, with it off it offers to turn it on first. The
 * Assistant is no window kind of a Workspace any more.
 *
 * <p>Pure: the activity's {@link Host} does the work.
 */
public final class TerlAiPage {

    private TerlAiPage() {}

    /** What the AI page needs from the activity. */
    public interface Host {
        /** Whether the AI page is on. */
        boolean isOn();

        /** Turns the AI page on (the setting); the page itself is placed by {@link #goToPage}. */
        void turnOn();

        /** Stands the page left of Home 1 when it is not there yet, then goes to it. */
        void goToPage();

        /** Asks whether to turn the AI page on; {@code turnOn} runs when the user agrees. */
        void offerToTurnOn(@NonNull Runnable turnOn);
    }

    /** A way into the Assistant: the AI page, or the offer to turn it on and then the page. */
    public static void open(@NonNull Host host) {
        if (host.isOn()) {
            host.goToPage();
            return;
        }
        host.offerToTurnOn(() -> {
            host.turnOn();
            host.goToPage();
        });
    }

    /**
     * Whether the one-time tip inviting the user to turn the AI page on is due: under Terl, on
     * Home, the page still off, and the tip never shown.
     */
    public static boolean nudgeDue(boolean terl, boolean onHome, boolean aiPageOn, boolean nudged) {
        return terl && onHome && !aiPageOn && !nudged;
    }
}
