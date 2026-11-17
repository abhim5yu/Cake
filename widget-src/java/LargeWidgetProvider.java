package com.abhim5yu.cake.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import com.abhim5yu.cake.R;

import org.json.JSONObject;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 4x4 home-screen widget: "UPCOMING" header with the next four birthdays.
 * Tapping anywhere opens the app. The list is fixed at four rows so the
 * widget needs no RemoteViewsService.
 */
public class LargeWidgetProvider extends AppWidgetProvider {

    private static final int ROWS = 4;

    private static final int[] ROW_IDS = {R.id.row1, R.id.row2, R.id.row3, R.id.row4};
    private static final int[] NAME_IDS = {R.id.row1_name, R.id.row2_name, R.id.row3_name, R.id.row4_name};
    private static final int[] DAYS_IDS = {R.id.row1_days, R.id.row2_days, R.id.row3_days, R.id.row4_days};

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(ctx, mgr, id);
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        for (int id : ids) WidgetPrefs.clearWidget(ctx, id);
    }

    static void update(Context ctx, AppWidgetManager mgr, int appWidgetId) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_large);

        int color = WidgetPrefs.getWidgetTextColor(ctx, appWidgetId);
        int transparencyPct = WidgetPrefs.getWidgetAlpha(ctx, appWidgetId);

        // Transparency only affects the background; text stays fully visible.
        float opacity = 1f - transparencyPct / 100f;
        rv.setInt(R.id.w_root, "setBackgroundColor", withAlpha(0xFF16181C, opacity));
        rv.setInt(R.id.w_header, "setTextColor", color);

        List<JSONObject> all = WidgetPrefs.loadBirthdays(ctx);
        Collections.sort(all, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return Integer.compare(WidgetPrefs.daysUntil(a), WidgetPrefs.daysUntil(b));
            }
        });

        if (all.isEmpty()) {
            rv.setTextViewText(R.id.w_header, ctx.getString(R.string.widget_empty));
        } else {
            rv.setTextViewText(R.id.w_header, "UPCOMING");
        }

        for (int i = 0; i < ROWS; i++) {
            if (i < all.size()) {
                JSONObject b = all.get(i);
                int days = WidgetPrefs.daysUntil(b);
                rv.setViewVisibility(ROW_IDS[i], View.VISIBLE);
                rv.setTextViewText(NAME_IDS[i], b.optString("name", ""));
                rv.setInt(NAME_IDS[i], "setTextColor", color);
                rv.setTextViewText(DAYS_IDS[i], days == 0 ? "Today" : days == 1 ? "Tomorrow" : "in " + days + " days");
                rv.setInt(DAYS_IDS[i], "setTextColor", color);
            } else {
                rv.setViewVisibility(ROW_IDS[i], View.GONE);
            }
        }

        rv.setOnClickPendingIntent(R.id.w_root, openAppIntent(ctx, appWidgetId));
        mgr.updateAppWidget(appWidgetId, rv);
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static PendingIntent openAppIntent(Context ctx, int appWidgetId) {
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (launch == null) launch = new Intent();
        launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                ctx,
                appWidgetId * 2 + 2,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
