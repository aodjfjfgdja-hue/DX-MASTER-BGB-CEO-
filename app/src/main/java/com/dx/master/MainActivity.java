package com.dx.master;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private WebView web;
    private boolean askedOverlay = false;

    private final BroadcastReceiver toggleRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            web.post(new Runnable() {
                @Override
                public void run() {
                    web.evaluateJavascript("window.toggleMic && window.toggleMic()", null);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest r) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        r.grant(r.getResources());
                    }
                });
            }
        });

        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");

        IntentFilter f = new IntentFilter("DX_TOGGLE");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(toggleRx, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(toggleRx, f);
        }

        ArrayList<String> need = new ArrayList<String>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            need.add("android.permission.POST_NOTIFICATIONS");
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        tryStartBubble();
    }

    private void tryStartBubble() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            if (!askedOverlay) {
                askedOverlay = true;
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
            return;
        }
        startForegroundService(new Intent(this, FloatingService.class));
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(toggleRx);
        } catch (Exception e) {
        }
        web.destroy();
        super.onDestroy();
    }

    public class Bridge {
        @JavascriptInterface
        public void micState(boolean on) {
            Intent i = new Intent("DX_STATE");
            i.setPackage(getPackageName());
            i.putExtra("on", on);
            sendBroadcast(i);
        }

        @JavascriptInterface
        public void callMode(boolean on) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.setMode(on ? AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_NORMAL);
            am.setSpeakerphoneOn(on);
        }

        @JavascriptInterface
        public void openTelegram() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent i = getPackageManager().getLaunchIntentForPackage("org.telegram.messenger");
                    if (i == null) {
                        i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me"));
                    }
                    startActivity(i);
                }
            });
        }
    }
}
