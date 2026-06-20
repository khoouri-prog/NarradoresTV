package com.akhimport.narradorestv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

public class MainActivity extends Activity {

    private static final int MODO_WEB   = 0;
    private static final int MODO_DELAY = 1;
    private int modo = MODO_WEB;

    private FrameLayout root;
    private WebView webView;
    private LinearLayout panelDelay;
    private TextView tvDelayNum;
    private TextView tvInfo;

    private ExoPlayer player;
    private String streamUrl = "";
    private boolean streamDetectado = false;

    private long delayMs = 0;
    private static final long DELAY_MIN = -120_000;
    private static final long DELAY_MAX =  120_000;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final String URL_INICIO = "https://futbollibre.net";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            construirUI();
            inicializarPlayer();
            configurarWebView();
            webView.loadUrl(URL_INICIO);
        } catch (Exception e) {
            mostrarError(e);
        }
    }

    private void construirUI() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(webView);

        panelDelay = new LinearLayout(this);
        panelDelay.setOrientation(LinearLayout.VERTICAL);
        panelDelay.setGravity(Gravity.CENTER);
        panelDelay.setBackgroundColor(Color.parseColor("#E6000000"));
        panelDelay.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        panelDelay.setPadding(60, 60, 60, 60);
        panelDelay.setVisibility(View.GONE);

        TextView titulo = new TextView(this);
        titulo.setText("NarradoresTV");
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(30);
        titulo.setGravity(Gravity.CENTER);
        panelDelay.addView(titulo);

        TextView subtitulo = new TextView(this);
        subtitulo.setText("Control de sincronizacion");
        subtitulo.setTextColor(Color.parseColor("#AAAAAA"));
        subtitulo.setTextSize(16);
        subtitulo.setGravity(Gravity.CENTER);
        subtitulo.setPadding(0, 0, 0, 40);
        panelDelay.addView(subtitulo);

        tvDelayNum = new TextView(this);
        tvDelayNum.setText("+0.0 seg");
        tvDelayNum.setTextColor(Color.parseColor("#00E676"));
