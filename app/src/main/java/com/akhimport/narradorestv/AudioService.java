package com.akhimport.narradorestv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

public class AudioService extends Service {

    private static final String CHANNEL_ID  = "narradores_tv_channel";
    private static final int    NOTIF_ID    = 1;
    private static final String WAKELOCK_TAG = "NarradoresTV::AudioWakeLock";

    // ─── ExoPlayer ───────────────────────────────────────────────────────────
    private ExoPlayer player;
    private String    streamUrl = "";

    // ─── Delay ───────────────────────────────────────────────────────────────
    private long delayMs   = 0;
    private static final long DELAY_MIN = -120_000;
    private static final long DELAY_MAX =  120_000;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // ─── Capa 1: WakeLock — impide que la CPU duerma ─────────────────────────
    private PowerManager.WakeLock wakeLock;

    // ─── Capa 2: AudioFocus — el sistema sabe que somos un reproductor activo ─
    private AudioManager      audioManager;
    private AudioFocusRequest audioFocusRequest; // API 26+

    // ─── Binder ──────────────────────────────────────────────────────────────
    private final IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder {
        AudioService getService() { return AudioService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    // ─── onCreate ────────────────────────────────────────────────────────────
    @Override
    public void onCreate() {
        super.onCreate();
        crearCanalNotificacion();

        // Capa 1: WakeLock parcial (mantiene CPU despierta, no pantalla)
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
        wakeLock.acquire(); // se libera en onDestroy

        // Capa 2: AudioFocus
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        pedirAudioFocus();

        // Capa 3: foreground service con notificación
        startForeground(NOTIF_ID, crearNotificacion("Listo", "Navega a FutbolLibre y dale Play"));

        // Capa 4: Inicializar player
        inicializarPlayer();
    }

    // ─── Capa 3: START_STICKY — se reinicia si el sistema lo mata ────────────
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    // ─── Capa 4: onTaskRemoved — cuando el usuario desliza la app ────────────
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Reprogramar reinicio del servicio si la app es deslizada
        Intent restartIntent = new Intent(getApplicationContext(), AudioService.class);
        PendingIntent restartPending = PendingIntent.getService(
            this, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
        am.set(android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            restartPending);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (player != null) {
            player.release();
            player = null;
        }

        // Liberar AudioFocus
        if (audioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(null);
            }
        }

        // Liberar WakeLock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    // ─── AudioFocus ──────────────────────────────────────────────────────────
    private void pedirAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChange -> {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        if (player != null) player.setVolume(0.3f);
                    } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                        if (player != null) player.setVolume(1.0f);
                    } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        // No pausar — queremos mantener la narración siempre
                        if (player != null) player.setVolume(0.5f);
                    }
                })
                .build();

            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            );
        }
    }

    // ─── Player ──────────────────────────────────────────────────────────────
    private void inicializarPlayer() {
        player = new ExoPlayer.Builder(this).build();

        // Listener para reconectar si el stream se corta
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
                    // Reintentar si se cortó el stream
                    handler.postDelayed(() -> {
                        if (player != null && !streamUrl.isEmpty()) {
                            player.prepare();
                            player.setPlayWhenReady(true);
                        }
                    }, 3000);
                }
                actualizarNotificacion();
            }
        });
    }

    public void setStreamUrl(String url) {
        this.streamUrl = url;

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36");

        DefaultDataSource.Factory dataFactory =
            new DefaultDataSource.Factory(this, httpFactory);

        MediaItem mediaItem = MediaItem.fromUri(url);

        if (url.contains(".m3u8") || url.contains("m3u8")) {
            HlsMediaSource mediaSource = new HlsMediaSource.Factory(dataFactory)
                .createMediaSource(mediaItem);
            player.setMediaSource(mediaSource);
        } else {
            player.setMediaItem(mediaItem);
        }

        player.prepare();
        player.setPlayWhenReady(true);
        aplicarDelay();
        actualizarNotificacion();
    }

    // ─── Delay ───────────────────────────────────────────────────────────────
    public void ajustarDelay(long deltaMs) {
        delayMs = Math.max(DELAY_MIN, Math.min(DELAY_MAX, delayMs + deltaMs));
        aplicarDelay();
        actualizarNotificacion();
    }

    public void resetDelay() {
        delayMs = 0;
        aplicarDelay();
        actualizarNotificacion();
    }

    private void aplicarDelay() {
        if (player == null) return;
        handler.post(() -> {
            try {
                long duracion = player.getDuration();
                if (duracion > 0) {
                    long posicion = duracion + delayMs;
                    posicion = Math.max(0, Math.min(duracion, posicion));
                    player.seekTo(posicion);
                }
            } catch (Exception ignored) {}
        });
    }

    // ─── Play / Pause ────────────────────────────────────────────────────────
    public void play()  { if (player != null) { player.setPlayWhenReady(true);  actualizarNotificacion(); } }
    public void pause() { if (player != null) { player.setPlayWhenReady(false); actualizarNotificacion(); } }

    // ─── Getters ─────────────────────────────────────────────────────────────
    public boolean isPlaying()    { return player != null && player.isPlaying(); }
    public long    getDelayMs()   { return delayMs; }
    public String  getStreamUrl() { return streamUrl; }

    // ─── Notificación ────────────────────────────────────────────────────────
    private void crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Narración TV", NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Narración sincronizada en background");
            channel.setSound(null, null); // sin sonido de notificación
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification crearNotificacion(String titulo, String texto) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
            this, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)          // no se puede deslizar para cerrar
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void actualizarNotificacion() {
        long abs    = Math.abs(delayMs);
        String sig  = delayMs >= 0 ? "+" : "-";
        String est  = isPlaying() ? "▶" : "⏸";
        String dStr = String.format("%s %s%d.%ds", est, sig, abs / 1000, (abs % 1000) / 100);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID, crearNotificacion("NarradoresTV — " + dStr,
            streamUrl.isEmpty() ? "Sin stream activo" : "Narración en vivo"));
    }
}
