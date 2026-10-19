package com.termux.app.launcher.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

/**
 * The default page's line in Edit pages is read whole at a 1.3 font scale: it wraps beside the
 * four buttons instead of ending in an ellipsis after "Home opens h".
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HomePagesEditorRowTest {

    /** The editor's list width on the emulator that clipped it: a 342dp dialog body. */
    private static final int LIST_WIDTH_DP = 342;

    @Test public void theDefaultPagesLineWrapsAtALargeFontScale() {
        Context base = ApplicationProvider.getApplicationContext();
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.fontScale = 1.3f;
        Context context = new ContextThemeWrapper(base.createConfigurationContext(configuration),
            R.style.Theme_TermuxApp_DayNight_NoActionBar);

        HomePagesEditor editor = ReflectionHelpers.callConstructor(HomePagesEditor.class,
            ReflectionHelpers.ClassParameter.from(Context.class, context),
            ReflectionHelpers.ClassParameter.from(HomePagesEditor.Host.class, new OnePageHost()));
        editor.refresh();
        LinearLayout list = ReflectionHelpers.getField(editor, "list");
        float density = context.getResources().getDisplayMetrics().density;
        list.measure(View.MeasureSpec.makeMeasureSpec(Math.round(LIST_WIDTH_DP * density),
            View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        list.layout(0, 0, list.getMeasuredWidth(), list.getMeasuredHeight());

        LinearLayout row = (LinearLayout) list.getChildAt(0);
        TextView detail = (TextView) ((LinearLayout) row.getChildAt(0)).getChildAt(1);
        Layout layout = detail.getLayout();
        assertNotNull(layout);
        assertEquals(context.getString(R.string.home_pages_editor_default_detail,
            context.getString(R.string.home_pages_editor_empty)), detail.getText().toString());
        assertEquals(0, layout.getEllipsisCount(layout.getLineCount() - 1));
    }

    private static final class OnePageHost implements HomePagesEditor.Host {
        @Override public int pageCount() { return 1; }
        @Override public int itemsOn(int page) { return 0; }
        @Override public int floatsOn(int page) { return 0; }
        @Override public int defaultPage() { return 0; }
        @Override public void movePage(int from, int to) { }
        @Override public void removePage(int page) { }
        @Override public void addPage() { }
        @Override public void setDefaultPage(int page) { }
    }
}
