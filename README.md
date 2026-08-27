# Lectur — tu biblioteca de PDF para la tablet

App Android muy sencilla para leer libros en PDF guardando siempre por dónde vas.

## Qué hace

- **Biblioteca propia**: añades tus PDF y se quedan dentro de la app, con su portada
  (la primera página del libro) y una barra de progreso.
- **Recuerda el progreso**: al abrir un libro vuelve exactamente a la página en la que
  lo dejaste. El progreso se guarda solo, mientras lees.
- **Modo oscuro de verdad**: fondo negro y letras blancas, tanto en la app como en las
  páginas del PDF (se invierten los colores al leer). Cuatro opciones: automático
  (sigue al sistema), claro, **sepia** (papel crema y tinta marrón) y oscuro.
- **Índice del libro**: si el PDF trae marcadores, se listan los capítulos y se salta
  a cualquiera con un toque.
- **Diccionario**: mantén pulsada una palabra y sale su definición (Wikcionario), con
  un botón para abrirla en la RAE. Es lo único que usa internet.
- **Brillo propio del lector** y **bloqueo de rotación**, sin salir del libro.
- **Añadir libros**: botón *Añadir PDF* (te deja elegir cualquier PDF de la tablet,
  Drive, Descargas...) o desde el gestor de archivos con *Abrir con → Lectur*.
- **Gestión**: renombrar un libro, empezarlo de nuevo o eliminarlo.
- **Tres modos de lectura** (se eligen con el icono de la barra inferior y se recuerdan):
  - *Scroll continuo*: una página detrás de otra, con zoom por pasos.
  - *Página a página*: la página entera ajustada a la pantalla, se pasa deslizando.
  - *Libro*: dos páginas abiertas una junto a otra, como un libro de papel (con la
    tablet en horizontal; en vertical pasa sola a una página).
- **Lectura cómoda**: barra para saltar a cualquier página, pantalla que no se apaga y
  un toque en el centro oculta o muestra los controles. En modo página y libro, tocar
  el borde izquierdo o derecho pasa página.

Todo funciona **sin internet** y sin cuentas: los libros se copian al almacenamiento
privado de la app, así que el PDF original de la tablet puedes borrarlo o moverlo sin
perder nada.

## Detalles técnicos

- Kotlin + Jetpack Compose (Material 3).
- El PDF se dibuja con `android.graphics.pdf.PdfRenderer`, el motor que ya trae
  Android. Para el índice y para saber qué palabra hay bajo el dedo se usa
  **PdfBox-Android**, que sí sabe leer la estructura y el texto del documento.
- El diccionario consulta la API REST del Wikcionario, por eso la app declara el
  permiso de internet. Leer, importar y guardar el progreso sigue siendo offline.
- El progreso y la biblioteca se guardan en un simple `library.json` dentro de la app;
  las preferencias, en `SharedPreferences`. Sin base de datos ni servidores.
- `minSdk 26` (Android 8) — `targetSdk 35`.

## Cómo conseguir el APK e instalarlo en la tablet

### Opción A — sin instalar nada (GitHub Actions)

1. En GitHub, entra en la pestaña **Actions** → workflow **Construir APK**.
2. Cada push a este repositorio lanza una compilación; también puedes lanzarla a mano
   con **Run workflow**.
3. Cuando termine (unos minutos), abre la ejecución y descarga el artefacto
   **lectur-debug-apk**. Dentro está `app-debug.apk`.
4. Pasa el APK a la tablet (Drive, cable, Telegram...), ábrelo y acepta
   *Instalar aplicaciones desconocidas* cuando Android lo pida.

### Opción B — Android Studio

1. Abre la carpeta del proyecto en Android Studio (Ladybug o posterior).
2. `Build → Build Bundle(s)/APK(s) → Build APK(s)`, o con la tablet conectada por USB
   y la depuración USB activada, dale a **Run**.

### Opción C — línea de comandos

```bash
./gradlew assembleDebug
# APK en app/build/outputs/apk/debug/app-debug.apk
```

Necesitas el SDK de Android instalado y un `local.properties` con `sdk.dir=/ruta/al/sdk`
(Android Studio lo crea solo).

## Cómo se usa

1. Abre la app y pulsa **Añadir PDF**.
2. Elige el libro; aparece en la biblioteca con su portada.
3. Tócalo para leer. Al salir (botón atrás), el progreso queda guardado.
4. El icono de arriba a la derecha cambia el tema; dentro del lector hay otro atajo
   para pasar a modo oscuro sin salir del libro.
