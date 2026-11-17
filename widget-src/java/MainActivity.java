package com.abhim5yu.cake;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.abhim5yu.cake.widgets.CakeWidgetPlugin;
import com.abhim5yu.cake.widgets.MediumWidgetProvider;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private String pendingWishId = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(CakeWidgetPlugin.class);
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        // Key must match MediumWidgetProvider.EXTRA_WISH_ID.
        if (intent != null && intent.hasExtra(MediumWidgetProvider.EXTRA_WISH_ID)) {
            pendingWishId = intent.getStringExtra(MediumWidgetProvider.EXTRA_WISH_ID);
            intent.removeExtra(MediumWidgetProvider.EXTRA_WISH_ID);
            tryOpenWish(0);
        }
    }

    private void tryOpenWish(final int attempt) {
        if (pendingWishId == null || attempt > 20) return;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (getBridge() == null || getBridge().getWebView() == null) {
                tryOpenWish(attempt + 1);
                return;
            }
            getBridge().getWebView().evaluateJavascript(
                "typeof window.cakeOpenWish === 'function'",
                value -> {
                    if ("true".equals(value)) {
                        String id = pendingWishId;
                        pendingWishId = null;
                        getBridge().getWebView().evaluateJavascript(
                            "window.cakeOpenWish('" + id.replace("'", "") + "')", null);
                    } else {
                        tryOpenWish(attempt + 1);
                    }
                });
        }, 250);
    }
}
