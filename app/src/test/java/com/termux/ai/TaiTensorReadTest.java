package com.termux.ai;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TaiTensorReadTest {

    @Test
    public void theSliceReadRunsOnlyOnTheVerifiedLitert() {
        assertTrue(TaiTensorRead.isVerifiedLitert("2.3.2"));
        assertFalse(TaiTensorRead.isVerifiedLitert("2.3.3"));
        assertFalse(TaiTensorRead.isVerifiedLitert("2.4.0"));
        assertFalse(TaiTensorRead.isVerifiedLitert("unknown"));
        assertFalse(TaiTensorRead.isVerifiedLitert(null));
    }
}
