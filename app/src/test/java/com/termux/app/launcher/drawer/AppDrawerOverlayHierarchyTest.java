package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import com.termux.R;
import com.termux.app.TermuxActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerOverlayHierarchyTest {

    @Test public void drawerHostRemainsASiblingOutsideTheAccessoryStack() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        View host = activity.findViewById(R.id.app_drawer_host);
        View accessory = activity.findViewById(R.id.accessory_stack_container);
        View contentRoot = activity.findViewById(R.id.activity_termux_root_relative_layout);
        assertSame(host.getParent(), contentRoot.getParent());
        assertTrue(isDescendant(accessory, contentRoot));
        assertFalse(isDescendant(host, accessory));
        assertFalse(isDescendant(accessory, host));
    }

    @Test public void pagerDotsAndCategoriesAreCreatedOnlyInsideThePlanesContentHost() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        AppDrawerController controller = activity.getAppDrawerController();
        assertTrue((Boolean) ReflectionHelpers.callInstanceMethod(controller, "bindViews"));
        AppDrawerPlaneView plane = ReflectionHelpers.getField(controller, "mPlane");
        AppDrawerContentView content = ReflectionHelpers.getField(controller, "mContent");
        assertSame(plane.getContentHost(), content.getParent());
        assertSame(content, content.getHorizontalPager().getParent());
        assertSame(content, content.getPageIndicator().getParent());
        assertSame(content, content.getCategoryView().getParent());
        assertSame(content.getCategoryView(), content.getCategoryView().getOverview().getParent());
        assertSame(content.getCategoryView(), content.getCategoryView().getDetailList().getParent());
        assertEquals(1, plane.getContentHost().getChildCount());
    }

    @Test public void categorySourcesContainNoAccessoryTerminalSecondClockOrEditText() throws Exception {
        String[] files = {
            "AppDrawerCategoryView.java", "AppDrawerCategoryTileView.java",
            "AppDrawerCategoryTileAdapter.java", "AppDrawerCategoryDetailAdapter.java",
            "AppDrawerCategoryMorphView.java", "AppDrawerCategoryGridMetrics.java"
        };
        for (String file : files) {
            java.nio.file.Path root = Paths.get(System.getProperty("user.dir"));
            java.nio.file.Path sourcePath = root.resolve(
                "src/main/java/com/termux/app/launcher/drawer/" + file);
            if (!Files.exists(sourcePath)) sourcePath = root.resolve(
                "app/src/main/java/com/termux/app/launcher/drawer/" + file);
            String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
            assertFalse(file, source.contains("updateSize("));
            assertFalse(file, source.contains("computeCombinedHeight("));
            assertFalse(file, source.contains("setTerminalToolbarHeight("));
            assertFalse(file, source.contains("androidx.dynamicanimation"));
            assertFalse(file, source.contains("new Choreographer"));
            assertFalse(file, source.contains("EditText"));
        }
    }

    @Test public void horizontalClassesContainNoTerminalOrAccessorySizingCalls() throws Exception {
        String[] files = {
            "AppDrawerHorizontalPagerView.java", "AppDrawerHorizontalPageAdapter.java",
            "AppDrawerPageIndicatorView.java", "AppDrawerHorizontalGridMetrics.java"
        };
        for (String file : files) {
            java.nio.file.Path root = Paths.get(System.getProperty("user.dir"));
            java.nio.file.Path sourcePath = root.resolve(
                "src/main/java/com/termux/app/launcher/drawer/" + file);
            if (!Files.exists(sourcePath)) sourcePath = root.resolve(
                "app/src/main/java/com/termux/app/launcher/drawer/" + file);
            String source = new String(Files.readAllBytes(sourcePath),
                StandardCharsets.UTF_8);
            assertFalse(file, source.contains("updateSize("));
            assertFalse(file, source.contains("computeCombinedHeight("));
            assertFalse(file, source.contains("setTerminalToolbarHeight("));
        }
    }

    private static boolean isDescendant(View possibleChild, View possibleParent) {
        android.view.ViewParent parent = possibleChild.getParent();
        while (parent instanceof View) {
            if (parent == possibleParent) return true;
            parent = parent.getParent();
        }
        return false;
    }
}
