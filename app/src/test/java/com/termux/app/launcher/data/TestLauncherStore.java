package com.termux.app.launcher.data;

final class TestLauncherStore implements LauncherConfigRepository.PreferencesStore {
    String raw = "";
    String legacy = "";
    int schema;
    int writes;
    boolean failPayload;
    boolean failSchema;

    @Override public String getPinnedItemsV2() { return raw; }
    @Override public void setPinnedItemsV2(String value) {
        if (failPayload) throw new IllegalStateException("payload");
        raw = value;
        writes++;
    }
    @Override public void setPinnedItemsSchemaVersion(int value) {
        if (failSchema) {
            failSchema = false;
            throw new IllegalStateException("schema");
        }
        schema = value;
    }
    @Override public String getLegacyDefaultButtons() { return legacy; }
}
