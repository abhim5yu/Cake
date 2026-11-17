package com.abhim5yu.cake.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.widget.RemoteViews;

import com.abhim5yu.cake.R;

import org.json.JSONObject;

import java.io.File;
import java.util.List;

/**
 * 4x1 home-screen widget: round avatar, name, "in N days", and a Wish button.
 * Tapping Wish opens the app on that person's wish screen (via EXTRA_WISH_ID).
 * Tapping anywhere else opens the app.
 */
public class MediumWidgetProvider extends AppWidgetProvider {

    /** Intent extra MainActivity reads to open the wish sheet for one person. */
    public static final String EXTRA_WISH_ID = "cake_wish_id";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(ctx, mgr, id);
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        for (int id : ids) WidgetPrefs.clearWidget(ctx, id);
    }

    static void update(Context ctx, AppWidgetManager mgr, int appWidgetId) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_medium);

        JSONObject b = pickBirthday(ctx, appWidgetId);
        int color = WidgetPrefs.getWidgetTextColor(ctx, appWidgetId);
        int transparencyPct = WidgetPrefs.getWidgetAlpha(ctx, appWidgetId);

        // Transparency only affects the background; text stays fully visible.
        float opacity = 1f - transparencyPct / 100f;
        rv.setInt(R.id.w_root, "setBackgroundColor", withAlpha(0xFF16181C, opacity));

        rv.setInt(R.id.w_name, "setTextColor", color);
        rv.setInt(R.id.w_days, "setTextColor", color);

        if (b == null) {
            rv.setImageViewBitmap(R.id.w_avatar, avatar(null, "?", color));
            rv.setTextViewText(R.id.w_name, ctx.getString(R.string.widget_empty));
            rv.setTextViewText(R.id.w_days, "");
            rv.setViewVisibility(R.id.w_wish, View.GONE);
            rv.setOnClickPendingIntent(R.id.w_root, openAppIntent(ctx, appWidgetId, null, 0));
        } else {
            String name = b.optString("name", "");
            int days = WidgetPrefs.daysUntil(b);
            String id = b.optString("id", "");

            rv.setImageViewBitmap(R.id.w_avatar, avatar(loadPhoto(b.optString("photoPath", "")), name, color));
            rv.setTextViewText(R.id.w_name, name);
            rv.setTextViewText(R.id.w_days, days == 0 ? "Today" : days == 1 ? "Tomorrow" : "in " + days + " days");
            rv.setViewVisibility(R.id.w_wish, View.VISIBLE);

            rv.setOnClickPendingIntent(R.id.w_wish, openAppIntent(ctx, appWidgetId, id, 1));
            rv.setOnClickPendingIntent(R.id.w_root, openAppIntent(ctx, appWidgetId, null, 0));
        }

        mgr.updateAppWidget(appWidgetId, rv);
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

    /**
     * Round avatar: the photo center-cropped to a circle, or initials on a dark
     * circle when there is no photo. Built as a bitmap because RemoteViews
     * cannot clip an ImageView to a circle.
     */
    private static Bitmap avatar(Bitmap photo, String name, int textColor) {
        final int px = 192;
        Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = px / 2f;

        if (photo != null) {
            Path clip = new Path();
            clip.addCircle(r, r, r, Path.Direction.CW);
            c.clipPath(clip);
            int side = Math.min(photo.getWidth(), photo.getHeight());
            Rect src = new Rect(
                    (photo.getWidth() - side) / 2,
                    (photo.getHeight() - side) / 2,
                    (photo.getWidth() + side) / 2,
                    (photo.getHeight() + side) / 2);
            c.drawBitmap(photo, src, new Rect(0, 0, px, px), p);
        } else {
            p.setColor(0xFF222427);
            c.drawCircle(r, r, r, p);
            String initials = initials(name);
            p.setColor(textColor);
            p.setTextSize(px * 0.38f);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = p.getFontMetrics();
            float y = r - (fm.ascent + fm.descent) / 2f;
            c.drawText(initials, r, y, p);
        }
        return out;
    }

    private static String initials(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return "?";
        String[] w = n.split("\\s+");
        String first = w[0].substring(0, 1);
        String last = w.length > 1 ? w[w.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * Opens the app. When wishId is set, MainActivity opens that person's wish
     * sheet. requestCode keeps the Wish tap and the background tap separate.
     */
    private static PendingIntent openAppIntent(Context ctx, int appWidgetId, String wishId, int slot) {
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (launch == null) launch = new Intent();
        launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (wishId != null) launch.putExtra(EXTRA_WISH_ID, wishId);
        return PendingIntent.getActivity(
                ctx,
                appWidgetId * 2 + slot,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
