package com.renewisse.radarencasa;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Abre la web de Radar en Casa a pantalla completa y le pasa al widget el lugar elegido. */
public class MainActivity extends Activity {
    static final String URL = "https://rene-maker.github.io/radar-en-casa/";
    static final String HOST = "rene-maker.github.io";
    private static final int REQ_LOC = 1;

    private WebView web;
    private GeolocationPermissions.Callback pendingGeo;
    private String pendingOrigin;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.parseColor("#0F6B8A"));
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.addJavascriptInterface(new Bridge(), "RadarApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                // Enlaces a otras webs (Open-Meteo, RainViewer…) se abren en el navegador.
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (hasLocation()) { cb.invoke(origin, true, false); return; }
                pendingGeo = cb; pendingOrigin = origin;
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOC);
            }
        });

        if (state != null) web.restoreState(state); else web.loadUrl(URL);
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == REQ_LOC && pendingGeo != null) {
            pendingGeo.invoke(pendingOrigin, hasLocation(), false);
            pendingGeo = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onStop() {
        super.onStop();
        WeatherWidget.refreshAll(this);
    }

    /** La web llama a RadarApp.setPlace(...) cada vez que muestra la previsión de un lugar. */
    class Bridge {
        @JavascriptInterface
        public void setPlace(String name, double lat, double lon) {
            getSharedPreferences(WeatherWidget.PREFS, MODE_PRIVATE).edit()
                .putString("name", name)
                .putString("lat", Double.toString(lat))
                .putString("lon", Double.toString(lon))
                .apply();
        }
    }
}
