package com.abhim5yu.cake;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
        if (intent != null && intent.hasExtra("wish_id")) {
            pendingWishId = intent.getStringExtra("wish_id");
            intent.removeExtra("wish_id");
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
