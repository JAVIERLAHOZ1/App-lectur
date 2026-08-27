package com.javierlahoz.lectur.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    /** Sigue al sistema: claro de dia, oscuro de noche. */
    SYSTEM,

    /** Papel blanco. */
    LIGHT,

    /** Papel crema, tinta marron: mas descansado que el blanco puro. */
    SEPIA,

    /** Fondo negro y letras blancas. */
    DARK;

    companion object {
        fun from(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** Como se pasan las paginas en el lector. */
enum class ReadingMode {
    /** Scroll vertical continuo, una pagina detras de otra. */
    SCROLL,

    /** Una pagina completa que se pasa deslizando de lado. */
    PAGE,

    /** Modo libro: dos paginas abiertas una junto a otra (en horizontal). */
    BOOK;

    companion object {
        fun from(name: String?): ReadingMode = entries.firstOrNull { it.name == name } ?: SCROLL
    }
}

/** Preferencias sencillas de la app: tema, modo de lectura y zoom. */
class SettingsStore private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("lectur_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(ThemeMode.from(prefs.getString(KEY_THEME, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _readingMode = MutableStateFlow(ReadingMode.from(prefs.getString(KEY_READING, null)))
    val readingMode: StateFlow<ReadingMode> = _readingMode.asStateFlow()

    private val _zoom = MutableStateFlow(prefs.getFloat(KEY_ZOOM, 1f))
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    /** Brillo del lector: [SYSTEM_BRIGHTNESS] = el del sistema, o 0f..1f manual. */
    private val _brightness = MutableStateFlow(prefs.getFloat(KEY_BRIGHTNESS, SYSTEM_BRIGHTNESS))
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private val _lockRotation = MutableStateFlow(prefs.getBoolean(KEY_LOCK_ROTATION, false))
    val lockRotation: StateFlow<Boolean> = _lockRotation.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setReadingMode(mode: ReadingMode) {
        _readingMode.value = mode
        prefs.edit().putString(KEY_READING, mode.name).apply()
    }

    fun setZoom(value: Float) {
        val clamped = value.coerceIn(MIN_ZOOM, MAX_ZOOM)
        _zoom.value = clamped
        prefs.edit().putFloat(KEY_ZOOM, clamped).apply()
    }

    fun setBrightness(value: Float) {
        val clamped = if (value < 0f) SYSTEM_BRIGHTNESS else value.coerceIn(MIN_BRIGHTNESS, 1f)
        _brightness.value = clamped
        prefs.edit().putFloat(KEY_BRIGHTNESS, clamped).apply()
    }

    fun setLockRotation(locked: Boolean) {
        _lockRotation.value = locked
        prefs.edit().putBoolean(KEY_LOCK_ROTATION, locked).apply()
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 3f
        const val ZOOM_STEP = 0.25f

        /** Brillo delegado en el sistema (el valor que entiende Android). */
        const val SYSTEM_BRIGHTNESS = -1f

        /** Por debajo de esto la pantalla se queda casi negra. */
        const val MIN_BRIGHTNESS = 0.05f

        private const val KEY_THEME = "theme_mode"
        private const val KEY_READING = "reading_mode"
        private const val KEY_ZOOM = "reader_zoom"
        private const val KEY_BRIGHTNESS = "reader_brightness"
        private const val KEY_LOCK_ROTATION = "lock_rotation"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}
