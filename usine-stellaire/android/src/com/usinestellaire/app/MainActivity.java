package com.usinestellaire.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView web;

    /** Pont JS → natif : le jeu appelle UsineBridge.vibrate(ms) pour les retours haptiques. */
    public static class Bridge {
        private final Vibrator vib;
        Bridge(Context c) {
            vib = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        }
        @JavascriptInterface
        public void vibrate(long ms) {
            try { if (vib != null) vib.vibrate(ms); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0e1116"));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);       // localStorage = sauvegarde du jeu
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(this), "UsineBridge");
        web.loadUrl("file:///android_asset/index.html");

        setContentView(web);

        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#0e1116"));
        w.setNavigationBarColor(Color.parseColor("#12171f"));
    }

    /** Bouton retour : mettre en arrière-plan sans tuer l'appli (la partie reste chargée). */
    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    /** Sauvegarde forcée quand l'appli passe en arrière-plan. */
    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.evaluateJavascript("try{G.save()}catch(e){}", null);
    }
}
