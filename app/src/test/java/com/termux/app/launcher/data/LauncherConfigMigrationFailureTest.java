package com.termux.app.launcher.data;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Test;

public class LauncherConfigMigrationFailureTest {
    @Test public void failedSchemaWriteRollsBackAndIdsRepairDeterministically() throws Exception {
        TestLauncherStore failed = new TestLauncherStore();
        failed.raw = "{\"schemaVersion\":4,\"items\":[{\"type\":\"app\",\"packageName\":\"old\"}]}";
        String prior = failed.raw;
        failed.failSchema = true;
        new LauncherConfigRepository(failed).loadSnapshot();
        assertEquals(prior, failed.raw);

        TestLauncherStore repair = new TestLauncherStore();
        repair.raw = "{\"schemaVersion\":4,\"items\":["
            + "{\"type\":\"folder\",\"id\":\"dup\",\"apps\":[{\"packageName\":\"a\"},{\"packageName\":\"b\"}]},"
            + "{\"type\":\"folder\",\"id\":\"dup\",\"apps\":[{\"packageName\":\"c\"},{\"packageName\":\"d\"}]},"
            + "{\"type\":\"folder\",\"apps\":[{\"packageName\":\"e\"},{\"packageName\":\"f\"}]}]}";
        new LauncherConfigRepository(repair).loadSnapshot();
        JSONObject root = new JSONObject(repair.raw);
        assertEquals("dup", root.getJSONArray("folders").getJSONObject(0).getString("id"));
        assertEquals("dup-2", root.getJSONArray("folders").getJSONObject(1).getString("id"));
        assertEquals("folder-3", root.getJSONArray("folders").getJSONObject(2).getString("id"));
    }
}
