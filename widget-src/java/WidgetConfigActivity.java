package com.abhim5yu.cake.widgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.abhim5yu.cake.R;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Shown when a widget is first added (and from the widget's settings).
 * Lets the user pick the person, transparency, and text color, then saves
 * them under the same keys WidgetPrefs reads.
 */
public class WidgetConfigActivity extends Activity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private List<JSONObject> people = new ArrayList<>();
    private String selectedId;

    private ListView list;
    private SeekBar alpha;
    private TextView alphaLabel;
    private EditText colorHex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_config);

        // If the user backs out, the widget is not added.
        setResult(RESULT_CANCELED);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        list = findViewById(R.id.w_list);
        alpha = findViewById(R.id.w_alpha);
        alphaLabel = findViewById(R.id.w_alpha_lbl);
        colorHex = findViewById(R.id.w_color);
        Button save = findViewById(R.id.w_save);

        people = WidgetPrefs.loadBirthdays(this);
        List<String> names = new ArrayList<>();
        for (JSONObject p : people) names.add(p.optString("name", "?"));

        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, names));

        // Preselect the saved person, if any.
        selectedId = WidgetPrefs.getWidgetPerson(this, appWidgetId);
        for (int i = 0; i < people.size(); i++) {
            if (people.get(i).optString("id").equals(selectedId)) {
                list.setItemChecked(i, true);
                break;
            }
        }
        list.setOnItemClickListener((parent, view, position, id) ->
                selectedId = people.get(position).optString("id"));

        // Transparency: 0 = opaque, 100 = fully transparent.
        int savedAlpha = WidgetPrefs.getWidgetAlpha(this, appWidgetId);
        alpha.setMax(100);
        alpha.setProgress(savedAlpha);
        updateAlphaLabel(savedAlpha);
        alpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                updateAlphaLabel(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        int savedColor = WidgetPrefs.getWidgetTextColor(this, appWidgetId);
        colorHex.setText(String.format("#%06X", 0xFFFFFF & savedColor));

        save.setOnClickListener(v -> save());
    }

    private void updateAlphaLabel(int pct) {
        alphaLabel.setText("Transparency " + pct + "%");
    }

    private void save() {
        int color;
        try {
            color = Color.parseColor(colorHex.getText().toString().trim());
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Enter a color like #F2F0EC", Toast.LENGTH_SHORT).show();
            return;
        }

        // Same keys WidgetPrefs reads. Keep these in sync with WidgetPrefs.
        SharedPreferences.Editor ed = getSharedPreferences(CakeWidgetPlugin.PREFS, MODE_PRIVATE).edit();
        String prefix = "widget_" + appWidgetId + "_";
        if (selectedId != null) ed.putString(prefix + "person", selectedId);
        else ed.remove(prefix + "person");
        ed.putInt(prefix + "alpha", alpha.getProgress());
        ed.putInt(prefix + "color", color);
        if (!ed.commit()) {
            Toast.makeText(this, "Could not save. Try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        refreshWidget();

        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    /** Asks the widget's own provider to redraw with the new settings. */
    private void refreshWidget() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        AppWidgetProviderInfo info = mgr.getAppWidgetInfo(appWidgetId);
        if (info == null) return;
        ComponentName provider = info.provider;
        Intent update = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        update.setComponent(provider);
        update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
        sendBroadcast(update);
    }
}
