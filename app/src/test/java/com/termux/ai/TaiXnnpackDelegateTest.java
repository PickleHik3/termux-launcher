package com.termux.ai;

import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * On the JVM there is no {@code libtensorflowlite_jni.so} and no {@code libtai_xnnpack.so} to
 * load, so {@link TaiXnnpackDelegate#create} must fail closed — return {@code null} — rather than
 * throw. That is the only behavior of this class a unit test can exercise; the delegate itself
 * only runs on-device.
 */
public class TaiXnnpackDelegateTest {
    @Test
    public void createReturnsNullWithoutNativeLibrary() {
        assertNull(TaiXnnpackDelegate.create(4));
    }
}
