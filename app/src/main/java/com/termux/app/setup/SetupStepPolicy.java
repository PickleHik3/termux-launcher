package com.termux.app.setup;

/**
 * Which stepper dots the user may tap. Pure: the stepper asks, the wizard moves.
 *
 * <p>A visited dot (up to the furthest section shown) goes back or forward again. The next dot
 * opens once the current section was visited, which is whenever it is shown. Dots past that are
 * inert. The last dot, Review, unlocks only after every section before it was visited.
 */
final class SetupStepPolicy {

    private SetupStepPolicy() {}

    /**
     * @param current  the section on screen
     * @param furthest the furthest section ever shown
     * @param count    how many sections there are, Review included as the last
     * @param index    the dot asked about
     */
    static boolean canJump(int current, int furthest, int count, int index) {
        if (index < 0 || index >= count || index == current)
            return false;
        boolean reviewLocked = index == count - 1 && furthest < count - 2;
        if (reviewLocked)
            return false;
        if (index <= furthest)
            return true;
        return index == current + 1 && current <= furthest;
    }
}
