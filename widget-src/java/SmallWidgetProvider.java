package com.abhim5yu.cake.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.RemoteViews;

import com.abhim5yu.cake.R;

import org.json.JSONObject;

import java.io.File;
import java.util.List;

/**
 * 2x2 home-screen widget: person's photo as background, big countdown,
 * name underneath. Tapping anywhere opens the app.
 */
public class SmallWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(ctx, mgr, id);
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        for (int id : ids) WidgetPrefs.clearWidget(ctx, id);
    }

    static void update(Context ctx, AppWidgetManager mgr, int appWidgetId) {
        try {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_small);
            JSONObject b = pickBirthday(ctx, appWidgetId);
            int color = WidgetPrefs.getWidgetTextColor(ctx, appWidgetId);
            int transparencyPct = WidgetPrefs.getWidgetAlpha(ctx, appWidgetId);

            rv.setInt(R.id.w_days, "setTextColor", color);
            rv.setInt(R.id.w_daylbl, "setTextColor", color);
            rv.setInt(R.id.w_name, "setTextColor", color);

            float opacity = 1f - transparencyPct / 100f;
            rv.setInt(R.id.w_root, "setBackgroundColor", withAlpha(0xFF16181C, opacity));
            rv.setFloat(R.id.w_photo, "setAlpha", opacity);
            rv.setInt(R.id.w_scrim, "setBackgroundColor", withAlpha(0xFF000000, 0.55f * opacity));

            if (b == null) {
                rv.setTextViewText(R.id.w_days, "");
                rv.setTextViewText(R.id.w_daylbl, "");
                rv.setTextViewText(R.id.w_name, ctx.getString(R.string.widget_empty));
                rv.setViewVisibility(R.id.w_photo, View.GONE);
                rv.setViewVisibility(R.id.w_scrim, View.GONE);
            } else {
                int days = WidgetPrefs.daysUntil(b);
                rv.setTextViewText(R.id.w_days, String.valueOf(days));
                rv.setTextViewText(R.id.w_daylbl, days == 0 ? "today" : "days to go");
                rv.setTextViewText(R.id.w_name, b.optString("name", ""));

                Bitmap photo = loadPhoto(b.optString("photoPath", ""));
                if (photo != null) {
                    rv.setImageViewBitmap(R.id.w_photo, photo);
                    rv.setViewVisibility(R.id.w_photo, View.VISIBLE);
                    rv.setViewVisibility(R.id.w_scrim, View.VISIBLE);
                } else {
                    rv.setViewVisibility(R.id.w_photo, View.GONE);
                    rv.setViewVisibility(R.id.w_scrim, View.GONE);
                }
            }

            rv.setOnClickPendingIntent(R.id.w_root, openAppIntent(ctx, appWidgetId));
            mgr.updateAppWidget(appWidgetId, rv);
        } catch (Throwable t) {
            // Show the real error on the widget so it can be read and fixed,
            // instead of Android's generic "Can't load widget".
            RemoteViews err = new RemoteViews(ctx.getPackageName(), R.layout.widget_small);
            err.setTextViewText(R.id.w_days, "!");
            err.setTextViewText(R.id.w_daylbl, "error");
            String msg = t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
            err.setTextViewText(R.id.w_name, msg.length() > 120 ? msg.substring(0, 120) : msg);
            err.setViewVisibility(R.id.w_photo, View.GONE);
            err.setViewVisibility(R.id.w_scrim, View.GONE);
            mgr.updateAppWidget(appWidgetId, err);
        }
    }

    /** The widget's chosen person, or the next upcoming birthday if none is chosen. */
    private static JSONObject pickBirthday(Context ctx, int appWidgetId) {
        String chosen = WidgetPrefs.getWidgetPerson(ctx, appWidgetId);
        if (chosen != null) {
            JSONObject b = WidgetPrefs.findBirthday(ctx, chosen);
            if (b != null) return b;
        }
        List<JSONObject> all = WidgetPrefs.loadBirthdays(ctx);
        JSONObject best = null;
        int bestDays = Integer.MAX_VALUE;
        for (JSONObject b : all) {
            int d = WidgetPrefs.daysUntil(b);
            if (d < bestDays) {
                bestDays = d;
                best = b;
            }
        }
        return best;
    }

    /** Photos are already downscaled to 320px by CakeWidgetPlugin, so decode directly. */
    private static Bitmap loadPhoto(String path) {
        if (path == null || path.isEmpty()) return null;
        File f = new File(path);
        if (!f.exists()) return null;
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Exception e) {
            return null;
        }
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
                appWidgetId,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
