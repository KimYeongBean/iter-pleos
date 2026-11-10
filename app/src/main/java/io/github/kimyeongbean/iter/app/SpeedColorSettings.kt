package io.github.kimyeongbean.iter.app

import android.content.Context
import androidx.annotation.StringRes

/**
 * Map graph background (맵 그래프 바탕), chosen by the user.
 * Colours are Pleos surface_basic (dark basic_50, light basic_00); text is basic_500 of each theme.
 */
internal enum class MapBackground(
    val argb: Int,
    val textArgb: Int,
    @StringRes val label: Int,
) {
    DARK(0xFF1D1E21.toInt(), 0xFFC7C9CE.toInt(), R.string.map_background_dark),
    LIGHT(0xFFFFFFFF.toInt(), 0xFF686A72.toInt(), R.string.map_background_light),
}

/**
 * Speed colour base (속도색 기본 색): fixed palette of five, chosen by the user.
 * Each has a variant per background so the full-speed colour keeps 4.5:1 contrast on both.
 * Hues avoid Pleos informative green, blue, red and purple so the line never reads as a warning.
 */
internal enum class SpeedColorBase(
    private val onLightArgb: Int,
    private val onDarkArgb: Int,
    @StringRes val label: Int,
) {
    AMBER(0xFFB45309.toInt(), 0xFFFBBF24.toInt(), R.string.speed_color_amber),
    LIME(0xFF4D7C0F.toInt(), 0xFFA3E635.toInt(), R.string.speed_color_lime),
    CYAN(0xFF0E7490.toInt(), 0xFF22D3EE.toInt(), R.string.speed_color_cyan),
    PINK(0xFFBE185D.toInt(), 0xFFF472B6.toInt(), R.string.speed_color_pink),
    GRAY(0xFF44464E.toInt(), 0xFFC7C9CE.toInt(), R.string.speed_color_gray),
    ;

    /** Opaque ARGB of this base colour on [background]; feed it to the speed colour. */
    fun argbOn(background: MapBackground): Int = when (background) {
        MapBackground.DARK -> onDarkArgb
        MapBackground.LIGHT -> onLightArgb
    }
}

/** What the user picked in settings. [base] and [background] count only for [ArtTheme.DEFAULT]. */
internal data class SpeedColorSettings(
    val base: SpeedColorBase = SpeedColorBase.AMBER,
    val background: MapBackground = MapBackground.DARK,
    val theme: ArtTheme = ArtTheme.DEFAULT,
) {
    /** What the art panels draw with; [dark] is the system theme. */
    fun panel(dark: Boolean): ThemePanel = theme.panel(base, background, dark)

    /** The line colour for a mark on the app background, dark or light as the system is. */
    fun accentArgb(dark: Boolean): Int = theme.accentArgb(base, dark)
}

/** Keeps [SpeedColorSettings] on the device so they survive restarts. */
internal class SpeedColorSettingsStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SpeedColorSettings {
        val defaults = SpeedColorSettings()
        return SpeedColorSettings(
            base = enumOrNull<SpeedColorBase>(prefs.getString(KEY_BASE, null)) ?: defaults.base,
            background = enumOrNull<MapBackground>(prefs.getString(KEY_BACKGROUND, null)) ?: defaults.background,
            theme = enumOrNull<ArtTheme>(prefs.getString(KEY_THEME, null)) ?: defaults.theme,
        )
    }

    fun save(settings: SpeedColorSettings) {
        prefs
            .edit()
            .putString(KEY_BASE, settings.base.name)
            .putString(KEY_BACKGROUND, settings.background.name)
            .putString(KEY_THEME, settings.theme.name)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "speed_color_settings"
        const val KEY_BASE = "base"
        const val KEY_BACKGROUND = "background"
        const val KEY_THEME = "theme"
    }
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? = enumValues<T>().firstOrNull { it.name == name }
