package com.termux.app.launcher.drawer;

import android.app.Application;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Map;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerSystemRoleResolverTest {

    @Before
    public void setUp() {
        AppDrawerSystemRoleResolver.invalidate();
    }

    /**
     * A classify pass runs two or three times per drawer open on identical input, and each used to
     * ask the PackageManager for every default role again. The answer is held between passes.
     */
    @Test
    public void repeatedResolvesReuseTheCachedRoleMap() {
        Map<String, AppDrawerCategory> first =
            AppDrawerSystemRoleResolver.resolve(RuntimeEnvironment.getApplication());
        Map<String, AppDrawerCategory> again =
            AppDrawerSystemRoleResolver.resolve(RuntimeEnvironment.getApplication());

        assertSame(first, again);
    }

    /** A package change can be a default-role change, so it drops the cache. */
    @Test
    public void invalidateForcesAFreshResolve() {
        Map<String, AppDrawerCategory> first =
            AppDrawerSystemRoleResolver.resolve(RuntimeEnvironment.getApplication());
        AppDrawerSystemRoleResolver.invalidate();
        Map<String, AppDrawerCategory> again =
            AppDrawerSystemRoleResolver.resolve(RuntimeEnvironment.getApplication());

        assertNotSame(first, again);
    }
}
