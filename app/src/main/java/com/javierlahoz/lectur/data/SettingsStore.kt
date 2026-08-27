package com.javierlahoz.lectur.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun from(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** Preferencias sencillas de la app: tema y tamano de pagina en el lector. */
class SettingsStore private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("lectur_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(ThemeMode.from(prefs.getString(KEY_THEME, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _zoom = MutableStateFlow(prefs.getFloat(KEY_ZOOM, 1f))
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setZoom(value: Float) {
        val clamped = value.coerceIn(MIN_ZOOM, MAX_ZOOM)
        _zoom.value = clamped
        prefs.edit().putFloat(KEY_ZOOM, clamped).apply()
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 3f
        const val ZOOM_STEP = 0.25f

        private const val KEY_THEME = "theme_mode"
        private const val KEY_ZOOM = "reader_zoom"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}
