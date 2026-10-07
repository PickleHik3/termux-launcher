package com.termux.app.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IdentitySequenceTest {

    private static void feed(IdentitySequence sequence, Object a, int n, Object b) {
        sequence.add(a);
        sequence.addInt(n);
        sequence.add(b);
    }

    @Test
    public void probeFailsBeforeAnyRecord() {
        IdentitySequence sequence = new IdentitySequence();
        sequence.beginProbe();
        assertFalse(sequence.endProbe());
    }

    @Test
    public void sameIdentitiesAndIntsMatch() {
        IdentitySequence sequence = new IdentitySequence();
        Object a = new Object(), b = new Object();
        sequence.beginRecord();
        feed(sequence, a, 3, b);
        sequence.endRecord();
        sequence.beginProbe();
        feed(sequence, a, 3, b);
        assertTrue(sequence.endProbe());
    }

    @Test
    public void equalButDistinctObjectsDoNotMatch() {
        IdentitySequence sequence = new IdentitySequence();
        String name = new String("main");
        sequence.beginRecord();
        sequence.add(name);
        sequence.endRecord();
        sequence.beginProbe();
        sequence.add(new String("main"));
        assertFalse("a rename to the same text is still a rename", sequence.endProbe());
    }

    @Test
    public void aChangedIntOrLengthFails() {
        IdentitySequence sequence = new IdentitySequence();
        Object a = new Object(), b = new Object();
        sequence.beginRecord();
        feed(sequence, a, 3, b);
        sequence.endRecord();

        sequence.beginProbe();
        feed(sequence, a, 4, b);
        assertFalse(sequence.endProbe());

        sequence.beginProbe();
        sequence.add(a);
        sequence.addInt(3);
        assertFalse("shorter", sequence.endProbe());

        sequence.beginProbe();
        feed(sequence, a, 3, b);
        sequence.add(null);
        assertFalse("longer", sequence.endProbe());
    }

    @Test
    public void nullIsAStepOfItsOwn() {
        IdentitySequence sequence = new IdentitySequence();
        sequence.beginRecord();
        sequence.add(null);
        sequence.endRecord();
        sequence.beginProbe();
        sequence.add(null);
        assertTrue(sequence.endProbe());
    }

    @Test
    public void manyIntsGrowTheRecord() {
        IdentitySequence sequence = new IdentitySequence();
        sequence.beginRecord();
        for (int i = 0; i < 100; i++) sequence.addInt(i);
        sequence.endRecord();
        sequence.beginProbe();
        for (int i = 0; i < 100; i++) sequence.addInt(i);
        assertTrue(sequence.endProbe());
    }

    @Test
    public void invalidateForgetsTheRecord() {
        IdentitySequence sequence = new IdentitySequence();
        sequence.beginRecord();
        sequence.addInt(1);
        sequence.endRecord();
        sequence.invalidate();
        sequence.beginProbe();
        sequence.addInt(1);
        assertFalse(sequence.endProbe());
    }
}
