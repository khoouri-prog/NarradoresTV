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
        tvDelayNum.setTextSize(64);
        tvDelayNum.setGravity(Gravity.CENTER);
        panelDelay.addView(tvDelayNum);

        tvInfo = new TextView(this);
        tvInfo.setTextColor(Color.parseColor("#CCCCCC"));
        tvInfo.setTextSize(17);
        tvInfo.setGravity(Gravity.CENTER);
        tvInfo.setPadding(0, 40, 0, 0);
        tvInfo.setLineSpacing(8, 1.2f);
        panelDelay.addView(tvInfo);

        root.addView(panelDelay);
        setContentView(root);
    }

    private void configurarWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                if (!streamDetectado && esStream(url)) {
                    streamDetectado = true;
                    streamUrl = url;
                    handler.post(() -> {
                        Toast.makeText(MainActivity.this,
                            "Stream detectado. Presiona el boton central para el delay.",
                            Toast.LENGTH_LONG).show();
                        prepararStream(url);
                    });
                }
            }
        });
    }

    private boolean esStream(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return u.contains(".m3u8") || u.contains("chunklist") ||
               u.contains("playlist.m3u") || u.contains("/hls/");
    }

    private void inicializarPlayer() {
        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                actualizarInfo();
            }
        });
    }

    private void prepararStream(String url) {
        try {
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 10)")
                .setAllowCrossProtocolRedirects(true);

            DefaultDataSource.Factory factory =
                new DefaultDataSource.Factory(this, http);

            MediaItem item = MediaItem.fromUri(url);

            if (url.toLowerCase().contains("m3u8") || url.toLowerCase().contains("hls")) {
                HlsMediaSource src = new HlsMediaSource.Factory(factory)
                    .createMediaSource(item);
                player.setMediaSource(src);
            } else {
                player.setMediaItem(item);
            }

            player.prepare();
            player.setPlayWhenReady(true);
            aplicarDelay();
        } catch (Exception e) {
            Toast.makeText(this, "Error al cargar stream: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_MENU) {

            if (modo == MODO_WEB && (streamDetectado || keyCode == KeyEvent.KEYCODE_MENU)) {
                cambiarModo();
                return true;
            } else if (modo == MODO_DELAY) {
                cambiarModo();
                return true;
            }
        }

        if (modo == MODO_DELAY) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    ajustarDelay(1000); return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    ajustarDelay(-1000); return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    ajustarDelay(5000); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    ajustarDelay(-5000); return true;
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    if (player.isPlaying()) player.pause();
                    else player.play();
                    actualizarInfo();
                    return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private void cambiarModo() {
        if (modo == MODO_WEB) {
            modo = MODO_DELAY;
            webView.setVisibility(View.GONE);
            panelDelay.setVisibility(View.VISIBLE);
            actualizarInfo();
        } else {
            modo = MODO_WEB;
            panelDelay.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
        }
    }

    private void ajustarDelay(long delta) {
        delayMs = Math.max(DELAY_MIN, Math.min(DELAY_MAX, delayMs + delta));
        aplicarDelay();
        actualizarInfo();
    }

    private void aplicarDelay() {
        if (player == null) return;
        try {
            long dur = player.getDuration();
            if (dur > 0) {
                long pos = Math.max(0, Math.min(dur, dur + delayMs));
                player.seekTo(pos);
            }
        } catch (Exception ignored) {}
    }

    private void actualizarInfo() {
        if (tvDelayNum == null) return;

        long abs = Math.abs(delayMs);
        String signo = delayMs >= 0 ? "+" : "-";
        tvDelayNum.setText(String.format("%s%d.%d seg", signo, abs / 1000, (abs % 1000) / 100));

        boolean playing = player != null && player.isPlaying();
        tvDelayNum.setTextColor(playing ? Color.parseColor("#00E676") : Color.parseColor("#FF5252"));

        String estado = playing ? "Reproduciendo" : "Pausado / sin stream";
        tvInfo.setText(
            estado + "\n\n" +
            "Flechas IZQ/DER : ajustar 1 segundo\n" +
            "Flechas ARR/ABA : ajustar 5 segundos\n" +
            "Boton central : volver al navegador\n\n" +
            "Sube el volumen de la TV.\n" +
            "Baja el volumen de YouTube a cero."
        );
    }

    private void mostrarError(Exception e) {
        TextView tv = new TextView(this);
        tv.setText("Error al iniciar:\n\n" + e.toString());
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(Color.BLACK);
        tv.setTextSize(14);
        tv.setPadding(40, 40, 40, 40);
        setContentView(tv);
    }

    @Override
    public void onBackPressed() {
        if (modo == MODO_WEB && webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
