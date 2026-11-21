package com.termux.app.notice;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.widget.LinearLayout;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import static org.junit.Assert.assertTrue;

/** The mark view has to actually paint its icon, not only take up its slot. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.LEGACY)
public class AppNoticeMarkPaintTest {

    @Test
    public void theBellMarkPaintsPixelsInsideItsBounds() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppNoticeHostView host = AppNotice.hostFor(activity);
        AppNotice.shell(activity, "[1] wants attention", null, R.drawable.ic_rl_bell, true, () -> {});
        host.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        host.layout(0, 0, host.getMeasuredWidth(), host.getMeasuredHeight());
        View mark = ((LinearLayout) host).getChildAt(0);
        Bitmap bitmap = Bitmap.createBitmap(host.getMeasuredWidth(), host.getMeasuredHeight(),
            Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.TRANSPARENT);
        host.setBackground(null);
        host.draw(new Canvas(bitmap));
        int painted = 0;
        for (int y = mark.getTop(); y < mark.getBottom(); y++)
            for (int x = mark.getLeft(); x < mark.getRight(); x++)
                if (Color.alpha(bitmap.getPixel(x, y)) > 0) painted++;
        assertTrue("mark " + mark.getWidth() + "x" + mark.getHeight() + " title h " + ((LinearLayout) host).getChildAt(1).getHeight() + " host " + host.getMeasuredWidth() + "x" + host.getMeasuredHeight() + " painted " + painted,
            painted > 10);
    }
}
