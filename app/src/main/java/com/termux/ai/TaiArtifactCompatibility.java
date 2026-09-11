package com.termux.ai;

final class TaiArtifactCompatibility {
    private TaiArtifactCompatibility() {}

    static boolean versionAtLeast(String actual, String required) {
        if (!actual.matches("[0-9]+(\\.[0-9]+){1,2}") || !required.matches("[0-9]+(\\.[0-9]+){1,2}")) return false;
        String[] a = actual.split("\\.");
        String[] b = required.split("\\.");
        try {
            for (int i = 0; i < Math.max(a.length, b.length); i++) {
                int compared = Integer.compare(i < a.length ? Integer.parseInt(a[i]) : 0,
                    i < b.length ? Integer.parseInt(b[i]) : 0);
                if (compared != 0) return compared > 0;
            }
            return true;
        } catch (NumberFormatException e) { return false; }
    }
}
