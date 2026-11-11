package com.termux.ai;

import androidx.annotation.NonNull;

/** {@link TaiRuntimeLinkContractTest} against the in-memory {@link FakeTaiRuntimeLink}. */
public class FakeTaiRuntimeLinkContractTest extends TaiRuntimeLinkContractTest {
    @Override
    protected TaiRuntimeLink newLink(@NonNull FakeTaiRuntimeLink.Gates gates) {
        return new FakeTaiRuntimeLink(gates);
    }
}
