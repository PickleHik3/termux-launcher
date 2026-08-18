package com.termux.app.launcher.data;

final class TestLauncherStore implements LauncherConfigRepository.PreferencesStore {
    String raw = "";
    String legacy = "";
    int schema;
    int writes;
    boolean failPayload;
    boolean failSchema;
    boolean failCommit;

    @Override public String getPinnedItemsV2() { return raw; }
    @Override public int getPinnedItemsSchemaVersion() { return schema; }
    @Override public boolean commitPinnedItems(String value, int version) {
        if (failPayload) throw new IllegalStateException("payload");
        if (failCommit) {
            failCommit = false;
            raw = value;
            schema = version;
            return false;
        }
        if (failSchema) {
            failSchema = false;
            throw new IllegalStateException("schema");
        }
        raw = value;
        schema = version;
        writes++;
        return true;
    }
    @Override public String getLegacyDefaultButtons() { return legacy; }
}
