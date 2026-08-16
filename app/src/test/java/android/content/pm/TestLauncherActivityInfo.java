package android.content.pm;

/** Test fixture with controllable profile metadata; the platform constructor is package-private. */
public final class TestLauncherActivityInfo extends LauncherActivityInfo {
    private final ApplicationInfo applicationInfo;
    private final long firstInstallTime;
    private final boolean throwCategory;
    private final boolean throwInstall;

    public TestLauncherActivityInfo(ApplicationInfo applicationInfo, long firstInstallTime,
                                    boolean throwCategory, boolean throwInstall) {
        super();
        this.applicationInfo = applicationInfo;
        this.firstInstallTime = firstInstallTime;
        this.throwCategory = throwCategory;
        this.throwInstall = throwInstall;
    }

    @Override public ApplicationInfo getApplicationInfo() {
        if (throwCategory) throw new SecurityException("category");
        return applicationInfo;
    }

    @Override public long getFirstInstallTime() {
        if (throwInstall) throw new SecurityException("install");
        return firstInstallTime;
    }
}
