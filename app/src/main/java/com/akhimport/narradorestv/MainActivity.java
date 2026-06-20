package com.akhimport.narradorestv;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // Modos de la app
    private static final int MODO_WEBVIEW  = 0;
    private static final int MODO_DELAY    = 1;

    private int modoActual = MODO_WEBVIEW;

    // Vistas
    private WebView webView;
    private LinearLayout panelDelay;
    private TextView tvDelay;
    private TextView tvInstrucciones;

    // Servicio de audio con delay
    private AudioService audioService;
    private boolean servicioConectado = false;

    // URL base de FutbolLibre
    private static final String URL_INICIO = "https://futbollibre.net";

    // ─── Conexión con el servicio ────────────────────────────────────────────
    private final ServiceConnection conexionServicio = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            AudioService.LocalBinder lb = (AudioService.LocalBinder) binder;
            audioService = lb.getService();
            servicioConectado = true;
            actualizarPanelDelay();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            servicioConectado = false;
        }
    };

    // ─── Ciclo de vida ───────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView          = findViewById(R.id.webView);
        panelDelay       = findViewById(R.id.panelDelay);
        tvDelay          = findViewById(R.id.tvDelay);
        tvInstrucciones  = findViewById(R.id.tvInstrucciones);

        configurarWebView();
        webView.loadUrl(URL_INICIO);

        // Iniciar y enlazar servicio de audio
        Intent intent = new Intent(this, AudioService.class);
        startService(intent);
        bindService(intent, conexionServicio, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (servicioConectado) {
            unbindService(conexionServicio);
        }
    }

    // ─── WebView ─────────────────────────────────────────────────────────────
    private void configurarWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36"
        );

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Detectar streams m3u8 y enviárselos al AudioService
                if (url.contains(".m3u8") || url.contains("stream") || url.contains("radio")) {
                    if (servicioConectado) {
                        audioService.setStreamUrl(url);
                        Toast.makeText(MainActivity.this,
                            "✓ Stream detectado. Presiona MENU para controlar el delay.",
                            Toast.LENGTH_LONG).show();
                    }
                    return true;
                }
                return false;
            }
        });

        // Interceptar peticiones para detectar streams automáticamente
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                if (servicioConectado && !audioService.isPlaying()) {
                    if (url.contains(".m3u8") || url.contains("chunklist") ||
                        url.contains("playlist") || url.contains("index.m3u8")) {
                        audioService.setStreamUrl(url);
                        runOnUiThread(() ->
                            Toast.makeText(MainActivity.this,
                                "📻 Stream detectado: " + url.substring(0, Math.min(url.length(), 50)) + "...\nPresiona MENU para el delay.",
                                Toast.LENGTH_LONG).show()
                        );
                    }
                }
            }
        });
    }

    // ─── Control remoto ──────────────────────────────────────────────────────
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        // Tecla MENU o botón verde → cambiar entre WebView y panel de delay
        if (keyCode == KeyEvent.KEYCODE_MENU ||
            keyCode == KeyEvent.KEYCODE_PROG_GREEN ||
            keyCode == KeyEvent.KEYCODE_BOOKMARK) {
            toggleModo();
            return true;
        }

        // En modo delay: flechas controlan el delay
        if (modoActual == MODO_DELAY && servicioConectado) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    // +1 segundo (narración llega más tarde)
                    audioService.ajustarDelay(1000);
                    actualizarPanelDelay();
                    return true;

                case KeyEvent.KEYCODE_DPAD_LEFT:
                    // -1 segundo (narración llega más temprano)
                    audioService.ajustarDelay(-1000);
                    actualizarPanelDelay();
                    return true;

                case KeyEvent.KEYCODE_DPAD_UP:
                    // +5 segundos
                    audioService.ajustarDelay(5000);
                    actualizarPanelDelay();
                    return true;

                case KeyEvent.KEYCODE_DPAD_DOWN:
                    // -5 segundos
                    audioService.ajustarDelay(-5000);
                    actualizarPanelDelay();
                    return true;

                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    // Resetear delay a 0
                    audioService.resetDelay();
                    actualizarPanelDelay();
                    Toast.makeText(this, "Delay reseteado a 0", Toast.LENGTH_SHORT).show();
                    return true;

                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    // Play/Pause del audio
                    if (audioService.isPlaying()) {
                        audioService.pause();
                    } else {
                        audioService.play();
                    }
                    actualizarPanelDelay();
                    return true;
            }
        }

        // Botón atrás en WebView
        if (keyCode == KeyEvent.KEYCODE_BACK && modoActual == MODO_WEBVIEW) {
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    // ─── Cambio de modo ──────────────────────────────────────────────────────
    private void toggleModo() {
        if (modoActual == MODO_WEBVIEW) {
            modoActual = MODO_DELAY;
            webView.setVisibility(View.GONE);
            panelDelay.setVisibility(View.VISIBLE);
            actualizarPanelDelay();
        } else {
            modoActual = MODO_WEBVIEW;
            panelDelay.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
        }
    }

    // ─── Actualizar UI del delay ──────────────────────────────────────────────
    private void actualizarPanelDelay() {
        if (!servicioConectado) return;

        long delayMs = audioService.getDelayMs();
        boolean playing = audioService.isPlaying();
        String streamUrl = audioService.getStreamUrl();

        runOnUiThread(() -> {
            // Mostrar delay formateado
            long absDel = Math.abs(delayMs);
            String signo = delayMs >= 0 ? "+" : "-";
            String delayStr = String.format("%s%d.%d seg", signo, absDel / 1000, (absDel % 1000) / 100);
            tvDelay.setText(delayStr);

            // Color según si está jugando
            int color = playing ? 0xFF00C853 : 0xFFFF5252;
            tvDelay.setTextColor(color);

            // Estado y stream
            String estado = playing ? "▶ Reproduciendo" : "⏸ Pausado";
            String urlCorta = (streamUrl != null && !streamUrl.isEmpty())
                ? streamUrl.substring(0, Math.min(streamUrl.length(), 60)) + "..."
                : "Sin stream — navega en el WebView y dale Play";

            tvInstrucciones.setText(
                estado + "\n\n" +
                "Stream: " + urlCorta + "\n\n" +
                "← → : ±1 segundo\n" +
                "↑ ↓  : ±5 segundos\n" +
                "OK   : resetear a 0\n" +
                "MENU : volver al navegador\n\n" +
                "Sube el volumen de la TV para escuchar la narración.\n" +
                "Baja el volumen de YouTube a 0."
            );
        });
    }
}
