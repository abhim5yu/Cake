package com.abhim5yu.cake.widgets;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Bridges the app's birthday list (stored in localStorage on the JS side)
 * into native SharedPreferences so the home-screen widgets can read it.
 * Called from www/index.html after every save().
 */
@CapacitorPlugin(name = "CakeWidget")
public class CakeWidgetPlugin extends Plugin {

    public static final String PREFS = "cake_widget_prefs";
    public static final String KEY_DATA = "birthdays_json";

    @PluginMethod
    public void syncBirthdays(PluginCall call) {
        JSArray input = call.getArray("birthdays");
        if (input == null) {
            call.reject("birthdays array required");
            return;
        }

        Context ctx = getContext();
        File dir = new File(ctx.getFilesDir(), "widget_photos");
        if (!dir.exists()) dir.mkdirs();

        JSONArray out = new JSONArray();
        try {
            // JSArray extends org.json.JSONArray
            JSONArray arr = input;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject src = arr.getJSONObject(i);
                JSONObject rec = new JSONObject();
                String id = src.optString("id", "");
                rec.put("id", id);
                rec.put("name", src.optString("name", ""));
                rec.put("month", src.optInt("month", 1));
                rec.put("day", src.optInt("day", 1));
                if (src.has("year") && !src.isNull("year")) {
                    rec.put("year", src.optInt("year"));
                }
                rec.put("pinned", src.optBoolean("pinned", false));

                String photo = src.optString("photo", "");
                String photoPath = savePhoto(dir, id, photo);
                if (photoPath != null) rec.put("photoPath", photoPath);

                out.put(rec);
            }
        } catch (Exception e) {
            call.reject("Failed to process birthdays", e);
            return;
        }

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_DATA, out.toString()).apply();

        requestUpdate(ctx, "com.abhim5yu.cake.widgets.SmallWidgetProvider");
        requestUpdate(ctx, "com.abhim5yu.cake.widgets.MediumWidgetProvider");
        requestUpdate(ctx, "com.abhim5yu.cake.widgets.LargeWidgetProvider");

        JSObject ret = new JSObject();
        ret.put("count", out.length());
        call.resolve(ret);
    }

    private String savePhoto(File dir, String id, String dataUri) {
        if (id == null || id.isEmpty() || dataUri == null || dataUri.isEmpty()) return null;
        int comma = dataUri.indexOf(',');
        if (comma < 0) return null;
        try {
            byte[] bytes = Base64.decode(dataUri.substring(comma + 1), Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp == null) return null;
            int max = 320;
            if (bmp.getWidth() > max || bmp.getHeight() > max) {
                float scale = Math.min((float) max / bmp.getWidth(), (float) max / bmp.getHeight());
                bmp = Bitmap.createScaledBitmap(
                        bmp,
                        Math.round(bmp.getWidth() * scale),
                        Math.round(bmp.getHeight() * scale),
                        true);
            }
            File f = new File(dir, id + ".jpg");
            FileOutputStream fos = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.close();
            return f.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    /** Resolves the provider class by name so this plugin doesn't need to depend on
     *  the provider classes directly (keeps step-by-step additions decoupled). */
    private void requestUpdate(Context ctx, String providerClassName) {
        try {
            Class<?> providerClass = Class.forName(providerClassName);
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            ComponentName cn = new ComponentName(ctx, providerClass);
            int[] ids = mgr.getAppWidgetIds(cn);
            if (ids.length == 0) return;
            Intent intent = new Intent(ctx, providerClass);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            ctx.sendBroadcast(intent);
        } catch (ClassNotFoundException e) {
            // Provider not added yet (we're building this feature step by step) — skip.
        }
    }
}
