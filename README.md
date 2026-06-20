# NarradoresTV 🎙📺

APK para Android TV que reproduce la narración de FutbolLibre con delay ajustable,
sincronizada con el video 4K de YouTube corriendo en la ONNTV.

---

## ¿Cómo funciona?

1. YouTube corre en la TV con volumen a 0
2. NarradoresTV abre FutbolLibre en un WebView integrado
3. Das Play al partido en FutbolLibre
4. Presionas MENU → panel de control del delay
5. Ajustas con las flechas hasta sincronizar con el video
6. Minimizas la app → el audio sigue en segundo plano

---

## Control remoto

| Botón     | Acción                          |
|-----------|--------------------------------|
| MENU      | Cambiar entre WebView y delay  |
| ← →       | ±1 segundo de delay            |
| ↑ ↓       | ±5 segundos de delay           |
| OK        | Resetear delay a 0             |
| Play/Pause| Pausar/reanudar narración      |
| Atrás     | Navegar atrás en el WebView    |

---

## Compilar el APK

### Requisitos
- Android Studio Hedgehog o superior
- JDK 11+
- SDK Android 34

### Pasos

1. Abrir el proyecto en Android Studio:
   ```
   File → Open → seleccionar carpeta NarradoresTV
   ```

2. Esperar que Gradle sincronice las dependencias

3. Compilar APK:
   ```
   Build → Build Bundle(s) / APK(s) → Build APK(s)
   ```
   El APK queda en:
   `app/build/outputs/apk/debug/app-debug.apk`

### Compilar desde línea de comandos (Windows)
```bash
cd NarradoresTV
gradlew.bat assembleDebug
```

---

## Instalar en la ONNTV

### Opción A — USB
1. Copia el APK a un USB
2. En la TV: Configuración → Aplicaciones → Instalar desde USB
3. O usar un explorador de archivos (ES File Explorer) desde el USB

### Opción B — ADB por WiFi
```bash
# En la TV: habilitar ADB en Configuración → Información del sistema
# Anotar la IP de la TV

adb connect 192.168.x.x:5555
adb install app-debug.apk
```

### Opción C — Descargar APK desde la red local
```bash
# En la PC, servir el APK con Python
python -m http.server 8080
# En la TV, abrir el navegador y ir a http://IP-DE-LA-PC:8080
# Descargar e instalar el APK
```

---

## Detección automática del stream

La app detecta automáticamente las URLs m3u8 que carga FutbolLibre.
Si no detecta automáticamente, navega al partido y la app intercepta
las peticiones del reproductor.

Si el stream no se detecta solo, puedes:
1. Abrir el partido en FutbolLibre dentro del WebView
2. Usar un interceptor de red (como el inspector de FutbolLibre)
   para copiar la URL m3u8 manualmente

---

## Estructura del proyecto

```
NarradoresTV/
├── app/src/main/
│   ├── java/com/akhimport/narradorestv/
│   │   ├── MainActivity.java     ← WebView + control remoto
│   │   └── AudioService.java     ← ExoPlayer + delay en background
│   ├── res/
│   │   ├── layout/activity_main.xml
│   │   ├── values/strings.xml
│   │   ├── values/styles.xml
│   │   └── drawable/ic_launcher.xml
│   └── AndroidManifest.xml
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## Notas técnicas

- El delay se implementa buscando en el buffer del stream HLS en vivo
- Rango: -120 a +120 segundos
- El servicio corre en background con notificación persistente
- Compatible con Android TV 5.0+ (API 21+)
- ExoPlayer 2.19 soporta HLS, DASH y streams directos

---

## Configuración ADB para reproducción en background (IMPORTANTE)

Sin este paso, la ONNTV puede matar el audio al cambiar de app.

### Conectar por ADB WiFi

```bash
# 1. En la TV: Configuración → Sistema → Opciones de desarrollador
#    → Depuración por ADB: ACTIVAR
#    → Anotar la IP de la TV (Configuración → Red)

# 2. En la PC:
adb connect 192.168.x.x:5555

# 3. Verificar conexión:
adb devices
```

### Comandos ADB obligatorios (ejecutar una sola vez)

```bash
# Excluir la app de la optimización de batería
# (evita que Android TV mate el servicio en background)
adb shell dumpsys deviceidle whitelist +com.akhimport.narradorestv

# Desactivar restricción de background para la app
adb shell cmd appops set com.akhimport.narradorestv RUN_IN_BACKGROUND allow
adb shell cmd appops set com.akhimport.narradorestv START_FOREGROUND allow

# Permitir que el servicio inicie en background (Android 12+)
adb shell appops set com.akhimport.narradorestv MANAGE_MEDIA allow
```

### Verificar que el servicio está corriendo en background

```bash
# Debe aparecer AudioService en la lista
adb shell dumpsys activity services com.akhimport.narradorestv
```

### Si el audio se corta al cambiar a YouTube

```bash
# Ver qué está matando los procesos
adb shell dumpsys activity processes | grep narradores

# Forzar que no se detenga
adb shell am set-inactive com.akhimport.narradorestv false
```
