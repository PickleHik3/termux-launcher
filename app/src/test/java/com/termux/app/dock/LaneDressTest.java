package com.termux.app.dock;

import static org.junit.Assert.assertSame;

import com.termux.app.page.Side;

import org.junit.Test;

/** Which side's layout dresses a bottom-stack row, for every row side and front side. */
public class LaneDressTest {

    private final DockLayout front = TestDockLayouts.capsule(2f, 0);
    private final DockLayout other = TestDockLayouts.defaultStyle(3f, 0);

    @Test
    public void aRowOfTheFrontSideWearsTheFrontLayout() {
        for (Side side : Side.values()) {
            assertSame(side.name(), front, LaneDress.layoutFor(side, side, front, other));
            assertSame(side.name(), front, LaneDress.layoutFor(side, side, front, null));
        }
    }

    @Test
    public void aRowOfTheOtherSideWearsThatSidesOwnRest() {
        for (Side frontSide : Side.values()) {
            assertSame(frontSide.name(), other,
                LaneDress.layoutFor(frontSide.other(), frontSide, front, other));
        }
    }

    @Test
    public void aRowOfTheOtherSideWearsTheFrontLayoutWhileThereIsNoLane() {
        for (Side frontSide : Side.values()) {
            assertSame(frontSide.name(), front,
                LaneDress.layoutFor(frontSide.other(), frontSide, front, null));
        }
    }

    @Test
    public void theSharedStacksRowWearsTheFrontLayoutWhicheverSideIsInFront() {
        for (Side frontSide : Side.values()) {
            assertSame(frontSide.name(), front, LaneDress.layoutFor(null, frontSide, front, other));
            assertSame(frontSide.name(), front, LaneDress.layoutFor(null, frontSide, front, null));
        }
    }

    @Test
    public void aRowsAnswerIsTheSameWhicheverSideIsInFront() {
        // Home's rest and Terminal's rest, each played as "front" once and "other" once.
        DockLayout home = front;
        DockLayout terminal = other;
        for (Side row : Side.values()) {
            DockLayout whenHomeFront = LaneDress.layoutFor(row, Side.HOME, home, terminal);
            DockLayout whenTerminalFront = LaneDress.layoutFor(row, Side.TERMINAL, terminal, home);
            assertSame(row.name(), row == Side.HOME ? home : terminal, whenHomeFront);
            assertSame(row.name(), whenHomeFront, whenTerminalFront);
        }
    }
}
