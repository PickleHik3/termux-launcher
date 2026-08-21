package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LauncherConfigCommitFailureTest {
    @Test public void falseAtomicCommitRestoresPriorPayloadAndSchema() {
        TestLauncherStore store = new TestLauncherStore();
        store.raw = "{\"schemaVersion\":4,\"items\":[{\"type\":\"app\",\"packageName\":\"old\"}]}";
        store.schema = 4;
        String prior = store.raw;
        store.failCommit = true;

        new LauncherConfigRepository(store).loadSnapshot();

        assertEquals(prior, store.raw);
        assertEquals(4, store.schema);
    }
}
