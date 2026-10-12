package com.renewisse.radarencasa;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Widget: lugar, temperatura actual y viento, con datos de Open-Meteo. */
public class WeatherWidget extends AppWidgetProvider {
    static final String PREFS = "radar";
    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        final PendingResult pr = goAsync();
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try { update(app, mgr, ids); } finally { pr.finish(); }
        }).start();
    }

    /** Pide a todos los widgets que se actualicen (al salir de la app). */
    static void refreshAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, WeatherWidget.class));
        if (ids.length == 0) return;
        Intent i = new Intent(ctx, WeatherWidget.class);
        i.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        ctx.sendBroadcast(i);
    }

    private static void update(Context ctx, AppWidgetManager mgr, int[] ids) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        Intent open = new Intent(ctx, MainActivity.class);
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String name = p.getString("name", null);
        double lat, lon;
        if (name != null) {
            lat = Double.parseDouble(p.getString("lat", "40.4168"));
            lon = Double.parseDouble(p.getString("lon", "-3.7038"));
        } else {
            Location loc = lastLocation(ctx);
            if (loc != null) { name = "Mi ubicación"; lat = loc.getLatitude(); lon = loc.getLongitude(); }
            else { name = "Madrid"; lat = 40.4168; lon = -3.7038; }
        }
        v.setTextViewText(R.id.place, name);

        try {
            String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&timezone=auto"
                + "&current=temperature_2m,weather_code,is_day,wind_speed_10m,wind_direction_10m,wind_gusts_10m", lat, lon);
            JSONObject c = new JSONObject(get(url)).getJSONObject("current");
            int code = c.getInt("weather_code");
            boolean day = c.optInt("is_day", 1) == 1;
            v.setTextViewText(R.id.temp, Math.round(c.getDouble("temperature_2m")) + "°");
            v.setTextViewText(R.id.icon, icon(code, day));
            v.setTextViewText(R.id.wind, "Viento " + Math.round(c.getDouble("wind_speed_10m")) + " km/h "
                + DIRS[(int) Math.round(c.getDouble("wind_direction_10m") / 45.0) % 8]);
            v.setTextViewText(R.id.gusts, "Rachas " + Math.round(c.getDouble("wind_gusts_10m")) + " km/h · "
                + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        } catch (Exception e) {
            v.setTextViewText(R.id.wind, "Sin conexión");
            v.setTextViewText(R.id.gusts, "Toca para abrir la app");
        }
        mgr.updateAppWidget(ids, v);
    }

    private static Location lastLocation(Context ctx) {
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String prov : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(prov);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            return best;
        } catch (SecurityException e) {
            return null;
        }
    }

    private static String get(String u) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(10000);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            con.disconnect();
        }
    }

    // Los mismos símbolos que usa la web (códigos WMO).
    private static String icon(int c, boolean day) {
        if (c == 0) return day ? "☀️" : "🌙";
        if (c == 1) return day ? "🌤️" : "🌙";
        if (c == 2) return day ? "⛅" : "☁️";
        if (c == 3) return "☁️";
        if (c == 45 || c == 48) return "🌫️";
        if (c >= 51 && c <= 53) return "🌦️";
        if (c >= 55 && c <= 57) return "🌧️";
        if (c == 61 || c == 80) return "🌦️";
        if (c >= 63 && c <= 67) return "🌧️";
        if (c == 81) return "🌧️";
        if (c == 82 || c >= 95) return "⛈️";
        if (c == 75 || c == 86) return "❄️";
        if (c >= 71 && c <= 86) return "🌨️";
        return "🌡️";
    }
}
